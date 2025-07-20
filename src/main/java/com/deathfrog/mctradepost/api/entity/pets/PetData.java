package com.deathfrog.mctradepost.api.entity.pets;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
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
import net.minecraft.world.phys.AABB;

public class PetData 
{
    protected Animal animal;
    protected int colonyId;
    protected BlockPos trainerBuildingID = BlockPos.ZERO;
    protected BlockPos workBuildingID = BlockPos.ZERO;
    protected String animalType;
    protected int entityId;
    private ResourceKey<Level> dimension = null;
    
    public PetData(Animal animal)
    {
        this.animal = animal;

        if (animal != null) {
            if (animal instanceof PetWolf)
            {
                    animalType = "Wolf";
            }
            else
            {
                animalType = "unrecognized";
            }
        }
        else 
        {
            animalType = "unknown";
        }

    }

    public PetData(Animal animal, CompoundTag compound) 
    {   
        this(animal);
        fromNBT(compound);
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
            BlockPosUtil.write(compound,"workBuilding", workBuildingID);
            compound.putString("dimension", dimension.location().toString());
            compound.putString("animalType", this.getAnimalType());
            compound.putInt("entityId", this.getEntityId());
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

        workBuildingID = BlockPosUtil.read(compound, "workBuilding");
        animalType = compound.getString("animalType");
        entityId = compound.getInt("entityId");

        String dimname = compound.getString("dimension");
        ResourceLocation level = ResourceLocation.parse(dimname);
        dimension = ResourceKey.create(Registries.DIMENSION, level);
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

    public BlockPos getWorkBuildingID()
    {
        return workBuildingID;
    }

    public IBuilding getWorkBuilding()
    {
        return BuildingUtil.buildingFromDimPos(dimension, workBuildingID);
    }

    public IBuildingView getWorkBuildingView()
    {
        return BuildingUtil.buildingViewFromDimPos(dimension, workBuildingID);
    }

    public void setWorkBuilding(IBuilding building)
    {
        if (building == null)
        {
            workBuildingID = BlockPos.ZERO;
            return;
        }
        this.workBuildingID = building.getID();
    }


    /**
     * Retrieves a list of compatible animals within the given bounding box.
     *
     * @param boundingBox the area to search for compatible animals.
     * @return a list of animals that are compatible with one or more of the AnimalHerdingModules associated with this pet's work
     *         building.
     */
    public List<? extends Animal> searchForCompatibleAnimals(AABB boundingBox)
    {
        final List<Animal> animals = new ArrayList<>();

        IBuilding work = this.getWorkBuilding();
        for (final AnimalHerdingModule module : work.getModulesByType(AnimalHerdingModule.class))
        {
            animals.addAll(work.getColony()
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
 * TODO: If animals and roles become loosely coupled (as desired), refactor this.
 * Determines the role of the pet based on its type.
 *
 * @return a string representing the role of the pet.
 */

    public String getRole() {
        String role = "Unassigned";

        switch (animalType) {
            case "Wolf":
                role = "Herding";
                break;
            default:
                role = "Unknown";
        }

        return role;
    }
    
}
