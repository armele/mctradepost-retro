package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.PetTrainingItemsModuleView;
import com.deathfrog.mctradepost.api.entity.pets.PetTypes;
import com.deathfrog.mctradepost.api.research.MCTPResearchConstants;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.colony.buildings.workerbuildings.BuildingMarketplace;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.EntityIcon;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import static com.minecolonies.api.util.constant.WindowConstants.*;

public class WindowPetTrainingItemsModule extends AbstractModuleWindow<PetTrainingItemsModuleView>
{
    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutpettrainingitems.xml";

    private static final String LABEL_PETLIST = "petlist";
    private static final String LABEL_TYPE = "pettype";
    private static final String LABEL_TRAINING_ITEM = "trainingitem";
    private static final String LABEL_COIN_ITEM = "coinitem";
    private static final String LABEL_HOWTO = "howto";

    /**
     * Resource scrolling list.
     */
    private final ScrollingList petList;
    protected final int husbandryResearch;


    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowPetTrainingItemsModule(final PetTrainingItemsModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        husbandryResearch = (int) moduleView.getColony().getResearchManager().getResearchEffects().getEffectStrength(MCTPResearchConstants.HUSBANDRY);  

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
            List<PetTypes> activePetTypes = new ArrayList<PetTypes>();

            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {

                if (activePetTypes.size() > 0) return activePetTypes.size();

                // Make the list smart enough to be research-aware.
                for (PetTypes type : PetTypes.values())
                {
                    if (type.isPet() == true || husbandryResearch > 0)
                    {
                        activePetTypes.add(type);
                    }
                }

                return activePetTypes.size();
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
                ClientLevel level = Minecraft.getInstance().level;

                if (level == null)
                {
                    return;
                }

                final EntityIcon entityIcon = rowPane.findPaneOfTypeByID(ENTITY_ICON, EntityIcon.class);

                PetTypes petTypeEntry = activePetTypes.get(index);
                Entity previewEntity = petTypeEntry.getEntityType().create(level);
                
                if (previewEntity != null)
                {
                    entityIcon.setEntity(previewEntity);
                    final AbstractTextBuilder.TooltipBuilder hoverPaneBuilder = PaneBuilders.tooltipBuilder().hoverPane(entityIcon);
                    hoverPaneBuilder.append(previewEntity.getName());
                    hoverPaneBuilder.build();
                    entityIcon.show();
                    final Text petType = rowPane.findPaneOfTypeByID(LABEL_TYPE, Text.class);
                    Component type = petTypeEntry.getEntityType().getDescription();
                    String typeText = type.getString() + "";
                    petType.setText(Component.literal(typeText));       // If we use the component directly it comes out white for some reason
                    petType.setColors(Color.getByName("black", 0));
                }
                else
                {
                    entityIcon.hide();
                }

                final ItemIcon trainingStackDisplay = rowPane.findPaneOfTypeByID(LABEL_TRAINING_ITEM, ItemIcon.class);
                if (!petTypeEntry.getTrainingItem().isEmpty())
                {
                    trainingStackDisplay.setVisible(true);
                    trainingStackDisplay.setItem(petTypeEntry.getTrainingItem());
                }

                final ItemIcon coinStackDisplay = rowPane.findPaneOfTypeByID(LABEL_COIN_ITEM, ItemIcon.class);
                if (petTypeEntry.getCoinCost() > 0)
                {
                    coinStackDisplay.setVisible(true);
                    coinStackDisplay.setItem(new ItemStack(NullnessBridge.assumeNonnull(BuildingMarketplace.tradeCurrency()), petTypeEntry.getCoinCost()));
                }
            }

        });
    }

}
