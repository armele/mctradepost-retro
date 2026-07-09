package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.Map;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StationConnectionModuleView.LinkageViewData;
import com.deathfrog.mctradepost.api.items.datacomponent.DimensionalLinkageRecord;
import com.deathfrog.mctradepost.api.colony.buildings.views.StationView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StationLinkageMessage.LinkageAction;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.DimensionalLinkageItem;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.StationData;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
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
        connectionDisplayList = findPaneOfTypeByID(PANE_STATIONS, ScrollingList.class);
        linkageDisplayList = findPaneOfTypeByID(PANE_LINKAGES, ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
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
                String statValue = ((StationView) buildingView).stationConnectionStatus(station).toString() + "";
                status.setText(Component.literal(statValue));
            }
        });
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
