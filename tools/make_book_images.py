#!/usr/bin/env python3
"""Turns in-game screenshots into guide book pictures for BSP-Core (macOS: uses the built-in `sips`).

  python3 tools/make_book_images.py

For every PNG in docs/book_screenshots/, takes the middle square, shrinks it to 200 x 200 and writes
it into the top-left of a 256 x 256 texture, which is the layout Patchouli's picture pages expect.
Run tools/gen_guide_book.py afterwards so the book uses the new pictures.
"""
import subprocess
import tempfile
from pathlib import Path

from gen_material_assets import ASSETS, ROOT, read_png, write_png

SRC = ROOT / "docs/book_screenshots"
OUT = ASSETS / "textures/gui/book"
SIZE, SHEET = 200, 256


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    done = 0
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
        print("no screenshots found in", SRC.relative_to(ROOT), "- see the README there")


if __name__ == "__main__":
    main()
