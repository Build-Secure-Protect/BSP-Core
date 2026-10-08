# BSP Field Guide: text for review

Every page of the in-game guide book, in the order it appears. Reword anything you like and send the file back:
the changes are then copied into `tools/gen_guide_book.py`, which builds the book. Keep each page short: a book page
holds roughly 100 words, or about 60 next to a recipe or picture.

*Italics* are item names (shown in colour in the book) and **bold** is bold. Lines marked `[recipe]`, `[picture]` or
`[3D structure]` show what else is on that page and are not text you need to edit.

---

# The Shatter Totem

Your totem is the heart of BSP: build around it, protect it, and take other players' totems if you can.

## Your Shatter Totem

**Page 1**

Every player is given one *Shatter Totem* the first time they join. It cannot be crafted and it cannot be destroyed.

Carry it, or place it in your base. A placed totem is where your upgrades and auras work from, so most players build their base around it.

**Page 2: Keeping it**

Only one totem is ever yours by right, but you can hold others: any totem you steal becomes yours.

If you die to another player while carrying your totem, it is placed where you fell. A totem dropped on the ground places itself after a short while.

## Stealing and Defending

**Page 1**

Right-click another player's placed totem and choose to steal it. A timer starts, and the owner is warned.

Stay close to the totem until the timer runs out and it is yours, with every upgrade on it.

**Page 2: Stopping a thief**

The steal is cancelled if the thief is killed, or stays too far from the totem for too long.

So defend in person, and make the room around your totem hard to stand in. The Fortify upgrade helps your walls survive explosions.

## The Upgrade Tree

**Page 1**

Right-click with the totem in your hand, or right-click your placed totem, to open its upgrade tree. Upgrades belong to the totem, so they move with it if it is stolen.

Your totem has five tiers, I to V. Each tier has its own Shatter Coin: Copper, Gold, Diamond, Netherite, Illyrium.

**Page 2: Tiers and paths**

Raising the totem a tier costs coins of the tier you are leaving, plus XP levels. A new tier opens new upgrades and two more levels on the ones you have.

Upgrades sit on paths. The next one opens when the one before it reaches level 2. Every level costs the coin of the tier it is bought at, plus XP.

**Page 3: Carried**

Work while the totem is in your **offhand**. Paid in experience levels.


- Damage, Resistance, Mining Speed
- Swiftness: move faster
- Vitality: extra hearts
- Featherfall: less fall damage
- Night Sight: see in the dark

**Page 4: Base**

Work only while the totem is **placed**. Paid in Shatter Coins.


- Fortify: blocks nearby resist explosions and intruders mine slower
- Healing Aura: heals you nearby
- Ward: weakens intruders
- Alarm: outlines intruders and warns you
- Sanctuary: no hostile spawns
- Deadlock: your totem takes longer to steal
- Overclock: machines nearby work faster

**Page 5: Raid**

Help you take other totems. They count when the totem that has them is in your offhand as you start a steal. Paid in Shatter Coins.


- Lockpick: your steals take less time
- Shroud: the owner is warned late

No upgrade can make a totem impossible to steal: blocks can always be mined, and a steal always has a time limit.

## The Totem Compass

**Page 1**  `[item shown] totem_compass`

Points to your own nearest totem. Load it with Shatter Coins and start tracking, and for a while it points to the nearest rival totem instead.

After tracking it needs to cool down. Upgrades shorten the cooldown.

**Page 2**  `[recipe] totem_compass`

## Scores and the Leaderboard

**Page 1**

Every totem you own scores points by its tier:


- Tier I: 1 point
- Tier II: 2 points
- Tier III: 4 points
- Tier IV: 7 points
- Tier V: 10 points

Your score is the total for all your totems, so stealing totems and upgrading them both move you up.

**Page 2**  `[recipe] score_screen`

*Score Screen* panels placed side by side and above each other, facing the same way, join into one display of up to 8 wide and 6 high. It shows the leaderboard, these scoring rules, or the season's prizes.

