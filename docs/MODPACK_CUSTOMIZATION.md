# MC Trade Post Modpack Customization Guide

This guide is for modpack authors who want to tune `mctradepost` so it fits their economy, progression, loot balance, and recipe ecosystem.

Most of the mod's customization surface falls into three buckets:

1. NeoForge config in `config/mctradepost-common.toml`
2. Datapack content under `data/mctradepost/...`
3. Tag-based integration under both `data/mctradepost/tags/...` and `data/minecolonies/tags/...`

## Where To Put Overrides

For pack-level customization, prefer a datapack instead of editing the jar.

Typical world-local layout:

```text
<world>/datapacks/<your_pack>/pack.mcmeta
<world>/datapacks/<your_pack>/data/mctradepost/...
<world>/datapacks/<your_pack>/data/minecolonies/...
<world>/datapacks/<your_pack>/data/neoforge/...
```

For shipped modpacks, you can also bundle the datapack through your normal pack distribution flow.

Use `"replace": false` when you want to extend the mod's defaults.
Use `"replace": true` when you want to fully take ownership of a file.

## Config File

Server/common config is registered as `mctradepost-common.toml`.

Main categories:

- `marketplace`
  - `tradeCoinValue`: base value of a trade coin
  - `economicScaling`: global bonus/penalty applied to item values used by the marketplace
  - `mintingLevel`: marketplace level required to mint coins
  - `shoppingCooldown`: visitor shopping cooldown
  - `registerSoundChance`: chance for sale sound effects
  - `shoppingChance`: visitor shopping chance per marketplace level
  - `tradeCurrency`: item id used as trade currency
- `resort`
  - `vacationMaxChance`
  - `advertisingCooldown`
  - `maxAdSaturation`
  - `vacationSusceptibilityThreshold`
  - `vacationImmunityThreshold`
  - `vacationHealing`
  - `vacationIncome`
  - `guestsPerResortLevel`
- `recycler`
  - `grindersPerLevel`
  - `baseRecyclerTime`
  - `flawlessRecycling`
  - `warehouseInventoryCooldown`
- `station`
  - `trackValidationFrequency`
  - `baseTradeSpeed`
  - `importsPerLevel`
- `petshop`
  - `petsPerLevel`
- `outpost`
  - `outpostEnabled`
  - `maxDistance`

Notes:

- `tradeCurrency` must resolve to an item in the `#mctradepost:base_currency` tag. If it does not, the mod falls back to `mctradepost:mctp_coin`.
- Rituals do not use `tradeCurrency`; they read their own `coin_type` from ritual JSON.
- The config syncs from server to clients, so pack balance should be done server-side.

## Economy Datapacks

There are two related economy files, and they serve different purposes.

### `data/mctradepost/item_values.json`

This is the live runtime value table used by the marketplace.

Format:

```json
{
  "replace": false,
  "values": {
    "minecraft:diamond": 800,
    "minecraft:emerald": 250
  }
}
```

Use this when you already know the values you want.

### `data/mctradepost/item_value_seeds.json`

This is not the live value table. It is a seed file for the built-in generator command:

```text
/mctp generateItemValues
/mctp generateItemValues dryRun
```

Format:

```json
{
  "replace": false,
  "tag_policy": "MIN",
  "tag_overrides_items": false,
  "tag_values": {
    "#c:ingots/iron": 90,
    "#c:gems/diamond": 800
  },
  "values": {
    "minecraft:stick": 4,
    "minecraft:coal": 24
  }
}
```

Behavior:

- `tag_policy` may be `MIN`, `MAX`, or `FIRST`
- `tag_overrides_items: false` means explicit item entries win over tag expansion
- The generator writes a datapack to `<world>/datapacks/mctp_generated/data/mctradepost/item_values.json`

Recommended workflow:

1. Seed a few core resources in `item_value_seeds.json`
2. Run `/mctp generateItemValues dryRun`
3. Add seeds for the most common unknown ingredients
4. Run `/mctp generateItemValues`
5. Copy the generated `item_values.json` into your own datapack and hand-tune from there

## Datapack-Driven Systems

### Burnout Remedies

Folder:

```text
data/mctradepost/burnout/*.json
```

