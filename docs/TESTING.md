# BSP-Core manual test checklist (v0.1.0)

Run `./gradlew runClient` for single-player checks. For two-player checks run `./gradlew runServer`
(accept the EULA in `run/eula.txt`, set `online-mode=false` in `run/server.properties`), then join
from two clients, or use one `runClient` and one external client.

Enable the grant on the test server: edit `run/<world>/serverconfig/bsp_core-server.toml` and set
`grantTotemOnFirstJoin = true`, or use `/bsp totem give <you>` as an operator.

## Look (Guardian model)
- [ ] In the inventory the totem is a small kneeling creeper statue whose body pulses turquoise to blue; the orb stays dark green.
- [ ] Placed while owned: gold body, 1.5 blocks tall, face visible above the orb, glows in the dark.
- [ ] Unclaim it as an operator: body turns turquoise. Start a steal: body flashes gold and red; ends: back to gold.
- [ ] Raise Damage to 1 in the operator column: a pale red orb appears front-left of the plinth. Raise to 5: it turns vivid red. Resistance orb (blue) is at the back, Mining Speed (amber) front-right.
- [ ] Mine and re-place the totem: orbs and colour come back exactly as before.

## Orientation
- [ ] In the hotbar and inventory the statue's face and orb are visible (not its back).
- [ ] Held in first and third person the face is visible.
- [ ] Placed from any direction, the statue faces you.

## Placed upgrades and auras (needs Shatter Coins: `/give @s bsp_core:shatter_coin 64`)
- [ ] Open your placed totem: Fortify and Healing Aura rows show "Not active" with an "Upgrade: 5 coins" button; "Your Shatter Coins" shows your count.
- [ ] Buy Fortify level 1: 5 coins leave your inventory, the row shows level 1 / radius 1, a violet orb appears at the base, and a faint violet sphere of radius 1.5 surrounds the totem.
- [ ] Raise Fortify to 3 (operator + button is fine): sphere grows to radius 5.5.
- [ ] As a non-owner (assign the totem to a dummy, or use a second account) mine stone inside the sphere: noticeably slower than outside. As the owner: normal speed.
- [ ] Set off TNT inside a level-5 Fortify sphere: blocks in range survive; the totem is untouched.
- [ ] Buy Healing Aura, hurt yourself, stand inside the green sphere: health ticks up once a second. Step outside: it stops.
- [ ] Mine the totem and re-place it: both auras and their spheres are restored.
- [ ] Walk 60 blocks away and back: spheres render from far away and disappear when the totem is removed.

## Logout protection
- [ ] With a totem in your inventory, open the pause menu and press Disconnect / Save and Quit: a warning appears. "No" returns to the pause menu.
- [ ] Press "Yes": you disconnect, and on rejoining the totem is a placed block next to where you stood, not in your inventory.
- [ ] Repeat inside a Compact Machine or other non-allowed dimension: the totem is placed at your last Overworld position instead.

