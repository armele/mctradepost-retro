package com.deathfrog.mctradepost.item;

import java.util.List;
import javax.annotation.Nonnull;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Gasifier feedstock produced by a Recycling Engineer working in Scrap mode. */
public class ScrapPileItem extends Item
{
    public ScrapPileItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
        @Nonnull TooltipContext context,
        @Nonnull List<Component> tooltip,
        @Nonnull TooltipFlag flag)
    {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.mctradepost.scrap_pile.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
