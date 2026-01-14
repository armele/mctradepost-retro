package com.deathfrog.mctradepost.api.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.tileentities.TileEntityRack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public class MCTPInventoryUtils
{
    /**
     * Finds a random slot in the given IItemHandler which matches the given predicate and returns it.
     * If no slot is found, returns -1.
     *
     * @param itemHandler the IItemHandler to search in
     * @param itemStackSelectionPredicate the predicate to test each slot with
     * @return a random matching slot, or -1 if no slot matches
     */
    public static int findRandomSlotInItemHandlerWith(@NotNull IItemHandler itemHandler,
        @NotNull Predicate<ItemStack> itemStackSelectionPredicate)
    {
        List<Integer> matchingSlots = new ArrayList<>();

        for (int slot = 0; slot < itemHandler.getSlots(); ++slot)
        {
            if (itemStackSelectionPredicate.test(itemHandler.getStackInSlot(slot)))
            {
                matchingSlots.add(slot);
            }
        }

        if (matchingSlots.isEmpty())
        {
            return -1;
        }

        return matchingSlots.get(ThreadLocalRandom.current().nextInt(matchingSlots.size()));
    }

    /**
     * Returns a map of all items stored in the building, including its inventory and all racks, with their count.
     * 
     * @param building the building to query
     * @return a map of item stacks to their count
     */
    public static Object2IntMap<ItemStorage> contentsForBuilding(IBuilding building)
    {
        final Object2IntOpenHashMap<ItemStorage> storedItems = new Object2IntOpenHashMap<>();
        if (building == null)
        {
            return storedItems;
        }

        final Set<BlockPos> containerList = new HashSet<>(building.getContainers());
        storedItems.defaultReturnValue(0); // avoid nulls on get()

        final Level world = building.getColony().getWorld();
        containerList.add(building.getPosition());

        for (final BlockPos blockPos : containerList)
        {
            if (blockPos == null)
            {
                continue;
            }

            final BlockEntity rack = world.getBlockEntity(blockPos);
            if (rack instanceof TileEntityRack)
            {
                final Map<ItemStorage, Integer> rackStorage = ((TileEntityRack) rack).getAllContent();

                for (final Map.Entry<ItemStorage, Integer> entry : rackStorage.entrySet())
                {
                    storedItems.addTo(entry.getKey(), entry.getValue().intValue());
                }
            }
        }

        return storedItems;
    }

    /**
     * This implementation is COPIED from the private method of com.minecolonies.api.crafting.GenericRecipe Calculates the secondary
     * outputs of a recipe, e.g. the items left over after crafting.
     * 
     * @param recipe the recipe to calculate the secondary outputs for
     * @param world  the world to use for the calculation, or null if not applicable
     * @return a list of the secondary outputs, or an empty list if no secondary outputs are possible
     */
    @NotNull
    public static List<ItemStack> calculateSecondaryOutputs(@NotNull final Recipe<?> recipe, @Nonnull final Level world)
    {
        if (recipe instanceof final CraftingRecipe craftingRecipe)
        {
            final List<Ingredient> inputs = recipe.getIngredients();
            final CraftingContainer inv = new TransientCraftingContainer(new AbstractContainerMenu(MenuType.CRAFTING, 0)
            {
                @Override
                public @Nonnull ItemStack quickMoveStack(final @Nonnull Player player, final int slot)
                {
                    return NullnessBridge.assumeNonnull(ItemStack.EMPTY);
                }

                @Override
                public boolean stillValid(@Nonnull final Player playerIn)
                {
                    return false;
                }
            }, 3, 3);

            for (int slot = 0; slot < inputs.size(); ++slot)
            {
                final ItemStack[] stacks = inputs.get(slot).getItems();
                if (stacks.length > 0)
                {
                    ItemStack copyFirst = stacks[0].copy();

                    if (copyFirst.isEmpty()) continue;

                    inv.setItem(slot, copyFirst);
                }
            }

            CraftingInput input = inv.asCraftInput();

            if (input != null && craftingRecipe.matches(input, world))
            {
                return craftingRecipe.getRemainingItems(input)
                    .stream()
                    .filter(ItemStackUtils::isNotEmpty)
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    /**
     * Drops an item stack into the world at the given position. Will only create an ItemEntity if the world is server-side.
     *
     * @param level     the level to drop the item in
     * @param pos       the position to drop the item at
     * @param itemStack the item stack to drop
     */
    public static void dropItemsInWorld(@Nonnull ServerLevel level, @Nonnull BlockPos pos, @Nonnull ItemStack itemStack)
    {
        // Check if the level is server-side
        if (!level.isClientSide)
        {
            // Create a new ItemEntity at the specified position with the item stack
            ItemEntity itemEntity = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
            level.addFreshEntity(itemEntity);
        }
    }


    /**
     * Counts the number of items in the given {@link ItemStorage} that are in the building's inventory and all of its assigned workers' inventories.
     * 
     * @param building     the building to check
     * @param itemToCount  the item to count
     * @return the count of the given item in the building and its assigned workers' inventories
     */
    public static int combinedInventoryCount(IBuilding building, ItemStorage itemToCount)
    {
        if (building == null || building.getItemHandlerCap() == null) 
        {
            return 0;
        }

        int buildingCount = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), stack -> Objects.equals(new ItemStorage(stack), itemToCount));
        int workerCount = 0;

        for (ICitizenData buildingWorker : building.getAllAssignedCitizen())
        {
            workerCount = workerCount + InventoryUtils.getItemCountInItemHandler(buildingWorker.getInventory(), stack -> Objects.equals(new ItemStorage(stack), itemToCount));
        }

        return buildingCount + workerCount;
    }

    /**
     * Attempts to remove the given amount of the given item from the building and assigned workers' inventories.
     * Building inventory takes precendence.
     * 
     * @param buildingStation The building to deduct from.
     * @param itemToDeduct     The item to deduct.
     * @param amountToDeduct   The amount of the item to deduct.
     * 
     * @return true if the item was successfully removed, false otherwise.
     */
    public static boolean combinedInventoryRemoval(IBuilding building, ItemStorage itemToDeduct, int amountToDeduct)
    {
        int remainingNeed = amountToDeduct;

        if (building == null || building.getItemHandlerCap() == null) 
        {
            return false;
        }

        int buildingCount = InventoryUtils.getItemCountInItemHandler(building.getItemHandlerCap(), stack -> Objects.equals(new ItemStorage(stack), itemToDeduct));
        int workerCount = 0;

        for (ICitizenData buildingWorker : building.getAllAssignedCitizen())
        {
            workerCount = workerCount + InventoryUtils.getItemCountInItemHandler(buildingWorker.getInventory(), stack -> Objects.equals(new ItemStorage(stack), itemToDeduct));
        }


        if (buildingCount + workerCount < amountToDeduct)
        {
            return false;
        }

        if (buildingCount >= amountToDeduct)
        {
            InventoryUtils.reduceStackInItemHandler(building.getItemHandlerCap(), itemToDeduct.getItemStack().copy(), amountToDeduct);
            remainingNeed = 0;
        }       
        else if (buildingCount > 0)
        {
            InventoryUtils.reduceStackInItemHandler(building.getItemHandlerCap(), itemToDeduct.getItemStack().copy(), buildingCount);
            remainingNeed = amountToDeduct - buildingCount;
        }

        if (remainingNeed > 0)
        {
            for (ICitizenData buildingWorker : building.getAllAssignedCitizen())
            {
                workerCount = InventoryUtils.getItemCountInItemHandler(buildingWorker.getInventory(), stack -> Objects.equals(new ItemStorage(stack), itemToDeduct));
                if ((workerCount >= remainingNeed) && (buildingWorker != null) && (buildingWorker.getInventory() != null))
                {   
                    InventoryUtils.reduceStackInItemHandler(buildingWorker.getInventory(), itemToDeduct.getItemStack().copy(), remainingNeed);
                    remainingNeed = 0;
                }
                else
                {
                    InventoryUtils.reduceStackInItemHandler(buildingWorker.getInventory(), itemToDeduct.getItemStack().copy(), workerCount);
                    remainingNeed = remainingNeed - workerCount;
                }
            }
        }

        if (remainingNeed > 0)
        {
            MCTradePostMod.LOGGER.warn("Partial removal of {} {} from building inventory and worker inventories - should not happen.", remainingNeed, itemToDeduct);
            return false;
        }

        return true;
    };

    /**
     * Attempts to insert the given amount of the given item into the specified building.
     * If any items cannot be inserted into the inventory, they will be dropped into the world.
     * Items are inserted at their maximum stack size.
     * @param building The building to insert into.
     * @param itemToDeposit     The item to deposit.
     * @param amountToDeposit   The amount of the item to deposit.
     */
    public static void insertOrDropByQuantity(IBuilding building, ItemStorage itemToDeposit) 
    {
        int amountToDeposit = itemToDeposit.getAmount();

        if (building == null || building.getItemHandlerCap() == null || itemToDeposit == null || itemToDeposit.getItemStack() == null) 
        {
            MCTradePostMod.LOGGER.warn("No building, building inventory or deposit item found attempting to deposit {} {} - this should not happen.", amountToDeposit, itemToDeposit);
            return;
        }

        BlockPos buildingPos = building.getPosition();
        Level world = building.getColony().getWorld();

        if (buildingPos == null || world == null || world.isClientSide())
        {
            return;
        }

        List<ItemStack> finalDeposit = new ArrayList<>();
        int amountRemaining = amountToDeposit;

        while (amountRemaining > 0) 
        {
            ItemStack deposit = itemToDeposit.getItemStack().copy();
            deposit.setCount(Math.min(deposit.getMaxStackSize(), amountRemaining));
            finalDeposit.add(deposit);
            amountRemaining -= deposit.getCount();
        }

        for (ItemStack finalDepositItem : finalDeposit)
        {
            if (finalDepositItem.isEmpty())
            {
                continue;
            }

            if (!InventoryUtils.addItemStackToItemHandler(building.getItemHandlerCap(), finalDepositItem))
            {
                MCTPInventoryUtils.dropItemsInWorld((ServerLevel) world, buildingPos, finalDepositItem);
            }
        }
    }

    /**
     * Calculates the percentage of occupied (non-empty) slots in an IItemHandlerCapProvider.
     * 
     * @param provider the IItemHandlerCapProvider to check (non-null)
     * @return percentage of occupied slots, 0..100
     */
    public static int filledSlotsPercentage(IItemHandlerCapProvider provider)
    {
        if (provider == null) return 0;

        IItemHandler handler = provider.getItemHandlerCap();
        if (handler == null) return 0;

        final int total = handler.getSlots();
        if (total <= 0) return 0;

        int filled = 0;
        for (int i = 0; i < total; i++)
        {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty())
            {
                filled++;
            }
        }

        // Use double to avoid integer truncation, then round and clamp to [0, 100]
        int percent = (int) Math.round(100.0 * filled / total);
        return Math.max(0, Math.min(100, percent));
    }

    /**
     * Extracts enchantments from an item and places them in new enchanted book items. If the item does not have any enchantments,
     * an empty list is returned. The enchantments are removed from the original item.
     * 
     * @param enchantedItem the item from which to extract enchantments
     * @return the list of enchanted book items, one per enchantment
     */
    @SuppressWarnings("unchecked")
    public static List<ItemStack> extractEnchantmentsToBooks(ItemStack enchantedItem)
    {
        if (!enchantedItem.isEnchanted()) 
        {
            return Collections.emptyList();
        }

        ItemEnchantments ench = enchantedItem.getTagEnchantments();
        if (ench == null || ench.isEmpty()) 
        {
            return Collections.emptyList();
        }

        List<ItemStack> out = new ArrayList<>();

        // Iterate each (enchantment, level)
        for (Map.Entry<Holder<Enchantment>, Integer> entry : ench.entrySet())
        {
            Holder<Enchantment> holder;
            int level;

            // Some mappings expose entry as a Map.Entry<Holder<Enchantment>, Integer>:
            try
            {
                holder = (Holder<Enchantment>) (Object) entry.getKey();
                level = (Integer) (Object) entry.getValue();
            }
            catch (Throwable t)
            {
                // Others expose specialized accessors:
                holder = (Holder<Enchantment>) (Object) (entry instanceof Object2IntMap.Entry<?> e ?
                    e.getKey() :
                    null);
                level = (entry instanceof Object2IntMap.Entry<?> e ? e.getIntValue() : 0);
            }

            if (holder != null && level > 0)
            {
                ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level));
                out.add(book);
            }
        }

        // Strip enchantments from the original item (important if you keep/return it)
        enchantedItem.remove(NullnessBridge.assumeNonnull(DataComponents.ENCHANTMENTS));

        return out;
    }

    /**
     * Extracts enchantments from an item and places them in a new enchanted book item. If the item does not have any enchantments, an
     * empty ItemStack is returned. The enchantments are removed from the original item.
     * 
     * @param enchantedItem the item from which to extract enchantments
     * @return the ItemStack containing a single enchanted book
     */
    public static ItemStack extractEnchantmentsToBook(ItemStack enchantedItem)
    {
        if (!enchantedItem.isEnchanted())
        {
            // Item has no enchantments
            return ItemStack.EMPTY;
        }

        ItemStack book = new ItemStack(NullnessBridge.assumeNonnull(Items.ENCHANTED_BOOK));

        // Get the enchantments from the original item
        ItemEnchantments enchantments = enchantedItem.getTagEnchantments();

        // Apply these enchantments to the book
        book.set(NullnessBridge.assumeNonnull(DataComponents.STORED_ENCHANTMENTS), enchantments);

        // Clear the enchantments from the original item
        enchantedItem.remove(NullnessBridge.assumeNonnull(DataComponents.ENCHANTMENTS));

        return book;
    }

    /**
     * Returns true if:
     * - there is no block entity, or
     * - it exposes an inventory (Container or IItemHandler) and all slots are empty.
     *
     * "side" can be null (unsided).
     */
    public static boolean isContainerEmpty(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull Direction side)
    {
        if (level == null || level.isClientSide)
        {
            return true;
        }

        final BlockEntity be = level.getBlockEntity(pos);
        if (be == null)
        {
            return true;
        }

        // 1) Vanilla-style inventory
        if (be instanceof Container container)
        {
            for (int i = 0; i < container.getContainerSize(); i++)
            {
                if (!container.getItem(i).isEmpty())
                {
                    return false;
                }
            }
            return true;
        }

        // 2) NeoForge capability-based inventory
        final IItemHandler handler = level.getCapability(NullnessBridge.assumeNonnull(Capabilities.ItemHandler.BLOCK), pos, side);
        if (handler != null)
        {
            for (int i = 0; i < handler.getSlots(); i++)
            {
                if (!handler.getStackInSlot(i).isEmpty())
                {
                    return false;
                }
            }
        }

        return true;
    }

    /** Convenience overload: unsided lookup. */
    @SuppressWarnings("null")
    public static boolean isContainerEmpty(Level level, BlockPos pos)
    {
        return isContainerEmpty(level, pos, null);
    }
}
