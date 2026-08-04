package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Reads generator inputs without depending on live world or Marketplace state. */
final class GenerationDataReader
{
    private static final Gson GSON = new Gson();

    /** Prevents instantiation of this utility class. */
    private GenerationDataReader() { }

    /**
     * Merges loaded item-value resources using their normal datapack precedence.
     *
     * @param server source of the resolved server resource stack
     * @return item IDs mapped to their unscaled configured values
     */
    static Map<ResourceLocation, Integer> itemValues(final MinecraftServer server)
    {
        final ResourceLocation file = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "item_values.json");
        final Map<ResourceLocation, Integer> result = new HashMap<>();

        if (file == null) return result;

        for (final Resource resource : server.getResourceManager().getResourceStack(file))
        {
            try (Reader reader = resource.openAsReader())
            {
                final JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) continue;
                if (root.has("replace") && root.get("replace").getAsBoolean()) result.clear();
                final JsonObject values = root.getAsJsonObject("values");
                if (values == null) continue;
                for (final Map.Entry<String, JsonElement> entry : values.entrySet())
                {
                    @SuppressWarnings("null")
                    final ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id != null) result.put(id, entry.getValue().getAsInt());
                }
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to read item values for Rare Finds generation", ex);
            }
        }
        return result;
    }
}
