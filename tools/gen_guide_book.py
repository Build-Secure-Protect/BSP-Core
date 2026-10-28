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
          "K": "bsp_core:centrifuge_casing", "T": "bsp_core:centrifuge_rotor", "W": "bsp_core:centrifuge_power_port",
          "N": "bsp_core:metal_intake", "E": "bsp_core:forge_hearth", "M": "bsp_core:melt_tank", "D": "bsp_core:pour_head", "Q": "bsp_core:forge_cap"}
CRUCIBLE = [["CCC", "CCC", "CXC"], ["P P", "HOH", "P P"], ["PCP", "C C", "PCP"]]
REFINERY = [["CCC", "COC", "CXC"], [" C ", "HGU", "   "], ["   ", " G ", "   "]]
FACTORY = [["B", "F", "X"], ["R", "S", "F"]]
CENTRIFUGE = [["KKK", "HTW", "KXK"]]
# '.' is space that must stay empty (the Totem Forge's hollow); ' ' is anything
TOTEM_FORGE = [["NE", "XE"], ["..", ".."], ["MQ", "MD"]]
# the Trade Post is one block whose lower right part carries the whole 2 x 2 desk model
TRADE_POST = [["X"]]


def multiblock(layers, controller):
    """Our pattern to Patchouli's: layers top to bottom, strings along x, characters along z, controller ('0') facing north."""
    rows, cols = len(layers[0]), len(layers[0][0])
    out = []
    for layer in reversed(layers):
        out.append(["".join({"X": "0", " ": "_", ".": " "}.get(layer[rows - 1 - j][cols - 1 - i], layer[rows - 1 - j][cols - 1 - i]) for j in range(rows)) for i in range(cols)])
    used = {ch for layer in layers for row in layer for ch in row} - {"X", " ", "."}
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


def picture(name, file, body):
    """A picture page: one of the renders of the mod's own models, made by tools/make_book_images.py (textures/gui/book/<file>.png)."""
    assert (ROOT / f"src/main/resources/assets/bsp_core/textures/gui/book/{file}.png").exists(), f"no book picture {file}: run make_book_images.py"
    return {"type": "patchouli:image", "title": name, "images": [f"bsp_core:textures/gui/book/{file}.png"], "border": True, "text": body}


HOOKUP = picture("The hook-up", "plasma_network",
                 "Totem on extractor, interfaces beside it, a run through a valve and a repeater to a base and its projector, and a second run into the back of a charger.")
GROUP = picture("Interfaces joined", "plasma_interface_group",
                "Touching interfaces become one body: the faces between them vanish and the frame runs round the outside. Up to twelve, any shape. A cable on any outer face is fed by the whole group, and each face sends up to 1,000 mB/t: a 6,000 mB/t totem needs six runs. The red one touches an extractor another group holds.")
BATTERY_FEED = picture("A battery as source", "plasma_batteries",
                       "The four tiers, and a charged battery standing on an extractor: it feeds at 100 mB/t with the powers stamped into it, until it runs dry. The projector gets those powers as if a totem stood there.")
TANK_SCENE = picture("A 5 x 4 x 5 tank, formed", "plasma_tank",
                     "Casing on the twelve edges, glass on the faces, a port on the top and one on a side. Once formed, the casing turns to glass with a lit outline along the edges and the uprights glow as high as the plasma stands. Any box from 3 x 3 x 3 up to 12 x 12 x 12 works the same way.")


def build(name, layers, controller, body):
    """A picture of the finished machine (made by tools/make_book_images.py from the renders or docs/book_screenshots), otherwise the turnable block layout."""
    machine = controller.split(":")[1]
    if (ROOT / f"src/main/resources/assets/bsp_core/textures/gui/book/{machine}.png").exists():
        return {"type": "patchouli:image", "title": name, "images": [f"bsp_core:textures/gui/book/{machine}.png"], "border": True, "text": body}
    return {"type": "patchouli:multiblock", "name": name, "multiblock": multiblock(layers, controller), "text": body}


CATEGORIES = [
    ("totem", "The Shatter Totem", "Your totem is the heart of BSP: build around it, protect it, and take other players' totems if you can.", "bsp_core:shatter_totem"),
    ("materials", "The Three Metals", "Tetrium, Illyrium and Magnatite, from first ore to refined ingot.", "bsp_core:illyrium_ingot"),
    ("machines", "Machines", "The parts every machine is made of, and how to build the multiblocks, the Totem Forge among them.", "bsp_core:illyrium_crucible"),
    ("coins", "Shatter Coins", "Coins pay for the strongest totem upgrades. You press them yourself.", "bsp_core:gold_shatter_coin"),
]

GUIDE_TIP = "$(br2)Stuck on a build? Empty your hand and $(l)sneak + right-click$() the controller for the step-by-step Assembly Guide."

