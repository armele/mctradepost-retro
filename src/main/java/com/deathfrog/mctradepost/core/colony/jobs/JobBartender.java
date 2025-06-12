package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkBartender;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

import net.minecraft.resources.ResourceLocation;

public class JobBartender extends AbstractJobCrafter<EntityAIWorkBartender, JobBartender> 
{

    public JobBartender(ICitizenData entity) 
    {
        super(entity);
    }

    @Override
    public EntityAIWorkBartender generateAI() 
    {
        return new EntityAIWorkBartender(this);
    }
    
    @Override
    public ResourceLocation getModel()
    {
        return super.getModel();
    }
}
