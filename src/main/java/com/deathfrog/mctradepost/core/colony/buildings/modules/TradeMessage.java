package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class TradeMessage extends AbstractBuildingServerMessage<IBuilding>
{
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
     * How many coins will this item be exchanged for?
     */
    private int cost;

    /**
     * Creates a Transfer Items request
     *
     * @param itemStack to be take from the player for the building
     * @param cost  coins being exchanged for
     * @param building  the building we're executing on.
     */
    public TradeMessage(final IBuildingView building, TradeAction action, TradeType type, final StationData remoteStation, final ItemStack itemStack, final int cost)
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
    }

    protected TradeMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        remoteStation = StationData.fromNBT(buf.readNbt());
        itemStack = Utils.deserializeCodecMess(buf);
        cost = buf.readInt();
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
                    building.getModule(MCTPBuildingModules.EXPORTS).removeTrade(remoteStation, itemStack);
                }
                else
                {
                    building.getModule(MCTPBuildingModules.EXPORTS).addTrade(remoteStation, itemStack, cost);
                }
            }
        }
        else 
        {
            if (building.hasModule(MCTPBuildingModules.IMPORTS))
            {
                if (tradeAction == TradeAction.REMOVE)
                {
                    building.getModule(MCTPBuildingModules.IMPORTS).removeTrade(itemStack);
                }
                else
                {
                    building.getModule(MCTPBuildingModules.IMPORTS).addTrade(itemStack, cost);
                }
            }
        }
    }
}
