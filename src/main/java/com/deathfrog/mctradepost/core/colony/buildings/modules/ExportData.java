package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult;
import com.minecolonies.api.crafting.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public class ExportData
{
    private final BuildingStation sourceStation;
    private final StationData destinationStationData;
    private final ItemStorage itemStorage;
    private final int cost;
    private int shipDistance = -1;
    private int trackDistance = -1;
    private int lastShipDay = -1;
    private GhostCartEntity cart = null;

    public ExportData(BuildingStation sourceStation, StationData destinationStationData, ItemStorage itemStorage, int cost)
    {
        this.sourceStation = sourceStation;
        this.destinationStationData = destinationStationData;
        this.itemStorage = itemStorage;
        this.cost = cost;
        this.shipDistance = -1;
        this.trackDistance = -1;
        this.lastShipDay = -1;
    }

    public StationData getDestinationStationData()
    {
        return destinationStationData;
    }

    public int getCost()
    {
        return cost;
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
        GhostCartEntity cart =
            GhostCartEntity.spawn((ServerLevel) this.getDestinationStationData().getStation().getColony().getWorld(), path);
        cart.setTradeItem(this.getItemStorage().getItemStack());
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

        if (cart != null)
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

    public ItemStorage getItemStorage()
    {
        return itemStorage;
    }

    public int getMaxStackSize()
    {
        return itemStorage.getItemStack().getMaxStackSize();
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
}
