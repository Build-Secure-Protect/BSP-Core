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
1. PAY PLAYERS. Type the first letters of the newcomer's name in "Find a player". **See:** matching names listed under the box (online players first, then players this server knows); click one to fill the name.
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

*(The shop parts are added as the shops are built.)*
