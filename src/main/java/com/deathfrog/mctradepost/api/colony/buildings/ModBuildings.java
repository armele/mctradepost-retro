package com.deathfrog.mctradepost.api.colony.buildings;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBuildings {
    public static final String MARKETPLACE_ID = "marketplace";
    public static final String RESORT_ID      = "resort";

    public static DeferredHolder<BuildingEntry, BuildingEntry> marketplace;
    public static DeferredHolder<BuildingEntry, BuildingEntry> resort;

    private ModBuildings()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildings but this is a Utility class.");
    }    

    @NotNull
    public static AbstractBlockHut<?>[] getHuts()
    {
        return new AbstractBlockHut[] {
            MCTradePostMod.blockHutMarketplace,
            MCTradePostMod.blockHutResort
        };
    }    
}
