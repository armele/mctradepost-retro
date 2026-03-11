package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.api.client.render.modeltype.MCTPSimpleModelType;
import com.deathfrog.mctradepost.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.client.render.modeltype.registry.IModelTypeRegistry;
import com.minecolonies.core.client.model.FemaleAlchemistModel;
import com.minecolonies.core.client.model.FemaleCrafterModel;
import com.minecolonies.core.client.model.MaleAlchemistModel;
import com.minecolonies.core.client.model.MaleCrafterModel;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.deathfrog.mctradepost.core.client.model.FemaleGuestServicesModel;
import com.deathfrog.mctradepost.core.client.model.MaleGuestServicesModel;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;

public class ModModelTypeInitializer
{
    private ModModelTypeInitializer()
    {
        throw new IllegalStateException("Tried to initialize: MCTradePost.ModModelTypeInitializer but this is a Utility class.");
    }
    /**
     * Initializes the model types for the mod. This method is only called on the client side.
     * 
     * NOTE: Entities are currently using default models. This is a placeholder for future custom model implementations.
     * 
     * @param context The context for the entity renderer provider.
     */
    @SuppressWarnings("null")
    @OnlyIn(Dist.CLIENT)
    public static void init(final EntityRendererProvider.Context context)
    {
        final IModelTypeRegistry reg = IModelTypeRegistry.getInstance();

        ModModelTypes.SHOPKEEPER = new MCTPSimpleModelType(ModModelTypes.SHOPKEEPER_MODEL_ID, 1, 
            new MaleAlchemistModel(context.bakeLayer(ModelRegistryHandler.MALE_SHOPKEEPER)), 
            new FemaleAlchemistModel(context.bakeLayer(ModelRegistryHandler.FEMALE_SHOPKEEPER)));
            
        reg.register(ModModelTypes.SHOPKEEPER);

        ModModelTypes.GUESTSERVICES = new MCTPSimpleModelType(ModModelTypes.SHOPKEEPER_MODEL_ID, 1, 
            new MaleGuestServicesModel(context.bakeLayer(ModelRegistryHandler.MALE_GUESTSERVICES)), 
            new FemaleGuestServicesModel(context.bakeLayer(ModelRegistryHandler.FEMALE_GUESTSERVICES)));
            
        reg.register(ModModelTypes.GUESTSERVICES);

        ModModelTypes.RECYCLINGENGINEER = new MCTPSimpleModelType(ModModelTypes.RECYCLINGENGINEER_MODEL_ID, 1, 
            new MaleCrafterModel(context.bakeLayer(ModelRegistryHandler.MALE_RECYCLINGENGINEER)), 
            new FemaleCrafterModel(context.bakeLayer(ModelRegistryHandler.FEMALE_RECYCLINGENGINEER)));

        reg.register(ModModelTypes.RECYCLINGENGINEER);

        ModModelTypes.BARTENDER = new MCTPSimpleModelType(ModModelTypes.BARTENDER_MODEL_ID, 1, 
            new MaleCrafterModel(context.bakeLayer(ModelRegistryHandler.MALE_BARTENDER)), 
            new FemaleCrafterModel(context.bakeLayer(ModelRegistryHandler.FEMALE_BARTENDER)));

        reg.register(ModModelTypes.BARTENDER);

        ModModelTypes.ANIMALTRAINER = new MCTPSimpleModelType(ModModelTypes.ANIMALTRAINER_MODEL_ID, 1, 
            new MaleCrafterModel(context.bakeLayer(ModelRegistryHandler.MALE_ANIMALTRAINER)), 
            new FemaleCrafterModel(context.bakeLayer(ModelRegistryHandler.FEMALE_ANIMALTRAINER)));

        reg.register(ModModelTypes.ANIMALTRAINER);

        ModModelTypes.DAIRYWORKER = new MCTPSimpleModelType(ModModelTypes.DAIRYWORKER_MODEL_ID, 1, 
            new MaleCrafterModel(context.bakeLayer(ModelRegistryHandler.MALE_DAIRYWORKER)), 
            new FemaleCrafterModel(context.bakeLayer(ModelRegistryHandler.FEMALE_DAIRYWORKER)));

        reg.register(ModModelTypes.DAIRYWORKER);


        ModModelTypes.STEWMELIER = new MCTPSimpleModelType(ModModelTypes.STEWMELIER_MODEL_ID, 1, 
            new MaleCrafterModel(context.bakeLayer(ModelRegistryHandler.MALE_STEWMELIER)), 
            new FemaleCrafterModel(context.bakeLayer(ModelRegistryHandler.FEMALE_STEWMELIER)));

        reg.register(ModModelTypes.STEWMELIER);

    }
}
