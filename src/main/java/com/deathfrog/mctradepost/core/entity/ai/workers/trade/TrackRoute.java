package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

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
        /** A visible airship path near a colony endpoint. */
        AIR,
        /** An unanimated, distance-bearing flight between colony borders. */
        AIR_TRANSIT,
        /** A zero-distance vehicle handoff at a trade dock. */
        DOCK,
        /** A zero-distance handoff between a local route and an air route. */
        MOORING,
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
            if (type == SegmentType.TRANSFER || type == SegmentType.DOCK || type == SegmentType.MOORING ||
                type == SegmentType.INTERCHANGE || type == SegmentType.AIR_TRANSIT)
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
         * Creates a zero-distance handoff at a Mooring Bay.
         *
         * @param dimension dimension containing the Bay
         * @param position Mooring Bay position
         * @return Mooring Bay handoff segment
         */
        @SuppressWarnings("null")
        public static Segment mooring(@Nonnull ResourceKey<Level> dimension, @Nonnull BlockPos position)
        {
            return new Segment(SegmentType.MOORING, dimension, List.of(position), null, null);
        }

        /**
         * Creates a visible airship path near a colony.
         *
         * @param dimension dimension containing the path
         * @param path ordered airship positions
         * @return visible air segment
         */
        public static Segment air(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            return traversable(SegmentType.AIR, dimension, path);
        }

        /**
         * Creates an unanimated air leg whose distance is the horizontal Euclidean distance between its endpoints.
         *
         * @param dimension dimension containing both colonies
         * @param from origin colony border position
         * @param to destination colony border position
         * @return abstract air-transit segment
         */
        @SuppressWarnings("null")
        public static Segment airTransit(@Nonnull ResourceKey<Level> dimension, @Nonnull BlockPos from, @Nonnull BlockPos to)
        {
            return new Segment(SegmentType.AIR_TRANSIT, dimension, List.of(from.immutable(), to.immutable()), null, null);
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
            if (type == SegmentType.AIR_TRANSIT)
            {
                if (path.size() < 2) return 0;
                long dx = (long) path.getLast().getX() - path.getFirst().getX();
                long dz = (long) path.getLast().getZ() - path.getFirst().getZ();
                return Math.max(1, (int) Math.ceil(Math.sqrt((double) dx * dx + (double) dz * dz)));
            }
            if (type == SegmentType.DOCK || type == SegmentType.MOORING || type == SegmentType.INTERCHANGE)
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
     * Returns the distinct shipment transport modes in the order they are first used by this route.
     * Infrastructure and handoff segments are omitted, and AIR_TRANSIT is represented as AIR.
     *
     * @return ordered, immutable transport-mode list
     */
    public List<SegmentType> transportModes()
    {
        Set<SegmentType> modes = new LinkedHashSet<>();
        for (Segment segment : segments)
        {
            SegmentType type = segment.type() == SegmentType.AIR_TRANSIT ? SegmentType.AIR : segment.type();
            if (type == SegmentType.RAIL || type == SegmentType.ROAD ||
                type == SegmentType.WATER || type == SegmentType.AIR)
            {
                modes.add(type);
            }
        }
        return List.copyOf(modes);
    }

    /**
     * Advances a physical route position by a base movement budget, applying the supplied speed factor independently to each segment.
     * Route distances remain physical distances, so this has no effect on route selection or progress-bar totals.
     *
     * @param startDistance current physical distance along the route
     * @param movementBudget movement available at a 1x segment speed
     * @param speedMultiplier speed factor for each segment type
     * @return the new physical route distance, clamped to the route length
     */
    public int advanceDistance(int startDistance, double movementBudget, ToDoubleFunction<SegmentType> speedMultiplier)
    {
        int position = Math.max(0, startDistance);
        double remainingBudget = Math.max(0.0D, movementBudget);
        int cursor = 0;

        for (Segment segment : segments)
        {
            int segmentDistance = segment.distance();
            int segmentEnd = cursor + segmentDistance;
            if (segmentDistance == 0 || position >= segmentEnd)
            {
                cursor = segmentEnd;
                continue;
            }

            position = Math.max(position, cursor);
            int remainingSegmentDistance = segmentEnd - position;
            double multiplier = Math.max(0.000001D, speedMultiplier.applyAsDouble(segment.type()));
            int possibleAdvance = (int) Math.floor((remainingBudget * multiplier) + 1.0E-9D);
            if (possibleAdvance < remainingSegmentDistance)
            {
                return position + possibleAdvance;
            }

            position = segmentEnd;
            remainingBudget -= remainingSegmentDistance / multiplier;
            cursor = segmentEnd;
        }

        return Math.min(position, totalDistance());
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
            if (segment.type() != SegmentType.TRANSFER && segment.type() != SegmentType.AIR_TRANSIT &&
                segment.type() != SegmentType.DOCK && segment.type() != SegmentType.MOORING &&
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
            else if (segment.type() == SegmentType.MOORING)
            {
                reversed.add(Segment.mooring(segment.dimension(), segment.path().getFirst()));
            }
            else if (segment.type() == SegmentType.AIR_TRANSIT)
            {
                reversed.add(Segment.airTransit(segment.dimension(), segment.path().getLast(), segment.path().getFirst()));
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
