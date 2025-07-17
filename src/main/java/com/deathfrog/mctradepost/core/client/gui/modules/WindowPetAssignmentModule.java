package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetAssignmentModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.views.PetshopView;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetMessage.PetAction;
import com.google.common.collect.ImmutableList;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.EntityIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.NotNull;
import java.util.logging.Logger;
import static com.minecolonies.api.util.constant.WindowConstants.*;

public class WindowPetAssignmentModule extends AbstractModuleWindow
{
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = ":gui/layouthuts/layoutpetassignment.xml";

    private static final String LABEL_TYPE = "pettype";
    private static final String BUILDING_SELECTION_ID = "buildings";

    public IBuildingView selectedBuilding = null;

    /**
     * Resource scrolling list.
     */
    private final ScrollingList petList;

    /**
     * The matching module view to the window.
     */
    private final PetAssignmentModuleView moduleView;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowPetAssignmentModule(final IBuildingView building, final PetAssignmentModuleView moduleView)
    {
        super(building, MCTradePostMod.MODID + RESOURCE_STRING);

        petList = this.window.findPaneOfTypeByID("petlist", ScrollingList.class);
        this.moduleView = moduleView;

    }

    /**
     * Called when the window is opened.
     * 
     * @see AbstractModuleWindow#onOpened()
     */
    @Override
    public void onOpened()
    {
        super.onOpened();
        updateTradeList();
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updateTradeList()
    {
        petList.enable();
        petList.show();

        // Creates a dataProvider for the unemployed resourceList.
        petList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return ((PetshopView) buildingView).getPets().size();
            }

            /**
             * Inserts the elements into each row.
             * 
             * @param index   the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                ImmutableList<PetData> pets = ((PetshopView) buildingView).getPets();

                rowPane.findPaneOfTypeByID(LABEL_TYPE, Text.class).setText(Component.literal(pets.get(index).getAnimalType()));
                final EntityIcon entityIcon = findPaneOfTypeByID(ENTITY_ICON, EntityIcon.class);

                @SuppressWarnings("null")
                Entity selectedEntity = Minecraft.getInstance().level.getEntity(pets.get(index).getEntityId());
                
                entityIcon.setEntity(selectedEntity);
                entityIcon.show();
                
                DropDownList buildings = findPaneOfTypeByID(BUILDING_SELECTION_ID, DropDownList.class);

                buildings.setHandler(dropDownList -> onDropDownListChanged(dropDownList, pets.get(index).getEntityId()));
                buildings.setDataProvider(new DropDownList.DataProvider()
                {
                    @Override
                    public int getElementCount()
                    {
                        if (moduleView.getHerdingBuildings() == null)
                        {
                            return 0;
                        }

                        return moduleView.getHerdingBuildings().size();
                    }

                    @Override
                    public MutableComponent getLabel(final int index)
                    {

                        if (index == -1 || moduleView.getHerdingBuildings() == null)
                        {
                            return Component.literal("Unassigned");
                        }

                        if (index >= moduleView.getHerdingBuildings().size())
                        {
                            return Component.empty();
                        }

                        return Component.translatableEscape((String) moduleView.getHerdingBuildings().get(index).getBuildingDisplayName());
                    }
                });

                BlockPos buildingId = pets.get(index).getWorkBuilding() != null ? pets.get(index).getWorkBuilding().getID() : BlockPos.ZERO;

                IBuildingView currentWorkAssignment = moduleView.getColony().getBuilding(buildingId);
                buildings.setSelectedIndex(buildingIndex(currentWorkAssignment));
            }

            /**
             * Returns the index of the given building in the list of herding buildings.
             * @param building the building to find the index of.
             * @return the index of the given building in the list of herding buildings, or -1 if the building is not in the list.
             */
            private int buildingIndex(final IBuildingView building)
            {
                int index = -1;
                
                if (building != null)
                {
                    index = moduleView.getHerdingBuildings().indexOf(building);
                }
                
                // MCTradePostMod.LOGGER.info("Index of building {} in herding buildings: {}", building, index);

                return index;
            }

            /**
             * Called when the selection in the dropdown list changes.
             * 
             * @param dropDownList the changed dropdown list.
             * @param selectedEntity the entity ID of the pet that the selected building is for.
             */
            private void onDropDownListChanged(final DropDownList dropDownList, int selectedEntity)
            {
                // We need to prevent the dropdown list from being updated while the module view is dirty
                // Our server signal needs to reach the server and be processed then show up back in the view before we respond to more inputs
                if (moduleView.isDirty())
                {
                    return;
                }

                if (dropDownList.getSelectedIndex() == -1 && selectedBuilding != null)
                {
                    PetMessage petMessage = new PetMessage(buildingView, PetAction.ASSIGN, selectedEntity);
                    petMessage.setWorkBuilding(BlockPos.ZERO);
                    petMessage.sendToServer();
                    moduleView.markDirty();
                    selectedBuilding = null;
                    return;
                }

                final IBuildingView temp = moduleView.getHerdingBuildings().get(dropDownList.getSelectedIndex());
                if (!temp.equals(selectedBuilding))
                {
                    selectedBuilding = temp;
                    PetMessage petMessage = new PetMessage(buildingView, PetAction.ASSIGN, selectedEntity);
                    petMessage.setWorkBuilding(selectedBuilding.getID());
                    petMessage.sendToServer();
                    moduleView.markDirty();
                }
            }

        });
    }

}
