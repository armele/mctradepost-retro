package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED;

final class SleepAuditService
{
    private SleepAuditService()
    {
    }

    static List<SleepAuditEntry> audit(IColony colony)
    {
        List<SleepAuditEntry> entries = new ArrayList<>();

        for (ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.isAsleep() || isMilitary(citizen))
            {
                continue;
            }

            entries.add(createEntry(citizen));
        }

        return entries;
    }

    private static boolean isMilitary(ICitizenData citizen)
    {
        return citizen.getJob() instanceof AbstractJobGuard;
    }

    @SuppressWarnings("null")
    private static SleepAuditEntry createEntry(ICitizenData citizen)
    {
        Optional<AbstractEntityCitizen> entity = citizen.getEntity();
        BlockPos position = entity.map(AbstractEntityCitizen::blockPosition).orElse(citizen.getLastPosition());
        IBuilding home = citizen.getHomeBuilding();
        BlockPos homePosition = citizen.getHomePosition();
        SleepAuditStatus status;
        int distanceFromHome = -1;

        if (home == null || homePosition == null || BlockPos.ZERO.equals(homePosition))
        {
            status = SleepAuditStatus.NO_HOME;
        }
        else if (entity.isEmpty())
        {
            status = SleepAuditStatus.ENTITY_UNLOADED;
        }
        else if (home.isInBuilding(position))
        {
            status = SleepAuditStatus.AT_HOME_AWAKE;
        }
        else
        {
            status = SleepAuditStatus.TRAVELLING_HOME;
            distanceFromHome = (int) Math.round(Math.sqrt(position.distSqr(homePosition)));
        }

        return new SleepAuditEntry(citizen.getName(), jobName(citizen.getJob()), position, status, distanceFromHome, entity.orElse(null));
    }

    private static Component jobName(IJob<?> job)
    {
        if (job == null)
        {
            return Component.translatable(COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED);
        }

        return Component.translatable(job.getJobRegistryEntry().getTranslationKey() + "");
    }

    enum SleepAuditStatus
    {
        NO_HOME,
        TRAVELLING_HOME,
        AT_HOME_AWAKE,
        ENTITY_UNLOADED
    }

    static final class SleepAuditEntry
    {
        private final String citizenName;
        private final Component jobName;
        private final BlockPos position;
        private final SleepAuditStatus status;
        private final int distanceFromHome;
        private final AbstractEntityCitizen entity;

        SleepAuditEntry(String citizenName, Component jobName, BlockPos position, SleepAuditStatus status,
            int distanceFromHome, AbstractEntityCitizen entity)
        {
            this.citizenName = citizenName;
            this.jobName = jobName;
            this.position = position;
            this.status = status;
            this.distanceFromHome = distanceFromHome;
            this.entity = entity;
        }

        String citizenName()
        {
            return citizenName;
        }

        Component jobName()
        {
            return jobName;
        }

        BlockPos position()
        {
            return position;
        }

        SleepAuditStatus status()
        {
            return status;
        }

        int distanceFromHome()
        {
            return distanceFromHome;
        }

        AbstractEntityCitizen entity()
        {
            return entity;
        }
    }
}
