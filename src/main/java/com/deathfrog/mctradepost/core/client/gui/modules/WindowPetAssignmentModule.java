package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetAssignmentModuleView;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetAssignmentModuleView.PetWorkingLocationData;
import com.deathfrog.mctradepost.api.colony.buildings.views.PetshopView;
import com.deathfrog.mctradepost.api.entity.pets.PetData;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetMessage.PetAction;
import com.google.common.collect.ImmutableList;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.EntityIcon;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import static com.minecolonies.api.util.constant.WindowConstants.*;

public class WindowPetAssignmentModule extends AbstractModuleWindow<PetAssignmentModuleView>
{
    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutpetassignment.xml";
    private static final String LABEL_HOWTO = "howto";
    private static final String IMAGE_HELP = "help";
    private static final String LABEL_PET_COUNT = "petcount";
    private static final String PET_COUNT_TOOLTIP = "com.minecolonies.coremod.gui.petstore.petcount.hover";
    private static final String LABEL_TYPE = "pettype";
    private static final String LABEL_OOR = "entityoor";
    private static final String BUILDING_SELECTION_ID = "buildings";
    private ArrayList<BlockPos> selectedBuildings = new ArrayList<>();

    /**
     * Resource scrolling list.
     */
    private final ScrollingList petList;

    public static String TAG_BUTTON_SUMMONPET = "summonPet";
    public static String TAG_BUTTON_FREEPET = "freePet";

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowPetAssignmentModule(final PetAssignmentModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));

        registerButton(TAG_BUTTON_SUMMONPET, this::summonPet);
        registerButton(TAG_BUTTON_FREEPET, this::freePet);

        petList = this.window.findPaneOfTypeByID("petlist", ScrollingList.class);

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
        
        // Send a message to the server forcing relevent building views to update.
        PetMessage petMessage = new PetMessage(buildingView, PetAction.QUERY, null);
        petMessage.sendToServer();

        final Text howto = findPaneOfTypeByID(LABEL_HOWTO, Text.class);
        final AbstractTextBuilder.TooltipBuilder howtoTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(howto);
        howtoTipBuilder.append(Component.translatable("com.minecolonies.coremod.gui.petstore.worklocations.hover"));
        howtoTipBuilder.build();

        final Image help = findPaneOfTypeByID(IMAGE_HELP, Image.class);
        final AbstractTextBuilder.TooltipBuilder helpTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(help);
        helpTipBuilder.append(Component.translatable("com.minecolonies.coremod.gui.petstore.worklocations.hover"));
        helpTipBuilder.build();

        final PetshopView petshopView = (PetshopView) buildingView;
        final int petCount = petshopView.getPets().size();
        final int maxPets = petshopView.getMaxPets();
        final Text petCountLabel = findPaneOfTypeByID(LABEL_PET_COUNT, Text.class);
        petCountLabel.setText(Component.literal(petCount + " / " + maxPets));
        PaneBuilders.tooltipBuilder().hoverPane(petCountLabel).build().setText(
            Component.translatable(PET_COUNT_TOOLTIP, petCount, maxPets));

        updatePetAssignmentList();

        MCTradePostMod.LOGGER.info("Pet working locations available: {}", moduleView.getPetWorkLocations().size());
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updatePetAssignmentList()
    {
        selectedBuildings.clear();
        for (PetData<?> pet : ((PetshopView) buildingView).getPets())
        {
            selectedBuildings.add(pet.getWorkLocation());
        }

        petList.enable();
        petList.show();
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
                @SuppressWarnings("rawtypes")
                ImmutableList<PetData> pets = ((PetshopView) buildingView).getPets();

                rowPane.findPaneOfTypeByID(LABEL_TYPE, Text.class).setText(Component.literal(pets.get(index).getAnimalType()));
                final EntityIcon entityIcon = rowPane.findPaneOfTypeByID(ENTITY_ICON, EntityIcon.class);
                
                ClientLevel level = Minecraft.getInstance().level;

                if (level == null)
                {
                    return;
                }

                final ButtonImage summonPetButton = rowPane.findPaneOfTypeByID(TAG_BUTTON_SUMMONPET, ButtonImage.class);
                PaneBuilders.tooltipBuilder().hoverPane(summonPetButton).build().setText(Component.translatable("com.minecolonies.coremod.gui.petstore.summon"));

                Button freePetButton = rowPane.findPaneOfTypeByID(TAG_BUTTON_FREEPET, Button.class);
                PaneBuilders.tooltipBuilder().hoverPane(freePetButton).build().setText(Component.translatable("com.minecolonies.coremod.gui.petstore.setfree"));


                Entity selectedEntity = level.getEntity(pets.get(index).getEntityId());
                final Text entityOor = rowPane.findPaneOfTypeByID(LABEL_OOR, Text.class);
                
                if (selectedEntity != null)
                {
                    entityIcon.setEntity(selectedEntity);
                    final AbstractTextBuilder.TooltipBuilder hoverPaneBuilder = PaneBuilders.tooltipBuilder().hoverPane(entityIcon);

                    Component customName = selectedEntity.getCustomName();

                    if (customName != null && !customName.toString().isEmpty()) 
                    {
                        hoverPaneBuilder.append(selectedEntity.getCustomName());
                    }
                    else
                    {
                        hoverPaneBuilder.append(selectedEntity.getName());
                    }

                    BlockPos entityPos = selectedEntity.getOnPos();
                    String entityPosStr = entityPos.toShortString() + "";
         
                    hoverPaneBuilder.appendNL(Component.literal(entityPosStr));

                    if (selectedEntity instanceof LivingEntity living) 
                    {
                        hoverPaneBuilder.appendNL(
                            Component.literal("HP: " +(int)living.getHealth() + " / " + (int)living.getMaxHealth())
                        );
                    }

                    hoverPaneBuilder.build();
                    entityIcon.show();
                    entityOor.hide();
                }
                else
                {
                    entityIcon.hide();
                    entityOor.show();
                }
                    
                DropDownList buildings = rowPane.findPaneOfTypeByID(BUILDING_SELECTION_ID, DropDownList.class);
                final List<PetWorkingLocationData> workLocations = getSortedWorkLocations();

                buildings.setHandler(dropDownList -> onDropDownListChanged(dropDownList, workLocations, index, pets.get(index).getEntityUuid()));
                buildings.setDataProvider(new DropDownList.DataProvider()
                {
                    @Override
                    public int getElementCount()
                    {
                        return workLocations.size();
                    }

                    @SuppressWarnings("null")
                    @Override
                    public MutableComponent getLabel(final int index)
                    {

                        if (index == -1)
                        {
                            return Component.literal("Unassigned");
                        }

                        if (index >= workLocations.size())
                        {
                            return Component.empty();
                        }

                        final PetWorkingLocationData workLocation = workLocations.get(index);
                        final int distance = (int) BlockPosUtil.getDistance(buildingView.getPosition(), workLocation.workLocation);
                        final Component direction = BlockPosUtil.calcDirection(buildingView.getPosition(), workLocation.workLocation).getShortText();
                        return Component.translatableEscape(workLocation.name)
                            .append(Component.literal(" - " + distance + "m "))
                            .append(direction);
                    }
                });

                BlockPos location = pets.get(index).getWorkLocation();
                buildings.setSelectedIndex(workLocationIndex(location, workLocations));
                updateSelectedLocationTooltip(buildings, location);
            }

            /**
             * Returns the index of the given building in the list of herding buildings.
             * @param location the work location to find.
             * @param workLocations the distance-sorted work locations.
             * @return the index of the given building in the list of herding buildings, or -1 if the building is not in the list.
             */
            private int workLocationIndex(final BlockPos location, final List<PetWorkingLocationData> workLocations)
            {
                int index = -1;
                
                if (location != null && !BlockPos.ZERO.equals(location))
                {
                    PetWorkingLocationData workData = moduleView.getPetWorkLocationsMap().get(location);
                    index = workLocations.indexOf(workData);
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
            private void onDropDownListChanged(
                final DropDownList dropDownList,
                final List<PetWorkingLocationData> workLocations,
                final int rowIndex,
                final UUID selectedEntity)
            {
                final int selectedIndex = dropDownList.getSelectedIndex();
                if (selectedIndex >= 0 && selectedIndex < workLocations.size())
                {
                    updateSelectedLocationTooltip(dropDownList, workLocations.get(selectedIndex).workLocation);
                }

                // We need to prevent the dropdown list from being updated while the module view is dirty
                // Our server signal needs to reach the server and be processed then show up back in the view before we respond to more inputs
                if (moduleView.isDirty())
                {
                    return;
                }

                if (dropDownList.getSelectedIndex() == -1 && selectedBuildings.get(rowIndex) != null)
                {
                    PetMessage petMessage = new PetMessage(buildingView, PetAction.ASSIGN, selectedEntity);
                    petMessage.setWorkLocation(BlockPos.ZERO);
                    petMessage.sendToServer();
                    moduleView.markDirty();
                    selectedBuildings.set(rowIndex, null);
                    updateSelectedLocationTooltip(dropDownList, null);
                    return;
                }

                final BlockPos temp = workLocations.get(dropDownList.getSelectedIndex()).workLocation;
                if (!temp.equals(selectedBuildings.get(rowIndex)))
                {
                    selectedBuildings.set(rowIndex, temp);
                    PetMessage petMessage = new PetMessage(buildingView, PetAction.ASSIGN, selectedEntity);
                    petMessage.setWorkLocation(selectedBuildings.get(rowIndex));
                    petMessage.sendToServer();
                    moduleView.markDirty();
                }
            }

        });
    }

    /**
     * Returns the available work locations in deterministic nearest-first order.
     */
    private List<PetWorkingLocationData> getSortedWorkLocations()
    {
        final BlockPos hutPosition = buildingView.getPosition();
        final List<PetWorkingLocationData> workLocations = new ArrayList<>(moduleView.getPetWorkLocations());
        workLocations.sort(Comparator
            .comparingLong((PetWorkingLocationData location) -> BlockPosUtil.getDistanceSquared(hutPosition, location.workLocation))
            .thenComparingInt(location -> location.workLocation.getX())
            .thenComparingInt(location -> location.workLocation.getY())
            .thenComparingInt(location -> location.workLocation.getZ()));
        return workLocations;
    }

    /**
     * Shows the exact position for the value displayed by the collapsed dropdown.
     */
    private void updateSelectedLocationTooltip(final DropDownList dropDownList, final BlockPos workLocation)
    {
        final Button selectionButton = dropDownList.findFirstPaneByType(Button.class);
        selectionButton.setHoverPane(null);
        if (workLocation != null && !BlockPos.ZERO.equals(workLocation))
        {
            PaneBuilders.tooltipBuilder()
                .hoverPane(selectionButton)
                .append(Component.literal(workLocation.toShortString() + ""))
                .build();
        }
    }



    /**
     * Frees the pet associated with the given button.
     * @param button the button to free the pet associated with.
     */
    @SuppressWarnings("rawtypes")
    private void freePet(@NotNull final Button button)
    {
        final int row = petList.getListElementIndexByPane(button);
        ImmutableList<PetData> pets = ((PetshopView) buildingView).getPets();
        UUID selectedPet = pets.get(row).getEntityUuid();
        PetMessage petMessage = new PetMessage(buildingView, PetAction.FREE, selectedPet);
        petMessage.sendToServer();
    }

    /**
     * Summons the pet associated with the given button.
     * @param button the button to free the pet associated with.
     */
    @SuppressWarnings("rawtypes")
    private void summonPet(@NotNull final Button button)
    {
        final int row = petList.getListElementIndexByPane(button);
        ImmutableList<PetData> pets = ((PetshopView) buildingView).getPets();
        UUID selectedPet = pets.get(row).getEntityUuid();
        PetMessage petMessage = new PetMessage(buildingView, PetAction.SUMMON, selectedPet);
        petMessage.sendToServer();
    }

}
