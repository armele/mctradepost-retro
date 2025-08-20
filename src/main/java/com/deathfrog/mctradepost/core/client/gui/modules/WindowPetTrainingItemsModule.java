package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.EntityIcon;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;
import static com.minecolonies.api.util.constant.WindowConstants.*;

public class WindowPetTrainingItemsModule extends AbstractModuleWindow
{
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = ":gui/layouthuts/layoutpettrainingitems.xml";

    private static final String LABEL_PETLIST = "petlist";
    private static final String LABEL_TYPE = "pettype";
    private static final String LABEL_TRAINING_ITEM = "trainingitem";
    private static final String LABEL_HOWTO = "howto";

    /**
     * Resource scrolling list.
     */
    private final ScrollingList petList;


    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowPetTrainingItemsModule(final IBuildingView building)
    {
        super(building, MCTradePostMod.MODID + RESOURCE_STRING);

        petList = this.window.findPaneOfTypeByID(LABEL_PETLIST, ScrollingList.class);

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

        final Text howto = findPaneOfTypeByID(LABEL_HOWTO, Text.class);
        final AbstractTextBuilder.TooltipBuilder howtoTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(howto);
        howtoTipBuilder.append(Component.translatable("com.minecolonies.coremod.gui.petstore.trainingtips.hover"));
        howtoTipBuilder.build();
        
        updatePetTrainingList();
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updatePetTrainingList()
    {
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
                return PetTypes.values().length;
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

                final EntityIcon entityIcon = rowPane.findPaneOfTypeByID(ENTITY_ICON, EntityIcon.class);

                Entity previewEntity = PetTypes.values()[index].getEntityType().create(Minecraft.getInstance().level);
                
                if (previewEntity != null)
                {
                    entityIcon.setEntity(previewEntity);
                    final AbstractTextBuilder.TooltipBuilder hoverPaneBuilder = PaneBuilders.tooltipBuilder().hoverPane(entityIcon);
                    hoverPaneBuilder.append(previewEntity.getName());
                    hoverPaneBuilder.build();
                    entityIcon.show();
                    final Text petType = rowPane.findPaneOfTypeByID(LABEL_TYPE, Text.class);
                    Component type = PetTypes.values()[index].getEntityType().getDescription();
                    String typeText = type.getString();
                    petType.setText(Component.literal(typeText));       // If we use the component directly it comes out white for some reason
                    petType.setColors(Color.getByName("black", 0));
                }
                else
                {
                    entityIcon.hide();
                }

                final ItemIcon trainingStackDisplay = rowPane.findPaneOfTypeByID(LABEL_TRAINING_ITEM, ItemIcon.class);
                if (!PetTypes.values()[index].getTrainingItem().isEmpty())
                {
                    trainingStackDisplay.setVisible(true);
                    trainingStackDisplay.setItem(PetTypes.values()[index].getTrainingItem());
                }
            }

        });
    }

}
