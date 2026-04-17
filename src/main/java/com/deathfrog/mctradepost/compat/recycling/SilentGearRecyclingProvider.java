package com.deathfrog.mctradepost.compat.recycling;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.mojang.logging.LogUtils;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Optional recycler integration for Silent Gear salvaging recipes. This class
 * uses registry lookups and reflection only, allowing Trade Post to consume
 * Silent Gear salvage behavior when present without introducing a binary
 * dependency on Silent Gear itself.
 */
public final class SilentGearRecyclingProvider implements IOptionalRecyclingProvider
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "silentgear";

    /**
     * Attempts to resolve Silent Gear salvaging outputs for the supplied item
 * stack. Matching recipes are discovered from the runtime recipe registry,
 * and salvage results are obtained reflectively from the recipe itself. The
 * final outputs are then processed through a local reimplementation of Silent
 * Gear's salvager loss logic so damaged gear does not return pristine salvage.
     *
     * @param input the item stack being recycled
     * @param level the level containing the active recipe manager
     * @param workerSkill the recycling worker's skill level, unused by this provider
     * @return final outputs from Silent Gear salvaging, or {@code null} if no matching
     *         Silent Gear recipe is available
     */
    @Override
    @Nullable
    public RecyclingPlan tryResolve(final ItemStack input, final Level level, final int workerSkill)
    {
        if (input.isEmpty() || !ModList.get().isLoaded(MOD_ID))
        {
            return null;
        }

        final RecipeManager recipeManager = level.getRecipeManager();
        if (recipeManager == null)
        {
            return null;
        }

        ItemStack inputCopy = input.copyWithCount(1);

        if (inputCopy == null || inputCopy.isEmpty())
        {
            return null;
        }

        final SingleRecipeInput singleInput = new SingleRecipeInput(inputCopy);

        for (final RecipeHolder<?> holder : recipeManager.getRecipes())
        {
            final Recipe<?> recipe = holder.value();
            if (!isSilentGearSalvageRecipe(holder, recipe))
            {
                continue;
            }

            try
            {
                final Method matchesMethod = recipe.getClass().getMethod("matches", SingleRecipeInput.class, Level.class);
                if (!Boolean.TRUE.equals(matchesMethod.invoke(recipe, singleInput, level)))
                {
                    continue;
                }

                final Method getPossibleResults = recipe.getClass().getMethod("getPossibleResults", net.minecraft.world.Container.class);
                final SimpleContainer container = new SimpleContainer(input.copyWithCount(1));
                final Object rawResults = getPossibleResults.invoke(recipe, container);
                final List<ItemStack> outputs = applySalvageLoss(copyItemStacks(rawResults), input, level);
                if (!outputs.isEmpty())
                {
                    return new RecyclingPlan.FinalOutputs(outputs);
                }
            }
            catch (final ReflectiveOperationException | RuntimeException ex)
            {
                MCTradePostMod.LOGGER.warn("Failed to query Silent Gear salvage recipe {} for {}.", holder.id(), input, ex);
            }
        }

        return null;
    }

    /**
     * Determines whether the supplied recipe is one of Silent Gear's salvaging
     * recipes. Silent Gear also registers normal crafting recipes under its own
     * namespace, and those use a different recipe input type in 1.21.1. Restricting
     * this provider to the dedicated salvage package and recipe id prefix avoids
     * probing unrelated recipes with the wrong reflective signature.
     *
     * @param holder the recipe holder being inspected
     * @param recipe the recipe to inspect
     * @return {@code true} if this is a Silent Gear salvaging recipe
     */
    private static boolean isSilentGearSalvageRecipe(final RecipeHolder<?> holder, final Recipe<?> recipe)
    {
        final String className = recipe.getClass().getName();
        return className.startsWith("net.silentchaos512.gear.crafting.recipe.salvage.")
            || holder.id().getNamespace().equals(MOD_ID) && holder.id().getPath().startsWith("salvaging/");
    }

    /**
     * Copies reflected output objects into a fresh list of {@link ItemStack}s.
     * Non-item entries are ignored to keep the integration tolerant of runtime
     * changes in third-party implementations.
     *
     * @param rawResults the reflected result object returned by a salvage recipe
     * @return copied item stacks extracted from the reflected result
     */
    private static List<ItemStack> copyItemStacks(final Object rawResults)
    {
        final List<ItemStack> outputs = new ArrayList<>();
        if (!(rawResults instanceof final Iterable<?> iterable))
        {
            return outputs;
        }

        for (final Object entry : iterable)
        {
            if (entry instanceof final ItemStack stack && !stack.isEmpty())
            {
                outputs.add(stack.copy());
            }
            else if (entry != null)
            {
                LOGGER.debug("Ignoring non-ItemStack Silent Gear salvage result entry {}", entry.getClass().getName());
            }
        }

        return outputs;
    }

    /**
     * Applies a local reimplementation of Silent Gear's salvager loss logic to a
     * list of candidate salvage outputs. This mirrors the block entity behavior
     * closely enough to preserve durability-sensitive salvage while still avoiding
     * a binary dependency.
     *
     * @param rawOutputs the candidate outputs returned by the Silent Gear salvage recipe
     * @param input the original gear item being salvaged
     * @param level the active level used for random loss rolls
     * @return the surviving salvage outputs after loss has been applied
     */
    private static List<ItemStack> applySalvageLoss(final List<ItemStack> rawOutputs, final ItemStack input, final Level level)
    {
        final List<ItemStack> outputs = new ArrayList<>();
        final double baseLossRate = getConfiguredLossRate(input);

        for (final ItemStack rawOutput : rawOutputs)
        {
            if (rawOutput.isEmpty())
            {
                continue;
            }

            final ItemStack adjusted = rawOutput.copy();
            final int originalCount = adjusted.getCount();
            final double effectiveLossRate = resolvePartSpecificLossRate(rawOutput, input, baseLossRate);

            for (int i = 0; i < originalCount; i++)
            {
                if (level.random.nextDouble() < clampLossRate(effectiveLossRate))
                {
                    adjusted.shrink(1);
                }
            }

            if (!adjusted.isEmpty())
            {
                outputs.add(adjusted);
            }
        }

        return outputs;
    }

    /**
     * Calculates Silent Gear's configured base salvage loss rate for the supplied
     * input item. The logic matches the salvager block entity: undamageable items
     * use the configured minimum loss rate, while damageable items interpolate
     * between the minimum and maximum based on current damage.
     *
     * @param input the gear item being salvaged
     * @return the configured base salvage loss rate
     */
    private static double getConfiguredLossRate(final ItemStack input)
    {
        final double minLossRate = getSilentGearConfigDouble("net.silentchaos512.gear.Config$Common", "salvagerMinLossRate", 0.0d);
        final int maxDamage = input.getMaxDamage();
        if (maxDamage <= 0)
        {
            return clampLossRate(minLossRate);
        }

        final double maxLossRate = getSilentGearConfigDouble("net.silentchaos512.gear.Config$Common", "salvagerMaxLossRate", 0.5d);
        final double damageFactor = input.getDamageValue() / (double) maxDamage;
        return clampLossRate(minLossRate + (damageFactor * (maxLossRate - minLossRate)));
    }

    /**
     * Resolves the effective loss rate for an individual salvaged part. When the
     * output stack represents a valid Silent Gear part instance, Silent Gear's
     * own {@code GearPart.getSalvageLossRate} hook is invoked reflectively.
     * Otherwise the base loss rate is used.
     *
     * @param output the salvaged output stack being processed
     * @param input the original gear item being salvaged
     * @param baseLossRate the default loss rate derived from item damage
     * @return the effective loss rate to apply to this output stack
     */
    private static double resolvePartSpecificLossRate(final ItemStack output, final ItemStack input, final double baseLossRate)
    {
        try
        {
            final Class<?> partInstanceClass = Class.forName("net.silentchaos512.gear.gear.part.PartInstance");
            final Method fromMethod = partInstanceClass.getMethod("from", ItemStack.class);
            final Object partInstance = fromMethod.invoke(null, output);
            if (partInstance == null)
            {
                return clampLossRate(baseLossRate);
            }

            final Method isValidMethod = partInstanceClass.getMethod("isValid");
            if (!Boolean.TRUE.equals(isValidMethod.invoke(partInstance)))
            {
                return clampLossRate(baseLossRate);
            }

            final Method getPartMethod = partInstanceClass.getMethod("get");
            final Object gearPart = getPartMethod.invoke(partInstance);
            if (gearPart == null)
            {
                return clampLossRate(baseLossRate);
            }

            final Method getSalvageLossRate = gearPart.getClass()
                .getMethod("getSalvageLossRate", partInstanceClass, ItemStack.class, double.class);
            final Object resolvedLossRate = getSalvageLossRate.invoke(gearPart, partInstance, input, baseLossRate);
            if (resolvedLossRate instanceof final Number number)
            {
                return clampLossRate(number.doubleValue());
            }
        }
        catch (final ReflectiveOperationException | RuntimeException ex)
        {
            LOGGER.debug("Falling back to base Silent Gear salvage loss rate for {}.", output, ex);
        }

        return clampLossRate(baseLossRate);
    }

    /**
     * Reads a Silent Gear double config value reflectively.
     *
     * @param ownerClassName the fully qualified name of the class holding the config field
     * @param fieldName the static field name to read
     * @param fallback the value to return if reflection fails
     * @return the config value, or the fallback when it cannot be read
     */
    private static double getSilentGearConfigDouble(final String ownerClassName, final String fieldName, final double fallback)
    {
        try
        {
            final Class<?> ownerClass = Class.forName(ownerClassName);
            final Field field = ownerClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            final Object configValue = field.get(null);
            if (configValue == null)
            {
                return fallback;
            }

            final Method getMethod = configValue.getClass().getMethod("get");
            final Object value = getMethod.invoke(configValue);
            if (value instanceof final Number number)
            {
                return number.doubleValue();
            }
        }
        catch (final ReflectiveOperationException | RuntimeException ex)
        {
            LOGGER.debug("Unable to read Silent Gear config {}.{}.", ownerClassName, fieldName, ex);
        }

        return fallback;
    }

    /**
     * Clamps a salvage loss rate to the valid range expected by the loss-roll
     * logic.
     *
     * @param value the loss rate to clamp
     * @return the clamped loss rate
     */
    private static double clampLossRate(final double value)
    {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
