package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Lightweight reference from a Companion Staff to its authoritative server-side companion record. */
public record CompanionStaffRecord(UUID bindingId)
{
    @SuppressWarnings("null")
    public static final Codec<CompanionStaffRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("binding_id").forGetter(CompanionStaffRecord::bindingId))
        .apply(instance, CompanionStaffRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionStaffRecord> STREAM_CODEC = new StreamCodec<>()
    {
        /**
         * Decodes a staff reference.
         *
         * @param buffer source network buffer
         * @return decoded reference
         */
        @Override
        public CompanionStaffRecord decode(@Nonnull RegistryFriendlyByteBuf buffer)
        {
            return new CompanionStaffRecord(buffer.readUUID());
        }

        /**
         * Encodes a staff reference.
         *
         * @param buffer destination network buffer
         * @param value reference to encode
         */
        @SuppressWarnings("null")
        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buffer, @Nonnull CompanionStaffRecord value)
        {
            buffer.writeUUID(value.bindingId());
        }
    };
}
