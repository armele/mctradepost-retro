package com.deathfrog.mctradepost.core.colony.buildings.modules;

import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_CART;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.util.ChunkUtil;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.ITradeCapable;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
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

    private final ITradeCapable sourceStation;
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



    public ExportData(ITradeCapable sourceStation, StationData destinationStationData, ItemStorage tradeItem, int cost, boolean reverse)
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

    public ITradeCapable getSourceStation()
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
        if (path == null || path.isEmpty()) 
        {
            TraceUtils.dynamicTrace(TRACE_CART, () -> LOGGER.warn("Null or empty path while spawning cart: {}", this));
            return null;
        }

        ServerLevel level = (ServerLevel) this.getDestinationStationData().getStation().getColony().getWorld();
        
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

        ChunkUtil.ensureChunkLoaded(level, startPos);

        GhostCartEntity newCart =
            GhostCartEntity.spawn(level, NullnessBridge.assumeNonnull(ImmutableList.copyOf(path)), isReverse());

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

        ChunkUtil.releaseChunkTicket(level, startPos, 1);

        return newCart;
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

        if (tcr != null && tcr.path != null && !tcr.path.isEmpty())
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
     * Sets the ship distance for this export and updates the visualization of the ghost cart (if it exists) to reflect the new
     * distance. If the cart does not exist yet, it will be spawned if possible.
     * 
     * @param shipDistance the new ship distance.
     */
    public void setShipDistance(int shipDistance)
    {
        this.shipDistance = shipDistance;

        if (cart != null && shipDistance >= 0)
        {
            cart.setSegment(shipDistance);
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
     * @param cure the expected cure item.
     * @return the predicate for checking if the cure exists.
     */
    public static Predicate<ItemStack> hasExportItem(final ItemStorage exportItem)
    {
        return stack -> isExportItem(stack, exportItem);
    }

    /**
     * Predicate for the different usages to check if inventory contains a cure.
     *
     * @param cure the expected cure item.
     * @return the predicate for checking if the cure exists.
     */
    public static Predicate<ItemStack> hasCoin()
    {
        return stack -> isExportItem(stack, new ItemStorage(MCTradePostMod.MCTP_COIN_ITEM.get()));
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
        return "ExportData{" + "sourceStation={" + sourceStation.getLocation().getInDimensionLocation().toShortString()
        + "}, destinationStation={" + destinationStationData.toString() 
        + "}, tradeItem=" + tradeItem 
        + ", reverse=" + reverse +'}';
    }
}