Each file is keyed by MineColonies skill name and supplies the remedy items plus flavor text for resort burnout handling.

Example:

```json
{
  "remedy": [
    { "id": "mctradepost:protien_shake", "count": 1 },
    { "id": "mctradepost:napkin", "count": 1 }
  ],
  "message": "I never get any exercise!"
}
```

The included files are named after skills such as `athletics.json`, `knowledge.json`, `mana.json`, and so on.

### Recycling Blacklist

Folder:

```text
data/mctradepost/recycling_blacklist/*.json
```

Format:

```json
{
  "replace": false,
  "deny": [
    { "type": "tag", "id": "c:foods" },
    { "type": "predicate", "id": "is_food" }
  ],
  "allow": [
    { "type": "item", "id": "minecraft:golden_apple" }
  ]
}
```

Supported rule types:

- `item`
- `tag`
- `namespace` or `mod` or `modid`
- `predicate`

Currently implemented predicate ids:

- `is_food`

Important behavior:

- Files are processed in sorted order
- `allow` rules override `deny` rules
- `replace: true` clears previously accumulated rules
- `rules` is accepted as an alias for simple deny-only files
- For `namespace`, `mod`, or `modid` rules, `id: "*"` matches all item namespaces

This is one of the cleanest integration points for excluding quest items, progression keys, curios, or mod-specific components from recycling.

Example: deny recycling from every mod by default, then opt specific mods back in:

```json
{
  "replace": false,
  "deny": [
    { "type": "modid", "id": "*" }
  ],
  "allow": [
    { "type": "modid", "id": "minecraft" },
    { "type": "modid", "id": "domum_ornamentum" }
  ]
}
```

### Wishing Well Rituals

Folder:

```text
data/mctradepost/rituals/*.json
```

Format:

```json
{
  "companion_item": "mctradepost:wish_health",
  "companion_item_count": 1,
  "effect_type": "community",
  "target": "",
  "radius": -1,
  "coin_cost": 1,
  "coin_type": "mctradepost:mctp_coin_diamond"
}
```

Use this to change:

- which item starts a ritual
- how many companion items are needed
- which coin item the ritual consumes
- target/radius data used by the ritual processor

Important caveat:

- The codebase includes a server reload listener for rituals, but its registration is currently commented out in `MCTradePostMod`. The client reload listener is active. Treat ritual overrides as supported data, but test them carefully on a real server and after restart.

### Recipes

Folder:

```text
data/mctradepost/recipe/*.json
```

These are standard datapack recipes plus a few custom recipe types provided by the mod:

- `mctradepost:unique_tag_shapeless`
- `mctradepost:potion_shapeless`
- `mctradepost:deconstruction`

Example of the custom unique-tag recipe:

```json
{
  "type": "mctradepost:unique_tag_shapeless",
  "base": { "item": "minecraft:bowl" },
  "tag": "mctradepost:bar_nut_seeds",
  "count": 4,
  "result": {
    "id": "mctradepost:bar_nuts",
    "count": 8
  }
}
```

This recipe requires:

- exactly one `base` ingredient
- exactly `count` inputs from the named tag
- each tagged ingredient must be a different item id

That makes tag composition part of recipe balance.

### Custom Advancement Triggers

The mod registers several custom advancement triggers under the `mctradepost` namespace. These can be used in your own advancement JSON if you want pack-specific progression, quests, or tutorials to react to Trade Post gameplay.

Folder to review for examples:

```text
data/mctradepost/advancement/mctradepost/*.json
```

Supported trigger ids:

- `mctradepost:pet_trained`
  - fired when the Animal Trainer successfully acquires or trains a Trade Post pet
- `mctradepost:colony_connected`
  - fired when a Station successfully validates a remote colony connection
- `mctradepost:recycle_item`
  - fired when a recycling process finishes
- `mctradepost:complete_vacation`
  - fired when a citizen completes a resort vacation
- `mctradepost:make_wish`
  - fired when a wishing well ritual completes successfully
- `mctradepost:runs_on_stew`
  - fired when the Stewmelier successfully serves stew
- `mctradepost:rare_find`
  - fired when a player purchases a tier-4 Rare Find from the thrift shop

Current limitation:

