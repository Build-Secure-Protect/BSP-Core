# BSP-Core

**The mod at the heart of the Build Secure Protect modpack.** Build a base, secure it, protect your Shatter Totem, and go and take everyone else's! Every block and machine in here is about the totem: keeping yours, stealing theirs, or getting rich enough to upgrade it.

- Minecraft 1.20.1, Forge 47 or newer, Java 17. Needed on the client and the server.
- Plays well with JEI or EMI (every recipe and machine job), Patchouli (the in-game guide book), Jade (block info), any RF mod (power for the machines), and the pack's crushers: Mekanism, Create, Thermal, Ender IO, Railcraft, Integrated Dynamics and Electrodynamics all turn BSP ingots into dust.

## The Shatter Totem

<!-- plate: totem -->

You get one the moment you join, and that is the only way to get one. Place it and it projects your base powers in a cube around it. Carry it and it powers you. Keep it somewhere clever, because anyone who finds it can stand beside it for five minutes and walk off with it, powers and all. You get a warning the second they start. There is no power, block or trick that makes a totem safe, and we are never adding one. You cannot hide it in a storage system either: it comes straight back out.

<!-- plate: totem_powers -->

Twenty-eight powers on six paths, bought with Shatter Coins and XP. Carried powers work while the totem is on you, Base powers come from a placed totem, and Raid powers help you take someone else's. Up to eight friends on the ACCESS tab can pass your Alarm and Ward and use your machines.

## The road ahead

<!-- plate: progression -->

Three metals, each slower than the last, and a machine for every step. That is the whole thing in one picture; the rest of this page walks it top to bottom.

## Ores

<!-- plate: ores -->

## Tetrium: where you start

<!-- plate: tetrium -->
<!-- plate: machines_single -->

Tetrium is everywhere and it is quick. A crucible, a forge, and you are making plates, chassis and circuits: the parts every later machine is built from. Hang on to the slag, it becomes the bricks in every chassis.

<!-- recipes: machines_single -->

## Illyrium: slow and worth it

<!-- plate: illyrium -->

Illyrium comes one nugget at a time: smelt the ore in lava, crush the dirty ingot, wash the dust, alloy it back. Nine nuggets and the best part of an hour make one ingot. The dirty metal is useful on its own: it presses the dies that build the coin factory and the centrifuge.

<!-- plate: machine_illyrium_crucible -->
<!-- recipes: machine_illyrium_crucible -->
<!-- plate: machine_illyrium_refinery -->
<!-- recipes: machine_illyrium_refinery -->

## Magnatite: the metal in between

<!-- plate: magnatite -->

The Magnetic Centrifuge separates the ore, and with a coil in it charges the ingots: a gamble that gets better with every layer you stack on top. Charged Magnatite and Carbon Dust go into every cable, battery and plasma block.

<!-- plate: machine_magnetic_centrifuge -->
<!-- recipes: machine_magnetic_centrifuge -->

## Shatter Coins: real time, no shortcuts

<!-- plate: coins -->

Coins buy totem tiers and powers, and you press them yourself, in real hours: 8 for Copper, 96 for Illyrium. The factory keeps pressing while you are offline, and a Coin Vault pays interest on what you keep in it. Rivals can pick the vault for a quarter of the coins.

<!-- plate: machine_coin_factory -->
<!-- recipes: machine_coin_factory -->

## Wave Plasma: your totem, on tap

<!-- plate: plasma_network -->

A placed totem gives off Wave Plasma. Pipe it and your base powers appear wherever the cable ends. Feed it into a Plasma Injector and a machine runs up to three times faster. Bottle it in a battery and your base keeps running while the totem is away, or stolen. Put a cell in the Wave Emitter and carry your powers without carrying the totem. A tank holds millions of mB of it for the lean times.

<!-- plate: plasma_blocks -->
<!-- plate: plasma_tank_injector -->
<!-- plate: batteries -->
<!-- plate: cable_colours -->
<!-- recipes: cable_colours -->

## Decoys, traps and the compass

<!-- plate: raiding -->

A Decoy Totem looks like the real thing to everyone but you, pulls rival compasses toward it, and goes off like a trap when they try to steal it. Range coils, five kinds of charge, an amplifier and reinforced casings go in its sockets. Your own Totem Compass points home, or for a while at the nearest rival.

<!-- recipes: raiding -->

## For server owners

<!-- plate: base_blocks -->

- **One config file,** `serverconfig/bsp_core-server.toml`: every cost, time, range, limit and price.
- **Admin Rack,** or `/bsp admin`: every totem, position and vault at a glance, teleport and reset, and the season end that pays the top three and hands everyone a fresh totem. Prizes are real items you place in the panel; Score Screens show them, the rules, and the live leaderboard.
- **Anti Totem Block:** keeps totems and chosen machines out of spawn, hubs and arenas.
- **Networks (experimental):** MySQL or MariaDB shares totems, limits, the leaderboard, vault totals, seasons and steal warnings across several servers. Not tested on a live database yet.

## Status

1.0 is the first full release. Every picture on this page is drawn from the mod's own models, textures and recipes, so what you see is what you get in game. Report problems at https://github.com/Mrgregles/BSP-Core/issues, and come and play it on BSP!
