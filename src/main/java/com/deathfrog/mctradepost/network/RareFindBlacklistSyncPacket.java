package com.deathfrog.mctradepost.network;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.rarefinds.blacklist.RareFindBlacklistManager;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Synchronizes the effective Rare Finds blacklist used by client pickers.
 *
 * @param itemIds numeric registry IDs of effectively blacklisted items
 */
public record RareFindBlacklistSyncPacket(Set<Integer> itemIds) implements CustomPacketPayload
{
    @SuppressWarnings("null")
    public static final Type<RareFindBlacklistSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "sync_rarefinds_blacklist"));

    public static final StreamCodec<ByteBuf, RareFindBlacklistSyncPacket> STREAM_CODEC = StreamCodec.of(
        RareFindBlacklistSyncPacket::encode, RareFindBlacklistSyncPacket::decode);

    /**
     * Returns the registered custom-payload type.
     *
     * @return Rare Finds blacklist payload type
     */
    @Override
    public Type<RareFindBlacklistSyncPacket> type()
    {
        return TYPE;
    }

    /**
     * Writes a blacklist snapshot to the network buffer.
     *
     * @param buffer destination network buffer
     * @param packet snapshot to encode
     */
    @SuppressWarnings("null")
    private static void encode(final @Nonnull ByteBuf buffer, final RareFindBlacklistSyncPacket packet)
    {
        ByteBufCodecs.VAR_INT.encode(buffer, packet.itemIds.size());
        packet.itemIds.stream().sorted().forEach(id -> ByteBufCodecs.VAR_INT.encode(buffer, id));
    }

    /**
     * Reads and validates a blacklist snapshot from the network buffer.
     *
     * @param buffer source network buffer
     * @return decoded blacklist snapshot
     */
    private static RareFindBlacklistSyncPacket decode(final @Nonnull ByteBuf buffer)
    {
        final int size = ByteBufCodecs.VAR_INT.decode(buffer);
        if (size < 0 || size > BuiltInRegistries.ITEM.size())
        {
            throw new IllegalArgumentException("Invalid Rare Finds blacklist size");
        }
        final Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < size; i++) ids.add(ByteBufCodecs.VAR_INT.decode(buffer));
        return new RareFindBlacklistSyncPacket(Set.copyOf(ids));
    }

    /**
     * Publishes a received snapshot on the client main thread.
     *
     * @param context payload handling context
     */
    @OnlyIn(Dist.CLIENT)
    public void handleDataInClientOnMain(final IPayloadContext context)
    {
        context.enqueueWork(() -> {
            final Set<Item> items = new HashSet<>();
            for (final int id : itemIds)
            {
                final Item item = BuiltInRegistries.ITEM.byId(id);
                if (item != null) items.add(item);
            }
            RareFindBlacklistManager.installSynchronizedItems(items);
        });
    }

    /**
     * Sends the current effective blacklist to one player.
     *
     * @param player receiving player
     */
    public static void sendToPlayer(final @Nonnull ServerPlayer player)
    {
        PacketDistributor.sendToPlayer(player, snapshot());
    }

    /**
     * Sends the current effective blacklist to every connected player.
     *
     * @param server source of the connected player list
     */
    public static void sendToAllPlayers(final MinecraftServer server)
    {
        PacketDistributor.sendToAllPlayers(snapshot());
    }

    /**
     * Converts the effective server blacklist into compact registry IDs.
     *
     * @return immutable synchronization payload
     */
    private static @Nonnull RareFindBlacklistSyncPacket snapshot()
    {
        final Set<Integer> ids = new HashSet<>();
        RareFindBlacklistManager.effectiveItems().forEach(item -> ids.add(BuiltInRegistries.ITEM.getId(item)));
        return new RareFindBlacklistSyncPacket(Set.copyOf(ids));
    }
}
