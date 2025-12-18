package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;

public final class NullnessBridge
{
    private NullnessBridge() {}

    /** Contract: value is non-null, JDT just can't prove it. No runtime check. */
    @Nonnull
    @SuppressWarnings("null")
    public static <T> T assumeNonnull(T value)
    {
        return value;
    }

    /** Use when null indicates a real bug and you want a hard fail. */
    @Nonnull
    public static <T> T requireNonnull(T value, String message)
    {
        return java.util.Objects.requireNonNull(value, message);
    }
}
