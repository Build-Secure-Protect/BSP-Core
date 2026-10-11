# Market Stall models: notes for the build conversation

**Status: approved by the owner (2026-10-11), ready to hook up.** Eight skins, each with a broken variant.

Made by `tools/gen_stall_design.py` (stdlib Python; rerun it, never hand-edit). Everything in this folder is the deliverable of `docs/STALL_MODEL_HANDOFF.md`. The preview page `tools/preview/stall_models.html` renders these exact files. `layout.json` holds the overlay points below in machine-readable form.

## Common to all eight models

- Vanilla JSON block models, `parent: minecraft:block/block`, `render_type: minecraft:cutout` (Forge reads it), `ambientocclusion: false` (as the bank models). Front faces north (-z). Every element has a `name`.
- **x stays inside 0..16 everywhere**, so stalls in a row never overlap or z-fight; only the front (z < 0) and the top (y > 16) overhang. Collision stays one block; the shape is two blocks tall, so the block should also block or be paired with the space above (the build conversation decides; an invisible upper block like `bank_atm_upper` works).
- Glowing elements carry `forge_data: {block_light: 15, sky_light: 15}` and `shade: false` on their faces (the mod's existing models use the same field). Unlit in the broken variants.
- Animated textures: `stall_scifi_holo` (6 frames), `stall_scifi_holo_broken` (8 frames, flicker), `stall_scifi_holo_label` (6), `stall_steampunk_firebox` (4 frames), `stall_nether_magma` (3), `stall_arcane_rune` (4); each has a `.mcmeta`.
- Lamp faces: the model draws an unlit lamp (dark glass) there; draw the lit quad 0.01 to 0.02 in front of it (towards -z). "Customer's left" is +x (the customer stands at -z looking south).
- Broken variants keep the lamp housings, unlit, so the lamp overlay simply is not drawn. No display item.
- Inventory icon: `market_stall_vanilla.json` carries `display` transforms (gui scale 0.4, shifted down 3); check the slot in game.

| Model | Elements |
|---|---|
| `market_stall_fantasy` | 33 |
| `market_stall_fantasy_broken` | 29 |
| `market_stall_scifi` | 31 |
| `market_stall_scifi_broken` | 36 |
| `market_stall_steampunk` | 40 |
| `market_stall_steampunk_broken` | 44 |
| `market_stall_vanilla` | 27 |
| `market_stall_vanilla_broken` | 29 |
| `market_stall_nether` | 28 |
| `market_stall_nether_broken` | 27 |
| `market_stall_yatai` | 27 |
| `market_stall_yatai_broken` | 26 |
| `market_stall_nautical` | 22 |
| `market_stall_nautical_broken` | 24 |
| `market_stall_arcane` | 26 |
| `market_stall_arcane_broken` | 21 |
| `stall_display_fantasy` | 12 |
| `stall_display_fantasy_broken` | 13 |
| `stall_display_scifi` | 9 |
| `stall_display_scifi_broken` | 12 |
| `stall_display_steampunk` | 12 |
| `stall_display_steampunk_broken` | 13 |
| `stall_display_vanilla` | 7 |
| `stall_display_vanilla_broken` | 8 |
| `stall_display_nether` | 8 |
| `stall_display_nether_broken` | 10 |
| `stall_display_yatai` | 10 |
| `stall_display_yatai_broken` | 11 |
| `stall_display_nautical` | 9 |
| `stall_display_nautical_broken` | 10 |
| `stall_display_arcane` | 9 |
| `stall_display_arcane_broken` | 8 |

## fantasy: Timber market booth

Dark-oak counter with a plank front, four posts, knee braces, a striped crimson and cream canopy sloping forward over the front with a scalloped valance, a blue and gold banner draped over the counter front, a lantern hung under the canopy, a green curtain at the back, a crate of apples, a sack and two bottles.

**Broken:** Canopy collapsed steeply to the back beam and torn, banner faded and shredded and hanging askew, one front post snapped (the front beam drops onto it), the upper counter rail loose, goods gone except a toppled crate and a lying bottle, lantern dark and fallen, cobwebs front and back, a pinned CLOSED note.

**Top:** `canopy` (rotated -22.5 on x around the front beam) and `canopy_valance`; broken: `canopy_collapsed`, `rag_a`, `rag_b`.

- Stock lamp: x 0..2, y 13.25..15.25, at z = -0.5, facing north
- Open lamp: x 14..16, y 13.25..15.25, at z = -0.5, facing north
- Display item: point (8, 16.25, 6), scale 0.4

## scifi: Vending terminal

Two-block monolith: dark plinth with a glowing base line, white cabinet with a dispense slot, keypad and lamp bar, an open alcove with a glowing projector pad (the item hovers over it), alcove light strips and downlight, a holo pane leaning out above the alcove (animated SHOP screen), side fins with light strips, a hood with an under-glow, a vent and an antenna with a red tip.

**Broken:** Every strip dark, the holo pane cracked and hanging by one corner (animated flicker with glitch frames and ERR), the access door swung open showing the circuit board with three cables spilling out, one red error light, hazard tape crossed over the alcove (OUT OF / ORDER), pad dark, antenna bent, panels scorched and grimy.

**Top:** No awning; the `hood` is the top. `holo` is the leaning pane (cutout, emissive).

- Stock lamp: x 3..5, y 8.5..10.5, at z = 1, facing north
- Open lamp: x 6..8, y 8.5..10.5, at z = 1, facing north
- Display item: point (8, 17.25, 8), scale 0.45

## steampunk: Tinkerer's boiler booth

Lopsided on purpose: a walnut counter with a riveted iron front, brass trims, two gears and brass-bezel lamps on the customer's right; a copper boiler with brass bands, a glowing firebox, a pressure dial, a whistle and an iron chimney to the top of the second block on the customer's left; a copper back panel with a brass cornice and nameplate; a pipe from the boiler across to a valve wheel; the item floats under a brass-based glass bell jar.

**Broken:** Copper and pipes gone green, firebox cold, the pipe burst (a stub, a sagging length, a steam puff, a puddle on the counter), dial glass cracked with the needle dropped, an OUT OF ORDER tag hanging from the dial, the big gear fallen and leaning on the counter, the bell jar cracked, chimney cap knocked askew.

**Top:** No awning; the chimney is the top (`chimney_cap`, `chimney_top`).

- Stock lamp: x 1.5..3.5, y 8.25..10.25, at z = 1.25, facing north
- Open lamp: x 6..8, y 8.25..10.25, at z = 1.25, facing north
- Display item: point (5.5, 16.25, 6.25), scale 0.375

## vanilla: Player-built stall

Cobblestone base, oak counter with two trapdoor fronts and a slab top, four fence-style posts with rails, a stepped red and white wool awning (no slopes, as a player would build it), a SHOP sign hung under the awning lip, a lantern and a flower pot at the back of the counter, a village bell at the front.

**Broken:** Mossy cobble, weathered planks, the customer's-left front post gone and the awning's front step sagging into the gap with holes torn in the wool, the sign hanging by one chain reading CLOSED, one trapdoor crooked and the other missing, two boards nailed in an X across the counter, a cobweb front and back, the lantern knocked over and dark, the flower dead, the bell gone.

**Top:** `awning_front`, `awning_middle`, `awning_back` (stepped, unrotated; broken rotates `awning_front` -22.5 on z).

- Stock lamp: x 0..2, y 14..16, at z = 0.5, facing north
- Open lamp: x 14..16, y 14..16, at z = 0.5, facing north
- Display item: point (8, 16.5, 7), scale 0.45

## nether: Blackstone shrine

Polished blackstone counter with a glowing magma panel (animated) and a gold edge, a gold barter plate for the item, basalt pillars, a basalt back wall, a heavy lintel with jagged teeth, nether wart on the roof, two soul lanterns hung on chains.

**Broken:** Cracked blackstone with gold flecks, the magma cold, one lantern's chain snapped and the lantern fallen on the counter, the other lantern dark, a tooth broken off the lintel and lying on the counter, a scorched CLOSED note.

**Top:** `lintel`, `roof`, `tooth_*` (teeth rotated 45 on z).

- Stock lamp: x 0.25..2.25, y 13..15, at z = 1.5, facing north
- Open lamp: x 13.75..15.75, y 13..15, at z = 1.5, facing north
- Display item: point (8, 16.75, 8.5), scale 0.45

## yatai: Eastern street cart

A cedar cart on two side wheels with a red lacquer front and top, bamboo posts, a tiled gable roof, an indigo noren curtain in three panels, two red paper lanterns, a covered pot with a ladle and a stack of bowls.

**Broken:** Lacquer and wood faded, tiles missing, the front roof slope dropped steeply, the noren torn with a panel gone, one wheel knocked askew, one lantern torn and fallen on the counter, the other hanging crooked and dark, a CLOSED note.

**Top:** `roof_front` (rotated -22.5 on x; -45 broken), `roof_back` (+22.5 on x, overhangs 2 units behind the block), `ridge`.

- Stock lamp: x 0.5..2, y 13..15, at z = 1.25, facing north
- Open lamp: x 14..15.5, y 13..15, at z = 1.25, facing north
- Display item: point (8, 16, 8), scale 0.4

## nautical: Dockside stall

A deck plank on two barrels, a fishing net across the barrel fronts, an anchor at the side, a rope coil and a crate of fish on the plank, a mast at the back with a yard, a canvas sail as the backdrop and a brass ship's lantern.

**Broken:** Rotting planks with algae, barnacled barrels, the net and sail torn, the yard and sail slipped, the lantern smashed on the plank, the fish crate empty, seaweed hanging off the barrels, a painted CLOSED board leaning on the front.

**Top:** `mast`, `yard`, `sail` (the mast reaches 31.5).

- Stock lamp: x 3..5, y 8..10, at z = 4.25, facing north
- Open lamp: x 11..13, y 8..10, at z = 4.25, facing north
- Display item: point (8, 16.25, 7), scale 0.45

## arcane: Mage's pedestal

A violet stone counter with a gold trim and a starry blue cloth, two obelisks with gold caps and floating crystals, three animated rune rings (one flat, two crossed) around the item, an open spellbook on a stand, two candles.

**Broken:** Stone cracked, crystals dark (one fallen), the rune rings broken and dim with one fallen flat, the book closed and dusty, the candles out and knocked over, the cloth torn, a CLOSED note.

**Top:** No roof; the crystals are the top (`crystal_*`).

- Stock lamp: x 2..4, y 8..10, at z = 2, facing north
- Open lamp: x 12..14, y 8..10, at z = 2, facing north
- Display item: point (8, 17.75, 8.5), scale 0.45

## Hook-up checklist for the build conversation

Skin ids, in order (use this order for the enum): `fantasy`, `scifi`, `steampunk`, `vanilla`, `nether`, `yatai`, `nautical`, `arcane`. Models: `market_stall_<skin>.json` and `market_stall_<skin>_broken.json`.

1. **Assets generator** (`tools/gen_stall_assets.py`): import or run `gen_stall_design.py`, then copy `tools/stall/models/*.json` to `assets/bsp_core/models/block/` and `tools/stall/textures/*` (PNGs and `.mcmeta`) to `assets/bsp_core/textures/block/stall/`. The models already reference `bsp_core:block/stall/<name>`; keep the file names. Re-run the validator after copying (element count, rotations, every texture present).
2. **Blockstate** `market_stall.json`: `facing` x `skin` x `broken` x `half` (see 4). Lower half: model `bsp_core:block/market_stall_<skin>` or `..._broken`, `y` 0 / 90 / 180 / 270 for north / east / south / west (the same as `facing_blockstate` in `gen_bank_assets.py`). Upper half: no model (an empty `minecraft:block/air`-style model or a particle-only model), as `bank_atm_upper`.
3. **Item model** `models/item/market_stall.json`: `{"parent": "bsp_core:block/market_stall_vanilla"}`. The vanilla model carries the `display` transforms (gui, ground, fixed, hands).
4. **Block** `shop/MarketStallBlock`: `FACING` (horizontal, placed facing the player), `SKIN` (an `EnumProperty` of a `StallSkin` enum with the eight ids above as serialized names), `BROKEN` (boolean, true while no shop is assigned; the block entity sets it on assign and unassign), `HALF` (two blocks tall, copy `BankAtmBlock`: the lower half holds the block entity and the whole model, the upper half only keeps the space and forwards clicks and breaking). `noOcclusion()`; collision one full block per half. The models set `render_type: minecraft:cutout` themselves.
5. **Choosing a skin** (owner's decision, 2026-10-11): the admin picks the skin in the stall setup panel (the panel used to assign the shop and set opening times); it is stored in the block state. One item places every skin; a fresh stall starts as `vanilla` and broken until assigned.
6. **Renderer** `client/MarketStallRenderer` (lower half only): use the same transform as `BankAtmRenderer`: `translate(0.5, 0, 0.5)`, `mulPose(Axis.YP.rotationDegrees(-yRot(facing)))`, `translate(-0.5, 0, -0.5)`, then model units / 16. Values come from `layout.json` (have the generator emit them as Java constants or a JSON in the assets). Not broken: the **stock lamp** quad at `stock_lamp` (green `0x58F29A` while the shop has stock, red `0xE24B3B` when sold out) and the **open lamp** at `open_lamp` (amber `0xF2B544` while open, nothing while closed), both facing north, 0.01 in front of the given z (towards -z), full bright. The **display item**: the shop's first item at `item`, scaled by `item_scale`, `ItemDisplayContext.FIXED`, spinning on game time, using the level's light. Broken: draw nothing. Inflate `getRenderBoundingBox` to two blocks tall.
7. **Glow**: the lit parts glow through `forge_data` in the models; the block itself needs no light level. Optionally `lightLevel` 7 while not broken for the nether, sci-fi, steampunk, yatai and arcane skins.
8. **Rest of the feature**: assignment and opening times like the Vendor Post, redstone to the block below while open, Jade line (shop, skin, stock state), JEI info, guide page (no history words), lang through `gen_shop_lang.py`, game tests (assigning clears `BROKEN`, unassigning sets it, the skin survives a reload), `TESTING_BANKING` part.
9. **Check in game** (not verifiable here): facing for all four directions, the inventory icon angle, the sci-fi hazard tape text reads the right way round, animated textures run, lamps sit on their housings.

## Stall Displays

**Status: approved by the owner (2026-10-11), ready to hook up.** One-block displays for the existing `StallDisplayBlock`, one per stall skin, each with a broken variant, built from the stall textures plus two wordless ones (`stall_scifi_holo_label`, `stall_vanilla_sign_blank`: the owner wants no SHOP word on the displays). Models `stall_display_<skin>.json` and `stall_display_<skin>_broken.json` in `models/`; item points in `display_layout.json`.

| Skin | Look | Item centre (model units) | Item width | `SCALE` with GROUND |
|---|---|---|---|---|
| `fantasy` | Timber-cornered crate, crimson cushion with gold tassels, a small banner on the front | (8, 17, 8) | 7 | 0.875 |
| `scifi` | Holo projector column: glowing base and collar, corner strips, a wordless holo label pane, a lit pad | (8, 17.5, 8) | 7 | 0.875 |
| `steampunk` | Riveted copper stand with a dial, a gear and a pipe; the item floats inside a bell jar | (8, 12.25, 8) | 5.5 | 0.688 |
| `vanilla` | Cobble base, fence post, oak slab with a striped carpet, a blank oak sign, a lantern | (8, 18.5, 8) | 7 | 0.875 |
| `nether` | Blackstone column with a magma front, a gold rim, two basalt spikes, a soul lantern | (8, 16.5, 7.5) | 6.5 | 0.812 |
| `yatai` | Lacquered tray stand, bamboo frame with a noren backdrop, a paper lantern | (8, 12.5, 7) | 5.5 | 0.688 |
| `nautical` | Barrel with a net and a rope ring, an anchor at the side, a ship's lantern on the lid | (8, 16, 7) | 7 | 0.875 |
| `arcane` | Violet column with a starry cloth, gold top, crystals, three rune rings around the item | (8, 16.5, 8) | 6 | 0.75 |

**Broken** (owner's decision: while unlinked, or while the linked shop is closed or missing): the same build neglected, as the stalls (cold glows, torn cloth, cracks, a fallen piece, a CLOSED note or OUT OF ORDER tape or tag). No item.

### Hook-up for the display

1. Copy the 16 `stall_display_*` models with the stall models (same generator); they use the stall textures.
2. Blockstate `stall_display.json`: `facing` x `skin` x `broken`, model `bsp_core:block/stall_display_<skin>` or `..._broken`, `y` 0 / 90 / 180 / 270. The placeholder model from `gen_bank_assets.py` goes.
3. `StallDisplayBlock`: add `SKIN` (the same `StallSkin` enum as the stall) and `BROKEN`. Item model parent `stall_display_vanilla`.
4. Skin (owner's decision): **the display copies the skin of the stall it is linked to**, updated when it links and when the stall's skin changes; linked to a Vendor Post or unlinked, it shows `vanilla`.
5. `StallDisplayRenderer`: replace the fixed `HEIGHT` and `SCALE` with the per-skin point and `SCALE` above: translate to the item centre / 16 (rotated with the block facing like the stall renderer, although the points are centred so only the z of nether, yatai and nautical moves), keep the bob and spin, GROUND context. Nothing drawn while broken. The steampunk and yatai items sit inside the jar and frame, so keep their bob small.

## Textures

All under `textures/`, referenced as `bsp_core:block/stall/<name>`; 16x16 except the text-bearing ones (notes, tags, signs, tape, holo) at 32x32.

`stall_arcane_book`, `stall_arcane_book_closed`, `stall_arcane_candle`, `stall_arcane_candle_out`, `stall_arcane_cloth`, `stall_arcane_cloth_torn`, `stall_arcane_cover`, `stall_arcane_crystal`, `stall_arcane_crystal_dark`, `stall_arcane_lamp`, `stall_arcane_note`, `stall_arcane_rune`, `stall_arcane_rune_broken`, `stall_arcane_stone`, `stall_arcane_stone_cracked`, `stall_arcane_trim`, `stall_fantasy_banner`, `stall_fantasy_banner_torn`, `stall_fantasy_cloth`, `stall_fantasy_cloth_torn`, `stall_fantasy_curtain`, `stall_fantasy_gold`, `stall_fantasy_goods`, `stall_fantasy_lamp`, `stall_fantasy_lantern`, `stall_fantasy_lantern_off`, `stall_fantasy_note`, `stall_fantasy_planks`, `stall_fantasy_planks_worn`, `stall_fantasy_timber`, `stall_fantasy_valance`, `stall_fantasy_web`, `stall_nautical_barrel`, `stall_nautical_barrel_old`, `stall_nautical_barrel_top`, `stall_nautical_crate`, `stall_nautical_iron`, `stall_nautical_lamp`, `stall_nautical_lantern`, `stall_nautical_lantern_off`, `stall_nautical_net`, `stall_nautical_net_torn`, `stall_nautical_planks`, `stall_nautical_planks_rot`, `stall_nautical_rope`, `stall_nautical_sail`, `stall_nautical_sail_torn`, `stall_nautical_sign`, `stall_nether_basalt`, `stall_nether_blackstone`, `stall_nether_blackstone_cracked`, `stall_nether_chain`, `stall_nether_gold`, `stall_nether_lamp`, `stall_nether_magma`, `stall_nether_magma_cold`, `stall_nether_note`, `stall_nether_soul_lantern`, `stall_nether_soul_lantern_off`, `stall_nether_wart`, `stall_scifi_cable`, `stall_scifi_circuit`, `stall_scifi_detail`, `stall_scifi_error`, `stall_scifi_holo`, `stall_scifi_holo_broken`, `stall_scifi_holo_label`, `stall_scifi_hull`, `stall_scifi_lamp`, `stall_scifi_pad`, `stall_scifi_pad_off`, `stall_scifi_panel`, `stall_scifi_panel_dirty`, `stall_scifi_strip`, `stall_scifi_strip_off`, `stall_scifi_tape`, `stall_steampunk_brass`, `stall_steampunk_copper`, `stall_steampunk_copper_patina`, `stall_steampunk_dial`, `stall_steampunk_dial_broken`, `stall_steampunk_firebox`, `stall_steampunk_firebox_cold`, `stall_steampunk_gear`, `stall_steampunk_glass`, `stall_steampunk_glass_broken`, `stall_steampunk_iron`, `stall_steampunk_lamp`, `stall_steampunk_pipe`, `stall_steampunk_pipe_patina`, `stall_steampunk_puddle`, `stall_steampunk_steam`, `stall_steampunk_tag`, `stall_steampunk_wood`, `stall_vanilla_awning`, `stall_vanilla_awning_torn`, `stall_vanilla_bell`, `stall_vanilla_cobble`, `stall_vanilla_cobble_mossy`, `stall_vanilla_lamp`, `stall_vanilla_lantern`, `stall_vanilla_lantern_off`, `stall_vanilla_misc`, `stall_vanilla_planks`, `stall_vanilla_planks_old`, `stall_vanilla_sign`, `stall_vanilla_sign_blank`, `stall_vanilla_sign_closed`, `stall_vanilla_trapdoor`, `stall_vanilla_web`, `stall_yatai_bamboo`, `stall_yatai_lacquer`, `stall_yatai_lacquer_worn`, `stall_yatai_lamp`, `stall_yatai_misc`, `stall_yatai_noren`, `stall_yatai_noren_torn`, `stall_yatai_note`, `stall_yatai_paper_lantern`, `stall_yatai_paper_lantern_torn`, `stall_yatai_roof`, `stall_yatai_roof_broken`, `stall_yatai_wheel`, `stall_yatai_wood`, `stall_yatai_wood_old`
