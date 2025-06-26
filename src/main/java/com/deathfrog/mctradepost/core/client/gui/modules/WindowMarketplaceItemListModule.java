package com.deathfrog.mctradepost.core.client.gui.modules;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.MarketplaceItemListModuleView;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.modules.IItemListModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.modules.ItemListModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import static com.minecolonies.api.util.constant.WindowConstants.*;
import static org.jline.utils.AttributedStyle.WHITE;

public class WindowMarketplaceItemListModule extends ItemListModuleWindow
{
    public static final String RESOURCE_VALUE = "resourceValue";

    protected MarketplaceItemListModuleView moduleView = null;

    public WindowMarketplaceItemListModule(String res, IBuildingView building, IItemListModuleView moduleView)
    {
        super(res, building, moduleView);
        this.moduleView = (MarketplaceItemListModuleView) moduleView;
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    protected void updateResourceList()
    {
        resourceList.enable();
        resourceList.show();

        //Creates a dataProvider for the unemployed resourceList.
        resourceList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return currentDisplayedList.size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                final ItemStack resource = currentDisplayedList.get(index).getItemStack();
                final Text resourceLabel = rowPane.findPaneOfTypeByID(RESOURCE_NAME, Text.class);
                resourceLabel.setText(resource.getHoverName());
                resourceLabel.setColors(WHITE);

                
                final Text resourceValue = rowPane.findPaneOfTypeByID(RESOURCE_VALUE, Text.class);
                resourceValue.setText(Component.literal(moduleView.getValueForItem(resource.getItem()) + ""));
                resourceValue.setColors(WHITE);

                rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class).setItem(resource);
                final boolean isAllowedItem  = building.getModuleViewMatching(MarketplaceItemListModuleView.class, view -> view.getId().equals(id)).isAllowedItem(new ItemStorage(resource));
                final Button switchButton = rowPane.findPaneOfTypeByID(BUTTON_SWITCH, Button.class);

                if ((isInverted && !isAllowedItem) || (!isInverted && isAllowedItem))
                {
                    switchButton.setText(Component.translatableEscape(ON));
                }
                else
                {
                    switchButton.setText(Component.translatableEscape(OFF));
                }
            }
        });
    }


    
}
