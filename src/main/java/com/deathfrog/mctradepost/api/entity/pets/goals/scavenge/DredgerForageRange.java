package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

/**
 * Shared dimensions of the volume sampled for dredger forage targets.
 * Kept side-neutral so server target selection and client visualization cannot
 * drift apart.
 */
public final class DredgerForageRange
{
    public static final int HORIZONTAL_RADIUS = 8;
    public static final int MIN_VERTICAL_OFFSET = -3;
    public static final int MAX_VERTICAL_OFFSET = 3;

    private DredgerForageRange() { }
}
