#!/usr/bin/env python3
"""Writes the lang lines the recipe viewers need beyond JEI's own: EMI names a recipe category from emi.category.<namespace>.<path>.

  python3 tools/gen_compat_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "emi.category.bsp_core.tetrium_crucible": "Tetrium Crucible",
    "emi.category.bsp_core.combination_forge": "Combination Forge",
    "emi.category.bsp_core.illyrium_crucible": "Illyrium Crucible",
    "emi.category.bsp_core.illyrium_refinery": "Illyrium Refinery",
    "emi.category.bsp_core.magnetic_centrifuge": "Magnetic Centrifuge",
    "emi.category.bsp_core.coin_pressing": "Coin Pressing",
    "emi.category.bsp_core.hand_crushing": "Crushing by Hand",
}

lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} viewer lang lines")
