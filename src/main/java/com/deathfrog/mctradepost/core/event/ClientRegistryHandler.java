package com.deathfrog.mctradepost.core.event;


import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public class ClientRegistryHandler
{
    public static final ModelLayerLocation MALE_SHOPKEEPER = new ModelLayerLocation(ResourceLocation.parse(MCTradePostMod.MODID + ":male_shopkeeper"), "male_shopkeeper");
    public static final ModelLayerLocation FEMALE_SHOPKEEPER = new ModelLayerLocation(ResourceLocation.parse(MCTradePostMod.MODID + ":female_shopkeeper"), "female_shopkeeper");

}
