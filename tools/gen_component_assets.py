#!/usr/bin/env python3
"""Generates the sprites and item models of the machine component items (no third-party dependencies).

  python3 tools/gen_component_assets.py

Slag Brick and Resonance Crystal are recolours of vanilla shapes; the rest are drawn here.
Rerun after changing anything; never hand-edit the outputs.
"""
import math

from gen_material_assets import ASSETS, CLEAR, DIRTY, ILL, SLAG, TET, hexc, hsh, item_model, recolour, vanilla, write_png

HULL, HULL2, TRIM, MID, PANEL, DARK, COPPER = (hexc(c) for c in ("#1E222B", "#2B313D", "#8A909C", "#565C6B", "#C9CED8", "#0C1116", "#D9773B"))


def shade(c, x, y, seed, amount=0.16):
    k = 1 - amount / 2 + hsh(x, y, seed) * amount
    return tuple(min(255, int(v * k)) for v in c[:3]) + (255,)


def plate(x, y):
    if not (2 <= x <= 13 and 3 <= y <= 12) or (x, y) in ((2, 3), (13, 3), (2, 12), (13, 12)):
        return CLEAR
    if x in (2, 13) or y in (3, 12):
        return TET["dark"]
    if (x, y) in ((4, 5), (11, 5), (4, 10), (11, 10)):
        return TET["ramp"][4]
    return TET["light"] if y == 4 or x == 3 else shade(TET["base"], x, y, 4)


def chassis(x, y):
    if not (1 <= x <= 14 and 1 <= y <= 14):
        return CLEAR
    edge = x in (1, 2, 13, 14) or y in (1, 2, 13, 14)
    if edge:
        return TRIM if x in (1, 14) or y in (1, 14) else shade(HULL2, x, y, 3)
    if x == y or x + y == 15:
        return shade(MID, x, y, 6)
    return CLEAR


def coil(x, y):
    if 7 <= x <= 8 and y in (1, 2, 13, 14):
        return COPPER
    if 4 <= x <= 11 and 3 <= y <= 12:
        if x in (4, 11):
            return TET["dark"]
        return TET["light"] if (x + y) % 3 == 0 else TET["base"]
    return CLEAR


def motor(x, y):
    if 13 <= x <= 14 and 7 <= y <= 8:
        return PANEL
    if 3 <= x <= 12 and 12 <= y <= 13 and x in (3, 4, 11, 12):
        return HULL
    if 2 <= x <= 12 and 4 <= y <= 11:
        if x in (2, 12) or y in (4, 11):
            return HULL
        if 5 <= x <= 9:
            return TET["light"] if x % 2 else TET["base"]
        return shade(MID, x, y, 8)
    return CLEAR


def board(x, y, base):
    if not (2 <= x <= 13 and 3 <= y <= 12):
        return None
    if x in (2, 13) or y in (3, 12):
        return hexc("#15201C")
    return shade(hexc(base), x, y, 5, 0.1)


def substrate(x, y):
    b = board(x, y, "#2F4A40")
    if b is None:
        return CLEAR
    return hexc("#8A7F93") if (x, y) in ((4, 5), (11, 5), (4, 10), (11, 10)) else b


def circuit(accent, big=False):
    acc = hexc(accent)

    def px(x, y):
        b = board(x, y, "#2F4A40")
        if b is None:
            return CLEAR
        lo, hi = (5, 10) if big else (6, 9)
        if lo <= x <= hi and lo <= y <= hi:
            return DARK if (x in (lo, hi) or y in (lo, hi)) else acc
        if (y in (5, 10) and 3 <= x <= 12) or (x in (4, 11) and 4 <= y <= 11):
            return acc
        if big and (x, y) in ((3, 4), (12, 4), (3, 11), (12, 11)):
            return ILL["ramp"][4]
        return b
    return px


def lining(x, y):
    if not (2 <= x <= 13 and 2 <= y <= 13):
        return CLEAR
    row = (y - 2) // 3
    if (y - 2) % 3 == 2 or (x + row * 3) % 6 == 0:
        return hexc("#FF7A1A") if hsh(x, y, 11) > 0.45 else hexc("#8A3A12")
    return shade(SLAG["base"], x, y, 2)


def conveyor(x, y):
    if 1 <= x <= 14 and y in (5, 10):
        return TET["base"]
    if 1 <= x <= 14 and 6 <= y <= 9:
        if x in (1, 2, 13, 14):
            return PANEL if 7 <= y <= 8 else TRIM
        return hexc("#3A4050") if (x + (0 if y < 8 else 1)) % 4 == 0 else hexc("#14171D")
    return CLEAR


def die(x, y):
    d = math.hypot(x - 7.5, y - 7.5)
    if d > 6.2:
        return CLEAR
    if d > 4.8:
        return DIRTY["dark"] if d > 5.6 else DIRTY["base"]
    if abs(x - 7.5) + abs(y - 7.5) < 2.2:
        return hexc("#C9FBFF")
    return hexc("#6FE7F2") if (x + y) % 2 else hexc("#2BA9B8")


def main():
    sprites = {
        "slag_brick": recolour(vanilla("item/brick.png"), SLAG, 4), "tetrium_plate": plate, "machine_chassis": chassis, "tetrium_coil": coil,
        "drive_motor": motor, "circuit_substrate": substrate, "basic_control_circuit": circuit("#D63B2F"),
        "crucible_control_circuit": circuit("#FF7A1A"), "refinery_control_circuit": circuit("#3A8BFF"), "mint_control_circuit": circuit("#FFD23A"),
        "thermal_lining": lining, "conveyor_belt": conveyor, "press_die": die,
        "resonance_crystal": recolour(vanilla("item/amethyst_shard.png"), ILL), "illyrium_processor": circuit("#19D3B0", True),
    }
    for name, px in sprites.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, px)
        item_model(name)
    print("component assets written:", len(sprites))


if __name__ == "__main__":
    main()
