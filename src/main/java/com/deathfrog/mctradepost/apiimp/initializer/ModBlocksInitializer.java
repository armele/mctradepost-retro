package com.deathfrog.mctradepost.apiimp.initializer;

import com.deathfrog.mctradepost.MCTradePostMod;
import com.minecolonies.api.blocks.ModBlocks;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModBlocksInitializer
{
    public final static String THATCH_NAME = "thatch";
    public final static String THATCH_STAIRS_NAME = "thatch_stairs";
    public final static String THATCH_WALL_NAME = "thatch_wall";
    public final static String THATCH_SLAB_NAME = "thatch_slab";
    public final static String PLASTER_NAME = "plaster";
    public final static String PLASTER_STAIRS_NAME = "plaster_stairs";
    public final static String PLASTER_WALL_NAME = "plaster_wall";
    public final static String PLASTER_SLAB_NAME = "plaster_slab";
    public final static String ROUGH_BRICK_NAME = "rough_brick";
    public final static String ROUGH_BRICK_STAIRS_NAME = "rough_brick_stairs";
    public final static String ROUGH_BRICK_WALL_NAME = "rough_brick_wall";
    public final static String ROUGH_BRICK_SLAB_NAME = "rough_brick_slab";
    public final static String ROUGH_STONE_NAME = "rough_stone";
    public final static String ROUGH_STONE_STAIRS_NAME = "rough_stone_stairs";
    public final static String ROUGH_STONE_WALL_NAME = "rough_stone_wall";
    public final static String ROUGH_STONE_SLAB_NAME = "rough_stone_slab";
	public final static String ENDETHYST_NAME = "endethyst";
    public final static String ENDETHYST_STAIRS_NAME = "endethyst_stairs";
    public final static String ENDETHYST_WALL_NAME = "endethyst_wall";
    public final static String ENDETHYST_SLAB_NAME = "endethyst_slab";
	public final static String ENDETHYST_BRICK_NAME = "endethyst_brick";
    public final static String ENDETHYST_BRICK_STAIRS_NAME = "endethyst_brick_stairs";
    public final static String ENDETHYST_BRICK_WALL_NAME = "endethyst_brick_wall";
    public final static String ENDETHYST_BRICK_SLAB_NAME = "endethyst_brick_slab";
	public final static String ENDMARINE_NAME = "endmarine";
    public final static String ENDMARINE_STAIRS_NAME = "endmarine_stairs";
    public final static String ENDMARINE_WALL_NAME = "endmarine_wall";
    public final static String ENDMARINE_SLAB_NAME = "endmarine_slab";
	public final static String ENDMARINE_BRICK_NAME = "endmarine_brick";
    public final static String ENDMARINE_BRICK_STAIRS_NAME = "endmarine_brick_stairs";
    public final static String ENDMARINE_BRICK_WALL_NAME = "endmarine_brick_wall";
    public final static String ENDMARINE_BRICK_SLAB_NAME = "endmarine_brick_slab";
	public final static String MARINE_LAPIS_NAME = "marine_lapis";
    public final static String MARINE_LAPIS_STAIRS_NAME = "marine_lapis_stairs";
    public final static String MARINE_LAPIS_WALL_NAME = "marine_lapis_wall";
    public final static String MARINE_LAPIS_SLAB_NAME = "marine_lapis_slab";
	public final static String MARINE_LAPIS_BRICK_NAME = "marine_lapis_brick";
    public final static String MARINE_LAPIS_BRICK_STAIRS_NAME = "marine_lapis_brick_stairs";
    public final static String MARINE_LAPIS_BRICK_WALL_NAME = "marine_lapis_brick_wall";
    public final static String MARINE_LAPIS_BRICK_SLAB_NAME = "marine_lapis_brick_slab";
    public final static String IVY_BRICK_NAME = "ivy_brick";
	public final static String WEATHERED_ROUGH_STONE_NAME = "weathered_rough_stone";
    public final static String WEATHERED_ROUGH_STONE_STAIRS_NAME = "weathered_rough_stone_stairs";
    public final static String WEATHERED_ROUGH_STONE_WALL_NAME = "weathered_rough_stone_wall";
    public final static String WEATHERED_ROUGH_STONE_SLAB_NAME = "weathered_rough_stone_slab";
	public final static String MARINE_BASALT_NAME = "marine_basalt";
    public final static String MARINE_BASALT_STAIRS_NAME = "marine_basalt_stairs";
    public final static String MARINE_BASALT_WALL_NAME = "marine_basalt_wall";
    public final static String MARINE_BASALT_SLAB_NAME = "marine_basalt_slab";
	public final static String WOVEN_KELP_NAME = "woven_kelp";
    public final static String WOVEN_KELP_STAIRS_NAME = "woven_kelp_stairs";
    public final static String WOVEN_KELP_WALL_NAME = "woven_kelp_wall";
    public final static String WOVEN_KELP_SLAB_NAME = "woven_kelp_slab";

    // Pet Working Blocks
    public final static String TROUGH_NAME = "trough";
    public final static String SCAVENGE_NAME = "scavenge";

    private ModBlocksInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBlockInitializer but this is a Utility class.");
    }

    @SubscribeEvent
    public static void registerBlocks(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(Registries.BLOCK))
        {
            ModBlocksInitializer.init(event.getRegistry(Registries.BLOCK));
        }
    }

    /**
     * Initializes {@link ModBlocks} with the block instances.
     *
     * @param registry The registry to register the new blocks.
     */
    public static void init(final Registry<Block> registry)
    {
        // Note that hut blocks are already successfully registered in MCTradePostMod.
        // We are deliberately not following the Minecolonies approach here. (Is it right? Is it wrong? We will let the functionality of the code determine that.)
    }

    @SubscribeEvent
    public static void registerItems(RegisterEvent event)
    {
        if (event.getRegistryKey().equals(Registries.ITEM))
        {
            ModBlocksInitializer.registerBlockItem(event.getRegistry(Registries.ITEM));
        }
    }

    /**
     * Initializes the registry with the relevant item produced by the relevant blocks.
     *
     * @param registry The item registry to add the items too.
     */
    public static void registerBlockItem(final Registry<Item> registry)
    {
        MCTradePostMod.blockHutMarketplace.get().registerBlockItem(registry, new Item.Properties());
        MCTradePostMod.blockHutResort.get().registerBlockItem(registry, new Item.Properties());
        MCTradePostMod.blockHutRecycling.get().registerBlockItem(registry, new Item.Properties());
        MCTradePostMod.blockHutStation.get().registerBlockItem(registry, new Item.Properties());
        MCTradePostMod.blockHutPetShop.get().registerBlockItem(registry, new Item.Properties());
    }
}
