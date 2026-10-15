package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.items.datacomponent.NavigationChartRecord;
import com.deathfrog.mctradepost.core.blocks.BlockTradeDock;
import com.deathfrog.mctradepost.item.NavigationChartItem;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Schedules bounded, multi-tick searches that create persistent water links between Trade Docks.
 * <p>
 * Limits on concurrency, search size, chunk loading, and per-tick expansion prevent Navigation Chart use from monopolizing the
 * server thread. Each player may have at most one active job.
 */
@EventBusSubscriber(modid = MCTradePostMod.MODID)
public final class NavigationChartingManager
{
    private static final String NO_ROUTE_MESSAGE_KEY = "item.mctradepost.navigation_chart.no_route";

    private static final int MAX_CONCURRENT = 2;
    private static final int NODES_PER_TICK = 256;
    private static final int MAX_VISITED = 200_000;
    private static final int MAX_CHUNKS = 128;
    private static final int MAX_LINKS_PER_DOCK = 32;
    private static final long COOLDOWN_TICKS = 100;
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private NavigationChartingManager()
    {}

    /**
     * Resolves the player-facing name of a dock endpoint.
     *
     * @param level level containing the dock
     * @param pos dock position
     * @return the containing colony's name, or the localized wilderness label when outside a colony
     */
    public static String endpointName(ServerLevel level, BlockPos pos)
    {
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        return colony == null ? Component.translatable("item.mctradepost.navigation_chart.wilderness").getString() : colony.getName();
    }

    /**
     * Validates a requested dock linkage and schedules its incremental water search.
     * <p>
     * The chart is reserved in the supplied hand while the job runs and is consumed only after a complete path is persisted.
     *
     * @param player player requesting the linkage
     * @param hand hand holding the Navigation Chart
     * @param origin origin Trade Dock position
     * @param destination destination Trade Dock position
     * @return {@code true} when the job was accepted, or {@code false} when validation rejected it
     */
    @SuppressWarnings("null")
    public static boolean start(ServerPlayer player, InteractionHand hand, @Nonnull BlockPos origin, @Nonnull BlockPos destination)
    {
        ServerLevel level = player.serverLevel();

        if (JOBS.containsKey(player.getUUID()))
        {
            return fail(player, "item.mctradepost.navigation_chart.already_charting");
        }

        if (JOBS.size() >= MAX_CONCURRENT)
        {
            return fail(player, "item.mctradepost.navigation_chart.server_busy");
        }

        if (COOLDOWNS.getOrDefault(player.getUUID(), 0L) > level.getGameTime())
        {
            return fail(player, "item.mctradepost.navigation_chart.cooldown");
        }

        if (!isDock(level, origin) || !isDock(level, destination))
        {
            return fail(player, "item.mctradepost.navigation_chart.dock_missing");
        }

        if (!mayManage(player, level, origin) || !mayManage(player, level, destination))
        {
            return fail(player, "item.mctradepost.navigation_chart.no_permission");
        }

        WaterRouteRegistry routes = WaterRouteRegistry.get(level);

        if (routes.between(origin, destination).isPresent())
        {
            return fail(player, "item.mctradepost.navigation_chart.duplicate");
        }

        if (routes.linksFor(origin).size() >= MAX_LINKS_PER_DOCK || routes.linksFor(destination).size() >= MAX_LINKS_PER_DOCK)
        {
            return fail(player, "item.mctradepost.navigation_chart.too_many_links");
        }

        BlockPos start = BlockTradeDock.waterEndpoint(level, level.getBlockState(origin), origin);

        BlockPos end = BlockTradeDock.waterEndpoint(level, level.getBlockState(destination), destination);

        int limit = MCTPConfig.maximumWaterRouteDistance.get();

        if (start.distManhattan(end) > limit)
        {
            return fail(player, "item.mctradepost.navigation_chart.too_far");
        }

        JOBS.put(player.getUUID(),
            new Job(player.getUUID(), hand, level, origin.immutable(), destination.immutable(), start, end, limit));

        player.displayClientMessage(Component.translatable("item.mctradepost.navigation_chart.started"), true);

        return true;
    }

