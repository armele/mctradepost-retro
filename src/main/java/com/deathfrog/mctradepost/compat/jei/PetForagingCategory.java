package com.deathfrog.mctradepost.compat.jei;

import java.util.List;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.entity.pets.scavenge.PetForagingJeiEntry;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * JEI category that displays possible pet foraging outcomes.
 * <p>
 * Recipes shown here are not real Minecraft recipes. They are server-derived display entries that describe which pet role and work
 * location can produce the listed outputs from a tagged source block.
 * </p>
 */
public class PetForagingCategory implements IRecipeCategory<PetForagingJeiEntry>
{
    public static final String JEI_CATEGORY_TITLE = "jei.mctradepost.pet_foraging";
    public static final int WIDTH = 170;
    public static final int HEIGHT = 64;
    private static final int MAX_OUTPUT_SLOTS = 6;

    private final IDrawable icon;

    /**
     * Creates the category using the pet hut as its JEI tab icon.
     *
     * @param guiHelper JEI GUI helper
     */
    @SuppressWarnings("null")
    public PetForagingCategory(final IGuiHelper guiHelper)
    {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MCTradePostMod.blockHutPetShop.get()));
    }

    @Override
    public RecipeType<PetForagingJeiEntry> getRecipeType()
    {
        return JEIMCTPPlugin.PET_FORAGING_TYPE;
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
    public void setRecipe(@Nonnull final IRecipeLayoutBuilder builder, @Nonnull final PetForagingJeiEntry recipe, @Nonnull final IFocusGroup focuses)
    {
        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 18)
            .setSlotName("work_location")
            .addItemStack(blockStack(recipe.workLocationBlock()));

        builder.addSlot(RecipeIngredientRole.INPUT, 31, 18)
            .setSlotName("source")
            .addItemStack(blockStack(recipe.sourceBlock()));

        final List<ItemStack> outputs = recipe.outputs();
        final int visibleOutputs = Math.min(outputs.size(), MAX_OUTPUT_SLOTS);

        for (int i = 0; i < visibleOutputs; i++)
        {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 78 + (i % 3) * 22, 8 + (i / 3) * 22)
                .setSlotName("output_" + i)
                .addItemStack(outputs.get(i));
        }
    }

    /**
     * Adds the recipe arrow and short role/context text for a display entry.
     */
    @SuppressWarnings("null")
    @Override
    public void createRecipeExtras(@Nonnull final IRecipeExtrasBuilder builder,
        @Nonnull final PetForagingJeiEntry recipe,
        @Nonnull final IFocusGroup focuses)
    {
        builder.addRecipeArrow().setPosition(55, 18);

        builder.addText(Component.translatable(roleKey(recipe)), 70, 10)
            .setPosition(4, 0)
            .setColor(ChatFormatting.DARK_GRAY.getColor());

        builder.addText(Component.translatable(recipe.noteKey()), 164, 14)
            .setPosition(4, 50)
            .setColor(ChatFormatting.GRAY.getColor());
    }

    /**
     * Resolves the translation key for the displayed pet role.
     */
    private static String roleKey(final PetForagingJeiEntry recipe)
    {
        return "jei.mctradepost.pet_foraging.role." + recipe.role().name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Creates an item stack for a block registry id.
     */
    private static ItemStack blockStack(final ResourceLocation blockId)
    {
        final Block block = BuiltInRegistries.BLOCK.get(blockId);

        return new ItemStack(block);
    }
}
