package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.MCTradePostMod;
import net.neoforged.bus.api.SubscribeEvent;

public class RitualReloadListener {
    
    @SubscribeEvent
    public static void onReloadListenerRegistration(@NotNull final AddReloadListenerEvent event) {
        event.addListener(new RitualManager());
        MCTradePostMod.LOGGER.info("Registered RitualManager as a datapack reload listener.");
    }
}
