#!/usr/bin/env python3
"""Writes machine tracking's models, textures, loot and recipes: the Machine Tagger (the owner's pick "Tag Tablet"), the Remote Control
Transmitter ("Radio Mast"; its dish and tip light are drawn by TransmitterRenderer) and the range card ("Circuit Card").

  python3 tools/gen_tracking_assets.py
"""
import json
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources"
ASSETS = ROOT / "assets/bsp_core"
DATA = ROOT / "data/bsp_core"


def png(path, rows, palette):
    """A 16 x 16 RGBA PNG from 16 strings of palette letters ('.' is clear)."""
    raw = b""
    for row in rows:
        raw += b"\x00"
        for ch in row:
            raw += bytes(palette[ch]) if ch != "." else b"\x00\x00\x00\x00"

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


# The Machine Tagger: the owner's pick "Tag Tablet" (P2). A 16 x 16 parts texture (4 x 4 patches) and a model of boxes: the tablet with
# orange rubber corners and a seam, its screen with the list, the tag printer with a paper tag coming out, the stylus on its clip, the
# strap on the back and the scanner lens.
PARTS = [
    "ggggooookkkkssss", "ggggooookkkkssss", "ggggooookkkkssss", "ggggooookkkkssss",
    "ppppGGGGbbbbLLLL", "ppppGGGGbbbbLLLL", "ppppGGGGbbbbLLLL", "ppppGGGGbbbbLLLL",
    "dddddddduuuuqqqq", "dTTTTTTduuuuqqqq", "dddddddduuuuqqqq", "dhhhhhhduuuuqqqq",
    "dddddddd........", "dhhhhhhd........", "dddddddd........", "dTTTTTTd........",
]
PARTS_PAL = {"g": (58, 68, 84, 255), "o": (224, 112, 32, 255), "k": (11, 13, 17, 255), "s": (201, 206, 216, 255), "p": (236, 230, 214, 255),
             "G": (61, 255, 110, 255), "b": (58, 42, 30, 255), "L": (127, 255, 230, 255), "d": (6, 20, 18, 255), "T": (25, 211, 176, 255),
             "h": (14, 122, 102, 255), "u": (74, 80, 92, 255), "q": (138, 144, 156, 255)}
png(ASSETS / "textures/item/machine_tagger.png", PARTS, PARTS_PAL)
UV = {"plastic": [0, 0, 4, 4], "orange": [4, 0, 8, 4], "black": [8, 0, 12, 4], "steel": [12, 0, 16, 4], "paper": [0, 4, 4, 8], "green": [4, 4, 8, 8],
      "strap": [8, 4, 12, 8], "lens": [12, 4, 16, 8], "screen": [0, 8, 8, 16], "steel_d": [12, 8, 16, 12]}


def box(frm, to, mat, front=None):
    faces = {f: {"uv": UV[mat], "texture": "#parts"} for f in ("north", "east", "south", "west", "up", "down")}
    if front:
        faces["south"] = {"uv": UV[front], "texture": "#parts"}
    return {"from": frm, "to": to, "faces": faces}


TABLET = [
    box([3, 0.5, 7.2], [13, 15.5, 8.8], "plastic"),
    box([3.6, 2, 8.8], [12.4, 14, 9.0], "plastic", "screen"),
    box([2.6, 7.4, 7.0], [13.4, 8.0, 9.0], "orange"),
    box([2.4, 0, 6.9], [4.4, 2, 9.1], "orange"), box([11.6, 0, 6.9], [13.6, 2, 9.1], "orange"),
    box([2.4, 14, 6.9], [4.4, 16, 9.1], "orange"), box([11.6, 14, 6.9], [13.6, 16, 9.1], "orange"),
    box([5, 15.5, 7.4], [11, 16.3, 8.6], "black"),
    box([6.4, 16.3, 7.95], [9.6, 18.6, 8.05], "paper"), box([6.4, 18.1, 7.94], [9.6, 18.6, 8.06], "green"),
    box([13.3, 1, 7.7], [13.9, 14.5, 8.3], "steel"), box([13.0, 11, 7.6], [14.0, 12.6, 8.4], "steel_d"),
    box([7, 2.5, 6.6], [9, 13, 7.2], "strap"), box([6.8, 12, 6.5], [9.2, 12.8, 7.2], "steel_d"), box([6.8, 3, 6.5], [9.2, 3.8, 7.2], "steel_d"),
    box([10, 12.2, 6.9], [11.6, 13.8, 7.2], "lens"),
    box([2.7, 10, 7.6], [3.0, 11, 8.4], "steel_d"), box([2.7, 8.6, 7.6], [3.0, 9.6, 8.4], "steel_d"),
]
tagger_model = {
    "textures": {"parts": "bsp_core:item/machine_tagger", "particle": "bsp_core:item/machine_tagger"},
    "elements": TABLET,
    "display": {
        "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2, 1], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [-10, 200, 8], "translation": [1.5, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [-10, 160, -8], "translation": [1.5, 3, 0], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [15, 200, 0], "translation": [0, -0.5, 0], "scale": [0.8, 0.8, 0.8]},
        "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.8, 0.8, 0.8]},
    },
}
(ASSETS / "models/item/machine_tagger.json").write_text(json.dumps(tagger_model, indent=2) + "\n")

