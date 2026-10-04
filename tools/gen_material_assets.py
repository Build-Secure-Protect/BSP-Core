#!/usr/bin/env python3
"""Generates the Tetrium / Illyrium material assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_material_assets.py

Ingots, nuggets and dusts are recolours of the vanilla iron ingot, iron nugget and redstone dust
textures, read from the Minecraft client jar that ForgeGradle already downloaded, so their shape and
shading match vanilla exactly. Ores are a speck overlay drawn over the vanilla host block texture by
the model. Everything else is drawn here. Rerun after changing palettes; never hand-edit outputs.
"""
import glob
import json
import math
import os
import struct
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bsp_core"
CLEAR = (0, 0, 0, 0)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def hsh(x, y, s):
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967296


# ----------------------------------------------------------------------------- PNG io
def write_png(path, w, h, pixel):
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


def read_png(data):
    """Decodes an 8-bit PNG (colour types 0, 2, 3, 4, 6) into rows of RGBA tuples."""
    pos, idat, plte, trns = 8, b"", None, None
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag, body = data[pos + 4:pos + 8], data[pos + 8:pos + 8 + ln]
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif tag == b"PLTE":
            plte = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        pos += 12 + ln
    assert depth == 8, "only 8-bit PNGs supported"
    bpp = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * bpp
    rows, prev = [], bytearray(stride)
    for y in range(h):
        f = raw[y * (stride + 1)]
        line = bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + ((a + b) >> 1)) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        prev = line
        row = []
        for x in range(w):
            px = line[x * bpp:(x + 1) * bpp]
            if ctype == 6:
                row.append(tuple(px))
            elif ctype == 2:
                row.append((px[0], px[1], px[2], 255))
            elif ctype == 3:
                i = px[0]
                row.append((plte[i * 3], plte[i * 3 + 1], plte[i * 3 + 2], trns[i] if trns and i < len(trns) else 255))
            elif ctype == 4:
                row.append((px[0], px[0], px[0], px[1]))
            else:
                row.append((px[0], px[0], px[0], 255))
        rows.append(row)
    return w, h, rows


