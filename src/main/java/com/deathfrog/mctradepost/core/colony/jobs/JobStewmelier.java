package com.deathfrog.mctradepost.core.colony.jobs;

import com.deathfrog.mctradepost.core.entity.ai.workers.EntityAIWorkStewmelier;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.jobs.AbstractJob;

public class JobStewmelier extends AbstractJob<EntityAIWorkStewmelier, JobStewmelier>
{
    /**
     * The value where when reached the counter check returns true. 100 ticks * 4 = 1 ingame day.
     */
    public static final int COUNTER_TRIGGER = 4;
    protected int stewpotCounter = 0;
    protected int bowlCounter = 0;
    protected int menuCounter = 0;

    public JobStewmelier(ICitizenData entity)
    {
        super(entity);
    }

    /**
     * Generate your AI class to register.
     *
     * @return your personal AI instance.
     */
    @Override
    public EntityAIWorkStewmelier generateAI()
    {
        return new EntityAIWorkStewmelier(this);
    }

    /**
     * Tick the stewpot interaction counter to determine the time when the interaction gets triggered.
     */
    public void tickNoStewpot()
    {
        if (stewpotCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            stewpotCounter++;
        }
    }

    /**
     * Tick the bowls interaction counter to determine the time when the interaction gets triggered.
     */
    public void tickNoBowls()
    {
        if (bowlCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            bowlCounter++;
        }
    }

    /**
     * Tick the menu interaction counter to determine the time when the interaction gets triggered.
     */
    public void tickNoMenu()
    {
        if (menuCounter < 100) // to prevent unnecessary high counter when ignored by player
        {
            menuCounter++;
        }
    }

    /**
     * Reset the bowl interaction counter.
     */
    public void resetBowlCounter()
    {
        bowlCounter = 0;
    }

    /**
     * Reset the stew interaction counter.
     */
    public void resetStewpotCounter()
    {
        stewpotCounter = 0;
    }

    /**
     * Reset the menu interaction counter.
     */
    public void resetMenuCounter()
    {
        menuCounter = 0;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForStewpotInteraction()
    {
        return stewpotCounter > COUNTER_TRIGGER;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForBowlInteraction()
    {
        return bowlCounter > COUNTER_TRIGGER;
    }

    /**
     * Check if the interaction is valid/should be triggered.
     *
     * @return true if the interaction is valid/should be triggered.
     */
    public boolean checkForMenuInteraction()
    {
        return menuCounter > COUNTER_TRIGGER;
    }
}