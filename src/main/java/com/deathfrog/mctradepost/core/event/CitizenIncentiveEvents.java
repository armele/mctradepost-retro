package com.deathfrog.mctradepost.core.event;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.CitizenIncentiveModule;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.ColonyState;
import com.minecolonies.api.colony.IColony;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Building ticks require a loaded hut chunk, but a colony-wide payroll must not. */
@EventBusSubscriber(modid = MCTradePostMod.MODID)
public final class CitizenIncentiveEvents
{
    /** Prevents instantiation of the static server event subscriber. */
    private CitizenIncentiveEvents() { }

    /**
     * Checks active colonies once per second, including those whose Town Hall chunk is unloaded.
     * @param event completed level tick; client ticks are ignored
     */
    @SubscribeEvent
    public static void onLevelTick(final LevelTickEvent.Post event)
    {
        final Level level = event.getLevel();
        if (level.isClientSide || level.getGameTime() % 20 != 0) return;
        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.getState() != ColonyState.ACTIVE) continue;
            final ITownHall townHall = colony.getServerBuildingManager().getTownHall();
            if (townHall != null && townHall.hasModule(CitizenIncentiveModule.class))
            {
                townHall.getModule(CitizenIncentiveModule.class).onColonyTick(colony);
            }
        }
    }
}