**Page 3: Seasons and Prizes**

The server runs in seasons. When a season ends, the top three players receive that season's prizes, every totem is removed, and every player gets one fresh Tier I totem.

A Score Screen set to Prizes shows what first, second and third place will win. If you are offline when the season ends, your prizes and your new totem are waiting when you next log in.

**Page 4: How it is counted**

A placed totem counts for its owner. A carried totem counts for whoever is carrying it.

Ties go to the player with more totems, then to the higher tier. Score Screens show the current leaderboard, and on a network it covers every server.

## Decoy Totems

**Page 1**  `[item shown] decoy_totem`

A fake totem. While it has power, rival Totem Compasses within its range point at it instead of a real totem, and everyone but you sees an ordinary Shatter Totem. You see the idol with a ghost of the totem around it.

You may have five placed.

**Page 2**  `[recipe] magnet_core, decoy_totem`

The *Magnet Core* is the hard part: six Charged Magnatite Ingots around an Illyrium Processor.

**Page 3**  `[recipe] decoy_power_base`

A decoy must stand on a *Decoy Power Base*, fed with RF from its sides or from below. It draws 100 RF every tick. Without power the disguise drops and everyone sees the bare idol.

**Page 4: When someone takes the bait**

An enemy who tries to steal a decoy finds out it is fake. Its traps go off and it takes a hit. When it has taken more hits than it has casings it breaks, and does nothing until you repair it with Magnatite Ingots.

Only you can mine a working decoy. Anyone can mine a broken or unpowered one.

**Page 5: Sockets**

Right-click your decoy to fit parts. A socket opens when the one before it is filled.


- **Range**: Range Coils Mk I, II, III widen the compass range from 24 blocks to 40, 64 and 96.
- **Traps**: a charge, then a Trap Amplifier, then a second charge.
- **Casing**: each Reinforced Casing survives one more attempt.

**Page 6: Trap charges**

Each charge is used up when it goes off.


- Blast: an explosion that hurts the thief and breaks nothing.
- Hex: Slowness, Weakness and Glowing.
- Poison.
- Fatigue: Mining Fatigue.
- Warp: throws the thief up to 30 blocks away.

**Page 7**  `[recipe] range_coil_mk1, range_coil_mk2`

**Page 8**  `[recipe] range_coil_mk3, trap_amplifier`

**Page 9**  `[recipe] blast_charge, warp_charge`

The other charges swap the middle item: Fermented Spider Eye, Spider Eye or Prismarine Shard.

**Page 10**  `[recipe] reinforced_casing`

## Wave Plasma and Projectors

**Page 1**

A placed totem gives off **Wave Plasma**: 100 mB every tick, up to 6,000 with the ten levels of the **Output** upgrade. The totem panel shows the figure. Drawn out and piped to a *Projector*, it recreates the totem's base powers as a second aura somewhere else: over a mine, a farm, or a second wall.

The projector works for whoever owns the totem. If your totem is stolen, so is the aura.

**Page 2**  `[3D structure] The hook-up`

Totem on extractor, interface beside it, a run east through a valve and a repeater to a base and its projector, and a second run south into the back of a charger. Drag to turn.

**Page 3: How it joins up**

The chain, in order:

- *Plasma Extractor* directly under the totem. More stack below; the flow is split between them.
- *Plasma Interface* touching an open face of an extractor. Cables plug into its outer faces.
- *Plasma Cable* from the interface onward.
- *Projector Base* at the end of the cable, holding what arrives.
- *Projector* standing on the base.

**Page 4**  `[recipe] plasma_extractor, plasma_interface`

Stack extractors under the totem to split the flow between several interfaces. An interface touching an extractor another group already serves is refused: its lit parts turn red and it does nothing.

**Page 5**  `[3D structure] Four interfaces joined`

Touching interfaces become one body: the faces between them vanish and the frame runs round the outside. Up to twelve, any shape. A cable on any outer face is fed by the whole group, and each face sends up to 1,000 mB/t: a 6,000 mB/t totem needs six runs.

