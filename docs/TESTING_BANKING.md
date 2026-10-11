# Banking: step-by-step test run

One run through everything the banking line adds, in order, on one server. Each step says what to do and what you should see. Tick the box when it matches; note anything else beside it. The matching checklist lines in `docs/TESTING.md` can be skipped once this run is done.

**You need:** a creative operator account (yours), a second account that has **never joined this world** ("the newcomer"), and a third account or a friend for payments if you want to test the offline cases fully. A fresh world is best, so the first-join grant can be seen.

**Config defaults assumed** (`serverconfig/bsp_core-server.toml`, section `[bank]`): 1,000 pieces per Copper coin, 500 starting pieces, confirmation at 1,000 pieces, 25% coin markup.

---

## Part 1: the account and the command backups

### 1.1 First join
1. Join with the newcomer.
2. **See:** a gold line "Welcome to the Shatter Bank: your account opens with 500 pieces."
3. Run `/bank`.
4. **See:** `Balance: 500 pieces.` and `No coins in the bank. ...`
5. Leave and rejoin with the newcomer. Run `/bank`.
6. **See:** still 500. No second welcome line.
- [ ] 1.1 matches

### 1.2 Paying a player who is online
1. With your own account, run `/bank` and note your balance (500 if this world is fresh).
2. Run `/bank pay <newcomer> 300`.
3. **See (you):** gold "Paid 300 pieces to <newcomer>. Balance: 200 pieces."
4. **See (newcomer):** gold "300 pieces received from <you>."
5. Run `/bank` on both accounts: 200 and 800.
- [ ] 1.2 matches

### 1.3 Shortfall and self-pay
1. Run `/bank pay <newcomer> 999999`.
2. **See:** red "Not enough in the bank." and `/bank` still shows 200.
3. Run `/bank pay <your own name> 1`.
4. **See:** red "You cannot pay yourself."
- [ ] 1.3 matches

### 1.4 The confirmation step
1. Give yourself money first: `/bsp bank give <you> 5000`. **See:** "<you>'s balance is now 5,200 pieces."
2. Run `/bank pay <newcomer> 1000`.
3. **See:** "Pay 1,000 pieces to <newcomer>? Run /bank pay <newcomer> 1000 confirm within a minute." Nothing moves yet (`/bank` shows 5,200).
4. Run `/bank pay <newcomer> 1000 confirm`.
5. **See:** the gold "Paid" line; balance 4,200.
6. Run `/bank pay <newcomer> 1500 confirm` straight away, without the set-up command.
7. **See:** red "That payment was not set up, or it timed out."
- [ ] 1.4 matches

### 1.5 Refusing payments
1. On the newcomer, run `/bank paytoggle`. **See:** "You are refusing payments from other players."
2. From your account, `/bank pay <newcomer> 10`. **See:** red "<newcomer> is not accepting payments."
3. On the newcomer, `/bank`. **See:** the balance line plus the "You are refusing payments" reminder.
4. On the newcomer, `/bank paytoggle` again. **See:** "You now accept payments."
5. From your account, `/bank pay <newcomer> 10` works.
- [ ] 1.5 matches

### 1.6 Paying an offline player
1. Log the newcomer out.
2. From your account, `/bank pay <newcomer> 50`.
3. **See:** the gold "Paid" line (the newcomer is known to this server, so the name resolves while they are offline).
4. Log the newcomer back in.
5. **See (newcomer):** gold "While you were away: 1 payment(s), 50 pieces in all. /bank log shows them."
- [ ] 1.6 matches

### 1.7 The transaction log
1. On the newcomer, `/bank log`.
2. **See:** "Transaction log, page 1 of 1 (newest first):" then one line per event, newest at the top: `received ... +50`, `received ... +10`, `received ... +1,000`, `received ... +300`, `grant Shatter Bank, welcome +500`. Money in is green, money out red, grey for coin-only moves.
3. On your account, `/bank log`: your `paid` lines in red and the `admin <you>, given +5,000` line.
4. Make more than eight entries (a few small `/bank pay` commands) and run `/bank log 2`.
5. **See:** page 2 of 2 with the oldest entries.
- [ ] 1.7 matches

### 1.8 Admin commands
1. `/bsp bank set <newcomer> 0`. **See:** "<newcomer>'s balance is now 0 pieces." The newcomer's `/bank` shows 0 and the log has an `admin ... set to 0` line.
2. `/bsp bank take <newcomer> 5`. **See:** red "Not enough in the bank." (taking below zero is refused, nothing changes).
3. `/bsp bank give <newcomer> 250` then `/bsp bank take <newcomer> 50`. **See:** 250 then 200.
4. `/bsp bank remote <newcomer> on`. **See:** "<newcomer>'s Portable Banker can reach the shop network." (It matters in Part 3.)
- [ ] 1.8 matches

