#!/usr/bin/env python3
"""Writes the Demo Totem's blockstate, item model and wording. The demo uses the Shatter Totem's own models (owned, or bare while
it wears a look), so it looks exactly like a real totem.

  python3 tools/gen_demo_assets.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core"
LANG = ASSETS / "lang/en_us.json"
KEYS = {
    "block.bsp_core.demo_totem": "Demo Totem",
    "tooltip.bsp_core.demo_totem": "Admins only. For show: it does nothing a Shatter Totem does.",
    "message.bsp_core.demo.place_admins": "Only admins can place demo totems",
    "message.bsp_core.demo.mine_admins": "Only admins can mine demo totems",
    "command.bsp_core.demo.given": "Gave %s Demo Totem(s) to %s",
    "command.bsp_core.demo.not_admin": "%s is not an admin: Demo Totems are for admins only",
    "message.bsp_core.looks.demo_admins": "Only admins can dress demo totems",
    "gui.bsp_core.looks.plain": "PLAIN",
    "gui.bsp_core.looks.plain_body": "The totem's own gold body, as on a totem without a look. Pick a hat below, or none.",
    "gui.bsp_core.looks.demo_shown": "A demo totem, for show: it does nothing a Shatter Totem does. Only admins change its look.",
}

variants = {}
for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
    for looks in ("false", "true"):
        v = {"model": "bsp_core:block/shatter_totem_bare" if looks == "true" else "bsp_core:block/shatter_totem_owned"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing},looks={looks}"] = v
(ASSETS / "blockstates/demo_totem.json").write_text(json.dumps({"variants": variants}, indent=2))
(ASSETS / "models/item/demo_totem.json").write_text(json.dumps({"parent": "bsp_core:item/shatter_totem"}, indent=2))

lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote the demo totem's blockstate, item model and {len(KEYS)} lines")
