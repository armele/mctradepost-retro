package com.deathfrog.mctradepost.network;

import java.util.ArrayList;
import java.util.List;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.render.TradePathDebugOverlay;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Player-local snapshot consumed by the extensible trade-path debug overlay. */
public record TradePathDebugPacket(boolean clear, List<VisualSegment> segments) implements CustomPacketPayload
{
    private static final int MAX_SEGMENTS = 256;
    private static final int MAX_NODES = 200_000;
    public static final Type<TradePathDebugPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "trade_path_debug"));
    public static final StreamCodec<ByteBuf, TradePathDebugPacket> STREAM_CODEC = StreamCodec.of(
        TradePathDebugPacket::encode, TradePathDebugPacket::decode);

    public static TradePathDebugPacket show(TrackRoute route)
    {
        List<VisualSegment> visuals = route.segments().stream()
            .map(segment -> new VisualSegment(segment.type(), segment.dimension().location(), segment.path()))
            .toList();
        return new TradePathDebugPacket(false, visuals);
    }

    public static TradePathDebugPacket clearOverlay()
    {
        return new TradePathDebugPacket(true, List.of());
    }

    private static void encode(ByteBuf buf, TradePathDebugPacket packet)
    {
        buf.writeBoolean(packet.clear);
        ByteBufCodecs.VAR_INT.encode(buf, packet.segments.size());
        for (VisualSegment segment : packet.segments)
        {
            ByteBufCodecs.VAR_INT.encode(buf, segment.type.ordinal());
            ByteBufCodecs.STRING_UTF8.encode(buf, segment.dimension.toString());
            ByteBufCodecs.VAR_INT.encode(buf, segment.path.size());
            for (BlockPos pos : segment.path) buf.writeLong(pos.asLong());
        }
    }

    private static TradePathDebugPacket decode(ByteBuf buf)
    {
        boolean clear = buf.readBoolean();
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        if (count < 0 || count > MAX_SEGMENTS) throw new IllegalArgumentException("Invalid trade-path segment count");
        List<VisualSegment> segments = new ArrayList<>(count);
        int totalNodes = 0;
        for (int i = 0; i < count; i++)
        {
            int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
            if (ordinal < 0 || ordinal >= TrackRoute.SegmentType.values().length) throw new IllegalArgumentException("Invalid trade-path segment type");
            ResourceLocation dimension = ResourceLocation.parse(ByteBufCodecs.STRING_UTF8.decode(buf));
            int nodes = ByteBufCodecs.VAR_INT.decode(buf);
            totalNodes += nodes;
            if (nodes < 0 || totalNodes > MAX_NODES) throw new IllegalArgumentException("Trade-path overlay is too large");
            List<BlockPos> path = new ArrayList<>(nodes);
            for (int node = 0; node < nodes; node++) path.add(BlockPos.of(buf.readLong()));
            segments.add(new VisualSegment(TrackRoute.SegmentType.values()[ordinal], dimension, List.copyOf(path)));
        }
        return new TradePathDebugPacket(clear, List.copyOf(segments));
    }

    @OnlyIn(Dist.CLIENT)
    public void handleDataInClientOnMain(IPayloadContext context)
    {
        context.enqueueWork(() -> {
            if (clear) TradePathDebugOverlay.clear();
            else TradePathDebugOverlay.show(segments);
        });
    }

    @Override
    public Type<TradePathDebugPacket> type() { return TYPE; }

    public record VisualSegment(TrackRoute.SegmentType type, ResourceLocation dimension, List<BlockPos> path) { }
}
