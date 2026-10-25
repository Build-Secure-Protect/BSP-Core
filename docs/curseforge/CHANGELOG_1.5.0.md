# BSP-Core 1.5.0

Your totem can call you on Discord now: **Herald**, a new power, sends you a direct message when someone starts stealing it, and every steal on the network gets one line in the BSP Discord. Plus everything since 1.4.3: smaller windows, fixes on the tree and the screens, a lighter X-ray, and safer single-player worlds.

**Existing worlds:** a new `[discord]` section is added to `serverconfig/bsp_core-server.toml` by itself, switched off. Nothing changes until you turn on `discord.enabled`, and that waits for the Discord bot in BSP-Velocity Additions on the proxy. Until then Herald shows on the tree, locked. Upgrades already bought keep their levels.

## Herald and the Discord steal warnings

- **Herald** is a Base power straight after Alarm, on the tier II ring, one level, at Raid prices like Sentinel (2 Gold Shatter Coins and 6 XP with the default settings). Its node reads HR.
- **When someone starts on your totem**, the BSP Discord bot messages you at the moment the game would warn you: the server, the spot, the thief and the seconds left. When it is over you get a second message: stolen, or saved and why (the thief walked away, left the game, the totem was taken down, or an operator stopped it). A thief's Shroud holds the message back just like the in-game warning.
- **Every steal on the network** of an owned totem, Herald or not, gets one line in the BSP Discord's alert channel: "A totem is being stolen on Frostwarf". It names the server and nothing else.
- **Linking:** `/bsp discord link`, or LINK on Herald's card, gives you a six-letter code for ten minutes; type `/link` and the code in the BSP Discord. `/bsp discord status` shows your link and `/bsp discord unlink` ends it. Herald's card shows the same: "Discord: not linked" with LINK, your code while it waits, or "Discord: linked" with UNLINK.
- **Safe by design.** You never type a Discord name, number or password into the game, and the game never holds your Discord details: only the bot knows who you are on Discord, and it learns that from Discord itself. If a message cannot reach you, nothing is posted publicly; the game tells you once at your next login to allow DMs from the BSP server.
- **No spam:** one warning, one outcome and one public line per steal, and at most one warning per player every 30 seconds.
- The guide book has Herald in the walls list and two new pages, "Herald" and "Linking Discord".

## For server owners

- Turn it on per server with `discord.enabled = true` and a `discord.serverName` (the name players see). It needs network storage with Redis. The alerts go out on their own Redis channel, `bsp_core:discord`, so the bot's Redis user can be limited to it. No game server ever holds the Discord token. `docs/DISCORD_SETUP.md` in the repository has the settings and checks.
- `/bsp` opens for every player so they can reach `/bsp discord`. Every other `/bsp` command keeps its operator-or-moderator rule.

## Changes

- **No more "click here to recall" line** for an owner who is on another server while their totem is being stolen: that line ran a staff-only command and did nothing for players. They still get the steal warning wherever they are, and the Recall popup is for an owner on the totem's own server.
- **Screens fit a small window.** When the window is smaller than a screen (a high GUI scale or a small window, down to 320 x 240), the screen is drawn scaled down to fit, mouse and all.
- **Lighter on your machine:** X-ray no longer eats memory while it is on and spreads its first scan over a second or so; a totem under steal stops re-sending its whole Cloaking picture every second; working machines no longer make clients rebuild their chunk every second; flowing cables, Sentinel, the leaderboard and the Land Office map all send and draw less.
- **An admin's totem goes along with the proxy's `/server` switch** instead of being placed on the server they left.

## Fixes

- **Every power has its own orb colour.** Harvest, Sentinel, Extra Homes, Swift Travel and Herald drew plain red, the same as Lockpick; they are yellow-green, violet, sea green, amber and rose.
- **Extra Homes and Swift Travel can be chosen on the tree.** Extra Homes was drawn on top of Sanctuary, so neither it nor Swift Travel could be clicked.
- **Single-player and LAN worlds never join a network's database**, even when the configs that came with the pack name one.
- **Placeholders for a missing mod's item** (from BSP-InventorySync) are refused by shops, kits and season prizes, so nothing is lost or copied.
- Text that ran past its box and things drawn over each other on the bank, shops, Land Office, kit editor, Coin Factory, Deposit Box, Battery Charger, projector, valve, assembly guide, Team screen, invite and Recall cards, and the totem screen's title.
- The kit editor's text boxes sit on their fields again.
- The Plasma Tank's streams and the Accelerator's glowing parts no longer flicker into the wrong layer.
- The projector's status line showed a raw text key while it ran on the base's tank.
- A projector base or battery charger from an older save no longer forgets which plasma powers it offers until its next refresh.

## For the other BSP mods

- The Discord alerts and link keys on Redis are described in `docs/API.md` ("Discord alerts"), for the bot in BSP-Velocity Additions.
- `BspStorage.jdbcDriver()` (the MySQL driver BSP-Core uses), `BspScores` and `ScoreEvent.Changed` (the leaderboard), `BspTotems.forgetPlayer` and `/bsp totem forget <player>` (a clean slate for test bots), and BSP-Core's teams count as BSP-RankUp's teams for its kill rule.
- Steal lookups for other mods never load a chunk.
