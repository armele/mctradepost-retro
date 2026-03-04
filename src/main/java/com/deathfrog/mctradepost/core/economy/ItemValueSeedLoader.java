package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

/**
 * Loads seed values from datapacks.
 *
 * Schema matches your item_values.json:
 * { "replace": false, "values": { "minecraft:stick": 1, ... } }
 *
 * Default path: data/<modid>/item_value_seeds.json
 */
public final class ItemValueSeedLoader
{
    @SuppressWarnings("null")
    private static final @Nonnull ResourceLocation SEED_PATH = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "item_value_seeds.json");

    private ItemValueSeedLoader() { }

    public static Map<Item, Integer> loadSeeds(final MinecraftServer server)
    {
        final ResourceManager rm = server.getResourceManager();

        // Supports multiple datapacks with stacking behavior (honors "replace")
        final Map<Item, Integer> out = new HashMap<>();

        final String fullPath = "data/" + SEED_PATH.getNamespace() + "/" + SEED_PATH.getPath();
        final var resources = rm.getResourceStack(SEED_PATH);

        if (resources.isEmpty())
        {
            MCTradePostMod.LOGGER.warn("No seed file found at {} (expected resource id {})", fullPath, SEED_PATH);
            return out;
        }

        for (final Resource res : resources)
        {
            try (var reader = new InputStreamReader(res.open(), StandardCharsets.UTF_8))
            {
                final JsonElement elem = JsonParser.parseReader(reader);
                if (!elem.isJsonObject())
                {
                    continue;
                }

                final JsonObject obj = elem.getAsJsonObject();
                final boolean replace = obj.has("replace") && obj.get("replace").getAsBoolean();
                if (replace)
                {
                    out.clear();
                }

                if (!obj.has("values") || !obj.get("values").isJsonObject())
                {
                    continue;
                }

                final JsonObject values = obj.getAsJsonObject("values");
                for (final var entry : values.entrySet())
                {
                    final String itemId = entry.getKey();
                    final int value = entry.getValue().getAsInt();

                    if (itemId == null) continue;

                    final Item item = ITEM.getOptional(ResourceLocation.tryParse(itemId)).orElse(null);
                    if (item == null)
                    {
                        MCTradePostMod.LOGGER.debug("Seed references unknown item id: {}", itemId);
                        continue;
                    }

                    out.put(item, value);
                }
            }
            catch (final Exception e)
            {
                MCTradePostMod.LOGGER.error("Failed reading seed values from {}", SEED_PATH, e);
            }
        }

        MCTradePostMod.LOGGER.info("Loaded {} seed item values from {}", out.size(), SEED_PATH);
        return out;
    }
}