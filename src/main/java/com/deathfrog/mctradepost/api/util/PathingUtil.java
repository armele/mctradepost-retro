package com.deathfrog.mctradepost.api.util;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class PathingUtil {
    public static final TagKey<Block> ICY =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "icy"));

    /**
     * Finds the top of the water column starting at the given position. This method
     * will return the highest block position that is water or ice, as long as the
     * block above it is air or ice (i.e. a surface closure is fine). If the starting
     * block is not water or ice, it will return null.
     *
     * @param lvl the level to search in
     * @param pos the starting position to search from
     * @return the top of the water column, or null if the starting block is not water or ice
     */
    public static BlockPos findTopOfWaterColumn(Level lvl, BlockPos pos)
    {
        // Quick reject
        var state = lvl.getBlockState(pos);
        if (!isWaterOrIce(state)) return null;

        BlockPos cur = pos;
        // Climb up while still water/ice
        while (isWaterOrIce(lvl.getBlockState(cur.above())))
        {
            cur = cur.above();
        }
        // Ensure we ended on water/ice and above is air or ice (surface closure is fine)
        var above = lvl.getBlockState(cur.above());
        if (isWaterOrIce(state) && (above.isAir() || isIce(above)))
        {
            return cur;
        }
        return null;
    }

    public static boolean isIce(BlockState s)
    {
        // If you have your ICY tag, keep it. Otherwise check vanilla ice classes/tags as needed.
        // Example using your tag from earlier:
        return s.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("mctradepost", "icy")));
    }

    public static boolean isOpenOrIce(BlockState s) {
        return s.isAir() || s.is(ICY);
    }

    public static boolean isWaterOrIce(BlockState s) {
        return s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || s.is(ICY);
    }

    public static boolean isWater(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().is(FluidTags.WATER);
    }

    public static boolean isSwimmableWater(Level level, BlockPos pos)
    {
        BlockState s = level.getBlockState(pos);
        if (!s.getFluidState().is(FluidTags.WATER)) return false;          // includes waterlogged
        // 1.21.1 API: no level/pos params
        if (!s.isPathfindable(PathComputationType.WATER)) return false;    // navigable for water pathing
        // Headroom: either water above or air to fit the axolotl
        BlockState above = level.getBlockState(pos.above());
        return above.getFluidState().is(FluidTags.WATER) || above.isAir();
    }

    public static boolean isStandableBank(Level level, BlockPos pos)
    {
        // We want a tile the axolotl can step onto: empty headroom and solid floor.
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || here.getFluidState().isEmpty())) return false; // not blocked by solids/fluids
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP);
    }
}
