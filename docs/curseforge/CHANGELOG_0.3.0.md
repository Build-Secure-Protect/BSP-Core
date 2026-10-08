# BSP-Core 0.3.0

The totem energy update. Totem Generators and Reach Amplifiers are gone. In their place a placed totem gives off a fluid, Wave Plasma, and a new set of blocks draws it out, routes it, bottles it and carries it. This is a big one and it has had less testing than I would like, so please report anything odd.

**Existing worlds:** Totem Generators and Reach Amplifiers already in a world disappear. Projectors need a Projector Base under them now. New settings are added to `serverconfig/bsp_core-server.toml` by themselves; the old `projector` section is replaced by `plasma`, and there are new `effects` and `chunks` sections.

## Wave Plasma

- A placed totem now makes **Wave Plasma**: 100 mB every tick, up to 6,000 with the new **Output** upgrade (ten levels, starts the plasma path on the tree). The totem panel shows what it makes.
- **Plasma Extractor.** Stand the totem on it to draw the plasma out. Stack more below and the flow is split between them. Each holds 4,000 mB.
- **Plasma Interface.** Touch it to an extractor and plug cables into it. Interfaces that touch join into one group of up to twelve, and each face sends up to 1,000 mB/t into its run, so a big totem needs a big group. They look like one body now: the faces between them vanish, the frame runs round the outside, and a lit cross on every outer face lines up with its neighbours. Blue while there is plasma, grey without, red on a block the group refuses (a thirteenth, or one touching an extractor another group holds).
- **Plasma Cables** are glass pipes now and you can see the plasma moving inside. Tetrium reaches 15 blocks and carries 250 mB/t, Magnatite 25 and 500, Illyrium 40 and 1,000, Charged Illyrium 80 and 1,000. A run is held to its weakest cable.
- **Sharing.** An interface splits its plasma equally between the runs leaving it. A run that needs less leaves the rest to the others. Every cable in a run carries the run's figure, so the cables leaving an interface always add up to what the extractors give.
- **Coloured cables.** Magnatite, Illyrium and Charged Illyrium cables come in all sixteen dyes: eight cables round a dye, and dye again to change. A coloured cable joins only its own colour, a plain one only plain ones, so runs can cross without mixing. Machines take any colour.
- **Cable ends.** Point the wrench at an end of a cable and right-click to set it: Normal, Output (plasma only leaves there), Input (only enters), Off, or Linked where another colour meets it. A line above the crosshair shows what you are pointing at. Sneak + right-click the core to pick a cable up with its settings.
- **Plasma Repeater.** Starts a fresh run. One way: in through the dark back, out through the lit front. Costs a tenth of the pressure, and a projector behind it receives one power at full level or several each a level lower per repeater. Arrows on the top and sides show the way.
- **Plasma Valve.** Sets the most that may pass it, from its screen: a dial, a typed number, nudge buttons, up to 1,000 mB/t. A lever on it or any redstone signal shuts it, unless you switch redstone control off on the screen. Powers pass unchanged. The hand wheel turns with the limit, the gauge needle follows, the lamp is blue open and red shut, and its windows show plasma going in one side and out the other.
- **Projector Base.** A tank block the Projector stands on. Cables plug into its four sides and the level shows through its windows. It holds 5,000 mB; the projector needs 100 mB/t arriving and burns 20 mB/t, so a full base keeps a projector going for a few minutes after a cut. Projectors no longer use RF.
- **Projector screen.** Right-click a projector: POWERS lists what the interface offers with a RECEIVE switch each, CHUNKS is the chunk picker, STATUS shows the pressure. The Channel Expander is fitted by right-clicking the projector with it.
- **Interface screen.** Right-click any interface: the whole network in 3D, every cable labelled with its mB/t (one label per run of cables), extractors in gold, refused blocks in red. Drag to turn, right-drag to pan, scroll to zoom, RESET VIEW puts it back. If Cloaking is taking part of the output it says so.
- **Wrench.** Turns things in place: repeaters, valves, chargers, batteries, placed totems. Sneak to turn the other way.

## Batteries, cells and the Wave Emitter

- **Battery Charger.** Cable into its back. Fills the battery or cell standing in it and stamps powers from the interface into it, chosen on its screen. Place it facing you; the wrench turns it. Only players with Machines access on the totem may open it. The item shows in its cradle.
- **Plasma Batteries I to IV.** 40,000, 200,000, 1,000,000 and 5,000,000 mB, and 0, 2, 3 and 4 Base powers. Stand a charged battery on an extractor instead of a totem and it feeds the extractor with its stamped powers until it runs dry. A battery keeps its charge when mined.
- **Power Cells I to III.** 8,000, 24,000 and 60,000 mB with 1, 2 and 3 Carried powers.
- **Wave Emitter.** Right-click at the air to open it: put a cell in, press ON, see its powers and how long it will last. In your offhand it gives you the cell's Carried powers as the totem would, at 20 mB/t. So the totem can stay at home.
- **Charged Resonance Crystal.** A Resonance Crystal magnetised in the centrifuge with a Magnatite Nugget in the upgrade slot. Every battery and cell is built around one.

## Totem

- **Access list.** The placed totem's panel has an ACCESS tab. Add up to eight friends, each with four switches: Upgrades, Alarm, Ward and Machines. The list stays when you pick the totem up and is wiped when it is stolen.
- **New powers.** Bouncy (Carried: less fall damage and a bounce, sneak to land flat), X-ray (Carried: ores, containers and spawners show through blocks), Cloaking (Base: outsiders see the land as it was when the cloak came on; takes 50 mB/t of the totem's output), Recall (Base: an offer to go home when your totem is stolen, R to accept, N to refuse), Recall Block (Raid: delays the owner's Recall). The tree gains a fifth ring for Tier V.
- **Auras are cubes.** A power with radius 5 covers 11 x 11 x 11 blocks, so you can build to the edge. Fortify and Healing Aura draw as thin-edged cubes; an AURAS switch in the totem panel hides them for you.
- X-ray can actually be bought now: it asked for Night Sight level 2 and Night Sight only has one level.

## Elsewhere

- **Raw ores.** Tetrium, Illyrium and Magnatite ores drop raw chunks like vanilla iron, more with Fortune, the block with Silk Touch. The crucibles and the centrifuge take raw chunks as they took ore blocks.
- **Jade.** A line on cables (flow), extractors (draw from the totem), Projector Bases (tank) and valves (limit, or shut). Everything else still shows only its name and RF.
- **Guide book.** Wave Plasma and Projectors rewritten with turnable 3D hook-up scenes, a new entry on pressure, runs and valves, and the batteries entry covers every tier, stamping and the emitter screen. Every new block and item has a page.
- **JEI.** Hold Shift over any plasma block, cable or battery to see the whole hook-up built step by step in the Assembly Guide.
- **Advancements** for every new block and item.
- Operators: `/bsp totem buff <power> <level>`, `/bsp totem tier <1-5>` and `/bsp totem recloak`.

## Fixed

- Buying Recall, or any Tier V power with more than two levels, crashed the server.
- A full Projector Base looked empty and its cables read 20 mB/t; both the drawing and the figures are right now.
- Cable figures in the interface view add up to the supply.
- The Battery Charger screen ran into the inventory with nine Carried powers.
- The emitter dipped in the hand every second while running.

## Removed

- The Totem Generator, its array screen and the Reach Amplifiers.
- "Totem" dropped from block names, and "Core" from the cables: Projector, Tetrium Plasma Cable, and so on.
