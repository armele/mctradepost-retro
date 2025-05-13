package com.deathfrog.mctradepost.core.colony.jobs.buildings.modules;

import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.moduleviews.*;



public class BuildingModules
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
    public static final BuildingEntry.ModuleProducer<CraftingWorkerBuildingModule,WorkerBuildingModuleView> SHOPKEEPER_WORK          =
      new BuildingEntry.ModuleProducer<>("shopkeeper_work", () -> new CraftingWorkerBuildingModule(MCTPModJobs.shopkeeper.get(), Skill.Dexterity, Skill.Mana, false, (b) -> 1),
        () -> WorkerBuildingModuleView::new);
    

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

