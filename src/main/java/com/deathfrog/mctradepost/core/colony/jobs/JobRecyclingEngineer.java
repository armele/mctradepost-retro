package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkRecyclingEngineer;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

import net.minecraft.resources.ResourceLocation;

public class JobRecyclingEngineer extends AbstractJobCrafter<EntityAIWorkRecyclingEngineer, JobRecyclingEngineer> {

    public JobRecyclingEngineer(ICitizenData entity) {
        super(entity);
    }

    @Override
    public EntityAIWorkRecyclingEngineer generateAI() {
        return new EntityAIWorkRecyclingEngineer(this);
    }
    
    @Override
    public ResourceLocation getModel()
    {
        // return ModModelTypes.RECYCLINGENGINEER_MODEL_ID;
        return super.getModel();
    }
}