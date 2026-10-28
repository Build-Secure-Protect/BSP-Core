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
| `TotemEvent.StealStarted` | a steal began; `getThief`, `getThiefName`, `getSeconds`. Also fired for the steal of a set totem in a Totem Forge (`getWhere` is the forge's controller, `getOwner` the pourer), as is `StealEnded`, so the network's `steals` table and the raid rules cover it; `BspTotems.thiefAt` and this server's own `stealsAnywhere` entries look at placed totems only |
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
| `TradeEvent.Completed` | a live trade at a Trade Post was made, after the pieces and items moved: `getFirst` / `getFirstName` (who asked), `getFirstGave` (copies), `getFirstPieces`, and the same for `getSecond` (who accepted) |
| `MarketEvent.Listed` | a player listed a lot on the marketplace, after paying the listing tax: `getLotId`, `getSeller`, `getSellerName`, `getPrice` (pieces, before tax), `getItems` (copies), `getTax` |
| `MarketEvent.Sold` | a lot was bought, after the pieces and items moved: the same, plus `getBuyer`, `getBuyerName` and `getTax` (what the buyer paid on top of the price; the seller paid the same at listing). Both go into the lottery pot, the bank account `Market.POT` |

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

## The marketplace across the network

With `storage.mode = "mysql"` the open lots are the table `market_lots` (one row per open lot: `id`, `seller`, `seller_name`, `price`, `listed_ms`, `ends_ms`, `server_id`, and `data`, the whole lot as SNBT with its items) and the Collect boxes `market_collect` (one row per stack, `uuid` and `item` as SNBT). A row leaves `market_lots` when the lot is bought, cancelled or ended, inside the same transaction that moves its money or its items. A purchase also puts the lot's items into the buyer's `market_collect` rows in that transaction. Emptying a box first marks its rows (`taking` = an attempt id, `taking_ms`), then deletes them once the items are delivered; rows marked for more than ten minutes are unmarked by the minute sweep, and a reader of the boxes should leave marked rows out. `market_attempts` (`id`, `ms`) records every listing by its lot id, and every write of items a server held while the database was away, so a server whose answer was lost can ask whether the change went in; rows older than 30 days are deleted. The lottery pot is the `bank_accounts` row of `Market.POT` (name "Lottery pot"). Every change is announced over Redis with kind `market` (empty payload); servers then read the open lots' ids and fetch only the lots they do not have (lots never change once listed), and do the same every minute. A lot that ends is closed by the server it was listed on (`server_id`), or by any server ten minutes after its end. A Collect box changed by a closing is announced with kind `mbox` (payload: the player's UUID). `MarketEvent`s fire only on the server where the listing or purchase happened. The Portable Banker's market unlock is the `bank_accounts` column `remote_market` (1 when bought), beside the Shopping Network's `remote_shops`; the season reset sets both to 0.

## The daily login reward across the network

`bank.dailyRewardPieces` (default 50, 0 off) is paid once a day per player: at their first login of the day, or at midnight (on the shop clock, `shop.timeZone`) to everyone online. With `storage.mode = "mysql"` the table `daily_rewards` (`uuid` primary key, `day`, the last day paid as days since 1970-01-01 on that clock) is claimed with one conditional `UPDATE ... SET day = ? WHERE uuid = ? AND (day < ? OR day > ? + 1)` on a row made first with `INSERT IGNORE` (no locking read, so no gap locks, under any isolation level), in the same transaction that adds the pieces to `bank_accounts` and writes the `bank_log` row (type `GRANT`, `with_name` "Shatter Bank", `detail` "daily reward"); a missing bank row rolls the claim back. Two servers never pay one day; a day more than one ahead (a clock that ran ahead) does not block the real one. Without network storage the world keeps the last day paid (`bsp_core_daily_reward`). The day is read when the player arrives. A claim that could not be settled (the database away, its answer lost, no bank row yet) is tried again every 30 seconds for that day while the player stays online; trying again is safe. The chat line goes through the network's notices, so it reaches a player who moved to another server. A network server with `shop.timeZone` empty warns at start.

## The lottery across the network

The lottery has no Java API or events for other mods; this is what it keeps and sends, for anyone reading the database or the logs.

