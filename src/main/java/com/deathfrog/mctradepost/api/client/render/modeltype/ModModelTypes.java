package com.deathfrog.mctradepost.api.client.render.modeltype;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.client.render.modeltype.IModelType;
import net.minecraft.resources.ResourceLocation;

public final class ModModelTypes
{

    public static final ResourceLocation SHOPKEEPER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "shopkeeper");
    public static final ResourceLocation GUESTSERVICES_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "guestservices");
    public static final ResourceLocation BARTENDER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "bartender");
    public static final ResourceLocation ANIMALTRAINER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "animaltrainer");
    public static final ResourceLocation RECYCLINGENGINEER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "recyclingengineer");
    public static final ResourceLocation DAIRYWORKER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "dairyworker");
    public static final ResourceLocation STEWMELIER_MODEL_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "stewmelier");


    public static IModelType SHOPKEEPER;
    public static IModelType GUESTSERVICES;
    public static IModelType BARTENDER;
    public static IModelType ANIMALTRAINER;
    public static IModelType RECYCLINGENGINEER;
    public static IModelType DAIRYWORKER;
    public static IModelType STEWMELIER;

    private ModModelTypes()
    {
        throw new IllegalStateException("Tried to initialize: ModModelTypes but this is a Utility class.");
    }
}