def vanilla(path):
    """Reads a texture out of the Minecraft 1.20.1 client jar in the Gradle cache."""
    jars = glob.glob(os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar"))
    if not jars:
        raise SystemExit("Minecraft client jar not found; run ./gradlew build once first")
    with zipfile.ZipFile(jars[0]) as z:
        return read_png(z.read("assets/minecraft/textures/" + path))


# ----------------------------------------------------------------------------- palettes
def pal(deep, dark, base, light, shine, accent=None):
    return {"ramp": [hexc(deep), hexc(dark), hexc(base), hexc(light), hexc(shine)], "accent": hexc(accent) if accent else None,
            "base": hexc(base), "light": hexc(light), "dark": hexc(dark)}


TET = pal("#241843", "#3B2A66", "#6B4FA3", "#B49CE6", "#E9DCFF")
ILL = pal("#064E44", "#0B8F78", "#19D3B0", "#8CFFE6", "#EFFFFB")
DIRTY = pal("#1B2C26", "#2E4A40", "#4F7A6A", "#7FA896", "#B7CFC4", accent="#8A5A3A")
SLAG = pal("#15121A", "#221D27", "#3A3340", "#5A5062", "#8A7F93", accent="#FF8A3A")
IRON = pal("#4A4F59", "#7F8794", "#C8CCD3", "#F2F4F7", "#FFFFFF")
DIAMOND = pal("#125D66", "#2BA9B8", "#6FE7F2", "#C9FBFF", "#FFFFFF")
NETH = pal("#17131A", "#2A2428", "#4B4247", "#6E6468", "#8F868A")


def recolour(template, p, rough_seed=0):
    """Maps a vanilla sprite's luminance onto a five-step palette ramp, keeping its exact shape."""
    w, h, rows = template
    lums = [0.299 * r + 0.587 * g + 0.114 * b for row in rows for (r, g, b, a) in row if a > 0]
    lo, hi = min(lums), max(lums)

    def px(x, y):
        r, g, b, a = rows[y][x]
        if a == 0:
            return CLEAR
        if rough_seed and p["accent"] and hsh(x, y, rough_seed) > 0.86:
            return p["accent"][:3] + (a,)
        t = (0.299 * r + 0.587 * g + 0.114 * b - lo) / max(1.0, hi - lo) * 4
        i = min(3, int(t))
        return mix(p["ramp"][i], p["ramp"][i + 1], t - i)[:3] + (a,)
    return px


def slag_pixel(x, y):
    d = math.hypot(x - 7.5, y - 8.2)
    if d > 4.6 + (hsh(x, y, 5) - 0.5) * 2.6:
        return CLEAR
    h = hsh(x, y, 11)
    return SLAG["accent"] if h > 0.92 else SLAG["light"] if h > 0.6 else SLAG["dark"] if h < 0.3 else SLAG["base"]


def filter_pixel(p):
    def px(x, y):
        if x < 2 or x > 13 or y < 2 or y > 13:
            return CLEAR
        if x <= 3 or x >= 12 or y <= 3 or y >= 12:
            return p["light"] if (x <= 2 or y <= 2) else p["dark"] if (x >= 13 or y >= 13) else p["base"]
        if x == y or x + y == 15:
            return hexc("#7C8594")
        return hexc("#E8ECF2") if (x + y) % 2 == 0 else hexc("#AEB6C2")
    return px


def upgrade_pixel(x, y):
    if x < 3 or x > 12 or y < 3 or y > 12:
        return hexc("#9AA1AE") if y in (2, 13) and x in (5, 7, 8, 10) else CLEAR
    if x in (3, 12) or y in (3, 12):
        return hexc("#9AA1AE")
    if 6 <= x <= 9 and 6 <= y <= 9:
        return ILL["light"] if (x + y) % 2 else ILL["base"]
    if x in (5, 10) and y in (5, 10):
        return hexc("#E0322B")
    return hexc("#2B3038")


def blank_pixel(x, y):
    """The base Shatter Blank: an unstruck Tetrium disc."""
    dx, dy = x - 7.5, y - 7.5
    d = math.hypot(dx, dy)
    if d > 7.7:
        return CLEAR
    lit = (-dx - dy) / 10
    if d > 5.4:
        return TET["light"] if lit > 0.25 else TET["dark"] if lit < -0.25 else TET["base"]
    dull = mix(TET["base"], TET["dark"], 0.35)
    return mix(dull, TET["light"], 0.3) if lit > 0.35 else dull


def ore_overlay(p, seed):
    spots = [(3, 3), (10, 2), (6, 8), (12, 9), (2, 12), (9, 13)]

    def px(x, y):
        for sx, sy in spots:
            d = math.hypot(x - sx - hsh(sx, sy, seed) * 2, y - sy - hsh(sy, sx, seed) * 2)
            if d < 1.7:
                h = hsh(x, y, seed)
                return p["light"] if h > 0.6 else p["dark"] if hsh(x, y, seed + 1) > 0.7 else p["base"]
            if d < 2.4 and hsh(x, y, seed + 3) > 0.55:
                return (0, 0, 0, 90)   # soft shadow so specks sit in the stone
        return CLEAR
    return px


def item_model(name):
    (ASSETS / "models/item").mkdir(parents=True, exist_ok=True)
    (ASSETS / f"models/item/{name}.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": f"bsp_core:item/{name}"}}, indent=2))


ORES = {  # block id: (vanilla host texture, overlay name)
    "tetrium_ore": ("minecraft:block/stone", "tetrium_ore_overlay"),
    "deepslate_tetrium_ore": ("minecraft:block/deepslate", "tetrium_ore_overlay"),
    "illyrium_ore": ("minecraft:block/stone", "illyrium_ore_overlay"),
    "deepslate_illyrium_ore": ("minecraft:block/deepslate", "illyrium_ore_overlay"),
    "end_stone_illyrium_ore": ("minecraft:block/end_stone", "illyrium_ore_overlay"),
}


# ----------------------------------------------------------------------------- machines (single block)
ATLAS, UVU = 64, 16 / 64
MATS = {  # name: (tile origin px, colour, noisy)
    "hull": ((0, 0), "#1E222B", True), "hull2": ((16, 0), "#2B313D", True), "panel": ((32, 0), "#C9CED8", True), "mid": ((48, 0), "#565C6B", True),
    "trim": ((0, 16), "#8A909C", True), "violet": ((16, 16), "#B58CFF", False), "turq": ((32, 16), "#19D3B0", False), "orange": ((48, 16), "#FF9A4A", False),
}
GLOW = {"violet", "turq", "orange"}


