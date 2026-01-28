package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockFeeder extends AbstractBlockPetWorkingLocation
{
    public static final @Nonnull BooleanProperty HANGING = NullnessBridge.assumeNonnull(BooleanProperty.create("hanging"));

    @SuppressWarnings("null")
    public BlockFeeder(@Nonnull Properties props) 
    {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(HANGING, false));
    }


    /**
     * Defines the block state properties for this block.
     * <p>This method is called by the constructor of the block to define the properties of the block, and is used by the game to
     * manage the state of the block in the world. The properties defined by this method are stored in the block's block state, and can
     * be accessed by other parts of the game to determine the block's behavior and appearance.
     * <p>This method adds the HANGING property to the block state definition, which is a boolean property that indicates whether
     * the block is hanging from the ceiling or not. The value of this property can be accessed using the {@link #isHanging} method.
     * 
     * @param builder the block state definition builder
     * @see #isHanging
     */
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) 
    {
        super.createBlockStateDefinition(builder);
        builder.add(HANGING);
    }

    /**
     * Gets the block state for this block based on the context of placement.
     * The block state returned by this method will have the HANGING property set to true if the block is being placed against the ceiling, and false otherwise.
     * The returned block state will be null if the block cannot survive at the given position.
     * @param ctx the context of placement
     * @return the block state that should be used when placing the block in the world
     */
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx) 
    {
        Direction face = ctx.getClickedFace();
        boolean hanging = (face == Direction.DOWN); // placing against ceiling
        BlockState state = this.defaultBlockState().setValue(HANGING, hanging);

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();

        if (level == null || pos == null) 
        {
            return state;
        }

        return state.canSurvive(level, pos) ? state : null;
    }

    /**
     * Determines if the given block state can survive at the given position in the level.
     * The block state is considered to be "hanging" if its HANGING property is set to true.
     * If the block state is hanging, this method checks if the block above the given position is face-sturdy in the downwards direction.
     * If the block state is not hanging, this method checks if the block below the given position is face-sturdy in the upwards direction.
     * @param state the block state to check
     * @param level the level containing the block
     * @param pos the position of the block
     * @return true if the block state can survive at the given position, false otherwise
     */
    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) 
    {
        boolean hanging = state.getValue(HANGING);

        if (hanging) 
        {
            @Nonnull BlockPos abovePos = NullnessBridge.assumeNonnull(pos.above());
            BlockState above = level.getBlockState(abovePos);
            return above.isFaceSturdy(level, abovePos, Direction.DOWN);
        } 
        else 
        {
            @Nonnull BlockPos belowPos = NullnessBridge.assumeNonnull(pos.below());
            BlockState below = level.getBlockState(belowPos);
            return below.isFaceSturdy(level, belowPos, Direction.UP);
        }
    }

    /**
     * Updates the block state of this block based on the block state of a neighboring block.
     * If the block state cannot survive at the given position, this method returns the default block state of air.
     * Otherwise, this method calls the superclass implementation of updateShape.
     * @param state the block state of this block
     * @param changedSide the direction of the neighbor block
     * @param changedState the block state of the neighbor block
     * @param level the level containing this block
     * @param pos the position of this block
     * @param changedPos the position of the neighbor block
     * @return the updated block state of this block
     */
    @Override
    public BlockState updateShape(
        @Nonnull BlockState state,
        @Nonnull Direction changedSide,
        @Nonnull BlockState changedState,
        @Nonnull LevelAccessor level,
        @Nonnull BlockPos pos,
        @Nonnull BlockPos changedPos
    ) 
    {
        if (!state.canSurvive(level, pos)) 
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, changedSide, changedState, level, pos, changedPos);
    }


    /**
     * Gets the collision shape of the block in the given block state.
     * The collision shape is a rectangular prism with the following dimensions:
     *     <ul>
     *     <li>x: 0-14</li>
     *     <li>y: 2-14</li>
     *     <li>z: 2-16</li>
     *     </ul>
     * The shape is used to determine the block's collision behavior with other blocks and entities.
     * @param state the block state of the block
     * @param level the level containing the block
     * @param pos the position of the block
     * @param context the collision context
     * @return the collision shape of the block
     */
    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        VoxelShape SHAPE = Block.box(0, 2, 2, 14, 14, 16);
        return SHAPE;
    }
}
