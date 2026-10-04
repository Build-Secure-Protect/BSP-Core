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

## Totem Compass
- [ ] Craft it (4 Tetrium Ingots around 1 Illyrium Ingot) or take it from the BSP tab. Holding it, the needle points toward your nearest placed totem and turns as you turn. Check it points the right way, not mirrored.
- [ ] Right-click: the screen shows "Pointing to your nearest totem". Click a coin button: one coin of that tier leaves your inventory and "Loaded" rises by 1, 5, 10, 30 or 60 seconds.
- [ ] Press Start tracking: the needle swings to the nearest totem owned by someone else (assign a second totem to a dummy name as an operator to test). The timer counts down.
- [ ] When tracking ends the screen shows a 10:00 cooldown, during which Start tracking is disabled and the needle points to your own totem again.
- [ ] Buy a cooldown upgrade: coin value is taken and the cooldown length drops by one minute, down to 3:00 after seven levels.

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

## JEI click area
- [ ] On each of the four machine screens, hovering the progress dial shows "Show Recipes" and clicking it opens that machine's JEI page.

## Redstone control
- [ ] A lever or redstone torch next to a working Tetrium Crucible or Combination Forge pauses it; the header reads PAUSED BY REDSTONE. Removing the signal resumes it.
- [ ] The same at the controller block of the Illyrium Crucible and Illyrium Refinery.
- [ ] A signal at one factory slice's controller pauses only that slice: its lane is framed red, the tooltip says it is paused by redstone, and its belts stop. Other slices keep pressing. No press time is counted while paused.

## Coin loot
- [ ] `/loot give @s loot minecraft:chests/simple_dungeon` run many times sometimes gives Copper (1 to 2) and Gold Shatter Coins; the same for abandoned_mineshaft and the three stronghold tables.
- [ ] `minecraft:chests/end_city_treasure` and `minecraft:chests/ancient_city` occasionally give a Diamond Shatter Coin.
- [ ] No table ever gives a Netherite or Illyrium Shatter Coin.

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

## Animated single-block machines
- [ ] Tetrium Crucible, idle: dim burner window, dark coils, low melt, tip glowing. Working: burner glows orange (yellow on RF), coils light in sequence, the melt rises and turns pale near the end, six collar lamps fill, the tip flashes white at the end.
- [ ] Slag piles up in one tray and nuggets in the other as output collects, and empty again when taken. An RF Upgrade shows as a capacitor on the collar.
- [ ] Combination Forge, idle: press head parked high. Working: the head strikes, faster near the end; the nuggets shrink as a glowing ingot grows; rail lights chase; five bed lamps fill. Illyrium jobs glow turquoise.
- [ ] The left front socket lights turquoise with the Illyrium Forge Upgrade, the right one yellow with an RF Upgrade.
- [ ] Both look right in all four facings, and their inventory icons still show the complete machine.

## Totem Compass screen
- [ ] The right-hand button reads "Shorten cooldown" and fits inside its frame. Under it is the price as a coin icon and "x4 Copper" (red when you do not have enough). Buying takes exactly those coins and nothing else; the next level asks for Gold.
- [ ] Five coin tiles with the tracking time each adds; clicking one spends a coin and raises STORED. Start and the upgrade button grey out when they cannot be used. Header shows READY, TRACKING or COOLDOWN with a clock.

## Demo mode (operators)
- [ ] As an operator, every machine screen shows an OPERATOR box with DEMO OFF to the left of the inventory. A non-operator sees no box.
- [ ] Switch it on with the machine empty: Tetrium Crucible (burner, coils, melt rising, lamps), Combination Forge (head striking, charge fusing), Illyrium Crucible (melt, stirring cross, rings, lava gauges part full), Illyrium Refinery (water, agitator, centrifuge), all looping.
- [ ] Shatter Coin Factory: demo on any controller sets the whole joined machine going: belts, press, blanks turning into coins, changing coin every ten seconds. Unformed slices show nothing.
- [ ] Nothing is produced or consumed in demo mode; a real job still runs normally with demo on or off.
- [ ] Demo stays on after leaving and rejoining the world, and switches off cleanly.
- [ ] A non-operator cannot switch demo, even by clicking where the button would be.

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

## Aura spheres and other models
- [ ] Stand outside a Fortify or Healing sphere and look through it at a machine, a factory, another totem, a chest and an item frame on the far side: all of them stay visible, tinted by the sphere. The same from inside the sphere looking out.
- [ ] The sphere itself still looks the same: a faint coloured bubble that pulses gently.

