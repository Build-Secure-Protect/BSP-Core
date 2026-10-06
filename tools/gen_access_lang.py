#!/usr/bin/env python3
"""Writes the totem ACCESS tab wording into en_us.json.

  python3 tools/gen_access_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "gui.bsp_core.tree.tab.access": "ACCESS",
    "gui.bsp_core.tree.auras.shown": "AURAS",
    "gui.bsp_core.tree.auras.hidden": "AURAS",
    "gui.bsp_core.access.name": "Online player name",
    "gui.bsp_core.access.add": "Add",
    "gui.bsp_core.access.col.upgrades": "UPGRADES",
    "gui.bsp_core.access.col.alarm": "ALARM",
    "gui.bsp_core.access.col.ward": "WARD",
    "gui.bsp_core.access.col.machines": "MACHINES",
    "gui.bsp_core.access.yes": "YES",
    "gui.bsp_core.access.no": "no",
    "gui.bsp_core.access.count": "%s of %s. Cleared if the totem is stolen.",
    "gui.bsp_core.access.help.0": "Upgrades: buy upgrades, change chunks and projectors.",
    "gui.bsp_core.access.help.1": "Alarm: does not set it off. Ward: not weakened by it.",
    "gui.bsp_core.access.help.2": "Machines: may open this totem's plasma machines.",
    "gui.bsp_core.access.help.3": "Friends still cannot pick the totem up or steal-protect it.",
    "message.bsp_core.access.not_online": "No player by that name is online",
    "message.bsp_core.access.is_owner": "That is the owner",
    "message.bsp_core.access.full": "The access list is full",
    "message.bsp_core.access.machines": "Only the totem's owner and friends with Machines access can open this",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} access lines")
