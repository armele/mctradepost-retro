package com.deathfrog.mctradepost.api.colony.buildings.moduleviews;

import com.deathfrog.mctradepost.core.client.gui.modules.WindowTradeModule;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModuleView;
import com.minecolonies.api.colony.buildings.modules.IMinimumStockModuleView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.Utils;
import com.minecolonies.core.client.gui.modules.MinimumStockModuleWindow;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BuildingStationTradeModuleView extends AbstractBuildingModuleView
{
    /**
     * The minimum stock.
     */
    private List<Tuple<ItemStorage, Integer>> tradeList = new ArrayList<>();

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
        tradeList.clear();
        final int size = buf.readInt();
        for (int i = 0; i < size; i++)
        {
            tradeList.add(new Tuple<>(new ItemStorage(Utils.deserializeCodecMess(buf)), buf.readInt()));
        }
        reachedLimit = buf.readBoolean();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public BOWindow getWindow()
    {
        return new WindowTradeModule(buildingView, this);
    }

    public List<Tuple<ItemStorage, Integer>> getTrades()
    {
        return tradeList;
    }


    public boolean hasReachedLimit()
    {
        return reachedLimit;
    }

    @Override
    public String getIcon()
    {
        return "stock";
    }

    @Override
    public String getDesc()
    {
        return "com.minecolonies.coremod.gui.station.trades";
    }
}
