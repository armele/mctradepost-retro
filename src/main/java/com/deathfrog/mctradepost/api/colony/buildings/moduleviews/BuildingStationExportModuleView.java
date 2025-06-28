package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationImportModule;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationExportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule.ExportData;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
            BlockPos station = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
            ItemStorage itemStorage = new ItemStorage(Utils.deserializeCodecMess(buf));
            int cost = buf.readInt();
            exportList.add(new ExportData(station, itemStorage, cost));
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowStationExportModule(buildingView, this);
    }

    @Override
    public String getIcon()
    {
        return("info");
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