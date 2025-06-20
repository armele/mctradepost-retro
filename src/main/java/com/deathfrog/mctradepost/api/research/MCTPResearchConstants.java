package com.deathfrog.mctradepost.api.research;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.resources.ResourceLocation;

public class MCTPResearchConstants {

    public static final ResourceLocation RESEARCH_DISENCHANTING = getResearchEffectID("disenchanting");
    public static final ResourceLocation TOURISTS = getResearchEffectID("tourists");

    public static ResourceLocation getResearchEffectID(String researchName) {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/" + researchName);
    }

}
