#!/usr/bin/env python3
"""Fails when an inventory icon is missing, the wrong size, empty or fully transparent, or when index.json and the lang file disagree."""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from gen_material_assets import read_png  # noqa: E402
from gen_icons import ids, OUT, SIZE  # noqa: E402

problems = []
index = json.loads((OUT / "index.json").read_text()) if (OUT / "index.json").exists() else {}
wanted = {i for i, _, _ in ids()}
for id_, kind, name in ids():
    p = OUT / f"{id_}.png"
    if not p.exists():
        problems.append(f"{id_}: no icon")
        continue
    w, h, rows = read_png(p.read_bytes())
    if (w, h) != (SIZE, SIZE):
        problems.append(f"{id_}: {w} x {h}, not {SIZE} x {SIZE}")
    drawn = sum(1 for row in rows for px in row if px[3] > 8)
    colours = {px[:3] for row in rows for px in row if px[3] > 8}
    if drawn < SIZE * SIZE * 0.02:
        problems.append(f"{id_}: {drawn} drawn pixels, as good as empty")
    elif len(colours) < 2:
        problems.append(f"{id_}: one flat colour")
    if index.get(id_, {}).get("name") != name or index.get(id_, {}).get("kind") != kind:
        problems.append(f"{id_}: index.json out of step")
extra = set(index) - wanted
if extra:
    problems.append(f"index.json lists ids the lang file lacks: {sorted(extra)}")
if problems:
    print("\n".join(problems))
    sys.exit(1)
print(f"icons ok: {len(wanted)} ids")
