package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.api.client.render.modeltype.MCTPSimpleModelType;
import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.deathfrog.mctradepost.core.client.model.FemaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.MaleShopkeeperModel;
import com.deathfrog.mctradepost.core.event.ClientRegistryHandler;
public class ModModelTypeInitializer
{
    private ModModelTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: MCTradePost.ModModelTypeInitializer but this is a Utility class.");
    }

    // TODO: Design shopkeeper textures and models  
    // TODO: Set up and record shopkeeper sounds.  Investigate ModSoundEvents.

    public static void init(final EntityRendererProvider.Context context)
    {
        final IModelTypeRegistry reg = IModelTypeRegistry.getInstance();

        ModModelTypes.SHOPKEEPER = new MCTPSimpleModelType(ModModelTypes.SHOPKEEPER_MODEL_ID, 1, new MaleShopkeeperModel(context.bakeLayer(ClientRegistryHandler.MALE_SHOPKEEPER)), 
            new FemaleShopkeeperModel(context.bakeLayer(ClientRegistryHandler.FEMALE_SHOPKEEPER)));
        reg.register(ModModelTypes.SHOPKEEPER);
    }
}
