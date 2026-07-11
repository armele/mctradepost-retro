package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Dimension-aware route made of rail segments and dimensional transfer hops.
 * <p>
 * A route is intentionally segmented so shipment progress and ghost-cart rendering can move along normal rails, disappear during a
 * dimensional transfer, and reappear in the next dimension with the correct local path.
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
     * @param path rail positions for rail segments, or the two endpoint positions for transfer segments
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
        @SuppressWarnings("null")
        public static Segment rail(@Nonnull ResourceKey<Level> dimension, @Nonnull List<BlockPos> path)
        {
            return new Segment(SegmentType.RAIL, dimension, Collections.unmodifiableList(new ArrayList<>(path)), null, null);
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
         * @return travel distance represented by this segment
         */
        public int distance()
        {
            if (type == SegmentType.TRANSFER)
            {
                return 1;
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
            else
            {
                List<BlockPos> path = new ArrayList<>(segment.path());
                Collections.reverse(path);
                reversed.add(Segment.rail(segment.dimension(), path));
            }
        }
        return new TrackRoute(reversed);
    }
}
