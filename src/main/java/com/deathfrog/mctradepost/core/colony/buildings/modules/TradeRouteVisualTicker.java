package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Advances transient vehicle handoffs between the much less frequent colony ticks. */
@EventBusSubscriber(modid = MCTradePostMod.MODID)
public final class TradeRouteVisualTicker
{
    private static final Set<ExportData> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private TradeRouteVisualTicker() { }

    static void activate(ExportData export) { ACTIVE.add(export); }
    static void deactivate(ExportData export) { ACTIVE.remove(export); }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        ACTIVE.removeIf(export -> !export.tickRouteVisualization());
    }
}
