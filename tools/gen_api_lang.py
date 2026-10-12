#!/usr/bin/env python3
"""Writes the wording for BSP-Core's hooks for other mods (the transfer hook) into en_us.json.

  python3 tools/gen_api_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "message.bsp_core.transfer.stored": "Your Shatter Totem is stored for the transfer (%s). It comes back to you when you arrive.",
    "message.bsp_core.transfer.returned": "Your Shatter Totem is back with you.",
    "message.bsp_core.transfer.timeout": "The transfer did not happen. Your Shatter Totem is back with you.",
    "message.bsp_core.transfer.arrived": "Your Shatter Totem arrived with you.",
    "message.bsp_core.recall.remote": "Your totem on %s is being stolen. Click here to recall to it (you have %s s).",
    "message.bsp_core.recall.remote_going": "Taking you to %s; you land near your totem on arrival.",
    "message.bsp_core.recall.remote_none": "No teleport mod can take you to %s from here.",
    "gui.bsp_core.zone.group.teleport": "Teleports (the pack's teleport mod)",
    "gui.bsp_core.zone.rule.teleport_in": "Teleporting into the zone",
    "gui.bsp_core.zone.rule.teleport_out": "Teleporting out of the zone",
    "gui.bsp_core.zone.rule.set_home": "Setting a home in the zone",
    "jei.bsp_core.info.anti_totem": "Admin only. Keeps Shatter Totems and chosen BSP blocks out of a box around it, and can tell the pack's teleport mod to refuse teleports in or out and homes inside. It has no recipe and cannot be mined.",
    "buff.bsp_core.extra_homes": "Extra Homes",
    "buff.bsp_core.extra_homes.effect": "+%s home slot(s) in the pack's teleport mod",
    "buff.bsp_core.swift_travel": "Swift Travel",
    "buff.bsp_core.swift_travel.effect": "-%s%% teleport cooldown in the pack's teleport mod",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} api lines")
