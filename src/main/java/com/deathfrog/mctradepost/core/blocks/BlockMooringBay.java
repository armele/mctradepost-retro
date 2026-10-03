package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.MooringBayRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A low-profile route block at which airships begin and end an air-trade leg.
 * A valid trade Mooring Bay must additionally have nine sky-open columns centered on the block.
 */
public class BlockMooringBay extends Block
{
    public static final String ID = "mooring_bay";
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 2.5D / 16.0D, 1.0D);

    /**
     * Creates a Mooring Bay with the supplied block properties.
     *
     * @param properties block properties
     */
    public BlockMooringBay(@Nonnull Properties properties)
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
        if (level instanceof ServerLevel serverLevel) MooringBayRegistry.get(serverLevel).add(pos);
    }

    @SuppressWarnings("null")
    @Override
    protected void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean movedByPiston)
    {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) MooringBayRegistry.get(serverLevel).remove(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Tests the nine columns centered above a Mooring Bay for an unobstructed view of the sky.
     *
     * @param level level containing the Bay
     * @param bay Mooring Bay position
     * @return true when every column above the surrounding 3x3 platform is sky-open
     */
    public static boolean isOpenToSky(ServerLevel level, BlockPos bay)
    {
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                final BlockPos above = bay.offset(x, 1, z);

                if (above == null) continue;

                if (!level.canSeeSky(above)) return false;
            }
        }
        return true;
    }
}
