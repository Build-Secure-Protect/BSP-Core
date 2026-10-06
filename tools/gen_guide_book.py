#!/usr/bin/env python3
"""Generates the BSP-Core guide book for Patchouli (no third-party dependencies).

  python3 tools/gen_guide_book.py

Writes the book definition (data pack) and its categories and entries (resource pack). Edit the
text here and rerun; never hand-edit the outputs. Multiblock pages are built from the same layer
patterns the controllers use (layers bottom to top, rows back to front, X = controller).
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BOOK = ROOT / "src/main/resources/data/bsp_core/patchouli_books/guide"
CONTENT = ROOT / "src/main/resources/assets/bsp_core/patchouli_books/guide/en_us"

BLOCKS = {"C": "bsp_core:illyrium_casing", "P": "bsp_core:lava_pylon", "H": "bsp_core:item_hatch", "O": "bsp_core:illyrium_core", "G": "bsp_core:illyrium_glass",
          "U": "bsp_core:refinery_pump", "F": "bsp_core:factory_frame", "B": "bsp_core:factory_blank_hatch", "R": "bsp_core:factory_power_port", "S": "bsp_core:factory_press",
          "K": "bsp_core:centrifuge_casing", "T": "bsp_core:centrifuge_rotor", "W": "bsp_core:centrifuge_power_port"}
CRUCIBLE = [["CCC", "CCC", "CXC"], ["P P", "HOH", "P P"], ["PCP", "C C", "PCP"]]
REFINERY = [["CCC", "COC", "CXC"], [" C ", "HGU", "   "], ["   ", " G ", "   "]]
FACTORY = [["B", "F", "X"], ["R", "S", "F"]]
CENTRIFUGE = [["KKK", "HTW", "KXK"]]


def multiblock(layers, controller):
    """Our pattern to Patchouli's: layers top to bottom, strings along x, characters along z, controller ('0') facing north."""
    rows, cols = len(layers[0]), len(layers[0][0])
    out = []
    for layer in reversed(layers):
        out.append(["".join({"X": "0", " ": "_"}.get(layer[rows - 1 - j][cols - 1 - i], layer[rows - 1 - j][cols - 1 - i]) for j in range(rows)) for i in range(cols)])
    used = {ch for layer in layers for row in layer for ch in row} - {"X", " "}
    mapping = {ch: BLOCKS[ch] for ch in sorted(used)}
    mapping["0"] = f"{controller}[facing=north]"
    return {"pattern": out, "mapping": mapping, "symmetrical": False}


def text(body, title=None):
    page = {"type": "patchouli:text", "text": body}
    if title:
        page["title"] = title
    return page


def spot(item, body, title=None):
    page = {"type": "patchouli:spotlight", "item": item, "text": body}
    if title:
        page["title"] = title
    return page


def craft(first, second=None, body=None):
    page = {"type": "patchouli:crafting", "recipe": "bsp_core:" + first}
    if second:
        page["recipe2"] = "bsp_core:" + second
    if body:
        page["text"] = body
    return page


def build(name, layers, controller, body):
    """A picture of the finished machine if one has been made (see docs/book_screenshots), otherwise the turnable block layout."""
    machine = controller.split(":")[1]
    if (ROOT / f"src/main/resources/assets/bsp_core/textures/gui/book/{machine}.png").exists():
        return {"type": "patchouli:image", "title": name, "images": [f"bsp_core:textures/gui/book/{machine}.png"], "border": True, "text": body}
    return {"type": "patchouli:multiblock", "name": name, "multiblock": multiblock(layers, controller), "text": body}


CATEGORIES = [
    ("totem", "The Shatter Totem", "Your totem is the heart of BSP: build around it, protect it, and take other players' totems if you can.", "bsp_core:shatter_totem"),
    ("materials", "Tetrium and Illyrium", "Two new metals, from first ore to refined ingot.", "bsp_core:illyrium_ingot"),
    ("machines", "Machines", "The parts every machine is made of, and how to build the two Illyrium multiblocks.", "bsp_core:illyrium_crucible"),
    ("coins", "Shatter Coins", "Coins pay for the strongest totem upgrades. You press them yourself.", "bsp_core:gold_shatter_coin"),
]

GUIDE_TIP = "$(br2)Stuck on a build? Empty your hand and $(l)sneak + right-click$() the controller for the step-by-step Assembly Guide."

