package com.deathfrog.mctradepost.api.entity.pets.goals.scavenge;

/**
 * Conventional block-state property names recognized by generic scavenging
 * behavior and its client-side representations.
 */
public final class ScavengePropertyNames
{
    /** Integer growth-stage property used by crops and other maturing blocks. */
    public static final String AGE = "age";

    /** Boolean fruit-presence property used by berry-bearing blocks. */
    public static final String BERRIES = "berries";

    /** Maximum age used to identify long-growth crops such as melon and pumpkin stems. */
    public static final int LONG_GROWTH_MAX_AGE = 7;

    /** Maximum age at which the small-range fruit post-harvest heuristic applies. */
    public static final int SMALL_FRUIT_MAX_AGE = 3;

    /** Growth stage assigned after harvesting a small-range fruit block. */
    public static final int SMALL_FRUIT_POST_HARVEST_AGE = 1;

    /**
     * Prevents instantiation of this constants class.
     */
    private ScavengePropertyNames()
    {
    }
}
