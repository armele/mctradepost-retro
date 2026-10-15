package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.core.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Provides bounded path searches and cached-path validation for non-rail trade modes.
 * <p>Road searches follow tagged trade-road blocks and may change elevation, while water searches follow unobstructed surface water.
 */
public final class ModalPathConnection
{
    private static final int MAX_VISITED = 200_000;

    private ModalPathConnection()
    {}

    /**
     * Result of a bounded local path search.
     *
     * @param path               completed path, or an empty list when disconnected
     * @param closestPoint       reachable position nearest the requested destination
     * @param searchLimitReached whether the visited-node safety limit stopped the search
     */
    public record SearchResult(List<BlockPos> path, BlockPos closestPoint, boolean searchLimitReached)
    {}

    /**
     * Finds a trade-road path and retains the best reachable position when no complete path exists.
     *
     * @param level level containing the route
     * @param start first position in the requested route
     * @param end   final position in the requested route
     * @return completed path and bounded-search diagnostic state
     */
    public static SearchResult roadSearch(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end)
    {
        return search(level,
            start,
            end,
            10_000,
            true,
            (world, pos) -> pos != null && world.getBlockState(pos).is(ModTags.BLOCKS.TRADE_ROADS_TAG));
    }

    /**
     * Tests the shared physical rule used when charting and validating a surface-water route.
     *
     * @param level level containing the position
     * @param pos   position occupied by the vessel
     * @return whether the position contains water and has collision-free headroom
     */
    @SuppressWarnings("null")
    public static boolean isNavigableWaterPosition(ServerLevel level, @Nonnull BlockPos pos)
    {
        return level.getFluidState(pos).is(FluidTags.WATER) &&
            level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /**
     * Checks whether the loaded interior positions of a cached road or water segment remain traversable.
     * <p>Endpoints are excluded because they may be docks, interchanges, or other modal handoff blocks. Unloaded positions are
     * accepted optimistically so validation does not force chunk loading.
     *
     * @param level   level containing the segment
     * @param segment cached road or water segment to validate
     * @return {@code true} when the segment is non-empty, has a supported type, and every loaded interior position remains valid
     */
    @SuppressWarnings("null")
    public static boolean validate(ServerLevel level, TrackRoute.Segment segment)
    {
        if (segment.path().isEmpty()) return false;

        BiPredicate<ServerLevel, BlockPos> valid;
        switch (segment.type())
        {
            case ROAD -> valid = (world, pos) -> world.getBlockState(pos).is(ModTags.BLOCKS.TRADE_ROADS_TAG);

            case WATER -> valid = ModalPathConnection::isNavigableWaterPosition;

            default -> {
                return false;
            }
        }

        for (int i = 1; i < segment.path().size() - 1; i++)
        {
            BlockPos pathpos = segment.path().get(i);

            if (pathpos == null) return false;

            if (level.isLoaded(pathpos) && !valid.test(level, pathpos)) return false;
        }
        return true;
    }

    /**
     * Performs a bounded breadth-first search using the supplied traversal rule.
     */
    @SuppressWarnings("null")
    private static SearchResult search(ServerLevel level,
        @Nonnull BlockPos start,
        @Nonnull BlockPos end,
        int maxDistance,
        boolean slopes,
        BiPredicate<ServerLevel, BlockPos> traversable)
    {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start.immutable());
        distance.put(start.immutable(), 0);
        BlockPos closestPoint = start.immutable();
        int closestDistance = start.distManhattan(end);
        while (!queue.isEmpty() && visited.size() < MAX_VISITED)
        {
            BlockPos current = queue.remove();
            int currentDistance = distance.getOrDefault(current, 0);
            if (!visited.add(current) || currentDistance > maxDistance)
            {
                continue;
            }
            int destinationDistance = current.distManhattan(end);
            if (destinationDistance < closestDistance)
            {
                closestDistance = destinationDistance;
                closestPoint = current.immutable();
            }
            if (current.equals(end) || destinationDistance == 1)
            {
                return new SearchResult(rebuild(parent, current, start, end), end.immutable(), false);
            }
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                if (direction == null) continue;

                enqueue(level,
                    current.relative(direction),
                    current,
                    currentDistance,
                    maxDistance,
                    end,
                    traversable,
                    queue,
                    visited,
                    parent,
                    distance);
                if (slopes)
                {
                    enqueue(level,
                        current.relative(direction).above(),
                        current,
                        currentDistance,
                        maxDistance,
                        end,
                        traversable,
                        queue,
                        visited,
                        parent,
                        distance);
                    enqueue(level,
                        current.relative(direction).below(),
                        current,
                        currentDistance,
                        maxDistance,
                        end,
                        traversable,
                        queue,
                        visited,
                        parent,
                        distance);
                }
            }
        }
        return new SearchResult(List.of(), closestPoint, visited.size() >= MAX_VISITED);
    }

    /**
     * Adds a neighboring position to a breadth-first search when it is loaded, traversable, and not already discovered.
     */
    private static void enqueue(ServerLevel level,
        @Nonnull BlockPos next,
        @Nonnull BlockPos from,
        int currentDistance,
        int maxDistance,
        BlockPos end,
        BiPredicate<ServerLevel, BlockPos> traversable,
        Queue<BlockPos> queue,
        Set<BlockPos> visited,
        Map<BlockPos, BlockPos> parent,
        Map<BlockPos, Integer> distance)
    {
        int nextDistance = currentDistance + 1;
        if (nextDistance > maxDistance || visited.contains(next) || !level.isLoaded(next)) return;
        if (!next.equals(end) && !traversable.test(level, next)) return;
        BlockPos immutable = next.immutable();
        if (distance.putIfAbsent(immutable, nextDistance) == null)
        {
            parent.put(immutable, from);
            queue.add(immutable);
        }
    }

    /**
     * Reconstructs an immutable endpoint-inclusive path from a search's parent map.
     */
    private static List<BlockPos> rebuild(Map<BlockPos, BlockPos> parent, BlockPos current, BlockPos start, BlockPos end)
    {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cursor = current; cursor != null && !cursor.equals(start); cursor = parent.get(cursor)) path.add(cursor);
        path.add(start);
        Collections.reverse(path);
        if (!path.get(path.size() - 1).equals(end)) path.add(end);
        return Collections.unmodifiableList(path);
    }
}
