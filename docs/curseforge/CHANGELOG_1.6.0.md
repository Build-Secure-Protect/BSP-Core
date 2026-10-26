# BSP-Core 1.6.0

The shops grow up for the spawn's market: limits that refill on their own, the same counts and the same rotating cards on every server of a network, a Challenge shop that changes every Monday and says so in chat, cards that buy back used machines, notes on cards, cards for ranks or hours of play, and a search box. Server owners get a shop import and export, so a whole price list goes in with one command, and a report of what the shops paid out and took in.

**Existing worlds:** every shop keeps working as it was. Cards saved before keep their restock: it becomes their refill rule. On a network, the first server to start with 1.6.0 moves its shop counts into the database (new tables `shop_counts` and `shop_draws`, and two new columns and an index on `bank_log`, all added by themselves); the other servers then take the database's. A new setting, `shop.timeZone`, says which clock fixed refill and change times use (empty: the machine's own); set it the same on every server.

## For players: the shops

- **Limits that refill.** A card can limit how many lots each player, or everyone together, may buy, and how many the shop will buy back. The card's corner shows what is left ("3/4"): green for what you can still buy, amber for what the shop will still take from you, OUT or FULL in red at zero. Hover it for the details: "You can sell 1 more of 4", "Refills every Monday at 00:00 (in 2d 4h)".
- **Limits come back on their own**: a while after they run out (12 hours, 2 days...), every day at a time, every week on a day at a time, or at each new set of cards in a rotating shop. A sale that would pass a limit sells what is left and says so; Sell all and the Sell Here box stop at the limits and hand back the rest.
- **One count for the whole network.** On a network the stock, the sell limits and a rotating shop's cards are the same on every server: two servers can no longer both sell the last lot, and a weekly limit counts once wherever you sell.
- **Rotating shops on a timetable.** A rotating shop can change at a fixed time (every Monday at 00:00, every day at 06:00) instead of hours after someone first opened it, with no card repeated until every card has had its turn, and some cards more likely than others. When the cards change, everyone hears it: "[Shops] Challenge now buys Steel Casing, Machine Frame, Brass Casing and Fluix Block. Next change Monday 00:00.", each name showing the item when hovered.
- **A rotating shop only buys what is on show**, in Sell, Sell all and the Sell Here box.
- **Used machines sell.** A card can take its item whatever it carries: a machine with energy, upgrades or settings, a renamed tool. A damaged item is refused, and so is one still holding items (a filled shulker box, a backpack, a machine with things in its slots), so nothing is lost with it. A potion and an enchanted book still have to be the right one.
- **Notes on cards**: up to three short lines at the foot of a card's tooltip, in amber, such as "Weekly limit: 4".
- **Cards for ranks and playtime.** A card or a whole shop can need a rank (a LuckPerms permission) or hours of play. Locked, a card is dimmed with what it needs where its price was ("Gold rank and up", "10 h play") and a padlock; its tooltip says how far you are ("Played: 4 h 12 m of 10 h"). Some are hidden until you qualify, and a hidden shop is left off your Shopping Network list. A shop's requirement covers its sections too.
- **Search.** A shop with more than a page of cards has a search box in its header: type part of a name, or `@create` for a mod's cards, and click a result to open its section on the right page, with the card picked out for a moment.

## For server owners

- **`/bsp shop import <file> [test]`** loads shops from `config/bsp_core/shops/<file>.json`. The whole file is checked first: one error (an unknown item or a mod the server lacks, a bad id, a missing section, a parent loop, a coin payout without `"allowCoinPayouts": true`...) refuses it and nothing changes. `test` checks and reports without changing anything. Chat shows the summary and the first ten problems; the full report is written beside the file. A shop in the file replaces the one of that id; its posts, stalls and displays keep it. **When its cards are the same items in the same places** (only prices, notes or switches changed), **its counts and its rotating cards stay**, so a price fix does not reroll the Challenge or refill its limits; when cards changed, they start again. The format is in `docs/API.md`.
- **`/bsp shop export <id|all> <file> [overwrite]`** writes shops back out in the same format, so a shop changed in the Workbench can be saved, edited and imported again.
- **`/bsp shop report [days] [shop]`**: per shop, the pieces paid out to players and taken in, the net, the items moved and coins apart; the busiest cards; the ten players who sold most; a CSV with every card for a spreadsheet. On a network it covers every server.
- **The Workbench** has Card and Limits tabs: limits for everyone and each player, refill rules (typed, with a hint) and a Refill now button on Limits; Match, Note and Weight on Card; a change time, No repeats and Announce in a rotating shop's settings. Editing a price, a note or a switch leaves a card's counts alone; changing a limit, a refill rule, the lot size or the match rule starts them again. TEST opens a shop hidden by a requirement, for an admin without the rank too.
- **Commands for everything**: `/bsp shop item <id> <index> sell|refill|match|note|weight|requires ...`, `/bsp shop set <id> rotate|norepeat|broadcast|requires ...`.
- **Requirements** ask LuckPerms (Forge or the Bukkit plugin on Mohist), so any permission node works; without LuckPerms only operators pass. Playtime is BSP-RankUp's when it is installed (the same on every server when its storage is shared), otherwise the server's Time Played.

## Fixes

- **No more items kept and paid for together** when a player sold on a network and logged out or changed server in the same moment. A trade that hears back after the player has left now moves nothing; a purchase paid for in that moment is handed over at their next login.
- **A sale the bank cannot take gives the items back** instead of keeping them unpaid.
- **The Sell Here box keeps your items when you log out** with it open. The box no longer takes a Shatter Totem.
- **Two servers selling the same card at the same moment** no longer trip over each other in the database ("The shop's records cannot be reached").
- **Moving, adding or removing a card in a rotating shop** keeps the same cards on show; before, whatever card moved into their places was shown and traded.
- **After an import, every server shows the same new cards** of a rotating shop; another server could bring the old ones back.
- **A shop placed under itself** (a parent loop) froze the server when opened; the Workbench and the import now refuse it.
- **Cards hidden by a rank stay off stall counters and Stall Displays**, which everyone passing sees.
- **Lighter on the server:** a trade on one server makes the others read one card, not the whole shop; pages, Sell all and searches on big shops cost far less.
- **The search never shows yesterday's cards**, and says so when a result has gone.
- A shop name or a requirement's reason too long to fit is refused instead of breaking the shop screen or keeping the shop off the other servers.
- A Workbench lot is never more than a stack of the item (64 ender pearls become 16).
- The shop screen's "Bought" and "Sold" lines show the balance again.

## For the other BSP mods

- Every BSP mod's BSP-Core version range already takes 1.6.0; none needs a new build.
- The import format, the `shop_counts` and `shop_draws` tables and the Redis `shopcount` notice are described in `docs/API.md`.
- Built against BSP Region Guard 0.6.0; Region Guard stays optional.
