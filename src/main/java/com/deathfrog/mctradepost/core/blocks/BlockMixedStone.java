package com.deathfrog.mctradepost.core.blocks;


import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

public class BlockMixedStone extends Block {
    public static final String BLOCK_MIXED_STONE = "mixed_stone";

    public BlockMixedStone() {
        super(Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f, 6.0f)
            .sound(SoundType.STONE));
    }
}
