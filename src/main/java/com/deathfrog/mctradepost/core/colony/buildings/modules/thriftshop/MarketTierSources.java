package com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.minecolonies.api.util.MathUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public final class MarketTierSources
{
    private static final List<ResourceLocation> COMMON_TABLES = List.of(
        rl("minecraft:chests/village/village_cartographer"),
        rl("minecraft:chests/village/village_mason"),
        rl("minecraft:chests/village/village_desert_house"),
        rl("minecraft:chests/village/village_taiga_house"),
        rl("minecraft:chests/village/village_plains_house"),
        rl("minecraft:chests/village/village_savanna_house"),
        rl("minecraft:chests/village/village_snowy_house"),
        rl("minecraft:chests/spawn_bonus_chest")
    );

    private static final List<ResourceLocation> UNCOMMON_TABLES = List.of(
        rl("minecraft:chests/simple_dungeon"),
        rl("minecraft:chests/abandoned_mineshaft"),
        rl("minecraft:chests/shipwreck_supply"),
        rl("minecraft:chests/desert_pyramid"),
        rl("minecraft:chests/igloo_chest"),
        rl("minecraft:chests/pillager_outpost")
    );

    private static final List<ResourceLocation> RARE_TABLES = List.of(
        rl("minecraft:chests/stronghold_library"),
        rl("minecraft:chests/stronghold_crossing"),
        rl("minecraft:chests/stronghold_corridor"),
        rl("minecraft:chests/jungle_temple")
    );

    private static final List<ResourceLocation> EPIC_TABLES = List.of(
        rl("minecraft:chests/end_city_treasure"),
        rl("minecraft:chests/bastion_treasure"),
        rl("minecraft:chests/ancient_city"),
        rl("minecraft:chests/woodland_mansion")
    );

    // ---- Tier 1: “grass/leaves break drops” ----
    // Use BLOCK loot tables so this automatically respects vanilla & datapack changes.
    // Short grass & a selection of leaves keeps it “across biomes” without needing biome awareness.
    private static final List<ResourceLocation> COMMON_BLOCK_DROP_TABLES = List.of(
        rl("minecraft:blocks/short_grass"),
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
        rl("minecraft:blocks/flowering_azalea_leaves")
    );

    // ---- Fishing ----
    // Vanilla fishing is driven by minecraft:gameplay/fishing, which uses subtables:
    // minecraft:gameplay/fishing/fish, .../junk, .../treasure.
    private static final @Nonnull ResourceLocation FISHING_FISH = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/fish"), "minecraft:gameplay/fishing/fish loot table is null");
    private static final @Nonnull ResourceLocation FISHING_JUNK = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/junk"), "minecraft:gameplay/fishing/junk loot table is null");
    private static final @Nonnull ResourceLocation FISHING_TREASURE = NullnessBridge.requireNonnull(rl("minecraft:gameplay/fishing/treasure"), "minecraft:gameplay/fishing/treasure loot table is null");

    public static List<MarketOffer> rollTier(@Nonnull ServerLevel level, BuildingMarketplace marketplace, RandomSource rand, MarketTier tier, int count)
    {
        List<ItemStack> stacks = new ArrayList<>();

        // Attempt cap so we don’t spin forever if filters reject lots of outcomes.
        int attempts = 0;
        int maxAttempts = Math.max(20, count * 10);

        while (stacks.size() < count && attempts++ < maxAttempts)
        {
            ItemStack rolled = switch (tier)
            {
                case TIER1_COMMON   -> rollTier1(level, marketplace, rand);
                case TIER2_UNCOMMON -> rollTier2(level, marketplace, rand);
                case TIER3_RARE     -> rollTier3(level, marketplace, rand);
                case TIER4_EPIC     -> rollTier4(level, marketplace, rand);
            };

            if (rolled.isEmpty()) continue;

            if (!isSellable(rolled, tier)) continue;

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

    /**
     * Calculate the luck modifier for a market offer roll.
     * This modifier is used to adjust the quality of the item generated.
     * The luck modifier is calculated as (shopkeeper primary skill) / 100.0f.
     * If the shopkeeper primary skill is negative, the luck modifier is 0.0f.
     * @param marketplace the marketplace to calculate the luck modifier for
     * @return the luck modifier for the market offer roll
     */
    protected static float calcLuck(BuildingMarketplace marketplace)
    {
        float luck = 0.0f;
        int skill = marketplace.shopkeeperPrimarySkill();

        luck = Math.min(0.75f, skill / 150.0f);

        return luck;
    }
    
    // ---------------- Tier roll implementations ----------------

    /**
     * Rolls an item from tier 1 of the thrift shop.
     * <p>
     * Tier 1 is a mix of common chests, block-break commons and common fish.
     * <p>
     * The returned item stack is empty if any of the above rolls fail to generate an item.
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @return a single item stack from tier 1 of the thrift shop, or an empty item stack if the roll fails
     */
    private static ItemStack rollTier1(@Nonnull ServerLevel level, BuildingMarketplace marketplace, RandomSource rand)
    {
        // 20% from datapack tag, else loottable logic
        if (rand.nextFloat() < 0.20f)
        {
            ItemStack tagged = TaggedItemPicker.rollFromTag(level, rand, ModTags.RARE_FINDS_TIER1_TAG);
            if (!tagged.isEmpty()) return tagged;
            // fall through if tag is empty / no valid results
        }

        float randFloat = rand.nextFloat();

        if (randFloat < 0.40f)
        {
            ResourceLocation tableId = pick(rand, COMMON_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else if (randFloat < 0.80f)
        {
            ResourceLocation tableId = pick(rand, COMMON_BLOCK_DROP_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollBlockStyle(level, rand, tableId);
        }
        else
        {
            // "common fishing": fish + junk skewed towards fish
            ResourceLocation tableId = (rand.nextFloat() < 0.80f) ? FISHING_FISH : FISHING_JUNK;

            return LootRoller.rollFishingStyle(level, rand, tableId, calcLuck(marketplace));
        }
    }


    /**
     * Rolls an item from tier 2 of the thrift shop.
     * <p>
     * Tier 2 is a mix of uncommon chest items and uncommon fish.
     * <p>
     * The returned item stack is empty if any of the above rolls fail to generate an item.
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @return a single item stack from tier 2 of the thrift shop, or an empty item stack if the roll fails
     */
    private static ItemStack rollTier2(@Nonnull ServerLevel level, BuildingMarketplace marketplace, RandomSource rand)
    {
        // 20% from datapack tag, else loottable logic
        if (rand.nextFloat() < 0.20f)
        {
            ItemStack tagged = TaggedItemPicker.rollFromTag(level, rand, ModTags.RARE_FINDS_TIER2_TAG);
            if (!tagged.isEmpty()) return tagged;
            // fall through if tag is empty / no valid results
        }

        float randFloat = rand.nextFloat();

        if (rand.nextFloat() < 0.60f)
        {
            ResourceLocation tableId = pick(rand, UNCOMMON_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else if (randFloat < 0.80f)
        {
            // Wandering trader pool. We generate a trade offer, then return the selling stack.
            return WanderingTraderSource.rollTradeOutput(level, rand, MarketTier.TIER3_RARE);
        }
        else
        {
            return LootRoller.rollFishingStyle(level, rand, FISHING_TREASURE, calcLuck(marketplace));
        }
    }

    /**
     * Rolls an item from tier 3 of the thrift shop.
     * <p>
     * Tier 3 is a mix of rare chests and rare fish.
     * <p>
     * The returned item stack is empty if any of the above rolls fail to generate an item.
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @return a single item stack from tier 3 of the thrift shop, or an empty item stack if the roll fails
     */
    private static ItemStack rollTier3(@Nonnull ServerLevel level, BuildingMarketplace marketplace, RandomSource rand)
    {
        // 20% from datapack tag, else loottable logic
        if (rand.nextFloat() < 0.20f)
        {
            ItemStack tagged = TaggedItemPicker.rollFromTag(level, rand, ModTags.RARE_FINDS_TIER3_TAG);
            if (!tagged.isEmpty()) return tagged;
            // fall through if tag is empty / no valid results
        }

        float randFloat = rand.nextFloat();

        if (randFloat < 0.60f)
        {
            ResourceLocation tableId = pick(rand, RARE_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else if (randFloat < 0.80f)
        {
            return LootRoller.rollFishingStyle(level, rand, FISHING_TREASURE, calcLuck(marketplace));
        }
        else
        {
            // Wandering trader pool. We generate a trade offer, then return the selling stack.
            return WanderingTraderSource.rollTradeOutput(level, rand, MarketTier.TIER3_RARE);
        }
    }

    /**
     * Rolls an item from tier 4 of the thrift shop.
     * <p>
     * Tier 4 is a mix of epic chests and epic fish.
     * <p>
     * The returned item stack is empty if any of the above rolls fail to generate an item.
     * @param level the server level to retrieve the loot table from
     * @param rand the random source to use when retrieving the item
     * @return a single item stack from tier 4 of the thrift shop, or an empty item stack if the roll fails
     */
    private static ItemStack rollTier4(ServerLevel level, BuildingMarketplace marketplace, RandomSource rand)
    {
        // 20% from datapack tag, else loottable logic
        if (rand.nextFloat() < 0.20f)
        {
            ItemStack tagged = TaggedItemPicker.rollFromTag(level, rand, ModTags.RARE_FINDS_TIER4_TAG);
            if (!tagged.isEmpty()) return tagged;
            // fall through if tag is empty / no valid results
        }

        // 85% rare chests, 15% rare fishing (treasure) with higher acceptance
        if (rand.nextFloat() < 0.85f)
        {
            ResourceLocation tableId = pick(rand, EPIC_TABLES);

            if (tableId == null) return ItemStack.EMPTY;

            return LootRoller.rollChestStyle(level, rand, tableId);
        }
        else
        {
            return LootRoller.rollFishingStyle(level, rand, FISHING_TREASURE, calcLuck(marketplace) + .5f);
        }
    }


    /**
     * Checks if the given item stack is sellable at the given market tier.
     * <p>
     * This method first checks if the stack is empty, in which case it returns false.
     * <p>
     * Then, it checks if the stack has the {@link ModTags#RARE_FINDS_BLACKLIST_TAG} tag, in which case it returns false.
     * <p>
     * Finally, it checks if the stack has a tag that is specific to a different market tier. If so, it returns false.
     * <p>
     * @param stack the item stack to check
     * @param tier the market tier to check against
     * @return true if the stack is sellable, false otherwise
     */
    private static boolean isSellable(ItemStack stack, MarketTier tier)
    {
        if (stack.isEmpty()) return false;
        if (stack.is(ModTags.RARE_FINDS_BLACKLIST_TAG)) return false;

        boolean inTier1 = stack.is(ModTags.RARE_FINDS_TIER1_TAG);
        boolean inTier2 = stack.is(ModTags.RARE_FINDS_TIER2_TAG);
        boolean inTier3 = stack.is(ModTags.RARE_FINDS_TIER3_TAG);
        boolean inTier4 = stack.is(ModTags.RARE_FINDS_TIER4_TAG);

        boolean taggedAnyTier = inTier1 || inTier2 || inTier3 || inTier4;

        // If it’s tier-tagged at all, it must match THIS tier.
        if (taggedAnyTier)
        {
            return switch (tier)
            {
                case TIER1_COMMON -> inTier1;
                case TIER2_UNCOMMON -> inTier2;
                case TIER3_RARE -> inTier3;
                case TIER4_EPIC -> inTier4;
            };
        }

        // Not explicitly tier-tagged → allowed to appear from any tier’s other sources.
        return true;
    }

    /**
     * Normalize an item stack for sale in the thrift shop.
     * <p>
     * This method takes an item stack and a market tier, and returns a new item stack with the count clamped to a maximum value based on the tier.
     * The clamp values are as follows: TIER1_COMMON=16, TIER2_UNCOMMON=8, TIER3_RARE=8, TIER4_EPIC=4.
     * The resulting item stack will have a count that is the minimum of its original count, the clamp value, and its maximum stack size.
     * <p>
     * This method is intended to be used server-side.
     * @param stack the item stack to normalize
     * @param tier the market tier to use when normalizing
     * @return a new item stack with the count clamped according to the tier
     */
    private static ItemStack normalizeForSale(ItemStack stack, MarketTier tier)
    {
        ItemStack out = stack.copy();

        // Keep “rare” feeling rare: clamp stack sizes more aggressively at higher tiers
        int min = switch (tier)
        {
            case TIER1_COMMON -> 8;
            case TIER2_UNCOMMON -> 4;
            case TIER3_RARE -> 2;
            case TIER4_EPIC -> 1;
        };

        int max = switch (tier)
        {
            case TIER1_COMMON -> 32;
            case TIER2_UNCOMMON -> 16;
            case TIER3_RARE -> 8;
            case TIER4_EPIC -> 4;
        };

        int stacksize = MathUtils.clamp(out.getCount(), Math.min(min, out.getMaxStackSize()), Math.min(max, out.getMaxStackSize()));

        out.setCount(stacksize);
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
