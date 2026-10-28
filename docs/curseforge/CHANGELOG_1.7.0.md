# BSP-Core 1.7.0

Players can finally trade with each other. Admins place a Trade Post at spawn: a desk with a screen, scales and four labelled keys. There you list lots on a market shared by every server, buy what others listed, and trade face to face with anyone at a Trade Post anywhere on the network. The Portable Banker can unlock the market for anywhere you stand, and Score Screens get a Market Board showing the newest lots. The end of the road also gets its goal: the **Totem Forge**, a multiblock that melts a bill of metals and pours a brand-new Shatter Totem, the only way to get another. Three molten metals come with it, and Tinkers' Construct and Thermal can melt and cast BSP metal.

This version also carries 1.6.1 and 1.6.2: bigger shop screens and a fix for Distant Horizons on Velocity networks.

**Existing worlds:** nothing to do by hand. The database and Redis settings move out of `serverconfig/bsp_core-server.toml` into a new `config/bsp_core-network.toml` at the first start (see *Security* below). On a network, the new tables (`market_lots`, `market_collect`, `market_attempts`, `trade_escrow`, `trade_outcomes`) and the new column `remote_market` on `bank_accounts` are added by themselves.

## For players: the Trade Post

- **The Exchange Desk.** A steel trading desk two blocks wide and two tall, with a screen under a hood and a pair of scales on the counter that tip while a trade runs. Its keys say MARKET, SELL, COLLECT and TRADE. Right-click a key to open that page.
- **The market.** List a lot of up to 9 stacks for one price in Shatter Pieces. It stays up for 7 days, and you can have 10 lots up at once. Buyers buy the whole lot. Listing costs 5% of the price, and the buyer pays the price plus 5%. Both taxes go into the lottery pot, which pays out in a later update. The seller is paid the moment a lot sells, or hears about it at their next login.
- **One market for the whole network.** A lot listed on one server can be bought on any other. When two players buy the same lot at the same moment, one gets it and the other pays nothing.
- **The Collect box.** Cancelled lots and lots that run out wait in your Collect box, and so does anything bought while your inventory was full. COLLECT ALL fills your inventory and leaves the rest in the box.
- **Search, sort and pages.** Search by item or seller, and sort by newest, cheapest or dearest. 36 lots come to a page, with arrows and "n/m" at the foot. Click a card for every stack in the lot, full tooltips included, then BUY, or CANCEL LOT on your own.
- **Trading with a player.** Everyone at a Trade Post shows down the right with their server's name, on every server of the network. Pick a name and press TRADE. A request lasts 30 seconds. Each side gets 12 offer places and a pieces box. Any change turns both READY lights off, and when both are ready a 5-second countdown locks the offers. If either side lacks the room, nothing moves and both are told who is short. Trade chat sits beside your inventory. Closing, walking away, logging out or dying cancels the trade, and everything goes back to whoever offered it.
- **Totems stay out of trade.** A Shatter Totem, forged or not, and a Totem Core can never be listed or offered, not even inside a shulker box or a bundle.
- **The market on the Portable Banker.** UNLOCK MARKET takes 12 Netherite Shatter Coins once, and the unlock stays with your account on every server. After that, MARKET opens the market screen wherever you are: browse, buy, list and collect. Trading with another player still needs a Trade Post.
- **The Market Board.** Score Screens get a Market Board: the newest 60 lots with the item, the price and how long ago each was listed. It scrolls through them, and follows every sale and new listing within a second on any server.

## For players: the Totem Forge

- **A second totem, made the hard way.** A 2 by 2 by 3 multiblock. On the bottom are the Totem Forge, a Metal Intake behind it and a Forge Hearth beside each. The middle is a hollow that must stay empty. On top are two Melt Tanks, a Pour Head and a Forge Cap. It forms like our other machines, with ghost blocks and the Assembly Guide.
- **The metal.** One totem takes 128 Tetrium, 32 Magnatite, 8 Charged Magnatite, 12 Illyrium, 32 Allthemodium, 16 Vibranium, 8 Unobtainium and 2 Netherite Ingots. Each metal melts into its own tank, an ingot every 2 seconds for 25 mB of lava. Molten Tetrium, Magnatite and Illyrium can be piped straight in.
- **The Totem Mould.** The mould takes a Base, two Feet, a Torso, two Arms and a Head, built from Mould Shells, with a Totem Core on top. Each piece looks like your totem with its own part in ceramic. Pieces last three pours and the core is used up by every pour. Allthemodium, Vibranium, Unobtainium, a Nether Star and a Totem of Undying are among the parts.
- **POUR.** With the mould complete and every tank full, POUR on the forge's screen starts it. If you own no totem, the pour is free and the totem sets in 5 days of real time. If you own any, you pay 4 Illyrium Coins' worth for each one you own, and it sets in 15 days. You may have one totem pouring or setting at a time, across the whole network. The forge tells you when it has set, on any server.
- **Steal it.** The pourer takes the set totem out with an empty hand. Anyone else has to steal it, by standing beside the forge for 2.5 minutes, while the pourer is warned in game and on Discord. It comes out as a tier I Forged Shatter Totem, and otherwise works like any other.
- **Molten Tetrium, Illyrium and Magnatite** come in buckets, burn like lava and can be moved and stored by any mod's pipes and tanks.
- **Tinkers' Construct and Thermal** melt BSP ores and metal and cast it back, never making more than our own machines do. There is no ore doubling.

