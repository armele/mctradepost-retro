package com.deathfrog.mctradepost.apiimp.initializer;

import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.jobs.MCTPModJobs;
import com.minecolonies.api.items.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;

/* Sets up crafting information for this mod's Minecolonies crafters.
 * ModTags.initCrafterRules() and its supporting constants are
 * not public, so this class reproduces some of that code.
 * 
 */
public class MCTPCraftingSetup {
    /**
     * Tag specifier for Products to Include
     */
    private static final String PRODUCT = "_product";

    /**
     * Tag specifier for Products to Exclude
     */
    private static final String PRODUCT_EXCLUDED = "_product_excluded";

    /**
     * Tag specifier for Ingredients to include
     */
    private static final String INGREDIENT = "_ingredient";

    /**
     * Tag specifier for Ingredients to exclude
     */
    private static final String INGREDIENT_EXCLUDED = "_ingredient_excluded";

    /**
     * Tag specifier for Ingredients to include
     */
    private static final String DO_INGREDIENT = "_do_ingredient";
    
    /**
     * Injects Minecolonies crafting rules for the Bartender (Marketplace).
     * This method is called by the mod's DeferredRegister callback.
     */
    public static void injectCraftingRules() 
    { 
        initCrafterRules(MCTPModJobs.BARTENDER_TAG);
    }

    /**
     * Initialize the four tags for a particular crafter
     * @param crafterName the string name of the crafter to initialize
     */
    private static void initCrafterRules(@NotNull final String crafterName)
    {
        final ResourceLocation products = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, crafterName.concat(PRODUCT));
        final ResourceLocation ingredients = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, crafterName.concat(INGREDIENT));
        final ResourceLocation productsExcluded = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, crafterName.concat(PRODUCT_EXCLUDED));
        final ResourceLocation ingredientsExcluded = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, crafterName.concat(INGREDIENT_EXCLUDED));
        final ResourceLocation doIngredients = ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, crafterName.concat(DO_INGREDIENT));

        ModTags.crafterProduct.put(crafterName, ItemTags.create(products));
        ModTags.crafterProductExclusions.put(crafterName, ItemTags.create(productsExcluded));
        ModTags.crafterIngredient.put(crafterName, ItemTags.create(ingredients));
        ModTags.crafterIngredientExclusions.put(crafterName, ItemTags.create(ingredientsExcluded));
        ModTags.crafterDoIngredient.put(crafterName, ItemTags.create(doIngredients));
    }
}
