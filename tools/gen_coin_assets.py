#!/usr/bin/env python3
"""Generates every Shatter Coin asset for BSP-Core (no third-party dependencies).

  python3 tools/gen_coin_assets.py

Writes coin / blank / speed gear sprites and item models, the Shatter Coin Factory atlas, block
models (static body, moving press head, inventory model) and blockstate. Design: "Stamp Press"
concept with the shatter-crack emblem. Edit palettes or geometry here and rerun; never hand-edit
the generated files.
"""
import json
import math
import random
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bsp_core"


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def write_png(path, w, h, pixel):
    """pixel(x, y) -> RGBA tuple."""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(pixel(x, y))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


CLEAR = (0, 0, 0, 0)
SILVER = {"base": hexc("#C9CED8"), "light": hexc("#F4F6FA"), "dark": hexc("#8A909C")}
TIERS = {
    "copper": {"base": hexc("#C8733A"), "light": hexc("#E79A62"), "dark": hexc("#8A4A22")},
    "gold": {"base": hexc("#F2C12E"), "light": hexc("#FFE27A"), "dark": hexc("#B8860B")},
    "diamond": {"base": hexc("#6FE7F2"), "light": hexc("#C9FBFF"), "dark": hexc("#2BA9B8")},
    "netherite": {"base": hexc("#4B4247"), "light": hexc("#6E6468"), "dark": hexc("#2A2428")},
    "illyrium": {"base": hexc("#19D3B0"), "light": hexc("#8CFFE6"), "dark": hexc("#0B8F78")},
}
MK = {
    "mk1": {"base": hexc("#9AA1AE"), "light": hexc("#D5DAE3"), "dark": hexc("#5E6470")},
    "mk2": {"base": hexc("#F2C12E"), "light": hexc("#FFE27A"), "dark": hexc("#B8860B")},
    "mk3": {"base": hexc("#19D3B0"), "light": hexc("#8CFFE6"), "dark": hexc("#0B8F78")},
}
CRACK = {(8, 4), (8, 5), (7, 6), (7, 7), (8, 8), (9, 9), (8, 10), (8, 11), (6, 7), (10, 9)}


def coin_pixel(tier, blank):
    def px(x, y):
        dx, dy = x - 7.5, y - 7.5
        d = math.hypot(dx, dy)
        if d > 7.7:
            return CLEAR
        lit = (-dx - dy) / 10
        if d > 5.4:
            return SILVER["light"] if lit > 0.25 else SILVER["dark"] if lit < -0.25 else SILVER["base"]
        if blank:
            # unstruck: dull, flat disc of the tier metal, no emblem
            dull = mix(tier["base"], SILVER["dark"], 0.45)
            return mix(dull, SILVER["light"], 0.25) if lit > 0.35 else dull
        if (x, y) in CRACK:
            return tier["dark"]
        return tier["light"] if lit > 0.35 else tier["base"]
    return px


def gear_pixel(p):
    def px(x, y):
        dx, dy = x - 7.5, y - 7.5
        r, a = math.hypot(dx, dy), math.atan2(dy, dx)
        tooth = 5.0 < r <= 7.4 and math.cos(a * 8) > 0.25
        body = 1.7 <= r <= 5.0
        if not tooth and not body:
            return CLEAR
        if r < 2.6:
            return p["dark"]
        if (-dx - dy) / 10 > 0.3:
            return p["light"]
        return p["dark"] if tooth else p["base"]
    return px


SHARD = """
................
.........L......
........LLB.....
.......LLBB.....
.......LBBB.....
......LLBBD.....
......LBBBD.....
.....LLBBBD.....
.....LBBBDD.....
....LLBBBD......
....LBBBDD......
....LBBDD.......
.....BDD........
.....DD.........
................
................
"""


def shard_pixel():
    pal = {".": CLEAR, "L": TIERS["illyrium"]["light"], "B": TIERS["illyrium"]["base"], "D": TIERS["illyrium"]["dark"]}
    rows = SHARD.strip("\n").splitlines()
    return lambda x, y: pal[rows[y][x]]


def item_model(name):
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / f"models/item/{name}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"bsp_core:item/{name}"}}, indent=2))


