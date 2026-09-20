package com.deathfrog.mctradepost.core.event.wishingwell.resurrection;

import com.minecolonies.api.eventbus.events.colony.citizens.CitizenBuriedModEvent;

import net.minecraft.server.level.ServerLevel;

/** Captures the saved citizen data after the Undertaker has exhausted its resurrection attempt. */
public final class BurialEventHandler
{
    private BurialEventHandler() {}

    public static void onCitizenBuried(CitizenBuriedModEvent event)
    {
        if (event.getColony().getWorld() instanceof ServerLevel level)
        {
            BuriedCitizenRegistry.get(level).add(event.getColony().getID(), event.getGravePosition(),
                event.getSavedCitizenNbt(), event.getCitizenName(), event.getCitizenJobName(), event.getBurialDay());
        }
    }
}
