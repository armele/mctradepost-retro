package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;

public class BlockLamp extends LanternBlock
{
    public enum LampAttachment implements StringRepresentable
    {
        FLOOR("floor"), WALL("wall"), CEILING("ceiling");

        private final String name;

        LampAttachment(final String name)
        {
            this.name = name;
        }

        @Override
        public String getSerializedName()
        {
            return name;
        }
    }

    public static final EnumProperty<LampAttachment> ATTACHMENT = EnumProperty.create("attachment", LampAttachment.class);

    @SuppressWarnings("null")
    public static final @Nonnull DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    @SuppressWarnings("null")
    public BlockLamp(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HANGING, false)              // you can keep this for compatibility if you want
            .setValue(FACING, Direction.NORTH)
            .setValue(ATTACHMENT, LampAttachment.FLOOR)
            .setValue(WATERLOGGED, false));
    }

    /**
     * Gets the block state for this block based on the context of placement. The block state returned by this method will have the
     * HANGING property set to true if the block is being placed against the ceiling, and false otherwise. The returned block state
     * will be null if the block cannot survive at the given position.
     * 
     * @param ctx the context of placement
     * @return the block state that should be used when placing the block in the world
     */
    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx)
    {
        final Direction clicked = ctx.getClickedFace();

        LampAttachment attachment;
        Direction facing = ctx.getHorizontalDirection().getOpposite();

        if (clicked == Direction.DOWN)
        {
            attachment = LampAttachment.CEILING;
        }
        else if (clicked.getAxis().isHorizontal())
        {
            attachment = LampAttachment.WALL;
            facing = clicked; // lamp faces away from the wall
        }
        else
        {
            attachment = LampAttachment.FLOOR;
        }

        final boolean waterlogged = ctx.getLevel().getFluidState(ctx.getClickedPos()).getType() == Fluids.WATER;
        final boolean hanging = attachment == LampAttachment.CEILING;

        return this.defaultBlockState()
            .setValue(HANGING, hanging)
            .setValue(FACING, facing)
            .setValue(ATTACHMENT, attachment)
            .setValue(WATERLOGGED, waterlogged);
    }

    /**
     * Defines the block state properties for this block.
     * <p>This method is called by the constructor of the block to define the properties of the block, and is used by the game to
     * manage the state of the block in the world. The properties defined by this method are stored in the block's block state, and can
     * be accessed by other parts of the game to determine the block's behavior and appearance.
     * <p>This method adds the HANGING and FACING properties to the block state definition. The HANGING property is a boolean property
     * that indicates whether the block is hanging from the ceiling or not. The FACING property is a DirectionProperty that indicates
     * which direction the block is facing.
     * <p>
     * 
     * @param builder the block state definition builder
     */
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder)
    {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, ATTACHMENT);
    }

    @SuppressWarnings("null")
    @Override
    public BlockState rotate(@Nonnull BlockState state, @Nonnull Rotation rot)
    {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public BlockState mirror(@Nonnull BlockState state, @Nonnull Mirror mirror)
    {
        return this.rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @SuppressWarnings("null")
    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos)
    {
        return switch (state.getValue(ATTACHMENT))
        {
            case CEILING -> {
                BlockPos above = pos.above();
                yield Block.canSupportCenter(level, above, Direction.DOWN);
            }
            case FLOOR -> {
                BlockPos below = pos.below();
                final BlockState belowState = level.getBlockState(below);
                yield Block.canSupportCenter(level, below, Direction.UP) || belowState.is(ModTags.BLOCKS.LAMP_BASES);
            }
            case WALL -> {
                Direction facing = state.getValue(FACING);
                BlockPos supportPos = pos.relative(facing.getOpposite());
                yield Block.canSupportCenter(level, supportPos, facing);
            }
        };
    }

    /**
     * Updates the block state of this block based on the block state of a neighboring block. If the block state cannot survive at the
     * given position, this method returns the default block state of air. Otherwise, this method returns the given block state.
     * <p>This method is called by the game when a block is placed or removed in the world. The block state returned by this method
     * will be used to update the block at the given position in the world.
     * 
     * @param state         the current block state of the block
     * @param dir           the direction of the neighbor block
     * @param neighborState the block state of the neighbor block
     * @param level         the level containing the block
     * @param pos           the position of the block
     * @param neighborPos   the position of the neighbor block
     * @return the updated block state of the block
     */
    @Override
    public BlockState updateShape(@Nonnull BlockState state,
        @Nonnull Direction dir,
        @Nonnull BlockState neighborState,
        @Nonnull LevelAccessor level,
        @Nonnull BlockPos pos,
        @Nonnull BlockPos neighborPos)
    {
        return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}