# ----------------------------------------------------------------------------- factory atlas
ATLAS = 64
UV = 16 / ATLAS
MATERIALS = {            # name: (tile origin px, colour)
    "iron": ((0, 0), hexc("#3A3F4B")),
    "plate": ((16, 0), hexc("#565C6B")),
    "steel": ((32, 0), hexc("#8A909C")),
    "brass": ((48, 0), hexc("#B8860B")),
    "socket": ((0, 16), hexc("#2B3038")),
    "white": ((16, 16), hexc("#FFFFFF")),
}


def atlas_pixel():
    rnd = random.Random(42)
    grid = [[CLEAR] * ATLAS for _ in range(ATLAS)]
    for name, ((ox, oy), colour) in MATERIALS.items():
        for y in range(16):
            for x in range(16):
                k = 1.0 if name == "white" else 0.9 + rnd.random() * 0.2
                c = tuple(min(255, int(colour[i] * k)) for i in range(3)) + (255,)
                # panel seams: darker border on plate-like tiles
                if name in ("iron", "plate") and (x == 0 or y == 0):
                    c = tuple(int(v * 0.8) for v in c[:3]) + (255,)
                grid[oy + y][ox + x] = c
    return lambda x, y: grid[y][x]


def box(frm, to, material, name=None):
    (ox, oy), _ = MATERIALS[material]
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    el = {"from": list(frm), "to": list(to), "faces": {}}
    if name:
        el["name"] = name
    for face, (fw, fh) in dims.items():
        fw, fh = min(fw, 16), min(fh, 16)
        el["faces"][face] = {"uv": [ox * UV, oy * UV, (ox + fw) * UV, (oy + fh) * UV], "texture": "#atlas"}
    return el


# Front (the side with the upgrade sockets) faces north, matching the totem convention.
STATIC = [
    box([0, 0, 0], [16, 4, 16], "iron", "base"),
    box([1, 4, 1], [15, 5, 15], "plate", "bed"),
    box([5, 5, 5], [11, 7, 11], "steel", "die"),
    box([1, 5, 6], [3, 15, 10], "plate", "pillar_w"),
    box([13, 5, 6], [15, 15, 10], "plate", "pillar_e"),
    box([1, 13, 5], [15, 16, 11], "iron", "beam"),
    box([12, 5, 12], [14, 9, 14], "brass", "gauge"),
    box([12.5, 9, 12.5], [13.5, 11, 13.5], "steel", "gauge_pin"),
] + [box([2.5 + 3 * i, 1, -0.4], [4.5 + 3 * i, 3, 0.4], "socket", f"socket_{i}") for i in range(4)]

HEAD = [
    box([7, 11, 7], [9, 14, 9], "steel", "ram"),
    box([5, 9, 5], [11, 11, 11], "plate", "head"),
    box([6, 8.5, 6], [10, 9, 10], "steel", "face"),
]


def block_model(elements, with_display):
    m = {"credit": "BSP-Core Shatter Coin Factory (Stamp Press)", "render_type": "minecraft:cutout",
         "ambientocclusion": False,
         "textures": {"atlas": "bsp_core:block/shatter_coin_factory", "particle": "bsp_core:block/shatter_coin_factory"},
         "elements": elements}
    if with_display:
        m["parent"] = "minecraft:block/block"
    return m


def main():
    for tier, pal in TIERS.items():
        write_png(ASSETS / f"textures/item/{tier}_shatter_coin.png", 16, 16, coin_pixel(pal, False))
        write_png(ASSETS / f"textures/item/{tier}_coin_blank.png", 16, 16, coin_pixel(pal, True))
        item_model(f"{tier}_shatter_coin")
        item_model(f"{tier}_coin_blank")
    # Speed Gears and the single-block factory were removed; the multiblock factory's assets come from gen_factory_assets.py

    # the single placeholder coin is superseded by the five tiers
    for stale in ("textures/item/shatter_coin.png", "models/item/shatter_coin.json"):
        p = ASSETS / stale
        if p.exists():
            p.unlink()
    print("coin assets written")


if __name__ == "__main__":
    main()
