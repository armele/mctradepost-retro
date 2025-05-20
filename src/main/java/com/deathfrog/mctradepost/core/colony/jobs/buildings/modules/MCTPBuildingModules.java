package com.deathfrog.mctradepost.core.colony.jobs.buildings.modules;

import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.EconModuleView;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.ItemListModule;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
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


    // TODO: Make marketplace level 3 upgrade conditional (see Minecolonies research datapack tutorial).
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

    public static void init() {

    }
}

