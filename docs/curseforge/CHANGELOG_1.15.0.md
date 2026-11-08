# BSP-Core 1.15.0

Teams get a fairer deal with coins! Big teams can't just out-press everyone any more: each member owns fewer Coin Factory slices, coins remember the team that pressed them, and totem upgrades cost a little more the bigger the team. A team still has an edge, just not five times one. On top of that the Totem Compass has a reach you can upgrade, and every totem gives off **totem radiation**, so a small totem is hard to find and a big one easy. New players' totems give off none at first. This file also covers 1.14.1: Coin Vault interest that rises with your hoard, prize rolls every 12 hours, and compass cooldowns that follow you between servers.

**Existing worlds:** nothing to do by hand.
- Coins pressed before this update carry no team stamp and pay on any totem.
- A player who already owns more factory slices than their team allows keeps them all; the newest ones pause.
- Every compass starts at the bottom of both reach ladders (100 blocks across, 10 up or down).
- Each player's main totem is picked by itself the first time it's needed, so a totem that already has Anchor 2 or 3 stays main.

**Every player needs 1.15.0 as well as the servers** (the network protocol changed). An older client is told to update when it joins. On a network, update every server together: 1.14.x and 1.15.0 servers can't run side by side.

## For players: coins in a team

- **Factories per player.** Alone you may own 10 Shatter Coin Factory slices. In a team each member owns fewer:

  | Team | Slices each | Team total |
  |---|---|---|
  | Alone | 10 | 10 |
  | 2 | 8 | 16 |
  | 3 | 7 | 21 |
  | 4 | 6 | 24 |
  | 5 | 5 | 25 |

  - It follows your team as it is, across every server: leave the team and you're back to 10.
  - Joining a team never takes factories away. If you own more than your number, your newest slices pause until you're back within it.
  - The factory screen shows "Owns 6/6 (team of 4)", and a paused slice says "Paused: over the owner's factory limit".
- **Team stamps on coins.** Every coin a factory presses carries the team its owner is in, or "outside a team". The tooltip shows it ("Pressed for team Iron Wolves"), and coins with different stamps don't stack.
  - **On your own totem** any coin pays, and the ones from outside your team go first.
  - **On a teammate's totem** only your team's coins pay, and coins with no stamp at all (from loot, or pressed before this update). The card tells you how many to re-press.
  - Banks, vaults, shops and trades keep the stamp. Change and coins a shop pays you carry one too.
- **Re-pressing.** Put a coin from outside your team, or another team's, into a factory slice's blank slot (shift-click works). It goes through again in **half the time and for half the RF** and comes out stamped for the factory owner's team. The lane turns violet while it works, and a coin rides the belt instead of a blank. Coins that already pay for the team aren't taken.
- **Team surcharge.** Tier raises and upgrades on the totem of a player in a team cost a little more: **6% for each member after the first** (+6% in a team of 2, up to +24% in a team of 5). The extra is paid in Shatter Pieces from the buyer's bank, 1,000 Pieces per copper coin of the price times the percent: raising Tier I to II in a team of 4 costs 720 Pieces on top of the 4 Copper coins and the XP. The totem's card shows it beside the price ("+18% team", "720 Pieces"). Compass upgrades cost the same in a team or alone.

## For players: the compass and totem radiation

- **The Totem Compass has a reach.** Tracking finds rivals only within so many blocks of you across the ground and so many above or below.
  - A compass starts at 100 blocks each way and 10 up or down.
  - Two ladders of six upgrades, WIDER and DEEPER, go up to **2,500 blocks** across and **150** up or down. Each level costs 10 Copper, 10 Gold, 10 Diamond, 10 Netherite, then 20 and 40 Illyrium coins.
  - The reach stays on the compass, between servers too, and is lost with it.
  - The screen's **REACH panel** draws your reach from the side, with the next level of each as a dotted outline and the price under each key.
  - Pips on the compass's icon show its levels: turquoise along the bottom for across, gold up the right edge for up or down.
  - With no rival in reach the needle spins. Pointing home has no limit.
