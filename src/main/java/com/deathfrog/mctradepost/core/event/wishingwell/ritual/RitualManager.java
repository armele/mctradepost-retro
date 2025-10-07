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

    public static final String RITUAL_EFFECT_SLAY       = "slay";
    public static final String RITUAL_EFFECT_WEATHER    = "weather";
    public static final String RITUAL_EFFECT_SUMMON     = "summon";
    public static final String RITUAL_EFFECT_TRANSFORM  = "transform";
    public static final String RITUAL_EFFECT_OUTPOST    = "outpost";

    public RitualManager() {
        super(MCTradePostMod.GSON, RITUAL_FOLDER);
    }

    /**
     * Called when the resource manager is reloading resources. This method is
     * responsible for loading all rituals from the given jsonMap and
     * registering them in the rituals map.
     *
     * @param jsonMap a map from resource location to the json element representing
     *                the ritual.
     * @param resourceManager the resource manager that is reloading resources.
     * @param profiler the profiler to use when loading the rituals.
     */
    @Override
    protected void apply(@Nonnull Map<ResourceLocation, JsonElement> objectMap, @Nonnull  ResourceManager resourceManager, @Nonnull  ProfilerFiller profiler) {
        rituals.clear();

        MCTradePostMod.LOGGER.info("Ritual map size: {}", objectMap.size());

        objectMap.forEach((id, json) -> {
            MCTradePostMod.LOGGER.info("Decoding ritual: {}", id);

            RitualDefinition.CODEC.decode(JsonOps.INSTANCE, json).result().ifPresent(pair -> {
                RitualDefinition def = pair.getFirst();
                rituals.put(id, new RitualDefinitionHelper(id, def));
            });
        });

        MCTradePostMod.LOGGER.info("Loaded {} ritual definitions", rituals.size());
    }

    /**
     * Retrieves a ritual definition helper for the specified resource location.
     *
     * @param id the resource location of the ritual.
     * @return the ritual definition helper associated with the given resource location, 
     *         or null if no such ritual exists.
     */
    public static RitualDefinitionHelper getRitual(ResourceLocation id) {
        return rituals.get(id);
    }

    /**
     * Puts a ritual definition helper into the map of rituals.
     *
     * @param id the resource location of the ritual.
     * @param helper the ritual definition helper to store in the map.
     */
    public static void putRitual(ResourceLocation id, RitualDefinitionHelper helper) {
        rituals.put(id, helper);
    }

    /**
     * Retrieves all the ritual definitions that have been loaded.
     *
     * @return a map of resource locations to their corresponding ritual definition helpers.
     */
    public static Map<ResourceLocation, RitualDefinitionHelper> getAllRituals() {
        return rituals;
    }

    /**
     * Retrieves all the ritual definitions that have been loaded, but without the
     * helper objects that wrap them. This is useful for serializing the rituals
     * to the client, for example.
     *
     * @return a map of resource locations to their corresponding ritual definitions.
     */
    public static Map<ResourceLocation, RitualDefinition> getUnwrappedRituals() {
        Map<ResourceLocation, RitualDefinition> unwrappedRituals = new HashMap<>();
        
        rituals.forEach((id, helper) -> unwrappedRituals.put(id, helper.getDefinition()));

        return unwrappedRituals;
    }
}
