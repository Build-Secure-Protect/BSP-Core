#!/usr/bin/env python3
"""Generates the Compass Tuner assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_tuner_assets.py

Writes the Compass Tuner's textures, block model, block state, item model, loot table and recipe. The look is concept A, the
Gimbal Cradle (tools/preview/compass_tuner_concepts.html). This model is the still part: the slate plinth with its recessed
front panel (screen bezel, TUNE plate, red key), the deck, the round sunken well with its pin, the four corner posts (three
carry open hoppers of lapis, quartz and redstone with copper pipes into the well, the fourth a glass plasma bulb), the FE
socket at the back and a rolled chart. CompassTunerRenderer draws the moving parts: the two gimbal rings, the compass and its
needle, the countdown on the screen, the TUNE label and the plasma in the bulb. The model points north (front panel at z 0).
Rerun after changing anything here; never hand-edit the outputs.
"""
import json

from gen_factory_assets import DATA, shade
from gen_material_assets import ASSETS, hexc, hsh, write_png

NAME = "compass_tuner"
SLATE, SLATE_D, SLATE_L = "#4A4F58", "#2B2F36", "#6A707A"
BRASS, BRASS_D, BRASS_L = "#C9A24A", "#8A6A2A", "#F0D27A"
IRON, IRON_D = "#8A9099", "#5A6068"
COPPER, COPPER_D = "#C4703E", "#8A4A26"
LAPIS, QUARTZ, REDSTONE = "#2F5BD8", "#E8E2D6", "#B81E16"


def slate(x, y):
    """Slate blocks: rough stone with a mortar line across and staggered joints."""
    if y in (7, 15) or (y < 7 and x == 5) or (y > 7 and x == 11):
        return shade(SLATE_D, x, y, 3)
    return shade(SLATE, x, y, 4) if hsh(x, y, 9) > 0.15 else hexc(SLATE_L)


def slate_dark(x, y):
    """The recessed panel and the well's floor: dark, fine grain."""
    return shade(SLATE_D, x, y, 11)


def deck(x, y):
    """Deck tiles: four tiles with dark grout, lighter top edges."""
    if x in (0, 8) or y in (0, 8):
        return hexc("#1F2227")
    if x in (1, 9) or y in (1, 9):
        return shade("#5A606A", x, y, 13)
    return shade("#43484F", x, y, 14)


def metal(base, dark, light):
    def px(x, y):
        if y == 0 or x == 0:
            return hexc(light)
        if y == 15 or x == 15:
            return hexc(dark)
        return shade(base, x, y, 21) if hsh(x, y, 22) > 0.08 else hexc(dark)
    return px


def ore(base, seed, sparkle):
    """A heap of material in a hopper: grains with a few bright specks."""
    def px(x, y):
        v = hsh(x, y, seed)
        if v > 0.9:
            return hexc(sparkle)
        return shade(base, x, y, seed + 1) if v > 0.2 else tuple(int(c * 0.7) for c in hexc(base)[:3]) + (255,)
    return px


def glass(x, y):
    """The plasma bulb's glass: a bright rim, see-through middle with a streak of shine (cutout)."""
    if x in (0, 15) or y in (0, 15):
        return hexc("#BFE6FF", 255)
    if x == y + 3 and 3 <= x <= 12:
        return hexc("#E8F6FF", 255)
    return (0, 0, 0, 0)


def paper(x, y):
    """The rolled chart: parchment with faint ink lines."""
    if y % 4 == 2 and 2 <= x <= 13 and hsh(x, y, 31) > 0.25:
        return hexc("#8A7A5A")
    return shade("#E2D3A8", x, y, 32)


def red(x, y):
    if x in (0, 15) or y in (0, 15):
        return hexc("#5A100C")
    return shade("#B0201A", x, y, 41)


def screen(x, y):
    """The countdown screen's dark glass with scan lines; the renderer writes the time on it."""
    return hexc("#0F2A26") if y % 2 else hexc("#071412")


TEXTURES = {
    "slate": slate, "slate_dark": slate_dark, "deck": deck,
    "brass": metal(BRASS, BRASS_D, BRASS_L), "iron": metal(IRON, IRON_D, "#B8BEC6"), "copper": metal(COPPER, COPPER_D, "#E89A66"),
    "lapis": ore(LAPIS, 51, "#9AB8FF"), "quartz": ore(QUARTZ, 52, "#FFFFFF"), "redstone": ore(REDSTONE, 53, "#FF5A3C"),
    "glass": glass, "paper": paper, "red": red, "screen": screen,
}


