package com.deathfrog.mctradepost.compat.jei;

import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.recipe.UniqueTagShapelessRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

public final class UniqueTagShapelessCraftingExtension
    implements ICraftingCategoryExtension<UniqueTagShapelessRecipe>
{
    public static final UniqueTagShapelessCraftingExtension INSTANCE = new UniqueTagShapelessCraftingExtension();

    private UniqueTagShapelessCraftingExtension() {}

    @Override
    public void setRecipe(
        @Nonnull RecipeHolder<UniqueTagShapelessRecipe> recipeHolder,
        @Nonnull IRecipeLayoutBuilder builder,
        @Nonnull ICraftingGridHelper craftingGridHelper,
        @Nonnull IFocusGroup focuses
    )
    {
        final UniqueTagShapelessRecipe recipe = recipeHolder.value();

        // JEI wants List<List<ItemStack>> for the crafting grid helper.
        final NonNullList<Ingredient> ingredients = recipe.getIngredients();
        final List<List<ItemStack>> inputs = new ArrayList<>(ingredients.size());

        for (Ingredient ing : ingredients)
        {
            // Ingredient#getItems() gives the concrete display stacks for JEI cycling
            final ItemStack[] stacks = ing.getItems();

            // If something is empty (shouldn't be), give it an empty list rather than crash.
            if (stacks == null || stacks.length == 0)
                inputs.add(List.of(ItemStack.EMPTY));
            else
                inputs.add(Arrays.stream(stacks).map(ItemStack::copy).toList());
        }

        // Populate a 3x3 grid (shapeless) with those inputs
        craftingGridHelper.createAndSetInputs(builder, NullnessBridge.assumeNonnull(VanillaTypes.ITEM_STACK), inputs, 3, 3);

        // Output slot (coords are standard-ish for crafting category; JEI will place it correctly)
        ItemStack result = recipe.getResultStack().copy();
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level != null)
        {
            RegistryAccess regAccess = mc.level.registryAccess();

            if (regAccess == null) return;

            // prefer registry-aware result if available
            result = recipe.getResultItem(regAccess).copy();
        }

        if (result == null || result.isEmpty())
        {
            return;
        }

        // Typical crafting output position used by JEI's vanilla crafting category
        final IRecipeSlotBuilder out = builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 18).addItemStack(result);

        Component tip = Component.literal("Requires " + recipe.getCount() + " unique items from the tag");

        if (tip != null)
        {
            // hint about the uniqueness rule
            out.addRichTooltipCallback((view, tooltip) ->
                tooltip.add(tip)
            );
        }
    }
}