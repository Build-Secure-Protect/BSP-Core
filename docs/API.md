# BSP-Core API for other mods

Package `com.mrgregles.bsp_core.api`. Compile against the BSP-Core jar; keep every call in one adapter class on your side. These classes are the only ones meant for other mods; anything else in the jar may change without notice. Nothing here is final until BSP-Core 1.1.

| Class | What it offers |
|---|---|
| `BspBank` | A player's Shatter Pieces: `balance`, `charge` and `credit` (callback on the server thread), `format`. |
| `BspTotems` | `placed(server, owner)` the owner's placed totems as `GlobalPos`; `isMain(level, pos)`; `totemAt(server, where)`; `beingStolen(server, owner)` with the thief and the fraction of the steal left; `upgrade(name)` to look an upgrade up by name; `highestLevel(player, buff)` / `highestLevel(server, uuid, buff)` across placed and carried totems; `allPlaced(server)` and `placedNear(level, pos, radius)` as `Placed(owner, where)`; `stealRadius()`; `isTotem(blockState)`; `isTotem(itemStack)` a live Shatter Totem item (not a spent husk) and `giveBack(serverPlayer, stack)` hands a totem back without ever dropping it (both for inventory sync mods, see below); `thiefAt(level, pos)`; `stealsAnywhere(server)` / `stealsAnywhere(server, owner)` every steal running on this server and, with network storage, on the other servers as `StealAnywhere(serverId, local, dimension, pos, owner, thief, thiefName, fractionLeft)`. `thiefAt`, `beingStolen` and `stealsAnywhere` never load a chunk (since 1.4.5, BSP-Teleport request R10): a steal needs a thief standing there, so a totem in an unloaded chunk has none. `highestLevel` reads placed totems' levels from the ledger without loading their chunks (a totem whose levels were never kept, one not loaded since updating, is read from its block once). `forgetPlayer(server, uuid)` (since 1.4.5, BSP-Stress Test request ST-C1) leaves nothing of a player's totems and gives nothing back, online or offline; returns how many totems it took; the command is `/bsp totem forget <player>`. |
| `BspCoins` | (Since 1.4.3.) Shatter Coins carried as one balance in copper units: `tiers()` (keys, cheapest first), `value(tier)`, `worth(player)`, `pay(player, value)` (smallest coins first, change back; false and nothing taken when short), `give(player, value)` (largest first). Inventory coins only; the bank is `BspBank`. |
| `BspStorage` | (Since 1.4.5.) The JDBC driver BSP-Core uses (BSP-Teleport request R9): `jdbcDriver()` a new instance as `Optional<java.sql.Driver>` (the MySQL driver bundled, relocated, in the full jar; the server's own MySQL driver with the slim jar; else MariaDB's; empty when none), `jdbcDriverClass()` its class name, `jdbcUrlPrefix()` the URL scheme it takes (`jdbc:mysql://`, or `jdbc:mariadb://`). Connect with `driver.connect(url, props)`: `DriverManager` refuses drivers loaded by a mod class loader. Never name the relocated class yourself; it may move. |
| `BspScores` | (Since 1.4.5.) The leaderboard (BSP-RankUp request RU-R1): `score(server, uuid)` as `Score(player, name, rank, points, totems, bestTier)`, `points`, `totemsHeld`, `bestTier` (1 = Tier I to 5 = Tier V, 0 = none), `board(server)` best first. Points are the sum of the player's totems' tier points (`scoring.tierPoints`); a totem counts for its owner while placed and its carrier while carried; on a network the board is every server's combined. Read from the last board: cheap to call. Rebuilt every `scoring.refreshSeconds` (30), on a single server within a second of a change. |
| `BspPlayers` | `isAdmin`, `isModerator`; Shatter Coins in hand: `coinValue`, `payCoins`, `giveCoins` (values in Copper); `isDimensionAllowed`. |
| `BspZones` | `isNoTotemServer()` / `isNoTotemServer(serverId)` / `noTotemServers()` (since 1.4.3): servers listed in `restrictions.noTotemServers` allow no totem placement anywhere (holding is allowed; `BspPlayers.isDimensionAllowed` is false for every world there). `isBlocked(level, pos, key)` for Anti Totem zone rules; keys `TOTEM_PLACE`, `TOTEM_DROP`, and for a teleport mod to enforce `TELEPORT_IN`, `TELEPORT_OUT`, `SET_HOME` (admins switch them on per zone in the Anti Totem block's Rules tab; off on a new block). |
| `BspTransfer` | For server transfers (`handBack(player, items, messageKey)` returns items to a player the way an arriving transfer does: totems recorded as arrived and given back without ever being dropped, the rest added or dropped; `messageKey` may be null for no message): `beginTransfer(player)` just before sending them away takes their carried totems and stores them for the journey (the arrival server hands them back) and tells the player; `endTransfer(player)` when the teleport is cancelled hands them straight back; `isTransferring(uuid)`. A transfer that does not happen within `restrictions.transferSeconds` (10) returns the totems by itself; a stored totem whose owner never logs in anywhere is placed back at the departure point after `restrictions.transferTotemTimeoutHours` (48). Without a shared database the totems are held in memory and placed at the departure point on a real logout, as usual. |
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
| `ScoreEvent.Changed` | (Since 1.4.5.) a rebuilt leaderboard gives a player different points than the last: `getPlayer`, `getName`, `getPoints` (0 when their last totem left the board), `getDelta`, `getTotems`, `getBestTier`; none for the first board after a start |
| `KitEvent.Given` | a kit's items landed in a player's inventory; `getPlayer`, `getKitId`, `getKitName`, `getHow()` is `FIRST_JOIN`, `REDEEMED`, `REWARD`, `ADMIN` or `TEST` (TEST keeps no record) |
| `KitEvent.Owed` | a kit became owed to a player (a reward, an admin's gift or a first login that did not fit, or they were offline); it is delivered when it fits and they are settled in, or from the WAITING card in their browser |

Upgrade names for `BspTotems.upgrade` are the constants of `TotemUpgrades.Buff` (for example `SWIFTNESS`, `RECALL`); the enum itself may be used directly.

Two upgrades exist only for a teleport mod to read; BSP-Core sells them on the tree (Home spoke, Carried prices at half XP, three levels each) and does nothing with them itself: `EXTRA_HOMES` (meant as one more home slot per level) and `SWIFT_TRAVEL` (meant as 25 % off the teleport cooldown per level). Read them with `BspTotems.highestLevel`.

## Inventory sync mods (`BspTotems.isTotem(ItemStack)`, `BspTotems.giveBack`; BSP-InventorySync requests R1 and R2)

A mod that saves a player's inventory on one server and restores it on another must leave Shatter Totems to BSP-Core, or a totem is duplicated: one in the world (or waiting in BSP-Core's records for the player's return) and one in the synced inventory.

- `public static boolean isTotem(ItemStack stack)`: true for a live Shatter Totem, `bsp_core:shatter_totem` that is not a spent husk (a totem not yet given its identity counts as live). False for a husk, any other item and the empty stack. The same rule BSP-Core applies to every inventory. **A snapshot must never carry a stack this says yes to**: drop them before saving.
- `public static void giveBack(ServerPlayer player, ItemStack stack)`: puts the totem into the selected hotbar slot when it is empty, else the first free slot; the totem itself is never dropped. When the inventory is full, the item in the selected slot (or, if that holds a totem, the first slot that does not) is dropped at the player's feet to make room; when every slot holds a totem, the totem goes on the cursor. Server thread only. **A restore hands totems back through this call**: live totems found in an inventory that a restore is about to replace go back to the player through BSP-Core.

Both are plain static methods with exactly these signatures, so a mod may find them with `MethodHandles.publicLookup().findStatic` instead of compiling against BSP-Core. Since BSP-Core 1.4.1; the 1.4.0 jar and older have neither, so check `ModList` for version 1.4.1 or fall back when the lookup fails.

Logins wait for the restore (BSP-InventorySync request R3, since 1.4.1): when `bsp_inventorysync` is loaded and `storage.deliverOnRestore` is on, BSP-Core listens for `com.mrgregles.bsp_inventorysync.api.event.InventoryRestoredEvent` (by class name, like the kits) and only then strips coins from an older epoch, runs the season check and hands over the items owed to the player, locally and from the shared `pending_items` table. A sync mod should post that event once per login on the server thread, after its restore and after handing live totems back through `giveBack`. Until it arrives, BSP-Core leaves the player's `pending_items` rows in the database; after `storage.restoreWaitSeconds` (30) it settles the login anyway. Kits already wait for the same event (`kit.deliverOnRestore`). A login is never settled on a dead player (request R4): a player who logs in on the death screen is settled at the respawn, on the respawned entity; when they are also waiting for the restore, the countdown stands still until the respawn and the restore event, posted after it, releases them.

## Servers without totem placement (`BspZones.isNoTotemServer`)

Since BSP-Core 1.4.3. `restrictions.noTotemServers` lists servers by `storage.serverId`; keep the same list on every server. On a listed server a Shatter Totem cannot be placed or dropped anywhere, but may be held, and `BspPlayers.isDimensionAllowed` answers false for every world (so a region mod that treats "worlds where totems live" as PvP worlds sees none there). `BspZones.isNoTotemServer()` (this server), `isNoTotemServer(serverId)` and `noTotemServers()` let another mod show or use the rule. A totem carried at logout on a listed server goes back to the player's previous spot on a server that allows placement: with `storage.mode = "mysql"` those spots live in the shared table `totem_spots` (one row per player: server, world, block), and the request to the server that places the totem back is a row in `totem_returns` announced with the Redis kind `totem_return` (payload: the target server's id).

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

## Discord alerts: `bsp_core:discord` on Redis (for the Discord bot)

With `discord.enabled = true` (default false; never in a single-player or LAN world) BSP-Core publishes one JSON object per alert on the Redis channel **`discord.redisChannel`** (default `bsp_core:discord`), through the same Redis link as network storage (so it needs `storage.mode = "mysql"` and `storage.redis.enabled`). The channel is separate from the storage channel, so the bot's Redis user can be allowed this channel only. The message is the bare JSON, with no `kind:server:` prefix. Pub/sub keeps nothing: an alert sent while the bot is down is lost. The game servers never hold a Discord token or a Discord id: the bot looks the player up by Minecraft UUID in its own links.

When: at the moment the game warns the owner of a steal (after the thief's Shroud), the server where the totem stands sends a `channel` alert for every steal of an owned totem, and a `warning` to the owner when the totem has Herald; at most one `warning` per player every 30 s (other totems raided in that window get the public line only). When that steal ends, an owner who got the `warning` gets one `saved` or `stolen`. Unclaimed totems send nothing.

| Field | In | Meaning |
|---|---|---|
| `v` | all | `1`. Ignore a version you do not know. |
| `type` | all | `warning`, `saved`, `stolen` or `channel`. Ignore unknown types. |
| `server` | all | `discord.serverName`, else `storage.serverId` |
| `sentAt` | all | epoch milliseconds |
| `player` | not `channel` | the owner's Minecraft UUID |
| `world`, `x`, `y`, `z` | not `channel` | the totem's dimension id and block position |
| `thief` | not `channel` | the thief's profile name |
| `secondsLeft` | `warning` | seconds of the steal left when it was sent (it ends about `secondsLeft` seconds later, a little later when the thief steps away). The bot counts that from when it receives the alert, not from `sentAt`, so clocks that differ between machines never drop a live warning; it drops an outcome or a channel line 60 s after it arrived. |
| `reason` | `saved` | `left` (the thief stayed away too long), `interrupted` (the thief logged out, died or changed world), `totem_gone` (the totem was taken down), `cancelled` (an operator stopped it), `ended` (anything else) |

A `channel` alert holds exactly `v`, `type`, `server` and `sentAt`: the public line names the server and nothing else.

Example: `{"v":1,"type":"warning","server":"Frostwarf","sentAt":1761419040000,"player":"0f3c...","world":"minecraft:overworld","x":120,"y":64,"z":-340,"thief":"Steve","secondsLeft":45}`

**Linking (the bot's side of `/bsp discord`).** All keys sit under the channel's name, so the bot's Redis user needs only `&bsp_core:discord` and `~bsp_core:discord:*`:

| Key or message | Written by | Meaning |
|---|---|---|
| `<channel>:code:<CODE>` | BSP-Core, `SET ... EX 600` | a code waiting for `/link CODE` in Discord. Value `{"v":1,"player":"<uuid>","name":"<profile name>","server":"<server>","expiresAt":<ms>}`. Six characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789`; a player's new code deletes their last. The bot takes it with `GETDEL` (single use) and links the Discord user who ran the command to `player`. |
| `<channel>:linked:<uuid>` | the bot | set (any value, no Discord id in it) while the player is linked; BSP-Core only checks `EXISTS` for `/bsp discord status` and Herald's card. |
| `<channel>:undelivered:<uuid>` | the bot (suggested `EX 604800`) | a direct message to the player could not be delivered (Discord error 50007 or 403). BSP-Core deletes it at the player's next login (`DEL`, any Redis version) and, if it was there, tells them once to allow DMs from the BSP server. |
| `{"v":1,"type":"unlink","player":"<uuid>","sentAt":<ms>}` on the channel | BSP-Core | the player unlinked in game: BSP-Core has already deleted the `linked` key; the bot deletes its own link. |

The Discord user id stays with the bot. BSP-Core shows a player only "linked" or "not linked". The bot's `GETDEL` needs Redis 6.2 or newer; BSP-Core's own calls (`SET`, `DEL`, `EXISTS`, `PUBLISH`) work on any version.

## Region Guard

`BspLandOffice.open(player)` opens the Land Office for a player; `BspLandOffice.open(player, x1, z1, x2, z2)` opens it with ground the player outlined (Region Guard calls this when the wand selection is complete), drawn on the map and priced when it lies in an open area. The office lists plots for sale, open areas, the player's own claims and other players' claims within 160 blocks (`claimsNear`) from `RegionGuardApi.get().claims()`, reads the wand outline with `selection(player)` when opened from the block, buys, enlarges and abandons through it, and redraws every open office on `ClaimPurchasedEvent` / `ClaimAbandonedEvent`; the outlined ground is held on the server for the purchase. Needs Region Guard API version 4. When Region Guard prices claims in Shatter Coins (`claims().paysInCoins()`, API 6), the office shows `claims().priceText(level, id, amount)` for prices, the cost per block and the quote, and the coins the player carries instead of the bank balance; an older Region Guard keeps the pieces display.

When BSP Region Guard is installed, BSP-Core's Anti Totem checks (placing a totem, dropping one, and the "stands in a totem zone" rule) also ask Region Guard's regions through `RegionGuardApi.get().regions().allows(level, pos, Flags.TOTEM_PLACE / TOTEM_DROP, player)`, and a no from either side refuses. The adapter is `compat.regionguard.RegionGuardZones`; Region Guard is an optional dependency, compiled against its jar in `../BSP-Region Guard/build/libs` and reached only when loaded.
