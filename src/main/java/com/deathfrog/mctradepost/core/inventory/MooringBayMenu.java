package com.deathfrog.mctradepost.core.inventory;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-backed menu for installed Route Surveys and synchronized Mooring Bay gas data. */
public class MooringBayMenu extends AbstractContainerMenu
{
    private final Container bay;
    private final ContainerData data;

    public MooringBayMenu(int id, Inventory inventory)
    {
        this(id, inventory, new SimpleContainer(MooringBayBlockEntity.SURVEY_SLOTS), new SimpleContainerData(4));
    }

    @SuppressWarnings("null")
    public MooringBayMenu(int id, Inventory inv, Container bay, ContainerData data)
    {
        super(MCTradePostMod.MOORING_BAY_MENU.get(), id);
        this.bay = bay;
        this.data = data;
        checkContainerSize(bay, MooringBayBlockEntity.SURVEY_SLOTS);
        checkContainerDataCount(data, 4);
        bay.startOpen(inv.player);
        for (int i = 0; i < 5; i++) addSlot(new Slot(bay, i, 44 + i * 18, 38)
        {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack)
            {
                return bay.canPlaceItem(getSlotIndex(), stack);
            }

            @Override
            public int getMaxStackSize()
            {
                return 1;
            }
        });
        addPlayerInventory(inv);
        addDataSlots(data);
    }

    private void addPlayerInventory(@Nonnull Inventory inv)
    {
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 69 + r * 18));
        for (int i = 0; i < 9; i++) addSlot(new Slot(inv, i, 8 + i * 18, 127));
    }

    @Override
    public boolean stillValid(@Nonnull Player p)
    {
        return bay.stillValid(p);
    }

    @Override
    public void removed(@Nonnull Player p)
    {
        super.removed(p);
        bay.stopOpen(p);
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index)
    {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), copy = original.copy();
        int n = bay.getContainerSize();
        if (index < n)
        {
            if (!moveItemStackTo(original, n, slots.size(), true)) return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(original, 0, n, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    public int gas()
    {
        return combine(data.get(0), data.get(1));
    }

    public int capacity()
    {
        return combine(data.get(2), data.get(3));
    }

    private static int combine(int low, int high)
    {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }
}
