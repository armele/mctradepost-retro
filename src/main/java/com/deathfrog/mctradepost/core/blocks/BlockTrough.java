package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockTrough extends AbstractBlockPetWorkingLocation
{

    public BlockTrough(@Nonnull Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) 
    {
        VoxelShape SHAPE = Block.box(0, 2, 2, 14, 8, 16);
        return SHAPE;
    }
}
