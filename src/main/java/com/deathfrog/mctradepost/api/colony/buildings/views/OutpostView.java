package com.deathfrog.mctradepost.api.colony.buildings.views;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingBuilderView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;

public class OutpostView extends AbstractBuildingBuilderView 
{
    protected int outpostLevel = 0;

    public OutpostView(IColonyView c, @NotNull BlockPos l)
    {
        super(c, l);
    }

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf) 
    {
        super.deserialize(buf);
    }

}
