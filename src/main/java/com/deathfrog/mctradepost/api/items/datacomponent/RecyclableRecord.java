package com.deathfrog.mctradepost.api.items.datacomponent;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RecyclableRecord(boolean isRecyclable)
{
    @SuppressWarnings("null")
    public static final Codec<RecyclableRecord> CODEC = RecordCodecBuilder
        .create(builder -> builder.group(Codec.BOOL.fieldOf("isRecyclable").forGetter(RecyclableRecord::isRecyclable))
            .apply(builder, RecyclableRecord::new));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, RecyclableRecord> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, RecyclableRecord::isRecyclable, RecyclableRecord::new);

    /**
     * Retrieves the value of isRecyclable from this RecyclableRecord.
     * @return the value of isRecyclable.
     */
    public boolean isRecyclable() {
        return isRecyclable;
    }

    /**
     * Retrieves the RecyclableRecord associated with the given ItemStack.
     * If the component is not present on the stack, a new RecyclableRecord is returned with isRecyclable set to false.
     * @param stack the ItemStack whose RecyclableRecord is to be retrieved.
     * @return the RecyclableRecord associated with the stack, or a new one with isRecyclable set to false if none is present.
     */
    @SuppressWarnings("null")
    public static RecyclableRecord fromStack(ItemStack stack) 
    {
        return stack.get(MCTPModDataComponents.RECYCLABLE_COMPONENT);
    }
}
