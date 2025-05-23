package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.colony.jobs.JobGuestServices;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkGuestServices extends AbstractEntityAIInteract<JobGuestServices, BuildingResort> {
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public EntityAIWorkGuestServices(@NotNull final JobGuestServices job)
    {
        // TODO: Implement JobGuestServcies AI
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, 1),
          new AITarget(START_WORKING, DECIDE, 1),
          new AITarget(DECIDE, this::decide, 20)
        );
        worker.setCanPickUpLoot(true);
    }

    private IAIState decide()
    {
        if (!walkToBuilding())
        {
            return DECIDE;
        }

        return getState();
    }

    @Override
    public Class<BuildingResort> getExpectedBuildingClass() {
        return BuildingResort.class;
    }
}
