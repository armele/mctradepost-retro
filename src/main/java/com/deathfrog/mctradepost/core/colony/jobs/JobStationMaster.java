package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkStationMaster;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobStationMaster extends AbstractJob<EntityAIWorkStationMaster, JobStationMaster>
{

    public JobStationMaster(ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Generate your AI class to register.
     *
     * @return your personal AI instance.
     */
    @Override
    public EntityAIWorkStationMaster generateAI()
    {
        return new EntityAIWorkStationMaster(this);
    }

    
}