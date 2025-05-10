package com.deathfrog.mctradepost.api.tileentities;

import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MCTPTileEntityColonyBuilding extends TileEntityColonyBuilding {

    public MCTPTileEntityColonyBuilding(BlockEntityType<? extends AbstractTileEntityColonyBuilding> type, BlockPos pos,
            BlockState state) {
        super(type, pos, state);
    }

    /**
     * Default constructor used to create a new TileEntity via reflection. Do not use.
     */
    public MCTPTileEntityColonyBuilding(final BlockPos pos, final BlockState state)
    {
        this(MCTradePostTileEntities.BUILDING.get(), pos, state);
    }
}
