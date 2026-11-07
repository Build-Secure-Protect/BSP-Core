# BSP-Core 1.14.0

Store credit is here! Servers can hand out tickets worth Shatter Pieces that pay at the shops, the Land Office and teleports they choose, mostly as a thank-you for a donation. The Tetrium Crucible and the Combination Forge get lava tanks, the crucible makes twice the nuggets and takes three new Yield Upgrades, and Overclock finally shows (and keeps) the speed it gives. This file also covers 1.11.1, 1.12.0 and 1.13.0: kits for players with no totem, the Lead-Lined Chest and radiation shielding, a Land Office that sells its own plots, and score fixes for networks.

**Existing worlds:** nothing to do by hand. A saved Tetrium Crucible setting of 2 nuggets an ore (the old default) moves to 4 by itself; crucibles already placed keep everything in them and gain three empty upgrade slots. **Every player needs 1.14.0 as well as the servers** (the network protocol changed): update the pack and the servers together. An older client is told to update when it joins.

## For players: store credit

- **What a ticket is.** A Store Credit Ticket holds Shatter Pieces that can only be spent where the server allows: chosen shops (and every shop inside them), plots at the Land Office, or teleports with BSP-Teleport. It comes in one of the sixteen dye colours, with a **C** (credit) or **D** (donor) tab in the corner, or none.
- **The tooltip** shows the credit left, where it spends, its time limit or the time left, who it belongs to and its serial number.
- **It pays first.** A ticket in your inventory pays before your bank. If it doesn't cover the whole price, the bank pays the rest. With several tickets, the one that runs out soonest pays first.
- **Shops.** Where a ticket can pay, the shop's status strip turns into the ticket, with its credit and time left. The quantity screen shows the sum, "2,500 = 2,000 credit + 500 bank". Click the strip to keep your credit for later. Cards priced in coins are paid with coins as usual.
- **The Land Office.** A ticket for a plot name, or for any plot, pays for land first. The strip follows the plot you pick, and its card shows what credit and what the bank pay. Give up a claim you bought with credit and the credit's share of the refund stays with the office.
- **Teleports.** Once BSP-Teleport is updated for it, a teleport ticket pays its pieces prices before your bank.
- **The clock.** A timed ticket's clock starts at its first purchase, not when you get it. The whole screen washes in the ticket's colour with "CREDIT CLOCK STARTED", and the end date goes in chat. You're warned with a day left and again with an hour.
- **When it's over.** When the time runs out, whatever is left is gone: the screen says "TICKET EXPIRED" and the ticket disappears from your inventory and ender chest. If you were away, you see it at your next login. A ticket that is used up disappears too.
- **Belonging.** Every ticket belongs to the player who makes its first purchase, and some belong to you as soon as you're given them. Copies of a ticket share one balance, so duplicating one gets you nothing.
- Every spend is a line in your bank log naming the ticket. The guide book has a Store Credit section, and JEI and EMI explain the ticket.

## For players: machines

- **Lava tanks.** The Tetrium Crucible and the Combination Forge each hold 8,000 mB of lava.
  - Pipes fill the tank from any side and can't drain it. A lava bucket used on the block pours in.
  - Lava comes first: while there's enough for a job the machine needs no fuel. The crucible uses 100 mB an ore (a bucket smelts 10), the forge 200 mB an ingot or plate.
  - With less lava than that, the fuel slot burns as before. Lava runs the machines at the same speed as coal.
  - The screen shows a TANK gauge under HEAT.
- **More nuggets.** The Tetrium Crucible makes **4 Tetrium Nuggets per ore** (it was 2).
- **Crucible Yield Upgrade.** The crucible has three upgrade slots for it. Each one adds 2 nuggets per ore, so 6, 8 or 10, and with all three fitted the slag doubles too.
  - Recipe: four Tetrium Plates in the corners, four Tetrium Ingots on the sides, a Basic Control Circuit in the middle.
  - Fit them by hand; pipes and hoppers can't move them. One fitted mid-smelt counts from the next ore.
  - On the screen the three yield slots sit together under YIELD, and the RF slot follows after a gap. Every machine's RF slot now sits after a gap like this.
- **Overclock shows its speed.** Machines and Coin Factory slices fed by a totem with Overclock always ran faster, but Jade and the Coin Factory tooltip showed the plain plasma speed. They show the real figure now (x5.0 at a full 500 mB/t with Overclock IV).
- **Jade on a Plasma Injector** names the machine it speeds up and the speed it runs at. It also says when there's nothing at the nozzle, when a multiblock isn't assembled, and when a block can't be sped up by plasma.

## For players: kits (from 1.12.0)

- **Kits for players with no totem.** A server can keep a kit for players who own no Shatter Totem anywhere on the network: a second chance after a raid. While you own one the card is greyed with "You own a totem", and turned over it says "Own no Shatter Totem · you own 1".
- **A kit's totem is always new.** Every player who takes a kit with a Shatter Totem in it gets a brand-new Tier I totem in their name. It goes into your inventory however full it is and is never dropped.

## For players: radiation (from 1.13.0)

