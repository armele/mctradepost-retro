package com.deathfrog.mctradepost.core.entity.ai.workers.crafting;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.colony.buildings.BuildingStation;
import com.deathfrog.mctradepost.core.colony.jobs.JobStationMaster;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;

public class EntityAIWorkStationMaster extends AbstractEntityAIInteract<JobStationMaster, BuildingStation>
{

    public EntityAIWorkStationMaster(@NotNull JobStationMaster job)
    {
        super(job);
    }

    @Override
    public Class<BuildingStation> getExpectedBuildingClass()
    {
        return BuildingStation.class;
    }
    
}
