// File: com/deathfrog/mctradepost/core/economy/gen/ItemValueSeedLoader.java
package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

public final class ItemValueSeedLoader
{
    private static final @Nonnull String SEED_FILE_NAME = "item_value_seeds.json";
    private static final @Nonnull String SETTING_REPLACE = "replace";
    private static final @Nonnull String SETTING_TAG_POLICY = "tag_policy";
    private static final @Nonnull String SETTING_TAG_OVERRIDES_ITEMS = "tag_overrides_items";
    private static final @Nonnull String SECTION_TAG_VALUES = "tag_values";
    private static final @Nonnull String SECTION_VALUES = "values";
    private static final @Nonnull String TAG_PREFIX = "#";

    private static final ResourceLocation SEED_PATH = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, SEED_FILE_NAME);

    /*
     * Tag policy
     * "MIN": if an item is in multiple tags, take the cheapest
     * "MAX": take the most expensive
     * "FIRST": keep the first tag encountered (depends on load order - not recommended)
     * 
     * tag_overrides_items:
     * false means explicit values entries win
     * true means tag can overwrite explicit values (usually not desired)
     */
    private enum TagPolicy { MIN, MAX, FIRST }

    private ItemValueSeedLoader() { }

    /**
     * Loads item values from the data pack resource file {@link #SEED_FILE_NAME}.
     * This file should contain a JSON object with the following structure:
     * <pre>
     * {
     *     "replace": boolean, // if true, previous item values will be cleared
     *     "tag_policy": string, // one of "MIN", "MAX", "FIRST"
     *     "tag_overrides_items": boolean, // if true, tag values can override explicit item values
     *     "tag_values": {
     *         "<tag id>": int, // value to apply to all items in the tag
     *     },
     *     "values": {
     *         "<item id>": int, // value to apply to the item
     *     }
     * }
     * </pre>
     * The file is read from the data pack's resource directory, and the resulting item values
     * will be stored in the mod's internal map.
     *
     * @return a map containing the loaded item values
     */
    @SuppressWarnings("null")
    public static Map<Item, Integer> loadSeeds(final MinecraftServer server)
    {
        final ResourceManager rm = server.getResourceManager();

        final Map<Item, Integer> out = new HashMap<>();

        // We need RegistryAccess to resolve tags reliably
        final Registry<Item> itemRegistry = server.registryAccess().registryOrThrow(Registries.ITEM);

        final var resources = rm.getResourceStack(SEED_PATH);
        if (resources.isEmpty())
        {
            MCTradePostMod.LOGGER.warn("No seed file found at resource id {}", SEED_PATH);
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
                final boolean replace = obj.has(SETTING_REPLACE) && obj.get(SETTING_REPLACE).getAsBoolean();
                if (replace)
                {
                    out.clear();
                }

                final TagPolicy policy = parseTagPolicy(obj);
                final boolean tagOverridesItems = obj.has(SETTING_TAG_OVERRIDES_ITEMS) && obj.get(SETTING_TAG_OVERRIDES_ITEMS).getAsBoolean();

                // 1) Load explicit item values
                if (obj.has(SECTION_VALUES) && obj.get(SECTION_VALUES).isJsonObject())
                {
                    final JsonObject values = obj.getAsJsonObject(SECTION_VALUES);
                    for (final var entry : values.entrySet())
                    {
                        final String itemId = entry.getKey();
                        final int value = entry.getValue().getAsInt();

                        // Ignore tag syntax accidentally placed here
                        if (itemId.startsWith(TAG_PREFIX))
                        {
                            MCTradePostMod.LOGGER.warn("Seed file {}: 'values' contains tag key {}, move it to 'tag_values'.", SEED_PATH, itemId);
                            continue;
                        }

                        final Item item = ITEM.getOptional(ResourceLocation.tryParse(itemId)).orElse(null);
                        if (item == null)
                        {
                            MCTradePostMod.LOGGER.debug("Seed references unknown item id: {}", itemId);
                            continue;
                        }

                        out.put(item, value);
                    }
                }

                // 2) Load tag-derived values
                if (obj.has(SECTION_TAG_VALUES) && obj.get(SECTION_TAG_VALUES).isJsonObject())
                {
                    final JsonObject tagValues = obj.getAsJsonObject(SECTION_TAG_VALUES);
                    for (final var entry : tagValues.entrySet())
                    {
                        final String tagKeyStr = entry.getKey();
                        final int value = entry.getValue().getAsInt();

                        if (!tagKeyStr.startsWith(TAG_PREFIX))
                        {
                            MCTradePostMod.LOGGER.warn("Seed file {}: tag_values key must start with '#': {}", SEED_PATH, tagKeyStr);
                            continue;
                        }

                        final ResourceLocation tagId = ResourceLocation.tryParse(tagKeyStr.substring(1));
                        if (tagId == null)
                        {
                            MCTradePostMod.LOGGER.warn("Seed file {}: invalid tag id: {}", SEED_PATH, tagKeyStr);
                            continue;
                        }

                        final TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
                        final HolderSet.Named<Item> tagSet = itemRegistry.getTag(tagKey).orElse(null);

                        if (tagSet == null)
                        {
                            MCTradePostMod.LOGGER.warn("Seed file {}: tag not found: {}", SEED_PATH, tagKeyStr);
                            continue;
                        }

                        // Apply to all items in the tag
                        for (final Holder<Item> holder : tagSet)
                        {
                            final Item item = holder.value();
                            if (item == null) continue;

                            // If explicit item value exists, decide whether tag can override
                            if (!tagOverridesItems && out.containsKey(item))
                            {
                                continue;
                            }

                            applyTagPolicy(out, item, value, policy);
                        }
                    }
                }
            }
            catch (final Exception e)
            {
                MCTradePostMod.LOGGER.error("Failed reading seed values from {}", SEED_PATH, e);
            }
        }

        MCTradePostMod.LOGGER.info("Loaded {} seed item values (including tag expansion) from {}", out.size(), SEED_PATH);
        return out;
    }

    /**
     * Parses the tag policy from the given JSON object.
     * <p>
     * If the object does not contain the {@link #SETTING_TAG_POLICY} key, the default policy of {@link TagPolicy#MIN} is returned.
     * If the object does contain the key, the value is parsed as a string and converted to a {@link TagPolicy} using the {@link Enum#valueOf(String)} method.
     * If the conversion fails (for example, if the string is not a valid tag policy name), the default policy of {@link TagPolicy#MIN} is returned.
     * @param obj the JSON object to parse the tag policy from
     * @return the parsed tag policy, or the default policy of {@link TagPolicy#MIN} if parsing fails
     */
    private static TagPolicy parseTagPolicy(final JsonObject obj)
    {
        if (!obj.has(SETTING_TAG_POLICY))
        {
            return TagPolicy.MIN;
        }
        final String s = obj.get(SETTING_TAG_POLICY).getAsString();
        try
        {
            return TagPolicy.valueOf(s.trim().toUpperCase());
        }
        catch (final Exception ignored)
        {
            return TagPolicy.MIN;
        }
    }

    /**
     * Applies a tag policy to a given item value in the output map.
     * <p>
     * This method takes an item and its value, and applies the given tag policy.
     * If the item does not exist in the output map, it is added with the given value.
     * If the item does exist in the output map, the tag policy is applied to determine the new value.
     * <p>
     * The tag policies are as follows:
     * <ul>
     * <li> {@link TagPolicy#FIRST FIRST}: keeps the existing value in the output map.</li>
     * <li> {@link TagPolicy#MIN MIN}: sets the value in the output map to the minimum of the existing value and the given value.</li>
     * <li> {@link TagPolicy#MAX MAX}: sets the value in the output map to the maximum of the existing value and the given value.</li>
     * </ul>
     */
    private static void applyTagPolicy(final Map<Item, Integer> out,
                                       final Item item,
                                       final int value,
                                       final TagPolicy policy)
    {
        final Integer existing = out.get(item);
        if (existing == null)
        {
            out.put(item, value);
            return;
        }

        switch (policy)
        {
            case FIRST -> {
                // keep existing
            }
            case MIN -> out.put(item, Math.min(existing, value));
            case MAX -> out.put(item, Math.max(existing, value));
        }
    }
}