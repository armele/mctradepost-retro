package com.deathfrog.mctradepost.core.rarefinds.generation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Conservative static extraction of standard item, tag, and nested-table loot entries. */
final class LootTableOutputScanner
{
    private static final Gson GSON = new Gson();
    private final ResourceManager resources;
    private final Map<ResourceLocation, Set<ResourceLocation>> cache = new HashMap<>();

    /**
     * Creates a scanner over effective server data resources.
     *
     * @param resources resolved resource manager
     */
    LootTableOutputScanner(final ResourceManager resources)
    {
        this.resources = resources;
    }

    /**
     * Statically discovers standard item outputs reachable from a loot table.
     *
     * @param table loot table ID
     * @return immutable set of possible base item IDs
     */
    Set<ResourceLocation> outputs(final ResourceLocation table)
    {
        return outputs(table, new HashSet<>());
    }

    /**
     * Resolves one table while preventing recursive table-reference cycles.
     *
     * @param table loot table being inspected
     * @param visiting recursion stack
     * @return statically discoverable item IDs
     */
    @SuppressWarnings("null")
    private Set<ResourceLocation> outputs(final ResourceLocation table, final Set<ResourceLocation> visiting)
    {
        if (cache.containsKey(table)) return cache.get(table);
        if (!visiting.add(table)) return Set.of();
        final ResourceLocation file = ResourceLocation.fromNamespaceAndPath(
            table.getNamespace(), "loot_table/" + table.getPath() + ".json");
        final Set<ResourceLocation> result = new HashSet<>();
        final java.util.Optional<Resource> resource = resources.getResource(file);
        if (resource.isPresent())
        {
            try (Reader reader = resource.get().openAsReader())
            {
                collect(GSON.fromJson(reader, JsonElement.class), result, visiting);
            }
            catch (Exception ex)
            {
                MCTradePostMod.LOGGER.warn("Unable to statically inspect loot table {}", table, ex);
            }
        }
        visiting.remove(table);
        final Set<ResourceLocation> immutable = Set.copyOf(result);
        cache.put(table, immutable);
        return immutable;
    }

    /**
     * Traverses loot JSON and handles standard item, tag, and nested-table entries.
     * Unknown or dynamic entry types are traversed conservatively.
     *
     * @param element current JSON node
     * @param items output accumulator
     * @param visiting nested-table recursion stack
     */
    @SuppressWarnings("null")
    private void collect(final JsonElement element, final Set<ResourceLocation> items,
                         final Set<ResourceLocation> visiting)
    {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray())
        {
            for (final JsonElement child : element.getAsJsonArray()) collect(child, items, visiting);
            return;
        }
        if (!element.isJsonObject()) return;
        final JsonObject object = element.getAsJsonObject();
        final String type = string(object, "type");
        final String name = string(object, "name");
        if (("minecraft:item".equals(type) || "item".equals(type)) && name != null)
        {
            final ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) items.add(id);
        }
        else if (("minecraft:tag".equals(type) || "tag".equals(type)) && name != null)
        {
            final ResourceLocation id = ResourceLocation.tryParse(name);
            if (id != null)
            {
                final TagKey<Item> tag = ItemTags.create(id);
                BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(holder ->
                    items.add(BuiltInRegistries.ITEM.getKey(holder.value()))));
            }
        }
        else if (("minecraft:loot_table".equals(type) || "loot_table".equals(type)))
        {
            String nestedName = name;
            if (nestedName == null) nestedName = string(object, "value");
            final ResourceLocation nested = nestedName == null ? null : ResourceLocation.tryParse(nestedName);
            if (nested != null) items.addAll(outputs(nested, visiting));
        }
        for (final Map.Entry<String, JsonElement> child : object.entrySet())
        {
            if (!"name".equals(child.getKey()) && !"value".equals(child.getKey())) collect(child.getValue(), items, visiting);
        }
    }

    /**
     * Reads a primitive string property when present.
     *
     * @param object containing JSON object
     * @param key property name
     * @return string value, or {@code null}
     */
    private static String string(final JsonObject object, final String key)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : null;
    }
}
