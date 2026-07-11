package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.ChunkUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.ModTags;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_TRACKPATH;

public class TrackPathConnection
{
    public static final int RAIL_CHUNK_RADIUS = 2;
    private static final int MAX_DEPTH = 10000; // Prevent runaway traversal
    private static final int BFS_PROGRESS_LOG_INTERVAL = 1000;
    private static final long SLOW_BFS_LOG_NANOS = 50_000_000L;
    private static final AtomicLong RAIL_SEARCH_SEQUENCE = new AtomicLong();

    public static class TrackConnectionResult
    {
        public boolean connected;
        public BlockPos closestPoint;         // same semantics as before
        public List<BlockPos> path;           // ordered start → end, tracks only
        public TrackRoute route;
        public long lastChecked = 0L;

        public TrackConnectionResult(boolean connected, BlockPos closestPoint, List<BlockPos> path, long now)
        {
            this(connected, closestPoint, path, now, null);
        }

        public TrackConnectionResult(boolean connected, BlockPos closestPoint, List<BlockPos> path, long now, TrackRoute route)
        {
            this.connected = connected;
            this.closestPoint = closestPoint;
            this.path = path;
            this.route = route;
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
         * Gets the dimension-aware route for this result.
         * <p>
         * Legacy single-dimension results are wrapped as Overworld routes to keep older callers working.
         *
         * @return route for this connection, or null when no path is available
         */
        @SuppressWarnings("null")
        public TrackRoute getRoute()
        {
            if (route != null)
            {
                return route;
            }
            if (path != null && !path.isEmpty())
            {
                return TrackRoute.singleDimension(Level.OVERWORLD, path);
            }
            return null;
        }

        /**
         * @return total route distance, including dimensional transfer hops
         */
        public int getRouteDistance()
        {
            TrackRoute localRoute = getRoute();
            if (localRoute != null)
            {
                return localRoute.totalDistance();
            }
            return path == null ? 0 : path.size();
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
    @SuppressWarnings("null")
    public static TrackConnectionResult arePointsConnectedByTracks(ServerLevel level, BlockPos start, BlockPos end, boolean loadChunks)
    {
        ServerLevel localLevel = level;

        if (localLevel == null || start == null || end == null) return new TrackConnectionResult(false, null, null, level.getGameTime());

        RailSearchDiagnostics diagnostics = new RailSearchDiagnostics(localLevel, start, end, loadChunks);
        Set<BlockPos> visited = new HashSet<>();
        Set<ChunkPos> addedRailTickets = new HashSet<>();
        Queue<BlockPos> toVisit = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();  // <child , parent>

        toVisit.add(start.immutable());

        BlockPos closestSoFar = start;
        double closestDist = distance(start, end);

        try
        {
        diagnostics.logStart();
        while (!toVisit.isEmpty() && visited.size() < MAX_DEPTH)
        {
            BlockPos current = toVisit.poll();

            // reached or touched the goal?
            if (current.equals(end) || isAdjacent(current, end))
            {
                List<BlockPos> path = rebuildPath(parent, current, start, end);
                diagnostics.logFinished("connected", visited.size(), toVisit.size(), addedRailTickets.size(), closestSoFar);
                return new TrackConnectionResult(true, end, path, localLevel.getGameTime(), TrackRoute.singleDimension(localLevel.dimension(), path));
            }

            if (!visited.add(current)) continue;   // already handled
            diagnostics.logProgressIfNeeded(visited.size(), toVisit.size(), current, closestSoFar, addedRailTickets.size());

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
                tryMove(localLevel, current, dir, 0, toVisit, visited, parent, loadChunks, addedRailTickets, diagnostics);
            }

            /* upward / downward slopes */
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                if (dir == null) continue;
                tryMove(localLevel, current, dir, +1, toVisit, visited, parent, loadChunks, addedRailTickets, diagnostics);
                tryMove(localLevel, current, dir, -1, toVisit, visited, parent, loadChunks, addedRailTickets, diagnostics);
            }
        }

        // not connected
        diagnostics.logFinished(visited.size() >= MAX_DEPTH ? "max-depth" : "disconnected", visited.size(), toVisit.size(), addedRailTickets.size(), closestSoFar);
        return new TrackConnectionResult(false, closestSoFar, List.of(), localLevel.getGameTime());
        }
        finally
        {
            releaseRailSearchTickets(localLevel, addedRailTickets);
        }
    }

    /**
     * Releases rail-search chunk tickets added during a single BFS pass.
     *
     * @param level level containing the ticketed chunks
     * @param addedRailTickets chunks that received rail-search tickets during this search
     */
    private static void releaseRailSearchTickets(@Nonnull ServerLevel level, Set<ChunkPos> addedRailTickets)
    {
        if (addedRailTickets == null || addedRailTickets.isEmpty())
        {
            return;
        }
        ChunkUtil.releaseChunkTickets(level, addedRailTickets, RAIL_CHUNK_RADIUS, ChunkUtil.RAIL_TICKET);
    }

    /**
     * Carries identifying information and counters for one rail BFS diagnostic log stream.
     */
    private static class RailSearchDiagnostics
    {
        private final long searchId = RAIL_SEARCH_SEQUENCE.incrementAndGet();
        private final ServerLevel level;
        private final BlockPos start;
        private final BlockPos end;
        private final boolean loadChunks;
        private final long startNanos = System.nanoTime();
        private int chunkLoadAttempts = 0;

