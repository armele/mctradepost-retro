package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.colony.buildings.modules.OutpostLivingBuildingModule;
import com.deathfrog.mctradepost.api.colony.buildings.modules.RecyclingItemListModule;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.BuildingStationExportModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.BuildingStationImportModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.EconModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.MarketplaceItemListModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.OutpostExportModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.OutpostLivingBuildingModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetAssignmentModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetTrainingItemsModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.RecyclableListModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.RecyclerProgressView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StewmelierIngredientModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.ThriftShopOffersModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.settings.SortSetting;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.ThriftShopOffersModule;
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
import net.minecraft.network.chat.Component;


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
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule, WorkerBuildingModuleView> STEWMELIER_WORK      =
      new BuildingEntry.ModuleProducer<>("stewmelier_work",
        () -> new WorkerBuildingModule(MCTPModJobs.stewmelier.get(), Skill.Creativity, Skill.Focus, false, (b) -> 1),
      () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<StewmelierIngredientModule,StewmelierIngredientModuleView> STEWMELIER_INGREDIENTS =
      new BuildingEntry.ModuleProducer<>("stewmelier_ingredients", 
        () -> new StewmelierIngredientModule(),
        () -> StewmelierIngredientModuleView::new);

    /**
     * Horticulture
     */


    /**
     * Husbandry
     */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule, WorkerBuildingModuleView> DAIRYWORKER_WORK      =
      new BuildingEntry.ModuleProducer<>("dairyworker_work",
        () -> new CraftingWorkerBuildingModule(MCTPModJobs.dairyworker.get(), Skill.Focus, Skill.Knowledge, false, (b) -> 1, Skill.Knowledge, Skill.Focus),
      () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<DairyworkerCraftingModule,CraftingModuleView> DAIRYWORKER_CRAFT       =
      new BuildingEntry.ModuleProducer<>("dairyworker_craft", () -> new DairyworkerCraftingModule(MCTPModJobs.dairyworker.get()), () -> CraftingModuleView::new);


    /**
     * Craftmanship
     */
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule,WorkerBuildingModuleView> RECYCLINGENGINEER_WORK          =
      new BuildingEntry.ModuleProducer<>("recyclingengineer_work", () -> new CraftingWorkerBuildingModule(MCTPModJobs.recyclingengineer.get(), Skill.Strength, Skill.Focus, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<SettingsModule,SettingsModuleView> RECYCLING_SETTINGS    =
      new BuildingEntry.ModuleProducer<>("recycling_settings", () -> new SettingsModule()
        .with(BuildingRecycling.ITERATIVE_PROCESSING, new BoolSetting(false)).with(BuildingRecycling.ALLOW_SORT, new SortSetting()), () -> SettingsModuleView::new);

    public static final BuildingEntry.ModuleProducer<BuildingRecyclerProgressModule, RecyclerProgressView> RECYCLING_PROGRESS     =
      new BuildingEntry.ModuleProducer<BuildingRecyclerProgressModule, RecyclerProgressView>("recycling_progress", () -> new BuildingRecyclerProgressModule(), () -> RecyclerProgressView::new);

    public static final BuildingEntry.ModuleProducer<RecyclingItemListModule,RecyclableListModuleView> ITEMLIST_RECYCLABLE =
      new BuildingEntry.ModuleProducer<>("itemlist_recyclable", () -> new RecyclingItemListModule(EntityAIWorkRecyclingEngineer.RECYCLING_LIST),
        () -> () -> new RecyclableListModuleView(EntityAIWorkRecyclingEngineer.RECYCLING_LIST, Component.translatable(EntityAIWorkRecyclingEngineer.REQUESTS_TYPE_RECYCLABLE_UI), false));
        
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
        () -> new SettingsModule().with(BuildingMarketplace.MIN, new IntSetting(16)).with(BuildingMarketplace.AUTOMINT, new BoolSetting(true)),
        () -> SettingsModuleView::new);

    public static final BuildingEntry.ModuleProducer<ThriftShopOffersModule, ThriftShopOffersModuleView> THRIFTSHOP =
      new BuildingEntry.ModuleProducer<>("thriftshop_module", 
        () -> new ThriftShopOffersModule(),
        () -> ThriftShopOffersModuleView::new);

    public static final BuildingEntry.ModuleProducer<ItemListModule,ItemListModuleView> ITEMLIST_SELLABLE =
      new BuildingEntry.ModuleProducer<>("itemlist_sellable", () -> new MarketplaceItemListModule(EntityAIWorkShopkeeper.SELLABLE_LIST),
        () -> () -> new MarketplaceItemListModuleView(EntityAIWorkShopkeeper.SELLABLE_LIST, Component.translatable(EntityAIWorkShopkeeper.REQUESTS_TYPE_SELLABLE_UI)));
    
    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> STATIONMASTER_WORK          =
      new BuildingEntry.ModuleProducer<>("stationmaster_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.stationmaster.get(), Skill.Knowledge, Skill.Focus, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> ANIMALTRAINER_WORK          =
      new BuildingEntry.ModuleProducer<>("animaltrainer_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.animaltrainer.get(), Skill.Dexterity, Skill.Athletics, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

    public static final BuildingEntry.ModuleProducer<WorkerBuildingModule,WorkerBuildingModuleView> SCOUT_WORK          =
      new BuildingEntry.ModuleProducer<>("scout_work", 
        () -> new WorkerBuildingModule(MCTPModJobs.scout.get(), Skill.Stamina, Skill.Knowledge, true, (b) -> 1),
        () -> WorkerBuildingModuleView::new);

   public static final BuildingEntry.ModuleProducer<BuildingStationConnectionModule, StationConnectionModuleView> STATION_CONNECTION     =
      new BuildingEntry.ModuleProducer<BuildingStationConnectionModule, StationConnectionModuleView>("station_connection", () -> new BuildingStationConnectionModule(), () -> StationConnectionModuleView::new);


   public static final BuildingEntry.ModuleProducer<BuildingStationImportModule,BuildingStationImportModuleView> IMPORTS              =
      new BuildingEntry.ModuleProducer<>("imports",
        () -> new BuildingStationImportModule(),
        () -> BuildingStationImportModuleView::new);

   public static final BuildingEntry.ModuleProducer<BuildingStationExportModule,BuildingStationExportModuleView> EXPORTS              =
      new BuildingEntry.ModuleProducer<>("exports",
        () -> new BuildingStationExportModule(),
        () -> BuildingStationExportModuleView::new);


   public static final BuildingEntry.ModuleProducer<OutpostExportModule,OutpostExportModuleView> OUTPOST_EXPORTS              =
      new BuildingEntry.ModuleProducer<>("outpost_exports",
        () -> new OutpostExportModule(),
        () -> () -> new OutpostExportModuleView(OutpostExportModule.ID, Component.translatable(OutpostExportModule.OUTPOST_EXPORT_WINDOW_DESC)));

   public static final BuildingEntry.ModuleProducer<PetAssignmentModule,PetAssignmentModuleView> PET_ASSIGNMENT              =
      new BuildingEntry.ModuleProducer<>("pet_assignment",
        () -> new PetAssignmentModule(),
        () -> PetAssignmentModuleView::new);

   public static final BuildingEntry.ModuleProducer<PetTrainingItemsModule,PetTrainingItemsModuleView> PET_TRAINING              =
      new BuildingEntry.ModuleProducer<>("pet_training",
        () -> new PetTrainingItemsModule(),
        () -> PetTrainingItemsModuleView::new);

    public static final BuildingEntry.ModuleProducer<OutpostLivingBuildingModule, OutpostLivingBuildingModuleView> OUTPOST_LIVING =
      new BuildingEntry.ModuleProducer<>("outpost_living", () -> new OutpostLivingBuildingModule(), () -> OutpostLivingBuildingModuleView::new);

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

