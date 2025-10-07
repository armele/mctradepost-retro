package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.deathfrog.mctradepost.api.tileentities.MCTPTileEntityColonyBuilding;
import com.deathfrog.mctradepost.api.tileentities.MCTradePostTileEntities;
import com.deathfrog.mctradepost.core.blocks.AbstractBlockPetWorkingLocation;
import com.deathfrog.mctradepost.core.blocks.blockentity.PetWorkingBlockEntity;

import net.minecraft.core.registries.Registries;
import com.mojang.datafixers.DSL;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;


public class TileEntityInitializer
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MCTradePostMod.MODID);

    static
    {
        MCTradePostTileEntities.PET_WORK_LOCATION = BLOCK_ENTITIES.register("pet_work_location", 
            () -> BlockEntityType.Builder.of(PetWorkingBlockEntity::new, AbstractBlockPetWorkingLocation.getPetWorkBlocks()).build(DSL.remainderType()));

        MCTradePostTileEntities.BUILDING = BLOCK_ENTITIES.register("mctp_colonybuilding", 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType())); 

        MCTradePostTileEntities.MARKETPLACE = BLOCK_ENTITIES.register(ModBuildings.MARKETPLACE_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  

        MCTradePostTileEntities.RESORT = BLOCK_ENTITIES.register(ModBuildings.RESORT_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  

        MCTradePostTileEntities.RECYCLING = BLOCK_ENTITIES.register(ModBuildings.RECYCLING_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  

        MCTradePostTileEntities.STATION = BLOCK_ENTITIES.register(ModBuildings.STATION_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType())); 

        MCTradePostTileEntities.PET_SHOP = BLOCK_ENTITIES.register(ModBuildings.PETSHOP_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  

        MCTradePostTileEntities.OUTPOST = BLOCK_ENTITIES.register(ModBuildings.OUTPOST_ID, 
            () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(DSL.remainderType()));  
 
            
    }
}