package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

/**
 * Shared dimensions of the volume sampled for vegetation forage targets.
 * Kept side-neutral so server target selection and client visualization cannot
 * drift apart.
 */
public final class VegetationForageRange
{
    public static final int HORIZONTAL_RADIUS = 12;
    public static final int MIN_VERTICAL_OFFSET = -2;
    public static final int GROUND_FEEDER_MAX_VERTICAL_OFFSET = 3;
    public static final int HANGING_FEEDER_MAX_VERTICAL_OFFSET = 10;

    private VegetationForageRange() { }

    public static int maxVerticalOffset(final boolean hanging)
    {
        return hanging ? HANGING_FEEDER_MAX_VERTICAL_OFFSET : GROUND_FEEDER_MAX_VERTICAL_OFFSET;
    }
}