    private static boolean mayManage(ServerPlayer player, ServerLevel level, BlockPos pos)
    {
        if (player.hasPermissions(2))
        {
            return true;
        }
        IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
        return colony == null || colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS);
    }

    /**
     * Advances active charting jobs after each server tick and applies a cooldown to terminal jobs.
     *
     * @param event completed server-tick event
     */
    @SuppressWarnings("null")
    private static boolean isDock(ServerLevel level, BlockPos pos)
    {
        return level.getBlockState(pos).is(MCTradePostMod.TRADE_DOCK.get());
    }

    @SuppressWarnings("null")
    private static boolean fail(ServerPlayer player, String key)
    {
        player.displayClientMessage(Component.translatable(key), true);
        return false;
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event)
    {
        Iterator<Job> iterator = JOBS.values().iterator();
        while (iterator.hasNext())
        {
            Job job = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(job.playerId);
            Outcome outcome = job.tick(player);
            if (outcome == Outcome.RUNNING)
            {
                continue;
            }
            iterator.remove();
            COOLDOWNS.put(job.playerId, job.level.getGameTime() + COOLDOWN_TICKS);
        }
    }

    private enum Outcome
    {
        RUNNING, COMPLETE, FAILED
    }

    private record Node(BlockPos pos, int distance, int score)
    {}

    private static final class Job
    {
        private final UUID playerId;
        private final InteractionHand hand;
        private final ServerLevel level;
        private final BlockPos originDock, destinationDock, start, end;
        private final int limit;

        @SuppressWarnings("null")
        private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingInt(Node::score));

        private final Map<BlockPos, BlockPos> parent = new HashMap<>();
        private final Map<BlockPos, Integer> best = new HashMap<>();
        private final Set<BlockPos> visited = new HashSet<>();
        private final Set<Long> loadedChunks = new HashSet<>();
        private java.util.concurrent.CompletableFuture<?> pendingChunk;

        @SuppressWarnings("null")
        private Job(UUID playerId,
            @Nonnull InteractionHand hand,
            ServerLevel level,
            BlockPos originDock,
            BlockPos destinationDock,
            BlockPos start,
            @Nonnull BlockPos end,
            int limit)
        {
            this.playerId = playerId;
            this.hand = hand;
            this.level = level;
            this.originDock = originDock;
            this.destinationDock = destinationDock;
            this.start = start.immutable();
            this.end = end.immutable();
            this.limit = limit;
            best.put(this.start, 0);
            open.add(new Node(this.start, 0, this.start.distManhattan(this.end)));
        }

        /**
         * Advances this search by one bounded unit of work.
         * <p>
         * The main steps are: revalidate the owner, chart, and docks; await an outstanding chunk request; expand up to the per-tick
         * A* node budget; then persist a completed route or report a terminal failure. Chunk waits and budget exhaustion yield until
         * the next server tick.
         *
         * @param player player that owns the job, or {@code null} when that player is offline
         * @return whether the job remains active, completed successfully, or failed
         */
        @SuppressWarnings("null")
        private Outcome tick(ServerPlayer player)
        {
            if (player == null || player.serverLevel() != level ||
                !hasReservedChart(player) ||
                !isDock(level, originDock) ||
                !isDock(level, destinationDock))
            {
                return finish(player, false, "item.mctradepost.navigation_chart.cancelled");
            }

            if (pendingChunk != null)
            {
                if (!pendingChunk.isDone())
                {
                    return Outcome.RUNNING;
                }
                if (pendingChunk.isCompletedExceptionally())
                {
                    return finish(player, false, NO_ROUTE_MESSAGE_KEY);
                }
                pendingChunk = null;
            }
            
            int expanded = 0;

            while (!open.isEmpty() && expanded < NODES_PER_TICK && visited.size() < MAX_VISITED)
            {
                Node node = open.remove();

                if (!visited.add(node.pos()) || node.distance() > limit)
                {
                    continue;
                }

                expanded++;

                if (node.pos().equals(end) || node.pos().distManhattan(end) == 1)
                {
                    List<BlockPos> path = rebuild(node.pos());
                    WaterRouteRegistry.get(level).add(originDock, destinationDock, path, level.getGameTime());
                    player.getItemInHand(hand).shrink(1);
                    player.displayClientMessage(Component.translatable("item.mctradepost.navigation_chart.complete", path.size() - 1),
                        false);
                    return Outcome.COMPLETE;
                }

                for (Direction direction : Direction.Plane.HORIZONTAL)
                {
                    if (direction == null)
                    {
                        continue;
                    }

                    BlockPos next = node.pos().relative(direction);

                    if (next == null)
                    {
                        continue;
                    }

                    int distance = node.distance() + 1;

                    if (distance > limit || visited.contains(next))
                    {
                        continue;
                    }

                    if (!level.isLoaded(next))
                    {
                        long chunk = net.minecraft.world.level.ChunkPos.asLong(next);
                        if (!loadedChunks.contains(chunk))
                        {
                            if (loadedChunks.size() >= MAX_CHUNKS)
                            {
                                continue;
                            }
                            pendingChunk = level.getChunkSource()
                                .getChunkFuture(next.getX() >> 4,
                                    next.getZ() >> 4,
                                    net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                                    true);
                            loadedChunks.add(chunk);
                            visited.remove(node.pos());
                            open.add(node);
                            return Outcome.RUNNING;
                        }
                    }
                    boolean navigable = next.equals(end) || ModalPathConnection.isNavigableWaterPosition(level, next);

                    if (!navigable || distance >= best.getOrDefault(next, Integer.MAX_VALUE))
                    {
                        continue;
                    }
                    BlockPos immutable = next.immutable();

                    best.put(immutable, distance);

                    parent.put(immutable, node.pos());

                    open.add(new Node(immutable, distance, distance + immutable.distManhattan(end)));
                }
            }

            if (open.isEmpty() || visited.size() >= MAX_VISITED || loadedChunks.size() >= MAX_CHUNKS)
            {
                return finish(player, false, NO_ROUTE_MESSAGE_KEY);
            }

            if (level.getGameTime() % 20 == 0)
            {
                player.displayClientMessage(Component.translatable("item.mctradepost.navigation_chart.progress", visited.size()), true);
            }
            
            return Outcome.RUNNING;
        }

        @SuppressWarnings("null")
        private boolean hasReservedChart(@Nonnull ServerPlayer player)
        {
            ItemStack stack = player.getItemInHand(hand);

            if (!stack.is(MCTradePostMod.NAVIGATION_CHART.get()))
            {
                return false;
            }

            NavigationChartRecord record = NavigationChartItem.record(stack);
            
            return record.origin().isPresent() && record.origin().get().dimension().equals(level.dimension()) &&
                record.origin().get().pos().equals(originDock);
        }

        private List<BlockPos> rebuild(BlockPos current)
        {
            List<BlockPos> path = new ArrayList<>();
            for (BlockPos cursor = current; cursor != null && !cursor.equals(start); cursor = parent.get(cursor))
            {
                path.add(cursor);
            }
            path.add(start);
            Collections.reverse(path);
            if (!path.getLast().equals(end))
            {
                path.add(end);
            }
            return List.copyOf(path);
        }

        @SuppressWarnings("null")
        private Outcome finish(ServerPlayer player, boolean success, @Nonnull String key)
        {
            if (player != null)
            {
                player.displayClientMessage(Component.translatable(key), false);
            }
            return success ? Outcome.COMPLETE : Outcome.FAILED;
        }
    }
}
