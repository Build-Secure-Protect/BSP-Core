#!/usr/bin/env python3
"""Captures the CurseForge pictures with headless Chrome, so no hand screenshots are needed:

  python3 tools/capture_images.py icons             -> docs/curseforge/icons/<block>.png (inventory-style icons of the blocks with no flat texture)
  python3 tools/capture_images.py renders [slug...]  -> docs/curseforge/images/render_<slug>.jpg (the 3D views of tools/preview/plasma_renders.html)
  python3 tools/capture_images.py plates             -> docs/curseforge/images/NN_<slug>.jpg (every plate of tools/preview/curseforge_sheet.html)

Run the generators first (gen_plasma_renders.py, gen_curseforge_sheet.py). Needs Google Chrome in /Applications.
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from gen_material_assets import read_png, write_png  # noqa: E402
from gen_plasma_renders import ICONS  # noqa: E402

CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
IMAGES = ROOT / "docs/curseforge/images"
ICON_DIR = ROOT / "docs/curseforge/icons"
PROFILE = Path(tempfile.gettempdir()) / "bsp_capture_profile"


def shot(url, w, h, out, transparent=False, budget=6000):
    """One screenshot of a page at w x h CSS pixels, scale 1."""
    args = [CHROME, "--headless=new", "--no-first-run", "--disable-extensions", f"--user-data-dir={PROFILE}", "--hide-scrollbars",
            f"--window-size={w},{h}", "--force-device-scale-factor=1", f"--virtual-time-budget={budget}", f"--screenshot={out}"]
    if transparent:
        args.append("--default-background-color=00000000")
    r = subprocess.run(args + [url], capture_output=True, text=True)
    if not Path(out).exists():
        raise SystemExit(f"no screenshot for {url}:\n{r.stderr[-2000:]}")


def jpeg(png, jpg):
    subprocess.run(["sips", "-s", "format", "jpeg", "-s", "formatOptions", "92", str(png), "--out", str(jpg)], capture_output=True, check=True)
    os.unlink(png)


def icons():
    cols, cell = 8, 96
    rows = (len(ICONS) + cols - 1) // cols
    tmp = Path(tempfile.gettempdir()) / "bsp_icons.png"
    shot(f"file://{ROOT}/tools/preview/plasma_renders.html?icons=1", cols * cell, rows * cell, tmp, transparent=True)
    w, h, px = read_png(tmp.read_bytes())
    assert w == cols * cell and h == rows * cell, (w, h)
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    for i, (name, _, _) in enumerate(ICONS):
        x0, y0 = (i % cols) * cell, (i // cols) * cell
        write_png(ICON_DIR / f"{name}.png", cell, cell, lambda x, y: px[y0 + y][x0 + x])
    os.unlink(tmp)
    print("icons:", len(ICONS), "->", ICON_DIR)


def renders(slugs):
    page = ROOT / "tools/preview/plasma_renders.html"
    if not slugs:
        slugs = re.findall(r"\['([a-z_]+)','[^']*',[a-zA-Z]+\]", page.read_text())
    for slug in slugs:
        tmp = Path(tempfile.gettempdir()) / f"render_{slug}.png"
        shot(f"file://{page}?view={slug}", 800, 500, tmp)
        jpeg(tmp, IMAGES / f"render_{slug}.jpg")
        print("render:", slug)


def plates():
    page = ROOT / "tools/preview/curseforge_sheet.html"
    plates = json.loads((IMAGES / "plates.json").read_text())
    for old in IMAGES.glob("[0-9][0-9]_*.jpg"):
        old.unlink()
    for p in plates:
        tmp = Path(tempfile.gettempdir()) / f"plate_{p['n']}.png"
        shot(f"file://{page}?plate={p['n']}", 800, 600, tmp, budget=3000)
        jpeg(tmp, IMAGES / f"{p['n']:02d}_{p['slug']}.jpg")
        print("plate:", p["n"], p["slug"])


if __name__ == "__main__":
    what = sys.argv[1] if len(sys.argv) > 1 else "plates"
    {"icons": lambda: icons(), "renders": lambda: renders(sys.argv[2:]), "plates": lambda: plates()}[what]()
