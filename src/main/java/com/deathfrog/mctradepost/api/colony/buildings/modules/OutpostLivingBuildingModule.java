package com.deathfrog.mctradepost.api.colony.buildings.modules;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.mojang.logging.LogUtils;

public class OutpostLivingBuildingModule extends LivingBuildingModule
{
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int OUTPOST_RESIDENTS = 3;

    @Override
    public int getModuleMax()
    {
        return OUTPOST_RESIDENTS;
    }    

    @Override
    public List<ICitizenData> getAssignedCitizen()
    {
        // LOGGER.info("OutpostLivingBuildingModule.getAssignedCitizen has {} assigned", super.getAssignedCitizen().size());
        return super.getAssignedCitizen();
    }

    @Override
    public boolean removeCitizen(@NotNull ICitizenData citizenData)
    {
        outpostGoals(false);
        return super.removeCitizen(citizenData);
    }

    @Override
    public boolean assignCitizen(ICitizenData citizenData)
    {
        outpostGoals(true);
        return super.assignCitizen(citizenData);
    }

    protected void outpostGoals(boolean livesInOutpost)
    {
        // Placeholder: If goals need to change based on outpost assignment, do it here.
    }
}
