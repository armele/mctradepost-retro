package com.deathfrog.mctradepost.api.util;

import javax.annotation.Nonnull;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public class ItemStackHandlerContainerWrapper implements Container
{
    private final ItemStackHandler handler;

    public ItemStackHandlerContainerWrapper(ItemStackHandler handler)
    {
        this.handler = handler;
    }

    @Override
    public int getContainerSize()
    {
        return handler.getSlots();
    }

    @Override
    public boolean isEmpty()
    {
        for (int i = 0; i < handler.getSlots(); i++)
        {
            if (!handler.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index)
    {
        return handler.getStackInSlot(index);
    }

    @Override
    public ItemStack removeItem(int index, int count)
    {
        ItemStack stack = handler.extractItem(index, count, false);
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index)
    {
        ItemStack stack = handler.getStackInSlot(index);
        handler.setStackInSlot(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int index, @Nonnull ItemStack stack)
    {
        handler.setStackInSlot(index, stack);
    }

    @Override
    public boolean stillValid(@Nonnull Player player)
    {
        return true;
    }

    @Override
    public void clearContent()
    {
        for (int i = 0; i < handler.getSlots(); i++)
        {
            handler.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public void setChanged()
    {

    }
}
