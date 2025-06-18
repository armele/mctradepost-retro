# Adding a new MC Trade Post Block
* Create a custom block class at src\main\java\com\deathfrog\mctradepost\core\blocks\ if any special functionality is needed - otherwise, at registration use the base Block class.
* Register the Block and BlockItem in MCTradePostMod.
* Add the registered item to the JEI in MCTradePostMod
* Create a blockstate .json file at src\main\resources\assets\mctradepost\blockstates
* Create a block model file referencing your texture graphic (later step) at src\main\resources\assets\mctradepost\models\block
* Create an item model file referencing your texture graphic (next step) at src\main\resources\assets\mctradepost\models\item
* Place your texture graphic in src\main\resources\assets\mctradepost\textures\block
* Create a recipe file (if this block can be created through a recipe) at src\main\resources\data\mctradepost\recipe
* Create a loot table .json file at src\main\resources\data\mctradepost\loot_tables that describes what drops when the block is broken.
* Add a line in en_us.json with the in-game name for this block and item.
* If Domum compatibility is desired, add the resource location for this new block to the domum default.json block tag.