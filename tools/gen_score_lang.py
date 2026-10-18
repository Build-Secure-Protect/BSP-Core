#!/usr/bin/env python3
"""Writes the Score Screen's board and rotation wording into en_us.json (BSP-Teleport request R8).

  python3 tools/gen_score_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "gui.bsp_core.screen.mode.3": "Board",
    "gui.bsp_core.screen.mode.4": "Rotate",
    "gui.bsp_core.screen.board": "Board",
    "gui.bsp_core.screen.board.none": "No boards registered on this client",
    "gui.bsp_core.screen.look": "Board look",
    "gui.bsp_core.screen.look.0": "Ledger",
    "gui.bsp_core.screen.look.1": "Tiles",
    "gui.bsp_core.screen.look.2": "Side column",
    "gui.bsp_core.screen.rotation": "In rotation",
    "gui.bsp_core.screen.every": "Every",
    "gui.bsp_core.screen.seconds": "%s s",
    "screen.bsp_core.board.missing": "Board %s is not installed here",
    "screen.bsp_core.board.empty": "Nothing on this board yet",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} score screen lines")
