package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.tags.FluidTags;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TradeDockRegistry;
import com.mojang.serialization.MapCodec;

/**
 * A facing-aware transition between a land transport path and navigable water.
 * The block faces its water endpoint; the opposite side is its land endpoint.
 */
public class BlockTradeDock extends HorizontalDirectionalBlock
{
    public static final String ID = "trade_dock";
    public static final MapCodec<BlockTradeDock> CODEC = simpleCodec(BlockTradeDock::new);

    /** Full footprint matching the model's approximately 2.4-pixel maximum height. */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 2.5D / 16.0D, 1.0D);

    @SuppressWarnings("null")
    public BlockTradeDock(@Nonnull Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec()
    {
        return CODEC;
    }

    /**
     * Resolves the navigable water beside the dock. A dock may sit level with
     * the water or, more commonly, one block above it.
     */
    @SuppressWarnings("null")
    public static BlockPos waterEndpoint(LevelReader level, BlockState state, BlockPos dock)
    {
        BlockPos facing = dock.relative(state.getValue(FACING));
        if (isNavigableWater(level, facing)) return facing;

        BlockPos below = facing.below();
        if (isNavigableWater(level, below)) return below;

        // Retain the deterministic facing endpoint when neither candidate is
        // currently valid so route discovery can report a normal failure.
        return facing;
    }

    @SuppressWarnings("null")
    private static boolean isNavigableWater(LevelReader level, @Nonnull BlockPos pos)
    {
        return level.getFluidState(pos).is(NullnessBridge.assumeNonnull(FluidTags.WATER))
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    @SuppressWarnings("null")
    public static BlockPos landEndpoint(BlockState state, BlockPos dock)
    {
        return dock.relative(state.getValue(FACING).getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @SuppressWarnings("null")
    @Override
    protected BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    protected BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror)
    {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) TradeDockRegistry.get(serverLevel).add(pos);
    }

    @SuppressWarnings("null")
    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean movedByPiston)
    {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) TradeDockRegistry.get(serverLevel).remove(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
