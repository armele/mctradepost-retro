package com.deathfrog.mctradepost;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.sounds.ModSoundEvents;
import com.deathfrog.mctradepost.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModModelTypeInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutMarketplace;
import com.deathfrog.mctradepost.core.blocks.huts.MCTPBaseBlockHut;
import com.deathfrog.mctradepost.core.client.model.FemaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.model.MaleShopkeeperModel;
import com.deathfrog.mctradepost.core.client.render.AdvancedClipBoardDecorator;
import com.deathfrog.mctradepost.core.colony.jobs.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.event.ClientRegistryHandler;
import com.deathfrog.mctradepost.item.AdvancedClipboardItem;
import com.deathfrog.mctradepost.network.ItemValuePacket;
import com.minecolonies.api.blocks.ModBlocks;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
/*
 */

// TODO: To register a sound in Minecraft, you'll need to add a sound event to the game's sounds.json file. You can do this by adding your custom sound events within the assets/minecraft/sounds.json file of your resource pack or mod. You'll also need to place the audio file (usually in .ogg format) in the correct directory (e.g., resources/assets/mod-id/sounds). 
/* MISSING SOUNDS:
[18May2025 16:38:30.044] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.general
[18May2025 16:38:30.044] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.general
[18May2025 16:38:30.044] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.general
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.noise
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.gotobed
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.badweather
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.lowsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.highsaturation
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.badhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.goodhousing
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.greeting
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.farewell
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.missingequipment
[18May2025 16:38:30.045] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.happy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.unhappy
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.sick
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.interaction
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.success
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male1.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female1.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male2.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female2.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male3.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female3.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.male4.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: mctradepost:citizen.shopkeeper.female4.danger
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.general
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.noise
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.gotobed
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.badweather
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.lowsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.highsaturation
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.badhousing
[18May2025 16:38:30.046] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.badhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.badhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.goodhousing
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.greeting
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.farewell
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.missingequipment
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.happy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.unhappy
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.sick
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.interaction
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.success
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male1.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female1.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male2.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female2.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male3.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female3.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.male4.danger
[18May2025 16:38:30.047] [Render thread/WARN] [net.minecraft.client.sounds.SoundEngine/]: Missing sound for event: minecolonies:citizen.shopkeeper.female4.danger
 * 
 */