def box(frm, to, tex, faces="nsewud", rot=None):
    names = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to],
         "faces": {names[f]: {"texture": "#" + tex} for f in faces}}
    if rot:
        e["rotation"] = rot
    return e


def diagonal(cx, cz, length, w, y0, y1, angle, tex):
    """A bar centred on (cx, cz) along x, turned by {angle} (multiples of 22.5) about y round its own centre."""
    return box([cx - length / 2, y0, cz - w / 2], [cx + length / 2, y1, cz + w / 2], tex, "nsewu",
               {"angle": angle, "axis": "y", "origin": [cx, (y0 + y1) / 2, cz]})


def model():
    el = []
    # the plinth: the body set back one pixel at the front, framing a recessed panel
    el.append(box([0, 0, 1], [16, 4, 15.5], "slate"))
    el.append(box([0, 0, 0], [2, 4, 1], "slate"))                 # frame left
    el.append(box([14, 0, 0], [16, 4, 1], "slate"))               # frame right
    el.append(box([2, 3.2, 0], [14, 4, 1], "slate"))              # frame top
    el.append(box([2, 0, 0], [14, 0.6, 1], "slate"))              # frame bottom (a step)
    el.append(box([2, 0.6, 0.9], [14, 3.2, 1], "slate_dark", "n"))  # the recessed panel
    el.append(box([2.8, 1.0, 0.5], [9.2, 2.9, 1], "iron"))        # screen bezel
    el.append(box([3.2, 1.3, 0.45], [8.8, 2.6, 0.5], "screen", "n"))  # the screen glass (the renderer writes on it)
    el.append(box([9.8, 1.3, 0.55], [12.4, 2.6, 1], "iron"))      # the TUNE plate (labelled by the renderer)
    el.append(box([12.8, 1.4, 0.5], [13.8, 2.4, 1], "red"))       # the red key
    # the deck and its lip
    el.append(box([0, 4, 0], [16, 4.5, 16], "deck", "nsewu"))
    # the round sunken well: an octagonal brass rim standing proud of a dark floor
    for i in range(4):
        # axis-aligned sides of the octagon
        if i % 2 == 0:
            z = 4 if i == 0 else 11.2
            el.append(box([6.2, 4.5, z], [9.8, 5.4, z + 0.8], "brass", "nsewu"))
        else:
            x = 4 if i == 3 else 11.2
            el.append(box([x, 4.5, 6.2], [x + 0.8, 5.4, 9.8], "brass", "nsewu"))
    # the corner sides are tangents: +45 turns +x toward -z (Y-axis rotation is right-handed)
    for cx, cz, angle in ((5.4, 5.4, 45), (10.6, 10.6, 45), (10.6, 5.4, -45), (5.4, 10.6, -45)):
        el.append(diagonal(cx, cz, 3.6, 0.8, 4.5, 5.4, angle, "brass"))
    el.append(box([4.8, 4.5, 4.8], [11.2, 4.52, 11.2], "slate_dark", "u"))  # the floor, sunk below the rim
    el.append(box([6.8, 4.5, 6.8], [9.2, 5.6, 9.2], "brass"))          # the pedestal
    el.append(box([7.6, 5.6, 7.6], [8.4, 9.8, 8.4], "iron", "nsewu"))  # the pin the compass rides on (it sits at y 10.2)
    # the corner posts with brass collars; three carry hoppers, the fourth (back right) the plasma bulb
    posts = {(0.5, 0.5): "lapis", (13.5, 0.5): "quartz", (0.5, 13.5): "redstone", (13.5, 13.5): None}
    for (x, z), mat in posts.items():
        el.append(box([x, 4.5, z], [x + 2, 11, z + 2], "iron", "nsewu"))
        el.append(box([x - 0.3, 5, z - 0.3], [x + 2.3, 5.6, z + 2.3], "brass"))
        if mat:
            # an open hopper: four walls and a heap of the material sunk below their tops
            x0, z0 = max(0, x - 0.8), max(0, z - 0.8)
            x1, z1 = min(16, x + 2.8), min(16, z + 2.8)
            el.append(box([x0, 11, z0], [x1, 13, z0 + 0.5], "iron"))
            el.append(box([x0, 11, z1 - 0.5], [x1, 13, z1], "iron"))
            el.append(box([x0, 11, z0 + 0.5], [x0 + 0.5, 13, z1 - 0.5], "iron"))
            el.append(box([x1 - 0.5, 11, z0 + 0.5], [x1, 13, z1 - 0.5], "iron"))
            el.append(box([x0 + 0.5, 11, z0 + 0.5], [x1 - 0.5, 12.4, z1 - 0.5], mat, "u"))
            el.append(box([x0 - 0.01, 12.9, z0 - 0.01], [x1 + 0.01, 13.3, z1 + 0.01], "brass", "nsewu"))
            # a copper pipe from the post's foot into the well
            cx, cz = (x + 1 + 8) / 2, (z + 1 + 8) / 2
            same = (x < 8) == (z < 8)
            el.append(diagonal(cx, cz, 6.2, 0.7, 4.5, 5.2, -45 if same else 45, "copper"))
        else:
            el.append(box([x - 0.2, 11, z - 0.2], [x + 2.2, 11.6, z + 2.2], "brass"))       # bulb base
            el.append(box([x - 0.6, 11.6, z - 0.6], [x + 2.6, 14.6, z + 2.6], "glass"))     # the glass bulb
            el.append(box([x - 0.2, 14.6, z - 0.2], [x + 2.2, 15.2, z + 2.2], "brass"))     # cap
            el.append(box([x + 0.7, 15.2, z + 0.7], [x + 1.3, 16, z + 1.3], "brass"))       # finial
    # the FE socket at the back: a dark block with red and iron terminals
    el.append(box([5.5, 0.8, 15.5], [10.5, 3.2, 16], "slate_dark"))
    el.append(box([6.2, 1.5, 15.95], [7.6, 2.5, 16.05], "red", "s"))
    el.append(box([8.4, 1.5, 15.95], [9.8, 2.5, 16.05], "iron", "s"))
    # the rolled chart with a red ribbon, lying on the deck at the front left
    el.append(box([2.6, 4.5, 1.4], [7.6, 5.5, 2.4], "paper"))
    el.append(box([4.7, 4.45, 1.35], [5.5, 5.55, 2.45], "red"))
    textures = {"particle": f"bsp_core:block/{NAME}_slate"}
    textures.update({k: f"bsp_core:block/{NAME}_{k}" for k in TEXTURES})
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": el}