def machine_atlas(x, y):
    for name, ((ox, oy), colour, noisy) in MATS.items():
        if ox <= x < ox + 16 and oy <= y < oy + 16:
            c = hexc(colour)
            k = 0.9 + hsh(x, y, len(name)) * 0.2 if noisy else 1.0
            if noisy and name in ("hull2", "panel") and ((x - ox) == 0 or (y - oy) == 0):
                k *= 0.82   # panel seam
            return tuple(min(255, int(v * k)) for v in c[:3]) + (255,)
    return CLEAR


def mbox(frm, to, mat):
    (ox, oy), _, _ = MATS[mat]
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    dims = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
    el = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": {}}
    for face, (fw, fh) in dims.items():
        fw, fh = min(fw, 16), min(fh, 16)
        el["faces"][face] = {"uv": [ox * UVU, oy * UVU, (ox + fw) * UVU, (oy + fh) * UVU], "texture": "#atlas"}
    if mat in GLOW:
        el["shade"] = False
        el["forge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def m_walls(x0, y0, z0, x1, y1, z1, t, mat):
    return [mbox([x0, y0, z0], [x1, y1, z0 + t], mat), mbox([x0, y0, z1 - t], [x1, y1, z1], mat),
            mbox([x0, y0, z0 + t], [x0 + t, y1, z1 - t], mat), mbox([x1 - t, y0, z0 + t], [x1, y1, z1 - t], mat)]


def m_plates(x0, y0, z0, x1, y1, z1, inset, mat):
    e = 0.45
    return [mbox([x0 + inset, y0 + inset, z0 - e], [x1 - inset, y1 - inset, z0], mat), mbox([x0 + inset, y0 + inset, z1], [x1 - inset, y1 - inset, z1 + e], mat),
            mbox([x0 - e, y0 + inset, z0 + inset], [x0, y1 - inset, z1 - inset], mat), mbox([x1, y0 + inset, z0 + inset], [x1 + e, y1 - inset, z1 - inset], mat)]


def m_corners(x0, y0, z0, x1, y1, z1, mat):
    e, w = 0.35, 0.9
    return [mbox([x, y0, z], [x + w, y1, z + w], mat) for x, z in ((x0 - e, z0 - e), (x1 - w + e, z0 - e), (x0 - e, z1 - w + e), (x1 - w + e, z1 - w + e))]


def tetrium_crucible_elements():
    e = [mbox([1, 0, 1], [15, 2, 15], "hull"), mbox([0, 2, 0], [16, 8, 16], "hull2")]
    e += m_plates(0, 2, 0, 16, 8, 16, 1.6, "panel") + m_corners(0, 2, 0, 16, 8, 16, "violet")
    e += [mbox([4.4, 2.6, -0.9], [11.6, 7.4, -0.45], "hull"), mbox([5, 3.2, -1.1], [11, 6.8, -0.8], "orange"),
          mbox([2, 8, 2], [14, 9.5, 14], "trim"), mbox([3.5, 9.5, 3.5], [12.5, 10.5, 12.5], "mid")]
    e += m_walls(3.5, 10.5, 3.5, 12.5, 17, 12.5, 1.3, "panel") + m_walls(2.8, 17, 2.8, 13.2, 18, 13.2, 1.6, "trim")
    for y in (10.9, 12.6, 14.3, 16):
        e += m_walls(2.3, y, 2.3, 13.7, y + 0.8, 13.7, 0.7, "orange")
    e += [mbox([4.9, 10.6, 4.9], [11.1, 13, 11.1], "violet"),
          mbox([-3, 3, 5], [0, 4, 11], "mid"), mbox([-3, 4, 5], [-2.4, 5.4, 11], "trim"), mbox([16, 3, 5], [19, 4, 11], "mid"), mbox([18.4, 4, 5], [19, 5.4, 11], "trim"),
          mbox([12.6, 9.5, 12.6], [14, 23, 14], "trim"), mbox([12.1, 19, 12.1], [14.5, 20, 14.5], "mid"), mbox([12.8, 23, 12.8], [13.8, 24.4, 13.8], "violet")]
    return e


def combination_forge_elements():
    e = [mbox([1, 0, 1], [15, 1.5, 15], "hull"), mbox([0, 1.5, 0], [16, 6, 16], "hull2")]
    e += m_plates(0, 1.5, 0, 16, 6, 16, 1.2, "panel") + m_corners(0, 1.5, 0, 16, 6, 16, "violet")
    e += [mbox([1, 6, 1], [15, 7, 15], "panel"), mbox([4.6, 7, 3.2], [11.4, 8.2, 9.8], "mid"), mbox([5.2, 8.2, 3.8], [10.8, 8.5, 9.2], "hull"),
          mbox([1, 7, 10.2], [3.6, 24, 13.8], "hull"), mbox([12.4, 7, 10.2], [15, 24, 13.8], "hull"),
          mbox([3.6, 8, 11.4], [3.95, 23, 12.6], "violet"), mbox([12.05, 8, 11.4], [12.4, 23, 12.6], "violet"),
          mbox([1, 22, 10.2], [15, 25, 13.8], "hull2"), mbox([5, 25, 10.8], [11, 26, 13.2], "trim"), mbox([7, 26, 11.4], [9, 27.6, 12.6], "violet"),
          mbox([4.4, 14.5, 3], [11.6, 17.5, 10], "panel"), mbox([5, 14, 3.6], [11, 14.5, 9.4], "hull"), mbox([3.6, 15.5, 10], [12.4, 16.9, 12.4], "mid"),
          mbox([2.6, 2.2, -0.7], [6.6, 5, -0.4], "hull"), mbox([9.4, 2.2, -0.7], [13.4, 5, -0.4], "hull")]
    return e


def write_machine(name, elements):
    models = ASSETS / "models/block"
    (models / f"{name}.json").write_text(json.dumps({
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
        "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": elements,
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.5, 0.5, 0.5]}}}, indent=1))
    (ASSETS / f"models/item/{name}.json").write_text(json.dumps({"parent": f"bsp_core:block/{name}"}, indent=2))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"bsp_core:block/{name}"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing}"] = v
    (ASSETS / f"blockstates/{name}.json").write_text(json.dumps({"variants": variants}, indent=2))


