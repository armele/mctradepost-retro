package com.deathfrog.mctradepost.core.economy;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TierDerivedItemValueProviderTest
{
    @Test
    void valueIsStableAndWithinConfiguredSpread()
    {
        final ResourceLocation id = ResourceLocation.parse("example:tiered_item");
        final int first = TierDerivedItemValueProvider.valueFor(id, 8_000);
        final int second = TierDerivedItemValueProvider.valueFor(id, 8_000);

        assertEquals(first, second);
        assertTrue(first >= 7_200);
        assertTrue(first <= 12_000);
    }

    @Test
    void minimumCutoffStillProducesPositiveValue()
    {
        final int value = TierDerivedItemValueProvider.valueFor(
            ResourceLocation.parse("example:small_cutoff"), 1);

        assertTrue(value >= 1);
    }

    @Test
    void firstPassRecipeValueWinsOverFallback()
    {
        final Map<String, Integer> merged = TierDerivedItemValueProvider.mergeWithFallbacks(
            Map.of("recipe-priced", 7_284),
            Map.of("recipe-priced", 1_090, "tier-only", 1_200));

        assertEquals(7_284, merged.get("recipe-priced"));
        assertEquals(1_200, merged.get("tier-only"));
    }
}
