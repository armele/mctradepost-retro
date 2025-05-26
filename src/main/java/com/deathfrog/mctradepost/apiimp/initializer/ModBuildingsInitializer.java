package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.views.EmptyView;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.views.MarketplaceView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;

@EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModBuildingsInitializer
{
    // public final static DeferredRegister<BuildingEntry> DEFERRED_REGISTER = DeferredRegister.create(CommonMinecoloniesAPIImpl.BUILDINGS, MCTradePostMod.MODID);

    private ModBuildingsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildingsInitializer but this is a Utility class.");
    }

    static
    {

    }

    /**
     * Registers and initializes the marketplace and resort buildings in the DEFERRED_REGISTER.
     * This method creates building entries for each building type, specifying their respective
     * building blocks, producers, view producers, and registry names.
     * Additional module producers can be added to each building entry as needed.
     */
    @SubscribeEvent
    public static void registerBuildings(RegisterEvent event) {
        if (event.getRegistryKey().equals(CommonMinecoloniesAPIImpl.BUILDINGS))
        {
            MCTradePostMod.LOGGER.info("Registering buildings.");

            // Using the a deferred holder is used for hut blocks to ensure they are not null at this point.
            BuildingEntry.Builder marketBuilder = new BuildingEntry.Builder();
            marketBuilder.setBuildingBlock(MCTradePostMod.blockHutMarketplace.get());
            marketBuilder.setBuildingProducer(BuildingMarketplace::new);
            marketBuilder.setBuildingViewProducer(() -> MarketplaceView::new);
            marketBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.MARKETPLACE_ID));
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.SHOPKEEPER_WORK);
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.ITEMLIST_SELLABLE);
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.ECON_MODULE);
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.ECON_SETTINGS);
            marketBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            marketBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE);  
            ModBuildings.marketplace = marketBuilder.createBuildingEntry();

            event.register(CommonMinecoloniesAPIImpl.BUILDINGS, registry -> {
                registry.register(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.MARKETPLACE_ID), ModBuildings.marketplace);
            });


            BuildingEntry.Builder resortBuilder = new BuildingEntry.Builder();
            resortBuilder.setBuildingBlock(MCTradePostMod.blockHutResort.get());
            resortBuilder.setBuildingProducer(BuildingResort::new);
            resortBuilder.setBuildingViewProducer(() -> EmptyView::new);
            resortBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.RESORT_ID));
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.GUESTSERVICES_WORK);
            resortBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            resortBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 
            // TODO: Add other modules as needed.
            // Minimim stock module
            // Stats module for drinks served.

            ModBuildings.resort = resortBuilder.createBuildingEntry();

            event.register(CommonMinecoloniesAPIImpl.BUILDINGS, registry -> {
                registry.register(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.RESORT_ID), ModBuildings.resort);
            });            
        }
        

    }

}
