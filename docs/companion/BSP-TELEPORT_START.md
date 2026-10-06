# Starting the teleport and homes mod in a new Claude conversation

The full handoff lives in the Obsidian vault (`obsidian-bsp`), in its own section so it stays apart from BSP-Core's notes:

| Note | What it holds |
|---|---|
| `BSP-Teleport/Claude Notes/Teleport - Handoff (Start Here).md` | Working rules, BSP design rules, where things are, a CLAUDE.md for the new repo |
| `BSP-Teleport/Claude Notes/Teleport - BSP-Core Integration Reference.md` | What BSP-Core is, how to depend on it, the classes to hook, the totem rules a teleport runs into, the screen style |
| `BSP-Teleport/Claude Notes/Teleport - Design Decisions.md` | The questions to settle first |
| `BSP-Teleport/Claude Notes/Teleport - Task Queue.md` | First tasks |
| `BSP-Teleport/Claude Notes/Teleport - Requests for BSP-Core.md` | Where the new mod asks for changes in BSP-Core |
| `BSP-Teleport/Claude Notes/Teleport - Changelog (Claude).md` | Session record |
| `BSP-Teleport/User Notes/Teleport - Mod Overview.md` | Plain-English overview |
| `00 Vault Map.md` | Which section belongs to which mod |

"BSP-Teleport" is a working name. If the mod gets a different name, the new conversation can rename the notes.

## Message to paste into the new conversation

```
I'm starting a new Forge 1.20.1 mod for my Build Secure Protect (BSP) modpack and server: a teleport and home system that hooks into the Shatter Totems from my other mod, BSP-Core.

Before anything else, use the obsidian-bsp MCP connection and read, in this order:
1. BSP-Teleport/Claude Notes/Teleport - Handoff (Start Here).md
2. BSP-Teleport/Claude Notes/Teleport - BSP-Core Integration Reference.md
3. BSP-Teleport/Claude Notes/Teleport - Design Decisions.md
4. BSP-Teleport/Claude Notes/Teleport - Task Queue.md

Follow the working rules in the handoff. Keep all notes for this mod inside the BSP-Teleport section of the vault and treat BSP-Core's notes as read-only. BSP-Core's source is at /Users/mrgregles/Github/BSP-Core if you need to check anything.

Don't write code yet. Start by asking me the design questions, as a numbered list in chat with a suggested default for each.
```

## Before you start it

- The new conversation needs the `obsidian-bsp` MCP connection, the same one this project uses.
- Open it in the folder where the new mod's repo will live (BSP-Core is in `/Users/mrgregles/Github/`). If it should be able to read BSP-Core's source, add that folder to the session too.
