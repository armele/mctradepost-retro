package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkAnimalTrainer;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

public class JobAnimalTrainer extends AbstractJobCrafter<EntityAIWorkAnimalTrainer, JobAnimalTrainer> 
{
    public static final int COUNTER_TRIGGER = 4;
    protected int noMarketplaceCounter = 0;
    protected int nsfCounter = 0;

    public JobAnimalTrainer(ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAIWorkAnimalTrainer generateAI()
    {
        return new EntityAIWorkAnimalTrainer(this);
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkNoMarketplaceInteraction()
    {
        return noMarketplaceCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickNoMarketplace()
    {
        if (noMarketplaceCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            noMarketplaceCounter++;
        }

        return noMarketplaceCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetNoMarketplaceCounter()
    {
        noMarketplaceCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkNSF()
    {
        return nsfCounter > COUNTER_TRIGGER;
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public int tickNSF()
    {
        if (nsfCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            nsfCounter++;
        }

        return nsfCounter;
    }

    /**
     * Reset the interaction counter.
     */
    public void resetNSFCounter()
    {
        nsfCounter = 0;
    }
}