### 1.9 Config
1. Stop the server, set `startingPieces = 750` and `payConfirmPieces = 100` in `[bank]`, start it.
2. Join with a third never-seen account. **See:** the welcome line says 750.
3. `/bank pay <that account> 100`. **See:** the confirmation prompt (threshold is now 100).
4. Put the two values back afterwards.
- [ ] 1.9 matches

## Part 2: the Shatter Bank block (ATM)

### 2.1 Placing it
1. As the operator in creative, find **Shatter Bank** in the BSP creative tab (two-block pillar icon) and place it on open ground facing you.
2. **See:** a dark steel pillar two blocks tall: bolted base plate, two riveted bands, a hooded green screen with four amber keys lit down each side, a slanted coin hopper, a lit tray low down, an amber cross turning on the crown. The screen reads SHATTER BANK, the server name, and "INSERT COINS" blinking.
3. Walk round it: the screen faces the way you stood when placing. Place a second one facing another way to check all four facings put the screen on the front.
4. At night (or `/time set midnight`) it lights the ground around it like a machine.
5. Try to place one with the newcomer (not an admin): the red admin message, nothing placed. The newcomer in survival cannot mine it either.
6. As the operator, break the **upper** half: both halves go. Place again; break the **lower** half: both go.
- [ ] 2.1 matches

### 2.2 The account screen
1. Right-click either half. **See:** a key beep; the screen: beige bezel, black screen with green text "> SHATTER BANK" and your name on the header line, your balance large in the middle with PIECES beside it, the five coin tiers in a row (coin icon in a green ring, count below, tier name under that; tiers you hold none of have a broken, dimmer ring), the hint about interest and the 1,000-piece rate; keys PAY PLAYERS and TRANSACTION LOG at the top right, DEPOSIT COINS and WITHDRAW COINS at the bottom right; the strip at the base reads "bank / <server>" and "synced".
2. While you have it open, have the newcomer look at the block: the screen on the block now reads ACCOUNT OPEN with a blinking cursor. Close it: back to SHATTER BANK.
3. Walk more than eight blocks away with the screen open: it closes.
- [ ] 2.2 matches

### 2.3 Deposit and convert
1. Give yourself coins: `/give @s bsp_core:copper_shatter_coin 5` and `/give @s bsp_core:diamond_shatter_coin 2`. Note your balance.
2. Open the bank, press DEPOSIT COINS. **See:** the row now shows coins **in your hand** (5 Copper, 2 Diamond), Copper selected, "In hand: 5 Copper", the preview "1 Copper coin(s) -> 1,000 pieces", the top-right key reads CONVERT (press it to toggle to KEEP COINS and back).
3. Press +1 then CONFIRM with CONVERT showing. **See:** a beep, the amber line "Converted 2 Copper coins into 2,000 pieces.", 3 Copper left in hand. BACK: the balance rose by 2,000 and the Copper ring on the account screen still shows 0 (converted coins are not banked).
4. DEPOSIT COINS, click the Diamond coin, press ALL, toggle to KEEP COINS, CONFIRM. **See:** "Banked 2 Diamond coins." BACK: the Diamond ring is solid with 2, the balance unchanged.
5. Try CONFIRM with more coins than you hold (the +1 key stops at what you hold, so use ALL on an empty tier): the key is greyed and does nothing.
- [ ] 2.3 matches

### 2.4 Withdraw
1. WITHDRAW COINS. **See:** the row shows coins **in the bank** (2 Diamond). Click Diamond, +1 is capped at 2; CONFIRM with 1. **See:** a heavier clunk, "Took out 1 Diamond coins.", one Diamond coin in your inventory, the ring shows 1.
2. Select a tier you have none of in the bank: CONFIRM is greyed.
- [ ] 2.4 matches

### 2.5 Pay from the screen
1. PAY PLAYERS. Type the first letters of the newcomer's name in the search box (hint "Search"; the amount box below shows "0" until you type). **See:** matching names listed under the box (online players first, then players this server knows); click one to fill the name.
2. Type 300 in Amount. **See:** "Balance after: ..." updates as you type. Press PAY. **See:** the amber line "Paid 300 pieces to <newcomer>. Balance: ...", the amount box clears; the newcomer gets the chat line.
3. Type 1,000 or more and press PAY. **See:** "Pay 1,000 pieces to <newcomer>? Press CONFIRM." and the bottom keys become CONFIRM and CANCEL. CANCEL goes back without paying; PAY again then CONFIRM pays.
4. Press the ACCEPTING PAY key: it turns to REFUSING PAY and the newcomer can no longer pay you; press again to accept.
5. Type a name nobody has: "No player called X is known to the bank." in red-amber.
- [ ] 2.5 matches

