package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.UUID;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingRecycling;
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

/** Requests cancellation of one active recycling processor. */
public class CancelRecyclingMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE =
        PlayMessageType.forServer(MCTradePostMod.MODID, "cancel_recycling", CancelRecyclingMessage::new);

    private final @Nonnull UUID processorId;

    public CancelRecyclingMessage(final IBuildingView building, final @Nonnull UUID processorId)
    {
        super(TYPE, building);
        this.processorId = processorId;
    }

    @SuppressWarnings("null")
    protected CancelRecyclingMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        UUID incomingUUID = buf.readUUID();
        this.processorId = incomingUUID;
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeUUID(processorId);
    }

    @Override
    protected void onExecute(final IPayloadContext context, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (!(building instanceof BuildingRecycling recycling))
        {
            MCTradePostMod.LOGGER.warn("Player {} attempted to cancel recycling at a non-recycling building.", player.getName().getString());
            return;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(Component.translatable("com.mctradepost.recycler.cancel.no_permission")).sendTo(player);
            return;
        }

        final ItemStack returnedStack = recycling.cancelRecyclingProcess(processorId);
        if (!returnedStack.isEmpty())
        {
            MessageUtils.format(Component.translatable("com.mctradepost.recycler.cancel.success", returnedStack.getHoverName())).sendTo(player);
        }
    }
}
