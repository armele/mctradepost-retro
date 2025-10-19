package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.mojang.logging.LogUtils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class BuildingStationExportModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule, IAltersRequiredItems
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final int BASE_TICK_MOVEMENT = 10;

    /**
     * The import tag name.
     */
    private static final String TAG_EXPORTS = "exports";

    /**
     * The cost tag name.
     */
    private static final String TAG_COST = "cost";

    /**
     * The cost tag name.
     */
    private static final String TAG_QUANTITY = "quantity";

    /**
     * The ship distance tag name.
     */
    private static final String TAG_SHIP_DISTANCE = "shipDistance";

    /**
     * The track distance tag name.
     */
    private static final String TAG_TRACK_DISTANCE = "trackDistance";

    /**
     * Tag for the last day this export was shipped.
     */
    private static final String TAG_LAST_SHIP_DAY = "lastShipDay";

    /**
     * Tag for the flag of insufficient funds
     */
    private static final String TAG_NSF = "nsf";

    /**
     * The list of exports configured.
     */
    protected final List<ExportData> exportList = new ArrayList<>();

    /**
     * Deserializes the NBT data for the trade list, restoring its state from the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        exportList.clear();
        final ListTag exportListTag = compound.getList(TAG_EXPORTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < exportListTag.size(); i++)
        {
            final CompoundTag compoundNBT = exportListTag.getCompound(i);
            StationData station = null;
            if (compoundNBT.contains("exportStation")) 
            {
                station = StationData.fromNBT(compoundNBT.getCompound("exportStation"));
            }
            ItemStorage itemStorage = new ItemStorage(ItemStack.parseOptional(provider, compoundNBT.getCompound(NbtTagConstants.STACK)));
            int cost = compoundNBT.getInt(TAG_COST);
            int quantity = compoundNBT.getInt(TAG_QUANTITY);
            int shipDistance = compoundNBT.getInt(TAG_SHIP_DISTANCE);
            int trackDistance = compoundNBT.getInt(TAG_TRACK_DISTANCE);
            int lastShipDay = compoundNBT.getInt(TAG_LAST_SHIP_DAY);
            boolean nsf = compoundNBT.getBoolean(TAG_NSF);
            if (station != null)
            {
                ExportData exportData = new ExportData((BuildingStation) building, station, itemStorage, cost, quantity);
                exportData.setTrackDistance(trackDistance);
                exportData.setLastShipDay(lastShipDay);
                exportData.spawnCartForTrade();
                exportData.setShipDistance(shipDistance);
                exportData.setInsufficientFunds(nsf);
                exportList.add(exportData);
            }
        }
    }

    /**
     * Serializes the NBT data for the trade list, storing its state in the
     * provided CompoundTag.
     *
     * @param provider The holder lookup provider for item and block references.
     * @param compound The CompoundTag containing the serialized state of the
     *                 trade list.
     */
    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag exportListTag = new ListTag();
        for (ExportData exportData : exportList)
        {
            final CompoundTag compoundNBT = new CompoundTag();
            compoundNBT.put("exportStation", exportData.getDestinationStationData().toNBT());
            compoundNBT.put(NbtTagConstants.STACK, exportData.getTradeItem().getItemStack().saveOptional(provider));
            compoundNBT.putInt(TAG_COST, exportData.getCost());
            compoundNBT.putInt(TAG_QUANTITY, exportData.getQuantity());
            compoundNBT.putInt(TAG_SHIP_DISTANCE, exportData.getShipDistance());
            compoundNBT.putInt(TAG_TRACK_DISTANCE, exportData.getTrackDistance());
            compoundNBT.putInt(TAG_LAST_SHIP_DAY, exportData.getLastShipDay());
            compoundNBT.putBoolean(TAG_NSF, exportData.isInsufficientFunds());
            exportListTag.add(compoundNBT);
        }
        compound.put(TAG_EXPORTS, exportListTag);
    }

    /**
     * Serializes the trade list to the given RegistryFriendlyByteBuf for
     * transmission to the client. 
     *
     * @param buf the buffer to serialize the trade list to.
     */
    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(exportList.size());
        for (ExportData exportData : exportList)
        {
            buf.writeNbt(exportData.getDestinationStationData().toNBT());
            Utils.serializeCodecMess(buf, exportData.getTradeItem().getItemStack());
            buf.writeInt(exportData.getCost());
            buf.writeInt(exportData.getQuantity());
            buf.writeInt(exportData.getShipDistance());
            buf.writeInt(exportData.getTrackDistance());
            buf.writeInt(exportData.getLastShipDay());
            buf.writeBoolean(exportData.isInsufficientFunds());
        }
    }


    /**
     * Adds a new export to the list of configured exports for this station.
     * This will add the export to the list and mark the module as dirty.
     * 
     * @param destinationStation The station to export to.
     * @param itemStack The item to export.
     * @param cost The cost of exporting one unit of the item.
     * @param quantity The quantity of the item to export.
     * @return The newly added export data.
     */
    public ExportData addExport(StationData destinationStation, final ItemStack itemStack, final int cost, final int quantity)
    {
        ExportData addedExport = new ExportData((BuildingStation) building, destinationStation, new ItemStorage(itemStack), cost, quantity);
        exportList.add(addedExport);

        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Added export {} to {}. Post-addition size: {}", itemStack, destinationStation.getBuildingPosition(), exportList.size()));

        markDirty();

        return addedExport;
    }

    /**
     * Removes the given export data from the list of configured exports for this station.
     * If the export data is found in the list, it is removed and the module is marked as dirty.
     * 
     * @param exportData the export data to remove.
     * @return true if the export data was found and removed, false otherwise.
     */
    public boolean removeExport(ExportData exportData)
    {
        if (exportData == null)
        {
            return false;
        }

        boolean removed = exportList.remove(exportData);
        markDirty();
        
        return removed;
    }


    /**
     * Removes a trade associated with the given ItemStack from the trade list.
     *
     * @param itemStack The ItemStack representing the trade to be removed.
     */
    public void removeExport(StationData station, final ItemStack itemStack)
    {
        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Removing export {} from {}. Pre-removal size: {}", itemStack, station.getBuildingPosition(), exportList.size()));

        exportList.removeIf(exportData -> exportData.getDestinationStationData().equals(station) && exportData.getTradeItem().getItemStack().is(itemStack.getItem()));

        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Removing export {} from {}. Post-removal size: {}", itemStack, station.getBuildingPosition(), exportList.size()));

        markDirty();
    }

    /**
     * Retrieves the number of configured exports in this module.
     * 
     * @return the count of configured exports.
     */
    public int exportCount() 
    {
        return exportList.size();
    }

    /**
     * Retrieves the list of configured exports in this module.
     * 
     * @return the list of configured exports.
     */
    public List<ExportData> getExports() 
    {
        return exportList;
    }

    /**
     * Called every tick that the colony updates. This method iterates through the list of
     * exports, incrementing the ship distance for each export that has a non-negative ship distance.
     *
     * @param colony the colony that this building module is a part of
     */
    @Override
    public void onColonyTick(@NotNull IColony colony) 
    { 
        for (ExportData exportData : exportList) 
        {
            int shipDistance = exportData.getShipDistance();
            if (shipDistance >= 0)
            { 
                // Check for completion before the next move.
                if (shipDistance >= exportData.getTrackDistance())
                {
                    ((BuildingStation) building).completeExport(exportData);
                    continue;
                }

                markDirty();

                double tradeSpeedBonus = building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.TRADESPEED);  
                int nextDistance = (building.getBuildingLevel() * MCTPConfig.baseTradeSpeed.get()) + BASE_TICK_MOVEMENT;

                if (tradeSpeedBonus > 0)
                {
                    nextDistance = (int)(nextDistance * tradeSpeedBonus);
                }

                shipDistance = shipDistance + nextDistance;

                exportData.spawnCartForTrade();
                exportData.setShipDistance(shipDistance);

                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Shipment in transit of {} {} for {} at {} of {}", exportData.getQuantity(),  exportData.getTradeItem().getItem(), exportData.getCost(), exportData.getShipDistance(), exportData.getTrackDistance()));
            }
            else
            {
                TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Export of {} {} for {} not shipping.", exportData.getQuantity(), exportData.getTradeItem().getItemStack().getHoverName(), exportData.getCost()));
            }
        }
    }

    /**
     * Modifies the items to be kept in the inventory. This method is called when the inventory is about to be cleared.
     * Any items expected to be exported are added to the list of items to be kept.
     * @param consumer The consumer to call for each item in the inventory.
     */
    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        if(!exportList.isEmpty())
        {
            for(ExportData data : exportList)
            {
                ItemStorage item = data.getTradeItem();
                int quantity = data.getQuantity();
                consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, item.getItemStack(), false, true), quantity, false);
            }
        }
    }
}