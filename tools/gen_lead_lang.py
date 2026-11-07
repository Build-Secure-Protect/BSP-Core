#!/usr/bin/env python3
"""Writes the Lead-Lined Chest and radiation shielding wording into en_us.json.

  python3 tools/gen_lead_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "block.bsp_core.lead_lined_chest": "Lead-Lined Chest",
    "block.bsp_core.radiation_shielding_panel": "Radiation Shielding Panel",
    "block.bsp_core.radiation_shielding_window": "Radiation Shielding Window",
    "block.bsp_core.radiation_shielding_door": "Radiation Shielding Door",
    "container.bsp_core.lead_lined_chest_double": "Large Lead-Lined Chest",
    "gui.bsp_core.lead_chest.shielded": "SHIELDED",
    "jei.bsp_core.info.lead_lined_chest": "A chest whose contents never give off radiation, however much raw uranium, yellorium, pellets or fuel rods are in it. In an ordinary chest they do, through walls. Two side by side join into a large chest; hoppers, pipes and comparators work as with any chest. The light on the lid is green while it is shut and amber while it is open. Lead plates from any mod make it.",
    "jei.bsp_core.info.radiation_shielding": "For walling off radiation. The Panel stops all of it, the Window lets a tenth through (and all the light), the Door blocks nearly all of it while shut and almost none while open, by hand or redstone. Panels side by side join into one riveted wall; windows in one plane join into one sheet of glass. As hard as iron.",
    "gui.bsp_core.lead_chest.shielded_tip": "Lead-lined: nothing in this chest gives off radiation, however much is in it.",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} lead lines")
