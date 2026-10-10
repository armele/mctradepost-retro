package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.EntityAIWorkStationMaster;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobStationMaster extends AbstractJob<EntityAIWorkStationMaster, JobStationMaster>
{
    private int gasShortageAttempts;
    private boolean gasShortage;

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

    public boolean noteGasShortage(int threshold)
    {
        gasShortage = true;
        return ++gasShortageAttempts == threshold;
    }

    public void clearGasShortage()
    {
        gasShortage = false;
        gasShortageAttempts = 0;
    }

    public boolean hasGasShortage() { return gasShortage; }

    
}
