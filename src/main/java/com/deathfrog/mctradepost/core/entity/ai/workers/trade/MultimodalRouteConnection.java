package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.blocks.BlockTradeDock;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_TRACKPATH;

/**
 * Builds the first viable route between two positions in the same dimension using rail, road, and water travel.
 * <p>Registered docks and interchanges form graph nodes at which a route may change transport mode. Edges are weighted by their path
 * distance, and the resulting route includes explicit zero-distance handoff segments wherever an intermediate node changes mode. A
 * direct connection is preferred; otherwise progressively larger candidate graphs are searched until one produces a route. Dijkstra's
 * algorithm selects the least-distance route within each candidate graph, but later stages are not searched after a viable route is found.
 */
public final class MultimodalRouteConnection
{
    private static final int[] CANDIDATE_STAGES = {8, 16, 32};
    private static final int MAX_CANDIDATES_PER_TYPE = CANDIDATE_STAGES[CANDIDATE_STAGES.length - 1];

    private MultimodalRouteConnection()
    {}

    /**
     * Finds the first viable multimodal route between two positions in a level.
     * <p>A direct connection is returned when available. Otherwise the search considers progressively larger sets of nearby registered
     * docks and interchanges, returning the least-distance route from the first candidate stage that connects the endpoints. Rail
     * discovery observes {@code loadChunks}; road discovery only traverses loaded chunks, while water discovery may load chunks.
     *
     * @param level      level containing both route endpoints
     * @param start      starting position of the route
     * @param end        destination position of the route
     * @param loadChunks whether rail pathfinding may load chunks while searching
     * @return a connected result containing the segmented route, or a disconnected result when no route is available
     */
    public static TrackConnectionResult findRoute(ServerLevel level,
        @Nonnull BlockPos start,
        @Nonnull BlockPos end,
        boolean loadChunks)
    {
        return findRoute(level, start, end, loadChunks, true);
    }

    /**
     * Finds the first viable multimodal route, optionally excluding water travel.
     *
     * @param allowWater whether dock-to-dock water edges may be considered
     */
    public static TrackConnectionResult findRoute(ServerLevel level,
        @Nonnull BlockPos start,
        @Nonnull BlockPos end,
        boolean loadChunks,
        boolean allowWater)
    {
        final long searchStarted = System.nanoTime();
        final Node source = endpointNode(level, start);
        final Node destination = endpointNode(level, end);
        final EdgeCache edgeCache = new EdgeCache(level, loadChunks, allowWater);

        TrackRoute.Segment direct = edgeCache.get(source, destination);
        if (direct != null)
        {
            edgeCache.logSummary(0, 0, 0, true, searchStarted);
            return connectedResult(level, end, List.of(source, destination), List.of(direct), List.of(0, 1));
        }

        final List<Node> docks = collectCandidates(level, TradeDockRegistry.get(level).docks(), start, end, NodeType.DOCK);
        final List<Node> interchanges =
            collectCandidates(level, TradeInterchangeRegistry.get(level).interchanges(), start, end, NodeType.INTERCHANGE);

        int previousDockCount = -1;
        int previousInterchangeCount = -1;
        for (int candidateLimit : CANDIDATE_STAGES)
        {
            int dockCount = Math.min(candidateLimit, docks.size());
            int interchangeCount = Math.min(candidateLimit, interchanges.size());
            if (dockCount == previousDockCount && interchangeCount == previousInterchangeCount) continue;

            previousDockCount = dockCount;
            previousInterchangeCount = interchangeCount;
            List<Node> nodes = new ArrayList<>(dockCount + interchangeCount + 2);
            nodes.add(source);
            nodes.addAll(docks.subList(0, dockCount));
            nodes.addAll(interchanges.subList(0, interchangeCount));
            nodes.add(destination);

            TrackConnectionResult result = searchGraph(level, start, end, nodes, edgeCache);
            if (result != null)
            {
                edgeCache.logSummary(candidateLimit, dockCount, interchangeCount, true, searchStarted);
                return result;
            }
            edgeCache.logSummary(candidateLimit, dockCount, interchangeCount, false, searchStarted);
        }

        return new TrackConnectionResult(false, start, List.of(), level.getGameTime());
    }

