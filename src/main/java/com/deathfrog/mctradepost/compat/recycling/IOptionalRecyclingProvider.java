package com.deathfrog.mctradepost.compat.recycling;

import javax.annotation.Nullable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Optional integration point for mods that expose recycling or salvage logic at
 * runtime. Implementations should return {@code null} when they do not handle
 * the supplied item stack.
 */
public interface IOptionalRecyclingProvider
{
    /**
     * Attempts to resolve a recycling plan for the supplied input stack.
     *
     * @param input the item stack being recycled
     * @param level the level containing the recipe manager and registries
     * @param workerSkill the recycling worker's skill level, or {@code -1} for flawless checks
     * @return a recycling plan if this provider can handle the input, or {@code null} otherwise
     */
    @Nullable
    RecyclingPlan tryResolve(ItemStack input, Level level, int workerSkill);
}
