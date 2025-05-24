package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.colony.buildings.moduleviews.BuildingStatisticsModuleView;

/**
 * Building statistic module.
 */
public class EconModuleView extends BuildingStatisticsModuleView
{
   @Override
    public String getIcon()
    {
        // MCTradePostMod.LOGGER.warn("Module icon load trace", new Exception("Module icon load trace"));
        return "info";
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