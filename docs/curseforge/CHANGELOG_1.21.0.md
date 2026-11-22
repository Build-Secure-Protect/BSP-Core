# BSP-Core 1.21.0

Dress your totems! With the VIP rank, every totem you own can wear a look of its own: any colour, a gradient, or a body you paint pixel by pixel, your own player head, and one of thirteen hats. Hats can also be bought one at a time. And the one player at the top of the score board gets something no rank can buy: all their totems glow gold and give off sparks. This file also covers everything since 1.15.0: the **Bounty Board** (1.19.0), the **Plasma Tank Mob Chamber** with thirteen mob powers (1.17.0), the **Compass Tuner** (1.20.0), a Bank & Market page for staff (1.18.0), chunk loading through tanks (1.20.1, 1.20.2), Cloak and X-ray with shader packs, and a long list of automation fixes (1.16.1).

**Existing worlds:** nothing to do by hand.
- A Totem Compass upgraded before 1.20.0 hands its upgrades to the first Compass Tuner it goes into, once, and keeps them.
- A Plasma Tank filled before 1.17.0 keeps its powered plasma (in the totem pool) and the rest as plain plasma.
- A projector that held chunks before 1.20.2 holds them again once fresh plasma arrives from its totem; its CHUNKS tab says so meanwhile. Fed from another totem, Anchor must be chosen again there.
- Your own key bindings stay as you set them. Keys you never changed move to the new defaults (1.16.2, below).

**Every player needs 1.21.0 as well as the servers** (the network protocol changed). An older client is told to update when it joins. On a network, update every server together.

## For players: totem looks

- **Open a placed totem of yours and choose LOOKS.** With the VIP rank you dress that totem:
  - **Body:** one colour (mottled like the gold totem, fading into the stone toward the knees), a gradient of two or three colours running top to base or side to side, or **PAINT**. Pick colours on a colour square with a hue strip, or from eight quick colours.
  - **The paint bench:** pick a part (head, body, knees, arms, hands) and paint its faces unfolded, one pixel on the bench for one pixel on the totem. 16 colours, PEN, FILL, PICK, UNDO, and a mirror that paints left and right together. It starts from the body as it was, creeper face and all.
  - **Head:** the totem's own face, or **your own player head**, hat layer included.
  - **Hat:** top hat, halo, flat cap, beer cap (with two cans and straws), sports cap, beanie, wizard hat, devil horns, cowboy hat, Viking helmet, party hat, chef's hat or propeller cap. Each in its own colours or the body's. The halo bobs and glows, the wizard hat's stars glow, the propeller spins.
- **A turning preview** shows the look before **SAVE** puts it on (and SEE STEAL shows how it pulses when raided). You can change it again after 10 seconds. **RESET** makes it plain gold.
- **SAVED LOOKS** keeps up to five looks to put on your other totems with one click.
- **Each totem has its own look**, and keeps it when you pick it up and place it again.
- **A look belongs to its owner.** A stolen totem is plain for its thief.
- **A totem being stolen still pulses red**, and a thin gold band round its base shows its state whatever its colours.
- **If your VIP ends**, your totems go back to plain gold until it returns; then their looks come back.
- **Hats on their own.** A hat can be bought by itself. Without VIP, your newest hat goes on every totem of yours that has no look, and the LOOKS tab lets you switch between the hats you have (or wear none). Some hats may be season prizes only.
- **Decoy Totems** dress like your nearest totem for everyone else.

## For players: the Score Leader

- **The player with the most points glows.** Every totem they own wears a thin, breathing gold glow, with gold sparks drifting up from the base.
- If two players share the most points, nobody glows. It moves to the new leader as soon as the board changes.
- Seen within 32 blocks; walls hide it. Shader packs draw the glow as light.
- Don't want the sparks? Turn off `leaderSparks` in the client config (the glow stays). Minecraft's Particles setting thins them too.

## For players: the Bounty Board (1.19.0)

