package com.deathfrog.mctradepost.core.blocks.huts;

import com.deathfrog.mctradepost.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;

/*
 * # Adding a new MineColonies Building
## "Block" for Building
Add the block hut for the building:
[Done] src\main\java\com\deathfrog\mctradepost\core\blocks\huts\
[Done] Extend MCTPBaseBlockHut
[Done] Add the hut block to the list of huts in ModBuildings.getHuts
[Done] Create a public static reference in MCTradePostMod.java
[Done] Register that in MCTradePostMod.registerBlocks
[Done] Add it to the creative tab in MCTradePostMod.addCreative
[Done] Register the associated block item in MCTradePostMod.registerBlockItem
[Done] Define facings in a .json file at src\main\resources\assets\mctradepost\blockstates
[Done] Define a model item at src\main\resources\assets\mctradepost\models\item
[Done] Define visuals of the block itself in a .json file at src\main\resources\assets\mctradepost\models\block\ (may want to use BlockBench)
[Done] Define the recipe to create the block in a .json file at src\main\resources\data\mctradepost\recipe

## Functional (Built) Building
Add the building class itself:
[Done] src\main\java\com\deathfrog\mctradepost\core\colony\jobs\buildings\workerbuildings\
[Done] Extend AbstractBuilding
[Done] Associate the building and its hut block in ModBuildingsInitializer (static block)
-[Done] This is also where you designate what modules the building will support once built.
-[Done] Define at least a new WorkerBuildingModule in MCTPBuildingModules.java specifying the primary stats.

Add any custom buildng modules that are needed:
- Define new modules in MCTPBuildingModules
- These may extend or reuse existing modules, or may require the development of new ones.

[Done] Define five building levels using Structurize.
- [Done] Place the structurize files in src\main\resources\blueprints\mctradepost\economic\

## Job for Building
Define the Job(s) associated with the Building
[Done] Add the job class at src\main\java\com\deathfrog\mctradepost\core\colony\jobs\
[Done] Register that job in ModJobsInitializer
[Done] Give it a constant ID and add it to the job list in MCTPModJobs
[Done] Assuming it has a custom AI (next step), hook into that at NewJob.generateAI()
[Done] Associate it with the models (later step) that animate the worker at ModModelTypeInitializer

Define the AI associated with the Job
[Done] Create a new class at src\main\java\com\deathfrog\mctradepost\core\entity\ai\workers\ overriding AbstractEntityAIInteract<JobClass, BuildingClass>
- Implement the desired logic.
- If desired, add a new work state visual icon: src\main\resources\assets\mctradepost\textures\icons\work\

Define the models that animate the worker for the Job
[DONE] Create new models at src\main\java\com\deathfrog\mctradepost\core\client\model\
[TODO] Create male and female variants of the model textures at src\main\resources\assets\mctradepost\textures\entity\citizen\default\
[DONE] Register the models at ModModelTypeInitializer

## Building Research
If the building is meant to be unlocked via research or progression, set that up with an effect using a json file at src\main\resources\data\mctradepost\researches\effects

 */

// TODO: For building unlock effects see CustomRecipe.isUnlockEffectResearched for a possible workaround to Minecolonies framework appearing to not handle effects on non-minecolonies buildings.
// TODO: Check organization of Economic Style (Icon leads only to marketplace, resort present but separate)
// TODO: RESORT Custom block item and recipe for "lounging chair"
// TODO: RESORT Custom block item and recipe for "drink table"
// TODO: RESORT Custom item for "marguarita"
// TODO: RESORT Adjust the recipe for blockhutresort to use the "marguarita"

public class BlockHutResort extends MCTPBaseBlockHut{

    public static final String HUT_NAME = "blockhutresort";

    @Override
    public String getHutName() {
        return HUT_NAME;
    }

    @Override
    public BuildingEntry getBuildingEntry() {
        return ModBuildings.resort;
    }
    
}
