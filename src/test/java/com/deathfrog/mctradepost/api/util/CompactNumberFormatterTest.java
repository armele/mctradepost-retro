package com.deathfrog.mctradepost.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CompactNumberFormatterTest
{
    @Test
    void leavesSmallValuesUnchanged()
    {
        assertEquals("999", CompactNumberFormatter.format(999));
        assertEquals("1000", CompactNumberFormatter.format(1_000));
    }

    @Test
    void abbreviatesThousands()
    {
        assertEquals("1K", CompactNumberFormatter.format(1_001));
        assertEquals("9.3K", CompactNumberFormatter.format(9_300));
        assertEquals("93.1K", CompactNumberFormatter.format(93_100));
        assertEquals("99.9K", CompactNumberFormatter.format(99_900));
    }

    @Test
    void switchesToMillionsAboveNinetyNinePointNineThousand()
    {
        assertEquals("0.1M", CompactNumberFormatter.format(99_901));
        assertEquals("1M", CompactNumberFormatter.format(1_000_000));
        assertEquals("8M", CompactNumberFormatter.format(8_000_000));
        assertEquals("8.3M", CompactNumberFormatter.format(8_300_000));
        assertEquals("83.1M", CompactNumberFormatter.format(83_100_000));
    }
}
