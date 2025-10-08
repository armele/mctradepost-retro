package com.deathfrog.mctradepost.core.placementhandlers;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.core.blocks.huts.BlockHutOutpost;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.core.placementhandlers.HutPlacementHandler;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class OutpostPlacementHandler extends HutPlacementHandler
{
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public boolean canHandle(Level level, BlockPos blockPos, BlockState blockState)
    {
        return blockState.getBlock() instanceof BlockHutOutpost;
    }

    @Override
    public ActionProcessingResult handle(
      @NotNull final Blueprint blueprint,
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final BlockState blockState,
      @Nullable final CompoundTag tileEntityData,
      final boolean complete,
      final BlockPos centerPos,
      final RotationMirror settings)
    {    
        LOGGER.info("Outpost Placement Handler Called at {}", pos);
        return super.handle(blueprint, world, pos, blockState, tileEntityData, complete, centerPos, settings);
    }
    
}
