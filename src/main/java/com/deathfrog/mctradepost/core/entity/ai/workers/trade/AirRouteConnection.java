package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.core.blocks.BlockMooringBay;
import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds preferred same-dimension routes between sky-open Mooring Bays without pathfinding through the intervening world. Local
 * station-to-Bay legs use the bounded multimodal finder; the long flight is represented by one abstract distance segment.
 */
public final class AirRouteConnection
{
    private static final int MAX_BAYS_PER_ENDPOINT = 4;

    /** Prevents construction of this route-search utility. */
    private AirRouteConnection()
    {}

    /**
     * Finds the best available air route between two trade-capable buildings.
     *
     * @param source      originating station or outpost
     * @param destination destination station data
     * @param loadChunks  whether local rail discovery may temporarily load chunks
     * @param allowWater  whether local Maritime Trade legs may use water
     * @return connected air route, or {@code null} when air trade is unavailable
     */
    public static TrackPathConnection.TrackConnectionResult findRoute(ITradeCapable source,
        StationData destination,
        boolean loadChunks,
        boolean allowWater)
    {
        return findRoute(source, destination, loadChunks, allowWater, true);
    }

    /**
     * Finds the best available physical air route, optionally enforcing the bilateral research prerequisite. The bypass form is
     * intended for diagnostics and does not alter production route selection.
     *
     * @param source          originating station or outpost
     * @param destination     destination station data
     * @param loadChunks      whether local rail discovery may temporarily load chunks
     * @param allowWater      whether local Maritime Trade legs may use water
     * @param enforceResearch true to require production research, false to diagnose physical connectivity
     * @return connected air route, or {@code null} when air trade is unavailable
     */
    @SuppressWarnings("null")
    public static TrackPathConnection.TrackConnectionResult findRoute(ITradeCapable source,
        StationData destination,
        boolean loadChunks,
        boolean allowWater,
        boolean enforceResearch)
    {
        ITradeCapable destinationBuilding = destination == null ? null : destination.getStation();
        if (source == null || destinationBuilding == null || source.getColony() == null || destinationBuilding.getColony() == null)
        {
            return null;
        }
        if (!source.getColony().getDimension().equals(destinationBuilding.getColony().getDimension())) return null;
        if (enforceResearch && !hasRequiredResearch(source.getColony(), destinationBuilding.getColony())) return null;

        ServerLevel level = (ServerLevel) source.getColony().getWorld();
        List<BlockPos> sourceBays = eligibleBays(level, source.getColony(), source.getRailStartPosition());
        List<BlockPos> destinationBays = eligibleBays(level, destinationBuilding.getColony(), destination.getRailStartPosition());
        if (sourceBays.isEmpty() || destinationBays.isEmpty()) return null;

        Map<BlockPos, TrackRoute> sourceLegs = new LinkedHashMap<>();
        for (BlockPos sourceBay : sourceBays)
        {
            if (!validateCandidateBay(level, sourceBay)) continue;
            TrackPathConnection.TrackConnectionResult sourceLeg =
                MultimodalRouteConnection.findRoute(level, source.getRailStartPosition(), sourceBay, loadChunks, allowWater);
            if (sourceLeg.isConnected() && sourceLeg.getRoute() != null) sourceLegs.put(sourceBay, sourceLeg.getRoute());
        }

        Map<BlockPos, TrackRoute> destinationLegs = new LinkedHashMap<>();
        for (BlockPos destinationBay : destinationBays)
        {
            if (!validateCandidateBay(level, destinationBay)) continue;
            TrackPathConnection.TrackConnectionResult destinationLeg =
                MultimodalRouteConnection.findRoute(level, destinationBay, destination.getRailStartPosition(), loadChunks, allowWater);
            if (destinationLeg.isConnected() && destinationLeg.getRoute() != null)
            {
                destinationLegs.put(destinationBay, destinationLeg.getRoute());
            }
        }

        TrackRoute bestRoute = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<BlockPos, TrackRoute> sourceEntry : sourceLegs.entrySet())
        {
            for (Map.Entry<BlockPos, TrackRoute> destinationEntry : destinationLegs.entrySet())
            {
                if (sourceEntry.getKey().equals(destinationEntry.getKey())) continue;
                if (!(level.getBlockEntity(sourceEntry.getKey()) instanceof MooringBayBlockEntity sourceBayEntity) ||
                    !sourceBayEntity.authorizes(destinationEntry.getKey())) continue;
                TrackRoute route = compose(level,
                    source.getColony(),
                    destinationBuilding.getColony(),
                    sourceEntry.getKey(),
                    destinationEntry.getKey(),
                    sourceEntry.getValue(),
                    destinationEntry.getValue());
                int distance = route.totalDistance();
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    bestRoute = route;
                }
            }
        }

        return bestRoute == null ? null :
            new TrackPathConnection.TrackConnectionResult(true,
                destination.getRailStartPosition(),
                bestRoute.firstPath(),
                level.getGameTime(),
                bestRoute);
    }

    /**
     * Determines whether a route contains an air-travel segment.
     *
     * @param route route to inspect
     * @return true when the route uses air trade
     */
    public static boolean isAirRoute(TrackRoute route)
    {
        if (route == null) return false;
        for (TrackRoute.Segment segment : route.segments())
        {
            if (segment.type() == TrackRoute.SegmentType.AIR || segment.type() == TrackRoute.SegmentType.AIR_TRANSIT) return true;
        }
        return false;
    }

    /**
     * Validates the research, ownership, block, and sky-clearance prerequisites for launching a cached air route. This check is
     * intentionally used only before launch; an in-flight shipment is allowed to finish after later changes.
     *
     * @param source      originating trade building
     * @param destination destination trade building
     * @param route       cached route to validate
     * @return true when a non-air route needs no air validation or every air-launch prerequisite remains satisfied
     */
    @SuppressWarnings("null")
    public static boolean canLaunch(ITradeCapable source, ITradeCapable destination, TrackRoute route)
    {
        if (!isAirRoute(route)) return true;
        if (source == null || destination == null || source.getColony() == null || destination.getColony() == null) return false;
        if (!hasRequiredResearch(source.getColony(), destination.getColony())) return false;
        if (!source.getColony().getDimension().equals(destination.getColony().getDimension())) return false;

        ServerLevel level = (ServerLevel) source.getColony().getWorld();
        List<BlockPos> routeBays = new ArrayList<>();

        for (TrackRoute.Segment segment : route.segments())
        {
            if (segment.type() != TrackRoute.SegmentType.MOORING) continue;
            if (segment.path().isEmpty()) return false;
            final BlockPos bay = segment.path().getFirst();

            if (bay == null) continue;

            if (!level.isLoaded(bay) || !level.getBlockState(bay).is(MCTradePostMod.MOORING_BAY.get()) ||
                !BlockMooringBay.isOpenToSky(level, bay)) return false;
            IColony owner = IColonyManager.getInstance().getColonyByPosFromWorld(level, bay);
            if (owner == null || (owner.getID() != source.getColony().getID() && owner.getID() != destination.getColony().getID()))
                return false;
            routeBays.add(bay);
        }

        if (routeBays.size() < 2 || !(level.getBlockEntity(routeBays.getFirst()) instanceof MooringBayBlockEntity departure) ||
            !departure.authorizes(routeBays.getLast())) return false;

        return true;
    }

    /** Returns the Lifting Gas required by the air portions of a route. */
    public static int liftingGasCost(TrackRoute route)
    {
        if (!isAirRoute(route)) return 0;
        long airDistance = 0;
        
        for (TrackRoute.Segment segment : route.segments())
        {
            if (segment.type() == TrackRoute.SegmentType.AIR || segment.type() == TrackRoute.SegmentType.AIR_TRANSIT)
            {
                airDistance += segment.distance();
            }
        }

        int divisor = Math.max(1, MCTPConfig.airshipBlocksPerGasUnit.get());

        return MCTPConfig.airshipBaseGasCost.get() + (int) Math.min(Integer.MAX_VALUE, (airDistance + divisor - 1) / divisor);
    }

    /** Finds the source-side Bay on a forward route. */
    @SuppressWarnings("null")
    public static MooringBayBlockEntity departureBay(ServerLevel level, TrackRoute route)
    {
        if (route == null) return null;
        for (TrackRoute.Segment segment : route.segments())
            if (segment.type() == TrackRoute.SegmentType.MOORING && !segment.path().isEmpty())
        {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(segment.path().getFirst());
            return be instanceof MooringBayBlockEntity bay ? bay : null;
        }
        return null;
    }

    /**
     * Tests the bilateral research rule, counting one research completion for endpoints in the same colony.
     *
     * @param source      source colony
     * @param destination destination colony
     * @return true when air trade is researched by every distinct participating colony
     */
    private static boolean hasRequiredResearch(IColony source, IColony destination)
    {
        boolean sourceResearched = source.getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.AIR_TRADE) > 0;

        if (!sourceResearched) return false;

        return source.getID() == destination.getID() || destination.getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.AIR_TRADE) > 0;
    }

    /**
     * Selects a bounded nearest-first list of loaded, owned, sky-open Mooring Bays.
     *
     * @param level    dimension containing the colony
     * @param colony   colony that must own each Bay
     * @param endpoint local trade-building endpoint used for ranking
     * @return at most four eligible Bay positions
     */
    @SuppressWarnings("null")
    private static List<BlockPos> eligibleBays(ServerLevel level, IColony colony, BlockPos endpoint)
    {
        List<BlockPos> bays = new ArrayList<>();
        for (BlockPos bay : MooringBayRegistry.get(level).bays())
        {
            if (bay == null) continue;

            IColony owner = IColonyManager.getInstance().getColonyByPosFromWorld(level, bay);
            if (owner == null || owner.getID() != colony.getID()) continue;
            if (level.isLoaded(bay) &&
                (!level.getBlockState(bay).is(MCTradePostMod.MOORING_BAY.get()) || !BlockMooringBay.isOpenToSky(level, bay))) continue;
            bays.add(bay);
        }
        bays.sort(Comparator.comparingDouble(endpoint::distSqr));
        if (bays.size() > MAX_BAYS_PER_ENDPOINT) return new ArrayList<>(bays.subList(0, MAX_BAYS_PER_ENDPOINT));
        return bays;
    }

    /**
     * Loads and authoritatively validates one already-ranked endpoint candidate. Only bounded local endpoint candidates reach this
     * method; no flight-corridor chunks are loaded.
     *
     * @param level dimension containing the candidate
     * @param bay   candidate Mooring Bay position
     * @return true when the registered block exists and its 3x3 area is sky-open
     */
    @SuppressWarnings("null")
    private static boolean validateCandidateBay(ServerLevel level, @Nonnull BlockPos bay)
    {
        level.getChunkAt(bay);
        return level.getBlockState(bay).is(MCTradePostMod.MOORING_BAY.get()) && BlockMooringBay.isOpenToSky(level, bay);
    }

    /**
     * Composes both local routes with visible endpoint flights and an abstract border-to-border transit leg.
     *
     * @param level             shared endpoint dimension
     * @param sourceColony      source colony
     * @param destinationColony destination colony
     * @param sourceBay         selected source Bay
     * @param destinationBay    selected destination Bay
     * @param sourceLeg         source building to Bay route
     * @param destinationLeg    destination Bay to building route
     * @return complete air-trade route
     */
    private static TrackRoute compose(ServerLevel level,
        IColony sourceColony,
        IColony destinationColony,
        @Nonnull BlockPos sourceBay,
        @Nonnull BlockPos destinationBay,
        TrackRoute sourceLeg,
        TrackRoute destinationLeg)
    {
        boolean sameColony = sourceColony.getID() == destinationColony.getID();
        BlockPos sourceBorder = sameColony ? sourceBay : findBorder(level, sourceColony, sourceBay, destinationBay);
        BlockPos destinationBorder = sameColony ? destinationBay : findBorder(level, destinationColony, destinationBay, sourceBay);
        int sourceCruiseY = cruiseY(level, sourceBay, sourceBorder);
        int destinationCruiseY = cruiseY(level, destinationBay, destinationBorder);
        BlockPos sourceCruiseBorder = new BlockPos(sourceBorder.getX(), sourceCruiseY, sourceBorder.getZ());
        BlockPos destinationCruiseBorder = new BlockPos(destinationBorder.getX(), destinationCruiseY, destinationBorder.getZ());

        ResourceKey<Level> levelDim = level.dimension();

        if (levelDim == null) return null;

        List<TrackRoute.Segment> segments = new ArrayList<>(sourceLeg.segments());
        segments.add(TrackRoute.Segment.mooring(levelDim, sourceBay));
        segments.add(TrackRoute.Segment.air(levelDim, endpointFlightPath(sourceBay, sourceCruiseBorder)));
        segments.add(TrackRoute.Segment.airTransit(levelDim, sourceCruiseBorder, destinationCruiseBorder));
        List<BlockPos> arrival = endpointFlightPath(destinationBay, destinationCruiseBorder);
        java.util.Collections.reverse(arrival);
        segments.add(TrackRoute.Segment.air(levelDim, arrival));
        segments.add(TrackRoute.Segment.mooring(levelDim, destinationBay));
        segments.addAll(destinationLeg.segments());
        return new TrackRoute(segments);
    }

    /**
     * Walks the direct horizontal line until it leaves the owning colony and returns the last owned position. The walk queries colony
     * claims only and does not load terrain chunks.
     *
     * @param level  shared route dimension
     * @param colony colony whose boundary is requested
     * @param from   Bay inside the colony
     * @param toward remote Bay direction
     * @return last claimed position along the direct line
     */
    private static BlockPos findBorder(ServerLevel level, IColony colony, BlockPos from, BlockPos toward)
    {
        int dx = toward.getX() - from.getX();
        int dz = toward.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        BlockPos lastOwned = from;
        for (int step = 1; step <= steps; step++)
        {
            int x = from.getX() + (int) Math.round(dx * (step / (double) steps));
            int z = from.getZ() + (int) Math.round(dz * (step / (double) steps));
            BlockPos check = new BlockPos(x, from.getY(), z);
            IColony owner = IColonyManager.getInstance().getColonyByPosFromWorld(level, check);
            if (owner == null || owner.getID() != colony.getID()) return lastOwned;
            lastOwned = check;
        }
        return lastOwned;
    }

    /**
     * Calculates cruising altitude from the higher of the Bay and loaded local border terrain.
     *
     * @param level  endpoint dimension
     * @param bay    endpoint Bay
     * @param border local colony-border position
     * @return configured-clearance cruising Y coordinate
     */
    private static int cruiseY(ServerLevel level, BlockPos bay, BlockPos border)
    {
        int terrainY = border.getY();
        if (level.isLoaded(border))
        {
            terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, border.getX(), border.getZ());
        }
        return Math.max(bay.getY(), terrainY) + MCTPConfig.airshipCruisingClearance.get();
    }

    /**
     * Builds the short visible path that rises above a Bay and then flies to its colony border.
     *
     * @param bay          Bay position
     * @param cruiseBorder border position at cruising altitude
     * @return mutable ordered endpoint path
     */
    static @Nonnull List<BlockPos> endpointFlightPath(@Nonnull BlockPos bay, @Nonnull BlockPos cruiseBorder)
    {
        List<BlockPos> path = new ArrayList<>();
        path.add(bay.immutable());
        int direction = Integer.compare(cruiseBorder.getY(), bay.getY());
        for (int y = bay.getY() + direction; direction != 0 && y != cruiseBorder.getY() + direction; y += direction)
        {
            path.add(new BlockPos(bay.getX(), y, bay.getZ()));
        }

        int dx = cruiseBorder.getX() - bay.getX();
        int dz = cruiseBorder.getZ() - bay.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        for (int step = 1; step <= steps; step++)
        {
            int x = bay.getX() + (int) Math.round(dx * (step / (double) steps));
            int z = bay.getZ() + (int) Math.round(dz * (step / (double) steps));
            BlockPos next = new BlockPos(x, cruiseBorder.getY(), z);
            if (!next.equals(path.getLast())) path.add(next);
        }
        return path;
    }
}
