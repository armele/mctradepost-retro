package com.deathfrog.mctradepost.core.colony.buildings.modules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.crafting.ItemStorage;

public class ItemValueRegistry
{
    private static final Map<Item, Integer> itemValues = new HashMap<Item, Integer>();

    // TODO: Make configurable.
    // Temporary hack to bring down server start speeds.
    private static final Set<String> allowedMods = new HashSet<>(Arrays.asList("minecraft", "minecolonies", "mctradepost"));

    private static boolean configured = false;
    private static boolean startTracking = false;

    /**
     * Configures the ItemValueRegistry by loading initial values from a JSON file
     * and estimating values for all registered items using available recipes.
     * If the registry has already been configured, the method logs a message and returns.
     * The method logs an error if attempted before a server is started.
     * Each item's value is determined by appraising the recipes from the recipe manager.
     * 
     * Allowing it to process all recipes on a large server (like ATM10) makes it take 
     * so long that server start-up fails.
     */

    public static void generateValues()
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (configured) {
            MCTradePostMod.LOGGER.info("ItemValueRegistry has already been configured.");
            return;
        }
        if (server == null) {
            MCTradePostMod.LOGGER.error("Attempting to configure the ItemValueRegistry before a server has been started.");
            return;
        } else {
            MCTradePostMod.LOGGER.info("Beginning to configure the ItemValueRegistry.");
        }

        loadInitialValuesFromJson();

        Level level = server.overworld();
        RecipeManager recipeManager = level.getRecipeManager();

        for (Item item : BuiltInRegistries.ITEM)
        {   
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);

            if (!allowedMods.contains(key.getNamespace())) {
                continue;
            }

            if (itemValues.containsKey(item)) {
                // MCTradePostMod.LOGGER.info("Skipping '{}'", item.toString());
                continue;
            }
            MCTradePostMod.LOGGER.info("Processing '{}'", item.toString());
            
            int value = appraiseRecipeListForItem(item, recipeManager, new HashSet<>(), level, 0);
            itemValues.put(item, value);

