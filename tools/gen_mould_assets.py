#!/usr/bin/env python3
"""Generates the Totem Mould items (Totem Forge brief, task 5): the five mould pieces, the Totem Core and the
Mould Shell they are built from. Models, textures, recipes and lang lines.

  python3 tools/gen_mould_assets.py

Each piece is drawn the way the totem itself looks in the hotbar ("A. Ghost Totem", the owner's pick in
tools/preview/totem_forge_concepts.html, section 3): the Shatter Totem's own geometry (read from
models/block/shatter_totem_geometry.json, so the two never drift apart) with the piece's boxes in outlined
ceramic and the rest a pale translucent ghost. The Totem Core shows the orb in turquoise the same way.
Recipes are the accepted draft (section 4). The Allthemodium metals are named by their forge:ingots tags,
so the recipes load without that mod and simply cannot be made until it is there.
"""
import json

from gen_magnatite_assets import write
from gen_material_assets import ASSETS, ROOT, hsh, write_png

DATA = ROOT / "src/main/resources/data"
LANG = ASSETS / "lang/en_us.json"
GEOMETRY = ASSETS / "models/block/shatter_totem_geometry.json"

# item id: (name, totem model elements picked out, texture of the picked-out boxes)
ITEMS = {
    "base_mould": ("Base Mould", {"plinth"}, "mould_piece"),
    "foot_mould": ("Foot Mould", {"knee_l"}, "mould_piece"),
    "torso_mould": ("Torso Mould", {"torso"}, "mould_piece"),
    "arm_mould": ("Arm Mould", {"arm_l", "hand_l"}, "mould_piece"),
    "head_mould": ("Head Mould", {"head"}, "mould_piece"),
    "totem_core": ("Totem Core", {"orb"}, "totem_core_orb"),
}

CERAMIC, EDGE, CAVITY = (0xc9, 0xce, 0xd8), (0x3a, 0x3f, 0x4b), (0x4a, 0x4f, 0x5b)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def piece(x, y):
    """Pale fired ceramic with a dark outline, so the piece reads against the ghost."""
    if x in (0, 15) or y in (0, 15):
        return (*EDGE, 255)
    return (*shade(CERAMIC, .93 + hsh(x, y, 11) * .12), 255)


def ghost(x, y):
    """The rest of the totem: pale grey, mostly see-through, with a faint edge."""
    if x in (0, 15) or y in (0, 15):
        return (0xff, 0xff, 0xff, 110)
    return (0xd0, 0xd4, 0xdc, 70)


def orb(x, y):
    d = ((x - 6) ** 2 + (y - 6) ** 2) ** .5
    c = (0xea, 0xff, 0xfb) if d < 2.5 else (0x2e, 0xc4, 0xb6) if d < 9 else (0x1b, 0x8f, 0x84)
    return (*shade(c, .95 + hsh(x, y, 12) * .1), 255)


def shell(x, y):
    """The Mould Shell's sprite: a ceramic half-shell with a figure-shaped cavity."""
    if not (2 <= x <= 13 and 1 <= y <= 14) or (x in (2, 13) and y in (1, 14)):
        return (0, 0, 0, 0)
    if x == 2 or y == 1:
        return (0xee, 0xf0, 0xf4, 255)
    if x == 13 or y == 14:
        return (*shade(CERAMIC, .6), 255)
    figure = (6 <= x <= 9 and 3 <= y <= 5) or (5 <= x <= 10 and 6 <= y <= 9) or (4 <= x <= 11 and y == 7) or (5 <= x <= 10 and 10 <= y <= 12)
    if figure:
        return (*CAVITY, 255) if not (x == 5 or y == 3) else (*shade(CAVITY, .7), 255)
    return (*shade(CERAMIC, .95 + hsh(x, y, 13) * .1), 255)


