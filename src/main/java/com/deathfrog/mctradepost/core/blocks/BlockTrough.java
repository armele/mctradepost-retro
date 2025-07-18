package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockTrough extends Block
{
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockTrough(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context)
    {
        // Rotate the block to face *opposite* the player's horizontal facing direction
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public boolean isCollisionShapeFullBlock(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return false;
    }

    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        VoxelShape SHAPE = Block.box(0, 2, 2, 14, 8, 16);
        return SHAPE;
    }

    @Override
    public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
        return true;
    }
}
