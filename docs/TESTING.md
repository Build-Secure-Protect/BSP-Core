# BSP-Core manual test checklist

Sections are grouped by feature; inside a group they run oldest to newest, so a later section may refine an earlier one (dated sections name the day they were added). Newest sections go at the end of their group.

Run `./gradlew runClient` for single-player checks. For two-player checks run `./gradlew runServer`
(accept the EULA in `run/eula.txt`, set `online-mode=false` in `run/server.properties`), then join
from two clients, or use one `runClient` and one external client.

Enable the grant on the test server: edit `run/<world>/serverconfig/bsp_core-server.toml` and set
`grantTotemOnFirstJoin = true`, or use `/bsp totem give <you>` as an operator.

Contents:
- Shatter Totem
- Powers, auras and placed upgrades
- Totem Compass
- Materials, machines and progression
- Shatter Coins, factory, vault, scoring and admin
- Anti Totem Block and Decoy Totems
- Wave Plasma network
- Batteries, charger, cells and emitter
- Plasma Tank
- Chunk loading
- JEI, Assembly Guide, guide book, advancements and Jade
- Network storage and cross-server
- Fix-up rounds (dated)

# Shatter Totem

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

## Logout protection

- [ ] With a totem in your inventory, open the pause menu and press Disconnect / Save and Quit: a warning appears. "No" returns to the pause menu.
- [ ] Press "Yes": you disconnect, and on rejoining the totem is a placed block next to where you stood, not in your inventory.
- [ ] Repeat inside a Compact Machine or other non-allowed dimension: the totem is placed at your last Overworld position instead.

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

## Totem tiers and the upgrade tree

Delete `serverconfig/bsp_core-server.toml` in an older test world first, so the new level lists are used.

**Tree and tiers**
- [ ] In survival, buying a level or raising the tier lowers the XP number and the right coin count in the wallet row straight away, including coins kept outside the hotbar. In creative the wallet row reads "CREATIVE: everything is free" and every price reads "Free in creative", on the totem screens and the compass.
- [ ] No two nodes on the tree touch or overlap, including the four around the centre.
- [ ] A totem upgraded before this change (in hand or placed) shows Tier I with no levels after loading; a totem upgraded under the new system keeps everything across relog, pick-up and placing.
- [ ] Right-click with the totem in hand: rings I to IV around the totem, five spokes. At Tier I only Mining Speed, Damage, Fortify and Healing Aura are outlined; everything else is dark. The wallet row shows XP and a count for each of the five coins.
- [ ] The panel shows the selected upgrade's level, NOW and NEXT, and the price as a coin icon with a count plus XP. A Tier I level costs Copper coins.
- [ ] After two levels the panel says the Tier I limit is reached. "Raise" costs 4 Copper coins and 15 XP levels; afterwards the centre reads II, ring II lights, and levels 3 and 4 cost Gold coins.
- [ ] Swiftness opens only once Mining Speed is level 2 and the totem is Tier II; before that the panel says what is needed. The same along every path.
- [ ] With too few of the right coin the reason names the coin and number; other coins are not accepted in its place.
- [ ] Base upgrades in hand say "Place the totem to buy Base upgrades". At the placed totem the owner can buy everything and raise the tier; tier and levels survive picking up, placing and stealing.
- [ ] Another player's placed totem: levels visible, buying refused, Steal button shown. Nothing overlaps in any state.
- [ ] Operator tab: the bar under the panel lowers or raises the selected level, the small bar in the tier box lowers or raises the tier, and Assign, Unclaim, Cancel steal, Finish steal and Reset buffs work.
- [ ] Changing a cost in `upgrades.costs` and rejoining changes the prices shown.

**Carried (totem in the offhand)**
- [ ] Mining Speed, Damage, Swiftness, Vitality (hearts appear and go with the offhand), Featherfall, Night Sight (steady, no flicker).
- [ ] Resistance removes a share of damage; even at level 8 you still take damage.

**Base (totem placed; test with a second player)**
- [ ] Fortify, Healing Aura, Ward (Weakness on intruders), Alarm (outline plus one chat line per visit), Sanctuary (no natural hostile spawns), Deadlock (longer steal), Overclock (machines in range are faster within 5 seconds).

