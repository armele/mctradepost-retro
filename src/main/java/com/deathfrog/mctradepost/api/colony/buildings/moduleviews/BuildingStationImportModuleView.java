package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.client.gui.modules.WindowStationImportModule;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData.TradeDefinition;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Utils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BuildingStationImportModuleView extends AbstractBuildingModuleView
{
    /**
     * The minimum stock.
     */
    private List<TradeDefinition> importList = new ArrayList<>();

    /**
     * If the stock limit was reached.
     */
    private boolean reachedLimit = false;

    /**
     * Read this view from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer to read this view from.
     */
    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        importList.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            importList.add(new TradeDefinition(new ItemStorage(Utils.deserializeCodecMess(buf)), buf.readInt(), buf.readInt()));
        }
        reachedLimit = buf.readBoolean();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowStationImportModule(buildingView, this);
    }

    /**
     * Get the list of import items and their amounts.
     *
     * @return a list of tuples, with the first element being the item storage and the second element being the amount.
     */
    public List<TradeDefinition> getImports()
    {
        return importList;
    }


    /**
     * Returns whether the stock limit has been reached.
     * @return true if the stock limit has been reached, false otherwise.
     */
    public boolean hasReachedLimit()
    {
        return reachedLimit;
    }

    /**
     * Get the icon of the module.
     * 
     * @return the icon to show.
     */
   @Override
    public ResourceLocation getIconResourceLocation()
    {
        return ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/modules/import.png");
    }

    /**
     * Gets the description of the module to display in the GUI.
     * 
     * @return The description of the module.
     */
    @Override
    public String getDesc()
    {
        return "com.minecolonies.coremod.gui.station.imports";
    }
}
