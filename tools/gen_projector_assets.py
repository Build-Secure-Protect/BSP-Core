#!/usr/bin/env python3
"""Generates the Projector and Plasma Cable assets and the Channel Expander sprite for BSP-Core.

  python3 tools/gen_projector_assets.py

Projector ("Totem Obelisk"): the dark body; its lights are drawn by TotemProjectorRenderer. Plasma
Cables ("Linked Segments"): a core and one arm per joined side, in four colours. The cable recipes
all use Carbon Dust. The rest of the plasma chain is in gen_plasma_assets.py. Never hand-edit the outputs.
"""
import json

from gen_factory_assets import loot
from gen_material_assets import ASSETS, ROOT, hexc, hsh, item_model, mbox, write_png

DATA = ROOT / "src/main/resources/data"
CLEAR = (0, 0, 0, 0)
CABLES = {"tetrium": "#6B4FA3", "magnatite": "#5C7FB8", "illyrium": "#19D3B0", "charged_illyrium": "#BFF6FF"}
# the sixteen dyes, in Minecraft's order, as the glass colour of a dyed cable; Tetrium cables stay plain
DYES = {'white': '#F9FFFE', 'orange': '#F9801D', 'magenta': '#C74EBD', 'light_blue': '#3AB3DA', 'yellow': '#FED83D', 'lime': '#80C71F', 'pink': '#F38BAA', 'gray': '#474F52', 'light_gray': '#9D9D97', 'cyan': '#169C9C', 'purple': '#8932B8', 'blue': '#3C44AA', 'brown': '#835432', 'green': '#5E7C16', 'red': '#B02E26', 'black': '#1D1D21'}
COLOURED_KINDS = ("magnatite", "illyrium", "charged_illyrium")
ATLAS = {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}
GUI = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1))


def model(elements, textures=None, gui=True):
    out = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False, "textures": textures or ATLAS, "elements": elements}
    if gui:
        out["display"] = {"gui": GUI}
    return out


def turned(elements, quarter):
    """The same elements turned about the block's vertical axis by quarter turns, for the item model."""
    out = []
    for el in elements:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        for _ in range(quarter):
            x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
        new = dict(el)
        new["from"], new["to"] = [x0, y0, z0], [x1, y1, z1]
        out.append(new)
    return out


# ----------------------------------------------------------------------------- cables
def cable_texture(colour):
    """Left half: dark armour rails. Right half: the cable's colour as a thin, see-through glass, so the plasma inside shows."""
    def px(x, y):
        if x >= 8:
            c = hexc(colour)
            k = 0.8 + hsh(x, y, 43) * 0.3
            return tuple(min(255, int(v * k)) for v in c[:3]) + (70,)
        k = 0.85 + hsh(x, y, 41) * 0.3
        base = hexc("#8A909C") if x in (0, 7) or y in (0, 15) else hexc("#1E222B")
        return tuple(min(255, int(v * k)) for v in base[:3]) + (255,)
    return px


def cbox(frm, to, glass=False):
    """A cable box: a solid armour rail, or a see-through glass panel in the cable's colour."""
    uv = [8, 0, 16, 16] if glass else [0, 0, 8, 16]
    el = {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#cable"} for f in ("north", "south", "east", "west", "up", "down")}}
    if glass:
        el["shade"] = False
    return el


def rails(x0, y0, z0, x1, y1, z1, t=0.8):
    """The twelve edges of a box as thin rails, so the glass between them reads as a pipe."""
    out = []
    for (ax, ay, az, bx, by, bz) in ((x0, y0, z0, x1, y0, z0), (x0, y1, z0, x1, y1, z0), (x0, y0, z1, x1, y0, z1), (x0, y1, z1, x1, y1, z1),
                                     (x0, y0, z0, x0, y1, z0), (x1, y0, z0, x1, y1, z0), (x0, y0, z1, x0, y1, z1), (x1, y0, z1, x1, y1, z1),
                                     (x0, y0, z0, x0, y0, z1), (x1, y0, z0, x1, y0, z1), (x0, y1, z0, x0, y1, z1), (x1, y1, z0, x1, y1, z1)):
        out.append(cbox([min(ax, bx) - (t / 2 if ax == bx else 0), min(ay, by) - (t / 2 if ay == by else 0), min(az, bz) - (t / 2 if az == bz else 0)],
                        [max(ax, bx) + (t / 2 if ax == bx else 0), max(ay, by) + (t / 2 if ay == by else 0), max(az, bz) + (t / 2 if az == bz else 0)]))
    return out


def cable_core():
    return rails(5, 5, 5, 11, 11, 11) + [cbox([5.2, 5.2, 5.2], [10.8, 10.8, 10.8], True)]


def cable_arm():
    """One link reaching north from the core to the block edge: four rails and the glass between them."""
    return [cbox([5.2, 5.2, 0], [6, 6, 5.2]), cbox([10, 5.2, 0], [10.8, 6, 5.2]), cbox([5.2, 10, 0], [6, 10.8, 5.2]), cbox([10, 10, 0], [10.8, 10.8, 5.2]),
            cbox([5.4, 5.4, 0], [10.6, 10.6, 5.2], True)]


def cable(kind, colour, dye=None):
    name = f"{dye}_{kind}_core_cable" if dye else f"{kind}_core_cable"
    tex = {"cable": f"bsp_core:block/{name}", "particle": f"bsp_core:block/{name}"}
    write_png(ASSETS / f"textures/block/{name}.png", 16, 16, cable_texture(colour))
    core, arm = model(cable_core(), tex, False), model(cable_arm(), tex, False)
    core["render_type"] = arm["render_type"] = "minecraft:translucent"
    write(ASSETS / f"models/block/{name}_core.json", core)
    write(ASSETS / f"models/block/{name}_arm.json", arm)
    arms = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]
    parts = [{"apply": {"model": f"bsp_core:block/{name}_core"}}]
    for side, rot in arms:
        parts.append({"when": {side: "true"}, "apply": dict({"model": f"bsp_core:block/{name}_arm"}, **rot)})
    write(ASSETS / f"blockstates/{name}.json", {"multipart": parts})
    item = model(cable_core() + [cbox([11, 5.4, 5.4], [16, 10.6, 10.6], True), cbox([0, 5.4, 5.4], [5, 10.6, 10.6], True)], tex)
    item["render_type"] = "minecraft:translucent"
    write(ASSETS / f"models/item/{name}.json", item)
    loot(name)
    return name


