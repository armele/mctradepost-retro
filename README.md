# MC Trade Post  

## Description

This mod extends the MineColonies mod.  It, and it's dependencies, are required for MC Trade Post to function. MineColonies base functionality is not altered. The team supporting MineColonies has created a great mod that has provided hours of fun, and inspired these small additions.

MC Trade Post introduces an economic system which allows the selling of excess items for Trade Coins (‡). These coins can be used for a variety of purposes, such as bribing raiders to leave the town alone, unlocking vacations and resorts at which your colonists can improve stats and get happier, and other additional upgrades and features.

### In-Game How-To

Craft a Marketplace hut. Using the build tool, choose the "Economics" style from the MC Trade Post mod, and contruct the Marketplace as you would any other hut. Interactions for the MC Trade Post mod are handled primarily in the Marketplace interface on that hut block.  

### Buildings
#### Marketplace
Gives you a place for a Shopkeeper to work. They will sell various trinkets (configured in the hut block) to the visitors in the colony, generating Trade Coins (‡).

#### Resort
A resort attracts your citizens who are feeling overworked. Guest Services will tend to vacationing citizens, who can relax and repair their adverse stat, and generate some Trade Coins (‡) for the colony while taking a break from work.

### Jobs
The Shopkeeper works in the Marketplace, keeping the shelves stocked and ordering up new supply as needed.

Guest Services staffs the resort, finding out what the vacationers need to relax, and bringing it to them.

The Bartender also works at the resort, mixing up delicious treats that help the vacationers relax.

### Custom Items

Marketplace Hut - Supports the Marketplace building and the Shopkeeper job.  
Advanced Clipboard - Just like the regular clipboard but with a button that filters the outstanding needs down to only those expected to be fulfilled by a player.  
Trade Coin - These coins can be minted from the marketplace by sneak-right-clicking your advanced clipboard on the Marketplace hut. The hut must be upgraded, first! Trade Coins will (eventually) be used as the basis for triggering most Trade Post mod features.  Coins can be used at a Wishing Well to trigger certain effects.

### Custom Blocks

Marketplace Hut Block - Block implementation of the Marketplace building.

Resort Hut Block - Block implementation of the Resort building.

### Configuration
The value of a Trade Coin can be configured.
The level of the marketplace required to mint coins can be configured.

### Making Wishes
When you throw a coin into a wishing well with another object, your wish might come true!
Currently implemented or in-progress:
- Kill all zombies nearby (1 Coin + Zombie Flesh)
- Terminate an in progress raid (1 Coin + 1 Iron Sword) NOT YET FULLY IMPLEMENTED

### Citizen Behaviors
Citizens who have been worked too hard (low Adverse skill) will occassionally go on vacation, where their Adverse skill is repaired, and some income for the colony is generated.

### Visitor Behaviors
Visitors will go shopping at the marketplace, once built.

## Installation

Download the appropriate mctradepost-x.y.z.jar file from Github at the root project directory.  
Copy that Jar file into your mods directory.  
Note that MineColonies and its dependencies must be present to work.  

### Compatibility Reference

mctradepost-0.3.003 -> MineColonies 1.1.950 (Minecraft 1.21.1)  

## Style Packs
### Economic
This style pack has the default building designs for the new hut blocks introduced by MC Trade Post

### Hills
This style pack has buildings designed for use in hilly terrain, with relatively small footprints.

## Building Design Reference
### Marketplace
The marketplace building should have two item frames or glowing item frames for each level of the building.
These frames should be mounted in empty (air) blocks tagged with the "display_shelf" tag.  (Apply the tag to a placeholder item, then remove that placeholder and put the frame in.)

### Wishing Well
A wishing well can be created by surrounding a 2x2 puddle of water (depth 1) with stone bricks.  (Future designs will be more aesthetically pleasing.)

## Current Status

This mod can best be described as a "pre-alpha" state. It functionality may change rapidly and without warning.

Resort:
- Introduce stat-specific recipes (currently a "placeholder" recipe is used for testing)


### Roadmap (Roughly Prioritized)

Logic: Enable building level locking and implement for new buildings.
Building: Pet Shop (enables pet functions, pet-related colony roles.)
Pets: animal fetchers
Pets: happiness multiplier
Pets: burnout mitigator
Pets: sickness decreased
Pets: scarecrow effect
Building Effect: Happiness based on colony wealth per citizen  
Item: In-Game Instructions
Building Effect: Autominting + Delivery to warehouse of coins?
Wishing Well Rituals: Weather changing
Wishing Well Rituals: Summon Trader
Research: Unlock Trader Recipes
Jobs: Orchards
Jobs: Bartender / brewmaster / mixologist at resort
Building: Recycling
Building Effect: Disenchanting
Building: Train station (allows intra-colony trade)
Assembling colonies into collections (empire, state, etc.)  

Random ideas that may never be implemented:
Advanced Guard Towers (prevent mob spawning within an area of effect).

### Complete

Marketplace implementation for selling items.  
Ability to turn income into coin items (and vice-versa).

## Additional Resources

Community Documentation: [Neoforge documentation](https://docs.neoforged.net/)  
NeoForged Discord: [Neoforge discord](https://discord.neoforged.net/)  
Minecolonies: [MineColonies Home](https://minecolonies.com/)  
