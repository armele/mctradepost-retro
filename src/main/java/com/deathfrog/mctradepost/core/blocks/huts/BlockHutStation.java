package com.deathfrog.mctradepost.core.blocks.huts;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BlockHutStation extends MCTPBaseBlockHut {
    public static final String HUT_NAME = "blockhutstation";

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.station;
    }

    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }
}