**Page 6: Seeing the flow**

Right-click any interface for its **flow view**: the group and every cable, valve, repeater, extractor, base and charger it reaches in 3D, labelled with the mB/t passing. Drag to turn, right-drag to pan, scroll to zoom.

Jade shows the same figures block by block: an extractor's draw, a cable's flow, a valve's limit, a base's tank.

**Page 7**  `[recipe] projector_base, totem_projector`

Cables plug into the base's four sides; the projector on top draws from it. A projector needs 100 mB/t arriving. Right-click it: POWERS lists what the interface offers with a RECEIVE switch each; CHUNKS is the chunk picker; STATUS shows the pressure.

**Page 8: The base's tank**

The base holds 5,000 mB and the projector burns 20 mB/t of it, so a base that was full keeps its projector going for about four minutes after a cable is cut. A base with nothing feeding it and no projector drains by itself.

A projector runs on plasma alone: no RF. With less than 100 mB/t arriving and a dry base it shows no aura.

**Page 9**  `[recipe] channel_expander`

A projector receives two powers at once, three with a *Channel Expander* fitted by right-clicking the projector with it; sneak and right-click empty-handed takes it out.

**Page 10: Powers and auras**

What a projector can receive: Fortify, Healing Aura, Alarm, Ward, Sanctuary, Overclock, Cloaking, Recall and Anchor, at the highest level any totem on the interface has.

Auras are cubes: a power with radius 5 covers 11 by 11 by 11 blocks around the totem or projector, so you can measure and build to the edge.

## Pressure, Runs and Valves

**Page 1**

Plasma moves in **runs**: a line of cables from an interface to whatever is at its end. Every cable in a run carries the same figure, the run's **pressure**, in mB/t. Nothing is lost along a cable, however long, up to its reach.

Each kind of cable has a longest run:

- Tetrium: 15 blocks, 250 mB/t
- Magnatite: 25 blocks, 500 mB/t
- Illyrium: 40 blocks, 1,000 mB/t
- Charged Illyrium: 80 blocks, 1,000 mB/t

A mixed run goes as far, and carries as much, as its weakest cable.

**Page 2**  `[recipe] tetrium_core_cable, magnatite_core_cable`

**Page 3**  `[recipe] illyrium_core_cable, charged_illyrium_core_cable`

**Page 4: Sharing**

An interface shares what its extractors give **equally** between the runs leaving it. A run that can take less than its share, because a valve caps it or its end is full, leaves the rest to the others.

So two bases on one totem get 50 mB/t each; cap one at 20 and the other gets 80. The cables leaving the interface always add up to the supply.

**Page 5**  `[recipe] plasma_repeater`

A *Plasma Repeater* ends one run and starts a fresh one, so the reach count begins again. Plasma goes in its dark back and out its lit front; placed pointing the way you look, the wrench turns it. It costs a tenth of the pressure, and a projector behind it receives one power at full level, or two or more each one level lower per repeater.

**Page 6**  `[recipe] plasma_valve`

A *Plasma Valve* sets the most that may pass it, from 0 to 1,000 mB/t. Right-click it and drag the dial, type a number, or use the buttons. A lever on it or any redstone signal shuts it, unless you switch redstone control off on its screen. Powers pass unchanged; it counts as one cable of reach and does not start a fresh run.

**Page 7: What you see**

The valve's hand wheel turns as far as the limit is set. Its windows show plasma arriving on one side and leaving on the other at the limited rate; the lamp by the gauge is blue while open and red while shut.

Each extractor also keeps a reserve of 4,000 mB. Take the totem away and the runs keep drinking from it for a short while, then stop.

**Page 8**  `[recipe] wrench`

The *Wrench* turns blocks in place: repeaters, valves, chargers, batteries and placed totems. Sneak and right-click turns the other way, or turns a repeater round.

**Page 9**  `[recipe] blue_illyrium_core_cable`

