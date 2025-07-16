package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkAnimalTrainer;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

public class JobAnimalTrainer extends AbstractJobCrafter<EntityAIWorkAnimalTrainer, JobAnimalTrainer> 
{

    public JobAnimalTrainer(ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIWorkAnimalTrainer generateAI()
    {
        return new EntityAIWorkAnimalTrainer(this);
    }
    
}
