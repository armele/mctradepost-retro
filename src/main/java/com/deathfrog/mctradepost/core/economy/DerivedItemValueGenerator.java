package com.deathfrog.mctradepost.core.economy;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.ldtteam.domumornamentum.recipe.ModRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.*;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import static net.minecraft.core.registries.BuiltInRegistries.ITEM;

/**
 * Standalone generator: seeds + fixpoint propagation over known recipes.
 *
 * Important: unknown ingredients remain unknown (do NOT treat as 0).
 */
public final class DerivedItemValueGenerator
{
    private DerivedItemValueGenerator() { }

    public static final class Options
    {
        private boolean applyCookingPremium = false;
        private int maxIterations = 50;
        private double cookingPremiumPer1200Ticks = 0.10; // 10% per 60s (1200 ticks)
        private Set<String> namespaceExclusions = Set.of();

        public boolean applyCookingPremium() { return applyCookingPremium; }
        public int maxIterations() { return maxIterations; }
        public double cookingPremiumPer1200Ticks() { return cookingPremiumPer1200Ticks; }
        public Set<String> namespaceExclusions() { return namespaceExclusions; }

        public Options setApplyCookingPremium(final boolean v) { this.applyCookingPremium = v; return this; }
        public Options setMaxIterations(final int v) { this.maxIterations = Math.max(1, v); return this; }
        public Options setCookingPremiumPer1200Ticks(final double v) { this.cookingPremiumPer1200Ticks = Math.max(0, v); return this; }
        public Options setNamespaceExclusions(final Collection<String> namespaces)
        {
            this.namespaceExclusions = namespaces == null ? Set.of() : Set.copyOf(namespaces);
            return this;
        }
    }

    public record Report(
        Map<Item, Integer> values,
        int iterations,
        int recipesConsidered,
        int recipesApplied,
        int derivedCount,
        Set<String> unknownOutputs,
        Map<String, Integer> unknownIngredientCounts
    )
    {
        public String toLogString()
        {
            return "ItemValueGen report: iterations=" + iterations
                + ", recipesConsidered=" + recipesConsidered
                + ", recipesApplied=" + recipesApplied
                + ", derivedCount=" + derivedCount
                + ", emittedValues=" + values.size()
                + ", unknownOutputs=" + unknownOutputs.size()
                + ", unknownIngredients=" + unknownIngredientCounts.size();
        }
    }

    /**
     * Generate a map of item values by propagating seeds and known recipes via fixpoint iteration.
     * 
     * <p>This method takes a set of seed values and iteratively applies all known recipes to derive new values.
     * It will continue to apply recipes until no new values are discovered or the maximum iteration count is reached.
     * 
     * @param server the Minecraft server instance (required for registry access)
     * @param seedValuesRaw a map of item seeds with their corresponding respective values
     * @param options configuration options for the generation process
     * @return a report containing the generated item values, iteration count, and other statistics
     */
    public static Report generate(final MinecraftServer server,
                                  final Map<?, Integer> seedValuesRaw,
                                  final Options options)
    {
        return generate(server, Map.of(), seedValuesRaw, options);
    }

