package com.deathfrog.mctradepost.core.colony.requestsystem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.ItemStackUtils;
import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Add this method to your IBuilding implementation (or a small helper class). It answers: "Does this building's inventory satisfy the
 * given request?"
 */
public interface IRequestSatisfaction extends IBuilding
{

    /**
     * True if the building has enough of something that matches the request's criteria.
     */
    default ItemStorage inventorySatisfiesRequest(@NotNull IRequest<? extends IRequestable> request, boolean allowPartial)
    {
        final Logger LOGGER = LogUtils.getLogger();

        final IRequestable payload = request.getRequest();
        final Predicate<ItemStack> matcher = matcherFor(payload);

        if (matcher == null)
        {
            // LOGGER.info("Unknown requestable type {}.", payload.getClass().getName());
            return null;
        }

        final int required = requiredCountFor(payload);

        final IItemHandler inv = getItemHandlerCap();
        int have = 0;

        // LOGGER.info("Looking for {} across {} slots.", required, inv.getSlots());
        List<ItemStack> matchingStacks = new ArrayList<>();

        for (int slot = 0; slot < inv.getSlots(); slot++)
        {
            final ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (matcher.test(stack))
            {
                matchingStacks.add(stack);
                have += stack.getCount();
                if (have >= required) 
                {
                    // LOGGER.info("Found enough matching items: {}", have);
                    return new ItemStorage(stack.copy(), required);
                }
            }

        }
        
        if (allowPartial && !matchingStacks.isEmpty()) 
        {
            ItemStack biggestStack = matchingStacks.stream()
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(ItemStack.EMPTY);
            return new ItemStorage(biggestStack.copy(), have); 
        }
        // LOGGER.info("Not enough matching items: {}", have);

        return null;
    }

    /* ----------------------- matchers & quantity logic ----------------------- */

    /**
     * Builds a predicate that tells whether an inventory ItemStack qualifies for the requestable. Extend this as you introduce new
     * requestable types.
     */
    static Predicate<ItemStack> matcherFor(IRequestable req)
    {
        // 1) Concrete Stack: match exact item+components (1.21+), ignore requested count here.
        if (req instanceof Stack)
        {
            Stack specificStack = (Stack) req;
            final ItemStack wanted = specificStack.getStack();
            return st -> !st.isEmpty() && ItemStack.isSameItemSameComponents(st, wanted);
        }

        // 2) Delivery often wraps a "Stack"-like intent (adjust if your Delivery exposes different getters)
        if (req instanceof Delivery d)
        {
            final ItemStack wanted = d.getStack();
            return st -> !st.isEmpty() && ItemStack.isSameItemSameComponents(st, wanted);
        }

        // 3) StackList matcher
        if (req instanceof StackList sl)
        {
            // Pre-filter empties once; the predicate runs a lot in hot paths
            final List<ItemStack> wanteds = sl.getStacks().stream().filter(s -> s != null && !s.isEmpty()).toList();

            if (wanteds.isEmpty())
            {
                return st -> false;
            }

            return st -> !st.isEmpty() && wanteds.stream().anyMatch(w -> ItemStack.isSameItem(st, w));
        }

        // Tool requests
        if (req instanceof Tool tool)
        {
            final EquipmentTypeEntry type = tool.getEquipmentType();
            final int min = tool.getMinLevel();             // -1 means “no minimum”
            final int max = tool.getMaxLevel();             // Integer.MAX_VALUE means “no maximum”

            // Use the same logic MineColonies uses:
            return st -> !st.isEmpty() && ItemStackUtils.hasEquipmentLevel(st, type, min, max);
        }

        // Tag-based requests (if requestable exposes a TagKey<Item> or HolderSet<Item>)
        if (req instanceof QualifiesByTag tagReq)
        {
            final TagKey<Item> tag = tagReq.tag();
            return st -> !st.isEmpty() && st.is(tag);
        }

        if (req instanceof QualifiesByHolderSet setReq)
        {
            final HolderSet<Item> set = setReq.items();
            final Predicate<ItemStack> p = predicateForHolderSet(set);
            return st -> p.test(st);
        }

        // Ingredient-based (“any item matching this Ingredient”)
        if (req instanceof QualifiesByIngredient ingReq)
        {
            final Ingredient ing = ingReq.ingredient();
            return st -> !st.isEmpty() && ing.test(st);
        }

        // Custom predicate-capable requestables
        if (req instanceof PredicateBackedRequestable p)
        {
            return st -> !st.isEmpty() && p.matches(st);
        }

        return null; // unknown type
    }

    /**
     * How many items must we have to consider the request satisfied?
     */
    default int requiredCountFor(IRequestable req)
    {
        if (req instanceof Stack s)
        {
            // Assume just the minumum.
            final int min = Math.max(0, s.getCount());
            return min;
        }
        if (req instanceof Delivery d)
        {
            return d.getStack().getCount(); // adjust if Delivery has min/complete flags in your build
        }
        if (req instanceof QuantityAware q)
        {
            return Math.max(0, q.requiredCount());
        }
        if (req instanceof StackList sl)
        {
            return Math.max(0, sl.getCount());
        }

        // Fallback: assume 1
        return 1;
    }

    static Predicate<ItemStack> predicateForHolderSet(HolderSet<Item> set)
    {
        if (set instanceof HolderSet.Named<Item> named)
        {
            final TagKey<Item> tag = named.key();
            return st -> !st.isEmpty() && st.is(tag);
        }

        // Pre-extract keys and fallback values once
        final java.util.HashSet<ResourceKey<Item>> keys = new java.util.HashSet<>();
        final java.util.HashSet<Item> values = new java.util.HashSet<>();
        for (Holder<Item> h : set)
        {
            h.unwrapKey().ifPresent(keys::add);
            if (!h.unwrapKey().isPresent()) values.add(h.value());
        }

        return st -> {
            if (st.isEmpty()) return false;
            final Item item = st.getItem();
            final Optional<ResourceKey<Item>> keyOpt = BuiltInRegistries.ITEM.getResourceKey(item);
            if (keyOpt.isPresent()) return keys.contains(keyOpt.get());
            return values.contains(item);
        };
    }


    /* ----------------------- optional mini-interfaces -----------------------
       These let us support “broad” requests without depending on internal classes.
    */

    /** A requestable that is satisfied by any item matching a tag. */
    interface QualifiesByTag extends IRequestable
    {
        TagKey<Item> tag();
    }

    /** A requestable that is satisfied by any item inside a HolderSet. */
    interface QualifiesByHolderSet extends IRequestable
    {
        HolderSet<Item> items();
    }

    /** A requestable that is satisfied by any item matching an Ingredient. */
    interface QualifiesByIngredient extends IRequestable
    {
        Ingredient ingredient();
    }

    /** A requestable that provides its own predicate. */
    interface PredicateBackedRequestable extends IRequestable
    {
        boolean matches(ItemStack stack);
    }

    /** A requestable that can tell how many are required. */
    interface QuantityAware extends IRequestable
    {
        int requiredCount();
    }
}
