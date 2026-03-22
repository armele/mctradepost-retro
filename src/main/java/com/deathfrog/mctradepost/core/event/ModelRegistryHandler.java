package com.deathfrog.mctradepost.core.event;


import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.core.client.model.FemaleAlchemistModel;
import com.minecolonies.core.client.model.FemaleCrafterModel;
import com.minecolonies.core.client.model.MaleAlchemistModel;
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
    public static final ModelLayerLocation MALE_STATIONMASTER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_stationmaster"), "male_stationmaster");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_STATIONMASTER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_stationmaster"), "female_stationmaster");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_BARTENDER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_bartender"), "male_bartender");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_BARTENDER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_bartender"), "female_bartender");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_ANIMALTRAINER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_animaltrainer"), "male_animaltrainer");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_ANIMALTRAINER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_animaltrainer"), "female_animaltrainer");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_DAIRYWORKER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_dairyworker"), "male_dairyworker");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_DAIRYWORKER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_dairyworker"), "female_dairyworker");

    @SuppressWarnings("null")
    public static final ModelLayerLocation MALE_STEWMELIER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "male_stewmelier"), "male_stewmelier");
    @SuppressWarnings("null")
    public static final ModelLayerLocation FEMALE_STEWMELIER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "female_stewmelier"), "female_stewmelier");

    @SuppressWarnings("null")
    public static void registerModels(EntityRenderersEvent.RegisterLayerDefinitions event) 
    {
        event.registerLayerDefinition(MALE_SHOPKEEPER, MaleAlchemistModel::createMesh);
        event.registerLayerDefinition(FEMALE_SHOPKEEPER, FemaleAlchemistModel::createMesh);

        event.registerLayerDefinition(MALE_GUESTSERVICES, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_GUESTSERVICES, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_RECYCLINGENGINEER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_RECYCLINGENGINEER, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_STATIONMASTER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_STATIONMASTER, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_BARTENDER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_BARTENDER, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_ANIMALTRAINER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_ANIMALTRAINER, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_STEWMELIER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_STEWMELIER, FemaleCrafterModel::createMesh);

        event.registerLayerDefinition(MALE_DAIRYWORKER, MaleCrafterModel::createMesh);
        event.registerLayerDefinition(FEMALE_DAIRYWORKER, FemaleCrafterModel::createMesh);
    }
}
