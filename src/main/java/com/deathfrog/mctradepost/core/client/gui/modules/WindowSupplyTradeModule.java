package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.Map;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.Box;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * BOWindow for the Marketplace hut's ECON module.
 */
public class WindowSupplyTradeModule extends AbstractModuleWindow
{
    // TODO: Refactor this window!
    private static final String PANE_STATIONS = "stations";
    private static final String STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX = ":gui/layouthuts/layoutstationconnection.xml";
    private Map<BlockPos, StationData> stations = null;
    IBuildingView buildingView = null;

    private final ItemStackHandler inputHandler = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);

            // Copy the item to your tracked station list logic
            ItemStack inserted = getStackInSlot(slot);
            if (!inserted.isEmpty()) {
                // TODO: ((StationView) building).addTrackedItem(inserted.copy()); implement this
            }
        }
    };

    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList connectionDisplayList;

    public WindowSupplyTradeModule(IBuildingView buildingView)
    {
        super(buildingView, MCTradePostMod.MODID + STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX);
        this.buildingView = buildingView;
        stations = ((StationView) buildingView).getStations();

        connectionDisplayList = findPaneOfTypeByID(PANE_STATIONS, ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        if (stations != null && !stations.isEmpty())
        {
            // TODO: Loop through each station and see what purchase offers are available.
            // Present them in an interface listing colony name, item stack, and and price.
            // Display a checkbox for each offer.  Persist the selection.
            updateConnections();

        }
        else
        {
            // MCTradePostMod.LOGGER.warn("Station list is empty or null.");
        }
    }

    /**
     * Updates the display for the stations in the gui.
     * 
     * @see {@link ScrollingList.DataProvider}
     */
    protected void updateConnections()
    {
        connectionDisplayList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return stations.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                if (index < 0 || index >= stations.size())
                {
                    return;
                }

                final BlockPos pos = stations.keySet().toArray(new BlockPos[0])[index];
                final StationData station = stations.get(pos);

                final Box wrapperBox = rowPane.findPaneOfTypeByID("stationx", Box.class);
                wrapperBox.setPosition(wrapperBox.getX(), wrapperBox.getY());
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                final Text location = wrapperBox.findPaneOfTypeByID("location", Text.class);
                location.setText(Component.literal(IColonyManager.getInstance().getColonyView(station.getColonyId(), station.getDimension()).getName()));

                final Text status = wrapperBox.findPaneOfTypeByID("status", Text.class);
                status.setText(Component.literal("Placeholder"));
            }
        });
    }

}
