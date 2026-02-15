package com.deathfrog.mctradepost.apiimp.initializer;

import javax.annotation.Nonnull;

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
public class MCTPCraftingSetup 
{

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
     * Injects Minecolonies crafting rules for the Trade Post crafters.
     * This method is called by the mod's DeferredRegister callback.
     */
    public static void injectCraftingRules() 
    { 
        initCrafterRules(MCTradePostMod.MODID, MCTPModJobs.BARTENDER_TAG);
        initCrafterRules(MCTradePostMod.MODID, MCTPModJobs.SHOPKEEPER_TAG);
        initCrafterRules(MCTradePostMod.MODID, MCTPModJobs.DAIRYWORKER_TAG);
    }

    /**
     * Initialize the four tags for a particular crafter
     * @param crafterName the string name of the crafter to initialize
     */
    public static void initCrafterRules(@Nonnull String modID, @Nonnull final String crafterName)
    {
        final ResourceLocation products = ResourceLocation.fromNamespaceAndPath(modID, crafterName.concat(PRODUCT));
        final ResourceLocation ingredients = ResourceLocation.fromNamespaceAndPath(modID, crafterName.concat(INGREDIENT));
        final ResourceLocation productsExcluded = ResourceLocation.fromNamespaceAndPath(modID, crafterName.concat(PRODUCT_EXCLUDED));
        final ResourceLocation ingredientsExcluded = ResourceLocation.fromNamespaceAndPath(modID, crafterName.concat(INGREDIENT_EXCLUDED));
        final ResourceLocation doIngredients = ResourceLocation.fromNamespaceAndPath(modID, crafterName.concat(DO_INGREDIENT));

        ModTags.crafterProduct.put(crafterName, ItemTags.create(products));
        ModTags.crafterProductExclusions.put(crafterName, ItemTags.create(productsExcluded));
        ModTags.crafterIngredient.put(crafterName, ItemTags.create(ingredients));
        ModTags.crafterIngredientExclusions.put(crafterName, ItemTags.create(ingredientsExcluded));
        ModTags.crafterDoIngredient.put(crafterName, ItemTags.create(doIngredients));
    }
}
