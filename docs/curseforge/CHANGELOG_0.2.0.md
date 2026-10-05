# BSP-Core 0.2.0

Everything added and changed since 0.1.0. This is a large update and has had little testing in game: please report anything odd.

**Important for existing worlds:** several config values changed. If something looks wrong, delete `serverconfig/bsp_core-server.toml` in the world folder and let the mod write a fresh one.

## Scoring and seasons

- **Leaderboard.** Every totem scores by its tier: 1, 2, 4, 7 and 10 points for Tier I to V. Your score is the total for all your totems.
- **Score Screen.** A display block that joins into one screen of up to 8 wide and 6 high. It shows the leaderboard in three layouts, the scoring rules, or the season's prizes. Admins set the layout, font, text size, scroll speed and title.
- **Seasons.** An admin can end the season: the top three receive their prizes, every totem is removed, and every player gets one fresh Tier I totem, at once or at their next login.
- **Season prizes.** Ten item slots each for first, second and third place, filled by an admin with real items.
- **Holder reward.** A fourth row of items copied to every totem holder, once for each totem held, on demand or automatically every so many hours.
- **Full reset.** End season, plus every Shatter Coin removed from vaults, factory trays and players. It is locked until the admin types RESET.

## Coin Vault

- A safe for Shatter Coins, 27 slots per block. Vault blocks of the same owner join into one vault of up to 3 x 3 x 3, drawn as one wall of deposit boxes. A player may own 27 vault blocks.
- **Access list** of up to 8 players, and **security upgrades**: three lock levels and an alarm.
- **Lockpicking.** Players without access can pick the lock in 2 to 5 minutes and take a quarter of the coins at random. A small stash of Illyrium coins is never taken.
- **Interest.** XP levels and Tetrium Ingots for every 50 coin value stored across all your vaults, and up to 2 Illyrium Ingots every 3 days. It builds up offline, to a cap, and is redeemed at a vault.
- Anyone but the owner can mine a vault block in 30 seconds but gets no coins: they are kept for the owner.
- **Shatter Coins now stack to 12** instead of 64.

## Admin tools

- **Admin Rack** block and `/bsp admin`: a panel listing every player with their totems, tiers, coordinates and vault coins. Admins can teleport to players and totems, reset a player's totems, and run seasons. Moderators see everything but cannot change anything.
- **Admins and moderators** are named in the config. With no admins named, every operator is an admin.
- **Anti Totem Block.** Keeps Shatter Totems and chosen BSP blocks out of a box of up to 256 blocks in each direction. Every block is listed by name and can be allowed or blocked. Each zone has its own outline colour, shown to admins holding the block.
- Players who die inside a zone keep their totem. A totem that would be placed inside is put just outside instead.

## Magnatite and the Magnetic Centrifuge

- **Magnatite Ore** generates deep in the Overworld, a little more often than diamond, and needs a diamond pickaxe.
- New items: Magnatite Nugget, Magnatite Ingot, Charged Magnatite Ingot, Carbon Dust and the Copper Tetrium Coil.
- **Magnetic Centrifuge.** A multiblock one block high and three by three, stackable up to six layers. It always needs RF.
  - Separates one ore into 3 nuggets and 1 Carbon Dust in 45 seconds; up to 7 to 9 nuggets with six layers.
  - With a Copper Tetrium Coil, charges Magnatite Ingots: a 1 in 6 chance per try, up to 1 in 2 when stacked. A failed try keeps the ingot.
- The Combination Forge now also makes Magnatite Ingots.

## Decoy Totems

- A craftable fake totem that stands on a **Decoy Power Base** and draws 100 RF per tick.
- Rival Totem Compasses within its range point at it instead of a real totem.
- Everyone but the owner sees an ordinary Shatter Totem. The owner sees the decoy with a ghost of the totem around it.
- Trying to steal it sets off its traps and breaks it until the owner repairs it.
- Socket parts: Range Coils Mk I to III, Blast, Hex, Poison, Fatigue and Warp Charges, a Trap Amplifier and Reinforced Casings.
- New item: the Magnet Core. Five decoys per player.

## Totem Generators, Projectors and Cables

- **Totem Generator.** Goes under a placed totem; up to nine join into a three by three. Each sends two of the totem's base powers to its own projector.
- **Totem Projector.** Recreates those powers as a second aura for the totem's owner.
- **Totem Cables** in four kinds, with runs of 15, 25, 40 and 80 blocks. Powers lose strength along the cable, down to half at full length.
- **Reach Amplifiers** Mk I to III let a generator push further; a **Channel Expander** adds a third power.
- Only the totem's owner can open its generators. Joined generators share their RF.

## Networks (experimental)

With MySQL storage on, these are now shared between servers as well: the leaderboard, Coin Vault totals and the vault limit, seasons and full resets, prizes and the holder reward, delivery of items owed to a player, and warnings (steal attempts, alarms, vault break-ins) to an owner who is on another server. None of the MySQL features has been tested on a live database yet.

## Also new

- Crafting recipes for every new block and item. All new recipes use existing BSP parts; the totem extras need Charged Magnatite and Carbon Dust.
- Guide book entries for scoring and seasons, the Coin Vault, Magnatite, the Magnetic Centrifuge, Decoy Totems, and Generators and Projectors.
- JEI: a Magnetic Centrifuge category and information pages for all new blocks.
- Six new advancements.
- The mod's page link now points to its CurseForge page.

## Easier to see what is wrong

- A multiblock with the wrong block in one of its places now boxes that block in red, floats the right block above it, and names both when you right-click the controller.
- A Totem Projector with a signal but no RF of its own glows amber, and right-clicking a projector says what it is doing.

## Known limitations

- Very little of this update has been tested in game.
- MySQL features are untested.
- A full reset does not remove coins from ordinary chests; it is meant to go with a new map.
- A decoy can be told from a real totem on the F3 screen and by mods that name the block you are looking at.
