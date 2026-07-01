package com.deathfrog.mctradepost.core.colony.buildings.modules;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetHelper;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingResort;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ResortGuestMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "resort_guest_message", ResortGuestMessage::new);

    public enum GuestAction
    {
        SUMMON
    }

    private GuestAction guestAction = GuestAction.SUMMON;
    private int civilianId;

    public ResortGuestMessage(final IBuildingView resortBuilding, final GuestAction action, final int civilianId)
    {
        super(TYPE, resortBuilding);
        this.guestAction = action;
        this.civilianId = civilianId;
    }

    protected ResortGuestMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.guestAction = GuestAction.values()[buf.readInt()];
        this.civilianId = buf.readInt();
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeInt(this.guestAction.ordinal());
        buf.writeInt(this.civilianId);
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        ServerLevel level = (ServerLevel) colony.getWorld();

        if (level == null || level.isClientSide)
        {
            return;
        }

        if (!(building instanceof BuildingResort resort))
        {
            MCTradePostMod.LOGGER.error("Invalid resort building: {} - this building is not a resort.", building);
            return;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.noperms")).sendTo(player);
            return;
        }

        switch (guestAction)
        {
            case SUMMON:
                summonGuest(player, colony, resort);
                break;
        }
    }

    private void summonGuest(final ServerPlayer player, final IColony colony, final BuildingResort resort)
    {
        Vacationer guestFile = resort.getGuestFile(civilianId);
        if (guestFile == null)
        {
            MCTradePostMod.LOGGER.warn("Attempt to summon guest {} to resort {}, but this resort does not have that guest.",
                civilianId, resort.getPosition());
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.summonfailed")).sendTo(player);
            return;
        }

        ICitizenData citizenData = colony.getCitizenManager().getCivilian(civilianId);
        if (citizenData == null)
        {
            MCTradePostMod.LOGGER.warn("Attempt to summon guest {} to resort {}, but no citizen data was found.",
                civilianId, resort.getPosition());
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.summonfailed")).sendTo(player);
            return;
        }

        Optional<AbstractEntityCitizen> citizenEntity = citizenData.getEntity();
        if (citizenEntity.isEmpty() || citizenEntity.get().isDead())
        {
            MCTradePostMod.LOGGER.warn("Attempt to summon guest {} to resort {}, but the citizen entity is not available.",
                civilianId, resort.getPosition());
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.summonfailed")).sendTo(player);
            return;
        }

        AbstractEntityCitizen citizen = citizenEntity.get();
        Optional<BlockPos> targetPos = PetHelper.findNearbyValidSpawn(citizen, resort.getPosition(), 3);
        if (targetPos.isEmpty())
        {
            MCTradePostMod.LOGGER.warn("Unable to find a safe summon position near resort {} for guest {}.",
                resort.getPosition(), citizen);
            MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.summonfailed")).sendTo(player);
            return;
        }

        BlockPos safeTargetPos = targetPos.get();
        citizen.teleportTo(safeTargetPos.getX() + 0.5D, safeTargetPos.getY(), safeTargetPos.getZ() + 0.5D);
        guestFile.setCurrentlyAtResort(true);
        ResortGuestListModule module = resort.getModule(ResortGuestListModule.class);
        if (module != null)
        {
            module.markDirty();
        }
        MessageUtils.format(Component.translatable("com.minecolonies.coremod.gui.guestlist.summoned")).sendTo(player);
    }
}