## For players: also new

- **Bigger shop screens** (1.6.2): 30% bigger where the window has room, and each player can set the size in `config/bsp_core-client.toml` (`[shop] screenPercent`, 100 to 200).
- **The guide book** has "The Trade Post", "Trading with Players", "The Market", "Market on the Go", "The Totem Forge", "The Totem Mould" and "Molten Metals". JEI and EMI show information pages for all of it.

## For server owners

- **The Trade Post** is placed from the creative menu, like the other admin blocks: nobody mines, pushes or blows it up. Breaking it ends the trades of everyone at it.
- **Market settings** in the server config (`market`): `maxStacks`, `maxLotsPerPlayer`, `listingDays`, `taxPercent`, and `refusedItems` (item ids or `#tags` the market and trades refuse). The Portable Banker's unlock is `bank.marketUnlockCoins` and `bank.marketUnlockTier`.
- **Market commands:** `/bsp market list [player]`, `pot`, `cancel <id>`, `expire` and `box <player>`. `/bsp bank remote <player> market on|off` switches one player's market. The season reset `/bsp bank remote reset` clears the market unlock along with the Shopping Network.
- **Totem Forge settings** (`totem_forge`): the metal `bill`, `pourSeconds`, `freeSetHours`, `paidSetHours`, `coinsPerOwnedTotem`, `coinTier` and `stealSeconds`. A bill line whose metal is not in the pack is left out with a warning. Operators have `/bsp totemforge clear <player>`, `fill` and `finish`.
- **The Market Board** appears in every Score Screen's settings. A screen rotating through the default pages shows it too.
- **"Market Board" in the shop styles** is renamed "Market Stall style", so it is not mistaken for the Score Screen's board. Import files still say `market_board`.

## Security

- **The database and Redis passwords are no longer sent to players.** Forge sends `serverconfig/bsp_core-server.toml` to everyone who joins, and it held them. At the first start of 1.7.0, the host, port, database, user, password, table prefix and SSL settings of `[storage]` and `[storage.redis]` move into `config/bsp_core-network.toml`, which is never sent. The log says what moved and never prints a password.
- **What you need to do:**
  - Clear those values from `defaultconfigs/bsp_core-server.toml` if they are there.
  - Keep `config/bsp_core-network.toml` out of any client pack.
  - **Change both passwords**, since earlier versions may already have sent them to players.
- `storage.mode`, `storage.serverId`, `storage.redis.enabled` and `storage.redis.channel` stay in the server config.

## Fixes

- **Distant Horizons on Velocity** (1.6.1). After a server switch with Forge Client Reset Packet, Distant Horizons took the session for a replay and filed its LODs under `REPLAY`. Now it keeps the server's details across the switch.
- **A Create contraption can no longer pick up a placed Shatter Totem** or a Totem Forge, which would have carried off its owner, its steal and its upgrades.
- **The Workbench and shop screens** (1.6.2): text no longer runs into the side panel, keys and cards, and messages no longer cover the boxes. A section card's tooltip names the section it opens.
- **Built to keep items safe:** a lost database answer, a logout, a death or a server stopping mid-trade can never double or lose items or pieces. Heavy lots, such as shulker boxes full of books, can never send a packet big enough to stop the server. Busy networks stall less when Redis or the database is slow, and Score Screens and the Trade Post animations are smoother.

## For the other BSP mods

- Every BSP mod's BSP-Core version range already takes 1.7.0, so none needs a new build.
- BSP-InventorySync's `storage.useBspCoreDatabase` and BSP-Server Utilities' `useBspCoreDatabase` and `useBspCoreRedis` keep working: they find the settings in their new file.
- New for other mods: `MarketEvent.Listed`, `MarketEvent.Sold` and `TradeEvent.Completed`, and a small note on a Score Screen board line (`BoardLine.note`, `withNote`). The new tables and the Redis kinds `market`, `mbox` and `trade` are described in `docs/API.md`.
