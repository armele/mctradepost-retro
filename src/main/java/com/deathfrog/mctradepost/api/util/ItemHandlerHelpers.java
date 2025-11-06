package com.deathfrog.mctradepost.api.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.StackList;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.ItemStackUtils;
import com.mojang.logging.LogUtils;

// --- Utilities to obtain a handler or provider for a block position ---
public final class ItemHandlerHelpers
{
    /** Try capability; if missing, wrap the Container (handles double chests). */
    public static Optional<IItemHandler> getHandler(Level level, BlockPos pos, @org.jetbrains.annotations.Nullable Direction side)
    {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return Optional.empty();

        // 1) NeoForge capability lookup (preferred)
        IItemHandler cap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, level.getBlockState(pos), be, side);
        if (cap != null) return Optional.of(cap);

        // 2) Wrap a vanilla Container if present
        Container container = extractContainer(level, pos, be);
        if (container == null) return Optional.empty();

        if (side != null && container instanceof WorldlyContainer wc)
        {
            return Optional.of(new SidedInvWrapper(wc, side));
        }
        return Optional.of(new InvWrapper(container));
    }

    /** Same as getHandler, but returns your provider interface for helpers that expect it. */
    public static Optional<IItemHandlerCapProvider> getProvider(Level level,
        BlockPos pos,
        @org.jetbrains.annotations.Nullable Direction side)
    {
        return getHandler(level, pos, side).map(SimpleHandlerProvider::of);
    }

    /** Gets a Container, joining double chests correctly. */
    private static Container extractContainer(Level level, BlockPos pos, BlockEntity be)
    {
        if (be instanceof ChestBlockEntity cbe)
        {
            // Join double chests (if any)
            Container joined =
                ChestBlock.getContainer((ChestBlock) level.getBlockState(pos).getBlock(), level.getBlockState(pos), level, pos, false);
            if (joined != null) return joined;
            return cbe; // fallback: single chest container
        }
        if (be instanceof Container c) return c;
        return null;
    }


    /**
     * Retrieves the first item storage from the given inventory that satisfies the given request.
     * If allowPartial is true, returns the biggest matching stack instead of null if the request is not fully satisfied.
     * 
     * @param request the request to check for satisfaction
     * @param inv the inventory to search
     * @param allowPartial whether to allow partial satisfaction
     * @return the item storage that satisfies the request, or null if not found
     */
    public static ItemStorage inventorySatisfiesRequest(@NotNull IRequest<? extends IRequestable> request, IItemHandler inv, boolean allowPartial)
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
                    return new ItemStorage(stack.getItem(), required);
                }
            }
        }
        
        if (allowPartial && !matchingStacks.isEmpty()) 
        {
            ItemStack biggestStack = matchingStacks.stream()
                .max(Comparator.comparingInt(ItemStack::getCount))
                .orElse(ItemStack.EMPTY);
            return new ItemStorage(biggestStack.getItem(), have); 
        }
        // LOGGER.info("Not enough matching items: {}", have);

        return null;
    }


    /**
     * Builds a predicate that tells whether an inventory ItemStack qualifies for the requestable. Extend this as you introduce new
     * requestable types.
     */
    public static Predicate<ItemStack> matcherFor(IRequestable req)
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
            return st -> !st.isEmpty() && ItemStack.isSameItem(st, wanted);
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


    public static Predicate<ItemStack> predicateForHolderSet(HolderSet<Item> set)
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


    /**
     * How many items must we have to consider the request satisfied?
     */
    public static int requiredCountFor(IRequestable req)
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

    /* ----------------------- matchers & quantity logic ----------------------- */


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