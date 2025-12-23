package com.deathfrog.mctradepost.core.entity.ai.workers;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.jobs.JobStewmelier;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;

public class EntityAIWorkStewmelier extends AbstractEntityAIInteract<JobStewmelier, BuildingCook>
{
    public enum StewmelierState implements IAIState
    {
        FIND_POT, MAKE_STEW, GATHER_INGREDIENTS, SERVE_STEW;

        @Override
        public boolean isOkayToEat()
        {
            return true;
        }
    }

    public EntityAIWorkStewmelier(@NotNull JobStewmelier job)
    {
        super(job);
        @SuppressWarnings("unchecked")
        AITarget<IAIState>[] targets = new AITarget[]
        {
          new AITarget<IAIState>(IDLE, START_WORKING, 2),
          new AITarget<IAIState>(START_WORKING, DECIDE, 2),
          new AITarget<IAIState>(DECIDE, this::decide, 50)
        };
        super.registerTargets(targets);
        worker.setCanPickUpLoot(true);
    }

    private IAIState decide()
    {
        // TODO: Implement the logic for deciding what to do next based on the current state and job requirements.
        // For example, if the stewmelier needs to gather ingredients, return GATHER_INGREDIENTS.
        // If the stewmelier needs to make stew, return MAKE_STEW.
        return IDLE;
    }

    @Override
    public Class<BuildingCook> getExpectedBuildingClass()
    {
        return BuildingCook.class;
    }
    
}