### 2.6 The transaction log
1. TRANSACTION LOG. **See:** filter chips All, Bought, Sold, Paid, Received, Deposits (All lit); a search box; rows newest first with the date, the type and the other side, and the signed amount at the right (money out in amber, in green, coin-only moves as "+2 Diamond" style); "page 1 of N, M entries" bottom right.
2. Click Paid: only your payments; Deposits: the convert, bank and withdraw lines. Type a name or word in the search box: rows narrow to matches.
3. SORT: DATE key toggles to SORT: AMOUNT: rows order by size. NEXT PAGE and PREVIOUS move when there are more than seven rows.
- [ ] 2.6 matches

### 2.7 Guide and JEI
1. The guide book, Shatter Coins category, entry "The Shatter Bank": three pages, no mention of anything being added or changed.
2. JEI or EMI: the Shatter Bank shows an information page and no recipe.
- [ ] 2.7 matches

## Part 3: the Portable Banker

### 3.1 Crafting and opening
1. In JEI or EMI look up **Portable Banker**: Illyrium Ingot, Charged Resonance Crystal, Illyrium Ingot over Tetrium Plate, Illyrium Processor, Tetrium Plate over Illyrium Ingot, Gold Shatter Coin, Illyrium Ingot. Craft one (or take it from the BSP creative tab, next to the Shatter Bank).
2. Hover it: three grey tooltip lines (account anywhere; coins at a bank only; unlock the Shopping Network with coins).
3. Right-click with it anywhere, away from any bank. **See:** the same Cash Machine screen headed "> PORTABLE BANKER"; the strip reads "anywhere / <server>". Top keys: PAY PLAYERS, TRANSACTION LOG, UNLOCK NETWORK; the bottom key reads "COINS: AT A BANK" greyed out. Under the hint: "Shopping Network: locked. Unlock with coins worth 16 Copper."
4. Pay a player and read the log from the device: both work as at the bank.
- [ ] 3.1 matches

### 3.2 Unlocking the Shopping Network
1. With no coins in your inventory, press UNLOCK NETWORK. **See:** "Unlocking the Shopping Network costs coins worth 16 Copper. Carry them." Nothing changes.
2. `/give @s bsp_core:illyrium_shatter_coin 1` (worth 16 Copper) plus `/give @s bsp_core:copper_shatter_coin 3`. Press UNLOCK NETWORK. **See:** "Shopping Network unlocked. It stays with your account...", the key now reads SHOPPING NETWORK, the hint "Shopping Network: open". The Illyrium coin is gone and the three Copper are still there (change is given in the smallest coins: paying 16 with 1 Illyrium plus 3 Copper takes the Copper first, so expect to be left with coins worth 3 Copper in some mix).
3. Drop or destroy the device and take a new one: open it. **See:** still unlocked; the account remembers.
4. `/bsp bank remote reset`. **See:** "Shopping Network access cleared for 1 player(s)...". Open the device: locked again. `/bsp bank remote <you> on` turns it back on without coins (admin).
- [ ] 3.2 matches

## Part 4: the Safety Deposit Box

### 4.1 Placing it
1. From the BSP creative tab place a **Safety Deposit Box** next to the bank, facing you. **See:** a steel cabinet with three drawers, gold edges round each drawer front, gold handle ends and label plates, a gold line along the top and bottom, rivets down both sides, a dark lamp strip up the right edge. Nothing lit (you have no box yet).
2. The newcomer cannot place or mine it; the operator can break it.
- [ ] 4.1 matches

### 4.2 Renting
1. Right-click it. **See:** a latch click; the Cash Machine screen headed "> SAFETY DEPOSIT BOX" with "not rented" at the right; the text "Rent a box: 350 pieces for 7 day(s)..." and the terms line; keys -7 DAYS / +7 DAYS at the top, RENT 7 D: PIECES and RENT 7 D: COINS at the bottom (COINS greyed unless you carry coins that cover it). No item slots yet.
2. Press +7 DAYS: the text and keys say 14 days and 700 pieces. Press -7 DAYS back to 7.
3. Press RENT 7 D: PIECES. **See:** a short clunk and the screen reopens with the box: 18 slots in two rows, the first 6 lit, the other 12 hatched; "6 of 18 slots unlocked"; "rented until <date>, 7 day(s)" at the right; keys now also ADD 3 SLOTS. Your balance dropped by 350.
4. Walk outside and look at the block: the middle drawer's label plate and the lamp strip are amber. The newcomer looking at the same block sees it dark.
- [ ] 4.2 matches

