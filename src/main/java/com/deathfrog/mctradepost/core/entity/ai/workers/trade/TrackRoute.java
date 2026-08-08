package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Dimension-aware route made of traversable segments, modal handoffs, and dimensional transfer hops.
 * <p>
 * A route is intentionally segmented so shipment progress and vehicle rendering can follow rail, road, or water paths, change
 * transport mode at docks and interchanges, disappear during a dimensional transfer, and reappear in the destination dimension with
 * the correct local path.
 */
public class TrackRoute
{
    /**
     * Type of route segment.
     */
    public enum SegmentType
    {
        /**
         * A normal contiguous rail path in one dimension.
         */
        RAIL,
        /** A contiguous tagged trade-road path. */
        ROAD,
        /** A contiguous navigable surface-water path between docks. */
        WATER,
        /** A zero-distance vehicle handoff at a trade dock. */
        DOCK,
        /** A zero-distance rail/road vehicle handoff. */
        INTERCHANGE,
        /**
         * A one-step transition between paired dimensional linkage endpoints.
         */
        TRANSFER
    }

    /**
     * One segment of a route.
     *
     * @param type segment type
     * @param dimension dimension used for rail traversal or transfer origin
     * @param path ordered traversal positions, a handoff position, or the two endpoint positions of a transfer
     * @param transferFrom origin endpoint for transfer segments
     * @param transferTo destination endpoint for transfer segments
     */
    public record Segment(@Nonnull SegmentType type, @Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path, DimPos transferFrom, DimPos transferTo)
    {
        /**
         * Creates a rail segment in one dimension.
         *
         * @param dimension dimension containing the rail path
         * @param path ordered rail path
         * @return immutable rail segment
         */
        public static Segment rail(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            return traversable(SegmentType.RAIL, dimension, path);
        }

        /**
         * Creates a road segment in one dimension.
         *
         * @param dimension dimension containing the road path
         * @param path ordered tagged trade-road path
         * @return immutable road segment
         */
        public static Segment road(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            return traversable(SegmentType.ROAD, dimension, path);
        }

        /**
         * Creates a water segment in one dimension.
         *
         * @param dimension dimension containing the water path
         * @param path ordered navigable surface-water path
         * @return immutable water segment
         */
        public static Segment water(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            return traversable(SegmentType.WATER, dimension, path);
        }

        /**
         * Creates an immutable rail, road, or water segment.
         *
         * @param type traversable segment type
         * @param dimension dimension containing the path
         * @param path ordered positions along the path
         * @return immutable traversable segment containing a defensive copy of the path
         * @throws IllegalArgumentException when {@code type} represents a transfer or modal handoff
         */
        @SuppressWarnings("null")
        public static Segment traversable(@Nonnull SegmentType type, @Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            if (type == SegmentType.TRANSFER || type == SegmentType.DOCK || type == SegmentType.INTERCHANGE)
            {
                throw new IllegalArgumentException("Transfer segments require endpoints");
            }
            return new Segment(type, dimension, Collections.unmodifiableList(new ArrayList<>(path)), null, null);
        }

        /**
         * Creates a dimensional transfer segment between two linkage endpoints.
         *
         * @param from origin transfer endpoint
         * @param to destination transfer endpoint
         * @return transfer segment
         */
        @SuppressWarnings("null")
        public static Segment transfer(@Nonnull DimPos from, @Nonnull DimPos to)
        {
            return new Segment(SegmentType.TRANSFER, from.dimension(), List.of(from.pos(), to.pos()), from, to);
        }

        /**
         * Creates a zero-distance modal handoff at a trade dock.
         *
         * @param dimension dimension containing the dock
         * @param position position of the dock
         * @return dock handoff segment
         */
        @SuppressWarnings("null")
        public static Segment dock(@Nonnull ResourceKey<Level> dimension, @Nonnull BlockPos position)
        {
            return new Segment(SegmentType.DOCK, dimension, List.of(position), null, null);
        }

        /**
         * Creates a zero-distance rail/road handoff at a trade interchange.
         *
         * @param dimension dimension containing the interchange
         * @param position position of the interchange
         * @return interchange handoff segment
         */
        @SuppressWarnings("null")
        public static Segment interchange(@Nonnull ResourceKey<Level> dimension, @Nonnull BlockPos position)
        {
            return new Segment(SegmentType.INTERCHANGE, dimension, List.of(position), null, null);
        }

        /**
         * @return travel distance represented by this segment
         */
        public int distance()
        {
            if (type == SegmentType.TRANSFER)
            {
                return 1;
            }
            if (type == SegmentType.DOCK || type == SegmentType.INTERCHANGE)
            {
                return 0;
            }
            return path == null ? 0 : Math.max(0, path.size() - 1);
        }
    }

    private final List<Segment> segments;

    /**
     * Creates a route from the supplied ordered segments.
     *
     * @param segments route segments in travel order
     */
    public TrackRoute(List<Segment> segments)
    {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    /**
     * Wraps a same-dimension rail path as a segmented route.
     *
     * @param dimension dimension containing the path
     * @param path ordered rail path
     * @return single-segment rail route
     */
    public static TrackRoute singleDimension(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
    {
        return new TrackRoute(List.of(Segment.rail(dimension, path)));
    }

    /**
     * @return immutable route segments in travel order
     */
    public List<Segment> segments()
    {
        return segments;
    }

    /**
     * @return total travel distance across all route segments
     */
    public int totalDistance()
    {
        int total = 0;
        for (Segment segment : segments)
        {
            total += segment.distance();
        }
        return total;
    }

    /**
     * @return first rail path in the route, used for legacy connection-result compatibility
     */
    public List<BlockPos> firstRailPath()
    {
        for (Segment segment : segments)
        {
            if (segment.type() == SegmentType.RAIL && segment.path() != null && !segment.path().isEmpty())
            {
                return segment.path();
            }
        }
        return List.of();
    }

    /**
     * Returns the first non-handoff traversal path for connection-result compatibility.
     *
     * @return first non-empty rail, road, or water path, or an empty list when the route has no traversable segment
     */
    public List<BlockPos> firstPath()
    {
        for (Segment segment : segments)
        {
            if (segment.type() != SegmentType.TRANSFER && segment.type() != SegmentType.DOCK &&
                segment.type() != SegmentType.INTERCHANGE && segment.path() != null && !segment.path().isEmpty()) return segment.path();
        }
        return List.of();
    }

    /**
     * Creates a route suitable for return shipments by reversing segment order and transfer direction.
     *
     * @return reversed route
     */
    @SuppressWarnings("null")
    public TrackRoute reversed()
    {
        List<Segment> reversed = new ArrayList<>();
        for (int i = segments.size() - 1; i >= 0; i--)
        {
            Segment segment = segments.get(i);
            if (segment.type() == SegmentType.TRANSFER)
            {
                reversed.add(Segment.transfer(segment.transferTo(), segment.transferFrom()));
            }
            else if (segment.type() == SegmentType.DOCK)
            {
                reversed.add(Segment.dock(segment.dimension(), segment.path().getFirst()));
            }
            else if (segment.type() == SegmentType.INTERCHANGE)
            {
                reversed.add(Segment.interchange(segment.dimension(), segment.path().getFirst()));
            }
            else
            {
                List<BlockPos> path = new ArrayList<>(segment.path());
                Collections.reverse(path);
                reversed.add(Segment.traversable(segment.type(), segment.dimension(), path));
            }
        }
        return new TrackRoute(reversed);
    }
}
