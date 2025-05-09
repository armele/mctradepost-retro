package com.deathfrog.mctradepost.core.colony.jobs;

import net.minecraft.resources.ResourceLocation;

import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

/**
 * Class of the Beekeeper job.
 */
public class JobShopkeeper extends AbstractJobCrafter<EntityAIWorkShopkeeper, JobShopkeeper>
{
    /**
     * The value where when reached the counter check returns true. 100 ticks * 4 = 1 ingame day.
     */
    public static final int COUNTER_TRIGGER = 4;

    /**
     * The counting variable.
     */
    private int counter = 0;

    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobShopkeeper(final ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Generate your AI class to register.
     * <p>
     * Suppressing Sonar Rule squid:S1452 This rule does "Generic wildcard types should not be used in return parameters" But in this case the rule does not apply because We are
     * fine with all AbstractJob implementations and need generics only for java
     *
     * @return your personal AI instance.
     */
    @Override
    public EntityAIWorkShopkeeper generateAI()
    {
        return new EntityAIWorkShopkeeper(this);
    }

    /**
     * Tick the bee interaction counter to determine the time when the interaction gets triggered.
     */
    public void tickNoBees()
    {
        if (counter < 100) // to prevent unnecessary high counter when ignored by player
        {
            counter++;
        }
    }

    /**
     * Reset the bee interaction counter.
     */
    public void resetCounter()
    {
        counter = 0;
    }

    @Override
    public ResourceLocation getModel()
    {
        return ModModelTypes.SHOPKEEPER_ID;
    }
}
