package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.world.level.block.Block;

public class BlockMixedStone extends Block
{
    public static final String MIXED_STONE_ID = "mixed_stone";
    public static final String MIXED_STONE_WALL_ID = "mixed_stone_wall";
    public static final String MIXED_STONE_STAIRS_ID = "mixed_stone_stairs";
    public static final String MIXED_STONE_SLAB_ID = "mixed_stone_slab";

    public BlockMixedStone(@Nonnull Properties props)
    {
        super(props);
    }
}
