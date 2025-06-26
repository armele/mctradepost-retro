package com.deathfrog.mctradepost.core.event.wishingwell.ritual;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.compat.jei.JEIMCTPPlugin;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Map;

/**
 * One-shot packet: sends the complete list of RitualDefinitions from server to client.
 */
public record RitualPacket(Map<ResourceLocation, RitualDefinition> rituals) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<RitualPacket> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "sync_rituals"));

    public static final Codec<RitualPacket> CODEC = Codec.unboundedMap(ResourceLocation.CODEC, RitualDefinition.CODEC)
        .fieldOf("rituals")
        .codec()
        .xmap(RitualPacket::new, RitualPacket::rituals);

    public static final StreamCodec<ByteBuf, RitualPacket> RITUAL_CODEC = ByteBufCodecs.fromCodec(CODEC);

    @Override
    public CustomPacketPayload.Type<RitualPacket> type()
    {
        return TYPE;
    }

    /**
     * Client-side handling for the RitualPacket. Replaces any existing rituals in the client-side
     * RitualManager with the new list of rituals sent from the server.
     *
     * @param ctx the payload context
     */
    public void handleDataInClientOnMain(IPayloadContext ctx)
    {
        rituals.forEach((id, def) -> RitualManager.putRitual(id, new RitualDefinitionHelper(id, def)));

        MCTradePostMod.LOGGER.info("Received {} rituals on client", rituals.size());

        JEIMCTPPlugin.refreshRitualRecipes();
    }

    // Executed on the SERVER main thread (you probably won’t send this client->server,
    // but the handler is required for bidirectional registration).
    public void handleDataInServerOnMain(IPayloadContext ctx)
    {
        MCTradePostMod.LOGGER.warn("Client should never send RitualPackets to the server.");
    }

    /**
     * Sends all sellable item values to the given player.
     * 
     * @param player the player to send the packets to
     */
    public static void sendPacketsToPlayer(ServerPlayer player)
    {
        try
        {
            PacketDistributor.sendToPlayer(player, new RitualPacket(RitualManager.getUnwrappedRituals()));
        }
        catch (Exception e)
        {
            MCTradePostMod.LOGGER.error("Failed to send RitualPacket to player: {}", player.getName().getString());
        }
        MCTradePostMod.LOGGER.info("Item values sent to player: {}", player.getName().getString());
    }

}
