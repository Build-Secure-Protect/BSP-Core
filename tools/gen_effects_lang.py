#!/usr/bin/env python3
"""Writes the wording for the newer totem powers (Bouncy, X-ray, Cloaking, Recall, Recall Block) into en_us.json, and the
Ghost Block's empty blockstate and model (X-ray's stand-in block).

  python3 tools/gen_effects_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "buff.bsp_core.bouncy": "Bouncy",
    "buff.bsp_core.bouncy.effect": "%s%% less fall damage; land from 3 blocks or more and bounce back up with %s%% of the speed",
    "buff.bsp_core.xray": "X-ray",
    "buff.bsp_core.xray.effect": "Blocks within %s turn to faint glass for %s s, ores show through them bright; recharges in %s s",
    "block.bsp_core.ghost_block": "Ghost Block",
    "buff.bsp_core.cloaking": "Cloaking",
    "buff.bsp_core.cloaking.effect": "Outsiders see the land as the world generated it in a %1$s x %1$s x %1$s box, and nobody inside it; uses %2$s mB/t of plasma",
    "buff.bsp_core.recall": "Recall",
    "buff.bsp_core.recall.effect": "While the totem is being stolen you may teleport to within %s blocks of it",
    "buff.bsp_core.recall_block": "Recall Block",
    "buff.bsp_core.recall_block.effect": "Delays the owner's Recall offer by %s s while you steal with this totem in your offhand",
    "key.bsp_core.recall_accept": "Accept a Recall",
    "key.bsp_core.recall_decline": "Decline a Recall",
    "key.bsp_core.xray": "X-ray on/off",
    "hud.bsp_core.recall.title": "Totem under attack",
    "hud.bsp_core.recall.distance": "%s %s blocks away",
    "hud.bsp_core.recall.elsewhere": "In another dimension",
    "hud.bsp_core.recall.lands": "Recall lands you within %s blocks",
    "hud.bsp_core.recall.keys": "[%s] Recall    [%s] Stay",
    "message.bsp_core.recall.resting": "Recall is resting for another %s min",
    "message.bsp_core.recall.expired": "The Recall offer has run out",
    "message.bsp_core.recall.done": "Recalled to within %s blocks of your totem",
    "message.bsp_core.xray.none": "You carry no X-ray power",
    "message.bsp_core.xray.recharging": "X-ray recharges in %s s",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} effect lines")

# The Ghost Block (X-ray's stand-in for a hidden block) draws nothing, but a blockstate and an empty model keep the model loader quiet.
ASSETS = LANG.parent.parent
(ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
(ASSETS / "models/block").mkdir(parents=True, exist_ok=True)
(ASSETS / "blockstates/ghost_block.json").write_text(json.dumps({"variants": {"": {"model": "bsp_core:block/ghost_block"}}}, indent=1) + "\n")
(ASSETS / "models/block/ghost_block.json").write_text(json.dumps({"textures": {"particle": "minecraft:block/stone"}, "elements": []}, indent=1) + "\n")
print("ghost block assets written")