            startTracking = false;
        }

        MCTradePostMod.LOGGER.info("Finished configuring theItemValueRegistry.");
    }

    /**
     * Estimate the value of an item by appraising the recipes that can craft it.
     *
     * @param item the item to estimate the value of
     * @param recipeManager the recipe manager to query for recipes
     * @param visited the set of items that have already been visited while estimating the value
     * @param level the level to query for item values
     * @param depth the current recursion depth of the estimation
     * @return the estimated value of the item
     */
    private static int appraiseRecipeListForItem(Item item, RecipeManager recipeManager, Set<Recipe<?>> visited, Level level, int depth) {
        int value = 0;
        
        // Retaining this for investigation of non-intuitive value calculations.
        if ("modname:item".equals(item.toString())) {
            MCTradePostMod.LOGGER.warn("At depth {}, Invoking tracking for item: {}", depth,item);
            startTracking = true;
        }

        if (startTracking) {
            MCTradePostMod.LOGGER.warn("At depth {}, start appraiseRecipeListForItem for item: {}", depth,item);
        }     

        if (itemValues.keySet().contains(item)) {
            // We have already appraised this item.  Use that.
            value = getValue(item);
        } else {
            List<RecipeHolder<?>> recipes = recipeManager.getRecipes().stream()
                // .filter(r -> r.value().getResultItem(level.registryAccess()).getItem().equals(item))
                .filter(r -> {
                    ItemStack result = r.value().getResultItem(level.registryAccess());
                    return result != null && !result.isEmpty() && result.getItem().equals(item);
                })
                .filter(r -> (r.value().getType() == RecipeType.CRAFTING) 
                    ||  (r.value().getType() == RecipeType.SMELTING) 
                    ||  (r.value().getType() == RecipeType.CAMPFIRE_COOKING)
                    ||  (r.value().getType() == RecipeType.STONECUTTING))
                .collect(Collectors.toList());

            // If recipes exists to create this thing, appraise the value of the thing from the sum of its recipe parts.
            if (!recipes.isEmpty()) {
                value = recipes.stream()
                        .map(r -> (int) appraiseRecipeValue(r.value(), recipeManager, level, visited, depth + 1))
                        .filter(v -> v > 0)
                        .min(Integer::compare)
                        .orElse(1);
                itemValues.put(item, value); // Since we've calculated the value, add  it to the map 
            } else {
                value = appraiseLootValue(item, (ServerLevel) level);     
                itemValues.put(item, value); // Since we've calculated the value, add  it to the map  
            }
        }

        if (startTracking) {
            MCTradePostMod.LOGGER.warn("At depth {}, finish appraiseRecipeListForItem for item: {}", depth,item);
        }    

        return value;
    }

    /**
     * Estimate the value of a recipe based on the values of its ingredients. This method is designed to be called
     * recursively to resolve the values of sub-recipes. If the recipe has a circular dependency, the method will
     * return the value of the item that caused the circular dependency.
     *
     * @param recipe the recipe to estimate the value of
     * @param recipeManager the recipe manager to query for sub-recipes
     * @param level the level to query for item values
     * @param trackingMode whether to log the ingredients of the recipe
     * @return the estimated value of the recipe
     */
    private static int appraiseRecipeValue(Recipe<?> recipe, RecipeManager recipeManager, Level level, Set<Recipe<?>> visited, int depth) {
        int total = 0;
            
        if (startTracking) {
            MCTradePostMod.LOGGER.warn("Depth: {}, start appraiseRecipeValue: {}", depth, recipe);
        }  

        if (visited.contains(recipe)) {
            if (startTracking) {
                MCTradePostMod.LOGGER.warn("Circular recipe dependency at Depth {}: recipe {}", depth, recipe);
            }
            total = 0;
            return total;   
        } else {
            visited.add(recipe);        
        }

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;

            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) {
                int ingredientvalue = Arrays.stream(stacks)
                    .map(stack -> {
                        int value = 0;
                        Item item = stack.getItem();                   

                        if (itemValues.keySet().contains(item)) {
                            // We have already appraised this item.  Use that.
                            value = getValue(item);
                        } else {
                            value = appraiseRecipeListForItem(item, recipeManager, visited, level, depth + 1);
                            itemValues.put(item, value); // Since we've calculated the value, add  it to the map  
                        }

                        if (startTracking) {
                            MCTradePostMod.LOGGER.warn("Depth {}: Item {} - value {}", depth, item, value);
                        }     

                        return value;
                    })
                    .min(Integer::compare)
                    .orElse(1);
                total += ingredientvalue;
            }
        }

        int resultCount = recipe.getResultItem(level.registryAccess()).getCount();
        total = resultCount == 0 ? total : Math.max(total / resultCount, 1);

        if (startTracking) {
            MCTradePostMod.LOGGER.warn("Depth {}: Recipe type {} - pre-smelt total {}", depth, recipe.getType(), total);
        }     

        if (recipe instanceof AbstractCookingRecipe) {
            int time = ((AbstractCookingRecipe) recipe).getCookingTime();

            // Normal cook time is 100, so this gives a 20% bonus to smelted items.  (Rounding means it matters only for items valued at 5+.)
            // Longer cook times result in higher bonuses.
            double cookMultiplier =  1 + ((double)time / 500.0);

            if (startTracking) {
                MCTradePostMod.LOGGER.warn("Cook time: {}, multiplier {} - pre-smelt total {}", time, cookMultiplier);
            }    

            total = (int) Math.round(total * cookMultiplier); 
        }

        if (startTracking) {
            MCTradePostMod.LOGGER.warn("Depth: {}, finish appraiseRecipeValue: {} with total {}", depth, recipe, total);
        }

        return total;
    }


    /**
     * Estimate the value of a given item by its rarity. The rarity is obtained from an ItemStack of the item.
     * @param item the item to estimate the value of
     * @param level the level to use for getting the rarity
     * @return the estimated value of the item
     */
    private static int appraiseLootValue(Item item, ServerLevel level) {
        ItemStack stack = new ItemStack(item);
        Rarity rarity = stack.getRarity();
        int value = 0;

        switch(rarity) {
            case COMMON -> value = 1;
            case UNCOMMON -> value = 5;
            case RARE -> value = 8;
            case EPIC -> value = 13;
            default -> value = 1;
        }

        if (startTracking) {
            MCTradePostMod.LOGGER.warn("appraiseLootValue Item {} - rarity {} - value {}", item, rarity, value);
        }

        return value;
    }

    public static int getValue(Item item)
    {
        return itemValues.getOrDefault(item, 1);
    }

    public static int getValue(ItemStack item)
    {
        return getValue(item.getItem());
    }

    public static void clearCache()
    {
        itemValues.clear();
        configured = false;
    }

    public static Map<Item, Integer> getItemValues()  
    {
        return itemValues;
    }

    /**
     * Retrieves a set of items that are sellable based on their registered values.
     * If the item values are empty, logs an error message.
     *
     * @return a set of ItemStorage objects representing sellable items.
     */
    public static Set<ItemStorage> getSellableItems()
    {
        if (itemValues.isEmpty())
        {
            MCTradePostMod.LOGGER.error("getSellableItems called when itemValues is empty");
        }
        return itemValues.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> new ItemStorage(new ItemStack(e.getKey())))
            .collect(Collectors.toSet());
    }

    /**
     * Retrieves a set of item keys representing sellable items.
     * Logs an error message if the item values map is empty.
     *
     * @return a set of strings representing the keys of sellable items.
     */
    public static Set<String> getSellableItemKeys()
    {
        if (itemValues.isEmpty())
        {
            MCTradePostMod.LOGGER.error("getSellableItemKeys called when itemValues is empty");
        }
        return itemValues.entrySet().stream()
            .filter(e -> e.getValue() > 0)
            .map(e -> e.getKey().toString())
            .collect(Collectors.toSet());
    }

    /**
     * Logs all known item values to the mod logger.
     */    
    public static void logValues() {
        Map<String, Integer> output = itemValues.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> BuiltInRegistries.ITEM.getKey(e.getKey()).toString(),
                        Map.Entry::getValue,
                        (v1, v2) -> v1,
                        TreeMap::new
                ));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(output);
        MCTradePostMod.LOGGER.info("Item Values JSON Dump:\n{}", json);
    }

    /**
     * Loads the default item values from the item_values.json file in the resources directory.
     * These values are used to seed the item value registry.
     * 
     * File is created based on ATM10 recipe lists.
     */
    private static void loadInitialValuesFromJson() {
        try {
            InputStream stream = ItemValueRegistry.class.getResourceAsStream("/data/mctradepost/item_values.json");
            if (stream == null) {
                MCTradePostMod.LOGGER.warn("Default item_values.json not found in resources.");
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Integer>>() {}.getType();
            Map<String, Integer> jsonValues = gson.fromJson(reader, type);

            for (Map.Entry<String, Integer> entry : jsonValues.entrySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(entry.getKey());

                if (rl != null) {
                    if (!allowedMods.contains(rl.getNamespace())) {
                        continue;
                    }

                    if (BuiltInRegistries.ITEM.containsKey(rl)) {
                        itemValues.putIfAbsent(BuiltInRegistries.ITEM.get(rl), entry.getValue());
                    } else {
                        // MCTradePostMod.LOGGER.warn("Invalid item or unknown key: {}", entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            MCTradePostMod.LOGGER.error("Error loading item_values.json"
            , e);
        }

        MCTradePostMod.LOGGER.info("Item seed values loaded.");
        // logValues();
    }

    /* In theory this should only be called on the client side after deserializing... */
    public static void deserializedSellableItem(String s, int value) {
        itemValues.putIfAbsent(BuiltInRegistries.ITEM.get(ResourceLocation.parse(s)), value);
    }    
} 
