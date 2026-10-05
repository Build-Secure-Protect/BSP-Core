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

## Aura spheres and other models
- [ ] Stand outside a Fortify or Healing sphere and look through it at a machine, a factory, another totem, a chest and an item frame on the far side: all of them stay visible, tinted by the sphere. The same from inside the sphere looking out.
- [ ] The sphere itself still looks the same: a faint coloured bubble that pulses gently.

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

## Cross-server warnings, compass and guide pictures
- [ ] Two servers on one database: with the owner on server B, start stealing their totem on server A. Within a few seconds the owner sees the red warning on B, starting with A's server name in square brackets. The "stolen" and "steal failed" messages arrive the same way.
- [ ] The same for a totem with the Alarm upgrade when an intruder walks in, and for a Coin Vault with an Alarm when its lock is picked.
- [ ] With the owner on the same server, messages arrive instantly and without a server name, as before.
- [ ] Totem Compass: with none of your totems placed on this server (or in this dimension) the needle spins. While tracking rivals with no rival totem on this server it also spins. It never points at a totem on another server.
- [ ] After adding screenshots and running the two commands in `docs/book_screenshots/README.md`, the Illyrium Crucible, Illyrium Refinery and Shatter Coin Factory entries show the picture with a frame instead of the block layout.

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

## Magnatite (materials only; the centrifuge comes next)
- [ ] Magnatite Ore and Deepslate Magnatite Ore are in the BSP creative tab and show dark specks with a blue sheen on stone and deepslate. A diamond pickaxe drops the ore block; an iron pickaxe drops nothing.
- [ ] In a new world the ore is found below Y 8, mostly in deepslate, somewhat more often than diamond.
- [ ] Magnatite Nugget and Ingot have the vanilla iron shapes in dark blue-grey; the Charged Magnatite Ingot is bright blue and glints; Carbon Dust is a black powder; the Copper Tetrium Coil shows copper windings.
- [ ] Nine nuggets craft into an ingot and back. The Combination Forge accepts nine Magnatite Nuggets and makes an ingot.
- [ ] The Copper Tetrium Coil recipe (Copper Ingots, Carbon Dust, Tetrium Coils around a Magnatite Ingot) shows in JEI.

## Magnetic Centrifuge
- [ ] All four blocks are craftable and in the creative tab. Placing the controller shows ghost blocks for the other eight; sneak + right-click opens the Assembly Guide, and holding Shift over any of the blocks in JEI opens it too.
- [ ] Built in all four facings (controller front centre, Rotor in the middle, Item Hatch left, Power Port right as seen from the front), the blocks hide and the Armoured Spin Drum appears: plated sides with slit windows, hazard stripes, lamps, the drum inside. Breaking any block brings the blocks back.
- [ ] With no RF the screen lists "Needs 60 RF per tick" and nothing runs. With a cable on the Power Port the RF gauge fills and it runs; cables on the casing or controller do nothing. Hoppers and pipes work on the Item Hatch only.
- [ ] One Magnatite Ore (either kind) takes 45 seconds and gives 3 nuggets and 1 Carbon Dust. The drum spins while working and the lamps turn green; a redstone signal pauses it.
- [ ] A Magnatite Ingot with no coil does nothing and the screen says to fit a coil. With the Copper Tetrium Coil fitted it tries every 45 seconds; over many tries about 1 in 6 succeed, and a failed try leaves the ingot in the input slot. The copper band behind the windows glows blue while charging.
- [ ] Stack a second layer (Rotor + 8 Casing): its blocks hide, a second drum appears turning the other way, and the status line at the top right of the screen shows the layers and the new numbers. Test up to six: nuggets rise to 7 to 9, the charge chance reaches 1 in 2 at five, and at six a try takes 25 seconds. A seventh layer is ignored.
- [ ] Removing a block from a middle layer drops the layers above it back to ordinary blocks and the numbers fall.
- [ ] Power use rises with layers (60 per layer separating, 240 per layer charging).
- [ ] The status line does not overlap the title, the state text or the PORTS list on the machine screen.
- [ ] The operator demo switch spins the drums with nothing in the machine.
- [ ] JEI shows a Magnetic Centrifuge category with both jobs, opened by clicking the dial on the machine screen. The guide book has "Magnatite" and "Magnetic Centrifuge" entries. The three new advancements are granted.

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

