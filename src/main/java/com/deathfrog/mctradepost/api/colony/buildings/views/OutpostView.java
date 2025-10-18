package com.deathfrog.mctradepost.api.colony.buildings.views;

import org.jetbrains.annotations.NotNull;

import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.buildings.views.LivingBuildingView;

import net.minecraft.core.BlockPos;

public class OutpostView extends LivingBuildingView 
{
    public OutpostView(IColonyView c, @NotNull BlockPos l)
    {
        super(c, l);
    }


}
