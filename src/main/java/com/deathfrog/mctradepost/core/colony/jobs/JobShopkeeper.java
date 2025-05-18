package com.deathfrog.mctradepost.core.colony.jobs;

import net.minecraft.resources.ResourceLocation;

import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkShopkeeper;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;

/**
 * Class of the Shopkeeper job.
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
        // TODO: Fix [17May2025 13:09:20.953] [Render thread/WARN] [net.minecraft.client.renderer.texture.TextureManager/]: Failed to load texture: mctradepost:textures/entity/citizen/default/shopkeepermale1_b.png
        // java.io.FileNotFoundException: mctradepost:textures/entity/citizen/default/shopkeepermale1_b.png
        // TODO: Fix [18May2025 08:30:35.970] [Render thread/WARN] [net.minecraft.client.renderer.texture.TextureManager/]: Failed to load texture: mctradepost:textures/entity/citizen/default/shopkeepermale1_w.png
        // java.io.FileNotFoundException: mctradepost:textures/entity/citizen/default/shopkeepermale1_w.png
        
        // MCTradePostMod.LOGGER.info("Getting JobShopkeeper model {}", ModModelTypes.SHOPKEEPER_MODEL_ID);
        // MCTradePostMod.LOGGER.warn("Model load trace", new Exception("Model trace"));
        return ModModelTypes.SHOPKEEPER_MODEL_ID;
    }


}