Magnatite, Illyrium and Charged Illyrium cables take any of the sixteen dyes: eight cables round a dye, and a coloured cable can be dyed again. A coloured cable joins only cables of its own colour, a plain one only plain ones, so two runs can cross without mixing. Machines take any colour.

**Page 10: Cable ends**

Point the wrench at an end of a cable and right-click to set what that end does:

- **Normal**: plasma flows either way, as before.
- **Output**: plasma may only leave the cable here.
- **Input**: plasma may only enter here.
- **Off**: not joined.

Where another colour meets the end, the first click **Links** them. Sneak and right-click puts an end back to Normal.

**Page 11: What you see**

While you hold the wrench a line above the crosshair names the end you point at and its setting. Output ends wear a copper collar with an arrow pointing out, Input ends an arrow pointing in, Off ends a dark plate, and Links a copper and white collar.

Sneak and right-click the **core** of a cable to pick it up with its settings kept on the item.

## The Plasma Tank

**Page 1**

Wave Plasma can be kept in bulk. A *Plasma Tank* is a hollow box you build from three blocks: *Tank Casing* on every edge, *Tank Glass* or casing on the faces, and *Tank Ports* wherever a cable should meet it. Three to twelve blocks a side, nothing inside. It forms by itself when the last block goes in.

Every block of the shell holds 2,500,000 mB. It holds plasma only, no powers.

**Page 2**  `[3D structure] A 4 x 3 x 4 tank, formed`

Casing on the twelve edges, glass on the faces, a port on the top and one on a side. Once formed, the casing turns to glass with a lit outline along the edges. Any box from 3 x 3 x 3 up to 12 x 12 x 12 works the same way.

**Page 3**  `[recipe] tetrium_glass, tank_glass`

Tetrium Glass is ordinary glass round a Tetrium Nugget; Tank Glass is Tetrium Glass round a Tetrium Plate. A wall of Tank Glass draws as one sheet.

**Page 4**  `[recipe] tank_casing, tank_port`

A port takes plasma from a run that comes from an interface, and gives plasma to a run that only leads to bases, chargers or other tanks. Point the wrench at it to set Input or Output only. Each port gives up to 1,000 mB/t, like an interface face.

**Page 5: Seeing it**

Right-click any block of the tank: the screen shows the tank in 3D with its plasma at the level it holds, each port with its setting and what passes, and the first few cables out of each port. Jade reads the level on any block.

When the tank forms, a sweep of light runs over it and the casing turns to glass with a lit outline along the edges; the four uprights glow as high as the plasma stands, so the level can be read from outside. Plasma coming in through a port above the surface pours down to it; the level rises evenly through the whole tank.

**Page 6: Breaking and rebuilding**

Break one block by accident and the tank goes dormant but keeps its plasma: put the block back and it is whole again. Rebuild it smaller and it keeps what fits; bigger and it keeps what it had. Take the last block away and the plasma is gone.

## Chunk Loading

**Page 1**

A placed totem can keep the land around it running while you are away: machines work and crops grow as if you were standing there.

Two base upgrades on the totem's tree do it. **Anchor** sets how many chunks stay loaded. **Survey** sets how far from the totem they can be.

**Page 2: Anchor and Survey**

**Anchor**, from Tier II:

- Level 1: 1 chunk
- Level 2: 3 chunks
- Level 3: 6 chunks

**Survey**, from Tier III, once Anchor is level 2:

- Without it: pick within 3 by 3 chunks
- Level 1: 5 by 5
- Level 2: 7 by 7

The chunk the totem stands in is always loaded and counts as one.

**Page 3: Choosing chunks**

Once Anchor has a level, your totem's panel gains a **CHUNKS** tab. It shows a map of the ground around the totem, one square for each chunk, north at the top.

Click a square to load that chunk. Click it again to let it go. Turquoise squares are loaded from here, violet ones from a projector, dark ones are out of range.

**Page 4: Through a projector**

Receive **Anchor** on a Projector and it gets a chunk map of its own, centred on the projector.

