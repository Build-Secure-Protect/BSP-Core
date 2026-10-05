#!/usr/bin/env python3
"""Generates the Magnetic Centrifuge block assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_centrifuge_assets.py

Writes the cube textures, models, block states, item models and loot tables of the Centrifuge
Casing, Centrifuge Rotor, Centrifuge Power Port and the controller. The formed machine is drawn at
run time by MagneticCentrifugeRenderer. Rerun after changing anything here; never hand-edit the outputs.
"""
import json
import math

from gen_factory_assets import cube_block, loot
from gen_material_assets import ASSETS, ROOT, hexc, hsh, write_png

EDGE, PLATE, DARK, BLUE, COPPER, RF, PANEL = "#8A909C", "#39414F", "#232831", "#7FB3FF", "#C87A3C", "#FFD23A", "#C9CED8"


def casing(x, y):
    """Magnatite-blue armour plate: bright edge, a slit window across the middle, a vent row above it."""
    if x in (0, 15) or y in (0, 15):
        return hexc(EDGE)
    if 7 <= y <= 8 and 3 <= x <= 12:
        return hexc(DARK)
    if y == 4 and x % 2 == 1 and 3 <= x <= 12:
        return hexc(DARK)
    k = 0.9 + hsh(x, y, 31) * 0.2
    return tuple(min(255, int(v * k)) for v in hexc(PLATE)[:3]) + (255,)


def rotor(x, y):
    """The drum seen end on: ribbed rim, bolted lid, blue hub."""
    d = math.hypot(x - 7.5, y - 7.5)
    if x in (0, 15) or y in (0, 15):
        return hexc(EDGE)
    if d < 1.6:
        return hexc(BLUE)
    if d < 4.6:
        return hexc(PANEL) if int(math.degrees(math.atan2(y - 7.5, x - 7.5)) // 60) % 2 else hexc("#A9AFBA")
    if d < 6.4:
        return hexc(EDGE) if int(math.degrees(math.atan2(y - 7.5, x - 7.5)) // 30) % 2 else hexc(DARK)
    return casing(x, y) if not (7 <= y <= 8) else hexc(PLATE)


def with_socket(colour):
    def px(x, y):
        if 4 <= x <= 11 and 4 <= y <= 11:
            if x in (4, 11) or y in (4, 11):
                return hexc(EDGE)
            return hexc(colour) if 6 <= x <= 9 and 6 <= y <= 9 else hexc("#0C1116")
        return casing(x, y) if not (7 <= y <= 8) else hexc(PLATE)
    return px


def front(x, y):
    if 3 <= x <= 12 and 4 <= y <= 10:
        if x in (3, 12) or y in (4, 10):
            return hexc(EDGE)
        if 5 <= x <= 8 and 6 <= y <= 8:
            return hexc(BLUE)
        return hexc(RF) if (x, y) == (10, 6) else hexc("#0C1116")
    if y == 13 and 2 <= x <= 13:
        return hexc(COPPER)
    return casing(x, y) if not (7 <= y <= 8) else hexc(PLATE)


def main():
    tex = ASSETS / "textures/block"
    write_png(tex / "centrifuge_casing.png", 16, 16, casing)
    write_png(tex / "centrifuge_rotor.png", 16, 16, rotor)
    write_png(tex / "centrifuge_power_port.png", 16, 16, with_socket(RF))
    write_png(tex / "magnetic_centrifuge_front.png", 16, 16, front)
    for name in ("centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port"):
        cube_block(name)
        loot(name)
    (ASSETS / "models/block/magnetic_centrifuge.json").write_text(json.dumps({"parent": "minecraft:block/orientable", "textures": {
        "top": "bsp_core:block/centrifuge_casing", "side": "bsp_core:block/centrifuge_casing", "front": "bsp_core:block/magnetic_centrifuge_front"}}, indent=2))
    (ASSETS / "models/item/magnetic_centrifuge.json").write_text(json.dumps({"parent": "bsp_core:block/magnetic_centrifuge"}, indent=2))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "bsp_core:block/magnetic_centrifuge"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing}"] = v
    (ASSETS / "blockstates/magnetic_centrifuge.json").write_text(json.dumps({"variants": variants}, indent=2))
    loot("magnetic_centrifuge")
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = ROOT / f"src/main/resources/data/minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        for name in ("magnetic_centrifuge", "centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port"):
            if f"bsp_core:{name}" not in data["values"]:
                data["values"].append(f"bsp_core:{name}")
        path.write_text(json.dumps(data, indent=2) + "\n")
    print("centrifuge assets written")


if __name__ == "__main__":
    main()
