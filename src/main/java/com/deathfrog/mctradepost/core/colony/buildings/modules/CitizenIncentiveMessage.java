package com.deathfrog.mctradepost.core.colony.buildings.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.core.network.messages.server.AbstractBuildingServerMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Uses core MANAGE_HUTS permissions; prices and available skill points are always checked on the server. */
public class CitizenIncentiveMessage extends AbstractBuildingServerMessage<IBuilding>
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(MCTradePostMod.MODID, "citizen_incentive", CitizenIncentiveMessage::new);
    private final int citizenId;
    private final UUID uuid;
    private final long revision;
    private final int direction;
    private final Map<Skill, Integer> boosts = new EnumMap<>(Skill.class);

    /**
     * Creates a request to save next-day boosts or change payment priority.
     * @param building Town Hall containing the incentive module
     * @param id colony-local citizen ID
     * @param uuid expected persistent citizen identity
     * @param revision payroll revision shown to the player
     * @param direction zero to save boosts, minus one to move earlier, or plus one to move later
     * @param boosts desired bonuses for a save request
     */
    public CitizenIncentiveMessage(final IBuildingView building, final int id, final UUID uuid, final long revision,
        final int direction, final Map<Skill, Integer> boosts)
    {
        super(TYPE, building);
        this.citizenId = id;
        this.uuid = uuid;
        this.revision = revision;
        this.direction = direction;
        this.boosts.putAll(boosts);
    }

    /**
     * Decodes a fixed-size skill adjustment request from the network.
     * @param buf incoming payload buffer
     * @param type registered payload type
     */
    private CitizenIncentiveMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        citizenId = buf.readInt();
        uuid = buf.readUUID();
        revision = buf.readLong();
        direction = buf.readInt();
        for (final Skill skill : Skill.values()) boosts.put(skill, buf.readInt());
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeInt(citizenId);
        buf.writeUUID(uuid);
        buf.writeLong(revision);
        buf.writeInt(direction);
        for (final Skill skill : Skill.values()) buf.writeInt(boosts.getOrDefault(skill, 0));
    }

    /** {@inheritDoc} */
    @SuppressWarnings("null")
    @Override
    protected void onExecute(final IPayloadContext ctx, final ServerPlayer player, final IColony colony, final IBuilding building)
    {
        if (building != colony.getServerBuildingManager().getTownHall() || !building.hasModule(CitizenIncentiveModule.class)) return;
        final CitizenIncentiveModule module = building.getModule(CitizenIncentiveModule.class);
        // Settle a dawn that arrived between opening the editor and receiving the message.
        module.onColonyTick(colony);
        final ICitizenData citizen = colony.getCitizenManager().getCivilian(citizenId);
        if (module.revision() != revision || citizen == null || !citizen.getUUID().equals(uuid))
        {
            player.displayClientMessage(Component.translatable("mctradepost.incentives.stale"), false);
            module.markDirty();
            return;
        }
        final boolean cancellation = direction == 0 && boosts.values().stream().allMatch(value -> value == 0);
        if (!cancellation && module.incentiveCap() <= 0)
        {
            player.displayClientMessage(Component.translatable("mctradepost.incentives.locked.message"), false);
            module.markDirty();
            return;
        }
        if (direction == 0 && !module.fitsSalaryCap(citizenId, boosts))
        {
            player.displayClientMessage(Component.translatable("mctradepost.incentives.cap_exceeded"), false);
            module.markDirty();
            return;
        }
        final boolean success = direction == 0 ? module.schedule(citizenId, uuid, boosts) : module.movePriority(citizenId, direction);
        player.displayClientMessage(Component.translatable(success ? "mctradepost.incentives.saved" : "mctradepost.incentives.invalid"), false);
        module.markDirty();
    }
}
