package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.BuildingStationExportModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.BuildingStationExportModule.ExportData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MCTPBuildingModules;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData.TrackConnectionStatus;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.Box;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class WindowStationExportModule extends AbstractModuleWindow
{
    private static final String PANE_STATIONS = "stations";
    private static final String STATION_EXPORT_WINDOW_RESOURCE_SUFFIX = ":gui/layouthuts/layoutstationexport.xml";
    private Map<BlockPos, StationData> stations = null;
    IBuildingView buildingView = null;
    BuildingStationExportModuleView moduleView = null;

    protected List<ExportGui> potentialExportMap = new ArrayList<>();

    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList exportDisplayList;

    public class ExportGui
    {
        private StationData destinationStation = null;
        private boolean selected = false;
        private ItemStorage itemStorage = null;
        private Integer cost = null;

        ExportGui(StationData destinationStation, boolean selected, ItemStorage itemStorage, Integer cost)
        {
            this.destinationStation = destinationStation;
            this.selected = selected;
            this.itemStorage = itemStorage;
            this.cost = cost;
        }

        /**
         * Checks if the export could be picked for trade configuration.
         * 
         * @return true if the destination station is not null and is connected, false otherwise.
         */
        public boolean isEnabled()
        {
            return destinationStation != null && destinationStation.getTrackConnectionStatus() == TrackConnectionStatus.CONNECTED;
        }

        /**
         * Checks if the current export configuration is selected.
         *
         * @return true if the export configuration is selected, false otherwise.
         */
        public boolean isSelected()
        {
            return selected;
        }

        /**
         * Sets whether the export configuration is selected or not.
         * 
         * @param selected true if the export configuration is selected, false otherwise.
         */
        public void setSelected(boolean selected)
        {
            this.selected = selected;
        }

        /**
         * Retrieves the destination station of this export configuration.
         * 
         * @return the destination station of this export configuration.
         */
        public StationData getDestinationStation()
        {
            return destinationStation;
        }

        /**
         * Retrieves the item storage of this export configuration.
         * 
         * @return the item storage of this export configuration.
         */
        public ItemStorage getItemStorage()
        {
            return itemStorage;
        }

        /**
         * Retrieves the cost of this export configuration.
         * 
         * @return the cost of this export configuration.
         */
        public Integer getCost()
        {
            return cost;
        }
    }

    public WindowStationExportModule(IBuildingView buildingView, BuildingStationExportModuleView moduleView)
    {
        super(buildingView, MCTradePostMod.MODID + STATION_EXPORT_WINDOW_RESOURCE_SUFFIX);
        this.buildingView = buildingView;
        this.moduleView = moduleView;
        stations = ((StationView) buildingView).getStations();

        MCTradePostMod.LOGGER.info("Configuring exports module window with {} connected stations.", stations.size());

        for (StationData station : stations.values())
        {
            IBuildingView remoteStationView = IColonyManager.getInstance().getBuildingView(station.getDimension(), station.getBuildingPosition());
            if (remoteStationView.hasModuleView(MCTPBuildingModules.IMPORTS) && !buildingView.getPosition().equals(remoteStationView.getPosition()))
            {
                List<Tuple<ItemStorage, Integer>> imports = remoteStationView.getModuleView(MCTPBuildingModules.IMPORTS).getImports();

                for (Tuple<ItemStorage, Integer> importTuple : imports)
                {
                    potentialExportMap.add(new ExportGui(station, false, importTuple.getA(), importTuple.getB()));
                }
            }
        }

        for (ExportGui exportGui : potentialExportMap)
        {
            if (TrackConnectionStatus.CONNECTED.equals(exportGui.destinationStation.getTrackConnectionStatus()))
            {
                MCTradePostMod.LOGGER.info("{} exports to check.", moduleView.getExportList());

                for (ExportData export : moduleView.getExportList())
                {
                    if (exportGui.isEnabled() && export.station().equals(exportGui.getDestinationStation().getBuildingPosition()) &&
                        export.itemStorage().getItemStack().is(exportGui.getItemStorage().getItem()) &&
                        exportGui.cost.equals(export.cost()))
                    {
                        exportGui.selected = true;
                        break;
                    }
                }
            }
        }

        exportDisplayList = findPaneOfTypeByID(PANE_STATIONS, ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        if (stations != null && !stations.isEmpty())
        {
            updatePotentialExports();
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
    protected void updatePotentialExports()
    {
        exportDisplayList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return potentialExportMap.size();
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                if (index < 0 || index >= potentialExportMap.size())
                {
                    return;
                }

                final ExportGui export = potentialExportMap.get(index);
                final StationData destinationStation = export.getDestinationStation();

                final Box wrapperBox = rowPane.findPaneOfTypeByID("stationx", Box.class);
                wrapperBox.setPosition(wrapperBox.getX(), wrapperBox.getY());
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                final Text location = wrapperBox.findPaneOfTypeByID("location", Text.class);
                location.setText(Component.literal(IColonyManager.getInstance()
                    .getColonyView(destinationStation.getColonyId(), destinationStation.getDimension())
                    .getName()));

                final ItemIcon outputStackDisplay = rowPane.findPaneOfTypeByID("wantedItemStack", ItemIcon.class);
                outputStackDisplay.setVisible(true);
                outputStackDisplay.setItem(export.getItemStorage().getItemStack());

                final ItemIcon costStackDisplay = rowPane.findPaneOfTypeByID("costStack", ItemIcon.class);
                costStackDisplay.setVisible(true);
                ItemStack stack = new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), export.getCost());
                costStackDisplay.setItem(stack);
            }
        });
    }
}
