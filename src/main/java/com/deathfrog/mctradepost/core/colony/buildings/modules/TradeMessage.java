package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;
import static com.deathfrog.mctradepost.api.util.TraceUtils.TRACE_STATION;

public class TradeMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "export_message", TradeMessage::new);

    public enum TradeAction
    {
        ADD, REMOVE, QUERY
    }

    public enum TradeType
    {
        IMPORT, EXPORT, QUERY
    }

    public static final String TRADES_UPDATED = "mctradepost.station.trades_updated";

    private TradeAction tradeAction;
    private TradeType tradeType;

    private StationData remoteStation;

    /**
     * What item are we trading for.
     */
    private ItemStack itemStack;

    /**
     * How many coins will these items be exchanged for?
     */
    private int cost;

    /**
     * How many items are we trading?
     */
    private int quantity;

    /**
     * Creates a Transfer Items request
     *
     * @param itemStack to be take from the player for the building
     * @param cost  coins being exchanged for
     * @param building  the building we're executing on.
     */
    public TradeMessage(final IBuildingView building, TradeAction action, TradeType tradeType, final StationData remoteStation, final ItemStack itemStack, final int cost, final int quantity)
    {
        super(TYPE, building);
        if (remoteStation != null)
        {
            this.remoteStation = remoteStation;
        }
        else
        {
            this.remoteStation = StationData.EMPTY;
        }
        this.itemStack = itemStack;
        this.cost = cost;
        this.tradeAction = action;
        this.tradeType = tradeType;
        this.quantity = quantity;
    }

    public TradeMessage(final IBuildingView building, TradeType tradeType)
    {
        super(TYPE, building);
        this.tradeType = tradeType;
        this.remoteStation = StationData.EMPTY;
        this.itemStack = ItemStack.EMPTY;
        this.tradeAction = TradeAction.QUERY;
    }

    protected TradeMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        remoteStation = StationData.fromNBT(buf.readNbt());
        itemStack = Utils.deserializeCodecMess(buf);
        cost = buf.readInt();
        quantity = buf.readInt();
        tradeAction = TradeAction.values()[buf.readInt()];
        tradeType = TradeType.values()[buf.readInt()];
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeNbt(remoteStation.toNBT());
        Utils.serializeCodecMess(buf, itemStack);
        buf.writeInt(cost);
        buf.writeInt(quantity);
        buf.writeInt(tradeAction.ordinal());
        buf.writeInt(tradeType.ordinal());
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        switch (tradeType)
        {
            case EXPORT:
                if (building.hasModule(MCTPBuildingModules.EXPORTS))
                {
                    if (tradeAction == TradeAction.REMOVE)
                    {
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Executing TradeMessage to remove export."));
                        building.getModule(MCTPBuildingModules.EXPORTS).removeExport(remoteStation, itemStack);
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Executing TradeMessage to add export."));
                        building.getModule(MCTPBuildingModules.EXPORTS).addExport(remoteStation, itemStack, cost, quantity);
                    }
                    notifyConnectedStations(building, player);
                }
                break;

            case IMPORT:
                if (building.hasModule(MCTPBuildingModules.IMPORTS))
                {
                    if (tradeAction == TradeAction.REMOVE)
                    {
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Executing TradeMessage to remove import."));
                        building.getModule(MCTPBuildingModules.IMPORTS).removeImport(itemStack);
                    }
                    else
                    {
                        TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Executing TradeMessage to add import."));
                        building.getModule(MCTPBuildingModules.IMPORTS).addImport(itemStack, cost, quantity);
                    }
                    notifyConnectedStations(building, player);
                }
                break;

            case QUERY:
                notifyConnectedStations(building, player);
                break;

        }
    }

    /**
     * Notifies connected stations by marking them dirty (which indicates
     * that they require an update or reevaluation) and subscribing the player
     * who initiated this message as to the remote colony view.
     */
    protected void notifyConnectedStations(IBuilding building, final ServerPlayer player)
    {
        if (building instanceof BuildingStation station)
        {

            for (StationData remoteStationData : station.getStations().values())
            {
                if (remoteStationData.getStation() != null && remoteStationData.getTrackConnectionStatus() == TrackConnectionStatus.CONNECTED)
                {
                    BuildingStation remoteStation = remoteStationData.getStation();
                    remoteStation.markTradesDirty();
                    IColony colony = remoteStation.getColony();
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Notifying station at {} of trade terms changes.", station.getPosition()));

                    MessageUtils.format(TRADES_UPDATED).sendTo(colony).forAllPlayers();

                    colony.getPackageManager().addCloseSubscriber(player);
                }
            }
        }
    }
}
