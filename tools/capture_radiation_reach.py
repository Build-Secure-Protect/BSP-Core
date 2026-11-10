#!/usr/bin/env python3
"""Captures the ten totem radiation and Totem Compass reach pictures (look C) with headless Chrome:

  python3 tools/capture_radiation_reach.py   -> tools/preview/radiation_reach/pair<N>_<top|side>.png

Source: tools/preview/radiation_reach_plate.html (?pair=N&view=top|side draws one picture at its own size).
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from capture_images import shot  # noqa: E402

PAGE = ROOT / "tools/preview/radiation_reach_plate.html"
OUT = ROOT / "tools/preview/radiation_reach"
SIZE = {"top": (1280, 960), "side": (1280, 572)}

if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for n in (1, 2, 3, 4, 5):
        for view in ("top", "side"):
            w, h = SIZE[view]
            out = OUT / f"pair{n}_{view}.png"
            shot(f"file://{PAGE}?pair={n}&view={view}", w, h, out, budget=5000)
            print("picture:", out.relative_to(ROOT))
