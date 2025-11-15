package com.deathfrog.mctradepost.api.entity.pets;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.goals.EatFromInventoryHealGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.HerdGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.OpenGateOrDoorGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.ReturnToTrainerAtNightGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.ScavengeForResourceGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.ScavengeWaterResourceGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.UnloadInventoryToWorkLocationGoal;
import com.deathfrog.mctradepost.api.entity.pets.goals.WalkToWorkPositionGoal;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.blocks.BlockDredger;
import com.deathfrog.mctradepost.core.blocks.BlockScavenge;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_PETGOALS;

public class  PetData<P extends Animal & ITradePostPet & IHerdingPet> 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int LOG_COOLDOWN_INTERVAL = 200;
    public static final int JOB_GOAL_PRIORITY = 7;

    public static final String STATS_PETS_DIED = "pets_died";
    public static final String STATS_PETS_RETIRED = "pets_retired";
    public static final String STATS_PETS_RANAWAY = "pets_ranaway";

    public int logCooldown = 0;
    private int stallTicks = 0;
    protected int watchdogCooldown = 0;
    protected static final int WATCHDOWN_COOLDOWN_INTERVAL = 20;
    protected static final String TAG_INV = "Inventory";

    // If your ItemStackHandler API requires a registry provider at load time, we
    // can buffer the tag until the Level is available (first tick).
    protected CompoundTag pendingInv = null;

    // After how many nudges without moving do we give the Sheep a pathfinding command to unstick themselves?
    public static final int STUCK_STEPS = 10;   
    public static final String TAG_ANIMAL_TYPE = "animalType";

    protected UUID entityUuid;
    protected int entityId = -1;
    protected P animal;
    protected int colonyId;
    protected BlockPos trainerBuildingID = BlockPos.ZERO;
    protected BlockPos workLocation = BlockPos.ZERO;
    private ResourceKey<Level> dimension = null;
    private final ItemStackHandler inventory = new ItemStackHandler(9);
    public PetTypes petType = null;
    
    private Component originalName = null;
    private String lastActiveGoal = null;

    public PetData(P animal)
    {
        this.animal = animal;

        if (animal != null) {
            this.petType = PetTypes.fromPetClass(animal.getClass());
            this.entityUuid = animal.getUUID();
            this.entityId = animal.getId();
            animal.setPersistenceRequired();
            this.originalName = this.getAnimal().getName();
        }

    }

    public PetData(P animal, CompoundTag compound) 
    {   
        this(animal);
        fromNBT(compound);
    }

    public P getAnimal()
    {
        return animal;
    }


    /**
     * Gets the name of the animal type associated with this pet.
     *
     * @return the name of the animal type associated with this pet.
     */
    public String getAnimalType()
    {
        return petType.getTypeName();
    }

    public Component getOriginalName()
    {
        return originalName;
    }

    public void setOriginalName(Component name)
    {
        this.originalName = name;
    }

    /**
     * Serializes the state of this BaseTradePostPet into the given CompoundTag.
     *
     * @param compound the CompoundTag to write the BaseTradePostPet's data into.
     *                 Includes the colony ID and, if available, the position and
     *                 dimension of the associated building.
     */
    public void toNBT(CompoundTag compound)
    {
        if (this.getTrainerBuilding() != null)
        {
            BlockPosUtil.write(compound,"trainerBuilding", trainerBuildingID);
            BlockPosUtil.write(compound,"workLocation", workLocation);
            compound.putString("dimension", dimension.location().toString());
            compound.putString(TAG_ANIMAL_TYPE, this.getAnimalType());
            // compound.put("Inventory", inventory.serializeNBT(animal.level().registryAccess()));
        }

        compound.putInt("entityId", this.getEntityId());

        if (animal != null && animal.getUUID() != null)
        {
            compound.putUUID("uuid", animal.getUUID());
        }

        // --- INVENTORY (always save) ---
        if (animal != null && animal.level() != null)
        {
            compound.put(TAG_INV, inventory.serializeNBT(animal.level().registryAccess()));
        }
        // LOGGER.info("Serialized PetData to NBT: {}", compound);
    }

    /**
     * Deserializes the NBT data for this BaseTradePostPet from the given CompoundTag.
     * Restores the colony ID and, if available, the position and dimension of the associated building.
     * If the building is found in the world, sets the trainer building for this pet.
     *
     * @param compound the CompoundTag containing the serialized state of the BaseTradePostPet.
     */
    public void fromNBT(CompoundTag compound)
    {
        trainerBuildingID = BlockPosUtil.read(compound, "trainerBuilding");

        if (trainerBuildingID == null || BlockPos.ZERO.equals(trainerBuildingID)) {
            MCTradePostMod.LOGGER.warn("Failed to deserialize pet data trainer positions from tag: {} - no position found.", compound);
            return;
        }

        workLocation = BlockPosUtil.read(compound, "workLocation");
        petType = PetTypes.fromPetString(compound.getString("animalType"));
        entityId = compound.getInt("entityId");
        UUID uuid = compound.hasUUID("uuid") ? compound.getUUID("uuid") : null;
        this.entityUuid = uuid; // add a field if you want


        // Dimension (guard against blank)
        String dimname = compound.getString("dimension");
        if (dimname != null && !dimname.isEmpty()) 
        {
            ResourceLocation level = ResourceLocation.parse(dimname);
            dimension = ResourceKey.create(Registries.DIMENSION, level);
        } 
        else 
        {
            dimension = null;
        }

        // --- INVENTORY (always read if present) ---
        if (compound.contains(TAG_INV))
        {
            CompoundTag invTag = compound.getCompound(TAG_INV);

            boolean applied = false;
            if (!applied) 
            {
                try 
                {
                    // Variant B: API with provider
                    if (animal != null && animal.level() != null) 
                    {
                        inventory.deserializeNBT(animal.level().registryAccess(), invTag);
                        applied = true;
                    }
                } catch (Throwable ignored) 
                { 
                    LOGGER.error("Error deserializing inventory", ignored);
                }
            }

            // If still not applied (no Level yet), keep it for later
            if (!applied) {
                pendingInv = invTag.copy();
            }

        }
    }


    /**
     * Called once per tick while this pet is active.
     * <p>If the server is present (i.e. not client-side), this method will attempt to apply any pending inventory data that was
     * deserialized from NBT earlier. If the pet is on the client-side, this does nothing.</p>
     * @param level The level containing the pet, used to access the registry if needed.
     */

    public void tick(Level level)
    {
        if (!level.isClientSide) 
        {
            if (animal != null)
            {
                this.updateDebugNameTag();
            }
            
            tryApplyPendingInventory(level);
        }
    }

    /**
     * Gets the BlockPos ID of the trainer building associated with this pet.
     * 
     * @return the BlockPos ID of the trainer building associated with this pet.
     */
    public BlockPos getTrainerBuildingID()
    {
        return trainerBuildingID;
    }

    public IBuilding getTrainerBuilding()
    {
        return BuildingUtil.buildingFromDimPos(dimension, trainerBuildingID);
    }

    public IBuildingView getTrainerBuildingView()
    {
        return BuildingUtil.buildingViewFromDimPos(dimension, trainerBuildingID);
    }

    /**
     * Sets the trainer building for this pet to the given building.
     *
     * @param building the IBuilding to set as the trainer building for this pet.
     */
    public void setTrainerBuilding(IBuilding building)
    {

        if (building == null)
        {
            trainerBuildingID = BlockPos.ZERO;
            return;
        }

        this.dimension = building.getColony().getDimension();
        this.trainerBuildingID = building.getID();
    }

    public BlockPos getWorkLocation()
    {
        return workLocation;
    }

    public IBuildingView getWorkBuildingView()
    {
        return BuildingUtil.buildingViewFromDimPos(dimension, workLocation);
    }

    /**
     * Sets the work location for the pet to the given BlockPos.
     * If the new work location is different from the current work location, calls {@link #workLocationChanged()} to notify the pet and reset its goals.
     * If the work location is null, sets the work location to BlockPos.ZERO.
     * @param workLocation the new work location for the pet.
     */
    public void setWorkLocation(BlockPos workLocation)
    {
        if (workLocation == null)
        {
            this.workLocation = BlockPos.ZERO;
            return;
        }

        if (!this.workLocation.equals(workLocation))
        {
            this.workLocation = workLocation;
            workLocationChanged();
        }

    }

    /**
     * Called when the pet's work location is changed to notify the pet and reset its goals.
     * If the pet is alive, reset its goals to account for the new work location.
     */
    public void workLocationChanged()
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Pet {}: Work location changed to: {}", this.animal.getUUID(), this.workLocation));
        
        if (this.animal != null || this.animal.isAlive())
        {
            this.animal.resetGoals();
        }
    }
    
    /**
     * Assigns basic goals to the pet, and then assigns a work-location-specific goal,
     * if the work location is valid.
     * 
     * @see #assignBasicGoals()
     * @see #assignGoalFromWorkLocation()
     */
    public void assignPetGoals()
    {
        this.assignBasicGoals();
        this.assignGoalFromWorkLocation();
    }


    /**
     * Assigns basic goals to the pet, such as floating, opening doors, unloading inventory, and walking to the work position.
     * Also assigns a goal to return to the trainer building at night, if the trainer building is valid.
     *
     * @see com.deathfrog.mctradepost.api.entity.pets.PetAxolotl#assignBasicGoals()
     * @see com.deathfrog.mctradepost.api.entity.pets.PetWolf#assignBasicGoals()
     */
    public void assignBasicGoals()
    {
        P animal = this.getAnimal();
        if (animal == null)
        {
            return;
        }
        animal.goalSelector.addGoal(1, new FloatGoal(animal));
        animal.goalSelector.addGoal(2, new OpenGateOrDoorGoal(animal, true, 20));
        animal.goalSelector.addGoal(3, new UnloadInventoryToWorkLocationGoal<>(animal, 0.4f));
        animal.getNavigation().getNodeEvaluator().setCanOpenDoors(true);

        BlockPos workPos = getWorkLocation();
        if (workPos != null && !workPos.equals(BlockPos.ZERO))
        {
            this.getAnimal().goalSelector.addGoal(5, new WalkToWorkPositionGoal<>(this.getAnimal(), getWorkLocation(), 1.2, 2));
        }

        animal.goalSelector.addGoal(6, new EatFromInventoryHealGoal<P>(animal, 300));

        if (getTrainerBuilding() != null)
        {
            this.getAnimal().goalSelector.addGoal(30, new ReturnToTrainerAtNightGoal<>(this.getAnimal(), getTrainerBuilding().getPosition()));
        }

    }

    // TODO: Implement scaling by trainer building level.
    // TODO: Implement research effects.


    /**
     * Assigns a goal to the pet based on the work location, if the work location is valid.
     *
     * If the work location is a herd, assigns a {@link HerdGoal HerdGoal}.
     * If the work location is a scavenge for land, assigns a {@link ScavengeForResourceGoal ScavengeForResourceGoal}.
     * If the work location is a scavenge for water, assigns a {@link ScavengeWaterResourceGoal ScavengeWaterResourceGoal}.
     *
     * @see HerdGoal
     * @see ScavengeForResourceGoal
     * @see ScavengeWaterResourceGoal
     */
    public void assignGoalFromWorkLocation()
    {

        if (this.getAnimal() == null)
        {
            return;
        }

        PetRoles role = this.roleFromWorkLocation(this.getAnimal().level());

        if (role == null)
        {
            return;
        }

        switch (role)
        {
            case HERDING:
                this.getAnimal().goalSelector.addGoal(JOB_GOAL_PRIORITY, new HerdGoal<P>(this.getAnimal()));
                break;

            case SCAVENGE_LAND:
                this.getAnimal().goalSelector.addGoal(JOB_GOAL_PRIORITY, new ScavengeForResourceGoal<>(
                    this.getAnimal(),
                    16,                      // search radius
                    8.0,                     // light level (optional to ignore)
                    0.3f,                    // 30% success rate
                    pos -> {
                        BlockState stateBelow = this.getAnimal().level().getBlockState(pos.below());
                        return this.getAnimal().level().getMaxLocalRawBrightness(pos) < 8 &&
                            (stateBelow.is(BlockTags.DIRT) || stateBelow.is(BlockTags.MUSHROOM_GROW_BLOCK)) &&
                            this.getAnimal().level().isEmptyBlock(pos);
                    },
                    pos -> {
                        // Pick a mushroom
                        Block mushroomBlock = this.getAnimal().getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
                        Item mushroomItem = mushroomBlock.asItem();
                        
                        // Track the stat with item name
                        StatsUtil.trackStatByName(this.getTrainerBuilding(), ScavengeForResourceGoal.ITEMS_SCAVENGED, mushroomItem.getDefaultInstance().getDisplayName(), 1);

                        // Place the mushroom
                        this.getAnimal().level().setBlock(pos, mushroomBlock.defaultBlockState(), 3);
                    },
                    1000
                ));
                break;

            case SCAVENGE_WATER:
                this.getAnimal().goalSelector.addGoal(JOB_GOAL_PRIORITY, new ScavengeWaterResourceGoal<>(
                    this.getAnimal(), 
                    8,
                    0.08f,          // Chance per try; there are 10 tries per cooldown cycle.
                    this.getTrainerBuilding(),
                    200            // cooldown (10 seconds)
                ));
                break;

            case NONE:
            default:
                break;
        }
    }

    /**
     * Periodic AI watchdog check. This is called every tick the pet AI is enabled.
     * 
     * <p>This function is responsible for detecting AI stalls and attempting to recover from them. 
     * If there are no active goals, targets, or navigation, it increments a counter. 
     * If the counter reaches 10, it enables the control flags for movement, looking, and targeting. 
     * If the counter reaches 20, it clears the stale path to let goals start fresh. If the counter reaches 50, 
     * it performs a single soft goal refresh. If the pet becomes active again, it resets the counter.</p>
     * 
     * <p>This is a "soft" AI reset, meaning it doesn't reset the entire pet state, but only the AI control flags, path, and goals. 
     * This is less intrusive than a full reset and avoids resetting the pet's inventory, skills, and other state.</p>
     * 
     * <p>This function is called from PetData.resetGoals() and is intended to be called every tick the pet AI is enabled.</p>
     */
    public void aiWatchdogTick()
    {
        if (watchdogCooldown >= 0) {
            watchdogCooldown--;
            return;
        }

        watchdogCooldown = WATCHDOWN_COOLDOWN_INTERVAL;
        
        if (this.getAnimal() == null || this.getAnimal().level().isClientSide) return;

        boolean anyMoveRunning = this.getAnimal().goalSelector.getAvailableGoals().stream().anyMatch(WrappedGoal::isRunning);
        boolean anyTargetRunning = this.getAnimal().targetSelector.getAvailableGoals().stream().anyMatch(WrappedGoal::isRunning);
        boolean navBusy = !getAnimal().getNavigation().isDone();
        boolean active = anyMoveRunning || anyTargetRunning || navBusy;

        if (!active && getAnimal().isAlive() && !getAnimal().isPassenger() && !getAnimal().isLeashed() && !getAnimal().isNoAi())
        {
            stallTicks++;
            // (first second): make sure control flags aren’t stuck off
            if (stallTicks == 10)
            {
                getAnimal().goalSelector.enableControlFlag(Goal.Flag.MOVE);
                getAnimal().goalSelector.enableControlFlag(Goal.Flag.LOOK);
                getAnimal().targetSelector.enableControlFlag(Goal.Flag.TARGET);
            }
            // (~2s): clear stale path to let goals start fresh
            if (stallTicks == 20)
            {
                getAnimal().getNavigation().stop();
            }
            // (~5s): single soft goal refresh (once)
            if (stallTicks == 50)
            {
                getAnimal().resetGoals();
            }
        }
        else
        {
            stallTicks = 0; // recovered
        }
    }

    public int getStallTicks() 
    { 
        return stallTicks; 
    }

    /**
     * Retrieves the closest building to the pet's work location within the given level.
     *
     * @param level the level in which to search for the closest building.
     * @return the building instance that is closest to the pet's current work location.
     */
    public IBuilding getBuildingContainingWorkLocation(Level level)
    {
        IColony colony = IColonyManager.getInstance().getClosestColony(level, this.getWorkLocation());
        for (IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            if (BlockPosUtil.isInArea(building.getCorners().getA(), building.getCorners().getB(), workLocation)) 
            {
                return building;
            }
        }

        return null;
    }

    /**
     * Retrieves a list of compatible animals within the given bounding box.
     *
     * @param boundingBox the area to search for compatible animals.
     * @return a list of animals that are compatible with one or more of the AnimalHerdingModules associated with this pet's work
     *         building.
     */
    public List<? extends Animal> searchForCompatibleAnimals(Level level, AABB boundingBox)
    {
        final List<Animal> animals = new ArrayList<>();

        IBuilding workBuilding = this.getBuildingContainingWorkLocation(level);

        if (workBuilding == null) return animals;

        for (final AnimalHerdingModule module : workBuilding.getModulesByType(AnimalHerdingModule.class))
        {
            animals.addAll(workBuilding.getColony()
                .getWorld()
                .getEntitiesOfClass(Animal.class, boundingBox, animal -> module.isCompatible(animal) && !animal.isFullyFrozen()));
        }

        return animals;
    }

    /**
     * Retrieves the entity ID associated with this pet data.
     * 
     * @return the entity ID associated with this pet data.
     */
    public int getEntityId()
    {
        if (animal == null) return entityId;

        return animal.getId();
    }

    /**
     * Retrieves the UUID of the entity associated with this pet data.
     *
     * @return the UUID of the entity associated with this pet data.
     */
    public UUID getEntityUuid()
    {
        return entityUuid;
    }
    
    /**
     * Retrieves the inventory associated with this pet. The inventory
     * stores the items the pet is carrying, and is used to transfer items
     * between the pet and the player, or the pet and another entity.
     *
     * @return the inventory of the pet.
     */
    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * Retrieves the role associated with the given block position in the given level.
     *
     * @param level the level that the block position is in
     * @param pos   the block position to retrieve the role for
     * @return the PetRoles value associated with the block at the given position, or null if the block is not a valid work location
     */
    public static PetRoles roleFromPosition(Level level, BlockPos pos) 
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BlockTrough)
        {
            return PetRoles.HERDING;
        }

        if (block instanceof BlockScavenge)
        {
            return PetRoles.SCAVENGE_LAND;
        }

        if (block instanceof BlockDredger)
        {
            return PetRoles.SCAVENGE_WATER;
        }

        return PetRoles.NONE;
    }

    /**
     * Retrieves the role associated with the pet's current work location.
     * For example, if the pet is assigned to a trough, it is a herder.
     *
     * @param level the level in which the pet's work location is.
     * @return the role associated with the pet's work location, or null if the location is not a valid role.
     */
    public PetRoles roleFromWorkLocation(Level level) 
    {
        if (this.workLocation == null || this.workLocation.equals(BlockPos.ZERO)) return PetRoles.NONE;

        return roleFromPosition(level, this.workLocation);
    }

    /**
     * Returns the active goal of this pet, if any. This is the goal that the pet is currently executing. If the pet does not have an
     * active goal, this returns null.
     *
     * @return the active goal of this pet, or null
     */
    public Goal getActiveGoal()
    {
        if (getAnimal() == null) return null;

        for (WrappedGoal wrapped : getAnimal().goalSelector.getAvailableGoals())
        {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                return goal;
            }
        }
        return null;
    }

    /**
     * Retrieves the set of goals that are available to the pet. A goal is
     * "available" if it is registered with the pet's goal selector and is
     * not currently running.
     *
     * @return the set of available goals, or null if the pet is null.
     */
    public Set<WrappedGoal> getAvailableGoals()
    {
        if (getAnimal() == null) return null;
        
        return getAnimal().goalSelector.getAvailableGoals();
    }

    /**
     * Logs the active goals of this pet every 100 ticks. Used for debugging.
     */
    public void logActiveGoals()
    {

        if (logCooldown > 0)
        {
            logCooldown--;
            return;
        }

        if (getAnimal() == null) 
        {
            TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("No pet data while logging active goal in PetData."));
            return;
        }

        logCooldown = LOG_COOLDOWN_INTERVAL;

        for (WrappedGoal wrapped : getAnimal().goalSelector.getAvailableGoals())
        {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Active Goal for pet {}: {} ", this.getAnimal().getUUID(), goal.getClass().getSimpleName()));
            }
        }

        for (WrappedGoal wrapped : getAnimal().targetSelector.getAvailableGoals())
        {
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                TraceUtils.dynamicTrace(TRACE_PETGOALS,
                    () -> LOGGER.info("Active Target Goal for pet {}: {}" , this.getAnimal().getUUID(), goal.getClass().getSimpleName()));
            }
        }
    }

    /**
     * Called when the pet is removed from the game for any reason.
     * Tracks a statistic based on the reason for removal.
     *
     * @param reason the reason the pet was removed
     */
    public void onRemoval(@Nonnull RemovalReason reason)
    {
        if (reason == RemovalReason.KILLED)
        {
            StatsUtil.trackStatByName(getTrainerBuilding(), STATS_PETS_DIED, this.getAnimalType(), 1);
        }
        else if (reason == RemovalReason.DISCARDED)
        {
            StatsUtil.trackStatByName(getTrainerBuilding(), STATS_PETS_RETIRED, this.getAnimalType(), 1);
        }
        else
        {
            StatsUtil.trackStatByName(getTrainerBuilding(), STATS_PETS_RANAWAY, this.getAnimalType(), 1);
        }
    }

    /** Call from the entity’s server tick once level/registry are available. */
    public void tryApplyPendingInventory(Level level)
    {
        if (pendingInv == null || level == null || level.isClientSide) return;

            try 
            {
                inventory.deserializeNBT(level.registryAccess(), pendingInv);
            } 
            catch (Throwable err) 
            {
                // LOGGER.error("Failed to deserialize pet inventory", err);
                return;
            }

        pendingInv = null; // applied successfully
    }

    /**
     * Updates the custom name tag of the associated animal, based on whether or not pet goal tracing is enabled.
     * If tracing is enabled, the name will be updated to include the animal's ID and original name.
     * If tracing is disabled, the name will be restored to the original name (if any).
     */
    public void updateDebugNameTag()
    {

        if (getAnimal() == null) return;

        boolean debugging = TraceUtils.isTracing(TraceUtils.TRACE_PETGOALS);

        if (debugging)
        {
            String idText = this.getAnimal().getUUID().toString();
            String baseName = this.originalName != null ? this.originalName.getString() : "<unnamed>";

            Goal goal = getActiveGoal();
            String goalName = null;

            if (goal != null)
            {
                lastActiveGoal = goal.getClass().getSimpleName();
                goalName = " - " + lastActiveGoal;
            }

            if (this.lastActiveGoal != null)
            {
                goalName = " - " + this.lastActiveGoal;
            }
            else
            {
                goalName = "";
            }

            this.getAnimal().setCustomName(Component.literal(idText + " (" + baseName + goalName + ")"));
            this.getAnimal().setCustomNameVisible(true);
        }
        else
        {
            // In normal gameplay: restore the true saved name
            this.getAnimal().setCustomName(this.originalName);
            this.getAnimal().setCustomNameVisible(false); // or false, depending on your preference
        }
    }
}
