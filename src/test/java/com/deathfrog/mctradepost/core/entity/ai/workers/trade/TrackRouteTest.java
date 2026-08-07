package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
