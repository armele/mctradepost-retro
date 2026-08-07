package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_TRACKPATH;

/**
 * Finds and validates multimodal station routes that may span multiple dimensions through installed dimensional linkages.
 * <p>
 * Same-dimension legs are delegated to {@link MultimodalRouteConnection}. This class selects usable linkage endpoints, composes local
 * legs with dimensional transfer segments, bounds repeated linkage-pair attempts, and validates cached segmented routes.
 */
public class TrackRouteConnection
{
    private static final int MAX_LINKAGE_PAIR_ATTEMPTS = 64;
    private static final long ROUTE_SEARCH_LOG_NANOS = 50_000_000L;
    private static final AtomicLong ROUTE_SEARCH_SEQUENCE = new AtomicLong();

    /**
     * Validates an existing cached route without doing a full BFS search.
     * <p>
     * Loaded traversal and handoff positions must still support their segment type. Unloaded traversal and transfer positions are
     * treated optimistically so validation does not force chunk loading.
     *
     * @param server active Minecraft server
     * @param result cached connection result to validate
     * @return true when the cached route can still be considered connected
     */
    @SuppressWarnings("null")
    public static boolean validateExistingRoute(MinecraftServer server, TrackPathConnection.TrackConnectionResult result)
    {
        if (server == null || result == null)
        {
            return false;
        }

        result.lastChecked = server.overworld().getGameTime();
        TrackRoute route = result.route;
        if (route == null)
        {
            return result.path != null && !result.path.isEmpty();
        }

        for (TrackRoute.Segment segment : route.segments())
        {
            ServerLevel level = server.getLevel(segment.dimension());
            if (level == null)
            {
                return false;
            }

            if (segment.type() == TrackRoute.SegmentType.TRANSFER)
            {
                if (!validateTransferEndpoint(server, segment.transferFrom()) || !validateTransferEndpoint(server, segment.transferTo()))
                {
                    return false;
                }
                continue;
            }

            if (segment.type() == TrackRoute.SegmentType.DOCK)
            {
                if (segment.path().isEmpty() || !level.getBlockState(segment.path().getFirst()).is(MCTradePostMod.TRADE_DOCK.get())) return false;
                continue;
            }

            if (segment.type() == TrackRoute.SegmentType.INTERCHANGE)
            {
                if (segment.path().isEmpty() || !level.getBlockState(segment.path().getFirst()).is(MCTradePostMod.TRADE_INTERCHANGE.get())) return false;
                continue;
            }

            if (segment.type() != TrackRoute.SegmentType.RAIL)
            {
                if (!ModalPathConnection.validate(level, segment))
                {
                    return false;
                }
                continue;
            }

            if (!validateRailSegment(level, segment.path()))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds a route from a source trade-capable building to a destination station.
     * <p>
     * A direct same-dimension multimodal path is attempted first. If no direct path exists, installed valid dimensional linkages on
     * both endpoint buildings are used to build one-transfer or Overworld-Nether-Overworld segmented routes.
     *
     * @param source source station or outpost
     * @param destination destination station data
     * @param loadChunks whether BFS searches may load chunks while exploring rails
     * @return connection result containing the route when one is found
     */
    @SuppressWarnings("null")
    public static TrackPathConnection.TrackConnectionResult findRoute(ITradeCapable source,
        StationData destination,
        boolean loadChunks)
    {
        if (source == null || destination == null || source.getColony() == null || source.getColony().getWorld() == null)
        {
            return new TrackPathConnection.TrackConnectionResult(false, null, List.of(), 0L);
        }

        MinecraftServer server = source.getColony().getWorld().getServer();
        if (server == null)
        {
            return new TrackPathConnection.TrackConnectionResult(false, null, List.of(), source.getColony().getWorld().getGameTime());
        }

        ServerLevel sourceLevel = server.getLevel(source.getColony().getDimension());
        ServerLevel destinationLevel = server.getLevel(destination.getDimension());
        if (sourceLevel == null || destinationLevel == null)
        {
            return new TrackPathConnection.TrackConnectionResult(false, source.getRailStartPosition(), List.of(), source.getColony().getWorld().getGameTime());
        }

        BlockPos sourceRail = source.getRailStartPosition();
        BlockPos destinationRail = destination.getRailStartPosition();
        RouteSearchContext context = new RouteSearchContext(loadChunks);
        context.logRouteStart(source, destination, sourceLevel, destinationLevel);

        TrackPathConnection.TrackConnectionResult direct =
            new TrackPathConnection.TrackConnectionResult(false, sourceRail, List.of(), source.getColony().getWorld().getGameTime());
        if (sourceLevel.dimension().equals(destinationLevel.dimension()))
        {
            direct = MultimodalRouteConnection.findRoute(sourceLevel, sourceRail, destinationRail, loadChunks);
        }

        if (direct.isConnected())
        {
            return finishRouteSearch(context, direct, source, destination);
        }

        ITradeCapable destinationBuilding = destination.getStation();
        List<DimensionalLinkageRecord> sourceLinkages = installedValidLinkages(source);
        List<DimensionalLinkageRecord> destinationLinkages = installedValidLinkages(destinationBuilding);
        List<DimensionalLinkageRecord> combinedLinkages = combineLinkages(sourceLinkages, destinationLinkages);
        context.logLinkages(sourceLinkages, destinationLinkages, combinedLinkages);
        TrackPathConnection.TrackConnectionResult routed = findLinkedRoute(sourceLevel,
            sourceRail,
            destinationLevel,
            destinationRail,
            sourceLinkages,
            destinationLinkages,
            combinedLinkages,
            context);
        if (routed != null && routed.isConnected())
        {
            return finishRouteSearch(context, routed, source, destination);
        }

        return finishRouteSearch(context, direct, source, destination);
    }

    /**
     * Selects the supported dimensional route shape and tries the available linkage candidates.
     *
     * @param sourceLevel level containing the source endpoint
     * @param sourceRail source route position
     * @param destinationLevel level containing the destination endpoint
     * @param destinationRail destination route position
     * @param sourceLinkages valid linkages installed at the source
     * @param destinationLinkages valid linkages installed at the destination
     * @param combinedLinkages unique linkage candidates from both endpoints
     * @param context per-search cache, limits, and diagnostics
     * @return a connected segmented result, or {@code null} when no supported linked route is found
     */
    private static TrackPathConnection.TrackConnectionResult findLinkedRoute(ServerLevel sourceLevel,
        BlockPos sourceRail,
        ServerLevel destinationLevel,
        BlockPos destinationRail,
        List<DimensionalLinkageRecord> sourceLinkages,
        List<DimensionalLinkageRecord> destinationLinkages,
        List<DimensionalLinkageRecord> combinedLinkages,
        RouteSearchContext context)
    {
        if (combinedLinkages.isEmpty())
        {
            return null;
        }

        if (sourceLevel.dimension().equals(Level.OVERWORLD) && destinationLevel.dimension().equals(Level.OVERWORLD))
        {
            return findOverworldToOverworldRoute(sourceLevel,
                sourceRail,
                destinationRail,
                sourceLinkages,
                destinationLinkages,
                combinedLinkages,
                context);
        }

        for (DimensionalLinkageRecord linkage : combinedLinkages)
        {
            if (!context.tryBeginPairAttempt())
            {
                return null;
            }

            if (sourceLevel.dimension().equals(Level.OVERWORLD) && destinationLevel.dimension().equals(Level.NETHER))
            {
                TrackPathConnection.TrackConnectionResult route = tryOneTransferRoute(sourceLevel,
                    sourceRail,
                    linkage.overworldEndpoint().get(),
                    linkage.netherEndpoint().get(),
                    destinationLevel,
                    destinationRail,
                    context);
                if (route != null && route.isConnected()) return route;
            }
            else if (sourceLevel.dimension().equals(Level.NETHER) && destinationLevel.dimension().equals(Level.OVERWORLD))
            {
                TrackPathConnection.TrackConnectionResult route = tryOneTransferRoute(sourceLevel,
                    sourceRail,
                    linkage.netherEndpoint().get(),
                    linkage.overworldEndpoint().get(),
                    destinationLevel,
                    destinationRail,
                    context);
                if (route != null && route.isConnected()) return route;
            }
        }

        return null;
    }

    /**
     * Tries to connect two Overworld endpoints through distinct entry and exit linkages in the Nether.
     * <p>
     * Linkages installed at the respective endpoints are preferred. If those candidates fail, the combined linkage set is used as a
     * fallback unless it is identical to both preferred sets.
     *
     * @param overworld level containing both route endpoints
     * @param sourceRail source route position
     * @param destinationRail destination route position
     * @param sourceLinkages preferred entry linkages installed at the source
     * @param destinationLinkages preferred exit linkages installed at the destination
     * @param combinedLinkages unique fallback linkages from both endpoints
     * @param context per-search cache, limits, and diagnostics
     * @return a connected Overworld-Nether-Overworld result, or {@code null} when no linkage pair connects
     */
    private static TrackPathConnection.TrackConnectionResult findOverworldToOverworldRoute(ServerLevel overworld,
        BlockPos sourceRail,
        BlockPos destinationRail,
        List<DimensionalLinkageRecord> sourceLinkages,
        List<DimensionalLinkageRecord> destinationLinkages,
        List<DimensionalLinkageRecord> combinedLinkages,
        RouteSearchContext context)
    {
        MinecraftServer server = overworld.getServer();
        ServerLevel nether = server.getLevel(NullnessBridge.assumeNonnull(Level.NETHER));
        if (nether == null)
        {
            return null;
        }

        TrackPathConnection.TrackConnectionResult endpointOwnedRoute =
            tryOverworldToOverworldLinkagePairs(overworld, nether, sourceRail, destinationRail, sourceLinkages, destinationLinkages, context);
        if (endpointOwnedRoute != null && endpointOwnedRoute.isConnected())
        {
            return endpointOwnedRoute;
        }

        if (sameLinkageSet(sourceLinkages, combinedLinkages) && sameLinkageSet(destinationLinkages, combinedLinkages))
        {
            return null;
        }

        return tryOverworldToOverworldLinkagePairs(overworld, nether, sourceRail, destinationRail, combinedLinkages, combinedLinkages, context);
    }

    /**
     * Attempts to build an Overworld-Nether-Overworld route from one set of entry linkages and one set of exit linkages.
     *
     * @param overworld Overworld level
     * @param nether Nether level
     * @param sourceRail source rail start position
     * @param destinationRail destination rail start position
     * @param entryLinkages candidate linkages reachable from the source side
     * @param exitLinkages candidate linkages reachable from the destination side
     * @param context per-search cache and safety limits
     * @return connected route result, or null when no linkage pair connects both endpoints
     */
    private static TrackPathConnection.TrackConnectionResult tryOverworldToOverworldLinkagePairs(ServerLevel overworld,
        ServerLevel nether,
        BlockPos sourceRail,
        BlockPos destinationRail,
        List<DimensionalLinkageRecord> entryLinkages,
        List<DimensionalLinkageRecord> exitLinkages,
        RouteSearchContext context)
    {
        if (sourceRail == null || destinationRail == null) return null;

        for (DimensionalLinkageRecord entry : entryLinkages)
        {
            for (DimensionalLinkageRecord exit : exitLinkages)
            {
                if (entry.id().equals(exit.id()))
                {
                    continue;
                }

                if (!context.tryBeginPairAttempt())
                {
                    return null;
                }

                TrackPathConnection.TrackConnectionResult startToEntry =
                    context.tryModalSegment(overworld, sourceRail, entry.overworldEndpoint().get().pos());
                if (!startToEntry.isConnected()) continue;

                TrackPathConnection.TrackConnectionResult netherSegment =
                    context.tryModalSegment(nether, entry.netherEndpoint().get().pos(), exit.netherEndpoint().get().pos());
                if (!netherSegment.isConnected()) continue;

                TrackPathConnection.TrackConnectionResult exitToDestination =
                    context.tryModalSegment(overworld, exit.overworldEndpoint().get().pos(), destinationRail);
                if (!exitToDestination.isConnected()) continue;

                @SuppressWarnings("null")
                TrackRoute route = joinedRoute(startToEntry,
                    TrackRoute.Segment.transfer(entry.overworldEndpoint().get(), entry.netherEndpoint().get()),
                    netherSegment,
                    TrackRoute.Segment.transfer(exit.netherEndpoint().get(), exit.overworldEndpoint().get()),
                    exitToDestination);

                return new TrackPathConnection.TrackConnectionResult(true, destinationRail, route.firstPath(), overworld.getGameTime(), route);
            }
        }

        return null;
    }

    /**
     * Checks whether two linkage lists contain the same linkage ids, ignoring order.
     *
     * @param first first linkage list to compare
     * @param second second linkage list to compare
     * @return true when both lists describe the same set of linkages
     */
    private static boolean sameLinkageSet(List<DimensionalLinkageRecord> first, List<DimensionalLinkageRecord> second)
    {
        if (first.size() != second.size())
        {
            return false;
        }

        Set<UUID> firstIds = new HashSet<>();
        for (DimensionalLinkageRecord linkage : first)
        {
            firstIds.add(linkage.id());
        }

        for (DimensionalLinkageRecord linkage : second)
        {
            if (!firstIds.contains(linkage.id()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Combines source and destination linkage records while preserving order and removing duplicate linkage ids.
     *
     * @param sourceLinkages valid linkages installed on the source endpoint
     * @param destinationLinkages valid linkages installed on the destination endpoint
     * @return ordered combined linkage records
     */
    private static List<DimensionalLinkageRecord> combineLinkages(List<DimensionalLinkageRecord> sourceLinkages,
        List<DimensionalLinkageRecord> destinationLinkages)
    {
        Map<UUID, DimensionalLinkageRecord> combined = new LinkedHashMap<>();
        for (DimensionalLinkageRecord linkage : sourceLinkages)
        {
            combined.put(linkage.id(), linkage);
        }
        for (DimensionalLinkageRecord linkage : destinationLinkages)
        {
            combined.putIfAbsent(linkage.id(), linkage);
        }
        return List.copyOf(combined.values());
    }

    /**
     * Builds a route containing one dimensional transfer and a multimodal leg on each side.
     *
     * @param sourceLevel level containing the source and transfer origin
     * @param sourceRail source route position
     * @param transferFrom transfer endpoint in the source dimension
     * @param transferTo paired transfer endpoint in the destination dimension
     * @param destinationLevel level containing the transfer destination and final endpoint
     * @param destinationRail final route position
     * @param context per-search cache, limits, and diagnostics
     * @return a connected composed result, or {@code null} when either local leg does not connect
     */
    private static TrackPathConnection.TrackConnectionResult tryOneTransferRoute(ServerLevel sourceLevel,
        BlockPos sourceRail,
        DimPos transferFrom,
        DimPos transferTo,
        ServerLevel destinationLevel,
        BlockPos destinationRail,
        RouteSearchContext context)
    {
        if (sourceRail == null || destinationRail == null) return null;

        TrackPathConnection.TrackConnectionResult startToTransfer =
            context.tryModalSegment(sourceLevel, sourceRail, transferFrom.pos());
        if (!startToTransfer.isConnected()) return null;

        TrackPathConnection.TrackConnectionResult transferToDestination =
            context.tryModalSegment(destinationLevel, transferTo.pos(), destinationRail);
        if (!transferToDestination.isConnected()) return null;

        TrackRoute route = joinedRoute(startToTransfer, TrackRoute.Segment.transfer(transferFrom, transferTo), transferToDestination);

        return new TrackPathConnection.TrackConnectionResult(true, destinationRail, route.firstPath(), sourceLevel.getGameTime(), route);
    }

    /**
     * Joins two local route results around one dimensional transfer.
     *
     * @param first local route before the transfer
     * @param transfer dimensional transfer segment
     * @param second local route after the transfer
     * @return combined segmented route
     */
    private static TrackRoute joinedRoute(TrackPathConnection.TrackConnectionResult first, TrackRoute.Segment transfer,
        TrackPathConnection.TrackConnectionResult second)
    {
        List<TrackRoute.Segment> segments = new ArrayList<>(routeSegments(first));
        segments.add(transfer);
        segments.addAll(routeSegments(second));
        return new TrackRoute(segments);
    }

    /**
     * Joins three local route results around an outbound and return dimensional transfer.
     *
     * @param first local route before entering the intermediate dimension
     * @param firstTransfer transfer into the intermediate dimension
     * @param middle route across the intermediate dimension
     * @param secondTransfer transfer back from the intermediate dimension
     * @param last local route to the final endpoint
     * @return combined segmented route
     */
    private static TrackRoute joinedRoute(TrackPathConnection.TrackConnectionResult first, TrackRoute.Segment firstTransfer,
        TrackPathConnection.TrackConnectionResult middle, TrackRoute.Segment secondTransfer,
        TrackPathConnection.TrackConnectionResult last)
    {
        List<TrackRoute.Segment> segments = new ArrayList<>(routeSegments(first));
        segments.add(firstTransfer);
        segments.addAll(routeSegments(middle));
        segments.add(secondTransfer);
        segments.addAll(routeSegments(last));
        return new TrackRoute(segments);
    }

    /**
     * Extracts the canonical segment list from a local connection result.
     *
     * @param result local connection result
     * @return result route segments, or an empty list when the result has no segmented route
     */
    private static List<TrackRoute.Segment> routeSegments(TrackPathConnection.TrackConnectionResult result)
    {
        return result != null && result.route != null ? result.route.segments() : List.of();
    }

    /**
     * Logs route-search diagnostics when a search was unusually expensive or hit a safety limit.
     *
     * @param context per-search counters and elapsed time
     * @param result result selected for the route search
     * @param source source trade-capable building
     * @param destination destination station data
     * @return the supplied result
     */
    private static TrackPathConnection.TrackConnectionResult finishRouteSearch(RouteSearchContext context,
        TrackPathConnection.TrackConnectionResult result,
        ITradeCapable source,
        StationData destination)
    {
        long elapsedNanos = System.nanoTime() - context.startNanos;
        if (elapsedNanos >= ROUTE_SEARCH_LOG_NANOS || context.pairLimitReached)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route search {} -> {} connected={} loadChunks={} segmentSearches={} cacheHits={} pairAttempts={} pairLimitReached={} elapsedMs={}",
                source.getRailStartPosition(),
                destination.getRailStartPosition(),
                result != null && result.isConnected(),
                context.loadChunks,
                context.segmentSearchCount,
                context.cacheHitCount,
                context.pairAttempts,
                context.pairLimitReached,
                elapsedNanos / 1_000_000L));
        }
        context.logRouteFinished(result, elapsedNanos);
        return result;
    }

    /**
     * Identifies a same-dimension multimodal segment request within one route search.
     *
     * @param dimension dimension containing the rail segment
     * @param start segment start position
     * @param end segment end position
     */
    private record ModalSegmentKey(ResourceKey<Level> dimension, BlockPos start, BlockPos end) { }

    /**
     * Stores per-route-search counters and caches repeated same-dimension multimodal searches.
     */
    private static class RouteSearchContext
    {
        private final long routeSearchId = ROUTE_SEARCH_SEQUENCE.incrementAndGet();
        private final boolean loadChunks;
        private final long startNanos = System.nanoTime();
        private final Map<ModalSegmentKey, TrackPathConnection.TrackConnectionResult> segmentCache = new LinkedHashMap<>();
        private int segmentSearchCount = 0;
        private int cacheHitCount = 0;
        private int pairAttempts = 0;
        private boolean pairLimitReached = false;

        /**
         * Creates a route search context for one call to {@link #findRoute(ITradeCapable, StationData, boolean)}.
         *
         * @param loadChunks whether rail searches within new modal searches may chunk-load while exploring
         */
        private RouteSearchContext(boolean loadChunks)
        {
            this.loadChunks = loadChunks;
        }

        /**
         * Logs the beginning of a dimension-aware route search.
         *
         * @param source source building for the route search
         * @param destination destination station data for the route search
         * @param sourceLevel level containing the source rail
         * @param destinationLevel level containing the destination rail
         */
        private void logRouteStart(ITradeCapable source, StationData destination, ServerLevel sourceLevel, ServerLevel destinationLevel)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} START source={} sourceDim={} destination={} destinationDim={} loadChunks={}",
                routeSearchId,
                source.getRailStartPosition(),
                sourceLevel.dimension().location(),
                destination.getRailStartPosition(),
                destinationLevel.dimension().location(),
                loadChunks));
        }

