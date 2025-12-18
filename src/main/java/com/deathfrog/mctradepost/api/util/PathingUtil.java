package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class PathingUtil {
    @SuppressWarnings("null")
    public static final TagKey<Block> ICY =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "icy"));

    /**
     * Finds the top of the water column starting at the given position. 
     * It is valid to start at a ground (non-water/ice) block.
     * This method will return the highest block position that is water or ice, as long as the
     * block above it is air or ice (i.e. a surface closure is fine).
     *
     * @param lvl the level to search in
     * @param pos the starting position to search from
     * @return the top of the water column
     */
    @SuppressWarnings("null")
    public static BlockPos findTopOfWaterColumn(Level lvl, BlockPos pos)
    {
        // Quick reject
        BlockState state = lvl.getBlockState(pos.above());
        if (!isWaterOrIce(state)) return pos;

        BlockPos cur = pos;
        // Climb up while still water/ice
        while (isWaterOrIce(lvl.getBlockState(cur.above())))
        {
            cur = cur.above();
        }
        // Ensure we ended on water/ice and above is air or ice (surface closure is fine)
        BlockState above = lvl.getBlockState(cur.above());
        if (isWaterOrIce(state) && (above.isAir() || isIce(above)))
        {
            return cur;
        }
        return null;
    }


    /**
     * Checks if a given block state is an icy block.
     * 
     * @param s the block state to check
     * @return true if the block state is an icy block, false otherwise
     */
    @SuppressWarnings("null")
    public static boolean isIce(BlockState s)
    {
        return s.is(TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("mctradepost", "icy")));
    }

    /**
     * Checks if a given block state is either air or an icy block.
     * 
     * @param s the block state to check
     * @return true if the block state is air or an icy block, false otherwise
     */
    public static boolean isOpenOrIce(BlockState s) 
    {
        return s.isAir() || s.is(NullnessBridge.assumeNonnull(ICY));
    }

    /**
     * Checks if a given block state is either water or an icy block.
     * 
     * @param s the block state to check
     * @return true if the block state is either water or an icy block, false otherwise
     */
    public static boolean isWaterOrIce(BlockState s) 
    {
        return s.getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER)) || s.is(NullnessBridge.assumeNonnull(ICY));
    }

    public static boolean isWater(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER));
    }

    public static boolean isSwimmableWater(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        BlockState s = level.getBlockState(pos);
        
        // includes waterlogged
        if (!s.getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER))) return false;
        
        if (!s.isPathfindable(PathComputationType.WATER)) return false;

        // Headroom: either water above or air to fit the axolotl
        BlockState above = level.getBlockState(NullnessBridge.assumeNonnull(pos.above()));
        return above.getFluidState().is(NullnessBridge.assumeNonnull(FluidTags.WATER)) || above.isAir();
    }

    /**
     * Checks if a given block position is a standable bank for an axolotl.
     * A standable bank is a block that the axolotl can step onto, which means that the block itself must be either air or empty fluid,
     * and the block below it must be solid and sturdy.
     * 
     * @param level the level to check the block in
     * @param pos   the block position to check
     * @return true if the block is a standable bank, false otherwise
     */
    public static boolean isStandableBank(@Nonnull Level level, @Nonnull BlockPos pos)
    {
        // We want a tile the axolotl can step onto: empty headroom and solid floor.
        BlockState here = level.getBlockState(pos);
        if (!(here.isAir() || here.getFluidState().isEmpty())) return false; // not blocked by solids/fluids
        @Nonnull BlockPos below = NullnessBridge.assumeNonnull(pos.below());
        BlockState floor = level.getBlockState(below);
        return floor.isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * Prefer the anchor, then a small ring around it (N/E/S/W + diagonals). If the anchor is ice and the block above is walkable,
     * include that surface too.
     */
    public static List<BlockPos> buildNavCandidates(@Nonnull Level level, @Nonnull BlockPos anchor)
    {
        List<BlockPos> list = new ArrayList<BlockPos>(10);

        // Anchor itself first
        list.add(anchor);

        // If anchor is ice/water with a solid/flat surface just above, try standing there
        BlockPos stand = NullnessBridge.assumeNonnull(anchor.above());
        BlockState above = level.getBlockState(stand);
        BlockState below = level.getBlockState(anchor);
        boolean surfaceWalkable = above.isAir() || above.getCollisionShape(level, stand).isEmpty();

        // treat “flat” if below has any collision (helps on ice slabs/packed ice)
        boolean belowHasCollision = !below.getCollisionShape(level, anchor).isEmpty();
        if (surfaceWalkable && belowHasCollision)
        {
            list.add(stand);
        }

        // 8-neighborhood around anchor at same Y and +1Y
        for (int dy = 0; dy <= 1; dy++)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0 && dy == 0) continue;
                    BlockPos p = anchor.offset(dx, dy, dz);

                    if (p == null) continue;

                    if (level.isInWorldBounds(p) && level.isLoaded(p))
                    {
                        list.add(p);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Attempts to path the given animal to the given position by first trying
     * exact and then tolerant paths to a set of candidate positions around the
     * target, and then as a last resort, moves directly to the center of the
     * target position.
     *
     * @param pet the animal to path
     * @param pos the target position
     * @return true if the animal was moved, false otherwise
     */
    public static boolean flexiblePathing(@Nonnull Animal pet, @Nonnull BlockPos pos, double speed) 
    {
        boolean moved = false;
        
        Level level = pet.level();
        if (level == null) return false;

        List<BlockPos> candidates = PathingUtil.buildNavCandidates(level, pos);

        // 1) Try exact & tolerant paths to each candidate
        outer:
        for (BlockPos c : candidates)
        {
            if (c == null) continue;

            for (int tol = 0; tol <= 2; tol++)
            { // tolerance 0..2
                Path path = pet.getNavigation().createPath(c, tol);
                if (path != null && pet.getNavigation().moveTo(path, 1.0))
                {
                    moved = true;
                    break outer;
                }
            }
        }

        // 2) Last resort: blind move toward the center (helps on slick ice)
        if (!moved)
        {
            moved = pet.getNavigation().moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, speed);
        }

        return moved;
    }

}