    /**
     * Propagates recipes from authoritative values and generator seeds while retaining their provenance.
     * Both input sets are protected from recipe replacement; generator seeds intentionally take precedence
     * over authoritative values. The returned value map contains only generator seeds and newly derived items,
     * making it safe to write as an overlay datapack.
     *
     * @param server Minecraft server supplying recipes and registries
     * @param authoritativeValuesRaw merged values supplied by ordinary datapacks
     * @param seedValuesRaw generator-specific seeds and explicit overrides
     * @param options generation options
     * @return generation report whose values are safe to emit
     */
    @SuppressWarnings("null")
    public static Report generate(final MinecraftServer server,
                                  final Map<?, Integer> authoritativeValuesRaw,
                                  final Map<?, Integer> seedValuesRaw,
                                  final Options options)
    {
        final RegistryAccess ra = server.registryAccess();
        final RecipeManager rm = server.getRecipeManager();

        if (rm == null || ra == null)
        {
            MCTradePostMod.LOGGER.error("ItemValueGen: No recipe manager or registry access available.");
            return new Report(new HashMap<>(), 0, 0, 0, 0, new HashSet<>(), new HashMap<>());
        }

        final Map<Item, Integer> unknownIngredientCounts = new HashMap<>();

        // Authoritative values are protected inputs but are not generator output.
        final Map<Item, Integer> authoritativeValues = typedValues(authoritativeValuesRaw, options);
        final Set<Item> authoritativeItems = authoritativeValues.keySet();

        // Seeds are explicit overrides and therefore load after authoritative values.
        final Map<Item, Integer> seedValues = typedValues(seedValuesRaw, options);
        final Set<Item> seedItems = seedValues.keySet();
        final Map<Item, Integer> values = combineInputs(authoritativeValues, seedValues);
        final int seedCount = seedItems.size();

        final Set<Item> protectedItems = new HashSet<>(authoritativeItems);
        protectedItems.addAll(seedItems);

        final Predicate<RecipeType<?>> allowedTypes = t ->
            t == RecipeType.CRAFTING
                || t == RecipeType.SMELTING
                || t == RecipeType.CAMPFIRE_COOKING
                || t == RecipeType.STONECUTTING
                || t == ModRecipeTypes.ARCHITECTS_CUTTER.get(); // MineColonies

        // Flatten allowed recipes once (1.21+ API)
        final List<RecipeHolder<?>> all = new ArrayList<>();

        for (final RecipeHolder<?> holder : rm.getRecipes())
        {
            final Recipe<?> recipe = holder.value();
            final ItemStack output = recipe.getResultItem(ra);

            if (allowedTypes.test(recipe.getType()) && !output.isEmpty() && !isNamespaceExcluded(output.getItem(), options))
            {
                all.add(holder);
            }
        }

        MCTradePostMod.LOGGER.info("ItemValueGen: seeds={}, allowedRecipes={}", seedCount, all.size());

        int iterations = 0;
        int recipesConsidered = 0;
        int recipesApplied = 0;
        int derivedCount = 0;

        // Fixpoint loop: keep applying recipes while new values are discovered/improved.
        boolean changed = true;
        while (changed && iterations < options.maxIterations())
        {
            changed = false;
            iterations++;

            for (final RecipeHolder<?> holder : all)
            {
                recipesConsidered++;
                final Recipe<?> recipe = holder.value();

                final ItemStack outStack = recipe.getResultItem(ra);
                if (outStack.isEmpty())
                {
                    continue;
                }

                final Item outItem = outStack.getItem();

                // If output already has a seed/override, you may or may not want to replace it.
                // Here we allow improvement by cheaper derivation, but do NOT override a seed if you want strict seeds.
                // If you prefer "seeds win", check a separate seedSet and skip if present.
                final Integer existing = values.get(outItem);

                final Integer cost = computeRecipeUnitCost(recipe, ra, values, options);
                if (cost == null)
                {
                    // record which ingredients are blocking this recipe
                    collectUnknownIngredients(recipe, values, unknownIngredientCounts);
                    continue;
                }
                
                // cost is per 1 output item already
                final int unitCost = Math.max(0, cost);

                if (!protectedItems.contains(outItem))
                {
                    // Keep minimum cost among multiple recipes, if not a seeded item
                    if (existing == null || unitCost < existing)
                    {
                        values.put(outItem, unitCost);
                        changed = true;
                        recipesApplied++;
                        if (existing == null)
                        {
                            derivedCount++;
                        }
                    }   
                }
            }
        }

        // Build "unknown outputs" set for recipes we considered whose output still has no value.
        final Set<String> unknown = new TreeSet<>();
        for (final RecipeHolder<?> holder : all)
        {
            final ItemStack out = holder.value().getResultItem(ra);
            if (out.isEmpty()) continue;

            final Item outItem = out.getItem();

            if (outItem == null) continue;

            if (!values.containsKey(outItem))
            {
                final ResourceLocation id = ITEM.getKey(outItem);
                unknown.add(id.toString());
            }
        }

        final Map<String, Integer> unknownCountsSorted = new LinkedHashMap<>();
        unknownIngredientCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(200) // keep it bounded
            .forEach(e -> 
            {
                Item eKey = e.getKey();
                
                if (eKey == null) return;

                unknownCountsSorted.put(ITEM.getKey(eKey).toString(), e.getValue());
            }
        );

        final Map<Item, Integer> emittedValues = selectEmittedValues(values, authoritativeItems, seedItems);
        return new Report(Collections.unmodifiableMap(emittedValues), iterations, recipesConsidered, recipesApplied, derivedCount, unknown, Collections.unmodifiableMap(unknownCountsSorted));
    }

    private static Map<Item, Integer> typedValues(final Map<?, Integer> source, final Options options)
    {
        final Map<Item, Integer> copied = new HashMap<>();
        for (final Map.Entry<?, ?> entry : source.entrySet())
        {
            if (entry.getKey() instanceof Item item && entry.getValue() instanceof Integer value
                && !isNamespaceExcluded(item, options))
            {
                copied.put(item, value);
            }
        }
        return copied;
    }

    /** Combines protected inputs with explicit generator seeds taking precedence. */
    static <T> Map<T, Integer> combineInputs(final Map<T, Integer> authoritative, final Map<T, Integer> seeds)
    {
        final Map<T, Integer> combined = new HashMap<>(authoritative);
        combined.putAll(seeds);
        return combined;
    }

    /** Selects generator-owned seeds and derivations while omitting untouched authoritative entries. */
    static <T> Map<T, Integer> selectEmittedValues(final Map<T, Integer> known,
                                                   final Set<T> authoritative,
                                                   final Set<T> seeds)
    {
        final Map<T, Integer> emitted = new HashMap<>();
        known.forEach((item, value) ->
        {
            if (seeds.contains(item) || !authoritative.contains(item)) emitted.put(item, value);
        });
        return emitted;
    }

