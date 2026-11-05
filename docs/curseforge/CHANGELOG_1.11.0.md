# BSP-Core 1.11.0

Your totem can work your redstone now. **Tripwire** is a new totem power: while an outsider is near, the placed totem gives a redstone signal, so your doors shut, your lamps light and your traps fire for strangers and never for your friends. This file also covers 1.10.1, which made totem levels count on every server of a network.

**Existing worlds:** nothing to do by hand. **Every player needs 1.11.0 as well as the servers** (the network protocol changed): update the pack and the servers together. An older client is told to update when it joins.

## For players: Tripwire

- **Where it is.** On the totem's Walls path, after Alarm. It opens at Tier II once Alarm reaches level 2.
- **Who sets it off.** Any player except you, your teammates, your allies and the friends on the totem's access list. Players in creative or spectator mode never do.
- **Four ranges, one per tier.** The area is a cube round the totem, like Alarm's, covering every height within the range:

  | Range | Opens at | Price |
  |---|---|---|
  | 16 blocks | Tier II | 2 Gold Shatter Coins and 6 XP levels |
  | 32 blocks | Tier III | 2 Diamond Shatter Coins and 9 XP levels |
  | 64 blocks | Tier IV | 2 Netherite Shatter Coins and 12 XP levels |
  | 128 blocks | Tier V | 2 Illyrium Shatter Coins and 15 XP levels |

- **The signal.**
  - It is full strength (15) out of all six sides of the totem.
  - It is weak power: dust, repeaters, lamps, doors and pistons touching the totem react, but the block it stands on doesn't pass it on.
  - The totem looks once a second and keeps the signal on for 3 seconds after the last outsider leaves, so doors don't flap.
- **Picking the range.** Tripwire's card shows "Range: 32 blocks" under Buy, with a **NEXT** key.
  - NEXT steps through the ranges you've bought, then off, then back to 16. So a 128-block totem can still guard just the front door.
  - Buying a new range puts it straight into use.
  - Only you, your teammates and friends with the Upgrades switch see the range or can change it. Visitors can't tell what it's set to.
  - The setting stays with the totem when you move it.
- **Lamp studs.** Every Shatter Totem and every Decoy Totem has eight small lamp studs round its base. While Tripwire is giving its signal they glow bright red. They're on every totem, so a raider can't tell from outside which ones have Tripwire.
- **Thief's Step** hides you from a Tripwire set to your Thief's Step level or lower. Thief's Step 2 walks past a totem set to 16 or 32, but not 64.
- **Good to know.**
  - A totem only watches while its chunk is loaded: while someone is near, or while Anchor keeps it loaded.
  - An unclaimed totem gives no signal.
  - Redstone dust beside any totem or decoy now bends towards it.
- Tripwire's orb round the base is magenta. The guide book has two Tripwire pages in Powers in Detail.

## For players: from 1.10.1

- **Totem levels count on every server of the network.** Extra Homes and Swift Travel used to count only on the server where your totem stood, so the spawn hub showed fewer homes and a longer cooldown. Your totems on the other servers count everywhere now.
- A stolen totem keeps its levels for its new owner straight away.

## For server owners

- **Settings** in the `[effects]` section:

  | Setting | Default | What it does |
  |---|---|---|
  | `tripwireRanges` | 16, 32, 64, 128 | The range of each level, in blocks. |
  | `tripwireHoldSeconds` | 3 | How long the signal stays on after the last outsider is seen. |
  | `thiefStepHidesFromTripwire` | 1, 2, 3, 4 | For each Thief's Step level, the highest Tripwire range level it hides from (0 hides from none). |

- **On a network** (from 1.10.1): the shared `totems` table gains a `levels` column, added by itself at start. Each server writes its totems' levels when they change, and reads the others' every few seconds.
- **For the other BSP mods:** the upgrade `TRIPWIRE` (levels 1 to 4). Every placed totem reports itself as a redstone source and gives 15 while its Tripwire is set off. The range setting is `TripRange` and the signal `TripSignal` in the block entity. No other mod needs a change to run with 1.11.0.
