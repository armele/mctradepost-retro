package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView.LinkageViewData;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage.LinkageAction;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationRouteRefreshMessage;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.Box;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WindowStationConnectionModule extends AbstractModuleWindow<StationConnectionModuleView>
{
    private static final String PANE_STATIONS = "stations";
    private static final String PANE_LINKAGES = "linkages";
    private static final String BUTTON_REMOVE_LINKAGE = "removeLinkage";
    private static final String BUTTON_REFRESH_ROUTE = "refreshRoute";
    private static final String IMAGE_HELP = "help";
    private static final String STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX = "gui/layouthuts/layoutstationconnection.xml";
    private Map<BlockPos, StationData> stations = null;
    IBuildingView buildingView = null;

    /**
     * Scrollinglist of the resources.
     */
    protected ScrollingList connectionDisplayList;
    protected ScrollingList linkageDisplayList;

    public WindowStationConnectionModule(IBuildingView buildingView, StationConnectionModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX));
        this.buildingView = buildingView;
        stations = ((StationView) buildingView).getStations();

        registerButton(BUTTON_REMOVE_LINKAGE, this::removeLinkage);
        registerButton(BUTTON_REFRESH_ROUTE, this::refreshRoute);
        connectionDisplayList = findPaneOfTypeByID(PANE_STATIONS, ScrollingList.class);
        linkageDisplayList = findPaneOfTypeByID(PANE_LINKAGES, ScrollingList.class);
    }

    /** Queues fresh route discovery for the connection represented by the clicked row. */
    private void refreshRoute(Button button)
    {
        if (!moduleView.isStationmasterEmployed())
        {
            return;
        }

        final int row = connectionDisplayList.getListElementIndexByPane(button);
        if (row < 0 || stations == null || row >= stations.size())
        {
            return;
        }

        final BlockPos destination = stations.keySet().toArray(new BlockPos[0])[row];
        button.disable();
        new StationRouteRefreshMessage(buildingView, destination).sendToServer();
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        final Image help = findPaneOfTypeByID(IMAGE_HELP, Image.class);
        final AbstractTextBuilder.TooltipBuilder helpTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(help);
        helpTipBuilder.append(Component.translatable("com.minecolonies.coremod.gui.station.connection.hover"));
        helpTipBuilder.build();
  
        updateConnections();
        updateLinkages();
    }

    /**
     * Sends a server request to uninstall the linkage represented by the clicked row button.
     *
     * @param button clicked remove button
     */
    private void removeLinkage(Button button)
    {
        final int row = linkageDisplayList.getListElementIndexByPane(button);
        if (row < 0 || row >= moduleView.getDimensionalLinkages().size())
        {
            return;
        }

        new StationLinkageMessage(buildingView, LinkageAction.REMOVE, row).sendToServer();
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
                return stations == null ? 0 : stations.size();
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
                    boolean isOutpost = station.isOutpost();
                    location.setText(Component.literal(colonyView.getName() + (isOutpost ? ": Outpost" : "")));
                }
                else
                {
                    location.setText(Component.literal("Unknown Colony (ID: " + station.getColonyId() + ")"));
                }

                final Text status = wrapperBox.findPaneOfTypeByID("status", Text.class);
                Component statusText = connectionStatusText((StationView) buildingView, station);
                status.setText(statusText);
                PaneBuilders.tooltipBuilder().hoverPane(status).build().setText(statusText);

                final ButtonImage refresh = wrapperBox.findPaneOfTypeByID(BUTTON_REFRESH_ROUTE, ButtonImage.class);
                refresh.setImage(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/refresh.png"));
                refresh.setEnabled(moduleView.isStationmasterEmployed());
                PaneBuilders.tooltipBuilder().hoverPane(refresh).build().setText(Component.translatable(
                    moduleView.isStationmasterEmployed()
                        ? "mctradepost.route_refresh.tooltip"
                        : "mctradepost.route_refresh.no_stationmaster_tooltip"));
            }
        });
    }

    /** Builds the status label and its ordered, distinct transport-mode summary. */
    private static Component connectionStatusText(StationView stationView, StationData station)
    {
        StationData.TrackConnectionStatus status = stationView.stationConnectionStatus(station);
        if (status != StationData.TrackConnectionStatus.CONNECTED)
        {
            return Component.literal(status.toString() + "");
        }

        List<TrackRoute.SegmentType> modes = stationView.stationConnectionModes(station);
        if (modes.isEmpty())
        {
            return Component.literal(status.toString() + "");
        }

        String modeSummary = modes.stream()
            .map(WindowStationConnectionModule::modeName)
            .collect(Collectors.joining(", "));
        return Component.literal(status + " (" + modeSummary + ")");
    }

    private static String modeName(TrackRoute.SegmentType mode)
    {
        return switch (mode)
        {
            case RAIL -> "Rail";
            case ROAD -> "Road";
            case WATER -> "Water";
            case AIR, AIR_TRANSIT -> "Air";
            default -> mode.name();
        };
    }

    /**
     * Populates the dimensional linkage list from the synchronized module view data.
     */
    protected void updateLinkages()
    {
        Text capacity = findPaneOfTypeByID("linkageCapacity", Text.class);
        capacity.setText(Component.translatable("mctradepost.linkage.gui.capacity",
            moduleView.getDimensionalLinkages().size(), moduleView.getDimensionalLinkageLimit()));

        linkageDisplayList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return moduleView.getDimensionalLinkages().size();
            }

            @SuppressWarnings("null")
            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                if (index < 0 || index >= moduleView.getDimensionalLinkages().size())
                {
                    return;
                }

                LinkageViewData linkage = moduleView.getDimensionalLinkages().get(index);
                DimensionalLinkageRecord record = DimensionalLinkageItem.linkageRecord(linkage.stack());

                final Box wrapperBox = rowPane.findPaneOfTypeByID("linkagex", Box.class);
                wrapperBox.setSize(wrapperBox.getParent().getWidth(), wrapperBox.getHeight());

                wrapperBox.findPaneOfTypeByID("linkageIcon", ItemIcon.class).setItem(linkage.stack());

                final Text name = wrapperBox.findPaneOfTypeByID("linkageName", Text.class);
                name.setText(Component.translatable("mctradepost.linkage.gui.entry", index + 1));
                PaneBuilders.tooltipBuilder().hoverPane(name).build().setText(Component.translatable(linkage.messageKey()));

                final Text overworld = wrapperBox.findPaneOfTypeByID("linkageOverworld", Text.class);
                overworld.setText(Component.translatable("mctradepost.linkage.gui.overworld", endpointText(record.overworldEndpoint().orElse(null))));

                final Text nether = wrapperBox.findPaneOfTypeByID("linkageNether", Text.class);
                nether.setText(Component.translatable("mctradepost.linkage.gui.nether", endpointText(record.netherEndpoint().orElse(null))));

                final Button remove = wrapperBox.findPaneOfTypeByID(BUTTON_REMOVE_LINKAGE, Button.class);
                remove.setText(Component.literal("X"));
                PaneBuilders.tooltipBuilder().hoverPane(remove).build().setText(Component.translatable("mctradepost.linkage.gui.remove"));
            }
        });
    }

    /**
     * Formats an endpoint position for compact GUI display.
     *
     * @param endpoint endpoint to display
     * @return position text or the unset label
     */
    private static Component endpointText(final DimPos endpoint)
    {
        return endpoint == null
            ? Component.translatable("item.mctradepost.dimensional_linkage.unset")
            : Component.literal(endpoint.pos().toShortString() + "");
    }
}
