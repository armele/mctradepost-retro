package com.deathfrog.mctradepost.core.client.gui.modules;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.moduleviews.StewmelierIngredientModuleView;
import com.deathfrog.mctradepost.core.ModTags;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewIngredientMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewTier;
import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.Color;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.Image;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class WindowStewmolierIngredientModule extends AbstractModuleWindow<StewmelierIngredientModuleView>
{
    private static final int BUTTON_COLOR_DEFAULT = Color.getByName("black", 0);
    private static final int BUTTON_COLOR_SELECTED = Color.getByName("green", 0);
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
    private static final String TIER_BASIC = "tierBasic";
    private static final String TIER_HEARTY = "tierHearty";
    private static final String TIER_GOURMET = "tierGourmet";

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
    private StewTier displayedDesiredTier;
    private StewTier displayedActualTier;
    private int displayedIngredientCount = -1;
    private int displayedKitchenLevel = -1;
    private int displayedStewServings = -1;
    private boolean displayedStewpotIdentified;
    private BlockPos displayedStewpotLocation = BlockPos.ZERO;
    private int displayedProteinCount = -1;
    private boolean displayedStewQualified;
    private int displayedWarehouseSnapshotVersion = -1;

    /**
     * Constructor for the minimum stock window view.
     *
     * @param buildingView building containing the module
     * @param moduleView the module view.
     */
    public WindowStewmolierIngredientModule(final IBuildingView buildingView, final StewmelierIngredientModuleView moduleView)
    {
        super(moduleView, ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, RESOURCE_STRING));
        this.buildingView = buildingView;
        ingredientList = this.window.findPaneOfTypeByID("ingredientlist", ScrollingList.class);

        registerButton(INGREDIENT_ADD, this::addIngredient);

        registerButton(INGREDIENT_REMOVE, this::removeIngredient);
        registerButton(TIER_BASIC, button -> selectTier(StewTier.BASIC));
        registerButton(TIER_HEARTY, button -> selectTier(StewTier.HEARTY));
        registerButton(TIER_GOURMET, button -> selectTier(StewTier.GOURMET));
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

        new WindowSelectItems(this,
            (stack) -> stack.is(ModTags.ITEMS.STEW_INGREDIENTS_TAG),
            (stack, quantity) -> new StewIngredientMessage(buildingView, StewIngredientMessage.IngredientAction.ADD, stack, quantity).sendToServer(),
            false,
            stack -> null,
            this::ingredientBadge,
            this::ingredientBadgeTooltip).open();
        
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
        final Image help = findPaneOfTypeByID("help", Image.class);
        PaneBuilders.tooltipBuilder().hoverPane(help).build()
            .setText(Component.translatable("mctradepost.stewmelier.ingredients.help.tooltip"));
        new StewIngredientMessage(buildingView, StewIngredientMessage.IngredientAction.QUERY).sendToServer();
        updateIngredientList();
        updateTierControls();
    }

    /** Refreshes tier controls when synchronized module state changes. */
    @Override
    public void onUpdate()
    {
        super.onUpdate();
        if (displayedWarehouseSnapshotVersion != moduleView.getWarehouseSnapshotVersion())
        {
            updateIngredientList();
            displayedWarehouseSnapshotVersion = moduleView.getWarehouseSnapshotVersion();
        }
        if (displayedDesiredTier != moduleView.getDesiredTier()
            || displayedActualTier != moduleView.getActualTier()
            || displayedIngredientCount != moduleView.getCreditedIngredientCount()
            || displayedKitchenLevel != moduleView.getKitchenLevel()
            || displayedStewServings != moduleView.getStewServings()
            || displayedStewpotIdentified != moduleView.isStewpotIdentified()
            || !displayedStewpotLocation.equals(moduleView.getStewpotLocation())
            || displayedProteinCount != moduleView.getCreditedProteinIngredientCount()
            || displayedStewQualified != moduleView.isStewQualified())
        {
            updateTierControls();
        }
    }

    /**
     * Requests a supported stew tier and updates the local selection immediately.
     *
     * @param tier requested stew tier
     */
    private void selectTier(final StewTier tier)
    {
        if (tier.getMinimumKitchenLevel() > moduleView.getKitchenLevel()) return;
        moduleView.setDesiredTier(tier);
        new StewIngredientMessage(buildingView, tier).sendToServer();
        updateTierControls();
    }

    /**
     * Builds the compact category badge displayed beside an ingredient.
     *
     * @param stack candidate ingredient
     * @return category badge text
     */
    private Component ingredientBadge(final ItemStack stack)
    {
        final boolean vegetable = stack.is(ModTags.ITEMS.VEGETABLE_TAG);
        final boolean protein = stack.is(ModTags.ITEMS.PROTEIN_TAG);
        if (vegetable && protein) return Component.literal("V/P");
        if (protein) return Component.literal("P");
        if (vegetable) return Component.literal("V");
        return Component.literal("O");
    }

    /**
     * Builds the explanatory tooltip for an ingredient category badge.
     *
     * @param stack candidate ingredient
     * @return badge tooltip text
     */
    private Component ingredientBadgeTooltip(final ItemStack stack)
    {
        final boolean vegetable = stack.is(ModTags.ITEMS.VEGETABLE_TAG);
        final boolean protein = stack.is(ModTags.ITEMS.PROTEIN_TAG);
        if (vegetable && protein) return Component.translatable("mctradepost.stewmelier.ingredient.vegetable_protein");
        if (protein) return Component.translatable("mctradepost.stewmelier.ingredient.protein");
        if (vegetable) return Component.translatable("mctradepost.stewmelier.ingredient.vegetable");
        return Component.translatable("mctradepost.stewmelier.ingredient.other");
    }

    /** Updates tier availability, tooltips, and current pot progress. */
    private void updateTierControls()
    {
        for (StewTier tier : StewTier.values())
        {
            final String id = switch (tier)
            {
                case BASIC -> TIER_BASIC;
                case HEARTY -> TIER_HEARTY;
                case GOURMET -> TIER_GOURMET;
            };
            final Button button = findPaneOfTypeByID(id, Button.class);
            button.setColors(tier == moduleView.getDesiredTier() ? BUTTON_COLOR_SELECTED : BUTTON_COLOR_DEFAULT);
            button.enable();
            if (tier.getMinimumKitchenLevel() > moduleView.getKitchenLevel()) button.disable();
            final Component tooltip;
            if (tier.getMinimumKitchenLevel() > moduleView.getKitchenLevel())
            {
                tooltip = Component.translatable("mctradepost.stewmelier.tier.locked", tier.getMinimumKitchenLevel());
            }
            else if (tier.getRequiredProteinIngredients() > 0)
            {
                tooltip = Component.translatable("mctradepost.stewmelier.tier.tooltip.protein",
                    tier.getRequiredDistinctIngredients(), tier.getRequiredProteinIngredients());
            }
            else
            {
                tooltip = Component.translatable("mctradepost.stewmelier.tier.tooltip",
                    tier.getRequiredDistinctIngredients());
            }
            PaneBuilders.tooltipBuilder().hoverPane(button).build().setText(tooltip);
        }

        final int servings = moduleView.getStewServings();
        final Text actualTierLabel = findPaneOfTypeByID("actualTier", Text.class);
        actualTierLabel.setText(moduleView.isStewpotIdentified()
            ? !moduleView.isStewQualified()
                ? Component.translatable("mctradepost.stewmelier.broth")
                : Component.translatable(
                servings == 1 ? "mctradepost.stewmelier.actual.single" : "mctradepost.stewmelier.actual",
                Component.translatable("mctradepost.stewmelier.tier." + moduleView.getActualTier().name().toLowerCase(java.util.Locale.US)), servings)
            : Component.translatable("mctradepost.stewmelier.no_pot"));
        PaneBuilders.tooltipBuilder().hoverPane(actualTierLabel).build().setText(moduleView.isStewpotIdentified()
            ? Component.translatable("mctradepost.stewmelier.pot_location", moduleView.getStewpotLocation().toShortString())
            : Component.empty());
        final int requiredProteins = moduleView.getDesiredTier().getRequiredProteinIngredients();
        final Component proteinStatus = requiredProteins > 0
            ? Component.translatable("mctradepost.stewmelier.protein.progress",
                moduleView.getCreditedProteinIngredientCount(), requiredProteins)
            : Component.translatable(moduleView.getCreditedProteinIngredientCount() > 0
                ? "mctradepost.stewmelier.protein.present" : "mctradepost.stewmelier.protein.none");
        findPaneOfTypeByID("tierProgress", Text.class).setText(Component.translatable(
            "mctradepost.stewmelier.progress", moduleView.getCreditedIngredientCount(),
            moduleView.getDesiredTier().getRequiredDistinctIngredients(),
            proteinStatus));
        displayedDesiredTier = moduleView.getDesiredTier();
        displayedActualTier = moduleView.getActualTier();
        displayedIngredientCount = moduleView.getCreditedIngredientCount();
        displayedKitchenLevel = moduleView.getKitchenLevel();
        displayedStewServings = moduleView.getStewServings();
        displayedStewpotIdentified = moduleView.isStewpotIdentified();
        displayedStewpotLocation = moduleView.getStewpotLocation();
        displayedProteinCount = moduleView.getCreditedProteinIngredientCount();
        displayedStewQualified = moduleView.isStewQualified();
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
                final Component availabilityTooltip;
                if (moduleView.isWarehouseAvailable())
                {
                    final StewmelierIngredientModuleView.WarehouseAvailability availability =
                        moduleView.getWarehouseAvailability(index);
                    availabilityTooltip = Component.translatable("mctradepost.stewmelier.ingredient.availability",
                        availability.warehouseCount(), availability.availableForStew());
                }
                else
                {
                    availabilityTooltip = Component.translatable("mctradepost.stewmelier.ingredient.availability.no_warehouse");
                }
                PaneBuilders.tooltipBuilder().hoverPane(rowPane.findPaneOfTypeByID(INGREDIENT_NAME, Text.class))
                    .build().setText(availabilityTooltip);
                PaneBuilders.tooltipBuilder().hoverPane(rowPane.findPaneOfTypeByID(LABEL_QUANTITY, Text.class))
                    .build().setText(availabilityTooltip);
            }
        });
    }
}
