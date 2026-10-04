package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

class TrackRouteTest
{
    @SuppressWarnings("null")
    @Test
    void computesUniformDistanceAndZeroCostDockHandoffs()
    {
        TrackRoute route = new TrackRoute(List.of(
            TrackRoute.Segment.road(Level.OVERWORLD, List.of(BlockPos.ZERO, BlockPos.ZERO.east(), BlockPos.ZERO.east(2))),
            TrackRoute.Segment.interchange(Level.OVERWORLD, BlockPos.ZERO.east(2)),
            TrackRoute.Segment.dock(Level.OVERWORLD, BlockPos.ZERO.east(3)),
            TrackRoute.Segment.water(Level.OVERWORLD, List.of(BlockPos.ZERO.east(4), BlockPos.ZERO.east(5)))));

        assertEquals(3, route.totalDistance());
        assertEquals(TrackRoute.SegmentType.ROAD, route.firstPath().isEmpty() ? null : route.segments().getFirst().type());
    }

    @SuppressWarnings("null")
    @Test
    void reversalPreservesModesAndReversesTheirPaths()
    {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = first.east();
        TrackRoute reversed = new TrackRoute(List.of(
            TrackRoute.Segment.road(Level.OVERWORLD, List.of(first, second)),
            TrackRoute.Segment.dock(Level.OVERWORLD, second),
            TrackRoute.Segment.water(Level.OVERWORLD, List.of(second, second.east())))).reversed();

        assertEquals(TrackRoute.SegmentType.WATER, reversed.segments().getFirst().type());
        assertEquals(second.east(), reversed.segments().getFirst().path().getFirst());
        assertEquals(TrackRoute.SegmentType.DOCK, reversed.segments().get(1).type());
        assertEquals(TrackRoute.SegmentType.ROAD, reversed.segments().getLast().type());
    }

    @SuppressWarnings("null")
    @Test
    void airTransitUsesHorizontalEuclideanDistanceAndReversesEndpoints()
    {
        BlockPos origin = new BlockPos(10, 90, 20);
        BlockPos destination = new BlockPos(13, 120, 24);
        TrackRoute route = new TrackRoute(List.of(
            TrackRoute.Segment.mooring(Level.OVERWORLD, origin),
            TrackRoute.Segment.airTransit(Level.OVERWORLD, origin, destination),
            TrackRoute.Segment.mooring(Level.OVERWORLD, destination)));

        assertEquals(5, route.totalDistance());
        TrackRoute reversed = route.reversed();
        assertEquals(destination, reversed.segments().get(1).path().getFirst());
        assertEquals(origin, reversed.segments().get(1).path().getLast());
    }

    @SuppressWarnings("null")
    @Test
    void identifiesOnlyRoutesContainingAirTravelAsAirRoutes()
    {
        TrackRoute terrestrial = new TrackRoute(List.of(
            TrackRoute.Segment.rail(Level.OVERWORLD, positions(0, 2)),
            TrackRoute.Segment.interchange(Level.OVERWORLD, BlockPos.ZERO.east(2)),
            TrackRoute.Segment.road(Level.OVERWORLD, positions(2, 4))));
        TrackRoute air = new TrackRoute(List.of(
            TrackRoute.Segment.mooring(Level.OVERWORLD, BlockPos.ZERO),
            TrackRoute.Segment.airTransit(Level.OVERWORLD, BlockPos.ZERO, BlockPos.ZERO.east(10)),
            TrackRoute.Segment.mooring(Level.OVERWORLD, BlockPos.ZERO.east(10))));

        assertFalse(AirRouteConnection.isAirRoute(null));
        assertFalse(AirRouteConnection.isAirRoute(terrestrial));
        assertTrue(AirRouteConnection.isAirRoute(air));
    }

    @SuppressWarnings("null")
    @Test
    void segmentSpeedFactorsApplyAcrossModeBoundariesWithoutChangingRouteDistance()
    {
        TrackRoute route = new TrackRoute(List.of(
            TrackRoute.Segment.road(Level.OVERWORLD, positions(0, 5)),
            TrackRoute.Segment.interchange(Level.OVERWORLD, BlockPos.ZERO.east(5)),
            TrackRoute.Segment.air(Level.OVERWORLD, positions(5, 25)),
            TrackRoute.Segment.airTransit(Level.OVERWORLD, BlockPos.ZERO.east(25), BlockPos.ZERO.east(125)),
            TrackRoute.Segment.mooring(Level.OVERWORLD, BlockPos.ZERO.east(125)),
            TrackRoute.Segment.rail(Level.OVERWORLD, positions(125, 130))));

        assertEquals(130, route.totalDistance());
        assertEquals(65, route.advanceDistance(0, 14, type -> switch (type)
        {
            case AIR -> 4.0D;
            case AIR_TRANSIT -> 10.0D;
            default -> 1.0D;
        }));
        assertEquals(130, route.advanceDistance(65, 12, type -> switch (type)
        {
            case AIR -> 4.0D;
            case AIR_TRANSIT -> 10.0D;
            default -> 1.0D;
        }));
    }

    private static List<BlockPos> positions(int startX, int endX)
    {
        return java.util.stream.IntStream.rangeClosed(startX, endX)
            .mapToObj(x -> new BlockPos(x, 0, 0))
            .toList();
    }

    @Test
    void endpointFlightPathRisesBeforeMovingTowardBorder()
    {
        BlockPos bay = new BlockPos(0, 64, 0);
        List<BlockPos> path = AirRouteConnection.endpointFlightPath(bay, new BlockPos(3, 66, 0));

        assertEquals(List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(0, 65, 0),
            new BlockPos(0, 66, 0),
            new BlockPos(1, 66, 0),
            new BlockPos(2, 66, 0),
            new BlockPos(3, 66, 0)), path);
    }
}
