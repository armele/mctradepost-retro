package com.deathfrog.mctradepost.api.tileentities;

import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;
import com.deathfrog.mctradepost.core.blocks.blockentity.MooringBayBlockEntity;
import com.deathfrog.mctradepost.core.blocks.blockentity.GasifierBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class MCTradePostTileEntities
{
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MooringBayBlockEntity>> MOORING_BAY;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<GasifierBlockEntity>> GASIFIER;
    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<PetWorkingBlockEntity>> PET_WORK_LOCATION;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> RESORT;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> RECYCLING;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> MARKETPLACE;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> STATION;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> PET_SHOP;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> OUTPOST;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> BUILDING;


}