    /**
     * Searches the supplied candidate graph using Dijkstra's algorithm.
     *
     * @return a connected result, or null when the destination is unreachable
     */
    private static TrackConnectionResult searchGraph(ServerLevel level,
        BlockPos start,
        BlockPos end,
        List<Node> nodes,
        EdgeCache edgeCache)
    {
        int destination = nodes.size() - 1;

        Map<Integer, Integer> best = new HashMap<>();
        Map<Integer, Previous> previous = new HashMap<>();
        @SuppressWarnings("null")
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

                TrackRoute.Segment edge = edgeCache.get(nodes.get(current.index()), nodes.get(next));

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

        if (!best.containsKey(destination)) return null;
        List<TrackRoute.Segment> segments = new ArrayList<>();
        List<Integer> routeNodes = new ArrayList<>();
        routeNodes.add(destination);

        for (int cursor = destination; cursor != 0;)
        {
            Previous step = previous.get(cursor);
            if (step == null) return null;
            segments.add(0, step.segment());
            cursor = step.node();
            routeNodes.add(0, cursor);
        }

        return connectedResult(level, end, nodes, segments, routeNodes);
    }

    /** Builds a connected result and inserts explicit handoff segments between traversable legs. */
    @SuppressWarnings("null")
    private static TrackConnectionResult connectedResult(ServerLevel level,
        BlockPos end,
        List<Node> nodes,
        List<TrackRoute.Segment> segments,
        List<Integer> routeNodes)
    {
        List<TrackRoute.Segment> withHandoffs = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++)
        {
            if (i > 0 && nodes.get(routeNodes.get(i)).isDock())
            {
                withHandoffs.add(TrackRoute.Segment.dock(level.dimension(), nodes.get(routeNodes.get(i)).position()));
            }
            else if (i > 0 && nodes.get(routeNodes.get(i)).isInterchange())
            {
                withHandoffs.add(TrackRoute.Segment.interchange(level.dimension(), nodes.get(routeNodes.get(i)).position()));
            }
            withHandoffs.add(segments.get(i));
        }

