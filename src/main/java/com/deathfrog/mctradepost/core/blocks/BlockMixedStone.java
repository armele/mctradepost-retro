package com.deathfrog.mctradepost.core.blocks;


import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

public class BlockMixedStone extends Block {
    public static final String MIXED_STONE_ID = "mixed_stone";
    public static final String MIXED_STONE_WALL_ID = "mixed_stone_wall";
    public static final String MIXED_STONE_STAIRS_ID = "mixed_stone_stairs";
    public static final String MIXED_STONE_SLAB_ID = "mixed_stone_slab";
    
    public BlockMixedStone() {
        super(Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f, 6.0f)
            .sound(SoundType.STONE));
    }
}
