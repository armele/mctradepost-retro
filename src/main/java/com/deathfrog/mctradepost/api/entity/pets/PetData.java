package com.deathfrog.mctradepost.api.entity.pets;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;

public class PetData 
{
    protected Animal animal;
    protected int colonyId;
    protected IBuilding trainerBuilding;
    protected IBuilding workBuilding;
    protected String animalType;
    protected int entityId;

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
            compound.put("trainerBuilding",BuildingUtil.uniqueBuildingNBT(trainerBuilding));
            compound.put("workBuilding",BuildingUtil.uniqueBuildingNBT(workBuilding));
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
        // MCTradePostMod.LOGGER.info("Deserializing PetData from NBT: {}", compound);
        CompoundTag trainerBuildingTag = compound.getCompound("trainerBuilding");

        IBuilding trainerBuilding = BuildingUtil.buildingFromNBT(trainerBuildingTag);
        if (trainerBuilding != null)
        {
            animalType = compound.getString("animalType");
            entityId = compound.getInt("entityId");
            this.setTrainerBuilding(trainerBuilding);
        }

        CompoundTag workBuildingTag = compound.getCompound("workBuilding");
        this.workBuilding = BuildingUtil.buildingFromNBT(workBuildingTag);

        // MCTradePostMod.LOGGER.info("Work building post-deserialization: {}", this.workBuilding);

    }

    public IBuilding getTrainerBuilding()
    {
        return trainerBuilding;
    }

    public void setTrainerBuilding(IBuilding building)
    {
        // MCTradePostMod.LOGGER.info("Setting trainer building for pet {} to {}", this, building);
        this.trainerBuilding = building;
    }

    public IBuilding getWorkBuilding()
    {
        return workBuilding;
    }

    public void setWorkBuilding(IBuilding building)
    {
        MCTradePostMod.LOGGER.info("Setting work building for pet {} to {}", this, building);
        this.workBuilding = building;
    }


    /**
     * Retrieves a list of compatible animals within the given bounding box.
     *
     * @param boundingBox the area to search for compatible animals.
     * @return a list of animals that are compatible with one or more of the AnimalHerdingModules associated with this pet's work building.
     */
    public List<? extends Animal> searchForCompatibleAnimals(AABB boundingBox)
    {
        final List<Animal> animals =  new ArrayList<>();

        for (final AnimalHerdingModule module : workBuilding.getModulesByType(AnimalHerdingModule.class))
        {
            animals.addAll(workBuilding.getColony().getWorld().getEntitiesOfClass(Animal.class, boundingBox, module::isCompatible));
            
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
    
}