## Totem Generator, Projector and Cables
- [ ] All blocks and the four socket parts are in the creative tab with recipes in JEI. Each cable kind has its own colour (purple, blue, turquoise, pale cyan).
- [ ] A generator placed under a placed, owned totem and given RF: a turquoise ring rises around the totem again and again. With no RF there is no ring.
- [ ] Nine joined generators with one RF cable into one of them all run (given about 1,800 RF per tick); with too little power some report NO POWER; a second cable into another generator adds throughput. The RF bar on every generator's screen shows the shared total.
- [ ] Another player right-clicking the generators gets a red message and no screen. After stealing the totem, that player can open them and the previous owner cannot. An open screen closes when the totem changes hands. The generator shows three ports on each of its four sides.
- [ ] Generators placed beside it join into one slab: the seam between them disappears, ports show only on outer edges, and the ring widens (wider with the four side ones, three blocks across with all nine). A generator that only touches the array diagonally, or lies outside the three by three, does not work (its screen says NO TOTEM).
- [ ] Right-clicking a single generator opens its screen. Right-clicking any generator in a joined array opens a three by three grid (north up) with a lit square per generator, the clicked one outlined in gold; clicking a square opens that generator's screen.
- [ ] Cables join to each other in all six directions, to a generator's sides and bottom (not its top), and to any face of a projector, with a link and lit band on each joined side.
- [ ] With a cable run to a projector and RF on both: the screen says CHOOSE A POWER; switching on a power the totem has makes it SENDING and shows the level that arrives. A third power is refused without a Channel Expander. Powers the totem lacks are greyed "not unlocked".
- [ ] Reach: with Tetrium Core cable a run of 15 works and 16 is OUT OF REACH. With Charged Illyrium Core cable and no amplifier, 16 is still out of reach; Mk I allows 25, Mk II 40, Mk III 80. A run with one Tetrium Core block in it is limited to 15. Amplifiers must be fitted in order.
- [ ] Strength: next to the generator the powers arrive at full level; at the end of the working run at about half, never below 1. The screen's strength percentage matches.
- [ ] The projector lights up, with shards circling, only while it is being fed and has RF. Fortify and Healing Aura show their spheres round the projector at the reduced level.
- [ ] Each power works at the projector as it does at the totem: Fortify slows non-owner mining and resists explosions; Healing Aura heals the owner; Alarm makes intruders glow and tells the owner; Ward weakens intruders; Sanctuary stops mob spawns; Overclock speeds BSP machines nearby.
- [ ] Two generators in one array with their own cables drive two projectors, each with its own two powers. Two generators whose cables meet do not share one projector: the second finds another or reports NO PROJECTOR.
- [ ] Removing RF from either block, breaking a cable, or picking up the totem stops the aura within a few seconds. If the totem is stolen, the projector now works for the thief.
- [ ] Breaking a generator drops its socket parts. Settings and stored RF survive a restart.

## Wrong-block marker and projector status
- [ ] Build a Magnetic Centrifuge with a Factory Power Port in place of the Centrifuge Power Port: the wrong block gets a pulsing red box with a small Centrifuge Power Port floating above it, and right-clicking the controller prints a red chat line naming both blocks and the position. Swapping in the right block forms the machine within a second.
- [ ] The same marker appears on the Illyrium Crucible, Illyrium Refinery and Shatter Coin Factory when a wrong block is in a part's place.
- [ ] A Totem Projector joined by cable to a sending generator, with no RF fed into the projector itself: its seams glow amber and right-click says it needs its own RF. Feed it RF: it turns turquoise, the shards appear, and with Fortify or Healing Aura being sent their spheres show around the projector, as they do around a totem.
- [ ] A projector with no generator sending to it stays dark and right-click says "No signal".
- [ ] Right-clicking a projecting projector lists the powers and their levels.

