package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TrackPathConnection
{
    private static final int MAX_DEPTH = 10000; // Prevent runaway traversal

    public static class TrackConnectionResult
    {
        public final boolean connected;
        public final BlockPos closestPoint;

        public TrackConnectionResult(boolean connected, BlockPos closestPoint)
        {
            this.connected = connected;
            this.closestPoint = closestPoint;
        }
    }

    /**
     * Checks if two points are connected by a path of track blocks in the given level.
     * The path is traversed in a breadth-first manner, first exploring all blocks at
     * the same Y-height as the start point, then exploring all blocks one block higher
     * or lower than that, and so on. This is done to minimize the number of blocks
     * that need to be checked for being part of a track.
     *
     * If the points are connected, a TrackConnectionResult is returned with the
     * connected flag set to true and the closest point set to the end point. If the
     * points are not connected, a TrackConnectionResult is returned with the connected
     * flag set to false and the closest point set to the closest point on the path
     * that was reached during the traversal.
     *
     * @param level the level in which to traverse the track path
     * @param start the start point of the track path
     * @param end the end point of the track path
     * @return a TrackConnectionResult describing the result of the traversal
     */
    public static TrackConnectionResult arePointsConnectedByTracks(ServerLevel level, BlockPos start, BlockPos end)
    {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.add(start.immutable());

        BlockPos closestSoFar = start;
        double closestDist = distance(start, end);

        while (!toVisit.isEmpty() && visited.size() < MAX_DEPTH)
        {
            BlockPos current = toVisit.poll();

            if (current.equals(end) || isAdjacent(current, end))
            {
                return new TrackConnectionResult(true, end);
            }

            if (!visited.add(current))
            {
                continue;
            }

            double dist = distance(current, end);
            if (dist < closestDist)
            {
                closestDist = dist;
                closestSoFar = current.immutable();
            }

            // Check cardinal directions on same Y
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                checkAndAdd(level, current.relative(dir), toVisit, visited);
            }

            // Check upward/downward slopes in cardinal directions
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                checkAndAdd(level, current.relative(dir).above(), toVisit, visited);
                checkAndAdd(level, current.relative(dir).below(), toVisit, visited);
            }
        }

        return new TrackConnectionResult(false, closestSoFar);
    }

    /**
     * Checks if a block is a track block and hasn't been visited before, and if so, adds it to the queue of blocks to visit.
     * This is used during the track path traversal to add new blocks to the search queue.
     * @param level the level in which to check the block
     * @param pos the position of the block to check
     * @param toVisit the queue of blocks to visit
     * @param visited the set of blocks that have already been visited
     */
    private static void checkAndAdd(ServerLevel level, BlockPos pos, Queue<BlockPos> toVisit, Set<BlockPos> visited)
    {
        if (!visited.contains(pos) && isTrackBlock(level, pos))
        {
            toVisit.add(pos.immutable());
        }
    }

    /**
     * Determines if the block at the specified position is a track block.
     * This method checks if the block is an instance of BaseRailBlock,
     * which represents a standard rail block in Minecraft.
     * 
     * @param level the ServerLevel to check the block in
     * @param pos the BlockPos of the block to check
     * @return true if the block is a track block, false otherwise
     */
    private static boolean isTrackBlock(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BaseRailBlock)
        {
            return true;
        }

        // Optional: support modded tracks by tag
        // return state.is(ModTags.TRACK_BLOCKS);

        return false;
    }

    /**
     * Calculates the Euclidean distance between two BlockPos objects.
     * This is used to determine the closest point on the track path to the end point.
     * @param a the first BlockPos
     * @param b the second BlockPos
     * @return the Euclidean distance between the two BlockPos objects
     */
    private static double distance(BlockPos a, BlockPos b)
    {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Determines if two BlockPos objects are adjacent to each other.
     * This is used during the track path traversal to determine if a block is directly adjacent to another block.
     * Two BlockPos objects are considered adjacent if the absolute difference in each dimension (x, y, z) is less than or equal to 1.
     * @param a the first BlockPos
     * @param b the second BlockPos
     * @return true if the two BlockPos objects are adjacent, false otherwise
     */
    private static boolean isAdjacent(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
    }   
}
