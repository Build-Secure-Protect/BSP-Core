# BSP-Core API for other mods

Package `com.mrgregles.bsp_core.api`. Compile against the BSP-Core jar; keep every call in one adapter class on your side. These classes are the only ones meant for other mods; anything else in the jar may change without notice. Nothing here is final until BSP-Core 1.1.

| Class | What it offers |
|---|---|
| `BspBank` | A player's Shatter Pieces: `balance`, `charge` and `credit` (callback on the server thread), `format`. |
| `BspTotems` | `placed(server, owner)` the owner's placed totems as `GlobalPos`; `isMain(level, pos)`; `totemAt(server, where)`; `beingStolen(server, owner)` with the thief and the fraction of the steal left; `upgrade(name)` to look an upgrade up by name; `highestLevel(player, buff)` / `highestLevel(server, uuid, buff)` across placed and carried totems; `allPlaced(server)` and `placedNear(level, pos, radius)` as `Placed(owner, where)`; `stealRadius()`; `isTotem(blockState)`; `thiefAt(level, pos)`. |
| `BspPlayers` | `isAdmin`, `isModerator`; Shatter Coins in hand: `coinValue`, `payCoins`, `giveCoins` (values in Copper); `isDimensionAllowed`. |
| `BspZones` | `isBlocked(level, pos, key)` for Anti Totem zone rules; keys `TOTEM_PLACE`, `TOTEM_DROP`, and for a teleport mod to enforce `TELEPORT_IN`, `TELEPORT_OUT`, `SET_HOME` (admins switch them on per zone in the Anti Totem block's Rules tab; off on a new block). |
| `BspTransfer` | For server transfers: `beginTransfer(player)` just before sending them away takes their carried totems and stores them for the journey (the arrival server hands them back) and tells the player; `endTransfer(player)` when the teleport is cancelled hands them straight back; `isTransferring(uuid)`. A transfer that does not happen within `restrictions.transferSeconds` (10) returns the totems by itself; a stored totem whose owner never logs in anywhere is placed back at the departure point after `restrictions.transferTotemTimeoutHours` (48). Without a shared database the totems are held in memory and placed at the departure point on a real logout, as usual. |

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

Upgrade names for `BspTotems.upgrade` are the constants of `TotemUpgrades.Buff` (for example `SWIFTNESS`, `RECALL`); the enum itself may be used directly.

Two upgrades exist only for a teleport mod to read; BSP-Core sells them on the tree (Home spoke, Carried prices at half XP, three levels each) and does nothing with them itself: `EXTRA_HOMES` (meant as one more home slot per level) and `SWIFT_TRAVEL` (meant as 25 % off the teleport cooldown per level). Read them with `BspTotems.highestLevel`.

Requests from other mods live in their own vault notes (for BSP-Teleport: `BSP-Teleport/Claude Notes/Teleport - Requests for BSP-Core.md`).

## Region Guard

`BspLandOffice.open(player)` opens the Land Office for a player; `BspLandOffice.open(player, x1, z1, x2, z2)` opens it with ground the player outlined (Region Guard calls this when the wand selection is complete), drawn on the map and priced when it lies in an open area. The office lists plots for sale, open areas, the player's own claims and other players' claims within 160 blocks (`claimsNear`) from `RegionGuardApi.get().claims()`, reads the wand outline with `selection(player)` when opened from the block, buys, enlarges and abandons through it, and redraws every open office on `ClaimPurchasedEvent` / `ClaimAbandonedEvent`; the outlined ground is held on the server for the purchase. Needs Region Guard API version 4.

When BSP Region Guard is installed, BSP-Core's Anti Totem checks (placing a totem, dropping one, and the "stands in a totem zone" rule) also ask Region Guard's regions through `RegionGuardApi.get().regions().allows(level, pos, Flags.TOTEM_PLACE / TOTEM_DROP, player)`, and a no from either side refuses. The adapter is `compat.regionguard.RegionGuardZones`; Region Guard is an optional dependency, compiled against its jar in `../BSP-Region Guard/build/libs` and reached only when loaded.
