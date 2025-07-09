package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.BuildingStationImportModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ExportData.TradeDefinition;
import com.deathfrog.mctradepost.core.colony.buildings.modules.TradeMessage;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import static com.minecolonies.api.util.constant.WindowConstants.*;

import java.util.logging.Logger;

public class WindowStationImportModule extends AbstractModuleWindow
{
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = ":gui/layouthuts/layoutstationimport.xml";

    /**
     * Limit reached label.
     */
    private static final String LABEL_LIMIT_REACHED = "com.minecolonies.coremod.gui.trade.limitreached";

    /**
     * The price label
     */
    private static final String LABEL_PRICE = "resourcePrice";

    /**
     * The quantity label
     */
    private static final String LABEL_QUANTITY = "resourceQuantity";

    /**
     * Resource scrolling list.
     */
    private final ScrollingList resourceList;

    /**
     * The matching module view to the window.
     */
    private final BuildingStationImportModuleView moduleView;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowStationImportModule(final IBuildingView building, final BuildingStationImportModuleView moduleView)
    {
        super(building, MCTradePostMod.MODID + RESOURCE_STRING);

        resourceList = this.window.findPaneOfTypeByID("resourcesstock", ScrollingList.class);
        this.moduleView = moduleView;

        registerButton(STOCK_ADD, this::addStock);
        if (moduleView.hasReachedLimit())
        {
            final ButtonImage button = findPaneOfTypeByID(STOCK_ADD, ButtonImage.class);
            button.setText(Component.translatableEscape(LABEL_LIMIT_REACHED));
            button.setImage(
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "textures/gui/builderhut/builder_button_medium_disabled.png"));
        }

        registerButton(STOCK_REMOVE, this::removeStock);
    }

    /**
     * Remove the stock.
     *
     * @param button the button.
     */
    private void removeStock(final Button button)
    {
        final int row = resourceList.getListElementIndexByPane(button);
        final TradeDefinition selected = moduleView.getImports().get(row);
        moduleView.getImports().remove(row);
        new TradeMessage(buildingView,TradeMessage.TradeAction.REMOVE, TradeMessage.TradeType.IMPORT, null,
            selected.tradeItem().getItemStack(), selected.price(), selected.quantity()
            ).sendToServer();
        updateTradeList();
    }

    /**
     * Add the stock.
     */
    private void addStock()
    {
        if (!moduleView.hasReachedLimit())
        {
            new WindowSelectImportResources(this,
                (stack) -> true,
                (stack, price, quantity) -> new TradeMessage(buildingView, TradeMessage.TradeAction.ADD, TradeMessage.TradeType.IMPORT, null, stack, price, quantity).sendToServer()).open();
        }
        else
        {
            LOGGER.info("Import limit reached.");
        }
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
        resourceList.enable();
        resourceList.show();

        // Creates a dataProvider for the unemployed resourceList.
        resourceList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return moduleView.getImports().size();
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
                final ItemStack resource = moduleView.getImports().get(index).tradeItem().getItemStack().copy();
                resource.setCount(moduleView.getImports().get(index).quantity());

                rowPane.findPaneOfTypeByID(RESOURCE_NAME, Text.class).setText(resource.getHoverName());
                rowPane.findPaneOfTypeByID(LABEL_PRICE, Text.class)
                    .setText(Component.literal(String.valueOf(moduleView.getImports().get(index).price()) + "‡"));
                rowPane.findPaneOfTypeByID(LABEL_QUANTITY, Text.class)
                    .setText(Component.literal(String.valueOf(moduleView.getImports().get(index).quantity())));
                rowPane.findPaneOfTypeByID(RESOURCE_ICON, ItemIcon.class).setItem(resource);
            }
        });
    }
}
