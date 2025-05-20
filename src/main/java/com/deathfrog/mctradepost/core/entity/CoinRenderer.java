package com.deathfrog.mctradepost.core.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

public class CoinRenderer extends ThrownItemRenderer<CoinEntity> {
    public CoinRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    // TODO: [Enhancement: Spin the coin]
}
