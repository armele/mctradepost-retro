# Tests

## Basic
Verify mod loads in singleplayer local.
- Verify world starts.
Verify mod loads on dedicated server.
- Verify world starts.
Verify the configuration screen is available.
Verify a configuration change syncs between client and server in multiplayer (for example, trade coin value).

## Marketplace

Test shopkeeper ability to place items in frames for selling.
- Identify broken frames
- Replace broken frames with a building repair action

Test conversion of items to Account Balance
- Stats kept
- Balance maintained

Test ability to withdraw Trade Coins using the Advanced Clipboard
- Configured coin value used
- Coin generated
- Coin able to be redeposited

## Wishing Well

Test structure identification
- Right-click thrown coin
- Q-thrown coin
- Inventory drop-out of coin

Test broken structures cannot be used

Test valid structures generate appropriate ritual.

Test in-progress incomplete rituals persist across server reboots.

Test Wish of Sowing
- Either half of a configured scarecrow binds to the same field position.
- Unconfigured, unregistered, unsupported, and unauthorized fields reject binding.
- Flat and terraced fields plant every eligible air block without replacing existing crops or obstructions.
- Dirt and recognized farmland convert to the crop's required farmland; stone and decorative ground remain unchanged.
- Newly converted vanilla and MineColonies farmland starts fully hydrated, then dries normally if the field lacks water.
- Melon and pumpkin stems are not planted directly beside another stem.
- Initial binding consumes one matching seed from the player's inventory and seals it in the wish.
- Rebinding to a field with the same seed retargets the wish without consuming or returning a seed.
- Rebinding to a differently seeded field consumes the new seed and returns the old seed to inventory or drops it when full.
- Rebinding without the newly required seed leaves the original binding and sealed seed unchanged.
- Execution rejects a field whose configured seed no longer matches the sealed seed.
- A different-colony well, an unloaded field, a missing field, and a full field fail without consuming the wish or coin.
- A successful wish consumes one gold coin, marks the field planted, and reports the planted count.

## Advanced Clipboard
Able to register it against the colony.
Able to see everything on the regular clipboard.
Able to trim down to player-only items.
Able to extract coins from the marketplace hut block

## JEI
 Verify ritual recipes appear.
 Verify the mod loads WITHOUT JEI installed.
