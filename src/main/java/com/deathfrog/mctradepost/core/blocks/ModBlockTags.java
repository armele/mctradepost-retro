package com.deathfrog.mctradepost.core.blocks;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModBlockTags 
{
    public static final ResourceLocation TRACK_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "track");

    public static final TagKey<Block> TRACK_TAG = BlockTags.create(NullnessBridge.assumeNonnull(TRACK_TAG_KEY));

}