def main():
    tex = ASSETS / "textures/block"
    for key, fn in TEXTURES.items():
        write_png(tex / f"{NAME}_{key}.png", 16, 16, fn)
    (ASSETS / f"models/block/{NAME}.json").write_text(json.dumps(model(), indent=2))
    (ASSETS / f"models/item/{NAME}.json").write_text(json.dumps({"parent": f"bsp_core:block/{NAME}"}, indent=2))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"bsp_core:block/{NAME}"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing}"] = v
    (ASSETS / f"blockstates/{NAME}.json").write_text(json.dumps({"variants": variants}, indent=2))
    # the block keeps its four upgrade levels as an item; only the owner and their team can mine it
    ops = [{"source": k, "target": "BlockEntityTag." + k, "op": "replace"} for k in ("Across", "Depth", "Cooldown", "Speed")]
    (DATA / f"loot_tables/blocks/{NAME}.json").write_text(json.dumps({"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"bsp_core:{NAME}", "functions": [{
            "function": "minecraft:copy_nbt", "source": "block_entity", "ops": ops}]}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, indent=2))
    # a plain compass in the middle, so an upgraded Totem Compass is never used up by the recipe
    (DATA / f"recipes/{NAME}.json").write_text(json.dumps({"type": "minecraft:crafting_shaped",
        "pattern": ["ILI", "QCR", "III"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "L": {"item": "minecraft:lapis_block"}, "Q": {"item": "minecraft:quartz_block"},
                "R": {"item": "minecraft:redstone_block"}, "C": {"item": "minecraft:compass"}},
        "result": {"item": f"bsp_core:{NAME}"}}, indent=2))
    print("compass tuner assets written")


if __name__ == "__main__":
    main()
