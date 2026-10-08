#!/usr/bin/env python3
"""Builds the BSP-Core reference sheet: tools/preview/curseforge_sheet.html.

  python3 tools/gen_curseforge_sheet.py

A set of 800 x 600 "plates" made from the mod's own files: every recipe JSON, every item and block
texture, the names in en_us.json, the multiblock layer patterns, and the 3D renders saved in
docs/curseforge/images/render_*.jpg. Opened normally the page shows every plate; with
?plate=N in the address it shows only plate N filling the window, which is how each plate is
captured as an image for CurseForge. Rerun after recipes, items or renders change.
"""
import base64
import glob
import html
import json
import os
import tempfile
import zipfile

from gen_material_assets import ASSETS, ROOT, read_png, vanilla, write_png

DATA = ROOT / "src/main/resources/data/bsp_core"
IMAGES = ROOT / "docs/curseforge/images"
LANG = json.loads((ASSETS / "lang/en_us.json").read_text())
JAR = glob.glob(os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar"))[0]
VANILLA_BLOCK = {"anvil": "anvil", "bricks": "bricks", "cobweb": "cobweb", "furnace": "furnace_front", "glass": "glass", "glass_pane": "glass", "magma_block": "magma",
                 "piston": "piston_side", "redstone_block": "redstone_block", "smooth_stone": "smooth_stone", "tnt": "tnt_side", "crafting_table": "crafting_table_front"}
VANILLA_NAMES = {"lapis_lazuli": "Lapis Lazuli", "tnt": "TNT"}
ORE_HOST = {"tetrium_ore": ("stone", "tetrium"), "deepslate_tetrium_ore": ("deepslate", "tetrium"), "illyrium_ore": ("stone", "illyrium"),
            "deepslate_illyrium_ore": ("deepslate", "illyrium"), "end_stone_illyrium_ore": ("end_stone", "illyrium"),
            "magnatite_ore": ("stone", "magnatite"), "deepslate_magnatite_ore": ("deepslate", "magnatite")}
BLOCK_TEX = {"shatter_coin_factory": "shatter_coin_factory_front", "illyrium_crucible": "illyrium_crucible_front", "illyrium_refinery": "illyrium_refinery_front",
             "magnetic_centrifuge": "magnetic_centrifuge_front", "coin_vault": "coin_vault_front", "score_screen": "score_screen_front"}
# blocks drawn by a renderer, with no flat texture: shown as a lettered tile in this colour
TILE = {"shatter_totem": ("ST", "#E3B341"), "tetrium_crucible": ("TC", "#B58CFF"), "combination_forge": ("CF", "#B58CFF"), "factory_motivator": ("Mo", "#B58CFF"),
        "admin_rack": ("AR", "#B58CFF"), "anti_totem": ("AT", "#FF4B3C"), "decoy_totem": ("DT", "#7FB3FF"), "decoy_power_base": ("PB", "#C87A3C"),
        "totem_generator": ("TG", "#19D3B0"), "totem_projector": ("TP", "#19D3B0"), "tetrium_core_cable": ("Ca", "#6B4FA3"), "magnatite_core_cable": ("Ca", "#5C7FB8"),
        "illyrium_core_cable": ("Ca", "#19D3B0"), "charged_illyrium_core_cable": ("Ca", "#BFF6FF"), "totem_compass": None,
        "plasma_extractor": ("Ex", "#4CB2FA"), "plasma_interface": ("In", "#4CB2FA"), "plasma_repeater": ("Re", "#C87A3C"), "plasma_valve": ("Va", "#C87A3C"), "projector_base": ("PB", "#4CB2FA"),
        "battery_charger": ("BC", "#19D3B0"), "plasma_battery_1": ("B1", "#8A909C"), "plasma_battery_2": ("B2", "#6F5A7A"), "plasma_battery_3": ("B3", "#19D3B0"), "plasma_battery_4": ("B4", "#BFE6FF"),
        "wave_emitter": ("WE", "#4CB2FA")}
_cache = {}


def b64(data):
    return "data:image/png;base64," + base64.b64encode(data).decode()


def rows_png(w, h, rows):
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
        path = f.name
    from pathlib import Path
    write_png(Path(path), w, h, lambda x, y: rows[y][x])
    data = open(path, "rb").read()
    os.unlink(path)
    return data


def vanilla16(path):
    """A vanilla texture as PNG bytes, cut to its first 16 x 16 frame."""
    w, h, rows = vanilla(path)
    return rows_png(16, 16, [r[:16] for r in rows[:16]])


def icon_src(item):
    """Picture for an item id ('bsp_core:x', 'minecraft:x' or a bare BSP id), or None if it has no flat texture."""
    if item in _cache:
        return _cache[item]
    ns, _, name = item.rpartition(":")
    src = None
    try:
        if ns == "minecraft":
            src = b64(vanilla16(f"block/{VANILLA_BLOCK[name]}.png" if name in VANILLA_BLOCK else f"item/{name}.png"))
        elif name in ORE_HOST:
            host, metal = ORE_HOST[name]
            _, _, base = vanilla(f"block/{host}.png")
            _, _, over = read_png((ASSETS / f"textures/block/{metal}_ore_overlay.png").read_bytes())
            mixed = [[tuple(int(o[i] * o[3] / 255 + b[i] * (1 - o[3] / 255)) for i in range(3)) + (255,) for b, o in zip(br, orow)] for br, orow in zip(base, over)]
            src = b64(rows_png(16, 16, mixed))
        else:
            for path in (ASSETS / f"textures/item/{name}.png", ASSETS / f"textures/block/{BLOCK_TEX.get(name, name)}.png", ASSETS / f"textures/item/{name}_00.png"):
                if path.exists() and name not in TILE:
                    src = b64(path.read_bytes())
                    break
    except (KeyError, FileNotFoundError):
        src = None
    _cache[item] = src
    return src


def name_of(item):
    ns, _, name = item.rpartition(":")
    if ns == "minecraft":
        return VANILLA_NAMES.get(name, name.replace("_", " ").title())
    return LANG.get(f"item.bsp_core.{name}") or LANG.get(f"block.bsp_core.{name}") or name.replace("_", " ").title()


def icon(item, size=32, count=None):
    """An item as a picture (or lettered tile), with an optional count badge."""
    if not item:
        return f'<span class="ic" style="width:{size}px;height:{size}px"></span>'
    item = item if ":" in item else "bsp_core:" + item
    src, name = icon_src(item), html.escape(name_of(item))
    bare = item.split(":")[1]
    if src:
        inner = f'<img src="{src}" alt="{name}" width="{size}" height="{size}">'
    else:
        letters, colour = TILE.get(bare) or (bare[:2].title(), "#8A909C")
        inner = f'<span class="tile" style="width:{size}px;height:{size}px;border-color:{colour};color:{colour};font-size:{max(9, size // 3)}px">{letters}</span>'
    badge = f'<b>{count}</b>' if count and count > 1 else ""
    return f'<span class="ic" title="{name}" style="width:{size}px;height:{size}px">{inner}{badge}</span>'


def render(name, alt, ratio=None):
    """A saved render as an inline picture; a ratio such as '2/1' crops it shorter so two fit in one column."""
    path = IMAGES / f"render_{name}.jpg"
    style = f' style="aspect-ratio:{ratio}"' if ratio else ""
    if not path.exists():
        return f'<div class="shot missing"{style}>{html.escape(alt)}</div>'
    return f'<img class="shot" alt="{html.escape(alt)}"{style} src="data:image/jpeg;base64,{base64.b64encode(path.read_bytes()).decode()}">'


PLATES = []


def plate(slug, title, body, note=""):
    PLATES.append((slug, title, f'<header><h2>{html.escape(title)}</h2><span>BSP-Core</span></header><div class="body">{body}</div>'
                   + (f'<footer>{note}</footer>' if note else "")))


# ----------------------------------------------------------------------------- 1. machines
def layers(pattern, mapping, labels):
    """Layer diagrams: each layer a grid of block pictures, rows back to front, with a caption."""
    out = '<div class="layers">'
    for i, layer in enumerate(pattern):
        grid = "".join("<div>" + "".join(icon(mapping.get(ch), 30) for ch in row) + "</div>" for row in layer)
        out += f'<figure><div class="grid">{grid}</div><figcaption>{labels[i]}</figcaption></figure>'
    return out + '</div><p class="hint">Each grid is one layer seen from above. The bottom row of a grid is the front of the machine.</p>'


def legend(mapping, counts):
    return '<ul class="legend">' + "".join(f'<li>{icon(v, 22)} {counts[v]} x {html.escape(name_of("bsp_core:" + v))}</li>' for v in dict.fromkeys(mapping.values()) if v in counts) + "</ul>"


def count(pattern, mapping):
    c = {}
    for layer in pattern:
        for row in layer:
            for ch in row:
                if ch in mapping:
                    c[mapping[ch]] = c.get(mapping[ch], 0) + 1
    return c


def machine_plate(slug, title, pattern, mapping, labels, shot, what, extra=""):
    counts = count(pattern, mapping)
    left = f'<div class="col"><h3>Build it</h3>{layers(pattern, mapping, labels)}{legend(mapping, counts)}{extra}</div>'
    right = f'<div class="col"><h3>Finished</h3>{render(shot, title)}<p>{what}</p></div>'
    plate(slug, title, f'<div class="split">{left}{right}</div>', "Sneak + right-click the controller in game for the step-by-step Assembly Guide.")


def machines():
    plate("machines_single", "Single-block machines",
          '<div class="split"><div class="col"><h3>Tetrium Crucible</h3>' + render("tetrium_crucible", "Tetrium Crucible")
          + '<p>Smelts one Tetrium Ore into 2 Tetrium Nuggets and 1 Tetrium Slag in 20 seconds. Burns furnace fuel.</p></div>'
          '<div class="col"><h3>Combination Forge</h3>' + render("combination_forge", "Combination Forge")
          + '<p>Presses nine nuggets into an ingot (Tetrium, Magnatite, and Illyrium with the Illyrium Forge Upgrade) and Tetrium Ingots into Tetrium Plates. Runs on fuel, or on RF with an RF Upgrade.</p></div></div>')
    cas = {"C": "illyrium_casing", "P": "lava_pylon", "H": "item_hatch", "O": "illyrium_core", "G": "illyrium_glass", "U": "refinery_pump", "X": "illyrium_crucible"}
    machine_plate("machine_illyrium_crucible", "Illyrium Crucible (3 x 3 x 3)", [["CCC", "CCC", "CXC"], ["P P", "HOH", "P P"], ["PCP", "C C", "PCP"]], cas,
                  ["Bottom layer", "Middle layer", "Top layer"], "illyrium_crucible",
                  "Runs on lava. Smelts Illyrium Ore with slag into Dirty Illyrium Ingots, and alloys Pure Illyrium Dust with Tetrium Dust into Illyrium Nuggets. Lower pylons take lava, upper pylons take RF.")
    ref = dict(cas, X="illyrium_refinery")
    machine_plate("machine_illyrium_refinery", "Illyrium Refinery", [["CCC", "COC", "CXC"], [" C ", "HGU", "   "], ["   ", " G ", "   "]], ref,
                  ["Bottom layer", "Middle layer", "Top layer"], "illyrium_refinery",
                  "Washes Dirty Illyrium Dust into Pure Illyrium Dust with water and a filter. The pump takes water and RF; the Item Hatch takes dust in and pure dust out.")
    fac = {"B": "factory_blank_hatch", "F": "factory_frame", "X": "shatter_coin_factory", "R": "factory_power_port", "S": "factory_press"}
    machine_plate("machine_coin_factory", "Shatter Coin Factory (one slice: 1 x 2 x 3)", [["B", "F", "X"], ["R", "S", "F"]], fac, ["Bottom", "Top"], "coin_factory",
                  "Presses coin blanks into Shatter Coins in real time, from 12 hours for Copper to 7 days for Illyrium. Needs RF. Up to 10 slices side by side join into one machine.",
                  f'<p class="hint">{icon("factory_motivator", 22)} Up to three Motivators on top of a slice cut its press time by 15%, 30% and 50%.</p>')
    cen = {"K": "centrifuge_casing", "T": "centrifuge_rotor", "W": "centrifuge_power_port", "H": "item_hatch", "X": "magnetic_centrifuge"}
    machine_plate("machine_magnetic_centrifuge", "Magnetic Centrifuge (1 x 3 x 3, stackable)", [["KKK", "HTW", "KXK"], ["KKK", "KTK", "KKK"]], cen,
                  ["Bottom layer", "Each layer stacked on top (up to 5 more)"], "magnetic_centrifuge",
                  "Always needs RF. Separates Magnatite Ore into nuggets and Carbon Dust, and with a Copper Tetrium Coil charges Magnatite Ingots. More layers give more nuggets and a better charge chance.")


# ----------------------------------------------------------------------------- 4. layouts
def layouts():
    plate("layout_plasma_network", "Wave Plasma: the network",
          '<div class="split"><div class="col" style="flex:1.6">' + render("plasma_network", "A Wave Plasma network")
          + '<p>Totem on an extractor, interfaces beside it, cables out to a Projector Base with its Projector, and a second run into the back of a Battery Charger. A valve and a repeater sit in the long run.</p></div>'
          '<div class="col"><h3>How it flows</h3><p>A placed totem gives 100 mB/t, up to 600 with the Output upgrade. An interface shares it equally between the runs leaving it. Every cable in a run carries the run\'s figure to its end.</p>'
          '<table class="t"><tr><th>Cable</th><th>Longest run</th></tr>'
          + "".join(f'<tr><td>{icon(c, 18)} {html.escape(name_of("bsp_core:" + c))}</td><td>{n} blocks</td></tr>' for c, n in
                    (("tetrium_core_cable", 15), ("magnatite_core_cable", 25), ("illyrium_core_cable", 40), ("charged_illyrium_core_cable", 80))) + '</table>'
          '<p>A repeater starts a fresh run for a tenth of the pressure. A valve caps a run or shuts it by redstone. A projector needs 100 mB/t; its base holds 5,000 mB.</p></div></div>',
          "Right-click any interface for the flow view: the whole network in 3D with the mB/t on every cable. Jade reads the figures block by block.")
    plate("layout_plasma_blocks", "The plasma blocks",
          '<div class="split"><div class="col"><h3>Extractor and interfaces</h3>' + render("plasma_interface_group", "Six interfaces joined, one refused", "5/2")
          + '<p>Touching interfaces join into one body, up to twelve. A thirteenth, or one touching an extractor another group holds, turns red and does nothing.</p>'
          '<h3>Projector Base</h3>' + render("projector_base", "Projector Base and Projector", "5/2") + '<p>A tank the projector stands on. Cables plug into its sides; the level shows through its windows.</p></div>'
          '<div class="col"><h3>Plasma Valve</h3>' + render("plasma_valve", "Plasma Valve", "5/2") + '<p>Sets the most that may pass, from its screen. A lever on it or any redstone signal shuts it. The wheel turns with the limit.</p>'
          '<h3>Plasma Repeater</h3>' + render("plasma_repeater", "Plasma Repeater", "5/2") + '<p>One way: in through the dark back, out through the lit front. Starts a fresh run. The wrench turns it.</p></div></div>',
          "Powers pass a valve unchanged. Behind a repeater a projector receives one power at full level, or several each a level lower per repeater.")
    plate("layout_batteries", "Batteries, cells and the Wave Emitter",
          '<div class="split"><div class="col"><h3>Battery Charger</h3>' + render("battery_charger", "Battery Charger", "5/2") + '<p>Cable into its back. Fills the battery or cell standing in it and stamps powers from the interface into it, chosen on its screen.</p>'
          '<h3>Wave Emitter, Power Cells, Wrench</h3>' + render("wave_emitter", "Wave Emitter, Power Cells and the Wrench", "5/2") + '<p>The emitter runs on a cell in your offhand and gives you its Carried powers at 20 mB/t. Right-click at the air to open it.</p></div>'
          '<div class="col"><h3>Plasma Batteries</h3>' + render("plasma_batteries", "Plasma Batteries, and one feeding an extractor", "5/2") + '<p>Four tiers. Stood on an extractor instead of a totem, a battery feeds it with its stamped Base powers until it runs dry.</p>'
          '<table class="t"><tr><th>Tier</th><th>Holds</th><th>Powers</th></tr><tr><td>Battery I to IV</td><td>40k / 200k / 1M / 5M mB</td><td>0 / 2 / 3 / 4 Base</td></tr><tr><td>Cell I to III</td><td>8k / 24k / 60k mB</td><td>1 / 2 / 3 Carried</td></tr></table></div></div>',
          "Every battery and cell is built around a Charged Resonance Crystal: a Resonance Crystal magnetised in the centrifuge with a Magnatite Nugget in the upgrade slot.")
    plate("layout_decoy", "Decoy Totem",
          '<div class="split"><div class="col"><h3>What its owner sees</h3>' + render("decoy_owner_view", "Decoy Totem as its owner sees it")
          + '<p>The Hollow Idol on its Decoy Power Base, with a faint ghost of the totem around it.</p></div>'
          '<div class="col"><h3>What everyone else sees</h3>' + render("decoy_other_view", "Decoy Totem as other players see it")
          + '<p>An ordinary Shatter Totem. Rival Totem Compasses within range point at it. Try to steal it and its traps go off.</p></div></div>',
          "Needs 100 RF per tick. Five per player. Sockets: Range Coils, trap charges, a Trap Amplifier, Reinforced Casings.")
    plate("layout_base_blocks", "Coin Vault and Anti Totem Block",
          '<div class="split"><div class="col"><h3>Coin Vault, joined</h3>' + render("coin_vault_joined", "Joined Coin Vault")
          + '<p>Vault blocks of one owner join into one vault of up to 3 x 3 x 3. Coins inside earn interest. Others can pick the lock for a quarter of the coins.</p></div>'
          '<div class="col"><h3>Anti Totem Block (admins)</h3>' + render("anti_totem_block", "Anti Totem Block")
          + '<p>Keeps Shatter Totems and chosen BSP blocks out of a box of up to 256 blocks in each direction. For spawn areas, hubs and arenas.</p></div></div>')


# ----------------------------------------------------------------------------- 2 + 3. items and resources
def all_ids():
    ids = [k.split(".")[-1] for k in LANG if (k.startswith("item.bsp_core.") or k.startswith("block.bsp_core.")) and k.count(".") == 2]
    return list(dict.fromkeys(ids))


def cell(i, size=40):
    return f'<figure class="cell">{icon(i, size)}<figcaption>{html.escape(name_of(i if ":" in i else "bsp_core:" + i))}</figcaption></figure>'


def items():
    resources = [("Tetrium", ["tetrium_ore", "deepslate_tetrium_ore", "tetrium_nugget", "tetrium_ingot", "tetrium_dust", "tetrium_slag", "slag_brick", "tetrium_plate"]),
                 ("Illyrium", ["illyrium_ore", "deepslate_illyrium_ore", "end_stone_illyrium_ore", "dirty_illyrium_ingot", "dirty_illyrium_nugget", "dirty_illyrium_dust",
                               "pure_illyrium_dust", "illyrium_nugget", "illyrium_ingot"]),
                 ("Magnatite", ["magnatite_ore", "deepslate_magnatite_ore", "magnatite_nugget", "magnatite_ingot", "charged_magnatite_ingot", "carbon_dust"])]
    plate("resources", "Ores and metals", "".join(f'<h3>{n}</h3><div class="cells wide">{"".join(cell(i) for i in ids)}</div>' for n, ids in resources),
          "Tetrium Ore needs an iron pickaxe; Illyrium and Magnatite Ore need diamond. All three also generate in the AllTheModium Mining dimension.")
    ids = all_ids()
    per = 42
    for n in range(0, len(ids), per):
        plate(f"items_{n // per + 1}", f"Every block and item ({n // per + 1} of {(len(ids) + per - 1) // per})", '<div class="cells">' + "".join(cell(i, 36) for i in ids[n:n + per]) + "</div>",
              "Lettered tiles are blocks drawn as animated 3D models in game.")


# ----------------------------------------------------------------------------- 5. recipes
def ingredient(o):
    if isinstance(o, list):
        o = o[0]
    if "tag" in o:
        return {"bsp_core:illyrium_ores": "bsp_core:illyrium_ore", "bsp_core:tetrium_ores": "bsp_core:tetrium_ore", "bsp_core:magnatite_ores": "bsp_core:magnatite_ore"}.get(o["tag"])
    return o.get("item")


def recipe_card(name, r):
    kind = r["type"].split(":")[1]
    result = r.get("result")
    out, n = (result, 1) if isinstance(result, str) else (result["item"], result.get("count", 1))
    if kind == "crafting_shaped":
        rows = [row.ljust(3) for row in r["pattern"]] + ["   "] * (3 - len(r["pattern"]))
        cells = "".join(f'<i>{icon(ingredient(r["key"][ch]), 24) if ch in r["key"] else ""}</i>' for row in rows for ch in row[:3])
        how = "Crafting"
    elif kind == "crafting_shapeless":
        ings = [ingredient(i) for i in r["ingredients"]]
        cells = "".join(f'<i>{icon(ings[k], 24) if k < len(ings) else ""}</i>' for k in range(9))
        how = "Crafting (any arrangement)"
    else:
        cells = "<i></i>" * 4 + f'<i>{icon(ingredient(r["ingredient"]), 24)}</i>' + "<i></i>" * 4
        how = "Furnace" if kind == "smelting" else "Blast Furnace"
    return f'<figure class="rc"><figcaption>{html.escape(name_of(out))}</figcaption><div class="rrow"><div class="g3">{cells}</div><span class="arrow">&#9654;</span>{icon(out, 32, n)}</div><small>{how}</small></figure>'


def recipes():
    cards = []
    for f in sorted(glob.glob(str(DATA / "recipes/*.json"))):
        r = json.loads(open(f).read())
        if r["type"].split(":")[0] == "minecraft" and r["type"].split(":")[1] != "blasting":
            res = r["result"] if isinstance(r["result"], str) else r["result"]["item"]
            cards.append((name_of(res), recipe_card(os.path.basename(f), r)))
    cards.sort(key=lambda c: c[0])
    per = 12
    for n in range(0, len(cards), per):
        plate(f"recipes_{n // per + 1:02d}", f"Crafting recipes ({n // per + 1} of {(len(cards) + per - 1) // per})", '<div class="rcs">' + "".join(c for _, c in cards[n:n + per]) + "</div>",
              "A to Z by result. A small number on the result is how many one craft makes.")


def step(inputs, how, outputs, note=""):
    """One machine job: inputs, the machine and time, outputs."""
    ins = '<span class="plus">+</span>'.join(f'<span class="node">{icon(i, 28, c)}<em>{html.escape(name_of(i if ":" in i else "bsp_core:" + i))}</em></span>' for i, c in inputs)
    outs = '<span class="plus">+</span>'.join(f'<span class="node">{icon(i, 28, c)}<em>{html.escape(name_of(i if ":" in i else "bsp_core:" + i))}</em></span>' for i, c in outputs)
    return f'<div class="step">{ins}<span class="via"><b>{how}</b>{("<br>" + note) if note else ""}</span>{outs}</div>'


def processes():
    plate("machine_recipes_1", "Machine recipes: Tetrium and Illyrium",
          step([("tetrium_ore", 1)], "Tetrium Crucible, 20 s", [("tetrium_nugget", 2), ("tetrium_slag", 1)], "burns fuel")
          + step([("tetrium_nugget", 9)], "Combination Forge, 30 s", [("tetrium_ingot", 1)], "fuel, or RF with an upgrade")
          + step([("tetrium_ingot", 1)], "Combination Forge, 30 s", [("tetrium_plate", 1)])
          + step([("illyrium_ore", 1), ("tetrium_slag", 1)], "Illyrium Crucible, 60 s", [("dirty_illyrium_ingot", 1)], "uses lava")
          + step([("dirty_illyrium_dust", 1), ("minecraft:water_bucket", 1)], "Illyrium Refinery, 2 min", [("pure_illyrium_dust", 1)], "uses a filter")
          + step([("pure_illyrium_dust", 1), ("tetrium_dust", 1)], "Illyrium Crucible, 90 s", [("illyrium_nugget", 1)], "uses lava")
          + step([("illyrium_nugget", 9)], "Combination Forge, 30 s", [("illyrium_ingot", 1)], "needs the Illyrium Forge Upgrade"),
          "Times are the defaults, with no upgrades. Every number is in the server config.")
    plate("machine_recipes_2", "Machine recipes: Magnatite and crystals",
          step([("magnatite_ore", 1)], "Magnetic Centrifuge, 45 s", [("magnatite_nugget", 3), ("carbon_dust", 1)], "up to 7 to 9 nuggets with six layers")
          + step([("magnatite_nugget", 9)], "Combination Forge, 30 s", [("magnatite_ingot", 1)])
          + step([("magnatite_ingot", 1), ("copper_tetrium_coil", 1)], "Magnetic Centrifuge, 45 s a try", [("charged_magnatite_ingot", 1)], "1 in 6, up to 1 in 2 stacked; the coil is not used up")
          + step([("resonance_crystal", 1), ("magnatite_nugget", 1)], "Magnetic Centrifuge, 30 s", [("charged_resonance_crystal", 1)], "the nugget goes in the upgrade slot; 20 s at full speed"),
          "The Charged Resonance Crystal is the heart of every Plasma Battery and Power Cell.")
    plate("machine_recipes_3", "Machine recipes: crushing and coins",
          step([("tetrium_ingot", 1), ("minecraft:iron_pickaxe", 1)], "Crafting grid, by chance", [("tetrium_dust", 1)], "or a crusher from another mod")
          + step([("dirty_illyrium_ingot", 1), ("minecraft:iron_pickaxe", 1)], "Crafting grid, by chance", [("dirty_illyrium_dust", 1)], "or a crusher from another mod")
          + step([("copper_coin_blank", 1)], "Shatter Coin Factory, 12 h", [("copper_shatter_coin", 1)], "Gold 24 h, Diamond 48 h, Netherite 4 d")
          + step([("illyrium_coin_blank", 1)], "Shatter Coin Factory, 7 d", [("illyrium_shatter_coin", 1)], "real time, also while offline"))


# ----------------------------------------------------------------------------- 6. flow charts
def chain(*parts):
    """A flow row: item ids alternate with the name of the step between them."""
    out = '<div class="chain">'
    for p in parts:
        if isinstance(p, tuple):
            out += f'<span class="link">{html.escape(p[0])}<br>&#9654;</span>'
        else:
            out += f'<span class="node">{icon(p, 32)}<em>{html.escape(name_of(p if ":" in p else "bsp_core:" + p))}</em></span>'
    return out + "</div>"


def flows():
    plate("flow_tetrium", "How Tetrium is made",
          "<h3>Ore to ingot: about 2 minutes</h3>" + chain("tetrium_ore", ("Tetrium Crucible",), "tetrium_nugget", ("x9, Combination Forge",), "tetrium_ingot", ("Combination Forge",), "tetrium_plate")
          + "<h3>The by-product</h3>" + chain("tetrium_ore", ("Tetrium Crucible",), "tetrium_slag", ("Furnace",), "slag_brick", ("crafting",), "machine_chassis")
          + "<h3>Dust</h3>" + chain("tetrium_ingot", ("pickaxe or crusher",), "tetrium_dust", ("crafting",), "crucible_control_circuit")
          + "<h3>What it builds</h3>" + chain("tetrium_plate", ("with",), "machine_chassis", ("and",), "tetrium_coil", ("and",), "basic_control_circuit", ("make",), "illyrium_crucible"),
          "Tetrium is the first metal: everything else is built from Tetrium parts.")
    plate("flow_illyrium", "How Illyrium is made",
          "<h3>Ore to ingot: about 40 minutes</h3>" + chain("illyrium_ore", ("Illyrium Crucible + slag",), "dirty_illyrium_ingot", ("pickaxe or crusher",), "dirty_illyrium_dust", ("Illyrium Refinery",), "pure_illyrium_dust")
          + chain("pure_illyrium_dust", ("Illyrium Crucible + Tetrium Dust",), "illyrium_nugget", ("x9, Combination Forge + upgrade",), "illyrium_ingot")
          + "<h3>What it unlocks</h3>" + chain("dirty_illyrium_ingot", ("crafting",), "press_die", ("crafting",), "factory_press", ("and",), "centrifuge_rotor")
          + chain("illyrium_nugget", ("crafting",), "illyrium_processor", ("crafting",), "factory_motivator", ("and",), "magnet_core", ("and",), "plasma_interface"),
          "Illyrium is the slow, valuable metal: each ingot is nine nuggets, each made one at a time.")
    plate("flow_magnatite", "How Magnatite is made",
          "<h3>Ore to ingot: about 3 minutes</h3>" + chain("magnatite_ore", ("Magnetic Centrifuge",), "magnatite_nugget", ("x9, Combination Forge",), "magnatite_ingot")
          + "<h3>Charging: about 4 to 8 minutes more</h3>" + chain("magnatite_ingot", ("Magnetic Centrifuge + coil",), "charged_magnatite_ingot", ("x6, crafting",), "magnet_core", ("crafting",), "decoy_totem")
          + "<h3>The by-product</h3>" + chain("magnatite_ore", ("Magnetic Centrifuge",), "carbon_dust", ("crafting",), "copper_tetrium_coil", ("and",), "tetrium_core_cable", ("and",), "blast_charge")
          + "<h3>What it builds</h3>" + chain("charged_magnatite_ingot", ("crafting",), "totem_projector", ("and",), "charged_illyrium_core_cable", ("and",), "plasma_valve"),
          "Magnatite sits between Tetrium and Illyrium. Stacking centrifuges raises the yield and the charge chance.")
    plate("flow_coins", "How Shatter Coins are made",
          "<h3>Blanks: each is built on the one before</h3>" + chain("tetrium_ingot", ("crafting",), "shatter_blank", ("+ copper",), "copper_coin_blank", ("+ gold",), "gold_coin_blank", ("+ diamond",), "diamond_coin_blank")
          + chain("diamond_coin_blank", ("+ netherite",), "netherite_coin_blank", ("+ Illyrium",), "illyrium_coin_blank")
          + "<h3>Pressing: real time in the Shatter Coin Factory</h3>" + chain("copper_coin_blank", ("12 hours",), "copper_shatter_coin", ("",), "gold_shatter_coin", ("",), "diamond_shatter_coin", ("",), "netherite_shatter_coin", ("",), "illyrium_shatter_coin")
          + "<h3>What coins pay for</h3>" + chain("gold_shatter_coin", ("pay for",), "shatter_totem", ("and",), "totem_compass", ("and",), "coin_vault"),
          "Gold takes 24 hours, Diamond 48 hours, Netherite 4 days, Illyrium 7 days. Coins stack to 12.")
    tiers = [("1. Start: Tetrium", ["shatter_totem", "tetrium_ore", "tetrium_crucible", "combination_forge", "tetrium_plate", "machine_chassis", "tetrium_coil", "basic_control_circuit", "coin_vault"]),
             ("2. Illyrium", ["illyrium_crucible", "dirty_illyrium_ingot", "illyrium_refinery", "illyrium_ingot"]),
             ("3. Coins and Magnatite", ["shatter_coin_factory", "copper_shatter_coin", "magnetic_centrifuge", "magnatite_ingot", "charged_magnatite_ingot"]),
             ("4. Wave Plasma", ["plasma_extractor", "plasma_interface", "projector_base", "totem_projector", "battery_charger", "plasma_battery_2", "wave_emitter"]),
             ("5. End game", ["factory_motivator", "illyrium_shatter_coin", "decoy_totem", "plasma_battery_4", "charged_illyrium_core_cable"])]
    plate("flow_plasma", "How Wave Plasma flows",
          "<h3>From the totem to a second aura</h3>" + chain("shatter_totem", ("stands on",), "plasma_extractor", ("touching",), "plasma_interface", ("cable run",), "projector_base", ("stands on it",), "totem_projector")
          + "<h3>Bottled: batteries</h3>" + chain("plasma_interface", ("cable into its back",), "battery_charger", ("fills and stamps",), "plasma_battery_2", ("stood on an",), "plasma_extractor")
          + "<h3>Carried: cells and the emitter</h3>" + chain("battery_charger", ("fills and stamps",), "power_cell_3", ("fitted in the",), "wave_emitter", ("in your offhand",), "shatter_totem")
          + "<h3>Shaping a run</h3>" + chain("plasma_valve", ("caps or shuts a run",), "plasma_repeater", ("starts a fresh run",), "charged_illyrium_core_cable", ("80 blocks",), "projector_base"),
          "Pressure is shared equally between runs. Jade and the interface screen show the mB/t everywhere.")
    plate("flow_progression", "The whole progression",
          "".join(f'<div class="tier"><h3>{n}</h3><div class="chain">' + "".join(f'<span class="node">{icon(i, 30)}<em>{html.escape(name_of("bsp_core:" + i))}</em></span>' for i in ids) + "</div></div>" for n, ids in tiers),
          "Each row needs the one above it. Raising your totem's tier costs the coin of the tier you are leaving.")


CSS = """
:root{ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --tq:#19d3b0; --slot:#0c0e12; color-scheme:dark; }
@media (prefers-color-scheme: light){ :root:not([data-theme="dark"]){ --bg:#f3f1ea; } }
:root[data-theme="light"]{ --bg:#f3f1ea; }
*{ box-sizing:border-box; } body{ background:var(--bg); margin:0; font:13px/1.4 "IBM Plex Sans",system-ui,sans-serif; color:#e8eaf0; }
.wrap{ display:flex; flex-direction:column; gap:20px; align-items:center; padding:20px 16px 40px; } .intro{ max-width:800px; color:#9aa3b5; margin:0; } .intro h1{ font:700 24px Silkscreen,monospace; color:#e8eaf0; margin:0 0 6px; }
body.cf .intro, body.light .intro{ color:#5f6472; } .scroll{ max-width:100%; overflow-x:auto; }
.plate{ width:800px; height:600px; background:#12151b; border:1px solid #2a2f3a; display:flex; flex-direction:column; overflow:hidden; flex:none; }
.plate header{ display:flex; justify-content:space-between; align-items:baseline; padding:12px 18px 8px; border-bottom:2px solid #19d3b0; } .plate h2{ font:700 17px Silkscreen,monospace; margin:0; color:#19d3b0; } .plate header span{ font:400 11px Silkscreen,monospace; color:#9aa3b5; }
.plate .body{ flex:1; padding:12px 18px; overflow:hidden; display:flex; flex-direction:column; gap:8px; } .plate footer{ padding:8px 18px; border-top:1px solid #2a2f3a; color:#9aa3b5; font-size:12px; }
.plate h3{ font:400 12px Silkscreen,monospace; margin:0; color:#ffd23a; } .plate p{ margin:0; color:#c9ced8; } .hint{ color:#9aa3b5 !important; font-size:12px; display:flex; gap:6px; align-items:center; }
.split{ display:flex; gap:18px; flex:1; min-height:0; } .col{ flex:1; display:flex; flex-direction:column; gap:8px; min-width:0; }
.shot{ width:100%; aspect-ratio:8/5; object-fit:cover; border:1px solid #2a2f3a; display:block; } .shot.missing{ display:grid; place-items:center; color:#9aa3b5; background:#0c0e12; }
.ic{ position:relative; display:inline-grid; place-items:center; flex:none; vertical-align:middle; } .ic img{ image-rendering:pixelated; display:block; } .ic b{ position:absolute; right:-3px; bottom:-5px; font:700 11px Silkscreen,monospace; color:#fff; text-shadow:1px 1px 0 #000,-1px -1px 0 #000,1px -1px 0 #000,-1px 1px 0 #000; }
.tile{ display:grid; place-items:center; border:2px solid; background:#0c0e12; font-family:Silkscreen,monospace; }
.layers{ display:flex; gap:12px; flex-wrap:wrap; } .layers figure{ margin:0; display:flex; flex-direction:column; gap:4px; align-items:center; } .layers figcaption{ font-size:11px; color:#9aa3b5; max-width:120px; text-align:center; }
.grid{ background:#0c0e12; border:1px solid #2a2f3a; padding:3px; display:flex; flex-direction:column; gap:2px; } .grid > div{ display:flex; gap:2px; }
.legend{ list-style:none; margin:0; padding:0; display:grid; grid-template-columns:1fr 1fr; gap:3px 10px; font-size:12px; } .legend li{ display:flex; gap:6px; align-items:center; }
.cells{ display:grid; grid-template-columns:repeat(8,1fr); gap:8px 4px; } .cells.wide{ grid-template-columns:repeat(9,1fr); } .cell{ margin:0; display:flex; flex-direction:column; align-items:center; gap:3px; text-align:center; } .cell figcaption{ font-size:10.5px; line-height:1.15; color:#c9ced8; }
.rcs{ display:grid; grid-template-columns:repeat(4,1fr); gap:8px; } .rc{ margin:0; background:#171a21; border:1px solid #2a2f3a; padding:6px 8px; display:flex; flex-direction:column; gap:4px; } .rc figcaption{ font-size:11.5px; font-weight:600; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; } .rc small{ color:#9aa3b5; font-size:10.5px; }
.rrow{ display:flex; align-items:center; gap:8px; } .g3{ display:grid; grid-template-columns:repeat(3,28px); gap:2px; } .g3 i{ width:28px; height:28px; background:#0c0e12; border:1px solid #2a2f3a; display:grid; place-items:center; } .arrow{ color:#19d3b0; }
.step{ display:flex; align-items:center; gap:8px; background:#171a21; border:1px solid #2a2f3a; padding:5px 10px; } .via{ flex:1; text-align:center; color:#9aa3b5; font-size:11.5px; border-bottom:2px solid #19d3b0; padding-bottom:2px; } .via b{ color:#e8eaf0; font-weight:600; } .plus{ color:#9aa3b5; }
.node{ display:inline-flex; flex-direction:column; align-items:center; gap:2px; width:76px; text-align:center; flex:none; } .node em{ font-style:normal; font-size:10.5px; line-height:1.15; color:#c9ced8; } .step .node{ width:84px; }
.chain{ display:flex; align-items:center; gap:2px; flex-wrap:nowrap; } .link{ color:#19d3b0; font-size:10.5px; text-align:center; flex:1; min-width:40px; line-height:1.2; } .tier{ display:flex; flex-direction:column; gap:4px; border-left:3px solid #19d3b0; padding-left:10px; }
.t{ border-collapse:collapse; font-size:12px; } .t th,.t td{ text-align:left; padding:3px 12px 3px 0; border-bottom:1px solid #2a2f3a; } .t th{ color:#9aa3b5; font-weight:500; }
body.one{ overflow:hidden; background:#12151b; } body.one .wrap{ padding:0; gap:0; } body.one .intro{ display:none; } body.one .plate{ display:none; border:0; width:100vw; height:100vh; } body.one .plate.show{ display:flex; }
"""


def main():
    machines()
    layouts()
    items()
    recipes()
    processes()
    flows()
    figures = "".join(f'<div class="scroll"><section class="plate" id="plate-{i + 1}" data-slug="{slug}" aria-label="{html.escape(title)}">{body}</section></div>' for i, (slug, title, body) in enumerate(PLATES))
    page = f"""<title>BSP-Core Reference Sheet</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Silkscreen:wght@400;700&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>{CSS}</style>
<div class="wrap"><div class="intro"><h1>BSP-Core Reference Sheet</h1>{len(PLATES)} plates: every machine with its build layout and finished look, the totem extras, every block and item, every recipe, and how each metal and coin is made. Each plate is also saved as an image for CurseForge.</div>
{figures}</div>
<script>
(function(){{ const n=new URLSearchParams(location.search).get('plate'); if(!n) return; document.body.classList.add('one'); const p=document.getElementById('plate-'+n); if(p) p.classList.add('show'); }})();
</script>
"""
    assert all(ord(c) < 128 for c in page), "page must be ASCII"
    (ROOT / "tools/preview/curseforge_sheet.html").write_text(page)
    (IMAGES / "plates.json").write_text(json.dumps([{"n": i + 1, "slug": s, "title": t} for i, (s, t, _) in enumerate(PLATES)], indent=1))
    print("sheet written:", len(PLATES), "plates,", len(page) // 1024, "KB")


if __name__ == "__main__":
    main()
