package com.deathfrog.mctradepost;

import java.io.IOException;

import org.slf4j.Logger;

import com.deathfrog.mctradepost.api.advancements.MCTPAdvancementTriggers;
import com.deathfrog.mctradepost.api.entity.GhostCartEntity;
import com.deathfrog.mctradepost.api.entity.pets.ITradePostPet;
import com.deathfrog.mctradepost.api.entity.pets.PetAxolotl;
import com.deathfrog.mctradepost.api.entity.pets.PetFox;
import com.deathfrog.mctradepost.api.entity.pets.PetWolf;
import com.deathfrog.mctradepost.api.items.MCTPModDataComponents;
import com.deathfrog.mctradepost.api.sounds.MCTPModSoundEvents;
import com.deathfrog.mctradepost.api.util.NullnessBridge;
import com.deathfrog.mctradepost.api.util.PetRegistryUtil;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPCraftingSetup;
import com.deathfrog.mctradepost.apiimp.initializer.MCTPInteractionInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModBlocksInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModBuildingsInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModJobsInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.ModModelTypeInitializer;
import com.deathfrog.mctradepost.apiimp.initializer.TileEntityInitializer;
import com.deathfrog.mctradepost.core.blocks.AbstractBlockPetWorkingLocation;
import com.deathfrog.mctradepost.core.blocks.BlockDistressed;
import com.deathfrog.mctradepost.core.blocks.BlockDredger;
import com.deathfrog.mctradepost.core.blocks.BlockGlazed;
import com.deathfrog.mctradepost.core.blocks.BlockMixedStone;
import com.deathfrog.mctradepost.core.blocks.BlockOutpostMarker;
import com.deathfrog.mctradepost.core.blocks.BlockSideSlab;
import com.deathfrog.mctradepost.core.blocks.BlockSideSlabInterleaved;
import com.deathfrog.mctradepost.core.blocks.BlockStackedSlab;
import com.deathfrog.mctradepost.core.blocks.BlockTrough;
import com.deathfrog.mctradepost.core.blocks.StewpotBlock;
import com.deathfrog.mctradepost.core.blocks.BlockScavenge;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutMarketplace;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutPetShop;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutRecycling;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutResort;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutStation;
import com.deathfrog.mctradepost.core.blocks.huts.BlockHutOutpost;
import com.deathfrog.mctradepost.core.blocks.huts.MCTPBaseBlockHut;
import com.deathfrog.mctradepost.core.client.render.AdvancedClipBoardDecorator;
import com.deathfrog.mctradepost.core.client.render.GhostCartRenderer;
import com.deathfrog.mctradepost.core.client.render.souvenir.SouvenirItemExtension;
import com.deathfrog.mctradepost.core.client.render.souvenir.SouvenirLoader;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ItemValueRegistry;
import com.deathfrog.mctradepost.core.colony.buildings.modules.PetMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.StewIngredientMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.ThriftShopMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.TradeMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.WithdrawMessage;
import com.deathfrog.mctradepost.core.colony.buildings.modules.settings.MCTPSettingsFactory;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.OutpostRequestResolverFactory;
import com.deathfrog.mctradepost.core.colony.requestsystem.resolvers.TrainDeliveryResolverFactory;
import com.deathfrog.mctradepost.core.event.ModelRegistryHandler;
import com.deathfrog.mctradepost.core.event.burnout.BurnoutRemedyManager;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualManager;
import com.deathfrog.mctradepost.core.event.wishingwell.ritual.RitualPacket;
import com.deathfrog.mctradepost.core.loot.ModLootModifiers;
import com.deathfrog.mctradepost.core.network.messages.OutpostAssignMessage;
import com.deathfrog.mctradepost.core.placementhandlers.OutpostPlacementHandler;
import com.deathfrog.mctradepost.item.AdvancedClipboardItem;
import com.deathfrog.mctradepost.item.BlockDistressedItem;
import com.deathfrog.mctradepost.item.BlockGlazedItem;
import com.deathfrog.mctradepost.item.BlockSideSlabInterleavedItem;
import com.deathfrog.mctradepost.item.BlockSideSlabItem;
import com.deathfrog.mctradepost.item.BlockStackedSlabItem;
import com.deathfrog.mctradepost.item.CoinItem;
import com.deathfrog.mctradepost.item.ImmersionBlenderItem;
import com.deathfrog.mctradepost.item.OutpostClaimMarkerItem;
import com.deathfrog.mctradepost.item.SouvenirItem;
import com.deathfrog.mctradepost.item.SouvenirItem.SouvenirRecord;
import com.deathfrog.mctradepost.network.ConfigurationPacket;
import com.deathfrog.mctradepost.network.ItemValuePacket;
import com.deathfrog.mctradepost.recipe.DeconstructionRecipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.core.items.ItemFood;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.AxolotlRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FoxRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
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
import com.deathfrog.mctradepost.core.entity.CoinRenderer;

