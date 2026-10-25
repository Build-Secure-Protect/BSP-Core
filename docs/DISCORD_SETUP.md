# Discord steal warnings: setting up the game servers

BSP-Core's half of the Discord steal warnings (the Herald upgrade, the alerts, linking). The Discord bot runs on the proxy in BSP-Velocity Additions, which has its own setup guide for the Discord application, the token and the bot's permissions. Nothing here asks for a Discord token: no game server ever holds one.

## What every backend needs

In `serverconfig/bsp_core-server.toml` (or the world's copy) on **every** server of the network:

```toml
[storage]
    mode = "mysql"            # network storage: Discord linking and alerts travel over its Redis link

    [storage.redis]
        enabled = true        # with host, port, password and channel as the other network features already use

[discord]
    enabled = true            # Herald unlocks and alerts go out; false keeps Herald locked
    serverName = "Frostwarf"  # the name players see in the messages; empty uses storage.serverId
    redisChannel = "bsp_core:discord"  # must match the bot's setting; leave as it is unless you change both
```

- Forge fills in the `[discord]` section by itself at the first start with the new jar; edit it after.
- Turn `discord.enabled` on only once the bot is running on the proxy. Before that, players could buy Herald and link codes would wait for a bot that is not there.
- `serverName` is public: it is the only detail in the alert channel's line ("A totem is being stolen on Frostwarf").
- Single-player and LAN worlds ignore all of this: Herald stays locked there and nothing is sent.
- The client pack must not ship this file. Keep `bsp_core-server.toml` out of the pack's `defaultconfigs` (it also holds the database and Redis passwords).

## Redis permissions (only if the network's Redis users are limited)

The backends' Redis user needs, on top of what network storage already uses:

- the channel `bsp_core:discord` (`PUBLISH`), and
- the keys `bsp_core:discord:*` (`SET`, `DEL`, `EXISTS`).

The bot's own Redis user is set up in BSP-Velocity Additions (subscribe to that channel; `GETDEL`, `SET`, `DEL` on those keys).

## Checking it works

- In game, an operator runs `/bsp discord status`: "not linked" (not "This server is not linked to Discord") means `discord.enabled` is on.
- `redis-cli -a <password> SUBSCRIBE bsp_core:discord` on the main machine shows the alerts as steals happen. `docs/TESTING.md` has the full checklists ("Discord alerts over Redis", "Linking a Discord account", "The undelivered line at login").
- With `discord.enabled = true` but no Redis link, the server log says once: "no Discord alert can reach the bot".