def main():
    ingot, nugget, dust = vanilla("item/iron_ingot.png"), vanilla("item/iron_nugget.png"), vanilla("item/redstone.png")
    sprites = {
        "tetrium_ingot": recolour(ingot, TET), "tetrium_nugget": recolour(nugget, TET), "tetrium_dust": recolour(dust, TET),
        "illyrium_ingot": recolour(ingot, ILL), "illyrium_nugget": recolour(nugget, ILL), "pure_illyrium_dust": recolour(dust, ILL),
        "dirty_illyrium_ingot": recolour(ingot, DIRTY, 3), "dirty_illyrium_nugget": recolour(nugget, DIRTY, 9), "dirty_illyrium_dust": recolour(dust, DIRTY, 5),
        "crushed_tetrium": recolour(vanilla("item/raw_iron.png"), TET), "crushed_dirty_illyrium": recolour(vanilla("item/raw_iron.png"), DIRTY, 7),
        "tetrium_slag": slag_pixel,
        "iron_filter": filter_pixel(IRON), "diamond_filter": filter_pixel(DIAMOND), "netherite_filter": filter_pixel(NETH), "illyrium_filter": filter_pixel(ILL),
        "illyrium_forge_upgrade": upgrade_pixel, "shatter_blank": blank_pixel,
    }
    for name, px in sprites.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, px)
        item_model(name)

    write_png(ASSETS / "textures/block/tetrium_ore_overlay.png", 16, 16, ore_overlay(TET, 1))
    write_png(ASSETS / "textures/block/illyrium_ore_overlay.png", 16, 16, ore_overlay(ILL, 3))
    models = ASSETS / "models/block"
    models.mkdir(parents=True, exist_ok=True)
    faces = {f: {"uv": [0, 0, 16, 16], "texture": "#TEX", "cullface": f} for f in ("north", "south", "east", "west", "up", "down")}

    def cube(tex):
        return {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {k: dict(v, texture=tex) for k, v in faces.items()}}
    # Same trick as the vanilla grass block: a second coplanar cube carries the overlay.
    (models / "ore_overlay.json").write_text(json.dumps({
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout_mipped",
        "textures": {"particle": "#base"}, "elements": [cube("#base"), cube("#overlay")]}, indent=2))
    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    for block, (host, overlay) in ORES.items():
        (models / f"{block}.json").write_text(json.dumps({
            "parent": "bsp_core:block/ore_overlay", "textures": {"base": host, "overlay": f"bsp_core:block/{overlay}"}}, indent=2))
        (ASSETS / f"models/item/{block}.json").write_text(json.dumps({"parent": f"bsp_core:block/{block}"}, indent=2))
        (ASSETS / f"blockstates/{block}.json").write_text(json.dumps({"variants": {"": {"model": f"bsp_core:block/{block}"}}}, indent=2))
    write_png(ASSETS / "textures/block/machine_atlas.png", ATLAS, ATLAS, machine_atlas)
    write_machine("tetrium_crucible", tetrium_crucible_elements())
    write_machine("combination_forge", combination_forge_elements())
    # multiblock parts: casing and glass tiles, controller faces
    def casing(x, y):
        edge = x in (0, 15) or y in (0, 15)
        k = 0.9 + hsh(x, y, 21) * 0.2
        base = hexc("#8A909C") if edge else hexc("#2B313D")
        if not edge and (x in (3, 12) or y in (3, 12)) and 3 <= x <= 12 and 3 <= y <= 12:
            return hexc("#19D3B0")
        return tuple(min(255, int(v * k)) for v in base[:3]) + (255,)

    def glass(x, y):
        if x in (0, 15) or y in (0, 15):
            return hexc("#8A909C")
        return (191, 230, 255, 70) if (x + y) % 7 else (255, 255, 255, 150)

    def controller(accent):
        def px(x, y):
            c = casing(x, y)
            if 4 <= x <= 11 and 5 <= y <= 10:
                return hexc("#0C1116") if (x in (4, 11) or y in (5, 10)) else (hexc(accent) if y == 7 and 5 <= x <= 9 else hexc("#11202A"))
            if y == 12 and x in (5, 7, 9):
                return hexc(accent)
            return c
        return px
    write_png(ASSETS / "textures/block/illyrium_casing.png", 16, 16, casing)
    write_png(ASSETS / "textures/block/illyrium_glass.png", 16, 16, glass)
    write_png(ASSETS / "textures/block/illyrium_crucible_front.png", 16, 16, controller("#FF7A1A"))
    write_png(ASSETS / "textures/block/illyrium_refinery_front.png", 16, 16, controller("#3A8BFF"))
    def port(kind):
        def px(x, y):
            c = casing(x, y)
            if kind == "lava_pylon" and 6 <= x <= 9 and 1 <= y <= 14:
                return hexc("#FF7A1A") if 7 <= x <= 8 else hexc("#3A1E12")
            if kind == "refinery_pump" and 5 <= x <= 10 and 4 <= y <= 11:
                return hexc("#3A8BFF") if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 6 else hexc("#11202A")
            if kind == "item_hatch" and 4 <= x <= 11 and 4 <= y <= 11:
                return hexc("#0C1116") if 5 <= x <= 10 and 5 <= y <= 10 else hexc("#C9CED8")
            return c
        return px
    for kind in ("lava_pylon", "refinery_pump", "item_hatch"):
        write_png(ASSETS / f"textures/block/{kind}.png", 16, 16, port(kind))

    def core(x, y):
        d = math.hypot(x - 7.5, y - 7.5)
        if x in (0, 15) or y in (0, 15):
            return hexc("#8A909C")
        if d < 2.6:
            return hexc("#EFFFFB")
        if d < 4.6:
            return ILL["light"] if (x + y) % 2 else ILL["base"]
        if d < 6.2:
            return ILL["dark"]
        return hexc("#1E222B")
    write_png(ASSETS / "textures/block/illyrium_core.png", 16, 16, core)

    def rf_pixel(mark):
        copper = {"l": hexc("#E79A62"), "b": hexc("#C8733A"), "d": hexc("#8A4A22")}
        bolt = {(8, 5), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (7, 10)}

        def px(x, y):
            if mark == 1:   # Flux Coil
                if x < 5 or x > 10 or y < 2 or y > 13:
                    return CLEAR
                if y <= 3 or y >= 12:
                    return TET["dark"] if x in (5, 10) else TET["base"]
                return (copper["l"] if x < 7 else copper["b"]) if y % 2 else copper["d"]
            if mark == 2:   # Power Cell
                if y == 2 and 6 <= x <= 9:
                    return hexc("#C9CED8")
                if x < 4 or x > 11 or y < 3 or y > 13:
                    return CLEAR
                if x in (4, 11) or y in (3, 13):
                    return TET["base"]
                return hexc("#FFD23A") if (x, y) in bolt else hexc("#1B2027")
            dx, dy = x - 7.5, y - 7.5   # Induction Core
            d = math.hypot(dx, dy)
            if d > 6.6:
                return CLEAR
            if d > 4.6:
                return copper["l"] if (-dx - dy) / 10 > 0.2 else copper["b"]
            if d < 2.4:
                return ILL["light"] if (x + y) % 2 else ILL["base"]
            return hexc("#E0322B") if abs(dx) < 0.8 or abs(dy) < 0.8 else hexc("#1B2027")
        return px
    def compass_pixel(frame):
        # frame 0 = needle pointing up (target straight ahead); frames advance clockwise
        ang = frame / 32 * 2 * math.pi
        vx, vy = math.sin(ang), -math.cos(ang)

        def px(x, y):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if d > 7.2:
                return CLEAR
            if d > 5.6:
                return TET["light"] if (-dx - dy) / 10 > 0.25 else TET["dark"] if (dx + dy) / 10 > 0.25 else TET["base"]
            along, across = dx * vx + dy * vy, abs(dx * vy - dy * vx)
            if across < 0.75 and 0.4 < along < 4.8:
                return hexc("#FFFFFF") if along > 3.8 else ILL["light"]
            if across < 0.75 and -3.2 < along <= 0.4:
                return hexc("#C8241C")
            return hexc("#232A33") if (x + y) % 5 == 0 else hexc("#161B22")
        return px
    overrides = []
    for f in range(32):
        write_png(ASSETS / f"textures/item/totem_compass_{f:02d}.png", 16, 16, compass_pixel(f))
        (ASSETS / f"models/item/totem_compass_{f:02d}.json").write_text(json.dumps(
            {"parent": "minecraft:item/generated", "textures": {"layer0": f"bsp_core:item/totem_compass_{f:02d}"}}, indent=2))
        # the angle predicate is 0..1 with 0 = ahead; each frame covers 1/32 centred on its direction
        overrides.append({"predicate": {"angle": max(0.0, (f - 0.5) / 32)}, "model": f"bsp_core:item/totem_compass_{f:02d}"})
    overrides.append({"predicate": {"angle": 31.5 / 32}, "model": "bsp_core:item/totem_compass_00"})
    (ASSETS / "models/item/totem_compass.json").write_text(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "bsp_core:item/totem_compass_00"}, "overrides": overrides}, indent=1))

    for mark in (1, 2, 3):
        write_png(ASSETS / f"textures/item/rf_upgrade_mk{mark}.png", 16, 16, rf_pixel(mark))
        item_model(f"rf_upgrade_mk{mark}")

    for name in ("illyrium_casing", "illyrium_glass", "illyrium_core", "lava_pylon", "refinery_pump", "item_hatch"):
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": f"bsp_core:block/{name}"}}
        if name == "illyrium_glass":
            m["render_type"] = "minecraft:translucent"
        (models / f"{name}.json").write_text(json.dumps(m, indent=2))
        (ASSETS / f"models/item/{name}.json").write_text(json.dumps({"parent": f"bsp_core:block/{name}"}, indent=2))
        (ASSETS / f"blockstates/{name}.json").write_text(json.dumps({"variants": {"": {"model": f"bsp_core:block/{name}"}}}, indent=2))
    for name in ("illyrium_crucible", "illyrium_refinery"):
        (models / f"{name}.json").write_text(json.dumps({"parent": "minecraft:block/orientable", "textures": {
            "top": "bsp_core:block/illyrium_casing", "side": "bsp_core:block/illyrium_casing", "front": f"bsp_core:block/{name}_front"}}, indent=2))
        (ASSETS / f"models/item/{name}.json").write_text(json.dumps({"parent": f"bsp_core:block/{name}"}, indent=2))
        variants = {}
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"bsp_core:block/{name}"}
            if rot:
                v["y"] = rot
            variants[f"facing={facing}"] = v
        (ASSETS / f"blockstates/{name}.json").write_text(json.dumps({"variants": variants}, indent=2))
    print("material assets written:", len(sprites), "items,", len(ORES), "ores")


if __name__ == "__main__":
    main()