A projector adds no chunks. It lets you spend whatever is left of the totem's chunks further away: click any chunks within range of the projector. The projector's own chunk is always one of them, so it keeps running and keeps drawing plasma. Its chunks stay loaded while plasma reaches it.

**Page 5: More than one totem**

Only your main totem, the one you have held longest, gets the full allowance. Every other totem you place loads the chunk it stands in and no more, and cannot take Anchor past level 1.

Lose your main totem and the next one you hold takes its place, with whatever upgrades it has.

**Page 6: Stolen and moved totems**

When a totem is stolen, every chunk chosen for it is let go, at the totem and at its projectors. The thief keeps the upgrades and chooses again.

If you pick your own totem up and place it somewhere else, it remembers the layout.

The server may be set to load chunks only while you are online.

## Batteries, Cells and the Emitter

**Page 1**

Wave Plasma can be bottled. A *Battery Charger* with a Plasma Cable into its back fills a *Plasma Battery* or *Power Cell* standing in it, and **stamps** powers from the interface into it, chosen on the charger's screen.

Every battery and cell is built around a *Charged Resonance Crystal*.

**Page 2: Charged Resonance Crystal**  `[item shown] charged_resonance_crystal`

A Resonance Crystal magnetised in the Magnetic Centrifuge, with a Magnatite Nugget in the upgrade slot. Ten seconds a crystal, faster with the centrifuge at full speed.

**Page 3**  `[recipe] battery_charger`

Place the charger facing you: the cable goes into its **back**, and only there. The wrench turns it. Pipes and hoppers may put an empty battery or cell in from any other side and take it out once it is full. Its screen shows the tank, what is arriving, the item filling, and one row per power with a STAMP button. Only players with Machines access on the totem may open it.

**Page 4: What gets stamped**

Stamping copies a power at the level the totem has. While the item sits in a fed charger, a stamped power follows the totem's level if it changes. Take the item out and the levels are fixed.

**Batteries** take Base powers: the ones a projector can receive. **Cells** take Carried powers: the ones that work from your offhand.

**Page 5**  `[recipe] plasma_battery_1, plasma_battery_2`

Tier I holds 40,000 mB and no powers. Tiers II, III and IV hold 200,000, 1,000,000 and 5,000,000 mB and two, three and four Base powers. Each tier is built around the one below.

**Page 6**  `[recipe] plasma_battery_3, plasma_battery_4`

**Page 7**  `[3D structure] A battery instead of a totem`

A charged battery standing on an extractor feeds it at 100 mB/t with the powers stamped into it, until it runs dry. The projector gets those powers as if a totem stood there.

**Page 8: Batteries as a source**

A battery on an extractor feeds it at 100 mB/t with its stamped powers until it runs dry, so projectors and chargers keep working while your totem is away or stolen. The battery lies along an axis; the wrench turns it. A battery keeps its charge and powers when mined.

**Page 9**  `[recipe] power_cell_1, power_cell_2`

A *Power Cell* holds Carried powers: 8,000 mB and one power for Tier I, 24,000 and two for II, 60,000 and three for III.

**Page 10**  `[recipe] power_cell_3, wave_emitter`

The *Wave Emitter* runs on one cell. Its hover text and the cell's own show the charge and the powers stamped in.

**Page 11: The Wave Emitter**

Right-click the emitter at the air (no block within four blocks) to open it: put a Power Cell in the slot, press ON, and read the cell's powers and how long it will last. Put the emitter in your **offhand**: you get the cell's powers as if you carried the totem, while it draws 20 mB/t. It switches off when the cell runs dry.

Right-click at a nearby block to switch it without opening the screen; sneak there to take the cell out.

**Page 12: Out and about**

So the totem can stay safe at home while you go out: a Tier III cell at 20 mB/t lasts 50 minutes.

Carried powers from the emitter: Damage, Resistance, Mining Speed, Swiftness, Vitality, Featherfall, Night Sight, Bouncy and X-ray, at the stamped level.

