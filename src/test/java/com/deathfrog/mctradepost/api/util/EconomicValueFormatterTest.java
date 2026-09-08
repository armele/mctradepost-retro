package com.deathfrog.mctradepost.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EconomicValueFormatterTest
{
    @Test
    void leavesSmallValuesUnchanged()
    {
        assertEquals("0\u2021", EconomicValueFormatter.compact(0));
        assertEquals("999\u2021", EconomicValueFormatter.compact(999));
        assertEquals("1000\u2021", EconomicValueFormatter.compact(1_000));
    }

    @Test
    void abbreviatesThousands()
    {
        assertEquals("1K\u2021", EconomicValueFormatter.compact(1_001));
        assertEquals("9.3K\u2021", EconomicValueFormatter.compact(9_300));
        assertEquals("93.1K\u2021", EconomicValueFormatter.compact(93_100));
        assertEquals("99.9K\u2021", EconomicValueFormatter.compact(99_900));
    }

    @Test
    void switchesToMillionsAboveNinetyNinePointNineThousand()
    {
        assertEquals("0.1M\u2021", EconomicValueFormatter.compact(99_901));
        assertEquals("1M\u2021", EconomicValueFormatter.compact(1_000_000));
        assertEquals("8M\u2021", EconomicValueFormatter.compact(8_000_000));
        assertEquals("8.3M\u2021", EconomicValueFormatter.compact(8_300_000));
        assertEquals("83.1M\u2021", EconomicValueFormatter.compact(83_100_000));
        assertEquals("100M\u2021", EconomicValueFormatter.compact(100_100_000));
    }

    @Test
    void formatsNegativeAmountsUsingTheSameMagnitudeThresholds()
    {
        assertEquals("-1000\u2021", EconomicValueFormatter.compact(-1_000));
        assertEquals("-1K\u2021", EconomicValueFormatter.compact(-1_001));
        assertEquals("-9.3K\u2021", EconomicValueFormatter.compact(-9_300));
        assertEquals("-99.9K\u2021", EconomicValueFormatter.compact(-99_900));
        assertEquals("-0.1M\u2021", EconomicValueFormatter.compact(-99_901));
        assertEquals("-8M\u2021", EconomicValueFormatter.compact(-8_000_000));
        assertEquals("-100M\u2021", EconomicValueFormatter.compact(-100_100_000));
    }

    @Test
    void supportsLongTotalsWithoutOverflow()
    {
        assertEquals("3000M\u2021", EconomicValueFormatter.compact(3_000_000_000L));
        assertEquals("9223372036855M\u2021", EconomicValueFormatter.compact(Long.MAX_VALUE));
        assertEquals("-9223372036855M\u2021", EconomicValueFormatter.compact(Long.MIN_VALUE));
    }

    @Test
    void preservesEveryDigitForExactAmounts()
    {
        assertEquals("0\u2021", EconomicValueFormatter.exact(0));
        assertEquals("99901\u2021", EconomicValueFormatter.exact(99_901));
        assertEquals("-99901\u2021", EconomicValueFormatter.exact(-99_901));
        assertEquals("9223372036854775807\u2021", EconomicValueFormatter.exact(Long.MAX_VALUE));
        assertEquals("-9223372036854775808\u2021", EconomicValueFormatter.exact(Long.MIN_VALUE));
    }
}
