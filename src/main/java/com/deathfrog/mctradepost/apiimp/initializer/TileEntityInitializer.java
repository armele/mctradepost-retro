package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.tileentities.MCTPTileEntityColonyBuilding;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MCTradePostMod.MODID);

    static
    {

        MCTradePostTileEntities.BUILDING = BLOCK_ENTITIES.register("mctp_colonybuilding", () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(null));

    }
}