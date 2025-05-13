package com.deathfrog.mctradepost.core.client.render;

import com.deathfrog.mctradepost.apiimp.initializer.ModModelTypeInitializer;
import com.minecolonies.core.client.render.RenderBipedCitizen;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;

public class MCTPRenderBipedCitizen extends RenderBipedCitizen {

    public MCTPRenderBipedCitizen(Context context) {
        super(context);
        ModModelTypeInitializer.init(context);
    }
    
}
