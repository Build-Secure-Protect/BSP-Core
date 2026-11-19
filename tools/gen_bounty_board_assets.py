#!/usr/bin/env python3
"""Generates the Bounty Board's assets and recipe for BSP-Core (no third-party dependencies).

  python3 tools/gen_bounty_board_assets.py

The Bounty Board is look A, the "Frontier Notice Board" (owner's pick, 2026-11-18, from tools/preview/bounty_board_concepts.html):
two blocks wide, two tall, one deep, facing the player who places it, in dark oak and spruce with brass and iron. As with the
Trade Post, the lower block on the right as you face it is the controller and carries the whole model (elements run from x 0
to 32 and y 0 to 32; the second column is the controller's clockwise neighbour, so +x here); the other three parts have an empty
model. Front faces north (z = 0). Vanilla textures only, so the wood reads as the wood it is made of.

Baked here: the two posts with their rope lashings and paper scraps, the cross beam and knee braces, the pitched shingle roof and
its ridge, the six uneven boards and the battens behind them, the sign board, the four brass bezels with their dark recesses and
nails, the shelf with its brackets and the three key plaques, and the lantern's chain, cap, cage and glass. Drawn by
BountyBoardRenderer: the posters on the four screens, the WANTED letters on the sign, the key names, and the lantern's flicker.
Model element rotations are limited to steps of 22.5 degrees, so the posters hang straight. Never hand-edit the outputs.
"""
import json
from pathlib import Path

from gen_material_assets import ASSETS

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data"

TEX = {
    "log": "minecraft:block/dark_oak_log", "log_top": "minecraft:block/dark_oak_log_top", "beam": "minecraft:block/stripped_dark_oak_log",
    "plank_a": "minecraft:block/stripped_spruce_log", "plank_b": "minecraft:block/stripped_dark_oak_log", "planks": "minecraft:block/dark_oak_planks",
    "roof": "minecraft:block/spruce_planks", "brass": "minecraft:block/gold_block", "iron": "minecraft:block/iron_block",
    "dark": "minecraft:block/black_concrete", "rope": "minecraft:block/hay_block_side", "paper": "minecraft:block/birch_planks",
    "glow": "minecraft:block/shroomlight",
}
GLOW = {"glow"}

# the three keys on the shelf, as the player sees them from the left: x from, x to (model px). BountyBoardBlock.Key uses the same numbers.
KEYS = [("board", 21.0, 27.0), ("place", 13.0, 19.0), ("hunts", 5.0, 11.0)]
KEY_Y = (0.6, 3.0)
KEY_FRONT = 2.0
# the four screens: x from, x to, y from, y to of each recess (model px), in the order the renderer fills them (top left first as seen;
# as you face the board, +x is on your left)
SCREENS = [(17.2, 26.8, 14.4, 21.4), (5.2, 14.8, 14.4, 21.4), (17.2, 26.8, 5.0, 12.0), (5.2, 14.8, 5.0, 12.0)]
SCREEN_Z = 6.8      # the recess back; the bezel's front is at 6.0
SIGN = (9.0, 23.0, 23.2, 26.8)


def vbox(frm, to, tex, top=None):
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for face, (fw, fh) in dims.items():
        fw, fh = max(0.01, min(fw, 16)), max(0.01, min(fh, 16))
        t = top if top and face in ("up", "down") else tex
        el["faces"][face] = {"uv": [0, 0, round(fw, 3), round(fh, 3)], "texture": "#" + t}
    if tex in GLOW:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def rot(el, origin, axis, angle):
    el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return el


def frame():
    e = []
    for x0 in (0.5, 28.5):
        e.append(vbox([x0, 0, 6], [x0 + 3, 31, 9], "log", "log_top"))
        # rope lashings: two bands round each post
        for y in (11.0, 12.6):
            e.append(vbox([x0 - 0.3, y, 5.7], [x0 + 3.3, y + 1.2, 9.3], "rope"))
        # a torn paper notice on the post's front
        e.append(vbox([x0 + 0.3, 15, 5.85], [x0 + 2.7, 19.2, 6], "paper"))
        e.append(vbox([x0 + 1.3, 18.6, 5.7], [x0 + 1.7, 19, 5.85], "iron"))
    e.append(vbox([0, 27.5, 5.5], [32, 30, 9.5], "beam"))
    # knee braces between the posts and the beam
    e.append(rot(vbox([3.6, 22.5, 6.6], [5.2, 28.5, 8.4], "beam"), [4.4, 25.5, 7.5], "z", -45))
    e.append(rot(vbox([26.8, 22.5, 6.6], [28.4, 28.5, 8.4], "beam"), [27.6, 25.5, 7.5], "z", 45))
    return e


def roof():
    e = []
    # two slopes from the ridge (y 31, z 7.5): the front one comes forward over the screens, the back one over the battens
    e.append(rot(vbox([0, 30.4, -1.5], [32, 31.6, 7.5], "roof"), [16, 31, 7.5], "x", -22.5))
    e.append(rot(vbox([0, 30.4, 7.5], [32, 31.6, 16.5], "roof"), [16, 31, 7.5], "x", 22.5))
    e.append(vbox([0, 31.2, 6.8], [32, 32, 8.2], "beam"))
    # shingle edges along the front eave
    for i in range(8):
        x = i * 4
        e.append(rot(vbox([x + 0.2, 29.9, -1.6], [x + 3.8, 30.4, -0.4], "planks"), [16, 31, 7.5], "x", -22.5))
    return e


