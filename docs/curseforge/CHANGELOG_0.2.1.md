# BSP-Core 0.2.1

Chunk loading for Shatter Totems, a proper screen for Totem Projectors, and Jade support. This update has had little testing in game: please report anything odd.

**Existing worlds:** nothing needs resetting. The new settings are added to `serverconfig/bsp_core-server.toml` by themselves.

## Chunk loading

- **Two new upgrades** on a sixth path of the totem's upgrade tree. Both are Base upgrades, so they work while the totem is placed.
  - **Anchor** (from Tier II, 3 levels): keeps 1, 3, then 6 chunks loaded. The chunk the totem stands in is always loaded and counts as one.
  - **Survey** (from Tier III, 2 levels, once Anchor is level 2): widens the area the chunks are picked from, from 3 x 3 chunks to 5 x 5, then 7 x 7.
- **CHUNKS tab.** Once Anchor has a level, the owner's totem panel gains a CHUNKS tab: a top-down map of the ground around the totem, one tile per chunk, north at the top. Click a tile to load that chunk, click again to let it go.
- **Through a projector.** Anchor is a seventh power a Totem Generator can send. The projector at the far end gets a chunk map of its own. A projector adds no chunks: it lets you spend what is left of the totem's allowance further away. The projector's own chunk is always one of them, so a projector holding chunks keeps running and keeps using RF. If it loses its signal or runs out of RF, its chunks are let go.
- **More than one totem.** Only your main totem, the one you have held longest, gets the full allowance. Every other totem you place loads the chunk it stands in and no more, and cannot take Anchor past level 1 or buy Survey. Lose your main totem and the next one you hold takes its place.
- **Stolen and moved totems.** When a totem is stolen, every chunk chosen for it is let go, at the totem and at its projectors; the thief keeps the upgrades and chooses again. If you pick your own totem up and place it elsewhere, it remembers the layout.
- **For server owners.** New config section `chunks`: a master switch, chunks per Anchor level, range per Survey level, a limit per player, and whether chunks load only while the owner is online. That last one is also a button on the admin panel's Settings tab.

## Totem Projector screen

- Right-clicking a Totem Projector now opens a screen instead of printing a line of chat. It shows what the projector is doing (no signal, no power, or projecting), its RF, and one chip for each power with the level that reaches it. Hover over a chip for its name.
- When Anchor is one of the powers, the chunk map appears below.

## Totem Generators and cables

- **Lighter on the server.** A generator used to load the chunks along its cable every second. It now looks only at loaded chunks each second, and every 30 seconds follows the cable all the way to confirm the run. A far-off projector keeps its signal in between, without anyone standing near the cable.
- A cable cut where nobody is standing is noticed at the next full check. The interval is `projector.cableCheckSeconds` in the config.
- The generator screen has a seventh row for Anchor; its sockets and inventory sit slightly lower.
- A projector no longer loses its signal for a moment after a server restart.

## Jade

- BSP-Core now works with Jade (optional: the mod runs the same without it). Jade shows each block's name and its RF, as it does for any mod.
- Every part of an assembled multiblock reads as the machine itself rather than as a casing or a port.
- A working Decoy Totem reads as a Shatter Totem to everyone but its owner, so Jade cannot give a decoy away.

## Guide book

- New entry **Chunk Loading** under The Shatter Totem, and a page on the projector screen under Generators and Projectors.
