package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Client-side module view for the husbandry animals available from the pet shop.
 */
public class HerdAnimalsModuleView extends PetTrainingItemsModuleView
{
    @Override
    public boolean displaysPets()
    {
        return false;
    }

    @Override
    public boolean requiresHusbandryResearch()
    {
        return true;
    }

    @Override
    public Component getItemsTooltip()
    {
        return Component.translatable("com.minecolonies.coremod.gui.petstore.trainingtips.herd.hover");
    }

    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/herdanimals.png");
    }

    @Override
    public @Nullable Component getDesc()
    {
        return Component.translatable("com.minecolonies.coremod.gui.petstore.herdanimals");
    }
}
