package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.ChunkUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.ModTags;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TrackPathConnection
{
    public static final int RAIL_CHUNK_RADIUS = 2;
    private static final int MAX_DEPTH = 10000; // Prevent runaway traversal

    public static class TrackConnectionResult
    {
        public boolean connected;
        public BlockPos closestPoint;         // same semantics as before
        public List<BlockPos> path;           // ordered start → end, tracks only
        public long lastChecked = 0L;

        public TrackConnectionResult(boolean connected, BlockPos closestPoint, List<BlockPos> path, long now)
        {
            this.connected = connected;
            this.closestPoint = closestPoint;
            this.path = path;
            this.lastChecked = now;
        }

        /**
         * Sets the connection status of the track path.
         *
         * @param connected a boolean indicating whether the track path is connected (true if connected, false otherwise).
         */
        public void setConnected(boolean connected)
        {
            this.connected = connected;
        }

        /**
         * Retrieves the connection status of the track path.
         *
         * @return true if the track path is connected, false otherwise.
         */
        public boolean isConnected()
        {
            return connected;
        }

        /**
         * Calculates the age of the last check of the track path, in game ticks.
         * 
         * @param now the current game time, in game ticks.
         * @return the age of the last check, in game ticks.
         */
        public long ageOfCheck(long now)
        {
            return now - lastChecked;
        }

    }

    /**
     * Checks if two points are connected by a path of track blocks in the given level. The path is traversed in a breadth-first
     * manner, first exploring all blocks at the same Y-height as the start point, then exploring all blocks one block higher or lower
     * than that, and so on. This is done to minimize the number of blocks that need to be checked for being part of a track. If the
     * points are connected, a TrackConnectionResult is returned with the connected flag set to true and the closest point set to the
     * end point. If the points are not connected, a TrackConnectionResult is returned with the connected flag set to false and the
     * closest point set to the closest point on the path that was reached during the traversal.
     *
     * @param level the level in which to traverse the track path
     * @param start the start point of the track path
     * @param end   the end point of the track path
     * @return a TrackConnectionResult describing the result of the traversal
     */
    public static TrackConnectionResult arePointsConnectedByTracks(ServerLevel level, BlockPos start, BlockPos end, boolean loadChunks)
    {
        ServerLevel localLevel = level;

        if (localLevel == null || start == null || end == null) return new TrackConnectionResult(false, null, null, level.getGameTime());

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();  // <child , parent>

        toVisit.add(start.immutable());

        BlockPos closestSoFar = start;
        double closestDist = distance(start, end);

        while (!toVisit.isEmpty() && visited.size() < MAX_DEPTH)
        {
            BlockPos current = toVisit.poll();

            // reached or touched the goal?
            if (current.equals(end) || isAdjacent(current, end))
            {
                List<BlockPos> path = rebuildPath(parent, current, start, end);
                return new TrackConnectionResult(true, end, path, level.getGameTime());
            }

            if (!visited.add(current)) continue;   // already handled

            double dist = distance(current, end);
            if (dist < closestDist)
            {
                closestDist = dist;
                closestSoFar = current.immutable();
            }

            /* cardinal moves – same Y */
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                if (dir == null) continue;
                tryMove(level, current, dir, 0, toVisit, visited, parent, loadChunks);
            }

            /* upward / downward slopes */
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                if (dir == null) continue;
                tryMove(level, current, dir, +1, toVisit, visited, parent, loadChunks);
                tryMove(level, current, dir, -1, toVisit, visited, parent, loadChunks);
            }
        }

        // not connected
        return new TrackConnectionResult(false, closestSoFar, List.of(), level.getGameTime());
    }

    /**
     * Validates an existing track path by checking that all the points in the middle of the path are track blocks. The first and last
     * points in the path are not required to be tracks. If any of the points in the middle of the path are not tracks, a
     * TrackConnectionResult is returned with the connected flag set to false and the closest point set to the last valid track point.
     * If all the points in the middle of the path are tracks, a TrackConnectionResult is returned with the connected flag set to true
     * and the closest point set to the last point in the path.
     *
     * @param level the level in which to traverse the track path
     * @param path  the list of BlockPos to traverse
     * @return flag indicating whether the path is still valid.
     */
    public static boolean validateExistingPath(ServerLevel level, TrackConnectionResult connectionResult)
    {
        List<BlockPos> path = connectionResult.path;
        connectionResult.lastChecked = level.getGameTime();

        if (level == null || path == null) return false;

        // Note that we are deliberately not testing the very first or very last positions on the list - they are not required to be
        // tracks.
        for (int i = 1; i < path.size() - 2; i++)
        {
            BlockPos nextStep = path.get(i);

            // We've got a bad path - something is missing.
            if (nextStep == null) return false;

            // We're going to be optimistic about unloaded positions - assume they are still tracks.
            boolean isTrack = isTrackBlock(level, nextStep) || !level.isLoaded(nextStep);

            if (!isTrack)
            {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Tries to move in the given direction from the given BlockPos to find the next block on the track path. If the next block is a
     * track block and has not been visited before, it is added to the queue to be visited and the parent map is updated to remember
     * where we came from.
     *
     * @param level   the level in which to traverse the track path
     * @param from    the BlockPos from which to move
     * @param dir     the direction in which to move
     * @param dy      the vertical offset to apply to the BlockPos
     * @param q       the queue of BlockPos to visit
     * @param visited the set of BlockPos that have already been visited
     * @param parent  the map of BlockPos to BlockPos that tracks the parent of each BlockPos in the path
     */
    private static void tryMove(ServerLevel level,
        BlockPos from,
        @Nonnull Direction dir,
        int dy,
        Queue<BlockPos> q,
        Set<BlockPos> visited,
        Map<BlockPos, BlockPos> parent,
        boolean loadChunks)
    {
        BlockPos nxt = from.relative(dir).offset(0, dy, 0);

        if (nxt == null) return;

        if (!level.isLoaded(nxt))
        {
            if (loadChunks)
            {
                ChunkUtil.ensureChunkLoadedByTicket(level, nxt, RAIL_CHUNK_RADIUS, ChunkUtil.RAIL_TICKET);
            }
        }

        if (!visited.contains(nxt) && isTrackBlock(level, nxt))
        {
            q.add(nxt.immutable());
            parent.put(nxt.immutable(), from);   // remember where we came from
        }
    }

    /**
     * Rebuilds the path from the given current BlockPos to the start BlockPos, inclusive of both, using the parent map to traverse
     * backwards from current to start. If the current BlockPos is adjacent to the end BlockPos, the end BlockPos is appended to the
     * returned list so that the caller can see the full span of the path from start to end.
     *
     * @param parent  a map of BlockPos to BlockPos, where each key has the value of its parent BlockPos on the path from current to
     *                start
     * @param current the BlockPos at which to start the path reconstruction
     * @param start   the BlockPos at which the path reconstruction should terminate
     * @param end     the BlockPos adjacent to which the path reconstruction may terminate
     * @return a List of BlockPos representing the path from start to current, inclusive of both, and possibly including the end
     *         BlockPos if it was adjacent to the current BlockPos
     */
    private static List<BlockPos> rebuildPath(Map<BlockPos, BlockPos> parent, BlockPos current, BlockPos start, BlockPos end)
    {
        List<BlockPos> rev = new ArrayList<>();
        BlockPos iter = current;
        while (iter != null && !iter.equals(start))
        {
            rev.add(iter);
            iter = parent.get(iter);
        }
        rev.add(start);                // include the origin
        Collections.reverse(rev);      // now in start → goal order

        if (!rev.get(rev.size() - 1).equals(end))
        {
            // we stopped adjacent to a non-track “end”; append it so caller sees full span
            rev.add(end);
        }
        return Collections.unmodifiableList(rev);
    }

    /**
     * Determines if the block at the specified position is a track block. This method checks if the block is an instance of
     * BaseRailBlock, which represents a standard rail block in Minecraft.
     * 
     * @param level the ServerLevel to check the block in
     * @param pos   the BlockPos of the block to check
     * @return true if the block is a track block, false otherwise
     */
    private static boolean isTrackBlock(@Nonnull ServerLevel level, @Nonnull BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof BaseRailBlock || state.is(NullnessBridge.assumeNonnull(ModTags.BLOCKS.TRACK_TAG)))
        {
            return true;
        }

        return false;
    }

    /**
     * Calculates the Euclidean distance between two BlockPos objects. This is used to determine the closest point on the track path to
     * the end point.
     * 
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
     * Determines if two BlockPos objects are adjacent to each other. This is used during the track path traversal to determine if a
     * block is directly adjacent to another block. Two BlockPos objects are considered adjacent if the absolute difference in each
     * dimension (x, y, z) is less than or equal to 1.
     * 
     * @param a the first BlockPos
     * @param b the second BlockPos
     * @return true if the two BlockPos objects are adjacent, false otherwise
     */
    private static boolean isAdjacent(BlockPos a, BlockPos b)
    {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
    }
}
