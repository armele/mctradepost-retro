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
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Queues fresh route discovery for one station connection by discarding its cached result. */
public class StationRouteRefreshMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(
        MCTradePostMod.MODID, "station_route_refresh", StationRouteRefreshMessage::new);

    private BlockPos destination;

    public StationRouteRefreshMessage(final IBuildingView building, final BlockPos destination)
    {
        super(TYPE, building);
        this.destination = destination.immutable();
    }

    protected StationRouteRefreshMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        destination = buf.readBlockPos();
    }

    @SuppressWarnings("null")
    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeBlockPos(destination);
    }

    @SuppressWarnings("null")
    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (!(building instanceof BuildingStation station))
        {
            return;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(Component.translatable("mctradepost.route_refresh.noperms")).sendTo(player);
            return;
        }

        if (station.getStationmaster() == null)
        {
            MessageUtils.format(Component.translatable("mctradepost.route_refresh.no_stationmaster")).sendTo(player);
            return;
        }

        StationData remote = station.getStationAt(destination);
        if (remote == null)
        {
            return;
        }

        station.getConnectionResults().remove(remote);
        station.markDirty();
        BuildingStationConnectionModule module = station.getModule(MCTPBuildingModules.STATION_CONNECTION);
        if (module != null)
        {
            module.markDirty();
        }
        MessageUtils.format(Component.translatable("mctradepost.route_refresh.queued")).sendTo(player);
    }
}