## Jade tooltips (needs the Jade mod; it is loaded in `./gradlew runClient`)
- [ ] With Jade installed, looking at any BSP-Core block shows its name and "BSP Core", with no extra description or status lines from BSP-Core. Blocks that hold RF show Jade's RF bar.
- [ ] Decoy Totem, as its owner: reads "Decoy Totem". As another player, while it is powered: reads "Shatter Totem", the same as a real one. Unpowered or broken, it reads "Decoy Totem".
- [ ] An assembled multiblock: looking at any part of it (casing, glass, rotor, ports) reads as the machine's name.
- [ ] Without Jade installed the game starts and plays as before (also on a dedicated server).

## Chunk loading (Anchor, Survey, CHUNKS tab, projector screen)
- [ ] The totem tree shows a sixth path with Anchor (Tier II ring) and Survey (Tier III ring). Anchor has 3 levels, Survey 2; Survey opens once Anchor is level 2. The Totem Generator screen lists Anchor as a seventh power and its sockets and inventory still line up.
- [ ] Buy Anchor 1 on a placed totem: a CHUNKS tab appears in the header (owner and admins only). It shows a terrain map, the totem's chunk framed in turquoise, LOADED 1 / 1, RANGE 3 x 3.
- [ ] Anchor 2: LOADED 1 / 3. Click two chunks next to the totem: they frame turquoise and the count rises. A fourth click says no chunks are left. Clicking the totem's own chunk says it is always loaded. Clicking a dark (out of range) chunk says it is out of range.
- [ ] Survey 1 and 2 widen the pickable square to 5 x 5 and 7 x 7. Anchor 3 gives 6 chunks.
- [ ] Walk far away (beyond view distance): a machine in a picked chunk keeps working; one in an unpicked chunk stops. `/forge chunkforce` or F3 are not needed: check the machine's progress on return.
- [ ] Restart the server: the picks are still shown and the chunks still load without anyone visiting.
- [ ] Pick the totem up and place it elsewhere: the same layout appears around the new position. Have another player steal it: all picks are gone except the totem's own chunk, and the thief can choose again.
- [ ] Projector: right-click opens the new screen for anyone, with status, RF bar and one chip per power (hover for the name). Without Anchor being sent it explains how to get chunk loading there.
- [ ] Send Anchor from the generator to a powered projector: the projector screen shows a map centred on the projector. The projector's own chunk is picked automatically (if the totem has one left) and cannot be removed; any other chunk within range can be picked in any order while the totem has chunks left. With the totem's allowance already full the projector map shows nothing picked and clicks say no chunks are left. Picks made here use the totem's allowance: the totem's CHUNKS tab shows them in violet and counts them.
- [ ] Cut the projector's RF or the generator's signal while you are near it: within a few seconds its chunks stop loading (the picks are kept and come back when it is fed again). Break the projector: its picks are freed.
- [ ] Second totem: with two placed totems owned by one player, the newer one shows "Not your main totem", loads only its own chunk, and refuses Anchor 2 and Survey with a message. Remove the older totem: the newer one becomes the main totem and its full allowance returns.
- [ ] Admin panel, Settings: the "Chunks: load always / owner online" button switches. With "owner online", the owner logging out unloads their chunks and logging in loads them again.
- [ ] Config: `chunks.enabled = false` stops all loading and the tab says so; `chunks.maxPerPlayer = 2` limits a player to 2 chunks in total.
- [ ] Long cable run: put a projector several chunks from the totem with Anchor sent to it, pick the projector's chunk, and leave the area so the cable's chunks unload. On return, a machine beside the projector has kept working. Cutting the cable while nobody is near (or with `projector.cableCheckSeconds` lowered to 5) stops the projector within one check.
- [ ] Restart the server with nobody near that projector: it is still loaded and projecting after the restart.
- [ ] Guide book: The Shatter Totem has a Chunk Loading entry, and Generators and Projectors ends with a page on the projector screen.
