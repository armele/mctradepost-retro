package com.deathfrog.mctradepost.api.research;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.resources.ResourceLocation;

public class MCTPResearchConstants {

    public static final ResourceLocation RESEARCH_DISENCHANTING     = getResearchEffectID("disenchanting");
    public static final ResourceLocation TOURISTS                   = getResearchEffectID("tourists");
    public static final ResourceLocation TRADESPEED                 = getResearchEffectID("tradespeed");
    public static final ResourceLocation TRADECAPACITY              = getResearchEffectID("tradecapacity");
    public static final ResourceLocation ADVERTISING                = getResearchEffectID("advertising");
    public static final ResourceLocation FIVE_STAR_SERVICE          = getResearchEffectID("fivestarservice");
    public static final ResourceLocation THRIFTSHOP_TIER            = getResearchEffectID("thriftshop_tier");
    public static final ResourceLocation THRIFTSHOP_MORE            = getResearchEffectID("thriftshop_more");
    public static final ResourceLocation THRIFTSHOP_REROLL          = getResearchEffectID("thriftshop_reroll");
    public static final ResourceLocation THRIFTSHOP_BOTTOMLESS      = getResearchEffectID("thriftshop_bottomless");
    public static final ResourceLocation RETAINED_SEARCH            = getResearchEffectID("retained_search");
    public static final ResourceLocation MARKETPLACE_SUBSCRIPTIONS  = getResearchEffectID("marketplace_subscriptions");
    public static final ResourceLocation HUSBANDRY                  = getResearchEffectID("husbandry");
    public static final ResourceLocation EXOTIC                     = getResearchEffectID("exotic");
    public static final ResourceLocation VET                        = getResearchEffectID("vet");
    public static final ResourceLocation FOCUSED_FORAGING           = getResearchEffectID("focused_foraging");
    public static final ResourceLocation ENHANCED_FOCUSED_FORAGING  = getResearchEffectID("enhanced_focused_foraging");

    public static ResourceLocation getResearchEffectID(String researchName) {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "effects/" + researchName);
    }

}
