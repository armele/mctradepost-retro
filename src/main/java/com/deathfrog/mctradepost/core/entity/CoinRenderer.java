package com.deathfrog.mctradepost.core.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class CoinRenderer extends ItemEntityRenderer
{
    public CoinRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }
}
