package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.Optional;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Persistent, directional endpoint data carried by a Route Survey. */
public record RouteSurveyRecord(Optional<DimPos> origin, Optional<DimPos> destination,
    @Nonnull String originName, @Nonnull String destinationName, int originColonyId, int destinationColonyId)
{
    @SuppressWarnings("null")
    public static final Codec<RouteSurveyRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        DimPos.CODEC.optionalFieldOf("origin").forGetter(RouteSurveyRecord::origin),
        DimPos.CODEC.optionalFieldOf("destination").forGetter(RouteSurveyRecord::destination),
        Codec.STRING.optionalFieldOf("originName", "").forGetter(RouteSurveyRecord::originName),
        Codec.STRING.optionalFieldOf("destinationName", "").forGetter(RouteSurveyRecord::destinationName),
        Codec.INT.optionalFieldOf("originColonyId", -1).forGetter(RouteSurveyRecord::originColonyId),
        Codec.INT.optionalFieldOf("destinationColonyId", -1).forGetter(RouteSurveyRecord::destinationColonyId))
        .apply(instance, RouteSurveyRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteSurveyRecord> STREAM_CODEC = new StreamCodec<>()
    {
        @SuppressWarnings("null")
        @Override public RouteSurveyRecord decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            Optional<DimPos> origin = ByteBufCodecs.optional(DimPos.STREAM_CODEC).decode(buf);
            Optional<DimPos> destination = ByteBufCodecs.optional(DimPos.STREAM_CODEC).decode(buf);
            return new RouteSurveyRecord(origin, destination, buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        @SuppressWarnings("null")
        @Override public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull RouteSurveyRecord record)
        {
            ByteBufCodecs.optional(DimPos.STREAM_CODEC).encode(buf, record.origin());
            ByteBufCodecs.optional(DimPos.STREAM_CODEC).encode(buf, record.destination());
            buf.writeUtf(record.originName()); buf.writeUtf(record.destinationName());
            buf.writeVarInt(record.originColonyId()); buf.writeVarInt(record.destinationColonyId());
        }
    };

    public static RouteSurveyRecord empty()
    {
        return new RouteSurveyRecord(Optional.empty(), Optional.empty(), "", "", -1, -1);
    }

    public boolean isComplete()
    {
        return origin.isPresent() && destination.isPresent();
    }

    public RouteSurveyRecord withOrigin(DimPos endpoint, @Nonnull String name, int colonyId)
    {
        return new RouteSurveyRecord(Optional.of(endpoint), Optional.empty(), name, "", colonyId, -1);
    }

    public RouteSurveyRecord withDestination(DimPos endpoint, @Nonnull String name, int colonyId)
    {
        return new RouteSurveyRecord(origin, Optional.of(endpoint), originName, name, originColonyId, colonyId);
    }
}
