package com.deathfrog.mctradepost.core.blocks.huts;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
// TODO: For building unlock effects see CustomRecipe.isUnlockEffectResearched for a possible workaround to Minecolonies framework appearing to not handle effects on non-minecolonies buildings.
// TODO: RESORT [Enhancement] Custom block item and recipe for "drink table"
// TODO: RESORT Custom item for "ice cream"
// TODO: RESORT Adjust the recipe for blockhutresort to use the "ice cream"

public class BlockHutResort extends MCTPBaseBlockHut{

    public static final String HUT_NAME = "blockhutresort";

    @Override
    public String getHutName() {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return ModBuildings.resort;
    }
    
}
