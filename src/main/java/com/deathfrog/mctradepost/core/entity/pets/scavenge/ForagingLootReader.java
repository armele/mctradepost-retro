package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Build-scoped JSON cache and bounded traversal of possible forage item entries. */
final class ForagingLootReader
{
    private static final int MAX_DEPTH = 64;
    private final Function<String, Optional<JsonObject>> loader;
    private final Consumer<String> diagnostic;
    private final Map<String, Optional<JsonObject>> tables = new HashMap<>();
    private final Set<String> reported = new HashSet<>();

    /**
     * Creates a reader whose parsed-table cache and diagnostic history last for one build.
     *
     * @param loader loads a table by its namespaced ID, returning empty when unavailable
     * @param diagnostic receives each distinct warning at most once per reader
     */
    ForagingLootReader(final Function<String, Optional<JsonObject>> loader, final Consumer<String> diagnostic)
    {
        this.loader = loader;
        this.diagnostic = diagnostic;
    }

    /**
     * Collects possible item entries with a fresh traversal path while reusing cached JSON.
     * Conditions are evaluated again for this call, allowing different source states to
     * share the parsed tables without sharing their eligible outputs.
     *
     * @param id namespaced ID of the root loot table
     * @param conditions evaluates pool and entry conditions for the current source state
     * @param item receives each eligible item entry, including repeated occurrences
     */
    void collect(final String id, final Predicate<JsonObject> conditions, final Consumer<JsonObject> item)
    {
        visit(id, conditions, item, new HashSet<>(), 0);
    }

    /**
     * Reports a warning only if this reader has not already reported the same text.
     *
     * @param message diagnostic text describing a skipped or unreadable branch
     */
    private void warn(final String message)
    {
        if (reported.add(message)) diagnostic.accept(message);
    }

    /**
     * Visits a named table, caching successful loads and failures and rejecting cycles
     * or excessive nesting. The ID is removed from the active path on return so other
     * branches can independently visit the same table.
     *
     * @param id namespaced ID of the table to load
     * @param conditions evaluates conditions using the original source state
     * @param item receives eligible item entries
     * @param active named table IDs on the current recursion path
     * @param depth current traversal depth, including entry-array nesting
     */
    private void visit(final String id, final Predicate<JsonObject> conditions, final Consumer<JsonObject> item,
        final Set<String> active, final int depth)
    {
        if (depth >= MAX_DEPTH) { warn("Foraging loot nesting limit reached: " + id); return; }
        if (!active.add(id)) { warn("Circular foraging loot reference: " + id); return; }
        try
        {
            final Optional<JsonObject> table = tables.computeIfAbsent(id, key -> {
                try { return loader.apply(key); }
                catch (RuntimeException exception)
                {
                    warn("Malformed foraging loot table: " + key);
                    return Optional.empty();
                }
            });
            if (table.isPresent()) table(table.get(), conditions, item, active, depth);
            else warn("Missing or unreadable foraging loot table: " + id);
        }
        finally { active.remove(id); }
    }

    /**
     * Traverses the eligible pools of a named or inline table. Invalid pools are
     * skipped without preventing traversal of subsequent pools.
     *
     * @param table parsed loot-table object
     * @param conditions evaluates pool and entry conditions for the source state
     * @param item receives eligible item entries
     * @param active named table IDs on the current recursion path
     * @param depth current traversal depth before entering this table's entry arrays
     */
    private void table(final JsonObject table, final Predicate<JsonObject> conditions, final Consumer<JsonObject> item,
        final Set<String> active, final int depth)
    {
        final JsonArray pools = array(table, "pools");
        if (pools == null) return;
        for (JsonElement element : pools)
        {
            if (!element.isJsonObject()) continue;
            final JsonObject pool = element.getAsJsonObject();
            try
            {
                if (conditions.test(pool)) entries(array(pool, "entries"), conditions, item, active, depth + 1);
            }
            catch (RuntimeException exception) { warn("Malformed foraging loot pool: " + active); }
        }
    }

    /**
     * Traverses an entry array, emitting direct items and following named tables,
     * inline tables, and composite entry arrays. Conditions retain the original
     * source context; malformed entries are skipped and nesting is bounded.
     *
     * @param entries entry array to inspect, or {@code null} when absent
     * @param conditions evaluates conditions for the source state
     * @param item receives eligible item entries
     * @param active named table IDs on the current recursion path
     * @param depth current traversal depth used to enforce the nesting limit
     */
    private void entries(final JsonArray entries, final Predicate<JsonObject> conditions, final Consumer<JsonObject> item,
        final Set<String> active, final int depth)
    {
        if (entries == null) return;
        if (depth >= MAX_DEPTH) { warn("Foraging loot nesting limit reached in entries"); return; }
        for (JsonElement element : entries)
        {
            if (!element.isJsonObject()) continue;
            try
            {
                final JsonObject entry = element.getAsJsonObject();
                if (!conditions.test(entry)) continue;
                final String type = entry.has("type") ? entry.get("type").getAsString() : "";
                if ("minecraft:item".equals(type) || "item".equals(type)) item.accept(entry);
                else if ("minecraft:loot_table".equals(type) || "loot_table".equals(type))
                {
                    final JsonElement value = entry.get("value");
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString())
                    {
                        final String id = value.getAsString();
                        visit(id.contains(":") ? id : "minecraft:" + id, conditions, item, active, depth + 1);
                    }
                    else if (value != null && value.isJsonObject()) table(value.getAsJsonObject(), conditions, item, active, depth + 1);
                }
                else
                {
                    entries(array(entry, "children"), conditions, item, active, depth + 1);
                    entries(array(entry, "entries"), conditions, item, active, depth + 1);
                }
            }
            catch (RuntimeException exception) { warn("Malformed foraging loot entry: " + active); }
        }
    }

    /**
     * Reads an optional array member without casting non-array values.
     *
     * @param owner JSON object containing the member
     * @param key member name to inspect
     * @return the array, or {@code null} when the member is absent or not an array
     */
    private static JsonArray array(final JsonObject owner, final String key)
    {
        final JsonElement value = owner.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }
}
