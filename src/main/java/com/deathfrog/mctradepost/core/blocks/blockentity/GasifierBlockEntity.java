package com.deathfrog.mctradepost.core.blocks.blockentity;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.deathfrog.mctradepost.MCTPConfig;
import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.inventory.GasifierMenu;
import com.deathfrog.mctradepost.core.blocks.BlockGasifier;
import com.deathfrog.mctradepost.recipe.GasifierRecipe;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Converts furnace fuels or configured gasifying recipes into stored Lifting Gas. */
public class GasifierBlockEntity extends BlockEntity implements Container, MenuProvider, LiftingGasStorage
{
    private static final String BURN_REMAINING_NBT_KEY = "BurnRemaining";
    private static final String BURN_TOTAL_NBT_KEY = "BurnTotal";
    private static final String GAS_REMAINING_NBT_KEY = "GasRemaining";
    private static final String DISPLAY_NAME_KEY = "container.mctradepost.gasifier";

    @SuppressWarnings("null")
    private NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private int gas;
    private int burnRemaining;
    private int burnTotal;
    private int gasRemaining;
    private int outputCursor;

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
                default -> getBlockState().getValue(BlockGasifier.LIT) ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int value)
        {
            if (i == 0) setGasAmount(value);
            else if (i == 2) burnRemaining = value;
            else if (i == 3) burnTotal = value;
        }

        @Override
        public int getCount()
        {
            return 5;
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
        gas = Math.max(0, Math.min(amount, gasCapacity()));
        setChanged();
    }

    @SuppressWarnings("null")
    public static void serverTick(Level level, BlockPos pos, BlockState state, GasifierBlockEntity be)
    {
        be.pushGas(level, pos);
        int perTick = be.burnRemaining <= 0 ? 0 : (be.gasRemaining + be.burnRemaining - 1) / be.burnRemaining;
        boolean active = false;

        if (be.burnRemaining > 0 && be.gasCapacity() - be.gas >= perTick)
        {
            active = true;
            be.burnRemaining--;
            be.gasRemaining -= perTick;
            be.setGasAmount(be.gas + perTick);
        }

        if (be.burnRemaining <= 0 && be.gas < be.gasCapacity())
        {
            ItemStack fuel = be.items.getFirst();
            int duration = 0;
            int yield = 0;
            if (!fuel.isEmpty())
            {
                Optional<RecipeHolder<GasifierRecipe>> override = level.getRecipeManager()
                    .getRecipeFor(MCTradePostMod.GASIFIER_RECIPE_TYPE.get(),
                        new net.minecraft.world.item.crafting.SingleRecipeInput(fuel),
                        level);
                        
                if (override.isPresent())
                {
                    duration = Math.max(1, override.get().value().burnTime());
                    yield = Math.max(1, override.get().value().gasYield());
                }
                else
                {
                    duration = fuel.getBurnTime(null);
                    yield = duration * MCTPConfig.gasPerBurnTick.get();
                }
            }
            if (duration > 0)
            {
                active = true;
                be.burnRemaining = duration;
                be.burnTotal = duration;
                be.gasRemaining = yield;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty() && !remainder.isEmpty()) be.items.set(0, remainder);
                be.setChanged();
            }
        }

        if (state.getValue(BlockGasifier.LIT) != active)
            level.setBlock(pos, state.setValue(BlockGasifier.LIT, active), 3);
    }

    @SuppressWarnings("null")
    private void pushGas(Level level, BlockPos pos)
    {
        if (gas <= 0) return;
        Direction[] directions = Direction.values();
        for (int checked = 0; checked < directions.length && gas > 0; checked++)
        {
            Direction direction = directions[(outputCursor + checked) % directions.length];
            IFluidHandler target =
                level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(direction), direction.getOpposite());
            if (target == null) continue;
            int offered = Math.min(gas, MCTPConfig.gasifierTransferRate.get());
            int accepted = target.fill(new FluidStack(liftingGas(), offered), IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) drainGas(accepted, false);
        }
        outputCursor = (outputCursor + 1) % directions.length;
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
    }

    @SuppressWarnings("null")
    @Override
    public void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider registries)
    {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(1, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        gas = Math.max(0, Math.min(tag.getInt(LIFTING_GAS_NBT_KEY), gasCapacity()));
        burnRemaining = tag.getInt(BURN_REMAINING_NBT_KEY);
        burnTotal = tag.getInt(BURN_TOTAL_NBT_KEY);
        gasRemaining = tag.getInt(GAS_REMAINING_NBT_KEY);
    }

    @Override
    public int getContainerSize()
    {
        return 1;
    }

    @Override
    public boolean isEmpty()
    {
        return items.getFirst().isEmpty();
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
        if (stack.getBurnTime(null) > 0)
            return true;
        return level != null && level.getRecipeManager()
            .getRecipeFor(MCTradePostMod.GASIFIER_RECIPE_TYPE.get(),
                new net.minecraft.world.item.crafting.SingleRecipeInput(stack),
                level)
            .isPresent();
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