### 4.3 Using the box
1. Put items in the 6 open slots; shift-click moves items in and out. Try to put a Shatter Coin or a Shatter Totem in: it will not go (and shift-clicking it does nothing).
2. Click a hatched slot: nothing can be placed there.
3. Press ADD 3 SLOTS. **See:** "Three more slots: 9 unlocked, for 2,000 pieces." and three more slots light. Repeat until 18: the key greys out at 18.
4. Close the screen, open a **second** deposit box block elsewhere: the same items are there.
5. With the newcomer, open the box: they get the "not rented" screen, never your items.
- [ ] 4.3 matches

### 4.4 Expiry and the holding fee
1. The rent cannot run out in test time, so use the admin test command: `/bsp bank boxlapse <you> 5` (your rent ran out 5 days ago). **See:** the chat confirmation.
2. Look at the block: the label and strip are red and a red tag hangs from the middle handle.
3. Right-click it. **See:** "rent ran out 5 day(s) ago" in red at the right, "To open again: 1,100 pieces for 7 day(s) of rent plus the holding fee for 5 unpaid day(s)." and "Your 18 slots and everything in them are kept; they open when you pay."; keys PAY TO OPEN: PIECES / COINS. No item slots shown.
4. Press PAY TO OPEN: PIECES. **See:** the box reopens with all your items still in place and 7 days of rent; balance down by 1,100.
5. Paying with coins: give yourself one Copper coin, set the box to lapse again, press PAY TO OPEN: COINS. **See:** the coin is taken and the difference comes back as pieces (`/bank log` shows a `rent ... paid with 1 Copper coins` line and a `convert ... change` line).
- [ ] 4.4 matches

### 4.5 Guide and JEI
1. Guide book, Shatter Coins category, entry "Safety Deposit Box": two pages.
2. JEI/EMI: an information page, no recipe.
- [ ] 4.5 matches

## Part 5: the network (two servers, one database, Redis optional)

Needs the MySQL live test setup: two servers with `storage.mode = "mysql"` on one database, different `serverId`s, and a Redis if you have one (`[storage.redis] enabled = true`, host, port, password). Both the MySQL and the Redis code have never run against real services before this: expect the first run to find things.

### 5.1 Without Redis (polling only)
1. On server A, `/bsp storage status`. **See:** "Network storage: MySQL, ... Redis: off (polling)."
2. Join A with the newcomer: the welcome and 500 pieces. Switch them to server B: `/bank` shows 500 and no second welcome.
3. From your account on A, `/bank pay <newcomer> 100` while they are on B. **See (newcomer on B):** the "received" line within `storage.noticeSeconds` (3 s). Their `/bank` on B shows 600 after opening a bank or the Portable Banker there (the balance refreshes on open and on login; a plain `/bank` may still show the old copy until then).
4. Rent a deposit box on A and put an item in; switch to B and open a box block there: the same item.
- [ ] 5.1 matches

### 5.2 With Redis
1. Set `[storage.redis] enabled = true` on both servers and restart. `/bsp storage status`. **See:** "... Redis: <host>:<port> channel "bsp_core", listening." `/bsp storage test` ends with "Redis: PONG from ...".
2. Repeat 5.1 step 3. **See:** the "received" line on B at once, and `/bank` on B shows 600 without opening anything.
3. Open the deposit box on B while a friend (or you, from A, using `/bsp bank boxlapse`) changes it on A. **See:** the screen on B closes by itself; reopening shows the change.
4. Stop Redis. **See:** the server log warns "subscriber dropped ... retrying", and payments still arrive within 3 s through the polling. Start Redis again: the log says "listening" within a minute.
- [ ] 5.2 matches

## Part 6: shops by command (before the screens)

Setup: operator, the newcomer with pieces (`/bsp bank give <newcomer> 5000`), some iron ingots in your hand.

