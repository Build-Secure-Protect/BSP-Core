#!/usr/bin/env python3
"""Generates the Decoy Totem assets and recipes for BSP-Core (no third-party dependencies).

  python3 tools/gen_decoy_assets.py

Writes: the Decoy Totem's item model (the "Hollow Idol"; in the world the block is drawn by
DecoyTotemRenderer), the Decoy Power Base ("Coil Plinth") model, the Magnet Core and the ten
socket parts as item sprites, loot tables, tags and every crafting recipe. The recipes are meant
to be hard: everything leads back to Charged Magnatite. Never hand-edit the outputs.
"""
import json
import math

from gen_factory_assets import loot
from gen_material_assets import ASSETS, ROOT, hexc, hsh, item_model, mbox, write_png

DATA = ROOT / "src/main/resources/data"
CLEAR = (0, 0, 0, 0)
BLUE, BLUE2, DARK, MID, EDGE, COPPER, COPPER2, PANEL = "#7FB3FF", "#4F7FD0", "#232831", "#39414F", "#8A909C", "#C87A3C", "#8F4A22", "#C9CED8"


def magnet_core(x, y):
    """A charged cube in a cage: bright centre, darker rim, four corner clamps."""
    if (x in (2, 3, 12, 13)) and (y in (2, 3, 12, 13)):
        return hexc(EDGE)
    if 4 <= x <= 11 and 4 <= y <= 11:
        d = max(abs(x - 7.5), abs(y - 7.5))
        return hexc("#E8F4FF") if d < 1.6 else hexc(BLUE) if d < 3 else hexc(BLUE2)
    if (x in (3, 12) and 5 <= y <= 10) or (y in (3, 12) and 5 <= x <= 10):
        return hexc(MID)
    return CLEAR


def coil(marks):
    """Copper windings on a dark bar, with one to three blue marks for the level."""
    def px(x, y):
        if 7 <= y <= 8 and 1 <= x <= 14:
            return hexc(BLUE) if x in (1, 14) else hexc(MID)
        if 3 <= x <= 12 and 4 <= y <= 11:
            if (x - 3) % 3 == 2:
                return CLEAR
            return hexc(COPPER2) if y in (4, 11) else hexc(COPPER)
        if y == 13 and x in [6, 8, 10][:marks]:
            return hexc(BLUE)
        return CLEAR
    return px


def charge(colour, light):
    """A capped canister with a coloured window."""
    def px(x, y):
        if 5 <= x <= 10 and 2 <= y <= 13:
            if y in (2, 3, 12, 13):
                return hexc(EDGE) if x not in (5, 10) or y in (3, 12) else CLEAR
            if 6 <= x <= 9 and 5 <= y <= 10:
                return hexc(light) if (x + y) % 3 == 0 else hexc(colour)
            return hexc(DARK)
        if y == 1 and 7 <= x <= 8:
            return hexc(PANEL)
        return CLEAR
    return px


def amplifier(x, y):
    if 3 <= x <= 12 and 3 <= y <= 12:
        if x in (3, 12) or y in (3, 12):
            return hexc(EDGE)
        d = math.hypot(x - 7.5, y - 7.5)
        return hexc(BLUE) if 1.5 < d < 2.6 or d < 0.8 else hexc(COPPER) if 3.4 < d < 4.4 else hexc(DARK)
    return CLEAR


