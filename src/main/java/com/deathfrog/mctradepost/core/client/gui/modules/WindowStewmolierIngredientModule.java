package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StewmelierIngredientModuleView;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewIngredientMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.constant.WindowConstants;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class WindowStewmolierIngredientModule extends AbstractModuleWindow<StewmelierIngredientModuleView>
{
    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutstewingredient.xml";
    private static final String INGREDIENT_ADD = "addIngredient";
    private static final String INGREDIENT_REMOVE = "removeIngredient";
    private static final String INGREDIENT_NAME = "ingredientName";
    private static final String INGREDIENT_ICON = "ingredientIcon";

    /**
     * The quantity label
     */
    private static final String LABEL_QUANTITY = "ingredientProtectedQuantity";

    /**
     * Ingredient scrolling list.
     */
    private final ScrollingList ingredientList;

    /**
     * The matching module view to the window.
     */
    private final IBuildingView buildingView;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowStewmolierIngredientModule(final IBuildingView buildingView, final StewmelierIngredientModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        this.buildingView = buildingView;
        ingredientList = this.window.findPaneOfTypeByID("ingredientlist", ScrollingList.class);

        registerButton(INGREDIENT_ADD, this::addIngredient);

        registerButton(INGREDIENT_REMOVE, this::removeIngredient);
    }

    /**
     * Remove the ingredient.
     *
     * @param button the button.
     */
    private void removeIngredient(final Button button)
    {
        // LOGGER.info("Removing Ingredient");

        final int row = ingredientList.getListElementIndexByPane(button);
        final ItemStorage ingredient = moduleView.getIngredients().get(row);
        moduleView.getIngredients().remove(row);
        new StewIngredientMessage(buildingView,StewIngredientMessage.IngredientAction.REMOVE, ingredient.getItemStack(), ingredient.getAmount()).sendToServer();
        updateIngredientList();
    }

    /**
     * Add an ingredient.
     */
    private void addIngredient()
    {

        // LOGGER.info("Adding Ingredient");

        new WindowSelectStewIngredients(this,
            (stack) -> stack.is(ModTags.STEW_INGREDIENTS_TAG),
            (stack, quantity) -> new StewIngredientMessage(buildingView, StewIngredientMessage.IngredientAction.ADD, stack, quantity).sendToServer()).open();
        
        updateIngredientList();

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
        updateIngredientList();
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updateIngredientList()
    {
        ingredientList.enable();
        ingredientList.show();

        // Creates a dataProvider for the unemployed ingredientList.
        ingredientList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return moduleView.getIngredients().size();
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
                final ItemStack resource = moduleView.getIngredients().get(index).getItemStack().copy();
                resource.setCount(moduleView.getIngredients().get(index).getAmount());

                rowPane.findPaneOfTypeByID(INGREDIENT_NAME, Text.class).setText(resource.getHoverName());
                rowPane.findPaneOfTypeByID(LABEL_QUANTITY, Text.class)
                    .setText(Component.literal(String.valueOf(moduleView.getIngredients().get(index).getAmount()) + ""));
                rowPane.findPaneOfTypeByID(INGREDIENT_ICON, ItemIcon.class).setItem(resource);
            }
        });
    }
}
