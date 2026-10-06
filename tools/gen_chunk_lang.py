#!/usr/bin/env python3
"""Writes the chunk loading wording (Anchor and Survey upgrades, the CHUNKS tab, the Totem Projector
screen) into en_us.json.

  python3 tools/gen_chunk_lang.py

Edit here and rerun.
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"

KEYS = {
    "buff.bsp_core.anchor": "Anchor",
    "buff.bsp_core.anchor.effect": "Keeps %s chunks loaded",
    "buff.bsp_core.survey": "Survey",
    "buff.bsp_core.survey.effect": "Pick chunks within %1$s x %1$s",
    "gui.bsp_core.tree.tab.chunks": "CHUNKS",
    "gui.bsp_core.chunks.loaded": "LOADED %s / %s",
    "gui.bsp_core.chunks.range": "RANGE %s x %s",
    "gui.bsp_core.chunks.help.0": "Click chunks to load them.",
    "gui.bsp_core.chunks.help.1": "The totem's chunk is always",
    "gui.bsp_core.chunks.help.2": "loaded. North is up.",
    "gui.bsp_core.chunks.projectors": "%s picked at projectors",
    "gui.bsp_core.chunks.second.0": "Not your main totem:",
    "gui.bsp_core.chunks.second.1": "it loads its own chunk only.",
    "gui.bsp_core.chunks.online_only": "Only while you are online",
    "gui.bsp_core.chunks.disabled": "Chunk loading is switched off",
    "gui.bsp_core.chunks.legend.totem": "This totem",
    "gui.bsp_core.chunks.legend.picked": "Loaded from here",
    "gui.bsp_core.chunks.legend.projector": "Loaded from elsewhere",
    "message.bsp_core.chunks.second_totem": "Only your main totem can go further. Your other totems load their own chunk only.",
    "message.bsp_core.chunks.unavailable": "Chunk loading is not available here right now",
    "message.bsp_core.chunks.not_owner": "Only the totem's owner can choose its chunks",
    "message.bsp_core.chunks.home": "The totem's own chunk is always loaded",
    "message.bsp_core.chunks.projector_home": "The projector's own chunk is always loaded while it holds chunks",
    "message.bsp_core.chunks.range": "That chunk is out of range. The Survey upgrade widens it.",
    "message.bsp_core.chunks.already": "That chunk is already loaded by this totem",
    "message.bsp_core.chunks.full": "No chunks left. Unload one first, or raise Anchor.",
    "gui.bsp_core.admin.settings.chunks_always": "Chunks: load always",
    "gui.bsp_core.admin.settings.chunks_online": "Chunks: owner online",
    "gui.bsp_core.admin.settings.chunks_help": "Click to switch. Saved to the config.",
}

lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} chunk loading lines")
