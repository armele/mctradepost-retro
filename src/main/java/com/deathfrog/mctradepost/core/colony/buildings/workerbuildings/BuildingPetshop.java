package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import java.util.List;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetAssignmentModule;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.animal.Animal;

public class BuildingPetshop extends AbstractBuilding
{

    public static final String ANIMALS_HERDED = "animals_herded";

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
    public List<ITradePostPet> getPets() 
    { 
        List<ITradePostPet> pets = PetRegistryUtil.getPetsInBuilding(this);
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
        buf.writeInt(getPets().size());
        for (final ITradePostPet pet : getPets())
        {
            CompoundTag petTag = new CompoundTag();
            PetData petBase = (PetData) pet.getPetData();
            petBase.toNBT(petTag);
            buf.writeNbt(petTag);
        }
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

        for (ITradePostPet pet : getPets())
        {
            if (pet instanceof Animal animal)
            {
                if (animal.isDeadOrDying() || animal.isRemoved())
                {
                    PetRegistryUtil.unregister(pet);
                }
            }
        }
    }

    // Armadillo
    // Axolotl (aquatic)
    // Bat (flying)
    // Camel
    // Cat
    // Dolphin (aquatic)
    // Donkey
    // Fox
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
    // Wolf

    
}
