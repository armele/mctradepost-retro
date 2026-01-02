package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.item.CoinItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class MarketDailyRoller
{
    public record MarketOffer(ItemStack stack, MarketTier tier, int price)
    {}

    public enum MarketTier
    {
        TIER1_COMMON, TIER2_UNCOMMON, TIER3_RARE, TIER4_EPIC
    }

    // 24000 ticks = 1 Minecraft day
    public static final long TICKS_PER_DAY = 24000L;

    /**
     * Roll the daily market offers for a given colony. The market offers are deterministic and will not change until the next
     * Minecraft day. The offers are rolled in the order of tier 1, tier 2, tier 3, tier 4.
     *
     * @param level         the server level
     * @param colonyId      the UUID of the colony
     * @param offersPerTier the number of offers to roll per tier
     * @return a list of market offers, with earlier tiers first
     */
    public static List<MarketOffer> rollDailyOffers(ServerLevel level, int colonyId, int commonOffers, int uncommonOffers, int rareOffers, int epicOffers)
    {
        // Deterministic daily seed so restart doesn't reshuffle the day's market.
        final long day = level.getDayTime() / TICKS_PER_DAY;
        final long seed = mixSeed(level.getSeed(), colonyId, -colonyId, day);
        final RandomSource rand = RandomSource.create(seed);

        final List<MarketOffer> out = new ArrayList<>();

        // Tier 1
        if (commonOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, rand, MarketTier.TIER1_COMMON, commonOffers));
        }

        // Tier 2
        if (uncommonOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, rand, MarketTier.TIER2_UNCOMMON, uncommonOffers));
        }

        // Tier 3
        if (rareOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, rand, MarketTier.TIER3_RARE, rareOffers));
        }

        // Tier 4
        if (epicOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, rand, MarketTier.TIER4_EPIC, epicOffers));
        }        
        
        // De-dupe (by item+nbt) while keeping earlier tiers first
        return dedupePreserveOrder(level, out);
    }

    /**
     * Mixes the given longs into a single long value, suitable for use as a random seed. This function is designed to be
     * deterministic, given the same input values. It is also designed to produce a well-distributed output value, given different
     * input values. The output value will be different for each different combination of input values. The output value will also be
     * different for the same input values but different order.
     *
     * @param worldSeed the world seed, used to ensure that the output value is different for each world
     * @param a         the first input value
     * @param b         the second input value
     * @param day       the current day (in Minecraft days)
     * @return a well-distributed long value, suitable for use as a random seed
     */
    private static long mixSeed(long worldSeed, long a, long b, long day)
    {
        long x = worldSeed;
        x ^= a * 0x9E3779B97F4A7C15L;
        x ^= b * 0xC2B2AE3D27D4EB4FL;
        x ^= day * 0x165667B19E3779F9L;
        x ^= (x >>> 33);
        x *= 0xFF51AFD7ED558CCDL;
        x ^= (x >>> 33);
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= (x >>> 33);
        return x;
    }

    /**
     * De-duplicates the given list of market offers, preserving the order of the original list. Each market offer is identified by the
     * item id and nbt tags of the item stack.
     *
     * @param offers the list of market offers to deduplicate
     * @return a list of market offers with duplicates removed
     */
    private static List<MarketOffer> dedupePreserveOrder(Level level, List<MarketOffer> offers)
    {
        // If we can’t get a provider, skip dedupe rather than returning empty market.
        var ra = level.registryAccess();
        if (!(ra instanceof HolderLookup.Provider provider))
        {
            return offers;
        }

        Set<String> seen = new HashSet<>();
        List<MarketOffer> out = new ArrayList<>(offers.size());

        for (MarketOffer o : offers)
        {
            if (o == null || o.stack() == null || o.stack().isEmpty())
                continue;

            try
            {
                Tag saved = o.stack().save(provider);

                // Normalize count away so different stack sizes dedupe
                String key;
                if (saved instanceof net.minecraft.nbt.CompoundTag ct)
                {
                    // copy() so we don’t mutate anything reused internally
                    net.minecraft.nbt.CompoundTag norm = ct.copy();
                    norm.remove("Count");

                    key = norm.toString();
                }
                else
                {
                    // Extremely unlikely, but safe fallback
                    key = saved.toString();
                }

                if (seen.add(key))
                    out.add(o);
            }
            catch (Exception e)
            {
                // Don’t crash the market roll because one stack failed to serialize
            }
        }

        return out;
    }


    /**
     * Calculates the price of a market offer in the given tier.
     * The price is a random value between the base value and the base value multiplied by the given multiplier, plus or minus a random fraction of the base value.
     * The multipliers are as follows:
     * Tier 1: base value + (base value / d4(rand))
     * Tier 2: (base value * d4(rand)) + (base value / d4(rand))
     * Tier 3: (base value * d4(rand) * CoinItem.GOLD_MULTIIN) + (base value * CoinItem.GOLD_MULTIIN / d4(rand))
     * Tier 4: (base value * d4(rand) * CoinItem.DIAMOND_MULTIIN) + (base value * CoinItem.DIAMOND_MULTIIN / d4(rand))
     *
     * @param tier the market tier
     * @param rand the RandomSource to use for generating the random price
     * @return the price of the market offer in the given tier
     */
    public static int priceForTier(MarketTier tier, RandomSource rand)
    {
        int baseValue = MCTPConfig.tradeCoinValue.get();
        return switch (tier)
        {
            case TIER1_COMMON -> baseValue + (baseValue / d4(rand));
            case TIER2_UNCOMMON -> (baseValue * d4(rand)) + (baseValue / d4(rand));
            case TIER3_RARE -> (baseValue * d4(rand) * CoinItem.GOLD_MULTIPLIER) + (baseValue * CoinItem.GOLD_MULTIPLIER / d4(rand));
            case TIER4_EPIC -> (baseValue * d4(rand) * CoinItem.DIAMOND_MULTIPLIER) + (baseValue * CoinItem.DIAMOND_MULTIPLIER / d4(rand));
        };
    }

    /**
     * Returns a random number between 1 and 4 (inclusive) from the given RandomSource.
     * @param rand the RandomSource to use for generating the random number
     * @return a random number between 1 and 4 (inclusive)
     */
    private static int d4(RandomSource rand)
    {
        return 1 + rand.nextInt(4);
    }
}
