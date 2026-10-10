package com.deathfrog.mctradepost.core.blocks.blockentity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.inventory.GasifierMenu;
import com.deathfrog.mctradepost.core.blocks.BlockGasifier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import com.deathfrog.mctradepost.recipe.GasifierRecipe;

/** Burns furnace fuel to convert recipe-defined feedstocks into stored Lifting Gas. */
public class GasifierBlockEntity extends BlockEntity implements Container, MenuProvider, LiftingGasStorage
{
    private static final String BURN_REMAINING_NBT_KEY = "BurnRemaining";
    private static final String BURN_TOTAL_NBT_KEY = "BurnTotal";
    private static final String GAS_REMAINING_NBT_KEY = "GasRemaining";
    private static final String PROCESS_REMAINING_NBT_KEY = "ProcessRemaining";
    private static final String PROCESS_TOTAL_NBT_KEY = "ProcessTotal";
    private static final String DISPLAY_NAME_KEY = "container.mctradepost.gasifier";
    private static final int FAILED_TRANSFER_RETRY_TICKS = 20;
    @SuppressWarnings("null")
    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private int gas;
    private int burnRemaining;
    private int burnTotal;
    private int gasRemaining;
    private int processRemaining;
    private int processTotal;
    private int outputCursor;
    private int transferRetryCooldown;

    private final @Nonnull ContainerData data = new ContainerData()
    {
        @SuppressWarnings("null")
        @Override
        public int get(int i)
        {
            return switch (i)
            {
                case 0 -> gas;
                case 1 -> gasCapacity();
                case 2 -> burnRemaining;
                case 3 -> burnTotal;
                case 4 -> processRemaining;
                case 5 -> processTotal;
                default -> getBlockState().getValue(BlockGasifier.LIT) ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int value)
        {
            if (i == 0) setGasAmount(value);
            else if (i == 2) burnRemaining = value;
            else if (i == 3) burnTotal = value;
            else if (i == 4) processRemaining = value;
            else if (i == 5) processTotal = value;
        }

        @Override
        public int getCount()
        {
            return 7;
        }
    };

    public GasifierBlockEntity(BlockPos pos, BlockState state)
    {
        super(MCTradePostTileEntities.GASIFIER.get(), pos, state);
    }

    @Override
    public int gasAmount()
    {
        return gas;
    }

    @Override
    public int gasCapacity()
    {
        return MCTPConfig.gasifierGasCapacity.get();
    }

    @Override
    public void setGasAmount(int amount)
    {
        final boolean wasEmpty = gas <= 0;
        gas = Math.max(0, Math.min(amount, gasCapacity()));
        if (wasEmpty && gas > 0) transferRetryCooldown = 0;
        setChanged();
    }

    @SuppressWarnings("null")
    public static void serverTick(Level level, BlockPos pos, BlockState state, GasifierBlockEntity be)
    {
        be.pushGas(level, pos);
        boolean active = false;

        if (be.processRemaining <= 0)
        {
            final ItemStack feedstock = be.items.get(1);
            final GasifierRecipe recipe = be.findRecipe(feedstock);
            if (recipe != null && be.gasCapacity() - be.gas >= recipe.gasYield()
                && (be.burnRemaining > 0 || be.canBurn(be.items.getFirst())))
            {
                be.processRemaining = recipe.burnTime();
                be.processTotal = be.processRemaining;
                be.gasRemaining = recipe.gasYield();
                feedstock.shrink(1);
                be.setChanged();
            }
        }

        if (be.processRemaining > 0 && be.burnRemaining <= 0)
        {
            ItemStack fuel = be.items.getFirst();
            int duration = fuel.isEmpty() ? 0 : fuel.getBurnTime(null);
            if (duration > 0)
            {
                be.burnRemaining = duration;
                be.burnTotal = duration;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty() && !remainder.isEmpty()) be.items.set(0, remainder);
                be.setChanged();
            }
        }

        if (be.processRemaining > 0 && be.burnRemaining > 0)
        {
            final int perTick = (be.gasRemaining + be.processRemaining - 1) / be.processRemaining;
            if (be.gasCapacity() - be.gas >= perTick)
            {
                active = true;
                be.burnRemaining--;
                be.processRemaining--;
                be.gasRemaining -= perTick;
                be.setGasAmount(be.gas + perTick);
            }
        }

        if (state.getValue(BlockGasifier.LIT) != active)
            level.setBlock(pos, state.setValue(BlockGasifier.LIT, active), 3);
    }

