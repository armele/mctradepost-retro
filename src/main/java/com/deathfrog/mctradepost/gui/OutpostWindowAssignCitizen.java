package com.deathfrog.mctradepost.gui;

import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.OutpostLivingBuildingModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.views.OutpostView;
import com.deathfrog.mctradepost.core.network.messages.OutpostAssignMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.HiringMode;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.TranslationConstants.COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED;
import static com.minecolonies.api.util.constant.WindowConstants.*;

/* 
* Based on WindowAssignCitizen
* (Ideally the Minecolonies window could implement an interface for LivingBuildingView, but not available at this time)
* 
*/
public class OutpostWindowAssignCitizen extends AbstractWindowSkeleton
{
    /**
     * Threshold that defines when the living quarters are too far away.
     */
    private static final double FAR_DISTANCE_THRESHOLD = 300;

    /**
     * The view of the current building.
     */
    private final OutpostLivingBuildingModuleView livingModule;

    private final OutpostView buildingView;

    /**
     * List of citizens which can be assigned.
     */
    private final ScrollingList unassignedCitizenList;

    /**
     * List of citizens which are currently assigned.
     */
    private final ScrollingList assignedCitizenList;

    /**
     * The colony.
     */
    private final IColonyView colony;

    /**
     * Contains all the unassigned citizens.
     */
    private List<ICitizenDataView> unassignedCitizens = new ArrayList<>();

    /**
     * Contains all the already assigned citizens.
     */
    private final List<ICitizenDataView> assignedCitizens = new ArrayList<>();

    /**
     * Constructor for the window when the player wants to assign a worker for a certain home building.
     *
     * @param c          the colony view.
     * @param building the building.
     */
    public OutpostWindowAssignCitizen(final IColonyView c, final OutpostView buildingView, final OutpostLivingBuildingModuleView livingModule)
    {
        super(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "gui/windowassigncitizen.xml"));
        this.colony = c;
        this.livingModule = livingModule;
        this.buildingView = buildingView;
        unassignedCitizenList = findPaneOfTypeByID(UNASSIGNED_CITIZEN_LIST, ScrollingList.class);
        assignedCitizenList = findPaneOfTypeByID(ASSIGNED_CITIZEN_LIST, ScrollingList.class);

        super.registerButton(BUTTON_CANCEL, this::cancelClicked);
        super.registerButton(BUTTON_MODE, this::modeClicked);

        super.registerButton(BUTTON_HIRE, this::hireClicked);
        super.registerButton(BUTTON_FIRE, this::fireClicked);

        updateCitizens();

