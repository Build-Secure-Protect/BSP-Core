# BSP-Core 0.1.0: first release

The companion mod for the Build Secure Protect (BSP) modpack. Build a base, protect your Shatter Totem, and raid other players for theirs.

This is an early test release. Expect bugs and balance changes.

## Requirements

- Minecraft 1.20.1
- Forge 47 or newer
- Java 17
- Needed on both the client and the server

**Optional, the mod works without them:**

- JEI: recipe pages for every machine, and "hold Shift" on a multiblock block for its Assembly Guide
- Patchouli: the in-game guide book, the BSP Field Guide
- Mekanism, Create or Thermal Expansion: crushing recipes for the new metals
- AllTheModium: the new ores also generate in its Mining dimension
- Any Forge Energy (RF) mod: to power RF Upgrades and the Shatter Coin Factory

## The Shatter Totem

- Every player is given one Shatter Totem on first join. It cannot be crafted or destroyed.
- Carry it, or place it in your base. Placed, it shows its owner and its upgrades.
- Other players can steal a placed totem: start the steal at the totem, stay close until the timer runs out, and it becomes yours with all its upgrades. The owner is warned. Killing the thief, or the thief leaving the area, cancels it.
- Dying to another player while carrying your totem places it where you fell. Logging out places a carried totem.
- A totem can never be made impossible to steal. Blocks near it can always be mined, and every steal has a time limit.

### Totem tiers and upgrades

- The totem has five tiers, I to V. Each tier uses its own Shatter Coin: Copper, Gold, Diamond, Netherite, Illyrium.
- Raising the totem a tier costs coins of the current tier plus XP levels.
- 16 upgrades on five paths. Each one unlocks the next on its path, and each totem tier adds two more levels.
  - **Carried** (totem in your offhand): Mining Speed, Swiftness, Featherfall, Night Sight, Damage, Resistance, Vitality
  - **Base** (totem placed): Fortify, Alarm, Ward, Deadlock, Healing Aura, Sanctuary, Overclock
  - **Raid** (in the thief's offhand): Lockpick, Shroud
- Every level is paid for with a specific coin and XP levels, shown on the upgrade tree screen.

### Totem Compass

- Points to your own nearest totem.
- Load it with Shatter Coins to track the nearest rival totem for a while, followed by a cooldown that upgrades can shorten.

## Ores and materials

**Ores**

- Tetrium Ore, Deepslate Tetrium Ore (iron pickaxe or better)
- Illyrium Ore, Deepslate Illyrium Ore, End Stone Illyrium Ore (diamond pickaxe or better)
- Both generate in the Overworld, and in the AllTheModium Mining dimension when that mod is installed.

**Tetrium**

- Tetrium Nugget, Tetrium Ingot, Tetrium Dust, Tetrium Slag

**Illyrium**

- Dirty Illyrium Ingot, Dirty Illyrium Nugget, Dirty Illyrium Dust
- Pure Illyrium Dust, Illyrium Nugget, Illyrium Ingot

**Crushing by hand:** a pickaxe and an ingot on a crafting table gives dust or nuggets by chance, for packs with no crusher.

## Machines

**Single block**

- **Tetrium Crucible:** smelts Tetrium Ore into nuggets and slag. Burns furnace fuel.
- **Combination Forge:** nine nuggets into an ingot, and Tetrium Ingots into Tetrium Plates. Runs on fuel, or on RF with an RF Upgrade.

**Multiblock**

- **Illyrium Crucible** (3 x 3 x 3): runs on lava. Smelts Illyrium Ore, and alloys Pure Illyrium Dust into Illyrium Nuggets.
- **Illyrium Refinery:** washes Dirty Illyrium Dust into Pure Illyrium Dust with water and a filter.
- **Shatter Coin Factory:** built in slices, 1 wide, 2 high and 3 long. Up to 10 slices side by side join into one machine.

**Multiblock blocks**

- Illyrium Crucible Controller, Illyrium Refinery Controller
- Illyrium Casing, Illyrium Tank Glass, Illyrium Core
- Lava Pylon, Refinery Pump, Item Hatch
- Shatter Coin Factory Controller, Factory Frame, Factory Press, Factory Blank Hatch, Factory Power Port
- Motivator: sits on top of a factory slice and shortens its press time

**Machine features**

- Animated models on every machine
- A shared screen style with a progress dial, gauges, a list of what is missing, and a power switch
- Per-side or per-port input and output settings, with colour-coded sockets on the multiblocks
- A redstone signal pauses a machine
- Ghost blocks show where each part of an unfinished multiblock goes
- Assembly Guide: sneak + right-click an unfinished controller for a step-by-step 3D build guide

**Upgrades and consumables**

- RF Upgrade Mk I, Mk II, Mk III: let a machine run on RF, faster
- Illyrium Forge Upgrade: lets the Combination Forge make Illyrium Ingots
- Iron, Diamond, Netherite and Illyrium Filters for the Refinery

## Machine components

Crafted parts that the machine blocks are built from:

- Slag Brick, Tetrium Plate, Machine Chassis, Tetrium Coil, Drive Motor
- Circuit Substrate, Basic Control Circuit
- Crucible Control Circuit, Refinery Control Circuit, Mint Control Circuit
- Thermal Lining, Conveyor Belt, Press Die
- Resonance Crystal, Illyrium Processor

Every block and item above has a crafting or machine recipe, except the ores and the Shatter Totem.

## Shatter Coins

- Five coins: Copper, Gold, Diamond, Netherite, Illyrium.
- Each starts as a Shatter Blank, is built up into that tier's Coin Blank, and is pressed in the Shatter Coin Factory.
- Pressing takes real time, from 12 hours for Copper to 7 days for Illyrium by default, and carries on while you are offline.
- The factory needs RF. Coins are collected from its tray by hand and cannot be piped out.
- Copper and Gold coins can be found rarely in dungeon, mineshaft and stronghold chests, and Diamond coins in End City and Ancient City chests.

## Progression help

- **BSP Field Guide:** a guide book given on first join and craftable from a Book and a Tetrium Nugget (needs Patchouli).
- **Advancements:** a BSP-Core tab with 20 advancements from your first totem to your first Motivator.
- **JEI pages** for every machine, coin pressing and hand crushing.

## For server owners

- **Config:** `serverconfig/bsp_core-server.toml`. Costs, times, radii, limits and prices are all configurable.
- **First-join totem:** on by default. On a network, leave it on for the hub only.
- **Operator commands:** `/bsp totem give`, `reset`, `locate`, `owner`, and `/bsp storage status`, `test`, `migrate`.
- **Operator tools in game:** an operator tab on the totem screen, and a demo switch on every machine that plays its working animation with nothing in it.
- **Network storage (experimental):** optional MySQL or MariaDB support so that first-join totems and the factory limit apply across several servers. Off by default. It has not been tested on a live database yet.
- Each player may own up to 10 factory slices.

## Known limitations

- This is the first public build and has had limited testing.
- MySQL storage is untested.
- Steal warnings and compass tracking do not cross servers yet.
- Land-claim mods may interfere with stealing; this is not handled yet.

Please report problems at https://github.com/Mrgregles/BSP-Core/issues