def boards():
    e = []
    xs = [3.5, 7.7, 11.9, 16.1, 20.3, 24.5]
    tops = [27.3, 27.8, 27.0, 27.6, 27.2, 27.9]
    for i, x in enumerate(xs):
        e.append(vbox([x, 1.5, 7], [x + 4.0, tops[i], 8.6], "plank_a" if i % 2 == 0 else "plank_b"))
    # battens behind the boards
    for y in (6, 22):
        e.append(vbox([3.5, y, 8.6], [28.5, y + 2, 9.6], "planks"))
    # the sign board under the beam (WANTED is drawn on it) and its two nails
    x0, x1, y0, y1 = SIGN
    e.append(vbox([x0, y0, 6.2], [x1, y1, 7], "planks"))
    for x in (x0 + 0.8, x1 - 1.2):
        e.append(vbox([x, y1 - 1.0, 5.9], [x + 0.4, y1 - 0.6, 6.2], "iron"))
    return e


def screens():
    e = []
    t = 0.8
    for x0, x1, y0, y1 in SCREENS:
        # the brass bezel, standing 1 px proud of the boards, and the dark recess the poster is drawn on
        e += [vbox([x0 - t, y1, 6.0], [x1 + t, y1 + t, 7], "brass"), vbox([x0 - t, y0 - t, 6.0], [x1 + t, y0, 7], "brass"),
              vbox([x0 - t, y0, 6.0], [x0, y1, 7], "brass"), vbox([x1, y0, 6.0], [x1 + t, y1, 7], "brass")]
        e.append(vbox([x0, y0, SCREEN_Z], [x1, y1, 7], "dark"))
        cx = (x0 + x1) / 2
        e.append(vbox([cx - 0.3, y1 + t, 5.6], [cx + 0.3, y1 + t + 0.6, 6.2], "iron"))
    return e


def shelf():
    e = [vbox([2, 1.6, 2.5], [30, 2.8, 7], "planks")]
    for x in (6, 25):
        e.append(vbox([x, 0, 4], [x + 1, 1.6, 7], "beam"))
    for _, x0, x1 in KEYS:
        e.append(vbox([x0, KEY_Y[0], KEY_FRONT], [x1, KEY_Y[1], 2.5], "planks"))
    return e


def lantern():
    e = []
    # hung from the front eave on the right as you face it (x 0 to 3), in front of the post
    e.append(vbox([1.2, 24, 2.2], [1.8, 29.2, 2.8], "iron"))
    e.append(vbox([0, 23, 1], [3, 24, 4], "iron"))
    e.append(vbox([0, 18.6, 1], [3, 19.2, 4], "iron"))
    e.append(vbox([0.4, 19.2, 1.4], [2.6, 23, 3.6], "glow"))
    for x, z in ((0, 1), (2.6, 1), (0, 3.6), (2.6, 3.6)):
        e.append(vbox([x, 19.2, z], [x + 0.4, 23, z + 0.4], "iron"))
    return e


def model(elements, item=False):
    m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
         "textures": {**TEX, "particle": TEX["planks"]}, "elements": elements}
    if item:
        m["display"] = {"gui": {"rotation": [30, 200, 0], "translation": [-2.2, -2.8, 0], "scale": [0.3, 0.3, 0.3]},
                        "ground": {"scale": [0.2, 0.2, 0.2]}, "fixed": {"translation": [-4, -4, 0], "scale": [0.45, 0.45, 0.45]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.15, 0.15, 0.15]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.2, 0.2, 0.2]}}
    return m


def blockstate():
    variants = {}
    for half in ("lower", "upper"):
        for wing in ("false", "true"):
            for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                main = half == "lower" and wing == "false"
                v = {"model": "bsp_core:block/bounty_board" if main else "bsp_core:block/bounty_board_part"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},half={half},wing={wing}"] = v
    return {"variants": variants}


def recipe():
    return {"type": "minecraft:crafting_shaped", "category": "building",
            "pattern": ["SSS", "GCG", "LIL"],
            "key": {"S": {"item": "minecraft:dark_oak_slab"}, "G": {"item": "minecraft:glass_pane"}, "C": {"item": "bsp_core:basic_control_circuit"},
                    "L": {"item": "minecraft:dark_oak_log"}, "I": {"item": "minecraft:gold_ingot"}},
            "result": {"item": "bsp_core:bounty_board"}}


def axe_tag():
    path = DATA / "minecraft/tags/blocks/mineable/axe.json"
    tag = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
    if "bsp_core:bounty_board" not in tag["values"]:
        tag["values"].append("bsp_core:bounty_board")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(tag, indent=2) + "\n")


if __name__ == "__main__":
    whole = frame() + roof() + boards() + screens() + shelf() + lantern()
    assert all(-16 <= v <= 32 for el in whole for v in el["from"] + el["to"]), "an element runs outside -16..32"
    (ASSETS / "models/block/bounty_board.json").write_text(json.dumps(model(whole), indent=1))
    (ASSETS / "models/block/bounty_board_part.json").write_text(json.dumps(model([]), indent=1))
    (ASSETS / "models/item/bounty_board.json").write_text(json.dumps(model(whole, True), indent=1))
    (ASSETS / "blockstates/bounty_board.json").write_text(json.dumps(blockstate(), indent=2))
    (DATA / "bsp_core/recipes/bounty_board.json").write_text(json.dumps(recipe(), indent=2) + "\n")
    axe_tag()
    print(f"bounty board assets written ({len(whole)} elements)")
