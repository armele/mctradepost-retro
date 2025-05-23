package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.api.client.render.modeltype.MCTPSimpleModelType;
import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.deathfrog.mctradepost.core.client.model.FemaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.MaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.FemaleGuestServicesModel;
import com.deathfrog.mctradepost.core.client.model.MaleGuestServicesModel;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;

public class ModModelTypeInitializer
{
    private ModModelTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: MCTradePost.ModModelTypeInitializer but this is a Utility class.");
    }
    @OnlyIn(Dist.CLIENT)
    public static void init(final EntityRendererProvider.Context context)
    {
        final IModelTypeRegistry reg = IModelTypeRegistry.getInstance();

        ModModelTypes.SHOPKEEPER = new MCTPSimpleModelType(ModModelTypes.SHOPKEEPER_MODEL_ID, 1, 
            new MaleShopkeeperModel(context.bakeLayer(ModelRegistryHandler.MALE_SHOPKEEPER)), 
            new FemaleShopkeeperModel(context.bakeLayer(ModelRegistryHandler.FEMALE_SHOPKEEPER)));
            
        reg.register(ModModelTypes.SHOPKEEPER);

        ModModelTypes.GUESTSERVICES = new MCTPSimpleModelType(ModModelTypes.SHOPKEEPER_MODEL_ID, 1, 
            new MaleGuestServicesModel(context.bakeLayer(ModelRegistryHandler.MALE_GUESTSERVICES)), 
            new FemaleGuestServicesModel(context.bakeLayer(ModelRegistryHandler.FEMALE_GUESTSERVICES)));
            
        reg.register(ModModelTypes.SHOPKEEPER);
    }
}
