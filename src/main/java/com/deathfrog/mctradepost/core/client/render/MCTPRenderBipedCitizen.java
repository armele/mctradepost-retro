package com.deathfrog.mctradepost.core.client.render;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.apiimp.initializer.ModModelTypeInitializer;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.client.render.RenderBipedCitizen;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;

public class MCTPRenderBipedCitizen extends RenderBipedCitizen {

    public MCTPRenderBipedCitizen(Context context) {
        super(context);
        ModModelTypeInitializer.init(context);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(final AbstractEntityCitizen entity) {
        return super.getTextureLocation(entity);
    }
}
