package com.deathfrog.mctradepost.core.blocks.huts;


import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;


public class BlockHutMarketplace extends MCTPBaseBlockHut {
    public BlockHutMarketplace() {
        super();
    }

    @Override
    public String getHutName() {
        return "blockhutmarketplace";
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return ModBuildings.marketplace.get();
    }

    public void handleRitual() {
        
    }

}