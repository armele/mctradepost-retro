package com.deathfrog.mctradepost.api.util;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.core.blocks.AbstractBlockPetWorkingLocation;
import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.core.colony.Colony;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;


public class PetRegistryUtil
{
    public static final Logger LOGGER = LogUtils.getLogger();

    protected static final ConcurrentHashMap<IBuilding, Queue<PetHandle>> globalPetRegistry = new ConcurrentHashMap<>();
    private static final Map<IColony, Set<BlockPos>> petWorkLocations = new WeakHashMap<>();

    /**
     * Resolve a pet in the global registry to its actual entity object on the given server.
     *
     * @param level the level to resolve the pet on
     * @param uuid  the uuid of the pet to resolve
     * @return the entity object of the pet, or null if the pet does not exist on the given level
     */
    public static @Nullable ITradePostPet resolve(ServerLevel level, UUID uuid)
    {
        Entity e = level.getEntity(uuid);
        return (e instanceof ITradePostPet p) ? p : null;
    }

    /**
     * Resolve a pet in the global registry to its actual entity object on the given server.
     * @param server the server to resolve the pet on
     * @param h the handle of the pet to resolve
     * @return the resolved pet entity, or null if the pet is not registered or the server has no level with the given dimension
     */
    public static @Nullable ITradePostPet resolve(MinecraftServer server, PetHandle h)
    {
        ServerLevel lvl = server.getLevel(h.dimension());
        if (lvl == null) return null;
        return resolve(lvl, h.uuid());
    }