### 6.1 Making a shop
1. `/bsp shop create smith Spawn Smith`. **See:** "Shop smith (Spawn Smith) created..."; `/bsp shop list` shows it with 0 items.
2. Hold 4 iron ingots: `/bsp shop add smith 120 30`. **See:** "Added 4 Iron Ingot to smith at index 0."
3. `/bsp shop item smith 0 stock 3 -1 1`: three lots for the whole server, restock one minute after they run out.
4. `/bsp shop open smith`. **See:** the shop listed in chat: "0: 4 Iron Ingot  buy 120  sell 30 pieces  stock 3" and the how-to line.
- [ ] 6.1 matches

### 6.2 Buying and stock
1. As the newcomer: `/bsp shop buy smith 0 2 test`. **See:** "Test: would buy 2 lot(s) for 240 pieces...", nothing in the inventory, balance unchanged.
2. `/bsp shop buy smith 0 2`. **See:** gold "Bought 2 lot(s) for 240 pieces. Balance: ..."; 8 iron ingots in the inventory; `/bank log` shows a `bought Spawn Smith, 8 Iron Ingot -240` line.
3. `/bsp shop buy smith 0 5`. **See:** only 1 lot bought (the stock left). Again: "Sold out. Check back after the restock." Wait a minute: a buy works again (restocked to 3).
4. Fill your inventory so fewer than 4 ingots fit, then buy 1 lot: "No room in your inventory for it." (nothing paid).
5. `/bsp bank set <newcomer> 50` then buy: "Not enough pieces in the bank."; nothing given.
- [ ] 6.2 matches

### 6.3 Selling, buy-only, offers
1. Holding 40 cobblestone, add it to a second shop at sell 20 per lot of 16: `/bsp shop create quarry Quarry`, hold 16 cobblestone, `/bsp shop add quarry 100 20`. `/bsp shop sell quarry 0 10`. **See:** "Sold 2 lot(s) for 40 pieces..."; 8 cobblestone left (only full lots sell).
2. `/bsp shop set quarry buyonly on`, then `/bsp shop sell quarry 0 1` with 16 cobble: "This shop does not buy that."
3. Opening the shop as a player who is not an admin works for `open`, `buy` and `sell`; `create`, `add`, `set` are refused for them.
- [ ] 6.3 matches

*(The Shopping Network, rotating shops, special offers and opening hours get their steps with the screens, the Vendor Post and the stall.)*

## Part 7: the shop screen

Setup as Part 6: shops `smith` (iron ingots, stock 3) and `quarry` (cobblestone) exist; the newcomer has pieces.

### 7.1 The page
1. `/bsp shop open smith`. **See:** the Cash Machine screen headed "> SPAWN SMITH" with "page 1 / 1" at the right; a card with the iron ingot icon, "buy 120" in amber, "sell 30" dim, "3" top right; keys BACK, PREVIOUS (greyed), NEXT PAGE (greyed) at the top, SELL HERE, SELL ALL at the bottom (plus ADMIN for you as an admin, not for the newcomer); the strip reads "open / <server>" and "balance ...". Hover the card: the tooltip lists the prices, the stock and the clicks.
2. Add 22 items to a shop (hold anything, `/bsp shop add smith 1 -1` repeatedly): a second page appears; NEXT PAGE and PREVIOUS move between them.
3. Add a card with no buy price (`/bsp shop add smith -1 5`): "--" in place of the buy price.
- [ ] 7.1 matches

### 7.2 Buying
1. Left-click the iron card. **See:** the quantity view: the item and its name, a big "1" with "lots", "total 120 pieces" in amber, keys -1, +1, +16, MAX at the top, CONFIRM and CANCEL at the bottom. +1 twice: total 360. MAX: the count stops at the stock (3), at what you can afford or at what fits. CONFIRM. **See:** "Bought 3 lot(s) for 360 pieces.", 12 iron ingots, the badge reads OUT and the card is dark.
2. Left-click the OUT card: nothing opens. After the restock minute the badge returns.
3. Make an instant-buy item (set `instant` once the configurator exists; skip for now).
- [ ] 7.2 matches

### 7.3 Selling
1. `/bsp shop open quarry` holding 40 cobblestone. Right-click the cobblestone card. **See:** the sell quantity view with "you get 20 pieces" for 1 lot; MAX gives 2 (two full lots of 16). CONFIRM: "Sold 2 lot(s) for 40 pieces.", 8 cobblestone left.
2. With more cobblestone, shift-right-click the card: every full lot sells at once.
3. SELL HERE. **See:** the grid screen "> SELL HERE: QUARRY" with 14 green-framed slots over your inventory and a SELL NOW key. Drop in cobblestone and a diamond; press SELL NOW (or close). **See:** the cobblestone's full lots sold with one gold "Sold ... lot(s) in all" line, the diamond and any part lot back in your inventory.
4. `/bsp shop set quarry buyonly on`, open it: no sell prices on cards, no SELL HERE or SELL ALL keys; right-click does nothing.
- [ ] 7.3 matches

