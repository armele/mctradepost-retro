package com.deathfrog.mctradepost.core.blocks.huts;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

public class BlockHutPetStore extends MCTPBaseBlockHut 
{
    public static final String HUT_NAME = "blockhutpetstore";

    @Override
    public String getHutName()
    {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.petstore;
    }    
}
