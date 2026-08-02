package com.deathfrog.mctradepost.core.economy;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExistingItemValueLoaderTest
{
    @Test
    void mergesLowToHighPriorityAndHonorsReplace()
    {
        final Map<ResourceLocation, Integer> merged = ExistingItemValueLoader.merge(List.of(
            document("mod/low", "{\"values\":{\"test:a\":1,\"test:b\":2}}"),
            document("mod/middle", "{\"values\":{\"test:a\":3}}"),
            document("file/high", "{\"replace\":true,\"values\":{\"test:c\":4}}")
        ));
        assertEquals(Map.of(ResourceLocation.parse("test:c"), 4), merged);
    }

    @Test
    void excludesPreviousGeneratedPackBeforeItCanReplaceValues()
    {
        final Map<ResourceLocation, Integer> merged = ExistingItemValueLoader.merge(List.of(
            document("mod/salvation", "{\"values\":{\"salvation:planks\":6}}"),
            document("file/mctp_generated", "{\"replace\":true,\"values\":{\"salvation:planks\":313}}")
        ));
        assertEquals(6, merged.get(ResourceLocation.parse("salvation:planks")));
    }

    private static ExistingItemValueLoader.ValueDocument document(final String pack, final String json)
    {
        return new ExistingItemValueLoader.ValueDocument(pack, JsonParser.parseString(json).getAsJsonObject());
    }
}
