package com.deathfrog.mctradepost.core.entity.ai.workers.trade;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A block position qualified by its dimension.
 * <p>
 * Track routes can cross dimensions, so a raw {@link BlockPos} is not enough to identify a rail endpoint unambiguously.
 *
 * @param dimension the dimension containing the position
 * @param pos the block position within the dimension
 */
public record DimPos(@Nonnull ResourceKey<Level> dimension, @Nonnull BlockPos pos)
{
    /**
     * Codec used to persist dimensional positions into item data components and building NBT.
     */
    @SuppressWarnings("null")
    public static final Codec<DimPos> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("dimension").forGetter(dimPos -> dimPos.dimension().location()),
        BlockPos.CODEC.fieldOf("pos").forGetter(DimPos::pos))
        .apply(instance, DimPos::fromResourceLocation));

    /**
     * Network codec used when synchronizing dimensional route and linkage data.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DimPos> STREAM_CODEC = new StreamCodec<>()
    {
        @Override
        public DimPos decode(@Nonnull RegistryFriendlyByteBuf buf)
        {
            ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buf);
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
            return fromResourceLocation(dimension, pos);
        }

        @SuppressWarnings("null")
        @Override
        public void encode(@Nonnull RegistryFriendlyByteBuf buf, @Nonnull DimPos dimPos)
        {
            ResourceLocation.STREAM_CODEC.encode(buf, dimPos.dimension().location());
            BlockPos.STREAM_CODEC.encode(buf, dimPos.pos());
        }
    };

    /**
     * Creates a dimensional position from a serialized dimension id and block position.
     *
     * @param dimension serialized dimension id
     * @param pos block position in that dimension
     * @return dimensional position for the supplied dimension and block
     */
    @SuppressWarnings("null")
    public static DimPos fromResourceLocation(ResourceLocation dimension, BlockPos pos)
    {
        ResourceKey<Level> levelKey = ResourceKey.create(NullnessBridge.assumeNonnull(Registries.DIMENSION),
            NullnessBridge.requireNonnull(dimension, "Null dimension for DimPos."));
        return new DimPos(levelKey, NullnessBridge.requireNonnull(pos, "Null position for DimPos."));
    }

    /**
     * @return true when this position is in the Overworld
     */
    public boolean isOverworld()
    {
        return Level.OVERWORLD.equals(dimension);
    }

    /**
     * @return true when this position is in the Nether
     */
    public boolean isNether()
    {
        return Level.NETHER.equals(dimension);
    }

    /**
     * @return compact human-readable dimension and position text for chat/debug output
     */
    public String shortDescription()
    {
        return dimension.location() + " " + pos.toShortString();
    }
}
