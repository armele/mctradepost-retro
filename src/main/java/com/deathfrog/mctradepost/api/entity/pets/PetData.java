package com.deathfrog.mctradepost.api.entity.pets;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.deathfrog.mctradepost.core.blocks.BlockScavenge;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PetData 
{
    // After how many nudges without moving do we give the Sheep a pathfinding command to unstick themselves?
    public static final int STUCK_STEPS = 10;   
    
    protected Animal animal;
    protected int colonyId;
    protected BlockPos trainerBuildingID = BlockPos.ZERO;
    protected BlockPos workLocation = BlockPos.ZERO;
    protected String animalType;
    protected int entityId;
    private ResourceKey<Level> dimension = null;
    private final ItemStackHandler inventory = new ItemStackHandler(9);

    public PetData(Animal animal)
    {
        this.animal = animal;

        if (animal != null) {
            if (animal instanceof PetWolf)
            {
                animalType = "Wolf";
            }
            else if (animal instanceof PetFox)
            {
                animalType = "Fox";
            }
            else
            {
                animalType = "unrecognized";
            }
        }

    }

    public PetData(Animal animal, CompoundTag compound) 
    {   
        this(animal);
        fromNBT(compound);
    }

    public Animal getAnimal()
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
            compound.putString("animalType", this.getAnimalType());
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
        this.workLocation = workLocation;
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
            return PetRoles.SCAVENGING;
        }

        return null;
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
        return roleFromPosition(level, this.workLocation);
    }
}
