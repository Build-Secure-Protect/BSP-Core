# BSP-Core 1.4.3

Everything since 1.4.0 (1.4.1, 1.4.2 and 1.4.3): servers where totems cannot be placed, land for Shatter Coins, logins that wait until your inventory has arrived and you are alive, and the hooks the other BSP mods asked for.

**Existing worlds:** new settings are added to `serverconfig/bsp_core-server.toml` by themselves (`restrictions.noTotemServers`, `storage.deliverOnRestore`, `storage.restoreWaitSeconds`). Nothing that exists changes its value, and nothing behaves differently until you list a server in `noTotemServers`. Servers on a shared database get two new tables at start (`totem_spots`, `totem_returns`).

## Servers without totem placement

- **List your safe servers** (spawn, a saferoom, a sanctuary) by their `storage.serverId` in `restrictions.noTotemServers`, the same list on every server. On a listed server a Shatter Totem cannot be placed or dropped anywhere, in any world.
- **You may still carry your totem there.** Holding one, you are told once: "Shatter Totems cannot be placed down anywhere on this server. If you log out, it will go back to your previous position on a server where totem placement is allowed."
- **Log out there with a totem and it goes back** to the spot where you last stood on a server that allows placement, and is placed there, so nobody can park a totem out of reach by logging off on a safe server. You are told where it stands at your next login. If you log in somewhere before that, you simply have it in your hand. A player who has never stood on a server that allows totems gets it back in their inventory at their next login.
- **Dying there** keeps your totem with you through the death. A totem lying on the ground there goes to its owner instead of placing itself.
- Totems already standing on a listed server stay where they are, and the server lists them in its log at start. `/bsp totem give`, `reset` and `wipe` still work there, with a warning.

## Land for Shatter Coins

- **When BSP Region Guard sells land for Shatter Coins**, the Land Office shows every price in coins as Region Guard words them ("3 gold coins"): a plot's price, an open area's cost per block and the quote for the ground you outlined. The foot of the screen shows the coins you carry instead of your bank balance. You pay from your inventory and get change back; the bank is not touched. Sold for pieces, the office looks as it always has.

## Safe logins on a network

- **Your inventory first.** With BSP-InventorySync installed, everything a login hands you waits until your inventory has been restored: coins from an older season are taken, the season check gives you your fresh totem, and items owed to you (prizes, deliveries, a totem from a transfer) arrive in the restored inventory instead of being overwritten by it. If no restore comes within 30 seconds (`storage.restoreWaitSeconds`), you get them anyway.
- **Never on the death screen.** Log back in dead and nothing is handed to the body you left behind: owed items, kits, returning totems and the totem your logout placed all wait for your respawn and arrive in your new inventory, once.
- **A totem kept for you arrives like a transferred one.** In a full inventory it takes a slot and the stack it moves lands at your feet; the totem itself is never dropped, and it is never mistaken for a stale copy afterwards.

## For the other BSP mods

- `BspTotems.isTotem(ItemStack)` and `BspTotems.giveBack(ServerPlayer, ItemStack)`: what an inventory sync mod needs to keep live totems out of snapshots and hand them back through BSP-Core.
- `BspCoins`: the coins a player carries as one balance (`worth`, `pay` with change, `give`, the tiers and their values), for BSP Region Guard's coin prices.
- `BspZones.isNoTotemServer()` and `noTotemServers()`: which servers allow no totem placement.
- BSP-InventorySync's restore event is honoured (above); BSP-Core builds against BSP Region Guard 0.4.0.

## Fixes

- Items handed out at login could be wiped by an inventory sync that restored the inventory a moment later; they now arrive after the restore.
- A player who logged in on the death screen could lose owed items, kits or a returning totem into the inventory of the body they left behind.
- A totem kept for a player and handed over later could land on the ground in a full inventory.
