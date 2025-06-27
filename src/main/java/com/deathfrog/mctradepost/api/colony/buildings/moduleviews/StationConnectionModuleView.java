package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationConnectionModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Building statistic module.
 */
public class StationConnectionModuleView extends AbstractBuildingModuleView
{

    public StationConnectionModuleView() {
        super();
        // MCTradePostMod.LOGGER.info("Constructing StationConnectionModuleView.");
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public String getIcon()
    {
        return "requests";
    }

    @Override
    public String getDesc()
    {
        return "com.mctradepost.core.gui.modules.stationconnections";
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowStationConnectionModule(getBuildingView());
    }

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        // No-op for now
    }

}