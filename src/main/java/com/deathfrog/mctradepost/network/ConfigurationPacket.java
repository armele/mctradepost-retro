package com.deathfrog.mctradepost.network;

import java.util.Map;

import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ConfigurationPacket (String configName, String configValue) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ConfigurationPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "config_value"));

    // Each pair of elements defines the stream codec of the element to encode/decode and the getter for the element to encode
    // 'name' will be encoded and decoded as a string
    // 'age' will be encoded and decoded as an integer
    // The final parameter takes in the previous parameters in the order they are provided to construct the payload object
    public static final StreamCodec<ByteBuf, ConfigurationPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        ConfigurationPacket::configName,
        ByteBufCodecs.STRING_UTF8,
        ConfigurationPacket::configValue,
        ConfigurationPacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the data in the client on the main thread. This method is called when an {@link ConfigurationPacket} is received
     * by the client. The packet is deserialized and the deserialized data is stored in the client-side configuration.
     * If an exception is thrown while deserializing the packet, the client is disconnected with a message indicating that
     * the packet handling failed.
     * @param data the deserialized packet data
     * @param context the context in which the packet was received
     */
    
    public static void handleDataInClientOnMain(final ConfigurationPacket data, final IPayloadContext context) {
        // Do something with the data, on the main thread
        context.enqueueWork(() -> {
            MCTPConfig.deserializeConfigurationSetting(data.configName(), data.configValue());
        })
        .exceptionally(e -> {
            // Handle exception
            context.disconnect(Component.translatable("ConfigurationPacket handling failed.", e.getMessage()));
            return null;
        });
    }

    public static void handleDataInServerOnMain(final ConfigurationPacket data, final IPayloadContext context) {
        // Do something with the data, on the main thread
        MCTradePostMod.LOGGER.warn("Client should never send ConfigurationPackets to the server. Received ConfigurationPacket: {} {}", data.configName(), data.configValue());
    }

    /**
     * Sends all configurationvalues to the given player.
     * 
     * @param player the player to send the packets to
     */
    public static void sendPacketsToPlayer(ServerPlayer player) {
        for (Map.Entry<String, String> entry : MCTPConfig.getSerializableConfigSettings().entrySet())
        {
            
            PacketDistributor.sendToPlayer(player, new ConfigurationPacket(entry.getKey(), entry.getValue()));

        }
        MCTradePostMod.LOGGER.info("Configuration sent to player: {}", player.getName().getString());
    }
}