package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockDredger extends AbstractBlockPetWorkingLocation implements SimpleWaterloggedBlock
{
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public BlockDredger(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    /**
     * Defines the block state properties for this block.
     *
     * <p>This method is called by the constructor of the block to define the properties of the block, and is used by
     * the game to manage the state of the block in the world. The properties defined by this method are stored in the
     * block's block state, and can be accessed by other parts of the game to determine the block's behavior and
     * appearance.
     *
     * <p>This method adds the WATERLOGGED property to the block state definition, which is a boolean property that
     * indicates whether the block is waterlogged or not. The value of this property can be accessed using the
     * {@link #isWaterlogged} method.
     *
     * @param builder the block state definition builder
     * @see #isWaterlogged
     */
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    /**
     * Gets the block state for this block based on the context of placement.
     *
     * @param ctx the context of placement
     * @return the block state that should be used when placing the block in the world
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        boolean inWater = level.getFluidState(pos).is(FluidTags.WATER);
        return this.defaultBlockState().setValue(WATERLOGGED, inWater);
    }

    /**
     * Updates the block state of this block based on its neighbors. If the block is waterlogged, it will schedule a
     * fluid tick to update the water surrounding the block.
     *
     * @param state the current state of this block
     * @param dir the direction of the neighbor block
     * @param neighborState the state of the neighbor block
     * @param level the level containing this block
     * @param pos the position of this block
     * @param neighborPos the position of the neighbor block
     * @return the updated state of this block
     */
    @Override
    public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction dir, @Nonnull BlockState neighborState,
                                  @Nonnull LevelAccessor level, @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /**
     * @return the fluid state of the block. If the block is waterlogged (i.e. it is in water), then the fluid state is
     *         that of still water. Otherwise, the fluid state is the default fluid state for the block.
     */
    @Override
    public FluidState getFluidState(@Nonnull BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * @return the collision shape of the block. The shape is a rectangular prism with the following dimensions:
     *         <ul>
     *             <li>x: 0-14</li>
     *             <li>y: 2-16</li>
     *             <li>z: 2-16</li>
     *         </ul>
     *         The shape is used to determine the block's collision behavior with other blocks and entities.
     */
    @Override
    public VoxelShape getShape(@Nonnull BlockState state,
        @Nonnull BlockGetter level,
        @Nonnull BlockPos pos,
        @Nonnull CollisionContext context)
    {
        VoxelShape SHAPE = Block.box(0, 2, 2, 14, 16, 16);
        return SHAPE;
    }
}