## Letting Friends In

**Page 1**

Your placed totem's panel has an **ACCESS** tab. Type an online player's name and Add: they get four switches.


- **Upgrades**: may buy upgrades and change chunks and projector settings.
- **Alarm**: does not set it off.
- **Ward**: is not weakened by it.
- **Machines**: may open the totem's chargers and see through its Cloaking.

**Page 2: Rules**

Up to eight people. The list stays with the totem when you pick it up, and is wiped when the totem is stolen. Friends still cannot pick the totem up, and they do not get its Carried powers.

## The Newer Powers

**Page 1**

Five powers added with the plasma rework. Auras are now cubes: a radius of 5 covers 11 by 11 by 11 blocks, so the edge can be measured and built to. Hide the cube drawings for yourself with the AURAS switch in the totem panel's header or the keybind.

**Page 2: Carried**

**Bouncy** (Carried, Dig path after Featherfall, Tier III): less fall damage, and landing from three blocks or more throws you back up with part of your landing speed, so a bigger fall means a bigger bounce. Sneak to land flat.

**X-ray** (Carried, after Night Sight, Tier V): press its key and the solid blocks around you turn to faint glass, fainter the deeper they are, for a while; ores show through them bright, containers and spawners are marked. The level sets how far it reaches and how long it lasts; then it recharges.

**Page 3: Base**

**Cloaking** (Base, Walls path after Deadlock, Tier IV): outsiders see the land as the world generated it, with its trees, plants, snow and ice but no caves, in place of everything inside the cube, and the players and mobs inside are hidden from them too. Uses 50 mB/t of the totem's plasma output.

**Recall** (Base, Home path after Overclock, Tier V): when your totem is being stolen, a card above the hotbar offers to bring you back to within its distance of the totem. Twenty seconds to decide, ten minutes' rest after.

**Page 4: Raid**

**Recall Block** (Raid, after Shroud, Tier IV): with it on the totem in your offhand while you steal, the owner's Recall offer comes later, on top of Shroud.

---

# Tetrium and Illyrium

Two new metals, from first ore to refined ingot.

## Tetrium

**Page 1**

*Tetrium Ore* is found underground in stone and deepslate. Mine it with an iron pickaxe or better. Without Silk Touch the ore drops *Raw Tetrium*; the crucibles take raw chunks and ore blocks alike, so do not smelt either.

**Page 2**  `[recipe] tetrium_crucible`

The *Tetrium Crucible* burns furnace fuel and turns each ore into Tetrium Nuggets and Tetrium Slag.

**Page 3**  `[recipe] tetrium_ingot_from_nuggets, combination_forge`

Nine nuggets make an ingot by hand. The *Combination Forge* does the same job for you and also presses plates.

**Page 4**  `[recipe] slag_brick_from_smelting`

Keep your slag. Smelted into *Slag Bricks* it goes into almost every machine part.

## Dust and Crushing

**Page 1**

Several recipes need metal dust. A crusher from another mod turns an ingot into dust every time: Mekanism's Crusher, Create's crushing wheels and millstone, Thermal's Pulverizer, Ender IO's SAG Mill, Railcraft's Crusher, Integrated Dynamics' Mechanical Squeezer and Electrodynamics' Mineral Grinder.

With no crusher, put any pickaxe and one ingot in a crafting grid. The pickaxe loses a little durability, and you get either dust or a few nuggets back, by chance.

## Illyrium

**Page 1**

*Illyrium Ore* is rarer and needs a diamond pickaxe. Refining it takes four steps:


- Illyrium Crucible: ore and slag become a Dirty Illyrium Ingot.
- Crush it into Dirty Illyrium Dust.
- Illyrium Refinery: dust, water and a filter give Pure Illyrium Dust.
- Back in the Crucible: Pure Illyrium Dust and Tetrium Dust alloy into Illyrium Nuggets.

**Page 2**  `[recipe] illyrium_ingot_from_nuggets, illyrium_forge_upgrade`