        /**
         * Logs the linkage candidates available to a route search, including their identities.
         *
         * @param sourceLinkages source-owned linkages
         * @param destinationLinkages destination-owned linkages
         * @param combinedLinkages unique linkages after combining endpoint records
         */
        @SuppressWarnings("null")
        private void logLinkages(List<DimensionalLinkageRecord> sourceLinkages,
            List<DimensionalLinkageRecord> destinationLinkages,
            List<DimensionalLinkageRecord> combinedLinkages)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} LINKAGES source={} sourceIds={} destination={} destinationIds={} combined={} combinedIds={}",
                routeSearchId,
                sourceLinkages.size(),
                sourceLinkages.stream().map(DimensionalLinkageRecord::id).toList(),
                destinationLinkages.size(),
                destinationLinkages.stream().map(DimensionalLinkageRecord::id).toList(),
                combinedLinkages.size(),
                combinedLinkages.stream().map(DimensionalLinkageRecord::id).toList()));
        }

        /**
         * Logs the final result for a route search.
         *
         * @param result route search result
         * @param elapsedNanos elapsed wall-clock nanoseconds
         */
        private void logRouteFinished(TrackPathConnection.TrackConnectionResult result, long elapsedNanos)
        {
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} END connected={} loadChunks={} segmentSearches={} cacheHits={} pairAttempts={} pairLimitReached={} elapsedMs={}",
                routeSearchId,
                result != null && result.isConnected(),
                loadChunks,
                segmentSearchCount,
                cacheHitCount,
                pairAttempts,
                pairLimitReached,
                elapsedNanos / 1_000_000L));
        }

        /**
         * Records an attempted linkage pair and enforces the route-search pair limit.
         *
         * @return true if the route search may continue with this pair
         */
        private boolean tryBeginPairAttempt()
        {
            if (pairAttempts >= MAX_LINKAGE_PAIR_ATTEMPTS)
            {
                pairLimitReached = true;
                TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} PAIR_LIMIT pairAttempts={} max={}",
                    routeSearchId,
                    pairAttempts,
                    MAX_LINKAGE_PAIR_ATTEMPTS));
                return false;
            }
            pairAttempts++;
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} PAIR_ATTEMPT {}/{}",
                routeSearchId,
                pairAttempts,
                MAX_LINKAGE_PAIR_ATTEMPTS));
            return true;
        }

        /**
         * Finds or reuses a same-dimension multimodal connection for the current route search.
         *
         * @param level level containing the segment
         * @param start segment start position
         * @param end segment end position
         * @return track connection result for this segment
         */
        private TrackPathConnection.TrackConnectionResult tryModalSegment(ServerLevel level, @Nonnull BlockPos start, @Nonnull BlockPos end)
        {
            ModalSegmentKey key = new ModalSegmentKey(level.dimension(), start, end);
            TrackPathConnection.TrackConnectionResult cached = segmentCache.get(key);
            if (cached != null)
            {
                cacheHitCount++;
                TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} SEGMENT_CACHE dim={} start={} end={} connected={}",
                    routeSearchId,
                    level.dimension().location(),
                    start,
                    end,
                    cached.isConnected()));
                return cached;
            }

            segmentSearchCount++;
            int segmentIndex = segmentSearchCount;
            long segmentStartNanos = System.nanoTime();
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} SEGMENT_BEGIN index={} dim={} start={} end={} loadChunks={}",
                routeSearchId,
                segmentIndex,
                level.dimension().location(),
                start,
                end,
                loadChunks));
            TrackPathConnection.TrackConnectionResult result = MultimodalRouteConnection.findRoute(level, start, end, loadChunks);
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} SEGMENT_END index={} dim={} start={} end={} connected={} pathSize={} elapsedMs={}",
                routeSearchId,
                segmentIndex,
                level.dimension().location(),
                start,
                end,
                result != null && result.isConnected(),
                result == null || result.path == null ? 0 : result.path.size(),
                (System.nanoTime() - segmentStartNanos) / 1_000_000L));
            segmentCache.put(key, result);
            return result;
        }
    }

    /**
     * Validates the loaded interior positions of a cached rail segment.
     * <p>
     * Endpoint positions are excluded because they may be modal handoff or building connection blocks. Unloaded positions are
     * accepted optimistically to avoid forcing chunk loads during cache validation.
     *
     * @param level level containing the segment
     * @param path ordered endpoint-inclusive rail path
     * @return {@code true} when the path is non-empty and every loaded interior position remains a track block
     */
    private static boolean validateRailSegment(ServerLevel level, List<BlockPos> path)
    {
        if (path == null || path.isEmpty())
        {
            return false;
        }

        for (int i = 1; i < path.size() - 1; i++)
        {
            BlockPos step = path.get(i);
            if (step == null)
            {
                return false;
            }
            if (!level.isLoaded(step))
            {
                continue;
            }
            if (!DimensionalLinkageItem.isTrackBlock(level, step))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates one endpoint of a cached dimensional transfer.
     *
     * @param server active Minecraft server
     * @param endpoint dimensional endpoint to validate
     * @return {@code true} when the endpoint is unloaded or remains a valid transport anchor beside an active portal
     */
    private static boolean validateTransferEndpoint(MinecraftServer server, DimPos endpoint)
    {
        if (endpoint == null)
        {
            return false;
        }
        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null)
        {
            return false;
        }
        if (!level.isLoaded(endpoint.pos()))
        {
            return true;
        }
        return DimensionalLinkageItem.isValidTransportAnchor(level, endpoint.pos()) &&
            DimensionalLinkageItem.isAdjacentToActivePortal(level, endpoint.pos());
    }

    /**
     * Gets all complete and currently valid dimensional linkages installed on the supplied source station.
     * <p>
     * Route discovery treats unloaded linkage endpoints optimistically, matching cached rail validation. The station module's GUI
     * validation may report those same endpoints as unloaded so players can see that the status is not fully confirmed.
     *
     * @param source trade-capable source building
     * @return linkage records that can participate in route discovery
     */
    public static List<DimensionalLinkageRecord> installedValidLinkages(ITradeCapable source)
    {
        if (!(source instanceof BuildingStation station))
        {
            return List.of();
        }

        BuildingStationConnectionModule module = station.getModule(MCTPBuildingModules.STATION_CONNECTION);
        if (module == null)
        {
            return List.of();
        }

        List<DimensionalLinkageRecord> linkages = new ArrayList<>();
        for (ItemStack stack : module.getDimensionalLinkages())
        {
            DimensionalLinkageRecord record = DimensionalLinkageItem.linkageRecord(stack);
            if (isRouteUsableLinkage(station, stack, record))
            {
                linkages.add(record);
            }
        }
        return linkages;
    }

    /**
     * Tests whether an installed linkage can be considered during route discovery.
     *
     * @param station station that owns the installed linkage
     * @param stack installed linkage item stack
     * @param record linkage record read from the stack
     * @return true when the linkage is complete, dimensionally valid, and has no loaded invalid endpoints
     */
    private static boolean isRouteUsableLinkage(BuildingStation station, ItemStack stack, DimensionalLinkageRecord record)
    {
        if (station == null || stack == null || stack.isEmpty() || !stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.DIMENSIONAL_LINKAGE.get())) || record == null || !record.isComplete())
        {
            return false;
        }

        DimPos overworld = record.overworldEndpoint().orElse(null);
        DimPos nether = record.netherEndpoint().orElse(null);
        if (overworld == null || nether == null || !overworld.isOverworld() || !nether.isNether())
        {
            return false;
        }

        MinecraftServer server = station.getColony() == null || station.getColony().getWorld() == null
            ? null
            : station.getColony().getWorld().getServer();
        if (server == null)
        {
            return false;
        }

        return isRouteUsableEndpoint(server, overworld) && isRouteUsableEndpoint(server, nether);
    }

    /**
     * Tests a linkage endpoint for route discovery, treating unloaded chunks as provisionally valid.
     *
     * @param server active Minecraft server
     * @param endpoint endpoint to test
     * @return true when the endpoint is either unloaded or currently confirms as portal-adjacent track
     */
    private static boolean isRouteUsableEndpoint(MinecraftServer server, DimPos endpoint)
    {
        if (endpoint == null)
        {
            return false;
        }

        ServerLevel level = server.getLevel(endpoint.dimension());
        if (level == null)
        {
            return false;
        }

        if (!level.isLoaded(endpoint.pos()))
        {
            return true;
        }

        return DimensionalLinkageItem.isValidTransportAnchor(level, endpoint.pos()) &&
            DimensionalLinkageItem.isAdjacentToActivePortal(level, endpoint.pos());
    }
}
