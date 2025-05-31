package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.EconModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
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


    // TODO: Make marketplace level 3 upgrade conditional (see Minecolonies research datapack tutorial). Building upgrades are not currently being locked behind a research wall.  Investigate the handling of the Research Effects.
    public static final BuildingEntry.ModuleProducer<SettingsModule,SettingsModuleView> ECON_SETTINGS              =
      new BuildingEntry.ModuleProducer<>("marketplace_settings",
        () -> new SettingsModule().with(BuildingMarketplace.MIN, new IntSetting(16)),
        () -> SettingsModuleView::new);

    // TODO: [Enhancement] Customize the sellable item list to display the sell value.
    public static final BuildingEntry.ModuleProducer<ItemListModule,ItemListModuleView> ITEMLIST_SELLABLE =
      new BuildingEntry.ModuleProducer<>("itemlist_sellable", () -> new ItemListModule(EntityAIWorkShopkeeper.SELLABLE_LIST),
        () -> () -> new ItemListModuleView(EntityAIWorkShopkeeper.SELLABLE_LIST, EntityAIWorkShopkeeper.REQUESTS_TYPE_SELLABLE_UI, false,
          (buildingView) -> ItemValueRegistry.getSellableItems()));
    
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

    public static final BuildingEntry.ModuleProducer<BuildingResort.CraftingModule,CraftingModuleView> RESORT_CRAFT         =
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

