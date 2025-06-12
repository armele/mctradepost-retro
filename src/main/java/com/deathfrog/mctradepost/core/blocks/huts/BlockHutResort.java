package com.deathfrog.mctradepost.core.blocks.huts;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
// TODO: RESORT [Enhancement] Custom block item and recipe for "drink table"

public class BlockHutResort extends MCTPBaseBlockHut
{

    public static final String HUT_NAME = "blockhutresort";

    @Override
    public String getHutName() 
    {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() 
    {
        return ModBuildings.resort;
    }
    
}