@Mod(MCTradePostMod.MODID)
public class MCTradePostMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "mctradepost";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Create a Deferred Register to hold Blocks which will all be registered under the "mctradepost" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    // Create a Deferred Register to hold Items which will all be registered under the "mctradepost" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<AdvancedClipboardItem> ADVANCED_CLIPBOARD = ITEMS.register("advanced_clipboard",
        () -> new AdvancedClipboardItem(new Item.Properties().stacksTo(1)));
        

    public static MCTPBaseBlockHut blockHutMarketplace;

    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a new Block with the id "examplemod:example_block", combining the namespace and path
    // public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    
    // Creates a new BlockItem with the id "examplemod:example_block", combining the namespace and path
    // public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    // Creates a new food item with the id "examplemod:example_id", nutrition 1 and saturation 2
    // public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item", new Item.Properties().food(new FoodProperties.Builder().alwaysEdible().nutrition(1).saturationModifier(2f).build()));

    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    /* 
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.examplemod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(EXAMPLE_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());
    */

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public MCTradePostMod(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        
        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        // TODO: Once working, refactor into server/client/common pattern.
        MCTPConfig.register(modContainer);

        // This will use NeoForge's ConfigurationScreen to display this mod's configs
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        ModJobsInitializer.DEFERRED_REGISTER.register(modEventBus);        
        TileEntityInitializer.BLOCK_ENTITIES.register(modEventBus);
        ModBuildingsInitializer.DEFERRED_REGISTER.register(modEventBus);
        ModSoundEvents.SOUND_EVENTS.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("MCTradePost common setup");
    }

    // Add items to their creative tabs.
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
        }

        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(MCTradePostMod.ADVANCED_CLIPBOARD.get());
        }

        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(MCTradePostMod.blockHutMarketplace);
        }        
    }

    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("MCTradePost onLoadComplete");  
    }

    @EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public class NetworkHandler {

        @SubscribeEvent
        public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event) {
            // Sets the current network version
            final PayloadRegistrar registrar = event.registrar("1");

            registrar.playBidirectional(
                ItemValuePacket.TYPE,
                ItemValuePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                    ItemValuePacket::handleDataInClientOnMain,
                    ItemValuePacket::handleDataInServerOnMain
                )
            );        
        }

        /**
         * This event fires on server-side both at initial world load and whenever a new player
         * joins the server (with getPlayer() != null), and also on datapack reload (with null).
         * Note that at this point the client has not yet received the recipes/tags.
         *
         * @param event {@link net.neoforged.neoforge.event.OnDatapackSyncEvent}
         *   
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onDataPackSync(final OnDatapackSyncEvent event)
        {
            final MinecraftServer server = event.getPlayerList().getServer();
            final GameProfile owner = server.getSingleplayerProfile();

            if (event.getPlayer() == null)
            {
                // for a reload event, we also want to rebuild various lists (mirroring FMLServerStartedEvent)
                ItemValueRegistry.generateValues();

                // and then finally update every player with the results
                for (final ServerPlayer player : event.getPlayerList().getPlayers())
                {
                    if (player.getGameProfile() != owner)   // don't need to send them in SP, or LAN owner
                    {
                        ItemValuePacket.sendPacketsToPlayer(player);
                    }
                }
            }
            else if (event.getPlayer().getGameProfile() != owner)
            {
                ItemValuePacket.sendPacketsToPlayer(event.getPlayer());
            }
        }       
        */

        @EventBusSubscriber(modid = MCTradePostMod.MODID)
        public class ServerLoginHandler {

            @SubscribeEvent
            public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
                if (event.getEntity() instanceof ServerPlayer player) {
                    MCTradePostMod.LOGGER.debug("Synchronizing information to new player: {} ", player);
                    ItemValuePacket.sendPacketsToPlayer(player);
                }
            }
        }

        
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {   
        // Do something when the server starts
        LOGGER.info("Server starting");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event)
    {   
        // Derive the value of all items
        LOGGER.info("Server has started.");
        ItemValueRegistry.generateValues();
        // ItemValueRegistry.logValues();
    }

    @EventBusSubscriber(modid = MCTradePostMod.MODID)
    public class ServerEventHandler {

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            MCTradePostMod.LOGGER.info("Server starting.");
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
        {
            // Register the layer definitions
            MCTradePostMod.LOGGER.info("Registering layer definitions.");
            event.registerLayerDefinition(ClientRegistryHandler.MALE_SHOPKEEPER, MaleShopkeeperModel::createMesh);
            event.registerLayerDefinition(ClientRegistryHandler.FEMALE_SHOPKEEPER, FemaleShopkeeperModel::createMesh);
        }

        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            LOGGER.info("Handling model initialization");
            var modelSet = event.getEntityModels();
            
            // Build a lightweight fake context using what's available
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            var blockRenderer = Minecraft.getInstance().getBlockRenderer();
            var resourceManager = Minecraft.getInstance().getResourceManager();
            var font = Minecraft.getInstance().font;
            ItemInHandRenderer itemInHandRenderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();

            var context = new EntityRendererProvider.Context(
                dispatcher,
                itemRenderer,
                blockRenderer,
                itemInHandRenderer, 
                resourceManager,
                modelSet,
                font
            );

            ModModelTypeInitializer.init(context);
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onRegisterItemDecorations(final RegisterItemDecorationsEvent event)
        {
            LOGGER.info("Registering item decorations");
            event.register(MCTradePostMod.ADVANCED_CLIPBOARD, new AdvancedClipBoardDecorator());
        }      
            
        @SubscribeEvent
        public static void registerBlocks(RegisterEvent event)
        {
            if (event.getRegistryKey().equals(Registries.BLOCK))
            {
                LOGGER.info("Registering blocks");
                ClientModEvents.registerBlocks(event.getRegistry(Registries.BLOCK));     
            }
        }

        @SubscribeEvent
        public static void registerEntities(RegisterEvent event)
        {
            if (event.getRegistryKey().equals(Registries.ENTITY_TYPE))
            {
                LOGGER.info("Registering entities");
            }
        }

        /**
         * Initializes {@link ModBlocks} with the block instances.
         *
         * @param registry The registry to register the new blocks.
         */
        public static void registerBlocks(final Registry<Block> registry)
        {
            LOGGER.info("Registering the block hut marketplace.");
            MCTradePostMod.blockHutMarketplace = new BlockHutMarketplace().registerMCTPHutBlock(registry);
        }


        @SubscribeEvent
        public static void registerItems(RegisterEvent event)
        {
            if (event.getRegistryKey().equals(Registries.ITEM))
            {
                LOGGER.info("Registering items");
                ClientModEvents.registerBlockItem(event.getRegistry(Registries.ITEM));
            }
        }  
        
        /**
         * Initializes the registry with the relevant item produced by the relevant blocks.
         *
         * @param registry The item registry to add the items too.
         */
        public static void registerBlockItem(final Registry<Item> registry)
        {
            MCTradePostMod.blockHutMarketplace.registerBlockItem(registry, new Item.Properties());
        }
    }
}
