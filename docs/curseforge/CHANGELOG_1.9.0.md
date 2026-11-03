# BSP-Core 1.9.0

Got an item you really need but nobody is selling it? Now you can ask for it. **Buy orders** are here: set the price per item and how many you want at any Trade Post, and watch them roll in over time from anyone on the network. Score Screens get a **Wanted Board** to show them off. This file also covers 1.8.1 to 1.8.3: a network-wide admin panel, team invites between servers, scores an admin can set by hand, and a big round of fixes to how totems move between servers.

**Existing worlds:** nothing to do by hand. On a network the new tables (`market_orders`, `score_adjust`) are added by themselves. **Every player needs 1.9.0 as well as the servers**: update the pack and the servers together. An older client is told to update when it joins.

## For players: buy orders

- **WANTED on the market.** On a Trade Post's MARKET page, press **WANTED** beside FOR SALE. Every buy order is a card: the item, who wants it, the price each, how many are still to go and a bar of how much has been sent. The ones you can fill from your inventory light up and say how many you have.
- **Fill one.** Open a card, choose how many to **SEND** (shift steps a whole stack) and press **FILL**. The items leave your inventory and you are paid at once, less the usual 5% market tax, which goes into the lottery pot. Whatever you are wearing is never taken.
- **Place one.** The first card, **+ NEW ORDER**, finds any item by name. Set how many you want (up to 9 stacks) and the price each, and press **PLACE ORDER**. The whole price comes out of your bank straight away and is held for the order, so sellers always get paid.
- **It fills over time.** Anyone on any server can send part of it. Everything sent waits in your **Collect** box at any Trade Post, and you get a message each time, even on another server or when you next log in.
- **Closing it.** An order lasts 7 days. Open your own order to **CANCEL ORDER**. When it is cancelled or runs out, the pieces held for whatever was not sent go straight back to your bank.
- **Plain items only.** An order takes the plain item: no enchantments, no damage, no custom name. Shatter Totems and Totem Cores can never be ordered.
- **Up to 10 orders** open at a time. Sort by newest, best pay or the ones you can fill, and search by item or buyer.
- **The Wanted Board** for Score Screens shows the open orders, best pay first, with how many each still needs. Put it on one screen and the Market Board on the next, and spawn shows both what is for sale and what people want.
- It is on the **Portable Banker's** market too, and the guide book has a **Buy Orders** entry under Shatter Coins.

## For players: also new since 1.8.0

- **Team invites reach the whole network.** The Team screen's INVITE list shows everyone online on any server who is not in a team, with their server's name, and the invite reaches them wherever they are: the card, the chime and the chat line with Accept and Decline.
- **Cleaner Trade Post cards.** Your own lots say YOURS in full, and the search row has room for the new WANTED key.

## For server owners

- **The admin panel shows the whole network.** Every server lists every player who has had a first totem anywhere, with all of their totems on every server, each marked with its server's name. TP is offered for totems on the server you are on.
- **Set a player's score by hand.** In the admin panel's Players tab, type a score under a player's name and press SET (CLEAR takes it off), or use `/bsp score set <player> <points>`, `/bsp score add <player> <points>` and `/bsp score clear <player>`, for online or offline players. It is stored as a difference from what their totems give, so it keeps moving when they take, lose or raise a totem, and it never goes below 0. A violet star marks players with a score set by hand. Every one is cleared when a season ends, and every change is in the server log with who made it. Handy for fixing scores after a bug.
- **Deleting a totem in creative mode is permanent.** Put a totem in the creative bin and it is gone for good on every server: any other copy of it turns into a dead husk. Putting the same copy straight back off your cursor still works. Breaking a placed totem in creative with a full inventory drops it at the block instead of sending it home to its owner.
- **Clearer husk messages.** A dead copy of a totem says whose the real one is ("the real totem is Brindlewick's"), and the server log says the same.
- **Buy order settings and commands.** `market.maxOrdersPerPlayer` (10 by default); orders last `market.listingDays` and pay the `market.taxPercent` tax. `/bsp market orders [player]` lists the open orders with their ids, `/bsp market cancelorder <id>` cancels one and gives the buyer back what is held, and `/bsp market expire` closes overdue orders too.
- **`/bsp totem check`** checks every placed totem the server knows of, loading the chunks, and forgets any whose block is gone (WorldEdit, a region reset, deleted chunks). It also runs by itself once a minute for loaded chunks.

## Fixes

- **Duplicated totems.** Placing a totem on a busy server could hand the player a second one. Placed and dropped totems are now recorded the moment they land. Stale copies turn into husks wherever they turn up, two copies of the same totem in one inventory leave one, and a totem that is gone stays gone.
- **Totems on the wrong server.** A totem on its way to another server could be placed back on a server the player had left days before. Each trip's totem now only ever goes back to its own server.
- **Totems that are not there no longer count** towards scores or the admin panel.
- **A placed totem removed by a command or world edit** goes back to its owner instead of being destroyed.
- **Logout placements and the last position** are kept per server, so one server never acts on another's record.
- **Deposit boxes on a network** kept their items: a balance change could empty the server's copy of a box.
- **Nothing the network keeps is decided while the database is not connected:** the bank, the market and buy orders, first totems and kits wait until it connects, and a server that cannot connect tries again every 30 seconds.
- **Bank changes are all or nothing,** so a lost answer can never refund coins twice or charge for an item never given. The same holds for every buy order: two players filling one order at the same moment on two servers never send more than it wants.
- **The first totem is never lost** to a crash or a disconnect, and comes after BSP-InventorySync's restore.
- **Totems kept through a death, dropped totems surviving `/kill` and entity cleaners,** and totems in transit kept when the database fails.
- **Teams on a network** no longer drop a member or bring back a removed one when two servers change the team at once.
- **The leaderboard keeps its rows** when a sync fails instead of briefly showing everyone else's points as lost.
- **No totem counts as lost** while a player's inventory is still being restored at login.
