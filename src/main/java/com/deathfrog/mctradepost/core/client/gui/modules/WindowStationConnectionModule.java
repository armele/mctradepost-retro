package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView.LinkageViewData;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage.LinkageAction;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationConnectionAttemptMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationRouteRefreshMessage;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackRoute;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult.FailureKind;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.TrackPathConnection.TrackConnectionResult.RouteFailureDiagnostic;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class WindowStationConnectionModule extends AbstractModuleWindow<StationConnectionModuleView>
{
    private static final String PANE_STATIONS = "stations";
    private static final String PANE_LINKAGES = "linkages";
    private static final String BUTTON_REMOVE_LINKAGE = "removeLinkage";
    private static final String BUTTON_REFRESH_ROUTE = "refreshRoute";
    private static final String BUTTON_ATTEMPT_CONNECTION = "attemptConnection";
    private static final String TEXTURE_CHECKED = "minecolonies:textures/gui/builderhut/builder_button_mini_check.png";
    private static final String TEXTURE_UNCHECKED = "minecolonies:textures/gui/builderhut/builder_button_mini.png";
    private static final String IMAGE_HELP = "help";
    private static final String STATIONCONNECTION_WINDOW_RESOURCE_SUFFIX = "gui/layouthuts/layoutstationconnection.xml";
    private Map<DimPos, StationData> stations = null;
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
        registerButton(BUTTON_ATTEMPT_CONNECTION, this::toggleConnectionAttempt);
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

        final StationData destination = stations.values().toArray(new StationData[0])[row];
        button.disable();
        new StationRouteRefreshMessage(buildingView, destination).sendToServer();
    }

    /** Toggles route discovery for the destination represented by the clicked row. */
    private void toggleConnectionAttempt(Button button)
    {
        final int row = connectionDisplayList.getListElementIndexByPane(button);
        if (row < 0 || stations == null || row >= stations.size())
        {
            return;
        }
        StationData station = stations.values().toArray(new StationData[0])[row];
        StationView stationView = (StationView) buildingView;
        boolean enabled = !stationView.shouldAttemptConnection(station);
        stationView.setConnectionAttempt(station, enabled);
        new StationConnectionAttemptMessage(buildingView, station, enabled).sendToServer();
        updateConnections();
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

                final StationData station = stations.values().toArray(new StationData[0])[index];

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
                final Image statusIcon = wrapperBox.findPaneOfTypeByID("connectionIcon", Image.class);
                StationData.TrackConnectionStatus connectionStatus = ((StationView) buildingView).stationConnectionStatus(station);
                Component statusText = connectionStatusText((StationView) buildingView, station, connectionStatus);
                status.setText(statusText);
                statusIcon.setVisible(connectionStatus != StationData.TrackConnectionStatus.UNKNOWN);
                if (connectionStatus != StationData.TrackConnectionStatus.UNKNOWN)
                {
                    statusIcon.setImage(ResourceLocation.fromNamespaceAndPath("minecolonies", connectionStatus == StationData.TrackConnectionStatus.CONNECTED
                        ? "textures/gui/research/icon_check.png"
                        : "textures/gui/research/icon_cancel.png"), false);
                }
                Component tooltipText = connectionTooltipText((StationView) buildingView, station, connectionStatus, statusText);
                PaneBuilders.tooltipBuilder().hoverPane(status).build().setText(tooltipText);
                PaneBuilders.tooltipBuilder().hoverPane(statusIcon).build().setText(tooltipText);

                final ButtonImage refresh = wrapperBox.findPaneOfTypeByID(BUTTON_REFRESH_ROUTE, ButtonImage.class);
                refresh.setImage(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/gui/refresh.png"));
                boolean attemptEnabled = ((StationView) buildingView).shouldAttemptConnection(station);
                refresh.setEnabled(moduleView.isStationmasterEmployed() && attemptEnabled);
                PaneBuilders.tooltipBuilder().hoverPane(refresh).build().setText(Component.translatable(
                    !attemptEnabled
                        ? "mctradepost.route_refresh.connection_disabled_tooltip"
                        : moduleView.isStationmasterEmployed()
                        ? "mctradepost.route_refresh.tooltip"
                        : "mctradepost.route_refresh.no_stationmaster_tooltip"));

                final ButtonImage attempt = wrapperBox.findPaneOfTypeByID(BUTTON_ATTEMPT_CONNECTION, ButtonImage.class);
                attempt.setVisible(!station.isOutpost());
                attempt.setEnabled(!station.isOutpost());
                attempt.setImage(ResourceLocation.parse(attemptEnabled ? TEXTURE_CHECKED : TEXTURE_UNCHECKED));
                PaneBuilders.tooltipBuilder().hoverPane(attempt).build()
                    .setText(Component.translatable("mctradepost.connection_attempt.tooltip"));
            }
        });
    }

    /** Builds the status label and its ordered, distinct transport-mode and dimension summary. */
    private static Component connectionStatusText(StationView stationView, StationData station, StationData.TrackConnectionStatus status)
    {
        if (status != StationData.TrackConnectionStatus.CONNECTED)
        {
            if (status == StationData.TrackConnectionStatus.UNKNOWN)
            {
                return Component.literal(status.toString() + "");
            }
            RouteFailureDiagnostic failure = stationView.stationConnectionFailure(station);
            return failure == null
                ? Component.translatable("mctradepost.route_failure.none")
                : Component.literal(failureLocation(failure));
        }

        List<TrackRoute.SegmentType> modes = stationView.stationConnectionModes(station);

        List<String> summaryParts = modes.stream()
            .map(WindowStationConnectionModule::modeName)
            .collect(Collectors.toList());

        summaryParts.addAll(stationView.stationConnectionDimensions(station).stream()
            .map(WindowStationConnectionModule::dimensionName)
            .toList());

        if (summaryParts.isEmpty())
        {
            return Component.empty();
        }

        return Component.literal("(" + String.join(", ", summaryParts) + ")");
    }

    @SuppressWarnings("null")
    private static String dimensionName(ResourceLocation dimension)
    {
        return Component.translatableWithFallback(dimension.toLanguageKey("dimension"), dimension.getPath()).getString();
    }

    private static Component connectionTooltipText(StationView stationView, StationData station,
        StationData.TrackConnectionStatus status, Component statusText)
    {
        if (status != StationData.TrackConnectionStatus.DISCONNECTED)
        {
            return status == StationData.TrackConnectionStatus.UNKNOWN
                ? statusText
                : Component.literal(status + (statusText.getString().isEmpty() ? "" : " " + statusText.getString()));
        }

        RouteFailureDiagnostic failure = stationView.stationConnectionFailure(station);
        if (failure == null)
        {
            return Component.translatable("mctradepost.route_failure.none");
        }
        String mode = modeName(failure.attemptedMode());
        String location = failureLocation(failure);
        if (failure.reason() == FailureKind.ROUTE_BROKEN)
        {
            return Component.translatable("mctradepost.route_failure.broken", mode, location);
        }
        if (failure.reason() == FailureKind.SEARCH_LIMIT_REACHED)
        {
            return Component.translatable("mctradepost.route_failure.limit", mode, location);
        }
        return Component.translatable("mctradepost.route_failure.stopped", mode, location);
    }

    private static @Nonnull String failureLocation(RouteFailureDiagnostic failure)
    {
        String position = failure.position().pos().toShortString();
        ResourceLocation dimension = failure.position().dimension().location();
        return dimension.equals(Level.OVERWORLD.location()) ? position + "" : dimensionName(dimension) + ": " + position;
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
