#!/usr/bin/env python3
"""Writes the aura cube and hide-switch wording into en_us.json.

  python3 tools/gen_aura_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "message.bsp_core.auras.shown": "Totem auras shown",
    "message.bsp_core.auras.hidden": "Totem auras hidden (for you only)",
    "key.categories.bsp_core": "BSP Core",
    "key.bsp_core.toggle_auras": "Show or hide totem auras",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} aura lines")
