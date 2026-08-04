package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.rarefinds.generation.RareFindGenerationRules;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

/** Supplies deterministic fallback values from the active Rare Finds tier tags. */
public final class TierDerivedItemValueProvider
{
    private static final List<TagKey<Item>> PRICED_TIERS = List.of(
        ModTags.ITEMS.RARE_FINDS_TIER2_TAG,
        ModTags.ITEMS.RARE_FINDS_TIER3_TAG,
        ModTags.ITEMS.RARE_FINDS_TIER4_TAG);

    private TierDerivedItemValueProvider() { }

    /**
     * Finds tier 2-4 items without an authoritative or already-generated value.
     * The active tags are used by reference, so generated and manually authored tiers
     * are both honored after their datapacks have been loaded.
     */
    @SuppressWarnings("null")
    public static Map<Item, Integer> derive(final MinecraftServer server,
                                            final Map<Item, Integer> authoritativeValues,
                                            final Map<Item, Integer> generatedValues)
    {
        final RareFindGenerationRules rules = RareFindGenerationRules.load(server);
        final int[] cutoffs = { rules.tier2Value(), rules.tier3Value(), rules.tier4Value() };
        final Map<Item, Integer> result = new HashMap<>();

        for (final Item item : ITEM)
        {
            if (authoritativeValues.containsKey(item) || generatedValues.containsKey(item)) continue;

            // Highest tier wins when a pack accidentally assigns an item more than once.
            for (int index = PRICED_TIERS.size() - 1; index >= 0; index--)
            {
                if (item == null) continue;

                if (ITEM.wrapAsHolder(item).is(PRICED_TIERS.get(index)))
                {
                    final ResourceLocation id = ITEM.getKey(item);
                    result.put(item, valueFor(id, cutoffs[index]));
                    break;
                }
            }
        }
        return Map.copyOf(result);
    }

    /**
     * Builds protected inputs for the second recipe pass. First-pass values win
     * defensively if a fallback map contains the same item.
     */
    public static <T> Map<T, Integer> mergeWithFallbacks(final Map<T, Integer> firstPassValues,
                                                          final Map<T, Integer> fallbackValues)
    {
        final Map<T, Integer> result = new HashMap<>(fallbackValues);
        result.putAll(firstPassValues);
        return result;
    }

    /** Produces a stable value in the inclusive range 90%-150% of a tier cutoff. */
    static int valueFor(final ResourceLocation id, final int cutoff)
    {
        final int percentage = 90 + Math.floorMod(id.toString().hashCode(), 61);
        return Math.max(1, (int) Math.round(cutoff * percentage / 100.0D));
    }
}
