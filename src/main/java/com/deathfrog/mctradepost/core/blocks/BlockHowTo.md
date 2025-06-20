# Adding a new MC Trade Post Block
* Create a custom block class at src\main\java\com\deathfrog\mctradepost\core\blocks\ if any special functionality is needed (like a new domum recipe to create) - otherwise, at registration (next step) use the base Block class.
* Register the Block and BlockItem in MCTradePostMod.
* Add the registered item to the Creative tab in MCTradePostMod (if desired)
* Create a blockstate .json file at src\main\resources\assets\mctradepost\blockstates. The name should match the registration key of your block.
* Create a block model .json file referencing your texture graphic (later step) at src\main\resources\assets\mctradepost\models\block. The name of the file(s) should match what you've referenced in your blockstate file (previous step).
* Create an item model file referencing your texture graphic (next step) at src\main\resources\assets\mctradepost\models\item
* Place your texture graphic in src\main\resources\assets\mctradepost\textures\block. Note that for a composite model created through blockbench no new textures might be needed.
* Create a recipe file (if this block can be created through a recipe) at src\main\resources\data\mctradepost\recipe
* Create a loot table .json file at src\main\resources\data\mctradepost\loot_tables\blocks that describes what drops when the block is broken.
* Add a line in en_us.json with the in-game name for this block and item.
* Add any needed tags for compatibility. For example, stairs and walls both need to be tagged as such to fit correctly with other stairs and walls. Construction material shoudl be assigned a Structurize tier, etc.
* If Domum compatibility is desired (to be able to use this block in the architect's cutter), add the resource location for this new block to the domum default.json block tag at src\main\resources\data\domum_ornamentum\tags\block\default.json.