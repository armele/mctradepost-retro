package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TradeInterchangeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A non-directional, zero-distance handoff joining every adjacent rail and road branch. */
public class BlockTradeInterchange extends Block
{
    public static final String ID = "trade_interchange";
    /** Full footprint matching the model's approximately 2.4-pixel maximum height. */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 2.5D / 16.0D, 1.0D);

    public BlockTradeInterchange(@Nonnull Properties properties)
    {
        super(properties);
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
    protected void onPlace(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) TradeInterchangeRegistry.get(serverLevel).add(pos);
    }

    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean movedByPiston)
    {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) TradeInterchangeRegistry.get(serverLevel).remove(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
