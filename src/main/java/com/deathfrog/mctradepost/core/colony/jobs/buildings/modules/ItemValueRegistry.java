package com.deathfrog.mctradepost.core.colony.jobs.buildings.modules;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ItemValueRegistry
{
    private static final Map<Item, Integer> itemValues = new ConcurrentHashMap<>();
    private static final int[] FIBONACCI = {1, 1, 2, 3, 5, 8, 13, 21, 34, 55};

    public static void generateValues(RegisterEvent event)
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Level level = server.overworld();
        RecipeManager recipeManager = level.getRecipeManager();

        for (Item item : event.getRegistry(Registries.ITEM))
        {
            if (itemValues.containsKey(item)) continue;

            int value = 0;
            ItemStack stack = new ItemStack(item);

            // Check if item is smeltable or craftable
            List<RecipeHolder<SmeltingRecipe>> smelt = recipeManager.getAllRecipesFor(RecipeType.SMELTING);
            Optional<RecipeHolder<CraftingRecipe>> craft = recipeManager.getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(r -> r.value().getResultItem(level.registryAccess()).getItem().equals(item))
                .findFirst();

            if (smelt.size() > 0)
            {
                for (int i = 0; i < smelt.size(); i++)
                {
                    int tempValue = estimateRecipeValue(smelt.get(i).value(), recipeManager, level) + 1; // add 1 for smelting time
                    if (tempValue < value)
                    {
                        value = tempValue;
                    }
                }

            }
            else if (craft.isPresent())
            {
                value = estimateRecipeValue(craft.get().value(), recipeManager, level);
            }
            else
            {
                // Default or worldgen-based fallback value
                value = 1; // TODO: Enhance with loot table, mob drops, trades, etc.
            }

            itemValues.put(item, value);
        }
    }

    private static int estimateRecipeValue(Recipe<?> recipe, RecipeManager recipeManager, Level level)
    {
        int total = 0;
        for (Ingredient ingredient : recipe.getIngredients())
        {
            if (ingredient.isEmpty()) continue;
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0)
            {
                int min = Arrays.stream(stacks)
                                .map(stack -> getValue(stack.getItem()))
                                .min(Integer::compare)
                                .orElse(1);
                total += min;
            }
        }

        int resultCount = recipe.getResultItem(level.registryAccess()).getCount();
        return resultCount == 0 ? total : total / resultCount;
    }

    public static int getValue(Item item)
    {
        return itemValues.getOrDefault(item, 1);
    }

    public static String formatValue(int value)
    {
        return NumberFormat.getIntegerInstance().format(value);
    }

    public static void clearCache()
    {
        itemValues.clear();
    }
} 
