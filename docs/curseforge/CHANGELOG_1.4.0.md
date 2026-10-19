# BSP-Core 1.4.0

Everything since 1.0.1: a bank and shops with their own money, land for sale, plasma that crosses worlds, teams, kits, boards on the Score Screens, the hooks the other BSP mods needed, and a long list of fixes. The guide book covers all of it.

**Existing worlds:** new settings are added to `serverconfig/bsp_core-server.toml` by themselves (`[bank]`, `[shop]`, `[team]`, `[kit]`, `[proxy]`, `[storage.redis]`). Nothing that exists changes its value. The first login of every player after the update gives them their bank account with its starting pieces. Servers on a shared database get the new tables at start; run `/bsp storage migrate` once on a server that had local kits before it joined the network.

## Shatter Pieces, the bank and shops

- **Bank accounts and Shatter Pieces.** Every player has a bank account: a balance of Shatter Pieces, the everyday money that is never an item, and the coins they keep in the bank by tier. New players receive 500 pieces once, on whichever server of the network they join first. Coins deposited at a bank are kept by tier, safe from theft, or converted to pieces (1,000 a Copper coin; depositing is the default, converting asks twice).
- **Shatter Bank.** The ATM: two blocks tall, admin-placed, unbreakable, with a beige bezel, a black phosphor screen and one column of keys. The account, paying players (by name, across the network, with a confirmation above a threshold and a key to refuse payments), the transaction log with filters and pages, deposit and withdraw coins. The block shows ACCOUNT OPEN while someone uses it, with a beep and a tray clunk.
- **Portable Banker.** A handheld that opens the same screens anywhere. Coins are converted and withdrawn at a bank only. Its Shopping Network key buys remote shop access once, for 12 Netherite coins; the unlock lives on the account.
- **Safety Deposit Box.** An admin-placed cabinet; right-click for your own box, the same box from any of these blocks on the network. Rented by the day in pieces or coins, 6 slots at first and 3 more per purchase up to 18. When the rent runs out the items are kept but locked until the rent plus a holding fee is paid. Shatter Totems cannot go in. The keys say what they charge.
- **Shops.** A shop is a tree of pages of lots: a stack, a buy price and a sell price in pieces or a coin tier, stock for the server and per player with a restock countdown, instant buy, hidden lots, sub-sections, rotating shops that draw from a pool, special-offer sections that cut prices until a time, buy-only shops, shops flagged for the Shopping Network. Shop screens: cards of seven by three with the item, "buy N" in gold, "sell N" dim, a stock badge and a star for instant buy; left click buys with -1, +1, +16 and Max, right click sells, shift-right sells every full stack. Cards size themselves to the longest price.
- **The Workbench.** `/bsp shop` or the Admin key under any shop: the shop list, the live page of cards (click a card to edit it, click an empty slot to add there, Hand to slot), the shop's settings and style, opening hours (always, server clock, game time), and the network flag. Moderators look and change nothing.
- **Vendor Post, Market Stall, Stall Display.** Admin-placed shop fronts: a steel post with a figure of your choice (villager, wandering trader, golem, piglin, allay, witch, pillager, sniffer, armour stand) that opens the shop when clicked; a two-block stall in eight styles (timber booth, vending terminal, boiler booth, player-built, blackstone shrine, street cart, dockside, mage's pedestal), out of order while no shop is assigned; a display stand that floats the shop's wares above it one after another, with a pinned item if you like. Staff sneak and right-click any of them for the setup screen.
- **Shopping Network.** On a Portable Banker whose account has the unlock, every shop flagged remote opens from anywhere.
- **The Land Office.** An admin-placed block where players buy land from BSP Region Guard: a map of the land around them drawn from the terrain, plots for sale in amber, open ground dashed, their own claims in green and the ground they outlined with the wand in white; click a plot for its size, price and distance; BUY, ENLARGE and GIVE UP. Needs Region Guard.

## Plasma across worlds

- **Plasma Accelerator and Decelerator.** Seven Accelerator Frames round an Accelerator Intake, standing up with the middle empty, form an accelerator: a thick hull octagon with a copper track, eight coil heads and a black sphere turning in a brass cage in the hole. A Decelerator Port behind a Decelerator Core forms a decelerator. A Link Card written on an intake and put into a core links the two across any worlds of the server; once a second the intake sends what it holds in equal shares to every linked decelerator with RF and room, and the port sends it on to the bases, chargers, injectors and tanks its cables reach, with the totem's powers, its access list and its Anchor. A totem in the Overworld keeps a Nether projector's chunks loaded through a link. Both ends draw RF; without it nothing moves.
- **The Worlds board.** Right-click an intake empty-handed: a column for every world in play, the accelerator as a gold pip, every link as a pip in its world's column with a line lit while plasma passes, and under each its position and state, the in and out rates and the RF. WAKE and UNLINK keys. Jade lines on all four blocks.
- **Link Expander.** A Decelerator Core takes more links with expanders in it.
- Plasma Tanks carry the totem's powers: plasma fed from a totem keeps its Overclock and the rest in the tank and passes them to injectors with every mB it gives, totem or no totem, until that plasma is used up.
- Recipes for the four blocks, the Link Card and the Link Expander; JEI and EMI pages; the guide entry "Plasma Across Worlds" with pictures of the finished machines.

## Teams

- **One team per player,** a leader and up to five members, a name and a colour. A team gives its members full access to each other's placed totems: open the panel, buy upgrades and raise tiers (the buyer pays), change chunks, open plasma machines and projectors, see through the cloak, and the Ward and Alarm leave them alone. Teammates cannot steal from each other and the Totem Compass does not point at a teammate's totem.
- **The Team screen.** A violet board with MEMBERS (online here, elsewhere on the network, or offline with when they were last seen; how many totems each holds), INVITE (players online and in no team, one key each), MESSAGES (the team's board: the leader's pinned note on top, the last fifty lines with a mark on the new ones, a line to type), TEAM CHAT, and for the leader SETTINGS (rename, recolour, hand over, remove, pin) and DISBAND. Anything that cannot be undone asks SURE?. It opens from a TEAM tab on your own or a teammate's totem, a TEAM tab on your held totem's screen, a TEAM button above the inventory, or a key you bind.
- **The invite card.** An invite arrives as a card at the top right with the inviter's face, the team, the two keys (Y and H) and the seconds left, with a chime and a clickable chat line. Cards stack; the inviter is told the answer.
- **Team chat and the board.** With TEAM CHAT on, what you type goes to the team as "[Team] name: text" in the team's colour, with a pill above the chat while it is on; `/t <message>` sends one line. Members who were offline find the lines on the board and hear how many are new at login.
- **One team across the network.** On a shared database the team is one record on every server: joins, leaves, lines and the pinned note reach every server as they happen, and the member list says "on server-2" for a teammate online elsewhere.
- **Team teleports.** With BSP-Teleport installed, every online teammate's row has a TELEPORT key; the teleport mod moves you under its own rules.
- Admin commands `/bsp team list | info | disband | kick | rename | lead`.

## Kits

- **Kits.** Admin-made bundles of up to 36 stacks, enchantments and all. Each kit has rules: given at the first login on the network or on chosen servers, or not by itself; shown in the players' browser or hidden; once only or again after a cooldown; a price in pieces; LuckPerms ranks that may take it; servers it may be taken on.
- **The kit browser.** A KITS button beside TEAM above the inventory, a key you bind, or `/kits`. Every kit is a card: REDEEM on a ready card; a card you took lately greyed with the time left; a locked card says why (the rank to reach, with your step on RankUp's ladder; the server to join; the price against your balance; the slots to free). A kit given to you that did not fit, or came while you were away, waits in violet with TAKE IT. Click a card's picture for what is inside and every condition ticked or crossed.
- **Nothing is ever lost.** A kit never goes into an inventory that cannot take it, and a price is charged only after that check. Kits are delivered once your inventory is settled after login (after BSP-InventorySync's restore where it is installed), so a sync never wipes one.
- **The KITS tab** of the admin panel: the kit list and the chosen kit's summary, NEW KIT, EDIT, GIVE TO… and DELETE, and the editor: the kit's slots (FROM HAND and FROM INVENTORY bring in enchanted items), an item browser with a search line and a chip per mod, and the settings rows. SAVE writes the kit to every server; TEST ON ME tries it with no record. `/bsp admin kits` opens the panel on the tab; `/bsp kit list | info | give | reset | delete | test` from the console.
- **One set of kits across the network**: kits and every record live in the shared database; a kit taken on one server is taken everywhere, and a kit limited to one server cannot be taken elsewhere.
- Other mods give kits through the API; BSP-RankUp's KIT reward kind hands a kit out when a rank is reached.

## Score Screens and seasons

- **Boards from other mods.** A Score Screen can show a board another mod registers (BSP-Teleport's "Home servers", say) in a Ledger, Tiles or Side column look, and **rotate** through the scoreboard, the rules, the prizes and every board, so many seconds each, with a fade and a row of dots.

## Your totem, kept safe

- **A totem placed by a logout comes back when you return.** A crash is a logout: the carried totem was placed where you stood. If it still stands untouched when you log back in on that server, it comes straight back to you. Mid-steal it stays, with a warning; taken, you are told so, and only then: a totem you destroyed in creative, picked up yourself or that was reissued says nothing.
- A totem left by a player who logs out in the air goes to the ground below them.
- A creative player can destroy a spare totem; it is forgotten instead of reissued.
- A totem in transit between servers is no longer reissued.
- **`/bsp totem wipe <player>`**: a clean slate for one player, for test accounts and support: carried totems taken, placed ones removed, every record forgotten, and a fresh Tier I totem handed over as on a first join.

## For the other BSP mods

- **Events and an API** (`com.mrgregles.bsp_core.api`): totems, steals, seasons, teams, kits, the bank, zones, transfers between servers, Recall across servers, and two teleport upgrades on the tree (Extra Homes and Swift Travel) that BSP-Teleport reads. Region Guard can keep totems out of a region; the Anti Totem Block has teleport rules for it.
- **A chat relay for the Velocity proxy** (`proxy.relayChat`, off by default) sends every public chat line to the proxy so BSP-Velocity Additions can show it on the other servers.
- **Redis** (optional, beside MySQL) makes every network change live: balances, deposit boxes, notices, steals, shops, teams and kits reach the other servers at once instead of at the next poll.

## Fixes

- The bundled MySQL driver is relocated under BSP-Core's own package, so the full jar starts on Mohist; the `-slim` jar without the driver is still built.
- The Integrated Dynamics squeezer recipes load.
- Cash Machine screens no longer draw text past their edges; the Safety Deposit Box, shop and workbench screens got a tidy-up pass.
- Other mods' keys no longer act inside BSP-Core's screens: a letter another mod had bound closed or replaced the screen.
- The placed totem's panel shows its buy keys to anyone the server would let buy, so a friend with the Upgrades flag can buy from the panel.
