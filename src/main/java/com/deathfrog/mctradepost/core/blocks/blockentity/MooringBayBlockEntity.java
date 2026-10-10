package com.deathfrog.mctradepost.core.blocks.blockentity;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.inventory.MooringBayMenu;
import com.deathfrog.mctradepost.core.entity.ai.workers.trade.DimPos;
import com.deathfrog.mctradepost.item.RouteSurveyItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores Lifting Gas and up to five directional Route Surveys for a Mooring Bay. */
public class MooringBayBlockEntity extends BlockEntity implements Container, MenuProvider, LiftingGasStorage
{
    public static final int SURVEY_SLOTS = 5;
    public static final int BUCKET_VOLUME = 1000;
    private static final String DISPLAY_NAME_KEY = "container.mctradepost.mooring_bay";

    @SuppressWarnings("null")
    private NonNullList<ItemStack> surveys = NonNullList.withSize(SURVEY_SLOTS, ItemStack.EMPTY);
    private int gas;

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case 0 -> gas & 0xFFFF;
                case 1 -> gas >>> 16;
                case 2 -> gasCapacity() & 0xFFFF;
                case 3 -> gasCapacity() >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value)
        {
            if (index == 0) setGasAmount((gas & 0xFFFF0000) | (value & 0xFFFF));
            else if (index == 1) setGasAmount((gas & 0xFFFF) | ((value & 0xFFFF) << 16));
        }

        @Override
        public int getCount()
        {
            return 4;
        }
    };

    public MooringBayBlockEntity(BlockPos pos, BlockState state)
    {
        super(MCTradePostTileEntities.MOORING_BAY.get(), pos, state);
    }

    @Override
    public int gasAmount()
    {
        return gas;
    }

    @Override
    public int gasCapacity()
    {
        return MCTPConfig.mooringBayGasCapacity.get();
    }

    @Override
    public void setGasAmount(int amount)
    {
        gas = Math.max(0, Math.min(amount, gasCapacity()));
        setChanged();
    }

    @SuppressWarnings("null")
    public boolean authorizes(BlockPos destination)
    {
        if (level == null) return false;
        DimPos origin = new DimPos(level.dimension(), worldPosition);
        DimPos target = new DimPos(level.dimension(), destination);
        return surveys.stream().anyMatch(stack -> RouteSurveyItem.authorizes(stack, origin, target));
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, surveys, registries);
        tag.putInt(LIFTING_GAS_NBT_KEY, gas);
    }

    @SuppressWarnings("null")
    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        surveys = NonNullList.withSize(SURVEY_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, surveys, registries);
        gas = Math.max(0, Math.min(tag.getInt(LIFTING_GAS_NBT_KEY), gasCapacity()));
    }

    @Override
    public int getContainerSize()
    {
        return SURVEY_SLOTS;
    }

    @SuppressWarnings("null")
    @Override
    public boolean isEmpty()
    {
        return surveys.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return surveys.get(slot);
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack result = ContainerHelper.removeItem(surveys, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(surveys, slot);
    }

    @Override
    public void setItem(int slot, @Nonnull ItemStack stack)
    {
        surveys.set(slot, stack.copyWithCount(Math.min(1, stack.getCount())));
        setChanged();
    }

    @SuppressWarnings("null")
    @Override
    public boolean canPlaceItem(int slot, @Nonnull ItemStack stack)
    {
        if (!stack.is(MCTradePostMod.ROUTE_SURVEY.get()) || !RouteSurveyItem.getRecord(stack).isComplete())
            return false;
        Optional<DimPos> origin = RouteSurveyItem.getRecord(stack).origin();
        return level != null && origin.isPresent() &&
            origin.get().dimension().equals(level.dimension()) &&
            origin.get().pos().equals(worldPosition) &&
            surveys.stream()
                .noneMatch(
                    existing -> !existing.isEmpty() && RouteSurveyItem.getRecord(existing).equals(RouteSurveyItem.getRecord(stack)));
    }

    @Override
    public int getMaxStackSize()
    {
        return 1;
    }

    @Override
    public boolean stillValid(@Nonnull Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        surveys.clear();
        setChanged();
    }

    @Override
    public Component getDisplayName()
    {
        return Component.translatable(DISPLAY_NAME_KEY);
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @Nonnull Inventory inventory, @Nonnull Player player)
    {
        return new MooringBayMenu(id, inventory, this, data);
    }
}
