package com.deathfrog.mctradepost.api.util;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import java.io.Reader;

public final class ItemValueManager extends SimplePreparableReloadListener<Map<ResourceLocation, Integer>>
{
    private static final Gson GSON = new Gson();
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "item_values.json");
    private static volatile Map<ResourceLocation, Integer> VALUES = Map.of();

    @Override
    protected Map<ResourceLocation, Integer> prepare(@Nonnull ResourceManager rm, @Nonnull ProfilerFiller profiler)
    {
        Map<ResourceLocation, Integer> merged = new HashMap<>();

        for (Resource res : rm.getResourceStack(NullnessBridge.assumeNonnull(FILE)))
        { // low priority -> high priority
            try (Reader reader = res.openAsReader())
            {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                boolean replace = root.has("replace") && root.get("replace").getAsBoolean();
                if (replace) merged.clear();

                JsonObject values = root.getAsJsonObject("values"); // key: item id, value: int
                for (Map.Entry<String, JsonElement> e : values.entrySet())
                {
                    if (e == null) continue;
                    
                    String keyString = e.getKey();
                    
                    if (keyString == null) continue;

                    ResourceLocation location = ResourceLocation.tryParse(keyString);

                    if (location == null) 
                    {
                        MCTradePostMod.LOGGER.error("Failed to load item value of {} for {} (bad item path?)", e.getValue(), keyString);
                        continue;
                    }

                    int value = 0;

                    try
                    {
                        value = e.getValue().getAsInt();
                    }
                    catch (Exception ex)
                    {
                        MCTradePostMod.LOGGER.error("Failed to load item value for {} (bad item value?): {}", location, e.getValue(), ex);
                        continue;
                    }

                    merged.put(location, value);
                }
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.error("Failed to load item values from {}", FILE, ex);
            }
        }
        return Map.copyOf(merged);
    }

    @Override
    protected void apply(@Nonnull Map<ResourceLocation, Integer> prepared,
        @Nonnull ResourceManager rm,
        @Nonnull ProfilerFiller profiler)
    {
        VALUES = prepared;
    }

    public static int get(Item item)
    {
        if (item == null) return 0;

        return VALUES.getOrDefault(BuiltInRegistries.ITEM.getKey(item), 0);
    }
}
