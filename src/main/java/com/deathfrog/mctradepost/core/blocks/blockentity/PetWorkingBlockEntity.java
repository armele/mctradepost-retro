package com.deathfrog.mctradepost.core.blocks.blockentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PetWorkingBlockEntity extends RandomizableContainerBlockEntity
{

    private Component customName;

    public static final int SLOT_COUNT = 27;
    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    public PetWorkingBlockEntity(BlockPos pos, BlockState state)
    {
        super(MCTradePostTileEntities.PET_WORK_LOCATION.get(), pos, state);
    }

    @Override
    protected NonNullList<ItemStack> getItems()
    {
        return items;
    }

    @Override
    protected void setItems(@Nonnull NonNullList<ItemStack> items)
    {
        this.items = items;
    }

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    public void setCustomName(Component name)
    {
        this.customName = name;
    }

    @Nullable
    public Component getCustomName()
    {
        return this.customName;
    }

    public boolean hasCustomName()
    {
        return this.customName != null;
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        if (this.hasCustomName())
        {
            tag.putString("CustomName", Component.Serializer.toJson(this.customName, registries));
        }
    }

    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        if (tag.contains("CustomName"))
        {
            this.customName = Component.Serializer.fromJson(tag.getString("CustomName"), registries);
        }
    }

    @Override
    protected AbstractContainerMenu createMenu(int windowId, @Nonnull Inventory inv)
    {
        return ChestMenu.threeRows(windowId, inv, this);
    }

    @Override
    public Component getDefaultName()
    {
        return this.hasCustomName() ? this.getCustomName() : Component.literal(this.getBlockPos().toShortString());
    }

    /**
     * Called when the block entity is loaded to ensure builder-placed animal working blocks are recognized
     * and registered as working locations.
     */
    @Override
    public void onLoad() {
        super.onLoad();

        if (this.getLevel() == null) {
            return;
        }

        if (!this.getLevel().isClientSide) {
            IColony colony = IColonyManager.getInstance().getClosestColony(this.getLevel(), this.getBlockPos());
            if (colony != null) {
                PetRegistryUtil.registerWorkLocation(colony, this.getBlockPos());
            }
        }
    }

}