        /**
         * Creates diagnostics for one rail path search.
         *
         * @param level level being searched
         * @param start rail search start position
         * @param end rail search end position
         * @param loadChunks whether this search may synchronously load chunks
         */
        private RailSearchDiagnostics(ServerLevel level, BlockPos start, BlockPos end, boolean loadChunks)
        {
            this.level = level;
            this.start = start;
            this.end = end;
            this.loadChunks = loadChunks;
        }

        /**
         * Logs the start of the rail search.
         */
        private void logStart()
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Rail BFS #{} START dim={} start={} end={} loadChunks={}",
                searchId,
                level.dimension().location(),
                start,
                end,
                loadChunks));
        }

        /**
         * Logs periodic search progress so a long-running expansion can be located.
         *
         * @param visited number of visited rail positions
         * @param queued number of queued rail positions
         * @param current current position being expanded
         * @param closest current closest reached position to the destination
         * @param ticketCount number of chunk tickets added by the search
         */
        private void logProgressIfNeeded(int visited, int queued, BlockPos current, BlockPos closest, int ticketCount)
        {
            if (visited % BFS_PROGRESS_LOG_INTERVAL != 0)
            {
                return;
            }

            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Rail BFS #{} PROGRESS dim={} visited={} queued={} current={} closest={} chunkLoads={} tickets={} elapsedMs={}",
                searchId,
                level.dimension().location(),
                visited,
                queued,
                current,
                closest,
                chunkLoadAttempts,
                ticketCount,
                elapsedMillis()));
        }

        /**
         * Logs immediately before a synchronous chunk load is requested.
         *
         * @param pos block position whose chunk is about to be loaded
         */
        private void logBeforeChunkLoad(@Nonnull BlockPos pos)
        {
            chunkLoadAttempts++;
            ChunkPos chunk = new ChunkPos(pos);
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Rail BFS #{} CHUNK_LOAD_BEGIN dim={} chunk={} pos={} attempt={} elapsedMs={}",
                searchId,
                level.dimension().location(),
                chunk,
                pos,
                chunkLoadAttempts,
                elapsedMillis()));
        }

        /**
         * Logs after a synchronous chunk load request returns.
         *
         * @param pos block position whose chunk was requested
         * @param loaded whether the block position is loaded after the request
         * @param ticketCount number of chunk tickets added by the search
         */
        private void logAfterChunkLoad(@Nonnull BlockPos pos, boolean loaded, int ticketCount)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Rail BFS #{} CHUNK_LOAD_END dim={} chunk={} pos={} loaded={} tickets={} elapsedMs={}",
                searchId,
                level.dimension().location(),
                new ChunkPos(pos),
                pos,
                loaded,
                ticketCount,
                elapsedMillis()));
        }

        /**
         * Logs the end state of the rail search.
         *
         * @param outcome connected, disconnected, or max-depth
         * @param visited number of visited rail positions
         * @param queued number of queued rail positions
         * @param ticketCount number of chunk tickets added by the search
         * @param closest current closest reached position to the destination
         */
        private void logFinished(String outcome, int visited, int queued, int ticketCount, BlockPos closest)
        {
            long elapsedNanos = System.nanoTime() - startNanos;
            if (loadChunks || elapsedNanos >= SLOW_BFS_LOG_NANOS || "max-depth".equals(outcome))
            {
                TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Rail BFS #{} END outcome={} dim={} start={} end={} visited={} queued={} closest={} chunkLoads={} tickets={} elapsedMs={}",
                    searchId,
                    outcome,
                    level.dimension().location(),
                    start,
                    end,
                    visited,
                    queued,
                    closest,
                    chunkLoadAttempts,
                    ticketCount,
                    elapsedNanos / 1_000_000L));
            }
        }

        /**
         * Gets elapsed milliseconds for this search.
         *
         * @return elapsed wall-clock milliseconds
         */
        private long elapsedMillis()
        {
            return (System.nanoTime() - startNanos) / 1_000_000L;
        }
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
     * @param loadChunks whether unloaded chunks may be synchronously loaded
     * @param addedRailTickets chunks that received tickets during this search
     * @param diagnostics logging state for this search
     */
    private static void tryMove(ServerLevel level,
        BlockPos from,
        @Nonnull Direction dir,
        int dy,
        Queue<BlockPos> q,
        Set<BlockPos> visited,
        Map<BlockPos, BlockPos> parent,
        boolean loadChunks,
        Set<ChunkPos> addedRailTickets,
        RailSearchDiagnostics diagnostics)
    {
        BlockPos nxt = from.relative(dir).offset(0, dy, 0);

        if (nxt == null) return;

        if (!level.isLoaded(nxt))
        {
            if (loadChunks)
            {
                diagnostics.logBeforeChunkLoad(nxt);
                ChunkUtil.ensureChunkLoadedByTicket(level, nxt, RAIL_CHUNK_RADIUS, ChunkUtil.RAIL_TICKET, addedRailTickets);
                diagnostics.logAfterChunkLoad(nxt, level.isLoaded(nxt), addedRailTickets.size());
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
