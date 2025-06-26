package com.deathfrog.mctradepost.network;

import java.util.Map;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ItemValuePacket(String itemkey, int value) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<ItemValuePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "sellable_item"));

    // Each pair of elements defines the stream codec of the element to encode/decode and the getter for the element to encode
    // 'name' will be encoded and decoded as a string
    // 'age' will be encoded and decoded as an integer
    // The final parameter takes in the previous parameters in the order they are provided to construct the payload object
    public static final StreamCodec<ByteBuf, ItemValuePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        ItemValuePacket::itemkey,
        ByteBufCodecs.VAR_INT,
        ItemValuePacket::value,
        ItemValuePacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handles the data in the client on the main thread. This method is called when an {@link ItemValuePacket} is received
     * by the client. The packet is deserialized and the deserialized data is passed to the 
     * {@link ItemValueRegistry#deserializedSellableItem(String, int)} method, which adds the item to the item values map.
     * If an exception is thrown while deserializing the packet, the client is disconnected with a message indicating that
     * the packet handling failed.
     * @param data the deserialized packet data
     * @param context the context in which the packet was received
     */
    public static void handleDataInClientOnMain(final ItemValuePacket data, final IPayloadContext context) {
        // Do something with the data, on the main thread
        context.enqueueWork(() -> {
            ItemValueRegistry.deserializedSellableItem(data.itemkey(), data.value());
        })
        .exceptionally(e -> {
            // Handle exception
            context.disconnect(Component.translatable("ItemValuePacket handling failed.", e.getMessage()));
            return null;
        });
    }

    public static void handleDataInServerOnMain(final ItemValuePacket data, final IPayloadContext context) {
        // Do something with the data, on the main thread
        MCTradePostMod.LOGGER.warn("Client should never send ItemValuePackets to the server. Received ItemValuePacket: {} {}", data.itemkey(), data.value());
    }

    /**
     * Sends all sellable item values to the given player.
     * 
     * @param player the player to send the packets to
     */
    public static void sendPacketsToPlayer(ServerPlayer player) {
        for (Map.Entry<Item, Integer> entry : ItemValueRegistry.getItemValues().entrySet())
        {
            String key = null;
            
            try {
                key = BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
                int value = entry.getValue();

                if (key != null) {
                    PacketDistributor.sendToPlayer(player, new ItemValuePacket(key, value));
                }

            } catch (Exception e) {
                // Possibly due to item registrations that happened subsequent to item value determination, some things can no longer be found.
                // We are happy to silently fail, and not pass the missing value to the player. (They will not be able to select it as sellable - and that's ok.)
                // MCTradePostMod.LOGGER.error("Error getting resource location for item: {}", entry.getKey());
                continue;
            }

        }
        MCTradePostMod.LOGGER.info("Item values sent to player: {}", player.getName().getString());
    }
}