ENTRIES = [
    ("totem", "your_totem", "Your Shatter Totem", "bsp_core:shatter_totem", 0, [
        text("Every player is given one $(item)Shatter Totem$() the first time they join. It cannot be crafted and it cannot be destroyed.$(br2)Carry it, or place it in your base. A placed totem is where your upgrades and auras work from, so most players build their base around it."),
        text("Only one totem is ever yours by right, but you can hold others: any totem you steal becomes yours.$(br2)If you die to another player while carrying your totem, it is placed where you fell. A totem dropped on the ground places itself after a short while.", "Keeping it"),
    ]),
    ("totem", "stealing", "Stealing and Defending", "minecraft:iron_sword", 1, [
        text("Right-click another player's placed totem and choose to steal it. A timer starts, and the owner is warned.$(br2)Stay close to the totem until the timer runs out and it is yours, with every upgrade on it."),
        text("The steal is cancelled if the thief is killed, or stays too far from the totem for too long.$(br2)So defend in person, and make the room around your totem hard to stand in. The $(l:totem/upgrades)Fortify$() upgrade helps your walls survive explosions.", "Stopping a thief"),
    ]),
    ("totem", "upgrades", "The Upgrade Tree", "minecraft:experience_bottle", 2, [
        text("Right-click with the totem in your hand, or right-click your placed totem, to open its upgrade tree. Upgrades belong to the totem, so they move with it if it is stolen.$(br2)Your totem has five tiers, I to V. Each tier has its own Shatter Coin: Copper, Gold, Diamond, Netherite, Illyrium."),
        text("Raising the totem a tier costs coins of the tier you are leaving, plus XP levels. A new tier opens new upgrades and two more levels on the ones you have.$(br2)Upgrades sit on paths. The next one opens when the one before it reaches level 2. Every level costs the coin of the tier it is bought at, plus XP.", "Tiers and paths"),
        text("Work while the totem is in your $(l)offhand$(). Paid in experience levels.$(br2)$(li)Damage, Resistance, Mining Speed$(li)Swiftness: move faster$(li)Vitality: extra hearts$(li)Featherfall: less fall damage$(li)Night Sight: see in the dark", "Carried"),
        text("Work only while the totem is $(l)placed$(). Paid in $(l:coins/shatter_coins)Shatter Coins$().$(br2)$(li)Fortify: blocks nearby resist explosions and intruders mine slower$(li)Healing Aura: heals you nearby$(li)Ward: weakens intruders$(li)Alarm: outlines intruders and warns you$(li)Sanctuary: no hostile spawns$(li)Deadlock: your totem takes longer to steal$(li)Overclock: machines nearby work faster", "Base"),
        text("Help you take other totems. They count when the totem that has them is in your offhand as you start a steal. Paid in Shatter Coins.$(br2)$(li)Lockpick: your steals take less time$(li)Shroud: the owner is warned late$(br2)No upgrade can make a totem impossible to steal: blocks can always be mined, and a steal always has a time limit.", "Raid"),
    ]),
    ("totem", "scoring", "Scores and the Leaderboard", "minecraft:gold_block", 4, [
        text("Every totem you own scores points by its tier:$(br2)$(li)Tier I: 1 point$(li)Tier II: 2 points$(li)Tier III: 4 points$(li)Tier IV: 7 points$(li)Tier V: 10 points$(br2)Your score is the total for all your totems, so stealing totems and upgrading them both move you up."),
        craft("score_screen", None, "$(item)Score Screen$() panels placed side by side and above each other, facing the same way, join into one display of up to 8 wide and 6 high. It shows the leaderboard, these scoring rules, or the season's prizes."),
        text("The server runs in seasons. When a season ends, the top three players receive that season's prizes, every totem is removed, and every player gets one fresh Tier I totem.$(br2)A Score Screen set to Prizes shows what first, second and third place will win. If you are offline when the season ends, your prizes and your new totem are waiting when you next log in.", "Seasons and Prizes"),
        text("A placed totem counts for its owner. A carried totem counts for whoever is carrying it.$(br2)Ties go to the player with more totems, then to the higher tier. Score Screens show the current leaderboard, and on a network it covers every server.", "How it is counted"),
    ]),
    ("totem", "decoys", "Decoy Totems", "bsp_core:decoy_totem", 5, [
        spot("bsp_core:decoy_totem", "A fake totem. While it has power, rival $(l:totem/compass)Totem Compasses$() within its range point at it instead of a real totem, and everyone but you sees an ordinary Shatter Totem. You see the idol with a ghost of the totem around it.$(br2)You may have five placed."),
        craft("magnet_core", "decoy_totem", "The $(item)Magnet Core$() is the hard part: six Charged Magnatite Ingots around an Illyrium Processor."),
        craft("decoy_power_base", None, "A decoy must stand on a $(item)Decoy Power Base$(), fed with RF from its sides or from below. It draws 100 RF every tick. Without power the disguise drops and everyone sees the bare idol."),
        text("An enemy who tries to steal a decoy finds out it is fake. Its traps go off and it takes a hit. When it has taken more hits than it has casings it breaks, and does nothing until you repair it with Magnatite Ingots.$(br2)Only you can mine a working decoy. Anyone can mine a broken or unpowered one.", "When someone takes the bait"),
        text("Right-click your decoy to fit parts. A socket opens when the one before it is filled.$(br2)$(li)$(l)Range$(): Range Coils Mk I, II, III widen the compass range from 24 blocks to 40, 64 and 96.$(li)$(l)Traps$(): a charge, then a Trap Amplifier, then a second charge.$(li)$(l)Casing$(): each Reinforced Casing survives one more attempt.", "Sockets"),
        text("Each charge is used up when it goes off.$(br2)$(li)Blast: an explosion that hurts the thief and breaks nothing.$(li)Hex: Slowness, Weakness and Glowing.$(li)Poison.$(li)Fatigue: Mining Fatigue.$(li)Warp: throws the thief up to 30 blocks away.", "Trap charges"),
        craft("range_coil_mk1", "range_coil_mk2"),
        craft("range_coil_mk3", "trap_amplifier"),
        craft("blast_charge", "warp_charge", "The other charges swap the middle item: Fermented Spider Eye, Spider Eye or Prismarine Shard."),
        craft("reinforced_casing"),
    ]),
    ("totem", "projectors", "Wave Plasma and Projectors", "bsp_core:totem_projector", 6, [
        text("A placed totem gives off $(l)Wave Plasma$(): 100 mB every tick, more with the $(l)Output$() upgrade. Drawn out and piped to a $(item)Projector$(), it recreates the totem's base powers as a second aura somewhere else: over a mine, a farm, or a second wall.$(br2)The projector works for whoever owns the totem. If your totem is stolen, so is the aura."),
        text("The chain, in order:$(br)$(li)$(item)Plasma Extractor$() directly under the totem. More stack below; the flow is split between them.$(li)$(item)Plasma Interface$() touching an open face of an extractor. Cables plug into it.$(li)$(item)Plasma Cable$() from the interface to the projector.$(li)$(item)Projector$() at the end.", "How it joins up"),
        craft("plasma_extractor", "plasma_interface", "Interfaces that touch join into one, up to twelve blocks in any shape. More than twelve, or one touching a drum another interface already serves, shows a lit seam and does nothing: an interface is not a cable."),
        craft("totem_projector", "channel_expander", "A projector needs 100 mB/t arriving. Right-click it: POWERS lists what the interface offers with a RECEIVE switch each; CHUNKS is the chunk picker; STATUS shows the pressure. It receives two powers at once, three with a $(item)Channel Expander$() fitted by right-clicking it."),
        text("A cable carries its whole run at full pressure. Each kind has a longest run:$(br2)$(li)Tetrium Core: 15 blocks$(li)Magnatite Core: 25$(li)Illyrium Core: 40$(li)Charged Illyrium Core: 80$(br2)A mixed run goes as far as its weakest cable. A $(item)Plasma Repeater$() in the run starts a fresh one.", "Cables and reach"),
        craft("plasma_repeater", None, "Each repeater costs something: nine tenths of the plasma passes it, and a projector behind it receives one power at full level, or two or more each one level lower per repeater. The projector screen shows what arrives before you switch."),
        craft("tetrium_core_cable", "magnatite_core_cable"),
        craft("illyrium_core_cable", "charged_illyrium_core_cable"),
        text("What a projector can receive: Fortify, Healing Aura, Alarm, Ward, Sanctuary, Overclock and Anchor, at the highest level any totem on the interface has.$(br2)Auras are cubes: a power with radius 5 covers 11 by 11 by 11 blocks around the totem or projector, so you can measure and build to the edge.", "Powers and auras"),
    ]),
    ("totem", "chunks", "Chunk Loading", "minecraft:filled_map", 7, [
        text("A placed totem can keep the land around it running while you are away: machines work and crops grow as if you were standing there.$(br2)Two base upgrades on the totem's tree do it. $(l)Anchor$() sets how many chunks stay loaded. $(l)Survey$() sets how far from the totem they can be."),
        text("$(l)Anchor$(), from Tier II:$(br)$(li)Level 1: 1 chunk$(li)Level 2: 3 chunks$(li)Level 3: 6 chunks$(br2)$(l)Survey$(), from Tier III, once Anchor is level 2:$(br)$(li)Without it: pick within 3 by 3 chunks$(li)Level 1: 5 by 5$(li)Level 2: 7 by 7$(br2)The chunk the totem stands in is always loaded and counts as one.", "Anchor and Survey"),
        text("Once Anchor has a level, your totem's panel gains a $(l)CHUNKS$() tab. It shows a map of the ground around the totem, one square for each chunk, north at the top.$(br2)Click a square to load that chunk. Click it again to let it go. Turquoise squares are loaded from here, violet ones from a projector, dark ones are out of range.", "Choosing chunks"),
        text("Receive $(l)Anchor$() on a $(l:totem/projectors)Projector$() and it gets a chunk map of its own, centred on the projector.$(br2)A projector adds no chunks. It lets you spend whatever is left of the totem's chunks further away: click any chunks within range of the projector. The projector's own chunk is always one of them, so it keeps running and keeps using RF. Its chunks stay loaded while it has a signal and RF.", "Through a projector"),
        text("Only your main totem, the one you have held longest, gets the full allowance. Every other totem you place loads the chunk it stands in and no more, and cannot take Anchor past level 1.$(br2)Lose your main totem and the next one you hold takes its place, with whatever upgrades it has.", "More than one totem"),
        text("When a totem is stolen, every chunk chosen for it is let go, at the totem and at its projectors. The thief keeps the upgrades and chooses again.$(br2)If you pick your own totem up and place it somewhere else, it remembers the layout.$(br2)The server may be set to load chunks only while you are online.", "Stolen and moved totems"),
    ]),
    ("totem", "batteries", "Batteries, Cells and the Emitter", "bsp_core:plasma_battery_2", 8, [
        text("Wave Plasma can be bottled. A $(item)Battery Charger$() on a Plasma Cable fills a $(item)Plasma Battery$() or $(item)Power Cell$() standing in it, and stamps powers from the interface into it, chosen on the charger's screen.$(br2)Every battery and cell is built around a $(item)Charged Resonance Crystal$(): a Resonance Crystal magnetised in the Magnetic Centrifuge with a Magnatite Nugget in the upgrade slot."),
        craft("battery_charger", "plasma_battery_1", "Tier I holds 40,000 mB of plasma and nothing else. Tiers II, III and IV hold 200,000, 1,000,000 and 5,000,000 mB and two, three and four Base powers, and each is built around the tier below."),
        craft("plasma_battery_2", "plasma_battery_3"),
        craft("plasma_battery_4", None, "Stand a charged battery on a Plasma Extractor instead of a totem: it feeds the extractor at 100 mB/t with its stamped powers until it runs dry. A spare for when your totem is stolen but you still want a projector running."),
        craft("power_cell_1", "wave_emitter", "A $(item)Power Cell$() holds Carried powers: 8,000 mB and one power for Tier I, 24,000 and two for II, 60,000 and three for III. The $(item)Wave Emitter$() runs on one."),
        text("Hold a Power Cell in one hand and the emitter in the other and right-click: the cell clicks in. Put the emitter in your offhand and right-click to switch it on: you get the cell's powers as if you carried the totem, while it draws 20 mB/t. Sneak and right-click to take the cell out.$(br2)So the totem can stay safe at home while you go out.", "The Wave Emitter"),
    ]),
    ("totem", "access", "Letting Friends In", "minecraft:name_tag", 9, [
        text("Your placed totem's panel has an $(l)ACCESS$() tab. Type an online player's name and Add: they get four switches.$(br2)$(li)$(l)Upgrades$(): may buy upgrades and change chunks and projector settings.$(li)$(l)Alarm$(): does not set it off.$(li)$(l)Ward$(): is not weakened by it.$(li)$(l)Machines$(): may open the totem's chargers and see through its Cloaking."),
        text("Up to eight people. The list stays with the totem when you pick it up, and is wiped when the totem is stolen. Friends still cannot pick the totem up, and they do not get its Carried powers.", "Rules"),
    ]),
    ("totem", "effects", "The Newer Powers", "minecraft:ender_eye", 10, [
        text("Five powers added with the plasma rework. Auras are now cubes: a radius of 5 covers 11 by 11 by 11 blocks, so the edge can be measured and built to. Hide the cube drawings for yourself with the AURAS switch in the totem panel's header or the keybind."),
        text("$(l)Bouncy$() (Carried, Dig path after Featherfall, Tier III): less fall damage, and landing from three blocks or more throws you back up with part of your landing speed, so a bigger fall means a bigger bounce. Sneak to land flat.$(br2)$(l)X-ray$() (Carried, after Night Sight, Tier V): press its key and ores, containers and spawners within range show through the blocks for a while, fading with depth; then it recharges.", "Carried"),
        text("$(l)Cloaking$() (Base, Walls path after Deadlock, Tier IV): outsiders see the land as it was when the cloak came on, not what is inside the cube. Uses 50 mB/t of the totem's plasma output.$(br2)$(l)Recall$() (Base, Home path after Overclock, Tier V): when your totem is being stolen, a card above the hotbar offers to bring you back to within its distance of the totem. Twenty seconds to decide, ten minutes' rest after.", "Base"),
        text("$(l)Recall Block$() (Raid, after Shroud, Tier IV): with it on the totem in your offhand while you steal, the owner's Recall offer comes later, on top of Shroud.", "Raid"),
    ]),
    ("totem", "compass", "The Totem Compass", "bsp_core:totem_compass", 3, [
        spot("bsp_core:totem_compass", "Points to your own nearest totem. Load it with Shatter Coins and start tracking, and for a while it points to the nearest rival totem instead.$(br2)After tracking it needs to cool down. Upgrades shorten the cooldown."),
        craft("totem_compass"),
    ]),
    ("materials", "tetrium", "Tetrium", "bsp_core:tetrium_ingot", 0, [
        text("$(item)Tetrium Ore$() is found underground in stone and deepslate. Mine it with an iron pickaxe or better. The ore block itself is what the machines take, so do not smelt it."),
        craft("tetrium_crucible", None, "The $(item)Tetrium Crucible$() burns furnace fuel and turns each ore into Tetrium Nuggets and Tetrium Slag."),
        craft("tetrium_ingot_from_nuggets", "combination_forge", "Nine nuggets make an ingot by hand. The $(item)Combination Forge$() does the same job for you and also presses plates."),
        {"type": "patchouli:smelting", "recipe": "bsp_core:slag_brick_from_smelting", "text": "Keep your slag. Smelted into $(item)Slag Bricks$() it goes into almost every machine part."},
    ]),
    ("materials", "crushing", "Dust and Crushing", "bsp_core:tetrium_dust", 1, [
        text("Several recipes need metal dust. A crusher from another mod (Mekanism, Create, Thermal) turns an ingot into dust every time.$(br2)With no crusher, put any pickaxe and one ingot in a crafting grid. The pickaxe loses a little durability, and you get either dust or a few nuggets back, by chance."),
    ]),
    ("materials", "illyrium", "Illyrium", "bsp_core:illyrium_ingot", 2, [
        text("$(item)Illyrium Ore$() is rarer and needs a diamond pickaxe. Refining it takes four steps:$(br2)$(li)$(l:machines/illyrium_crucible)Illyrium Crucible$(): ore and slag become a Dirty Illyrium Ingot.$(li)Crush it into Dirty Illyrium Dust.$(li)$(l:machines/illyrium_refinery)Illyrium Refinery$(): dust, water and a filter give Pure Illyrium Dust.$(li)Back in the Crucible: Pure Illyrium Dust and Tetrium Dust alloy into Illyrium Nuggets."),
        craft("illyrium_ingot_from_nuggets", "illyrium_forge_upgrade", "Nine nuggets make an ingot by hand. To forge Illyrium in the Combination Forge, fit the $(item)Illyrium Forge Upgrade$()."),
        craft("iron_filter", "diamond_filter", "Filters wear out. Better filters refine far more dust before they are used up."),
    ]),
    ("materials", "magnatite", "Magnatite", "bsp_core:magnatite_ingot", 3, [
        text("$(item)Magnatite Ore$() lies deep underground, mostly in deepslate, and needs a diamond pickaxe. It is a little easier to find than diamond. As with the other ores, keep the ore block: the machine takes it as it is.$(br2)The $(l:machines/magnetic_centrifuge)Magnetic Centrifuge$() spins each ore into Magnatite Nuggets and one $(item)Carbon Dust$()."),
        craft("magnatite_ingot_from_nuggets", None, "Nine nuggets make a $(item)Magnatite Ingot$() by hand or in the Combination Forge."),
        craft("copper_tetrium_coil", None, "Fit the $(item)Copper Tetrium Coil$() to a centrifuge and it can magnetise ingots into $(item)Charged Magnatite Ingots$(). Each try may fail; a failed try keeps the ingot."),
    ]),
    ("machines", "magnetic_centrifuge", "Magnetic Centrifuge", "bsp_core:magnetic_centrifuge", 4, [
        text("A multiblock one block high and three by three. It separates Magnatite Ore and, with a Copper Tetrium Coil, charges Magnatite Ingots. It always needs RF, fed into the Power Port (yellow socket); the Item Hatch (blue socket) takes items in and out." + GUIDE_TIP),
        build("Magnetic Centrifuge", CENTRIFUGE, "bsp_core:magnetic_centrifuge", "Controller front centre, Rotor in the middle, Item Hatch left, Power Port right, 5 Casing."),
        text("Put another Rotor on top of the first and ring it with eight Casing to add a layer, up to six layers.$(br2)$(li)More layers: more nuggets from each ore, up to 7 to 9 with six.$(li)More layers: a better chance to charge an ingot, from 1 in 6 up to 1 in 2 with five.$(li)Six layers: each charging try is much quicker.$(br2)Every layer draws power, and charging draws far more than separating.", "Stacking"),
        craft("centrifuge_casing", "centrifuge_rotor"),
        craft("centrifuge_power_port", "magnetic_centrifuge"),
    ]),
    ("machines", "components", "Machine Parts", "bsp_core:machine_chassis", 0, [
        text("Every machine block is built from the same few parts. Make these first:$(br2)$(li)$(item)Slag Brick$(): smelt Tetrium Slag.$(li)$(item)Tetrium Plate$(): put a Tetrium Ingot in the Combination Forge.$(li)$(item)Machine Chassis$(), $(item)Tetrium Coil$(), $(item)Drive Motor$() and the $(item)Basic Control Circuit$(): see the next pages."),
        craft("machine_chassis", "tetrium_coil"),
        craft("drive_motor", "circuit_substrate"),
        craft("basic_control_circuit", None, "Each controller needs this circuit upgraded for its job: Crucible, Refinery or Mint."),
    ]),
    ("machines", "illyrium_crucible", "Illyrium Crucible", "bsp_core:illyrium_crucible", 1, [
        text("A 3 x 3 x 3 multiblock that runs on lava. It smelts Illyrium Ore with slag into Dirty Illyrium Ingots, and alloys Pure Illyrium Dust with Tetrium Dust into Illyrium Nuggets.$(br2)The lower Lava Pylons (orange sockets) take lava by pipe. The upper ones (yellow sockets) take RF once an RF Upgrade is fitted." + GUIDE_TIP),
        build("Illyrium Crucible", CRUCIBLE, "bsp_core:illyrium_crucible", "Controller at the bottom front centre. 12 Casing, 8 Lava Pylons, 2 Item Hatches, 1 Core."),
        craft("thermal_lining", "crucible_control_circuit"),
        craft("illyrium_casing", "illyrium_core"),
        craft("lava_pylon", "item_hatch"),
        craft("illyrium_crucible"),
    ]),
    ("machines", "illyrium_refinery", "Illyrium Refinery", "bsp_core:illyrium_refinery", 2, [
        text("Washes Dirty Illyrium Dust into Pure Illyrium Dust using water and a filter.$(br2)The Refinery Pump takes water (blue socket) and RF (yellow socket). The Item Hatch takes dust in and gives pure dust out." + GUIDE_TIP),
        build("Illyrium Refinery", REFINERY, "bsp_core:illyrium_refinery", "Controller at the bottom front centre. 8 Casing, 1 Core, 2 Tank Glass, 1 Item Hatch, 1 Pump."),
        craft("refinery_control_circuit", "illyrium_glass"),
        craft("refinery_pump", "illyrium_refinery"),
    ]),
    ("machines", "rf_upgrades", "RF Upgrades", "bsp_core:rf_upgrade_mk1", 3, [
        text("Fit an RF Upgrade to a machine and it can take RF. It then runs faster and, on the Crucible, uses less lava. Mk I, II and III give 5%, 10% and 30%.$(br2)The Combination Forge runs on RF alone with an upgrade, at twice its coal speed."),
        craft("rf_upgrade_mk1", "rf_upgrade_mk2"),
        craft("rf_upgrade_mk3"),
    ]),
    ("coins", "shatter_coins", "Blanks and Coins", "bsp_core:copper_shatter_coin", 0, [
        text("There are five coins: Copper, Gold, Diamond, Netherite and Illyrium. Each is worth twice the one before.$(br2)A coin starts as a $(item)Shatter Blank$(), is built up into a tier's coin blank, and is then pressed in the $(l:coins/factory)Shatter Coin Factory$()."),
        craft("shatter_blank", "copper_coin_blank", "Each tier's blank is crafted around the blank of the tier below it."),
        craft("gold_coin_blank", "diamond_coin_blank"),
    ]),
    ("coins", "factory", "The Shatter Coin Factory", "bsp_core:shatter_coin_factory", 1, [
        text("An end-game machine built in slices. One slice is 1 wide, 2 high and 3 long. Put blanks in the Blank Hatch and RF in the Power Port, both at the back.$(br2)Pressing takes real time, from hours to days by tier, and carries on while you are offline. Coins collect in the tray and must be taken by hand." + GUIDE_TIP),
        build("Factory slice", FACTORY, "bsp_core:shatter_coin_factory", "Bottom from the front: Controller, Frame, Blank Hatch. Top: Frame, Press, Power Port."),
        text("Build slices side by side, facing the same way, and they join into one machine with one screen. You may own up to 10 slices.$(br2)Each slice presses its own blanks. RF is shared, and cabling more Power Ports charges the machine faster.", "Joining slices"),
        craft("mint_control_circuit", "conveyor_belt"),
        craft("press_die", "factory_frame"),
        craft("factory_press", "factory_blank_hatch"),
        craft("factory_power_port", "shatter_coin_factory"),
    ]),
    ("coins", "vault", "The Coin Vault", "bsp_core:coin_vault", 3, [
        spot("bsp_core:coin_vault", "A safe that holds only Shatter Coins, 27 stacks of 12 per block. Nothing can pipe coins in or out.$(br2)Vault blocks of yours that touch join into one vault, up to 3 x 3 x 3. You may own 27 vault blocks."),
        craft("coin_vault", None, "The block keeps its lock and alarm when you break it and place it again."),
        text("On the $(l)Access$() tab, add the players who may open the vault. They must be online when you add them. They can move coins but cannot change the settings.$(br2)On the $(l)Security$() tab, buy up to three lock levels and an Alarm with coins. The Alarm warns everyone with access the moment someone starts picking the lock.", "Access and Security"),
        text("Anyone without access can pick the lock: 2 minutes, plus 1 minute for each lock level, staying within 4 blocks. If they finish, they take a quarter of the coins, chosen at random. Illyrium coins are safe while the vault holds 3 or fewer.$(br2)Breaking a vault that is not yours gives no coins: they are kept for the owner, who collects them by opening any vault of theirs.", "Lockpicking"),
        text("Coins in your vaults earn interest: XP levels and Tetrium Ingots for every 50 coin value stored, counted across all your vaults. With a Netherite or Illyrium coin stored you also earn up to 2 Illyrium Ingots every 3 days.$(br2)Interest builds up while you are offline, but stops at 3 days' worth. Press $(l)Redeem$() on the Storage tab to collect it and start it building again.", "Interest"),
    ]),
    ("coins", "motivators", "Motivators", "bsp_core:factory_motivator", 2, [
        spot("bsp_core:factory_motivator", "Place up to three on top of a complete factory slice. One, two or three cut that slice's press time by 15%, 30% or 50%.$(br2)They pulse faster the more a slice has, and link up with their neighbours."),
        craft("resonance_crystal", "illyrium_processor"),
        craft("factory_motivator"),
    ]),
]


