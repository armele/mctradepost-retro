package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;

public class PetRegistryUtil 
{
    protected static final Map<IBuilding, List<ITradePostPet>> globalPetRegistry = new HashMap<>();
    
    /**
     * Determines if a pet is registered in the global pet registry.
     * @param pet the pet to check.
     * @return true if the pet is registered, false otherwise.
     */
    public static final boolean isRegistered(ITradePostPet pet) 
    {
        List<ITradePostPet> pets = globalPetRegistry.get(pet.getTrainerBuilding());
        return pets != null && pets.contains(pet);  
    }
    
    /**
     * Clears the global pet registry of all entries.
     */
    public static final void clear() 
    {
        globalPetRegistry.clear();
    }

    /**
     * Clears the global pet registry of all pets associated with the given building.
     * @param building the building whose associated pets should be cleared from the registry.
     */
    public static final void clear(IBuilding building) 
    {
        globalPetRegistry.remove(building);
    }

    /**
     * Removes a pet from the global pet registry. If the pet is not in the registry, does nothing.
     * @param pet the pet to remove from the registry.
     */
    public static final void unregister(@Nonnull ITradePostPet pet)
    {
        List<ITradePostPet> pets = globalPetRegistry.get(pet.getTrainerBuilding());

        if (pets == null) 
        {
            return;
        }

        pets.remove(pet);
    }


    /**
     * Registers a pet in the global pet registry. If the pet is already
     * registered, does nothing and returns false. If the pet is not registered,
     * adds it to the registry and returns true.
     * @param pet the pet to register in the registry.
     * @return true if the pet was successfully registered, false if it was already
     *         registered.
     */
    public static final boolean register(@Nonnull ITradePostPet pet) throws IllegalArgumentException
    {
        boolean newRegistration = false;

        if (pet.getTrainerBuilding() == null)
        {
            throw new IllegalArgumentException("Pet must have a trainer building.");
        }

        List<ITradePostPet> pets = globalPetRegistry.get(pet.getTrainerBuilding());

        if (pets == null) 
        {
            pets = new ArrayList<>(); 
            globalPetRegistry.put(pet.getTrainerBuilding(), pets);
        }
        
        if (!pets.contains(pet))
        {
            MCTradePostMod.LOGGER.info("Registering pet: {} to building {}", pet, pet.getTrainerBuilding());
            pets.add(pet);
            newRegistration = true;
        }
        
        return newRegistration;
    }

    /**
     * Retrieves all pets in the given colony by iterating over all buildings in the
     * colony and retrieving the list of pets associated with each building from the
     * global pet registry. If a building does not have any associated pets, it is
     * skipped.
     *
     * @param colony the colony to retrieve the list of pets from.
     * @return the list of all ITradePostPet entities in the colony.
     */
    public static final List<ITradePostPet> getPetsInColony(IColony colony) 
    {
        List<ITradePostPet> pets = new ArrayList<>(); 
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) 
        {
            List<ITradePostPet> buildingPets = globalPetRegistry.get(building);
            if (buildingPets != null) 
            {
                pets.addAll(buildingPets);
            }
        }
        return pets;
    }

    /**
     * Retrieves the list of pets associated with the given building from the global pet registry.
     * If the building does not have any associated pets, returns an empty list.
     *
     * @param building the building to retrieve the list of pets from.
     * @return the list of ITradePostPet entities associated with the given building, or an empty list if none.
     */
    public static final List<ITradePostPet> getPetsInBuilding(IBuilding building) 
    {
        List<ITradePostPet> pets = globalPetRegistry.get(building);

        if (pets == null) {
            pets = new ArrayList<>();
            globalPetRegistry.put(building, pets);
        }

        return pets;
    }

}
