package com.deathfrog.mctradepost.core;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModTags 
{
    /* Block Tags */
    public static final ResourceLocation TRACK_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "track");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Block> TRACK_TAG = BlockTags.create(TRACK_TAG_KEY);

    public static final ResourceLocation FRUIT_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "scavenge_fruit");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Block> TAG_FRUIT = BlockTags.create(FRUIT_TAG_KEY);

    public static final ResourceLocation SCAVENGE_LEAVES_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "scavenge_leaves");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Block> TAG_SCAVENGE_LEAVES = BlockTags.create(SCAVENGE_LEAVES_TAG_KEY);
 
    public static final ResourceLocation GROUNDCOVER_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "scavenge_groundcover");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Block> TAG_GROUNDCOVER = BlockTags.create(GROUNDCOVER_KEY);   

    public static final ResourceLocation WATER_SCAVENGE_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "amphibious_scavenge");
    @SuppressWarnings("null")
    public static final TagKey<Block> WATER_SCAVENGE_BLOCK_TAG = BlockTags.create(WATER_SCAVENGE_TAG_KEY);

    /* Item Tags */
    public static final ResourceLocation STEW_INGREDIENTS_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "stew_ingredients");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> STEW_INGREDIENTS_TAG = ItemTags.create(STEW_INGREDIENTS_TAG_KEY);

    public static final ResourceLocation RARE_FINDS_BLACKLIST_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_blacklist");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> RARE_FINDS_BLACKLIST_TAG = ItemTags.create(RARE_FINDS_BLACKLIST_TAG_KEY);

    public static final ResourceLocation RARE_FINDS_TIER1_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_tier1");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> RARE_FINDS_TIER1_TAG = ItemTags.create(RARE_FINDS_TIER1_TAG_KEY);

    public static final ResourceLocation RARE_FINDS_TIER2_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_tier2");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> RARE_FINDS_TIER2_TAG = ItemTags.create(RARE_FINDS_TIER2_TAG_KEY);

    public static final ResourceLocation RARE_FINDS_TIER3_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_tier3");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> RARE_FINDS_TIER3_TAG = ItemTags.create(RARE_FINDS_TIER3_TAG_KEY);

    public static final ResourceLocation RARE_FINDS_TIER4_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "rarefinds_tier4");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> RARE_FINDS_TIER4_TAG = ItemTags.create(RARE_FINDS_TIER4_TAG_KEY);

    public static final ResourceLocation OUTPOST_CROPS_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "outpost_crops");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> OUTPOST_CROPS_TAG = ItemTags.create(OUTPOST_CROPS_KEY);

    public static final ResourceLocation BASE_CURRENCY_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "base_currency");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> BASE_CURRENCY_TAG = ItemTags.create(BASE_CURRENCY_KEY);
}
