#!/usr/bin/env python3
"""Generates the Trade Post's assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_trade_post_assets.py

The Trade Post is the "Exchange Desk" (owner's pick, 2026-10-27, from tools/preview/trade_post_block_concepts_2.html): two
blocks wide, two tall, one deep, in the Bank's steel. The lower block on the right as you face it is the controller and
carries the whole model (elements run from x 0 to 32 and y 0 to 32; the second column is the controller's clockwise
neighbour, so +x here); the other three parts have an empty model so nothing draws twice. Front faces north (z = 0).

Baked here: the striped kick plate, the carcass around the open drawer bay with its trim and tray light, the desk top, the
sloped key deck with its sunken well and four keys, the coin tray, the ribbed neck, the vented monitor housing with the
screen set 2 px deep, the hood and the corner bolts. Drawn by TradePostRenderer: the screen text, the key names, the steel
A-frame scales, the corner bumpers, the beacons, the sliding tray and the coins. Never hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, mbox

# the four keys, as the player sees them from the left: x from, x to (model px). TradePostBlock.KEYS uses the same numbers.
KEYS = [("market", 23.0, 28.6), ("sell", 16.3, 21.9), ("collect", 9.6, 15.2), ("trade", 2.9, 8.5)]
DECK_ORIGIN = [16, 9.2, 0]
DECK_ANGLE = -22.5   # rises toward the screen


def rot(el, origin, axis, angle):
    el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return el


def deck(frm, to, mat):
    return rot(mbox(frm, to, mat), DECK_ORIGIN, "x", DECK_ANGLE)


def desk():
    e = []
    # kick plate with yellow-and-black stripes (amber reads as the warning yellow)
    e.append(mbox([0, 0, 1], [32, 2, 15], "hull2"))
    for i in range(8):
        x = i * 4 + 0.5
        e.append(mbox([x, 0.35, 0.85], [x + 2, 1.65, 1], "amber"))
    # carcass around the open drawer bay (x 9 to 23, y 3.5 to 7.5, 8 px deep)
    e += [mbox([23, 2, 2], [32, 10, 14], "steel"), mbox([0, 2, 2], [9, 10, 14], "steel"),
          mbox([9, 2, 2], [23, 3.5, 14], "steel"), mbox([9, 7.5, 2], [23, 10, 14], "steel"), mbox([9, 3.5, 10], [23, 7.5, 14], "hull")]
    e += [mbox([23, 2, 0], [32, 9, 2], "steel"), mbox([0, 2, 0], [9, 9, 2], "steel"),
          mbox([9, 2, 0], [23, 3.5, 2], "steel"), mbox([9, 7.5, 0], [23, 9, 2], "steel")]
    # the bay's trim lip and the amber light along its ceiling
    e += [mbox([8.6, 3.1, -0.2], [23.4, 3.5, 0.4], "trim"), mbox([8.6, 7.5, -0.2], [23.4, 7.9, 0.4], "trim"),
          mbox([23, 3.5, -0.2], [23.4, 7.5, 0.4], "trim"), mbox([8.6, 3.5, -0.2], [9, 7.5, 0.4], "trim")]
    e.append(mbox([9.4, 7.1, 2.5], [22.6, 7.4, 9], "amber"))
    # the desk top behind the deck
    e.append(mbox([0, 10, 3.6], [32, 10.6, 14], "hull2"))
    # the sloped key deck, its sunken well and the four keys with an amber lamp under each
    e.append(deck([0, 8.2, 0], [32, 9.2, 5.4], "trim"))
    e += [deck([2, 9.2, 4.2], [30, 9.4, 4.6], "hull2"), deck([2, 9.2, 0.4], [30, 9.4, 0.8], "hull2"),
          deck([29.6, 9.2, 0.8], [30, 9.4, 4.2], "hull2"), deck([2, 9.2, 0.8], [2.4, 9.4, 4.2], "hull2")]
    e.append(deck([2.4, 9.0, 0.8], [29.6, 9.21, 4.2], "hull"))
    for _, x0, x1 in KEYS:
        e.append(deck([x0, 9.1, 1.2], [x1, 9.7, 3.8], "hull2"))
        e.append(deck([x0 + 0.3, 9.05, 1.05], [x1 - 0.3, 9.15, 3.95], "amber"))
    # the coin tray on the desk (the coins are drawn by the renderer)
    e += [mbox([24, 10.6, 5.5], [29, 11.2, 10], "hull2"), mbox([24.4, 11.2, 5.9], [28.6, 11.3, 9.6], "hull")]
    return e


def monitor():
    e = []
    # the ribbed neck
    e.append(mbox([12, 10.6, 11.4], [20, 16, 15], "hull2"))
    for i in range(4):
        y = 11.4 + i * 1.2
        e.append(mbox([11.6, y, 11.2], [20.4, y + 0.5, 14.6], "trim"))
    # the housing, its frame around the 2 px deep screen recess, and the dark recess back
    e += [mbox([2, 16, 12], [30, 31, 16], "steel"),
          mbox([2, 16, 10], [30, 18, 12], "steel"), mbox([2, 29, 10], [30, 31, 12], "steel"),
          mbox([2, 18, 10], [4, 29, 12], "steel"), mbox([28, 18, 10], [30, 29, 12], "steel")]
    e.append(mbox([4, 18, 11.9], [28, 29, 12], "screen"))
    # corner bolts on the frame
    for x, y in ((4, 17), (28, 17), (4, 30), (28, 30)):
        e.append(mbox([x - 0.35, y - 0.35, 9.75], [x + 0.35, y + 0.35, 10], "trim"))
    # the hood over the top, coming forward, with its side cheeks
    e.append(mbox([1.6, 30.6, 7.4], [30.4, 31.6, 16.2], "hull2"))
    e += [mbox([1.6, 26, 7.4], [2.4, 30.6, 9.8], "hull2"), mbox([29.6, 26, 7.4], [30.4, 30.6, 9.8], "hull2")]
    # vents on both sides of the housing
    for i in range(5):
        y = 19 + i * 2
        e += [mbox([1.9, y, 12.6], [2, y + 0.8, 15.2], "hull"), mbox([30, y, 12.6], [30.1, y + 0.8, 15.2], "hull")]
    return e


def model(elements, item=False):
    m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
         "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": elements}
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
                v = {"model": "bsp_core:block/trade_post" if main else "bsp_core:block/trade_post_part"}
                if y:
                    v["y"] = y
                variants[f"facing={facing},half={half},wing={wing}"] = v
    return {"variants": variants}


if __name__ == "__main__":
    whole = desk() + monitor()
    assert all(-16 <= v <= 32 for el in whole for v in el["from"] + el["to"]), "an element runs outside -16..32"
    (ASSETS / "models/block/trade_post.json").write_text(json.dumps(model(whole), indent=1))
    (ASSETS / "models/block/trade_post_part.json").write_text(json.dumps(model([]), indent=1))
    (ASSETS / "models/item/trade_post.json").write_text(json.dumps(model(whole, True), indent=1))
    (ASSETS / "blockstates/trade_post.json").write_text(json.dumps(blockstate(), indent=2))
    print(f"trade post assets written ({len(whole)} elements)")
