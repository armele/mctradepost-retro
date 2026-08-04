package com.deathfrog.mctradepost.core.inventory;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Three-row work inventory plus a research-gated, one-item forage focus slot. */
public class PetWorkingMenu extends AbstractContainerMenu
{
    public static final int FOCUS_SLOT = 27;
    private final Container workInventory;
    private final Container focusInventory;
    private final PetWorkingBlockEntity working;
    private final DataSlot focusedResearch = DataSlot.standalone();

    /**
     * Creates the client-side menu and resolves its working block from the
     * position supplied in the opening packet.
     *
     * @param id synchronized container id
     * @param playerInventory viewing player's inventory
     * @param data menu-opening payload containing the working-block position
     */
    public PetWorkingMenu(final int id, final @Nonnull Inventory playerInventory, final RegistryFriendlyByteBuf data)
    {
        this(id, playerInventory, resolve(playerInventory, data));
    }

    /**
     * Resolves the menu's block entity from its network payload.
     *
     * @param inventory viewing player's inventory and level context
     * @param data payload containing the working-block position
     * @return resolved working block, or {@code null} if it is unavailable
     */
    private static PetWorkingBlockEntity resolve(final Inventory inventory, final RegistryFriendlyByteBuf data)
    {
        final BlockPos dataPos = data.readBlockPos();
        
        if (dataPos == null) return null;

        if (inventory.player.level().getBlockEntity(dataPos) instanceof PetWorkingBlockEntity working) return working;
        return null;
    }

    /**
     * Creates the authoritative server-side menu for a working block.
     *
     * @param id synchronized container id
     * @param playerInventory viewing player's inventory
     * @param working working block whose inventories are exposed
     */
    @SuppressWarnings("null")
    public PetWorkingMenu(final int id, final @Nonnull Inventory playerInventory, final PetWorkingBlockEntity working)
    {
        super(MCTradePostMod.PET_WORKING_MENU.get(), id);
        this.workInventory = working == null ? new SimpleContainer(27) : working;
        this.working = working;
        this.focusInventory = working == null ? new SimpleContainer(1) : working.getFocusContainer();
        this.focusedResearch.set(working != null && working.isFocusedForagingEnabled() ? 1 : 0);
        addDataSlot(focusedResearch);

        checkContainerSize(workInventory, 27);
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(workInventory, column + row * 9, 8 + column * 18, 18 + row * 18));

        addSlot(new Slot(focusInventory, 0, 184, 18)
        {
            @Override public boolean mayPlace(@Nonnull ItemStack stack) {
                return isFocusEnabled() && (PetWorkingMenu.this.working == null || PetWorkingMenu.this.working.isValidFocusItem(stack));
            }
            @Override public boolean mayPickup(@Nonnull Player player) { return isFocusEnabled(); }
            @Override public int getMaxStackSize() { return 1; }
        });

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
    }

    /**
     * Reports whether the synchronized research gate permits focus-slot use.
     *
     * @return {@code true} when focused foraging is unlocked
     */
    public boolean isFocusEnabled()
    {
        return focusedResearch.get() > 0;
    }

    /**
     * Returns the stack displayed in the dedicated focus slot.
     *
     * @return configured reference item, or an empty stack
     */
    public ItemStack focusStack()
    {
        return focusInventory.getItem(0);
    }

    /** Returns the position whose forage search volume is represented by this menu. */
    public BlockPos workingPosition()
    {
        return working == null ? null : working.getBlockPos();
    }

    /**
     * Checks whether the viewing player may continue using the working block.
     *
     * @param player viewing player
     * @return {@code true} while the underlying container remains usable
     */
    @Override
    public boolean stillValid(@Nonnull Player player)
    {
        return workInventory.stillValid(player);
    }

    /**
     * Handles shift-click transfer among the work inventory, protected focus
     * slot, and player inventory.
     *
     * @param player player performing the transfer
     * @param index source menu-slot index
     * @return copy of the transferred stack, or an empty stack on failure
     */
    @Override
    public ItemStack quickMoveStack(@Nonnull Player player, int index)
    {
        final Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        final ItemStack original = slot.getItem();
        final ItemStack copy = original.copy();

        if (index < 27)
        {
            if (!moveItemStackTo(original, 28, 64, true)) return ItemStack.EMPTY;
        }
        else if (index == FOCUS_SLOT)
        {
            if (!isFocusEnabled() || !moveItemStackTo(original, 28, 64, true)) return ItemStack.EMPTY;
        }
        else
        {
            if (isFocusEnabled() && focusStack().isEmpty() && (working == null || working.isValidFocusItem(original)))
            {
                final ItemStack one = original.copyWithCount(1);

                if (one != null) 
                {
                    focusInventory.setItem(0, one);
                    original.shrink(1);
                }
            }
            else if (!moveItemStackTo(original, 0, 27, false)) return ItemStack.EMPTY;
        }

        if (original.isEmpty()) slot.setByPlayer(NullnessBridge.assumeNonnull(ItemStack.EMPTY));
        else slot.setChanged();
        return copy;
    }
}
