package com.deathfrog.mctradepost.core.economy;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DerivedItemValueGeneratorTest
{
    @Test
    void authoritativeValuesAreProtectedInputsAndCanDeriveNewOutputs()
    {
        final Map<String, Integer> authoritative = Map.of("ingredient", 6, "protected-output", 8);
        final Map<String, Integer> known = DerivedItemValueGenerator.combineInputs(authoritative, Map.of());
        known.putIfAbsent("protected-output", 313);
        known.put("derived-from-ingredient", known.get("ingredient") * 2);

        final Map<String, Integer> emitted = DerivedItemValueGenerator.selectEmittedValues(
            known, authoritative.keySet(), Set.of());
        assertEquals(8, known.get("protected-output"));
        assertFalse(emitted.containsKey("protected-output"));
        assertEquals(12, emitted.get("derived-from-ingredient"));
    }

    @Test
    void explicitSeedOverridesAuthoritativeValueAndIsEmitted()
    {
        final Map<String, Integer> known = DerivedItemValueGenerator.combineInputs(
            Map.of("item", 6), Map.of("item", 10));
        assertEquals(10, known.get("item"));
        assertEquals(Map.of("item", 10), DerivedItemValueGenerator.selectEmittedValues(
            known, Set.of("item"), Set.of("item")));
    }
}
