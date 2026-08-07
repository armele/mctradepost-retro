package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_CART;

import java.util.List;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.entity.GhostBoatEntity;
import com.deathfrog.mctradepost.api.entity.WagonEntity;
import com.deathfrog.mctradepost.api.util.ChunkUtil;
import com.deathfrog.mctradepost.api.util.ItemHandlerHelpers;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.google.common.collect.ImmutableList;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.util.DomumOrnamentumUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class ExportData
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String TAG_COST = "cost";
    public static final String TAG_QUANTITY = "quantity";

    public record TradeDefinition(ItemStorage tradeItem, int price, int quantity)
    {
    };

    private final @Nullable ITradeCapable sourceStation;
    private final StationData destinationStationData;
    private final ItemStorage tradeItem;
    private final int cost;

    // By default, ship infinitely. If set >0, the export will only be shipped shipmentCountdown number of times.
    private int shipmentCountdown = -1;
    protected boolean reverse = false;
    private int shipDistance = -1;
    private int trackDistance = -1;
    private int lastShipDay = -1;
    private boolean insufficientFunds = false;
    private IToken<?> requestToken = null;
    private GhostCartEntity cart = null;
    private TrackRoute activeRoute = null;
    private int activeRouteSegmentIndex = -1;
    private final Deque<VisualLeg> pendingVisualLegs = new ArrayDeque<>();
    private long nextVisualTransitionTick = Long.MAX_VALUE;

    private record VisualLeg(int segmentIndex, TrackRoute.Segment segment, int targetIndex, int durationTicks) { }



    public ExportData(@Nullable ITradeCapable sourceStation, StationData destinationStationData, ItemStorage tradeItem, int cost, boolean reverse)
    {
        this.sourceStation = sourceStation;
        this.destinationStationData = destinationStationData;
        this.tradeItem = tradeItem;
        this.cost = cost;
        this.shipDistance = -1;
        this.trackDistance = -1;
        this.lastShipDay = -1;
        this.shipmentCountdown = -1;
        this.insufficientFunds = false;
        this.reverse = reverse;
    }

    public ExportData(ITradeCapable sourceStation, StationData destinationStationData, ItemStorage tradeItem, int cost)
    {
        this(sourceStation, destinationStationData, tradeItem, cost, false);
    }

    public StationData getDestinationStationData()
    {
        return destinationStationData;
    }

    public @Nullable ITradeCapable getSourceStation()
    {
        return sourceStation;
    }

    public int getCost()
    {
        return cost;
    }

    public int getQuantity()
    {
        return tradeItem.getAmount();
    }

    public int getShipDistance()
    {
        return shipDistance;
    }

    public int getShipmentCountdown()
    {
        return shipmentCountdown;
    }

    public void setShipmentCountdown(int shipmentCountdown) 
    { 
        this.shipmentCountdown = shipmentCountdown; 
    }

    public boolean isReverse() 
    { 
        return reverse; 
    } 

    public void setRequestToken(IToken<?> requestToken) 
    { 
        this.requestToken = requestToken; 
    }

    public IToken<?> getRequestToken() 
    {
        return requestToken;
    }

    /**
     * Spawns a GhostCartEntity for trade if one does not already exist. The cart is initialized with the current export's trade item
     * and set on this export data.
     *
     * @param path the path of block positions that the cart should follow when spawned
     * @return the spawned GhostCartEntity, or the existing one if already present
     */
    public @Nullable GhostCartEntity spawnCartForTrade(List<BlockPos> path)
    {
        ServerLevel level = (ServerLevel) this.getDestinationStationData().getStation().getColony().getWorld();
        return spawnCartForTrade(level, path);
    }

    public @Nullable GhostCartEntity spawnCartForTrade(ServerLevel level, List<BlockPos> path)
    {
        return spawnVehicleForTrade(level, path, TrackRoute.SegmentType.RAIL, isReverse());
    }

    private @Nullable GhostCartEntity spawnVehicleForTrade(ServerLevel level, List<BlockPos> path, TrackRoute.SegmentType mode, boolean reverse)
    {
        if (path == null || path.isEmpty()) 
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Null or empty path while spawning cart: {}", this));
            return null;
        }

        if (level == null)
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Null level while spawning cart: {}", this));
            return null;
        }

        BlockPos startPos = path.getFirst();

        if (startPos == null || startPos.equals(BlockPos.ZERO)) 
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Null or ZERO start position for path while spawning cart: {}", this));
            return null;
        }

        ChunkUtil.ensureChunkLoadedByTicket(level, startPos, TrackPathConnection.RAIL_CHUNK_RADIUS, ChunkUtil.RAIL_TICKET);

        List<BlockPos> immutablePath = NullnessBridge.assumeNonnull(ImmutableList.copyOf(path));
        GhostCartEntity newCart = switch (mode)
        {
            case WATER -> GhostCartEntity.spawnTyped(level, immutablePath, reverse, MCTradePostMod.GHOST_BOAT.get());
            case ROAD -> GhostCartEntity.spawnTyped(level, immutablePath, reverse, MCTradePostMod.WAGON.get());
            default -> GhostCartEntity.spawn(level, immutablePath, reverse);
        };

        if (newCart == null)
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Null cart spawned from GhostCartEntity.spawn(): {}", this));
            return null;
        }

        ItemStack tradeItem = this.getTradeItem().getItemStack().copy();

        if (tradeItem.isEmpty()) 
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Empty trade item while spawning cart: {}", this));
            return null;
        }

        newCart.setTradeItem(tradeItem);
        this.setCart(newCart);

        ChunkUtil.releaseChunkTicket(level, startPos, TrackPathConnection.RAIL_CHUNK_RADIUS, ChunkUtil.RAIL_TICKET);

        return newCart;
    }

    /**
     * Spawns or updates the ghost cart for a segmented, dimension-aware route.
     * <p>
     * Return shipments use the reversed route so progress moves from destination back to source.
     *
     * @param route route to visualize
     * @return active ghost cart, or null when the current route segment is a transfer or cannot spawn
     */
    public @Nullable GhostCartEntity spawnCartForTrade(TrackRoute route)
    {
        if (route == null)
        {
            return null;
        }

        this.activeRoute = isReverse() ? route.reversed() : route;
        updateCartForRouteDistance(this.shipDistance < 0 ? 0 : this.shipDistance);
        return cart;
    }

    /**
     * Spawns a GhostCartEntity for trade if one does not already exist. The cart is initialized with the current export's trade item
     * and set on this export data.
     *
     * @return the spawned GhostCartEntity, or the existing one if already present.
     */
    public GhostCartEntity spawnCartForTrade()
    {
        if (this.cart != null && this.cart.hasPath()) return cart;

        TrackConnectionResult tcr = null;
        
        if (!isReverse())
        {
            if (sourceStation == null) return null;

            tcr = sourceStation.getTrackConnectionResult(this.getDestinationStationData());
        }
        else
        {
            StationData returningLocation = new StationData(this.getSourceStation());
            ITradeCapable destinationStation = this.getDestinationStationData().getStation();

            if (destinationStation != null)
            {
                tcr = destinationStationData.getStation().getTrackConnectionResult(returningLocation);
            }
        }

        if (tcr != null && tcr.getRoute() != null)
        {
            spawnCartForTrade(tcr.getRoute());
        }
        else if (tcr != null && tcr.path != null && !tcr.path.isEmpty())
        {
            if (this.cart == null)
            {
                this.cart = spawnCartForTrade(tcr.path);
                TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Spawning cart for trade: {}", this));
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Setting path for existing cart: {}", this));
                this.cart.setPath(tcr.path, isReverse());
            }
        }
        else
        { 
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Deferring cart spawn for trade - no path information for export: {}", this));
            return null;
        }

        return cart;
    }

    /**
     * Sets the ship distance for this export and updates the visualization of the ghost cart.
     * <p>
     * Negative distances clear route/cart state. Segmented dimensional routes map this global distance into the current rail segment.
     * 
     * @param shipDistance the new ship distance.
     */
    public void setShipDistance(int shipDistance)
    {
        int previousDistance = this.shipDistance;
        this.shipDistance = shipDistance;

        if (shipDistance < 0)
        {
            discardCart();
            activeRoute = null;
            activeRouteSegmentIndex = -1;
            pendingVisualLegs.clear();
            TradeRouteVisualTicker.deactivate(this);
            return;
        }

        if (activeRoute != null)
        {
            if (previousDistance >= 0 && shipDistance > previousDistance)
            {
                beginVisualStride(previousDistance, shipDistance);
            }
            else
            {
                updateCartForRouteDistance(shipDistance);
            }
            return;
        }

        if (cart != null && shipDistance >= 0)
        {
            cart.setSegment(shipDistance);
        }
    }

    /** Splits one colony-tick movement request across every modal segment it crosses. */
    private void beginVisualStride(int fromDistance, int toDistance)
    {
        pendingVisualLegs.clear();
        int target = Math.min(toDistance, activeRoute.totalDistance());
        int travelled = Math.max(1, target - fromDistance);
        int cursor = 0;
        List<VisualLegDraft> drafts = new ArrayList<>();
        List<TrackRoute.Segment> routeSegments = activeRoute.segments();
        for (int segmentIndex = 0; segmentIndex < routeSegments.size(); segmentIndex++)
        {
            TrackRoute.Segment segment = routeSegments.get(segmentIndex);
            int distance = segment.distance();
            if (distance == 0) continue;
            int overlapStart = Math.max(fromDistance, cursor);
            int overlapEnd = Math.min(target, cursor + distance);
            if (overlapEnd > overlapStart)
            {
                int amount = overlapEnd - overlapStart;
                int localTarget = segment.type() == TrackRoute.SegmentType.TRANSFER
                    ? 0 : Math.min(segment.path().size() - 1, overlapEnd - cursor);
                drafts.add(new VisualLegDraft(segmentIndex, segment, localTarget, amount));
            }
            cursor += distance;
        }

        int assignedTicks = 0;
        for (int i = 0; i < drafts.size(); i++)
        {
            VisualLegDraft draft = drafts.get(i);
            int duration = i == drafts.size() - 1
                ? Math.max(1, GhostCartEntity.DEFAULT_STRIDE_TICKS - assignedTicks)
                : Math.max(1, Math.round(GhostCartEntity.DEFAULT_STRIDE_TICKS * draft.distance() / (float) travelled));
            assignedTicks += duration;
            pendingVisualLegs.addLast(new VisualLeg(draft.segmentIndex(), draft.segment(), draft.targetIndex(), duration));
        }

        startNextVisualLeg();
        if (!pendingVisualLegs.isEmpty()) TradeRouteVisualTicker.activate(this);
    }

    private record VisualLegDraft(int segmentIndex, TrackRoute.Segment segment, int targetIndex, int distance) { }

    /** Starts the next timed modal leg; zero-distance handoffs are implicit between consecutive legs. */
    private void startNextVisualLeg()
    {
        VisualLeg leg = pendingVisualLegs.pollFirst();
        if (leg == null)
        {
            nextVisualTransitionTick = Long.MAX_VALUE;
            return;
        }

        TrackRoute.Segment segment = leg.segment();
        MinecraftServer server = sourceStation == null || sourceStation.getColony() == null || sourceStation.getColony().getWorld() == null
            ? null : sourceStation.getColony().getWorld().getServer();
        if (server == null) return;

        TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn(
            "Starting trade visual leg index={} mode={} target={} durationTicks={} remainingLegs={}",
            leg.segmentIndex(), segment.type(), leg.targetIndex(), leg.durationTicks(), pendingVisualLegs.size()));

        if (segment.type() == TrackRoute.SegmentType.TRANSFER)
        {
            if (cart != null) cart.playTransferEffects();
            discardCart();
            activeRouteSegmentIndex = leg.segmentIndex();
        }
        else
        {
            ServerLevel level = server.getLevel(segment.dimension());
            if (level == null) return;
            int segmentIndex = leg.segmentIndex();
            if (cart == null || !cart.hasPath() || activeRouteSegmentIndex != segmentIndex || !vehicleMatches(segment.type()))
            {
                emitVehicleHandoff(level, activeRouteSegmentIndex, segmentIndex, segment.type());
                discardCart();
                cart = spawnVehicleForTrade(level, segment.path(), segment.type(), false);
                activeRouteSegmentIndex = segmentIndex;
            }
            if (cart != null) cart.setSegment(leg.targetIndex(), leg.durationTicks());
        }
        nextVisualTransitionTick = server.overworld().getGameTime() + leg.durationTicks();
    }

    /** Emits one restrained, mode-aware particle burst at a dock or interchange vehicle handoff. */
    private void emitVehicleHandoff(ServerLevel level, int previousIndex, int nextIndex, TrackRoute.SegmentType nextType)
    {
        if (activeRoute == null || previousIndex < 0 || previousIndex >= activeRoute.segments().size() || previousIndex == nextIndex)
        {
            return;
        }

        TrackRoute.SegmentType previousType = activeRoute.segments().get(previousIndex).type();
        if (!isVehicleMode(previousType) || !isVehicleMode(nextType) || previousType == nextType)
        {
            return;
        }

        BlockPos handoff = null;
        int step = previousIndex < nextIndex ? 1 : -1;
        for (int index = previousIndex + step; index != nextIndex; index += step)
        {
            TrackRoute.Segment between = activeRoute.segments().get(index);
            if ((between.type() == TrackRoute.SegmentType.DOCK || between.type() == TrackRoute.SegmentType.INTERCHANGE)
                && between.dimension().equals(level.dimension()) && !between.path().isEmpty())
            {
                handoff = between.path().getFirst();
                break;
            }
        }
        if (handoff == null)
        {
            TrackRoute.Segment arriving = activeRoute.segments().get(nextIndex);
            if (arriving.path().isEmpty()) return;
            handoff = arriving.path().getFirst();
        }

        double x = handoff.getX() + 0.5D;
        double y = handoff.getY() + 0.55D;
        double z = handoff.getZ() + 0.5D;
        boolean waterHandoff = previousType == TrackRoute.SegmentType.WATER || nextType == TrackRoute.SegmentType.WATER;
        level.sendParticles(waterHandoff ? ParticleTypes.SPLASH : ParticleTypes.POOF,
            x, y, z, waterHandoff ? 6 : 7, 0.28D, 0.2D, 0.28D, 0.025D);
        level.sendParticles(ParticleTypes.WAX_ON, x, y + 0.1D, z, 4, 0.24D, 0.22D, 0.24D, 0.015D);
    }

    private static boolean isVehicleMode(TrackRoute.SegmentType type)
    {
        return type == TrackRoute.SegmentType.RAIL || type == TrackRoute.SegmentType.ROAD || type == TrackRoute.SegmentType.WATER;
    }

    private boolean vehicleMatches(TrackRoute.SegmentType type)
    {
        return switch (type)
        {
            case ROAD -> cart instanceof WagonEntity;
            case WATER -> cart instanceof GhostBoatEntity;
            case RAIL -> !(cart instanceof WagonEntity) && !(cart instanceof GhostBoatEntity);
            default -> true;
        };
    }

    /** @return true while another server tick is needed for this visual stride */
    boolean tickRouteVisualization()
    {
        if (pendingVisualLegs.isEmpty()) return false;
        MinecraftServer server = sourceStation == null || sourceStation.getColony() == null || sourceStation.getColony().getWorld() == null
            ? null : sourceStation.getColony().getWorld().getServer();
        if (server == null) return false;
        if (server.overworld().getGameTime() >= nextVisualTransitionTick) startNextVisualLeg();
        return !pendingVisualLegs.isEmpty();
    }

    /**
     * Moves the ghost cart to the rail segment matching the supplied global route distance.
     * <p>
     * Transfer segments discard the visible cart after playing transfer effects. The cart will respawn on the next rail segment as
     * progress advances.
     *
     * @param routeDistance distance traveled along the full segmented route
     */
    @SuppressWarnings("unused")
    private void updateCartForRouteDistance(int routeDistance)
    {
        if (activeRoute == null || routeDistance < 0)
        {
            return;
        }

        int cursor = 0;
        List<TrackRoute.Segment> segments = activeRoute.segments();
        for (int i = 0; i < segments.size(); i++)
        {
            TrackRoute.Segment segment = segments.get(i);
            int segmentDistance = segment.distance();
            if (segmentDistance == 0) continue;
            boolean inSegment = routeDistance < cursor + segmentDistance || i == segments.size() - 1;
            if (!inSegment)
            {
                cursor += segmentDistance;
                continue;
            }

            if (segment.type() == TrackRoute.SegmentType.TRANSFER)
            {
                if (cart != null)
                {
                    cart.playTransferEffects();
                }
                discardCart();
                activeRouteSegmentIndex = i;
                return;
            }

            if (segment.path() == null || segment.path().isEmpty())
            {
                return;
            }

            if (sourceStation == null)
            {
                return;
            }

            MinecraftServer stationServer = sourceStation.getColony().getWorld().getServer();
            if (stationServer == null)
            {
                return;
            }

            ServerLevel level = stationServer.getLevel(segment.dimension());
            if (level == null)
            {
                return;
            }

            if (cart == null || !cart.hasPath() || activeRouteSegmentIndex != i)
            {
                discardCart();
                cart = spawnVehicleForTrade(level, segment.path(), segment.type(), false);
                activeRouteSegmentIndex = i;
            }

            if (cart != null)
            {
                int localSegment = Math.max(0, Math.min(segment.path().size() - 1, routeDistance - cursor));
                cart.setSegment(localSegment);
            }
            return;
        }
    }

    /**
     * Removes the active ghost cart entity, if one exists.
     */
    public void discardCart()
    {
        if (cart != null)
        {
            cart.discard();
            cart = null;
        }
    }

    public int getTrackDistance()
    {
        return trackDistance;
    }

    public void setTrackDistance(int trackDistance)
    {
        this.trackDistance = trackDistance;
    }

    public ItemStorage getTradeItem()
    {
        return tradeItem;
    }

    public int getMaxStackSize()
    {
        return tradeItem.getItemStack().getMaxStackSize();
    }

    public int getLastShipDay()
    {
        return lastShipDay;
    }

    public void setLastShipDay(int lastShipDay)
    {
        this.lastShipDay = lastShipDay;
    }

    /**
     * Predicate for the different usages to check if inventory contains a given item.
     *
     * @param cure the expected export item item.
     * @return the predicate for checking if the export item exists.
     */
    public static Predicate<ItemStack> hasExportItem(final ItemStorage exportItem)
    {
        final IMateriallyTexturedBlock domumBlock = DomumOrnamentumUtils.getBlock(exportItem.getItemStack());
        final boolean isDomum = domumBlock != null;

        if (isDomum)
        {
            return ItemHandlerHelpers.domumMatcher(exportItem.getItemStack());
        }

        return stack -> isExportItem(stack, exportItem);
    }

    /**
     * Predicate for the different usages to check if inventory contains a coin.
     *
     * @return the predicate for checking if the coin exists.
     */
    public static Predicate<ItemStack> hasCoin()
    {
        return stack -> isExportItem(stack, new ItemStorage(BuildingMarketplace.tradeCurrency()));
    }

    /**
     * Check if the given item is an export item item.
     *
     * @param stack      the input stack.
     * @param exportItem the export item.
     * @return true if so.
     */
    public static boolean isExportItem(final ItemStack stack, final ItemStorage exportItem)
    {
        return Objects.equals(new ItemStorage(stack), exportItem);
    }

    /**
     * Retrieves the cart entity associated with this export record.
     *
     * @return the cart entity.
     */
    public GhostCartEntity getCart()
    {
        return cart;
    }

    /**
     * Sets the cart entity associated with this export record.
     *
     * @param cart the cart entity.
     */
    public void setCart(GhostCartEntity cart)
    {
        this.cart = cart;
    }

    /**
     * Sets the insufficient funds status for this export.
     *
     * @param insufficientFunds true if there are insufficient funds for the export, false otherwise.
     */
    public void setInsufficientFunds(boolean insufficientFunds)
    {
        this.insufficientFunds = insufficientFunds;
    }

    /**
     * Returns true if there are insufficient funds for this export, false otherwise.
     * @return true if there are insufficient funds, false otherwise.
     */
    public boolean isInsufficientFunds()
    {
        return insufficientFunds;
    }

    @Override
    public String toString()
    {
        String sourceStationIdentifier = sourceStation == null ? "null" : sourceStation.getLocation().getInDimensionLocation().toShortString();

        return "ExportData{" + "sourceStation={" + sourceStationIdentifier
        + "}, destinationStation={" + destinationStationData.toString() 
        + "}, tradeItem=" + tradeItem 
        + ", reverse=" + reverse +'}';
    }
}
