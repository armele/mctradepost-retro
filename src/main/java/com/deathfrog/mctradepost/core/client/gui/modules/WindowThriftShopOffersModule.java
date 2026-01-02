package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.ThriftShopOffersModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.mod.Log;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class WindowThriftShopOffersModule extends AbstractModuleWindow<ThriftShopOffersModuleView>
{
    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutthriftshop.xml";

    private static final String OFFER_ICON = "offerIcon";
    private static final String OFFER_NAME = "offerName";
    private static final String OFFER_TAKE = "offerTake";

    /**
     * Ingredient scrolling list.
     */
    private final ScrollingList offerList;

    /**
     * The matching module view to the window.
     */
    @SuppressWarnings("unused")
    private final IBuildingView buildingView;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowThriftShopOffersModule(final IBuildingView buildingView, final ThriftShopOffersModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        this.buildingView = buildingView;
        offerList = this.window.findPaneOfTypeByID("offerlist", ScrollingList.class);
        registerButton(OFFER_TAKE, this::takeOffer);
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
        updateOfferList();
    }

    /**
     * Remove the ingredient.
     *
     * @param button the button.
     */
    private void takeOffer(final Button button)
    {
        // LOGGER.info("Removing Ingredient");

        final int row = offerList.getListElementIndexByPane(button);
        final MarketOffer offer =  moduleView.getOffers().get(row);

        // TODO: Remove the offer from the list or disable the buy button.
        // TODO: Send a message to the server which deducts funds, adds the bought item and plays the cash register sounds.

        Log.getLogger().info("Offer taken: {}", offer.stack().getHoverName());
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updateOfferList()
    {
        offerList.enable();
        offerList.show();

        // Creates a dataProvider for the unemployed ingredientList.
        offerList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return moduleView.getOffers().size();
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
                MarketOffer offer = moduleView.getOffers().get(index);
                final ItemStack resource = offer.stack().copy();

                // TODO: Add price to the row.
                rowPane.findPaneOfTypeByID(OFFER_NAME, Text.class).setText(resource.getHoverName());
                rowPane.findPaneOfTypeByID(OFFER_ICON, ItemIcon.class).setItem(resource);
            }
        });
        
    }
}
