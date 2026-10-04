package com.deathfrog.mctradepost.compat.jei;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.fluids.MCTPFluids;
import com.deathfrog.mctradepost.recipe.GasifierRecipe;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Displays authoritative Gasifier recipes and their Lifting Gas yields in JEI. */
public class GasifierCategory implements IRecipeCategory<GasifierRecipe>
{
    public static final int WIDTH = 170;
    public static final int HEIGHT = 44;

    private final IDrawable icon;

    /**
     * Creates the category using the Gasifier block as its tab icon.
     *
     * @param guiHelper JEI GUI helper
     */
    @SuppressWarnings("null")
    public GasifierCategory(IGuiHelper guiHelper)
    {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MCTradePostMod.GASIFIER.get()));
    }

    @Override
    public RecipeType<GasifierRecipe> getRecipeType()
    {
        return JEIMCTPPlugin.GASIFYING_TYPE;
    }

    @Override
    public Component getTitle()
    {
        return Component.translatable("jei.mctradepost.gasifying");
    }

    @Override
    public IDrawable getIcon()
    {
        return icon;
    }

    @Override
    public int getWidth()
    {
        return WIDTH;
    }

    @Override
    public int getHeight()
    {
        return HEIGHT;
    }

    @SuppressWarnings("null")
    @Override
    public void setRecipe(@Nonnull IRecipeLayoutBuilder builder, @Nonnull GasifierRecipe recipe, @Nonnull IFocusGroup focuses)
    {
        builder.addSlot(RecipeIngredientRole.INPUT, 12, 10)
            .setStandardSlotBackground()
            .addIngredients(recipe.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 140, 10)
            .setOutputSlotBackground()
            .setFluidRenderer(recipe.gasYield(), false, 16, 16)
            .addFluidStack(MCTPFluids.LIFTING_GAS.get(), recipe.gasYield());
    }

    @SuppressWarnings("null")
    @Override
    public void createRecipeExtras(@Nonnull IRecipeExtrasBuilder builder, @Nonnull GasifierRecipe recipe,
        @Nonnull IFocusGroup focuses)
    {
        builder.addAnimatedRecipeArrow(recipe.burnTime()).setPosition(73, 10);
        builder.addText(Component.translatable("jei.mctradepost.gasifying.output", recipe.gasYield()), WIDTH, 10)
            .setPosition(0, 33)
            .setColor(ChatFormatting.DARK_GRAY.getColor());
    }
}
