package com.deathfrog.mctradepost.api.client.render.modeltype;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.client.render.modeltype.IModelType;
import net.minecraft.resources.ResourceLocation;

public final class ModModelTypes
{

    public static final ResourceLocation SHOPKEEPER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "shopkeeper");
    public static final ResourceLocation GUESTSERVICES_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "guestservices");

    public static IModelType SHOPKEEPER;
    public static IModelType GUESTSERVICES;

    private ModModelTypes()
    {
        throw new IllegalStateException("Tried to initialize: ModModelTypes but this is a Utility class.");
    }
}
