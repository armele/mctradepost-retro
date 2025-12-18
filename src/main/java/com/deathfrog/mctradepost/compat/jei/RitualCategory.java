package com.deathfrog.mctradepost.compat.jei;

import java.util.List;
import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import com.deathfrog.mctradepost.item.CoinItem;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class RitualCategory implements IRecipeCategory<RitualDefinitionHelper>
{
    public static final String JEI_RITUALS = "rituals";
    public static final String JEI_CATEGORY_TITLE = "jei.mctradepost.rituals";
    public static final int WIDTH = 170;
    public static final int HEIGHT = 50;
    public static final int TEXT_XPOS = 55;
    public static final int RITUAL_ICON_SIZE = 32;
    public static final int WISHING_WELL_SIZE = 50;
    public static final int WELL_TIP_HEIGHT = 30;
    public static final int WELL_TIP_WIDTH = 42;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, JEI_RITUALS);

    private final IGuiHelper guiHelper;
    private final IDrawable icon;
    @SuppressWarnings("unused")
    private IDrawableStatic background;

    public RitualCategory(IGuiHelper guiHelper)
    {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);

        CoinItem coinItem = MCTradePostMod.MCTP_COIN_ITEM.get();

        if (coinItem != null)
        {
            this.icon = guiHelper.createDrawableItemStack(new ItemStack(coinItem));
        }
        else
        {
            this.icon = guiHelper.createDrawableItemStack(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        }
    }

    @Override
    public RecipeType<RitualDefinitionHelper> getRecipeType()
    {
        return JEIMCTPPlugin.RITUAL_TYPE;
    }

    @Override
    public Component getTitle()
    {
        return Component.translatable(JEI_CATEGORY_TITLE);
    }

    @Override
    public IDrawable getIcon()
    {
        return icon;
    }

    /**
     * The width of the category in JEI.
     *
     * @return the width in pixels
     */
    @Override
    public int getWidth()
    {
        return WIDTH;
    }

    /**
     * The height of the category in JEI.
     *
     * @return the height in pixels
     */
    @Override
    public int getHeight()
    {
        return HEIGHT;
    }

    /**
     * Lays out the recipe for JEI.
     *
     * @param builder the JEI builder for the category
     * @param recipe  the recipe to lay out
     * @param focuses not used
     */
    @SuppressWarnings("null")
    @Override
    public void setRecipe(@Nonnull IRecipeLayoutBuilder builder, @Nonnull RitualDefinitionHelper recipe, @Nonnull IFocusGroup focuses)
    {
        ItemStack companion = new ItemStack(BuiltInRegistries.ITEM.get(recipe.companionItem()), recipe.companionItemCount());
        Item coinItem = recipe.getCoinAsItem();
        Item targetItem = recipe.getTargetAsItem();

        if (coinItem == null) 
        {
            MCTradePostMod.LOGGER.warn("Invalid recipe identified in ritual with coin type {}, target {} and companion item: {}", recipe.coinType(), recipe.target(), recipe.companionItem());
            return;
        }

        ItemStack coin = new ItemStack(coinItem, recipe.requiredCoins());

        ItemStack result = null;

        if (recipe.effect().equals(RitualManager.RITUAL_EFFECT_TRANSFORM) && targetItem != null)
        {
            result = new ItemStack(targetItem, recipe.companionItemCount());
        }

        MCTradePostMod.LOGGER.info("Setting up ritual in the JEI for companion item: {}", recipe.companionItem());

        IRecipeSlotBuilder tipSlot = builder.addSlot(RecipeIngredientRole.CATALYST, 4, 20)
            .setBackground(guiHelper.createBlankDrawable(WELL_TIP_WIDTH, WELL_TIP_HEIGHT), -1, -1)
            .addItemStack(new ItemStack(MCTradePostMod.MIXED_STONE.get()));

        tipSlot.setCustomRenderer(VanillaTypes.ITEM_STACK, new IIngredientRenderer<ItemStack>()
        {
            @Override
            public int getWidth()
            {
                return WELL_TIP_WIDTH;
            }

            @Override
            public int getHeight()
            {
                return WELL_TIP_HEIGHT;
            }

            @Override
            public void render(@Nonnull GuiGraphics guiGraphics, @Nonnull ItemStack ingredient)
            {
                // No-op (invisible)
            }

            @Override
            public List<Component> getTooltip(@Nonnull ItemStack ingredient, @Nonnull TooltipFlag tooltipFlag)
            {
                return List.of(Component.translatable("jei.mctradepost.tooltip.wishingwell").withStyle(ChatFormatting.GRAY));
            }
        });

        builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).setSlotName("coin").addItemStack(coin);

        builder.addSlot(RecipeIngredientRole.INPUT, 34, 0).setSlotName("companion").addItemStack(companion);

        if (result != null)
        {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 34, 34).setSlotName("result").addItemStack(result);
        }
    }

    /**
     * Adds extra information to the recipe in JEI.
     *
     * @param builder the JEI builder for the category
     * @param recipe  the recipe to add information for
     * @param focuses not used
     */
    @SuppressWarnings("null")
    @Override
    public void createRecipeExtras(@Nonnull final IRecipeExtrasBuilder builder,
        @Nonnull final RitualDefinitionHelper recipe,
        @Nonnull final IFocusGroup focuses)
    {
        String effectinfo = recipe.describe();

        builder.addText(Component.literal(effectinfo), getWidth() - TEXT_XPOS, HEIGHT)
            .setPosition(TEXT_XPOS, 0)
            .setColor(ChatFormatting.BLACK.getColor());

        // Adds the (shitty) picture of a wishing well.
        ResourceLocation wishingwellIllustration =
            ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/rituals/wishingwell.png");
        IDrawableBuilder wwPic =
            guiHelper.drawableBuilder(wishingwellIllustration, 0, WISHING_WELL_SIZE, WISHING_WELL_SIZE, WISHING_WELL_SIZE);
        wwPic.setTextureSize(WISHING_WELL_SIZE, WISHING_WELL_SIZE);
        builder.addDrawable(wwPic.build(), 0, 0);
    }
}
