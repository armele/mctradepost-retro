package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.ThriftShopOffersModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ThriftShopMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketOffer;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketDailyRoller.MarketTier;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Gradient;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.logging.Logger;

public class WindowThriftShopOffersModule extends AbstractModuleWindow<ThriftShopOffersModuleView>
{
    @SuppressWarnings("unused")
    private Logger LOGGER = Logger.getLogger(MCTradePostMod.MODID);
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutthriftshop.xml";

    private static final String OFFER_ICON  = "offerIcon";
    private static final String OFFER_NAME  = "offerName";
    private static final String OFFER_TAKE  = "offerTake";
    private static final String OFFER_PRICE = "offerPrice";
    private static final String REROLL_OFFERS  = "rerollOffers";
    private static final String REROLL_PRICE  = "rerollPrice";

    /**
     * Ingredient scrolling list.
     */
    private final ScrollingList offerList;

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
    public WindowThriftShopOffersModule(final IBuildingView buildingView, final ThriftShopOffersModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        this.buildingView = buildingView;
        offerList = this.window.findPaneOfTypeByID("offerlist", ScrollingList.class);
        registerButton(OFFER_TAKE, this::takeOffer);
        registerButton(REROLL_OFFERS, this::rerollOffers);

        updateRerollPrice();

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
     * Updates the reroll price pane to display the current reroll cost.
     * If the reroll cost is greater than 0, the pane is updated to display the formatted cost.
     * If the reroll cost is 0 or less, the pane is hidden and the reroll button is also hidden.
     */
    protected void updateRerollPrice()
    {
        int rerollPrice = moduleView.getRerollCost();

        Text rerollPricePane = this.window.findPaneOfTypeByID(REROLL_PRICE, Text.class);

        if (rerollPrice > 0)
        {
            String formattedDefault = NumberFormat.getIntegerInstance().format(rerollPrice);

            rerollPricePane.setText(Component.literal(formattedDefault + "‡"));
        }
        else
        {
            rerollPricePane.hide();
            Button rerollButton = this.window.findPaneOfTypeByID(REROLL_OFFERS, Button.class);
            rerollButton.hide();
        }
    }

    /**
     * Called when the user clicks on an offer in the list.
     * Sends a ThriftShopMessage to the server with the action PURCHASE and the selected offer's stack and price.
     * 
     * @param button the button that was clicked, which is used to determine which offer was selected
     */
    private void takeOffer(final Button button)
    {
        final int row = offerList.getListElementIndexByPane(button);
        final MarketOffer offer =  moduleView.getOffers().get(row);
        
        new ThriftShopMessage(buildingView,ThriftShopMessage.ThriftAction.PURCHASE, offer.stack(), offer.price()).sendToServer();
    }

    /**
     * Called when the reroll button is clicked.
     * 
     * @param button the button that was clicked.
     */
    private void rerollOffers(final Button button)
    {   
        new ThriftShopMessage(buildingView,ThriftShopMessage.ThriftAction.REROLL, ItemStack.EMPTY, -1).sendToServer();
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
                updateRerollPrice();
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
                final Gradient gradient = rowPane.findPaneOfTypeByID("gradient", Gradient.class);

                if (offer.tier() == MarketTier.TIER4_EPIC)
                {
                    gradient.setGradientStart(230, 170, 255, 255);
                    gradient.setGradientEnd(178, 96, 255, 255);
                }
                else if (offer.tier() == MarketTier.TIER3_RARE)
                {
                    gradient.setGradientStart(120, 236, 255, 255);
                    gradient.setGradientEnd(40, 190, 225, 255);
                }
                else if (offer.tier() == MarketTier.TIER2_UNCOMMON)
                {
                    gradient.setGradientStart(255, 232, 120, 255);
                    gradient.setGradientEnd(255, 195, 64, 255);
                } 
                else 
                {
                    gradient.setGradientStart(235, 235, 235, 255);
                    gradient.setGradientEnd(200, 200, 200, 255);
                }

                rowPane.findPaneOfTypeByID(OFFER_NAME, Text.class).setText(resource.getHoverName());

                Text pricePane = rowPane.findPaneOfTypeByID(OFFER_PRICE, Text.class);

                if (offer.price() > 10000)
                {
                    pricePane.setText(Component.literal(offer.price() / 1000 + "k‡"));
                }
                else 
                {
                    pricePane.setText(Component.literal(offer.price() + "‡"));
                }

                PaneBuilders.tooltipBuilder()
                        .append(Component.literal(offer.price() + "‡"))
                        .hoverPane(pricePane)
                        .build();

                rowPane.findPaneOfTypeByID(OFFER_ICON, ItemIcon.class).setItem(resource);
            }
        });
        
    }
}
