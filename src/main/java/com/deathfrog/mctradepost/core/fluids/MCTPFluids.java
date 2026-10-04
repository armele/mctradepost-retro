package com.deathfrog.mctradepost.core.fluids;

import com.deathfrog.mctradepost.MCTradePostMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Native automation fluid used to fuel trade airships. It deliberately has no world block. */
public final class MCTPFluids
{
    private static final String LIFTING_GAS_ID = "lifting_gas";
    private static final String FLOWING_LIFTING_GAS_ID = "flowing_lifting_gas";

    @SuppressWarnings("null")
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MCTradePostMod.MODID);
    
    @SuppressWarnings("null")
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MCTradePostMod.MODID);

    @SuppressWarnings("null")
    public static final DeferredHolder<FluidType, FluidType> LIFTING_GAS_TYPE =
        FLUID_TYPES.register(LIFTING_GAS_ID, () -> new FluidType(FluidType.Properties.create().density(-100).viscosity(100))
        {});
        
    @SuppressWarnings("null")
    public static final DeferredHolder<Fluid, FlowingFluid> LIFTING_GAS =
        FLUIDS.register(LIFTING_GAS_ID, () -> new BaseFlowingFluid.Source(properties()));
        
    @SuppressWarnings("null")
    public static final DeferredHolder<Fluid, FlowingFluid> FLOWING_LIFTING_GAS =
        FLUIDS.register(FLOWING_LIFTING_GAS_ID, () -> new BaseFlowingFluid.Flowing(properties()));

    @SuppressWarnings("null")
    private static BaseFlowingFluid.Properties properties()
    {
        return new BaseFlowingFluid.Properties(LIFTING_GAS_TYPE, LIFTING_GAS, FLOWING_LIFTING_GAS).bucket(MCTradePostMod.LIFTING_GAS_BUCKET);
    }

    private MCTPFluids()
    {}
}
