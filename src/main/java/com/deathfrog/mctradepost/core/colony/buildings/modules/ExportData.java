package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class ExportData
{
    public static final String TAG_COST = "cost";
    public static final String TAG_QUANTITY = "quantity";

    public record TradeDefinition(ItemStorage tradeItem, int price, int quantity)
    {
    };

    private final BuildingStation sourceStation;
    private final StationData destinationStationData;
    private final ItemStorage tradeItem;
    private final int cost;
    private final int quantity;
    private int shipDistance = -1;
    private int trackDistance = -1;
    private int lastShipDay = -1;
    private boolean insufficientFunds = false;
    private GhostCartEntity cart = null;


    public ExportData(BuildingStation sourceStation, StationData destinationStationData, ItemStorage tradeItem, int cost, int quantity)
    {
        this.sourceStation = sourceStation;
        this.destinationStationData = destinationStationData;
        this.tradeItem = tradeItem;
        this.cost = cost;
        this.quantity = quantity;
        this.shipDistance = -1;
        this.trackDistance = -1;
        this.lastShipDay = -1;
        this.insufficientFunds = false;
    }

    public StationData getDestinationStationData()
    {
        return destinationStationData;
    }

    public int getCost()
    {
        return cost;
    }

    public int getQuantity()
    {
        return quantity;
    }

    public int getShipDistance()
    {
        return shipDistance;
    }

    /**
     * Spawns a GhostCartEntity for trade if one does not already exist. The cart is initialized with the current export's trade item
     * and set on this export data.
     *
     * @param path the path of block positions that the cart should follow when spawned
     * @return the spawned GhostCartEntity, or the existing one if already present
     */
    public GhostCartEntity spawnCartForTrade(List<BlockPos> path)
    {
        if (path == null || path.isEmpty()) return null;

        GhostCartEntity cart =
            GhostCartEntity.spawn((ServerLevel) this.getDestinationStationData().getStation().getColony().getWorld(), ImmutableList.copyOf(path));
        cart.setTradeItem(this.getTradeItem().getItemStack());
        this.setCart(cart);

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
        if (cart != null) return cart;

        if (sourceStation == null) return null;

        TrackConnectionResult tcr = sourceStation.getTrackConnectionResult(this.getDestinationStationData());
        if (tcr != null && tcr.path != null && !tcr.path.isEmpty())
        {
            cart = spawnCartForTrade(tcr.path);
        }
        else
        {
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
     * Check if the given item is a cure item.
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
}
