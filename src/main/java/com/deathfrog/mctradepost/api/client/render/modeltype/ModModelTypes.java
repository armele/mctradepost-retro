package com.deathfrog.mctradepost.api.client.render.modeltype;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.client.render.modeltype.IModelType;
import net.minecraft.resources.ResourceLocation;

public final class ModModelTypes
{

    public static final ResourceLocation SHOPKEEPER_ID = ResourceLocation.parse(MCTradePostMod.MODID +":shopkeeper");

    public static IModelType SHOPKEEPER;

    private ModModelTypes()
    {
        throw new IllegalStateException("Tried to initialize: ModModelTypes but this is a Utility class.");
    }
}