def casing(x, y):
    if 2 <= x <= 13 and 2 <= y <= 13:
        if x in (2, 13) or y in (2, 13):
            return hexc(EDGE)
        if (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
            return hexc(PANEL)
        if 6 <= x <= 9 and 6 <= y <= 9:
            return hexc(BLUE2)
        k = 0.9 + hsh(x, y, 13) * 0.2
        return tuple(min(255, int(v * k)) for v in hexc(MID)[:3]) + (255,)
    return CLEAR


def idol():
    e = [mbox([3, 0, 3], [13, 2, 13], "hull2")]
    for x, z in ((5, 5), (10.2, 5), (5, 10.2), (10.2, 10.2)):
        e.append(mbox([x, 2, z], [x + 0.8, 13, z + 0.8], "trim"))
    for y in (4.5, 8.5, 12.2):
        e += [mbox([5, y, 5], [11, y + 0.6, 5.6], "mid"), mbox([5, y, 10.4], [11, y + 0.6, 11], "mid"),
              mbox([5, y, 5.6], [5.6, y + 0.6, 10.4], "mid"), mbox([10.4, y, 5.6], [11, y + 0.6, 10.4], "mid")]
    e.append(mbox([6.4, 6.4, 6.4], [9.6, 9.6, 9.6], "turq"))
    return e


def plinth():
    """Coil Plinth: two plates, a dark core, four copper bands and a socket on top for the decoy."""
    e = [mbox([0, 0, 0], [16, 3, 16], "hull2"), mbox([0, 13, 0], [16, 16, 16], "hull2"), mbox([2, 3, 2], [14, 13, 14], "hull")]
    for i in range(4):
        e.append(mbox([1.4, 3.8 + i * 2.4, 1.4], [14.6, 5 + i * 2.4, 14.6], "orange"))
    e.append(mbox([5, 16, 5], [11, 16.4, 11], "orange"))
    return e


def model(elements, gui):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": elements, "display": {"gui": gui}}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2))


def shaped(name, rows, key, count=1):
    result = {"item": f"bsp_core:{name}"}
    if count > 1:
        result["count"] = count
    full = {k: {"item": v if ":" in v else f"bsp_core:{v}"} for k, v in key.items()}
    write(DATA / f"bsp_core/recipes/{name}.json", {"type": "minecraft:crafting_shaped", "pattern": rows, "key": full, "result": result})


def main():
    sprites = {"magnet_core": magnet_core, "range_coil_mk1": coil(1), "range_coil_mk2": coil(2), "range_coil_mk3": coil(3),
               "blast_charge": charge("#D63B2F", "#FF9A4A"), "hex_charge": charge("#8A4FD0", "#D9B8FF"), "poison_charge": charge("#4FA83A", "#B8F27A"),
               "fatigue_charge": charge("#5C6B85", "#C9CED8"), "warp_charge": charge("#C83AA8", "#FFB0F0"), "trap_amplifier": amplifier, "reinforced_casing": casing}
    for name, px in sprites.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, px)
        item_model(name)

    gui = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}
    write(ASSETS / "models/block/decoy_totem.json", model(idol(), gui))
    write(ASSETS / "models/item/decoy_totem.json", {"parent": "bsp_core:block/decoy_totem"})
    variants = {f"facing={f}": {"model": "bsp_core:block/decoy_totem"} for f in ("north", "east", "south", "west")}
    write(ASSETS / "blockstates/decoy_totem.json", {"variants": variants})  # only used for break particles: the renderer draws the block
    write(ASSETS / "models/block/decoy_power_base.json", model(plinth(), gui))
    write(ASSETS / "models/item/decoy_power_base.json", {"parent": "bsp_core:block/decoy_power_base"})
    write(ASSETS / "blockstates/decoy_power_base.json", {"variants": {"": {"model": "bsp_core:block/decoy_power_base"}}})
    loot("decoy_totem")
    loot("decoy_power_base")
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = DATA / f"minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        for name in ("decoy_totem", "decoy_power_base")[(1 if tag == "needs_iron_tool" else 0):]:
            if f"bsp_core:{name}" not in data["values"]:
                data["values"].append(f"bsp_core:{name}")
        path.write_text(json.dumps(data, indent=2) + "\n")

    C, I, N, D = "charged_magnatite_ingot", "magnatite_ingot", "magnatite_nugget", "carbon_dust"
    shaped("magnet_core", ["CDC", "CZC", "CDC"], {"C": C, "D": D, "Z": "illyrium_processor"})
    shaped("decoy_totem", ["GMG", "IKI", "PHP"], {"G": "minecraft:gold_ingot", "M": "magnet_core", "I": I, "K": "basic_control_circuit", "P": "tetrium_plate", "H": "machine_chassis"})
    shaped("decoy_power_base", ["PWP", "TRT", "PHP"], {"P": "tetrium_plate", "W": "copper_tetrium_coil", "T": "tetrium_coil", "R": "minecraft:redstone_block", "H": "machine_chassis"})
    shaped("range_coil_mk1", [" W ", "WIW", " D "], {"W": "tetrium_coil", "I": I, "D": D})
    shaped("range_coil_mk2", [" C ", "CKC", " D "], {"C": C, "K": "range_coil_mk1", "D": D})
    shaped("range_coil_mk3", ["LCL", "CKC", "LDL"], {"L": "illyrium_nugget", "C": C, "K": "range_coil_mk2", "D": D})
    for name, payload in (("blast_charge", "minecraft:tnt"), ("hex_charge", "minecraft:fermented_spider_eye"), ("poison_charge", "minecraft:spider_eye"),
                          ("fatigue_charge", "minecraft:prismarine_shard"), ("warp_charge", "minecraft:ender_pearl")):
        shaped(name, ["DND", "NXN", "DND"], {"D": D, "N": N, "X": payload})
    shaped("trap_amplifier", ["CRC", "RKR", "CDC"], {"C": C, "R": "minecraft:redstone", "K": "basic_control_circuit", "D": D})
    shaped("reinforced_casing", ["PIP", "ICI", "PIP"], {"P": "tetrium_plate", "I": I, "C": C})
    print("decoy assets written")


if __name__ == "__main__":
    main()
