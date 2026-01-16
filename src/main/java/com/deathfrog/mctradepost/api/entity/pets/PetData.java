package com.deathfrog.mctradepost.api.entity.pets;

import java.lang.reflect.Field;
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
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.blocks.BlockDredger;
import com.deathfrog.mctradepost.core.blocks.BlockScavenge;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.ldtteam.blockui.mod.Log;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import static com.deathfrog.mctradepost.api.util.TraceUtils.*;

public class  PetData<P extends Animal & ITradePostPet & IHerdingPet> 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int LOG_COOLDOWN_INTERVAL = 200;

    public static final int STALL_PHASE1 = 10;
    public static final int STALL_PHASE2 = 30;
    public static final int STALL_PHASE3 = 50;
    
    // --- Debug snapshot state (server) ---
    private net.minecraft.world.phys.Vec3 lastGoalLogPos = null;
    private int lastGoalLogTick = 0;

    public static final int JOB_GOAL_PRIORITY = 7;

    private static final double MOVE_EPSILON_SQ = 0.001 * 0.001; // tiny movement threshold
    
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
    
    private int watchdogGraceTicks = 0;

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

    protected boolean goalsInitialized = false;
    protected boolean goalResetInProgress = false;

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


    /**
     * Gets the underlying animal associated with this pet.
     *
     * @return the underlying animal associated with this pet.
     */
    public P getAnimal()
    {
        return animal;
    }


    /**
     * Gets the name of the animal type associated with this pet.
     *
     * @return the name of the animal type associated with this pet.
     */
    public @Nonnull String getAnimalType()
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
            String dimName = dimension.location().toString();
            compound.putString("dimension", dimName != null ? dimName : "");
            compound.putString(TAG_ANIMAL_TYPE, this.getAnimalType());
            // compound.put("Inventory", inventory.serializeNBT(animal.level().registryAccess()));
        }

        compound.putInt("entityId", this.getEntityId());

        final UUID localUuid = animal.getUUID();
        if (animal != null && localUuid != null)
        {
            compound.putUUID("uuid", localUuid);
        }

        // --- INVENTORY (always save) ---
        if (animal != null && animal.level() != null)
        {
            RegistryAccess registryAccess = animal.level().registryAccess();
            if (registryAccess != null)
            {
                CompoundTag inventoryTag = inventory.serializeNBT(registryAccess);
                if (inventoryTag != null)
                {
                    compound.put(TAG_INV, inventoryTag);
                }
            }
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

            if (level != null) 
            {
                dimension = ResourceKey.create(NullnessBridge.assumeNonnull(Registries.DIMENSION), level);
            }
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
                    if (animal != null && animal.level() != null && invTag != null) 
                    {
                        RegistryAccess registryAccess = animal.level().registryAccess();
                        if (registryAccess != null) 
                        {
                            inventory.deserializeNBT(registryAccess, invTag);
                            applied = true;
                        }
                    }
                } 
                catch (Throwable ignored) 
                { 
                    LOGGER.error("Error deserializing inventory", ignored);
                }
            }

            // If still not applied (no Level yet), keep it for later
            if (!applied && invTag != null) 
            {
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
        
        if (this.animal != null && this.animal.isAlive())
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
        TraceUtils.dynamicTrace(TRACE_PETOTHERGOALS, () -> LOGGER.info("Assigning pet goals for pet {}:", this.animal.getUUID()));

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
            this.getAnimal().goalSelector.addGoal(5, new WalkToWorkPositionGoal<>(this.getAnimal(), workPos, 1.2, 2));
        }

        animal.goalSelector.addGoal(6, new EatFromInventoryHealGoal<P>(animal, 300));

        if (getTrainerBuilding() != null)
        {
            BlockPos trainerPos = getTrainerBuilding().getPosition();
            if (trainerPos != null && !trainerPos.equals(BlockPos.ZERO))
            {
                this.getAnimal().goalSelector.addGoal(30, new ReturnToTrainerAtNightGoal<>(this.getAnimal(), trainerPos));
            }
        }

    }

    // IDEA: Implement scaling by trainer building level.
    // IDEA: Implement research effects.


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
                TraceUtils.dynamicTrace(TRACE_PETHERDGOALS, () -> LOGGER.info("Assigning herding goals for pet {}:", this.animal.getUUID()));
                this.getAnimal().goalSelector.addGoal(JOB_GOAL_PRIORITY, new HerdGoal<P>(this.getAnimal()));
                break;

            case SCAVENGE_LAND:
                TraceUtils.dynamicTrace(TRACE_PETSCAVENGEGOALS, () -> LOGGER.info("Assigning scavenge_land goals for pet {}:", this.animal.getUUID()));
                this.getAnimal().goalSelector.addGoal(JOB_GOAL_PRIORITY, new ScavengeForResourceGoal<>(
                    this.getAnimal(),
                    16,                      // search radius
                    8.0,                     // light level (optional to ignore)
                    0.3f,                    // 30% success rate
                    pos -> {
                        BlockState stateBelow = this.getAnimal().level().getBlockState(NullnessBridge.assumeNonnull(pos.below()));
                        return this.getAnimal().level().getMaxLocalRawBrightness(pos) < 8 &&
                            (stateBelow.is(NullnessBridge.assumeNonnull(BlockTags.DIRT)) || stateBelow.is(NullnessBridge.assumeNonnull(BlockTags.MUSHROOM_GROW_BLOCK))) &&
                            this.getAnimal().level().isEmptyBlock(pos);
                    },
                    pos -> {
                        // Pick a mushroom
                        Block mushroomBlock = this.getAnimal().getRandom().nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM;
                        Item mushroomItem = mushroomBlock.asItem();
                        
                        // Track the stat with item name
                        StatsUtil.trackStatByName(this.getTrainerBuilding(), ScavengeForResourceGoal.ITEMS_SCAVENGED, mushroomItem.getDefaultInstance().getDisplayName(), 1);

                        // Place the mushroom
                        this.getAnimal().level().setBlock(NullnessBridge.assumeNonnull(pos), NullnessBridge.assumeNonnull(mushroomBlock.defaultBlockState()), 3);
                    },
                    1000
                ));
                break;

            case SCAVENGE_WATER:
                TraceUtils.dynamicTrace(TRACE_PETSCAVENGEGOALS, () -> LOGGER.info("Assigning scavenge_water goals for pet {}:", this.animal.getUUID()));
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
     * Called once per tick while the AI is active.
     * 
     * If the AI is not active, it will increment a stall counter and attempt to refresh the AI goals if the stall counter reaches certain thresholds.
     * If the AI is active, it will reset the stall counter.
     * 
     * @see #isAiActiveNow()
     * @see #stallTicks
     */
    public void aiWatchdogTick()
    {
        // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> LOGGER.info("Pet {}: watchdog tick. Grace: {}, Cooldown: {}, Stall: {}", this.animal.getUUID(), watchdogGraceTicks, watchdogCooldown, stallTicks));

        if (watchdogGraceTicks > 0)
        {
            watchdogGraceTicks--;
            return;
        }

        if (watchdogCooldown > 0) 
        { 
            watchdogCooldown--;
            return; 
        }

        watchdogCooldown = WATCHDOWN_COOLDOWN_INTERVAL;

        
        if (this.getAnimal() == null || this.getAnimal().level().isClientSide) return;

        boolean active = isAiActiveNow();

        if (!active && getAnimal().isAlive() && !getAnimal().isPassenger() && !getAnimal().isLeashed() && !getAnimal().isNoAi())
        {
            stallTicks++;
            // (first second): make sure control flags aren’t stuck off
            if (stallTicks == STALL_PHASE1)
            {
                TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("Pet stall tick. Pet {}: checking control flags.", this.animal.getUUID()));
                getAnimal().goalSelector.enableControlFlag(Goal.Flag.MOVE);
                getAnimal().goalSelector.enableControlFlag(Goal.Flag.LOOK);
                getAnimal().targetSelector.enableControlFlag(Goal.Flag.TARGET);
            }
            // (~2s): clear stale path to let goals start fresh
            if (stallTicks == STALL_PHASE2)
            {
                TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("Pet stall tick. Pet  {}: Clear stale path to let goals start fresh.", this.animal.getUUID()));
                getAnimal().getNavigation().stop();
            }
            // (~5s): single soft goal refresh (once)
            if (stallTicks == STALL_PHASE3)
            {
                TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("Pet stall tick. Pet  {}: Resetting goals.", this.animal.getUUID()));
                getAnimal().resetGoals();
            }
        }
        else
        {
            if (stallTicks >= STALL_PHASE1)
            {
                TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("Pet {}: Recovered. Active: {}, getAnimal().isAlive(): {}, getAnimal().isPassenger(): {}, getAnimal().isLeashed(): {}, getAnimal().isNoAi(): {}", 
                    this.animal.getUUID(), active, getAnimal().isAlive(), getAnimal().isPassenger(), getAnimal().isLeashed(), getAnimal().isNoAi()));
            }

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

        if (colony == null) return null;

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
    public List<? extends Animal> searchForCompatibleAnimals(Level level, @Nonnull AABB boundingBox)
    {
        final List<Animal> animals = new ArrayList<>();

        IBuilding workBuilding = this.getBuildingContainingWorkLocation(level);

        if (workBuilding == null) return animals;

        for (final AnimalHerdingModule module : workBuilding.getModules(AnimalHerdingModule.class))
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
    public ItemStackHandler getInventory() 
    {
        return inventory;
    }

    /**
     * Retrieves the role associated with the given block position in the given level.
     *
     * @param level the level that the block position is in
     * @param pos   the block position to retrieve the role for
     * @return the PetRoles value associated with the block at the given position, or null if the block is not a valid work location
     */
    public static PetRoles roleFromPosition(Level level, @Nonnull BlockPos pos) 
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
        final BlockPos localWorkPos = this.workLocation;
        if (localWorkPos == null || localWorkPos.equals(BlockPos.ZERO)) return PetRoles.NONE;

        return roleFromPosition(level, localWorkPos);
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
        if (animal.level().isClientSide) return;
        boolean debugging = TraceUtils.isTracing(TraceUtils.TRACE_PETACTIVEGOAL);

        if (!debugging) return;

        if (logCooldown > 0)
        {
            logCooldown--;
            return;
        }

        if (getAnimal() == null) 
        {
            TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("No pet data while logging active goal in PetData."));
            return;
        }

        logCooldown = LOG_COOLDOWN_INTERVAL;
        boolean goalFound = false;
        int selectorGoalsChecked = 0;
        int targetGoalsChecked = 0;

        for (WrappedGoal wrapped : getAnimal().goalSelector.getAvailableGoals())
        {
            selectorGoalsChecked++;
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                goalFound = true;
                LOGGER.info("Active Goal for pet {}: {}, with tick count {}.", this.getAnimal().getUUID(), goal.getClass().getSimpleName(), animal.tickCount);
            }
        }

        for (WrappedGoal wrapped : getAnimal().targetSelector.getAvailableGoals())
        {
            targetGoalsChecked++;
            Goal goal = wrapped.getGoal();
            if (wrapped.isRunning())
            {
                goalFound = true;
                TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL,
                    () -> LOGGER.info("Active Target Goal for pet {}: {}, with tick count {}." , this.getAnimal().getUUID(), goal.getClass().getSimpleName(), animal.tickCount));
            }
        }

        if (!goalFound)
        {
            LOGGER.info("No active goals for pet {}, tick count {}. {} selector goals checked and {} target goals checked. Is reset in progress? {}. Is nav in progress? {}.",
                this.getAnimal().getUUID(), animal.tickCount, selectorGoalsChecked, targetGoalsChecked, isGoalResetInProgress(), animal.getNavigation().isInProgress());
            logNoGoalDetail();
        }
    }

    /**
     * Logs some extra information about the pet's AI state when no goal is active.
     *
     * This information is useful for debugging why a pet is not doing anything.
     */
    @SuppressWarnings("null")
    private void logNoGoalDetail()
    {
        // --- Compute a few “why would anything start?” signals ---
        final Player nearestPlayer = animal.level().getNearestPlayer(animal, 64.0);
        final double nearestPlayerDist = nearestPlayer == null ? -1.0 : nearestPlayer.distanceTo(animal);

        final double moveSpeed =
            animal.getAttribute(Attributes.MOVEMENT_SPEED) == null ? -1.0 : animal.getAttributeValue(Attributes.MOVEMENT_SPEED);

        final Vec3 pos = animal.position();
        final Vec3 dMove = animal.getDeltaMovement();

        double movedSinceLastLog = -1.0;
        int ticksSinceLastLog = -1;
        if (lastGoalLogPos != null)
        {
            movedSinceLastLog = pos.distanceTo(lastGoalLogPos);
            ticksSinceLastLog = animal.tickCount - lastGoalLogTick;
        }
        lastGoalLogPos = pos;
        lastGoalLogTick = animal.tickCount;

        // --- Try to read GoalSelector disabled flags (reflection-safe) ---
        String disabledFlagsStr = "<unknown>";
        try
        {
            // In many mappings this field is called "disabledFlags" (EnumSet<Goal.Flag>)
            final Field f = animal.goalSelector.getClass().getDeclaredField("disabledFlags");
            f.setAccessible(true);
            final Object v = f.get(animal.goalSelector);
            disabledFlagsStr = String.valueOf(v);
        }
        catch (final Throwable t)
        {
            // ignore; keep <unknown>
        }

        // --- Nav/path details (if any) ---
        String pathStr = "<null>";
        try
        {
            final var path = animal.getNavigation().getPath();
            if (path != null)
            {
                pathStr = "done=" + path.isDone()
                    + " idx=" + path.getNextNodeIndex()
                    + "/" + path.getNodeCount();
            }
        }
        catch (final Throwable t)
        {
            // ignore
        }

        // --- This is the “high-signal” snapshot line ---
        LOGGER.info(
            "[AI-SNAPSHOT] uuid={} tick={} effAi={} noAi={} sittingWolf={} passenger={} leashed={} onGround={} " +
            "navInProg={} path={} moveSpeed={} dMove=({},{},{}) pos=({},{},{}) movedSinceLast={} overTicks={} " +
            "nearestPlayerDist={} hasTarget={} resetInProg={} disabledFlags={}",
            animal.getUUID(),
            animal.tickCount,
            animal.isEffectiveAi(),
            animal.isNoAi(),
            animal instanceof Wolf wolf && (wolf.isOrderedToSit() || wolf.isInSittingPose()),
            animal.isPassenger(),
            animal.isLeashed(),
            animal.onGround(),
            animal.getNavigation().isInProgress(),
            pathStr,
            moveSpeed,
            dMove.x, dMove.y, dMove.z,
            pos.x, pos.y, pos.z,
            movedSinceLastLog,
            ticksSinceLastLog,
            nearestPlayerDist,
            (animal instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() != null),
            isGoalResetInProgress(),
            disabledFlagsStr
        );

        // Dump all selector goals (rate-limited by your existing logCooldown)
        LOGGER.info("[GOAL-DUMP][SEL] uuid={} tick={}", animal.getUUID(), animal.tickCount);
        int i = 0;
        WrappedGoal wanderWrapped = null;

        for (WrappedGoal wrapped : animal.goalSelector.getAvailableGoals())
        {
            final Goal g = wrapped.getGoal();
            final String name = g.getClass().getName();
            final String simple = g.getClass().getSimpleName();

            // Flags are useful: MOVE/LOOK/JUMP/TARGET
            LOGGER.info("[GOAL-DUMP][SEL] #{} running={} flags={} goal={}",
                i++, wrapped.isRunning(), wrapped.getFlags(), simple);

            // Heuristic: find the wander goal
            if (wanderWrapped == null && (name.contains("RandomStroll") || name.contains("WaterAvoidingRandomStroll")))
            {
                wanderWrapped = wrapped;
            }
        }

        LOGGER.info("[GOAL-DUMP][TGT] uuid={} tick={}", animal.getUUID(), animal.tickCount);
        i = 0;
        for (WrappedGoal wrapped : animal.targetSelector.getAvailableGoals())
        {
            final Goal g = wrapped.getGoal();
            LOGGER.info("[GOAL-DUMP][TGT] #{} running={} flags={} goal={}",
                i++, wrapped.isRunning(), wrapped.getFlags(), g.getClass().getSimpleName());
        }

        // Probe wander goal eligibility once
        if (wanderWrapped != null)
        {
            try
            {
                final Goal wg = wanderWrapped.getGoal();
                final boolean can = wg.canUse();
                final boolean canCont = wg.canContinueToUse();
                LOGGER.info("[WANDER-PROBE] uuid={} tick={} goal={} canUse={} canContinue={}",
                    animal.getUUID(), animal.tickCount, wg.getClass().getSimpleName(), can, canCont);
            }
            catch (Throwable t)
            {
                LOGGER.info("[WANDER-PROBE] uuid={} tick={} error calling canUse: {}",
                    animal.getUUID(), animal.tickCount, t.toString());
            }
        }
        else
        {
            LOGGER.info("[WANDER-PROBE] uuid={} tick={} No RandomStroll/WaterAvoidingRandomStroll goal found in selector!",
                animal.getUUID(), animal.tickCount);
        }

        dumpGoalSelectorInternals();
    }

    /**
     * Attempts to dump the internal state of the animal's goal selector, which can help debug AI issues.
     * This is a best-effort attempt and may not work in all mappings.
     * @see #dumpGoalSelector
     */
    private void dumpGoalSelectorInternals()
    {
        try
        {
            // GoalSelector internals vary by mappings, so we do best-effort reflection.
            var sel = getAnimal().goalSelector;

            String disabled = "<unknown>";
            String locked = "<unknown>";
            String running = "<unknown>";

            try
            {
                var f = sel.getClass().getDeclaredField("disabledFlags");
                f.setAccessible(true);
                disabled = String.valueOf(f.get(sel));
            }
            catch (Throwable ignored) {}

            try
            {
                // Often a Map<Goal.Flag, WrappedGoal> or similar
                var f = sel.getClass().getDeclaredField("lockedFlags");
                f.setAccessible(true);
                locked = String.valueOf(f.get(sel));
            }
            catch (Throwable ignored) {}

            try
            {
                // Often a Set<WrappedGoal>
                var f = sel.getClass().getDeclaredField("runningGoals");
                f.setAccessible(true);
                running = String.valueOf(f.get(sel));
            }
            catch (Throwable ignored) {}

            LOGGER.info("[GOAL-SELECTOR-INTERNAL] uuid={} tick={} disabledFlags={} lockedFlags={} runningGoals={}",
                getAnimal().getUUID(), getAnimal().tickCount, disabled, locked, running);

            dumpMoveLockOwner();
        }
        catch (Throwable t)
        {
            LOGGER.info("[GOAL-SELECTOR-INTERNAL] Failed: {}", t.toString());
        }
    }


    /**
     * Attempts to dump the owner of the move lock (if any).
     * This tries to read the lockedFlags map of the goal selector, then extract the Goal and flags associated with the MOVE flag.
     * If the move lock owner is not found, it will log a message indicating this.
     * The goal of this method is to help debug AI issues.
     * @see #dumpGoalSelector
     */
    private void dumpMoveLockOwner()
    {
        final var animal = getAnimal();
        if (animal == null) return;

        try
        {
            final Object sel = animal.goalSelector;

            // 1) Read lockedFlags map
            Field lockedField = null;
            for (Field f : sel.getClass().getDeclaredFields())
            {
                if (f.getName().toLowerCase().contains("locked"))
                {
                    lockedField = f;
                    break;
                }
            }
            if (lockedField == null)
            {
                LOGGER.info("[MOVE-LOCK] uuid={} tick={} lockedFlags field not found", animal.getUUID(), animal.tickCount);
                return;
            }

            lockedField.setAccessible(true);
            final Object lockedObj = lockedField.get(sel);
            if (!(lockedObj instanceof java.util.Map<?, ?> lockedMap))
            {
                LOGGER.info("[MOVE-LOCK] uuid={} tick={} lockedFlags not a Map: {}", animal.getUUID(), animal.tickCount, lockedObj);
                return;
            }

            final Object wrapped = lockedMap.get(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
            if (wrapped == null)
            {
                LOGGER.info("[MOVE-LOCK] uuid={} tick={} MOVE not locked", animal.getUUID(), animal.tickCount);
                return;
            }

            // 2) Extract Goal from WrappedGoal
            Goal goal = null;
            try
            {
                // Prefer method if present
                var m = wrapped.getClass().getMethod("getGoal");
                goal = (Goal) m.invoke(wrapped);
            }
            catch (Throwable ignored)
            {
                // Try field named "goal"
                for (Field f : wrapped.getClass().getDeclaredFields())
                {
                    if (Goal.class.isAssignableFrom(f.getType()))
                    {
                        f.setAccessible(true);
                        goal = (Goal) f.get(wrapped);
                        break;
                    }
                }
            }

            // 3) Extract flags / running from WrappedGoal
            Object flags = "<unknown>";
            Object running = "<unknown>";
            Object priority = "<unknown>";

            try { flags = wrapped.getClass().getMethod("getFlags").invoke(wrapped); } catch (Throwable ignored) {}
            try { running = wrapped.getClass().getMethod("isRunning").invoke(wrapped); } catch (Throwable ignored) {}

            // Priority is sometimes stored on WrappedGoal as an int field or getPriority()
            try { priority = wrapped.getClass().getMethod("getPriority").invoke(wrapped); } catch (Throwable ignored) {}
            if ("<unknown>".equals(priority))
            {
                for (Field f : wrapped.getClass().getDeclaredFields())
                {
                    if (f.getType() == int.class && f.getName().toLowerCase().contains("priority"))
                    {
                        f.setAccessible(true);
                        priority = f.getInt(wrapped);
                        break;
                    }
                }
            }

            LOGGER.info("[MOVE-LOCK] uuid={} tick={} ownerWrappedClass={} ownerGoal={} priority={} running={} flags={}",
                animal.getUUID(),
                animal.tickCount,
                wrapped.getClass().getName(),
                goal == null ? "<unknown>" : goal.getClass().getSimpleName(),
                priority,
                running,
                flags
            );
        }
        catch (Throwable t)
        {
            LOGGER.info("[MOVE-LOCK] uuid={} tick={} failed: {}", animal.getUUID(), animal.tickCount, t.toString());
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
    
    /**
     * Returns whether the pet is currently in the process of resetting its goals.
     * This is used to prevent the pet from attempting to reset its goals while it is already in the process of doing so.
     *
     * @return true if the pet is currently resetting its goals, false otherwise
     */
    public boolean isGoalResetInProgress() 
    { 
        return goalResetInProgress; 
    }



    /**
     * Sets whether the pet is currently in the process of resetting its goals.
     * This flag is used to prevent the pet from attempting to reset its goals while it is already in the process of doing so.
     * @param goalResetInProgress true if the pet is currently resetting its goals, false otherwise
     */
    public void setGoalResetInProgress(boolean goalResetInProgress) 
    { 
        this.goalResetInProgress = goalResetInProgress; 
    }

    /**
     * Returns whether the pet's goals have been initialized.
     * This should be set to true after the pet's goals have been registered.
     * 
     * @return true if the pet's goals have been initialized, false otherwise
     */
    public boolean areGoalsInitialized() 
    { 
        return goalsInitialized; 
    }


    /**
     * Sets whether the pet's goals have been initialized.
     * This should be set to true after the pet's goals have been registered.
     * 
     * @param intialized true if the pet's goals have been initialized, false otherwise
     */
    public void setGoalsInitialized(boolean intialized) 
    { 
        this.goalsInitialized = intialized; 
    }

    /**
     * Resets the state of this pet's AI goals and targets, by clearing all existing goals and targets and re-registering them.
     * A change of work location may necessitate a change of goals. The goals and targets are only registered once the pet has a valid
     * colony context, which is not available until the pet is loaded into a world.
     *
     * This function is idempotent: calling it multiple times will have the same effect as calling it once.
     *
     * @see #stopAllSelectorGoalsSafely
     */
    public void resetGoals()
    {

        if (animal == null)
        {
            return;
        }

        watchdogGraceTicks = 60; // 3 seconds
        stallTicks = 0;

        try
        {
            TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("Resetting pet goals for pet {}:", this.animal.getUUID()));

            animal.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            animal.goalSelector.enableControlFlag(Goal.Flag.LOOK);
            animal.goalSelector.enableControlFlag(Goal.Flag.JUMP);
            animal.targetSelector.enableControlFlag(Goal.Flag.TARGET);

            // Hard-stop movement/navigation FIRST so "isInProgress()" deadlocks don't persist.
            animal.getNavigation().stop();
            animal.getMoveControl().setWantedPosition(animal.getX(), animal.getY(), animal.getZ(), 0.0); // clear wanted movement
            animal.setDeltaMovement(NullnessBridge.assumeNonnull(Vec3.ZERO));
            animal.hurtMarked = true; // ensure motion sync

            // Stop any currently running goals cleanly before removing them.
            stopAllSelectorGoalsSafely(animal.goalSelector);
            stopAllSelectorGoalsSafely(animal.targetSelector);

            // Clear everything.
            animal.goalSelector.removeAllGoals(g -> true);
            animal.targetSelector.removeAllGoals(g -> true);

            // Reset misc AI state that can carry across goal sets.
            animal.setTarget(null);
        }
        catch (Exception e)
        {
            LOGGER.error("Failed to reset pet goals for pet {}", this.animal.getUUID(), e);
        }
        finally
        {
            // No Op
        }
    }

    /**
     * Best-effort: call stop() on goals that are currently running, then clear navigation again.
     * This prevents "removed goal still thinks it owns MOVE flag" style lockups.
     */
    private void stopAllSelectorGoalsSafely(final GoalSelector selector)
    {

        if (animal == null)
        {
            return;
        }

        try
        {

            for (WrappedGoal wrapped : selector.getAvailableGoals())
            {
                final Goal goal = wrapped.getGoal();
                if (goal == null) continue;

                // Only stop goals that are actually running, if that info is accessible.
                // WrappedGoal typically has isRunning(); if not, calling stop() is still usually safe.
                if (wrapped.isRunning())
                {
                    try
                    {
                        TraceUtils.dynamicTrace(TRACE_PETACTIVEGOAL, () -> LOGGER.info("{}: Stopping goal {}.", this.animal.getUUID(), goal.getClass().getSimpleName()));
                        wrapped.stop();
                    }
                    catch (Exception ignored)
                    {
                        // Goal stop should never bring down the entity tick.
                        Log.getLogger().error("Failed to stop goal", ignored);
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
            // Some mappings don't expose getAvailableGoals() / WrappedGoal.
            // In that case, we at least rely on navigation stop + selector removal.
            Log.getLogger().error("Error stopping all selector goals.", ignored);
        }

        // Re-stop navigation after stopping goals, because some stop() methods call moveTo().
        animal.getNavigation().stop();
    }

    /**
     * Checks if the pet is currently active (not stalled) by checking for various signs of AI activity.
     * This includes:
     * <ol>
     * <li>Any goal or target goal currently running</li>
     * <li>Navigation state (more robust than isDone())</li>
     * <li>Physical movement (covers knockback, nudges, falling, etc.)</li>
     * <li>Intent without motion (very important)</li>
     * <li>"Eligible to act": there exists *any* MOVE goal</li>
     * </ol>
     *
     * @return true if the pet is currently active, false otherwise
     */
    private boolean isAiActiveNow()
    {
        final P a = this.getAnimal();
        if (a == null) return false;

        // If AI is disabled, treat as active (not stalled)
        if (a.isNoAi()) return true;

        // 1) Any goal or target goal currently running?
        boolean goalRunning = a.goalSelector.getAvailableGoals().stream()
            .anyMatch(WrappedGoal::isRunning);

        boolean targetRunning = a.targetSelector.getAvailableGoals().stream()
            .anyMatch(WrappedGoal::isRunning);

        // 2) Navigation state (more robust than isDone())
        final PathNavigation nav = a.getNavigation();
        final Path path = nav.getPath();

        boolean navBusy =
            nav.isInProgress()
            || (path != null && !path.isDone());

        // 3) Physical movement (covers knockback, nudges, falling, etc.)
        Vec3 dm = a.getDeltaMovement();

        // horizontal-only motion
        boolean horizMoving = (dm.x * dm.x + dm.z * dm.z) > MOVE_EPSILON_SQ;

        // count vertical only if not grounded (falling / jumping / swimming)
        boolean vertMoving = !a.onGround() && (dm.y * dm.y) > MOVE_EPSILON_SQ;

        boolean physicallyMoving = horizMoving || vertMoving;

        // 4) Intent without motion (very important)
        boolean hasAttackTarget = a.getTarget() != null;

        // 5) “Eligible to act”: there exists *any* MOVE goal
        boolean hasMoveGoals =
            a.goalSelector.getAvailableGoals().stream()
                .map(WrappedGoal::getGoal)
                .anyMatch(g -> g != null && g.getFlags() != null && g.getFlags().contains(Goal.Flag.MOVE));

        boolean hasAnyGoals = !a.goalSelector.getAvailableGoals().isEmpty();
        boolean eligibleButIdle = hasAnyGoals && hasMoveGoals;

        boolean isActive = goalRunning || targetRunning || navBusy || physicallyMoving || hasAttackTarget
            || (eligibleButIdle && watchdogGraceTicks > 0); // only suppress for the first second

        // TraceUtils.dynamicTrace(TRACE_PETGOALS, () -> 
        //     LOGGER.info("Pet {}: isAiActiveNow({}). goalRunning: {}, targetRunning: {}, navBusy: {}, physicallyMoving({}): {}, hasAttackTarget: {}, hasMoveGoals: {}, hasAnyGoals: {}, eligibleButIdle: {}, stallTicks: {}",
        //     this.animal.getUUID(), isActive, goalRunning, targetRunning, navBusy, dm, physicallyMoving, hasAttackTarget, hasMoveGoals, hasAnyGoals, eligibleButIdle, stallTicks));

        return isActive;

    }

    /** 
     * Call from the entity’s server tick once level/registry are available. 
     */
    public void tryApplyPendingInventory(Level level)
    {
        if (pendingInv == null || level == null || level.isClientSide) return;

        try 
        {
            RegistryAccess access = level.registryAccess();
            final CompoundTag localPendingInv = pendingInv;
            
            if (access == null || localPendingInv == null) return;

            inventory.deserializeNBT(access, localPendingInv);
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

        boolean debugging = TraceUtils.isTracing(TraceUtils.TRACE_PETACTIVEGOAL);

        if (debugging)
        {
            String idText = this.getAnimal().getUUID().toString();
            String job = this.roleFromWorkLocation(this.getAnimal().level()).toString();

            Goal goal = getActiveGoal();
            String goalName = null;

            if (goal != null)
            {
                lastActiveGoal = goal.getClass().getSimpleName();
                goalName = " - " + lastActiveGoal;
            }

            this.getAnimal().setCustomName(Component.literal(idText + " Role: " + job + " (" + goalName + ")"));
            this.getAnimal().setCustomNameVisible(true);
        }
        else
        {
            // In normal gameplay: restore the true saved name
            this.getAnimal().setCustomName(this.originalName);
            this.getAnimal().setCustomNameVisible(false);
        }
    }


    /**
     * Called when the pet dies, to drop all the items in its inventory.
     * This is called before the animal is removed from the world.
     * @param level the level the pet is in
     * @param source the damage source for the pet's death
     * @param recentlyHit whether the pet was recently hit (true if just killed)
     */
    public void onDropCustomDeathLoot (@Nonnull ServerLevel level, @Nonnull DamageSource source, boolean recentlyHit)
    {

        // Drop the pet’s inventory
        ItemStackHandler inv = this.getInventory();
        if (inv == null) return;

        for (int i = 0; i < inv.getSlots(); i++)
        {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                ItemStack dropStack = stack.copy();

                if (dropStack != null && !dropStack.isEmpty())
                {
                    Containers.dropItemStack(level, animal.getX(), animal.getY(), animal.getZ(), dropStack);
                    inv.setStackInSlot(i, NullnessBridge.assumeNonnull(ItemStack.EMPTY)); // prevent dupes
                }
            }
        }
    }
}
