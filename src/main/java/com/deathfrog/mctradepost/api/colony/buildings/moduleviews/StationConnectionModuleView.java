package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationConnectionModule.LinkageStatus;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationConnectionModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side module view for station connections and installed dimensional linkages.
 */
public class StationConnectionModuleView extends AbstractBuildingModuleView
{
    /**
     * Client-facing status data for one installed dimensional linkage.
     *
     * @param stack linkage item stack
     * @param status validation status computed on the server
     * @param messageKey translation key for the validation status
     */
    public record LinkageViewData(ItemStack stack, LinkageStatus status, String messageKey) {}

    private final List<LinkageViewData> dimensionalLinkages = new ArrayList<>();
    private int dimensionalLinkageLimit = 0;

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

    /**
     * @return immutable list of installed dimensional linkages synchronized from the server
     */
    public List<LinkageViewData> getDimensionalLinkages()
    {
        return Collections.unmodifiableList(dimensionalLinkages);
    }

    /**
     * @return maximum number of dimensional linkages the station can hold
     */
    public int getDimensionalLinkageLimit()
    {
        return dimensionalLinkageLimit;
    }

    @Override
    public void deserialize(@NotNull RegistryFriendlyByteBuf buf)
    {
        dimensionalLinkages.clear();
        int count = buf.readInt();
        for (int i = 0; i < count; i++)
        {
            ItemStack stack = BuildingStationConnectionModule.readItemStack(buf);
            LinkageStatus status = LinkageStatus.valueOf(buf.readUtf());
            String messageKey = buf.readUtf();
            dimensionalLinkages.add(new LinkageViewData(stack, status, messageKey));
        }
        dimensionalLinkageLimit = buf.readInt();
    }

}
