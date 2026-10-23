#!/usr/bin/env python3
"""Writes the wording for servers without totem placement (restrictions.noTotemServers) into en_us.json.

  python3 tools/gen_no_totem_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "message.bsp_core.no_totem.held": "Shatter Totems cannot be placed down anywhere on this server. If you log out, it will go back to your previous position on a server where totem placement is allowed.",
    "message.bsp_core.no_totem.held_kept": "Shatter Totems cannot be placed down anywhere on this server. If you log out, it will be kept for you and given back when you next log in.",
    "message.bsp_core.no_totem.no_place": "No Shatter Totem placement anywhere on this server",
    "message.bsp_core.no_totem.kept_on_death": "Shatter Totems cannot be placed down on this server: yours stays with you through the death.",
    "message.bsp_core.no_totem.placed_back": "The Shatter Totem you logged out with on a server without totem placement stands at %s on %s.",
    "message.bsp_core.no_totem.admin": "This server allows no Shatter Totem placement (restrictions.noTotemServers). The command runs anyway.",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} no-totem lines")
