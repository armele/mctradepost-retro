package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.TraceUtils;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
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
        ADD, REMOVE
    }

    public enum TradeType
    {
        IMPORT, EXPORT
    }

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
    public TradeMessage(final IBuildingView building, TradeAction action, TradeType type, final StationData remoteStation, final ItemStack itemStack, final int cost, final int quantity)
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
        this.tradeType = type;
        this.quantity = quantity;
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
        if (tradeType == TradeType.EXPORT)
        {
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
            }
        }
        else 
        {
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
                notifyAllStations();
            }
        }
    }

    /**
     * Notifies all stations in all colonies by marking them dirty, which indicates
     * that they require an update or reevaluation. This method iterates over all
     * colonies and their buildings, checking each building to see if it is an
     * instance of BuildingStation and marking it dirty if so.
     */
    protected void notifyAllStations()
    {
        for (IColony colony : IColonyManager.getInstance().getAllColonies())
        {
            for (IBuilding checkbuilding : colony.getBuildingManager().getBuildings().values())
            {
                if (checkbuilding instanceof BuildingStation station)
                {
                    TraceUtils.dynamicTrace(TRACE_STATION, () -> LOGGER.info("Notifying station at {} of import changes.", station.getPosition()));
                    station.markDirty();
                }
            }
        }
    }
}
