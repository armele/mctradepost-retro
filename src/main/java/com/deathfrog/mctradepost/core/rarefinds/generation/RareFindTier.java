package com.deathfrog.mctradepost.core.rarefinds.generation;

import net.minecraft.world.item.Rarity;

/** The generated Rare Finds classifications, including search-only tier zero. */
public enum RareFindTier
{
    TIER0(0), TIER1(1), TIER2(2), TIER3(3), TIER4(4);

    private final int level;

    /**
     * Creates a tier with its numeric classification level.
     *
     * @param level tier level from zero through four
     */
    RareFindTier(final int level)
    {
        this.level = level;
    }

    /** @return the numeric classification level */
    public int level()
    {
        return level;
    }

    /**
     * Converts an integer to a tier, clamping values outside the supported range.
     *
     * @param level proposed tier level
     * @return the corresponding tier
     */
    public static RareFindTier of(final int level)
    {
        return values()[Math.max(0, Math.min(4, level))];
    }

    /**
     * Converts non-common vanilla rarity into generator evidence.
     *
     * @param rarity item rarity to translate
     * @return tiers two through four, or {@code null} for common rarity
     */
    public static RareFindTier fromRarity(final Rarity rarity)
    {
        return switch (rarity)
        {
            case UNCOMMON -> TIER2;
            case RARE -> TIER3;
            case EPIC -> TIER4;
            default -> null;
        };
    }
}
