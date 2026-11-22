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
    "gui.bsp_core.screen.look.3": "Most Wanted",
    "gui.bsp_core.screen.rotation": "In rotation",
    "gui.bsp_core.screen.every": "Every",
    "gui.bsp_core.screen.seconds": "%s s",
    "screen.bsp_core.board.missing": "Board %s is not installed here",
    "screen.bsp_core.board.empty": "Nothing on this board yet",
    # the VIPs board (2026-11-22)
    "screen.bsp_core.vips.title": "OUR VIPS",
    "screen.bsp_core.vips.support": "SUPPORT",
    "screen.bsp_core.vips.none": "No VIPs yet",
    "gui.bsp_core.screen.vip_texts": "VIP texts",
    "gui.bsp_core.screen.back": "Back",
    "gui.bsp_core.screen.vip.top": "Top",
    "gui.bsp_core.screen.vip.bottom": "Bottom",
    "gui.bsp_core.screen.vip.label": "Key label",
    "gui.bsp_core.screen.vip.link": "Link",
    "gui.bsp_core.screen.vip.colours": "Colours: &6 gold &c red &a green &l bold &r plain",
    "gui.bsp_core.screen.vip.label_hint": "Shown on the key. Empty: SUPPORT",
    "gui.bsp_core.screen.vip.link_ok": "A right-click on the key opens the link prompt",
    "gui.bsp_core.screen.vip.link_none": "No link: the key is hidden",
    "gui.bsp_core.screen.vip.link_bad": "Not a web address: the saved link stays",
    "gui.bsp_core.screen.vip.link_unsaved": "Saved on Enter, Back or Done",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} score screen lines")