## Shatter Coins and the factory
Fast testing: in `serverconfig/bsp_core-server.toml` set `pressHours = [0.01, 0.02, 0.04, 0.08, 0.14]` (36 seconds for copper).
- [ ] BSP creative tab shows the factory, five blanks, five coins and three Speed Gears. Coin tooltips show values 1, 2, 4, 8, 16.
- [ ] Craft a Copper Coin Blank (8 iron nuggets around a copper ingot), then a Gold blank from it (4 gold ingots around the copper blank).
- [ ] Place the factory: it faces you; the press head sits raised. Right-click opens the screen with your name and "1/10".
- [ ] Put a blank in with no energy: status says idle. Feed RF (any mod's cable or a creative energy source): the red bar fills, the press starts, the head begins slamming, the blank shows on the die, and the timer counts down.
- [ ] When the timer ends a coin appears in the output slot and the next blank starts.
- [ ] Fit Speed Gears: sockets on the front light up (grey, gold, turquoise), the "Upgrades: -N% time" line updates, and the remaining time drops. Four Mk III gears show -75% (the cap).
- [ ] Leave the area or stop the server for longer than a press, then return: the coin is already made.
- [ ] Fill the output with 64 coins: status shows "Output full" and resumes when you take them.
- [ ] Click a side button to cycle Off / In / Out / In+Out. A hopper on an In face inserts blanks; a hopper under an Out face pulls coins; neither works on an Off face. Try a Mekanism transporter and an AE2 bus the same way.
- [ ] Break the factory: it drops itself and everything inside; your owned count goes down.
- [ ] Place factories until the limit (set `maxPerPlayer = 2` to test quickly): the next placement is refused with a red message.
- [ ] Buy a totem upgrade costing 5 with one Diamond coin (value 4) and one Gold (value 2): both are taken and one Copper comes back as change.

## Item basics
- [ ] BSP creative tab shows a glinting Shatter Totem, tooltip "Unclaimed".
- [ ] Tooltip shows "Owner: <name>" after `/bsp totem give`.
- [ ] Cannot stack two totems. Cannot put a totem in a shulker box or bundle.
- [ ] Putting a totem in a chest pulls it straight back into your inventory with a red message.

## Placing and mining
- [ ] Sneak + right-click a block places a glowing gold figure on a plinth.
- [ ] Right-click the placed block opens the panel showing the owner.
- [ ] Mining your own placed totem gives it back, owner intact.
- [ ] Another player mining it is refused with a red message. An operator can mine it.
- [ ] TNT or a creeper next to it leaves it standing. A piston cannot push it.

## Dropping
- [ ] Q-drop in the Overworld: it lies on the ground, cannot be destroyed by lava or fire.
- [ ] After 30 seconds on the ground it becomes a block where it fell.
- [ ] Another player cannot pick it up while on the ground; the owner can.
- [ ] Throw it into the void (End): it reappears instead of being lost.
- [ ] In a non-allowed dimension (e.g. a Compact Machine, or set `allowedDimensions` to only the Nether and test in the Overworld): Q-drop and placement are refused and the totem stays in your slot.

## Death
- [ ] Die to a mob or fall while carrying it: it drops as the 30 second ground item, no grave contains it.
- [ ] Die to another player: it is immediately placed as a block next to your body.
- [ ] Die in a non-allowed dimension: it appears at your last position in an allowed dimension.

## Stealing solo, as an operator
Make yourself an operator (`/op <you>` in the server console, or single-player with cheats). Right-click
a placed totem: a purple "Operator" column appears on the left.
- [ ] Type any name (e.g. `Dummy`) in the box and press Assign: the panel shows "Owner: Dummy" and a Steal button appears for you.
- [ ] Press Steal: HUD countdown appears. Walk 6+ blocks away: "Return to the totem! 15s" appears. Come back: it clears.
- [ ] Press Cancel steal in the column: the HUD shows "Steal failed".
- [ ] Press Steal again, then Finish steal: the panel shows you as the owner and the HUD shows "You stole Dummy's totem!".
- [ ] Press Unclaim: the panel says the totem is unclaimed. Mining it is refused for non-operators. Steal takes 60 s (or `unclaimedStealSeconds`).
- [ ] Press + on a buff: the level rises in the column. Mine the totem: the item tooltip lists that buff. Reset buffs clears them.

## Stealing (two players)
- [ ] Player B opens A's placed totem: a Steal button is shown. Player A sees no Steal button.
- [ ] B presses Steal: B sees a countdown at the top of the screen; A sees a red warning and countdown.
- [ ] B walks more than 5 blocks away: B sees "Return to the totem! 15s", A sees "Thief is out of range".
- [ ] B stays away 15 s: the steal fails for both.
- [ ] B stays near for the full time (set `stealSeconds = 20` in config for testing): B becomes the owner, B can mine it, A cannot.
- [ ] A can steal it back with the same timer.
- [ ] B dies or disconnects during a steal: the steal fails.

## Upgrades
- [ ] Right-click with the totem in the main hand (not sneaking) opens the upgrade tree.
- [ ] Buying a level costs the configured XP levels; button is disabled when you cannot afford it.
- [ ] Tooltip lists bought buffs. Place and pick up the totem: buffs are still there.
- [ ] With the totem in the offhand: hit a mob for extra damage (Damage), see the Resistance effect icon (Resistance), mine stone faster (Mining Speed).
- [ ] Buffs stop when the totem leaves the offhand.

## Commands (operator)
- [ ] `/bsp totem locate <player>` lists placed totem positions, and stops listing them once mined.
- [ ] `/bsp totem reset <player>` followed by relogging on a grant-enabled server gives a new totem.
