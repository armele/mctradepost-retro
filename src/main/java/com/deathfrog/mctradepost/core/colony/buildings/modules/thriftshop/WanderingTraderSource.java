package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

public final class WanderingTraderSource
{
    public static ItemStack rollTradeOutput(@Nonnull ServerLevel level, RandomSource rand, MarketTier tier)
    {
        // In vanilla, wandering trader trades are keyed by tier (usually 1 and 2)
        // and contain arrays of ItemListing factories.
        Int2ObjectMap<ItemListing[]> map = VillagerTrades.WANDERING_TRADER_TRADES;

        int traderTier = switch (tier) {
            case TIER1_COMMON   -> 1;
            case TIER2_UNCOMMON -> 1;
            case TIER3_RARE     -> 2;
            case TIER4_EPIC     -> 2;
        };

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
