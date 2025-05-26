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
    }
}