- **Lead-Lined Chest.** With Voltaic radiation (Electrodynamics, Nuclear Science), nothing in it ever gives off radiation, however much uranium is inside.
  - Otherwise it's a chest: two side by side make a 54-slot large chest, and hoppers, pipes, comparators and claims all work.
  - It looks the part: a lead box with a trembling gauge, and a lid light that glows green shut and amber while anyone has it open.
  - Recipe: eight lead plates round a wooden chest.
- **Radiation shielding:**
  - The **Panel** stops all radiation. Recipe: eight lead plates round stone make two.
  - The **Window** is a thick pane that lets a tenth through, and all the light. Recipe: lead plates either side of three glass make four.
  - The **Door** lets 3% through while shut and opens by hand or redstone. Mobs treat it as an iron door. Recipe: six panels make three.
  - Panels and windows join into one riveted wall and one sheet of glass. All three are as hard as iron.
  - Lead plates come from Electrodynamics, AllTheOres, Railcraft or Thermal.
- The guide book has a Radiation section.

## For players: the Land Office (from 1.13.0)

- **Each Land Office sells its own plots,** chosen in Region Guard. The office the wand opens still lists every plot.
- **Banned blocks.** When a plot bans blocks or items, its card says "3 BANNED" and a **BANNED** key lists them before you buy.

## For players: fixes

- **Guide book pages with two recipes show their words.** Fourteen pages showed two crafting grids and no text. Each is now two pages.
- **Coin Factories keep Overclock's speed while nobody is near.** Time away was counted at the plain plasma speed, so a factory left running gained almost nothing from Overclock.
- **Multiblock tanks follow their configured size.** The Illyrium Crucible, the Illyrium Refinery and the Totem Forge filled only to 8,000 mB whatever they were set to, and a bucket could be part-wasted.
- **Scores drop totems that are gone** (1.11.1). On a network, a server that stopped writing (renamed, removed or down) kept its totems on the board for good.

## For server owners

- **Store credit kinds** are made in the admin panel's new **CREDIT** tab, or with `/bsp credit kind <id> ...` from the console.
  - A kind has a name, a value, a colour and a C or D tab, and a time limit counted from the first purchase (or none).
  - It also sets whether it binds to the player it's given to.
  - And where it spends: shops (with their sub-shops), plot names (each with how many plots share it), any plot (which also covers open ground and enlarging), and teleports.
  - **COPY /GIVE** puts the give command on your clipboard. **TICKETS** lists every ticket of a kind with what is left, and **VOID** (click twice) ends one.
  - Only BSP-Core admins and operators can use the tab and `/bsp credit`.
- **Giving tickets.**
  - `/give <player> bsp_core:store_credit{Kind:"<id>"}` works, so a donation plugin can run it.
  - `/bsp credit give <player> <kind> [count]` does the same, and also works for a player who is offline: the tickets arrive at their next login.
  - A ticket put in a kit, on a shop card or in the season prizes stays blank, and every taker gets a new one with the full value.
  - `/bsp credit ticket <serial>` shows one ticket. `/bsp credit void <serial>` ends it.
- **Settings:**

  | Setting | Default | What it does |
  |---|---|---|
  | `tetrium_crucible.nuggetsPerOre` | 4 | Nuggets per ore with no Yield Upgrade. |
  | `tetrium_crucible.nuggetsPerUpgrade` | 2 | Extra nuggets per ore for each Yield Upgrade. |
  | `tetrium_crucible.lavaTank` / `combination_forge.lavaTank` | 8,000 | Lava tank size, in mB. |
  | `tetrium_crucible.lavaPerOre` | 100 | Lava per ore, in mB. |
  | `combination_forge.lavaPerJob` | 200 | Lava per ingot or plate, in mB. |
  | `scoring.serverTimeoutMinutes` | 30 | A server not heard from for this long drops off the board until it's back. |
  | `scoring.ledgerSweepPerMinute` | 4 | Placed totems checked each minute in chunks nobody visits (0 off). |

- **Kit conditions.** In the kit editor the **Needs** line has "Owns no Shatter Totem anywhere on the network". On a network, REDEEM asks the database afresh, and while it's down such a kit is refused with "try again shortly".
- **Land Office.** BSP-Core admins and operators get an **ADMIN** key on a Land Office block, which opens Region Guard's plot list for that office. This needs Region Guard API 8 (banned blocks need API 7).
- **New in the config and commands** (1.11.1):
  - `restrictions.activeDimensions` is written at every start with the names of every dimension the server has, ready to copy into `allowedDimensions`.
  - `/bsp score why <player>` shows what a score is made of.
  - A totem wipe also clears a score set by hand.
- **On a network.**
  - New tables `credit_kinds`, `credit_tickets`, `totem_losses` and `steal_watch`, and a `conditions` column on `kits`. All are added by themselves at start.
  - Every credit spend is made in the database in one step, so two servers never spend the same credit. While a server's database is away, credit can't be spent there: players see "Try again shortly".
- **For the other BSP mods:**
  - `com.mrgregles.bsp_core.api.BspStoreCredit` lets a mod charge a price to a player's tickets before their bank (`credit`, `spendable`, `charge` with a `Receipt`, `refund`). BSP-Teleport uses it for teleports from its next update.
  - `BspKits` reports kit conditions.
  - Region Guard needs API 7 for banned blocks and API 8 for each office's own plots.
  - The full details are in `docs/API.md`.
