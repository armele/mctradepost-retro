package com.deathfrog.mctradepost.api.util;

import java.util.Locale;

/** Formats economic values consistently for compact labels and exact hover text. */
public final class EconomicValueFormatter
{
    private static final long MILLION_DISPLAY_THRESHOLD = 99_900;
    private static final long THOUSAND = 1_000;
    private static final long MILLION = 1_000_000;
    private static final String CURRENCY_SYMBOL = "\u2021";

    /** Prevents construction of this stateless utility. */
    private EconomicValueFormatter()
    {
    }

    /**
     * Uses K above 1,000 and M above 99,900 in magnitude. Scaled values below
     * 100 in magnitude keep at most one decimal place; larger values use whole
     * numbers, with a locale-independent decimal point.
     * @param value signed economic amount, including long-valued daily totals
     * @return compact amount with the economic currency symbol
     */
    public static String compact(final long value)
    {
        if (value > MILLION_DISPLAY_THRESHOLD || value < -MILLION_DISPLAY_THRESHOLD)
        {
            return formatScaled(value / (double) MILLION) + "M" + CURRENCY_SYMBOL;
        }
        if (value > THOUSAND || value < -THOUSAND)
        {
            return formatScaled(value / (double) THOUSAND) + "K" + CURRENCY_SYMBOL;
        }
        return exact(value);
    }

    private static String formatScaled(final double value)
    {
        final int decimalPlaces = Math.abs(value) < 100 && value != Math.floor(value) ? 1 : 0;
        final String formatted = String.format(Locale.ROOT, "%." + decimalPlaces + "f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
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
