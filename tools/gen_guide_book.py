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
          "U": "bsp_core:refinery_pump", "F": "bsp_core:factory_frame", "B": "bsp_core:factory_blank_hatch", "R": "bsp_core:factory_power_port", "S": "bsp_core:factory_press"}
CRUCIBLE = [["CCC", "CCC", "CXC"], ["P P", "HOH", "P P"], ["PCP", "C C", "PCP"]]
REFINERY = [["CCC", "COC", "CXC"], [" C ", "HGU", "   "], ["   ", " G ", "   "]]
FACTORY = [["B", "F", "X"], ["R", "S", "F"]]


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
        text("Right-click with the totem in your hand, or right-click your placed totem, to open its upgrade tree. Upgrades belong to the totem, so they move with it if it is stolen.$(br2)The tree has three branches. Click any upgrade to see what it does and what the next level costs."),
        text("Work while the totem is in your $(l)offhand$(). Paid in experience levels.$(br2)$(li)Damage, Resistance, Mining Speed$(li)Swiftness: move faster$(li)Vitality: extra hearts$(li)Featherfall: less fall damage$(li)Night Sight: see in the dark", "Carried"),
        text("Work only while the totem is $(l)placed$(). Paid in $(l:coins/shatter_coins)Shatter Coins$().$(br2)$(li)Fortify: blocks nearby resist explosions and intruders mine slower$(li)Healing Aura: heals you nearby$(li)Ward: weakens intruders$(li)Alarm: outlines intruders and warns you$(li)Sanctuary: no hostile spawns$(li)Deadlock: your totem takes longer to steal$(li)Overclock: machines nearby work faster", "Base"),
        text("Help you take other totems. They count when the totem that has them is in your offhand as you start a steal. Paid in Shatter Coins.$(br2)$(li)Lockpick: your steals take less time$(li)Shroud: the owner is warned late$(br2)No upgrade can make a totem impossible to steal: blocks can always be mined, and a steal always has a time limit.", "Raid"),
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
    ("coins", "motivators", "Motivators", "bsp_core:factory_motivator", 2, [
        spot("bsp_core:factory_motivator", "Place up to three on top of a complete factory slice. One, two or three cut that slice's press time by 15%, 30% or 50%.$(br2)They pulse faster the more a slice has, and link up with their neighbours."),
        craft("resonance_crystal", "illyrium_processor"),
        craft("factory_motivator"),
    ]),
]


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
    print("guide book written:", len(CATEGORIES), "categories,", len(ENTRIES), "entries")


if __name__ == "__main__":
    main()
