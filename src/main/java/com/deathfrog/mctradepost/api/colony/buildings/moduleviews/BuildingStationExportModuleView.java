package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class BuildingStationExportModuleView  extends AbstractBuildingModuleView
{

    /**
     * The list of exports configured.
     */
    protected final List<ExportData> exportList = new ArrayList<>();

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        exportList.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            StationData destinationStation = StationData.fromNBT(buf.readNbt());
            ItemStack itemStack = Utils.deserializeCodecMess(buf);
            int cost = buf.readInt();
            int quantity = buf.readInt();
            int shipDistance = buf.readInt();
            int trackDistance = buf.readInt();
            int lastShipDay = buf.readInt();
            boolean nsf = buf.readBoolean();
            int shipmentCountdown = buf.readInt();
            boolean reverse = buf.readBoolean();
            ExportData exportData = new ExportData(null, destinationStation, new ItemStorage(itemStack, quantity), cost, reverse);
            exportData.setShipDistance(shipDistance);
            exportData.setTrackDistance(trackDistance);
            exportData.setLastShipDay(lastShipDay);
            exportData.setInsufficientFunds(nsf);
            exportData.setShipmentCountdown(shipmentCountdown);
            
            exportList.add(exportData);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowStationExportModule(buildingView, this);
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/export.png");
    }

    @Override
    public String getDesc()
    {
        return("com.minecolonies.coremod.gui.station.exports");
    }
    
    /**
     * Get the list of export data.
     *
     * @return the list of exports.
     */
    public List<ExportData> getExportList()
    {
        return exportList;
    }
}