package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.EntityAIWorkStewmelier;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobStewmelier extends AbstractJob<EntityAIWorkStewmelier, JobStewmelier>
{

    public JobStewmelier(ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Generate your AI class to register.
     *
     * @return your personal AI instance.
     */
    @Override
    public EntityAIWorkStewmelier generateAI()
    {
        return new EntityAIWorkStewmelier(this);
    }

    
}