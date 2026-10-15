package com.deathfrog.mctradepost.core.colony.buildings.modules;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Updates whether a station should attempt route discovery to one destination. */
public class StationConnectionAttemptMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(
        MCTradePostMod.MODID, "station_connection_attempt", StationConnectionAttemptMessage::new);

    private StationData destination;
    private boolean enabled;

    /**
     * Creates a connection-attempt preference update.
     *
     * @param building local station view
     * @param destination destination station
     * @param enabled whether discovery should be enabled
     */
    public StationConnectionAttemptMessage(final IBuildingView building, final StationData destination, final boolean enabled)
    {
        super(TYPE, building);
        this.destination = destination;
        this.enabled = enabled;
    }

    /**
     * Decodes a connection-attempt update.
     *
     * @param buf network buffer
     * @param type message type
     */
    protected StationConnectionAttemptMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        destination = StationData.fromNBT(buf.readNbt());
        enabled = buf.readBoolean();
    }

    /**
     * Encodes this preference update.
     *
     * @param buf destination network buffer
     */
    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeNbt(destination.toNBT());
        buf.writeBoolean(enabled);
    }

    /**
     * Applies the preference when the player may manage the station.
     *
     * @param context payload context
     * @param player requesting player
     * @param colony local colony
     * @param building target building
     */
    @Override
    protected void onExecute(final IPayloadContext context, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (!(building instanceof BuildingStation station)
            || !colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            return;
        }
        StationData knownDestination = station.getStationAt(destination.getDimensionalPosition());
        if (knownDestination != null)
        {
            station.setConnectionAttempt(knownDestination, enabled);
        }
    }
}
