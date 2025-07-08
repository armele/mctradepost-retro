package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;

import net.minecraft.resources.ResourceLocation;

/**
 * Building statistic module.
 */
public class EconModuleView extends BuildingStatisticsModuleView
{
    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
    @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/econ.png");
    }

    @Override
    public String getDesc()
    {
        return "com.mctradepost.core.gui.modules.econ";
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowEconModule(getBuildingView(), this.getBuildingStatisticsManager());
    }
}