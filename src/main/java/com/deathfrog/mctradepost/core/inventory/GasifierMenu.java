package com.deathfrog.mctradepost.core.inventory;

import javax.annotation.Nonnull;
import com.deathfrog.mctradepost.MCTradePostMod;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-backed menu for the Gasifier fuel slot and synchronized operating data. */
public class GasifierMenu extends AbstractContainerMenu
{
    private final Container machine;
    private final ContainerData data;

    public GasifierMenu(int id, Inventory inventory)
    {
        this(id, inventory, new SimpleContainer(1), new SimpleContainerData(5));
    }

    @SuppressWarnings("null")
    public GasifierMenu(int id, Inventory inv, @Nonnull Container machine, @Nonnull ContainerData data)
    {
        super(MCTradePostMod.GASIFIER_MENU.get(), id);
        this.machine = machine;
        this.data = data;
        checkContainerSize(machine, 1);
        checkContainerDataCount(data, 5);
        machine.startOpen(inv.player);
        addSlot(new Slot(machine, 0, 56, 53)
        {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack)
            {
                return machine.canPlaceItem(0, stack);
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 84 + r * 18));
        for (int i = 0; i < 9; i++) addSlot(new Slot(inv, i, 8 + i * 18, 142));
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(@Nonnull Player p)
    {
        return machine.stillValid(p);
    }

    @Override
    public void removed(@Nonnull Player p)
    {
        super.removed(p);
        machine.stopOpen(p);
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index)
    {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem(), copy = original.copy();
        if (index == 0)
        {
            if (!moveItemStackTo(original, 1, slots.size(), true)) return ItemStack.EMPTY;
        }
        else if (!moveItemStackTo(original, 0, 1, false)) return ItemStack.EMPTY;
        if (original.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    public int gas()
    {
        return data.get(0);
    }

    public int capacity()
    {
        return data.get(1);
    }

    public int burn()
    {
        return data.get(2);
    }

    public int burnTotal()
    {
        return data.get(3);
    }

    public boolean isLit()
    {
        return data.get(4) != 0;
    }

    public float litProgress()
    {
        return burnTotal() <= 0 ? 0.0F : (float) burn() / burnTotal();
    }

    public float burnProgress()
    {
        return burnTotal() <= 0 ? 0.0F : (float) (burnTotal() - burn()) / burnTotal();
    }
}
