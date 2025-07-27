package com.deathfrog.mctradepost.api.util;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nonnull;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.google.common.collect.ImmutableList;
import com.jcraft.jorbis.Block;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.Colony;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public class PetRegistryUtil 
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    protected static final ConcurrentHashMap<IBuilding, Queue<ITradePostPet>> globalPetRegistry = new ConcurrentHashMap<>();
    private static final Map<IColony, Set<BlockPos>> petWorkLocations = new WeakHashMap<>();

    /**
     * Determines if a pet is registered in the global pet registry.
     * @param pet the pet to check.
     * @return true if the pet is registered, false otherwise.
     */
    public static final boolean isRegistered(ITradePostPet pet) 
    {
        ImmutableList<ITradePostPet> pets = ImmutableList.copyOf(getPetsInBuilding(pet.getTrainerBuilding()));
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
        Queue<ITradePostPet> pets = safePetsForBuilding(pet.getTrainerBuilding());
        
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Unregistering pet: {} to building {}", pet, pet.getTrainerBuilding()));

        pets.remove(pet);
    }


    /**
     * Retrieves the queue of pets associated with the given building from the global pet registry.
     * If no pets are associated with the building, initializes an empty queue and adds it to the registry.
     *
     * @param building the building to retrieve or initialize the queue of pets for.
     * @return the queue of ITradePostPet entities associated with the given building.
     */
    protected static Queue<ITradePostPet> safePetsForBuilding(IBuilding building)
    {
        Queue<ITradePostPet> pets = globalPetRegistry.get(building);

        if (pets == null) 
        {
            pets = new ConcurrentLinkedQueue<>(); 
            globalPetRegistry.put(building, pets);
        }

        return pets;
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

        Queue<ITradePostPet> pets = safePetsForBuilding(pet.getTrainerBuilding());

        if (!pets.contains(pet))
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Registering pet: {} to building {}", pet, pet.getTrainerBuilding()));
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
    public static final ImmutableList<ITradePostPet> getPetsInColony(IColony colony) 
    {
        List<ITradePostPet> pets = new ArrayList<>(); 
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) 
        {
            Queue<ITradePostPet> buildingPets = globalPetRegistry.get(building);
            if (buildingPets != null) 
            {
                pets.addAll(buildingPets);
            }
        }

        ImmutableList<ITradePostPet> returnPets = ImmutableList.copyOf(pets);
        return returnPets;
    }

    /**
     * Retrieves the list of pets associated with the given building from the global pet registry.
     * If the building does not have any associated pets, returns an empty list.
     *
     * @param building the building to retrieve the list of pets from.
     * @return the list of ITradePostPet entities associated with the given building, or an empty list if none.
     */
    public static final ImmutableList<ITradePostPet> getPetsInBuilding(IBuilding building) 
    {
        Queue<ITradePostPet> pets = safePetsForBuilding(building);

        ImmutableList<ITradePostPet> returnPets = ImmutableList.copyOf(pets);

        return returnPets;
    }

    /**
     * Registers a BlockPos as a work location for a pet in the given colony.
     * This is used to determine if a pet can be assigned to a building.
     * @param colony the colony to register the work location for.
     * @param pos the BlockPos to register as a work location.
     */
    public static void registerWorkLocation(IColony colony, BlockPos pos) {
        if (colony == null || pos == null || BlockPos.ZERO.equals(pos)) {
            return;
        }
        petWorkLocations.computeIfAbsent(colony, c -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    /**
     * Removes a BlockPos as a work location for a pet in the given colony.
     * If the BlockPos was not registered as a work location, does nothing.
     * @param colony the colony to unregister the work location for.
     * @param pos the BlockPos to unregister as a work location.
     */
    public static void unregisterWorkLocation(IColony colony, BlockPos pos) {
        Set<BlockPos> positions = petWorkLocations.get(colony);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                petWorkLocations.remove(colony);
            }
        }
    }

    /**
     * Retrieves the set of all BlockPos that are valid work locations for a pet in the given colony.
     * A BlockPos is a valid work location if it has been previously registered as such by calling
     * {@link #registerWorkLocation(Colony, BlockPos)}. If no BlockPos have been registered, an empty
     * set is returned.
     * @param colony the colony to retrieve the set of work locations for.
     * @return a set of BlockPos that are valid work locations for a pet in the given colony.
     */
    public static Set<BlockPos> getWorkLocations(IColony colony) {
        return petWorkLocations.getOrDefault(colony, Set.of());
    }

    /**
     * Retrieves the map of all colonies to their valid work locations for pets.
     * The keys of the map are the colonies, and the values are the sets of BlockPos
     * that are valid work locations for pets in that colony.
     *
     * @return a map of all colonies to their valid work locations for pets.
     */
    public static final Map<IColony, Set<BlockPos>> getPetWorkLocations() 
    {
        return petWorkLocations;
    }

    public static void loadPetWorkLocations(IColony colony, CompoundTag tag) {
        ListTag posList = tag.getList("petWorkLocations", Tag.TAG_COMPOUND);

        Set<BlockPos> positions = new HashSet<>();  
        for (Tag posTagRaw : posList) {
            CompoundTag posTag = (CompoundTag) posTagRaw;
            BlockPos pos = BlockPosUtil.readFromNBT(posTag, "WorkLocation");
            PetRegistryUtil.registerWorkLocation(colony, pos);
        }
    }

    /**
     * Saves the set of all BlockPos that are valid work locations for a pet in the given colony
     * to the provided CompoundTag. 
     * @param tag the CompoundTag to save the data to.
     * @return the CompoundTag with the data saved.
     */
    public static CompoundTag savePetWorkLocations(IColony colony, CompoundTag tag) {
        CompoundTag colonyTag = new CompoundTag();

        ListTag posList = new ListTag();
        for (BlockPos pos : getWorkLocations(colony)) {
            CompoundTag posTag = new CompoundTag();
            BlockPosUtil.writeToNBT(posTag, "WorkLocation", pos);
            posList.add(posTag);
        }

        tag.put("petWorkLocations", posList);

        return tag;
    }


}
