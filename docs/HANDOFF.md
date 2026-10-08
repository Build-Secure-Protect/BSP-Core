# Starting the next BSP-Core conversation

The full handoff lives in the Obsidian vault (`obsidian-bsp` MCP connection):

| Note | What it holds |
|---|---|
| `Claude Notes/00 Handoff - Start Here.md` | The standing rules and where everything lives |
| `Claude Notes/01 Handoff - 2026-10-08 Wave Plasma.md` | The rules as set in the Wave Plasma conversations, the state of 0.3.0, the code map for the plasma system, generator order, preview and capture workflow, CurseForge publishing steps |
| `Claude Notes/Changelog (Claude).md` | Dated record of every step; read the last ten entries |
| `Claude Notes/Task Queue.md` | What comes next |
| `Claude Notes/BSP-Core Design Decisions.md` | Rules that were decided |

## Message to paste into the new conversation

```
Continue BSP-Core (Forge 1.20.1 mod at /Users/mrgregles/Github/BSP-Core). Use the obsidian-bsp MCP connection and read, in order: Claude Notes/00 Handoff - Start Here.md, Claude Notes/01 Handoff - 2026-10-08 Wave Plasma.md, the last ten entries of Claude Notes/Changelog (Claude).md, and Claude Notes/Task Queue.md. Follow every rule in the two handoff notes: never push, record in the vault, decisions in chat with defaults, three mock-ups before new visuals, generators for all assets, CurseForge files only when I ask. I cannot be tested by you; I test from docs/TESTING.md and send screenshots.
```

## Before you start it

- The new conversation needs the `obsidian-bsp` MCP connection and this folder as its working directory.
- Version 0.3.0 is built at `build/libs/bsp-core-0.3.0.jar`. Everything since commit `8cf013d` is uncommitted; commit and push yourself when ready.
