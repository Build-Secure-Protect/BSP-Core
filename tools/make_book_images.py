#!/usr/bin/env python3
"""Makes the guide book's pictures for BSP-Core (macOS: uses the built-in `sips`).

  python3 tools/make_book_images.py

Two sources, both written to assets/bsp_core/textures/gui/book/<name>.png as a 256 x 256 texture with the picture in the
top-left 200 x 200, which is the layout Patchouli's picture pages expect:
  - RENDERS: the 3D views captured from tools/preview/plasma_renders.html (docs/curseforge/images/render_<slug>.jpg,
    made by `python3 tools/capture_images.py renders`), letterboxed to 200 x 125 on the render's own background. These are
    drawn from the mod's models, so they match the game.
  - docs/book_screenshots/*.png: in-game screenshots, middle square, shrunk to 200 x 200. A screenshot wins over a render
    of the same name.
Run tools/gen_guide_book.py afterwards so the book uses the pictures.
"""
import subprocess
import tempfile
from pathlib import Path

from gen_material_assets import ASSETS, ROOT, read_png, write_png

SRC = ROOT / "docs/book_screenshots"
RENDERS = ROOT / "docs/curseforge/images"
OUT = ASSETS / "textures/gui/book"
SIZE, SHEET = 200, 256
BG = (0x12, 0x15, 0x1b, 255)
# book picture name -> render slug
RENDER_PICTURES = {"illyrium_crucible": "illyrium_crucible", "illyrium_refinery": "illyrium_refinery", "shatter_coin_factory": "coin_factory",
                   "magnetic_centrifuge": "magnetic_centrifuge", "plasma_network": "plasma_network", "plasma_interface_group": "plasma_interface_group",
                   "plasma_batteries": "plasma_batteries", "plasma_tank": "plasma_tank", "plasma_injector": "plasma_injector", "shatter_totem": "shatter_totem"}


def from_render(name, slug):
    """A render letterboxed to 200 x 125 in the picture square."""
    src = RENDERS / f"render_{slug}.jpg"
    if not src.exists():
        print("no render for", name, "(run capture_images.py renders", slug + ")")
        return False
    with tempfile.TemporaryDirectory() as tmp:
        small = Path(tmp) / "small.png"
        subprocess.run(["sips", "-s", "format", "png", "-z", "125", "200", str(src), "--out", str(small)], capture_output=True, check=True)
        w, h, rows = read_png(small.read_bytes())
    top = (SIZE - h) // 2
    write_png(OUT / f"{name}.png", SHEET, SHEET, lambda x, y: (rows[y - top][x][:3] + (255,)) if x < w and top <= y < top + h else (BG if x < SIZE and y < SIZE else (0, 0, 0, 0)))
    print("picture written from render:", name)
    return True


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    done = 0
    shots = {p.stem for p in SRC.glob("*.png")}
    for name, slug in RENDER_PICTURES.items():
        if name not in shots:
            done += from_render(name, slug)
    for shot in sorted(SRC.glob("*.png")):
        info = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(shot)], capture_output=True, text=True, check=True).stdout
        dims = [int(line.split(":")[1]) for line in info.splitlines() if "pixel" in line]
        side = min(dims)
        with tempfile.TemporaryDirectory() as tmp:
            square, small = Path(tmp) / "square.png", Path(tmp) / "small.png"
            # two steps: asked to crop and resize in one call, sips resizes first
            subprocess.run(["sips", "-c", str(side), str(side), str(shot), "--out", str(square)], capture_output=True, check=True)
            subprocess.run(["sips", "-z", str(SIZE), str(SIZE), str(square), "--out", str(small)], capture_output=True, check=True)
            w, h, rows = read_png(small.read_bytes())
        write_png(OUT / shot.name, SHEET, SHEET, lambda x, y: rows[y][x][:3] + (255,) if x < w and y < h else (0, 0, 0, 0))
        print("picture written:", (OUT / shot.name).relative_to(ROOT))
        done += 1
    if not done:
        print("no pictures made: no renders in", RENDERS.relative_to(ROOT), "and no screenshots in", SRC.relative_to(ROOT))


if __name__ == "__main__":
    main()
