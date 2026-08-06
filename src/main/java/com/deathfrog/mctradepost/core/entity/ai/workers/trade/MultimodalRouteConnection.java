package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.core.blocks.BlockTradeDock;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Builds a least-distance same-dimension route across rail, road, water, and docks. */
public final class MultimodalRouteConnection
{
    private static final int MAX_DOCK_CANDIDATES = 32;
    private MultimodalRouteConnection() { }

    public static TrackConnectionResult findRoute(ServerLevel level, BlockPos start, BlockPos end, boolean loadChunks)
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

    private static TrackRoute.Segment edge(ServerLevel level, Node from, Node to, boolean loadChunks)
    {
        if (from.isDock() && to.isDock())
        {
            int limit = MCTPConfig.maximumWaterRouteDistance.get();
            BlockPos waterFrom = from.water();
            BlockPos waterTo = to.water();
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

    private enum NodeType { ENDPOINT, DOCK, INTERCHANGE }

    private static Node endpointNode(ServerLevel level, BlockPos position)
    {
        BlockState state = level.getBlockState(position);
        if (state.is(MCTradePostMod.TRADE_DOCK.get())) return new Node(position, state, NodeType.DOCK);
        if (state.is(MCTradePostMod.TRADE_INTERCHANGE.get())) return new Node(position, state, NodeType.INTERCHANGE);
        return new Node(position, state, NodeType.ENDPOINT);
    }

    private record Node(BlockPos position, BlockState state, NodeType type)
    {
        boolean isDock() { return type == NodeType.DOCK; }
        boolean isInterchange() { return type == NodeType.INTERCHANGE; }
        BlockPos rail()
        {
            if (isDock()) return BlockTradeDock.landEndpoint(state, position);
            if (isInterchange()) return position;
            return position;
        }
        BlockPos road()
        {
            if (isDock()) return BlockTradeDock.landEndpoint(state, position);
            if (isInterchange()) return position;
            return position;
        }
        BlockPos water() { return isDock() ? BlockTradeDock.waterEndpoint(state, position) : position; }
    }
    private record QueueEntry(int index, int distance) { }
    private record Previous(int node, TrackRoute.Segment segment) { }
}
