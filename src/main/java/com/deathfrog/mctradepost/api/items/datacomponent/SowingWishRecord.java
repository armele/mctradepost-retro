package com.deathfrog.mctradepost.api.items.datacomponent;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.BuildingUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Non-authoritative reference from a sowing wish to a configured MineColonies farm field. */
public record SowingWishRecord(int colonyId, String dimension, BlockPos fieldPos, String cropName,
    ItemStack sealedSeed, UUID bindingPlayer)
{
    @SuppressWarnings("null")
    public static final Codec<SowingWishRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("colony_id").forGetter(SowingWishRecord::colonyId),
        Codec.STRING.fieldOf(BuildingUtil.TAG_DIMENSION).forGetter(SowingWishRecord::dimension),
        BlockPos.CODEC.fieldOf("field_pos").forGetter(SowingWishRecord::fieldPos),
        Codec.STRING.fieldOf("crop_name").forGetter(SowingWishRecord::cropName),
        ItemStack.CODEC.fieldOf("sealed_seed").forGetter(SowingWishRecord::sealedSeed),
        Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("binding_player").forGetter(SowingWishRecord::bindingPlayer))
        .apply(instance, SowingWishRecord::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SowingWishRecord> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public SowingWishRecord decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            return new SowingWishRecord(buf.readVarInt(), buf.readUtf(), buf.readBlockPos(), buf.readUtf(),
                ItemStack.STREAM_CODEC.decode(buf), buf.readUUID());
        }

        @SuppressWarnings("null")
        @Override
        public void encode(RegistryFriendlyByteBuf buf, SowingWishRecord value)
        {
            buf.writeVarInt(value.colonyId());
            buf.writeUtf(value.dimension());
            buf.writeBlockPos(value.fieldPos());
            buf.writeUtf(value.cropName());
            ItemStack.STREAM_CODEC.encode(buf, value.sealedSeed());
            buf.writeUUID(value.bindingPlayer());
        }
    };
}
