package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = MCTradePostMod.MODID)
public class TooltipHandler
{
    /**
     * Adds Trade Post-specific explanatory text to supported item tooltips.
     *
     * @param event tooltip event containing the displayed item and mutable tooltip
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();

        if (stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.WISH_PLENTY.get())))
        {
            event.getToolTip().add(Component.translatable("com.mctradepost.wishplenty.tooltip"));
        }
        else if (stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.WISH_HEALTH.get())))
        {
            event.getToolTip().add(Component.translatable("com.mctradepost.wishhealth.tooltip"));
        }
        else if (stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.WISH_SHELTER.get())))
        {
            event.getToolTip().add(Component.translatable("com.mctradepost.wishshelter.tooltip"));
        }
        else if (stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.WISH_MOON.get())))
        {
            event.getToolTip().add(Component.translatable("com.mctradepost.wishmoon.tooltip"));
        }
        else if (stack.is(NullnessBridge.assumeNonnull(MCTradePostMod.blockHutOutpost.get().asItem())))
        {
            event.getToolTip().add(Component.translatable("com.mctradepost.outpost.deprecated.tooltip"));
        }
    }
}
