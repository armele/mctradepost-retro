package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceItemListModule;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.item.CoinItem;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

public final class MarketDailyRoller
{
    public record MarketOffer(
        @Nonnull ItemStack stack,
        @Nonnull MarketTier tier,
        int price,
        boolean retainedSearchResult,
        int subscriptionPrice)
    {
        /**
         * Creates an ordinary market offer whose synchronized subscription price has not yet been assigned.
         *
         * @param stack offered stack
         * @param tier offer tier
         * @param price one-time purchase price
         */
        public MarketOffer(@Nonnull ItemStack stack, @Nonnull MarketTier tier, int price)
        {
            this(stack, tier, price, false, -1);
        }

        /**
         * Creates a market offer whose synchronized subscription price has not yet been assigned.
         *
         * @param stack offered stack
         * @param tier offer tier
         * @param price one-time purchase price
         * @param retainedSearchResult whether a retained search produced the offer
         */
        public MarketOffer(@Nonnull ItemStack stack, @Nonnull MarketTier tier, int price, boolean retainedSearchResult)
        {
            this(stack, tier, price, retainedSearchResult, -1);
        }

        /**
         * Validates a fully populated market offer.
         *
         * @param stack offered stack
         * @param tier offer tier
         * @param price one-time purchase price
         * @param retainedSearchResult whether a retained search produced the offer
         * @param subscriptionPrice server-authoritative recurring price, or a negative value before synchronization
         */
        public MarketOffer
        {
            Objects.requireNonNull(stack, "stack");
            Objects.requireNonNull(tier, "tier");
        }
    }

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
        RegistryAccess ra = level.registryAccess();
        if (!(ra instanceof HolderLookup.Provider provider))
        {
            return offers;
        }

        Set<String> seen = new HashSet<>();
        List<MarketOffer> out = new ArrayList<>(offers.size());

        for (MarketOffer o : offers)
        {
            if (o == null || o.stack().isEmpty())
            {
                continue;
            }

            try
            {
                Tag saved = o.stack().save(provider);

                // Normalize count away so different stack sizes dedupe
                String key;
                if (saved instanceof net.minecraft.nbt.CompoundTag ct)
                {
                    // copy() so we don’t mutate anything reused internally
                    net.minecraft.nbt.CompoundTag norm = ct.copy();
                    // ItemStack's codec uses lowercase "count" in 1.21. Remove the
                    // legacy spelling too so the identity remains count-independent
                    // if an older serialized stack reaches this path.
                    norm.remove("count");
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
        return randomizedPrice(normalPriceForTier(tier), rand);
    }

    /**
     * Calculates a randomized one-time price without allowing the complete stack
     * to sell below its configured economic value.
     *
     * @param stack offered stack
     * @param tier market tier supplying the randomized reference price
     * @param rand random source used for market variation
     * @return positive one-time purchase price for the complete stack
     */
    public static int priceForOffer(ItemStack stack, MarketTier tier, RandomSource rand)
    {
        final long stackValue = (long) MarketplaceItemListModule.marketplaceValue(stack) * stack.getCount();
        final long marketReference = Math.max(stackValue, normalPriceForTier(tier));
        return applyStackValueFloor(stack, randomizedPrice(marketReference, rand));
    }

    /**
     * Prevents a one-time offer for a complete stack from falling below its configured economic value.
     *
     * @param stack offered stack
     * @param candidatePrice randomized or premium-adjusted candidate price
     * @return bounded price no lower than the complete stack value
     */
    public static int applyStackValueFloor(ItemStack stack, long candidatePrice)
    {
        final long stackValue = (long) MarketplaceItemListModule.marketplaceValue(stack) * stack.getCount();
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.max(stackValue, candidatePrice)));
    }

    /**
     * Returns the non-randomized normal price represented by a market tier.
     *
     * @param tier market tier
     * @return positive normal tier price
     */
    public static int normalPriceForTier(MarketTier tier)
    {
        final int coin = MCTPConfig.tradeCoinValue.get();
        final long price = switch (tier)
        {
            case TIER1_COMMON -> (long) Math.ceil(coin * 0.8D);
            case TIER2_UNCOMMON -> (long) Math.ceil(coin * 1.2D);
            case TIER3_RARE -> (long) coin * CoinItem.GOLD_MULTIPLIER;
            case TIER4_EPIC -> (long) coin * CoinItem.DIAMOND_MULTIPLIER;
        };
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, price));
    }

    /** Applies triangular market variation to a positive reference price. */
    private static int randomizedPrice(long referencePrice, RandomSource rand)
    {
        double centeredNoise = rand.nextFloat() + rand.nextFloat() - 1.0;
        double multiplier = 1.0D + PRICE_SPREAD * centeredNoise;

        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, Math.round(referencePrice * multiplier)));
    }
}