        TrackRoute route = new TrackRoute(withHandoffs);
        return new TrackConnectionResult(true, end, route.firstPath(), level.getGameTime(), route);
    }

    /**
     * Selects candidates near both endpoints and along the likely route corridor. Prefixes are intentionally useful so the same list
     * can be expanded through the 8, 16, and 32 candidate stages.
     */
    private static List<Node> collectCandidates(ServerLevel level,
        Set<BlockPos> registered,
        @Nonnull BlockPos start,
        @Nonnull BlockPos end,
        NodeType type)
    {
        int endpointLimit = MAX_CANDIDATES_PER_TYPE / 4;
        List<Candidate> byStart = new ArrayList<>(endpointLimit);
        List<Candidate> byEnd = new ArrayList<>(endpointLimit);
        List<Candidate> byDetour = new ArrayList<>(MAX_CANDIDATES_PER_TYPE);

        for (BlockPos pos : registered)
        {
            if (pos.equals(start) || pos.equals(end)) continue;

            BlockState state = level.getBlockState(pos);

            @SuppressWarnings("null")
            boolean valid = type == NodeType.DOCK ? state.is(MCTradePostMod.TRADE_DOCK.get()) :
                state.is(MCTradePostMod.TRADE_INTERCHANGE.get());

            if (!valid) continue;

            Candidate candidate = new Candidate(new Node(pos, state, type), pos.distSqr(start), pos.distSqr(end));
            insertRanked(byStart, candidate, endpointLimit, CandidateRanking.START);
            insertRanked(byEnd, candidate, endpointLimit, CandidateRanking.END);
            insertRanked(byDetour, candidate, MAX_CANDIDATES_PER_TYPE, CandidateRanking.DETOUR);
        }

        LinkedHashSet<Candidate> selected = new LinkedHashSet<>();

        for (int stageLimit : CANDIDATE_STAGES)
        {
            int endpointShare = Math.max(1, stageLimit / 4);
            addRankedCandidates(selected, byStart, endpointShare, stageLimit);
            addRankedCandidates(selected, byEnd, endpointShare, stageLimit);
            fillCandidates(selected, byDetour, stageLimit);
        }

        List<Node> nodes = new ArrayList<>(selected.size());
        for (Candidate candidate : selected) nodes.add(candidate.node());
        return nodes;
    }

    /** Inserts a candidate into a bounded ascending ranking, retaining encounter order for equal scores. */
    private static void insertRanked(List<Candidate> ranked, Candidate candidate, int limit, CandidateRanking ranking)
    {
        double candidateScore = ranking.score(candidate);
        int index = 0;
        while (index < ranked.size() && ranking.score(ranked.get(index)) <= candidateScore) index++;
        if (index >= limit) return;

        ranked.add(index, candidate);
        if (ranked.size() > limit) ranked.remove(ranked.size() - 1);
    }

    /** Adds positions within the requested prefix of a ranking without exceeding the stage limit. */
    private static void addRankedCandidates(LinkedHashSet<Candidate> selected,
        List<Candidate> candidates,
        int rankedLimit,
        int stageLimit)
    {
        int limit = Math.min(rankedLimit, candidates.size());
        for (int i = 0; i < limit && selected.size() < stageLimit; i++)
        {
            selected.add(candidates.get(i));
        }
    }

    /** Fills the remainder of a stage from its corridor ranking. */
    private static void fillCandidates(LinkedHashSet<Candidate> selected, List<Candidate> candidates, int stageLimit)
    {
        for (Candidate candidate : candidates)
        {
            if (selected.size() >= stageLimit) return;
            selected.add(candidate);
        }
    }

    /** A validated graph node and its cached candidate-ranking distances. */
    private record Candidate(Node node, double startDistance, double endDistance)
    {
        double detour()
        {
            return startDistance + endDistance;
        }
    }

    /** Selects which cached distance is used to rank a candidate. */
    private enum CandidateRanking
    {
        START, END, DETOUR;

        double score(Candidate candidate)
        {
            return switch (this)
            {
                case START -> candidate.startDistance();
                case END -> candidate.endDistance();
                case DETOUR -> candidate.detour();
            };
        }
    }

    /**
     * Finds the shortest direct modal connection between two graph nodes.
     * <p>All node pairs may connect by rail or road. Two docks may also connect by water when water can improve upon the best land
     * route; the water search is bounded by that land route's distance. Equal distances prefer rail over road and land over water.
     *
     * @param level      level containing the nodes
     * @param from       origin graph node
     * @param to         destination graph node
     * @param loadChunks whether rail pathfinding may load chunks
     * @return the shortest connecting segment, or {@code null} when the nodes cannot be connected
     */
    @SuppressWarnings("null")
    private static TrackRoute.Segment edge(ServerLevel level, Node from, Node to, boolean loadChunks, boolean allowWater)
    {
        TrackConnectionResult rail = TrackPathConnection.arePointsConnectedByTracks(level, from.rail(), to.rail(), loadChunks);
        TrackRoute.Segment best = rail.isConnected() ? TrackRoute.Segment.rail(level.dimension(), rail.path) : null;
        List<BlockPos> road = ModalPathConnection.road(level, from.road(), to.road());
        if (!road.isEmpty() && (best == null || road.size() - 1 < best.distance()))
            best = TrackRoute.Segment.road(level.dimension(), road);

        if (!allowWater || !from.isDock() || !to.isDock()) return best;

        int limit = MCTPConfig.maximumWaterRouteDistance.get();
        BlockPos waterFrom = from.water(level);
        BlockPos waterTo = to.water(level);
        if (waterFrom == null || waterTo == null) return best;

        int minimumWaterDistance = Math.abs(waterFrom.getX() - waterTo.getX()) + Math.abs(waterFrom.getZ() - waterTo.getZ());
        if (minimumWaterDistance > limit) return best;

        if (best != null)
        {
            if (minimumWaterDistance >= best.distance()) return best;
            limit = Math.min(limit, best.distance() - 1);
        }

        List<BlockPos> water = ModalPathConnection.water(level, waterFrom, waterTo, limit);
        if (!water.isEmpty()) best = TrackRoute.Segment.water(level.dimension(), water);
        return best;
    }

    /** Identifies the transport role of a route-graph node. */
    private enum NodeType
    {
        ENDPOINT, DOCK, INTERCHANGE
    }

    /**
     * Classifies a route endpoint according to the block currently occupying its position.
     *
     * @param level    level containing the endpoint
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
     * @param state    block state captured when the node is created
     * @param type     transport role of the node
     */
    private record Node(BlockPos position, BlockState state, NodeType type)
    {
        /** @return whether this node represents a trade dock */
        boolean isDock()
        {
            return type == NodeType.DOCK;
        }

        /** @return whether this node represents a trade interchange */
        boolean isInterchange()
        {
            return type == NodeType.INTERCHANGE;
        }

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
        BlockPos water(ServerLevel level)
        {
            return isDock() ? BlockTradeDock.waterEndpoint(level, state, position) : position;
        }
    }

    /** Stable cache identity independent of candidate-list ordering between search stages. */
    private record NodeKey(BlockPos position, NodeType type)
    {}

    /** Directed key because the cached value's path is ordered from {@code from} to {@code to}. */
    private record EdgeKey(NodeKey from, NodeKey to)
    {}

    /**
     * Search-local cache for both successful and failed graph edges. Every computed edge is also stored in reverse, preventing the
     * graph search and later expansion stages from repeating the same BFS or A* work.
     */
    private static final class EdgeCache
    {
        private final ServerLevel level;
        private final boolean loadChunks;
        private final boolean allowWater;
        private final Map<EdgeKey, Optional<TrackRoute.Segment>> edges = new HashMap<>();
        private int searches;
        private int hits;
        private int failedHits;

        private EdgeCache(ServerLevel level, boolean loadChunks, boolean allowWater)
        {
            this.level = level;
            this.loadChunks = loadChunks;
            this.allowWater = allowWater;
        }

        private TrackRoute.Segment get(Node from, Node to)
        {
            EdgeKey key = key(from, to);
            Optional<TrackRoute.Segment> cached = edges.get(key);
            if (cached != null)
            {
                hits++;
                if (cached.isEmpty()) failedHits++;
                return cached.orElse(null);
            }

            searches++;
            TrackRoute.Segment result = edge(level, from, to, loadChunks, allowWater);
            edges.put(key, Optional.ofNullable(result));
            edges.put(key(to, from), Optional.ofNullable(result == null ? null : reverse(result)));
            return result;
        }

        private void logSummary(int stageLimit, int docks, int interchanges, boolean connected, long started)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH,
                () -> MCTradePostMod.LOGGER.warn(
                    "Multimodal stage limit={} docks={} interchanges={} connected={} edgeSearches={} cacheHits={} failedCacheHits={} elapsedMs={}",
                    stageLimit,
                    docks,
                    interchanges,
                    connected,
                    searches,
                    hits,
                    failedHits,
                    (System.nanoTime() - started) / 1_000_000L));
        }

        private static EdgeKey key(Node from, Node to)
        {
            return new EdgeKey(new NodeKey(from.position(), from.type()), new NodeKey(to.position(), to.type()));
        }

        private static TrackRoute.Segment reverse(TrackRoute.Segment segment)
        {
            if (segment.type() == TrackRoute.SegmentType.TRANSFER || segment.type() == TrackRoute.SegmentType.DOCK ||
                segment.type() == TrackRoute.SegmentType.INTERCHANGE)
            {
                throw new IllegalArgumentException("Only traversable graph edges can be reversed");
            }

            List<BlockPos> reversedPath = new ArrayList<>(segment.path());
            Collections.reverse(reversedPath);
            return TrackRoute.Segment.traversable(segment.type(), segment.dimension(), reversedPath);
        }
    }

    /** Priority-queue entry associating a graph node index with its best known route distance. */
    private record QueueEntry(int index, int distance)
    {}

    /** Predecessor information used to reconstruct the selected route. */
    private record Previous(int node, TrackRoute.Segment segment)
    {}
}
