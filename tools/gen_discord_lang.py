#!/usr/bin/env python3
"""Writes the wording for Discord linking (/bsp discord and the line on Herald's card) into en_us.json.

  python3 tools/gen_discord_lang.py
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "message.bsp_core.discord.code": "Your Discord link code is %s. In the BSP Discord, type %s within %s minutes.",
    "message.bsp_core.discord.copy": "[Copy]",
    "message.bsp_core.discord.copy.hover": "Copy the /link command to paste in Discord",
    "message.bsp_core.discord.too_many": "You have made %s codes this hour. Use the last one, or try again later.",
    "message.bsp_core.discord.failed": "The code could not be saved. Try again in a moment.",
    "message.bsp_core.discord.unlinked": "Your Discord is unlinked. Herald's messages stop.",
    "message.bsp_core.discord.status.linked": "Discord: linked. Herald's messages come to your Discord DMs.",
    "message.bsp_core.discord.status.not_linked": "Discord: not linked. /bsp discord link gives you a code.",
    "message.bsp_core.discord.status.code": "Discord: code %s waits for /link in the BSP Discord (%s min left).",
    "message.bsp_core.discord.status.unknown": "Discord: your link cannot be checked right now.",
    "message.bsp_core.discord.undelivered": "Your Discord warning could not reach you: allow DMs from the BSP server (Discord: the server's Privacy Settings, Direct Messages).",
    "gui.bsp_core.discord.label": "Discord:",
    "gui.bsp_core.discord.not_linked": "not linked",
    "gui.bsp_core.discord.linked": "linked ✓",
    "gui.bsp_core.discord.checking": "checking...",
    "gui.bsp_core.discord.unknown": "cannot check now",
    "gui.bsp_core.discord.link": "LINK",
    "gui.bsp_core.discord.unlink": "UNLINK",
    "gui.bsp_core.discord.left": "%s left",
    "gui.bsp_core.discord.type": "/link %s in BSP Discord",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} discord lines")
