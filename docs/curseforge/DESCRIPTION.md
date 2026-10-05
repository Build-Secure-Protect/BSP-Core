<!--
BSP-Core: CurseForge description (Markdown).

How to use the pictures: upload the files from docs/curseforge/images/ to the project's
Images tab on CurseForge, copy each image's URL, and replace the matching IMAGE_URL_... marker
below. Each marker is named after its file, for example IMAGE_URL_01_machines_single is
01_machines_single.jpg. Delete this comment before pasting, or leave it: it is not shown.
-->

# BSP-Core

**The companion mod for the Build Secure Protect (BSP) modpack.** Build a base, protect your Shatter Totem, raid other players for theirs, and climb the season leaderboard.

Every player starts with one **Shatter Totem**. It cannot be crafted or destroyed, but it can be stolen. Everything else in the mod exists to help you defend your totem, take someone else's, or grow rich enough to upgrade it.

- Minecraft 1.20.1, Forge 47 or newer, Java 17
- Needed on both the client and the server
- Optional: JEI (recipes and build guides), Patchouli (the in-game guide book), any RF mod (power), Mekanism, Create or Thermal (crushers)

![The whole progression](IMAGE_URL_26_flow_progression)

## The Shatter Totem

- **Carry it or place it.** A placed totem projects your base upgrades; a carried one powers your personal upgrades.
- **Steal and defend.** Start a steal at a rival's totem and stay close until the timer runs out, and it is yours with every upgrade on it. The owner is warned. No upgrade can make a totem impossible to steal.
- **Five tiers, sixteen upgrades** on five paths: Mining Speed, Swiftness, Featherfall, Night Sight, Damage, Resistance, Vitality, Fortify, Alarm, Ward, Deadlock, Healing Aura, Sanctuary, Overclock, Lockpick and Shroud.
- **Totem Compass.** Points to your own totem, or for a while to the nearest rival's.
- **Scores and seasons.** Every totem scores by its tier. Score Screens show the live leaderboard, the rules, or the season's prizes.

## Three metals

Tetrium is where you start, Magnatite comes next, and Illyrium is the slow, valuable one.

![Ores and metals](IMAGE_URL_09_resources)

![How Tetrium is made](IMAGE_URL_22_flow_tetrium)

![How Illyrium is made](IMAGE_URL_23_flow_illyrium)

![How Magnatite is made](IMAGE_URL_24_flow_magnatite)

## Machines

Two single-block machines get you going. Four multiblocks do the heavy work. Every multiblock shows ghost blocks where its parts go, and sneak + right-click on a controller opens a step-by-step Assembly Guide.

![Single-block machines](IMAGE_URL_01_machines_single)

![Illyrium Crucible](IMAGE_URL_02_machine_illyrium_crucible)

![Illyrium Refinery](IMAGE_URL_03_machine_illyrium_refinery)

![Shatter Coin Factory](IMAGE_URL_04_machine_coin_factory)

![Magnetic Centrifuge](IMAGE_URL_05_machine_magnetic_centrifuge)

![Machine recipes: Tetrium and Illyrium](IMAGE_URL_20_machine_recipes_1)

![Machine recipes: Magnatite, crushing and coins](IMAGE_URL_21_machine_recipes_2)

## Shatter Coins

Coins pay for totem upgrades and much else. You press them yourself, in real time: 12 hours for a Copper coin, 7 days for an Illyrium one. The factory keeps working while you are offline.

![How Shatter Coins are made](IMAGE_URL_25_flow_coins)

- **Coin Vault.** A safe for coins that pays interest. Vault blocks join into one vault of up to 3 x 3 x 3. Rivals can pick the lock for a quarter of what is inside.

## Totem extras

- **Totem Generators and Projectors.** Put generators under your totem, run Totem Cable to a projector, and a second aura appears wherever you need it: over a mine, a farm or an outer wall. The longer the cable, the weaker the powers.
- **Decoy Totems.** A fake totem that pulls rival compasses toward it. To everyone but you it looks like the real thing, until they try to steal it and its traps go off.

![Totem Generators and the Totem Projector](IMAGE_URL_06_layout_generators)

![Decoy Totem](IMAGE_URL_07_layout_decoy)

![Coin Vault and Anti Totem Block](IMAGE_URL_08_layout_base_blocks)

## Every block and item

![Every block and item, 1 of 3](IMAGE_URL_10_items_1)

![Every block and item, 2 of 3](IMAGE_URL_11_items_2)

![Every block and item, 3 of 3](IMAGE_URL_12_items_3)

## Every crafting recipe

All recipes are also in JEI, and the in-game guide book walks through each chain.

![Crafting recipes, 1 of 7](IMAGE_URL_13_recipes_01)

![Crafting recipes, 2 of 7](IMAGE_URL_14_recipes_02)

![Crafting recipes, 3 of 7](IMAGE_URL_15_recipes_03)

![Crafting recipes, 4 of 7](IMAGE_URL_16_recipes_04)

![Crafting recipes, 5 of 7](IMAGE_URL_17_recipes_05)

![Crafting recipes, 6 of 7](IMAGE_URL_18_recipes_06)

![Crafting recipes, 7 of 7](IMAGE_URL_19_recipes_07)

## For server owners

- **One config file:** `serverconfig/bsp_core-server.toml`. Costs, times, ranges, limits and prices are all there.
- **Admin panel:** an Admin Rack block or `/bsp admin` shows every player's totems, positions and vault coins, with teleport and reset tools. Moderators get a read-only view.
- **Seasons:** end a season from the panel to pay prizes to the top three and hand everyone a fresh totem. Prizes are real items you place in the panel, and can be shown on Score Screens.
- **Anti Totem Block:** keeps totems and chosen machines out of spawn areas, hubs and arenas, in a zone of up to 256 blocks each way.
- **Networks (experimental):** optional MySQL or MariaDB support shares first-join totems, limits, the leaderboard, vault totals, seasons, prizes and steal warnings across several servers. It has not been tested on a live database yet.

## Status

This is an early release made for the BSP modpack. Expect bugs and balance changes. The pictures of machines on this page are renders of the in-game models.

Please report problems at https://github.com/Mrgregles/BSP-Core/issues