    /**
     * Checks if the given ITradePostPet is currently registered in the global pet registry. This method
     * is thread-safe and can be called from any thread.
     *
     * @param pet the pet to check
     * @return true if the pet is registered, false otherwise
     */
    public static boolean isRegistered(ITradePostPet pet)
    {
        IBuilding b = pet.getTrainerBuilding();
        if (pet == null || b == null) return false;
        return safePetsForBuilding(b).stream().anyMatch(h -> h.uuid().equals(pet.getUUID()));
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
     * 
     * @param building the building whose associated pets should be cleared from the registry.
     */
    public static final void clear(IBuilding building)
    {
        globalPetRegistry.remove(building);
    }

    /**
     * Unregisters the given pet from the global pet registry, and removes it from the associated building's list of pets.
     * If the pet is not registered, this method has no effect.
     *
     * @param pet the pet to unregister.
     */
    public static void unregister(@Nonnull ITradePostPet pet)
    {
        IBuilding b = pet.getTrainerBuilding();
        if (b == null) return;
        Queue<PetHandle> q = safePetsForBuilding(b);
        UUID id = pet.getUUID();
        q.removeIf(h -> h.uuid().equals(id));
        TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Unregister pet {} from {}", id, b));
    }

    /**
     * Retrieves the queue of pets associated with the given building from the global pet registry. If no pets are associated with the
     * building, initializes an empty queue and adds it to the registry.
     *
     * @param building the building to retrieve or initialize the queue of pets for.
     * @return the queue of ITradePostPet entities associated with the given building.
     */
    protected static Queue<PetHandle> safePetsForBuilding(IBuilding building)
    {
        Queue<PetHandle> pets = globalPetRegistry.get(building);

        if (pets == null)
        {
            pets = new ConcurrentLinkedQueue<>();
            globalPetRegistry.put(building, pets);
        }

        return pets;
    }

    /**
     * Registers the given ITradePostPet with the global pet registry under its associated BuildingPetshop. If the pet is already
     * registered, this method has no effect and returns false.
     *
     * @param pet the pet to be registered.
     * @return true if the pet was registered successfully, false otherwise.
     */
    public static boolean register(@Nonnull ITradePostPet pet)
    {
        IBuilding b = pet.getTrainerBuilding();
        if (b == null) throw new IllegalArgumentException("Pet must have a trainer building.");

        Queue<PetHandle> q = safePetsForBuilding(b);

        PetHandle handle = new PetHandle(pet.getUUID(), pet.getDimension());

        // Avoid duplicates by UUID
        boolean exists = q.stream().anyMatch(h -> h.uuid().equals(handle.uuid()));
        if (!exists)
        {
            TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Register pet {} in {}", handle, b));
            q.add(handle);
            return true;
        }
        return false;
    }

    /**
     * Retrieves all pets in the given colony by iterating over all buildings in the colony and retrieving the list of pets associated
     * with each building from the global pet registry. If a building does not have any associated pets, it is skipped.
     *
     * @param colony the colony to retrieve the list of pets from.
     * @return the list of all ITradePostPet entities in the colony.
     */
    public static final ImmutableList<PetHandle> getPetsInColony(IColony colony)
    {
        List<PetHandle> pets = new ArrayList<>();
        for (IBuilding building : colony.getBuildingManager().getBuildings().values())
        {
            Queue<PetHandle> buildingPets = globalPetRegistry.get(building);
            if (buildingPets != null)
            {
                pets.addAll(buildingPets);
            }
        }

        ImmutableList<PetHandle> returnPets = ImmutableList.copyOf(pets);
        return returnPets;
    }

    /**
     * Retrieves the list of pets associated with the given building from the global pet registry and resolves them to ITradePostPet objects.
     * The returned list only includes pets that are currently alive and not removed from the world.
     *
     * @param building the building to retrieve the list of pets from.
     * @return a list of ITradePostPet objects associated with the given building, or null if none are found.
     */
    public static ImmutableList<ITradePostPet> getPetsInBuilding(IBuilding building)
    {
        Queue<PetHandle> q = safePetsForBuilding(building);
        MinecraftServer server = building.getColony().getWorld().getServer();
        List<ITradePostPet> result = new ArrayList<>();
        for (PetHandle h : q)
        {
            ITradePostPet p = resolve(server, h);
            if (p != null && !((Entity) p).isRemoved() && ((Entity) p).isAlive())
            {
                result.add(p);
            }
        }
        return ImmutableList.copyOf(result);
    }

    /**
     * Registers a BlockPos as a work location for a pet in the given colony. This is used to determine if a pet can be assigned to
     * work at a given location.
     * 
     * @param colony the colony to register the work location for.
     * @param pos    the BlockPos to register as a work location.
     */
    public static void registerWorkLocation(IColony colony, BlockPos pos)
    {
        if (colony == null || pos == null || BlockPos.ZERO.equals(pos))
        {
            return;
        }

        petWorkLocations.computeIfAbsent(colony, c -> ConcurrentHashMap.newKeySet()).add(pos);
    }

    /**
     * Removes a BlockPos as a work location for a pet in the given colony. If the BlockPos was not registered as a work location, does
     * nothing.
     * 
     * @param colony the colony to unregister the work location for.
     * @param pos    the BlockPos to unregister as a work location.
     */
    public static void unregisterWorkLocation(IColony colony, BlockPos pos)
    {
        Set<BlockPos> positions = petWorkLocations.get(colony);
        if (positions != null)
        {
            positions.remove(pos);
            if (positions.isEmpty())
            {
                petWorkLocations.remove(colony);
            }
        }
    }

    /**
     * Retrieves the set of all BlockPos that are valid work locations for a pet in the given colony. A BlockPos is a valid work
     * location if it has been previously registered as such by calling {@link #registerWorkLocation(Colony, BlockPos)}, and
     * if the block at the BlockPos is still a valid work location block. 
     * If no BlockPos have been registered, an empty set is returned.
     * 
     * @param colony the colony to retrieve the set of work locations for.  
     * @return a set of BlockPos that are valid work locations for a pet in the given colony.
     */
    public static Set<BlockPos> getWorkLocations(IColony colony)
    {
        Set<BlockPos> workLocations = petWorkLocations.getOrDefault(colony, Set.of());

        return workLocations;
    }

    /**
     * Validates the set of work locations for the given colony. If the block at any BlockPos in the set is not a valid work location
     * block, it is removed from the set of work locations. This is used to ensure that the set of work locations remains up-to-date even
     * if the state of the world changes.
     * 
     * @param colony the colony to validate the work locations for.
     */
    public static void validateWorkLocations(IColony colony)
    {
        Set<BlockPos> workLocations = petWorkLocations.getOrDefault(colony, Set.of());

        for (BlockPos pos : workLocations)
        {
            BlockState state = colony.getWorld().getBlockState(pos);
            if (!(state.getBlock() instanceof AbstractBlockPetWorkingLocation))
            {
                unregisterWorkLocation(colony, pos);
            }
        }
    }


    /**
     * Retrieves the map of all colonies to their valid work locations for pets. The keys of the map are the colonies, and the values
     * are the sets of BlockPos that are valid work locations for pets in that colony.
     *
     * @return a map of all colonies to their valid work locations for pets.
     */
    public static final Map<IColony, Set<BlockPos>> getPetWorkLocations()
    {
        return petWorkLocations;
    }

    public static void loadPetWorkLocations(IColony colony, CompoundTag tag)
    {
        ListTag posList = tag.getList("petWorkLocations", Tag.TAG_COMPOUND);

        Set<BlockPos> positions = new HashSet<>();
        for (Tag posTagRaw : posList)
        {
            CompoundTag posTag = (CompoundTag) posTagRaw;
            BlockPos pos = BlockPosUtil.readFromNBT(posTag, "WorkLocation");
            PetRegistryUtil.registerWorkLocation(colony, pos);
        }
    }

    /**
     * Saves the set of all BlockPos that are valid work locations for a pet in the given colony to the provided CompoundTag.
     * 
     * @param tag the CompoundTag to save the data to.
     * @return the CompoundTag with the data saved.
     */
    public static CompoundTag savePetWorkLocations(IColony colony, CompoundTag tag)
    {
        CompoundTag colonyTag = new CompoundTag();

        ListTag posList = new ListTag();
        for (BlockPos pos : getWorkLocations(colony))
        {
            CompoundTag posTag = new CompoundTag();
            BlockPosUtil.writeToNBT(posTag, "WorkLocation", pos);
            posList.add(posTag);
        }

        tag.put("petWorkLocations", posList);

        return tag;
    }

    public record PetHandle(UUID uuid, ResourceKey<Level> dimension)
    {

    }
}
