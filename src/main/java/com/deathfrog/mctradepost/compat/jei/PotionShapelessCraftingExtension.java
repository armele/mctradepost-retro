package com.deathfrog.mctradepost.compat.jei;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.recipe.PotionShapelessRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import javax.annotation.Nonnull;

public final class PotionShapelessCraftingExtension implements ICraftingCategoryExtension<PotionShapelessRecipe>
{
    public static final PotionShapelessCraftingExtension INSTANCE = new PotionShapelessCraftingExtension();

    private PotionShapelessCraftingExtension() {}

    @SuppressWarnings("null")
    @Override
    public void setRecipe(
        @Nonnull RecipeHolder<PotionShapelessRecipe> recipeHolder,
        @Nonnull IRecipeLayoutBuilder builder,
        @Nonnull ICraftingGridHelper craftingGridHelper,
        @Nonnull IFocusGroup focuses
    )
    {
        final PotionShapelessRecipe recipe = recipeHolder.value();

        craftingGridHelper.createAndSetInputs(
            builder,
            NullnessBridge.assumeNonnull(VanillaTypes.ITEM_STACK),
            recipe.getDisplayInputs(),
            3,
            3
        );

        ItemStack result = recipe.getResultStack().copy();
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null)
        {
            final RegistryAccess registryAccess = mc.level.registryAccess();
            if (registryAccess != null)
            {
                result = recipe.getResultItem(registryAccess).copy();
            }
        }

        if (result.isEmpty())
        {
            return;
        }

        final IRecipeSlotBuilder out = builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 18);
        out.addItemStack(result);
    }
}
