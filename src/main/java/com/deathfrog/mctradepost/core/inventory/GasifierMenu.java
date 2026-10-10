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
        this(id, inventory, new SimpleContainer(2), new SimpleContainerData(7));
    }

    @SuppressWarnings("null")
    public GasifierMenu(int id, Inventory inv, @Nonnull Container machine, @Nonnull ContainerData data)
    {
        super(MCTradePostMod.GASIFIER_MENU.get(), id);
        this.machine = machine;
        this.data = data;
        checkContainerSize(machine, 2);
        checkContainerDataCount(data, 7);
        machine.startOpen(inv.player);
        addSlot(new Slot(machine, 0, 56, 53)
        {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack)
            {
                return machine.canPlaceItem(0, stack);
            }
        });
        addSlot(new Slot(machine, 1, 56, 17)
        {
            @Override
            public boolean mayPlace(@Nonnull ItemStack stack)
            {
                return machine.canPlaceItem(1, stack);
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
        if (index < 2)
        {
            if (!moveItemStackTo(original, 2, slots.size(), true)) return ItemStack.EMPTY;
        }
        else
        {
            final int target = machine.canPlaceItem(1, original) ? 1 : 0;
            if (!machine.canPlaceItem(target, original) || !moveItemStackTo(original, target, target + 1, false)) return ItemStack.EMPTY;
        }
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
        return data.get(6) != 0;
    }

    public float litProgress()
    {
        return burnTotal() <= 0 ? 0.0F : (float) burn() / burnTotal();
    }

    public float burnProgress()
    {
        return data.get(5) <= 0 ? 0.0F : (float) (data.get(5) - data.get(4)) / data.get(5);
    }
}
