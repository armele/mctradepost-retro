package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry.ModuleProducer;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;


import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.colony.buildings.views.MarketplaceView;
import com.deathfrog.mctradepost.api.colony.buildings.views.OutpostView;
import com.deathfrog.mctradepost.api.colony.buildings.views.PetshopView;
import com.deathfrog.mctradepost.api.colony.buildings.views.RecyclingView;
import com.deathfrog.mctradepost.api.colony.buildings.views.ResortView;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingPetshop;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;


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
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.THRIFTSHOP);
            marketBuilder.addBuildingModuleProducer(MCTPBuildingModules.ECON_SETTINGS);
            marketBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            marketBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE);  
            ModBuildings.marketplace = marketBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.marketplace);
    

            BuildingEntry.Builder resortBuilder = new BuildingEntry.Builder();
            resortBuilder.setBuildingBlock(MCTradePostMod.blockHutResort.get());
            resortBuilder.setBuildingProducer(BuildingResort::new);
            resortBuilder.setBuildingViewProducer(() -> ResortView::new);
            resortBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.RESORT_ID));
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.GUESTSERVICES_WORK);
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.BARTENDER_WORK);
            resortBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            resortBuilder.addBuildingModuleProducer(MCTPBuildingModules.BARTENDER_CRAFT);
            resortBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 
            ModBuildings.resort = resortBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.resort);


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

            registerBuilding(event, ModBuildings.recycling);


            BuildingEntry.Builder stationBuilder = new BuildingEntry.Builder();
            stationBuilder.setBuildingBlock(MCTradePostMod.blockHutStation.get());
            stationBuilder.setBuildingProducer(BuildingStation::new);
            stationBuilder.setBuildingViewProducer(() -> StationView::new);
            stationBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.STATION_ID));
            stationBuilder.addBuildingModuleProducer(MCTPBuildingModules.STATIONMASTER_WORK);
            stationBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            stationBuilder.addBuildingModuleProducer(MCTPBuildingModules.STATION_CONNECTION);
            stationBuilder.addBuildingModuleProducer(MCTPBuildingModules.IMPORTS);
            stationBuilder.addBuildingModuleProducer(MCTPBuildingModules.EXPORTS);
            stationBuilder.addBuildingModuleProducer(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
            stationBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE); 
            ModBuildings.station = stationBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.station);


            BuildingEntry.Builder petShopBuilder = new BuildingEntry.Builder();
            petShopBuilder.setBuildingBlock(MCTradePostMod.blockHutPetShop.get());
            petShopBuilder.setBuildingProducer(BuildingPetshop::new);
            petShopBuilder.setBuildingViewProducer(() -> PetshopView::new);
            petShopBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.PETSHOP_ID));
            petShopBuilder.addBuildingModuleProducer(MCTPBuildingModules.ANIMALTRAINER_WORK);
            petShopBuilder.addBuildingModuleProducer(MCTPBuildingModules.PET_TRAINING);
            petShopBuilder.addBuildingModuleProducer(MCTPBuildingModules.PET_ASSIGNMENT);
            petShopBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE);
            ModBuildings.petshop = petShopBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.petshop);


            BuildingEntry.Builder outpostBuilder = new BuildingEntry.Builder();
            outpostBuilder.setBuildingBlock(MCTradePostMod.blockHutOutpost.get());
            outpostBuilder.setBuildingProducer((colony, blockPos) -> new BuildingOutpost(colony, blockPos, ModBuildings.OUTPOST_ID, 5));
            outpostBuilder.setBuildingViewProducer(() -> OutpostView::new);
            outpostBuilder.setRegistryName(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, ModBuildings.OUTPOST_ID));
            outpostBuilder.addBuildingModuleProducer(MCTPBuildingModules.SCOUT_WORK);
            outpostBuilder.addBuildingModuleProducer(MCTPBuildingModules.OUTPOST_LIVING);
            outpostBuilder.addBuildingModuleProducer(BuildingModules.BED);
            outpostBuilder.addBuildingModuleProducer(MCTPBuildingModules.OUTPOST_EXPORTS);
            outpostBuilder.addBuildingModuleProducer(BuildingModules.BUILDING_RESOURCES);
            outpostBuilder.addBuildingModuleProducer(BuildingModules.BUILDER_SETTINGS);
            outpostBuilder.addBuildingModuleProducer(BuildingModules.MIN_STOCK);
            outpostBuilder.addBuildingModuleProducer(BuildingModules.STATS_MODULE);  
            ModBuildings.outpost = outpostBuilder.createBuildingEntry();

            registerBuilding(event, ModBuildings.outpost);

        }
    }

    /**
     * Registers a building with the given building entry to the given RegisterEvent.
     *
     * @param event The event to register the building with.
     * @param buildingEntry The building entry to register.
     */
    protected static void registerBuilding(RegisterEvent event, BuildingEntry buildingEntry)
    {
            ResourceKey<Registry<BuildingEntry>> buildingsRegistry = CommonMinecoloniesAPIImpl.BUILDINGS;

            if (buildingsRegistry == null)
            {
                throw new IllegalStateException("Building registry is null while attempting to register Trade Post buildings.");
            }

            ResourceLocation registryName = buildingEntry.getRegistryName();

            if (registryName == null)
            {
                throw new IllegalStateException("Attempting to register a building with no registry name.");
            }

            event.register(buildingsRegistry, registry -> {
                registry.register(registryName, buildingEntry);
            });
    }

    /**
     * Extends the existing buildings with Trade Post specific modules
     * if they are not already present
     */
    public static void injectBuildingModules()
    {
        // Get the existing entry to extend
        final DeferredHolder<BuildingEntry,BuildingEntry> cowboy = com.minecolonies.api.colony.buildings.ModBuildings.cowboy;

        injectModuleToBuilding(MCTPBuildingModules.DAIRYWORKER_WORK, cowboy);
        injectModuleToBuilding(MCTPBuildingModules.DAIRYWORKER_CRAFT, cowboy, 2);

        // Get the existing entry to extend
        final DeferredHolder<BuildingEntry,BuildingEntry> kitchen = com.minecolonies.api.colony.buildings.ModBuildings.kitchen;

        injectModuleToBuilding(MCTPBuildingModules.STEWMELIER_WORK, kitchen);
        injectModuleToBuilding(MCTPBuildingModules.STEWMELIER_INGREDIENTS, kitchen, 2);
    }

    /**
     * Injects a module into an existing building entry if it is not already present.
     * 
     * @param producer the module to inject
     * @param buildingEntry the building to inject into
     * If the module is not already present, it will be added to the end of the module list.
     * If the module is already present, it will not be injected again.
     * If the buildingEntry is not bound, this method does nothing.
     */
    protected static void injectModuleToBuilding(ModuleProducer<?, ?> producer, DeferredHolder<BuildingEntry,BuildingEntry> buildingEntryHolder) 
    {
        injectModuleToBuilding(producer, buildingEntryHolder, -1);
    }

    /**
     * Injects a module into an existing building entry if it is not already present
     *
     * @param producer the module to inject
     * @param buildingEntry the building to inject into
     */
    protected static void injectModuleToBuilding(ModuleProducer<?, ?> producer, DeferredHolder<BuildingEntry,BuildingEntry> buildingEntryHolder, int position) 
    {
        if (buildingEntryHolder == null || !buildingEntryHolder.isBound()) return;
        BuildingEntry buildingEntry = buildingEntryHolder.get();

        @SuppressWarnings("rawtypes")
        final List<ModuleProducer> modules = buildingEntry.getModuleProducers();

        if (modules.stream().noneMatch(mp -> mp.key.equals(producer.key))) 
        {
            if (position == -1 || position >= modules.size())
            {    
                modules.add(producer);
            }
            else
            {
                modules.add(position, producer);
            }
        }
    }
}
