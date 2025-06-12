package com.deathfrog.mctradepost.api.colony.buildings;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

public class ModBuildings {
    public static final String MARKETPLACE_ID   = "marketplace";
    public static final String RESORT_ID        = "resort";
    public static final String RECYCLING_ID     = "recycling";
    public static final String STATION_ID       = "station";

    public static BuildingEntry marketplace;
    public static BuildingEntry resort;
    public static BuildingEntry recycling;
    public static BuildingEntry station;
    
    private ModBuildings()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildings but this is a Utility class.");
    }    

    @NotNull
    public static AbstractBlockHut<?>[] getHuts()
    {
        return new AbstractBlockHut[] {
            MCTradePostMod.blockHutMarketplace.get(),
            MCTradePostMod.blockHutResort.get(),
            MCTradePostMod.blockHutRecycling.get(),
            MCTradePostMod.blockHutStation.get()
        };
    }    
}
