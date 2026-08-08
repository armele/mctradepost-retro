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
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.function.BiPredicate;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;

/**
 * Provides bounded path searches and cached-path validation for non-rail trade modes.
 * <p>
 * Road searches follow tagged trade-road blocks and may change elevation, while water searches follow unobstructed surface water.
 */
public final class ModalPathConnection
{
    private static final int MAX_VISITED = 200_000;
    private ModalPathConnection() { }

    /**
     * Finds a trade-road path between two positions without loading chunks.
     *
     * @param level level containing the route
     * @param start first position in the requested route
     * @param end final position in the requested route
     * @return an immutable ordered path including both endpoints, or an empty list when no path is found within the search bounds
     */
    public static List<BlockPos> road(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end)
    {
        return search(level, start, end, 10_000, true,
            (world, pos) -> pos != null && world.getBlockState(pos).is(ModTags.BLOCKS.TRADE_ROADS_TAG));
    }

    /**
     * Finds a surface-water path between two positions, loading chunks encountered by the bounded search when necessary.
     *
     * @param level level containing the route
     * @param start first position in the requested route
     * @param end final position in the requested route
     * @param maximumDistance maximum number of horizontal steps to explore from the start
     * @return an immutable ordered path including both endpoints, or an empty list when no path is found within the distance limit
     */
    public static List<BlockPos> water(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end, int maximumDistance)
    {
        return waterAStar(level, start, end, maximumDistance);
    }

    /**
     * Performs a bounded A* search over unobstructed surface-water blocks.
     */
    @SuppressWarnings("null")
    private static List<BlockPos> waterAStar(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end, int maximumDistance)
    {
        PriorityQueue<WaterNode> open = new PriorityQueue<>(Comparator.comparingInt(WaterNode::score).thenComparingInt(WaterNode::heuristic));
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> best = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        best.put(start.immutable(), 0);
        open.add(new WaterNode(start.immutable(), 0, start.distManhattan(end), start.distManhattan(end)));
        while (!open.isEmpty() && visited.size() < MAX_VISITED)
        {
            WaterNode node = open.remove();
            BlockPos current = node.position();
            if (!visited.add(current) || node.distance() > maximumDistance) continue;
            if (current.equals(end) || current.distManhattan(end) == 1) return rebuild(parent, current, start, end);
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                if (direction == null) continue;

                BlockPos next = current.relative(direction);

                if (next == null) continue;

                int nextDistance = node.distance() + 1;
                if (nextDistance > maximumDistance || visited.contains(next)) continue;
                if (!level.isLoaded(next)) level.getChunkAt(next);
                boolean navigable = next.equals(end) ||
                    (level.getFluidState(next).is(FluidTags.WATER) && level.getBlockState(next.above()).getCollisionShape(level, next.above()).isEmpty());
                if (!navigable || nextDistance >= best.getOrDefault(next, Integer.MAX_VALUE)) continue;
                BlockPos immutable = next.immutable();
                best.put(immutable, nextDistance);
                parent.put(immutable, current);
                int heuristic = immutable.distManhattan(end);
                open.add(new WaterNode(immutable, nextDistance, nextDistance + heuristic, heuristic));
            }
        }
        return List.of();
    }

    /** Search state for the water-path priority queue. */
    private record WaterNode(BlockPos position, int distance, int score, int heuristic) { }

    /**
     * Checks whether the loaded interior positions of a cached road or water segment remain traversable.
     * <p>
     * Endpoints are excluded because they may be docks, interchanges, or other modal handoff blocks. Unloaded positions are accepted
     * optimistically so validation does not force chunk loading.
     *
     * @param level level containing the segment
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
            case ROAD -> valid = (world, pos) ->
                world.getBlockState(pos).is(ModTags.BLOCKS.TRADE_ROADS_TAG);

            case WATER -> valid = (world, pos) ->
                world.getFluidState(pos).is(FluidTags.WATER)
                    && world.getBlockState(pos.above())
                        .getCollisionShape(world, pos.above()).isEmpty();

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
    private static List<BlockPos> search(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end, int maxDistance, boolean slopes,
        BiPredicate<ServerLevel, BlockPos> traversable)
    {
        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start.immutable());
        distance.put(start.immutable(), 0);
        while (!queue.isEmpty() && visited.size() < MAX_VISITED)
        {
            BlockPos current = queue.remove();
            int currentDistance = distance.getOrDefault(current, 0);
            if (!visited.add(current) || currentDistance > maxDistance) continue;
            if (current.equals(end) || current.distManhattan(end) == 1) return rebuild(parent, current, start, end);
            for (Direction direction : Direction.Plane.HORIZONTAL)
            {
                if (direction == null) continue;

                enqueue(level, current.relative(direction), current, currentDistance, maxDistance, end, traversable, queue, visited, parent, distance);
                if (slopes)
                {
                    enqueue(level, current.relative(direction).above(), current, currentDistance, maxDistance, end, traversable, queue, visited, parent, distance);
                    enqueue(level, current.relative(direction).below(), current, currentDistance, maxDistance, end, traversable, queue, visited, parent, distance);
                }
            }
        }
        return List.of();
    }

    /**
     * Adds a neighboring position to a breadth-first search when it is loaded, traversable, and not already discovered.
     */
    private static void enqueue(ServerLevel level, @Nonnull BlockPos next, @Nonnull BlockPos from, int currentDistance, int maxDistance, BlockPos end,
        BiPredicate<ServerLevel, BlockPos> traversable, Queue<BlockPos> queue, Set<BlockPos> visited, Map<BlockPos, BlockPos> parent,
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