ENTRIES = [
    ("coins", "kits", "Kits", "minecraft:chest", 10, [
        text("A kit is a bundle of items the server hands out. Open the kit browser with the KITS button above your inventory, a key you bind in Controls, or $(l)/kits$().$(br2)Every kit you may see is a card: its picture, its name, and what stands between you and it."),
        text("A green card is ready: press REDEEM on it and the items land in your inventory.$(br2)A grey card is one you took lately. It says how long until it is ready again, or that it was once only.$(br2)A card with a rank on it needs that rank; the steps show where you stand and how far there is to go. A card naming a server is taken on that server. A price in pieces comes from your bank account. A card counting slots needs that much room in your inventory first.", "Reading a card"),
        text("A kit given to you as a reward, or at your first login, waits in violet when it did not fit or came while you were away. Free the slots it names and press TAKE IT.$(br2)Nothing is lost: a kit that cannot fit stays waiting until it can.", "Waiting for you"),
        text("Click a card's picture to see what is in the kit, with the counts, and every condition ticked or crossed. BACK returns to the wall.$(br2)On a network your kits and what you have taken are the same on every server.", "Looking inside"),
    ]),
    ("coins", "land_office", "The Land Office", "bsp_core:land_office", 9, [
        text("The $(item)Land Office$() sells land. Right-click it for a map of the plots around you: $(l)amber$() plots are for sale at a set price, $(l)dashed$() areas are open ground where you mark out your own plot, and $(l)green$() plots are yours.$(br2)Click a plot to see its size and price, then press $(l)Buy$() and confirm. The price comes out of your bank account."),
        text("For open ground, mark the corners with the land wand ($(l)/claim tool$()); the office opens by itself with the price of what you marked, and shows how near it lies to other plots.$(br2)$(l)Enlarge$() grows one of your plots to an outline that covers it, paying for the new ground. $(l)Give up$() returns part of what you paid and frees the land.", "Open ground"),
    ]),
    ("coins", "safety_deposit_box", "Safety Deposit Box", "bsp_core:safety_deposit_box", 7, [
        text("A $(item)Safety Deposit Box$() is a cabinet admins place beside the bank. Right-click it to rent $(l)your own box$(): the same box opens from any of these cabinets on the network, and what is inside is safe at all times.$(br2)Rent is paid by the day, in pieces or coins, for as long ahead as you like. A box starts with $(l)6 slots$(); buy $(l)3 more$() at a time, up to 18."),
        text("When the rent runs out nothing is lost: your slots and their contents are kept, but the drawer is locked until you pay rent again, plus a $(l)holding fee$() for every day the box sat unpaid. Leave it long and it costs more to get back.$(br2)Shatter Coins and totems cannot be stored in a box. The bank keeps coins; totems belong in the world.", "Rent and fees"),
    ]),
    ("coins", "shatter_bank", "The Shatter Bank", "bsp_core:bank_atm", 6, [
        text("The $(item)Shatter Bank$() is a two-block machine admins place at spawn and in safe zones. Right-click it to open your account.$(br2)Your account holds $(l)Shatter Pieces$(), the everyday money. Pieces are never an item: they live in the account, and the same balance follows you to every server of the network.$(br2)Every player is given 500 pieces on their first join."),
        text("The bank also keeps $(item)Shatter Coins$() for you, by tier. Coins in the bank are safe from raids but earn $(l)no interest$(): a Coin Vault pays interest and can be robbed. Choose.$(br2)Deposit coins to keep them, or $(l)convert$() them: a Copper coin becomes 1,000 pieces, each higher tier double the one below. Converting is one way. Pieces never turn back into coins.", "Coins and pieces"),
        text("From the account screen you can pay any player on the network, online or not; large payments ask you to confirm, and you can refuse incoming payments.$(br2)The transaction log lists what you bought and sold, who you paid and who paid you, with filters and a search. It is kept for 90 days.$(br2)Commands are a backup: $(l)/bank$(), $(l)/bank pay$(), $(l)/bank log$().", "Paying and the log"),
        text("Once a day, the first time you log in on any server of the network, $(l)50 pieces$() go into your account, with a line in chat to say so.$(br2)If you are online when midnight comes, the new day's reward arrives then. Days run from midnight to midnight.", "Daily reward"),
    ]),
    ("totem", "your_totem", "Your Shatter Totem", "bsp_core:shatter_totem", 0, [
        text("Every player is given one $(item)Shatter Totem$() the first time they join. It cannot be destroyed, and the only way to make another is to pour one in a $(l:machines/totem_forge)Totem Forge$().$(br2)Carry it, or place it in your base. A placed totem is where your upgrades and auras work from, so most players build their base around it."),
        text("Only one totem is ever yours by right, but you can hold others: any totem you steal becomes yours.$(br2)If you die to another player while carrying your totem, it is placed where you fell. A totem dropped on the ground places itself after a short while.", "Keeping it"),
        text("A totem never goes offline with you. When you log out, every totem you carry is placed where you stood, so it can still be stolen; if it is still there, untouched, when you come back, it returns to you.", "Logging out"),
    ]),
    ("totem", "stealing", "Stealing and Defending", "minecraft:iron_sword", 1, [
        text("Right-click another player's placed totem and choose to steal it. A timer starts, and the owner is warned.$(br2)Stay close to the totem until the timer runs out and it is yours, with every upgrade on it."),
        text("The steal is cancelled if the thief is killed, or stays too far from the totem for too long.$(br2)So defend in person, and make the room around your totem hard to stand in. The $(l:totem/upgrades)Fortify$() upgrade helps your walls survive explosions.", "Stopping a thief"),
    ]),
    ("totem", "upgrades", "The Upgrade Tree", "minecraft:experience_bottle", 2, [
        text("Right-click with the totem in your hand, or right-click your placed totem, to open its upgrade tree. Upgrades belong to the totem, so they move with it if it is stolen.$(br2)Your totem has five tiers, I to V. Each tier has its own Shatter Coin: Copper, Gold, Diamond, Netherite, Illyrium."),
        text("Raising the totem a tier costs coins of the tier you are leaving, plus XP levels. A new tier opens new upgrades and two more levels on the ones you have.$(br2)Upgrades sit on paths. The next one opens when the one before it reaches level 2. Every level costs the coin of the tier it is bought at, plus XP.", "Tiers and paths"),
        text("Work while the totem is in your $(l)offhand$(). Paid in experience levels.$(br2)$(li)Damage, Resistance, Mining Speed$(li)Swiftness: move faster$(li)Vitality: extra hearts$(li)Featherfall: less fall damage$(li)Night Sight: see in the dark$(li)Bouncy: bounce back up from a fall$(li)X-ray: see ores through the walls for a while$(li)Extra Homes, Swift Travel: more homes and quicker teleports with the pack's teleport mod$(br2)Bouncy and X-ray are described under $(l:totem/effects)Powers in Detail$().", "Carried"),
        text("Work only while the totem is $(l)placed$(). Paid in $(l:coins/shatter_coins)Shatter Coins$().$(br2)$(li)Fortify: blocks nearby resist explosions and intruders mine slower$(li)Alarm: outlines intruders and warns you$(li)Sentinel: tells you who is inside, from anywhere$(li)Herald: calls you on Discord$(li)Ward: weakens intruders$(li)Deadlock: your totem takes longer to steal$(li)Cloaking: outsiders see untouched land", "Base: walls"),
        text("$(li)Healing Aura: heals you nearby$(li)Sanctuary: no hostile spawns$(li)Overclock: every injector fed by this totem's plasma speeds its machine up more$(li)Recall: teleport to the totem while it is being stolen$(li)Output: more Wave Plasma$(li)Anchor and Survey: keep chunks loaded, see $(l:totem/chunks)Chunk Loading$()$(li)Harvest: mined ores grow back", "Base: aura and plasma"),
        text("Help you take other totems. They count when the totem that has them is in your offhand as you start a steal. Paid in Shatter Coins.$(br2)$(li)Lockpick: your steals take less time$(li)Shroud: the owner is warned late$(li)Recall Block: delays the owner's Recall$(li)Thief's Step: silent steps, hidden from weak Alarms$(li)Siege: mine at full speed inside Fortify for a while$(br2)No upgrade can make a totem impossible to steal: blocks can always be mined, and a steal always has a time limit.", "Raid"),
    ]),
    ("totem", "scoring", "Scores and Seasons", "minecraft:gold_block", 4, [
        text("Every totem you own scores points by its tier:$(br2)$(li)Tier I: 1 point$(li)Tier II: 2 points$(li)Tier III: 4 points$(li)Tier IV: 7 points$(li)Tier V: 10 points$(br2)Your score is the total for all your totems, so stealing totems and upgrading them both move you up."),
        text("The server runs in seasons. When a season ends, the top three players receive that season's prizes, every totem is removed, and every player gets one fresh Tier I totem.$(br2)The screens the admins put up show what first, second and third place will win. If you are offline when the season ends, your prizes and your new totem are waiting when you next log in.", "Seasons and Prizes"),
        text("A placed totem counts for its owner. A carried totem counts for whoever is carrying it.$(br2)Ties go to the player with more totems, then to the higher tier. The leaderboard screens at spawn show the standings, and on a network they cover every server. A screen can also show other boards, such as which servers players call home, and turn through its pages.", "How it is counted"),
    ]),
    ("totem", "decoys", "Decoy Totems", "bsp_core:decoy_totem", 5, [
        spot("bsp_core:decoy_totem", "A fake totem. While it has power, rival $(l:totem/compass)Totem Compasses$() within its range point at it instead of a real totem, and everyone but you sees an ordinary Shatter Totem. You see the idol with a ghost of the totem around it.$(br2)You may have five placed."),
        craft("magnet_core", "decoy_totem", "The $(item)Magnet Core$() is the hard part: six Charged Magnatite Ingots around an Illyrium Processor."),
        craft("decoy_power_base", None, "A decoy must stand on a $(item)Decoy Power Base$(), fed with RF from its sides or from below. It draws 100 RF every tick. Without power the disguise drops and everyone sees the bare idol."),
        text("An enemy who tries to steal a decoy finds out it is fake. Its traps go off and it takes a hit. When it has taken more hits than it has casings it breaks, and does nothing until you repair it with Magnatite Ingots.$(br2)Only you can mine a working decoy. Anyone can mine a broken or unpowered one.", "Taking the bait"),
        text("Right-click your decoy to fit parts. A socket opens when the one before it is filled.$(br2)$(li)$(l)Range$(): Range Coils Mk I, II, III widen the compass range from 24 blocks to 40, 64 and 96.$(li)$(l)Traps$(): a charge, then a Trap Amplifier, then a second charge.$(li)$(l)Casing$(): each Reinforced Casing survives one more attempt.", "Sockets"),
        text("Each charge is used up when it goes off.$(br2)$(li)Blast: an explosion that hurts the thief and breaks nothing.$(li)Hex: Slowness, Weakness and Glowing.$(li)Poison.$(li)Fatigue: Mining Fatigue.$(li)Warp: throws the thief up to 30 blocks away.", "Trap charges"),
        craft("range_coil_mk1", "range_coil_mk2"),
        craft("range_coil_mk3", "trap_amplifier"),
        craft("blast_charge", "warp_charge", "The other charges swap the middle item: Fermented Spider Eye, Spider Eye or Prismarine Shard."),
        craft("reinforced_casing"),
    ]),
    ("totem", "projectors", "Plasma and Projectors", "bsp_core:totem_projector", 6, [
        text("A placed totem gives off $(l)Wave Plasma$(): 100 mB every tick, up to 6,000 with the ten levels of the $(l)Output$() upgrade. The totem panel shows the figure. Drawn out and piped to a $(item)Projector$(), it recreates the totem's base powers as a second aura somewhere else: over a mine, a farm, or a second wall.$(br2)The projector works for whoever owns the totem. If your totem is stolen, so is the aura."),
        HOOKUP,
        text("The chain, in order:$(br)$(li)$(item)Plasma Extractor$() directly under the totem. More stack below; the flow is split between them.$(li)$(item)Plasma Interface$() touching an open face of an extractor. Cables plug into its outer faces.$(li)$(item)Plasma Cable$() from the interface onward.$(li)$(item)Projector Base$() at the end of the cable, holding what arrives.$(li)$(item)Projector$() standing on the base.", "How it joins up"),
        craft("plasma_extractor", "plasma_interface", "Stack extractors under the totem to split the flow between several interfaces. An interface touching an extractor another group already serves is refused: its lit parts turn red and it does nothing."),
        GROUP,
        text("Right-click any interface for its $(l)flow view$(): the group and every cable, valve, repeater, extractor, base and charger it reaches in 3D, labelled with the mB/t passing. Drag to turn, right-drag to pan, scroll to zoom.$(br2)Jade shows the same figures block by block: an extractor's draw, a cable's flow, a valve's limit, a base's tank.", "Seeing the flow"),
        craft("projector_base", "totem_projector", "Cables plug into the base's four sides; the projector on top draws from it. A projector needs 100 mB/t arriving. Right-click it: POWERS lists what the interface offers with a RECEIVE switch each; CHUNKS is the chunk picker; STATUS shows the pressure."),
        text("The base holds 5,000 mB and the projector burns 20 mB/t of it, so a base that was full keeps its projector going for about four minutes after a cable is cut. A base with nothing feeding it and no projector drains by itself.$(br2)A projector runs on plasma alone: no RF. With less than 100 mB/t arriving and a dry base it shows no aura.", "The base's tank"),
        craft("channel_expander", None, "A projector receives two powers at once, three with a $(item)Channel Expander$() fitted by right-clicking the projector with it; sneak and right-click empty-handed takes it out."),
        text("What a projector can receive: Fortify, Healing Aura, Alarm, Ward, Sanctuary, Cloaking, Recall and Anchor, at the highest level any totem on the interface has.$(br2)Auras are cubes: a power with radius 5 covers 11 by 11 by 11 blocks around the totem or projector, so you can measure and build to the edge.", "Powers and auras"),
    ]),
    ("totem", "flow", "Runs and Valves", "bsp_core:plasma_valve", 7, [
        text("Plasma moves in $(l)runs$(): a line of cables from an interface to whatever is at its end. Every cable in a run carries the same figure, the run's $(l)pressure$(), in mB/t. Nothing is lost along a cable, however long, up to its reach.$(br2)Each kind of cable has a longest run:$(br)$(li)Tetrium: 15 blocks, 250 mB/t$(li)Magnatite: 25 blocks, 500 mB/t$(li)Illyrium: 40 blocks, 1,000 mB/t$(li)Charged Illyrium: 80 blocks, 1,000 mB/t$(br2)A mixed run goes as far, and carries as much, as its weakest cable."),
        craft("tetrium_core_cable", "magnatite_core_cable"),
        craft("illyrium_core_cable", "charged_illyrium_core_cable"),
        text("An interface shares what its extractors give $(l)equally$() between the runs leaving it. A run that can take less than its share, because a valve caps it or its end is full, leaves the rest to the others.$(br2)So two bases on one totem get 50 mB/t each; cap one at 20 and the other gets 80. The cables leaving the interface always add up to the supply.", "Sharing"),
        craft("plasma_repeater", None, "A $(item)Plasma Repeater$() ends one run and starts a fresh one, so the reach count begins again. Plasma goes in its dark back and out its lit front; placed pointing the way you look, the wrench turns it. It costs a tenth of the pressure, and a projector behind it receives one power at full level, or two or more each one level lower per repeater."),
        craft("plasma_valve", None, "A $(item)Plasma Valve$() sets the most that may pass it, from 0 to 1,000 mB/t. Right-click it and drag the dial, type a number, or use the buttons. A lever on it or any redstone signal shuts it, unless you switch redstone control off on its screen. Powers pass unchanged; it counts as one cable of reach and does not start a fresh run."),
        text("The valve's hand wheel turns as far as the limit is set. Its windows show plasma arriving on one side and leaving on the other at the limited rate; the lamp by the gauge is blue while open and red while shut.$(br2)Each extractor also keeps a reserve of 4,000 mB. Take the totem away and the runs keep drinking from it for a short while, then stop.", "What you see"),
        craft("wrench", None, "The $(item)Wrench$() turns blocks in place: repeaters, valves, chargers, batteries and placed totems. Sneak and right-click turns the other way, or turns a repeater round."),
        craft("blue_illyrium_core_cable", None, "Magnatite, Illyrium and Charged Illyrium cables take any of the sixteen dyes: eight cables round a dye, and a coloured cable can be dyed again. A coloured cable joins only cables of its own colour, a plain one only plain ones, so two runs can cross without mixing. Machines take any colour."),
        text("Point the wrench at an end of a cable and right-click to set what that end does:$(br)$(li)$(l)Normal$(): plasma flows either way.$(li)$(l)Output$(): plasma may only leave the cable here.$(li)$(l)Input$(): plasma may only enter here.$(li)$(l)Off$(): not joined.$(br2)Where another colour meets the end, the first click $(l)Links$() them. Sneak and right-click puts an end back to Normal.", "Cable ends"),
        text("While you hold the wrench a line above the crosshair names the end you point at and its setting. Output ends wear a copper collar with an arrow pointing out, Input ends an arrow pointing in, Off ends a dark plate, and Links a copper and white collar.$(br2)Sneak and right-click the $(l)core$() of a cable to pick it up with its settings kept on the item.", "What you see"),
    ]),
    ("totem", "injector", "The Plasma Injector", "bsp_core:plasma_injector", 11, [
        spot("bsp_core:plasma_injector", "Wave Plasma can drive machines. A $(item)Plasma Injector$() stands against a machine, with its port on the far end for the cable (the only face a cable connects to), and the machine runs faster the more plasma arrives: twice as fast at 300 mB/t, three times at 500. Only what is arriving counts, so a cut cable or a stolen totem slows the machine back down within a second or two."),
        text("It works on every BSP-Core machine: against any block of the Illyrium Crucible, Refinery or Magnetic Centrifuge, or against a Tetrium Crucible or Combination Forge. On a $(item)Shatter Coin Factory$() it stands in one of the three Motivator cells and speeds that slice only, on top of what the Motivators do.$(br2)It also works on most machines from other mods, which it ticks extra; a few that keep their own clock will ignore it."),
        craft("plasma_injector"),
        picture("Injector on a crucible", "plasma_injector", "The cable comes in at the far end, the nozzle end touches the machine. The barrel fills as high as the rate, and a slug of plasma runs down each feed tube while it works."),
        text("$(l)Overclock$() on a totem travels with the plasma. Every injector fed from that totem's extractors, however far the cables run, is stronger: the speed-up above normal is multiplied by 1.25, 1.5, 1.75 or 2 by level, so a run that gives a machine twice its speed gives three times at Overclock IV. Plasma from a tank keeps the Overclock of the totem that filled it, and a projector cannot send it.", "Overclock"),
    ]),
    ("totem", "accelerator", "Plasma Across Worlds", "bsp_core:accelerator_intake", 12, [
        spot("bsp_core:accelerator_intake", "Wave Plasma can cross into another world on the same server, the Nether, the End or a mining world, so a $(item)Projector$() there keeps chunks loaded with $(l)Anchor$() from a totem that stays safe at home. The $(item)Plasma Accelerator$() sends it; a $(item)Plasma Decelerator$() receives it. Plasma goes one way only, and every power it carried arrives with it."),
        text("The accelerator is a ring, three blocks high and wide, one deep: an $(item)Accelerator Intake$() at the bottom middle, placed facing you, and seven $(item)Accelerator Frames$() round it with the middle left empty. It forms within a second, the blocks vanish and the ring takes their place. Run a plasma cable into the intake (any face) and RF into any frame. It takes up to 3,000 mB/t over every run feeding it.", "The ring"),
        craft("accelerator_intake", "accelerator_frame"),
        picture("A formed accelerator", "plasma_accelerator", "The ring stands across the intake's facing, the cable in at the intake's socket, the black hole in its cage in the middle. With RF the coil heads light in turn round the ring; while a link delivers, pulses race round the copper track and arcs jump to the cage."),
        text("The decelerator is two blocks: a $(item)Decelerator Core$(), placed facing you, with a $(item)Decelerator Port$() directly behind it. RF goes into the core; cables leave the port's free faces, carrying the plasma on to bases, chargers, injectors, tanks or another accelerator.", "The far end"),
        craft("decelerator_core", "decelerator_port"),
        picture("Port into a projector", "plasma_decelerator", "The core's barrel holds the small black hole; the port behind it has a socket on every free face. Here a cable leaves its back into a Projector Base, so the projector keeps its chunks with the far totem's Anchor."),
        text("A blank $(item)Link Card$() right-clicked on an intake takes that accelerator's world and position. Right-click a Decelerator Core with it and the two are linked, in whatever world the core stands. Sneak and right-click the core empty-handed to take the card out.$(br2)An accelerator holds one link. Each $(item)Link Expander$() right-clicked onto the intake adds one, two at most, and the flow is shared equally between the links that can take it.", "Linking"),
        craft("link_card", "link_expander"),
        text("Both ends burn RF: 1,000 RF/t at the intake while any link delivers, 100 RF/t at a core while it receives, from pools of 200,000. Without RF on either side nothing crosses and the plasma stays with the source.$(br2)Once the plasma arrives it is ordinary plasma: an Overworld totem's $(l)Overclock$() speeds a Nether injector, its $(l)Anchor$() holds a Nether projector's chunks. Only the users of the totem feeding an intake may write cards there.", "RF and powers"),
        text("A far side whose chunk is not loaded sleeps, and its share goes to the other links. Once a minute the accelerator loads it for a moment and delivers, so a projector there relights its own chunk loading by itself; after that the projector keeps the chunk and the link runs on its own.$(br2)Right-click the intake empty-handed for the $(l)Worlds board$(): a column per world, every link as a pip with its state, UNLINK for the one you pick and WAKE to load every sleeping far side at once.", "Sleeping and waking"),
    ]),
    ("totem", "tank", "The Plasma Tank", "bsp_core:tank_port", 8, [
        text("Wave Plasma can be kept in bulk. A $(item)Plasma Tank$() is a hollow box you build from three blocks: $(item)Tank Casing$() on every edge, $(item)Tank Glass$() or casing on the faces, and $(item)Tank Ports$() wherever a cable should meet it. Three to twelve blocks a side, nothing inside. It forms by itself when the last block goes in.$(br2)Every block of the shell holds 2,500,000 mB."),
        TANK_SCENE,
        craft("tetrium_glass", "tank_glass", "Tetrium Glass is ordinary glass round a Tetrium Nugget; Tank Glass is Tetrium Glass round a Tetrium Plate. A wall of Tank Glass draws as one sheet."),
        craft("tank_casing", "tank_port", "A port takes plasma from a run that comes from an interface, and gives plasma to a run that only leads to bases, chargers or other tanks. Point the wrench at it to set Input or Output only. Each port gives up to 1,000 mB/t, like an interface face."),
        text("Right-click any block of the tank: the screen shows the tank in 3D with its plasma at the level it holds, each port with its setting and what passes, and the first few cables out of each port. Jade reads the level on any block.$(br2)When the tank forms, a sweep of light runs over it and the casing turns to glass with a lit outline along the edges; the four uprights glow as high as the plasma stands, so the level can be read from outside. Plasma coming in through a port above the surface pours down to it; the level rises evenly through the whole tank.", "Seeing it"),
        text("Plasma fed in from a totem keeps that totem's powers: Overclock, Output and the rest. The tank's Output ports pass them on to injectors with every mB they give, with or without the totem, until that plasma is used up.$(br2)Plain plasma fed in mixes with it, and the powers fade as the mix runs down. The tank screen lists the powers the plasma carries.", "Powers in the plasma"),
        text("Break one block by accident and the tank goes dormant but keeps its plasma: put the block back and it is whole again. Rebuild it smaller and it keeps what fits; bigger and it keeps what it had. Take the last block away and the plasma is gone.", "Breaking and rebuilding"),
    ]),
    ("totem", "chunks", "Chunk Loading", "minecraft:filled_map", 9, [
        text("A placed totem can keep the land around it running while you are away: machines work and crops grow as if you were standing there.$(br2)Two base upgrades on the totem's tree do it. $(l)Anchor$() sets how many chunks stay loaded. $(l)Survey$() sets how far from the totem they can be."),
        text("$(l)Anchor$(), from Tier II:$(br)$(li)Level 1: 1 chunk$(li)Level 2: 3 chunks$(li)Level 3: 6 chunks$(br2)$(l)Survey$(), from Tier III, once Anchor is level 2:$(br)$(li)Without it: pick within 3 by 3 chunks$(li)Level 1: 5 by 5$(li)Level 2: 7 by 7$(br2)The chunk the totem stands in is always loaded and counts as one.", "Anchor and Survey"),
        text("Once Anchor has a level, your totem's panel gains a $(l)CHUNKS$() tab. It shows a map of the ground around the totem, one square for each chunk, north at the top.$(br2)Click a square to load that chunk. Click it again to let it go. Turquoise squares are loaded from here, violet ones from a projector, dark ones are out of range.", "Choosing chunks"),
        text("Receive $(l)Anchor$() on a $(l:totem/projectors)Projector$() and it gets a chunk map of its own, centred on the projector.$(br2)A projector adds no chunks. It lets you spend whatever is left of the totem's chunks further away: click any chunks within range of the projector. The projector's own chunk is always one of them, so it keeps running and keeps drawing plasma. Its chunks stay loaded while plasma reaches it.", "Through a projector"),
        text("Only your main totem, the one you have held longest, gets the full allowance. Every other totem you place loads the chunk it stands in and no more, and cannot take Anchor past level 1.$(br2)Lose your main totem and the next one you hold takes its place, with whatever upgrades it has.", "More than one totem"),
        text("When a totem is stolen, every chunk chosen for it is let go, at the totem and at its projectors. The thief keeps the upgrades and chooses again.$(br2)If you pick your own totem up and place it somewhere else, it remembers the layout.$(br2)The server may be set to load chunks only while you are online.", "Stolen and moved totems"),
    ]),
    ("totem", "batteries", "Batteries and Cells", "bsp_core:plasma_battery_2", 10, [
        text("Wave Plasma can be bottled. A $(item)Battery Charger$() with a Plasma Cable into its back fills a $(item)Plasma Battery$() or $(item)Power Cell$() standing in it, and $(l)stamps$() powers from the interface into it, chosen on the charger's screen.$(br2)Every battery and cell is built around a $(item)Charged Resonance Crystal$()."),
        spot("bsp_core:charged_resonance_crystal", "A Resonance Crystal magnetised in the Magnetic Centrifuge, with a Magnatite Nugget in the upgrade slot. Ten seconds a crystal, faster with the centrifuge at full speed.", "The Charged Crystal"),
        craft("battery_charger", None, "Place the charger facing you: the cable goes into its $(l)back$(), and only there. The wrench turns it. Pipes and hoppers may put an empty battery or cell in from any other side and take it out once it is full. Its screen shows the tank, what is arriving, the item filling, and one row per power with a STAMP button. Only players with Machines access on the totem may open it."),
        text("Stamping copies a power at the level the totem has. While the item sits in a fed charger, a stamped power follows the totem's level if it changes. Take the item out and the levels are fixed.$(br2)$(l)Batteries$() take Base powers: the ones a projector can receive. $(l)Cells$() take Carried powers: the ones that work from your offhand.", "What gets stamped"),
        craft("plasma_battery_1", "plasma_battery_2", "Tier I holds 40,000 mB and no powers. Tiers II, III and IV hold 200,000, 1,000,000 and 5,000,000 mB and two, three and four Base powers. Each tier is built around the one below."),
        craft("plasma_battery_3", "plasma_battery_4"),
        BATTERY_FEED,
        text("A battery on an extractor feeds it at 100 mB/t with its stamped powers until it runs dry, so projectors and chargers keep working while your totem is away or stolen. The battery lies along an axis; the wrench turns it. A battery keeps its charge and powers when mined.", "Batteries as a source"),
        craft("power_cell_1", "power_cell_2", "A $(item)Power Cell$() holds Carried powers: 8,000 mB and one power for Tier I, 24,000 and two for II, 60,000 and three for III."),
        craft("power_cell_3", "wave_emitter", "The $(item)Wave Emitter$() runs on one cell. Its hover text and the cell's own show the charge and the powers stamped in."),
        text("Right-click the emitter at the air (no block within four blocks) to open it: put a Power Cell in the slot, press ON, and read the cell's powers and how long it will last. Put the emitter in your $(l)offhand$(): you get the cell's powers as if you carried the totem, while it draws 20 mB/t. It switches off when the cell runs dry.$(br2)Right-click at a nearby block to switch it without opening the screen; sneak there to take the cell out.", "The Wave Emitter"),
        text("So the totem can stay safe at home while you go out: a Tier III cell at 20 mB/t lasts 50 minutes.$(br2)Carried powers from the emitter: Damage, Resistance, Mining Speed, Swiftness, Vitality, Featherfall, Night Sight, Bouncy and X-ray, at the stamped level.", "Out and about"),
    ]),
    ("totem", "access", "Letting Friends In", "minecraft:name_tag", 11, [
        text("Your placed totem's panel has an $(l)ACCESS$() tab. Type an online player's name and Add: they get four switches.$(br2)$(li)$(l)Upgrades$(): may buy upgrades and change chunks and projector settings.$(li)$(l)Alarm$(): does not set it off.$(li)$(l)Ward$(): is not weakened by it.$(li)$(l)Machines$(): may open the totem's chargers and see through its Cloaking."),
        text("Up to eight people. The list stays with the totem when you pick it up, and is wiped when the totem is stolen. Friends still cannot pick the totem up, and they do not get its Carried powers. Teammates have all four switches without being listed: see Teams.", "Rules"),
    ]),
    ("totem", "teams", "Teams", "minecraft:white_banner", 11, [
        text("Friends who build together can form a $(l)team$(). Every member counts as a friend with all four switches on every teammate's totem: they open its panel, buy its upgrades, pass its Ward and Alarm, use its machines and see through its Cloaking. Teammates cannot steal from each other, and the Totem Compass leaves teammates' totems alone."),
        text("Open the Team screen from the $(l)TEAM$() button above your inventory, the TEAM tab on your placed totem, the TEAM tab on your carried totem's screen, or a key of your choice in Controls. Name the team, pick its colour and press CREATE. Invite players who are online from the INVITE view; a seat is held for a minute while they decide.", "Making one"),
        text("An invited player sees a card at the top right with your face and the team's name, hears a chime, and finds the same offer in chat with Accept and Decline. Two keys answer the card (Y and H unless rebound); the chat buttons work from inside any screen.", "The invite"),
        text("A team holds up to five. The leader renames, recolours, removes members, hands the lead over and disbands; everyone may invite. Leaving, being removed or a disband takes the shared access away at once, and a totem stolen by an outsider is out of the team's reach by itself. The leader hands the lead over before leaving.", "Members and the lead"),
        text("Press $(l)TEAM CHAT$() on the Team screen and what you type goes to the team until you press it again; a pill above the chat shows while it is on. $(l)/t message$() sends one line without switching. The team's board keeps the last fifty lines for members who were away, counts the unread ones at login, and the leader can pin one note above it.", "Talking"),
        text("Any member may buy upgrades on a teammate's placed totem and raise its tier; the one buying pays, with coins and experience as for their own. The upgrade belongs to the totem and stays with it whoever leaves the team.", "Upgrading together"),
    ]),
    ("totem", "effects", "Powers in Detail", "minecraft:ender_eye", 12, [
        text("The higher-tier powers, one by one. Auras are cubes: a radius of 5 covers 11 by 11 by 11 blocks, so the edge can be measured and built to. Hide the cube drawings for yourself with the AURAS switch in the totem panel's header or the keybind."),
        text("$(l)Bouncy$() (Carried, Dig path after Featherfall, Tier III): less fall damage, and landing from three blocks or more throws you back up with part of your landing speed, so a bigger fall means a bigger bounce. Sneak to land flat.$(br2)$(l)X-ray$() (Carried, after Night Sight, Tier V): press its key and the solid blocks around you turn to faint glass, fainter the deeper they are, for a while; ores show through them bright, containers and spawners are marked. The level sets how far it reaches and how long it lasts; then it recharges.", "Carried"),
        text("$(l)Cloaking$() (Base, Walls path after Deadlock, Tier IV): outsiders see the land as the world generated it, with its trees, plants, snow and ice but no caves, in place of everything inside the cube, and the players and mobs inside are hidden from them too. Uses 50 mB/t of the totem's plasma output.$(br2)$(l)Recall$() (Base, Home path after Overclock, Tier V): when your totem is being stolen, a card above the hotbar offers to bring you back to within its distance of the totem. Twenty seconds to decide, ten minutes' rest after.", "Base"),
        text("$(l)Recall Block$() (Raid, after Shroud, Tier IV): with it on the totem in your offhand while you steal, the owner's Recall offer comes later, on top of Shroud.$(br2)$(l)Thief's Step$() (Raid, beside Recall Block, Tier IV): your footsteps make no sound, and an Alarm up to the level shown does not see you.$(br2)$(l)Siege$() (Raid, after Thief's Step, Tier V): press its key and for a while Fortify does not slow your mining; then it recharges.", "Raid"),
        text("$(l)Sentinel$() (Base, Walls path beside Ward after Alarm, Tier III, priced like a Raid power): while anyone is inside your Alarm cube, a strip at the top of your screen tells you, from anywhere on the server: how many (level 1), who (2), how far from the totem (3), and at level 4 where they are right now, with a marker through the walls when they are near you. A steal in progress shows the thief and the seconds left. Friends with the Alarm switch see it too.$(br2)$(l)Harvest$() (Base, Anchor path after Survey, Tier IV): BSP ores you mine within its reach grow back where they were, one at a time, as long as the totem has plasma to spare for it.", "Sentinel and Harvest"),
        text("$(l)Herald$() (Base, Walls path after Alarm, Tier II, one level): when your totem is being stolen, the BSP Discord bot sends you a direct message with the server, the place, the thief and the time left, and another when the steal ends. It comes when the game warns you, so a thief's Shroud delays it too. Where the server has no Discord link, Herald stays locked.", "Herald"),
        text("Type $(l)/bsp discord link$(), or press LINK on Herald's card, for a six-letter code that lasts ten minutes. In the BSP Discord, type $(l)/link$() and the code. Only you see the bot's answer.$(br2)You never type a Discord name or password in the game. $(l)/bsp discord status$() shows the link; $(l)/bsp discord unlink$() ends it.", "Linking Discord"),
    ]),
    ("totem", "compass", "The Totem Compass", "bsp_core:totem_compass", 3, [
        spot("bsp_core:totem_compass", "Points to your own nearest totem. Load it with Shatter Coins and start tracking, and for a while it points to the nearest rival totem instead.$(br2)After tracking it needs to cool down. Upgrades shorten the cooldown."),
        craft("totem_compass"),
    ]),
    ("materials", "tetrium", "Tetrium", "bsp_core:tetrium_ingot", 0, [
        text("$(item)Tetrium Ore$() is found underground in stone and deepslate. Mine it with an iron pickaxe or better. Without Silk Touch the ore drops $(item)Raw Tetrium$(); the crucibles take raw chunks and ore blocks alike, so do not smelt either."),
        craft("tetrium_crucible", None, "The $(item)Tetrium Crucible$() burns furnace fuel and turns each ore into Tetrium Nuggets and Tetrium Slag."),
        craft("tetrium_ingot_from_nuggets", "combination_forge", "Nine nuggets make an ingot by hand. The $(item)Combination Forge$() does the same job for you and also presses plates."),
        {"type": "patchouli:smelting", "recipe": "bsp_core:slag_brick_from_smelting", "text": "Keep your slag. Smelted into $(item)Slag Bricks$() it goes into almost every machine part."},
    ]),
    ("materials", "crushing", "Dust and Crushing", "bsp_core:tetrium_dust", 1, [
        text("Several recipes need metal dust. A crusher from another mod turns an ingot into dust every time: Mekanism's Crusher, Create's crushing wheels and millstone, Thermal's Pulverizer, Ender IO's SAG Mill, Railcraft's Crusher, Integrated Dynamics' Squeezer and Mechanical Squeezer, and Electrodynamics' Mineral Grinder.$(br2)With no crusher, put any pickaxe and one ingot in a crafting grid. The pickaxe loses a little durability, and you get either dust or a few nuggets back, by chance."),
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
    ("machines", "totem_forge", "The Totem Forge", "bsp_core:totem_forge", 5, [
        text("A 2 x 2 x 3 multiblock that pours a new $(item)Shatter Totem$(). It melts a long bill of metals into its tanks, fills a mould of eight pieces standing in its hollow middle, and lets the totem set." + GUIDE_TIP),
        build("Totem Forge", TOTEM_FORGE, "bsp_core:totem_forge", "Bottom: the Totem Forge front left, the Metal Intake behind it, a Forge Hearth on the right of each. Middle: empty. Top: Melt Tanks left, Pour Head front right, Forge Cap behind it."),
        text("The tanks must hold the whole metal bill before the forge pours. Its screen shows a tube for each metal; by default 128 Tetrium, 32 Magnatite, 8 Charged Magnatite, 12 Illyrium, 32 Allthemodium, 16 Vibranium, 8 Unobtainium and 2 Netherite Ingots.$(br2)Ingots go into the Metal Intake or the screen's slot and melt one at a time, burning lava from the Forge Hearths. An RF Upgrade speeds it up. Molten Tetrium, Magnatite and Illyrium can be piped straight into the intake.", "The metal"),
        text("The $(l:machines/totem_mould)Totem Mould$() goes into the hollow one piece at a time, in order: the Base, two Feet, the Torso, two Arms, the Head, and the Totem Core last. Right-click the forge with each piece.$(br2)$(l)Sneak + right-click$() with an empty hand, or TAKE PIECE OUT on the screen, takes the last piece back out, until the metal is poured.", "The mould"),
        text("With the mould complete and the tanks full, press $(l)POUR$().$(br2)If you own no Shatter Totem, the pour is free and the totem sets in 5 days. If you own any, you pay 4 Illyrium Shatter Coins for each one first (or their worth in any other Shatter Coins, with change), and it sets in 15 days.$(br2)You can have one totem pouring or setting at a time, on any server.", "Pouring"),
        text("The totem sets on real time, even while you are away, and you are told when it is ready. Right-click the forge with an empty hand to take it out: a $(item)Forged Shatter Totem$(), tier I, yours.$(br2)Anyone else must steal it the same way: they stay by the forge for two and a half minutes while you are warned. Break the forge before the totem has set and the pour is lost.", "Setting and stealing"),
        craft("forge_hearth", "totem_forge"),
        craft("metal_intake", "melt_tank"),
        craft("pour_head", "forge_cap"),
    ]),
    ("machines", "totem_mould", "The Totem Mould", "bsp_core:torso_mould", 6, [
        text("Seven mould pieces and a core make one mould. Each piece looks like the totem with its own part picked out: the Base, the Foot and Arm (two of each go in, left and right), the Torso and the Head.$(br2)A piece lasts three pours, then cracks. It takes no enchantment and no repair. The $(item)Totem Core$() becomes the forged totem's orb, so every pour needs a new one."),
        craft("mould_shell", "base_mould", "Mould Shells come two at a time; every piece is built from them."),
        craft("foot_mould", "torso_mould"),
        craft("arm_mould", "head_mould"),
        craft("totem_core"),
    ]),
    ("materials", "molten_metals", "Molten Metals", "bsp_core:molten_tetrium_bucket", 4, [
        text("Tetrium, Magnatite and Illyrium melt in a Tinkers' smeltery or a Thermal Magma Crucible, and pipe and store like any fluid. A nugget is 10 mB and an ingot 90 mB, and both cast back one for one.$(br2)Ore melts to exactly what the BSP machines make from it: Tetrium ore 20 mB, two nuggets; Magnatite ore 30 mB, three. The slag and the Carbon Dust come only from the BSP machines.$(br2)Illyrium ore, Dirty Illyrium and Charged Magnatite do not melt: the Crucible, the Refinery and the Centrifuge are their only way."),
        text("Spilled, molten metal burns whatever wades into it, as lava does, and meets water as lava does: a still pool sets to $(item)Obsidian$(), a flowing edge to $(item)Cobblestone$(), and the metal is gone.$(br2)A dispenser pours a molten bucket, and fills an empty one from a pool in front of it.", "Spills"),
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
    ("coins", "trade_post", "The Trade Post", "bsp_core:trade_post", 11, [
        text("The $(item)Trade Post$() is a steel trading desk two blocks wide and two high that admins place at spawn and in hubs. It is where players trade with each other, and where the $(l:coins/market)market$() is.$(br2)Its four keys open their pages: $(l)MARKET$(), $(l)SELL$(), $(l)COLLECT$() and $(l)TRADE$(). Click anywhere else on the desk for the market."),
        build("Trade Post", TRADE_POST, "bsp_core:trade_post", "The Exchange Desk. The scales on the counter tip while a trade runs, and the beacons on the hood turn while anyone uses the post."),
        text("At the right of the screen is everyone standing at a Trade Post on any server of the network, with their server's name. A grey mark means they are busy in a trade.$(br2)Pick a name and press $(l)TRADE$() to ask them. See $(l:coins/trading)Trading with Players$().", "Who is here"),
    ]),
    ("coins", "trading", "Trading with Players", "bsp_core:trade_post", 12, [
        text("A request lasts 30 seconds and comes up as a card on the other player's screen, with $(l)ACCEPT$() and $(l)DECLINE$(). You can have one request out at a time; $(l)TAKE BACK$() withdraws it.$(br2)Accepted, you both get the trade screen: your 12 offer places at the left, theirs at the right, a pieces box under each, and a chat between the two of you."),
        text("You see what the other player puts in as they do it. Any change to either offer turns both $(l)READY$() lights off, so nobody is shown one thing and given another.$(br2)When you are both ready, five lights count down 5 seconds and the offers lock. Press READY again to stop. At the end the items and pieces cross over in one go.", "Ready"),
        text("Before anything moves, the trade checks that both of you have room for what you are getting. If not, nothing moves and you are told who is short of space.$(br2)Closing the screen, walking more than 8 blocks from the post, logging out or dying ends the trade, and your offer comes back to you.$(br2)Shatter Totems and Totem Cores never go into an offer.", "Room and leaving"),
    ]),
    ("coins", "market", "The Market", "bsp_core:trade_post", 13, [
        text("The market is one list for the whole network. On $(l)SELL$(), put up to 9 stacks into a lot, type a price in pieces and press $(l)LIST LOT$(). A lot stays up for 7 days, and you can have 10 at once.$(br2)On $(l)MARKET$(), search by item or seller and sort by newest, cheapest or dearest. A card opens the lot: every stack, the price and what you pay."),
        text("Listing costs a $(l)5% tax$() on the price, rounded up, paid when you list and kept if the lot does not sell. The buyer pays the price plus the same 5%. Both taxes go into the network's $(l:coins/lottery)lottery$() pot.$(br2)A lot is bought whole. The seller is paid at once, with a message, or at their next login.", "The tax"),
        text("A lot you cancel, and one whose 7 days run out, waits in your $(l)Collect$() box. $(l)COLLECT ALL$() fills your inventory and leaves the rest in the box.$(br2)Shatter Totems and Totem Cores cannot be sold, not even inside a shulker box.$(br2)Score Screens can show the market as the $(l)Market Board$(): the newest lots with their prices and how long ago each was listed.", "Collecting"),
    ]),
    ("coins", "market_portable", "Market on the Go", "bsp_core:portable_banker", 14, [
        spot("bsp_core:portable_banker", "The $(item)Portable Banker$() has a $(l)MARKET$() key under the Shopping Network. The market is its own one-time unlock: $(l)UNLOCK MARKET$() takes 12 Netherite Shatter Coins from your inventory, and it stays with your account on every server."),
        text("MARKET opens the same market as a Trade Post: browse, buy, list lots and collect, wherever you are.$(br2)There is no list of traders on the Portable Banker. Trading with another player needs a Trade Post, and nobody can ask you to trade while you are only on the banker.$(br2)The unlock lasts the season, like the Shopping Network.", "Buying and selling"),
    ]),
    ("coins", "lottery", "The Lottery", "bsp_core:gold_shatter_coin", 15, [
        text("The network has one lottery pot. It fills from the $(l:coins/market)market$()'s taxes and from half of what players pay for shop items.$(br2)Everyone who has played on the network in the past week is in the lottery, with one equal chance each. There are no tickets to buy or earn. Operators and staff ranks are left out."),
        text("An admin draws the lottery when they choose. Chat on every server counts down from 5 and then names the winner and what they won.$(br2)The winner takes the whole pot, straight into their bank. A winner who is offline is told when they next log in.$(br2)After a draw the pot starts again, and everyone who keeps playing is in the next one.", "The draw"),
        text("The $(l)LOTTERY$() tab on a $(l:coins/trade_post)Trade Post$() turns a drum of the names of everyone in, beside the pot, how many are in and your own chance. The last five winners are along the bottom.$(br2)The $(item)Portable Banker$() shows the pot and your chance on its account page, and Score Screens can show the $(l)Lottery$() board.", "Watching the pot"),
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
                extra = extra or (("[3D scene] " + page.get("name", "")) if kind == "multiblock" else None)
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
    import re
    history = re.compile(r"\b(added|newer|no longer|rework|as before|used to|any ?more|replaces|nowadays)\b|(?<!right )\bnow\b", re.I)
    told = [m.group(0) for _, _, name, _, _, pages in ENTRIES for p in pages for m in [history.search(p.get("text", "") + " " + (p.get("title") or ""))] if m]
    assert not told, f"the book must read as if everything was always there; found: {told}"
    long = [t for _, _, name, _, _, pages in ENTRIES for t in [name] + [p.get("title") or p.get("name") for p in pages] if t and len(t) > 24]
    assert not long, f"titles too long for a page: {long}"
    write_review_copy()
    print("guide book written:", len(CATEGORIES), "categories,", len(ENTRIES), "entries")


if __name__ == "__main__":
    main()
