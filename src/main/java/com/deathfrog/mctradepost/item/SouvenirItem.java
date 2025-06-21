package com.deathfrog.mctradepost.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;

public class SouvenirItem extends Item {

    public record SouvenirRecord(String originalItem, int itemValue) {

        public static final Codec<SouvenirRecord> CODEC = RecordCodecBuilder.create(builder -> builder
            .group( Codec.STRING.fieldOf("originalItem").forGetter(SouvenirRecord::originalItem),
                    Codec.INT.fieldOf("itemValue").forGetter(SouvenirRecord::itemValue))
            .apply(builder, SouvenirRecord::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, SouvenirRecord> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SouvenirRecord::originalItem,
            ByteBufCodecs.VAR_INT,
            SouvenirRecord::itemValue,
            SouvenirRecord::new
        );
    
        /**
         * Converts the original item description ID stored in this SouvenirRecord 
         * to an actual Item object using the built-in item registry.
         * 
         * @return the Item corresponding to the original item description ID.
         */
        public Item asOriginalItem() {
            // MCTradePostMod.LOGGER.info("asOriginalItem doing a registry search for: {} ", originalItem);

            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(originalItem));
        }
    }

    public SouvenirItem(Properties properties) {
        super(properties);
    }
    
    /**
     * Returns the original item of the given souvenir item stack.
     * 
     * @param stack the souvenir item stack
     * @return the original item
     */
    public static Item getOriginal(ItemStack stack) {
        SouvenirRecord souvenirRecord = stack.get(MCTPModDataComponents.SOUVENIR_COMPONENT);

        // MCTradePostMod.LOGGER.info("Getting original item based on : {} ", souvenirRecord.originalItem);

        return souvenirRecord.asOriginalItem();
    }
  
    /**
     * Returns the souvenir value associated with the given souvenir item stack.
     * This is the value that is used to determine the price of the souvenir when it is sold.
     * 
     * @param stack the souvenir item stack
     * @return the souvenir value
     */
    public static int getSouvenirValue(ItemStack stack) {
        SouvenirRecord souvenirRecord = stack.get(MCTPModDataComponents.SOUVENIR_COMPONENT);

        if (souvenirRecord == null) {
            return 0;
        }
        
        return souvenirRecord.itemValue;
    }
  
    /**
     * Creates a souvenir item stack from the given original item and value.
     * 
     * @param original the original item
     * @param value the souvenir value
     * @return the souvenir item stack
     */
    public static ItemStack createSouvenir(Item original, int value) {
        ItemStack originalStack = new ItemStack(original);
        return createSouvenir(originalStack, value);
    }

    /**
     * Creates a souvenir item stack from the given original item stack and value.
     * The souvenir item stack is created with the same count as the original item stack.
     * The souvenir value is stored in the souvenir item stack as a custom data component.
     * 
     * @param original the original item stack
     * @param value the souvenir value
     * @return the souvenir item stack
     */
    public static ItemStack createSouvenir(ItemStack original, int value) {
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(original.getItem());
        ItemStack stack = new ItemStack(MCTradePostMod.SOUVENIR.get());
        stack.setCount(original.getCount());

        SouvenirRecord souvenirRecord = new SouvenirRecord(registryName.toString(), value);
        stack.set(MCTPModDataComponents.SOUVENIR_COMPONENT, souvenirRecord);
        
        return stack;
    }

    /**
     * Returns a string representation of the souvenir item stack.
     * The string includes the original item and the souvenir value.
     *
     * @param stack the souvenir item stack
     * @return the string representation of the souvenir item
     */
    public static String toString(ItemStack stack) {
        return "SouvenirItem {" +
            "originalItem=" + getOriginal(stack) +
            ", souvenirValue=" + getSouvenirValue(stack) +
            '}';
    }


}