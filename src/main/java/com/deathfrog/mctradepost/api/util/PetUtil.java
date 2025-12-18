package com.deathfrog.mctradepost.api.util;

import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class PetUtil 
{

    /**
     * Inserts the {@link ItemEntity}'s item into the {@link ITradePostPet}'s inventory.
     * If the item is already present in the inventory, it will be added to the existing stack.
     * If there is no room in the inventory, the item remains in the {@link ItemEntity}.
     *
     * @param pet the pet to insert items into
     * @param item the item to insert
     */
    public static void insertItem(ITradePostPet pet, ItemEntity item) 
    {
        ItemStackHandler inventory = pet.getInventory();
        ItemStack stack = item.getItem();

        if (stack == null || stack.isEmpty()) return;

        for (int i = 0; i < inventory.getSlots(); i++)
        {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing != null && ItemStack.isSameItemSameComponents(existing, stack))
            {
                stack = inventory.insertItem(i, stack, false);
                if (stack.isEmpty()) break;
            }
        }

        if (!stack.isEmpty())
        {
            int slot = firstEmptySlot(inventory);
            if (slot > -1)
            {
                stack = inventory.insertItem(slot, stack, false);
            }
        }

        if (stack.isEmpty())
        {
            item.discard();
        }
        else
        {
            item.setItem(stack);
        }
    }

    /**
     * Inserts {@link ItemEntity} items into the inventory of an {@link ITradePostPet}.
     * 
     * @param pet the pet to insert items into
     * @param items the items to insert
     */
    public static void insertItems(ITradePostPet pet, Iterable<ItemEntity> items) 
    {
        // ItemStackHandler inventory = pet.getInventory();
        for (ItemEntity item : items)
        {
            insertItem(pet, item);
        }        
    }

    /**
     * Finds the first empty slot in the given inventory.
     *
     * @param inventory the inventory to search
     * @return the index of the first empty slot, or -1 if no empty slot is found
     */
    private static int firstEmptySlot(ItemStackHandler inventory)
    {
        for (int i = 0; i < inventory.getSlots(); i++)
        {
            if (inventory.getStackInSlot(i).isEmpty())
            {
                return i;
            }
        }
        return -1;
    }
}