def model(picked, tex):
    geo = json.loads(GEOMETRY.read_text())
    elements = []
    for e in geo["elements"]:
        t = "#piece" if e.get("name") in picked else "#ghost"
        elements.append({"name": e.get("name"), "from": e["from"], "to": e["to"],
                         "faces": {f: {"uv": [0, 0, 16, 16], "texture": t} for f in e["faces"]}})
    # the picked-out boxes first: translucent quads write depth, so a ghost drawn before them would hide them where they overlap
    elements.sort(key=lambda e: e["faces"][next(iter(e["faces"]))]["texture"] != "#piece")
    return {"credit": "BSP-Core Totem Mould (tools/gen_mould_assets.py)", "render_type": "minecraft:translucent", "ambientocclusion": False,
            "textures": {"piece": f"bsp_core:item/{tex}", "ghost": "bsp_core:item/mould_ghost", "particle": f"bsp_core:item/{tex}"},
            "display": geo["display"], "elements": elements}


def shaped(name, rows, key, count=1):
    def ing(v):
        return {"tag": v[1:]} if v.startswith("#") else {"item": v}
    res = {"item": f"bsp_core:{name}"}
    if count > 1:
        res["count"] = count
    write(DATA / f"bsp_core/recipes/{name}.json", {"type": "minecraft:crafting_shaped", "pattern": rows, "key": {k: ing(v) for k, v in key.items()}, "result": res})


TP, SH, CM, MC, CRC, IP, II, IC = ("bsp_core:tetrium_plate", "bsp_core:mould_shell", "bsp_core:charged_magnatite_ingot", "bsp_core:magnet_core",
                                   "bsp_core:charged_resonance_crystal", "bsp_core:illyrium_processor", "bsp_core:illyrium_ingot", "bsp_core:illyrium_core")
ATM, VIB, UNO = "#forge:ingots/allthemodium", "#forge:ingots/vibranium", "#forge:ingots/unobtainium"

LANG_LINES = {
    "item.bsp_core.mould_shell": "Mould Shell",
    "tooltip.bsp_core.mould.pours": "Pours left: %s of %s",
    "tooltip.bsp_core.mould.once": "One goes into the Totem Forge's mould",
    "tooltip.bsp_core.mould.twice": "Two go into the Totem Forge's mould, left and right",
    "tooltip.bsp_core.totem_core": "Becomes the orb of a forged totem. Every pour uses one.",
}


def main():
    write_png(ASSETS / "textures/item/mould_piece.png", 16, 16, piece)
    write_png(ASSETS / "textures/item/mould_ghost.png", 16, 16, ghost)
    write_png(ASSETS / "textures/item/totem_core_orb.png", 16, 16, orb)
    write_png(ASSETS / "textures/item/mould_shell.png", 16, 16, shell)
    lang = json.loads(LANG.read_text())
    for iid, (name, picked, tex) in ITEMS.items():
        write(ASSETS / f"models/item/{iid}.json", model(picked, tex))
        lang[f"item.bsp_core.{iid}"] = name
    write(ASSETS / "models/item/mould_shell.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bsp_core:item/mould_shell"}})
    shaped("mould_shell", ["TAT", "ACA", "TAT"], {"T": TP, "A": ATM, "C": CM}, 2)
    shaped("base_mould", ["SSS", "ONO", "TTT"], {"S": SH, "O": "minecraft:crying_obsidian", "N": "minecraft:netherite_ingot", "T": TP})
    shaped("foot_mould", ["SVS", "TMT"], {"S": SH, "V": VIB, "T": TP, "M": MC})
    shaped("torso_mould", ["SUS", "SHS", "TUT"], {"S": SH, "U": UNO, "H": "minecraft:heart_of_the_sea", "T": TP})
    shaped("arm_mould", ["SC", "SV", "TT"], {"S": SH, "C": CRC, "V": VIB, "T": TP})
    shaped("head_mould", ["SES", "PWP", "SES"], {"S": SH, "E": "minecraft:echo_shard", "P": IP, "W": "minecraft:wither_skeleton_skull"})
    shaped("totem_core", ["ICI", "NTO", "ICI"], {"I": II, "C": CRC, "N": "minecraft:nether_star", "T": "minecraft:totem_of_undying", "O": IC})
    lang.update(LANG_LINES)
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print("mould assets written")


if __name__ == "__main__":
    main()
