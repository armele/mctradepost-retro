package com.deathfrog.mctradepost.compat.recycling;

import java.util.List;

import com.minecolonies.api.crafting.ItemStorage;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.item.ItemStack;

/**
 * Describes a resolved recycling strategy for a specific input item.
 * Implementations may either provide final outputs directly or provide
 * ingredient-like outputs that should still be processed by Trade Post's normal
 * recycling efficiency logic.
 */
public sealed interface RecyclingPlan permits RecyclingPlan.FinalOutputs, RecyclingPlan.IngredientOutputs
{
    /**
     * A recycling plan that already contains the final output items to emit.
     * These outputs should be used as-is, without additional Trade Post scaling.
     *
     * @param outputs the final item stacks to emit
     */
    record FinalOutputs(List<ItemStack> outputs) implements RecyclingPlan
    {
    }

    /**
     * A recycling plan expressed as ingredient counts and a reference result
     * stack, allowing Trade Post's existing efficiency and damage scaling logic
     * to be applied.
     *
     * @param outputs the ingredient-like outputs and their counts
     * @param referenceResult the reference crafted result used to normalize output counts
     */
    record IngredientOutputs(Object2IntOpenHashMap<ItemStorage> outputs, ItemStack referenceResult) implements RecyclingPlan
    {
    }
}
