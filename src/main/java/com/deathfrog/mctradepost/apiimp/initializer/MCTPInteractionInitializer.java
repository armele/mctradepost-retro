package com.deathfrog.mctradepost.apiimp.initializer;

import com.minecolonies.api.colony.interactionhandling.InteractionValidatorRegistry;


import net.minecraft.network.chat.Component;

import static com.deathfrog.mctradepost.core.entity.ai.workers.minimal.EntityAIBurnoutTask.GREAT_VACATION;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer.VacationState;

public class MCTPInteractionInitializer {
    public static void injectInteractionHandlers() {
        InteractionValidatorRegistry.registerStandardPredicate(Component.translatable(GREAT_VACATION),
            citizen -> citizen.getColony() != null && citizen.getEntity().isPresent()
            && ((BuildingResort) citizen.getColony().getBuildingManager().getBuilding(
                    citizen.getColony().getBuildingManager().getBestBuilding(citizen.getEntity().get(), BuildingResort.class)
                )).getGuestFile(citizen.getEntity().get().getCivilianID()) != null
            && ((BuildingResort) citizen.getColony().getBuildingManager().getBuilding(
                    citizen.getColony().getBuildingManager().getBestBuilding(citizen.getEntity().get(), BuildingResort.class)
                )).getGuestFile(citizen.getEntity().get().getCivilianID()).getState() != VacationState.CHECKED_OUT
        );
    }
}
