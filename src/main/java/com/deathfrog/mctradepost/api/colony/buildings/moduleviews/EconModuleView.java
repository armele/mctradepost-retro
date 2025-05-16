package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.client.gui.modules.WindowEconModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.colony.managers.interfaces.IStatisticsManager;
import com.minecolonies.core.colony.managers.StatisticsManager;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Building statistic module.
 */
public class EconModuleView extends AbstractBuildingModuleView
{
    /**
     * List of all beds.
     */
    private StatisticsManager statisticsManager = new StatisticsManager();

    @Override
    public void deserialize(final @NotNull RegistryFriendlyByteBuf buf)
    {
        statisticsManager.deserialize(buf);
    }

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

    /**
     * Get the statistic manager of the building.
     * @return the manager.
     */
    public IStatisticsManager getBuildingStatisticsManager()
    {
        return statisticsManager;
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowEconModule(getBuildingView(), statisticsManager);
    }
}