@Mod(MCTradePostMod.MODID)
@SuppressWarnings("null")
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
    
    public static final DeferredRegister<RecipeType<?>> RECIPES = DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, MCTradePostMod.MODID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, MCTradePostMod.MODID);

    public static final String CREATIVE_TRADEPOST_TABNAME = "tradepost";

    /*
     * Items (Non-Block)
     */
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

    public static final DeferredItem<ItemFood> PERPETUAL_STEW = ITEMS.register("perpetual_stew",
        () -> new ItemFood((new Item.Properties()).food(new FoodProperties.Builder().nutrition(6).usingConvertsTo(Items.BOWL).saturationModifier(3.0F).alwaysEdible().build()), 1));

    public static final DeferredItem<Item> NAPKIN = ITEMS.register("napkin",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> END_MORTAR = ITEMS.register("end_mortar",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> PRISMARINE_MORTAR = ITEMS.register("prismarine_mortar",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> QUARTZ_MORTAR = ITEMS.register("quartz_mortar",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> STEW_SEASONING = ITEMS.register("stew_seasoning",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WISH_PLENTY = ITEMS.register("wish_plenty",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> WISH_HEALTH = ITEMS.register("wish_health",
        () -> new Item(new Item.Properties()));

    public static final DeferredItem<ImmersionBlenderItem> IMMERSION_BLENDER = ITEMS.register("immersion_blender",
        () -> new ImmersionBlenderItem(new Item.Properties().durability(100)));

    public static final DeferredItem<SouvenirItem> SOUVENIR = ITEMS.register("souvenir",
        () -> new SouvenirItem(new Item.Properties().component(MCTPModDataComponents.SOUVENIR_COMPONENT.get(), new SouvenirRecord("empty", 0))));
    
    public static final DeferredItem<CoinItem> MCTP_COIN_ITEM = ITEMS.register("mctp_coin", 
        () -> new CoinItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CoinItem> MCTP_COIN_GOLD = ITEMS.register("mctp_coin_gold", 
        () -> new CoinItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));

    public static final DeferredItem<CoinItem> MCTP_COIN_DIAMOND = ITEMS.register("mctp_coin_diamond", 
        () -> new CoinItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)));

    public static final DeferredItem<OutpostClaimMarkerItem> OUTPOST_CLAIM = ITEMS.register("outpost_claim",
        () -> new OutpostClaimMarkerItem(new Item.Properties()));

    /*
    * ENTITIES 
    */
    public static final DeferredHolder<EntityType<?>, EntityType<CoinEntity>> COIN_ENTITY_TYPE = ENTITIES.register("coin_entity",
        () -> EntityType.Builder.<CoinEntity>of(CoinEntity::new, MobCategory.MISC)
            .sized(0.20f, 0.20f)    // Size of the entity (like an ItemEntity)
            .clientTrackingRange(8) // Reasonable for item-like entities
            .updateInterval(10)     // Sync rate
            .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "coin_entity").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<GhostCartEntity>> GHOST_CART = ENTITIES.register("ghost_cart",
        () -> EntityType.Builder.<GhostCartEntity>of(
            GhostCartEntity::new, MobCategory.MISC)
            .sized(0.98f, 0.7f)          // same hitbox as normal cart
            .clientTrackingRange(128)
            .updateInterval(1)            // send pos every tick
            .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "ghost_cart").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<PetWolf>> PET_WOLF = ENTITIES.register("pet_wolf",
        () -> EntityType.Builder.of(PetWolf::new, MobCategory.CREATURE)
            .sized(EntityType.WOLF.getDimensions().width(), EntityType.WOLF.getDimensions().height())
            .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet_wolf").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<PetFox>> PET_FOX = ENTITIES.register("pet_fox",
        () -> EntityType.Builder.of(PetFox::new, MobCategory.CREATURE)
            .sized(EntityType.FOX.getDimensions().width(), EntityType.FOX.getDimensions().height())
            .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet_fox").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<PetAxolotl>> PET_AXOLOTL = ENTITIES.register("pet_axolotl",
        () -> EntityType.Builder.of(PetAxolotl::new, MobCategory.CREATURE)
            .sized(EntityType.AXOLOTL.getDimensions().width(), EntityType.AXOLOTL.getDimensions().height())
            .build(ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, "pet_axolotl").toString()));

    /*
    * BLOCKS
    */
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutMarketplace = BLOCKS.register(BlockHutMarketplace.HUT_NAME, () -> new BlockHutMarketplace());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutResort = BLOCKS.register(BlockHutResort.HUT_NAME, () -> new BlockHutResort());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutRecycling = BLOCKS.register(BlockHutRecycling.HUT_NAME, () -> new BlockHutRecycling());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutStation = BLOCKS.register(BlockHutStation.HUT_NAME, () -> new BlockHutStation());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutPetShop = BLOCKS.register(BlockHutPetShop.HUT_NAME, () -> new BlockHutPetShop());
    public static final DeferredBlock<MCTPBaseBlockHut> blockHutOutpost = BLOCKS.register(BlockHutOutpost.HUT_NAME, () -> new BlockHutOutpost());

    public static final DeferredBlock<BlockMixedStone> MIXED_STONE = BLOCKS.register(BlockMixedStone.MIXED_STONE_ID, () -> new BlockMixedStone(Properties.of()
            .mapColor(NullnessBridge.assumeNonnull(MapColor.STONE))
            .strength(2.0f, 6.0f)
            .sound(NullnessBridge.assumeNonnull(SoundType.STONE))));
            
    public static final DeferredBlock<StairBlock> MIXED_STONE_STAIRS =
        BLOCKS.register(BlockMixedStone.MIXED_STONE_STAIRS_ID,
            () -> new StairBlock(MIXED_STONE.get().defaultBlockState(),  // base block state supplier
                MIXED_STONE.get().properties()                           // reuse base block's properties
            ));

    public static final DeferredBlock<WallBlock> MIXED_STONE_WALL = BLOCKS.register(BlockMixedStone.MIXED_STONE_WALL_ID, () -> new WallBlock(Block.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f, 6.0f)
            .sound(SoundType.STONE)));

    public static final DeferredBlock<SlabBlock> MIXED_STONE_SLAB = BLOCKS.register(BlockMixedStone.MIXED_STONE_SLAB_ID, () -> new SlabBlock(Block.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f, 6.0f)
            .sound(SoundType.STONE)));

    public static final DeferredBlock<Block> MIXED_STONE_BRICK = BLOCKS.register(ModBlocksInitializer.MIXED_STONE_BRICK_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> MIXED_STONE_BRICK_STAIRS = BLOCKS.register(ModBlocksInitializer.MIXED_STONE_BRICK_STAIRS_NAME,
        () -> new StairBlock(MIXED_STONE_BRICK.get().defaultBlockState(), MIXED_STONE_BRICK.get().properties()));
    public static final DeferredBlock<WallBlock> MIXED_STONE_BRICK_WALL =
        BLOCKS.register(ModBlocksInitializer.MIXED_STONE_BRICK_WALL_NAME, () -> new WallBlock(MIXED_STONE_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> MIXED_STONE_BRICK_SLAB =
        BLOCKS.register(ModBlocksInitializer.MIXED_STONE_BRICK_SLAB_NAME, () -> new SlabBlock(MIXED_STONE_BRICK.get().properties()));


    public static final DeferredBlock<BlockDistressed> DISTRESSED = BLOCKS.register(BlockDistressed.DISTRESSED_ID, () -> new BlockDistressed());
    public static final DeferredBlock<BlockStackedSlab> STACKED_SLAB = BLOCKS.register(BlockStackedSlab.STACKED_SLAB_ID, () -> new BlockStackedSlab());
    public static final DeferredBlock<BlockSideSlab> SIDE_SLAB = BLOCKS.register(BlockSideSlab.SIDE_SLAB_ID, () -> new BlockSideSlab());
    public static final DeferredBlock<BlockSideSlabInterleaved> SIDE_SLAB_INTERLEAVED = BLOCKS.register(BlockSideSlabInterleaved.SIDE_SLAB_INTERLEAVED_ID, () -> new BlockSideSlabInterleaved());
    public static final DeferredBlock<BlockGlazed> GLAZED = BLOCKS.register(BlockGlazed.GLAZED_ID, () -> new BlockGlazed());

    public static final DeferredBlock<Block> THATCH = BLOCKS.register(ModBlocksInitializer.THATCH_NAME, () -> new Block(Blocks.HAY_BLOCK.properties()));

    public static final DeferredBlock<StairBlock> THATCH_STAIRS =
        BLOCKS.register(ModBlocksInitializer.THATCH_STAIRS_NAME,
            () -> new StairBlock(THATCH.get().defaultBlockState(),  // base block state supplier
                THATCH.get().properties()                           // reuse base block's properties
            ));

    public static final DeferredBlock<WallBlock> THATCH_WALL = BLOCKS.register(ModBlocksInitializer.THATCH_WALL_NAME, () -> new WallBlock(THATCH.get().properties()));
    public static final DeferredBlock<SlabBlock> THATCH_SLAB = BLOCKS.register(ModBlocksInitializer.THATCH_SLAB_NAME, () -> new SlabBlock(THATCH.get().properties()));

    public static final DeferredBlock<Block> PLASTER = BLOCKS.register(ModBlocksInitializer.PLASTER_NAME, () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
            .mapColor(MapColor.TERRACOTTA_WHITE)
            .sound(SoundType.BAMBOO_WOOD)));


    public static final DeferredBlock<StairBlock> PLASTER_STAIRS =
        BLOCKS.register(ModBlocksInitializer.PLASTER_STAIRS_NAME,
            () -> new StairBlock(PLASTER.get().defaultBlockState(),
                PLASTER.get().properties()
            ));

    public static final DeferredBlock<WallBlock> PLASTER_WALL = BLOCKS.register(ModBlocksInitializer.PLASTER_WALL_NAME, () -> new WallBlock(PLASTER.get().properties()));
    public static final DeferredBlock<SlabBlock> PLASTER_SLAB = BLOCKS.register(ModBlocksInitializer.PLASTER_SLAB_NAME, () -> new SlabBlock(PLASTER.get().properties()));

    public static final DeferredBlock<Block> QUARTZ_PLASTER = BLOCKS.register(ModBlocksInitializer.QUARTZ_PLASTER_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> QUARTZ_PLASTER_STAIRS = BLOCKS.register(ModBlocksInitializer.QUARTZ_PLASTER_STAIRS_NAME,
        () -> new StairBlock(QUARTZ_PLASTER.get().defaultBlockState(), QUARTZ_PLASTER.get().properties()));
    public static final DeferredBlock<WallBlock> QUARTZ_PLASTER_WALL =
        BLOCKS.register(ModBlocksInitializer.QUARTZ_PLASTER_WALL_NAME, () -> new WallBlock(QUARTZ_PLASTER.get().properties()));
    public static final DeferredBlock<SlabBlock> QUARTZ_PLASTER_SLAB =
        BLOCKS.register(ModBlocksInitializer.QUARTZ_PLASTER_SLAB_NAME, () -> new SlabBlock(QUARTZ_PLASTER.get().properties()));

    public static final DeferredBlock<Block> ROUGH_STONE = BLOCKS.register(ModBlocksInitializer.ROUGH_STONE_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> ROUGH_STONE_STAIRS = BLOCKS.register(ModBlocksInitializer.ROUGH_STONE_STAIRS_NAME,
        () -> new StairBlock(ROUGH_STONE.get().defaultBlockState(), ROUGH_STONE.get().properties()));
    public static final DeferredBlock<WallBlock> ROUGH_STONE_WALL =
        BLOCKS.register(ModBlocksInitializer.ROUGH_STONE_WALL_NAME, () -> new WallBlock(ROUGH_STONE.get().properties()));
    public static final DeferredBlock<SlabBlock> ROUGH_STONE_SLAB =
        BLOCKS.register(ModBlocksInitializer.ROUGH_STONE_SLAB_NAME, () -> new SlabBlock(ROUGH_STONE.get().properties()));

    public static final DeferredBlock<Block> ROUGH_BRICK = BLOCKS.register(ModBlocksInitializer.ROUGH_BRICK_NAME, () -> new Block(Block.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(1.5f, 2.0f)
            .sound(SoundType.STONE)));


    public static final DeferredBlock<StairBlock> ROUGH_BRICK_STAIRS =
        BLOCKS.register(ModBlocksInitializer.ROUGH_BRICK_STAIRS_NAME,
            () -> new StairBlock(ROUGH_BRICK.get().defaultBlockState(),  // base block state supplier
                ROUGH_BRICK.get().properties()                           // reuse base block's properties
            ));

    public static final DeferredBlock<WallBlock> ROUGH_BRICK_WALL = BLOCKS.register(ModBlocksInitializer.ROUGH_BRICK_WALL_NAME, () -> new WallBlock(ROUGH_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> ROUGH_BRICK_SLAB = BLOCKS.register(ModBlocksInitializer.ROUGH_BRICK_SLAB_NAME, () -> new SlabBlock(ROUGH_BRICK.get().properties()));

    public static final DeferredItem<Item> MIXED_STONE_ITEM =
        ITEMS.register(BlockMixedStone.MIXED_STONE_ID, () -> new BlockItem(MIXED_STONE.get(), new Item.Properties()));
    
    public static final DeferredItem<Item> MIXED_STONE_STAIRS_ITEM =
        ITEMS.register(BlockMixedStone.MIXED_STONE_STAIRS_ID, () -> new BlockItem(MIXED_STONE_STAIRS.get(), new Item.Properties()));
        
    public static final DeferredItem<Item> MIXED_STONE_WALL_ITEM =
        ITEMS.register(BlockMixedStone.MIXED_STONE_WALL_ID, () -> new BlockItem(MIXED_STONE_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> MIXED_STONE_SLAB_ITEM =
        ITEMS.register(BlockMixedStone.MIXED_STONE_SLAB_ID, () -> new BlockItem(MIXED_STONE_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> MIXED_STONE_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.MIXED_STONE_BRICK_NAME, () -> new BlockItem(MIXED_STONE_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> MIXED_STONE_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.MIXED_STONE_BRICK_STAIRS_NAME, () -> new BlockItem(MIXED_STONE_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> MIXED_STONE_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.MIXED_STONE_BRICK_WALL_NAME, () -> new BlockItem(MIXED_STONE_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> MIXED_STONE_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.MIXED_STONE_BRICK_SLAB_NAME, () -> new BlockItem(MIXED_STONE_BRICK_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<BlockDistressedItem> DISTRESSED_ITEM =
        ITEMS.register(BlockDistressed.DISTRESSED_ID, () -> new BlockDistressedItem(DISTRESSED.get(), new BlockDistressedItem.Properties()));

    public static final DeferredItem<BlockStackedSlabItem> STACKED_SLAB_ITEM =
        ITEMS.register(BlockStackedSlab.STACKED_SLAB_ID, () -> new BlockStackedSlabItem(STACKED_SLAB.get(), new BlockStackedSlabItem.Properties()));

    public static final DeferredItem<BlockSideSlabItem> SIDE_SLAB_ITEM =
        ITEMS.register(BlockSideSlab.SIDE_SLAB_ID, () -> new BlockSideSlabItem(SIDE_SLAB.get(), new BlockSideSlabItem.Properties()));

    public static final DeferredItem<BlockSideSlabInterleavedItem> SIDE_SLAB_INTERLEAVED_ITEM =
        ITEMS.register(BlockSideSlabInterleaved.SIDE_SLAB_INTERLEAVED_ID, () -> new BlockSideSlabInterleavedItem(SIDE_SLAB_INTERLEAVED.get(), new BlockSideSlabInterleavedItem.Properties()));

    public static final DeferredItem<BlockGlazedItem> GLAZED_ITEM =
        ITEMS.register(BlockGlazed.GLAZED_ID, () -> new BlockGlazedItem(GLAZED.get(), new BlockItem.Properties()));

    public static final DeferredBlock<Block> ENDETHYST = BLOCKS.register(ModBlocksInitializer.ENDETHYST_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 3.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> ENDETHYST_STAIRS = BLOCKS.register(ModBlocksInitializer.ENDETHYST_STAIRS_NAME,
        () -> new StairBlock(ENDETHYST.get().defaultBlockState(), ENDETHYST.get().properties()));
    public static final DeferredBlock<WallBlock> ENDETHYST_WALL =
        BLOCKS.register(ModBlocksInitializer.ENDETHYST_WALL_NAME, () -> new WallBlock(ENDETHYST.get().properties()));
    public static final DeferredBlock<SlabBlock> ENDETHYST_SLAB =
        BLOCKS.register(ModBlocksInitializer.ENDETHYST_SLAB_NAME, () -> new SlabBlock(ENDETHYST.get().properties()));

    public static final DeferredBlock<Block> ENDETHYST_BRICK = BLOCKS.register(ModBlocksInitializer.ENDETHYST_BRICK_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> ENDETHYST_BRICK_STAIRS = BLOCKS.register(ModBlocksInitializer.ENDETHYST_BRICK_STAIRS_NAME,
        () -> new StairBlock(ENDETHYST_BRICK.get().defaultBlockState(), ENDETHYST_BRICK.get().properties()));
    public static final DeferredBlock<WallBlock> ENDETHYST_BRICK_WALL =
        BLOCKS.register(ModBlocksInitializer.ENDETHYST_BRICK_WALL_NAME, () -> new WallBlock(ENDETHYST_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> ENDETHYST_BRICK_SLAB =
        BLOCKS.register(ModBlocksInitializer.ENDETHYST_BRICK_SLAB_NAME, () -> new SlabBlock(ENDETHYST_BRICK.get().properties()));
    public static final DeferredBlock<Block> ENDMARINE = BLOCKS.register(ModBlocksInitializer.ENDMARINE_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> ENDMARINE_STAIRS = BLOCKS.register(ModBlocksInitializer.ENDMARINE_STAIRS_NAME,
        () -> new StairBlock(ENDMARINE.get().defaultBlockState(), ENDMARINE.get().properties()));
    public static final DeferredBlock<WallBlock> ENDMARINE_WALL =
        BLOCKS.register(ModBlocksInitializer.ENDMARINE_WALL_NAME, () -> new WallBlock(ENDMARINE.get().properties()));
    public static final DeferredBlock<SlabBlock> ENDMARINE_SLAB =
        BLOCKS.register(ModBlocksInitializer.ENDMARINE_SLAB_NAME, () -> new SlabBlock(ENDMARINE.get().properties()));

    public static final DeferredBlock<Block> ENDMARINE_BRICK = BLOCKS.register(ModBlocksInitializer.ENDMARINE_BRICK_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> ENDMARINE_BRICK_STAIRS = BLOCKS.register(ModBlocksInitializer.ENDMARINE_BRICK_STAIRS_NAME,
        () -> new StairBlock(ENDMARINE_BRICK.get().defaultBlockState(), ENDMARINE_BRICK.get().properties()));
    public static final DeferredBlock<WallBlock> ENDMARINE_BRICK_WALL =
        BLOCKS.register(ModBlocksInitializer.ENDMARINE_BRICK_WALL_NAME, () -> new WallBlock(ENDMARINE_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> ENDMARINE_BRICK_SLAB =
        BLOCKS.register(ModBlocksInitializer.ENDMARINE_BRICK_SLAB_NAME, () -> new SlabBlock(ENDMARINE_BRICK.get().properties()));

    public static final DeferredBlock<Block> MARINE_LAPIS = BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> MARINE_LAPIS_STAIRS = BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_STAIRS_NAME,
        () -> new StairBlock(MARINE_LAPIS.get().defaultBlockState(), MARINE_LAPIS.get().properties()));
    public static final DeferredBlock<WallBlock> MARINE_LAPIS_WALL =
        BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_WALL_NAME, () -> new WallBlock(MARINE_LAPIS.get().properties()));
    public static final DeferredBlock<SlabBlock> MARINE_LAPIS_SLAB =
        BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_SLAB_NAME, () -> new SlabBlock(MARINE_LAPIS.get().properties()));

    public static final DeferredBlock<Block> MARINE_LAPIS_BRICK = BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> MARINE_LAPIS_BRICK_STAIRS = BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_STAIRS_NAME,
        () -> new StairBlock(MARINE_LAPIS_BRICK.get().defaultBlockState(), MARINE_LAPIS_BRICK.get().properties()));
    public static final DeferredBlock<WallBlock> MARINE_LAPIS_BRICK_WALL =
        BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_WALL_NAME, () -> new WallBlock(MARINE_LAPIS_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> MARINE_LAPIS_BRICK_SLAB =
        BLOCKS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_SLAB_NAME, () -> new SlabBlock(MARINE_LAPIS_BRICK.get().properties()));

    public static final DeferredBlock<Block> BLOCK_IVY_BRICK =
        BLOCKS.register(ModBlocksInitializer.IVY_BRICK_NAME, () -> new Block(Blocks.VINE.properties()));

    public static final DeferredBlock<Block> WEATHERED_ROUGH_STONE = BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> WEATHERED_ROUGH_STONE_STAIRS = BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_STAIRS_NAME,
        () -> new StairBlock(WEATHERED_ROUGH_STONE.get().defaultBlockState(), WEATHERED_ROUGH_STONE.get().properties()));
    public static final DeferredBlock<WallBlock> WEATHERED_ROUGH_STONE_WALL =
        BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_WALL_NAME, () -> new WallBlock(WEATHERED_ROUGH_STONE.get().properties()));
    public static final DeferredBlock<SlabBlock> WEATHERED_ROUGH_STONE_SLAB =
        BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_SLAB_NAME, () -> new SlabBlock(WEATHERED_ROUGH_STONE.get().properties()));

    public static final DeferredBlock<Block> MARINE_BASALT = BLOCKS.register(ModBlocksInitializer.MARINE_BASALT_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> MARINE_BASALT_STAIRS = BLOCKS.register(ModBlocksInitializer.MARINE_BASALT_STAIRS_NAME,
        () -> new StairBlock(MARINE_BASALT.get().defaultBlockState(), MARINE_BASALT.get().properties()));
    public static final DeferredBlock<WallBlock> MARINE_BASALT_WALL =
        BLOCKS.register(ModBlocksInitializer.MARINE_BASALT_WALL_NAME, () -> new WallBlock(MARINE_BASALT.get().properties()));
    public static final DeferredBlock<SlabBlock> MARINE_BASALT_SLAB =
        BLOCKS.register(ModBlocksInitializer.MARINE_BASALT_SLAB_NAME, () -> new SlabBlock(MARINE_BASALT.get().properties()));

    public static final DeferredBlock<BlockTrough> TROUGH =
        BLOCKS.register(ModBlocksInitializer.TROUGH_NAME, () -> new BlockTrough(THATCH.get().properties().lightLevel(state -> 4)));

    public static final DeferredBlock<BlockScavenge> SCAVENGE =
        BLOCKS.register(ModBlocksInitializer.SCAVENGE_NAME, () -> new BlockScavenge(THATCH.get().properties().lightLevel(state -> 4)));

    public static final DeferredBlock<BlockDredger> DREDGER =
        BLOCKS.register(ModBlocksInitializer.DREDGER_NAME, () -> new BlockDredger(THATCH.get().properties().lightLevel(state -> 4)));


    public static final DeferredBlock<Block> WOVEN_KELP = BLOCKS.register(ModBlocksInitializer.WOVEN_KELP_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> WOVEN_KELP_STAIRS = BLOCKS.register(ModBlocksInitializer.WOVEN_KELP_STAIRS_NAME,
        () -> new StairBlock(WOVEN_KELP.get().defaultBlockState(), WOVEN_KELP.get().properties()));
    public static final DeferredBlock<WallBlock> WOVEN_KELP_WALL =
        BLOCKS.register(ModBlocksInitializer.WOVEN_KELP_WALL_NAME, () -> new WallBlock(WOVEN_KELP.get().properties()));
    public static final DeferredBlock<SlabBlock> WOVEN_KELP_SLAB =
        BLOCKS.register(ModBlocksInitializer.WOVEN_KELP_SLAB_NAME, () -> new SlabBlock(WOVEN_KELP.get().properties()));

    public static final DeferredBlock<BlockOutpostMarker> BLOCK_OUTPOST_MARKER =
        BLOCKS.register(ModBlocksInitializer.BLOCK_OUTPOST_MARKER_NAME, () -> new BlockOutpostMarker(Blocks.BLACK_BANNER.properties()));
 
    public static final DeferredBlock<StewpotBlock> STEWPOT_FILLED =
        BLOCKS.register(ModBlocksInitializer.STEWPOT_FILLED_NAME,
            () -> new StewpotBlock(BlockBehaviour.Properties.of()
                .strength(2.0F)
                .requiresCorrectToolForDrops()
                .noOcclusion()
            )
        );

    /*
    * ITEMS (Block)
    */
    public static final DeferredItem<Item> THATCH_ITEM =
        ITEMS.register(ModBlocksInitializer.THATCH_NAME, () -> new BlockItem(THATCH.get(), new Item.Properties()));

    public static final DeferredItem<Item> THATCH_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.THATCH_STAIRS_NAME, () -> new BlockItem(THATCH_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> THATCH_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.THATCH_WALL_NAME, () -> new BlockItem(THATCH_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> THATCH_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.THATCH_SLAB_NAME, () -> new BlockItem(THATCH_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> PLASTER_ITEM =
        ITEMS.register(ModBlocksInitializer.PLASTER_NAME, () -> new BlockItem(PLASTER.get(), new Item.Properties()));

    public static final DeferredItem<Item> PLASTER_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.PLASTER_STAIRS_NAME, () -> new BlockItem(PLASTER_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> PLASTER_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.PLASTER_WALL_NAME, () -> new BlockItem(PLASTER_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> PLASTER_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.PLASTER_SLAB_NAME, () -> new BlockItem(PLASTER_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> QUARTZ_PLASTER_ITEM =
        ITEMS.register(ModBlocksInitializer.QUARTZ_PLASTER_NAME, () -> new BlockItem(QUARTZ_PLASTER.get(), new Item.Properties()));

    public static final DeferredItem<Item> QUARTZ_PLASTER_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.QUARTZ_PLASTER_STAIRS_NAME, () -> new BlockItem(QUARTZ_PLASTER_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> QUARTZ_PLASTER_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.QUARTZ_PLASTER_WALL_NAME, () -> new BlockItem(QUARTZ_PLASTER_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> QUARTZ_PLASTER_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.QUARTZ_PLASTER_SLAB_NAME, () -> new BlockItem(QUARTZ_PLASTER_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_STONE_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_STONE_NAME, () -> new BlockItem(ROUGH_STONE.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_STONE_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_STONE_STAIRS_NAME, () -> new BlockItem(ROUGH_STONE_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_STONE_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_STONE_WALL_NAME, () -> new BlockItem(ROUGH_STONE_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_STONE_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_STONE_SLAB_NAME, () -> new BlockItem(ROUGH_STONE_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_BRICK_NAME, () -> new BlockItem(ROUGH_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_BRICK_STAIRS_NAME, () -> new BlockItem(ROUGH_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_BRICK_WALL_NAME, () -> new BlockItem(ROUGH_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ROUGH_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ROUGH_BRICK_SLAB_NAME, () -> new BlockItem(ROUGH_BRICK_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_NAME, () -> new BlockItem(ENDETHYST.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_STAIRS_NAME, () -> new BlockItem(ENDETHYST_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_WALL_NAME, () -> new BlockItem(ENDETHYST_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_SLAB_NAME, () -> new BlockItem(ENDETHYST_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_BRICK_NAME, () -> new BlockItem(ENDETHYST_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_BRICK_STAIRS_NAME, () -> new BlockItem(ENDETHYST_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_BRICK_WALL_NAME, () -> new BlockItem(ENDETHYST_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDETHYST_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDETHYST_BRICK_SLAB_NAME, () -> new BlockItem(ENDETHYST_BRICK_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_NAME, () -> new BlockItem(ENDMARINE.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_STAIRS_NAME, () -> new BlockItem(ENDMARINE_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_WALL_NAME, () -> new BlockItem(ENDMARINE_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_SLAB_NAME, () -> new BlockItem(ENDMARINE_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_BRICK_NAME, () -> new BlockItem(ENDMARINE_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_BRICK_STAIRS_NAME, () -> new BlockItem(ENDMARINE_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_BRICK_WALL_NAME, () -> new BlockItem(ENDMARINE_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> ENDMARINE_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.ENDMARINE_BRICK_SLAB_NAME, () -> new BlockItem(ENDMARINE_BRICK_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_NAME, () -> new BlockItem(MARINE_LAPIS.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_STAIRS_NAME, () -> new BlockItem(MARINE_LAPIS_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_WALL_NAME, () -> new BlockItem(MARINE_LAPIS_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_SLAB_NAME, () -> new BlockItem(MARINE_LAPIS_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_NAME, () -> new BlockItem(MARINE_LAPIS_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_STAIRS_NAME, () -> new BlockItem(MARINE_LAPIS_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_WALL_NAME, () -> new BlockItem(MARINE_LAPIS_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_LAPIS_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_LAPIS_BRICK_SLAB_NAME, () -> new BlockItem(MARINE_LAPIS_BRICK_SLAB.get(), new Item.Properties()));
		
    public static final DeferredItem<Item> IVY_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.IVY_BRICK_NAME, () -> new BlockItem(BLOCK_IVY_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_STAIRS_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_WALL_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_SLAB_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_BASALT_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_BASALT_NAME, () -> new BlockItem(MARINE_BASALT.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_BASALT_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_BASALT_STAIRS_NAME, () -> new BlockItem(MARINE_BASALT_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_BASALT_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_BASALT_WALL_NAME, () -> new BlockItem(MARINE_BASALT_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> MARINE_BASALT_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.MARINE_BASALT_SLAB_NAME, () -> new BlockItem(MARINE_BASALT_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> TROUGH_ITEM =
        ITEMS.register(ModBlocksInitializer.TROUGH_NAME, () -> new BlockItem(TROUGH.get(), new Item.Properties()));

    public static final DeferredItem<Item> SCAVENGE_ITEM =
        ITEMS.register(ModBlocksInitializer.SCAVENGE_NAME, () -> new BlockItem(SCAVENGE.get(), new Item.Properties()));    
    
    public static final DeferredItem<Item> DREDGER_ITEM =
        ITEMS.register(ModBlocksInitializer.DREDGER_NAME, () -> new BlockItem(DREDGER.get(), new Item.Properties()));   

    public static final DeferredItem<Item> WOVEN_KELP_ITEM =
        ITEMS.register(ModBlocksInitializer.WOVEN_KELP_NAME, () -> new BlockItem(WOVEN_KELP.get(), new Item.Properties()));

    public static final DeferredItem<Item> WOVEN_KELP_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.WOVEN_KELP_STAIRS_NAME, () -> new BlockItem(WOVEN_KELP_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> WOVEN_KELP_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.WOVEN_KELP_WALL_NAME, () -> new BlockItem(WOVEN_KELP_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> WOVEN_KELP_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.WOVEN_KELP_SLAB_NAME, () -> new BlockItem(WOVEN_KELP_SLAB.get(), new Item.Properties()));

    public static final DeferredItem<Item> STEWPOT_FILLED_ITEM =
        ITEMS.register(ModBlocksInitializer.STEWPOT_FILLED_NAME, () -> new BlockItem(STEWPOT_FILLED.get(), new Item.Properties()));

    /*
    * Creative Mode Tabs
    */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Creates a creative tab with the id "examplemod:example_tab" for the example item, that is placed after the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TRADEPOST_TAB = CREATIVE_MODE_TABS.register(CREATIVE_TRADEPOST_TABNAME, () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup:mctradepost")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> MCTP_COIN_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(MCTP_COIN_ITEM.get());
                output.accept(MCTP_COIN_GOLD.get());
                output.accept(MCTP_COIN_DIAMOND.get());
            }).build());

    /*
     * RECIPES
     */
    public static final DeferredHolder<RecipeType<?>, RecipeType<DeconstructionRecipe>> DECON_RECIPE_TYPE =
        MCTradePostMod.RECIPES.register(
            DeconstructionRecipe.DECON_RECIPE_KEY,
            () -> RecipeType.<DeconstructionRecipe>simple(
                ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, DeconstructionRecipe.DECON_RECIPE_KEY)
            )
        );

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<DeconstructionRecipe>> DECON_RECIPE_SERIALIZER =
        MCTradePostMod.RECIPE_SERIALIZERS.register(
            DeconstructionRecipe.DECON_RECIPE_KEY,
            DeconstructionRecipe.Serializer::new
        );


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

        // Register custom advancements
        MCTPAdvancementTriggers.DEFERRED_REGISTER.register(modEventBus);

        // Register recipe support
        RECIPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        // NeoForge.EVENT_BUS.register(RitualReloadListener.class);
        NeoForge.EVENT_BUS.register(BurnoutRemedyManager.class);

        // Add a listener for the common setup event.
        modEventBus.addListener(this::onCommonSetup);

        // Add a listener for entities joining the world.
        NeoForge.EVENT_BUS.addListener(MCTradePostMod::onEntityJoinWorld);


        // Add a listener for the completion of the load.
        modEventBus.addListener(this::onLoadComplete);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        MCTPConfig.register(modContainer);
        clientSideInitializations(modContainer);                            // Environment enforcement is handled inside this method.
        
        ModJobsInitializer.DEFERRED_REGISTER.register(modEventBus);        
        TileEntityInitializer.BLOCK_ENTITIES.register(modEventBus);
        // ModBuildingsInitializer.DEFERRED_REGISTER.register(modEventBus);  // NETSYNC: Building registration at this point with static initialization, server load fails due to early access of objects.
        MCTPModSoundEvents.SOUND_EVENTS.register(modEventBus);

        // Register our data components.
        MCTPModDataComponents.REGISTRAR.register(modEventBus);
        
        ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

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

    /**
     * This method is called on both the client and server side during the mod's initialization.
     * It is responsible for setting up and initializing any code that needs to be shared between
     * the client and server. This includes things like setting up custom damage sources, 
     * registering capabilities, and setting up custom registry objects.
     */
    private void onCommonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("MCTradePost common setup ");  

        StandardFactoryController.getInstance().registerNewFactory(new OutpostRequestResolverFactory());
        StandardFactoryController.getInstance().registerNewFactory(new TrainDeliveryResolverFactory());
        StandardFactoryController.getInstance().registerNewFactory(new MCTPSettingsFactory.SortSettingFactory());

        ItemValueRegistry.loadInitialValuesFromJson();  

        PlacementHandlers.add(new OutpostPlacementHandler());
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

        MCTradePostMod.LOGGER.info("Injecting building modules.");
        ModBuildingsInitializer.injectBuildingModules();
    }

    @EventBusSubscriber(modid = MCTradePostMod.MODID)
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
            
            // Register the payload handler for the ConfigurationPacket - used to update the client with ritual recipes they need to be aware of.
            registrar.playToClient(
                RitualPacket.TYPE,
                RitualPacket.RITUAL_CODEC,
                (payload, ctx) -> payload.handleDataInClientOnMain(ctx)
            );  

            TradeMessage.TYPE.register(registrar);
            WithdrawMessage.TYPE.register(registrar);
            PetMessage.TYPE.register(registrar);
            OutpostAssignMessage.TYPE.register(registrar);
            StewIngredientMessage.TYPE.register(registrar);
            ThriftShopMessage.TYPE.register(registrar);

        }

        @EventBusSubscriber(modid = MCTradePostMod.MODID)
        public class ServerLoginHandler {

            @SubscribeEvent
            public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
                if (event.getEntity() instanceof ServerPlayer player) {
                    MCTradePostMod.LOGGER.debug("Synchronizing information to new player: {} ", player);
                    ItemValuePacket.sendPacketsToPlayer(player);
                    ConfigurationPacket.sendPacketsToPlayer(player);
                    RitualPacket.sendPacketsToPlayer(player);
                }
            }
        }   
    }

    /**
     * Handles the event when an entity joins the world. This method checks if the
     * entity is an instance of ITradePostPet and, if so, registers the pet in the
     * global pet registry. This ensures that all trade post pets are tracked for
     * functionality related to the MCTradePost mod.
     *
     * @param event The event that occurs when an entity joins the world. Contains
     *              information about the entity and the level it joins.
     */
    public static void onEntityJoinWorld(EntityJoinLevelEvent event) {
        // MCTradePostMod.LOGGER.info("Entity joining world: {}", event.getEntity());

        if (!(event.getLevel() instanceof ServerLevel)) return;

        if (event.getEntity() instanceof ITradePostPet pet) {
            // MCTradePostMod.LOGGER.info("Registering pet: {}", pet);
            try {
                PetRegistryUtil.register(pet);
            } catch (IllegalArgumentException e) {
                MCTradePostMod.LOGGER.error("Error registering pet: {}. Discarding.", pet, e);
                event.getEntity().discard();
            }
        }
    }


    /**
     * Events that can safely take place at world loading.
     * 
     * @param event The level load event.
     */
    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {

    }


    @EventBusSubscriber(modid = MODID)
    public class ModServerEventHandler
    {
        /**
         * Handles the entity attribute creation event for custom entities.
         * This method is responsible for assigning attribute modifiers to entities
         * when they are created. Specifically, it assigns attributes to the PET_WOLF
         * entity type by building the default attribute map for pet entities.
         *
         * @param event The event that triggers the creation of entity attributes.
         */
        @SubscribeEvent
        public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
            event.put(MCTradePostMod.PET_WOLF.get(), Wolf.createAttributes().build());
            event.put(MCTradePostMod.PET_FOX.get(), Fox.createAttributes().build());
            event.put(MCTradePostMod.PET_AXOLOTL.get(), Axolotl.createAttributes().build());
        }

        /**
         * Registers entities for the mod.
         * This method is invoked when the RegisterEvent is fired for the ENTITY_TYPE registry key.
         * @param event The register event containing the registry to register the entities with.
         */
        @SubscribeEvent
        public static void registerEntities(RegisterEvent event)
        {
            if (event.getRegistryKey().equals(Registries.ENTITY_TYPE))
            {
                MCTradePostMod.LOGGER.info("Placeholder: Registering entities");
            }
        }

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void registerCaps(final RegisterCapabilitiesEvent event)
        {
            // Placeholder for registering capabilities.
        }
    }

    @EventBusSubscriber(modid = MODID)
    public class GameplayServerEventHandler
    {
        /**
         * Handles the BlockEvent.EntityPlaceEvent.
         * This event is fired by the EntityPlaceEvent class when a block is placed by an entity.
         * Specifically, it is fired when a player places a block.
         * This method is responsible for registering the placed block as a work location for pets in the colony.
         * @param event The event that is fired when an entity places a block.
         */
        @SubscribeEvent
        public static void onBlockPlaced(final BlockEvent.EntityPlaceEvent event) {
            if (!(event.getEntity() instanceof Player)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockState state = event.getPlacedBlock();
            if (!(state.getBlock() instanceof AbstractBlockPetWorkingLocation)) return;

            BlockPos pos = event.getPos();
            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony != null) {
                PetRegistryUtil.registerWorkLocation(colony, pos);
                MCTradePostMod.LOGGER.info("Registered Work Location block at {} for colony {}", pos, colony.getID());
            }
        }

        /**
         * Called when a block is broken in the world.
         * If the broken block is a Work Location block, this will unregister the BlockPos
         * as a valid work location for a pet in the colony at that BlockPos.
         * @param event The event containing the broken block and its BlockPos.
         */
        @SubscribeEvent
        public static void onBlockBroken(final BlockEvent.BreakEvent event) {
            if (!(event.getLevel() instanceof ServerLevel level)) return;

            BlockState state = event.getState();
            if (!(state.getBlock() instanceof AbstractBlockPetWorkingLocation)) return;

            BlockPos pos = event.getPos();
            IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(level, pos);
            if (colony != null) {
                PetRegistryUtil.unregisterWorkLocation(colony, pos);
                MCTradePostMod.LOGGER.info("Unregistered Work Location block at {} for colony {}", pos, colony.getID());
            }

        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            MCTradePostMod.LOGGER.info("Server starting.");
        }

        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event)
        {   
            MCTradePostMod.LOGGER.info("Server started.");
        }

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
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
                    event.accept(MCTradePostMod.blockHutPetShop.get());
                    event.accept(MCTradePostMod.blockHutOutpost.get());
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
                    event.accept(MCTradePostMod.PERPETUAL_STEW.get());
                    event.accept(MCTradePostMod.COLD_BREW.get());
                    event.accept(MCTradePostMod.MYSTIC_TEA.get());
                    event.accept(MCTradePostMod.NAPKIN.get());
                    event.accept(MCTradePostMod.END_MORTAR.get());
                    event.accept(MCTradePostMod.PRISMARINE_MORTAR.get());
                    event.accept(MCTradePostMod.QUARTZ_MORTAR.get());
                    event.accept(MCTradePostMod.STEW_SEASONING.get());
                    event.accept(MCTradePostMod.MIXED_STONE.get());
                    event.accept(MCTradePostMod.MIXED_STONE_STAIRS.get());
                    event.accept(MCTradePostMod.MIXED_STONE_WALL.get());
                    event.accept(MCTradePostMod.MIXED_STONE_SLAB.get());
                    event.accept(MCTradePostMod.MIXED_STONE_BRICK.get());
                    event.accept(MCTradePostMod.MIXED_STONE_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.MIXED_STONE_BRICK_WALL.get());
                    event.accept(MCTradePostMod.MIXED_STONE_BRICK_SLAB.get());		
                    event.accept(MCTradePostMod.THATCH.get());
                    event.accept(MCTradePostMod.THATCH_STAIRS.get());
                    event.accept(MCTradePostMod.THATCH_WALL.get());
                    event.accept(MCTradePostMod.THATCH_SLAB.get());
                    event.accept(MCTradePostMod.PLASTER.get());
                    event.accept(MCTradePostMod.PLASTER_STAIRS.get());
                    event.accept(MCTradePostMod.PLASTER_WALL.get());
                    event.accept(MCTradePostMod.PLASTER_SLAB.get());
                    event.accept(MCTradePostMod.QUARTZ_PLASTER.get());
                    event.accept(MCTradePostMod.QUARTZ_PLASTER_STAIRS.get());
                    event.accept(MCTradePostMod.QUARTZ_PLASTER_WALL.get());
                    event.accept(MCTradePostMod.QUARTZ_PLASTER_SLAB.get());	
                    event.accept(MCTradePostMod.ROUGH_STONE.get());
                    event.accept(MCTradePostMod.ROUGH_STONE_STAIRS.get());
                    event.accept(MCTradePostMod.ROUGH_STONE_WALL.get());
                    event.accept(MCTradePostMod.ROUGH_STONE_SLAB.get());
                    event.accept(MCTradePostMod.ROUGH_BRICK.get());
                    event.accept(MCTradePostMod.ROUGH_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.ROUGH_BRICK_WALL.get());
                    event.accept(MCTradePostMod.ROUGH_BRICK_SLAB.get());
                    event.accept(MCTradePostMod.ENDETHYST.get());
                    event.accept(MCTradePostMod.ENDETHYST_STAIRS.get());
                    event.accept(MCTradePostMod.ENDETHYST_WALL.get());
                    event.accept(MCTradePostMod.ENDETHYST_SLAB.get());
                    event.accept(MCTradePostMod.ENDETHYST_BRICK.get());
                    event.accept(MCTradePostMod.ENDETHYST_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.ENDETHYST_BRICK_WALL.get());
                    event.accept(MCTradePostMod.ENDETHYST_BRICK_SLAB.get());	
                    event.accept(MCTradePostMod.ENDMARINE.get());
                    event.accept(MCTradePostMod.ENDMARINE_STAIRS.get());
                    event.accept(MCTradePostMod.ENDMARINE_WALL.get());
                    event.accept(MCTradePostMod.ENDMARINE_SLAB.get());
                    event.accept(MCTradePostMod.ENDMARINE_BRICK.get());
                    event.accept(MCTradePostMod.ENDMARINE_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.ENDMARINE_BRICK_WALL.get());
                    event.accept(MCTradePostMod.ENDMARINE_BRICK_SLAB.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_STAIRS.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_WALL.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_SLAB.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_BRICK.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_BRICK_WALL.get());
                    event.accept(MCTradePostMod.MARINE_LAPIS_BRICK_SLAB.get());
                    event.accept(MCTradePostMod.BLOCK_IVY_BRICK.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_STAIRS.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_WALL.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_SLAB.get());	
                    event.accept(MCTradePostMod.MARINE_BASALT.get());
                    event.accept(MCTradePostMod.MARINE_BASALT_STAIRS.get());
                    event.accept(MCTradePostMod.MARINE_BASALT_WALL.get());
                    event.accept(MCTradePostMod.MARINE_BASALT_SLAB.get());	
                    event.accept(MCTradePostMod.TROUGH.get());
                    event.accept(MCTradePostMod.SCAVENGE.get());
                    event.accept(MCTradePostMod.DREDGER.get());
                    event.accept(MCTradePostMod.WOVEN_KELP.get());
                    event.accept(MCTradePostMod.WOVEN_KELP_STAIRS.get());
                    event.accept(MCTradePostMod.WOVEN_KELP_WALL.get());
                    event.accept(MCTradePostMod.WOVEN_KELP_SLAB.get());
                    event.accept(MCTradePostMod.WISH_PLENTY.get());
                    event.accept(MCTradePostMod.WISH_HEALTH.get());
                    event.accept(MCTradePostMod.OUTPOST_CLAIM.get());
                    event.accept(MCTradePostMod.STEWPOT_FILLED.get());
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
            event.registerEntityRenderer(MCTradePostMod.GHOST_CART.get(), GhostCartRenderer::new);
            event.registerEntityRenderer(MCTradePostMod.PET_WOLF.get(), WolfRenderer::new);
            event.registerEntityRenderer(MCTradePostMod.PET_FOX.get(), FoxRenderer::new);
            event.registerEntityRenderer(MCTradePostMod.PET_AXOLOTL.get(), AxolotlRenderer::new);
        }

        /**
         * Handles the addition of custom layers to entity renderers.
         * This method is called on the client side when the AddLayers event is fired.
         * It initializes a context with various rendering components and
         * invokes the model type initializer to set up custom entity models.
         *
         * @param event The event providing access to entity models for registering custom layers.
         */
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onAddLayers(EntityRenderersEvent.AddLayers event) 
        {
            LOGGER.info("Handling model initialization");
            EntityModelSet modelSet = event.getEntityModels();
            
            // Build a lightweight fake context using what's available
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            Font font = Minecraft.getInstance().font;
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

        /**
         * Registers item decorations for the mod.
         * This method is invoked when the RegisterEvent is fired for the ITEM_DECORATIONS registry key.
         * @param event The register event containing the registry to register the item decorations with.
         */
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onRegisterItemDecorations(final RegisterItemDecorationsEvent event)
        {
            LOGGER.info("Registering item decorations");
            event.register(MCTradePostMod.ADVANCED_CLIPBOARD, new AdvancedClipBoardDecorator());
        }      
            
        /**
         * Registers blocks for the mod.
         * This method is invoked when the RegisterEvent is fired for the BLOCK registry key.
         * @param event The register event containing the registry to register the blocks with.
         */
        @SubscribeEvent
        public static void registerBlocks(RegisterEvent event)
        {
            if (event.getRegistryKey().equals(Registries.BLOCK))
            {
                LOGGER.info("Placeholder: Registering blocks");
            }
        }

        /**
         * Registers the custom item renderer for the souvenir item.
         * The custom renderer is responsible for rendering the souvenir item in the inventory.
         * @param event The register client extensions event.
         */
        @SubscribeEvent
        public static void onRegisterClientExtensions(final RegisterClientExtensionsEvent event) 
        {
            LOGGER.info("Registering souvenir item renderer");
            event.registerItem(new SouvenirItemExtension(), MCTradePostMod.SOUVENIR.get());
        }

        /**
         * Registers the custom geometry loader for the souvenir item.
         * The custom geometry loader is responsible for loading the souvenir item model.
         * @param event The register geometry loaders event.
         */
        @SubscribeEvent
        public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
            event.register(SouvenirLoader.LOADER_ID, SouvenirLoader.INSTANCE);
        }

        // Register client-side listeners on the Mod bus
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new RitualManager());
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemProperties.register(
                    MCTradePostMod.OUTPOST_CLAIM.get(),
                    ResourceLocation.fromNamespaceAndPath(MCTradePostMod.MODID, OutpostClaimMarkerItem.LINKED),
                    (stack, level, entity, seed) ->
                        OutpostClaimMarkerItem.hasLinkedBlockPos(stack) ? 1.0F : 0.0F
                );
            });
        }
    }

}
