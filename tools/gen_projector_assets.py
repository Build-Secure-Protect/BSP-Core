#!/usr/bin/env python3
"""Generates the Totem Generator, Totem Projector and Totem Cable assets and recipes for BSP-Core.

  python3 tools/gen_projector_assets.py

Totem Generator ("Siphon Plinth", joined as "One Slab"): a multipart block state. The body is always
drawn; on each side either a filler that runs the slab into the neighbouring generator, or three
cable ports on an outer edge. Totem Projector ("Totem Obelisk"): the dark body; its lights are
drawn by TotemProjectorRenderer. Totem Cables ("Linked Segments"): a core and one arm per joined
side, in four colours. Also the generator's socket parts, loot tables, tags and recipes, all of
which use Carbon Dust. Never hand-edit the outputs.
"""
import json

from gen_factory_assets import loot
from gen_material_assets import ASSETS, ROOT, hexc, hsh, item_model, mbox, write_png

DATA = ROOT / "src/main/resources/data"
CLEAR = (0, 0, 0, 0)
CABLES = {"tetrium": "#6B4FA3", "magnatite": "#5C7FB8", "illyrium": "#19D3B0", "charged_illyrium": "#BFF6FF"}
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


# ----------------------------------------------------------------------------- generator
def gen_body():
    """Top and bottom plates the full width, a dark core set in one pixel, three bands round it."""
    e = [mbox([0, 0, 0], [16, 3, 16], "hull2"), mbox([0, 13, 0], [16, 16, 16], "hull2"), mbox([1, 3, 1], [15, 13, 15], "hull")]
    for i in range(3):
        e.append(mbox([0.6, 4.5 + i * 3, 0.6], [15.4, 5.7 + i * 3, 15.4], "mid"))
    return e


def gen_join():
    """North side joined to another generator: the core and bands carry on to the block edge."""
    e = [mbox([0, 3, 0], [16, 13, 1], "hull")]
    for i in range(3):
        e.append(mbox([0, 4.5 + i * 3, 0], [16, 5.7 + i * 3, 0.6], "mid"))
    return e


def gen_ports():
    """North side open: three cable ports, each a trim ring with a lit centre."""
    e = []
    for x in (2.2, 6.6, 11):
        e += [mbox([x, 6, 0.2], [x + 2.8, 10, 1], "trim"), mbox([x + 0.7, 6.8, 0], [x + 2.1, 9.2, 0.2], "turq")]
    return e


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
    """Left half: dark armour with a seam. Right half: the cable's colour, for the lit band."""
    def px(x, y):
        if x >= 8:
            return hexc(colour)
        k = 0.85 + hsh(x, y, 41) * 0.3
        base = hexc("#8A909C") if x in (0, 7) or y in (0, 15) else hexc("#1E222B")
        return tuple(min(255, int(v * k)) for v in base[:3]) + (255,)
    return px


def cbox(frm, to, lit=False):
    uv = [8, 0, 16, 16] if lit else [0, 0, 8, 16]
    el = {"from": frm, "to": to, "faces": {f: {"uv": uv, "texture": "#cable"} for f in ("north", "south", "east", "west", "up", "down")}}
    if lit:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def cable_core():
    return [cbox([5, 5, 5], [11, 11, 11]), cbox([4.6, 7.4, 4.6], [11.4, 8.6, 11.4], True)]


def cable_arm():
    """One link reaching north from the core to the block edge, with its lit band."""
    return [cbox([5.9, 5.9, 0], [10.1, 10.1, 5]), cbox([5.4, 5.4, 1.8], [10.6, 10.6, 2.8], True)]