- these triggers are simple event triggers and currently only expose the standard optional `player` predicate in their codec
- they do not currently provide additional custom trigger fields for filtering by item, building, tier, or similar event metadata

Minimal example:

```json
{
  "criteria": {
    "wish": {
      "trigger": "mctradepost:make_wish"
    }
  }
}
```

### Research Trees And Effects

Folders:

```text
data/mctradepost/researches/economic.json
data/mctradepost/researches/economic/*.json
data/mctradepost/researches/effects/*.json
```

These are MineColonies research data files, so they can be overridden like other MineColonies datapack content.

Use them to rebalance:

- research requirements
- research costs
- parent dependencies
- granted effect levels

This is the right place to move Trade Post progression later or earlier in a pack.

### Loot Modifiers And Pet Loot Tables

The mod uses both global loot modifiers and explicit loot tables.

Global loot modifier:

```text
data/neoforge/loot_modifiers/global_loot_modifiers.json
data/mctradepost/loot_modifiers/add_trade_coin_entities.json
```

The included modifier grants trade coins to entities matching `#mctradepost:coindroppers`.

Pet scavenging uses explicit loot tables:

```text
data/mctradepost/loot_table/pet/amphibious_scavenge/*.json
data/mctradepost/loot_table/pet/vegetation_scavenge/fruit/*.json
data/mctradepost/loot_table/pet/vegetation_scavenge/leaves/*.json
data/mctradepost/loot_table/pet/vegetation_scavenge/groundcover/*.json
```

The associated trigger tags are:

- `#mctradepost:amphibious_scavenge` -> `pet/amphibious_scavenge/<block_path>.json`
- `#mctradepost:scavenge_fruit` -> `pet/vegetation_scavenge/fruit/<block_path>.json`
- `#mctradepost:scavenge_leaves` -> `pet/vegetation_scavenge/leaves/<block_path>.json`
- `#mctradepost:scavenge_groundcover` -> `pet/vegetation_scavenge/groundcover/<block_path>.json`

If you add a block to one of these scavenge tags, you should usually also provide the matching loot table for that block path.

## Tag-Driven Logic

Tags are the most important integration layer for modpacks.

### Currency And Economy

- `data/mctradepost/tags/item/base_currency.json`
  - valid trade currencies for `tradeCurrency`
- `data/mctradepost/tags/item/rarefinds_tier1.json`
- `data/mctradepost/tags/item/rarefinds_tier2.json`
- `data/mctradepost/tags/item/rarefinds_tier3.json`
- `data/mctradepost/tags/item/rarefinds_tier4.json`
- `data/mctradepost/tags/item/rarefinds_blacklist.json`

Rare Finds notes:

- Each tier has a 20% chance to roll directly from its tag before falling back to chest/fishing/wandering-trader sources
- If an item appears in multiple rarefinds tiers, the mod treats the highest tier as owner and logs a warning
- `rarefinds_blacklist` blocks an item from appearing even if other sources would roll it

### Crafting And Food Categories

- `data/mctradepost/tags/item/fruit.json`
- `data/mctradepost/tags/item/vegetable.json`
- `data/mctradepost/tags/item/meat.json`
- `data/mctradepost/tags/item/seasoning.json`
- `data/mctradepost/tags/item/fermentables.json`
- `data/mctradepost/tags/item/dairy.json`
- `data/mctradepost/tags/item/stew_ingredients.json`
- `data/mctradepost/tags/item/bar_nut_seeds.json`

These drive multiple recipes and worker behaviors. If you add food items from other mods, extending these tags is usually better than rewriting recipes one by one.

### MineColonies Crafter Integration

Trade Post adds MineColonies crafter tag hooks for its own workers.

Folders:

```text
data/mctradepost/tags/item/bartender_ingredient.json
data/mctradepost/tags/item/bartender_ingredient_excluded.json
data/mctradepost/tags/item/bartender_product.json
data/mctradepost/tags/item/bartender_product_excluded.json
data/mctradepost/tags/item/dairyworker_ingredient.json
data/mctradepost/tags/item/dairyworker_ingredient_excluded.json
data/mctradepost/tags/item/dairyworker_product.json
data/mctradepost/tags/item/dairyworker_product_excluded.json
```

The mod also ships MineColonies crafter recipe files under:

