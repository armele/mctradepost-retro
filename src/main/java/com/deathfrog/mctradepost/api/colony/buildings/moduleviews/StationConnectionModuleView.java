package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationConnectionModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/connections.png");
    }

    /**
     * Gets the translation key for the description of the module.
     * 
     * @return the translation key.
     */
    @Override
    public Component getDesc()
    {
        return Component.translatable("com.mctradepost.core.gui.modules.stationconnections");
    }

    @Override
    public BOWindow getWindow()
    {
        return new WindowStationConnectionModule(getBuildingView(), this);
    }

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        // No-op for now
    }

}