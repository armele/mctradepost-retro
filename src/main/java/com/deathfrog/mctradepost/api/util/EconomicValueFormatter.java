package com.deathfrog.mctradepost.api.util;

import java.util.Locale;

/** Formats economic values consistently for compact labels and exact hover text. */
public final class EconomicValueFormatter
{
    private static final long COMPACT_THRESHOLD = 10_000;
    private static final long THOUSAND = 1_000;
    private static final long MILLION = 1_000_000;
    private static final String CURRENCY_SYMBOL = "\u2021";

    /** Prevents construction of this stateless utility. */
    private EconomicValueFormatter()
    {
    }

    /**
     * Uses whole thousands above 10,000 and millions rounded to one decimal place
     * above 1,000,000 in magnitude, with a locale-independent decimal point.
     * @param value signed economic amount, including long-valued daily totals
     * @return compact amount with the economic currency symbol
     */
    public static String compact(final long value)
    {
        if (value > MILLION || value < -MILLION)
        {
            return String.format(Locale.ROOT, "%.1fM", value / (double) MILLION) + CURRENCY_SYMBOL;
        }
        return value > COMPACT_THRESHOLD || value < -COMPACT_THRESHOLD
            ? value / THOUSAND + "k" + CURRENCY_SYMBOL : exact(value);
    }

    /**
     * Preserves every digit for tooltips, including amounts abbreviated in labels.
     * @param value signed economic amount
     * @return full amount with the economic currency symbol
     */
    public static String exact(final long value)
    {
        return Long.toString(value) + CURRENCY_SYMBOL;
    }
}
