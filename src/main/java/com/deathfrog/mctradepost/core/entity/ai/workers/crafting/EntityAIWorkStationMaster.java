package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkStationMaster extends AbstractEntityAIInteract<JobStationMaster, BuildingStation>
{

    public EntityAIWorkStationMaster(@NotNull JobStationMaster job)
    {
        super(job);
        super.registerTargets(
          new AITarget<IAIState>(IDLE, START_WORKING, 10),
          new AITarget<IAIState>(START_WORKING, DECIDE, 10),
          new AITarget<IAIState>(DECIDE, this::decideWhatToDo, 10)
        );
        worker.setCanPickUpLoot(true);
    }

    protected IAIState decideWhatToDo()
    {
        EntityNavigationUtils.walkToRandomPos(worker, 10, 0.6D);
        return AIWorkerState.IDLE;
    }

    @Override
    public Class<BuildingStation> getExpectedBuildingClass()
    {
        return BuildingStation.class;
    }
    
}
