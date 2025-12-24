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

    /* Item Tags */
    public static final ResourceLocation STEW_INGREDIENTS_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "stew_ingredients");
    @SuppressWarnings("null")
    public @Nonnull static final TagKey<Item> STEW_INGREDIENTS_TAG = ItemTags.create(STEW_INGREDIENTS_TAG_KEY);

}
