#!/usr/bin/env python3
"""Writes the Plasma Interface screen wording into en_us.json.

  python3 tools/gen_interface_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "gui.bsp_core.interface.supply": "%s mB/t",
    "gui.bsp_core.interface.supply_from": "from the extractors",
    "gui.bsp_core.interface.cloak": "The totem makes %s; Cloaking takes %s",
    "gui.bsp_core.interface.reset": "RESET VIEW",
    "gui.bsp_core.interface.zoom": "Zoom %s%%",
    "gui.bsp_core.interface.members": "%s of %s blocks joined",
    "gui.bsp_core.interface.refused_n": "%s refused (over the limit)",
    "gui.bsp_core.interface.refused": "refused",
    "gui.bsp_core.interface.shut": "shut",
    "gui.bsp_core.interface.extractors": "Extractors: %s",
    "gui.bsp_core.interface.cables": "Cables: %s, repeaters: %s",
    "gui.bsp_core.interface.receivers": "Bases and chargers reached: %s",
    "gui.bsp_core.interface.delivered": "Delivered: %s mB/t",
    "gui.bsp_core.interface.spare": "Spare: %s mB/t",
    "gui.bsp_core.interface.help": "Drag to turn, right-drag to pan, scroll to zoom. Numbers are mB/t passing each block this second. A cable with a dash carries nothing: out of reach, or nothing at its end.",
    "gui.bsp_core.interface.legend.extractor": "Gold: what an extractor gives",
    "gui.bsp_core.interface.legend.cable": "Blue: cables and what arrives",
    "gui.bsp_core.interface.legend.refused": "Red: not part of the group",
    "gui.bsp_core.projector.active_reserve": "PROJECTING (on the base's tank)",
}
lang = json.loads(LANG.read_text())
for old in ("gui.bsp_core.interface.drums", "gui.bsp_core.interface.legend.drum", "gui.bsp_core.interface.help.0", "gui.bsp_core.interface.help.1", "gui.bsp_core.interface.help.2", "gui.bsp_core.interface.help.3"):  # renamed or folded
    lang.pop(old, None)
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} interface lines")
