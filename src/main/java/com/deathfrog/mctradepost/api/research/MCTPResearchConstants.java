package com.deathfrog.mctradepost.api.research;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.resources.ResourceLocation;

public class MCTPResearchConstants {

    public static final ResourceLocation RESEARCH_DISENCHANTING = getResearchEffectID("disenchanting");
    public static final ResourceLocation TOURISTS = getResearchEffectID("tourists");
    public static final ResourceLocation TRADESPEED = getResearchEffectID("tradespeed");
    public static final ResourceLocation TRADECAPACITY = getResearchEffectID("tradecapacity");
    public static final ResourceLocation ADVERTISING = getResearchEffectID("advertising");
    public static final ResourceLocation FIVE_STAR_SERVICE = getResearchEffectID("fivestarservice");

    public static ResourceLocation getResearchEffectID(String researchName) {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/" + researchName);
    }

}