def plain(body):
    """Patchouli's formatting codes as Markdown, so the text reads normally in an editor."""
    import re
    body = re.sub(r"\$\(l:[^)]*\)(.*?)\$\(/?l?\)", r"\1", body)      # links to other entries: keep the words
    body = re.sub(r"\$\(item\)(.*?)\$\(\)", r"*\1*", body)            # item names
    body = re.sub(r"\$\(l\)(.*?)\$\(\)", r"**\1**", body)             # bold
    body = body.replace("$(br2)", "\n\n").replace("$(br)", "\n").replace("$(li)", "\n- ")
    return re.sub(r"\$\([^)]*\)", "", body).strip()


def write_review_copy():
    """All of the book's wording in one Markdown file, for reading and rewording outside the game."""
    out = ["# BSP Field Guide: text for review", "",
           "Every page of the in-game guide book, in the order it appears. Reword anything you like and send the file back:",
           "the changes are then copied into `tools/gen_guide_book.py`, which builds the book. Keep each page short: a book page",
           "holds roughly 100 words, or about 60 next to a recipe or picture.", "",
           "*Italics* are item names (shown in colour in the book) and **bold** is bold. Lines marked `[recipe]`, `[picture]` or",
           "`[3D structure]` show what else is on that page and are not text you need to edit.", ""]
    for cid, cname, cdesc, _ in CATEGORIES:
        out += ["---", "", f"# {cname}", "", cdesc, ""]
        for cat, eid, ename, _, sort, pages in sorted((e for e in ENTRIES if e[0] == cid), key=lambda e: e[4]):
            out += [f"## {ename}", ""]
            for n, page in enumerate(pages, 1):
                kind = page["type"].split(":")[1]
                extra = {"crafting": "[recipe] " + ", ".join(v.split(":")[1] for k, v in page.items() if k in ("recipe", "recipe2")),
                         "smelting": "[recipe] " + page.get("recipe", "").split(":")[-1], "multiblock": "[3D structure] " + page.get("name", ""),
                         "image": "[picture] " + page.get("title", ""), "spotlight": "[item shown] " + page.get("item", "").split(":")[-1]}.get(kind)
                title = page.get("title")
                out.append(f"**Page {n}" + (f": {title}" if title and kind != "image" else "") + "**" + (f"  `{extra}`" if extra else ""))
                out.append("")
                if page.get("text"):
                    out += [plain(page["text"]), ""]
    (ROOT / "docs/GUIDE_BOOK_TEXT.md").write_text("\n".join(out) + "\n")


