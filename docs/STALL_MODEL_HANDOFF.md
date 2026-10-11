# Handoff: design the Market Stall models (for a separate design conversation)

> **Status 2026-10-11: delivered and approved by the owner.** Eight skins (the four below plus `nether`, `yatai`, `nautical`, `arcane`), each with a broken variant: 16 models, 123 textures. Source `tools/gen_stall_design.py`, output `tools/stall/` (models, textures, `layout.json`, `NOTES.md` with a hook-up checklist), preview `tools/preview/stall_models.html`. The build conversation starts at `tools/stall/NOTES.md`. The brief below is kept as written.

This is a design-only brief. The files you deliver come back to the BSP-Core build conversation, which wraps them in the mod (registration, generator, renderer, Jade, book, tests). Nothing here needs the mod's code.

## What the block is

BSP-Core (Forge, Minecraft 1.20.1) is adding shops. A **Market Stall** is a one-block shop front an admin places and ties to a shop; players right-click it to open the shop. It must come in **four skins** that are genuinely different builds, not recolours of one shape:

| Skin id | Feel | Think of |
|---|---|---|
| `fantasy` | medieval market, cloth and timber, a banner | a tavern-square trader's stall |
| `scifi` | clean panels, light strips, a holo pane | a vending terminal on a station deck |
| `steampunk` | copper, brass, rivets, pipes, a dial | a tinkerer's booth with a boiler |
| `vanilla` | plain oak, cobble, wool, a sign | something a player would build from vanilla blocks |

Each skin needs a **broken, out-of-order variant**: the same stall after neglect (torn cloth, cracked pane, boards loose, pipes leaking, a tag or sign saying it is out of order). The broken variant is what a stall shows while no shop is assigned to it.

So: **eight models** (four skins x normal and broken), plus textures.

## Hard constraints (Minecraft block model rules)

- **Format**: vanilla Minecraft JSON block models (`elements` with `from`/`to` boxes, `faces` with `uv` and `texture`, optional `rotation`). Blockbench's "Minecraft Block/Item" export is exactly this; hand-written JSON is fine too.
- **Units**: 16 units = one block. The stall's **footprint is one block**: keep x and z inside 0..16 for anything the player should be able to walk around. Height may go up to **32** (two blocks tall is fine). Small overhangs (an awning, a sign) may extend outside the block: coordinates from -16 to 32 are legal on every axis. The collision box stays the one block; overhangs are visual only.
- **Rotation**: an element may rotate only around one axis, by -45, -22.5, 0, 22.5 or 45 degrees (`"rotation": {"origin": [x,y,z], "axis": "x"|"y"|"z", "angle": 22.5}`). No other angles.
- **Front**: the stall's **front faces north** (the -z side, the face at z = 0) in the model. The mod rotates it for the other three facings.
- **Element budget**: aim for **under 120 elements** per model (performance; stalls stand in rows). Fewer is better.
- **Textures**: either (a) **your own PNGs**, square, 16x16 or 32x32 each, named `stall_<skin>_<part>.png` (for example `stall_fantasy_cloth.png`), referenced in the model's `textures` map as `"cloth": "bsp_core:block/stall/stall_fantasy_cloth"`; or (b) flat colours drawn in a palette PNG with UV picks. (a) is the better route for real material differences. Transparent pixels are allowed (the mod renders the block as cutout); no translucency.
- Give every model a `"particle"` texture.
- Animated textures (mcmeta) are allowed but optional.

## What the mod draws on top (so you leave room for it)

The mod's renderer overlays three things; the model should leave the surfaces where they go plain, and you tell us the coordinates:

1. **Stock lamp**: a small lit quad, green when the shop has stock, red when sold out. Give a flat face (any size about 2 x 2 units) and its coordinates and normal direction.
2. **Open lamp**: the same, amber while the shop is open. Another flat face.
3. **Display item**: the shop's first item floats and turns at a point you choose (over the counter, behind a pane, on a shelf). Give the point and the scale (0.5 is half an item).

Put these in a short notes file per skin (see deliverables). The broken variant has no lamps and no display item, so it may cover those faces with damage.

## Also wanted, if time allows

- **Inventory icon**: the normal `vanilla` skin is used for the item icon; nothing extra needed. If you want a specific angle, add a `display.gui` transform to that model (`rotation`, `translation`, `scale`).
- The **Vendor Post**: a one-block post that a stock game mob (villager, wandering trader) stands on, with a lamp and a small name plate on its front, in the same four skins. Only if the stalls are done; the mod has a plain one already.

## The Stall Display (a second, smaller block)

A **Stall Display** is a one-block stand an admin places near a stall or vendor and links to it; the mod floats that shop's wares above it, one item after another, turning slowly, while the shop is open. It is set dressing for a market: a plinth, a pedestal, a hovering pane, a crate with a lid, whatever reads as "look what is sold here". One model is enough (no skins needed; if you want one per skin, name them `stall_display_<skin>.json` and say so in the notes). Keep the top open: the item floats above the block, so nothing should stand in the top third of the space over it. Give the **float point** (x, y, z in model units, y may be above 16) and the **scale** (0.8 is the default) in `NOTES.md`; they become the defaults, and admins can change size and height per display in game. It needs no lamps. The mod has a plain placeholder plinth today, which your model replaces.

## Deliverables (what to send back)

```
stall/
  models/
    market_stall_fantasy.json
    market_stall_fantasy_broken.json
    market_stall_scifi.json
    market_stall_scifi_broken.json
    market_stall_steampunk.json
    market_stall_steampunk_broken.json
    market_stall_vanilla.json
    market_stall_vanilla_broken.json
    stall_display.json
  textures/
    stall_<skin>_<part>.png ...
    stall_display_<part>.png ...   (and .mcmeta files for any animated texture)
  NOTES.md
```

`NOTES.md` lists, per skin: the stock lamp face (coordinates and which way it faces), the open lamp face, the display item point and scale, and for the Stall Display its float point and scale, and anything the build conversation should know (which elements are the awning, what the broken variant changed). Texture references inside the models must be `bsp_core:block/stall/<file name without .png>`.

The build conversation then: copies the files into `src/main/resources/assets/bsp_core/` through a generator (so the rule "every asset comes from a generator" holds), writes the blockstate (`facing` x `skin` x `broken`), adds the lamps, the display item, the Jade line, the guide page and the tests.

## Style anchors already in the mod

- The bank line uses a "Cash Machine" look: beige bezel, black phosphor screen with green text, amber lamps; its blocks are dark steel with amber lamps and gold trim. The `scifi` or `steampunk` stall can echo the amber lamp; the others need not.
- Existing BSP blocks are drawn from a small flat-colour atlas (dark navy hull, grey trims, violet and turquoise glows). The stalls are allowed to look richer than that; they are set dressing for shops.