recipe = {
    "type": "minecraft:crafting_shaped",
    "category": "equipment",
    "pattern": [" G ", "CRC", " E "],
    "key": {"G": {"item": "minecraft:glass_pane"}, "C": {"item": "minecraft:copper_ingot"}, "R": {"item": "minecraft:redstone"}, "E": {"item": "minecraft:ender_pearl"}},
    "result": {"item": "bsp_core:machine_tagger"},
}
(DATA / "recipes/machine_tagger.json").write_text(json.dumps(recipe, indent=2) + "\n")

# The Remote Control Transmitter: the owner's pick "Radio Mast" (P4). The baked model: a steel base with a turquoise seam, a dark front
# panel with three lit keys (CARDS, POWER, TEAM; the screen's own words, too small to letter at block size) and a copper FE port, three card
# slits on top with green cards standing in them, corner bolts, and the mast with two collars. The renderer adds the turning dish and the
# tip light.
MAST = [
    "hhhhTTTTddddkkkk", "hhhhTTTTddddkkkk", "hhhhTTTTddddkkkk", "hhhhTTTTddddkkkk",
    "GGGGccccsssslLlL", "GGGGccccsssslLlL", "GGGGccccssssLLLL", "GGGGccccssssllll",
    "mmmmqqqq........", "mmmmqqqq........", "mmmmqqqq........", "mmmmqqqq........",
    "................", "................", "................", "................",
]
MAST_PAL = {"h": (90, 98, 114, 255), "T": (14, 122, 102, 255), "d": (28, 34, 44, 255), "k": (11, 13, 17, 255), "G": (61, 255, 110, 255),
            "c": (210, 125, 70, 255), "s": (201, 206, 216, 255), "l": (25, 211, 176, 255), "L": (127, 255, 230, 255), "m": (138, 144, 156, 255),
            "q": (74, 80, 92, 255)}
png(ASSETS / "textures/block/remote_transmitter.png", MAST, MAST_PAL)
for old in ("remote_transmitter_front", "remote_transmitter_side", "remote_transmitter_top"):
    (ASSETS / f"textures/block/{old}.png").unlink(missing_ok=True)
MUV = {"hull": [0, 0, 4, 4], "seam": [4, 0, 8, 4], "panel": [8, 0, 12, 4], "black": [12, 0, 16, 4], "card": [0, 4, 4, 8], "copper": [4, 4, 8, 8],
       "steel": [8, 4, 12, 8], "key": [12, 4, 16, 8], "mast": [0, 8, 4, 12], "dark": [4, 8, 8, 12]}


def mbox(frm, to, mat, shade=True):
    faces = {f: {"uv": MUV[mat], "texture": "#parts"} for f in ("north", "east", "south", "west", "up", "down")}
    return {"from": frm, "to": to, "faces": faces, "shade": shade}


MAST_ELEMENTS = [
    mbox([0, 0, 0], [16, 6, 16], "hull"),
    mbox([-0.05, 4.4, -0.05], [16.05, 5.0, 16.05], "seam"),
    mbox([1, 0.8, 15.6], [15, 4.0, 16.1], "panel"),
    mbox([1.8, 1.6, 16.0], [4.8, 3.0, 16.5], "key", False), mbox([5.6, 1.6, 16.0], [8.6, 3.0, 16.5], "key", False),
    mbox([9.4, 1.6, 16.0], [12.4, 3.0, 16.5], "key", False),
    mbox([12.9, 1.4, 16.0], [14.6, 3.2, 16.4], "copper"),
    mbox([2, 6, 11.6], [5, 6.5, 13.0], "black"), mbox([6.5, 6, 11.6], [9.5, 6.5, 13.0], "black"), mbox([11, 6, 11.6], [14, 6.5, 13.0], "black"),
    mbox([2.5, 6.5, 12.1], [4.5, 8.6, 12.5], "card", False), mbox([7, 6.5, 12.1], [9, 8.2, 12.5], "card", False), mbox([11.5, 6.5, 12.1], [13.5, 7.6, 12.5], "card", False),
    mbox([0.6, 6, 0.6], [1.6, 6.4, 1.6], "dark"), mbox([14.4, 6, 0.6], [15.4, 6.4, 1.6], "dark"),
    mbox([0.6, 6, 14.4], [1.6, 6.4, 15.4], "dark"), mbox([14.4, 6, 14.4], [15.4, 6.4, 15.4], "dark"),
    mbox([6.8, 6, 6.8], [9.2, 7.0, 9.2], "dark"),
    mbox([7.2, 7, 7.2], [8.8, 16, 8.8], "mast"),
    mbox([5.8, 9.5, 5.8], [10.2, 10.0, 10.2], "steel"), mbox([6.3, 12.5, 6.3], [9.7, 13.0, 9.7], "steel"),
]
(ASSETS / "models/block/remote_transmitter.json").write_text(json.dumps({
    "parent": "minecraft:block/block", "textures": {"parts": "bsp_core:block/remote_transmitter", "particle": "bsp_core:block/remote_transmitter"},
    "elements": MAST_ELEMENTS}, indent=2) + "\n")
