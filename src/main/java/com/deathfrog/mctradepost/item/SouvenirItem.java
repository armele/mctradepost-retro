package com.deathfrog.mctradepost.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;

public class SouvenirItem extends Item
{
    public record SouvenirRecord(@Nonnull String originalItem, int itemValue)
    {
        @SuppressWarnings("null")
        public static final Codec<SouvenirRecord> CODEC = RecordCodecBuilder.create(
            builder -> builder
                .group(Codec.STRING.fieldOf("originalItem").forGetter(SouvenirRecord::originalItem),
                    Codec.INT.fieldOf("itemValue").forGetter(SouvenirRecord::itemValue))
                .apply(builder, SouvenirRecord::new));

        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, SouvenirRecord> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8,
                SouvenirRecord::originalItem,
                ByteBufCodecs.VAR_INT,
                SouvenirRecord::itemValue,
                SouvenirRecord::new);

        /**
         * Converts the original item description ID stored in this SouvenirRecord to an actual Item object using the built-in item
         * registry.
         * 
         * @return the Item corresponding to the original item description ID.
         */
        public Item asOriginalItem()
        {
            return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(originalItem)).orElse(net.minecraft.world.item.Items.AIR);
        }
    }

    public SouvenirItem(@Nonnull Properties properties)
    {
        super(properties);
    }

    /**
     * Returns the original item of the given souvenir item stack.
     * 
     * @param stack the souvenir item stack
     * @return the original item
     */
    public static Item getOriginal(ItemStack stack)
    {
        SouvenirRecord souvenirRecord = stack.get(souvenirComponent());

        // MCTradePostMod.LOGGER.info("Getting original item based on : {} ", souvenirRecord.originalItem);

        return souvenirRecord.asOriginalItem();
    }

    /**
     * Returns the souvenir value associated with the given souvenir item stack. This is the value that is used to determine the price
     * of the souvenir when it is sold.
     * 
     * @param stack the souvenir item stack
     * @return the souvenir value
     */
    public static int getSouvenirValue(ItemStack stack)
    {
        SouvenirRecord souvenirRecord = stack.get(souvenirComponent());

        if (souvenirRecord == null)
        {
            return 0;
        }

        return souvenirRecord.itemValue;
    }

    /**
     * Creates a souvenir item stack from the given original item and value.
     * 
     * @param original the original item
     * @param value    the souvenir value
     * @return the souvenir item stack
     */
    public static ItemStack createSouvenir(@Nonnull Item original, int value)
    {
        ItemStack originalStack = new ItemStack(original);
        return createSouvenir(originalStack, value);
    }

    /**
     * Creates a souvenir item stack from the given original item stack and value. The souvenir item stack is created with the same
     * count as the original item stack. The souvenir value is stored in the souvenir item stack as a custom data component.
     * 
     * @param original the original item stack
     * @param value    the souvenir value
     * @return the souvenir item stack
     */
    public static ItemStack createSouvenir(@Nonnull ItemStack original, int value)
    {
        Item originalItem = original.getItem();
        
        if (original.isEmpty() || originalItem == null || net.minecraft.world.item.Items.AIR.equals(originalItem))
        {
            return ItemStack.EMPTY;
        }

        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(originalItem);
        ItemStack stack = new ItemStack(NullnessBridge.assumeNonnull(MCTradePostMod.SOUVENIR.get()));
        stack.setCount(original.getCount());

        SouvenirRecord souvenirRecord = new SouvenirRecord(registryName.toString() + "", value);
        stack.set(souvenirComponent(), souvenirRecord);

        return stack;
    }

    /**
     * Returns a string representation of the souvenir item stack. The string includes the original item and the souvenir value.
     *
     * @param stack the souvenir item stack
     * @return the string representation of the souvenir item
     */
    public static String toString(ItemStack stack)
    {
        return "SouvenirItem {" + "originalItem=" + getOriginal(stack) + ", souvenirValue=" + getSouvenirValue(stack) + '}';
    }

    /**
     * Appends information to the item's tooltip.
     * <p>
     * This shows a short line of text, and if Shift is held, provides additional information.
     * </p>
     * @param stack the item stack to generate the tooltip for
     * @param context the tooltip context
     * @param tooltip the list of components to append to
     * @param flag the tooltip flag
     */
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context, @Nonnull List<Component> tooltip, @Nonnull TooltipFlag flag)
    {
        super.appendHoverText(stack, context, tooltip, flag);

        final SouvenirRecord rec = stack.get(souvenirComponent());
        if (rec == null)
        {
            return;
        }

        // Resolve original item safely (falls back to AIR if missing)
        Item originalItem;
        try
        {
            originalItem = rec.asOriginalItem();
        }
        catch (Exception e)
        {
            originalItem = Items.AIR;
        }

        if (originalItem != Items.AIR)
        {
            boolean hasCustomName = stack.has(NullnessBridge.assumeNonnull(DataComponents.CUSTOM_NAME));
            if (!hasCustomName) 
            {
                Component display = buildSouvenirDisplayName(stack);
                if (display != null) 
                {
                    tooltip.add(display.copy().withStyle(ChatFormatting.GRAY));
                }
            }
        }

        int value = getSouvenirValue(stack);
        if (value > 0)
        {
            tooltip.add(Component.translatable("item.mctradepost.tooltip.souvenir_value", value).withStyle(ChatFormatting.DARK_GREEN));
        }
    }

    /**
     * Builds a Component that represents the display name of a souvenir item.
     * This will be "Souvenir: <original item name>".
     * If the souvenir item stack is missing its SouvenirRecord or the original item is AIR, returns null.
     * @param stack the souvenir item stack
     * @return the display name of the souvenir item, or null if not applicable
     */
    public static Component buildSouvenirDisplayName(ItemStack stack)
    {
        final SouvenirRecord rec = stack.get(souvenirComponent());
        if (rec == null) 
        {
            return null;
        }

        final Item original = rec.asOriginalItem();
        if (original == null || original == net.minecraft.world.item.Items.AIR) return null;

        Component origName = new ItemStack(original).getHoverName();
        return Component.translatable("item.mctradepost.tooltip.souvenir_of", origName);
    }

    /**
     * Returns the DataComponentType for SouvenirRecord.
     * This is used to store and retrieve SouvenirRecord data from ItemStacks.
     * If the souvenir component is not initialized, an IllegalStateException is thrown.
     * This should not happen in normal circumstances, but if it does, report it to the mod author.
     * @return the DataComponentType for SouvenirRecord
     */
    private static @Nonnull DeferredHolder<DataComponentType<?>, DataComponentType<SouvenirRecord>> souvenirComponent()
    {
        DeferredHolder<DataComponentType<?>, DataComponentType<SouvenirRecord>> souvenirComponent = MCTPModDataComponents.SOUVENIR_COMPONENT;

        if (souvenirComponent == null)
        {
            throw new IllegalStateException("Souvenir component not initialized. This should not happen. Report this to the mod author.");
        }

        return souvenirComponent;
    }
}