*(Closed shops, special offers and rotating pages get their steps with the Vendor Post and the configurator.)*

## Part 8: the shop workbench

Setup: operator; a moderator account if you have one; a few items in hand.

### 8.1 Opening and the list
1. `/bsp shop`. **See:** the Cash Machine workbench "> SHOP WORKBENCH": the shop list (smith, quarry, ...) with kind, card count and NET flags; keys SHOPS, PAGE (greyed until a shop is picked), SETTINGS, STYLE at the top; NEW SHOP (greyed until an id is typed), PREVIOUS, NEXT at the bottom; the strip "admin: changes save at once".
2. Type `bakery` in the id box, press NEW SHOP. **See:** "Shop bakery created.", the list shows it, PAGE opens on an empty page.
3. The newcomer: `/bsp shop` is refused. A moderator: the workbench opens with the strip "moderator: read only" and every change answers "Moderators may look but not change."
- [ ] 8.1 matches

### 8.2 Cards
1. On bakery's PAGE hold 8 bread, press HAND TO SLOT. **See:** a bread card with "--" (no prices yet). Click it: the side panel lists Lot size 8, Buy off, Sell off, Currency pieces, Stock inf, Stock per player inf, Restock 0 m, Instant off, Hidden off, Opens section -.
2. Click "Buy price" on the side panel, type 12, Enter. **See:** "Saved." and the card reads "buy 12". Set Sell 3 the same way. Click Currency: it cycles to Copper, Gold ... and back to pieces. Click Instant: on. Click Stock, server: type 5, Enter; Restock: 1.
3. Press BROWSE ITEMS, type `torch` in the search: the grid narrows; click a torch. **See:** back on the page with a torch card added after the bread. Type `mekanism` or another mod's name in the search: its items appear with their mod id in the tooltip.
4. Click a card then REMOVE CARD: gone. Click an empty slot then HAND TO SLOT: the item lands there, shifting the rest.
- [ ] 8.2 matches

### 8.3 Settings, style and test
1. SETTINGS. **See:** Name, Kind, Parent, Shopping Network, Buy only, rotating slots and interval, Offer discount and end, Id. Click Name, type "The Bakery", Enter: the header updates. Click Kind: shop > sub > rotating > offer > shop. Click Shopping Network: on. Click Offer discount, type 25: the player page shows the SPECIAL OFFER chip with prices cut.
2. STYLE: Cash Machine marked; the other two are listed as coming later.
3. PAGE then TEST SHOP. **See:** the player's shop screen for the bakery; buying answers "Test: would buy ... Nothing moved." and your balance and inventory do not change; SELL HERE is not offered in test mode.
4. Open the bakery as a player (`/bsp shop open bakery`): the ADMIN key opens the workbench on it.
5. DELETE SHOP on a shop: it leaves the list and `/bsp shop open` for it fails.
- [ ] 8.3 matches

## Part 9: the Vendor Post

Setup: operator, a shop (bakery) with cards, a redstone lamp.

### 9.1 Placing and assigning
1. From the BSP creative tab place a **Vendor Post** on a redstone lamp, facing you. **See:** a steel post with a bolted base, a name plate and a dark lamp on the front; the lamp blinks red (no shop yet). Right-click as a player (the newcomer): "No shop here yet."
2. As the operator, sneak and right-click it with an empty hand. **See:** the Cash Machine "> VENDOR POST" screen: the shop list, Opens and Closes boxes, "Always open" in amber, "Figure: villager", "No shop assigned" in red; keys PREVIOUS, NEXT, NEXT FIGURE, PROFESSION at the top; HOURS: ALWAYS, UNASSIGN, DONE at the bottom.
3. Click bakery, NEXT FIGURE until "wandering trader", DONE. **See:** a wandering trader standing on the post facing the way you placed it; within a second the front lamp lights amber and the redstone lamp beneath turns on. Walk around it within six blocks: its head follows you.
4. Right-click the post as the newcomer: the bakery screen opens, with "open / <server>" in the strip; no ADMIN or VENDOR key. As the operator: both keys; VENDOR reopens the assignment screen.
- [ ] 9.1 matches

