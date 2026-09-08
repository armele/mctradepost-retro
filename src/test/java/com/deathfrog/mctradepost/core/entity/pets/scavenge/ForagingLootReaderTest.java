package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class ForagingLootReaderTest
{
    private static JsonObject table(final String entries)
    {
        return JsonParser.parseString("{\"pools\":[{\"entries\":[" + entries + "]}]}").getAsJsonObject();
    }

    private static String item(final String name)
    {
        return "{\"type\":\"minecraft:item\",\"name\":\"" + name + "\"}";
    }

    private static String ref(final String name)
    {
        return "{\"type\":\"minecraft:loot_table\",\"value\":\"" + name + "\"}";
    }

    private static List<String> outputs(final ForagingLootReader reader, final String id, final Predicate<JsonObject> condition)
    {
        final List<String> result = new ArrayList<>();
        reader.collect(id, condition, entry -> result.add(entry.get("name").getAsString()));
        return result;
    }

    @Test
    void actualTwilightTablesRetainMaturityAndReuseParsedJson() throws Exception
    {
        final Map<String, JsonObject> tables = new HashMap<>();
        for (String name : List.of("blueberry_bush", "blueberry_bush_berries"))
        {
            try (var stream = getClass().getResourceAsStream("/foraging/" + name + ".json");
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
            {
                tables.put("twilightforest:blocks/" + name, JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
        final List<String> loads = new ArrayList<>();
        final ForagingLootReader reader = new ForagingLootReader(id -> {
            loads.add(id);
            return Optional.ofNullable(tables.get(id));
        }, message -> fail(message));
        final String root = "twilightforest:blocks/blueberry_bush";
        assertEquals(List.of("twilightforest:blueberry", "twilightforest:blueberry_bush"), outputs(reader, root, owner -> matchesAge(owner, 3)));
        assertEquals(List.of("twilightforest:blueberry_bush"), outputs(reader, root, owner -> matchesAge(owner, 2)));
        assertEquals(2, loads.size());
    }

    private static boolean matchesAge(final JsonObject owner, final int age)
    {
        if (!owner.has("conditions")) return true;
        return owner.getAsJsonArray("conditions").asList().stream().allMatch(condition ->
            condition.getAsJsonObject().getAsJsonObject("properties").get("age").getAsInt() == age);
    }

    @Test
    void referencedPoolConditionsUseEachTraversalsSourceState()
    {
        final JsonObject nested = table(item("test:fruit"));
        nested.getAsJsonArray("pools").get(0).getAsJsonObject().add("conditions",
            JsonParser.parseString("[{\"properties\":{\"age\":\"3\"}}]"));
        final ForagingLootReader reader = new ForagingLootReader(id -> Optional.of(
            id.equals("test:root") ? table(ref("test:nested")) : nested), message -> fail(message));
        assertEquals(List.of(), outputs(reader, "test:root", owner -> matchesAge(owner, 2)));
        assertEquals(List.of("test:fruit"), outputs(reader, "test:root", owner -> matchesAge(owner, 3)));
    }

    @Test
    void cyclesAndBrokenReferencesPreserveOtherBranchesAndCacheFailures()
    {
        final Map<String, JsonObject> tables = Map.of(
            "test:a", table(ref("test:a") + "," + ref("test:b") + "," + ref("test:b") + "," + ref("test:missing") + "," + ref("test:bad") + "," + ref("test:bad")),
            "test:b", table(ref("test:a") + "," + ref("test:c")),
            "test:c", table(item("test:fruit")));
        final List<String> loads = new ArrayList<>();
        final List<String> diagnostics = new ArrayList<>();
        final ForagingLootReader reader = new ForagingLootReader(id -> {
            loads.add(id);
            if (id.equals("test:bad")) throw new IllegalArgumentException("invalid JSON");
            return Optional.ofNullable(tables.get(id));
        }, diagnostics::add);
        assertEquals(List.of("test:fruit", "test:fruit"), outputs(reader, "test:a", owner -> true));
        assertEquals(5, loads.size());
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("Circular")));
        final int count = diagnostics.size();
        outputs(reader, "test:a", owner -> true);
        assertEquals(count, diagnostics.size());
        assertEquals(5, loads.size());
    }

    @Test
    void inlineTablesAndCompositeEntriesPreserveConditions()
    {
        final JsonObject root = table("{\"type\":\"minecraft:alternatives\",\"children\":["
            + item("test:direct") + ",{\"type\":\"minecraft:loot_table\",\"value\":"
            + table(item("test:inline")) + "}, {\"type\":\"minecraft:item\",\"conditions\":[],\"name\":\"test:excluded\"}]}");
        final ForagingLootReader reader = new ForagingLootReader(id -> Optional.of(root), message -> fail(message));
        assertEquals(List.of("test:direct", "test:inline"), outputs(reader, "test:root", owner -> !owner.has("conditions")));
    }

    @Test
    void boundsDeepReferencesAndKeepsSiblingItems()
    {
        final List<String> diagnostics = new ArrayList<>();
        final ForagingLootReader reader = new ForagingLootReader(id -> {
            final int index = Integer.parseInt(id.substring("test:".length()));
            return Optional.of(table(ref("test:" + (index + 1)) + "," + item("test:fruit")));
        }, diagnostics::add);
        final List<String> result = outputs(reader, "test:0", owner -> true);
        assertFalse(result.isEmpty());
        assertTrue(result.size() <= 64);
        assertTrue(diagnostics.stream().anyMatch(message -> message.contains("limit")));
    }

    @Test
    void malformedEntryDoesNotDiscardValidSibling()
    {
        final ForagingLootReader reader = new ForagingLootReader(id -> Optional.of(table(
            "{\"type\":{}}," + item("test:valid"))), message -> {});
        assertEquals(List.of("test:valid"), outputs(reader, "test:root", owner -> true));
    }
}
