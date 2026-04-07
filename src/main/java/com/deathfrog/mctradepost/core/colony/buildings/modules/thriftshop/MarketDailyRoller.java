package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
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
    private static final double PRICE_SPREAD = 0.30;

    /**
     * Roll the daily market offers for a given colony. The market offers are deterministic and will not change until the next
     * Minecraft day. The offers are rolled in the order of tier 1, tier 2, tier 3, tier 4.
     *
     * @param level         the server level
     * @param colonyId      the UUID of the colony
     * @param offersPerTier the number of offers to roll per tier
     * @return a list of market offers, with earlier tiers first
     */
    public static List<MarketOffer> rollDailyOffers(ServerLevel level, BuildingMarketplace marketplace, int rerollIndex, int commonOffers, int uncommonOffers, int rareOffers, int epicOffers)
    {
        // Deterministic daily seed so restart doesn't reshuffle the day's market.
        final long day = level.getDayTime() / TICKS_PER_DAY;
        final long colonyId = marketplace.getColony().getID();
        final long seed = mixSeed(level.getSeed(), colonyId, rerollIndex, day);
        final RandomSource rand = RandomSource.create(seed);

        final List<MarketOffer> out = new ArrayList<>();

        // Tier 1
        if (commonOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, marketplace, rand, MarketTier.TIER1_COMMON, commonOffers));
        }

        // Tier 2
        if (uncommonOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, marketplace, rand, MarketTier.TIER2_UNCOMMON, uncommonOffers));
        }

        // Tier 3
        if (rareOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, marketplace, rand, MarketTier.TIER3_RARE, rareOffers));
        }

        // Tier 4
        if (epicOffers > 0)
        {
            out.addAll(MarketTierSources.rollTier(level, marketplace, rand, MarketTier.TIER4_EPIC, epicOffers));
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
     * Prices are centered around a tier-specific multiplier of the configured trade coin value, with random noise clustered near
     * the target price.
     *
     * @param tier the market tier
     * @param rand the RandomSource to use for generating the random price
     * @return the price of the market offer in the given tier
     */
    public static int priceForTier(MarketTier tier, RandomSource rand)
    {
        return switch (tier)
        {
            case TIER1_COMMON -> priceForMultiplier(0.80, rand);
            case TIER2_UNCOMMON -> priceForMultiplier(1.20, rand);
            case TIER3_RARE -> priceForMultiplier(CoinItem.GOLD_MULTIPLIER, rand);
            case TIER4_EPIC -> priceForMultiplier(CoinItem.DIAMOND_MULTIPLIER, rand);
        };
    }

    /**
     * Prices a tier by multiplying the configured coin value by the target multiplier, then applying triangular noise.
     * @param targetMultiplier the target multiplier of the configured trade coin value
     * @param rand the RandomSource to use for generating the random price
     * @return the randomized price
     */
    private static int priceForMultiplier(double targetMultiplier, RandomSource rand)
    {
        int baseValue = MCTPConfig.tradeCoinValue.get();
        double centeredNoise = rand.nextFloat() + rand.nextFloat() - 1.0;
        double multiplier = targetMultiplier * (1.0 + PRICE_SPREAD * centeredNoise);

        return Math.max(1, (int) Math.round(baseValue * multiplier));
    }
}
