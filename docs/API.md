# BSP-Core API for other mods

Package `com.mrgregles.bsp_core.api`. Compile against the BSP-Core jar; keep every call in one adapter class on your side. These classes are the only ones meant for other mods; anything else in the jar may change without notice. Nothing here is final until BSP-Core 1.1.

| Class | What it offers |
|---|---|
| `BspBank` | A player's Shatter Pieces: `balance`, `charge` and `credit` (callback on the server thread), `format`. |
| `BspTotems` | `placed(server, owner)` the owner's placed totems as `GlobalPos`; `isMain(level, pos)`; `totemAt(server, where)`; `beingStolen(server, owner)` with the thief and the fraction of the steal left; `upgrade(name)` to look an upgrade up by name; `highestLevel(player, buff)` / `highestLevel(server, uuid, buff)` across placed and carried totems; `allPlaced(server)` and `placedNear(level, pos, radius)` as `Placed(owner, where)`; `stealRadius()`; `isTotem(blockState)`; `thiefAt(level, pos)`; `stealsAnywhere(server)` / `stealsAnywhere(server, owner)` every steal running on this server and, with network storage, on the other servers as `StealAnywhere(serverId, local, dimension, pos, owner, thief, thiefName, fractionLeft)`. |
| `BspPlayers` | `isAdmin`, `isModerator`; Shatter Coins in hand: `coinValue`, `payCoins`, `giveCoins` (values in Copper); `isDimensionAllowed`. |
| `BspZones` | `isBlocked(level, pos, key)` for Anti Totem zone rules; keys `TOTEM_PLACE`, `TOTEM_DROP`, and for a teleport mod to enforce `TELEPORT_IN`, `TELEPORT_OUT`, `SET_HOME` (admins switch them on per zone in the Anti Totem block's Rules tab; off on a new block). |
| `BspTransfer` | For server transfers: `beginTransfer(player)` just before sending them away takes their carried totems and stores them for the journey (the arrival server hands them back) and tells the player; `endTransfer(player)` when the teleport is cancelled hands them straight back; `isTransferring(uuid)`. A transfer that does not happen within `restrictions.transferSeconds` (10) returns the totems by itself; a stored totem whose owner never logs in anywhere is placed back at the departure point after `restrictions.transferTotemTimeoutHours` (48). Without a shared database the totems are held in memory and placed at the departure point on a real logout, as usual. |
| `BspTeams` | Teams: `teamOf(server, uuid)` / `team(server, teamId)` as `Team(id, name, colour, leader, members)`; `sameTeam(server, a, b)` (teammates count as friends with every access flag on each other's totems; a player is not their own teammate); `teammates(server, uuid)`; `members(server, teamId)` as `Member(uuid, name, leader, onlineHere, server, seenMs)` (`server` is the network server they are online on, "" when offline; `seenMs` is wall-clock and never 0 for a member: at the latest it is the moment they joined, since they were online to accept, and it moves on at every login and logout seen on any server); `all(server)`. Team teleports: a teleport mod calls `teleportsHandledBy(modId)` once at setup and handles `TeamEvent.TeleportRequest`; `requestTeleport(player, target)` asks it the way the TELEPORT key does. Otherwise read-only: players run a team from the Team screen. |
| `BspKits` | Kits: `kits(server)` / `kit(server, id)` as `Info(id, name, description, icon, items, inBrowser, once, cooldownSeconds, price, ranks, servers, grant, enabled)`; `status(player, id)` as `Status(state, number, text)` (states `READY`, `OWED`, `REDEEMED`, `COOLDOWN`, `RANK`, `SERVER`, `PRICE`, `SPACE`, `HIDDEN`; `number` is the seconds, pieces or slots the state names, `text` the ranks or servers); `redeem(player, id, then)` under every rule with the price charged; `give(server, uuid, id)` as a reward (no price, rank, cooldown, once or server rule; delivered when it fits, otherwise it waits for them); `owed(server, uuid)`; `timesTaken(server, uuid, id)`. Kits are made in the admin panel's KITS tab. |

## Events

Package `com.mrgregles.bsp_core.api.event`, posted on `MinecraftForge.EVENT_BUS` on the server thread; notifications only, never cancelable.

| Event | When |
|---|---|
| `TotemEvent.Placed` | a totem with an owner now stands (placed, or claimed where none stood); `getOwner`, `getWhere` |
| `TotemEvent.Removed` | the totem is gone (picked up, mined, reissued, wiped); `getOwner` is who owned it |
| `TotemEvent.OwnerChanged` | the owner changed while it stood (a completed steal or an operator); `getPrevious`, `getOwner` (null = unclaimed) |
| `TotemEvent.StealStarted` | a steal began; `getThief`, `getThiefName`, `getSeconds` |
| `TotemEvent.StealEnded` | the steal ended; `isSuccess` (an `OwnerChanged` follows), or `getReasonKey` for why it failed |
| `RecallEvent.CrossServer` | the owner clicked a Recall offer for a totem on another server (`/bsp recall <server>`); `getTargetServer()` is that server's `storage.serverId`; a teleport mod moves them there and calls `setHandled()`; BSP-Core lands them near the totem on arrival |
| `SeasonEvent.Ended` | a season ended on this server; `getEndedNumber`, `isFullReset` (coins wiped too), `getBy`. On a network each server fires its own when it picks the new season up. |
| `TeamEvent.Created` | a team was made; `getLeader`, `getTeamId`, `getTeamName` |
| `TeamEvent.Joined` | a player accepted an invite; `getPlayer` |
| `TeamEvent.Left` | a player is out: `getReason()` is `LEFT`, `KICKED` or `DISBANDED`; `getPlayer` |
| `TeamEvent.LeaderChanged` | the lead passed on; `getPrevious`, `getLeader` |
| `TeamEvent.Disbanded` | the team is gone (a `Left` with `DISBANDED` came first for every member); `getBy` is null for an admin command |
| `TeamEvent.TeleportRequest` | a member pressed TELEPORT on a teammate's row: `getPlayer()` (the `ServerPlayer` to move), `getTarget()` / `getTargetName()`, `getTargetServer()` ("" when the target is on this server); the teleport mod moves them under its own rules (no price for a team teleport; its cooldown and warm-up, destination access, zone flags, and its raid rule, which refuses a landing within 150 blocks of any running steal on the network) and calls `setHandled()` on every request it receives, refusals included (it tells the player why in its own words); only when nothing handled the event does BSP-Core say no teleport mod answered, which in practice means a pack without BSP-Teleport |
| `KitEvent.Given` | a kit's items landed in a player's inventory; `getPlayer`, `getKitId`, `getKitName`, `getHow()` is `FIRST_JOIN`, `REDEEMED`, `REWARD`, `ADMIN` or `TEST` (TEST keeps no record) |
| `KitEvent.Owed` | a kit became owed to a player (a reward, an admin's gift or a first login that did not fit, or they were offline); it is delivered when it fits and they are settled in, or from the WAITING card in their browser |

Upgrade names for `BspTotems.upgrade` are the constants of `TotemUpgrades.Buff` (for example `SWIFTNESS`, `RECALL`); the enum itself may be used directly.

Two upgrades exist only for a teleport mod to read; BSP-Core sells them on the tree (Home spoke, Carried prices at half XP, three levels each) and does nothing with them itself: `EXTRA_HOMES` (meant as one more home slot per level) and `SWIFT_TRAVEL` (meant as 25 % off the teleport cooldown per level). Read them with `BspTotems.highestLevel`.

## Steals across the network

With `storage.mode = "mysql"` every running steal is written to the shared `steals` table on `StealStarted` and removed on `StealEnded` (a server also clears its own rows when it starts, so a crash leaves nothing behind, and rows an hour past their expected end are dropped as stale). Each server reads the other servers' rows every `storage.noticeSeconds` and at once on a Redis notice, so `BspTotems.stealsAnywhere` answers from a cache and never touches the database on the server thread. A remote entry's `fractionLeft` is estimated from the start and expected end times; a steal pauses while the thief is outside the radius, so the estimate can run a little behind the real steal. Without network storage the list holds this server's steals only.

Requests from other mods live in their own vault notes (for BSP-Teleport: `BSP-Teleport/Claude Notes/Teleport - Requests for BSP-Core.md`).

## Teams across the network

With `storage.mode = "mysql"` the team record is the shared tables `teams`, `team_members` and `team_messages`; each server keeps a local copy (`bsp_core_teams`), writes every change through and announces it over Redis (kind `team`), and reads a team back when another server announces it. `BspTeams` and `TeamEvent` answer from the local copy, which is a moment behind a change made elsewhere; `TeamEvent`s fire only on the server where the change was made.

## Kits (`BspKits`)

A kit is an admin-made bundle of up to 36 stacks with full NBT, given at first login (per network or per server), redeemed from the players' kit browser, or given as a reward. The rules on a redeem, in the order they are checked and reported one at a time: enabled and in the browser, redeemable on this server, a rank the player has (LuckPerms group names, any of them), not already taken when once-only, the cooldown run out, the price affordable, and the space: a kit is never given to an inventory that cannot take it (the check simulates the insert into the 36 main slots, topping up part stacks first; armour and offhand slots are never used), and the price is charged only after that check. A reward (`give`) skips every rule but the space: the kit becomes owed, is delivered at once when the player is online here and it fits, otherwise waits for their next login or their TAKE IT in the browser, and `KitEvent.Owed` then `KitEvent.Given` say so.

```java
// a rank-up reward
BspKits.give(server, player.getUUID(), "vip_toolkit");
// what the browser would show for it
BspKits.status(player, "vip_toolkit").ifPresent(s -> { if (!s.ready()) log(s.state() + " " + s.number() + " " + s.text()); });
```

**Login order.** Kits are never put into an inventory at login time. What is owed is delivered on BSP-InventorySync's `InventoryRestoredEvent` when that mod is loaded (BSP-Core listens to it by class name, no compile-time dependency; `kit.deliverOnRestore`), otherwise `kit.grantDelayTicks` after the login. A mod that gives kits at login should call `BspKits.give` and leave the delivery to BSP-Core.

**Across the network.** With `storage.mode = "mysql"` the kits are the shared tables `kits`, `kit_items` and `kit_redeems` (with the usual prefix); each server keeps a local copy (`bsp_core_kits`), writes every change through, announces it over Redis (kind `kit`: a kit id, `-id` for a deletion, `r:<uuid>` for a player's records) and reads the fresh copy back. `BspKits` reads the local copy, which is current to within a moment; a player's own records are re-read at their login. Other mods never touch the tables.

**Ranks.** A kit's ranks are LuckPerms group names, read through `compat/LuckPermsBridge` (the Forge edition directly, the Bukkit plugin on Mohist by reflection); with BSP-RankUp loaded the admin's rank picker and the players' cards use `RankUpApi.ladder(server)` read by name, so RankUp needs no change for kits. A `KIT` reward kind on RankUp's side would call `BspKits.give(server, uuid, kitId)`.

## Boards on the Score Screen (`BspBoards`, BSP-Teleport request R8)

Client side. Register once, at client setup or when the mod's data first arrives:

```java
BspBoards.register(new ResourceLocation("bsp_teleport", "home_servers"), Component.literal("HOME SERVERS"),
        () -> List.of(BspBoards.BoardLine.of(Component.literal("BSP-Wild"), Component.literal("12 home · 3 full · 2 renting"), accessTokenStack)));
```

`BoardLine(text, value, icon, colour)`: `text` at the left, `value` at the right (null for none), `icon` an `ItemStack` drawn before the text (`ItemStack.EMPTY` for none), `colour` the value's RGB (0 for the screen's gold). The supplier runs on the render thread while a page shows it (about once a second per screen), so keep it cheap and never block; an exception is logged once and the board shows "Nothing on this board yet". `unregister(id)`, `boards()`, `board(id)`, `lines(id)`. Admins pick a board in a screen's settings (Show: Board, or Rotate with the board among the pages); a screen stores the id as `namespace:path`, so a client without the providing mod sees "Board ... is not installed here" once and that client's rotation then skips it. Nothing is synced by BSP-Core: the lines come from data the registering mod already has on the client.

## Proxy relay: `bsp_core:proxy` (BSP-Velocity Additions, request VA-C2)

With `proxy.relayChat = true` (default false) BSP-Core sends the Velocity proxy every **public** chat line after every listener on this server ran: a `LOWEST`-priority `ServerChatEvent` listener that a cancelled event never reaches (team chat and chat Region Guard blocked are cancelled, so they never go). The message is a vanilla `CustomPayload` (clientbound `ClientboundCustomPayloadPacket`) on channel **`bsp_core:proxy`**, sent through the **sender's own connection**, so the proxy sees it on that player's `ServerConnection`. The proxy must register the channel and mark the `PluginMessageEvent` handled, so the bytes do not travel on to the client (a client that receives them logs an unknown channel and ignores them; nothing breaks). Nothing else is ever sent on the channel today.

Byte layout (Minecraft's `FriendlyByteBuf` encodings: `writeUtf` is a VarInt length followed by UTF-8; `writeUUID` is two big-endian longs):

| # | Field | Type | Meaning |
|---|---|---|---|
| 1 | `version` | byte | `1`. The proxy ignores a version it does not know. |
| 2 | `kind` | Utf (max 32) | `"chat"`. Other kinds may follow later; ignore unknown ones. |
| 3 | `sender` | UUID | the player who spoke |
| 4 | `name` | Utf (max 64) | their profile name |
| 5 | `text` | Utf (max 32767) | the final message as plain text, after any listener's change (the team tag, say) |
| 6 | `json` | Utf (max 32767) | the decorated line the server showed, as a Component JSON string: the chat type's `<Name> text` with any datapack decoration and the message's own styles |
| 7 | `server` | Utf (max 64) | this backend's `storage.serverId`, for the `[Server]` tag the proxy shows staff and writes in its console (the plugin warns once and uses the Velocity server name when the two differ, so keep them the same) |

`BspProxy` in the api: `CHANNEL`, `relayEnabled()`, and `relayChat(player, message)` for a mod that shows public chat another way and wants those lines relayed too (Region Guard's `receive-chat` re-send, if it ever wants them seen across the network). Commands such as `/me`, `/say` and `/tellraw` are not chat events and are not relayed.

## Region Guard

`BspLandOffice.open(player)` opens the Land Office for a player; `BspLandOffice.open(player, x1, z1, x2, z2)` opens it with ground the player outlined (Region Guard calls this when the wand selection is complete), drawn on the map and priced when it lies in an open area. The office lists plots for sale, open areas, the player's own claims and other players' claims within 160 blocks (`claimsNear`) from `RegionGuardApi.get().claims()`, reads the wand outline with `selection(player)` when opened from the block, buys, enlarges and abandons through it, and redraws every open office on `ClaimPurchasedEvent` / `ClaimAbandonedEvent`; the outlined ground is held on the server for the purchase. Needs Region Guard API version 4.

When BSP Region Guard is installed, BSP-Core's Anti Totem checks (placing a totem, dropping one, and the "stands in a totem zone" rule) also ask Region Guard's regions through `RegionGuardApi.get().regions().allows(level, pos, Flags.TOTEM_PLACE / TOTEM_DROP, player)`, and a no from either side refuses. The adapter is `compat.regionguard.RegionGuardZones`; Region Guard is an optional dependency, compiled against its jar in `../BSP-Region Guard/build/libs` and reached only when loaded.
