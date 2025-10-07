package com.deathfrog.mctradepost.api.colony.buildings.views;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class OutpostView extends AbstractBuildingView 
{
    protected List<PetData<?>> pets = new ArrayList<>();

    public OutpostView(IColonyView c, @NotNull BlockPos l)
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

    }

}
