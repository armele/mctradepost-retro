package com.deathfrog.mctradepost.api.util;

import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;

public class StringUtils {
    private static final Map<String, String> IRREGULAR_PLURALS = Map.of(
        "sheep", "sheep",
        "fish", "fish",
        "goose", "geese",
        "ox", "oxen",
        "cactus", "cacti",
        "child", "children",
        "man", "men",
        "woman", "women",
        "mouse", "mice"
        // Add more as needed
    );

    /**
     * Checks if the given character is a vowel (a, e, i, o, or u).
     *
     * @param c the character to check
     * @return true if the character is a vowel, false otherwise
     */

    public static boolean isVowel(char c) 
    {
        return "aeiou".indexOf(Character.toLowerCase(c)) >= 0;
    }

    /**
     * Returns the English plural form of the given EntityType's name.
     * This method knows about some irregular plurals, and follows some basic rules for regular plurals.
     * If the name ends with "y", but the second-to-last character is not a vowel, the plural is formed by replacing "y" with "ies".
     * If the name ends with "s", "x", "z", "ch", or "sh", the plural is formed by adding "es".
     * Otherwise, the plural is formed by adding "s".
     * @param entityType the EntityType to get the plural form of
     * @return the plural form of the EntityType's name
     */
    public static String getPluralEntityName(EntityType<?> entityType) 
    {
        String baseName = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).getPath();

        if (IRREGULAR_PLURALS.containsKey(baseName)) 
        {
            return IRREGULAR_PLURALS.get(baseName);
        }

        // Basic pluralization rules
        if (baseName.endsWith("y") && baseName.length() > 1 &&
            !isVowel(baseName.charAt(baseName.length() - 2))) {
            return baseName.substring(0, baseName.length() - 1) + "ies";
        } 
        else if (baseName.endsWith("s") || baseName.endsWith("x") ||
                baseName.endsWith("z") || baseName.endsWith("ch") || baseName.endsWith("sh")) {
            return baseName + "es";
        }

        return baseName + "s";
    }
}
