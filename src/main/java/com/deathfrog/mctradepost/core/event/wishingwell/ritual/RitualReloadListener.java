package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import net.neoforged.neoforge.event.AddReloadListenerEvent;
import com.deathfrog.mctradepost.MCTradePostMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class RitualReloadListener
{
    @SubscribeEvent
    public static void onReloadListenerRegistration(AddReloadListenerEvent event)
    {
        event.addListener(new RitualManager());
        MCTradePostMod.LOGGER.info("Registered RitualManager as a datapack reload listener.");
    }
}
