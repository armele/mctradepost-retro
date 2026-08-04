package com.deathfrog.mctradepost.network;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.ItemValueManager;
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

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

/** Chunked synchronization of the complete server-authoritative item-value table. */
public record ItemValueSyncPacket(long generation, int chunkIndex, int chunkCount, List<ValueEntry> entries)
    implements CustomPacketPayload
{
    /** Keeps every uncompressed custom payload comfortably below the one MiB clientbound limit. */
    private static final int CHUNK_ENTRY_LIMIT = 20_000;
    private static final AtomicLong NEXT_GENERATION = new AtomicLong(System.nanoTime());

    @SuppressWarnings("null")
    public static final Type<ItemValueSyncPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "sync_item_values"));

    public static final StreamCodec<ByteBuf, ItemValueSyncPacket> STREAM_CODEC = StreamCodec.of(
        ItemValueSyncPacket::encode,
        ItemValueSyncPacket::decode);

    private static long pendingGeneration = Long.MIN_VALUE;
    private static int pendingChunkCount;
    private static BitSet pendingChunks = new BitSet();
    private static Map<ResourceLocation, Integer> pendingValues = new HashMap<>();

    @Override
    public Type<ItemValueSyncPacket> type()
    {
        return TYPE;
    }

    private static void encode(ByteBuf buf, ItemValueSyncPacket packet)
    {
        buf.writeLong(packet.generation);
        ByteBufCodecs.VAR_INT.encode(buf, packet.chunkIndex);
        ByteBufCodecs.VAR_INT.encode(buf, packet.chunkCount);
        ByteBufCodecs.VAR_INT.encode(buf, packet.entries.size());
        for (ValueEntry entry : packet.entries)
        {
            ByteBufCodecs.VAR_INT.encode(buf, entry.itemId());
            ByteBufCodecs.VAR_INT.encode(buf, entry.value());
        }
    }

    private static ItemValueSyncPacket decode(ByteBuf buf)
    {
        long generation = buf.readLong();
        int chunkIndex = ByteBufCodecs.VAR_INT.decode(buf);
        int chunkCount = ByteBufCodecs.VAR_INT.decode(buf);
        int entryCount = ByteBufCodecs.VAR_INT.decode(buf);
        if (chunkCount <= 0 || chunkIndex < 0 || chunkIndex >= chunkCount
            || entryCount < 0 || entryCount > CHUNK_ENTRY_LIMIT)
        {
            throw new IllegalArgumentException("Invalid item-value synchronization chunk");
        }

        List<ValueEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++)
        {
            entries.add(new ValueEntry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return new ItemValueSyncPacket(generation, chunkIndex, chunkCount, List.copyOf(entries));
    }

    /** Collects this chunk and atomically publishes the snapshot once all chunks arrive. */
    @OnlyIn(Dist.CLIENT)
    public void handleDataInClientOnMain(IPayloadContext context)
    {
        context.enqueueWork(() -> acceptClientChunk(this));
    }

    @OnlyIn(Dist.CLIENT)
    private static synchronized void acceptClientChunk(ItemValueSyncPacket packet)
    {
        if (packet.generation != pendingGeneration)
        {
            pendingGeneration = packet.generation;
            pendingChunkCount = packet.chunkCount;
            pendingChunks = new BitSet(packet.chunkCount);
            pendingValues = new HashMap<>();
        }
        if (packet.chunkCount != pendingChunkCount || pendingChunks.get(packet.chunkIndex)) return;

        for (ValueEntry entry : packet.entries)
        {
            if (entry.itemId() >= 0 && entry.itemId() < BuiltInRegistries.ITEM.size())
            {
                Item item = BuiltInRegistries.ITEM.byId(entry.itemId());
                pendingValues.put(BuiltInRegistries.ITEM.getKey(item), entry.value());
            }
        }
        pendingChunks.set(packet.chunkIndex);

        if (pendingChunks.cardinality() == pendingChunkCount)
        {
            ItemValueManager.replaceFromServer(pendingValues);
            MCTradePostMod.LOGGER.info("Received {} server item values in {} chunks", pendingValues.size(), pendingChunkCount);
            pendingValues = new HashMap<>();
            pendingChunks = new BitSet();
        }
    }

    /** Sends a new complete snapshot to one player. */
    public static void sendPacketsToPlayer(@Nonnull ServerPlayer player)
    {
        List<ValueEntry> values = encodedValues();
        int chunkCount = Math.max(1, (values.size() + CHUNK_ENTRY_LIMIT - 1) / CHUNK_ENTRY_LIMIT);
        long generation = NEXT_GENERATION.incrementAndGet();
        for (int chunk = 0; chunk < chunkCount; chunk++)
        {
            int from = chunk * CHUNK_ENTRY_LIMIT;
            int to = Math.min(values.size(), from + CHUNK_ENTRY_LIMIT);
            PacketDistributor.sendToPlayer(player, new ItemValueSyncPacket(
                generation, chunk, chunkCount, List.copyOf(values.subList(from, to))));
        }
        MCTradePostMod.LOGGER.info("Sent {} item values to {} in {} chunks", values.size(), player.getName().getString(), chunkCount);
    }

    /** Sends a fresh snapshot to every connected player after a datapack reload. */
    public static void sendPacketsToAllPlayers(MinecraftServer server)
    {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) 
        {
            if (player == null) continue;

            sendPacketsToPlayer(player);
        }
    }

    private static List<ValueEntry> encodedValues()
    {
        List<ValueEntry> result = new ArrayList<>(ItemValueManager.getBaseValues().size());
        ItemValueManager.getBaseValues().forEach((key, value) -> {
            if (key != null && BuiltInRegistries.ITEM.containsKey(key))
            {
                result.add(new ValueEntry(BuiltInRegistries.ITEM.getId(BuiltInRegistries.ITEM.get(key)), value));
            }
        });
        return result;
    }

    public record ValueEntry(int itemId, int value) { }
}