        setupSettings(findPaneOfTypeByID(BUTTON_MODE, Button.class));
    }

    /**
     * When hire was clicked.
     * @param button the clicked button.
     */
    private void hireClicked(@NotNull final Button button)
    {
        final int row = unassignedCitizenList.getListElementIndexByPane(button);
        final ICitizenDataView data = unassignedCitizens.get(row);

        if (livingModule.getAssignedCitizens().size() >= livingModule.getMax())
        {
            return;
        }
        livingModule.add(data.getId());
        data.setHomeBuilding(buildingView.getPosition());

        new OutpostAssignMessage(this.buildingView, true, data.getId(), null).sendToServer();

        updateCitizens();
        unassignedCitizenList.refreshElementPanes();
        assignedCitizenList.refreshElementPanes();
    }

    /**
     * When fire was clicked.
     * @param button the clicked button.
     */
    private void fireClicked(@NotNull final Button button)
    {
        final int row = assignedCitizenList.getListElementIndexByPane(button);
        final ICitizenDataView data = assignedCitizens.get(row);

        livingModule.remove(data.getId());
        data.setHomeBuilding(null);

        new OutpostAssignMessage(buildingView, false, data.getId(), null).sendToServer();

        updateCitizens();
        unassignedCitizenList.refreshElementPanes();
        assignedCitizenList.refreshElementPanes();
    }


    /**
     * Hiring mode switch clicked.
     *
     * @param button the clicked button.
     */
    private void modeClicked(@NotNull final Button button)
    {
        switchHiringMode(button);
    }

    /**
     * Switch the mode after clicking the button.
     *
     * @param settingsButton the clicked button.
     */
    private void switchHiringMode(final Button settingsButton)
    {
        int index = livingModule.getHiringMode().ordinal() + 1;

        if (index >= HiringMode.values().length)
        {
            index = 0;
        }

        livingModule.setHiringMode(HiringMode.values()[index]);
        setupSettings(settingsButton);
    }

    /**
     * Canceled clicked to exit the GUI.
     *
     * @param button the clicked button.
     */
    private void cancelClicked(@NotNull final Button button)
    {
        if (button.getID().equals(BUTTON_CANCEL) && colony.getTownHall() != null)
        {
            buildingView.openGui(false);
        }
    }

    /**
     * Setup the settings.
     *
     * @param settingsButton the buttons to setup.
     */
    private void setupSettings(final Button settingsButton)
    {
        settingsButton.setText(Component.translatable("com.minecolonies.coremod.gui.hiringmode." + livingModule.getHiringMode().name().toLowerCase(Locale.US)));
    }

    /**
     * Clears and resets/updates all citizens.
     */
    private void updateCitizens()
    {
        //Removes citizens that work from home and remove citizens already living here.
        unassignedCitizens = colony.getCitizens().values().stream()
                     .filter(cit -> (!Objects.equals(cit.getHomeBuilding(), cit.getWorkBuilding()) || cit.getHomeBuilding() == null) && !buildingView.getPosition().equals(cit.getHomeBuilding()))
                     .sorted(Comparator.comparing((ICitizenDataView cit) -> cit.getHomeBuilding() == null ? 0 : 1)
                               .thenComparingLong(cit -> {
                                   if (cit.getWorkBuilding() == null)
                                   {
                                       if (cit.getHomeBuilding() == null)
                                       {
                                           return 0;
                                       }
                                       return Integer.MAX_VALUE;
                                   }

                                   return (int) BlockPosUtil.getDistance(cit.getWorkBuilding(), buildingView.getPosition());
                               })).toList();

        assignedCitizens.clear();
        for (final int id : livingModule.getAssignedCitizens())
        {
            assignedCitizens.add(colony.getCitizen(id));
        }
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        updateCitizens();

        unassignedCitizenList.enable();
        unassignedCitizenList.show();
        //Creates a dataProvider for the homeless citizenList.
        unassignedCitizenList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return unassignedCitizens.size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                @NotNull final ICitizenDataView citizen = unassignedCitizens.get(index);
                final Button hireButton = rowPane.findPaneOfTypeByID(BUTTON_HIRE, Button.class);
                final BlockPos home = citizen.getHomeBuilding();
                final BlockPos work = citizen.getWorkBuilding();

                final Text citizenLabel = rowPane.findPaneOfTypeByID(CITIZEN_LABEL, Text.class);
                citizenLabel.setText(Component.literal(citizen.getName()));

                MutableComponent workString = Component.empty();
                int newDistance = 0;
                if (work != null)
                {
                    newDistance = (int) BlockPosUtil.getDistance(work, buildingView.getPosition());
                    workString = Component.translatableEscape("com.minecolonies.coremod.gui.home.new", newDistance);
                }

                MutableComponent homeString = Component.translatableEscape("com.minecolonies.coremod.gui.home.homeless");
                boolean better = false;
                if (home != null)
                {
                    if (work != null)
                    {
                        final int oldDistance = (int) BlockPosUtil.getDistance(work, home);
                        homeString = Component.translatableEscape("com.minecolonies.coremod.gui.home.currently", oldDistance);
                        better = newDistance < oldDistance;
                        if (oldDistance > FAR_DISTANCE_THRESHOLD)
                        {
                            homeString = homeString.withStyle(ChatFormatting.RED);
                        }
                    }
                    else
                    {
                        homeString = Component.empty();
                    }
                }

                if (better)
                {
                    workString = workString.withStyle(ChatFormatting.DARK_GREEN);
                }


                final Text newLivingLabel = rowPane.findPaneOfTypeByID(CITIZEN_JOB, Text.class);
                if (citizen.getJobView() != null)
                {
                    newLivingLabel.setText(Component.empty().append(Component.translatable(citizen.getJobView().getEntry().getTranslationKey())).append(": ").append(workString).append(" ").append(homeString));
                }
                else
                {
                    newLivingLabel.setText(Component.translatable(COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED).append("\n").append(homeString));
                }
                newLivingLabel.setTextWrap(true);

                if (((colony.isManualHousing() && livingModule.getHiringMode() == HiringMode.DEFAULT) || (livingModule.getHiringMode() == HiringMode.MANUAL)))
                {
                    if (livingModule.getAssignedCitizens().size() < livingModule.getMax())
                    {
                        hireButton.enable();
                    }
                    else
                    {
                        hireButton.disable();
                    }
                    PaneBuilders.tooltipBuilder().hoverPane(hireButton).build().setText(Component.empty());
                }
                else
                {
                    hireButton.disable();
                    PaneBuilders.tooltipBuilder().hoverPane(hireButton).build().setText(Component.translatableEscape("com.minecolonies.coremod.gui.home.hire.warning"));
                }
            }
        });

        assignedCitizenList.enable();
        assignedCitizenList.show();
        //Creates a dataProvider for the homeless citizenList.
        assignedCitizenList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return assignedCitizens.size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                @NotNull final ICitizenDataView citizen = assignedCitizens.get(index);
                final Button fireButton = rowPane.findPaneOfTypeByID(BUTTON_FIRE, Button.class);
                final BlockPos work = citizen.getWorkBuilding();
                fireButton.setText(Component.translatableEscape("com.minecolonies.coremod.gui.hiring.buttonunassign"));

                final Text citizenLabel = rowPane.findPaneOfTypeByID(CITIZEN_LABEL, Text.class);
                citizenLabel.setText(Component.literal(citizen.getName()));

                MutableComponent workString = Component.empty();
                int newDistance;
                if (work != null)
                {
                    newDistance = (int) BlockPosUtil.getDistance(work, buildingView.getPosition());
                    workString = Component.translatableEscape("com.minecolonies.coremod.gui.home.new", newDistance);
                }

                final Text newLivingLabel = rowPane.findPaneOfTypeByID(CITIZEN_JOB, Text.class);
                newLivingLabel.setTextWrap(true);
                if (citizen.getJobView() != null)
                {
                    if (work != null)
                    {
                        final int distance = (int) BlockPosUtil.getDistance(work, buildingView.getPosition());
                        if (distance > FAR_DISTANCE_THRESHOLD)
                        {
                            workString = workString.withStyle(ChatFormatting.RED);
                        }
                    }
                    newLivingLabel.setText(Component.empty().append(Component.translatableEscape(citizen.getJobView().getEntry().getTranslationKey())).append(Component.literal(": ")).append(workString));
                }
                else
                {
                    newLivingLabel.setText(Component.translatableEscape(COM_MINECOLONIES_COREMOD_GUI_TOWNHALL_CITIZEN_UNEMPLOYED));
                }

                if (((colony.isManualHousing() && livingModule.getHiringMode() == HiringMode.DEFAULT) || (livingModule.getHiringMode() == HiringMode.MANUAL)))
                {
                    if (citizen.getColony().getTravellingManager().isTravelling(citizen.getId()))
                    {
                        fireButton.disable();
                        PaneBuilders.tooltipBuilder().hoverPane(fireButton).build().setText(Component.translatable("com.minecolonies.coremod.gui.home.travelling"));
                    }
                    else
                    {
                        fireButton.enable();
                    }

                    PaneBuilders.tooltipBuilder().hoverPane(fireButton).build().setText(Component.empty());
                }
                else
                {
                    fireButton.disable();
                    PaneBuilders.tooltipBuilder().hoverPane(fireButton).build().setText(Component.translatableEscape("com.minecolonies.coremod.gui.home.hire.warning"));
                }
            }
        });
    }
}
