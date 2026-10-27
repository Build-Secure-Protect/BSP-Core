#!/usr/bin/env python3
"""Generates the molten metals' assets: Molten Tetrium, Illyrium and Magnatite (MoltenMetals.java).

  python3 tools/gen_molten_assets.py

Writes the animated still and flowing textures in the "Bright Melt" look the owner picked
(tools/preview/totem_forge_concepts.html, section 1: smooth glowing bands flowing downward, brightest
where they fold), the bucket models (Forge's fluid_container loader draws the fluid into the vanilla
bucket), the fluid blocks' blockstates and models, the lang lines and the forge:molten_<metal> fluid
tags. Only fluid tags: the metals stay off forge:ingots/..., forge:ores/... on purpose.
"""
import json
import math

from gen_material_assets import ASSETS, ROOT, write_png

DATA = ROOT / "src/main/resources/data"
LANG = ASSETS / "lang/en_us.json"
FRAMES = 16

# dark, mid, highlight, white-hot; the ingots' own colours (median and highlight of each ingot texture)
METALS = {
    "tetrium": ("Tetrium", (0x3b, 0x2a, 0x63), (0x7f, 0x64, 0xb6), (0xc0, 0xab, 0xec), (0xf3, 0xec, 0xff)),
    "illyrium": ("Illyrium", (0x0f, 0x5e, 0x52), (0x39, 0xdf, 0xbf), (0xa3, 0xff, 0xeb), (0xea, 0xff, 0xfb)),
    "magnatite": ("Magnatite", (0x1c, 0x20, 0x29), (0x43, 0x4d, 0x5e), (0x64, 0x7b, 0xa1), (0x9e, 0xc4, 0xff)),
}


def hsh(x, y, s):
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967296


def vn(x, y, s):
    """Value noise on a lattice that repeats every 4 cells, so the textures tile."""
    xi, yi = math.floor(x), math.floor(y)
    fx, fy = x - xi, y - yi
    sm = lambda v: v * v * (3 - 2 * v)
    a, b = hsh(xi % 4, yi % 4, s), hsh((xi + 1) % 4, yi % 4, s)
    c, d = hsh(xi % 4, (yi + 1) % 4, s), hsh((xi + 1) % 4, (yi + 1) % 4, s)
    u, v = sm(fx), sm(fy)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def melt(pal, size):
    """A vertical strip of FRAMES frames. Over the loop the coarse bands drift down one whole pattern
    (size px) and the fine ones two, so the last frame runs on into the first."""
    _, d, m, h, w = pal
    cell = size / 4

    def px(x, y):
        f, yy = divmod(y, size)
        v = vn(x / cell, (yy - f * size / FRAMES) / cell, 1) * .7 + vn(x / (cell / 2), (yy - f * 2 * size / FRAMES) / (cell / 2), 2) * .3
        c = w if v > .78 else lerp(m, h, (v - .55) / .23) if v > .55 else lerp(d, m, v / .55)
        return (*c, 255)
    return px


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def main():
    lang = json.loads(LANG.read_text())
    for key, pal in METALS.items():
        mid = f"molten_{key}"
        # still: 16 px frames; flowing: 32 px frames (as vanilla's lava_flow), played faster
        write_png(ASSETS / f"textures/block/{mid}_still.png", 16, 16 * FRAMES, melt(pal, 16))
        write_png(ASSETS / f"textures/block/{mid}_flow.png", 32, 32 * FRAMES, melt(pal, 32))
        write(ASSETS / f"textures/block/{mid}_still.png.mcmeta", {"animation": {"frametime": 3}})
        write(ASSETS / f"textures/block/{mid}_flow.png.mcmeta", {"animation": {"frametime": 2}})
        write(ASSETS / f"blockstates/{mid}.json", {"variants": {"": {"model": f"bsp_core:block/{mid}"}}})
        write(ASSETS / f"models/block/{mid}.json", {"textures": {"particle": f"bsp_core:block/{mid}_still"}})
        write(ASSETS / f"models/item/{mid}_bucket.json", {"parent": "forge:item/bucket_drip", "loader": "forge:fluid_container", "fluid": f"bsp_core:{mid}"})
        write(DATA / f"forge/tags/fluids/{mid}.json", {"replace": False, "values": [f"bsp_core:{mid}", f"bsp_core:flowing_{mid}"]})
        name = "Molten " + pal[0]
        lang.update({f"fluid_type.bsp_core.{mid}": name, f"block.bsp_core.{mid}": name, f"item.bsp_core.{mid}_bucket": f"{name} Bucket"})
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print("molten metal assets written")


if __name__ == "__main__":
    main()
