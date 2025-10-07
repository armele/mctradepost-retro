package com.deathfrog.mctradepost.core.colony.buildings.workerbuildings;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

public class BuildingOutpost extends AbstractBuilding
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public BuildingOutpost(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }


    @Override
    public String getSchematicName()
    {
        return ModBuildings.OUTPOST_ID;
    }
}