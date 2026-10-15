package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.Optional;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The origin selected on an otherwise consumable Navigation Chart. */
public record NavigationChartRecord(Optional<DimPos> origin, @Nonnull String originName)
{
    @SuppressWarnings("null")
    public static final Codec<NavigationChartRecord> CODEC = RecordCodecBuilder.create(instance -> instance
        .group(DimPos.CODEC.optionalFieldOf("origin").forGetter(NavigationChartRecord::origin),
            Codec.STRING.optionalFieldOf("originName", "").forGetter(NavigationChartRecord::originName))
        .apply(instance, NavigationChartRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, NavigationChartRecord> STREAM_CODEC = new StreamCodec<>()
    {
        @SuppressWarnings("null")
        @Override
        public NavigationChartRecord decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            return new NavigationChartRecord(ByteBufCodecs.optional(DimPos.STREAM_CODEC).decode(buf), buf.readUtf());
        }

        @SuppressWarnings("null")
        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull NavigationChartRecord record)
        {
            ByteBufCodecs.optional(DimPos.STREAM_CODEC).encode(buf, record.origin());
            buf.writeUtf(record.originName());
        }
    };

    public static NavigationChartRecord empty()
    {
        return new NavigationChartRecord(Optional.empty(), "");
    }

    public NavigationChartRecord withOrigin(DimPos endpoint, @Nonnull String name)
    {
        return new NavigationChartRecord(Optional.of(endpoint), name);
    }
}
