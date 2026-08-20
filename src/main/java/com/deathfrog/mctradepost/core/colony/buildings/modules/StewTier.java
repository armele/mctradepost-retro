package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The selectable quality levels of perpetual stew. */
public enum StewTier
{
    BASIC(1, 1, 2, 0, "perpetual_stew"),
    HEARTY(2, 2, 4, 1, "hearty_perpetual_stew"),
    GOURMET(3, 3, 6, 2, "gourmet_perpetual_stew");

    private final int level;
    private final int minimumKitchenLevel;
    private final int requiredDistinctIngredients;
    private final int requiredProteinIngredients;
    private final String serializedName;

    /**
     * Creates a stew tier definition.
     *
     * @param level numeric MineColonies food tier
     * @param minimumKitchenLevel minimum kitchen level
     * @param requiredDistinctIngredients required per-pot ingredient diversity
     * @param requiredProteinIngredients required distinct proteins within the total ingredient requirement
     * @param serializedName stable item and translation identifier
     */
    StewTier(final int level, final int minimumKitchenLevel, final int requiredDistinctIngredients,
        final int requiredProteinIngredients, final String serializedName)
    {
        this.level = level;
        this.minimumKitchenLevel = minimumKitchenLevel;
        this.requiredDistinctIngredients = requiredDistinctIngredients;
        this.requiredProteinIngredients = requiredProteinIngredients;
        this.serializedName = serializedName;
    }

    /** @return the numeric MineColonies food tier. */
    public int getLevel() { return level; }

    /** @return the minimum kitchen level at which this tier may be selected. */
    public int getMinimumKitchenLevel() { return minimumKitchenLevel; }

    /** @return the number of distinct ingredients required in the current pot. */
    public int getRequiredDistinctIngredients() { return requiredDistinctIngredients; }

    /** @return the number of distinct protein ingredients required within the current pot. */
    public int getRequiredProteinIngredients() { return requiredProteinIngredients; }

    /** @return the stable name used for item and translation identifiers. */
    public String getSerializedName() { return serializedName; }

    /**
     * Resolves the registered food item produced for this tier.
     *
     * @return the stew item for this tier
     */
    public Item getItem()
    {
        return switch (this)
        {
            case BASIC -> MCTradePostMod.PERPETUAL_STEW.get();
            case HEARTY -> MCTradePostMod.HEARTY_PERPETUAL_STEW.get();
            case GOURMET -> MCTradePostMod.GOURMET_PERPETUAL_STEW.get();
        };
    }

    /**
     * Resolves a tier from a numeric level, clamping values to the supported range.
     *
     * @param level numeric tier level
     * @return the matching supported tier
     */
    public static StewTier fromLevel(final int level)
    {
        if (level >= 3) return GOURMET;
        if (level >= 2) return HEARTY;
        return BASIC;
    }

    /**
     * Gets the highest tier supported by a kitchen level.
     *
     * @param kitchenLevel current kitchen level
     * @return the highest selectable tier
     */
    public static StewTier maxForKitchenLevel(final int kitchenLevel)
    {
        return fromLevel(kitchenLevel);
    }

    /**
     * Resolves the stew tier represented by an item stack.
     *
     * @param stack stew item stack
     * @return the matching tier, or {@link #BASIC} for an unknown item
     */
    @SuppressWarnings("null")
    public static StewTier fromItem(final ItemStack stack)
    {
        if (stack != null && stack.is(MCTradePostMod.GOURMET_PERPETUAL_STEW.get())) return GOURMET;
        if (stack != null && stack.is(MCTradePostMod.HEARTY_PERPETUAL_STEW.get())) return HEARTY;
        return BASIC;
    }
}
