package com.deathfrog.mctradepost.compat.jei;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
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

public class RitualCategory implements IRecipeCategory<RitualDefinitionHelper> {
    public static final String JEI_RITUALS = "rituals";
    public static final String JEI_CATEGORY_TITLE = "jei.mctradepost.rituals";
    public static final int WIDTH = 150;
    public static final int HEIGHT = 50;
    public static final int RITUAL_ICON_SIZE = 32;
    public static final int WISHING_WELL_SIZE = 50;

    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, JEI_RITUALS);

    private final IGuiHelper guiHelper;
    private final IDrawable icon;
    @SuppressWarnings("unused")
    private IDrawableStatic background;

    public RitualCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get()));
    }

    @Override
    public RecipeType<RitualDefinitionHelper> getRecipeType() {
        return JEIMCTPPlugin.RITUAL_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(JEI_CATEGORY_TITLE);
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @NotNull
    @Override
    public IDrawable getBackground()
    {
        return this.background;
    }
    
    /**
     * Lays out the recipe for JEI.
     *
     * @param builder the JEI builder for the category
     * @param recipe the recipe to lay out
     * @param focuses not used
     */
    @Override
    public void setRecipe(@Nonnull IRecipeLayoutBuilder builder, @Nonnull RitualDefinitionHelper recipe, @Nonnull IFocusGroup focuses) {
        ItemStack companion = new ItemStack(BuiltInRegistries.ITEM.get(recipe.companionItem()));
        ItemStack coin = new ItemStack(MCTradePostMod.MCTP_COIN_ITEM.get(), recipe.requiredCoins());

        // IDrawable badRitualPic = guiHelper.createDrawable(ResourceLocation.parse(recipe.getRitualTexture()), 0, RITUAL_ICON_SIZE, RITUAL_ICON_SIZE, RITUAL_ICON_SIZE);

        MCTradePostMod.LOGGER.info("Setting up ritual in the JEI for companion item: {}", recipe.companionItem());

        builder.addSlot(RecipeIngredientRole.INPUT, 0, 0)
                .setSlotName("coin")
                .addItemStack(coin);

        builder.addSlot(RecipeIngredientRole.CATALYST, 34, 0)
                .setSlotName("companion")
                .addItemStack(companion);

    }

    /**
     * Adds extra information to the recipe in JEI.
     *
     * @param builder the JEI builder for the category
     * @param recipe the recipe to add information for
     * @param focuses not used
     */
    @SuppressWarnings("null")
    @Override
    public void createRecipeExtras(@Nonnull final IRecipeExtrasBuilder builder,
                                   @Nonnull final RitualDefinitionHelper recipe,
                                   @Nonnull final IFocusGroup focuses)
    {
        String effectinfo = recipe.describe();


        builder.addText(Component.literal(effectinfo), getWidth() - WISHING_WELL_SIZE - 2, HEIGHT)
                .setPosition(52, 0)
                .setColor(ChatFormatting.BLACK.getColor());

        // Adds the (shitty) picture of a wishing well.
        ResourceLocation wishingwellIllustration = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "textures/rituals/wishingwell.png");
            IDrawableBuilder wwPic = guiHelper.drawableBuilder(wishingwellIllustration, 0, WISHING_WELL_SIZE, WISHING_WELL_SIZE, WISHING_WELL_SIZE);
            wwPic.setTextureSize(WISHING_WELL_SIZE, WISHING_WELL_SIZE);
            builder.addDrawable(wwPic.build(), 0, 0);

        /* Commenting out the ritual effect pictures for now.  Crowds the UI and the words are more informative.
        ResourceLocation ritualOutcomePicture;
        
        // Adds the ritual effect icon, if present.
        try {
            ritualOutcomePicture = ResourceLocation.parse(recipe.getRitualTexture());
            IDrawableBuilder ritualPic = guiHelper.drawableBuilder(ritualOutcomePicture, 0, RITUAL_ICON_SIZE, RITUAL_ICON_SIZE, RITUAL_ICON_SIZE);
            ritualPic.setTextureSize(RITUAL_ICON_SIZE, RITUAL_ICON_SIZE);
            builder.addDrawable(ritualPic.build(), WIDTH - RITUAL_ICON_SIZE, 0);
        } catch (IllegalArgumentException e) {
            MCTradePostMod.LOGGER.warn("Unknown ritual texture {} identified in ritual with companion item: {}", recipe.getRitualTexture(), recipe.companionItem());
        }
        */
    }
}