```text
data/minecolonies/crafterrecipes/bartender/*.json
data/minecolonies/crafterrecipes/dairyworker/*.json
data/minecolonies/crafterrecipes/enchanter/*.json
```

Use these together:

- crafter recipes define specific worker recipes
- crafter tags control what each worker is allowed to accept as inputs and outputs

### Outposts, Stations, Lamps, And Pets

- `data/mctradepost/tags/item/outpost_crops.json`
  - the scout/outpost logic keeps one of each tagged crop behind so replanting still works
- `data/mctradepost/tags/block/track.json`
  - blocks counted as valid station track
- `data/mctradepost/tags/block/lamp_bases.json`
  - extra support blocks that lamps may attach to
- `data/mctradepost/tags/block/scavenge_fruit.json`
- `data/mctradepost/tags/block/scavenge_leaves.json`
- `data/mctradepost/tags/block/scavenge_groundcover.json`
- `data/mctradepost/tags/block/amphibious_scavenge.json`
  - pet trainer scavenging targets
- `data/mctradepost/tags/block/icy.json`
  - used by pathing helpers
- `data/mctradepost/tags/entity_type/coindroppers.json`
  - entity types eligible for trade coin drops via the global loot modifier

There is also a `trained_pets` entity tag in the data pack, but in this codebase it does not appear to be wired into gameplay yet.

### MineColonies Block Tags

The mod also contributes to MineColonies tags under `data/minecolonies/tags/block/...`.

Examples:

- `tier1blocks` through `tier5blocks`
- `pathblocks`
- `cropblocks`

These are worth overriding if your pack changes building material progression or wants MineColonies to treat additional blocks as valid path or crop blocks.

## Practical Modpack Strategies

### If You Want A Different Currency

1. Add your currency item to `#mctradepost:base_currency`
2. Set `tradeCurrency` in `mctradepost-common.toml`
3. Adjust `tradeCoinValue` and your `item_values.json`
4. Review recipes like `to_tradecoin.json` if you want exchange behavior to match your economy

### If You Want Better Cross-Mod Food Support

1. Extend `fruit`, `vegetable`, `meat`, `dairy`, `seasoning`, and `stew_ingredients`
2. Extend bartender and dairyworker ingredient/product tags
3. Review burnout remedies so resort demand lines up with your food additions

### If You Want Custom Rare Finds

1. Put your desired items in the `rarefinds_tier*` tags
2. Remove unsuitable results through `rarefinds_blacklist`
3. Avoid tagging the same item in more than one tier

### If You Want Pack-Specific Pet Scavenging

1. Add blocks to the scavenge block tags
2. Add matching loot tables under the corresponding `pet/...` loot-table folders
3. Test harvest/reset behavior for fruit-bearing plants with unusual blockstate properties

### If You Want To Lock Down Recycler Abuse

1. Add blacklist rules by item, tag, namespace, or `is_food`
2. Use `allow` sparingly for exceptions
3. Prefer tag- or namespace-based rules over long item lists when possible

## Suggested Testing Checklist

- Reload datapacks and confirm logs do not show missing tags or invalid ids
- Verify marketplace currency still resolves correctly
- Test one resort burnout cure per skill you changed
- Test recycler blacklist allow/deny precedence
- Test one rarefinds roll from each tier
- Test one station route if you changed `#mctradepost:track`
- Test one pet scavenge target for every new block tag you added
- Restart a dedicated server once if you changed rituals

## High-Value Files To Review First

- `src/main/java/com/deathfrog/mctradepost/MCTPConfig.java`
- `src/main/java/com/deathfrog/mctradepost/api/util/ItemValueManager.java`
- `src/main/java/com/deathfrog/mctradepost/core/economy/ItemValueSeedLoader.java`
- `src/main/java/com/deathfrog/mctradepost/core/recycling/blacklist/RecyclingBlacklistManager.java`
- `src/main/java/com/deathfrog/mctradepost/core/event/burnout/BurnoutRemedyManager.java`
- `src/main/java/com/deathfrog/mctradepost/core/event/wishingwell/ritual/RitualDefinition.java`
- `src/main/java/com/deathfrog/mctradepost/core/ModTags.java`

Those files define most of the pack-author-facing behavior.
