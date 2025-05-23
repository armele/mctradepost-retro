package com.deathfrog.mctradepost.apiimp.initializer;

import java.util.Arrays;

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

    }

    public static void initializeTileEntities() {
        MCTradePostMod.LOGGER.info("Registering with huts: {}", Arrays.toString(ModBuildings.getHuts()));

        // Uncertain exactly what this does... modeled on Minecolonies code. Removing it breaks things!  Expect it has to do with ensuring the ResourceLocator uses our mod ID when needed.
        MCTradePostTileEntities.BUILDING = BLOCK_ENTITIES.register("mctp_colonybuilding", () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(null)); 

        // Uncertain if these are used, but again modelling on Minecolonies and setting them up for completeness.
        MCTradePostTileEntities.MARKETPLACE = BLOCK_ENTITIES.register(ModBuildings.MARKETPLACE_ID, () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(null));  
        MCTradePostTileEntities.RESORT = BLOCK_ENTITIES.register(ModBuildings.RESORT_ID, () -> BlockEntityType.Builder.of(MCTPTileEntityColonyBuilding::new, ModBuildings.getHuts()).build(null));  
    }
}