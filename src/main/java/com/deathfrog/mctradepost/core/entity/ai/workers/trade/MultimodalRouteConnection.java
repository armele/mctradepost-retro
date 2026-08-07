package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.core.blocks.BlockTradeDock;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds least-distance routes between two positions in the same dimension using rail, road, and water travel.
 * <p>
 * Registered docks and interchanges form graph nodes at which a route may change transport mode. Edges are weighted by their path
 * distance, and the resulting route includes explicit zero-distance handoff segments wherever an intermediate node changes mode.
 */
public final class MultimodalRouteConnection
{
    private static final int MAX_DOCK_CANDIDATES = 32;
    private MultimodalRouteConnection() { }

    /**
     * Finds the shortest available multimodal route between two positions in a level.
     * <p>
     * The search considers nearby registered docks and interchanges, then uses shortest-path routing across every reachable pair.
     * Rail discovery observes {@code loadChunks}; road discovery only traverses loaded chunks, while water discovery may load chunks.
     *
     * @param level level containing both route endpoints
     * @param start starting position of the route
     * @param end destination position of the route
     * @param loadChunks whether rail pathfinding may load chunks while searching
     * @return a connected result containing the segmented route, or a disconnected result when no route is available
     */
    @SuppressWarnings("null")
    public static TrackConnectionResult findRoute(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end, boolean loadChunks)
    {
        List<Node> nodes = new ArrayList<>();
        nodes.add(endpointNode(level, start));
        TradeDockRegistry.get(level).docks().stream()
            .filter(pos -> !pos.equals(start) && !pos.equals(end) && level.getBlockState(pos).is(MCTradePostMod.TRADE_DOCK.get()))
            .sorted(Comparator.comparingDouble(pos -> Math.min(pos.distSqr(start), pos.distSqr(end))))
            .limit(MAX_DOCK_CANDIDATES)
            .forEach(pos -> nodes.add(new Node(pos, level.getBlockState(pos), NodeType.DOCK)));
        TradeInterchangeRegistry.get(level).interchanges().stream()
            .filter(pos -> !pos.equals(start) && !pos.equals(end) && level.getBlockState(pos).is(MCTradePostMod.TRADE_INTERCHANGE.get()))
            .sorted(Comparator.comparingDouble(pos -> Math.min(pos.distSqr(start), pos.distSqr(end))))
            .limit(MAX_DOCK_CANDIDATES)
            .forEach(pos -> nodes.add(new Node(pos, level.getBlockState(pos), NodeType.INTERCHANGE)));
        nodes.add(endpointNode(level, end));

        int destination = nodes.size() - 1;
        Map<Integer, Integer> best = new HashMap<>();
        Map<Integer, Previous> previous = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(Comparator.comparingInt(QueueEntry::distance));
        best.put(0, 0);
        open.add(new QueueEntry(0, 0));
        while (!open.isEmpty())
        {
            QueueEntry current = open.remove();
            if (current.distance() != best.getOrDefault(current.index(), Integer.MAX_VALUE)) continue;
            if (current.index() == destination) break;
            for (int next = 0; next < nodes.size(); next++)
            {
                if (next == current.index()) continue;
                TrackRoute.Segment edge = edge(level, nodes.get(current.index()), nodes.get(next), loadChunks);
                if (edge == null) continue;
                int candidate = current.distance() + edge.distance();
                if (candidate < best.getOrDefault(next, Integer.MAX_VALUE))
                {
                    best.put(next, candidate);
                    previous.put(next, new Previous(current.index(), edge));
                    open.add(new QueueEntry(next, candidate));
                }
            }
        }
        if (!best.containsKey(destination)) return new TrackConnectionResult(false, start, List.of(), level.getGameTime());
        List<TrackRoute.Segment> segments = new ArrayList<>();
        List<Integer> routeNodes = new ArrayList<>();
        routeNodes.add(destination);
        for (int cursor = destination; cursor != 0; )
        {
            Previous step = previous.get(cursor);
            if (step == null) return new TrackConnectionResult(false, start, List.of(), level.getGameTime());
            segments.add(0, step.segment());
            cursor = step.node();
            routeNodes.add(0, cursor);
        }
        List<TrackRoute.Segment> withDocks = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++)
        {
            if (i > 0 && nodes.get(routeNodes.get(i)).isDock())
            {
                withDocks.add(TrackRoute.Segment.dock(level.dimension(), nodes.get(routeNodes.get(i)).position()));
            }
            else if (i > 0 && nodes.get(routeNodes.get(i)).isInterchange())
            {
                withDocks.add(TrackRoute.Segment.interchange(level.dimension(), nodes.get(routeNodes.get(i)).position()));
            }
            withDocks.add(segments.get(i));
        }
        TrackRoute route = new TrackRoute(withDocks);
        return new TrackConnectionResult(true, end, route.firstPath(), level.getGameTime(), route);
    }

    /**
     * Finds the shortest direct modal connection between two graph nodes.
     * <p>
     * Two docks may connect by water. All node pairs may connect by rail or road, with the shorter available land path selected.
     *
     * @param level level containing the nodes
     * @param from origin graph node
     * @param to destination graph node
     * @param loadChunks whether rail pathfinding may load chunks
     * @return the shortest connecting segment, or {@code null} when the nodes cannot be connected
     */
    @SuppressWarnings("null")
    private static TrackRoute.Segment edge(ServerLevel level, Node from, Node to, boolean loadChunks)
    {
        if (from.isDock() && to.isDock())
        {
            int limit = MCTPConfig.maximumWaterRouteDistance.get();
            BlockPos waterFrom = from.water(level);
            BlockPos waterTo = to.water(level);

            if (waterTo == null || waterFrom == null) return null;
            if (waterFrom.distSqr(waterTo) > (double) limit * limit) return null;
            
            List<BlockPos> water = ModalPathConnection.water(level, waterFrom, waterTo, limit);
            return water.isEmpty() ? null : TrackRoute.Segment.water(level.dimension(), water);
        }

        TrackConnectionResult rail = TrackPathConnection.arePointsConnectedByTracks(level, from.rail(), to.rail(), loadChunks);
        TrackRoute.Segment best = rail.isConnected() ? TrackRoute.Segment.rail(level.dimension(), rail.path) : null;
        List<BlockPos> road = ModalPathConnection.road(level, from.road(), to.road());
        if (!road.isEmpty() && (best == null || road.size() - 1 < best.distance())) best = TrackRoute.Segment.road(level.dimension(), road);
        return best;
    }

    /** Identifies the transport role of a route-graph node. */
    private enum NodeType { ENDPOINT, DOCK, INTERCHANGE }

    /**
     * Classifies a route endpoint according to the block currently occupying its position.
     *
     * @param level level containing the endpoint
     * @param position endpoint position
     * @return a graph node representing an ordinary endpoint, dock, or interchange
     */
    @SuppressWarnings("null")
    private static Node endpointNode(ServerLevel level, @Nonnull BlockPos position)
    {
        BlockState state = level.getBlockState(position);
        if (state.is(MCTradePostMod.TRADE_DOCK.get())) return new Node(position, state, NodeType.DOCK);
        if (state.is(MCTradePostMod.TRADE_INTERCHANGE.get())) return new Node(position, state, NodeType.INTERCHANGE);
        return new Node(position, state, NodeType.ENDPOINT);
    }

    /**
     * A physical route-graph location and its mode-specific connection positions.
     *
     * @param position position of the endpoint, dock, or interchange block
     * @param state block state captured when the node is created
     * @param type transport role of the node
     */
    private record Node(BlockPos position, BlockState state, NodeType type)
    {
        /** @return whether this node represents a trade dock */
        boolean isDock() { return type == NodeType.DOCK; }

        /** @return whether this node represents a trade interchange */
        boolean isInterchange() { return type == NodeType.INTERCHANGE; }

        /** @return position from which rail pathfinding should enter or leave this node */
        BlockPos rail()
        {
            if (isDock()) return BlockTradeDock.landEndpoint(state, position);
            if (isInterchange()) return position;
            return position;
        }
        /** @return position from which road pathfinding should enter or leave this node */
        BlockPos road()
        {
            if (isDock()) return BlockTradeDock.landEndpoint(state, position);
            if (isInterchange()) return position;
            return position;
        }
        /**
         * Resolves the position from which water pathfinding should enter or leave this node.
         *
         * @param level level containing the node
         * @return the dock's water endpoint, or this node's position when it is not a dock
         */
        BlockPos water(ServerLevel level) { return isDock() ? BlockTradeDock.waterEndpoint(level, state, position) : position; }
    }

    /** Priority-queue entry associating a graph node index with its best known route distance. */
    private record QueueEntry(int index, int distance) { }

    /** Predecessor information used to reconstruct the selected route. */
    private record Previous(int node, TrackRoute.Segment segment) { }
}
