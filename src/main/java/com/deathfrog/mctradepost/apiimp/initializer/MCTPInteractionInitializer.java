package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;
import com.minecolonies.api.util.InventoryUtils;

import net.minecraft.network.chat.Component;

import static com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask.GREAT_VACATION;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingOutpost;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;

public class MCTPInteractionInitializer 
{
    public static final String DISCONNECTED_OUTPOST         = "com.mctradepost.outpost.disconnected";
    public static final String MISSING_OUTPOST_BUILDINGS    = "com.mctradepost.outpost.missing";
    public static final String OUTPOST_INVENTORY_FULL       = "com.mctradepost.outpost.full_inventory";

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

    }
}
