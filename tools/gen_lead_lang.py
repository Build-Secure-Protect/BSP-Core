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
    "item.bsp_core.totem_radiation_coating": "Totem Radiation Coating",
    "tooltip.bsp_core.coating": "Use on a Radiation Shielding Panel, Window or Door to coat it. Coated shielding round a totem weakens its totem radiation.",
    "tooltip.bsp_core.coating.coated": "Coated against totem radiation",
    "message.bsp_core.coating.only_shielding": "Only Radiation Shielding Panels, Windows and Doors take the coating",
    "message.bsp_core.coating.already": "This block is already coated",
    "jei.bsp_core.info.totem_radiation_coating": "Use it on a Radiation Shielding Panel, Window or Door to coat that block; a door takes one tin for both halves. Coated shielding all round a totem, within 32 blocks, weakens the totem radiation that lets rival Totem Compasses find it: each layer the radiation passes through takes a fifth off how far away the totem is seen, up to 4 layers. An open door does not count. The coating cannot be taken off, and a coated block keeps it when broken.",
    "gui.bsp_core.lead_chest.shielded_tip": "Lead-lined: nothing in this chest gives off radiation, however much is in it.",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} lead lines")