- **Put a price on a player's head.** Pay a reward in pieces (from your bank) or in Shatter Coins of one tier, with an optional **bonus** paid only if the kill comes within a window you pick. Add notes that only hunters read, and choose how long it runs, from 1 hour to 30 days. The market tax on reward and bonus goes into the lottery pot.
- **Hunt.** Accept up to 5 bounties (never on yourself, your own, your teammates or allies). Kill the target with your own hit, arrow or other shot, or knock them into a fall, lava or the void within 5 seconds of your hit, and every bounty you accepted on them pays at once: pieces to your bank, coins to your Collect box. A mob's or a pet's kill never counts.
- **Unclaimed bounties** give the reward and bonus back when they end. You can cancel your own while nobody has accepted it.
- Staff and players still under new-player protection can't have bounties on them. The target is told when a bounty is placed on them and when it ends.
- **The Bounty Board block:** an old western notice board, two blocks wide and two high, with four poster screens showing WANTED posters (the target's own face, the reward, the time left, a BONUS tag), a flickering lantern, and BOARD, PLACE and HUNTS keys on its shelf. Crafted from dark oak, glass panes, a Basic Control Circuit and a gold ingot.
- **Its screen:** WANTED (every bounty, searchable, sorted by reward, ending or newest), MY HUNTS, MY BOUNTIES, a page for each bounty with ACCEPT, ABANDON or CANCEL, and **PLACE A BOUNTY** in four steps (who, reward, notes, time and bonus).
- **On a network** a bounty placed on one server shows on every server and can be claimed by a kill on any of them, and is paid exactly once.
- **Most Wanted on Score Screens:** a Bounties board (the biggest price first), and a new Most Wanted look for any board, with the top line as a large card.

## For players: the Plasma Tank Mob Chamber (1.17.0)

- **The Spawn Grate** turns a Plasma Tank into a Mob Chamber. Once the tank is 15% full it spawns a mob every 30 seconds (faster in a wider tank, down to 14 seconds at 12 wide), picked from what its biome and structure spawn. The mobs stand still and the plasma burns them. Their drops go into a 27-slot loot store that hoppers and pipes can empty through a Tank Port.
- **The CHAMBER tab** on the tank screen: when the next mob comes, which mobs can spawn with their chances, the loot store, an ALL / HOSTILE / PASSIVE switch, and the power picker.
- **Thirteen mob powers.** Feed a drop and pick level I, II or III, and the chamber tags plain plasma with its power. A Projector fed with that plasma gives the power out in a cube (16, 24 or 32 blocks), beside its totem powers:
  - For your team: **Haste**, **Fire Ward**, **Tide** (water breathing), **Mending** (held tools repair themselves).
  - For the area: **Growth** (crops grow faster), **Keep** (dropped items last 20 minutes), **Undead Ward** (no zombies or skeletons), **Lantern** (no monsters).
  - For outsiders: **Wither**, **Snare** (slowness), **Scorch** (set alight), **Warp Ward** (no teleporting in or out), and **Wireframe** (your team sees intruders through walls as pink wire figures).
- **Plasma pools.** A tank keeps plain, totem and each mob power's plasma apart, shown as coloured bands. The new **Tank Extractor** sends one pool into the cables. A broken and rebuilt tank keeps its pools and powers.
- **Wither Bones** keep a wither skeleton's power apart from a bone's.
- JEI and EMI have a Mob Chamber page for every mob a chamber can spawn, and the guide book has an entry.

## For players: the Compass Tuner (1.20.0)

- **Compass upgrades live on the tuner,** so losing a compass no longer loses the coins spent on it. Buy Across, Up or down and Cooldown on the tuner, plus **Tune speed** (a tune takes 2 hours, down to 30 minutes).
- Put a Totem Compass in the middle of its dial and press TUNE. A tune uses 16 lapis, 16 nether quartz and 16 redstone, and FE. A Plasma Injector speeds it up to three times. It keeps counting while the area is unloaded.
- Only you and your team can open, use or mine it, and nothing else moves or breaks it. One per player.
- **The Totem Compass sells no upgrades.** An untuned compass tracks at the base reach (100 across, 10 up or down) with the 10-minute cooldown.
- The block: a slate plinth with a turning gimbal of brass and copper rings, hoppers of lapis, quartz and redstone, a plasma bulb, and a countdown screen.

## For players: other changes

- **Totem radiation detected** (1.16.0). While tracking, if a rival totem's radiation reaches you but the totem is out of your reach, the needle turns slowly and a chip above the hotbar says TOTEM RADIATION DETECTED. Search until your reach covers it.
- **Chunk loading through tanks** (1.20.1, 1.20.2). A projector fed through a Plasma Tank holds chunks under its totem. A projector holds chunks only while everyone who may change it may also upgrade the totem its Anchor comes from. The CHUNKS tab says why when it can't. **Plasma Batteries can't hold Anchor.**
- **Lava goes further poured into the tank** (1.17.0): a bucket lasts 25 ores in the Tetrium Crucible and 12 jobs in the Combination Forge. Burnt in the fuel slot, it gives 10 and 5.
- **New default keys** (1.16.2), so nothing clashes with the pack's other mods: Siege 0, accept or decline a Recall Home / End, accept or decline a team invite Insert / Delete. X-ray stays on X. Every BSP key is listed once in Controls.
- **Shatter Totems can't be placed or dropped inside Compact Machines rooms** (1.17.1). Carrying one in is fine. A totem found standing in a room goes back to its owner.
- **No mobs spawn naturally inside a Plasma Tank.**
- **A Stall Display linked to a shop made only of sections** shows the sections' items (1.17.2).

## Fixes

- **Cloak and X-ray with shader packs.** Walking into a Cloak fades the base in from clear to solid, never black. X-ray's see-through rock stays faint glass, and its gold and red marks stay faint and lit.
- **Automation on BSP machines** (1.16.1), checked against AE2, Refined Storage, Mekanism, EnderIO, Create, XNet, Integrated Tunnels, Functional Storage, Sophisticated Storage, Industrial Foregoing and hoppers:
  - Inputs a machine can't use come back out, so they never block a slot.
  - Fuel and water slots keep what they'll still use, and give back the empty buckets.
  - Multiblock ports keep their pipe connections, and every face answers pipes the same way.
- **Your other mods' player data is left alone.** BSP-Core only ever touches its own keys in a player's saved data (Waystones lists included) (1.19.1).
- **Siege and the Recall cooldown** carry over between servers correctly (1.19.1).
- **A Projector fed from a Plasma Tank knows its team** (1.17.0): the tank remembers who fed it, so Ward and Alarm leave the owners alone.
- **Power levels** on the tank and Accelerator screens and in Jade show the right number (1.17.0).
- **A power stops arriving** from a Plasma Accelerator or Decelerator once its plasma has gone through (1.20.2).

## For server owners

- **Totem looks:**
  - Give the VIP rank the LuckPerms permission `bsp_core.looks`. It includes every hat.
  - Sell or award a single hat with `bsp_core.looks.hat.<hat id>`. A store product runs `lp user {uuid} permission set bsp_core.looks.hat.<hat id> true`. Hat ids: `top_hat`, `halo`, `flat_cap`, `beer_cap`, `sports_cap`, `beanie`, `wizard`, `devil`, `cowboy`, `viking`, `party`, `chef`, `propeller`.
  - `/bsp looks show` and `/bsp looks clear` work on the totem you hold or look at. `/bsp looks show <player>` and `/bsp looks clear <player>` cover a player: the clear takes every look of theirs off, on every server, and empties their saved looks. Every look put on or saved is logged with the player's name.
- **The Bank & Market page** (1.18.0). A BANK & MARKET button on the admin panel's Players tab shows a player's pieces, coins, week and full bank log, and the lots and requests they have on the market, each with CANCEL. **EDIT TRANSACTIONS** refunds any bank row in full or in part (coin conversions and payments both ways), and the player is told, even if they're away. Moderators need `bsp_core.admin.bank` to refund and cancel.
- **Bounties for staff** (1.19.0): the admin panel's BOUNTIES tab puts up server-paid bounties and removes (with or without a refund) or extends any bounty. Moderators need `bsp_core.admin.bounty`.
- **Settings:**

  | Setting | Default | What it does |
  |---|---|---|
  | `looks.exclusiveHats` | (none) | Hats the VIP rank does not include: they come only with their own permission. |
  | `leaderSparks` (client) | true | The Score Leader's sparks for that player. |
  | `bounty.maxDays` / `minPieces` | 30 / 1,000 | The longest bounty, and the smallest reward in pieces. |
  | `bounty.maxOpenPerPlayer` / `maxAcceptedPerHunter` / `notesMax` | 5 / 5 / 256 | Bounties per placer, per hunter, and note length. |
  | `compassTuner.*` | | `maxPerPlayer`, `energyCapacity`, `fePerTick`, `tuneMinutes`, the Tune speed prices and the materials. |
  | `mob_chamber.*` | | The spawn timer, mobs per chamber and per server, burn, which drops tag which power, power reach, Growth, Mending and Keep. |
  | `tetrium_crucible.lavaBucketTicks` / `combination_forge.lavaBucketTicks` | 3,000 | How long a lava bucket burns in the fuel slot. |

- **On a network.** New tables, all made by themselves at start: `look_owners` (1.21.0), `bounties`, `bounty_hunters` and `bounty_closed` (1.19.0), and new `bank_log` columns (1.18.0).
- **For the other BSP mods:** new API for BSP-Server Utilities' staff web panel (`BspMarket`, `BspLottery`, `BspBank`, `BspBounties`) and `BspWarpWard` for teleport mods. The details are in `docs/API.md`.
