package com.deathfrog.mctradepost.core.client.render.souvenir;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class SouvenirItemExtension implements IClientItemExtensions
{
    // Cache our BEWLR in a field.
    private final SouvenirISTER souvenirRenderer = new SouvenirISTER();

    // Return our BEWLR here.
    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer()
    {
        return souvenirRenderer;
    }
}