### 9.2 Hours and redstone
1. Assignment screen, press HOURS: ALWAYS so it reads HOURS: CLOCK; type a window that is closed now (for example 03:00 to 03:10), DONE. **See:** the lamp beneath turns off within a second, the post's front lamp goes dark. Right-click: the bakery shows CLOSED with "opens at 03:00" and the countdown, no cards.
2. Set a window that is open now: the lamp comes back and the shop opens.
3. HOURS: GAME with 13000 to 23000 (night); `/time set day`: closed; `/time set night`: open within a second.
4. Villager figure: NEXT FIGURE to "villager", PROFESSION to "librarian", DONE: a librarian stands on the post.
5. UNASSIGN then DONE: the figure vanishes, the lamp blinks red again, players get "No shop here yet."
6. The newcomer cannot place or mine the post.
- [ ] 9.2 matches

## Part 10: the Shopping Network

1. In the workbench, Settings of bakery: Shopping Network on. Leave quarry off.
2. On a locked account (newcomer, Portable Banker): the UNLOCK NETWORK key; pressing it with no coins gives the cost line. Unlock it with coins (Part 3).
3. Press SHOPPING NETWORK. **See:** "> SHOPPING NETWORK": bakery listed with "shop" and its card count; quarry absent; PREVIOUS, NEXT, CLOSE keys. Click bakery: the bakery opens with "open / shopping network" in the strip; buying and selling work from anywhere.
4. In the workbench, bakery Settings: click "Hours" until it reads clock, click Opens and type 03:00, Closes 03:10 (a window closed now). Reopen the Shopping Network: bakery shows "closed" in red; clicking it opens the closed view with the countdown. Set the hours back to always.
5. Place a Vendor Post on the bakery with its own game-time hours: the post's hours decide at the post; with the post on "always" the shop's hours decide.
6. `/bsp bank remote reset`, reopen the device: the key reads UNLOCK NETWORK again.
- [ ] Part 10 matches

## Part 11: the Stall Display

Setup: operator, the bakery assigned to a Vendor Post (Part 9) with at least two cards, one of them with a stock of 1.

### 11.1 Linking
1. From the BSP creative tab place a **Stall Display** two blocks from the post. **See:** the player-built display in its neglected look (cobble base, fence post, an oak slab with a striped carpet, a dark lantern, the small sign reading CLOSED). Right-click it as the newcomer: "This display is not linked to a shop yet." Nothing floats.
2. As the operator, sneak and right-click the display. **See:** the Cash Machine "> STALL DISPLAY" screen: "Not linked" in red, an empty item grid, "Size 0.80*  Height 1.35*  Every 4* s" with the note that * means default; keys SIZE, HEIGHT, INTERVAL, RESET LOOK (greyed) at the top; LINK, UNLINK (greyed), DONE at the bottom.
3. Press LINK. **See:** the screen closes and the status line says "Now right-click the Vendor Post or stall this display should follow." Right-click a stone block: nothing happens, the prompt still waits. Right-click the post: "Display linked to <x, y, z>." and the post's own screens do not open.
4. Within a few seconds the display becomes whole (lantern lit, SHOP sign) and the first card's item floats above it, turning slowly and bobbing; about every four seconds it changes to the next card and then back round. Buy the whole stock of the stock-1 card: that item leaves the rotation the next time round. Linked to a Vendor Post the display stays in the player-built style.
5. Right-click the display as the newcomer: the bakery opens as if the post had been clicked, with the post's hours. As the operator the ADMIN and VENDOR keys are there too.
- [ ] 11.1 matches

### 11.2 Settings
1. Sneak and right-click the display again. **See:** "Linked to <x, y, z>: bakery" and the bakery's item cards in a grid (hover for the tooltip; scroll if there are more than 33).
2. Click a card: it lights green, the line reads "Pinned: <item>" in amber and, behind the screen, that item floats and stays. Press DONE and watch for ten seconds: it does not change. Click the same card again: "Every item in turn, 4 s each" and the rotation resumes.
3. The starred defaults are the style's own item point and size (player-built: size 0.88, height 1.16). Press SIZE: the line steps to the next set value (1.00, 1.25, 1.50; the * disappears) and the floating item grows at once. HEIGHT: 1.20, 1.35, 1.50, the item rises. INTERVAL: 6, 10, 15 s. RESET LOOK: all three back to the starred defaults. Pressing SIZE past the largest value wraps to 0.50.
4. Press UNLINK: "Not linked" and the item vanishes. LINK it again (11.1 step 3).
5. LINK, then right-click a post more than 32 blocks away: "Too far: a display follows a post or stall within 32 blocks." and the display keeps its old link.
6. Give the post a closed window (Part 9.2): the floating item disappears within a few seconds; the display's right-click shows the closed view with the countdown. Open the post again: the items return. UNASSIGN the post: the display goes dark and the screen reads "Linked to <x, y, z>: no shop assigned there." in amber; assign the bakery again: the items return.
7. Break the post as the operator: the display goes dark but stays linked to that spot; place a new post there and assign it: the items return.
8. The newcomer cannot place or mine the display, and sneaking and right-clicking it as the newcomer opens the shop, not the settings.
- [ ] 11.2 matches

