package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import com.deathfrog.mctradepost.core.blocks.ModBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TrackPathConnection
{
    private static final int MAX_DEPTH = 10000; // Prevent runaway traversal

    /**
     * Determines if two points are connected by tracks within a Minecraft level.
     *
     * This method performs a breadth-first search to check if there is a continuous
     * path of tracks connecting the starting position to the ending position. It 
     * considers both horizontal tracks and upward/downward slopes, and limits the 
     * search depth to prevent runaway traversal.
     *
     * @param level the Minecraft server level in which the tracks are located
     * @param start the starting position to begin the track search
     * @param end the ending position to check for track connection
     * @return true if there is a track path connecting the start and end positions, 
     *         false otherwise
     */
    public static boolean arePointsConnectedByTracks(ServerLevel level, BlockPos start, BlockPos end)
    {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.add(start.immutable());

        while (!toVisit.isEmpty() && visited.size() < MAX_DEPTH)
        {
            BlockPos current = toVisit.poll();

            if (current.equals(end))
            {
                return true;
            }

            if (!visited.add(current))
            {
                continue;
            }

            // Check cardinal directions on same Y
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                checkAndAdd(level, current.relative(dir), toVisit, visited);
            }

            // Check upward and downward slopes in cardinal directions
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                checkAndAdd(level, current.relative(dir).above(), toVisit, visited);
                checkAndAdd(level, current.relative(dir).below(), toVisit, visited);
            }
        }

        return false;
    }

    /**
     * Checks if the given position contains a track block and if it hasn't been visited,
     * adds it to the queue for further exploration.
     *
     * @param level   The server level to perform the check on.
     * @param pos     The position to check for a track block.
     * @param toVisit The queue holding positions to be checked.
     * @param visited The set of positions that have already been visited.
     */
    private static void checkAndAdd(ServerLevel level, BlockPos pos, Queue<BlockPos> toVisit, Set<BlockPos> visited)
    {
        if (!visited.contains(pos) && isTrackBlock(level, pos))
        {
            toVisit.add(pos.immutable());
        }
    }

    /**
     * Checks if the given position contains a track block. Track blocks are either vanilla rail blocks or any block that
     * has the {@link ModBlockTags#TRACK_TAG} tag.
     *
     * @param level The server level to perform the check on.
     * @param pos   The position to check for a track block.
     * @return True if the position contains a track block, false otherwise.
     */
    private static boolean isTrackBlock(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // Check if it's a vanilla rail
        if (block instanceof BaseRailBlock || state.is(ModBlockTags.TRACK_TAG))
        {
            return true;
        }

        return false;
    }
}
