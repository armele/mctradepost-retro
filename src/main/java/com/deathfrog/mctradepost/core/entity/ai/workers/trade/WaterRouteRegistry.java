package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.core.blocks.BlockTradeDock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Stores persistent, undirected Trade Dock linkages and their player-surveyed water paths.
 * <p>
 * Links are saved with the level and keyed independently of travel direction. Validation examines only loaded positions, records
 * actionable break coordinates, and never searches for or substitutes a different route.
 */
public final class WaterRouteRegistry extends SavedData
{
    private static final String DATA_NAME = MCTradePostMod.MODID + "_water_routes";
    private static final String TAG_LINKS = "links";
    private static final String TAG_FIRST_DOCK = "first";
    private static final String TAG_SECOND_DOCK = "second";
    private static final String TAG_PATH = "path";
    private static final String TAG_STATUS = "status";
    private static final String TAG_VALIDATED = "validated";
    private static final String TAG_BREAK_START = "breakStart";
    private static final String TAG_BREAK_END = "breakEnd";
    private final Map<LinkKey, WaterLink> links = new HashMap<>();
    private static final @Nonnull Factory<WaterRouteRegistry> FACTORY =
        new Factory<>(WaterRouteRegistry::new, WaterRouteRegistry::load);

    /** Describes whether a surveyed link can currently participate in trade routing. */
    public enum Status
    {
        OPERATIONAL,
        PARTIALLY_UNVERIFIED,
        BROKEN,
        DESTINATION_MISSING,
        OVER_LIMIT
    }

    /**
     * Immutable persisted state for one bidirectional dock linkage.
     *
     * @param firstDock first dock endpoint in persisted path order
     * @param secondDock second dock endpoint in persisted path order
     * @param path ordered, endpoint-inclusive water path from {@code firstDock} to {@code secondDock}
     * @param status most recently determined validation status
     * @param lastValidated game time of the most recent completed validation
     * @param breakStart last known navigable position before a break, or {@code null} when no break is known
     * @param breakEnd first known navigable position after a break, or {@code null} when no break is known
     */
    public record WaterLink(BlockPos firstDock, BlockPos secondDock, List<BlockPos> path, Status status,
        long lastValidated, BlockPos breakStart, BlockPos breakEnd)
    {
        public WaterLink
        {
            firstDock = firstDock.immutable();
            secondDock = secondDock.immutable();
            path = List.copyOf(path);
        }

        /** @return number of block-to-block steps in the surveyed path */
        public int distance()
        {
            return Math.max(0, path.size() - 1);
        }

        /**
         * Returns the endpoint opposite a participating dock.
         *
         * @param dock either endpoint of this link
         * @return the other dock endpoint
         */
        public BlockPos other(BlockPos dock)
        {
            return firstDock.equals(dock) ? secondDock : firstDock;
        }

        /**
         * Tests whether a dock is an endpoint of this link.
         *
         * @param dock dock position to test
         * @return {@code true} when the dock is either endpoint
         */
        public boolean includes(BlockPos dock)
        {
            return firstDock.equals(dock) || secondDock.equals(dock);
        }

        /** @return whether the status and configured distance limit permit this link to be routed */
        public boolean usable()
        {
            return (status == Status.OPERATIONAL || status == Status.PARTIALLY_UNVERIFIED) &&
                distance() <= MCTPConfig.maximumWaterRouteDistance.get();
        }
        /**
         * Returns the persisted path ordered away from the supplied endpoint.
         *
         * @param dock endpoint from which travel begins; callers must supply a participating dock
         * @return immutable path ordered from {@code dock} toward the opposite endpoint
         */
        public List<BlockPos> pathFrom(BlockPos dock)
        {
            if (firstDock.equals(dock))
            {
                return path;
            }
            List<BlockPos> reversed = new ArrayList<>(path);
            Collections.reverse(reversed);
            return List.copyOf(reversed);
        }
    }

    private record LinkKey(long first, long second)
    {
        static LinkKey of(BlockPos a, BlockPos b)
        {
            long av = a.asLong(), bv = b.asLong();
            return Long.compareUnsigned(av, bv) <= 0 ? new LinkKey(av, bv) : new LinkKey(bv, av);
        }
    }

