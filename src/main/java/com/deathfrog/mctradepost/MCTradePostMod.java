package com.deathfrog.mctradepost;

import java.io.IOException;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPCraftingSetup;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModModelTypeInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutMarketplace;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutRecycling;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutResort;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutStation;
import com.deathfrog.mctradepost.core.blocks.huts.MCTPBaseBlockHut;
import com.deathfrog.mctradepost.core.client.render.AdvancedClipBoardDecorator;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;
import com.deathfrog.mctradepost.core.event.burnout.BurnoutRemedyManager;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualReloadListener;
import com.deathfrog.mctradepost.item.AdvancedClipboardItem;
import com.deathfrog.mctradepost.item.CoinItem;
import com.deathfrog.mctradepost.item.ImmersionBlenderItem;
import com.deathfrog.mctradepost.network.ConfigurationPacket;
import com.deathfrog.mctradepost.network.ItemValuePacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.minecolonies.core.items.ItemFood;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.deathfrog.mctradepost.core.entity.CoinEntity;

/*
 */
import com.deathfrog.mctradepost.core.entity.CoinRenderer;

// TODO: Add missing sounds (anything mapped to replaceme.ogg)
// TODO: PUBLISHING [Enhancement] Add in the nice cosmetics that show in the CurseForge mods list (or at least determine how)

