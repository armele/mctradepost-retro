package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import javax.annotation.Nonnull;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public final class WanderingTraderSource
{
    public static ItemStack rollTradeOutput(@Nonnull ServerLevel level, RandomSource rand)
    {
        // In vanilla, wandering trader trades are keyed by tier (usually 1 and 2)
        // and contain arrays of ItemListing factories.
        // Names may vary slightly across versions; adjust imports accordingly.
        var map = VillagerTrades.WANDERING_TRADER_TRADES;

        // Choose which “trader tier” corresponds to your Market Tier 3.
        // I recommend mixing both: 70% common trader offers, 30% rare trader offers.
        int traderTier = (rand.nextFloat() < 0.70f) ? 1 : 2;

        VillagerTrades.ItemListing[] listings = map.get(traderTier);
        if (listings == null || listings.length == 0)
            return ItemStack.EMPTY;

        // Pick a random listing factory and create an offer
        VillagerTrades.ItemListing listing = listings[rand.nextInt(listings.length)];

        // Many listings only need an Entity + Random.
        // We can create a dummy WanderingTrader in-memory. It doesn't need to be added to the world.
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        if (trader == null)
            return ItemStack.EMPTY;

        MerchantOffer offer = listing.getOffer(trader, rand);
        if (offer == null)
            return ItemStack.EMPTY;

        // The stack the trader sells (your marketplace item)
        ItemStack result = offer.getResult();
        return result == null ? ItemStack.EMPTY : result.copy();
    }
}
