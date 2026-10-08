# BSP-Core

**The companion mod for the Build Secure Protect (BSP) modpack.** Build a base, protect your Shatter Totem, raid other players for theirs, and climb the season leaderboard.

Every player starts with one **Shatter Totem**. It cannot be crafted or destroyed, but it can be stolen. Everything else in the mod is there to help you defend your totem, take someone else's, or grow rich enough to upgrade it.

- Minecraft 1.20.1, Forge 47 or newer, Java 17
- Needed on both the client and the server
- Optional: JEI (recipes and build guides), Patchouli (the in-game guide book), Jade (block info), any RF mod (power for the machines), Mekanism, Create or Thermal (crushers)

## The Shatter Totem

- **Carry it or place it.** A placed totem projects your base upgrades; a carried one powers your personal upgrades.
- **Steal and defend.** Start a steal at a rival's totem and stay close until the timer runs out, and it is yours with every upgrade on it. The owner is warned. No upgrade can make a totem impossible to steal. That rule is not going to change.
- **Five tiers, six paths, twenty-four upgrades.** Carried: Mining Speed, Swiftness, Featherfall, Night Sight, X-ray, Damage, Resistance, Vitality, Bouncy. Base: Fortify, Alarm, Ward, Deadlock, Cloaking, Healing Aura, Sanctuary, Overclock, Recall, Output, Anchor, Survey. Raid: Lockpick, Shroud, Recall Block.
- **Auras are cubes.** Radius 5 means 11 x 11 x 11 blocks, so you can build right to the edge. Switch the drawings off in the totem panel if you prefer.
- **Friends.** The ACCESS tab lets up to eight players through your Alarm and Ward, buy upgrades, or open your machines. The list is wiped if the totem is stolen.
- **Chunk loading.** The Anchor and Survey upgrades keep chunks around the totem loaded while you are away, picked on a map in the totem panel. A projector can spend the same allowance somewhere else.
- **Totem Compass.** Points to your own totem, or for a while to the nearest rival's.
- **Scores and seasons.** Every totem scores by its tier. Score Screens show the live leaderboard, the rules, or the season's prizes.

## Wave Plasma

A placed totem gives off Wave Plasma, 100 mB a tick and up to 6,000 with the Output upgrade. Pipe it somewhere and the totem's base powers appear there too. Bottle it and you can carry its powers around without the totem. This is where most of the new blocks live.

- **Extractor** under the totem. **Interface** touching it. **Cables** out to wherever you need the aura. Interfaces that touch join into one body, up to twelve blocks, and each face sends up to 1,000 mB/t, so a big totem needs a big group and good cables: Tetrium carries 250 mB/t, Magnatite 500, Illyrium 1,000.
- **Projector Base and Projector.** The base is a tank the projector stands on. 100 mB/t arriving and it projects; it burns 20 mB/t, so a full base runs on for a few minutes if a cable is cut. Right-click the projector to choose which powers it receives.
- **Pressure is shared.** An interface splits its plasma equally between the runs leaving it, and a run that needs less leaves the rest to the others. Right-click any interface and you get the whole network in 3D with the mB/t on every cable. Jade shows the same block by block.
- **Repeaters** start a fresh run for a tenth of the pressure. **Valves** cap a run from a dial or shut it with a lever or redstone.
- **Colours and ends.** Dye cables in any of the sixteen colours and they only join their own colour, so runs cross without mixing. The wrench sets each end of a cable to Normal, Output, Input or Off, or links two colours.

- **Battery Charger.** Cable into its back. Fills the battery or cell in it and stamps powers from the interface into it.
- **Plasma Batteries** hold Base powers. Stand a charged one on an extractor instead of a totem and everything downstream keeps working while the totem is away, or stolen.
- **Power Cells** hold Carried powers. Fit one in the **Wave Emitter**, put the emitter in your offhand, and you have the cell's powers at 20 mB/t. The totem stays safe at home.

## Three metals

Tetrium is where you start, Magnatite comes next, and Illyrium is the slow, valuable one. Ores drop raw chunks like vanilla iron; the machines take the chunks and the blocks alike.

## Machines

Two single-block machines get you going. Four multiblocks do the heavy work. Every multiblock shows ghost blocks where its parts go, and sneak + right-click on a controller opens a step-by-step Assembly Guide. JEI opens the same guide if you hold Shift over a part.

## Shatter Coins

Coins pay for totem upgrades and much else. You press them yourself, in real time: 12 hours for a Copper coin, 7 days for an Illyrium one. The factory keeps working while you are offline.

- **Coin Vault.** A safe for coins that pays interest. Vault blocks join into one vault of up to 3 x 3 x 3. Rivals can pick the lock for a quarter of what is inside.

## Decoys and base blocks

- **Decoy Totems.** A fake totem that pulls rival compasses toward it. To everyone but you it looks like the real thing, until they try to steal it and its traps go off.

## Every block and item

## Every crafting recipe

All recipes are in JEI too, and the guide book walks through each chain.

## For server owners

- **One config file:** `serverconfig/bsp_core-server.toml`. Costs, times, ranges, limits and prices are all there, including the `plasma`, `effects` and `chunks` sections.
- **Admin panel:** an Admin Rack block or `/bsp admin` shows every player's totems, positions and vault coins, with teleport and reset tools. Moderators get a read-only view. `/bsp totem buff` and `/bsp totem tier` set powers for testing.
- **Seasons:** end a season from the panel to pay prizes to the top three and hand everyone a fresh totem. Prizes are real items you place in the panel, and can be shown on Score Screens.
- **Anti Totem Block:** keeps totems and chosen machines out of spawn areas, hubs and arenas, in a zone of up to 256 blocks each way.
- **Networks (experimental):** optional MySQL or MariaDB support shares first-join totems, limits, the leaderboard, vault totals, seasons, prizes and steal warnings across several servers. Not tested on a live database yet.

## Status

Made for the BSP modpack and still early. Expect bugs and balance changes, especially around Wave Plasma, which is new in 0.3.0. The pictures of machines on this page are renders of the in-game models.

Please report problems at https://github.com/Mrgregles/BSP-Core/issues
