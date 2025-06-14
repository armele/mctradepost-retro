package com.deathfrog.mctradepost.compat.jei;

// File: JEIMCTPPlugin.java

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import javax.annotation.Nonnull;

// TODO: Make JEI optional.  Currently reports as required from CurseForge

@mezz.jei.api.JeiPlugin
public class JEIMCTPPlugin implements IModPlugin
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "jei_plugin");
    public static final RecipeType<RitualDefinitionHelper> RITUAL_TYPE = new RecipeType<>(ID, RitualDefinitionHelper.class);

    @Nonnull
    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(@Nonnull IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new RitualCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@Nonnull IRecipeRegistration registration) {
        List<RitualDefinitionHelper> allRituals = RitualManager.getAllRituals().values().stream().toList();
        registration.addRecipes(RITUAL_TYPE, allRituals);
    }

    /**
     * Called when the JEI runtime is available.
     *
     * In this method, you can register click handlers for recipes, and
     * use the recipe manager to access all registered recipes.
     *
     * The JEI runtime is available as soon as the game is fully initialized
     * and the player has logged in. This means that the mod's content has been
     * fully registered and is available for use.
     *
     * @param jeiRuntime The JEI runtime object.
     */
    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime jeiRuntime) {
        IRecipeManager manager = jeiRuntime.getRecipeManager();
        // Optional: use this to add click actions
    }
}
