package com.deathfrog.mctradepost.api.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Defines the recycler's item matching semantics. Durability and the exact
 * enchantments are ignored, but enchanted and unenchanted stacks remain
 * distinct and all other components must match.
 */
public final class RecyclableItemMatcher
{
    private RecyclableItemMatcher()
    {
    }

    public static boolean matches(final ItemStack definition, final ItemStack candidate)
    {
        if (definition == null || candidate == null || definition.isEmpty() || candidate.isEmpty())
        {
            return false;
        }

        if (isEnchanted(definition) != isEnchanted(candidate))
        {
            return false;
        }

        final ItemStack normalizedDefinition = normalize(definition);
        final ItemStack normalizedCandidate = normalize(candidate);

        if (normalizedCandidate == null || normalizedDefinition == null) return false;

        return ItemStack.isSameItemSameComponents(normalizedDefinition, normalizedCandidate);
    }

    @SuppressWarnings("null")
    public static boolean isEnchanted(final ItemStack stack)
    {
        return stack != null && !stack.isEmpty()
            && (stack.isEnchanted()
                || !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty());
    }

    @SuppressWarnings("null")
    private static ItemStack normalize(final ItemStack stack)
    {
        final ItemStack normalized = stack.copy();
        normalized.setCount(1);
        normalized.remove(DataComponents.DAMAGE);
        normalized.remove(DataComponents.ENCHANTMENTS);
        normalized.remove(DataComponents.STORED_ENCHANTMENTS);
        return normalized;
    }
}