def cable(kind, colour):
    name = f"{kind}_core_cable"
    tex = {"cable": f"bsp_core:block/{name}", "particle": f"bsp_core:block/{name}"}
    write_png(ASSETS / f"textures/block/{name}.png", 16, 16, cable_texture(colour))
    write(ASSETS / f"models/block/{name}_core.json", model(cable_core(), tex, False))
    write(ASSETS / f"models/block/{name}_arm.json", model(cable_arm(), tex, False))
    arms = [("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})]
    parts = [{"apply": {"model": f"bsp_core:block/{name}_core"}}]
    for side, rot in arms:
        parts.append({"when": {side: "true"}, "apply": dict({"model": f"bsp_core:block/{name}_arm"}, **rot)})
    write(ASSETS / f"blockstates/{name}.json", {"multipart": parts})
    east = [cbox([11, 5.9, 5.9], [16, 10.1, 10.1]), cbox([13.2, 5.4, 5.4], [14.2, 10.6, 10.6], True)]
    west = [cbox([0, 5.9, 5.9], [5, 10.1, 10.1]), cbox([1.8, 5.4, 5.4], [2.8, 10.6, 10.6], True)]
    write(ASSETS / f"models/item/{name}.json", model(cable_core() + east + west, tex))
    loot(name)
    return name


# ----------------------------------------------------------------------------- item sprites
def amplifier(marks):
    """A dish on a stem, with one to three turquoise marks for the level."""
    def px(x, y):
        if 7 <= x <= 8 and 8 <= y <= 13:
            return hexc("#8A909C")
        if y == 14 and 5 <= x <= 10:
            return hexc("#565C6B")
        if 3 <= y <= 7 and abs(x - 7.5) <= (y - 2) * 1.0 + 0.5:
            return hexc("#C9CED8") if abs(x - 7.5) > (y - 3) * 1.0 else hexc("#39414F")
        if y == 1 and x in [5, 7, 9][:marks]:
            return hexc("#19D3B0")
        return CLEAR
    return px


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
    full = {k: {"item": v if ":" in v else f"bsp_core:{v}"} for k, v in key.items()}
    path = DATA / f"bsp_core/recipes/{name}.json"
    path.write_text(json.dumps({"type": "minecraft:crafting_shaped", "pattern": rows, "key": full, "result": result}, indent=2))


def main():
    # generator
    write(ASSETS / "models/block/totem_generator.json", model(gen_body(), gui=False))
    write(ASSETS / "models/block/totem_generator_join.json", model(gen_join(), gui=False))
    write(ASSETS / "models/block/totem_generator_ports.json", model(gen_ports(), gui=False))
    parts = [{"apply": {"model": "bsp_core:block/totem_generator"}}]
    for side, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270})):
        parts.append({"when": {side: "true"}, "apply": dict({"model": "bsp_core:block/totem_generator_join"}, **rot)})
        parts.append({"when": {side: "false"}, "apply": dict({"model": "bsp_core:block/totem_generator_ports"}, **rot)})
    write(ASSETS / "blockstates/totem_generator.json", {"multipart": parts})
    icon = gen_body()
    for q in range(4):
        icon += turned(gen_ports(), q)
    write(ASSETS / "models/item/totem_generator.json", model(icon))
    # projector: base, collar and the dark obelisk; seams, cap and shards are drawn by the renderer
    body = [mbox([2, 0, 2], [14, 2, 14], "hull2"), mbox([4, 2, 4], [12, 4, 12], "trim"), mbox([6, 4, 6], [10, 20, 10], "hull")]
    write(ASSETS / "models/block/totem_projector.json", model(body, gui=False))
    write(ASSETS / "blockstates/totem_projector.json", {"variants": {"": {"model": "bsp_core:block/totem_projector"}}})
    lit = [mbox([5.8, 5, 7], [6.1, 19, 9], "turq"), mbox([9.9, 5, 7], [10.2, 19, 9], "turq"), mbox([7, 5, 5.8], [9, 19, 6.1], "turq"), mbox([7, 5, 9.9], [9, 19, 10.2], "turq"),
           mbox([6.6, 20, 6.6], [9.4, 21.6, 9.4], "turq")]
    icon = model(body + lit)
    icon["display"]["gui"] = {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]}
    write(ASSETS / "models/item/totem_projector.json", icon)
    loot("totem_generator")
    loot("totem_projector")
    names = [cable(kind, colour) for kind, colour in CABLES.items()]

    for name, px in {"reach_amplifier_mk1": amplifier(1), "reach_amplifier_mk2": amplifier(2), "reach_amplifier_mk3": amplifier(3), "channel_expander": expander}.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, px)
        item_model(name)

    for tag, blocks in (("mineable/pickaxe", ["totem_generator", "totem_projector"] + names), ("needs_iron_tool", ["totem_generator", "totem_projector"])):
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
    shaped("totem_generator", ["DXD", "WKW", "PHP"], {"D": D, "X": "resonance_crystal", "W": "copper_tetrium_coil", "K": "illyrium_processor", "P": "tetrium_plate", "H": "machine_chassis"})
    shaped("totem_projector", ["DXD", "CKC", "PHP"], {"D": D, "X": "resonance_crystal", "C": C, "K": "illyrium_processor", "P": "tetrium_plate", "H": "machine_chassis"})
    shaped("reach_amplifier_mk1", [" D ", "WMW", " D "], {"D": D, "W": "tetrium_coil", "M": "magnatite_ingot"})
    shaped("reach_amplifier_mk2", ["DCD", "CKC", "DCD"], {"D": D, "C": C, "K": "reach_amplifier_mk1"})
    shaped("reach_amplifier_mk3", ["ICI", "CKC", "IDI"], {"I": I, "C": C, "K": "reach_amplifier_mk2", "D": D})
    shaped("channel_expander", ["CBC", "BKB", "CDC"], {"C": C, "B": "basic_control_circuit", "K": "illyrium_processor", "D": D})
    print("projector assets written")


if __name__ == "__main__":
    main()
