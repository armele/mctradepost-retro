# Adding a new MineColonies Building
## "Block" for Building
Add the block hut for the building:
- src\main\java\com\deathfrog\mctradepost\core\blocks\huts\
- Extend MCTPBaseBlockHut
- Add the hut block to the list of huts in ModBuildings.getHuts
- Create a public static reference in MCTradePostMod.java
- Add it to the creative tab in MCTradePostMod.addCreative
- Register the associated block item in ModBlocksInitializer.registerBlockItem
- Define facings in a .json file at src\main\resources\assets\mctradepost\blockstates
- Define a model item at src\main\resources\assets\mctradepost\models\item
- Define visuals of the block itself in a .json file at src\main\resources\assets\mctradepost\models\block\ (may want to use BlockBench)
- Define the recipe to create the block in a .json file at src\main\resources\data\mctradepost\recipe
- Define the tile entity in MCTradePostTileEntities and add the Tile Entity registration in TileEntityInitializer

## Functional (Built) Building
Add the building class itself:
- src\main\java\com\deathfrog\mctradepost\core\colony\jobs\buildings\workerbuildings\
- Extend AbstractBuilding
- Associate the building and its hut block in ModBuildingsInitializer (static block)
-- Add the translation key to en_us.json: "com." + key.getNamespace() + ".building." + key.getPath();
-- This is also where you designate what modules the building will support once built.
-- Define at least a new WorkerBuildingModule in MCTPBuildingModules.java specifying the primary stats. DO NOT assign Intelligence here - it will break BOWindow ("BADJOBS" bug)
-- If the building serializes any data, create a view that deserializes it to the client, and associate that view with the building in ModBuildingsInitializer.

Add any custom buildng modules that are needed:
- Define new modules in MCTPBuildingModules
- These may extend or reuse existing modules, or may require the development of new ones.

Define five building levels using Structurize, named the same as the NewBuildingClass.schematicName()
- Place the structurize files in src\main\resources\blueprints\mctradepost\Trade Post Default\economic\

## Job for Building
Define the Job(s) associated with the Building
- Add the job class at src\main\java\com\deathfrog\mctradepost\core\colony\jobs\
- Give it a constant ID and add it to the job list in MCTPModJobs
- Register that job in ModJobsInitializer
- Add the translation key to en_us.json: "com." + key.getNamespace() + ".job." + key.getPath();
- Assuming it has a custom AI (next step), hook into that at NewJob.generateAI()
- Associate it with the models (later step) that animate the worker at ModModelTypeInitializer OR override getModel in the Job class to call its super().

Define the AI associated with the Job
- Create a new class at src\main\java\com\deathfrog\mctradepost\core\entity\ai\workers\ overriding AbstractEntityAIInteract<JobClass, BuildingClass> (or other base class as appropriate)
- Implement the desired logic (the hard part - and the fun part)
- If desired, add a new work state visual icon: src\main\resources\assets\mctradepost\textures\icons\work\

Define the models that animate the worker for the Job (not necessary if calling super() in getModel of the job class)
- Create new models at src\main\java\com\deathfrog\mctradepost\core\client\model\
- Create male and female variants of the model textures at src\main\resources\assets\mctradepost\textures\entity\citizen\default\

## Building Research
If the building is meant to be unlocked via research or progression, set that up with an effect using a json file at src\main\resources\data\mctradepost\researches\effects
