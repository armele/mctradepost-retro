package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.colony.buildings.modules.RecyclingItemListModule;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.BuildingStationTradeModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.EconModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.MarketplaceItemListModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.RecyclableListModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.RecyclerProgressView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkRecyclingEngineer;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.ItemListModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkerBuildingModuleView;


public class MCTPBuildingModules
{
    /**
     * Global
     */


    /**
     * Item Lists
     */

    /**
     * Workers
     */

    /**
     * Horticulture
     */


    /**
     * Husbandry
     */


    /**
     * Craftmanship
     */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule,WorkerBuildingModuleView> RECYCLINGENGINEER_WORK          =
      new BuildingEntry.ModuleProducer<>("recyclingengineer_work", () -> new CraftingWorkerBuildingModule(MCTPModJobs.recyclingengineer.get(), Skill.Strength, Skill.Focus, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<SettingsModule,SettingsModuleView> RECYCLING_SETTINGS    =
      new BuildingEntry.ModuleProducer<>("recycling_settings", () -> new SettingsModule()
        .with(BuildingRecycling.ITERATIVE_PROCESSING, new BoolSetting(false)), () -> SettingsModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingRecyclerProgressModule, RecyclerProgressView> RECYCLING_PROGRESS     =
      new BuildingEntry.ModuleProducer<BuildingRecyclerProgressModule, RecyclerProgressView>("recycling_progress", () -> new BuildingRecyclerProgressModule(), () -> RecyclerProgressView::new);

    public static final BuildingEntry.ModuleProducer<RecyclingItemListModule,RecyclableListModuleView> ITEMLIST_RECYCLABLE =
      new BuildingEntry.ModuleProducer<>("itemlist_recyclable", () -> new RecyclingItemListModule(EntityAIWorkRecyclingEngineer.RECYCLING_LIST),
        () -> () -> new RecyclableListModuleView(EntityAIWorkRecyclingEngineer.RECYCLING_LIST, EntityAIWorkRecyclingEngineer.REQUESTS_TYPE_RECYCLABLE_UI, false));
        
     /**
      * Economic
      */
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> SHOPKEEPER_WORK          =
      new BuildingEntry.ModuleProducer<>("shopkeeper_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.shopkeeper.get(), Skill.Creativity, Skill.Knowledge, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingEconModule, EconModuleView> ECON_MODULE = new BuildingEntry.ModuleProducer<>(
      "econ_module", BuildingEconModule::new,
      () -> EconModuleView::new);

    public static final BuildingEntry.ModuleProducer<SettingsModule,SettingsModuleView> ECON_SETTINGS              =
      new BuildingEntry.ModuleProducer<>("marketplace_settings",
        () -> new SettingsModule().with(BuildingMarketplace.MIN, new IntSetting(16)),
        () -> SettingsModuleView::new);

    // TODO: [Enhancement] Customize the sellable item list to display the sell value.
    public static final BuildingEntry.ModuleProducer<ItemListModule,ItemListModuleView> ITEMLIST_SELLABLE =
      new BuildingEntry.ModuleProducer<>("itemlist_sellable", () -> new MarketplaceItemListModule(EntityAIWorkShopkeeper.SELLABLE_LIST),
        () -> () -> new MarketplaceItemListModuleView(EntityAIWorkShopkeeper.SELLABLE_LIST, EntityAIWorkShopkeeper.REQUESTS_TYPE_SELLABLE_UI, false,
          (buildingView) -> ItemValueRegistry.getSellableItems()));
    
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> STATIONMASTER_WORK          =
      new BuildingEntry.ModuleProducer<>("stationmaster_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.stationmaster.get(), Skill.Knowledge, Skill.Focus, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

   public static final BuildingEntry.ModuleProducer<BuildingStationConnectionModule, StationConnectionModuleView> STATION_CONNECTION     =
      new BuildingEntry.ModuleProducer<BuildingStationConnectionModule, StationConnectionModuleView>("station_connection", () -> new BuildingStationConnectionModule(), () -> StationConnectionModuleView::new);


   public static final BuildingEntry.ModuleProducer<BuildingStationTradeModule,BuildingStationTradeModuleView> TRADES              =
      new BuildingEntry.ModuleProducer<>("trades",
        () -> new BuildingStationTradeModule(),
        () -> BuildingStationTradeModuleView::new);

    /**
     * Leisure
     */
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> GUESTSERVICES_WORK          =
      new BuildingEntry.ModuleProducer<>("guestservices_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.guestservices.get(), Skill.Adaptability, Skill.Creativity, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule,WorkerBuildingModuleView> BARTENDER_WORK          =
      new BuildingEntry.ModuleProducer<>("bartender_work", () -> new CraftingWorkerBuildingModule(MCTPModJobs.bartender.get(), Skill.Agility, Skill.Focus, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingResort.CraftingModule,CraftingModuleView> BARTENDER_CRAFT         =
      new BuildingEntry.ModuleProducer<>("bartender_craft", () -> new BuildingResort.CraftingModule(MCTPModJobs.bartender.get()), () -> CraftingModuleView::new);

    /**
     * Storage
     */

    
    /**
     * Education
     */

    

    /**
     * Fundamentals
     */

    
    /**
     * Military
     */



    /**
     * Mystic
     */
}

