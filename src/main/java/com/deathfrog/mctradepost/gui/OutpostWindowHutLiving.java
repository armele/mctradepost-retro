package com.deathfrog.mctradepost.gui;

import org.jetbrains.annotations.NotNull;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.network.messages.server.colony.building.RecallCitizenHutMessage;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.OutpostLivingBuildingModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.views.OutpostView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.util.constant.Constants;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class OutpostWindowHutLiving extends AbstractModuleWindow<OutpostLivingBuildingModuleView>
{
    private static final String LIST_CITIZEN = "assignedCitizen";
    private final OutpostLivingBuildingModuleView home;
    private OutpostView outpostView = null;
    private ScrollingList citizen;

    public OutpostWindowHutLiving(OutpostView buildingView, OutpostLivingBuildingModuleView homeModule)
    {
        super(homeModule, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "gui/windowhuthome.xml"));
        super.registerButton("assign", this::assignClicked);
        super.registerButton("recall", this::recallClicked);
        this.outpostView = buildingView;
        this.home = homeModule;
    }

    private void recallClicked()
    {
        (new RecallCitizenHutMessage((AbstractBuildingView) this.outpostView)).sendToServer();
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        citizen = findPaneOfTypeByID(LIST_CITIZEN, ScrollingList.class);
        citizen.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return home.getAssignedCitizens().size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final ICitizenDataView citizenDataView = home.getColony().getCitizen(home.getAssignedCitizens().get(index));
                if (citizenDataView != null)
                {
                    rowPane.findPaneOfTypeByID("name", Text.class).setText(Component.literal((citizenDataView.getJob().isEmpty() ? "" : (Component.translatable(citizenDataView.getJob() + "").getString() + ": ")) + citizenDataView.getName()));
                }
            }
        });

        refreshView();
    }

    protected void refreshView()
    {
        ((Text) this.findPaneOfTypeByID("assignedlabel", Text.class)).setText(Component.translatableEscape(
            "com.minecolonies.coremod.gui.home.assigned",
            new Object[] {this.home.getAssignedCitizens().size(), this.home.getMax()}));
        this.citizen.refreshElementPanes();
    }

    protected void assignClicked()
    {
        (new OutpostWindowAssignCitizen(this.home.getColony(), outpostView, home)).open();
    }
}
