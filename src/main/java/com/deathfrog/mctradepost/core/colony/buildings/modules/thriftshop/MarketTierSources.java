package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public final class MarketTierSources
{
    // ---- Your existing lists from Option A ----
    private static final List<ResourceLocation> UNCOMMON_TABLES = List.of(rl("minecraft:chests/simple_dungeon"),
        rl("minecraft:chests/abandoned_mineshaft"),
        rl("minecraft:chests/shipwreck_supply"),
        rl("minecraft:chests/desert_pyramid"));

    private static final List<ResourceLocation> RARE_TABLES = List.of(rl("minecraft:chests/end_city_treasure"),
        rl("minecraft:chests/bastion_treasure"),
        rl("minecraft:chests/ancient_city"),
        rl("minecraft:chests/woodland_mansion"));

    // ---- Tier 1: “grass/leaves break drops” ----
    // Use BLOCK loot tables so this automatically respects vanilla & datapack changes.
    // Short grass & a selection of leaves keeps it “across biomes” without needing biome awareness.
    private static final List<ResourceLocation> COMMON_BLOCK_DROP_TABLES = List.of(rl("minecraft:blocks/short_grass"),
        rl("minecraft:blocks/tall_grass"),
        rl("minecraft:blocks/fern"),
        rl("minecraft:blocks/oak_leaves"),
        rl("minecraft:blocks/spruce_leaves"),
        rl("minecraft:blocks/birch_leaves"),
        rl("minecraft:blocks/jungle_leaves"),
        rl("minecraft:blocks/acacia_leaves"),
        rl("minecraft:blocks/dark_oak_leaves"),
        rl("minecraft:blocks/mangrove_leaves"),
        rl("minecraft:blocks/cherry_leaves"),
        rl("minecraft:blocks/azalea_leaves"),
        rl("minecraft:blocks/flowering_azalea_leaves"));

    // ---- Fishing ----
    // Vanilla fishing is driven by minecraft:gameplay/fishing, which uses subtables:
    // minecraft:gameplay/fishing/fish, .../junk, .../treasure.
    private static final @Nonnull ResourceLocation FISHING_FISH = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/fish"), "minecraft:gameplay/fishing/fish loot table is null");
    private static final @Nonnull ResourceLocation FISHING_JUNK = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/junk"), "minecraft:gameplay/fishing/junk loot table is null");
    private static final @Nonnull ResourceLocation FISHING_TREASURE = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/treasure"), "minecraft:gameplay/fishing/treasure loot table is null");

    public static List<MarketOffer> rollTier(@Nonnull ServerLevel level, RandomSource rand, MarketTier tier, int count)
    {
        List<ItemStack> stacks = new ArrayList<>();

        // Attempt cap so we don’t spin forever if filters reject lots of outcomes.
        int attempts = 0;
        int maxAttempts = Math.max(20, count * 10);

        while (stacks.size() < count && attempts++ < maxAttempts)
        {
            ItemStack rolled = switch (tier)
            {
                case TIER1_COMMON -> rollTier1(level, rand);
                case TIER2_UNCOMMON -> rollTier2(level, rand);
                case TIER3_RARE -> rollTier3(level, rand);
                case TIER4_EPIC -> rollTier4(level, rand);
            };

            if (rolled.isEmpty()) continue;

            // TODO: Implement gating tags.

            if (!isSellable(rolled)) continue;

            stacks.add(normalizeForSale(rolled, tier));
        }

        // Attach prices
        List<MarketOffer> offers = new ArrayList<>();
        for (ItemStack s : stacks)
        {
            offers.add(new MarketOffer(s, tier, MarketDailyRoller.priceForTier(tier, rand)));
        }
        return offers;
    }

    // ---------------- Tier roll implementations ----------------

    private static ItemStack rollTier1(ServerLevel level, RandomSource rand)
    {
        // 65% block-break commons, 35% common fishing
        if (rand.nextFloat() < 0.65f)
        {
            ResourceLocation tableId = pick(rand, COMMON_BLOCK_DROP_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollBlockStyle(level, rand, tableId);
        }
        else
        {
            // "common fishing": fish + junk skewed towards fish
            ResourceLocation tableId = (rand.nextFloat() < 0.80f) ? FISHING_FISH : FISHING_JUNK;

            return LootRoller.rollFishingStyle(level, rand, tableId, /*luck*/ 0.0f);
        }
    }

    private static ItemStack rollTier2(ServerLevel level, RandomSource rand)
    {
        // 75% chest-uncommon, 25% uncommon fishing (i.e., a small chance to surface treasure)
        if (rand.nextFloat() < 0.75f)
        {
            ResourceLocation tableId = pick(rand, UNCOMMON_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else
        {
            // "uncommon fishing": rare-ish treasure, but you can also gate it
            if (rand.nextFloat() < 0.50f) // accept treasure only half the time in tier2
                return ItemStack.EMPTY;

            return LootRoller.rollFishingStyle(level, rand, FISHING_TREASURE, /*luck*/ 0.0f);
        }
    }

    private static ItemStack rollTier3(@Nonnull ServerLevel level, RandomSource rand)
    {
        // Wandering trader pool. We generate a trade offer, then return the selling stack.
        return WanderingTraderSource.rollTradeOutput(level, rand);
    }

    private static ItemStack rollTier4(ServerLevel level, RandomSource rand)
    {
        // 85% rare chests, 15% rare fishing (treasure) with higher acceptance
        if (rand.nextFloat() < 0.85f)
        {
            ResourceLocation tableId = pick(rand, RARE_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else
        {
            // "rare fishing": treasure with looser gating
            if (rand.nextFloat() < 0.75f) return LootRoller.rollFishingStyle(level, rand, FISHING_TREASURE, /*luck*/ 0.0f);

            return ItemStack.EMPTY;
        }
    }

    // ---------------- Filtering / normalization ----------------

    private static boolean isSellable(ItemStack stack)
    {
        if (stack.isEmpty()) return false;

        // TODO: tag-driven blacklist/whitelist long term:
        // if (stack.is(ModTags.Items.MARKET_BLACKLIST)) return false;

        return true;
    }

    private static ItemStack normalizeForSale(ItemStack stack, MarketTier tier)
    {
        ItemStack out = stack.copy();

        // Keep “rare” feeling rare: clamp stack sizes more aggressively at higher tiers
        int clamp = switch (tier)
        {
            case TIER1_COMMON -> 16;
            case TIER2_UNCOMMON -> 8;
            case TIER3_RARE -> 8;
            case TIER4_EPIC -> 4;
        };

        out.setCount(Math.min(out.getCount(), Math.min(clamp, out.getMaxStackSize())));
        return out;
    }

    // ---------------- Small helpers ----------------
    private static ResourceLocation rl(@Nonnull String s)
    {
        return ResourceLocation.tryParse(s);
    }

    private static <T> T pick(RandomSource rand, List<T> list)
    {
        return list.get(rand.nextInt(list.size()));
    }
}
