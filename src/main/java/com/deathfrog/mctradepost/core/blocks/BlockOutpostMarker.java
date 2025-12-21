package com.deathfrog.mctradepost.core.blocks;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class BlockOutpostMarker extends Block
{

    public BlockOutpostMarker(@Nonnull BlockBehaviour.Properties props)
    {
        super(props);
    }
    
    /**
     * Places an outpost flag at the specified BlockPos within the ServerLevel.
     * This method will return false if the outpost flag cannot be placed at the specified BlockPos.
     * This method will fire standard hooks (drops, sounds, be init, etc.) after the outpost flag is placed.
     * @param level the ServerLevel where the outpost flag is being placed
     * @param pos the BlockPos of the outpost flag structure
     * @param placer the ServerPlayer who is placing the outpost flag, or null if not applicable
     * @return true if the outpost flag was placed, false otherwise
     */
    @SuppressWarnings("null")
    public static boolean placeOutpostMarker(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nullable ServerPlayer placer)
    {
        if (level.isClientSide) return false;

        BlockPos target = pos.above();

        if (target == null) return false;

        // Make sure the chunk is loaded and spot is replaceable
        if (!level.isAreaLoaded(target, 1)) return false;
        if (!level.getBlockState(target).canBeReplaced()) return false;

        Block block = MCTradePostMod.BLOCK_OUTPOST_MARKER.get();
        BlockState state = block.defaultBlockState();

        // Orient to player if the block has a facing property
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            Direction face = (placer != null ? placer.getDirection().getOpposite() : Direction.NORTH);
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, face);
        }

        if (state.hasProperty(BlockStateProperties.WATERLOGGED))
        {
            boolean water = level.getFluidState(target).is(FluidTags.WATER);
            state = state.setValue(BlockStateProperties.WATERLOGGED, water);
        }

        if (!state.canSurvive(level, target)) return false;

        boolean ok = level.setBlock(target, state, Block.UPDATE_ALL);
        if (ok)
        {
            // Fire standard hooks (drops, sounds, be init, etc.)
            block.setPlacedBy(level, target, state, placer, ItemStack.EMPTY);
        }
        
        return ok;
    }
}
