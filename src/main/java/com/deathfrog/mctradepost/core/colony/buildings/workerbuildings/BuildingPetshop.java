package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_ANIMALTRAINER;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetAssignmentModule;
import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.BlockPosUtil;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IManagedAnimal;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.animal.Animal;

public class BuildingPetshop extends AbstractBuilding
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public static final String ANIMALS_HERDED = "animals_herded";
    private static final String TAG_PET_DATA = "petData";
    private final Map<UUID, CompoundTag> petDataByUuid = new LinkedHashMap<>();

    public BuildingPetshop(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName()
    {
        return ModBuildings.PETSHOP_ID;
    }

    /**
     * Retrieves the list of pets associated with this building from the global pet registry.
     *
     * @return a list of ITradePostPet objects associated with this building, or null if none are found.
     */
    public ImmutableList<ITradePostPet> getPets() 
    { 
        ImmutableList<ITradePostPet> pets = PetRegistryUtil.getPetsInBuilding(this);
        return pets;
    }

    /**
     * Serializes the state of the building to NBT, including the list of pets.
     * @param provider The holder lookup provider for item and block references.
     * @return The compound tag containing the serialized state of the building.
     */
    @Override
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);
        PetRegistryUtil.savePetWorkLocations(this.getColony(), compound);

        final ListTag petDataList = new ListTag();
        for (final CompoundTag petTag : petDataByUuid.values())
        {
            petDataList.add(petTag.copy());
        }
        compound.put(TAG_PET_DATA, petDataList);

        return compound;
    }

    /**
     * Deserializes the state of the building from NBT, including the list of stations.
     * @param provider The holder lookup provider for item and block references.
     * @param compound The compound tag containing the serialized state of the building.
     */
    @Override
    public void deserializeNBT(Provider provider, CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        PetRegistryUtil.loadPetWorkLocations(this.getColony(), compound);

        petDataByUuid.clear();
        final ListTag petDataList = compound.getList(TAG_PET_DATA, Tag.TAG_COMPOUND);
        for (final Tag tag : petDataList)
        {
            if (tag instanceof CompoundTag petTag)
            {
                rememberPetData(petTag);
            }
        }
    }

    /**
     * Serializes the current state of the building, including the list of stations, to the given buffer.
     * The state of the stations is stored under the key TAG_STATIONS in the serialized CompoundTag.
     *
     * @param buf      The buffer to serialize the state of the building into.
     * @param fullSync Whether or not to serialize the full state of the building, or just the delta.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf, final boolean fullSync) 
    {
        super.serializeToView(buf, fullSync);
        final List<CompoundTag> petDataTags = getPetDataTagsForView();
        buf.writeInt(petDataTags.size());
        for (final CompoundTag petTag : petDataTags)
        {
            buf.writeNbt(petTag);
        }
    }

    /**
     * Remembers the pet data for the given pet and returns a serialized copy of it.
     * @param pet The pet to remember the data for.
     * @return A serialized copy of the pet data, or null if the pet does not have any data.
     */
    public CompoundTag rememberPetData(final ITradePostPet pet)
    {
        if (pet == null || pet.getPetData() == null)
        {
            return null;
        }

        final CompoundTag petTag = new CompoundTag();
        @SuppressWarnings("rawtypes")
        final PetData petBase = (PetData) pet.getPetData();
        petBase.toNBT(petTag);
        rememberPetData(petTag);
        return petTag;
    }

    /**
     * Remembers the pet data for the given pet, represented as a CompoundTag.
     * The pet data must contain a UUID and the animal type for it to be stored.
     * If the pet data does not contain the required data, it is not stored.
     *
     * @param petTag The CompoundTag representing the pet data to remember.
     */
    public void rememberPetData(final CompoundTag petTag)
    {
        if (petTag == null || !petTag.hasUUID("uuid") || !petTag.contains(PetData.TAG_ANIMAL_TYPE))
        {
            return;
        }

        petDataByUuid.put(petTag.getUUID("uuid"), petTag.copy());
    }

    /**
     * Applies the persisted pet data to the given pet, if it is found.
     * This method is called after the pet data has been loaded from disk.
     * If the pet data does not contain a UUID, it is not applied.
     * <p>
     * The pet data is applied by reading the serialized pet data from the given CompoundTag
     * and applying it to the pet. If the pet data contains a work location, it is
     * applied to the pet.
     * <p>
     * This method is idempotent and can be safely called multiple times on the same pet.
     *
     * @param pet The pet to apply the persisted pet data to.
     */
    public void applyPersistedPetData(final ITradePostPet pet)
    {
        if (pet == null)
        {
            return;
        }

        final CompoundTag petTag = petDataByUuid.get(pet.getUUID());
        if (petTag == null)
        {
            return;
        }

        final BlockPos persistedWorkLocation = BlockPosUtil.readFromNBT(petTag, "workLocation");
        if (persistedWorkLocation != null && !persistedWorkLocation.equals(pet.getWorkLocation()))
        {
            pet.setWorkLocation(persistedWorkLocation);
        }
    }

    /**
     * Updates the persisted work location for the pet with the given UUID.
     * If the pet data for the given UUID is not found, returns false.
     * If the pet data is found, updates the work location in the pet data with the given value.
     * If the work location is null, sets the work location in the pet data to BlockPos.ZERO.
     * Marks the pet data as dirty after updating.
     * <p>
     * This method is idempotent and can be safely called multiple times with the same UUID and work location.
     * <p>
     * Returns true if the pet data was found and updated, false otherwise.
     *
     * @param uuid The UUID of the pet to update.
     * @param workLocation The new work location for the pet. Can be null to set the work location to BlockPos.ZERO.
     * @return true if the pet data was found and updated, false otherwise.
     */
    public boolean updatePersistedPetWorkLocation(final UUID uuid, final BlockPos workLocation)
    {
        if (uuid == null)
        {
            return false;
        }

        final CompoundTag petTag = petDataByUuid.get(uuid);
        if (petTag == null)
        {
            return false;
        }

        BlockPosUtil.writeToNBT(petTag, "workLocation", workLocation == null ? BlockPos.ZERO : workLocation);
        markPetsDirty();
        return true;
    }

    /**
     * Forgets the pet data for the pet with the given UUID.
     * If the pet data is found, removes it from the map and marks the pet data as dirty.
     * If the pet data is not found, does nothing.
     * <p>
     * This method is idempotent and can be safely called multiple times with the same UUID.
     * <p>
     * Returns true if the pet data was found and removed, false otherwise.
     *
     * @param uuid The UUID of the pet to forget.
     */
    public void forgetPetData(final UUID uuid)
    {
        if (uuid != null && petDataByUuid.remove(uuid) != null)
        {
            markPetsDirty();
        }
    }

    /**
     * Returns a list of all pet data tags for the pets that are currently part of this petshop.
     * The list contains all pet data tags that are currently part of this petshop, including pets that are
     * currently being worked on by the petshop's workers.
     * <p>
     * The list is populated by iterating over all pets currently part of this petshop and adding their pet data tags
     * to the list. The list also includes pet data tags for pets that are not currently part of this petshop but are
     * managed by the colony's animal manager and are not currently being worked on by any other petshop. These pets
     * may be available for work by this petshop.
     * <p>
     * The list does not contain any pet data tags for pets that are not managed by the colony's animal manager.
     * <p>
     * The list is ordered by the order in which the pets were added to the list.
     * <p>
     * This method is thread-safe and can be safely called from any thread.
     * <p>
     * Returns an unmodifiable list of all pet data tags for the pets that are currently part of this petshop.
     */
    private List<CompoundTag> getPetDataTagsForView()
    {
        final Map<UUID, CompoundTag> petTags = new LinkedHashMap<>();

        for (final ITradePostPet pet : getPets())
        {
            final CompoundTag petTag = rememberPetData(pet);
            if (petTag != null && petTag.hasUUID("uuid"))
            {
                petTags.put(petTag.getUUID("uuid"), petTag);
            }
        }

        for (final IAnimalData animalData : getColony().getAnimalManager().getAnimals())
        {
            if (!isAnimalDataForThisPetshop(animalData))
            {
                continue;
            }

            final UUID uuid = animalData.getUUID();
            if (uuid == null || petTags.containsKey(uuid))
            {
                continue;
            }

            animalData.getManagedAnimal()
                .map(IManagedAnimal::getEntity)
                .filter(ITradePostPet.class::isInstance)
                .map(ITradePostPet.class::cast)
                .map(this::rememberPetData)
                .filter(tag -> tag != null && tag.hasUUID("uuid"))
                .ifPresent(tag -> petTags.put(tag.getUUID("uuid"), tag));

            if (!petTags.containsKey(uuid) && petDataByUuid.containsKey(uuid))
            {
                final CompoundTag petTag = petDataByUuid.get(uuid).copy();
                petTag.putInt("entityId", -1);
                petTags.put(uuid, petTag);
            }
        }

        return new ArrayList<>(petTags.values());
    }

    /**
     * Determines whether the given animal data is associated with this petshop.
     * @param animalData the animal data to check
     * @return true if the animal data is associated with this petshop, false otherwise
     */
    private boolean isAnimalDataForThisPetshop(final IAnimalData animalData)
    {
        if (animalData == null)
        {
            return false;
        }

        final IBuilding homeBuilding = animalData.getHomeBuilding();
        return homeBuilding != null && getID().equals(homeBuilding.getID());
    }


    /**
     * Marks the pet list as dirty, so that it gets reserialized when the building is synced to the client.
     * Also marks the PetAssignmentModule as dirty, so that it gets reserialized with the updated pet list.
     */
    public void markPetsDirty()
    {
        super.markDirty();

        PetAssignmentModule petModule = getModule(MCTPBuildingModules.PET_ASSIGNMENT);

        if (petModule != null)
        {
            petModule.markDirty();
        }
    }


    /**
     * Called on every colony tick to perform periodic maintenance tasks for the pet shop.
     * Iterates over the list of pets associated with this building and unregisters any pets
     * that are dead or have been removed from the world.
     *
     * @param colony the colony that this building is a part of
     */
    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        super.onColonyTick(colony);

        PetRegistryUtil.validateWorkLocations(colony);

        for (ITradePostPet pet : getPets())
        {
            if (pet instanceof Animal animal)
            {
                if (animal.isDeadOrDying() || animal.isRemoved())
                {
                    TraceUtils.dynamicTrace(TRACE_ANIMALTRAINER, () -> LOGGER.info("Stale pet: {} associated with building {}", pet, pet.getTrainerBuilding()));

                    PetRegistryUtil.unregister(pet);
                }

                if (animal.isNoAi()) 
                {
                    animal.setNoAi(false);
                }
            }
        }
    }


    /**
     * Gets the animal trainer of the pet shop, if any. Returns null if no animal trainer is assigned.
     * 
     * @return the animal trainer of the pet shop, or null if none is assigned.
     */
    public ICitizenData trainer()
    {
        WorkerBuildingModule module = this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.animaltrainer.get());

        List<ICitizenData> employees = module.getAssignedCitizen();

        if (employees.isEmpty())
        {
            return null;
        }

        ICitizenData trainer = employees.get(0);

        return trainer;
    }

    /**
     * Retrieves the level of the primary skill of the animal trainer assigned to this building.
     * If there is no animal trainer, or the animal trainer is not assigned to a module, or the module does not have a primary skill, this method returns 0.
     * 
     * @return the level of the primary skill of the animal trainer, or 0 if no suitable worker is found.
     */
    public int trainerPrimarySkill()
    {
        int skill = 0;
        
        WorkerBuildingModule module = this.getModule(WorkerBuildingModule.class, m -> m.getJobEntry() == MCTPModJobs.animaltrainer.get());

        ICitizenData trainer = trainer();

        if (trainer != null)
        {
            skill = trainer.getCitizenSkillHandler().getLevel(module.getPrimarySkill());
        }

        return skill;
    }


    // Armadillo
    // Bat (flying)
    // Camel
    // Cat
    // Dolphin (aquatic)
    // Donkey
    // Frog
    // Goat
    // Horse
    // Llama
    // Mule
    // Ocelot
    // Panda
    // Parrot (flying)
    // Polar Bear
    // Turtle

    
}
