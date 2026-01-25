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
    public static final DeferredBlock<Block> DIAGONAL_BRICK = BLOCKS.register(ModBlocksInitializer.DIAGONAL_BRICK_NAME,
        () -> new Block(Block.Properties.of().mapColor(MapColor.STONE).strength(1.5f, 2.0f).sound(SoundType.STONE)));
    public static final DeferredBlock<StairBlock> DIAGONAL_BRICK_STAIRS = BLOCKS.register(ModBlocksInitializer.DIAGONAL_BRICK_STAIRS_NAME,
        () -> new StairBlock(DIAGONAL_BRICK.get().defaultBlockState(), DIAGONAL_BRICK.get().properties()));
    public static final DeferredBlock<WallBlock> DIAGONAL_BRICK_WALL =
        BLOCKS.register(ModBlocksInitializer.DIAGONAL_BRICK_WALL_NAME, () -> new WallBlock(DIAGONAL_BRICK.get().properties()));
    public static final DeferredBlock<SlabBlock> DIAGONAL_BRICK_SLAB =
        BLOCKS.register(ModBlocksInitializer.DIAGONAL_BRICK_SLAB_NAME, () -> new SlabBlock(DIAGONAL_BRICK.get().properties()));

// ITEM Declaration (MCTradePostMod)		
    public static final DeferredItem<Item> DIAGONAL_BRICK_ITEM =
        ITEMS.register(ModBlocksInitializer.DIAGONAL_BRICK_NAME, () -> new BlockItem(DIAGONAL_BRICK.get(), new Item.Properties()));

    public static final DeferredItem<Item> DIAGONAL_BRICK_STAIRS_ITEM =
        ITEMS.register(ModBlocksInitializer.DIAGONAL_BRICK_STAIRS_NAME, () -> new BlockItem(DIAGONAL_BRICK_STAIRS.get(), new Item.Properties()));

    public static final DeferredItem<Item> DIAGONAL_BRICK_WALL_ITEM =
        ITEMS.register(ModBlocksInitializer.DIAGONAL_BRICK_WALL_NAME, () -> new BlockItem(DIAGONAL_BRICK_WALL.get(), new Item.Properties()));

    public static final DeferredItem<Item> DIAGONAL_BRICK_SLAB_ITEM =
        ITEMS.register(ModBlocksInitializer.DIAGONAL_BRICK_SLAB_NAME, () -> new BlockItem(DIAGONAL_BRICK_SLAB.get(), new Item.Properties()));
		
// CREATIVE tab entries
                    event.accept(MCTradePostMod.DIAGONAL_BRICK.get());
                    event.accept(MCTradePostMod.DIAGONAL_BRICK_STAIRS.get());
                    event.accept(MCTradePostMod.DIAGONAL_BRICK_WALL.get());
                    event.accept(MCTradePostMod.DIAGONAL_BRICK_SLAB.get());		
		
// Identifiers (ModBlocksInitializer)
	public final static String DIAGONAL_BRICK_NAME = "diagonal_brick";
    public final static String DIAGONAL_BRICK_STAIRS_NAME = "diagonal_brick_stairs";
    public final static String DIAGONAL_BRICK_WALL_NAME = "diagonal_brick_wall";
    public final static String DIAGONAL_BRICK_SLAB_NAME = "diagonal_brick_slab";
	
// Strings (en_us.json)
    "item.mctradepost.diagonal_brick": "Diagonal Brick",
    "item.mctradepost.diagonal_brick_stairs": "Diagonal Brick Stairs",
    "item.mctradepost.diagonal_brick_wall": "Diagonal Brick Wall",
    "item.mctradepost.diagonal_brick_slab": "Diagonal Brick Slab",
    "block.mctradepost.diagonal_brick": "Diagonal Brick",
    "block.mctradepost.diagonal_brick_stairs": "Diagonal Brick Stairs",
    "block.mctradepost.diagonal_brick_wall": "Diagonal Brick Wall",
    "block.mctradepost.diagonal_brick_slab": "Diagonal Brick Slab",

// File conversions	
python jsonConvert.py --dir src/main/resources/assets/mctradepost/blockstates  --start rough_brick --new diagonal_brick
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/block --start rough_brick --new diagonal_brick
python jsonConvert.py --dir src/main/resources/assets/mctradepost/models/item  --start rough_brick --new diagonal_brick
python jsonConvert.py --dir src/main/resources/data/mctradepost/loot_table/blocks  --start rough_brick --new diagonal_brick
python jsonConvert.py --dir src/main/resources/data/mctradepost/recipe  --start rough_brick --new diagonal_brick

// Do tags by hand!
domum_ornamentum/tags/block/default.json
minecraft/tags/block/*.*
minecolonies/tags/block/tier*.json
minecolonies/tags/block/pathblocks

// Update recipe by hand!
src\main\resources\data\mctradepost\recipe
