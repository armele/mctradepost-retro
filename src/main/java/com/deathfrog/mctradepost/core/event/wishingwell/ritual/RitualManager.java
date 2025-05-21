package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

public class RitualManager extends SimpleJsonResourceReloadListener {

    private static final String RITUAL_FOLDER = "rituals";
    private static final Map<ResourceLocation, RitualDefinitionHelper> rituals = new HashMap<>();

    public static final String RITUAL_EFFECT_SLAY = "slay";

    public RitualManager() {
        super(MCTradePostMod.GSON, RITUAL_FOLDER);
    }

    @Override
    protected void apply(@Nonnull Map<ResourceLocation, JsonElement> objectMap,@Nonnull  ResourceManager resourceManager,@Nonnull  ProfilerFiller profiler) {
        rituals.clear();

        /*
        objectMap.forEach((id, json) -> {
            RitualDefinition.CODEC.decode(JsonOps.INSTANCE, json).result().ifPresent(pair -> rituals.put(id, pair.getFirst()));
        });
        */

        objectMap.forEach((id, json) -> {
            RitualDefinition.CODEC.decode(JsonOps.INSTANCE, json).result().ifPresent(pair -> {
                RitualDefinition def = pair.getFirst();
                rituals.put(id, new RitualDefinitionHelper(id, def));
            });
        });

        MCTradePostMod.LOGGER.info("Loaded {} ritual definitions", rituals.size());
    }

    public static RitualDefinitionHelper getRitual(ResourceLocation id) {
        return rituals.get(id);
    }

    public static Map<ResourceLocation, RitualDefinitionHelper> getAllRituals() {
        return rituals;
    }
}
