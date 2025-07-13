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
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class WindowStationConnectionModule extends AbstractModuleWindow
{
    private static final String PANE_STATIONS = "stations";
    private static final String STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX = ":gui/layouthuts/layoutstationconnection.xml";
    private Map<BlockPos, StationData> stations = null;
    IBuildingView buildingView = null;

    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList connectionDisplayList;

    public WindowStationConnectionModule(IBuildingView buildingView)
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
                Pane helpPane = findPaneOfTypeByID("tabHelp", Text.class);

                if (stations == null || stations.isEmpty())
                {
                    helpPane.setVisible(true);
                    connectionDisplayList.setVisible(false);
                    return;
                }

                if (index < 0 || index >= stations.size())
                {
                    return;
                }

                helpPane.setVisible(false);
                connectionDisplayList.setVisible(true);

                final BlockPos pos = stations.keySet().toArray(new BlockPos[0])[index];
                final StationData station = stations.get(pos);

                final Box wrapperBox = rowPane.findPaneOfTypeByID("stationx", Box.class);
                wrapperBox.setPosition(wrapperBox.getX(), wrapperBox.getY());
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                final Text location = wrapperBox.findPaneOfTypeByID("location", Text.class);
                IColonyView colonyView = IColonyManager.getInstance().getColonyView(station.getColonyId(), station.getDimension());
                if (colonyView != null) 
                {
                    location.setText(Component.literal(colonyView.getName()));
                }
                else
                {
                    location.setText(Component.literal("Unknown Colony (ID: " + station.getColonyId() + ", Dimension: " + station.getDimension() + ")"));
                }

                final Text status = wrapperBox.findPaneOfTypeByID("status", Text.class);
                status.setText(Component.literal(station.getTrackConnectionStatus().toString()));
            }
        });
    }

}
