package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCowboy;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingKitchen;

import net.minecraft.network.chat.Component;

import static com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask.GREAT_VACATION;

import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobDairyworker;
import com.deathfrog.mctradepost.core.colony.jobs.JobShopkeeper;
import com.deathfrog.mctradepost.core.colony.jobs.JobStewmelier;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;

public class MCTPInteractionInitializer 
{
    public static final String DISCONNECTED_OUTPOST         = "com.mctradepost.outpost.disconnected";
    public static final String MISSING_OUTPOST_BUILDINGS    = "com.mctradepost.outpost.missing";
    public static final String OUTPOST_INVENTORY_FULL       = "com.mctradepost.outpost.full_inventory";
    public static final String NO_COWS                      = "entity.dairyworker.nocows";
    public static final String NO_STEWPOT                   = "entity.stewmelier.nostewpot";
    public static final String NO_INGREDIENTS               = "entity.stewmelier.noingredients";
    public static final String NO_BOWLS                     = "entity.stewmelier.nobowls";
    public static final String NOT_ON_MENU                  = "entity.stewmelier.onmenu";
    public static final String NO_SHOP_INVENTORY            = "entity.shopkeeper.noinventory";
    public static final String NO_SALE_ITEMS                = "entity.shopkeeper.noitems";

    public static void injectInteractionHandlers() 
    {
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREAT_VACATION),
            citizen -> citizen.getColony() != null && citizen.getEntity().isPresent()
            && ((BuildingResort) citizen.getColony().getBuildingManager().getBuilding(
                    citizen.getColony().getBuildingManager().getBestBuilding(citizen.getEntity().get(), BuildingResort.class)
                )).getGuestFile(citizen.getEntity().get().getCivilianID()) != null
            && ((BuildingResort) citizen.getColony().getBuildingManager().getBuilding(
                    citizen.getColony().getBuildingManager().getBestBuilding(citizen.getEntity().get(), BuildingResort.class)
                )).getGuestFile(citizen.getEntity().get().getCivilianID()).getState() != VacationState.CHECKED_OUT
            && (citizen.getStatus() == EntityAIBurnoutTask.VACATION_STATUS)
        );

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(DISCONNECTED_OUTPOST),
            citizen -> citizen.getColony() != null 
            && citizen.getEntity().isPresent()
            && citizen.getWorkBuilding() != null 
            && ((citizen.getWorkBuilding() instanceof BuildingOutpost workOutpost && workOutpost.isDisconnected())
                || (citizen.getWorkBuilding().getParent() != null 
                    && citizen.getWorkBuilding().getColony().getBuildingManager().getBuilding(citizen.getWorkBuilding().getParent()) instanceof BuildingOutpost workOutpostParent
                    && workOutpostParent.isDisconnected())
            )
        );

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(OUTPOST_INVENTORY_FULL),
            citizen -> citizen.getColony() != null 
            && citizen.getEntity().isPresent()
            && citizen.getWorkBuilding() != null 
            && (citizen.getWorkBuilding() instanceof BuildingOutpost workOutpost && InventoryUtils.isBuildingFull(workOutpost))
        );

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_COWS),
          citizen -> citizen.getWorkBuilding() instanceof BuildingCowboy && citizen.getJob(JobDairyworker.class).checkForCowInteraction());

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_STEWPOT),
          citizen -> citizen.getWorkBuilding() instanceof BuildingKitchen && citizen.getJob(JobStewmelier.class).checkForStewpotInteraction());

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_INGREDIENTS),
          citizen -> citizen.getWorkBuilding() instanceof BuildingKitchen 
          && citizen.getWorkBuilding() != null 
          && citizen.getWorkBuilding().getModule(MCTPBuildingModules.STEWMELIER_INGREDIENTS).ingredientCount() == 0);

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_BOWLS),
          citizen -> citizen.getWorkBuilding() instanceof BuildingKitchen && citizen.getJob(JobStewmelier.class).checkForStewpotInteraction());

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NOT_ON_MENU),
          citizen -> citizen.getWorkBuilding() instanceof BuildingKitchen && citizen.getJob(JobStewmelier.class).checkForMenuInteraction());

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_SHOP_INVENTORY),
          citizen -> citizen.getWorkBuilding() instanceof BuildingMarketplace && citizen.getJob(JobShopkeeper.class).checkForInventoryManagementInteraction());

        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(NO_SALE_ITEMS),
          citizen -> citizen.getWorkBuilding() instanceof BuildingMarketplace && citizen.getJob(JobShopkeeper.class).checkForSaleItemsInteraction());
    }
}