Nine nuggets make an ingot by hand. To forge Illyrium in the Combination Forge, fit the *Illyrium Forge Upgrade*.

**Page 3**  `[recipe] iron_filter, diamond_filter`

Filters wear out. Better filters refine far more dust before they are used up.

## Magnatite

**Page 1**

*Magnatite Ore* lies deep underground, mostly in deepslate, and needs a diamond pickaxe. It is a little easier to find than diamond. As with the other ores, keep the ore block: the machine takes it as it is.

The Magnetic Centrifuge spins each ore into Magnatite Nuggets and one *Carbon Dust*.

**Page 2**  `[recipe] magnatite_ingot_from_nuggets`

Nine nuggets make a *Magnatite Ingot* by hand or in the Combination Forge.

**Page 3**  `[recipe] copper_tetrium_coil`

Fit the *Copper Tetrium Coil* to a centrifuge and it can magnetise ingots into *Charged Magnatite Ingots*. Each try may fail; a failed try keeps the ingot.

---

# Machines

The parts every machine is made of, and how to build the two Illyrium multiblocks.

## Machine Parts

**Page 1**

Every machine block is built from the same few parts. Make these first:


- *Slag Brick*: smelt Tetrium Slag.
- *Tetrium Plate*: put a Tetrium Ingot in the Combination Forge.
- *Machine Chassis*, *Tetrium Coil*, *Drive Motor* and the *Basic Control Circuit*: see the next pages.

**Page 2**  `[recipe] machine_chassis, tetrium_coil`

**Page 3**  `[recipe] drive_motor, circuit_substrate`

**Page 4**  `[recipe] basic_control_circuit`

Each controller needs this circuit upgraded for its job: Crucible, Refinery or Mint.

## Illyrium Crucible

**Page 1**

A 3 x 3 x 3 multiblock that runs on lava. It smelts Illyrium Ore with slag into Dirty Illyrium Ingots, and alloys Pure Illyrium Dust with Tetrium Dust into Illyrium Nuggets.

The lower Lava Pylons (orange sockets) take lava by pipe. The upper ones (yellow sockets) take RF once an RF Upgrade is fitted.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[3D structure] Illyrium Crucible`

Controller at the bottom front centre. 12 Casing, 8 Lava Pylons, 2 Item Hatches, 1 Core.

**Page 3**  `[recipe] thermal_lining, crucible_control_circuit`

**Page 4**  `[recipe] illyrium_casing, illyrium_core`

**Page 5**  `[recipe] lava_pylon, item_hatch`

**Page 6**  `[recipe] illyrium_crucible`

## Illyrium Refinery

**Page 1**

Washes Dirty Illyrium Dust into Pure Illyrium Dust using water and a filter.

The Refinery Pump takes water (blue socket) and RF (yellow socket). The Item Hatch takes dust in and gives pure dust out.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[3D structure] Illyrium Refinery`

Controller at the bottom front centre. 8 Casing, 1 Core, 2 Tank Glass, 1 Item Hatch, 1 Pump.

**Page 3**  `[recipe] refinery_control_circuit, illyrium_glass`

**Page 4**  `[recipe] refinery_pump, illyrium_refinery`

## RF Upgrades

**Page 1**

Fit an RF Upgrade to a machine and it can take RF. It then runs faster and, on the Crucible, uses less lava. Mk I, II and III give 5%, 10% and 30%.

The Combination Forge runs on RF alone with an upgrade, at twice its coal speed.

**Page 2**  `[recipe] rf_upgrade_mk1, rf_upgrade_mk2`

**Page 3**  `[recipe] rf_upgrade_mk3`

## Magnetic Centrifuge

**Page 1**

