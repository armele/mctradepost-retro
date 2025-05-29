package com.deathfrog.mctradepost.core.colony.jobs;

import net.minecraft.resources.ResourceLocation;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;


// TODO: Test - Multiplayer the non-hosting player cannot interact with the Shopkeeper (new after fixing sounds)
// TODO: Add some logic around variation of items sold.
/**
 * Class of the Shopkeeper job.
 * 
 */
public class JobShopkeeper extends AbstractJobCrafter<EntityAIWorkShopkeeper, JobShopkeeper>
{

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

    @Override
    public ResourceLocation getModel()
    {
        // MCTradePostMod.LOGGER.info("Getting JobShopkeeper model {}", ModModelTypes.SHOPKEEPER_MODEL_ID);
        // MCTradePostMod.LOGGER.warn("Model load trace", new Exception("Model trace"));
        // return ModModelTypes.SHOPKEEPER_MODEL_ID;
        return super.getModel();
    }


}
