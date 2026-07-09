package com.deathfrog.mctradepost.core.colony.buildings.modules;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingStation;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server message for mutating dimensional linkages installed in a station.
 */
public class StationLinkageMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "station_linkage_message", StationLinkageMessage::new);

    /**
     * Linkage actions available from the station connection GUI.
     */
    public enum LinkageAction
    {
        /**
         * Remove an installed linkage and return the item to the player.
         */
        REMOVE
    }

    private LinkageAction action = LinkageAction.REMOVE;
    private int index;

    /**
     * Creates a station linkage mutation message.
     *
     * @param building target station building view
     * @param action mutation to perform
     * @param index zero-based installed linkage index
     */
    public StationLinkageMessage(final IBuildingView building, final LinkageAction action, final int index)
    {
        super(TYPE, building);
        this.action = action;
        this.index = index;
    }

    /**
     * Deserializes a station linkage message from the network.
     *
     * @param buf network buffer
     * @param type registered message type
     */
    protected StationLinkageMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.action = LinkageAction.values()[buf.readInt()];
        this.index = buf.readInt();
    }

    /**
     * Writes this message to the network buffer.
     *
     * @param buf network buffer
     */
    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeInt(action.ordinal());
        buf.writeInt(index);
    }

    /**
     * Executes the requested linkage action on the server after permission checks.
     */
    @SuppressWarnings("null")
    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (!(building instanceof BuildingStation station))
        {
            MCTradePostMod.LOGGER.error("Invalid station linkage building: {} - this building is not a station.", building);
            return;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(Component.translatable("mctradepost.linkage.gui.noperms")).sendTo(player);
            return;
        }

        BuildingStationConnectionModule module = station.getModule(MCTPBuildingModules.STATION_CONNECTION);
        if (module == null)
        {
            return;
        }

        if (action == LinkageAction.REMOVE)
        {
            ItemStack removed = module.removeLinkage(index);
            if (removed.isEmpty())
            {
                return;
            }

            if (!player.getInventory().add(removed.copy()))
            {
                player.drop(removed.copy(), false);
            }

            station.getConnectionResults().clear();
            station.markDirty();
            module.markDirty();
            colony.getPackageManager().addCloseSubscriber(player);
            MessageUtils.format(Component.translatable("mctradepost.linkage.gui.removed")).sendTo(player);
        }
    }
}