    @SuppressWarnings("null")
    private boolean canBurn(ItemStack stack)
    {
        return !stack.isEmpty() && stack.getBurnTime(null) > 0;
    }

    /** Resolves the authoritative gasifying recipe for an input stack. */
    @SuppressWarnings("null")
    private @Nullable GasifierRecipe findRecipe(ItemStack stack)
    {
        if (stack.isEmpty() || level == null) return null;
        SingleRecipeInput input = new SingleRecipeInput(stack);
        return level.getRecipeManager()
            .getRecipeFor(MCTradePostMod.GASIFIER_RECIPE_TYPE.get(), input, level)
            .map(RecipeHolder::value)
            .orElse(null);
    }

    @SuppressWarnings("null")
    private void pushGas(Level level, BlockPos pos)
    {
        if (gas <= 0)
        {
            transferRetryCooldown = 0;
            return;
        }
        if (transferRetryCooldown > 0)
        {
            transferRetryCooldown--;
            return;
        }

        Direction[] directions = Direction.values();
        boolean transferred = false;
        for (int checked = 0; checked < directions.length && gas > 0; checked++)
        {
            Direction direction = directions[(outputCursor + checked) % directions.length];
            IFluidHandler target =
                level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(direction), direction.getOpposite());
            if (target == null) continue;
            int offered = Math.min(gas, MCTPConfig.gasifierTransferRate.get());
            int accepted = target.fill(new FluidStack(liftingGas(), offered), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0)
            {
                transferred = true;
                drainGas(accepted, false);
            }
        }
        outputCursor = (outputCursor + 1) % directions.length;
        if (!transferred) transferRetryCooldown = FAILED_TRANSFER_RETRY_TICKS;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt(LIFTING_GAS_NBT_KEY, gas);
        tag.putInt(BURN_REMAINING_NBT_KEY, burnRemaining);
        tag.putInt(BURN_TOTAL_NBT_KEY, burnTotal);
        tag.putInt(GAS_REMAINING_NBT_KEY, gasRemaining);
        tag.putInt(PROCESS_REMAINING_NBT_KEY, processRemaining);
        tag.putInt(PROCESS_TOTAL_NBT_KEY, processTotal);
    }

    @SuppressWarnings("null")
    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        gas = Math.max(0, Math.min(tag.getInt(LIFTING_GAS_NBT_KEY), gasCapacity()));
        burnRemaining = tag.getInt(BURN_REMAINING_NBT_KEY);
        burnTotal = tag.getInt(BURN_TOTAL_NBT_KEY);
        gasRemaining = tag.getInt(GAS_REMAINING_NBT_KEY);
        processRemaining = tag.getInt(PROCESS_REMAINING_NBT_KEY);
        processTotal = tag.getInt(PROCESS_TOTAL_NBT_KEY);
        // Continue legacy in-flight gas batches rather than silently losing their yield.
        if (!tag.contains(PROCESS_REMAINING_NBT_KEY) && gasRemaining > 0 && burnRemaining > 0)
        {
            processRemaining = burnRemaining;
            processTotal = Math.max(processRemaining, burnTotal);
        }
    }

    @Override
    public int getContainerSize()
    {
        return 2;
    }

    @Override
    public boolean isEmpty()
    {
        return items.getFirst().isEmpty() && items.get(1).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return items.get(slot);
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @SuppressWarnings("null")
    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @Nonnull ItemStack stack)
    {
        items.set(slot, stack);
        setChanged();
    }

    @SuppressWarnings("null")
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack)
    {
        return slot == 0 ? stack.getBurnTime(null) > 0 : slot == 1 && findRecipe(stack) != null;
    }

    @Override
    public boolean stillValid(@Nonnull Player player)
    {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        items.clear();
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
        return new GasifierMenu(id, inventory, this, data);
    }
}