- **Totem radiation.** Every placed totem gives off harmless totem radiation (nothing to do with Voltaic's), and a tracking compass sees a rival only as far as that radiation reaches.
  - A totem's level is its tier steps (Tier I 0 to Tier V 4) plus every upgrade level it has bought.
  - A Tier I totem with nothing bought gives off none and is never found. Level 7 is seen from 9 blocks, 27 from 127, 63 from 689, and 120 or more from 2,500.
  - In the outer half of that distance the needle points beside the totem, not at it, so two bearings don't cross on it until you're close.
  - The totem's screen has a radiation strip on every tab: the level, a bar of how far it's seen, and an **i** that explains it.
- **Totem Radiation Coating.** A tin, crafted 4 at a time from 1 Pure Illyrium Dust, 4 Lead Plates and 4 Tetrium Dust.
  - Use it on a Radiation Shielding Panel, Window or Door to coat it in a turquoise glaze (one tin a block, one for both halves of a door).
  - Whole walls of coated shielding round a totem each take a fifth off how far it's seen, up to 4 layers. A box with a gap counts nothing, and a door counts only while it's shut.
  - The coating can't come off, and a coated block drops still coated.
- **New players are hidden at first.** While a totem's owner is new, their totems up to Tier II give off no radiation at all. The server picks what ends it: a BSP-RankUp rank (Sentinel to start) or hours of play (24). The totem's strip says "PROTECTED until the owner reaches Sentinel".

## For players: the Coin Vault (from 1.14.1)

- **Interest rises with the hoard.** Every 10 coin value in your vaults earns 1 XP level and 3 Tetrium Ingots a day, and every 32 value in Netherite and Illyrium coins earns 1 Illyrium Ingot a day. Your whole hoard sets a multiplier: x1.5 from 32 coin value, x2 from 128, x3 from 512.
- **Prize rolls.** While you keep 32 coin value or more in vaults, a prize roll builds up every 12 hours, online or offline, up to 6 waiting. Redeem rolls each one from the server's prize table. A rare prize plays the level-up sound and tells you its odds.
- **The INTEREST tab** shows your hoard on a ladder of the steps, what you earn a day, what's waiting, and your prize rolls with the time to the next one.

## For players: fixes

- **Your main totem is the right one.** A totem that was gone (a world edit, a region reset, a wipe) could stay someone's main totem for good, so their real main totem refused Anchor past level 1. Your main totem is your first one on the server, kept while it's yours, carried or moved; if it's stolen, your next one takes over.
- **A new Totem Compass can't skip the cooldown** (1.14.1). The wait after tracking belongs to you as well as the compass, on every server of the network.
- **Compass times read the same on every server** (1.14.1), so a compass carried to another server keeps its tracking and cooldown.
- **Tinkers' Construct and Thermal melt Tetrium ore to 4 nuggets** (1.14.1), the same as the Tetrium Crucible.

## For server owners

- **The admin panel's RADIATION tab** (a seventh tab) sets what ends the new-player protection: RANK (RankUp's ladder, lowest first; click the rank that ends it), PLAYTIME (hours) or OFF. Beside it are this server's totem radiation numbers with − and +, and a chart of how far each level is seen. Moderators see it read-only.
- **The admin panel's VAULT tab** (1.14.1) edits the prize table: FROM MY HAND takes the held stack (enchantments and name included), PICK FROM LIST opens an item browser, and each prize's weight shows as "about 1 in N". Up to 24 prizes, shared by every server of a network.
- **Settings:**

  | Setting | Default | What it does |
  |---|---|---|
  | `factory.maxPerTeamSize` | 10, 8, 7, 6, 5 | Slices each player may own, by team size (larger teams use the last). |
  | `factory.maxPerPlayer` | 10 | A ceiling for everyone: raise `maxPerTeamSize` too if you raised this. |
  | `team.upgradeSurchargePercent` | 6 | Extra cost per team member after the first (0 turns it off). |
  | `compass.rangeAcross` / `rangeUpDown` | 100 ... 2,500 / 10 ... 150 | The reach ladders (the first entry is a new compass's). |
  | `compass.rangeUpgradeCoins` / `rangeUpgradeCoinTiers` | 10, 10, 10, 10, 20, 40 | The price of each reach level. |
  | `totemRadiation.enabled` | true | Totem radiation on or off. |
  | `totemRadiation.fullLevel` / `maxSeenAt` | 120 / 2,500 | The level seen from the furthest distance, and that distance. |
  | `totemRadiation.blurFraction` | 0.2 | How far off the bearing is at the edge. |
  | `totemRadiation.layerCut` / `maxLayers` | 0.2 / 4 | The coating's cut per layer, and most layers. |
  | `totemRadiation.checkRadius` / `checkSeconds` | 32 / 30 | How far out and how often the coating is counted. |
  | `vault.interest.*` | | `stepValues`, `stepMultipliers` and the daily caps (1.14.1). |
  | `vault.prizes.*` | 12 h, 32, 6, 5 | `hoursPerRoll`, `minimumCoinValue`, `maxWaiting`, `rareWeight` (1.14.1). |

- **On a network.** New tables and columns, all added by themselves at start: `bank_coin_stamps`, `factory_slices.placed_ms`, `protection_seen`, `compass_waits`. The team factory limit counts slices on every server, and the new-player protection and the prize table are one record for every server.
- **For the other BSP mods:** a coin pressed by a factory carries a `PressTeam` tag. A mod that keeps coins and hands them back should hand back the same stacks. The details are in `docs/API.md`.
