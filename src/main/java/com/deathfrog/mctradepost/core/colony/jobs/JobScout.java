package com.deathfrog.mctradepost.core.colony.jobs;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkScout;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobStructure;
import com.mojang.logging.LogUtils;


public class JobScout extends AbstractJobStructure<EntityAIWorkScout, JobScout>
{

    public static final Logger LOGGER = LogUtils.getLogger();

    public JobScout(ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Generates a new instance of the AI associated with this job.
     *
     * @return a new instance of the AI associated with this job.
     */
    @Override
    public EntityAIWorkScout generateAI()
    {
        return new EntityAIWorkScout(this);
    }

}
