package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.deathfrog.mctradepost.core.colony.jobs.buildings.modules.BuildingModules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.BuildingMarketplace;


public final class ModBuildingsInitializer
{
    public final static DeferredRegister<BuildingEntry> DEFERRED_REGISTER = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, MCTradePostMod.MODID);

    private ModBuildingsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildingsInitializer but this is a Utility class.");
    }

    static
    {

        ModBuildings.marketplace = DEFERRED_REGISTER.register(ModBuildings.MARKETPLACE_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(MCTradePostMod.blockHutMarketplace)
          .setBuildingProducer(BuildingMarketplace::new).setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(MCTradePostMod.blockHutMarketplace.getRegistryName())
          .addBuildingModuleProducer(BuildingModules.SHOPKEEPER_WORK)
          .createBuildingEntry());
    }
}
