package com.deathfrog.mctradepost.core.client.render.souvenir;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class SouvenirLoader implements IGeometryLoader<SouvenirGeometry> {
    // It is highly recommended to use a singleton pattern for geometry loaders, as all models can be loaded through one loader.
    public static final SouvenirLoader INSTANCE = new SouvenirLoader();
    // The id we will use to register this loader. Also used in the loader datagen class.
    public static final ResourceLocation LOADER_ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "souvenir_loader");
    
    // In accordance with the singleton pattern, make the constructor private.        
    private SouvenirLoader() {}
    
    @Override
    public SouvenirGeometry read(@Nonnull JsonObject jsonObject, @Nonnull JsonDeserializationContext context) throws JsonParseException {
        // Use the given JsonObject and, if needed, the JsonDeserializationContext to get properties from the model JSON.

        return new SouvenirGeometry();
    }
}