**Who played.** Every player seen on the network is a row of `lottery_players` (`uuid` primary key, `name`, `seen_ms`, `ranks`, `op`, `server_id`), written by the server they are on at each login and logout. `seen_ms` only moves forward; `ranks` (the player's LuckPerms groups, lower-cased, comma-separated and sorted) and `op` (1 for a server operator) are as they were at that moment, so a draw can leave out ranks for players who are offline.

**Settings.** The shared state key `lottery_settings` (SNBT: `Days`, 1 to 90, default 7; `LeftOut`, the LuckPerms ranks left out, lower-cased and comma-separated, default `admin,mod,moderator,owner,staff`). A player is in a draw when they are not an operator, hold none of the ranks left out, and were seen within the days. Without network storage the records, the settings and the wins are in the world's saved data (`bsp_core_lottery`).

**The pot** is the `bank_accounts` row of `Market.POT` (name "Lottery pot"). It fills from the market's taxes and the shops' share of each purchase (`shop.lotteryPercent`); admins move pieces into or out of it with `/bsp lottery add|take <pieces> <reason>` or the admin page, logged on the pot as `ADMIN` rows with the admin's name and the reason.

**A draw** is run by the server it is started on (`/bsp lottery draw`, or DRAW on the admin page). It claims the shared state key `lottery_draw` (value `<draw id>@<ms>`; a claim older than two minutes is taken over), reads the players who are in and the pot, and refuses with nobody in or an empty pot before anything shows. It then tells every server over Redis with kind `lottery`, payload `start|<draw>|<pot>|<players>`; each server says in chat that the lottery is being drawn and counts 5 to 1, one a second. At the end of the count the drawing server picks the winner at random among the players who were in at the start (sorted by UUID; the seed is in its log) and pays them the whole pot in one transaction, which also writes both `bank_log` rows and puts the win at the front of the shared state key `lottery_wins` (SNBT, the ten newest: `Id`, `Name`, `Pieces`, `Ms`, `Entrants`, `Draw`). When the payout's answer is lost it reads `lottery_wins` to tell whether it went in. It then sends `won|<draw>|<uuid>|<name>|<pieces>|<players>` or `fail|<draw>|<reason>` (`empty`, `untouched` for surely not paid, `unsure` when even that could not be told, `stopped` when the drawing server stopped during the count), and frees the claim. The `bank_log` rows are of type `LOTTERY`: the pot's (negative, `with_name` the winner, `detail` "draw <id>") and the winner's (positive); a winner who was offline is told at their next login from that row. An operator on the drawing server at the moment of the draw is left out whatever was recorded. Without Redis on a network, only the drawing server shows the countdown.

**Safety.** A server meant to keep its records on the network whose database is not connected neither draws, nor moves the pot, nor changes the settings, nor records players: it never acts on its own old copies. The pot cannot be added to or taken from while a draw is running (a live `lottery_draw` claim). Every server records its online players as seen every five minutes and once while stopping, so a long stay never reads as away; a sighting whose LuckPerms lookup failed keeps the ranks stored before.

**What clients get.**
- `LotterySummaryPacket`, to every player when theirs changes (checked once a second) and at login: the pot, up to 60 names in, the count, the last three winners, the draw's phase on that server and the player's own standing. It feeds the `bsp_core:lottery` Score Screen board (`ClientLottery`; the Score Screen draws it itself, and its `BoardLine`s, the pot, the count and the winners, serve the Side look) and the Portable Banker's lottery card.
- `LotteryPagePacket`, in answer to `LotteryPageAskPacket`, which the Trade Post's LOTTERY tab sends about once a second while it shows (only from a player with a Trade Post screen open): the pot, up to 200 names in, the count, the asker's standing, the last five winners and the draw's phase.
- `LotteryAdminPacket`, in answer to `LotteryAdminActionPacket` (REFRESH, DAYS, RANK, ADD, TAKE, DRAW) from the admin page, operators only (permission level 2): the pot, the settings, the rank names (LuckPerms' loaded groups, the ranks left out and every rank seen), and up to 300 players seen since the season began with their standing.

Who is in and the winners come from a copy each server reads at most every five seconds (and again at once after a draw), shared by all three, so watchers cost no extra database reads.

## Live trades across the network

Trade messages go over Redis with kind `trade`; the payload is the target server id (or `*`) and a space, then an SNBT compound with `t` (presence, request, answer, start, refuse, ended, act, state, prepare, short, escrowed, outcome, cancel). The hand-over uses the tables `trade_escrow` (one row per stack a player put in escrow for an attempt: `attempt`, `owner`, `item` SNBT, `ms`) and `trade_outcomes` (`attempt` primary key, `outcome` "done" or "aborted"); finished trades send their items through `pending_items`. `TradeEvent.Completed` fires on the server that ran the trade (the asker's). Each escrow also writes `e:<attempt>` into `market_attempts` in its transaction, so a server whose escrow answer was lost can tell whether it went in.

## Kits (`BspKits`)

A kit is an admin-made bundle of up to 36 stacks with full NBT, given at first login (per network or per server), redeemed from the players' kit browser, or given as a reward. The rules on a redeem, in the order they are checked and reported one at a time: enabled and in the browser, redeemable on this server, a rank the player has (LuckPerms group names, any of them), not already taken when once-only, the cooldown run out, the price affordable, and the space: a kit is never given to an inventory that cannot take it (the check simulates the insert into the 36 main slots, topping up part stacks first; armour and offhand slots are never used), and the price is charged only after that check. A reward (`give`) skips every rule but the space: the kit becomes owed, is delivered at once when the player is online here and it fits, otherwise waits for their next login or their TAKE IT in the browser, and `KitEvent.Owed` then `KitEvent.Given` say so.

```java
// a rank-up reward
BspKits.give(server, player.getUUID(), "vip_toolkit");
// what the browser would show for it
BspKits.status(player, "vip_toolkit").ifPresent(s -> { if (!s.ready()) log(s.state() + " " + s.number() + " " + s.text()); });
```

**Login order.** Kits are never put into an inventory at login time. What is owed is delivered on BSP-InventorySync's `InventoryRestoredEvent` when that mod is loaded (BSP-Core listens to it by class name, no compile-time dependency; `kit.deliverOnRestore`), otherwise `kit.grantDelayTicks` after the login. A mod that gives kits at login should call `BspKits.give` and leave the delivery to BSP-Core.

**Shop counts across the network.** With `storage.mode = "mysql"` every shop card's stock and sell room are rows of `shop_counts` (with the usual prefix): `shop`, `card` (its place in the shop), `side` (`B` stock, `S` sell room), `who` (empty for everyone, else a player UUID), `left_lots` (-1 full), `at_ms` (the countdown's end, or the refill time or draw it was filled for). A trade holds both rows of a card side in one transaction: an `INSERT ... ON DUPLICATE KEY UPDATE` locks them exclusively from the start, then `SELECT ... FOR UPDATE`; a transaction MySQL ends over a lock (errors 1213, 1205) runs once more, and each connection waits at most 5 s for a lock (`innodb_lock_wait_timeout`). Redis kind `shopcount` tells the other servers what changed: after a trade the payload is `shop|card|B or S|player` and they re-read just that card side's everyone row and that player's row; a payload of the shop id alone (a reset, cards moved, an import, a new draw) has them re-read the shop's rows and its draw (without a lock). The state row `shop_counts_moved` names the server whose counts were moved in at the first start. A rotating shop's draw is a row of `shop_draws`: `shop`, `shown` (card places, comma separated), `used` (no repeats: the places shown so far this cycle), `drawn_at`, `next_at`, `for_shop` (the shop record's edit stamp the draw was made for: a server whose copy of the shop is older takes the draw but never makes one); it is changed under `SELECT ... FOR UPDATE`, and the same `shopcount` notice follows. Other mods never touch the tables.

**Across the network.** With `storage.mode = "mysql"` the kits are the shared tables `kits`, `kit_items` and `kit_redeems` (with the usual prefix); each server keeps a local copy (`bsp_core_kits`), writes every change through, announces it over Redis (kind `kit`: a kit id, `-id` for a deletion, `r:<uuid>` for a player's records) and reads the fresh copy back. `BspKits` reads the local copy, which is current to within a moment; a player's own records are re-read at their login. Other mods never touch the tables.

**Ranks.** A kit's ranks are LuckPerms group names, read through `compat/LuckPermsBridge` (the Forge edition directly, the Bukkit plugin on Mohist by reflection); with BSP-RankUp loaded the admin's rank picker and the players' cards use `RankUpApi.ladder(server)` read by name, so RankUp needs no change for kits. A `KIT` reward kind on RankUp's side would call `BspKits.give(server, uuid, kitId)`.

## Boards on the Score Screen (`BspBoards`, BSP-Teleport request R8)

Client side. Register once, at client setup or when the mod's data first arrives:

```java
BspBoards.register(new ResourceLocation("bsp_teleport", "home_servers"), Component.literal("HOME SERVERS"),
        () -> List.of(BspBoards.BoardLine.of(Component.literal("BSP-Wild"), Component.literal("12 home · 3 full · 2 renting"), accessTokenStack)));
```

`BoardLine(text, value, icon, colour)`: `text` at the left, `value` at the right (null for none), `icon` an `ItemStack` drawn before the text (`ItemStack.EMPTY` for none), `colour` the value's RGB (0 for the screen's gold). The supplier runs on the render thread while a page shows it, at most once a second however many screens show the board (`BspBoards.invalidate(id)` when your data changes shows it at once), so keep it cheap and never block; an exception is logged once and the board shows "Nothing on this board yet". `unregister(id)`, `boards()`, `board(id)`, `lines(id)`. Admins pick a board in a screen's settings (Show: Board, or Rotate with the board among the pages); a screen stores the id as `namespace:path`, so a client without the providing mod sees "Board ... is not installed here" once and that client's rotation then skips it. Nothing is synced by BSP-Core: the lines come from data the registering mod already has on the client.

**Notes (since the Trade Post line).** `BoardLine` has a fifth part, `note` (a `Component`, null for none): a small grey line such as an age ("3 h ago"), drawn under the text in the Tiles look and just before the value in the Ledger and Side looks. Lines made as before have no note: `new BoardLine(text, value, icon, colour)` and both `BoardLine.of(...)` still work, and a mod compiled against the four-part record keeps working; add a note with `line.withNote(Component)` or the five-part constructor. In the Tiles look, when the tiles would have to shrink below what their contents need, they keep that size and scroll up at the screen's speed instead.

**BSP-Core's own board** is `bsp_core:market`, "Market Board": the newest open lots of the marketplace (at most 60, newest first), each with its first item as the icon, its count and name ("+2 more" for a lot of three stacks), the price in pieces as the value and its age as the note. Its data comes from the server: every second the server checks the newest lots and, when they changed, sends them to every client (and to each player at login). Picking the Market Board in a screen's settings sets the look to Tiles; the admin can change it.

## The network connection settings

The database and Redis connection (host, port, database, user, password, table prefix, SSL; Redis host, port, password, TLS, db) is in `config/bsp_core-network.toml`, a COMMON config that is never sent to clients (the SERVER config `bsp_core-server.toml` is sent to every player who joins). `storage.mode`, `storage.serverId`, `storage.redis.enabled` and `storage.redis.channel` stay in the server config. The class is `storage.NetworkConfig`. For BSP-InventorySync and BSP-Server Utilities, which read them by reflection, `BSPConfig.STORAGE_HOST`, `STORAGE_PORT`, `STORAGE_DATABASE`, `STORAGE_USER`, `STORAGE_PASSWORD`, `STORAGE_SSL`, `STORAGE_TABLE_PREFIX`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL` and `REDIS_DB` still exist and now read the network file (`STORAGE_SERVER_ID`, `REDIS_ENABLED` and `REDIS_CHANNEL` are still the server config's own); they are not an API, and another mod should ask for one rather than reading them.

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

## Shop import file (format 1)

`/bsp shop import <file> [test]` reads `config/bsp_core/shops/<file>.json` (letters, digits, `_`, `-`, `.` only; no folders). All or nothing: one error refuses the file. A shop whose id is on the server is replaced whole: when its cards are the same items (with the same data, lot size and section) in the same places, its counts and draw stay and its change time keeps its date, and only a card whose limits, refill rules or match rule changed starts its counts again; otherwise its counts and draw start again. Shops not named are untouched; nothing is deleted. Names hold up to 64 characters, a requirement's reason 64 and its permission 128. The full report is written to `<file>-report.txt` beside it.

```json
{
  "format": 1,
  "allowCoinPayouts": false,
  "shops": [
    {
      "id": "spawn_challenge", "name": "Challenge", "kind": "rotating", "parent": "", "remote": true, "buyOnly": false,
      "style": "cash_machine", "columns": 0, "rows": 3, "hours": {"mode": "always"},
      "displaySlots": 4, "intervalHours": 168, "rotateAt": {"every": "monday", "at": "00:00"}, "noRepeat": true, "broadcast": true,
      "pages": [[
        {"item": "mekanism:steel_casing", "count": 1, "sell": 2500, "currency": "pieces",
         "sellGlobal": 200, "sellPlayer": 4, "sellRefill": "rotation", "match": "item", "weight": 1,
         "lore": ["The Challenge buys up to 4 each per week."], "note": "for people; ignored"}
      ]]
    }
  ]
}
```

**Shop keys** (all but `id` optional; a missing key takes the value a new shop has): `id` (`[a-z0-9_]{1,32}`), `name`, `kind` (`shop`, `sub`, `rotating`, `offer`), `parent` (a sub-shop's parent, in the file or on the server; parents may not go round in a loop or more than 16 deep), `remote`, `buyOnly`, `style` (`cash_machine`, `passbook`, `market_board`), `columns` (0 automatic, or 4 to 7), `rows` (2 or 3), `hours` (`{"mode": "always"}`, `{"mode": "real", "open": "09:00", "close": "21:00"}`, `{"mode": "game", "open": 0, "close": 12000}`), `displaySlots`, `intervalHours`, `rotateAt` (a day or weekday time), `noRepeat`, `broadcast`, `offerPercent`, `offerEnd` (`2026-12-31T23:59` in `shop.timeZone`, or empty), `requires`, `pages` (lists of up to 21 cards), `note` (ignored).

**Card keys**: `item` (registry id), `count` (lot size, 1 to the stack size), `nbt` (SNBT), `buy` and `sell` (-1 or absent for off), `currency` (`pieces`, `copper`, `gold`, `diamond`, `netherite`, `illyrium`; a `sell` in coins needs `allowCoinPayouts`), `stockGlobal`, `stockPlayer`, `sellGlobal`, `sellPlayer` (lots, -1 none), `restockMinutes` (the old form of `stockRefill: {"after": N}`), `stockRefill`, `stockPlayerRefill`, `sellRefill`, `sellPlayerRefill`, `instant`, `hidden`, `section` (a sub-shop's id: the card opens it and `item` is its picture), `match` (`exact` or `item`), `weight` (0 to 100), `lore` (up to three lines of 60 characters), `requires`, `note` (ignored).

**`requires`** (on a shop or a card): `{"permission": "bsp.shop.gold", "playtimeHours": 10, "whenLocked": "show", "reason": "Gold rank and up"}`, every key optional. The permission is asked of LuckPerms (any node; without LuckPerms only operators pass); playtime is BSP-RankUp's minutes when it is installed, else the server's Time Played. `show` greys the card with what it needs; `hide` leaves it off the player's page (a shop: off their Shopping Network list, and it does not open for them). A shop's requirement covers its sub-sections and theirs, and the section card that opens a locked or hidden shop is locked or hidden with it. A card or shop that hides from anyone never shows on a stall's counter or a Stall Display.

**Refill and rotation rules**: an object, `{"after": 720}` (minutes from running out), `{"every": "day", "at": "06:00"}`, `{"every": "monday", "at": "00:00"}`, or text: `"rotation"` (a rotating shop's cards only), `"same"` (per-player rules: follow the shop-wide one; the default), `"never"`, `"12h"`, `"90m"`, `"2d"`, `"day 06:00"`, `"monday 00:00"`.

A key this version does not know is a warning, not an error, so a file written for a later version still imports.

`/bsp shop export <id|all> <file> [overwrite]` writes the same format with every key (rules as objects; `"never"`, `"same"` and `"rotation"` as text; `rotateAt` only when set; `allowCoinPayouts: true` when a card pays out coins). One shop is written with its sub-sections (shops whose parent chain leads to it). Counts and draws are never written. An existing file is kept unless `overwrite` is given.

## Shop report and the bank log

Buy and sell rows of the bank log name the shop and the item: `BankTransaction.shopId()` and `itemId()` (empty on rows from before, and on rows that are not shop trades); on a network the `bank_log` table has the columns `shop_id` and `item_id`, added at start-up to a table made before them. `/bsp shop report [days] [shop]` (admins, console) adds them up and writes `config/bsp_core/shops/report-<days>d-<date>[-shop].csv` with the columns `shop, shop_name, item, items_bought, items_sold, pieces_in, pieces_out, net_out, coins_in, coins_out`.

The market and the Trade Post write their own row types (`bank_log.type`): `MARKET` for a lot bought or sold (the buyer's row is the price and the purchase tax, negative; the seller's the price, positive; `with_name` the other player, `detail` the lot), `LOTTERY` for a lottery payout (on the pot and the winner), `TAX` for the seller's listing tax and for the lottery pot's own rows (both taxes coming in, and the shops' share of each purchase, `shop.lotteryPercent`, with `with_name` the buyer and `detail` "shop share: ..."), `PAID` / `RECEIVED` for the pieces of a live trade, and `FEE` for the Portable Banker's market unlock ("Market unlocked", paid in coins: `coin_tier`, `coin_count`). The shop report leaves these rows out.

