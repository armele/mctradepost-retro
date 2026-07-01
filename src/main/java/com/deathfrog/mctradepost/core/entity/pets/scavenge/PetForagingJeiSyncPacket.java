package com.deathfrog.mctradepost.core.entity.pets.scavenge;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.compat.CompatHooks;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client packet containing the complete flattened pet foraging JEI dataset.
 * <p>
 * The server builds these entries from its active tags and loot-table resources. Clients cache the packet contents and ask the JEI
 * compat hook to refresh the custom category.
 * </p>
 *
 * @param entries display entries to expose in JEI
 */
public record PetForagingJeiSyncPacket(List<PetForagingJeiEntry> entries) implements CustomPacketPayload
{
    /**
     * Unique payload id for pet foraging JEI synchronization.
     */
    @SuppressWarnings("null")
    public static final CustomPacketPayload.Type<PetForagingJeiSyncPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "sync_pet_foraging_jei"));

    /**
     * Packet codec for the full synced entry list.
     */
    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, PetForagingJeiSyncPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, PetForagingJeiEntry.STREAM_CODEC), PetForagingJeiSyncPacket::entries,
            PetForagingJeiSyncPacket::new
        );

    public PetForagingJeiSyncPacket
    {
        entries = List.copyOf(entries);
    }

    @Override
    public CustomPacketPayload.Type<PetForagingJeiSyncPacket> type()
    {
        return TYPE;
    }

    /**
     * Handles the packet on the client by replacing the local cache and refreshing JEI if the integration is loaded.
     *
     * @param ctx network payload context
     */
    @OnlyIn(Dist.CLIENT)
    public void handleDataInClientOnMain(final IPayloadContext ctx)
    {
        PetForagingJeiCache.setEntries(entries);
        MCTradePostMod.LOGGER.info("Received {} pet foraging JEI entries on client", entries.size());
        CompatHooks.refreshPetForagingJei();
    }

    /**
     * Builds and sends the current server-derived foraging entries to one player.
     *
     * @param player target player
     */
    public static void sendPacketsToPlayer(final ServerPlayer player)
    {
        if (player == null) return;

        try
        {
            PacketDistributor.sendToPlayer(player, new PetForagingJeiSyncPacket(PetForagingJeiDataBuilder.build(player.server)));
        }
        catch (Exception e)
        {
            MCTradePostMod.LOGGER.error("Failed to send pet foraging JEI entries to player: {}", player.getName().getString(), e);
        }
    }

    /**
     * Builds the current foraging entries once and sends them to every connected player.
     *
     * @param server server whose datapack state should be reflected
     */
    public static void sendPacketsToAllPlayers(final MinecraftServer server)
    {
        if (server == null) return;

        final List<PetForagingJeiEntry> entries = PetForagingJeiDataBuilder.build(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (player == null) continue;

            PacketDistributor.sendToPlayer(player, new PetForagingJeiSyncPacket(entries));
        }
    }
}
