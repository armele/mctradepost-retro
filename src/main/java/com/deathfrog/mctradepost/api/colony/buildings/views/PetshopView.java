package com.deathfrog.mctradepost.api.colony.buildings.views;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.PetFox;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetAxolotl;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class PetshopView extends AbstractBuildingView 
{
    protected List<PetData<?>> pets = new ArrayList<>();

    public PetshopView(IColonyView c, @NotNull BlockPos l)
    {
        super(c, l);
    }

    /**
     * Deserializes the given byte buffer into the petshop view. It first deserializes the superclass, and then reads the size of the pet list, and then for each entry in the list, it reads an NBTTagCompound from the buffer and deserializes it into a {@link ITradePostPet} object
     * @param buf the byte buffer to deserialize
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf) 
    {
        pets.clear();

        super.deserialize(buf);
        final int petsize = buf.readInt();
        for (int i = 0; i < petsize; i++)
        {
            final CompoundTag compound = buf.readNbt();
            if (compound != null)
            {   
                String animalType = compound.getString(PetData.TAG_ANIMAL_TYPE);
                PetTypes petType = PetTypes.fromPetString(animalType);
                PetData<?> pet = PetTypes.createPetDataForType(petType, compound);

                pets.add(pet);
            }
        }

        // MCTradePostMod.LOGGER.info("Deserialized Petshop at {} has {} pets.", this.getPosition(), pets.size());
    }

    /**
     * Gets the list of all pets in the building
     * @return a list of pet objects associated with the building
     */
    public ImmutableList<PetData> getPets()
    {
        return ImmutableList.copyOf(pets);
    }
    
}
