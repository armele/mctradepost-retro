package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component payload stored on a Dimensional Linkage item.
 * <p>
 * The item records one Overworld endpoint and one Nether endpoint. The id distinguishes otherwise-identical endpoint pairs so route
 * building can avoid using the same installed linkage as both entry and exit for Overworld-to-Overworld routing.
 *
 * @param id stable identity for this linkage item
 * @param overworldEndpoint optional Overworld transfer track
 * @param netherEndpoint optional Nether transfer track
 */
public record DimensionalLinkageRecord(@Nonnull UUID id, Optional<DimPos> overworldEndpoint, Optional<DimPos> netherEndpoint)
{
    /**
     * Codec used to persist this linkage in item data components and building module NBT.
     */
    @SuppressWarnings("null")
    public static final Codec<DimensionalLinkageRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("id").forGetter(DimensionalLinkageRecord::id),
        DimPos.CODEC.optionalFieldOf("overworldEndpoint").forGetter(DimensionalLinkageRecord::overworldEndpoint),
        DimPos.CODEC.optionalFieldOf("netherEndpoint").forGetter(DimensionalLinkageRecord::netherEndpoint))
        .apply(instance, DimensionalLinkageRecord::new));

    /**
     * Network codec used when synchronizing installed linkages to the station connection GUI.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DimensionalLinkageRecord> STREAM_CODEC = new StreamCodec<>()
    {
        @SuppressWarnings("null")
        @Override
        public DimensionalLinkageRecord decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            UUID id = buf.readUUID();
            Optional<DimPos> overworld = readOptionalDimPos(buf);
            Optional<DimPos> nether = readOptionalDimPos(buf);
            return new DimensionalLinkageRecord(id, overworld, nether);
        }

        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull DimensionalLinkageRecord record)
        {
            buf.writeUUID(record.id());
            writeOptionalDimPos(buf, record.overworldEndpoint());
            writeOptionalDimPos(buf, record.netherEndpoint());
        }
    };

    /**
     * Creates a blank linkage record with a fresh identity.
     *
     * @return an empty linkage record
     */
    @SuppressWarnings("null")
    public static DimensionalLinkageRecord empty()
    {
        return new DimensionalLinkageRecord(UUID.randomUUID(), Optional.empty(), Optional.empty());
    }

    /**
     * Returns a copy of this record with the supplied endpoint recorded in the matching dimension slot.
     * <p>
     * Positions outside the Overworld and Nether are ignored.
     *
     * @param endpoint endpoint to record
     * @return updated linkage record, or this record for unsupported dimensions
     */
    public DimensionalLinkageRecord withEndpoint(DimPos endpoint)
    {
        if (endpoint.isOverworld())
        {
            return new DimensionalLinkageRecord(id, Optional.of(endpoint), netherEndpoint);
        }
        if (endpoint.isNether())
        {
            return new DimensionalLinkageRecord(id, overworldEndpoint, Optional.of(endpoint));
        }
        return this;
    }

    /**
     * @return true when both Overworld and Nether endpoints have been recorded
     */
    public boolean isComplete()
    {
        return overworldEndpoint.isPresent() && netherEndpoint.isPresent();
    }

    private static Optional<DimPos> readOptionalDimPos(RegistryFriendlyByteBuf buf)
    {
        if (!buf.readBoolean())
        {
            return Optional.empty();
        }
        return Optional.of(DimPos.STREAM_CODEC.decode(buf));
    }

    @SuppressWarnings("null")
    private static void writeOptionalDimPos(RegistryFriendlyByteBuf buf, Optional<DimPos> endpoint)
    {
        buf.writeBoolean(endpoint.isPresent());
        endpoint.ifPresent(dimPos -> DimPos.STREAM_CODEC.encode(buf, dimPos));
    }
}
