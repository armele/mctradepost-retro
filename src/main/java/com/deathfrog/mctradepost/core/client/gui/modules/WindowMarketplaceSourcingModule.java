package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.MarketplaceSourcingModuleView;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceSourcingMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.MarketplaceSourcingMessage.Action;
import com.deathfrog.mctradepost.core.colony.buildings.modules.thriftshop.MarketplaceSourcingModule.RetainedSearch;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.DropDownList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

/** Marketplace tab used to select retained searches and manage their prepaid investment. */
public class WindowMarketplaceSourcingModule extends AbstractModuleWindow<MarketplaceSourcingModuleView>
{
    private final IBuildingView buildingView;
    private final ScrollingList searchList;
    private int displayedSearchCount = -1;
    private int displayedSearchCapacity = -1;
    private final Map<Integer, Integer> selectedInvestmentLevels = new HashMap<>();

    /**
     * Creates the retained-search management window.
     *
     * @param buildingView owning Marketplace
     * @param moduleView synchronized sourcing state
     */
    public WindowMarketplaceSourcingModule(IBuildingView buildingView, MarketplaceSourcingModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "gui/layouthuts/layoutretainedsearch.xml"));
        this.buildingView = buildingView;
        this.searchList = window.findPaneOfTypeByID("searchlist", ScrollingList.class);
        registerButton("addSearch", this::addSearch);
        registerButton("removeSearch", this::removeSearch);
        registerButton("invest", this::toggleSearch);
    }

    /** Opens the item picker for explicitly tagged Rare Finds. */
    private void addSearch()
    {
        new WindowSelectStewIngredients(this,
            stack -> !stack.is(ModTags.ITEMS.RARE_FINDS_BLACKLIST_TAG)
                && (stack.is(ModTags.ITEMS.RARE_FINDS_TIER1_TAG) || stack.is(ModTags.ITEMS.RARE_FINDS_TIER2_TAG)
                || stack.is(ModTags.ITEMS.RARE_FINDS_TIER3_TAG) || stack.is(ModTags.ITEMS.RARE_FINDS_TIER4_TAG)),
            (stack, ignored) -> new MarketplaceSourcingMessage(buildingView, Action.ADD_SEARCH, stack, 0).sendToServer(), true).open();
    }

    /** Removes the retained-search row containing the clicked button. */
    private void removeSearch(Button button)
    {
        sendRowAction(button, Action.REMOVE_SEARCH, 0);
    }

    /** Starts the selected investment or stops the active retained search. */
    private void toggleSearch(Button button)
    {
        int row = searchList.getListElementIndexByPane(button);
        if (row < 0 || row >= moduleView.getSearches().size()) return;
        RetainedSearch search = moduleView.getSearches().get(row);
        if (search.investmentUntil() > moduleView.getCurrentDay())
        {
            sendRowAction(button, Action.CANCEL_INVESTMENT, 0);
            return;
        }
        sendRowAction(button, Action.INVEST, selectedInvestmentLevels.getOrDefault(row, 1));
    }

    /** Sends an action for the row containing a clicked control. */
    private void sendRowAction(Button button, Action action, int level)
    {
        int row = searchList.getListElementIndexByPane(button);
        if (row < 0 || row >= moduleView.getSearches().size()) return;
        new MarketplaceSourcingMessage(buildingView, action, moduleView.getSearches().get(row).stack(), level).sendToServer();
    }

    /** {@inheritDoc} */
    @Override
    public void onOpened()
    {
        super.onOpened();
        Image help = findPaneOfTypeByID("help", Image.class);
        PaneBuilders.tooltipBuilder().hoverPane(help).build()
            .setText(Component.translatable("mctradepost.retained_search.help.tooltip"));
        updateList();
    }

    /** Refreshes capacity controls after the server synchronizes a changed search list. */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (displayedSearchCount != moduleView.getSearches().size()
            || displayedSearchCapacity != moduleView.getSearchCapacity())
        {
            updateSearchCapacityControls();
        }
    }

    /** Populates retained searches and their remaining prepaid duration. */
    private void updateList()
    {
        updateSearchCapacityControls();
        searchList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override public int getElementCount() { return moduleView.getSearches().size(); }

            @Override
            public void updateElement(int index, @NotNull Pane row)
            {
                RetainedSearch search = moduleView.getSearches().get(index);
                ItemStack display = search.stack().copy();
                row.findPaneOfTypeByID("searchIcon", ItemIcon.class).setItem(display);
                row.findPaneOfTypeByID("searchName", Text.class).setText(display.getHoverName());
                long days = Math.max(0L, search.investmentUntil() - moduleView.getCurrentDay());
                boolean active = days > 0;
                row.findPaneOfTypeByID("investment", Text.class).setText(Component.literal(active
                    ? "L" + search.investmentLevel() + " - " + investmentChanceLabel(search.investmentLevel())
                        + " (" + days + " " + (days == 1 ? "Day" : "Days") + ")"
                    : ""));

                DropDownList investmentLevel = row.findPaneOfTypeByID("investmentLevel", DropDownList.class);
                investmentLevel.setDataProvider(new DropDownList.DataProvider()
                {
                    @Override public int getElementCount() { return 3; }

                    @Override
                    public MutableComponent getLabel(int option)
                    {
                        int level = option + 1;
                        return Component.literal("Level " + level + " - " + investmentChanceLabel(level));
                    }
                });
                int selectedLevel = active ? search.investmentLevel() : selectedInvestmentLevels.getOrDefault(index, 1);
                investmentLevel.setSelectedIndex(selectedLevel - 1);
                investmentLevel.setHandler(dropDown -> selectedInvestmentLevels.put(index, dropDown.getSelectedIndex() + 1));
                investmentLevel.enable();
                if (active) investmentLevel.disable();
                PaneBuilders.tooltipBuilder().hoverPane(investmentLevel).build()
                    .setText(Component.translatable("mctradepost.retained_search.invest.tooltip"));

                Button invest = row.findPaneOfTypeByID("invest", Button.class);
                invest.setText(Component.translatable(active
                    ? "mctradepost.retained_search.stop"
                    : "mctradepost.retained_search.start"));
                PaneBuilders.tooltipBuilder().hoverPane(invest).build()
                    .setText(Component.translatable(active
                        ? "mctradepost.retained_search.stop.tooltip"
                        : "mctradepost.retained_search.invest.tooltip"));
            }
        });
    }

    /** Updates the capacity label and resets the Add button's enabled state and tooltip. */
    private void updateSearchCapacityControls()
    {
        if (displayedSearchCount != moduleView.getSearches().size())
        {
            selectedInvestmentLevels.clear();
        }
        displayedSearchCount = moduleView.getSearches().size();
        displayedSearchCapacity = moduleView.getSearchCapacity();
        window.findPaneOfTypeByID("capacity", Text.class).setText(Component.literal(
            displayedSearchCount + " / " + displayedSearchCapacity));

        Button add = window.findPaneOfTypeByID("addSearch", Button.class);
        add.enable();
        PaneBuilders.tooltipBuilder().hoverPane(add).build().setText(Component.empty());
        if (displayedSearchCapacity <= displayedSearchCount)
        {
            add.disable();
            if (displayedSearchCapacity == 0)
            {
                PaneBuilders.tooltipBuilder().hoverPane(add).build()
                    .setText(Component.translatable("mctradepost.research.unlock_tooltip"));
            }
        }
    }

    /**
     * Formats the configured total retained-search chance for an investment level.
     *
     * @param level investment level from one through three
     * @return rounded percentage label
     */
    private @Nonnull String investmentChanceLabel(int level)
    {
        double bonus = switch (level)
        {
            case 1 -> MCTPConfig.retainedSearchInvestmentLevelOneBonus.get();
            case 2 -> MCTPConfig.retainedSearchInvestmentLevelTwoBonus.get();
            case 3 -> MCTPConfig.retainedSearchInvestmentLevelThreeBonus.get();
            default -> 0.0D;
        };
        return Math.round((MCTPConfig.retainedSearchBaseChance.get() + bonus) * 100.0D) + "%";
    }
}
