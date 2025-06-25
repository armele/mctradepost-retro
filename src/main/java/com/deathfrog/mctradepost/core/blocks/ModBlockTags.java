package com.deathfrog.mctradepost.core.blocks;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class ModBlockTags {
    public static final ResourceLocation TRACK_TAG_KEY = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "track");

    public static final TagKey<Block> TRACK_TAG = BlockTags.create(TRACK_TAG_KEY);

}
