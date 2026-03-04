package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.Map;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.jobs.JobBaker;
import com.minecolonies.core.colony.jobs.JobBlacksmith;
import com.minecolonies.core.colony.jobs.JobBuilder;
import com.minecolonies.core.colony.jobs.JobChef;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.colony.jobs.JobHealer;
import com.minecolonies.core.colony.jobs.JobSawmill;
import com.minecolonies.core.colony.jobs.JobStonemason;
import com.minecolonies.core.colony.jobs.JobUndertaker;

import net.minecraft.core.BlockPos;

public class HomeAssignment
{
    private final ICitizenData citizen;
    private final int jobPriority;

    protected static final Map<Class<? extends IJob<?>>, Integer> JOB_PRIORITIES = Map.ofEntries(
        Map.entry(JobChef.class, 100),
        Map.entry(JobCook.class, 80),
        Map.entry(JobBuilder.class, 70),
        Map.entry(JobHealer.class, 60),
        Map.entry(JobDeliveryman.class, 50),
        Map.entry(JobFarmer.class, 45),
        Map.entry(JobBaker.class, 40),
        Map.entry(JobUndertaker.class, 35),
        Map.entry(JobBlacksmith.class, 30),
        Map.entry(JobStonemason.class, 25),
        Map.entry(JobSawmill.class, 20)
    );

    public HomeAssignment(ICitizenData citizen)
    {
        this.citizen = citizen;

        IBuilding workBuilding = citizen.getWorkBuilding();

        if (workBuilding != null)
        {
            jobPriority = JOB_PRIORITIES.getOrDefault(citizen.getJob().getClass(), 10);
        }
        else
        {
            jobPriority = 0;
        }
    }

    public ICitizenData getCitizen()
    {
        return citizen;
    }

    public int getJobPriority()
    {
        return jobPriority;
    }

    /**
     * Returns the BlockPos of the citizen's workplace. If the citizen is a deliveryman, this will be the BlockPos of the warehouse
     * they are assigned to (if any). Otherwise, this will be the BlockPos of the citizen's work building.
     * 
     * @return The BlockPos of the citizen's workplace, or null if the citizen does not have a work building or is a deliveryman
     *         without a warehouse.
     */
    public BlockPos getWorkPos()
    {
        if (citizen.getWorkBuilding() == null)
        {
            return null;
        }

        if (citizen.getJob() instanceof JobDeliveryman deliveryJob)
        {
            IWareHouse warehouse = deliveryJob.findWareHouse();

            if (warehouse != null)
            {
                return warehouse.getPosition();
            }
        }

        return citizen.getWorkBuilding().getPosition();
    }

    public BlockPos getCurrentHomePos()
    {
        return citizen.getHomePosition();
    }

    public int getDistanceToWork()
    {
        BlockPos workPos = getWorkPos();
        BlockPos currentHomePos = getCurrentHomePos();

        if (workPos == null || currentHomePos == null)
        {
            return Integer.MAX_VALUE;
        }
        return BlockPosUtil.distManhattan(workPos, currentHomePos);
    }
}