## Part 12: the Market Stall

Setup: operator, the bakery with at least two cards (one with a stock of 1), a redstone lamp. Jade installed if you want the hover lines.

### 12.1 Placing, skins and the setup screen
1. From the BSP creative tab place a **Market Stall** on a redstone lamp, facing you. **See:** a two-block stall in the player-built style, but broken: a post gone, the awning sagging with torn wool, boards nailed across the counter, a CLOSED sign on one chain, cobwebs, a dark lantern. The inventory icon shows the whole player-built stall at an angle. Right-click it as the newcomer: "No shop here yet." Hover with Jade: "Market Stall", "Player-built style", "No shop assigned" in red; hovering the upper half shows the same.
2. As the operator, sneak and right-click it empty-handed. **See:** the Cash Machine "> MARKET STALL" screen: the shop list, Opens and Closes, "Style: Player-built", the red note that the stall stands broken; keys PREVIOUS, NEXT, NEXT STYLE at the top; HOURS: ALWAYS, UNASSIGN, DONE at the bottom. No figure or profession keys.
3. Press NEXT STYLE until "Style: Blackstone shrine", click bakery, DONE. **See:** the stall becomes the whole nether shrine: glowing magma front, soul lanterns lit, nether wart on the roof; within a second two lamps light at the top corners of the front (left green, right amber), the bakery's first card's item turns above the gold plate on the counter, the redstone lamp beneath turns on, and the stall gives off light.
4. Step through all eight styles with NEXT STYLE and DONE, walking round each: timber booth, vending terminal (two blocks tall, animated holo screen leaning out), boiler booth (item inside the bell jar, firebox flickering), player-built, blackstone shrine, street cart (lanterns, tiled roof), dockside (mast and sail), mage's pedestal (rune rings turning). For each: the lamps sit on their housings (not floating or buried), the item sits where the model makes room for it, nothing z-fights, and the awning or roof overhangs only to the front or top. Place a second stall directly beside the first: no overlap between neighbours. Face a stall north, east, south and west: the lamps and item turn with it.
5. Hover with Jade on an assigned stall: "Blackstone shrine style", "Shop: bakery" in gold, "open, in stock".
- [ ] 12.1 matches

### 12.2 Lamps, hours, redstone and the broken state
1. As the newcomer, right-click either half: the bakery opens with the hours in the strip. Buy the whole stock of the stock-1 card: the stall's left lamp stays green (another card still has stock). In the workbench give every bakery card a stock of 1 and buy them all: within a second the left lamp turns red; Jade reads "sold out". Restock (set the stock again): green.
2. Setup screen, HOURS: CLOCK with a window closed now, DONE: within a second the right lamp goes dark and the lamp beneath turns off; the model stays whole (the stall is closed, not broken); right-click shows the closed view with the countdown. HOURS: GAME 13000 to 23000, `/time set day` closed, `/time set night` open.
3. UNASSIGN, DONE: the stall turns back into its broken variant in the current style (for the shrine: cracked blackstone, the magma cold, a lantern fallen, a tooth broken off, a scorched CLOSED note), no lamps, no item, no light, the lamp beneath off. Assign the bakery again: whole within a second.
4. Link a Stall Display (Part 11) to the stall: within a few seconds the display takes the stall's style (for the shrine: a blackstone column with a magma front, basalt spikes, a soul lantern) and floats the bakery's items at that style's own point; step the stall through the eight styles and the display follows each time. When the stall is unassigned or closed the display shows its neglected variant with no item. The boiler-booth and street-cart displays hold the item inside a jar or frame with a small bob.
5. Break the upper half as the operator: both halves go. Place again, break the lower half: both go. The newcomer can neither place nor mine either half, and sneaking and right-clicking as the newcomer opens the shop, not the setup screen. A moderator sees the setup screen read-only.
6. Leave and rejoin (or restart the server): the stall keeps its style, shop and hours.
- [ ] 12.2 matches

*(The shop parts are added as the shops are built.)*
