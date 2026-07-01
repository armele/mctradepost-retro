package com.deathfrog.mctradepost.core.client.gui.modules;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.ResortGuestListModuleView;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ResortGuestMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ResortGuestMessage.GuestAction;
import com.deathfrog.mctradepost.core.entity.ai.workers.minimal.Vacationer;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.AbstractTextBuilder;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class WindowGuestListModule extends AbstractModuleWindow<ResortGuestListModuleView>
{
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * The resource string.
     */
    private static final String RESOURCE_STRING = "gui/layouthuts/layoutguestlist.xml";

    private static final String LABEL_GUESTLIST = "guestlist";
    private static final String LABEL_NAME = "name";
    private static final String LABEL_ITEMBASE = "item";
    private static final String BUTTON_SUMMONGUEST = "summonGuest";

    /**
     * Resource scrolling list.
     */
    private final ScrollingList guestList;


    /**
     * Constructor for the minimum stock window view.
     *
     * @param building   class extending
     * @param moduleView the module view.
     */
    public WindowGuestListModule(final ResortGuestListModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        registerButton(BUTTON_SUMMONGUEST, this::summonGuest);
        guestList = this.window.findPaneOfTypeByID(LABEL_GUESTLIST, ScrollingList.class);

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

        updateGuestList();
    }

    /**
     * Updates the resource list in the GUI with the info we need.
     */
    private void updateGuestList()
    {
        guestList.enable();
        guestList.show();
        guestList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * 
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return moduleView.getGuests().size();
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

                Vacationer guest = moduleView.getGuests().get(index);

                final Text name = rowPane.findPaneOfTypeByID(LABEL_NAME, Text.class);

                ICitizenDataView citizen = moduleView.getColony().getCitizen(guest.getCivilianId());

                if (citizen != null)
                {
                    name.setText(Component.literal(citizen.getName() + ""));
                }
                else
                {
                    name.setText(Component.literal("Unknown"));
                }

                final ButtonImage summonGuestButton = rowPane.findPaneOfTypeByID(BUTTON_SUMMONGUEST, ButtonImage.class);
                PaneBuilders.tooltipBuilder().hoverPane(summonGuestButton).build().setText(Component.translatable("com.minecolonies.coremod.gui.guestlist.summon"));

                final AbstractTextBuilder.TooltipBuilder statusTipBuilder = PaneBuilders.tooltipBuilder().hoverPane(name);
                statusTipBuilder.append(Component.literal(guest.getState().toString() + ""));
                statusTipBuilder.build();

                List<ItemStorage> items = guest.getRemedyItems();
                for (ItemStorage item : items)
                {
                    final ItemIcon itemIcon = rowPane.findPaneOfTypeByID(LABEL_ITEMBASE + items.indexOf(item), ItemIcon.class);
                    itemIcon.setItem(item.getItemStack());
                }
            }

        });
    }

    /**
     * Summons the guest associated with the given button.
     * @param button the button associated with the guest to recall.
     */
    private void summonGuest(@NotNull final Button button)
    {
        final int row = guestList.getListElementIndexByPane(button);
        if (row < 0 || row >= moduleView.getGuests().size())
        {
            return;
        }

        Vacationer guest = moduleView.getGuests().get(row);
        ResortGuestMessage guestMessage = new ResortGuestMessage(buildingView, GuestAction.SUMMON, guest.getCivilianId());
        guestMessage.sendToServer();
    }

}