    /**
     * Gets the persistent water-route registry for a server level.
     *
     * @param level level whose saved data should be accessed
     * @return existing registry or a newly created empty registry
     */
    public static WaterRouteRegistry get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Looks up the undirected link between two docks.
     *
     * @param first one dock endpoint
     * @param second other dock endpoint
     * @return matching link, or an empty optional when the docks are not linked
     */
    public Optional<WaterLink> between(BlockPos first, BlockPos second)
    {
        return Optional.ofNullable(links.get(LinkKey.of(first, second)));
    }

    /**
     * Lists all links involving a dock in stable opposite-endpoint order.
     *
     * @param dock dock whose links should be returned
     * @return immutable list of participating links
     */
    public List<WaterLink> linksFor(BlockPos dock)
    {
        return links.values().stream().filter(link -> link.includes(dock))
            .sorted(Comparator.comparingLong(link -> link.other(dock).asLong())).toList();
    }

    /**
     * Finds a link whose stored path matches the supplied path in either direction.
     *
     * @param path endpoint-inclusive path to match; may be {@code null}
     * @return matching link, or an empty optional for a null, empty, or unknown path
     */
    public Optional<WaterLink> matchingPath(List<BlockPos> path)
    {
        if (path == null || path.isEmpty())
        {
            return Optional.empty();
        }
        for (WaterLink link : links.values())
        {
            if (link.path().equals(path))
            {
                return Optional.of(link);
            }
            if (link.path().size() == path.size())
            {
                boolean reverse = true;
                for (int i = 0; i < path.size(); i++)
                {
                    if (!path.get(i).equals(link.path().get(path.size() - 1 - i)))
                    {
                        reverse = false;
                        break;
                    }
                }
                if (reverse)
                {
                    return Optional.of(link);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Adds or replaces the undirected linkage between two docks and marks the registry dirty.
     *
     * @param firstDock first dock endpoint
     * @param secondDock second dock endpoint
     * @param path ordered, endpoint-inclusive path from {@code firstDock} to {@code secondDock}
     * @param now current game time used as the initial validation timestamp
     */
    public void add(BlockPos firstDock, BlockPos secondDock, List<BlockPos> path, long now)
    {
        links.put(LinkKey.of(firstDock, secondDock),
            new WaterLink(firstDock, secondDock, path, Status.OPERATIONAL, now, null, null));
        setDirty();
    }

    /**
     * Removes every linkage involving a dock, such as when its block is removed.
     *
     * @param dock removed dock position
     */
    public void removeDock(BlockPos dock)
    {
        if (links.entrySet().removeIf(entry -> entry.getValue().includes(dock)))
        {
            setDirty();
        }
    }

    /**
     * Revalidates a persisted link using loaded positions only.
     * <p>
     * This operation never loads chunks and never searches for a replacement path. Unloaded interior positions produce a partially
     * unverified status. A changed result replaces the stored link and marks the registry dirty.
     *
     * @param level level containing the link
     * @param link persisted link to validate
     * @return the updated link, or the original instance when a known break cannot yet be rechecked
     */
    @SuppressWarnings("null")
    public WaterLink validate(ServerLevel level, WaterLink link)
    {
        Status status = link.status();
        BlockPos breakStart = null, breakEnd = null;
        boolean firstLoaded = level.isLoaded(link.firstDock());
        boolean secondLoaded = level.isLoaded(link.secondDock());
        if ((firstLoaded && !level.getBlockState(link.firstDock()).is(MCTradePostMod.TRADE_DOCK.get())) ||
            (secondLoaded && !level.getBlockState(link.secondDock()).is(MCTradePostMod.TRADE_DOCK.get())))
        {
            status = Status.DESTINATION_MISSING;
        }
        else if (link.distance() > MCTPConfig.maximumWaterRouteDistance.get())
        {
            status = Status.OVER_LIMIT;
        }
        else if (link.path().isEmpty() || (firstLoaded && secondLoaded &&
            (!BlockTradeDock.waterEndpoint(level, level.getBlockState(link.firstDock()), link.firstDock()).equals(link.path().getFirst()) ||
             !BlockTradeDock.waterEndpoint(level, level.getBlockState(link.secondDock()), link.secondDock()).equals(link.path().getLast()))))
        {
            status = Status.BROKEN;
            breakStart = link.path().isEmpty() ? link.firstDock() : link.path().getFirst();
            breakEnd = link.path().isEmpty() ? link.secondDock() : link.path().getLast();
        }
        else
        {
            if (link.status() == Status.BROKEN && link.breakStart() != null &&
                (!level.isLoaded(link.breakStart()) || (link.breakEnd() != null && !level.isLoaded(link.breakEnd()))))
            {
                return link;
            }
            status = Status.OPERATIONAL;
            boolean unloaded = false;
            for (int i = 1; i < link.path().size() - 1; i++)
            {
                BlockPos pos = link.path().get(i);

                if (pos == null) continue;

                if (!level.isLoaded(pos))
                {
                    unloaded = true;
                    continue;
                }

                boolean valid = ModalPathConnection.isNavigableWaterPosition(level, pos);
                
                if (!valid)
                {
                    status = Status.BROKEN;
                    breakStart = link.path().get(Math.max(0, i - 1));
                    int end = i;
                    while (end + 1 < link.path().size() - 1 && level.isLoaded(link.path().get(end + 1)))
                    {
                        BlockPos next = link.path().get(end + 1);
                        if (ModalPathConnection.isNavigableWaterPosition(level, next))
                        {
                            break;
                        }
                        end++;
                    }

                    breakEnd = link.path().get(Math.min(link.path().size() - 1, end + 1));
                    
                    break;
                }
            }
            
            if (status == Status.OPERATIONAL && unloaded)
            {
                status = Status.PARTIALLY_UNVERIFIED;
            }
        }
        WaterLink updated = new WaterLink(link.firstDock(), link.secondDock(), link.path(), status,
            level.getGameTime(), breakStart, breakEnd);
        if (!updated.equals(link))
        {
            links.put(LinkKey.of(link.firstDock(), link.secondDock()), updated);
            setDirty();
        }
        return updated;
    }

    private static WaterRouteRegistry load(CompoundTag tag, HolderLookup.Provider provider)
    {
        WaterRouteRegistry registry = new WaterRouteRegistry();
        ListTag list = tag.getList(TAG_LINKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            BlockPos first = BlockPos.of(entry.getLong(TAG_FIRST_DOCK));
            BlockPos second = BlockPos.of(entry.getLong(TAG_SECOND_DOCK));
            List<BlockPos> path = new ArrayList<>();
            long[] packed = entry.getLongArray(TAG_PATH);
            for (long value : packed)
            {
                path.add(BlockPos.of(value));
            }
            Status status;
            try
            {
                status = Status.valueOf(entry.getString(TAG_STATUS));
            }
            catch (IllegalArgumentException ignored)
            {
                status = Status.BROKEN;
            }
            BlockPos breakStart = entry.contains(TAG_BREAK_START) ? BlockPos.of(entry.getLong(TAG_BREAK_START)) : null;
            BlockPos breakEnd = entry.contains(TAG_BREAK_END) ? BlockPos.of(entry.getLong(TAG_BREAK_END)) : null;
            if (!path.isEmpty())
            {
                registry.links.put(LinkKey.of(first, second),
                    new WaterLink(first, second, path, status, entry.getLong(TAG_VALIDATED), breakStart, breakEnd));
            }
        }
        return registry;
    }

    /**
     * Serializes all registered links into the level's saved-data tag.
     *
     * @param tag destination tag
     * @param provider registry lookup provider supplied by the saved-data system
     * @return the populated destination tag
     */
    @SuppressWarnings("null")
    @Override
    public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider)
    {
        ListTag list = new ListTag();
        
        for (WaterLink link : links.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_FIRST_DOCK, link.firstDock().asLong());
            entry.putLong(TAG_SECOND_DOCK, link.secondDock().asLong());
            entry.putLongArray(TAG_PATH, link.path().stream().mapToLong(BlockPos::asLong).toArray());
            entry.putString(TAG_STATUS, link.status().name());
            entry.putLong(TAG_VALIDATED, link.lastValidated());

            if (link.breakStart() != null)
            {
                entry.putLong(TAG_BREAK_START, link.breakStart().asLong());
            }

            if (link.breakEnd() != null)
            {
                entry.putLong(TAG_BREAK_END, link.breakEnd().asLong());
            }

            list.add(entry);
        }

        tag.put(TAG_LINKS, list);
        return tag;
    }
}
