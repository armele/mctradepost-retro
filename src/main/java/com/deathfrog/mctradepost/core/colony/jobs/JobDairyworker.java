package com.deathfrog.mctradepost.core.colony.jobs;

import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import com.deathfrog.mctradepost.core.entity.ai.workers.crafting.EntityAIWorkDairyworker;
import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;

import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;

/**
 * The fisherman's job class. implements some useful things for him.
 */
public class JobDairyworker extends AbstractJobCrafter<EntityAIWorkDairyworker, JobDairyworker>
{
    /**
     * The value where when reached the counter check returns true. 100 ticks * 4 = 1 ingame day.
     */
    public static final int COUNTER_TRIGGER = 4;
    protected int cowCounter = 0;

    /**
     * Initializes the job class.
     *
     * @param entity The entity which will use this job class.
     */
    public JobDairyworker(final ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Tick the bee interaction counter to determine the time when the interaction gets triggered.
     */
    public void tickNoCows()
    {
        if (cowCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            cowCounter++;
        }
    }

    /**
     * Reset the bee interaction counter.
     */
    public void resetCounter()
    {
        cowCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForCowInteraction()
    {
        return cowCounter > COUNTER_TRIGGER;
    }

    /**
     * Get the RenderBipedCitizen.Model to use when the Citizen performs this job role.
     *
     * @return Model of the citizen.
     */
    @NotNull
    @Override
    public ResourceLocation getModel()
    {
        return ModModelTypes.BAKER_ID;
    }

    /**
     * Generate your AI class to register.
     *
     * @return your personal AI instance.
     */
    @NotNull
    @Override
    public EntityAIWorkDairyworker generateAI()
    {
        return new EntityAIWorkDairyworker(this);
    }

    @Override
    public void playSound(final BlockPos blockPos, final EntityCitizen worker)
    {
        worker.queueSound(SoundEvents.FIRECHARGE_USE, blockPos, 10, 0, 0.5f, 0.1f);
    }
}