# ----------------------------------------------------------------------------- item sprites
def expander(x, y):
    if 2 <= x <= 13 and 4 <= y <= 11:
        if x in (2, 13) or y in (4, 11):
            return hexc("#8A909C")
        if x in (4, 7, 10) and 6 <= y <= 9:
            return hexc("#19D3B0")
        if x in (5, 8, 11) and 6 <= y <= 9:
            return hexc("#0C8F78")
        return hexc("#1E222B")
    if y in (2, 3, 12, 13) and x in (4, 7, 10):
        return hexc("#C87A3C")
    return CLEAR


def shaped(name, rows, key, count=1):
    result = {"item": f"bsp_core:{name}"}
    if count > 1:
        result["count"] = count
    full = {k: ({"tag": v[1:]} if v.startswith("#") else {"item": v if ":" in v else f"bsp_core:{v}"}) for k, v in key.items()}
    path = DATA / f"bsp_core/recipes/{name}.json"
    path.write_text(json.dumps({"type": "minecraft:crafting_shaped", "pattern": rows, "key": full, "result": result}, indent=2))


def main():
    # projector: base, collar and the dark obelisk; seams, cap and shards are drawn by the renderer
    body = [mbox([2, 0, 2], [14, 2, 14], "hull2"), mbox([4, 2, 4], [12, 4, 12], "trim"), mbox([6, 4, 6], [10, 20, 10], "hull")]
    write(ASSETS / "models/block/totem_projector.json", model(body, gui=False))
    write(ASSETS / "blockstates/totem_projector.json", {"variants": {"": {"model": "bsp_core:block/totem_projector"}}})
    lit = [mbox([5.8, 5, 7], [6.1, 19, 9], "turq"), mbox([9.9, 5, 7], [10.2, 19, 9], "turq"), mbox([7, 5, 5.8], [9, 19, 6.1], "turq"), mbox([7, 5, 9.9], [9, 19, 10.2], "turq"),
           mbox([6.6, 20, 6.6], [9.4, 21.6, 9.4], "turq")]
    icon = model(body + lit)
    icon["display"]["gui"] = {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]}
    write(ASSETS / "models/item/totem_projector.json", icon)
    loot("totem_projector")
    names = [cable(kind, colour) for kind, colour in CABLES.items()]
    coloured = [cable(kind, hexcol, dye) for kind in COLOURED_KINDS for dye, hexcol in DYES.items()]
    names += coloured
    for kind in COLOURED_KINDS:  # one item tag per kind: the plain cable and all sixteen colours, for the dye recipes
        path = DATA / f"bsp_core/tags/items/{kind}_cables.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"replace": False, "values": [f"bsp_core:{kind}_core_cable"] + [f"bsp_core:{dye}_{kind}_core_cable" for dye in DYES]}, indent=2) + "\n")
    write_png(ASSETS / "textures/item/channel_expander.png", 16, 16, expander)
    item_model("channel_expander")

    for tag, blocks in (("mineable/pickaxe", ["totem_projector"] + names), ("needs_iron_tool", ["totem_projector"])):
        path = DATA / f"minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        for b in blocks:
            if f"bsp_core:{b}" not in data["values"]:
                data["values"].append(f"bsp_core:{b}")
        path.write_text(json.dumps(data, indent=2) + "\n")

    D, C, I = "carbon_dust", "charged_magnatite_ingot", "illyrium_ingot"
    # cables: every one is insulated with Carbon Dust; the longer the reach, the dearer the core and the fewer per craft
    shaped("tetrium_core_cable", ["NNN", "DTD", "NNN"], {"N": "tetrium_nugget", "D": D, "T": "tetrium_ingot"}, 8)
    shaped("magnatite_core_cable", ["NNN", "DMD", "NNN"], {"N": "magnatite_nugget", "D": D, "M": "magnatite_ingot"}, 8)
    shaped("illyrium_core_cable", ["NDN", "DID", "NDN"], {"N": "magnatite_nugget", "D": D, "I": I}, 6)
    shaped("charged_illyrium_core_cable", ["CDC", "DID", "CDC"], {"C": C, "D": D, "I": I}, 4)
    for kind in COLOURED_KINDS:  # eight cables of the kind, any colour, round a dye
        for dye in DYES:
            shaped(f"{dye}_{kind}_core_cable", ["CCC", "CYC", "CCC"], {"C": f"#bsp_core:{kind}_cables", "Y": f"minecraft:{dye}_dye"}, 8)
    print("projector assets written")


if __name__ == "__main__":
    main()
