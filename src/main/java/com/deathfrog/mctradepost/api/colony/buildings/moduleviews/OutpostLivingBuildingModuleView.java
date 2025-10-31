package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.views.OutpostView;
import com.deathfrog.mctradepost.gui.OutpostWindowHutLiving;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.colony.buildings.moduleviews.LivingBuildingModuleView;
import net.minecraft.resources.ResourceLocation;

public class OutpostLivingBuildingModuleView extends LivingBuildingModuleView
{
    @NotNull
    @Override
    public BOWindow getWindow()
    {
        return new OutpostWindowHutLiving((OutpostView) this.getBuildingView(), this);
    }

    @Override
    public boolean isPageVisible()
    {
        return true;
    }

    @Override
    public String getDesc()
    {
        return "com.mctradepost.core.gui.modules.outpost.residents";
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/home.png");
    }
}