def main():
    for folder in (BOOK, CONTENT):
        if folder.exists():
            shutil.rmtree(folder)
    BOOK.mkdir(parents=True)
    (BOOK / "book.json").write_text(json.dumps({
        "name": "BSP Field Guide", "landing_text": "Build. Secure. Protect. Everything BSP-Core adds, from your first Shatter Totem to a ten-slice coin factory.",
        "version": 1, "model": "patchouli:book_cyan", "book_texture": "patchouli:textures/gui/book_cyan.png", "subtitle": "BSP-Core",
        "creative_tab": "bsp_core:bsp", "show_progress": False, "use_resource_pack": True, "i18n": False}, indent=2))
    for i, (cid, name, desc, icon) in enumerate(CATEGORIES):
        path = CONTENT / "categories" / f"{cid}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"name": name, "description": desc, "icon": icon, "sortnum": i}, indent=2))
    for cat, eid, name, icon, sort, pages in ENTRIES:
        path = CONTENT / "entries" / cat / f"{eid}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"name": name, "category": f"bsp_core:{cat}", "icon": icon, "sortnum": sort, "pages": pages}, indent=2))
    # every crafting page must point at a real recipe file
    recipes = {p.stem for p in (ROOT / "src/main/resources/data/bsp_core/recipes").glob("*.json")}
    missing = sorted({v.split(":")[1] for _, _, _, _, _, pages in ENTRIES for p in pages for k, v in p.items() if k in ("recipe", "recipe2")} - recipes)
    assert not missing, missing
    write_review_copy()
    print("guide book written:", len(CATEGORIES), "categories,", len(ENTRIES), "entries")


if __name__ == "__main__":
    main()
