package com.deathfrog.mctradepost.api.tileentities;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

public class MCTradePostTileEntities
{

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> RESORT;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> RECYCLING;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> MARKETPLACE;

    public static DeferredHolder<BlockEntityType<?>, BlockEntityType<MCTPTileEntityColonyBuilding>> BUILDING;
}