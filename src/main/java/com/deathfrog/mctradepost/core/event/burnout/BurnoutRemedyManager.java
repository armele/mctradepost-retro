package com.deathfrog.mctradepost.core.event.burnout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.crafting.ItemStorage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public class BurnoutRemedyManager extends SimpleJsonResourceReloadListener {
    public static final String BURNOUT_FOLDER = "burnout";
    private static final Map<ResourceLocation, BurnoutRemedy> burnoutMap = new HashMap<>();

    public BurnoutRemedyManager() {
        super(MCTradePostMod.GSON, BURNOUT_FOLDER);
    }

    public record BurnoutRemedy(List<ItemEntry> remedy, String message) {

        public record ItemEntry(ResourceLocation id, int count) {
            public ItemStorage toStack() {
                Item item = BuiltInRegistries.ITEM.get(id);
                return new ItemStorage(new ItemStack(item, count));
            }
        }

        public List<ItemStorage> toItemStorage() {
            return remedy.stream().map(ItemEntry::toStack).toList();
        }
    }

    /**
     * Called when the resource manager is reloading resources. This method is
     * responsible for loading all burnout remedies from the given jsonMap and
     * registering them in the burnoutMap.
     *
     * @param jsonMap a map from resource location to the json element representing
     *                the burnout remedy.
     * @param resourceManager the resource manager that is reloading resources.
     * @param profiler the profiler to use when loading the remedies.
     */
    @Override
    protected void apply(@Nonnull Map<ResourceLocation, com.google.gson.JsonElement> jsonMap, @Nonnull ResourceManager resourceManager, @Nonnull ProfilerFiller profiler) {
        burnoutMap.clear();
        for (Map.Entry<ResourceLocation, com.google.gson.JsonElement> entry : jsonMap.entrySet()) {
            try {
                // MCTradePostMod.LOGGER.info("Loading burnout remedy: {} with value: {}", entry.getKey(), entry.getValue());
                BurnoutRemedy remedy = MCTradePostMod.GSON.fromJson(entry.getValue(), new com.google.gson.reflect.TypeToken<BurnoutRemedy>(){}.getType());
                burnoutMap.put(entry.getKey(), remedy);
            } catch (Exception e) {
                MCTradePostMod.LOGGER.error("Failed to load resort remedy: {}", entry.getKey(), e);
            }
        }
        MCTradePostMod.LOGGER.info("Loaded {} burnout remedies.", burnoutMap.size());
    }

    /**
     * Listens for the AddReloadListenerEvent to add the BurnoutRemedyManager
     * as a reload listener. This ensures that burnout remedies are reloaded
     * whenever the server resources are reloaded.
     *
     * @param event the event that triggers the addition of the reload listener.
     */
    @SubscribeEvent
    public static void listenForBurnoutRecords(@Nonnull final AddReloadListenerEvent event) {
        event.addListener(new BurnoutRemedyManager());
    }

    /**
     * Get the burnout remedy for the given skill.
     *
     * @param skill the skill for which we want the burnout remedy.
     * @return the burnout remedy for the given skill, or null if there is no such remedy.
     */
    public static  List<ItemStorage>  getRemedy(Skill skill) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, skill.toString().toLowerCase());
        return burnoutMap.get(id).toItemStorage();
    }
}
