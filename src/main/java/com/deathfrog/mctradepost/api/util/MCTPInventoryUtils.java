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
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.tileentities.TileEntityRack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public class MCTPInventoryUtils
{
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
                    return ItemStack.EMPTY;
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
                    inv.setItem(slot, stacks[0].copy());
                }
            }
            if (craftingRecipe.matches(inv.asCraftInput(), world))
            {
                return craftingRecipe.getRemainingItems(inv.asCraftInput())
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
    public static void dropItemsInWorld(ServerLevel level, BlockPos pos, ItemStack itemStack)
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
            InventoryUtils.reduceStackInItemHandler(building.getItemHandlerCap(), itemToDeduct.getItemStack(), amountToDeduct);
            remainingNeed = 0;
        }       
        else if (buildingCount > 0)
        {
            InventoryUtils.reduceStackInItemHandler(building.getItemHandlerCap(), itemToDeduct.getItemStack(), buildingCount);
            remainingNeed = amountToDeduct - buildingCount;
        }

        if (remainingNeed > 0)
        {
            for (ICitizenData buildingWorker : building.getAllAssignedCitizen())
            {
                workerCount = InventoryUtils.getItemCountInItemHandler(buildingWorker.getInventory(), stack -> Objects.equals(new ItemStorage(stack), itemToDeduct));
                if ((workerCount >= remainingNeed) && (buildingWorker != null) && (buildingWorker.getInventory() != null))
                {   
                    InventoryUtils.reduceStackInItemHandler(buildingWorker.getInventory(), itemToDeduct.getItemStack(), remainingNeed);
                    remainingNeed = 0;
                }
                else
                {
                    InventoryUtils.reduceStackInItemHandler(buildingWorker.getInventory(), itemToDeduct.getItemStack(), workerCount);
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
    public static void InsertOrDropByQuantity(IBuilding building, ItemStorage itemToDeposit, int amountToDeposit) 
    {
        if (building == null || building.getItemHandlerCap() == null || itemToDeposit == null || itemToDeposit.getItemStack() == null) 
        {
            MCTradePostMod.LOGGER.warn("No building, building inventory or deposit item found attempting to deposit {} {} - this should not happen.", amountToDeposit, itemToDeposit);
            return;
        }

        List<ItemStack> finalDeposit = new ArrayList<>();
        int amountRemaining = amountToDeposit;

        while (amountRemaining > 0) {
            ItemStack deposit = itemToDeposit.getItemStack().copy();
            deposit.setCount(Math.min(deposit.getMaxStackSize(), amountRemaining));
            finalDeposit.add(deposit);
            amountRemaining -= deposit.getCount();
        }

        for (ItemStack finalDepositItem : finalDeposit)
        {
            if (!InventoryUtils.addItemStackToItemHandler(building.getItemHandlerCap(), finalDepositItem))
            {
                MCTPInventoryUtils.dropItemsInWorld((ServerLevel) building.getColony().getWorld(), 
                    building.getPosition(), 
                    finalDepositItem);
            }
        }
    }
}
