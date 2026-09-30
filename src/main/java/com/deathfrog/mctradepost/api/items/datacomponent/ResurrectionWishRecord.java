package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Lightweight, non-authoritative reference from a resurrection wish to a recorded burial. */
public record ResurrectionWishRecord(UUID burialId, int colonyId, String dimension, BlockPos gravePos,
    String citizenName, int burialDay, UUID bindingPlayer)
{
    @SuppressWarnings("null")
    public static final Codec<ResurrectionWishRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("burial_id").forGetter(ResurrectionWishRecord::burialId),
        Codec.INT.fieldOf("colony_id").forGetter(ResurrectionWishRecord::colonyId),
        Codec.STRING.fieldOf(BuildingUtil.TAG_DIMENSION).forGetter(ResurrectionWishRecord::dimension),
        BlockPos.CODEC.fieldOf("grave_pos").forGetter(ResurrectionWishRecord::gravePos),
        Codec.STRING.fieldOf("citizen_name").forGetter(ResurrectionWishRecord::citizenName),
        Codec.INT.fieldOf("burial_day").forGetter(ResurrectionWishRecord::burialDay),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("binding_player").forGetter(ResurrectionWishRecord::bindingPlayer))
        .apply(instance, ResurrectionWishRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResurrectionWishRecord> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public ResurrectionWishRecord decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            return new ResurrectionWishRecord(buf.readUUID(), buf.readVarInt(), buf.readUtf(), buf.readBlockPos(),
                buf.readUtf(), buf.readVarInt(), buf.readUUID());
        }

        @SuppressWarnings("null")
        @Override
        public void encode(RegistryFriendlyByteBuf buf, ResurrectionWishRecord value)
        {
            buf.writeUUID(value.burialId());
            buf.writeVarInt(value.colonyId());
            buf.writeUtf(value.dimension());
            buf.writeBlockPos(value.gravePos());
            buf.writeUtf(value.citizenName());
            buf.writeVarInt(value.burialDay());
            buf.writeUUID(value.bindingPlayer());
        }
    };
}