    /**
     * Counts the number of times an unknown ingredient appears in a recipe, and
     * its candidate items. This is used to track the number of unknown ingredients
     * that are blocking the value propagation process.
     *
     * @param recipe the recipe to inspect
     * @param knownValues the known values of ingredients
     * @param unknownCounts the counts of unknown ingredients
     */
    @SuppressWarnings("null")
    private static void collectUnknownIngredients(final Recipe<?> recipe,
                                                final Map<Item, Integer> knownValues,
                                                final Map<Item, Integer> unknownCounts)
    {
        for (final Ingredient ing : recipe.getIngredients())
        {
            if (ing == null || ing.isEmpty())
            {
                continue;
            }

            // If this ingredient has ANY priced option, it's not blocking.
            if (hasAnyKnownOption(ing, knownValues))
            {
                continue;
            }

            // Otherwise, it's blocking: count all its candidate items.
            final ItemStack[] matches = ing.getItems();
            if (matches == null || matches.length == 0)
            {
                continue;
            }

            // Safety limit: some tags expand huge in modpacks.
            final int limit = Math.min(matches.length, 64);

            for (int i = 0; i < limit; i++)
            {
                final ItemStack s = matches[i];
                if (s == null || s.isEmpty()) continue;

                final Item it = s.getItem();
                unknownCounts.merge(it, 1, Integer::sum);
            }
        }
    }

    /**
     * Determines if any of the ingredient's options are known to have a value.
     * If any option has a value, it's not blocking.
     *
     * @param ing the ingredient to check
     * @param knownValues map of items to their known values
     * @return true if any of the ingredient's options are known to have a value, false otherwise
     */
    private static boolean hasAnyKnownOption(final Ingredient ing, final Map<Item, Integer> knownValues)
    {
        final ItemStack[] matches = ing.getItems();
        if (matches == null || matches.length == 0)
        {
            return false;
        }

        for (final ItemStack s : matches)
        {
            if (s == null || s.isEmpty()) continue;

            if (knownValues.containsKey(s.getItem()))
            {
                return true;
            }
        }
        return false;
    }


    /**
     * Returns unit cost per 1 output item, or null if unknown.
     */
    private static Integer computeRecipeUnitCost(final Recipe<?> recipe,
                                                final @Nonnull RegistryAccess ra,
                                                final Map<Item, Integer> knownValues,
                                                final Options options)
    {
        final ItemStack outStack = recipe.getResultItem(ra);
        if (outStack.isEmpty())
        {
            return null;
        }

        final int outCount = Math.max(1, outStack.getCount());

        // Sum ingredient costs (skip empty ingredients).
        long sum = 0L;

        for (final Ingredient ing : recipe.getIngredients())
        {
            if (ing == null || ing.isEmpty())
            {
                continue;
            }

            final Integer ingCost = minKnownIngredientCost(ing, knownValues);
            if (ingCost == null)
            {
                return null; // cannot evaluate this recipe yet
            }

            sum += ingCost;
            if (sum > Integer.MAX_VALUE)
            {
                sum = Integer.MAX_VALUE;
                break;
            }
        }

        // If a recipe has no ingredients (weird/special), skip.
        if (sum <= 0L && !recipe.getIngredients().isEmpty())
        {
            // still allowed to be 0 if all ingredient costs are 0, but that’s usually not desired.
            // leave as-is.
        }

        double multiplier = 1.0;

        // Optional premium for cooking-time based recipes
        if (options.applyCookingPremium() && recipe instanceof AbstractCookingRecipe cook)
        {
            final int t = Math.max(0, cook.getCookingTime());
            multiplier += (t / 1200.0) * options.cookingPremiumPer1200Ticks();
        }

        final double perItem = (sum / (double) outCount) * multiplier;

        // Round up so you don't get 0.1 style values; keep integer economy.
        final int rounded = (int) Math.ceil(perItem);

        // If inputs were nonzero, prevent rounding to 0.
        if (sum > 0 && rounded <= 0)
        {
            return 1;
        }

        return rounded;
    }

    private static Integer minKnownIngredientCost(final Ingredient ing, final Map<Item, Integer> known)
    {
        int best = Integer.MAX_VALUE;
        boolean any = false;

        // Ingredient#getItems returns matching stacks (already expanded across tags etc).
        final ItemStack[] matches = ing.getItems();
        if (matches == null || matches.length == 0)
        {
            return null;
        }

        for (final ItemStack stack : matches)
        {
            if (stack == null || stack.isEmpty()) continue;

            final Integer v = known.get(stack.getItem());
            if (v == null) continue;

            any = true;
            if (v < best)
            {
                best = v;
                if (best == 0)
                {
                    // can't beat free
                    break;
                }
            }
        }

        return any ? best : null;
    }

    private static boolean isNamespaceExcluded(final @Nonnull Item item, final Options options)
    {
        final ResourceLocation id = ITEM.getKey(item);
        return id != null && options.namespaceExclusions().contains(id.getNamespace());
    }
}
