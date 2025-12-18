package com.deathfrog.mctradepost.compat.jei;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.BlockMixedStone;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualDefinitionHelper;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import javax.annotation.Nonnull;

@mezz.jei.api.JeiPlugin
public class JEIMCTPPlugin implements IModPlugin
{
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "jei_plugin");
    public static final RecipeType<RitualDefinitionHelper> RITUAL_TYPE = new RecipeType<>(NullnessBridge.assumeNonnull(ID), RitualDefinitionHelper.class);

    public static IRecipeManager RECIPE_MANAGER = null;

    /**
     * Returns the unique identifier for the JEI plugin for MCTradePost.
     * 
     * @return The unique identifier for the JEI plugin for MCTradePost.
     */
    @Nonnull
    @Override
    public ResourceLocation getPluginUid()
    {
        return NullnessBridge.assumeNonnull(ID);
    }

    /**
     * Registers the Ritual category with JEI.
     * 
     * @param registration the registration for JEI categories
     */
    @Override
    public void registerCategories(@Nonnull IRecipeCategoryRegistration registration)
    {
        registration.addRecipeCategories(new RitualCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(@Nonnull IRecipeRegistration registration)
    {
        MCTradePostMod.LOGGER.info("Registering JEI recipes");
        List<RitualDefinitionHelper> allRituals = RitualManager.getAllRituals().values().stream().toList();

        if (allRituals == null || allRituals.isEmpty())
        {
            MCTradePostMod.LOGGER.info("No rituals found; skipping JEI registration");
            return;
        }

        registration.addRecipes(NullnessBridge.assumeNonnull(RITUAL_TYPE), allRituals);
    }

    /**
     * Called when the JEI runtime is available. In this method, you can register click handlers for recipes, and use the recipe
     * manager to access all registered recipes. The JEI runtime is available as soon as the game is fully initialized and the player
     * has logged in. This means that the mod's content has been fully registered and is available for use.
     *
     * @param jeiRuntime The JEI runtime object.
     */
    @Override
    public void onRuntimeAvailable(@Nonnull IJeiRuntime jeiRuntime)
    {
        RECIPE_MANAGER = jeiRuntime.getRecipeManager();
    }

    /**
     * Reloads the list of registered rituals in JEI.
     * This can be used to refresh the list of rituals after a change to the data files.
     * If the JEI runtime is not yet available, this method does nothing.
     */
    public static void refreshRitualRecipes()
    {
        if (RECIPE_MANAGER == null)
        {
            MCTradePostMod.LOGGER.warn("JEI runtime not ready; cannot refresh rituals");
            return;
        }

        List<RitualDefinitionHelper> allRituals = RitualManager.getAllRituals().values().stream().toList();

        if (allRituals == null || allRituals.isEmpty())
        {
            MCTradePostMod.LOGGER.info("No rituals found; skipping JEI registration");
            return;
        }

        RECIPE_MANAGER.addRecipes(NullnessBridge.assumeNonnull(RITUAL_TYPE), allRituals);

        MCTradePostMod.LOGGER.info("JEI ritual list reloaded");
    }

    /**
     * Registers the mixed stone as a catalyst for all rituals.
     */
    @Override
    public void registerRecipeCatalysts(@Nonnull IRecipeCatalystRegistration reg)
    {
        BlockMixedStone mixedStone = MCTradePostMod.MIXED_STONE.get();
        
        if (mixedStone == null)
        {
            MCTradePostMod.LOGGER.warn("Mixed stone block not found; skipping catalyst registration");
            return;
        }

        reg.addRecipeCatalyst(new ItemStack(mixedStone), RITUAL_TYPE);
    }
}
