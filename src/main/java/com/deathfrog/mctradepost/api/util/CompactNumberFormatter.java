package com.deathfrog.mctradepost.api.util;

import java.util.Locale;

import javax.annotation.Nonnull;

/** Formats large, non-negative GUI values without crowding neighboring controls. */
public final class CompactNumberFormatter
{
    private static final int THOUSAND = 1_000;
    private static final int MILLION = 1_000_000;
    private static final int MILLION_DISPLAY_THRESHOLD = 99_900;

    private CompactNumberFormatter()
    {
    }

    /**
     * Uses K for values above one thousand and M for values above 99.9K.
     * The result keeps at most three significant digits where the suffix permits it.
     *
     * @param value value to abbreviate.
     * @return compact, locale-independent text.
     */
    public static @Nonnull String format(final int value)
    {
        if (value > MILLION_DISPLAY_THRESHOLD)
        {
            return formatScaled(value / (double) MILLION) + "M";
        }
        if (value > THOUSAND)
        {
            return formatScaled(value / (double) THOUSAND) + "K";
        }
        return Integer.toString(value) + "";
    }

    private static String formatScaled(final double value)
    {
        final int decimalPlaces = value < 100 && value != Math.floor(value) ? 1 : 0;
        final String formatted = String.format(Locale.ROOT, "%." + decimalPlaces + "f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }
}
