package com.deathfrog.mctradepost.api.entity.pets;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

public class  PetData<P extends Animal & ITradePostPet & IHerdingPet> 
{
    public static final Logger LOGGER = LogUtils.getLogger();

    // After how many nudges without moving do we give the Sheep a pathfinding command to unstick themselves?
    public static final int STUCK_STEPS = 10;   
    public static final String TAG_ANIMAL_TYPE = "animalType";
    public static final String PET_TYPE_WOLF = "Wolf";
    public static final String PET_TYPE_FOX = "Fox";
    public static final String PET_TYPE_AXOLOTL = "Axolotl";

    protected P animal;
    protected int colonyId;
    protected BlockPos trainerBuildingID = BlockPos.ZERO;
    protected BlockPos workLocation = BlockPos.ZERO;
    protected String animalType;
    protected int entityId;
    private ResourceKey<Level> dimension = null;
    private final ItemStackHandler inventory = new ItemStackHandler(9);

    public PetData(P animal)
    {
        this.animal = animal;

        if (animal != null) {
            if (animal instanceof PetWolf)
            {
                animalType = PET_TYPE_WOLF;
            }
            else if (animal instanceof PetFox)
            {
                animalType = PET_TYPE_FOX;
            }
            else if (animal instanceof PetAxolotl)
            {
                animalType = PET_TYPE_AXOLOTL;
            }
            else
            {
                animalType = "unrecognized";
            }
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


    public String getAnimalType()
    {
        return animalType;
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
            compound.putInt("entityId", this.getEntityId());
            compound.put("Inventory", inventory.serializeNBT(animal.level().registryAccess()));
        }

        // MCTradePostMod.LOGGER.info("Serialized PetData to NBT: {}", compound);
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
        animalType = compound.getString("animalType");
        entityId = compound.getInt("entityId");

        String dimname = compound.getString("dimension");
        ResourceLocation level = ResourceLocation.parse(dimname);
        dimension = ResourceKey.create(Registries.DIMENSION, level);

        if (animal != null)
        {
            inventory.deserializeNBT(animal.level().registryAccess(), compound.getCompound("Inventory"));
        }
    }

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

    public void workLocationChanged()
    {
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Work location changed to: {}" + this.workLocation));
        
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
        animal.goalSelector.addGoal(2, new OpenGateOrDoorGoal(animal, true, 10));
        animal.goalSelector.addGoal(3, new UnloadInventoryToWorkLocationGoal<>(animal, 0.8f));
        animal.getNavigation().getNodeEvaluator().setCanOpenDoors(true);

        BlockPos workPos = getWorkLocation();
        if (workPos != null && !workPos.equals(BlockPos.ZERO))
        {
            this.getAnimal().goalSelector.addGoal(5, new WalkToWorkPositionGoal(this.getAnimal(), getWorkLocation(), 1.2, 20.0));
        }

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
                this.getAnimal().goalSelector.addGoal(3, new HerdGoal<P>(this.getAnimal()));
                break;

            case SCAVENGE_LAND:
                this.getAnimal().goalSelector.addGoal(4, new ScavengeForResourceGoal<>(
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
                this.getAnimal().goalSelector.addGoal(6, new ScavengeWaterResourceGoal<>(
                    this.getAnimal(), 
                    16,
                    0.05f, // Chance per try; there are 10 tries per cooldown cycle.
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

        for (final AnimalHerdingModule module : workBuilding.getModulesByType(AnimalHerdingModule.class))
        {
            animals.addAll(workBuilding.getColony()
                .getWorld()
                .getEntitiesOfClass(Animal.class, boundingBox, animal -> module.isCompatible(animal) && !animal.isFullyFrozen()));
        }

        return animals;
    }

    /**
     * Retrieves the unique entity ID of this pet.
     *
     * @return the integer ID of the animal entity associated with this pet.
     */
    public int getEntityId()
    {
        if (animal == null) return entityId;

        return animal.getId();
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
}
