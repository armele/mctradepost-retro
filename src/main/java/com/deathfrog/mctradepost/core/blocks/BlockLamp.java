package com.deathfrog.mctradepost.core.blocks;

import javax.annotation.Nonnull;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

public class BlockLamp extends LanternBlock
{
    
    @SuppressWarnings("null")
    public BlockLamp(Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(HANGING, false));
    }

    /**
     * Gets the block state for this block based on the context of placement.
     * The block state returned by this method will have the HANGING property set to true if the block is being placed against the ceiling, and false otherwise.
     * The returned block state will be null if the block cannot survive at the given position.
     * @param ctx the context of placement
     * @return the block state that should be used when placing the block in the world
     */
    @SuppressWarnings("null")
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext ctx) 
    {
        Direction face = ctx.getClickedFace();
        boolean hanging = (face == Direction.DOWN);
        BlockState state = this.defaultBlockState().setValue(HANGING, hanging);
        
        return state.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? state : null;
    }
}