**Raid (in the thief's offhand when the steal starts)**
- [ ] Lockpick shortens the steal; Shroud delays the owner's warning.
- [ ] A steal is never longer than 600 s or shorter than 60 s.

**Always stealable**
- [ ] At Fortify 10 an intruder can still mine blocks next to the totem, at one fifth speed.

## Commands (operator)

- [ ] `/bsp totem locate <player>` lists placed totem positions, and stops listing them once mined.
- [ ] `/bsp totem reset <player>` followed by relogging on a grant-enabled server gives a new totem.

## Demo mode (operators)

- [ ] As an operator, every machine screen shows an OPERATOR box with DEMO OFF to the left of the inventory. A non-operator sees no box.
- [ ] Switch it on with the machine empty: Tetrium Crucible (burner, coils, melt rising, lamps), Combination Forge (head striking, charge fusing), Illyrium Crucible (melt, stirring cross, rings, lava gauges part full), Illyrium Refinery (water, agitator, centrifuge), all looping.
- [ ] Shatter Coin Factory: demo on any controller sets the whole joined machine going: belts, press, blanks turning into coins, changing coin every ten seconds. Unformed slices show nothing.
- [ ] Nothing is produced or consumed in demo mode; a real job still runs normally with demo on or off.
- [ ] Demo stays on after leaving and rejoining the world, and switches off cleanly.
- [ ] A non-operator cannot switch demo, even by clicking where the button would be.

## Friends and access

- [ ] The totem panel's ACCESS tab (owner and admins only): type an online player's name and Add. The row shows four switches, all on; each toggles; x removes. A ninth name is refused.
- [ ] A friend with Upgrades can buy upgrades on your placed totem, change its chunks and switch a projector's powers. Without it, those are refused.
- [ ] A friend with Alarm walks into the Alarm cube without glowing or warning you; with Ward they are not weakened. Projectors honour the same switches.
- [ ] The list survives pick-up and re-placing, and is empty after the totem is stolen.

## A totem cannot be stored away (2026-10-08)

- Drop your totem onto a hopper (or a Create funnel, a Mekanism collector, an item magnet): nothing moves it. It lies there bobbing like an item, only you can pick it up by walking into it, and after 30 s it places itself as before. Falling into the void still returns it.
- Open an AE2 or Refined Storage terminal and drag the totem into the grid: within about three seconds the totem is back in your inventory with the gold message "Your Shatter Totem came back to you." Take the copy out of the grid: it turns into a spent husk (red tooltip "Spent...", no shine, no owner, no upgrades, cannot be placed or opened). Same for a totem that a hopper took from a chest you put it in through any menu we do not catch.
- Normal play does not trigger it: placing the totem, dropping it, dying, logging out, moving it between slots or into the offhand, carrying it on the cursor. If it ever comes back to you without reason, report what you were doing.
- The spent husk is worthless: it cannot be upgraded, placed, or used in the offhand, and admin and death handling ignore it.

# Powers, auras and placed upgrades

## Placed upgrades and auras (needs Shatter Coins: `/give @s bsp_core:shatter_coin 64`)

- [ ] Open your placed totem: Fortify and Healing Aura rows show "Not active" with an "Upgrade: 5 coins" button; "Your Shatter Coins" shows your count.
- [ ] Buy Fortify level 1: 5 coins leave your inventory, the row shows level 1 / radius 1, a violet orb appears at the base, and a faint violet sphere of radius 1.5 surrounds the totem.
- [ ] Raise Fortify to 3 (operator + button is fine): sphere grows to radius 5.5.
- [ ] As a non-owner (assign the totem to a dummy, or use a second account) mine stone inside the sphere: noticeably slower than outside. As the owner: normal speed.
- [ ] Set off TNT inside a level-5 Fortify sphere: blocks in range survive; the totem is untouched.
- [ ] Buy Healing Aura, hurt yourself, stand inside the green sphere: health ticks up once a second. Step outside: it stops.
- [ ] Mine the totem and re-place it: both auras and their spheres are restored.
- [ ] Walk 60 blocks away and back: spheres render from far away and disappear when the totem is removed.

## Aura spheres and other models

- [ ] Stand outside a Fortify or Healing sphere and look through it at a machine, a factory, another totem, a chest and an item frame on the far side: all of them stay visible, tinted by the sphere. The same from inside the sphere looking out.
- [ ] The sphere itself still looks the same: a faint coloured bubble that pulses gently.

## Aura cubes and the hide switch

- [ ] Fortify and Healing Aura draw as cubes with glowing edges around totems and projectors. A player standing inside the cube still sees its edges.
- [ ] The AURAS switch in the totem panel's header hides every aura cube for you only; another player still sees them. The setting survives a restart (`config/bsp_core-client.toml`), and the keybind (unbound by default, under BSP Core in Controls) does the same with an action-bar message.
- [ ] Effects use the cube: a player just outside the sphere but inside the cube's corner is still warded/alarmed/healed.

## Newer powers

- [ ] The tree has five rings now (Tier V outermost). Bouncy sits beside Night Sight on the Dig path, X-ray on the outer ring; Cloaking beside Deadlock; Recall on the outer ring of Home; Recall Block after Shroud. Output starts the plasma path.
- [ ] Bouncy: fall 3+ blocks and bounce back up, higher from a higher fall, with some forward speed; sneaking lands flat; fall damage reduced per level and no damage at level 4. Works from the emitter too.
- [ ] X-ray: press X with X-ray carried. Ores show blue, block entities gold, spawners red through the walls within range, fading with depth; a small ring by the crosshair drains, then shows the recharge. Pressing X again switches it off early.
- [ ] Recall: with Recall on a placed totem, have someone start stealing it while you stand far away: after the thief's Shroud and Recall Block delays a card appears above the hotbar with an arrow and distance; R teleports you to the surface at the level's distance from the totem facing it; N declines; the card runs out after 20 s. Recall then rests 10 minutes. No card appears when you are already within range.
- [ ] Recall Block on the thief's offhand totem delays the card by its seconds.
- [ ] Cloaking: buy it on a totem with a base built around it, then view from outside as a player not on the access list: the cube shows what was there when Cloaking came on (walk in and the real blocks appear; the owner always sees the real blocks). The totem's plasma output is 50 mB/t lower while cloaked. Known limits of this first version: the copy is of the moment it came on (not the land as generated), entities inside are not hidden, and block entities inside briefly lose their contents on the client after walking in until the chunk reloads.

## The 1.0 buffs: Thief's Step, Siege, Harvest; cheaper gates; Damage (2026-10-10)

- Tree: Thief's Step sits beside Recall Block on the Raid path (Tier IV, after Shroud), Siege after it on the outer ring (Tier V); Harvest after Survey on the Anchor path (Tier IV). Four levels each, Raid or Base prices.
- Mining Speed, Swiftness, Featherfall and Night Sight cost half the XP they did (coins unchanged). Damage reaches +8 at level 10 (0.8 a level; `carried.damagePerLevel`).
- Thief's Step (carry it in the offhand, or on an emitter if stamped): your footsteps and landing make no sound to anyone; an Alarm of level 2 / 4 / 6 / 8 or lower neither outlines you nor warns the owner. A higher Alarm still does.
- Siege: press V (key "Siege on/off"). Inside an enemy Fortify your mining runs at full speed for 8 / 12 / 16 / 20 s; an orange ring left of the crosshair drains, then a grey one fills over the 180 / 150 / 120 / 90 s recharge. Press again to stop early. Without the power: "You carry no Siege power".
- Sentinel (Base, Tier III beside Ward, Raid prices): with Alarm and Sentinel on your placed totem, have another player walk into the Alarm cube while you stand anywhere on the server. A bar slides down from the top edge: "TOTEM x, y, z", "1 inside", and from level 2 a red chip with their name, from level 3 their distance from the totem. Level 4: the chip updates every tick, and when you are within 128 blocks of them in the same dimension a red diamond with their name and distance shows through the walls. They leave: the bar slides away within four seconds. A steal in progress turns the head gold: "STEAL: Bob, 212 s". A friend with the Alarm switch sees the same; nobody else does. A player with Thief's Step high enough for your Alarm level is not shown.
- Harvest: on a placed totem with plasma; mine a BSP ore (Tetrium, Illyrium, Magnatite, any form) within 8 / 12 / 16 / 24 blocks: it grows back where it was after 120 / 90 / 60 / 30 s, one at a time, oldest first. While ores wait, the totem's plasma output drops by 25 mB/t (the interface line now reads "its powers take"). Ores mined by someone else count too. Fill the spot with a block: it does not grow back until the spot is clear.

# Totem Compass

## Totem Compass

- [ ] Craft it (4 Tetrium Ingots around 1 Illyrium Ingot) or take it from the BSP tab. Holding it, the needle points toward your nearest placed totem and turns as you turn. Check it points the right way, not mirrored.
- [ ] Right-click: the screen shows "Pointing to your nearest totem". Click a coin button: one coin of that tier leaves your inventory and "Loaded" rises by 1, 5, 10, 30 or 60 seconds.
- [ ] Press Start tracking: the needle swings to the nearest totem owned by someone else (assign a second totem to a dummy name as an operator to test). The timer counts down.
- [ ] When tracking ends the screen shows a 10:00 cooldown, during which Start tracking is disabled and the needle points to your own totem again.
- [ ] Buy a cooldown upgrade: coin value is taken and the cooldown length drops by one minute, down to 3:00 after seven levels.

## Totem Compass screen

- [ ] The right-hand button reads "Shorten cooldown" and fits inside its frame. Under it is the price as a coin icon and "x4 Copper" (red when you do not have enough). Buying takes exactly those coins and nothing else; the next level asks for Gold.
- [ ] Five coin tiles with the tracking time each adds; clicking one spends a coin and raises STORED. Start and the upgrade button grey out when they cannot be used. Header shows READY, TRACKING or COOLDOWN with a clock.

# Materials, machines and progression

## Illyrium progression

Speed up testing in `serverconfig/bsp_core-server.toml`: set `ticksPerOre`, `ticksPerIngot`, `smeltTicks`, `alloyTicks` and `ticksPerDust` to 40.
- [ ] A new world loads without errors (world generation files are valid). `/locate` is not available for ores, so dig or use spectator: Tetrium Ore is common between y -32 and 80; Illyrium Ore is rare below y -16.
- [ ] Ores need an iron pickaxe (Tetrium) or diamond pickaxe (Illyrium) and drop themselves.
- [ ] Ingots, nuggets and dusts look like recoloured vanilla iron ingots, iron nuggets and redstone.
- [ ] Tetrium Crucible: ore in the top-left slot, coal below it. The flame gauge lights, the bar fills, and 2 nuggets plus 1 slag appear. A lava bucket as fuel leaves an empty bucket.
- [ ] Combination Forge: 9 Tetrium Nuggets plus coal in the lower-left slot give 1 ingot in 30 seconds. With no fuel and no RF it does nothing. 9 Illyrium Nuggets do nothing until an Illyrium Forge Upgrade is in the bottom slot.
- [ ] Crafting table: 9 nuggets make an ingot and an ingot makes 9 nuggets, for both metals.
- [ ] Crush a Tetrium Ingot in a Mekanism Crusher, Create crushing wheels or millstone, and a Thermal Pulverizer: each gives Tetrium Dust.
- [ ] Illyrium Crucible: place the controller on the ground and open it: "Structure incomplete", and small ghost blocks show every missing part. The controller is the middle of the front edge of the bottom layer; the structure goes back and up from it:
  - Bottom layer: Illyrium Casing everywhere except the controller.
  - Middle layer: Lava Pylons on the four corners, Item Hatches in the middle of the left and right edges, Illyrium Core in the centre, front-middle and back-middle empty.
  - Top layer: Lava Pylons on the four corners, Illyrium Casing in the middle of each edge, centre empty.
  Totals: 12 casing, 8 pylons, 2 hatches, 1 core. The warning clears within a second of the last block.
- [ ] Crucible smelting: Illyrium Ore + 2 Tetrium Slag + a lava bucket or magma blocks gives a Dirty Illyrium Ingot. The orange gauge shows lava.
- [ ] Illyrium Refinery: controller on the ground at the middle of the front edge. Bottom layer: Illyrium Core in the centre, casing in the other seven spots. Middle layer: Illyrium Tank Glass in the centre, casing behind it, Item Hatch on its left, Refinery Pump on its right. Top layer: one more glass on the centre. Totals: 8 casing, 1 core, 2 glass, 1 pump, 1 hatch.
- [ ] Hoppers and item pipes on an Item Hatch, and fluid pipes on a Lava Pylon or Refinery Pump, reach the controller's inventory and tank.
- [ ] Refinery: Dirty Illyrium Dust + water bucket + an Iron Filter gives one Pure Illyrium Dust and uses up the filter. A Diamond Filter shows a wear bar and lasts 20.
- [ ] Crucible alloying: Pure Illyrium Dust + Tetrium Dust + lava gives an Illyrium Nugget.
- [ ] Side buttons, hoppers and pipes work on all four machines; fluid pipes fill the two multiblock controllers.
- [ ] Blanks: 4 Tetrium Ingots give 2 Shatter Blanks; Shatter Blank + 4 copper gives a Copper Coin Blank; and so on up to Netherite Blank + Illyrium Ingot.
- [ ] With AllTheModium installed, its mining dimension contains Tetrium Ore in the stone and deepslate layers and End Stone Illyrium Ore in the bottom layer.

## Crushing by hand

- [ ] Pickaxe + Tetrium Ingot anywhere in a crafting grid shows a Crushed Tetrium Ingot. Taking it leaves the pickaxe in the grid with one less durability and removes the ingot.
- [ ] The crushed item turns at once into either 1 Tetrium Dust (about 1 in 3) or 3 Tetrium Nuggets, with a message. Shift-click a stack of ingots with a pickaxe: each ingot is rolled separately.
- [ ] Same with a Dirty Illyrium Ingot: about 1 in 6 gives Dirty Illyrium Dust, otherwise 3 Dirty Illyrium Nuggets. Nine Dirty Illyrium Nuggets craft into one Dirty Illyrium Ingot.
- [ ] A pickaxe on its last durability point breaks after the craft.

## RF upgrades

- [ ] Put an RF Upgrade in the bottom-right slot of any machine: a yellow gauge appears and the machine now accepts RF from cables. Without the upgrade it accepts none.
- [ ] With RF stored, a Tetrium Crucible job is about 5%, 10% or 30% quicker for Mk I, II, III.
- [ ] Combination Forge with an RF upgrade and RF: works with no coal and takes about half the time, or less with higher marks.
- [ ] Illyrium Crucible with RF Upgrade Mk III and RF: each job drains about 175 mB of lava where it drained 250.

## Port sockets (multiblocks)

- [ ] Form an Illyrium Crucible. The four pylons stand in the centre of their blocks. Each has an orange socket (lower pylon block) and a yellow socket (upper pylon block) on both outward faces, reaching out to the block face. Each Item Hatch shows one framed socket flush with its outward face.
- [ ] A pipe or cable run to any of those faces meets the socket dead centre, with no gap and no overlap, on all four facings of the controller.
- [ ] Form an Illyrium Refinery. The Item Hatch side shows an item socket; the Refinery Pump side shows a blue water socket with a yellow RF one above it.
- [ ] Crucible: a lava pipe fills the tank only through a lower pylon block (orange); an RF cable charges only through an upper pylon block (yellow). The wrong one does not connect.
- [ ] The yellow RF sockets are dim without an RF upgrade and pulse once one is fitted.
- [ ] Open the screen and cycle a hatch's mode in PORTS: the socket on the machine changes colour straight away (blue in, orange out, turquoise both, grey off).
- [ ] PORTS lists each hatch with "Items: <mode>", the fluid row with what it accepts, and an RF row that reads "No RF upgrade" or "RF in". No text overlaps the STATUS column or the UPGRADES label.
- [ ] Pipes and cables connect at the socketed blocks: items at hatches, lava/water and RF at pylons/pump.

## Recipes and machine components

Full list with grids: `tools/preview/recipe_plan.html` (regenerate with `python3 tools/gen_recipe_plan.py`).
- [ ] BSP creative tab shows the 15 components, each with its own icon and name.
- [ ] Furnace or blast furnace: Tetrium Slag smelts into a Slag Brick.
- [ ] Combination Forge: one Tetrium Ingot gives one Tetrium Plate; nine nuggets still give an ingot. The empty input slot tooltip mentions both.
- [ ] Shared parts craft: Machine Chassis (x2), Tetrium Coil (x2), Drive Motor, Circuit Substrate (x3), Basic Control Circuit.
- [ ] Illyrium Crucible parts craft: Thermal Lining (x4), Crucible Control Circuit, Illyrium Casing (x4), Illyrium Core (any Illyrium Ore in the centre), Lava Pylon (x2), Item Hatch, controller.
- [ ] Illyrium Refinery parts craft: Refinery Control Circuit, Illyrium Tank Glass (x4), Refinery Pump, controller.
- [ ] Factory parts craft: Mint Control Circuit, Conveyor Belt (x3), Press Die, Factory Frame (x2), Factory Press, Blank Hatch, Power Port, controller.
- [ ] Motivator crafts from a Resonance Crystal, Illyrium Processor, two Tetrium Coils and three Tetrium Plates.
- [ ] RF Upgrades Mk I to III and the Illyrium Forge Upgrade use their new recipes; the old ones no longer work.
- [ ] A full survival run is possible in order: Tetrium Crucible, Combination Forge, Illyrium Crucible, Illyrium Refinery, factory slice, Motivator.

## Animated single-block machines

- [ ] Tetrium Crucible, idle: dim burner window, dark coils, low melt, tip glowing. Working: burner glows orange (yellow on RF), coils light in sequence, the melt rises and turns pale near the end, six collar lamps fill, the tip flashes white at the end.
- [ ] Slag piles up in one tray and nuggets in the other as output collects, and empty again when taken. An RF Upgrade shows as a capacitor on the collar.
- [ ] Combination Forge, idle: press head parked high. Working: the head strikes, faster near the end; the nuggets shrink as a glowing ingot grows; rail lights chase; five bed lamps fill. Illyrium jobs glow turquoise.
- [ ] The left front socket lights turquoise with the Illyrium Forge Upgrade, the right one yellow with an RF Upgrade.
- [ ] Both look right in all four facings, and their inventory icons still show the complete machine.

## Redstone control

- [ ] A lever or redstone torch next to a working Tetrium Crucible or Combination Forge pauses it; the header reads PAUSED BY REDSTONE. Removing the signal resumes it.
- [ ] The same at the controller block of the Illyrium Crucible and Illyrium Refinery.
- [ ] A signal at one factory slice's controller pauses only that slice: its lane is framed red, the tooltip says it is paused by redstone, and its belts stop. Other slices keep pressing. No press time is counted while paused.

## Magnatite (materials only; the centrifuge comes next)

- [ ] Magnatite Ore and Deepslate Magnatite Ore are in the BSP creative tab and show dark specks with a blue sheen on stone and deepslate. A diamond pickaxe drops the ore block; an iron pickaxe drops nothing.
- [ ] In a new world the ore is found below Y 8, mostly in deepslate, somewhat more often than diamond.
- [ ] Magnatite Nugget and Ingot have the vanilla iron shapes in dark blue-grey; the Charged Magnatite Ingot is bright blue and glints; Carbon Dust is a black powder; the Copper Tetrium Coil shows copper windings.
- [ ] Nine nuggets craft into an ingot and back. The Combination Forge accepts nine Magnatite Nuggets and makes an ingot.
- [ ] The Copper Tetrium Coil recipe (Copper Ingots, Carbon Dust, Tetrium Coils around a Magnatite Ingot) shows in JEI.

## Magnetic Centrifuge

- [ ] All four blocks are craftable and in the creative tab. Placing the controller shows ghost blocks for the other eight; sneak + right-click opens the Assembly Guide, and holding Shift over any of the blocks in JEI opens it too.
- [ ] Built in all four facings (controller front centre, Rotor in the middle, Item Hatch left, Power Port right as seen from the front), the blocks hide and the Armoured Spin Drum appears: plated sides with slit windows, hazard stripes, lamps, the extractor inside. Breaking any block brings the blocks back.
- [ ] With no RF the screen lists "Needs 60 RF per tick" and nothing runs. With a cable on the Power Port the RF gauge fills and it runs; cables on the casing or controller do nothing. Hoppers and pipes work on the Item Hatch only.
- [ ] One Magnatite Ore (either kind) takes 30 seconds and gives 3 nuggets and 1 Carbon Dust. The extractor spins while working and the lamps turn green; a redstone signal pauses it.
- [ ] A Magnatite Ingot with no coil does nothing and the screen says to fit a coil. With the Copper Tetrium Coil fitted it tries every 30 seconds; over many tries about 1 in 6 succeed, and a failed try leaves the ingot in the input slot. The copper band behind the windows glows blue while charging.
- [ ] Stack a second layer (Rotor + 8 Casing): its blocks hide, a second extractor appears turning the other way, and the status line at the top right of the screen shows the layers and the new numbers. Test up to six: nuggets rise to 7 to 9, the charge chance reaches 1 in 2 at five, and at six a try takes 25 seconds. A seventh layer is ignored.
- [ ] Removing a block from a middle layer drops the layers above it back to ordinary blocks and the numbers fall.
- [ ] Power use rises with layers (60 per layer separating, 240 per layer charging).
- [ ] The status line does not overlap the title, the state text or the PORTS list on the machine screen.
- [ ] The operator demo switch spins the extractors with nothing in the machine.
- [ ] JEI shows a Magnetic Centrifuge category with both jobs, opened by clicking the dial on the machine screen. The guide book has "Magnatite" and "Magnetic Centrifuge" entries. The three new advancements are granted.

# Shatter Coins, factory, vault, scoring and admin

## Balancing for 1.0, first pass (2026-10-09)

New world, or edit the test world's `bsp_core-server.toml` to the new defaults (`ticksPerOre` 300, `smeltTicks` 800, `alloyTicks` 1200, `ticksPerDust` 1800, `filterUses` [5, 20, 100, 1000], `dirtyIllyriumDustChance` 0.25, `separateTicks` 600, `chargeTicks` 600, `pressHours` [8, 16, 32, 64, 96]); the dev world's copy has been updated.

- [ ] Tetrium Crucible: one ore takes 15 seconds (the game test `tetrium_crucible_takes_300_ticks` checks this headlessly).
- [ ] Illyrium Crucible: a Dirty Illyrium Ingot takes 40 seconds, an alloy job 60 seconds; the lava per job is unchanged (250 mB).
- [ ] Illyrium Refinery: one Pure Illyrium Dust takes 90 seconds. An Iron Filter shows 5 uses in its tooltip and lasts five dusts.
- [ ] Magnetic Centrifuge: one ore takes 30 seconds; a charging try takes 30 seconds (25 with six layers, unchanged).
- [ ] JEI: the four filter recipes show string in the cross, not cobweb; the Lava Pylon recipe gives 4.
- [ ] Crafting-grid crushing of Dirty Illyrium Ingots succeeds about 1 in 4 over many tries (the tooltip shows 25%).
- [ ] Coin factory screen: a Copper blank shows 8 h to press (Gold 16, Diamond 32, Netherite 64, Illyrium 96) with no Motivators.
- [ ] New world: Illyrium ore between y -64 and -16 is clearly easier to find than before (8 veins per chunk; about half as common as diamond). X-ray marks it.
- [ ] `python3 tools/balance_report.py` after any further number change; `docs/BALANCE.md` is generated.

## Shatter Coins and the factory (multiblock slices)

Blocks: Shatter Coin Factory Controller, Factory Frame x2, Factory Press, Factory Blank Hatch, Factory Power Port, Motivator. All in the BSP creative tab.

**Building one slice (1 wide, 2 high, 3 long)**
- [ ] BSP creative tab shows the controller, the four factory blocks, the Motivator, five blanks and five coins. No Speed Gears anywhere. Coin tooltips show values 1, 2, 4, 8, 16.
- [ ] Place the controller: ghost blocks show the five missing parts behind and above it. Bottom row from the front: controller, Frame, Blank Hatch. Top row: Frame, Press, Power Port.
- [ ] Right-clicking an incomplete controller says how many blocks are missing and does not open the screen.
- [ ] Place the last part: within a second the blocks vanish and the Cascade Mill model appears: three belts stepping down, press over the middle belt, machinery under the belts, glass on both sides and in the roof, tray in front, screen on the front block.
- [ ] Blue socket on the lower back block, yellow socket on the upper back block.
- [ ] Break any part: the slice turns back into plain blocks and the ghost returns. The controller drops its blanks and coins when broken.
- [ ] All four facings look right (front always toward you when placed).

**Pressing**
- [ ] Put blanks in the lane's blank slot and feed RF into the Power Port (Mekanism cable). A press starts: belts run, the press hammers, blanks ride down from the back and turn into coins of the same tier after the press.
- [ ] Change the blank tier: the items on the belt and in the tray match the tier being pressed.
- [ ] The finished coin appears in the tray slot and as a pile in the tray on the model. Coins can only be taken by hand in the screen.
- [ ] A hopper or pipe on the Blank Hatch inserts blanks. Nothing can pull coins or blanks out of any block of the factory.
- [ ] Tray full (64): the lane turns orange and holds at 100% until coins are taken.
- [ ] Log out and back in, and restart the world: the press carries on and catches up with real time.

**Joining slices**
- [ ] Build a second slice directly beside the first, facing the same way: the wall between them disappears, a slim rib and glowing couplings appear, the tray and roof run through, and a podium screen appears centred in front. Glass stays only on the two outer ends.
- [ ] Add slices up to 10: the podium stays centred. The 11th controller is refused with the limit message.
- [ ] Right-click any controller of the row: the same screen opens with one lane per slice, in the same left-to-right order as the slices seen from the front.
- [ ] Each slice starts and finishes its own presses at its own time.
- [ ] RF is shared: one cable on one Power Port fills the whole machine. Add cables to more Power Ports: the "+N RF/t from N cables" line rises.
- [ ] A slice placed beside a row but facing another way, or owned by another player, does not join.
- [ ] Break one slice in the middle of a row: the row splits into two machines, each with its own screen position.

**Motivators**
- [ ] Place a Motivator on top of a slice (three top cells). On a complete slice it becomes the pulsing half-height pylon; elsewhere it stays a plain small block.
- [ ] 1, 2, 3 Motivators cut that slice's time by 15%, 30%, 50% (lane tooltip and time left), and the pulse gets faster.
- [ ] Neighbouring Motivators link with a base conduit and a beam between cores, along a slice and across joined slices.

**Screen (Floor Plan)**
- [ ] One lane per slice: number, blank slot (blue), a bar filling downward, tray slot (orange), three Motivator pips. No coins move along the bars.
- [ ] A lane with no blank or not enough RF is framed red; a full tray is framed orange. Header reads WORKING, ATTENTION or SWITCHED OFF.
- [ ] Hovering a lane shows slice number, coin and time left or what it needs, and its Motivators.
- [ ] Shift-clicking blanks from the inventory fills the lanes from the left.
- [ ] Power switch stops and restarts the whole machine; while off no time is counted and the model stops and its screen lines turn red.
- [ ] With 10 slices nothing overlaps; the screen fits at GUI scale 3 and 4.
- [ ] Add or remove a slice while the screen is open: it closes, and reopening shows the new layout.

## Coin loot

- [ ] `/loot give @s loot minecraft:chests/simple_dungeon` run many times sometimes gives Copper (1 to 2) and Gold Shatter Coins; the same for abandoned_mineshaft and the three stronghold tables.
- [ ] `minecraft:chests/end_city_treasure` and `minecraft:chests/ancient_city` occasionally give a Diamond Shatter Coin.
- [ ] No table ever gives a Netherite or Illyrium Shatter Coin.

## Scoring and coin stacks

- [ ] Shatter Coins stack to 12 everywhere; a factory tray stops at 12 and shows as full.
- [ ] `/bsp score top` lists players with points, totem count and best tier. One Tier I totem is 1 point; raising it to Tier II makes it 2, Tier III 4, Tier IV 7, Tier V 10.
- [ ] A carried totem counts for the carrier; placing it keeps the score; a stolen totem moves its points to the thief within a second or two.
- [ ] A totem in an unloaded chunk, and one whose owner is offline, still count.
- [ ] The guide book's "Scores and the Leaderboard" entry matches the numbers in the config.

## Score Screen

- [ ] The Score Screen is in the BSP creative tab. One panel placed on its own shows the leaderboard title and rows on its front face, readable, the right way round, in all four facings.
- [ ] Panels placed beside and above it (same facing) join into one display: try 2 x 1, 5 x 3 and 8 x 6. A ninth column or seventh row starts a separate screen. Breaking a panel re-splits the screen sensibly.
- [ ] An L-shaped or ragged group shows a screen over the largest full rectangle from its bottom-left corner.
- [ ] As an admin, right-click any panel: the settings open. A non-admin gets nothing.
- [ ] Each setting changes the screen in the world at once: Scoreboard or Scoring rules; Ranked list, Podium, Spotlight; Pixel, Clean, Bold; text size 60% to 160%; scroll speed 0% to 100%; the title text.
- [ ] Ranked list: the top three are gold; with more players than rows it scrolls and loops. Podium: top three on bars, the rest on a ticker. Spotlight: one player at a time.
- [ ] Scoring rules: five tier lines with their points and three notes, matching `scoring.tierPoints`.
- [ ] The screen changes within a second or two when a totem is placed, stolen or raised a tier.
- [ ] Settings survive a restart, and the text stays bright at night.
- [ ] Nothing behind or beside the screen disappears when looking past it.

## Coin Vault

- [ ] The Coin Vault is in the BSP creative tab, faces the player when placed in all four directions, and needs an iron pickaxe or better for its owner.
- [ ] The owner's right-click opens the tabbed screen. Only Shatter Coins go into the 27 slots (shift-click works both ways); other items are refused. Hoppers and pipes cannot move coins in or out.
- [ ] A second vault placed touching the first joins it: the Storage tab shows "VAULT BLOCK 1 OF 2" with arrows. Build 3 x 3 x 3; a block that would make it 4 long starts a separate vault. Another player's vault next to it never joins.
- [ ] Access tab: type an online player's name and Add. That player can now open the vault and move coins but cannot change Access or Security. The x removes them and closes their screen. An offline name, a duplicate and a ninth name each show a red message.
- [ ] Security tab: lock upgrades cost 4 Gold, then 4 Diamond, then 4 Netherite coins and raise the pick time from 2:00 to 3:00, 4:00, 5:00. The Alarm costs 4 Gold coins. Prices show "Free" in creative. Upgrades apply to every block of a joined vault.
- [ ] A player without access gets the LOCKED view, never the contents. "Pick the lock" starts a countdown on the screen and above the hotbar; it carries on with the screen closed.
- [ ] Walking more than 4 blocks away, dying or logging out fails the pick. A second thief is told someone is already picking.
- [ ] Success: the thief receives about 25% of the coins, a random mix. With 3 or fewer Illyrium coins in the vault none are taken; with 4 or more they can be.
- [ ] With the Alarm fitted, the owner and everyone with access get a red warning with the coordinates when picking starts. The owner is told when coins are taken either way.
- [ ] Interest: with coins stored, the Storage tab's interest line rises over time (for a quick test set `vault.interest` rates very high). The bar fills toward the cap and turns red when full. Redeem gives XP levels and ingots and only works for the owner.
- [ ] Interest counts all of the owner's vaults together, and Illyrium Ingots only build up while a Netherite or Illyrium coin is stored.
- [ ] The owner breaking a vault block drops its coins. Anyone else takes 30 seconds with any tool, gets the vault block but no coins; the owner is told, and receives the coins on next opening any vault of theirs.
- [ ] Explosions and pistons do not move or destroy a vault. Contents, access and upgrades survive a restart.
- [ ] A 28th vault block cannot be placed: a red message shows and the item stays in the hand. Breaking one allows another.
- [ ] The owner breaking an upgraded vault gets an item whose tooltip shows the lock level and alarm; placed again it has them. Placed next to an existing vault, the whole vault takes the higher levels. A vault block broken by someone else drops with no upgrades.
- [ ] Joined look: two vault blocks side by side become one body with a wall of deposit drawers (four per block on the door side) and blinking keyhole lights. Try 2 x 1 x 1, 3 x 3 x 1, 3 x 3 x 3 and a tower 1 x 3 x 1, with the first block facing each of the four directions: the drawers are always on the side the first block faced.
- [ ] An L shape of three blocks shows a two-block vault plus one single safe; all three still share storage pages. Breaking a block of a joined vault re-forms the rest at once, and a lone block turns back into the Classic Safe.
- [ ] The joined vault is lit like its surroundings (not black), is visible from every side with no gaps, does not flicker, and nothing nearby disappears when looking past it. It survives a restart.

## Admin Rack and admin panel

- [ ] The Admin Rack is in the BSP creative tab, faces the player when placed, cannot be mined in survival and can be removed in creative.
- [ ] With `admin.admins` empty, an operator's right-click (or `/bsp admin`) opens the panel; a non-operator gets a red message and the command is not available to them.
- [ ] A name in `admin.moderators` (not an operator) opens the panel with a READ ONLY tag: every action button is grey and nothing happens when clicked.
- [ ] With names in `admin.admins`, only those players have full control, even if others are operators.
- [ ] The list shows every player who was granted a totem, owns one, has a vault or is online, ordered by score, offline names dimmer; it scrolls with the mouse wheel past 14 names.
- [ ] Selecting a player shows each totem's tier and coordinates (placed) or "held" with the carrier's position, and the vault's coins by type, value and block count. Compare against the world.
- [ ] TP beside a totem and "Go to player" move the admin there, including across dimensions. "Go to player" is grey for offline players.
- [ ] "Reset totems" needs a second click, then puts every totem of that player (placed and carried) back to Tier I with no upgrades; the score updates after Refresh.
- [ ] Season tab: "End season" needs a second click. After it: no totem blocks, carried totems or dropped totems remain; every online player has exactly one fresh Tier I totem; the chat announces the winner; the season number goes up and the day returns to 1.
- [ ] A player who was offline at the season end gets one fresh totem, with a message, when they log in, and only once.
- [ ] "Reward holders" gives each holder one Gold Shatter Coin per totem held (change `season.holderRewardItem` to test another item); an offline holder gets theirs at next login.
- [ ] Coin Vaults, coins and factories are unchanged by a season end.
- [ ] "Season prizes" (admins only) opens three rows of ten slots labelled 1ST, 2ND, 3RD. Items placed there are still there after closing, reopening and a restart.
- [ ] End a season with three or more scoring players: first, second and third each receive exactly their row (an offline one at next login, with a message), and the rows are empty afterwards. With only one scoring player, the 2ND and 3RD rows stay filled.
- [ ] "Full reset" is grey until RESET is typed in the box beside it, then needs a second click. After it: everything End season does, plus every Coin Vault is empty and interest shows zero. Switching tabs clears the typed word.
- [ ] A moderator cannot open Season prizes or use Full reset.
- [ ] Full reset also removes every Shatter Coin from online players' inventories, cursor and ender chests, from every Coin Vault and from factory coin trays. A player who was offline loses theirs at next login. Coin blanks and other items are untouched. (Coins in ordinary chests are expected to remain.)
- [ ] A moderator can open Season prizes from the Season tab: it shows READ ONLY and no item can be taken out or put in, including by shift-click.
- [ ] Score Screen settings have a third "Prizes" option. The screen then shows "SEASON n PRIZES" and three rows (1ST, 2ND, 3RD) with each prize item as a flat icon and its count; an empty row says "Not set yet". Check on a small (2 x 1) and a large (8 x 6) screen, in all four facings, that icons are the right way up, not mirrored, bright at night, and do not poke out of or sink into the screen.
- [ ] Changing a prize in the admin panel updates every Prizes screen within a second, and ending a season moves the title to the next season number.

## Step 5: recipes, guide and JEI for the new blocks

- [ ] The Coin Vault and Score Screen (gives 2) can be crafted in survival with the recipes JEI shows. The Admin Rack has no recipe.
- [ ] JEI shows an information page for the Coin Vault, Score Screen and Admin Rack.
- [ ] The guide book has "The Coin Vault" under Shatter Coins with five pages, and "Scores and the Leaderboard" now has the Score Screen recipe and a "Seasons and Prizes" page. Recipes on the pages draw correctly.
- [ ] The Admin Rack's lamps blink in the world and on the item, violet with a few turquoise, and stay bright at night.

## Holder reward and vault advancement

- [ ] "Prizes and rewards" shows four rows: 1ST, 2ND, 3RD and HOLD. Items put in HOLD stay there after a payout.
- [ ] "Reward holders now" (second click to confirm) gives every totem holder one copy of the HOLD row for each totem they hold (two totems, two copies); a player with no totem gets nothing; an offline holder gets theirs at next login. With HOLD empty it says so and pays nothing.
- [ ] Typing a number of hours in the box on the Season tab and pressing Set (or Enter) sets the automatic holder reward; only digits are accepted; 0 or empty turns it off; the "next" time counts down after Refresh. Set 1 hour and confirm a payout happens by itself (within a minute of the hour), and that the setting survives a restart.
- [ ] A Score Screen set to Prizes shows a fourth HOLD row only while the HOLD row has items.
- [ ] Crafting a Coin Vault grants the "Safe Keeping" advancement. Ending a season grants no advancement.
- [ ] The Mods screen's link for BSP-Core opens the CurseForge mod page.

# Anti Totem Block and Decoy Totems

## Anti Totem Block

- [ ] In the BSP creative tab. A non-admin cannot place it (red message, item stays), cannot open it, and cannot break it even in creative. An admin in creative can remove it. Pistons and TNT do nothing to it.
- [ ] Placed, it shows a cage with a cube turning inside, glowing in the zone colour.
- [ ] Admin right-click opens the settings. Rules tab: two totem rules, then Machines and Other blocks listing every BSP block by name; it scrolls; each line toggles between BLOCKED and allowed; "block all / allow all" sets a whole group. A fresh block has totems and machines blocked.
- [ ] Zone tab: the -10, -1, +1, +10 buttons change each of the six distances between 0 and 256; the map shows the zone from above with north up and updates; the summary line at the bottom shows the size.
- [ ] Holding an Anti Totem Block in the main hand, an admin sees the outline of each zone, matching the distances exactly, in the chosen colour; two zones with different colours can be told apart. With anything else in hand, or for a non-admin, there is no outline.
- [ ] Inside the zone a non-admin cannot place a Shatter Totem or any blocked BSP block: red message above the hotbar, item kept, no ghost block. One block outside the zone it works. Allowed blocks can be placed inside. Admins can place anything.
- [ ] A machine placed before the zone was set up keeps working.
- [ ] Dropping a totem inside the zone returns it to the inventory with a message (when "dropping" is blocked).
- [ ] Walking through the zone with a totem is fine. Killed inside the zone (by a player and by other causes), the player gets a gold message and has the totem again after respawning; nothing is placed or dropped at the body.
- [ ] Logging out inside the zone with a totem: the totem is placed just outside the nearest side of the zone, on the surface.
- [ ] A totem dropped just outside that slides or is thrown in places itself outside the zone when its timer runs out.
- [ ] Settings and zones survive a restart, and still apply when the Anti Totem Block's own chunk is not loaded (test with a 200-block zone, standing far from the block).

## Decoy Totems

- [ ] The Decoy Totem, Decoy Power Base, Magnet Core and ten socket parts are in the creative tab, have recipes in JEI, and each part's tooltip says what it does.
- [ ] A decoy placed on a Decoy Power Base with a cable on the base's side or bottom becomes ACTIVE; the base's buffer drains at 100 RF per tick. With no base, or no power, its screen says so.
- [ ] As the owner: an active decoy shows the open cage with a blue cube turning inside, and a faint see-through Shatter Totem around it facing the way it was placed. Unpowered it shows the dark cage only.
- [ ] As another player (a second account): an active decoy looks exactly like a real Tier I Shatter Totem placed beside it: same model, colour, outline and collision. Unpowered or broken, it shows the bare cage. (Known tell: the F3 screen and mods that name the block you look at.)
- [ ] The other player's right-click on an active decoy opens a totem-style panel with the owner's name and a STEAL button. Compare it with a real totem's panel and report what differs.
- [ ] Pressing STEAL: the thief gets a red "That was a decoy!" message, sparks and a clang; the owner gets a gold message with the coordinates. With no casing the decoy is now BROKEN, shows the collapsed cage to everyone and can be mined by anyone.
- [ ] Each trap charge works and is used up: Blast (hurts, no blocks broken), Hex (Slowness, Weakness, Glowing), Poison, Fatigue (Mining Fatigue), Warp (thrown up to 30 blocks away, standing on the surface, never in a block). With the Trap Amplifier and a second charge, both go off.
- [ ] With one Reinforced Casing the decoy survives the first attempt and breaks on the second; with two, on the third. The screen's "Attempts left" counts down.
- [ ] Repair costs 2 Magnatite Ingots, only shows when broken, and returns the decoy to ACTIVE if it has power.
- [ ] Sockets: Range Coil II is refused until Coil I is fitted, and so on along each branch; a part cannot be taken out while a later one on its branch is fitted; charges can always be taken out. Shift-click moves parts in and out. Breaking the decoy drops its parts.
- [ ] Compass: a second player tracking rivals who walks within the decoy's range sees the needle swing to the decoy; outside the range, or with the decoy unpowered or broken, it points at the real totem. The owner's compass ignores their own decoys. Range grows 24, 40, 64, 96 with coils.
- [ ] A sixth decoy cannot be placed. A working decoy cannot be mined by another player; the owner can mine it.
- [ ] Admin panel, Settings tab: the four decoy ranges change with the -8, -1, +1, +8 buttons, survive a restart, and within a few seconds apply to decoys already placed. A moderator sees them but cannot change them.
- [ ] An Anti Totem zone with machines blocked refuses a Decoy Totem.

# Wave Plasma network

## Wave Plasma chain (replaces the Totem Generator)

- [ ] The totem tree's plasma path now starts with Output (Tier I ring, 6 levels), then Anchor, then Survey. Output's detail line reads "Gives off 100 mB/t of Wave Plasma" and rises per level (150, 200, 300, 400, 500, 600).
- [ ] Plasma Extractor directly under a placed, owned totem: an extractor with portholes showing moving ion-blue plasma. A second extractor under the first stacks. Wrong order (extractor above the totem, or a gap) does nothing.
- [ ] Plasma Interface touching an extractor's side: cables plug into it. Place 13 touching interface blocks: the 13th shows a lit seam and is not part of the group. Touch a second, separate interface to an extractor the first already serves: its seam lights.
- [ ] Cables join to each other, to interfaces, to projectors and to a repeater's two ends (not its sides). Breaking a cable updates the neighbours' arms.
- [ ] Projector on a cable run from the interface: right-click opens POWERS / CHUNKS / STATUS. POWERS lists Fortify, Healing Aura, Alarm, Ward, Sanctuary, Overclock, Anchor with the totem's level under OFFERED; switching one to RECEIVING shows the same level under ARRIVES; a third switch is refused until a Channel Expander is fitted (right-click the projector holding one; sneak + right-click empty-handed takes it out).
- [ ] STATUS: PROJECTING with the plasma bar full at 100 of 100 mB/t from a single Tier I totem with one extractor. With two extractors under the totem and the interface touching only one: LOW PRESSURE at 50 mB/t. Touching both: back to 100.
- [ ] Repeater in the run: the plasma arriving drops to 90 mB/t and STATUS reads LOW PRESSURE; raising Output to level 1 (150 mB/t) cures it. With one power received it arrives at full level; with two, each arrives one level lower; a level-1 power behind one repeater shows 0 and a tooltip explaining why.
- [ ] A cable run longer than the cable's reach (16 Tetrium cables) is not fed: NO SIGNAL. A repeater in the middle starts a fresh run and it is fed again.
- [ ] The projector lights up with shards circling only while projecting. Fortify and Healing Aura now draw cubes (edges), not spheres; so does the totem. Each power works at the projector as at the totem, within the cube: Fortify slows non-owner mining and resists explosions; Healing heals the owner; Alarm makes intruders glow and tells the owner; Ward weakens; Sanctuary stops spawns; Overclock speeds machines.
- [ ] Two projectors on one interface each pick their own powers. Breaking a cable, picking up the totem, or breaking the extractor stops the aura within a few seconds. A stolen totem keeps the chain working for the thief.
- [ ] Another player right-clicking the projector sees the screen but the RECEIVE switches are dead and a note says only the owner can change it.
- [ ] Settings (received powers, expander) and the extractor's tank survive a restart. Nothing in the chain accepts RF or other mods' fluid pipes.

## Wrong-block marker and projector status

- [ ] Build a Magnetic Centrifuge with a Factory Power Port in place of the Centrifuge Power Port: the wrong block gets a pulsing red box with a small Centrifuge Power Port floating above it, and right-clicking the controller prints a red chat line naming both blocks and the position. Swapping in the right block forms the machine within a second.
- [ ] The same marker appears on the Illyrium Crucible, Illyrium Refinery and Shatter Coin Factory when a wrong block is in a part's place.
- [ ] A Projector on an interface's cable but short of plasma: its seams glow amber and STATUS says LOW PRESSURE. With enough plasma it turns turquoise, the shards appear, and Fortify or Healing Aura draw their cubes around it.
- [ ] A projector with no interface feeding it stays dark and STATUS says NO SIGNAL.

## Cable pressure (2026-10-07, evening)

Totem -> extractor -> interface -> cables -> Projector Base -> projector, all in one run.

- Right-click the interface and watch the cable labels: every cable on the run reads 100 mB/t (the extractor's share), whether the base is empty, filling or full. A full base no longer drops the cables to the 20 mB/t the projector burns.
- Break a cable: the labels drop to 0 and the base drains over time as before.
- Two bases on one interface: each run reads 50 mB/t. A repeater on a run takes a tenth off (90 mB/t).
- Stack a battery with little charge under the extractor instead of a totem and let it run dry: the cables read what actually got through, then 0.
- Projector STATUS tab: "Projecting" with the pressure bar at 100 / 100 once the base is full. The base's tank fills to 5000 mB and holds there.

## Projector Base tank look (2026-10-07, late)

- Feed a base and stand at the window: the band shows bright, near-solid blue plasma up to the level the tank holds, with a slightly brighter surface line. The base's own floor, ceiling and corner posts no longer paint grey over it.
- Empty base: the band is a dark cavity behind clear glass, not a grey box. No flicker on the plates above and below the glass.
- Cut the cable: the level sinks over a few seconds and the cavity goes dark.
- The Plasma Interface's little extractor tube still has its top and bottom glass.

## Plasma Valve and fair sharing (2026-10-08)

Setup from the owner's screenshot: one totem, one extractor, one interface, two runs; run A has a Battery Charger (with a battery charging) and a Projector Base + projector, run B has a base + projector.

- Fair share, no valves: open the interface. Each run reads half the supply (50 mB/t from a plain totem). Run B's base fills and its projector runs; the charger no longer takes everything. Remove the charger's battery: run A needs less, run B's figure rises.
- Place a Plasma Valve in a run (it joins cables at its two ends; the wrench turns its axis, sneak for the other way). Right-click: the dial, the typed box, the nudge buttons and MAX / SHUT all set the limit, and the hand wheel on the block turns with it (one and a half turns from shut to full). The needle on the side gauge follows.
- Set the valve to 20 mB/t: cables after it read 20, the valve's outlet window holds a sliver of plasma, the inlet window holds what the run is offered, and the other run's figure rises by what this run gave up.
- Lever on the valve, flipped on: the lamp goes red, the screen says SHUT BY REDSTONE, cables beyond read 0 and the base beyond drains. Flip it off: back to normal. A redstone wire or torch next to it does the same. Switch REDSTONE: SHUTS IT to REDSTONE: IGNORED on the screen: the lever no longer matters.
- Limit 0 shuts it the same way (screen says SHUT, interface view labels it "shut").
- A repeater after a valve still costs its tenth; the valve itself costs nothing and powers arrive at full level. A valve counts as one cable for reach.
- Access: a player without Machines access on the totem gets the red "machines" message instead of the screen. Admins always may.
- The valve appears in the interface's 3D view with its passing figure, or "shut".
- Totem removed with stock in the extractor: runs keep receiving from the extractor for a while (at most a projector's need per tick), then stop.

## Jade lines on plasma blocks (2026-10-08)

- Look at a Plasma Extractor under a totem: "Drawing 100 mB/t from the totem" (split by the stack; an extractor with no totem reads "No totem to draw from").
- Projector Base: "Wave Plasma: 4600 / 5000 mB", grey when empty.
- Plasma Valve: "Letting through up to 600 mB/t", or red "Shut (limit 600 mB/t)" when a lever or limit 0 shuts it.
- Cables keep their flow line; everything else still shows only name and RF.

## Cable figures add up (2026-10-08, later)

- Interface view with several runs: the cables leaving the interface add up to the extractors' supply (allowing for rounding down). Before, a run with a valve showed what it was offered rather than what passed, and a run with a repeater showed the figure after the repeater's loss on every cable.
- A run with a repeater: cables before the repeater read a tenth more than cables after it. The gap between the supply and "Delivered" is what the repeaters cost.
- A run with a valve set low: cables on both sides of the valve read the limited figure; only the valve's inlet window and its screen ("Offered X, passing Y") show what the run could have had.

## Joined Plasma Interfaces (2026-10-08)

- Place one interface: a dark cube with a rimmed, recessed panel on every face, bolts at the rim corners, vent slots, and a grey cross on each panel. The frame strips run round all twelve edges.
- Place a second touching it: the faces between them vanish, the frame now runs round the outside of the pair only, and the crosses line up into one grid. Try an L, a row of four and a 2x2x2 cube; inner corners of an L get a frame strip, flat joins do not.
- Feed the group from an extractor: every cross turns blue. Remove the totem: they go grey after a second.
- A thirteenth block, or one touching an extractor another group holds: its crosses pulse red (the old orange seam is gone). The rest of the group stays blue.
- A cable touching any outer face gets a nozzle with a lit window on that face; a face touching an extractor gets the tube; faces with nothing stay plain panels. Repeaters and valves touching an interface along their axis also get a nozzle.
- The item in hand and in JEI shows the single block with turquoise crosses.
- Wording: everything that said "drum" now says "extractor" (interface screen header, legend, guide book, JEI).

## Interface labels and the emitter screen (2026-10-08)

- Interface view: a straight or bent run of cables carrying the same figure shows one label, on the cable nearest its middle. Where the figure changes (after a valve or a repeater) the two runs get their own labels side by side. Repeaters, valves, extractors and bases keep their own labels.
- Wave Emitter: right-click at the sky or open ground (no block within four blocks) to open its screen. Drop a Power Cell in the slot (shift-click works), press ON. The right side shows the cell's name, plasma bar, time left at 20 mB/t (m:ss or h:mm:ss), and each stamped power with its level. Take the cell out and the emitter switches off. Right-clicking at a nearby block still toggles it, and sneaking there still takes the cell out.
- The emitter cannot be moved in the inventory while its screen is open.

## Interface screen: cloak line, zoom and pan (2026-10-08)

- Totem with Output maxed and Cloaking on: the header reads "550 mB/t" with "from the extractors" under it and a grey line "The totem makes 600; Cloaking takes 50". Switch Cloaking off: 600 and the line goes.
- Scroll over the view to zoom (25% to 800%, shown next to the button), right-drag or shift-drag to pan, left-drag still turns. RESET VIEW under the view puts everything back.
- The right column fits inside the box: the help text wraps to the column and the legend sits below it. Nothing runs past the edge at any window size.

## Output, caps and throughput (2026-10-08)

- Totem panel, TOTEM tab: under "This totem is yours" a blue line "Wave Plasma: 100 mB/t (Output 0 of 10)". Buy Output levels: 200, 350, 500, 750, 1,000, 1,500, 2,000, 3,000, 4,500, 6,000. The tree shows ten levels on the Output node.
- A 6,000 mB/t totem on one extractor and one interface with a single run reads 1,000 mB/t on that run (the face cap) and the interface screen shows the rest spare. Add more faces with cables: each run gets up to 1,000.
- A run of Tetrium cables reads at most 250 mB/t, Magnatite 500, Illyrium or Charged Illyrium 1,000. One Tetrium cable in an Illyrium run holds the whole run to 250.
- The valve's dial tops out at 1,000.
- Your test world's `bsp_core-server.toml` has the new Output list and valve max already; a fresh world gets them by default.

## Coloured cables and cable ends (2026-10-08)

- Craft eight Illyrium cables round a blue dye: eight Blue Illyrium Plasma Cables. Dye them red: red ones. JEI shows one dye recipe per colour (48 in all); tab and JEI list all 48.
- A blue run next to a red run: no arms between them, and the interface feeds only the run it touches. Plain next to blue: no join. Blue to a base, charger, valve, repeater or interface: joins.
- Hold the wrench and look at a cable: a box above the crosshair reads "West end: Normal" with the hint line; on the core it reads "Cable core". Right-click an end: Output (copper collar, arrow out), again: Input (arrow in), again: Off (plate, arm gone), again: Normal. Sneak + right-click: Normal. Where a red cable meets a blue end the first click reads "Linked across colours" (copper and white collar) and the two join.
- Output and Input do what they say: an Output end at the base lets plasma reach the base; set that end to Input and the base starves. An Off end is a wall.
- Sneak + right-click a cable's core with the wrench: it drops as an item that says it keeps its ends; place it and the ends are back.
- Jade on a set cable lists its ends. The interface view draws coloured cables in their colour.

## Face cap over branching runs; wrench readout (2026-10-08)

- One cable out of an interface face that later splits to two bases: the first cable reads 1,000 mB/t at most and each branch 500 (with a 6,000 totem). Put a second cable on another face and that run gets its own 1,000.
- A Tetrium trunk feeding two Illyrium branches: the trunk reads 250 and the branches 125 each.
- The wrench readout is a single short line, a little above the crosshair: "West end: Output", "Core: sneak-click picks it up", "Port: Input".

# Batteries, charger, cells and emitter

## Batteries, charger, cells, emitter

- [ ] Magnetic Centrifuge: a Resonance Crystal in the input slot and Magnatite Nuggets in the upgrade slot make a Charged Resonance Crystal (30 s, 20 s with six layers). JEI shows the job.
- [ ] Battery Charger on a cable from an interface: its screen reads FILLING once a battery is in the slot, the tank bar moves, the battery's bar fills. The STAMP switches list the interface's powers; a Tier I battery allows none, II two, III three, IV four. Stamped levels follow the totem's level while the battery sits in a fed charger.
- [ ] A charged Tier II battery placed on a Plasma Extractor (lying on its side) feeds the interface at 100 mB/t with only its stamped powers; a projector on that interface lists only those; the battery item shows its bar draining and keeps its contents when mined.
- [ ] Power Cells take Carried powers only (the charger's list changes to Damage, Resistance, Mining Speed, ...). The Wave Emitter: right-click with a cell in the other hand fits it; in the offhand, right-click switches it on; it gives the cell's powers (test Mining Speed and Swiftness), draws 20 mB/t, switches itself off when the cell is empty; sneak + right-click takes the cell out. It does nothing in the main hand.
- [ ] Only the totem's owner (or a friend with Machines) can open a fed charger; others get a red message.

## Battery Charger facing (2026-10-08)

- Place a charger: its open front faces you, the back (solid wall with the port) faces away. The wrench turns it a quarter at a time; sneak turns it back.
- Cables only join the back. A cable at the front or sides does not connect visually and the interface does not feed the charger through it. Move the cable behind it and it does.
- Unfed: the cradle shows no plasma. Fed: the two pads above and below the slot and the port window on the back light up with plasma; cut the cable and they go dark after the signal lapses.
- The outline and collision follow the facing.

## Battery Charger automation (2026-10-08)

- A hopper on top of a fed charger holding an empty Tier I battery: the battery drops into the cradle. A hopper under the charger (or a pipe on any side but the back) takes nothing until the battery is full, then pulls it out. A second empty battery waits in the top hopper until the cradle is empty again.
- A hopper against the back (the cable side) neither inserts nor extracts. Stones and other items never go in.
- Mekanism transporters, Pipez, AE2 export and import buses and Create funnels behave the same way. Stamping powers still needs the screen.

# Plasma Tank

## Plasma Injector (2026-10-10)

- [ ] The Plasma Injector is craftable (Slag Brick, Charged Resonance Crystal, Tetrium Plasma Cable, Illyrium Processor, Tetrium Plate, Copper Tetrium Coil) and in the creative tab; JEI shows the recipe; its tooltip names the curve (x3.0 at 500 mB/t).
- [ ] Placing it against a block attaches it to that block: the plate end touches the block, the socket end points away, in all six directions. Jade on it says "No plasma arriving".
- [ ] The look: the nozzle and the four feed tubes touch the machine with no gap in all six facings (they sink a little into its face); the glass barrel, gauge marks, guide rails, plunger ring, bolted cap and port read from a few blocks away. Idle: dark, nothing moves.
- [ ] Fed: plasma stands in the barrel as high as the rate (a sliver at 50 mB/t, full at 500) and breathes; a slug of plasma runs down each tube into the machine, faster with more plasma; the nozzle tip, the lamp on the cap and the port light. Cut the cable: all of it goes dark within two seconds. Check the fill and the slugs run toward the machine in every facing, including on a factory slice (facing down).
- [ ] Only a cable on the port face (the end away from the machine) connects to it, its arm reaching into the port ring; cables on the sides stay unconnected and feed nothing. The ring stands proud of the cap with a dark throat that fills with plasma while fed. A cable into the port from an interface feeds it; the interface's flow view lists it like a base with the mB/t. Jade: "Injecting 100 mB/t: x1.3 speed" with a base totem.
- [ ] Against a Tetrium Crucible fed 100 mB/t an ore takes about 11 s instead of 15; with Output 3 (500 mB/t) about 5 s. Jade on the crucible shows the plasma line. Cutting the cable brings it back to 15 s within two seconds. The Combination Forge, Illyrium Crucible (against any casing or pylon), Refinery and Centrifuge behave the same; the factor stacks with an RF upgrade and with Overclock.
- [ ] Two injectors on one machine add their rates (two at 250 = 500 = x3); an injector pointing at a different block does nothing for this one.
- [ ] Factory: an injector in a Motivator cell (socket up, cable from above or the side) speeds that slice only. The screen's PLASMA gauge lights one cell per 100 mB/t over the machine; the lane tooltip shows "Plasma: 500 mB/t (x3.0 press speed)"; the remaining time on that lane drops; other lanes are unchanged. It stacks with that slice's Motivators (two Motivators + 500 mB/t: 8 h Copper coin in about 1 h 50 min).
- [ ] Other mods: against a vanilla furnace (game test `injector_speeds_a_furnace`) and, in the pack, a Mekanism or Thermal machine: it runs faster and uses its RF faster. With `plasma.injectorForeign = false` or the block in `plasma.injectorBlacklist` nothing happens. A real-time machine (another factory-like block) is not sped up.
- [ ] Game tests `injector_speeds_a_furnace`, `injector_speeds_the_tetrium_crucible`, `injector_boosts_a_factory_slice` pass in `./gradlew runGameTestServer`.
- Overclock (2026-10-10 rule): Overclock travels with the plasma. An injector fed from a totem with Overclock, at any distance along the cables, is stronger: with an injector giving 2x, Overclock I makes it 2.25x, II 2.5x, III 2.75x, IV 3x; Jade on the injector shows the factor with it. An injector fed from a tank, or from a totem without Overclock, gives the plain factor. Cut the cable: back to normal within two seconds. Overclock no longer appears in a projector's POWERS tab and has no radius (`overclockRadius` is gone from the config).

## Plasma Tank (2026-10-08)

- Craft: Tetrium Glass (8 glass round a nugget), Tank Casing (8 plates round a chassis), Tank Glass (8 tetrium glass round a plate), Tank Port (crystal top, tetrium cables sides, casing centre, plate bottom).
- Build a 3 x 3 x 3: casing on the edges, glass on the six face centres, swap one face for a port. The last block placed forms it: the ports' rings light turquoise, Jade says "Wave Plasma: 0 / 65,000,000 mB" on any block. Glass walls show no seams between blocks.
- Wrong builds do not form: a glass block on an edge, a block inside, a side of 2 or 13.
- Feed it: cable from an interface into a port. The level rises; a port on top pours a stream to the surface with a ripple; a side port shows a short spout while above the surface and nothing once under it. Right-click any block: the screen shows the tank, the plasma level, "Input in 100" on the port and the first cables.
- Drain it: a run from a port to a Projector Base with no interface on it. The port gives up to 1,000 mB/t; the base fills and the projector runs on plasma from the tank (with no powers offered). An In-and-out port that is being fed does not give out at the same time.
- Wrench on a port steps In and out, Input, Output (ring turquoise, blue, orange); the crosshair readout names it. An Output port ignores an interface's run; an Input port gives nothing.
- Break one glass block: rings go grey, Jade says "Not part of a tank"; place it back: formed again with the same plasma. Break two blocks and put both back: still kept (the plasma lasts until the last block of the old shell is gone). Rebuild one block smaller than a full tank: it holds what fits. Add a layer: it keeps what it had.
- Two tanks joined by a cable, one port Output, the other Input: plasma moves across at 1,000 mB/t.

## Tank building without the screen (2026-10-08)

- Place a Tank Casing, then right-click it with another casing in hand: the second block is placed and no screen opens. Same for Tank Glass and Tank Ports while the tank is unfinished.
- Jade on any unfinished tank block shows "Tank unfinished" in yellow. Once the last block forms the tank it shows the level line instead, and right-click opens the screen as before.
- Break one block of a formed tank: Jade on the rest says "Tank unfinished" again and right-click on them does nothing until the block is back.

## Tank formed look (2026-10-08)

- Place the last block of a 3 x 3 x 3: a band of turquoise light runs over the shell from that block for about a second. Every casing cube becomes glass with a thin dark rail along the tank's edge, a lit turquoise strip on the outer edge of each horizontal rail, and a lit 4-pixel node at each of the eight corners. A casing block placed on a face (not an edge) becomes plain glass. Ports do not change.
- The glass is one sheet: no seams between glass blocks and formed casing. The item in the inventory and a casing in the hand still look like the riveted cube.
- The four vertical edges have no lit strip while the tank is empty (only a stub above the bottom node). Feed plasma in: the uprights light up from the bottom as the level rises, and reach the top node when the tank is full.
- The plasma fills the hollow and half of each shell block: looking through the glass, the plasma is right behind it, not a full block in. The top port's stream and the side port's spout still start at the ports. The tank's screen shows the same wider body.
- Break one block: all the casing goes back to cubes at once, the strips and nodes go out, the rings go grey. Put it back: the sweep plays again and the formed look returns with the same plasma.
- Guide book, tank entry: the scene shows the formed look (glass body with outline) and the "Seeing it" page mentions it. JEI info text mentions it.
- Larger tanks (up to 12 a side): the sweep takes the same second to cross; rails and nodes line up along every edge with no gap or doubled rail at the corners.

## Extractor throughput and the tank's first plasma (2026-10-08)

- Totem at Output 10 (6,000 mB/t), one extractor, interface, one run of Illyrium cable into a Tank Port: the port reads 1,000 mB/t and Jade on the tank climbs by 20,000 mB every second (1,000 x 20). Before, it read 200 and climbed by 4,000.
- Six runs from six faces into six ports: each reads 1,000 and the tank climbs by 120,000 a second. Jade on the extractor still reads its draw (6,000 minus Cloaking).
- Take the totem off the extractor: the runs keep going for a couple of seconds, then stop (the reserve is 4,000 mB, as before).
- Capacity: a 3 x 3 x 3 reads 65,000,000 mB (26 blocks x 2,500,000); the test world's config file already has the new value.
- A cable placed against a Tank Port on any face that is not against the tank draws an arm into the port, like into a base.
- A tank that has just started filling shows a thin layer of plasma across the whole floor (about a tenth of a block) even when the figure is far below one per cent. The stream lands on it. The screen shows the same layer.

## X-ray with ghost blocks (2026-10-08)

- BSP ores (Tetrium, Illyrium, Magnatite, their deepslate and End forms) show bright through the rock like vanilla ores: they are in `forge:ores` now.
- Hold a Wave Emitter with X-ray stamped (or `/bsp totem buff xray 3` on a held totem) and press X underground: the stone, dirt and other solid blocks within range turn to faint glass, fainter the further they are; ores show through them bright even in the dark; chests and other containers get a gold mark, spawners a red mark. Glass, leaves, torches, plants and water stay as they are.
- Walking, jumping and mining work as before: the ghosted blocks still have their shape. Jade names a ghosted block "Ghost Block" while X-ray is on.
- Move: blocks that leave the range go back to normal and new ones fade in as you go; the ring by the crosshair drains.
- Press X again, or let it run out: every block is back as it was, with no holes and no leftover ghosts. Log out and in during X-ray: the world is normal.
- Mine an ore or a ghosted block while on: the server breaks the real block and the drop is right.
- Near a cloaked base that hides from you: its fake terrain stays, X-ray does not reveal it.
- Levels: radius 4 / 6 / 8 / 10 / 12, strength 35 % to 75 % (the shells draw at half of that, so 17 % to 37 %; container marks at 8 % of it, a faint gold tint so the machine underneath can be read; spawner marks at the full figure), 25 to 45 s, recharge 120 to 80 s (`effects.xray*` config).

## Cloaking: generated land and hidden players (2026-10-08)

- As an operator run `/bsp cloak see off` first: operators normally see through every cloak. The reply confirms it; `/bsp cloak see` shows the state and `/bsp cloak see on` puts it back. The choice survives relogging.
- Totem with Cloaking (`/bsp totem buff cloaking 2` on a placed totem) and a base built around it. Viewed from outside by a player not on the access list (or an operator after `see off`): the cube shows the land as the world generated it, not a copy of the base: ground, stone, water, trees, plants, ores, and in a cold biome snow on the ground and ice on the water, matching the surroundings; any village or other structure that stood there shows too. Caves are not made. The land matches the real ground outside the cube at the edges. Making it takes a little longer now (one chunk a tick, up to a few seconds) and may give a short stutter. The owner, operators and friends with Machines access see the base.
- The snapshot is made over a couple of seconds (one chunk a tick) without a server stall; until it is ready the old snapshot, or nothing, shows. `/bsp totem recloak` makes it again. A snapshot made by an older build (a copy of the base, or the bare land without snow, ice and trees) is thrown away and made again by itself the first time the totem ticks; `/bsp totem recloak` forces it.
- Stand outside the green aura grid: inside it everything is always shown. The totem itself is hidden too, with its orbs and aura grid. After `/bsp cloak see on`, walking in, or the cloak ending, every machine and the tank come back whole (size, plasma, work in progress), because the server resends the chunks; it takes up to two seconds.
- The totem's panel keeps its owner throughout (open it as the owner after `see on`): the earlier "unclaimed" came from the client swapping the totem block itself.
- Walk into the cube as an outsider (or with `see off`): the base does not pop in. The fake land stays and the real blocks fade in over it as see-through shells that turn solid over `effects.cloakFadeSeconds` (7 by default; the test world's config file has it), then the real blocks take over, machines' moving parts, the tank's plasma and the players inside appearing at that moment. Walk back out: the fake land is back at once and the base fades out over it in `effects.cloakFadeOutSeconds` (3 by default); step out mid-fade-in or back in mid-fade-out and the fade carries on from where it is. Both react the tick you cross the cube's edge. Set either value to 0 for a sudden change.
- Break the totem while an outsider is looking at the cloak: within two seconds the outsider sees the real base. Remove the Cloaking upgrade: same.
- A player or mob standing inside the cube is invisible to an outsider: no model, no name tag; the owner and friends with Machines access are hidden like anyone else. During the fade in or out they are drawn see-through at the same strength as the blocks (armour and held items included); name tags appear only once the fade is over. Footsteps, sounds, arrows and the shadow blob under a player are not hidden. Walk in: everything appears.
- The Nether and the End work the same way with their own land.

# Chunk loading

## Chunk loading (Anchor, Survey, CHUNKS tab, projector screen)

- [ ] The totem tree shows a sixth path with Anchor (Tier II ring) and Survey (Tier III ring). Anchor has 3 levels, Survey 2; Survey opens once Anchor is level 2. The projector's POWERS tab lists Anchor as a seventh power.
- [ ] Buy Anchor 1 on a placed totem: a CHUNKS tab appears in the header (owner and admins only). It shows a terrain map, the totem's chunk framed in turquoise, LOADED 1 / 1, RANGE 3 x 3.
- [ ] Anchor 2: LOADED 1 / 3. Click two chunks next to the totem: they frame turquoise and the count rises. A fourth click says no chunks are left. Clicking the totem's own chunk says it is always loaded. Clicking a dark (out of range) chunk says it is out of range.
- [ ] Survey 1 and 2 widen the pickable square to 5 x 5 and 7 x 7. Anchor 3 gives 6 chunks.
- [ ] Walk far away (beyond view distance): a machine in a picked chunk keeps working; one in an unpicked chunk stops. `/forge chunkforce` or F3 are not needed: check the machine's progress on return.
- [ ] Restart the server: the picks are still shown and the chunks still load without anyone visiting.
- [ ] Pick the totem up and place it elsewhere: the same layout appears around the new position. Have another player steal it: all picks are gone except the totem's own chunk, and the thief can choose again.
- [ ] Projector: the CHUNKS tab explains how to get chunk loading there until Anchor is received.
- [ ] Receive Anchor on a fed projector: the CHUNKS tab shows a map centred on the projector. The projector's own chunk is picked automatically (if the totem has one left) and cannot be removed; any other chunk within range can be picked in any order while the totem has chunks left. With the totem's allowance already full the projector map shows nothing picked and clicks say no chunks are left. Picks made here use the totem's allowance: the totem's CHUNKS tab shows them in violet and counts them.
- [ ] Cut the projector's plasma (break the cable) while you are near it: within a few seconds its chunks stop loading (the picks are kept and come back when it is fed again). Break the projector: its picks are freed.
- [ ] Second totem: with two placed totems owned by one player, the newer one shows "Not your main totem", loads only its own chunk, and refuses Anchor 2 and Survey with a message. Remove the older totem: the newer one becomes the main totem and its full allowance returns.
- [ ] Admin panel, Settings: the "Chunks: load always / owner online" button switches. With "owner online", the owner logging out unloads their chunks and logging in loads them again.
- [ ] Config: `chunks.enabled = false` stops all loading and the tab says so; `chunks.maxPerPlayer = 2` limits a player to 2 chunks in total.
- [ ] Long cable run: put a projector several chunks from the totem receiving Anchor, pick the projector's chunk, and leave the area so the cable's chunks unload. On return, a machine beside the projector has kept working. Cutting the cable while nobody is near (or with `projector.cableCheckSeconds` lowered to 5) stops the projector within one check.
- [ ] Restart the server with nobody near that projector: it is still loaded and projecting after the restart.
- [ ] Guide book: The Shatter Totem has a Chunk Loading entry and a Wave Plasma and Projectors entry.

# JEI, Assembly Guide, guide book, advancements and Jade

## JEI

JEI is loaded in dev runs (`./gradlew runClient`).
- [ ] The game starts with JEI and the item list shows all BSP-Core items.
- [ ] Pressing R on a Tetrium Nugget shows the Tetrium Crucible page (ore in, nuggets and slag out, time, fuel note) and the "9 nuggets from an ingot" crafting recipe.
- [ ] Combination Forge page has three jobs: 9 Tetrium Nuggets to an ingot, 1 Tetrium Ingot to a plate, 9 Illyrium Nuggets to an ingot with the Illyrium Forge Upgrade shown as a fitted part.
- [ ] Illyrium Crucible page has two jobs (ore + slag + lava; Pure Illyrium Dust + Tetrium Dust + lava) and shows the lava amount on hover.
- [ ] Illyrium Refinery page shows Dirty Illyrium Dust, water and the filters cycling as the fitted part.
- [ ] Shatter Coin Factory page has five jobs, one per tier, with real time and RF per coin.
- [ ] Crushing by Hand page shows pickaxe + ingot giving dust and nuggets, each with its chance on hover.
- [ ] Pressing U on a machine block shows what it makes. Pressing R on any multiblock block, the Motivator, the Shatter Totem or a Tetrium Plate shows an Information page.
- [ ] Numbers on the pages match the config after changing it and rejoining the world.
- [ ] Text on every page fits inside the page.

## JEI click area

- [ ] On each of the four machine screens, hovering the progress dial shows "Show Recipes" and clicking it opens that machine's JEI page.

## Assembly Guide

- [ ] Place an Illyrium Crucible controller, empty your hand, sneak + right-click it: the Assembly Guide opens. The same on a Refinery controller and a factory controller.
- [ ] Plain right-click still behaves as before, and its message mentions the guide.
- [ ] The controller in the guide faces you by default, whichever way the real one faces. Dragging turns and tilts the model.
- [ ] Next / Back and the arrow keys change step; Play runs through the steps and stops at the end.
- [ ] Earlier steps are full blocks, the current step pulses, later steps are faint see-through blocks that do not distract.
- [ ] Part names in the list are not cut off; the controller is listed as "Controller".
- [ ] The caption names the layer, the count and the block, with a tip underneath; nothing overlaps or runs off the panel.
- [ ] The parts list shows each block with placed / total; the current block is orange, finished ones turquoise.
- [ ] Positions in the guide match the in-world ghost blocks exactly (left and right not mirrored).
- [ ] Factory guide ends with two optional steps: a second slice beside the first, then Motivators on both.
- [ ] Sneak + right-click on a finished machine does not open the guide.
- [ ] E or Escape closes it.

## Guide book (Patchouli is loaded in dev runs)

- [ ] The game starts with Patchouli and no errors about the book `bsp_core:guide` in the log.
- [ ] A Book plus a Tetrium Nugget crafts the BSP Field Guide. It is also in the BSP creative tab.
- [ ] A brand-new player gets the guide along with the Shatter Totem on first join (where first-join grants are on).
- [ ] The book opens with four chapters: The Shatter Totem, Tetrium and Illyrium, Machines, Shatter Coins. Every entry opens; no page says a recipe is missing.
- [ ] Links between entries work (for example Fortify, Illyrium Crucible, Shatter Coin Factory).
- [ ] Build pages for the Illyrium Crucible, Illyrium Refinery and factory slice show the right blocks, with the Item Hatch left and the Pump right on the Refinery, matching the in-world ghosts. "Visualize" projects the structure in the world.
- [ ] Text fits on every page.
- [ ] In JEI (item list, bookmarks or a recipe page), hovering any multiblock block shows "Hold [Shift] for the Assembly Guide". Holding Shift fills a short bar and opens the guide for that machine; closing it returns to where you were.
- [ ] Casing, Core and Item Hatch open the Illyrium Crucible guide; Tank Glass and Pump the Refinery; factory blocks and the Motivator the factory.
- [ ] Holding Shift over the same blocks in your own inventory or a chest does not open the guide.

## Advancements

- [ ] The advancements screen has a BSP-Core tab whose first entry is earned by holding a Shatter Totem.
- [ ] Picking up or crafting each item in the chain earns its advancement with a toast: Tetrium Ore, Tetrium Crucible, Tetrium Ingot, Slag Brick, Combination Forge, Tetrium Plate, Machine Chassis, Basic Control Circuit, RF Upgrade, both Illyrium controllers, Dirty Illyrium Ingot, Pure Illyrium Dust, Illyrium Ingot, factory controller, first coin, Illyrium coin, Motivator, Totem Compass.
- [ ] Goal and challenge advancements are announced in chat; ordinary ones are not.
- [ ] The tree reads left to right without crossed lines.

## Advancements, guide book and JEI demo (2026-10-08)

- Advancements tab: 41 entries. New ones hang off the totem projector (extractor, interface, cable, base, expander, repeater, valve) and the centrifuge (charged crystal, charger, batteries, Tier IV battery, cells, emitter), plus the wrench under the chassis and raw ore under Tetrium Ore. Each is earned by holding the item.
- Guide book, The Shatter Totem category: "Wave Plasma and Projectors" (with the turnable hook-up scene and the four-interface scene), new "Pressure, Runs and Valves" (runs, reach, sharing, repeater, valve, wrench), "Batteries, Cells and the Emitter" (charged crystal, charger rules, stamping, all four batteries, battery-as-source scene, all three cells, the emitter screen). Every new block and item has a recipe or spotlight page, so its picture is in the book. Check the three scenes turn and show cables with arms, the charger facing south and the totem on the extractor.
- JEI: hover any plasma block, cable or battery in JEI and hold Shift: the Assembly Guide opens with the Wave Plasma hook-up in ten steps (extractor, totem, interface, cables, valve, repeater, base, projector, second run, charger). The interface's JEI info says so.
- docs/GUIDE_BOOK_TEXT.md is regenerated with the new pages for reading outside the game.

## Jade tooltips (needs the Jade mod; it is loaded in `./gradlew runClient`)

- [ ] With Jade installed, looking at any BSP-Core block shows its name and "BSP Core", with no extra description or status lines from BSP-Core. Blocks that hold RF show Jade's RF bar.
- [ ] Decoy Totem, as its owner: reads "Decoy Totem". As another player, while it is powered: reads "Shatter Totem", the same as a real one. Unpowered or broken, it reads "Decoy Totem".
- [ ] An assembled multiblock: looking at any part of it (casing, glass, rotor, ports) reads as the machine's name.
- [ ] Without Jade installed the game starts and plays as before (also on a dedicated server).

## Crushers from other mods (2026-10-08)

- Each of these turns a Tetrium Ingot into Tetrium Dust, a Dirty Illyrium Ingot into Dirty Illyrium Dust and an Illyrium Ingot into Pure Illyrium Dust, one for one: Mekanism Crusher, Create crushing wheels and millstone, Thermal Pulverizer, Ender IO SAG Mill, Railcraft Reborn Crusher, Integrated Dynamics Mechanical Squeezer, Electrodynamics Mineral Grinder. JEI shows the recipe under each machine.
- The recipes only load when their mod is present (`forge:mod_loaded`); the formats for Ender IO, Railcraft, Integrated Dynamics and Electrodynamics were read from those mods' sources and could not be loaded in the dev run, so in the pack check the log for a "recipe" error naming `bsp_core:compat/` on first start.
- No machine of any mod turns BSP ore or raw ore into anything: ore goes through the Tetrium Crucible, the Illyrium Crucible and the Magnetic Centrifuge only.

## EMI (2026-10-08)

- With EMI as the recipe viewer (the pack has it beside JEI): each BSP machine has a category named like the block (Tetrium Crucible, Combination Forge, Illyrium Crucible, Illyrium Refinery, Magnetic Centrifuge, Coin Pressing, Crushing by Hand) with the same jobs, times, fuel notes and output tooltips as in JEI; the machine item is the workstation. Every block without a crafting recipe (multiblock parts, plasma blocks, batteries, cells, emitter, wrench, totem, plate) shows its information page.
- EMI has no Shift-hover hook, so the Assembly Guide opens only from the controller (sneak + right-click) or in JEI.
- In dev `./gradlew runClient` now loads EMI too, so EMI is what you see there; JEI still loads underneath for the Jade and guide hooks.

# Network storage and cross-server

## Network storage (needs a MySQL or MariaDB database)

Use an empty test database and an account with CREATE, SELECT, INSERT, UPDATE, DELETE on it.

**Setup on one server**
- [ ] With `storage.mode = "local"`, `/bsp storage status` says records are kept in this world only, and everything behaves as before.
- [ ] Fill in host, port, database, user, password and a `serverId`; restart; `/bsp storage test` reports "Connected" and the four `bsp_` tables exist in the database. With a wrong password it reports that it could not connect, with the reason.
- [ ] `/bsp storage migrate` reports grants added, totems and slices written; the rows are in the database; the world's own records are unchanged. Running it again refuses; `/bsp storage migrate force` runs again.
- [ ] Set `mode = "mysql"`, restart: the log says it connected, and `/bsp storage status` shows MySQL.
- [ ] Place and break a totem and a factory controller: rows appear in and disappear from `bsp_totems` and `bsp_factory_slices` with this server's id.
- [ ] Stop the database while the server runs: the game keeps working, errors appear in the log, and it recovers when the database is back.

**Across two servers sharing the database (different `serverId`)**
- [ ] A new player joins server A and gets a totem and book; joining server B gives nothing. `/bsp totem reset <player>` on either lets them be granted again.
- [ ] A player with slices on server A sees them counted on server B: the screen's "you own" count includes them (after rejoining B), and the limit of 10 is enforced across both.
- [ ] Without logging out of server B, have slices added for the same player on server A (second client or direct database row), then place a controller on B that takes the total over 10: it is removed a moment later, returned to the inventory, and the limit message shows.
- [ ] Migrating server B merges: players already granted in the database are reported as "already in the database".

**Release jar**
- [ ] On a plain Forge server with only `bsp-core-<version>.jar` (not `-slim`), `/bsp storage test` connects: the bundled driver is found.

### Vaults, seasons and prizes across two servers (same database)
- [ ] After a restart on the new build, the four new tables exist: `vault_totals`, `state`, `season_claims`, `pending_items`. The first server to connect fills `state`.
- [ ] Vault limit: place vault blocks on server A, then straight away (no waiting) on server B the same player can only reach the network total of 27: a block over the limit is placed for a moment, then removed and returned with a red message, keeping any lock and alarm it carried. Breaking a block on A frees a place on B at once.
- [ ] The admin panel on either server shows a player's vault coins and block count added up across both.
- [ ] A player with a Netherite coin in a vault on both servers earns the Illyrium Ingot interest on one of them only.
- [ ] Put prizes in on server A: within a refresh, "Prizes and rewards" and a Prizes Score Screen on server B show the same rows. The holder reward hours set on A show on B.
- [ ] End the season on A with a player online on B: within a refresh B announces it, B's totems are gone and that player gets one fresh totem. A player who then moves from B to A does not get a second one.
- [ ] A player who was offline gets exactly one fresh totem at their next login, on whichever server that is.
- [ ] A prize winner who is on server B (or offline) when A ends the season receives the prize on B within a refresh (or at login anywhere).
- [ ] Full reset on A removes coins on B too (vaults, trays, online players; offline players at login).
- [ ] With an automatic holder reward set, each payout happens once, not once per server.
- [ ] A server that joins the network with a lower season number than the database has its totems removed, as if it had missed a season end. Check this is what you want before connecting an old world.

## Cross-server warnings, compass and guide pictures

- [ ] Two servers on one database: with the owner on server B, start stealing their totem on server A. Within a few seconds the owner sees the red warning on B, starting with A's server name in square brackets. The "stolen" and "steal failed" messages arrive the same way.
- [ ] The same for a totem with the Alarm upgrade when an intruder walks in, and for a Coin Vault with an Alarm when its lock is picked.
- [ ] With the owner on the same server, messages arrive instantly and without a server name, as before.
- [ ] Totem Compass: with none of your totems placed on this server (or in this dimension) the needle spins. While tracking rivals with no rival totem on this server it also spins. It never points at a totem on another server.
- [ ] After adding screenshots and running the two commands in `docs/book_screenshots/README.md`, the Illyrium Crucible, Illyrium Refinery and Shatter Coin Factory entries show the picture with a frame instead of the block layout.

# Game tests (headless, no client)

## Running the Forge GameTests (2026-10-08)

- `./gradlew runGameTestServer` starts a headless server, runs every test in `src/main/java/com/mrgregles/bsp_core/gametest/`, prints a pass/fail line per test and exits; the Gradle task fails when a test fails. Claude can run this, so these checks no longer need screenshots.
- Each test builds its own blocks inside the empty 24 x 10 x 24 template `bsp_core:empty` (written by `tools/gen_gametest_structures.py`).
- Tests so far: the tank forms when complete (flags, master, capacity, port), rejects glass on an edge, keeps plasma while a block is missing; `allocate` is max-min fair; plasma reaches a base over 3 Tetrium cables; 15 Tetrium cables reach, 16 do not; an Output 10 totem moves 20,000 mB a second through one Illyrium cable into a tank port.
- Add a test for each new rule or fix where the client is not needed: anything about forming, flow figures, reach, caps and sharing.

# Fix-up rounds (dated)
- [ ] The game test server keeps its own world config in `run/world/serverconfig/bsp_core-server.toml`, which does not follow a changed default. After changing a machine number in `BSPConfig`, edit that file too (or delete it so it regenerates); the crucible test reads its timing from the loaded config and separately asserts the shipped default, so a stale file fails it with a clear message.

## Fix-ups 2026-10-06

- [ ] Mining Tetrium, Illyrium or Magnatite ore drops a raw item (two or three with Fortune III); Silk Touch drops the block. The Tetrium Crucible, Illyrium Crucible and Magnetic Centrifuge take the raw items.
- [ ] Buying Recall level 1 to 7 on a Tier V totem works and charges Illyrium coins.
- [ ] Cables are glass pipes in the cable's colour with dark rails. A run carrying plasma shows the plasma inside: a level that rises with the flow (full at 100 mB/t, low behind repeaters or on a short supply) and bright pulses travelling toward the projector. Break the supply and the pipe empties within a second or two. Jade on a carrying cable reads "Wave Plasma: N mB/t", on an idle one "No plasma flowing".
- [ ] A repeater placed while looking along the cable points that way (lit cap at the front). Plasma passes only front-ways: a repeater placed backwards leaves the projector with NO SIGNAL; right-click it empty-handed and it turns round and works.
- [ ] `/bsp totem buff cloaking 2` while looking at a placed totem gives it Cloaking 2 (and `/bsp totem buff xray 3` while holding a totem gives the held one X-ray 3 for the X-ray test); `/bsp totem tier 5` raises the tier; `/bsp totem recloak` re-takes the cloak copy after building inside it.
- [ ] Repeaters show turquoise arrows on their top and both sides pointing out of the lit front, in every one of the six facings (including up and down). Their five copper rings sit still when idle and swell and nudge forward one after another while plasma passes.
- [ ] Wrench (two Tetrium Ingots over a Tetrium Ingot over a Stick): right-click a repeater steps it east, south, west, north, up, down; sneak + right-click turns it round. On a placed totem or a battery it turns them; on a plain block (stone) it does nothing. The cables re-join after the turn.

## Fix-ups 2026-10-07

- [ ] Cut a cable in a carrying run: the cables on the far side of the cut drain and empty within about three seconds, with no movement left in them; the near side keeps flowing. Take a repeater out: the same on its far side. Put the cable back: the run fills again within two seconds.
- [ ] The plasma inside a pipe looks like a liquid: a level with a rippling surface, the ripples running the way the plasma goes, no cubes. Behind a repeater the level is lower.
- [ ] Repeater rings are shaded copper (lighter on top, darker underneath) and no longer glare; while pumping a warm highlight runs over them.
- [ ] Projector Base: a low tank with a nozzle on each side. Cables join its sides and not the projector. A projector with no base under it, or a base with no cable, reads NO SIGNAL; stood on a fed base it projects, and STATUS shows the base's tank.
- [ ] Aura cubes have hair-thin edges and a faint one-block grid on every face that breathes slowly. Hiding auras removes the lot.
- [ ] The AURAS control in the totem panel's header is a slide switch like the machines' power switch: turquoise with the knob right when on, red with the knob left when off; the AURAS label sits beside it.
- [ ] Projector Base: the plasma level shows through the windows on every side, with a brighter line at its surface and no flicker. Fed by a 100 mB/t run it fills to the top in a few seconds (the projector burns 20 mB/t); cut the cable and it drains over about twelve seconds while the projector keeps projecting ("PROJECTING (on the base's tank)"), then stops.
- [ ] Plasma Interface: right-click opens a screen with the group in 3D: its blocks, the extractors it draws from (gold mB/t), every cable and repeater with the mB/t passing it (blue, a dash for nothing), and the bases and chargers reached. Drag to turn. Refused blocks pulse red. The right side totals supply, blocks joined, extractors, cables, receivers, delivered and spare.
- [ ] An interface touching an extractor shows a glass tube into the extractor's porthole; two touching interfaces show a sleeve and headers running through the seam.

## Fix-ups 2026-10-08 (late)

- X-ray can be bought once Night Sight is at level 1 (its only level) and the totem is Tier V. The tree's "needs Night Sight 2" line is gone. The same rule covers any one-level parent.
- Battery Charger screen: all nine Carried powers fit above the inventory; the "Cells take Carried powers" note sits between the list and the Inventory label. The screen is taller.
- Battery Charger block: a battery stands in the cradle as a small block, a cell as an upright card facing the open front, as soon as it goes in; it vanishes when taken out. The pads still light only while fed.
