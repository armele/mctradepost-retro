package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.views.MarketplaceView;
import com.deathfrog.mctradepost.api.colony.buildings.views.RecyclingView;
import com.deathfrog.mctradepost.api.colony.buildings.views.ResortView;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
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
                registry.register(ModBuildings.marketplace.getRegistryName(), ModBuildings.marketplace);
            });
    

            BuildingEntry.Builder resortBuilder = new BuildingEntry.Builder();
            resortBuilder.setBuildingBlock(MCTradePostMod.blockHutResort.get());
            resortBuilder.setBuildingProducer(BuildingResort::new);
            resortBuilder.setBuildingViewProducer(() -> ResortView::new);
            resortBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.RESORT_ID));
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.GUESTSERVICES_WORK);
            resortBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.BARTENDER_WORK);
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.BARTENDER_CRAFT);
            resortBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 
            ModBuildings.resort = resortBuilder.createBuildingEntry();

            event.register(CommonMinecoloniesAPIImpl.BUILDINGS, registry -> {
                registry.register(ModBuildings.resort.getRegistryName(), ModBuildings.resort);
            });


            BuildingEntry.Builder recyclingBuilder = new BuildingEntry.Builder();
            recyclingBuilder.setBuildingBlock(MCTradePostMod.blockHutRecycling.get());
            recyclingBuilder.setBuildingProducer(BuildingRecycling::new);
            recyclingBuilder.setBuildingViewProducer(() -> RecyclingView::new);
            recyclingBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.RECYCLING_ID));
            recyclingBuilder.addBuildingModuleProducer(MCTPBuildingModules.RECYCLINGENGINEER_WORK);
            recyclingBuilder.addBuildingModuleProducer(MCTPBuildingModules.ITEMLIST_RECYCLABLE);
            recyclingBuilder.addBuildingModuleProducer(MCTPBuildingModules.RECYCLING_PROGRESS);
            recyclingBuilder.addBuildingModuleProducer(MCTPBuildingModules.RECYCLING_SETTINGS);
            recyclingBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 

            ModBuildings.recycling = recyclingBuilder.createBuildingEntry();

            event.register(CommonMinecoloniesAPIImpl.BUILDINGS, registry -> {
                registry.register(ModBuildings.recycling.getRegistryName(), ModBuildings.recycling);
            });

            BuildingEntry.Builder stationBuilder = new BuildingEntry.Builder();
            stationBuilder.setBuildingBlock(MCTradePostMod.blockHutStation.get());
            stationBuilder.setBuildingProducer(BuildingStation::new);
            stationBuilder.setBuildingViewProducer(() -> StationView::new);
            stationBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.STATION_ID));
            stationBuilder.addBuildingModuleProducer(MCTPBuildingModules.STATIONMASTER_WORK);
            stationBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 

            ModBuildings.station = stationBuilder.createBuildingEntry();

            event.register(CommonMinecoloniesAPIImpl.BUILDINGS, registry -> {
                registry.register(ModBuildings.station.getRegistryName(), ModBuildings.station);
            });
        }
        

    }

}