variants = {}
for facing, rot in (("north", 180), ("east", 270), ("south", 0), ("west", 90)):  # the model's front faces south
    v = {"model": "bsp_core:block/remote_transmitter"}
    if rot:
        v["y"] = rot
    variants[f"facing={facing}"] = v
(ASSETS / "blockstates/remote_transmitter.json").write_text(json.dumps({"variants": variants}, indent=2) + "\n")
(ASSETS / "models/item/remote_transmitter.json").write_text(json.dumps({"parent": "bsp_core:block/remote_transmitter"}, indent=2) + "\n")
loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "bsp_core:remote_transmitter", "functions": [
    {"function": "minecraft:copy_nbt", "source": "block_entity", "ops": [{"source": "Cards", "target": "BlockEntityTag.Cards", "op": "replace"},
                                                                       {"source": "Energy", "target": "BlockEntityTag.Energy", "op": "replace"},
                                                                       {"source": "InventoryLink", "target": "BlockEntityTag.InventoryLink", "op": "replace"}]}]}]}]}  # no survives_explosion: a blast never destroys the cards inside
(DATA / "loot_tables/blocks/remote_transmitter.json").write_text(json.dumps(loot, indent=2) + "\n")
pick = ROOT / "data/minecraft/tags/blocks/mineable/pickaxe.json"
tags = json.loads(pick.read_text())
if "bsp_core:remote_transmitter" not in tags["values"]:
    tags["values"].append("bsp_core:remote_transmitter")
    pick.write_text(json.dumps(tags, indent=2) + "\n")
transmitter = {
    "type": "minecraft:crafting_shaped",
    "category": "redstone",
    "pattern": ["ILI", "CRC", "IBI"],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "L": {"item": "minecraft:lightning_rod"}, "C": {"item": "bsp_core:range_card"}, "R": {"item": "minecraft:redstone_block"},
            "B": {"item": "minecraft:iron_block"}},
    "result": {"item": "bsp_core:remote_transmitter"},
}
(DATA / "recipes/remote_transmitter.json").write_text(json.dumps(transmitter, indent=2) + "\n")

CARD = ["................", "................", "................", ".ggggggggggggg..", ".gGGGGGGGGGGGg..", ".gGyyyyyyyGGGg..", ".gGGGGGGGyGkkg..",
        ".gGGGGGGGyGkkg..", ".gGGtttttyGGGg..", ".gGGGGGGGGGGGg..", ".gGyGyGyGyGGGg..", ".gyyyyyyyyyGGg..", ".ggggggggggggg..", "................", "................", "................"]
CARD_PAL = {"g": (31, 106, 58, 255), "G": (47, 138, 74, 255), "y": (255, 210, 58, 255), "k": (25, 211, 176, 255), "t": (25, 211, 176, 255)}
png(ASSETS / "textures/item/range_card.png", CARD, CARD_PAL)
(ASSETS / "models/item/range_card.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": "bsp_core:item/range_card"}}, indent=2) + "\n")
card = {"type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minecraft:copper_ingot"}, {"item": "minecraft:copper_ingot"}, {"item": "minecraft:redstone"}, {"item": "minecraft:redstone"},
                        {"item": "minecraft:quartz"}, {"item": "minecraft:ender_pearl"}],
        "result": {"item": "bsp_core:range_card", "count": 2}}
(DATA / "recipes/range_card.json").write_text(json.dumps(card, indent=2) + "\n")
print("wrote the Machine Tagger's, the Remote Control Transmitter's and the range card's models, textures, loot and recipes")
