package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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
 * Finds and validates station routes that may span multiple dimensions through installed dimensional linkages.
 */
public class TrackRouteConnection
{
    private static final int MAX_LINKAGE_PAIR_ATTEMPTS = 64;
    private static final long ROUTE_SEARCH_LOG_NANOS = 50_000_000L;
    private static final AtomicLong ROUTE_SEARCH_SEQUENCE = new AtomicLong();

    /**
     * Validates an existing cached route without doing a full BFS search.
     * <p>
     * Loaded rail positions must still be valid track blocks. Unloaded rail positions are treated optimistically, matching the
     * existing track validation behavior.
     *
     * @param server active Minecraft server
     * @param result cached connection result to validate
     * @return true when the cached route can still be considered connected
     */
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
     * Same-dimension rail paths are attempted first. If no direct path exists, installed valid dimensional linkages on both endpoint
     * buildings are used to build one-transfer or Overworld-Nether-Overworld segmented routes.
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
            direct = context.tryRailSegment(sourceLevel, sourceRail, destinationRail);
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
                    context.tryRailSegment(overworld, sourceRail, entry.overworldEndpoint().get().pos());
                if (!startToEntry.isConnected()) continue;

                TrackPathConnection.TrackConnectionResult netherSegment =
                    context.tryRailSegment(nether, entry.netherEndpoint().get().pos(), exit.netherEndpoint().get().pos());
                if (!netherSegment.isConnected()) continue;

                TrackPathConnection.TrackConnectionResult exitToDestination =
                    context.tryRailSegment(overworld, exit.overworldEndpoint().get().pos(), destinationRail);
                if (!exitToDestination.isConnected()) continue;

                @SuppressWarnings("null")
                TrackRoute route = new TrackRoute(List.of(
                    TrackRoute.Segment.rail(Level.OVERWORLD, startToEntry.path),
                    TrackRoute.Segment.transfer(entry.overworldEndpoint().get(), entry.netherEndpoint().get()),
                    TrackRoute.Segment.rail(Level.NETHER, netherSegment.path),
                    TrackRoute.Segment.transfer(exit.netherEndpoint().get(), exit.overworldEndpoint().get()),
                    TrackRoute.Segment.rail(Level.OVERWORLD, exitToDestination.path)));

                return new TrackPathConnection.TrackConnectionResult(true, destinationRail, route.firstRailPath(), overworld.getGameTime(), route);
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

    private static TrackPathConnection.TrackConnectionResult tryOneTransferRoute(ServerLevel sourceLevel,
        BlockPos sourceRail,
        DimPos transferFrom,
        DimPos transferTo,
        ServerLevel destinationLevel,
        BlockPos destinationRail,
        RouteSearchContext context)
    {
        TrackPathConnection.TrackConnectionResult startToTransfer =
            context.tryRailSegment(sourceLevel, sourceRail, transferFrom.pos());
        if (!startToTransfer.isConnected()) return null;

        TrackPathConnection.TrackConnectionResult transferToDestination =
            context.tryRailSegment(destinationLevel, transferTo.pos(), destinationRail);
        if (!transferToDestination.isConnected()) return null;

        @SuppressWarnings("null")
        TrackRoute route = new TrackRoute(List.of(
            TrackRoute.Segment.rail(sourceLevel.dimension(), startToTransfer.path),
            TrackRoute.Segment.transfer(transferFrom, transferTo),
            TrackRoute.Segment.rail(destinationLevel.dimension(), transferToDestination.path)));

        return new TrackPathConnection.TrackConnectionResult(true, destinationRail, route.firstRailPath(), sourceLevel.getGameTime(), route);
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
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route search {} -> {} connected={} loadChunks={} railSearches={} cacheHits={} pairAttempts={} pairLimitReached={} elapsedMs={}",
                source.getRailStartPosition(),
                destination.getRailStartPosition(),
                result != null && result.isConnected(),
                context.loadChunks,
                context.railSearchCount,
                context.cacheHitCount,
                context.pairAttempts,
                context.pairLimitReached,
                elapsedNanos / 1_000_000L));
        }
        context.logRouteFinished(result, elapsedNanos);
        return result;
    }

    /**
     * Identifies a rail-segment BFS request within one route search.
     *
     * @param dimension dimension containing the rail segment
     * @param start segment start position
     * @param end segment end position
     */
    private record RailSegmentKey(ResourceKey<Level> dimension, BlockPos start, BlockPos end) { }

    /**
     * Stores per-route-search counters and caches repeated rail segment checks.
     */
    private static class RouteSearchContext
    {
        private final long routeSearchId = ROUTE_SEARCH_SEQUENCE.incrementAndGet();
        private final boolean loadChunks;
        private final long startNanos = System.nanoTime();
        private final Map<RailSegmentKey, TrackPathConnection.TrackConnectionResult> segmentCache = new LinkedHashMap<>();
        private int railSearchCount = 0;
        private int cacheHitCount = 0;
        private int pairAttempts = 0;
        private boolean pairLimitReached = false;

        /**
         * Creates a route search context for one call to {@link #findRoute(ITradeCapable, StationData, boolean)}.
         *
         * @param loadChunks whether new rail searches may chunk-load while exploring
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
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} END connected={} loadChunks={} railSearches={} cacheHits={} pairAttempts={} pairLimitReached={} elapsedMs={}",
                routeSearchId,
                result != null && result.isConnected(),
                loadChunks,
                railSearchCount,
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
         * Finds or reuses a rail segment connection for the current route search.
         *
         * @param level level containing the segment
         * @param start segment start position
         * @param end segment end position
         * @return track connection result for this segment
         */
        private TrackPathConnection.TrackConnectionResult tryRailSegment(ServerLevel level, BlockPos start, BlockPos end)
        {
            RailSegmentKey key = new RailSegmentKey(level.dimension(), start, end);
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

            railSearchCount++;
            int segmentIndex = railSearchCount;
            long segmentStartNanos = System.nanoTime();
            TraceUtils.dynamicTrace(TRACE_TRACKPATH, () -> MCTradePostMod.LOGGER.warn("Track route #{} SEGMENT_BEGIN index={} dim={} start={} end={} loadChunks={}",
                routeSearchId,
                segmentIndex,
                level.dimension().location(),
                start,
                end,
                loadChunks));
            TrackPathConnection.TrackConnectionResult result = TrackPathConnection.arePointsConnectedByTracks(level, start, end, loadChunks);
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
        return DimensionalLinkageItem.isTrackBlock(level, endpoint.pos()) &&
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

        return DimensionalLinkageItem.isTrackBlock(level, endpoint.pos()) &&
            DimensionalLinkageItem.isAdjacentToActivePortal(level, endpoint.pos());
    }
}