A multiblock one block high and three by three. It separates Magnatite Ore and, with a Copper Tetrium Coil, charges Magnatite Ingots. It always needs RF, fed into the Power Port (yellow socket); the Item Hatch (blue socket) takes items in and out.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[3D structure] Magnetic Centrifuge`

Controller front centre, Rotor in the middle, Item Hatch left, Power Port right, 5 Casing.

**Page 3: Stacking**

Put another Rotor on top of the first and ring it with eight Casing to add a layer, up to six layers.


- More layers: more nuggets from each ore, up to 7 to 9 with six.
- More layers: a better chance to charge an ingot, from 1 in 6 up to 1 in 2 with five.
- Six layers: each charging try is much quicker.

Every layer draws power, and charging draws far more than separating.

**Page 4**  `[recipe] centrifuge_casing, centrifuge_rotor`

**Page 5**  `[recipe] centrifuge_power_port, magnetic_centrifuge`

---

# Shatter Coins

Coins pay for the strongest totem upgrades. You press them yourself.

## Blanks and Coins

**Page 1**

There are five coins: Copper, Gold, Diamond, Netherite and Illyrium. Each is worth twice the one before.

A coin starts as a *Shatter Blank*, is built up into a tier's coin blank, and is then pressed in the Shatter Coin Factory.

**Page 2**  `[recipe] shatter_blank, copper_coin_blank`

Each tier's blank is crafted around the blank of the tier below it.

**Page 3**  `[recipe] gold_coin_blank, diamond_coin_blank`

## The Shatter Coin Factory

**Page 1**

An end-game machine built in slices. One slice is 1 wide, 2 high and 3 long. Put blanks in the Blank Hatch and RF in the Power Port, both at the back.

Pressing takes real time, from hours to days by tier, and carries on while you are offline. Coins collect in the tray and must be taken by hand.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[3D structure] Factory slice`

Bottom from the front: Controller, Frame, Blank Hatch. Top: Frame, Press, Power Port.

**Page 3: Joining slices**

Build slices side by side, facing the same way, and they join into one machine with one screen. You may own up to 10 slices.

Each slice presses its own blanks. RF is shared, and cabling more Power Ports charges the machine faster.

**Page 4**  `[recipe] mint_control_circuit, conveyor_belt`

**Page 5**  `[recipe] press_die, factory_frame`

**Page 6**  `[recipe] factory_press, factory_blank_hatch`

**Page 7**  `[recipe] factory_power_port, shatter_coin_factory`

## Motivators

**Page 1**  `[item shown] factory_motivator`

Place up to three on top of a complete factory slice. One, two or three cut that slice's press time by 15%, 30% or 50%.

They pulse faster the more a slice has, and link up with their neighbours.

**Page 2**  `[recipe] resonance_crystal, illyrium_processor`

**Page 3**  `[recipe] factory_motivator`

## The Coin Vault

**Page 1**  `[item shown] coin_vault`

A safe that holds only Shatter Coins, 27 stacks of 12 per block. Nothing can pipe coins in or out.

Vault blocks of yours that touch join into one vault, up to 3 x 3 x 3. You may own 27 vault blocks.

**Page 2**  `[recipe] coin_vault`

The block keeps its lock and alarm when you break it and place it again.

**Page 3: Access and Security**

On the **Access** tab, add the players who may open the vault. They must be online when you add them. They can move coins but cannot change the settings.

On the **Security** tab, buy up to three lock levels and an Alarm with coins. The Alarm warns everyone with access the moment someone starts picking the lock.

**Page 4: Lockpicking**

Anyone without access can pick the lock: 2 minutes, plus 1 minute for each lock level, staying within 4 blocks. If they finish, they take a quarter of the coins, chosen at random. Illyrium coins are safe while the vault holds 3 or fewer.

Breaking a vault that is not yours gives no coins: they are kept for the owner, who collects them by opening any vault of theirs.

**Page 5: Interest**

Coins in your vaults earn interest: XP levels and Tetrium Ingots for every 50 coin value stored, counted across all your vaults. With a Netherite or Illyrium coin stored you also earn up to 2 Illyrium Ingots every 3 days.

Interest builds up while you are offline, but stops at 3 days' worth. Press **Redeem** on the Storage tab to collect it and start it building again.

