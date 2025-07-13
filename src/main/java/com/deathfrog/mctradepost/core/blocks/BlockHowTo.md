# Adding a new MC Trade Post Block
* Create a custom block class at src\main\java\com\deathfrog\mctradepost\core\blocks\ if any special functionality is needed (like a new domum recipe to create) - otherwise, at registration (next step) use the base Block class (or built in block for stair, wall, etc.).

* Register the Block and BlockItem in MCTradePostMod.

* Add the registered item to the Creative tab in MCTradePostMod (if desired)

* Create a blockstate .json file at src\main\resources\assets\mctradepost\blockstates. The name should match the registration key of your block.

* Create a block model .json file referencing your texture graphic (later step) at src\main\resources\assets\mctradepost\models\block. The name of the file(s) should match what you've referenced in your blockstate file (previous step).

* Create an item model file referencing your texture graphic (next step) at src\main\resources\assets\mctradepost\models\item. For "normal" shapes (walls, stairs, slabs, etc) find and follow the vanilla model file equivalent.

* Place your texture graphic in src\main\resources\assets\mctradepost\textures\block. Note that for a composite model created through blockbench no new textures might be needed.

* Create a loot table .json file at src\main\resources\data\mctradepost\loot_table\blocks that describes what drops when the block is broken.

* Create a recipe file (if this block can be created through a recipe) at src\main\resources\data\mctradepost\recipe

* Add a line in en_us.json with the in-game name for this block and item.

* Add any needed tags for compatibility. For example, stairs and walls both need to be tagged as such to fit correctly with other stairs and walls. Construction material shoudl be assigned a Structurize tier, etc.

* If Domum compatibility is desired (to be able to use this block in the architect's cutter), add the resource location for this new block to the domum default.json block tag at src\main\resources\data\domum_ornamentum\tags\block\default.json.

** Template to generating a set of new blocks:
Weathered Rough Stone - 


// BLOCK Declaration (MCTradePostMod)
    public static final DeferredBlock<Block> WEATHERED_ROUGH_STONE = BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> WEATHERED_ROUGH_STONE_STAIRS = BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_STAIRS_NAME,
        () -> new StairBlock(WEATHERED_ROUGH_STONE.get().defaultBlockState(), WEATHERED_ROUGH_STONE.get().properties()));
    public static final DeferredBlock<WallBlock> WEATHERED_ROUGH_STONE_WALL =
        BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_WALL_NAME, () -> new WallBlock(WEATHERED_ROUGH_STONE.get().properties()));
    public static final DeferredBlock<SlabBlock> WEATHERED_ROUGH_STONE_SLAB =
        BLOCKS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_SLAB_NAME, () -> new SlabBlock(WEATHERED_ROUGH_STONE.get().properties()));

// ITEM Declaration (MCTradePostMod)		
    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_STAIRS_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_WALL_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> WEATHERED_ROUGH_STONE_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.WEATHERED_ROUGH_STONE_SLAB_NAME, () -> new BlockItem(WEATHERED_ROUGH_STONE_SLAB.get(), new Item.Properties()));
		
// CREATIVE tab entries
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_STAIRS.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_WALL.get());
                    event.accept(MCTradePostMod.WEATHERED_ROUGH_STONE_SLAB.get());		
		
// Identifiers (ModBlocksInitializer)
	public final static String WEATHERED_ROUGH_STONE_NAME = "weathered_rough_stone";
    public final static String WEATHERED_ROUGH_STONE_STAIRS_NAME = "weathered_rough_stone_stairs";
    public final static String WEATHERED_ROUGH_STONE_WALL_NAME = "weathered_rough_stone_wall";
    public final static String WEATHERED_ROUGH_STONE_SLAB_NAME = "weathered_rough_stone_slab";
	
// Strings (en_us.json)
    "item.mctradepost.weathered_rough_stone": "Weathered Rough Stone Brick",
    "item.mctradepost.weathered_rough_stone_stairs": "Weathered Rough Stone Brick Stairs",
    "item.mctradepost.weathered_rough_stone_wall": "Weathered Rough Stone Brick Wall",
    "item.mctradepost.weathered_rough_stone_slab": "Weathered Rough Stone Brick Slab",
    "block.mctradepost.weathered_rough_stone": "Weathered Rough Stone Brick",
    "block.mctradepost.weathered_rough_stone_stairs": "Weathered Rough Stone Brick Stairs",
    "block.mctradepost.weathered_rough_stone_wall": "Weathered Rough Stone Brick Wall",
    "block.mctradepost.weathered_rough_stone_slab": "Weathered Rough Stone Brick Slab",

// File conversions	
python jsonConvert.py --dir src/main/resources/assets/mctradepost/blockstates  --start endethyst --new weathered_rough_stone
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/block --start endethyst --new weathered_rough_stone
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/item  --start endethyst --new weathered_rough_stone
python jsonConvert.py --dir src/main/resources/data/mctradepost/loot_table/blocks  --start endethyst --new weathered_rough_stone
python jsonConvert.py --dir src/main/resources/data/mctradepost/recipe  --start endethyst --new weathered_rough_stone

// Do tags by hand!
domum_ornamentum/tags/block/default.json
minecraft/tags/block/*.*
minecolonies/tags/block/tier*.json
minecolonies/tags/block/pathblocks

// Update recipe by hand!
src\main\resources\data\mctradepost\recipe
