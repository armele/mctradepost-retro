package com.deathfrog.mctradepost.core.event;


import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.model.FemaleGuestServicesModel;
import com.deathfrog.mctradepost.core.client.model.FemaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.MaleGuestServicesModel;
import com.deathfrog.mctradepost.core.client.model.MaleShopkeeperModel;
import com.minecolonies.core.client.model.FemaleCrafterModel;
import com.minecolonies.core.client.model.MaleCrafterModel;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

@OnlyIn(Dist.CLIENT)
public class ModelRegistryHandler
{
    /**
     * These are currently unused until usable citizen skins can be designed.
     * Note that MineColonies citizens use custom models and the skins need to be designed around those models.
    */

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_SHOPKEEPER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_shopkeeper"), "male_shopkeeper");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_SHOPKEEPER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_shopkeeper"), "female_shopkeeper");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_GUESTSERVICES = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_guestservices"), "male_guestservices");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_GUESTSERVICES = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_guestservices"), "female_guestservices");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_RECYCLINGENGINEER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_recyclingengineer"), "male_recyclingengineer");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_RECYCLINGENGINEER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_recyclingengineer"), "female_recyclingengineer");

    @SuppressWarnings("null")
    public static void registerModels(EntityRenderersEvent.RegisterLayerDefinitions event) 
    {
        event.registerLayerDefinition(MALE_SHOPKEEPER, MaleShopkeeperModel::createMesh);
        event.registerLayerDefinition(FEMALE_SHOPKEEPER, FemaleShopkeeperModel::createMesh);

        event.registerLayerDefinition(MALE_GUESTSERVICES, MaleGuestServicesModel::createMesh);
        event.registerLayerDefinition(FEMALE_GUESTSERVICES, FemaleGuestServicesModel::createMesh);

        event.registerLayerDefinition(MALE_RECYCLINGENGINEER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_RECYCLINGENGINEER, FemaleCrafterModel::createMesh);
    }
}
