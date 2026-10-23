#!/usr/bin/env python3
"""Writes the Land Office wording into en_us.json.

  python3 tools/gen_land_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "block.bsp_core.land_office": "Land Office",
    "jei.bsp_core.info.land_office": "Admin only. Where players buy land from BSP Region Guard: a map of the plots for sale (amber), open ground (dashed) and their own claims (green) around them. Click a plot for its size and price, then BUY. Outline ground with Region Guard's wand and the office opens by itself with the price. GIVE UP returns part of what was paid.",
    "gui.bsp_core.land.title": "Land Office",
    "gui.bsp_core.land.side": "Plot",
    "gui.bsp_core.land.scale": "%s blocks across",
    "gui.bsp_core.land.zoom_in": "Zoom in",
    "gui.bsp_core.land.zoom_out": "Zoom out",
    "gui.bsp_core.land.centre": "Centre",
    "gui.bsp_core.land.buy": "Buy",
    "gui.bsp_core.land.enlarge": "Enlarge",
    "gui.bsp_core.land.give_up": "Give up",
    "gui.bsp_core.land.confirm": "Confirm",
    "gui.bsp_core.land.done": "Done",
    "gui.bsp_core.land.confirm_hint": "Press CONFIRM to go ahead, or click anywhere else to stop.",
    "gui.bsp_core.land.legend": "Amber: for sale. Dashed: open ground. Green: yours. Violet: other players' claims. White: your outline. Drag to move, scroll to zoom.",
    "gui.bsp_core.land.kind_other": "%s's claim",
    "gui.bsp_core.land.nearest": "%s blocks from %s",
    "gui.bsp_core.land.touches": "Touches %s",
    "gui.bsp_core.land.strip": "land office: Region Guard holds the claims",
    "gui.bsp_core.land.strip_no_claims": "land office: no claims in this world",
    "gui.bsp_core.land.strip_absent": "land office: Region Guard is not installed",
    "gui.bsp_core.land.balance": "balance %s",
    "gui.bsp_core.land.side_hint": "Click a plot on the map.",
    "gui.bsp_core.land.none_here": "Nothing for sale in this world.",
    "gui.bsp_core.land.absent": "Region Guard is not installed, so there is no land to sell.",
    "gui.bsp_core.land.kind_sale": "for sale",
    "gui.bsp_core.land.kind_open": "open ground",
    "gui.bsp_core.land.kind_mine": "your claim",
    "gui.bsp_core.land.size": "%s x %s blocks",
    "gui.bsp_core.land.price": "%s pieces",
    "gui.bsp_core.land.after": "%s left after",
    "gui.bsp_core.land.per_block": "%s a block, %s to %s a side",
    "gui.bsp_core.land.outline": "Outline %s x %s: %s pieces",
    "gui.bsp_core.land.coins": "coins %s",
    "gui.bsp_core.land.coins_none": "none",
    "gui.bsp_core.land.price_coins": "%s",
    "gui.bsp_core.land.outline_coins": "Outline %s x %s: %s",
    "gui.bsp_core.land.outline_none": "Outline your ground here with the wand: the office opens by itself.",
    "gui.bsp_core.land.enlarge_ok": "Your outline covers it: ENLARGE grows it to the outline.",
    "gui.bsp_core.land.enlarge_no": "To enlarge, outline ground that covers the whole claim.",
    "gui.bsp_core.land.distance": "%s blocks away",
    "message.bsp_core.land.no_selection": "Outline the ground with Region Guard's wand first.",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} land lines")
