package com.deathfrog.mctradepost.core.colony.buildings;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.AbstractBuilding;

import net.minecraft.core.BlockPos;

public class BuildingStation extends AbstractBuilding 
{
    public BuildingStation(@NotNull IColony colony, BlockPos pos) 
    {
        super(colony, pos);
    }

    @Override
    public String getSchematicName() 
    {
        return ModBuildings.STATION_ID;
    }
}
