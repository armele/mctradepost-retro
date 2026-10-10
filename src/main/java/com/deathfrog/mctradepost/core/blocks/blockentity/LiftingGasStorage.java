package com.deathfrog.mctradepost.core.blocks.blockentity;

import javax.annotation.Nonnull;

import com.deathfrog.mctradepost.core.fluids.MCTPFluids;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Shared one-tank Lifting Gas storage and NeoForge automation contract. */
public interface LiftingGasStorage extends IFluidHandler
{
    /** Block-entity data key used to persist stored Lifting Gas. */
    String LIFTING_GAS_NBT_KEY = "LiftingGas";

    /** Translation key shared by Lifting Gas storage screens. */
    String LIFTING_GAS_DISPLAY_KEY = "container.mctradepost.lifting_gas";

    int gasAmount();

    int gasCapacity();

    void setGasAmount(int amount);

    default int fillGas(int amount, boolean simulate)
    {
        int accepted = Math.min(Math.max(0, amount), gasCapacity() - gasAmount());
        if (!simulate && accepted > 0) setGasAmount(gasAmount() + accepted);
        return accepted;
    }

    default int drainGas(int amount, boolean simulate)
    {
        int drained = Math.min(Math.max(0, amount), gasAmount());
        if (!simulate && drained > 0) setGasAmount(gasAmount() - drained);
        return drained;
    }

    @Override
    default int getTanks()
    {
        return 1;
    }

    @SuppressWarnings("null")
    @Override
    default FluidStack getFluidInTank(int tank)
    {
        return tank == 0 ? new FluidStack(MCTPFluids.LIFTING_GAS.get(), gasAmount()) : FluidStack.EMPTY;
    }

    @Override
    default int getTankCapacity(int tank)
    {
        return tank == 0 ? gasCapacity() : 0;
    }

    @SuppressWarnings("null")
    @Override
    default boolean isFluidValid(int tank, @Nonnull FluidStack stack)
    {
        return tank == 0 && stack.is(MCTPFluids.LIFTING_GAS.get());
    }

    @Override
    default int fill(@Nonnull FluidStack resource, @Nonnull FluidAction action)
    {
        return isFluidValid(0, resource) ? fillGas(resource.getAmount(), action.simulate()) : 0;
    }

    @SuppressWarnings("null")
    @Override
    default FluidStack drain(@Nonnull FluidStack resource, @Nonnull FluidAction action)
    {
        return isFluidValid(0, resource) ?
            new FluidStack(MCTPFluids.LIFTING_GAS.get(), drainGas(resource.getAmount(), action.simulate())) :
            FluidStack.EMPTY;
    }

    @SuppressWarnings("null")
    @Override
    default FluidStack drain(int maxDrain, @Nonnull FluidAction action)
    {
        return new FluidStack(MCTPFluids.LIFTING_GAS.get(), drainGas(maxDrain, action.simulate()));
    }

    default Fluid liftingGas()
    {
        return MCTPFluids.LIFTING_GAS.get();
    }
}