@Mod(MCTradePostMod.MODID)
public class MCTradePostMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "mctradepost";

    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // Create a Gson instance
    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(ResourceLocation.class, new TypeAdapter<ResourceLocation>() {
            @Override
            public void write(JsonWriter out, ResourceLocation value) throws IOException {
                out.value(value.toString());
            }

            @Override
            public ResourceLocation read(JsonReader in) throws IOException {
                return ResourceLocation.parse(in.nextString());
            }
        })
        .setPrettyPrinting()
        .create();

    // Create a Deferred Register to hold Blocks which will all be registered under the "mctradepost" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    // Create a Deferred Register to hold Items which will all be registered under the "mctradepost" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    
    // Create a Deferred Register to hold Entities which will all be registered under the "mctradepost" namespace
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MCTradePostMod.MODID);

    public static final String CREATIVE_TRADEPOST_TABNAME = "tradepost";
    
    public static final DeferredItem<AdvancedClipboardItem> ADVANCED_CLIPBOARD = ITEMS.register("advanced_clipboard",
        () -> new AdvancedClipboardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ItemFood> ICECREAM = ITEMS.register("icecream",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> DAIQUIRI = ITEMS.register("daiquiri",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).usingConvertsTo(Items.GLASS_BOTTLE).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> VEGGIE_JUICE = ITEMS.register("veggie_juice",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> FRUIT_JUICE = ITEMS.register("fruit_juice",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> PROTIEN_SHAKE = ITEMS.register("protien_shake",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));
    
    public static final DeferredItem<ItemFood> BAR_NUTS = ITEMS.register("bar_nuts",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).usingConvertsTo(Items.BOWL).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> COLD_BREW = ITEMS.register("cold_brew",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).usingConvertsTo(Items.GLASS_BOTTLE).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> MYSTIC_TEA = ITEMS.register("mystic_tea",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> VANILLA_MILKSHAKE = ITEMS.register("vanilla_milkshake",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<ItemFood> ENERGY_SHAKE = ITEMS.register("energy_shake",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.6F).build()), 1));

    public static final DeferredItem<Item> NAPKIN = ITEMS.register("napkin",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<ImmersionBlenderItem> IMMERSION_BLENDER = ITEMS.register("immersion_blender",
        () -> new ImmersionBlenderItem(new Item.Properties().durability(100)));

    public static final DeferredHolder<EntityType<?>, EntityType<CoinEntity>> COIN_ENTITY_TYPE =
            ENTITIES.register("coin_entity", () ->
                EntityType.Builder.<CoinEntity>of(CoinEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)    // Size of the entity (like an ItemEntity)
                    .clientTrackingRange(8) // Reasonable for item-like entities
                    .updateInterval(10)     // Sync rate
                    .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "coin_entity").toString())
            );
        
    public static final DeferredItem<CoinItem> MCTP_COIN_ITEM = ITEMS.register("mctp_coin", 
        () -> new CoinItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    public static final DeferredBlock<MCTPBaseBlockHut> blockHutMarketplace = BLOCKS.register(BlockHutMarketplace.HUT_NAME, () -> new BlockHutMarketplace());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutResort = BLOCKS.register(BlockHutResort.HUT_NAME, () -> new BlockHutResort());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutRecycling = BLOCKS.register(BlockHutRecycling.HUT_NAME, () -> new BlockHutRecycling());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutStation = BLOCKS.register(BlockHutStation.HUT_NAME, () -> new BlockHutStation());


    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRADEPOST_TAB = CREATIVE_MODE_TABS.register(CREATIVE_TRADEPOST_TABNAME, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup:mctradepost")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> MCTP_COIN_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MCTP_COIN_ITEM.get()); // Add the example item to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public MCTradePostMod(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
       
        // Register the Deferred Register to the mod event bus so entities get registered
        ENTITIES.register(modEventBus);
        
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(RitualReloadListener.class);
        NeoForge.EVENT_BUS.register(BurnoutRemedyManager.class);

        // Add a listener for the common setup event.
        modEventBus.addListener(this::onCommonSetup);

        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        MCTPConfig.register(modContainer);
        clientSideInitializations(modContainer);                            // Environment enforcement is handled inside this method.
        
        ModJobsInitializer.DEFERRED_REGISTER.register(modEventBus);        
        TileEntityInitializer.BLOCK_ENTITIES.register(modEventBus);
        // ModBuildingsInitializer.DEFERRED_REGISTER.register(modEventBus);  // NETSYNC: Building registration at this point with static initialization, server load fails due to early access of objects.
        MCTPModSoundEvents.SOUND_EVENTS.register(modEventBus);



        LOGGER.info("MCTradePost mod initialized.");
    }

    /**
     * This method is called on the client side only.
     * It is responsible for setting up the client side configuration screen.
     * The configuration screen is created by registering a IConfigScreenFactory 
     * with the mod container. This factory is used to create the configuration screen
     * for this mod.
     */
    private void clientSideInitializations(ModContainer modContainer) {
        // This will use NeoForge's ConfigurationScreen to display this mod's configs
        if (FMLEnvironment.dist.isClient()) {
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);   
        }
    }

    private void onCommonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("MCTradePost common setup (placeholder)");    
        // NETSYNC: Building registration at this point with explicit initialization, server load fails due to "Cannot register new entries to DeferredRegister after RegisterEvent has been fired. "
    }

    /**
     * This method is called on both the client and server side after the mod has finished loading.
     * It is responsible for injecting the sound events from MCTradePost into MineColonies' CITIZEN_SOUND_EVENTS.
     * This is a temporary solution until sounds in MineColonies have the flexibility to look up sound events from other modpacks.
     */
    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("MCTradePost onLoadComplete"); 
        MCTradePostMod.LOGGER.info("Injecting sounds."); 
        MCTPModSoundEvents.injectSounds();              // These need to be injected both on client (to play) and server (to register)

        MCTradePostMod.LOGGER.info("Injecting crafting rules.");
        MCTPCraftingSetup.injectCraftingRules();    
        
        MCTradePostMod.LOGGER.info("Injecting interaction handlers.");
        MCTPInteractionInitializer.injectInteractionHandlers();
    }

    @EventBusSubscriber(modid = MCTradePostMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public class NetworkHandler {

        /**
         * Handles the registration of network payload handlers for the mod.
         * This method is invoked when the RegisterPayloadHandlersEvent is fired,
         * allowing the mod to set up its network communication protocols.
         *
         * @param event The event that provides the registrar for registering payload handlers.
         */
        @SubscribeEvent
        public static void onNetworkRegistry(final RegisterPayloadHandlersEvent event) {
            // Sets the current network version
            final PayloadRegistrar registrar = event.registrar("1");

            // Register the payload handler for the ItemValuePacket - used to update the client with the list (and value) of sellable items.
            registrar.playBidirectional(
                ItemValuePacket.TYPE,
                ItemValuePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                    ItemValuePacket::handleDataInClientOnMain,
                    ItemValuePacket::handleDataInServerOnMain
                )
            );        

            // Register the payload handler for the ConfigurationPacket - used to update the client with configurations they need to be aware of.
            registrar.playBidirectional(
                ConfigurationPacket.TYPE,
                ConfigurationPacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                    ConfigurationPacket::handleDataInClientOnMain,
                    ConfigurationPacket::handleDataInServerOnMain
                )
            );              
        }

        @EventBusSubscriber(modid = MCTradePostMod.MODID)
        public class ServerLoginHandler {

            @SubscribeEvent
            public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
                if (event.getEntity() instanceof ServerPlayer player) {
                    MCTradePostMod.LOGGER.debug("Synchronizing information to new player: {} ", player);
                    ItemValuePacket.sendPacketsToPlayer(player);
                    ConfigurationPacket.sendPacketsToPlayer(player);
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

    /**
     * Events that can safely take place at world loading.
     * 
     * @param event The level load event.
     */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {

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

        // Add items to their creative tabs.
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        private static void addCreative(BuildCreativeModeTabContentsEvent event)
        {

            if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
                // No-op placeholde for future use.
            }

            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                // No-op placeholder for future use.
            }

            TRADEPOST_TAB.unwrapKey().ifPresent(key -> {
                if (event.getTabKey().equals(key)) {
                    event.accept(MCTradePostMod.blockHutMarketplace.get());
                    event.accept(MCTradePostMod.blockHutResort.get());
                    event.accept(MCTradePostMod.blockHutRecycling.get());
                    event.accept(MCTradePostMod.blockHutStation.get());
                    event.accept(MCTradePostMod.ADVANCED_CLIPBOARD.get());
                    event.accept(MCTradePostMod.ICECREAM.get());
                    event.accept(MCTradePostMod.DAIQUIRI.get());
                    event.accept(MCTradePostMod.IMMERSION_BLENDER.get());
                    event.accept(MCTradePostMod.VEGGIE_JUICE.get());
                    event.accept(MCTradePostMod.FRUIT_JUICE.get());
                    event.accept(MCTradePostMod.PROTIEN_SHAKE.get());
                    event.accept(MCTradePostMod.ENERGY_SHAKE.get());
                    event.accept(MCTradePostMod.VANILLA_MILKSHAKE.get());
                    event.accept(MCTradePostMod.BAR_NUTS.get());
                    event.accept(MCTradePostMod.COLD_BREW.get());
                    event.accept(MCTradePostMod.MYSTIC_TEA.get());
                    event.accept(MCTradePostMod.NAPKIN.get());
                }
            });

        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
        {
            // Register the layer definitions
            MCTradePostMod.LOGGER.info("Registering model definitions.");
            ModelRegistryHandler.registerModels(event);
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(MCTradePostMod.COIN_ENTITY_TYPE.get(), CoinRenderer::new);
        }

        @OnlyIn(Dist.CLIENT)
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
    }

}
