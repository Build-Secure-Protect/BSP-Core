#!/usr/bin/env python3
"""Builds tools/preview/curseforge_sheet.html: the plates for the CurseForge description, in the order a player meets
things. Every picture is drawn from the mod's own files: item and block textures, the inventory-style icons that
tools/capture_images.py icons renders for the blocks that have no flat texture (docs/curseforge/icons/), the 3D renders
of tools/gen_plasma_renders.py (docs/curseforge/images/render_*.jpg), the recipe JSON, the lang file and the config
defaults written out below. Each plate is 800 x 600.

  python3 tools/gen_curseforge_sheet.py            # the page, and docs/curseforge/images/plates.json
  python3 tools/capture_images.py plates           # docs/curseforge/images/NN_slug.jpg, one per plate

Open the page to browse; ?plate=N shows one plate alone, which is what the capture uses.
"""
import base64
import glob
import html
import json
import os
import tempfile

from gen_material_assets import ASSETS, ROOT, read_png, vanilla, write_png

DATA = ROOT / "src/main/resources/data/bsp_core"
IMAGES = ROOT / "docs/curseforge/images"
ICONS = ROOT / "docs/curseforge/icons"
LANG = json.loads((ASSETS / "lang/en_us.json").read_text())
TQ = "#19d3b0"

# vanilla items whose picture is a block texture rather than an item texture
VANILLA_BLOCK = {"anvil": "anvil", "bricks": "bricks", "cobweb": "cobweb", "furnace": "furnace_front", "glass": "glass", "glass_pane": "glass", "magma_block": "magma",
                 "piston": "piston_side", "redstone_block": "redstone_block", "smooth_stone": "smooth_stone", "tnt": "tnt_side", "crafting_table": "crafting_table_front",
                 "lever": "lever", "hopper": "hopper_outside", "stone": "stone", "deepslate": "deepslate", "end_stone": "end_stone"}
VANILLA_NAMES = {"lapis_lazuli": "Lapis Lazuli", "tnt": "TNT"}
ORE_HOST = {"tetrium_ore": ("stone", "tetrium"), "deepslate_tetrium_ore": ("deepslate", "tetrium"), "illyrium_ore": ("stone", "illyrium"),
            "deepslate_illyrium_ore": ("deepslate", "illyrium"), "end_stone_illyrium_ore": ("end_stone", "illyrium"),
            "magnatite_ore": ("stone", "magnatite"), "deepslate_magnatite_ore": ("deepslate", "magnatite")}
BLOCK_TEX = {"shatter_coin_factory": "shatter_coin_factory_front", "illyrium_crucible": "illyrium_crucible_front", "illyrium_refinery": "illyrium_refinery_front",
             "magnetic_centrifuge": "magnetic_centrifuge_front", "coin_vault": "coin_vault_front", "score_screen": "score_screen_front"}
DYE_COLOURS = {"white": "#F9FFFE", "orange": "#F9801D", "magenta": "#C74EBD", "light_blue": "#3AB3DA", "yellow": "#FED83D", "lime": "#80C71F", "pink": "#F38BAA", "gray": "#474F52",
               "light_gray": "#9D9D97", "cyan": "#169C9C", "purple": "#8932B8", "blue": "#3C44AA", "brown": "#835432", "green": "#5E7C16", "red": "#B02E26", "black": "#1D1D21"}
_cache = {}


def is_dyed_cable(name):
    return any(name.startswith(d + "_") and name.endswith("_core_cable") for d in DYE_COLOURS)


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
    """Picture for an item id ('bsp_core:x', 'minecraft:x' or a bare BSP id), or None if nothing can be found."""
    if item in _cache:
        return _cache[item]
    ns, _, name = item.rpartition(":")
    src = None
    try:
        if ns == "patchouli":
            src = b64(vanilla16("item/book.png"))
        elif ns == "minecraft":
            src = b64(vanilla16(f"block/{VANILLA_BLOCK[name]}.png" if name in VANILLA_BLOCK else f"item/{name}.png"))
        elif name in ORE_HOST:
            host, metal = ORE_HOST[name]
            _, _, base = vanilla(f"block/{host}.png")
            _, _, over = read_png((ASSETS / f"textures/block/{metal}_ore_overlay.png").read_bytes())
            mixed = [[tuple(int(o[i] * o[3] / 255 + b[i] * (1 - o[3] / 255)) for i in range(3)) + (255,) for b, o in zip(br, orow)] for br, orow in zip(base, over)]
            src = b64(rows_png(16, 16, mixed))
        else:
            if is_dyed_cable(name):  # a dyed cable is pictured as its plain kind
                name = name.split("_", 1)[1] if not name.startswith("light_") else name.split("_", 2)[2]
            for path in (ASSETS / f"textures/item/{name}.png", ASSETS / f"textures/block/{BLOCK_TEX.get(name, name)}.png", ASSETS / f"textures/item/{name}_00.png",
                         ICONS / f"{name}.png"):
                if path.exists():
                    src = b64(path.read_bytes())
                    break
    except (KeyError, FileNotFoundError):
        src = None
    _cache[item] = src
    return src


def name_of(item):
    ns, _, name = item.rpartition(":")
    if ns == "patchouli":
        return "Guide Book"
    if ns == "minecraft":
        return VANILLA_NAMES.get(name, name.replace("_", " ").title())
    return LANG.get(f"item.bsp_core.{name}") or LANG.get(f"block.bsp_core.{name}") or name.replace("_", " ").title()


def full(item):
    return item if ":" in item else "bsp_core:" + item


def icon(item, size=32, count=None):
    """An item as a picture with an optional count badge; an empty span if item is None."""
    if not item:
        return f'<span class="ic" style="width:{size}px;height:{size}px"></span>'
    item = full(item)
    src, name = icon_src(item), html.escape(name_of(item))
    if src:
        inner = f'<img src="{src}" alt="{name}" width="{size}" height="{size}">'
    else:
        inner = f'<span class="tile" style="width:{size}px;height:{size}px;font-size:{max(9, size // 3)}px">{html.escape(item.split(":")[1][:2].title())}</span>'
    badge = f'<b>{count}</b>' if count and count > 1 else ""
    return f'<span class="ic" title="{name}" style="width:{size}px;height:{size}px">{inner}{badge}</span>'


def render(name, alt, ratio=None):
    """A saved render as an inline picture; a ratio such as '2/1' crops it shorter so two fit in one column."""
    path = IMAGES / f"render_{name}.jpg"
    style = f' style="aspect-ratio:{ratio}"' if ratio else ""
    if not path.exists():
        return f'<div class="shot missing"{style}>{html.escape(alt)}</div>'
    return f'<img class="shot" alt="{html.escape(alt)}"{style} src="data:image/jpeg;base64,{base64.b64encode(path.read_bytes()).decode()}">'


def arrow(label="", w=44, h=12):
    """A tailed arrow with a label above it; '|' in the label breaks the line."""
    lab = "<br>".join(html.escape(p) for p in label.split("|"))
    return (f'<span class="arr"><em>{lab}</em><svg width="{w}" height="{h}" viewBox="0 0 {w} {h}"><path d="M1 {h // 2}H{w - 7}" stroke="{TQ}" stroke-width="2"/>'
            f'<path d="M{w - 8} 1l7 {h // 2 - 1}-7 {h // 2 - 1}z" fill="{TQ}"/></svg></span>')


def node(item, size=44, count=None, label=None):
    return f'<span class="node" style="width:{max(72, size * 2)}px">{icon(item, size, count)}<em>{html.escape(label or name_of(full(item)))}</em></span>'


def chain(*parts, size=44):
    """A flow row: item ids (optionally (id, count) tuples) joined by arrows given as ('label',) tuples; the string '+' is a plus sign."""
    out = '<div class="chain">'
    for p in parts:
        if p == "+":
            out += '<span class="plus">+</span>'
        elif isinstance(p, tuple) and len(p) == 1:
            out += arrow(p[0])
        elif isinstance(p, tuple):
            out += node(p[0], size, p[1])
        else:
            out += node(p, size)
    return out + "</div>"


PLATES = []


def plate(slug, title, body, note=""):
    PLATES.append((slug, title, f'<header><h2>{html.escape(title)}</h2><span>BSP-Core</span></header><div class="body">{body}</div>'
                   + (f'<footer>{note}</footer>' if note else "")))


def h3(text):
    return f"<h3>{html.escape(text)}</h3>"


def p(text):
    return f"<p>{text}</p>"


# ----------------------------------------------------------------------------- the totem
def totem_plates():
    facts = ("<ul class='facts'>"
             "<li><b>Everyone gets one</b> on their first join. It cannot be crafted or destroyed, only stolen.</li>"
             "<li><b>Place it</b> and it projects your base powers in a cube around it. <b>Carry it</b> and it powers you.</li>"
             "<li><b>Steal one</b> by standing at a rival's totem until the timer runs out: five minutes by default. The owner is warned the moment it starts.</li>"
             "<li><b>Nothing makes a totem safe.</b> No power, no block, no trick. That rule is not going to change.</li>"
             "<li><b>It stays in play.</b> A dropped totem can only be picked up by its owner, and one put into a storage system comes straight back.</li>"
             "<li><b>Coins and seasons.</b> Powers are bought with Shatter Coins and XP; every totem scores by its tier on the season leaderboard.</li></ul>")
    plate("totem", "The Shatter Totem",
          '<div class="split"><div class="col" style="flex:1.15">' + render("shatter_totem", "The Shatter Totem")
          + '<p class="hint">Left to right below: unclaimed, owned, and being stolen.</p>' + render("totem_states", "Unclaimed, owned and being stolen", "8/3") + '</div>'
          '<div class="col">' + facts + '</div></div>',
          "Friends go on the ACCESS tab: up to eight players who pass your Alarm and Ward, may buy powers, or may open your machines.")

    def path(title, steps):
        boxes = "".join(f'<div class="pw"><b>{html.escape(LANG["buff.bsp_core." + k])}</b><span>{html.escape(t)}</span></div>'
                        + ('<svg class="varr" width="12" height="11" viewBox="0 0 12 11"><path d="M6 0v6" stroke="#19d3b0" stroke-width="2"/><path d="M1 5l5 6 5-6z" fill="#19d3b0"/></svg>' if i < len(steps) - 1 else "")
                        for i, (k, t) in enumerate(steps))
        return f'<div class="path"><h3>{html.escape(title)}</h3>{boxes}</div>'

    body = ('<div class="paths">'
            + path("Carried: body", [("damage", "+0.8 attack a level, to +8"), ("resistance", "Less damage taken"), ("vitality", "Extra hearts")])
            + path("Carried: feet, eyes", [("mining_speed", "Faster mining"), ("swiftness", "Faster running"), ("featherfall", "Less fall damage"), ("night_sight", "Night vision while held"),
                                           ("xray", "Blocks turn to glass, ores shine through"), ("bouncy", "Off Featherfall: bounce back up")])
            + path("Base: walls", [("fortify", "Blocks survive blasts; intruders mine slower"), ("alarm", "Intruders outlined, you are warned"), ("ward", "Intruders get Weakness"),
                                   ("deadlock", "Stealing takes longer"), ("cloaking", "Outsiders see empty land"), ("sentinel", "Off Alarm: who is inside, from anywhere")])
            + path("Base: aura, plasma", [("healing", "Heals you near the totem"), ("sanctuary", "No hostile mobs spawn"), ("overclock", "Its injectors speed machines up more"),
                                          ("recall", "Teleport home during a steal"), ("output", "Wave Plasma, to 6,000 mB/t"), ("anchor", "Keeps chunks loaded"), ("survey", "Pick which chunks"),
                                          ("harvest", "Mined BSP ores grow back")])
            + path("Raid", [("lockpick", "Shorter steals"), ("shroud", "The owner is warned late"), ("recall_block", "Delays the owner's Recall"), ("thief_step", "Silent; low Alarms miss you"),
                            ("siege", "Press V: mine at full speed in Fortify")])
            + '</div>')
    plate("totem_powers", "28 powers on five tiers",
          body, "Each power opens the next on its path at level 2. Raising the totem's tier costs 4 coins of the tier you are leaving; Mining Speed to Night Sight cost half the usual XP.")


# ----------------------------------------------------------------------------- progression and ores
def progression():
    tiers = [("1. Tetrium: the first machines", ["shatter_totem", "tetrium_ore", "tetrium_crucible", "combination_forge", "machine_chassis", "illyrium_crucible"]),
             ("2. Illyrium: slow, valuable, and the key to the rest", ["illyrium_ore", "dirty_illyrium_ingot", "illyrium_refinery", "illyrium_nugget", "illyrium_processor", "illyrium_ingot"]),
             ("3. Magnatite: the centrifuge, charged metal and coins", ["magnetic_centrifuge", "magnatite_ingot", "charged_magnatite_ingot", "shatter_coin_factory", "copper_shatter_coin", "charged_resonance_crystal"]),
             ("4. Wave Plasma: the totem's power on tap", ["plasma_extractor", "plasma_interface", "tetrium_core_cable", "totem_projector", "plasma_injector", "plasma_battery_2"]),
             ("5. End game", ["illyrium_shatter_coin", "decoy_totem", "plasma_battery_4", "charged_illyrium_core_cable", "wave_emitter", "tank_port"])]
    rows = ""
    for title, ids in tiers:
        parts = []
        for i in ids:
            parts += [i, ("",)]
        rows += f'<div class="tier">{h3(title)}{chain(*parts[:-1], size=40)}</div>'
    plate("progression", "The road from Tetrium to Illyrium", rows,
          "Each row needs the one above it. Everything here is crafted from Tetrium parts first; Illyrium only ever comes one nugget at a time.")

    plate("ores", "Where the metals come from",
          h3("Tetrium: iron pickaxe, common from the surface down") + chain("tetrium_ore", "deepslate_tetrium_ore", ("mine",), "raw_tetrium", ("Tetrium Crucible",), "tetrium_nugget", "+", "tetrium_slag")
          + h3("Magnatite: diamond pickaxe") + chain("magnatite_ore", "deepslate_magnatite_ore", ("mine",), "raw_magnatite", ("Magnetic Centrifuge",), "magnatite_nugget", "+", "carbon_dust")
          + h3("Illyrium: diamond pickaxe, 8 small veins a chunk, in the End too") + chain("illyrium_ore", "deepslate_illyrium_ore", "end_stone_illyrium_ore", ("mine",), "raw_illyrium", ("Illyrium Crucible",), "dirty_illyrium_ingot")
          + h3("Ores drop raw chunks, like iron; the machines take chunks and ore blocks alike")
          + p("All three also generate in the AllTheModium mining dimension. Fortune works on them, and any crusher from another mod can turn the <b>ingots</b> into dust, "
              "but no other mod can smelt the ore: nuggets only ever come out of the Tetrium Crucible, the Illyrium Crucible and the Magnetic Centrifuge."),
          "Ore blocks are tagged forge:ores, so other mods' tools and X-rays recognise them.")


# ----------------------------------------------------------------------------- the metals, one plate each: the jobs with their default times
def metals():
    plate("tetrium", "Tetrium: the first metal",
          h3("Ore to ingot in about a minute") + chain("tetrium_ore", ("Tetrium Crucible|15 s, burns fuel",), ("tetrium_nugget", 2), ("x9, Combination Forge|30 s, or 15 s on RF",), "tetrium_ingot", ("Combination Forge|30 s",), "tetrium_plate")
          + h3("The by-product builds every machine") + chain("tetrium_ore", ("Tetrium Crucible",), "tetrium_slag", ("Furnace",), "slag_brick", ("+ plates",), "machine_chassis", ("+ nugget",), "circuit_substrate")
          + h3("Dust, by hand or in a crusher") + chain("tetrium_ingot", "+", "minecraft:iron_pickaxe", ("crafting grid|1 in 3; or any crusher",), "tetrium_dust", ("crafting",), "crucible_control_circuit", "+", "mint_control_circuit")
          + h3("Parts that everything later is built from") + chain("tetrium_plate", "+", "machine_chassis", "+", "tetrium_coil", "+", "basic_control_circuit", "+", "drive_motor", ("make",), "illyrium_crucible")
          + h3("And the first things worth having") + chain("tetrium_ingot", ("crafting",), "wrench", "+", "coin_vault", "+", "patchouli:guide_book", "+", "rf_upgrade_mk1", "+", "tetrium_glass"),
          "Times are the defaults with no upgrades or plasma. Every number is in serverconfig/bsp_core-server.toml.")
    plate("machines_single", "Tetrium Crucible and Combination Forge",
          '<div class="split"><div class="col">' + h3("Tetrium Crucible") + render("tetrium_crucible", "Tetrium Crucible")
          + p("A furnace with a bigger idea. Burns any furnace fuel and turns one Tetrium Ore or raw chunk into 2 Tetrium Nuggets and 1 Tetrium Slag in 15 seconds. Keep the slag.") + '</div>'
          '<div class="col">' + h3("Combination Forge") + render("combination_forge", "Combination Forge")
          + p("Presses nine nuggets into an ingot, Tetrium Ingots into Tetrium Plates, and with the Illyrium Forge Upgrade nine Illyrium Nuggets into an ingot. Fuel, or RF twice as fast with an RF Upgrade.") + '</div></div>'
          + h3("What they do") + '<div class="jobs">' + chain("tetrium_ore", ("Tetrium Crucible|15 s",), ("tetrium_nugget", 2), "+", "tetrium_slag", size=36)
          + chain(("tetrium_nugget", 9), ("Combination Forge|30 s",), "tetrium_ingot", ("Combination Forge|30 s",), "tetrium_plate", size=36) + '</div>'
          + '<div class="jobs">' + chain("rf_upgrade_mk1", "+", "rf_upgrade_mk2", "+", "rf_upgrade_mk3", ("in the upgrade slot",), "combination_forge", ("runs on RF, twice as fast; Mk II and III faster still",), "illyrium_forge_upgrade", size=36) + '</div>',
          "Both take a Plasma Injector on any side for up to three times the speed, and Jade reads their progress.")
    plate("illyrium", "Illyrium: slow and valuable",
          h3("Ore to dust: lava in, dirty metal out") + chain("illyrium_ore", "+", ("tetrium_slag", 2), ("Illyrium Crucible|40 s, 250 mB lava",), "dirty_illyrium_ingot", "+", "minecraft:iron_pickaxe", ("crafting grid|1 in 4; or a crusher",), "dirty_illyrium_dust")
          + h3("Dust to nugget: wash it, then alloy it") + chain("dirty_illyrium_dust", "+", "minecraft:water_bucket", ("Illyrium Refinery|90 s, a filter",), "pure_illyrium_dust", "+", "tetrium_dust", ("Illyrium Crucible|60 s, lava",), "illyrium_nugget")
          + h3("Nine nuggets, forty minutes, one ingot") + chain(("illyrium_nugget", 9), ("Combination Forge|Illyrium Forge Upgrade",), "illyrium_ingot", ("crafting",), "totem_compass", "+", "illyrium_filter", "+", "illyrium_coin_blank")
          + h3("Dirty metal is useful on its own") + chain("dirty_illyrium_ingot", ("+ diamond",), "press_die", ("crafting",), "factory_press", "+", "centrifuge_rotor", ("and the dust",), "refinery_control_circuit")
          + h3("Nuggets open the next tier") + chain("illyrium_nugget", ("crafting",), "illyrium_processor", ("crafting",), "factory_motivator", "+", "magnet_core", "+", "plasma_injector", "+", "rf_upgrade_mk3"),
          "An Iron Filter cleans 5 dusts, Diamond 20, Netherite 100, Illyrium 1,000. The refinery and the upper crucible pylons take RF.")
    plate("magnatite", "Magnatite: the metal in between",
          h3("Ore to ingot in about three minutes") + chain("magnatite_ore", ("Magnetic Centrifuge|30 s, RF",), ("magnatite_nugget", 3), "+", "carbon_dust", ("x9, Combination Forge",), "magnatite_ingot")
          + h3("Charging: a gamble that pays off with more layers") + chain("magnatite_ingot", "+", "copper_tetrium_coil", ("Magnetic Centrifuge|30 s a try, 1 in 6; 1 in 2 with six layers",), "charged_magnatite_ingot", ("x6, crafting",), "magnet_core", ("crafting",), "decoy_totem")
          + h3("Carbon Dust is in everything electrical") + chain("carbon_dust", ("crafting",), "copper_tetrium_coil", "+", "tetrium_core_cable", "+", "blast_charge", "+", "range_coil_mk1", "+", "plasma_extractor")
          + h3("The crystal at the heart of every battery") + chain("resonance_crystal", "+", "magnatite_nugget", ("Magnetic Centrifuge|30 s; nugget in the upgrade slot",), "charged_resonance_crystal", ("crafting",), "plasma_battery_1", "+", "power_cell_1", "+", "tank_port")
          + h3("Charged metal builds the plasma gear") + chain("charged_magnatite_ingot", ("crafting",), "totem_projector", "+", "plasma_repeater", "+", "charged_illyrium_core_cable", "+", "wave_emitter", "+", "trap_amplifier"),
          "Stack up to six centrifuge layers: one ore gives 3 nuggets with one layer and 7 to 9 with six, and a charge try succeeds 1 in 2.")
    plate("coins", "Shatter Coins: real time, no shortcuts",
          h3("Blanks: each one is built on the one before") + chain("tetrium_ingot", ("crafting",), "shatter_blank", ("+ copper",), "copper_coin_blank", ("+ gold",), "gold_coin_blank", ("+ diamond",), "diamond_coin_blank", ("+ netherite",), "netherite_coin_blank")
          + h3("Pressed in the Shatter Coin Factory, in real hours, even while you are offline")
          + chain("copper_coin_blank", ("8 hours",), "copper_shatter_coin", ("16 h",), "gold_shatter_coin", ("32 h",), "diamond_shatter_coin", ("64 h",), "netherite_shatter_coin", ("96 h",), "illyrium_shatter_coin")
          + h3("The top blank needs the slow metal") + chain("netherite_coin_blank", "+", "illyrium_ingot", ("crafting",), "illyrium_coin_blank", ("96 hours, 800,000 RF",), "illyrium_shatter_coin")
          + h3("What coins pay for") + chain("gold_shatter_coin", ("tier gates and powers",), "shatter_totem", ("faster tracking",), "totem_compass", ("earns interest in",), "coin_vault")
          + h3("Faster: up to three Motivators on a slice") + chain("factory_motivator", ("15 %, 30 %, 50 % off",), "shatter_coin_factory", ("a Plasma Injector in a Motivator cell",), "plasma_injector"),
          "A press costs 50,000 RF for a Copper coin and doubles each tier. Up to 10 slices side by side make one factory. Coins stack to 12.")


# ----------------------------------------------------------------------------- build plates for the multiblocks
def layers(pattern, mapping, labels, size=34):
    out = '<div class="layers">'
    for i, layer in enumerate(pattern):
        grid = "".join("<div>" + "".join(icon(mapping.get(ch), size) for ch in row) + "</div>" for row in layer)
        out += f'<figure><div class="grid">{grid}</div><figcaption>{labels[i]}</figcaption></figure>'
    return out + '</div><p class="hint">Each grid is one layer seen from above; the bottom row of a grid is the front.</p>'


def legend(mapping, counts):
    return '<ul class="legend">' + "".join(f'<li>{icon(v, 24)} {counts[v]} x {html.escape(name_of("bsp_core:" + v))}</li>' for v in dict.fromkeys(mapping.values()) if v in counts) + "</ul>"


def count(pattern, mapping):
    c = {}
    for layer in pattern:
        for row in layer:
            for ch in row:
                if ch in mapping:
                    c[mapping[ch]] = c.get(mapping[ch], 0) + 1
    return c


def machine_plate(slug, title, pattern, mapping, labels, shot, what, extra="", jobs=()):
    counts = count(pattern, mapping)
    left = f'<div class="col">{h3("Build it")}{layers(pattern, mapping, labels)}{legend(mapping, counts)}{extra}</div>'
    right = f'<div class="col">{h3("Finished")}{render(shot, title)}{p(what)}</div>'
    bottom = (h3("What it does") + '<div class="jobs">' + "".join(jobs) + '</div>') if jobs else ""
    plate(slug, title, f'<div class="split">{left}{right}</div>{bottom}', "Ghost blocks show where every part goes. Sneak + right-click the controller for the step-by-step Assembly Guide; JEI and EMI have it too.")


def machines():
    cas = {"C": "illyrium_casing", "P": "lava_pylon", "H": "item_hatch", "O": "illyrium_core", "G": "illyrium_glass", "U": "refinery_pump", "X": "illyrium_crucible"}
    machine_plate("machine_illyrium_crucible", "Illyrium Crucible (3 x 3 x 3)", [["CCC", "CCC", "CXC"], ["P P", "HOH", "P P"], ["PCP", "C C", "PCP"]], cas,
                  ["Bottom layer", "Middle layer", "Top layer"], "illyrium_crucible",
                  "Runs on lava: buckets or Magma Blocks into the lower pylons, 250 mB a job. Smelts Illyrium Ore with two slag into a Dirty Illyrium Ingot in 40 seconds, "
                  "and alloys Pure Illyrium Dust with Tetrium Dust into an Illyrium Nugget in 60. The upper pylons take RF.",
                  jobs=[chain("illyrium_ore", "+", ("tetrium_slag", 2), ("40 s, 250 mB lava",), "dirty_illyrium_ingot", size=36),
                        chain("pure_illyrium_dust", "+", "tetrium_dust", ("60 s, 250 mB lava",), "illyrium_nugget", size=36)])
    ref = dict(cas, X="illyrium_refinery")
    machine_plate("machine_illyrium_refinery", "Illyrium Refinery", [["CCC", "COC", "CXC"], [" C ", "HGU", "   "], ["   ", " G ", "   "]], ref,
                  ["Bottom layer", "Middle layer", "Top layer"], "illyrium_refinery",
                  "Washes Dirty Illyrium Dust into Pure Illyrium Dust: 500 mB of water and a filter use per dust, 90 seconds each. The pump takes water and RF, "
                  "the Item Hatch takes dust in and gives pure dust out. Filters: Iron 5 uses, Diamond 20, Netherite 100, Illyrium 1,000.",
                  jobs=[chain("dirty_illyrium_dust", "+", "minecraft:water_bucket", "+", "iron_filter", ("90 s, 500 mB water, one filter use",), "pure_illyrium_dust", size=36)])
    cen = {"K": "centrifuge_casing", "T": "centrifuge_rotor", "W": "centrifuge_power_port", "H": "item_hatch", "X": "magnetic_centrifuge"}
    machine_plate("machine_magnetic_centrifuge", "Magnetic Centrifuge (3 x 3, stackable to six layers)", [["KKK", "HTW", "KXK"], ["KKK", "KTK", "KKK"]], cen,
                  ["Bottom layer", "Each extra layer on top (up to 5 more)"], "magnetic_centrifuge",
                  "Always needs RF: 60 a tick per layer separating, 240 charging. Separates Magnatite Ore into nuggets and Carbon Dust in 30 seconds, charges Magnatite Ingots with a Copper Tetrium Coil, "
                  "and magnetises Resonance Crystals with a nugget in the upgrade slot. More layers: more nuggets, better odds, faster.",
                  jobs=[chain("magnatite_ore", ("30 s, 60 RF/t a layer",), ("magnatite_nugget", 3), "+", "carbon_dust", size=36),
                        chain("magnatite_ingot", "+", "copper_tetrium_coil", ("30 s a try, 1 in 6; the coil stays",), "charged_magnatite_ingot", size=36)])
    fac = {"B": "factory_blank_hatch", "F": "factory_frame", "X": "shatter_coin_factory", "R": "factory_power_port", "S": "factory_press"}
    machine_plate("machine_coin_factory", "Shatter Coin Factory (one slice: 1 x 2 x 3)", [["B", "F", "X"], ["R", "S", "F"]], fac, ["Bottom", "Top"], "coin_factory",
                  "Presses coin blanks into Shatter Coins in real time, 8 hours for Copper to 96 for Illyrium, and keeps going while you are offline. Needs RF. "
                  "Up to 10 slices side by side join into one machine, each pressing its own coin.",
                  f'<p class="hint">{icon("factory_motivator", 24)} Up to three Motivators on top of a slice cut its time by 15, 30 and 50 %. A Plasma Injector standing in a Motivator cell speeds it further.</p>',
                  jobs=[chain("copper_coin_blank", ("8 hours, 50,000 RF",), "copper_shatter_coin", size=36), chain("netherite_coin_blank", ("64 hours, 400,000 RF",), "netherite_shatter_coin", size=36),
                        chain("illyrium_coin_blank", ("96 hours, 800,000 RF",), "illyrium_shatter_coin", size=36)])


# ----------------------------------------------------------------------------- wave plasma
def plasma():
    plate("plasma_network", "Wave Plasma: the totem's power on tap",
          '<div class="split"><div class="col" style="flex:1.6">' + render("plasma_network", "A Wave Plasma network")
          + p("Totem on an extractor, interfaces beside it, cables out to a Projector Base with its Projector, and a second run into the back of a Battery Charger. A valve and a repeater sit in the long run.") + '</div>'
          '<div class="col">' + h3("How it flows") + p("A placed totem gives off 100 mB/t, up to 6,000 with Output. Touching interfaces join into one body of up to twelve, and each face sends up to 1,000 mB/t, shared equally between the runs leaving it.")
          + '<table class="t"><tr><th>Cable</th><th>Run</th><th>Carries</th></tr>'
          + "".join(f'<tr><td>{icon(c, 22)} {k}</td><td>{n} blocks</td><td>{t} mB/t</td></tr>' for c, k, n, t in
                    (("tetrium_core_cable", "Tetrium", 15, 250), ("magnatite_core_cable", "Magnatite", 25, 500), ("illyrium_core_cable", "Illyrium", 40, "1,000"), ("charged_illyrium_core_cable", "Charged Illyrium", 80, "1,000"))) + '</table>'
          + p("A repeater starts a fresh run for a tenth of the pressure. A valve caps a run or shuts it by redstone. A projector needs 100 mB/t and its base holds 5,000 mB.") + '</div></div>',
          "Right-click any interface for the flow view: the whole network in 3D with the mB/t on every cable. Jade reads the figures block by block.")
    plate("plasma_blocks", "The plasma blocks",
          '<div class="split"><div class="col">' + h3("Extractor and interfaces") + render("plasma_interface_group", "Six interfaces joined, one refused", "5/2")
          + p("Touching interfaces join into one body, up to twelve. A thirteenth, or one touching an extractor another group holds, turns red and does nothing.")
          + h3("Projector Base") + render("projector_base", "Projector Base and Projector", "5/2") + p("A tank the Totem Projector stands on. Cables plug into its sides; the level shows through its windows. Right-click the projector to choose which powers it receives.") + '</div>'
          '<div class="col">' + h3("Plasma Valve") + render("plasma_valve", "Plasma Valve", "5/2") + p("Sets the most that may pass, from its screen. A lever on it or any redstone signal shuts it. The wheel turns with the limit.")
          + h3("Plasma Repeater") + render("plasma_repeater", "Plasma Repeater", "5/2") + p("One way: in through the dark back, out through the lit front. Starts a fresh run. The wrench turns it.") + '</div></div>',
          "Powers pass a valve unchanged. Behind a repeater a projector receives one power at full level, or several each a level lower per repeater.")
    plate("plasma_tank_injector", "Plasma Tank and Plasma Injector",
          '<div class="split"><div class="col">' + h3("Plasma Tank") + render("plasma_tank", "A formed Plasma Tank, 60 % full")
          + p("A hollow box of Tank Casing edges, Tank Glass or casing faces and Tank Ports, 3 to 12 blocks a side. It forms by itself when the last block goes in: a sweep of light runs over it, the shell turns to glass with lit rails, "
              "and the corner uprights glow as high as the plasma stands. 2,500,000 mB for every shell block. Ports take plasma from an interface run and feed bases, chargers and other tanks.") + '</div>'
          '<div class="col">' + h3("Plasma Injector") + render("plasma_injector", "A Plasma Injector feeding a Tetrium Crucible")
          + p("A column placed against any machine, cable on the far end. Feed it and the machine runs faster the more arrives: twice the speed at 300 mB/t, three times at 500. "
              "Works on every BSP machine, on a coin factory slice through a Motivator cell, and on other mods' machines. Cut the cable and it drops back to normal in a second.") + '</div></div>'
          + '<div class="jobs">' + chain("plasma_interface", ("a run in",), "tank_port", ("runs out, a face's worth each",), "projector_base", "+", "battery_charger", "+", "plasma_injector", size=36)
          + chain("plasma_injector", ("300 mB/t: twice the speed|500 mB/t: three times",), "combination_forge", size=36) + '</div>',
          "Overclock travels with the plasma: every injector fed from a totem with Overclock is stronger, by a quarter to double its speed-up by level. Tank plasma carries no Overclock.")
    plate("batteries", "Batteries, cells and the Wave Emitter",
          '<div class="split"><div class="col">' + h3("Battery Charger") + render("battery_charger", "Battery Charger", "5/2")
          + p("Cable into its back. Fills the battery or cell standing in it and stamps the interface's powers into it. Hoppers and pipes may feed empties in and take full ones out on any other side.")
          + h3("Wave Emitter, Power Cells, Wrench") + render("wave_emitter", "Wave Emitter, Power Cells and the Wrench", "5/2")
          + p("The emitter runs on a cell in your offhand and gives you its Carried powers at 20 mB/t, with the totem safe at home. The wrench sets cable ends and turns repeaters.") + '</div>'
          '<div class="col">' + h3("Plasma Batteries") + render("plasma_batteries", "Plasma Batteries, and one feeding an extractor", "5/2")
          + p("Four tiers. Stood on an extractor instead of a totem, a battery feeds the network with its stamped Base powers, so everything keeps working while the totem is away, or stolen.")
          + '<table class="t"><tr><th>Tier</th><th>Holds</th><th>Powers</th></tr><tr><td>Battery I to IV</td><td>40k / 200k / 1M / 5M mB</td><td>0 / 2 / 3 / 4 Base</td></tr>'
            '<tr><td>Cell I to III</td><td>8k / 24k / 60k mB</td><td>1 / 2 / 3 Carried</td></tr></table></div></div>',
          "Every battery and cell is built round a Charged Resonance Crystal: a Resonance Crystal magnetised in the centrifuge with a Magnatite Nugget in the upgrade slot.")
    plate("cable_colours", "Coloured cables and cable ends",
          '<div class="split"><div class="col">' + h3("Sixteen colours") + render("coloured_cables", "Sixteen colours of Illyrium cable", "5/2")
          + p("Magnatite, Illyrium and Charged Illyrium cables take any dye: eight cables round a dye, and dye again to change. A coloured cable joins only its own colour and a plain one only plain ones, so runs cross without mixing.")
          + '<div class="cells" style="grid-template-columns:repeat(8,1fr)">' + "".join(f'<span class="swatch" style="background:{c}" title="{d}"></span>' for d, c in DYE_COLOURS.items()) + '</div></div>'
          '<div class="col">' + h3("Wrench-set ends") + render("cable_ends", "Wrench-set cable ends", "5/2")
          + p("Point the wrench at the end of a cable and right-click to set it: Normal, Output (plasma only leaves there), Input (only enters), Off (not joined), or Linked where another colour may join.") + '</div></div>',
          "One Output end at a base stops a loop feeding back; an Input end on a shared trunk stops a run drawing from the wrong side.")


# ----------------------------------------------------------------------------- raiding and base blocks
def extras():
    plate("raiding", "Decoys, the compass and traps",
          '<div class="split"><div class="col">' + h3("What the decoy's owner sees") + render("decoy_owner_view", "Decoy Totem as its owner sees it", "5/2")
          + p("A Hollow Idol on its Decoy Power Base, 100 RF a tick, with a faint ghost of the totem round it. Five per player.") + '</div>'
          '<div class="col">' + h3("What everyone else sees") + render("decoy_other_view", "Decoy Totem as other players see it", "5/2")
          + p("An ordinary Shatter Totem. Rival compasses within range point at it. Try to steal it and its traps go off.") + '</div></div>'
          + h3("Decoy sockets: coils for range, charges for traps, an amplifier to make them worse, casings to make it tougher")
          + chain("range_coil_mk1", "+", "range_coil_mk3", "+", "blast_charge", "+", "hex_charge", "+", "poison_charge", "+", "fatigue_charge", "+", "warp_charge", "+", "trap_amplifier", "+", "reinforced_casing", size=36)
          + h3("Totem Compass") + chain("totem_compass", ("points to your own",), "shatter_totem", ("or for a while to the nearest rival's",), "decoy_totem", ("coins shorten the cooldown",), "gold_shatter_coin", size=36),
          "A decoy within a compass's range is what the compass finds. Decoys are meant to be costly to run.")
    plate("base_blocks", "Vault, screens and admin blocks",
          '<div class="split"><div class="col">' + h3("Coin Vault, joined") + render("coin_vault_joined", "Joined Coin Vault", "5/2")
          + p("Vault blocks of one owner join into one vault of up to 3 x 3 x 3. Coins inside earn interest. Rivals can pick the lock for a quarter of the coins.") + '</div>'
          '<div class="col">' + h3("Anti Totem Block") + render("anti_totem_block", "Anti Totem Block", "5/2")
          + p("For admins: keeps Shatter Totems and chosen BSP blocks out of a box of up to 256 blocks in each direction. Spawn, hubs, arenas.") + '</div></div>'
          + f'<p class="hint big">{icon("score_screen", 40)} <span><b>Score Screen.</b> An admin block, placed from the creative menu: shows the live leaderboard, the rules, or the season\'s prizes. Every totem scores by its tier.</span></p>'
          + f'<p class="hint big">{icon("admin_rack", 40)} <span><b>Admin Rack</b>, or /bsp admin. Every player\'s totems, positions and vault coins, with teleport and reset. End a season from it: the top three get the prizes you placed, everyone gets a fresh totem.</span></p>'
          + f'<p class="hint big">{icon("totem_compass", 40)} <span><b>One config file</b>, serverconfig/bsp_core-server.toml, holds every cost, time, range and price. Optional MySQL or MariaDB links several servers into one season.</span></p>',
          "Moderators get a read-only admin view. /bsp totem buff and /bsp totem tier set powers for testing.")


# ----------------------------------------------------------------------------- recipes, in the order you need them
GROUPS = [
    ("Tetrium", ["guide_book", "wrench", "tetrium_crucible", "combination_forge", "tetrium_ingot_from_nuggets", "tetrium_nuggets_from_ingot", "slag_brick_from_smelting", "machine_chassis",
                 "tetrium_coil", "circuit_substrate", "basic_control_circuit", "drive_motor", "thermal_lining", "conveyor_belt", "rf_upgrade_mk1", "rf_upgrade_mk2", "tetrium_glass",
                 "coin_vault", "hand:tetrium_dust"]),
    ("Illyrium Crucible", ["crucible_control_circuit", "illyrium_casing", "illyrium_glass", "lava_pylon", "item_hatch", "illyrium_core", "illyrium_crucible", "dirty_illyrium_ingot_from_nuggets"]),
    ("Illyrium Refinery", ["hand:dirty_illyrium_dust", "refinery_control_circuit", "refinery_pump", "iron_filter", "diamond_filter", "netherite_filter", "illyrium_refinery"]),
    ("Illyrium", ["press_die", "illyrium_nuggets_from_ingot", "illyrium_ingot_from_nuggets", "illyrium_processor", "illyrium_forge_upgrade", "illyrium_filter", "rf_upgrade_mk3", "totem_compass", "resonance_crystal"]),
    ("Magnetic Centrifuge", ["centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port", "magnetic_centrifuge", "magnatite_ingot_from_nuggets", "magnatite_nuggets_from_ingot", "copper_tetrium_coil"]),
    ("Shatter Coins", ["mint_control_circuit", "shatter_coin_factory", "factory_frame", "factory_blank_hatch", "factory_power_port", "factory_press", "factory_motivator", "shatter_blank",
                       "copper_coin_blank", "gold_coin_blank", "diamond_coin_blank", "netherite_coin_blank", "illyrium_coin_blank"]),
    ("Wave Plasma", ["tetrium_core_cable", "magnatite_core_cable", "illyrium_core_cable", "charged_illyrium_core_cable", "blue_illyrium_core_cable", "plasma_extractor", "plasma_interface",
                     "plasma_valve", "plasma_repeater", "projector_base", "totem_projector", "channel_expander", "tank_casing", "tank_glass", "tank_port", "plasma_injector"]),
    ("Batteries", ["battery_charger", "plasma_battery_1", "plasma_battery_2", "plasma_battery_3", "plasma_battery_4", "power_cell_1", "power_cell_2", "power_cell_3", "wave_emitter"]),
    ("Decoys and traps", ["magnet_core", "decoy_power_base", "decoy_totem", "range_coil_mk1", "range_coil_mk2", "range_coil_mk3", "blast_charge", "hex_charge", "poison_charge", "fatigue_charge",
                          "warp_charge", "trap_amplifier", "reinforced_casing"]),
]
# where each group's recipe plates go: after this plate
ANCHOR = {"Tetrium": "machines_single", "Illyrium Crucible": "machine_illyrium_crucible", "Illyrium Refinery": "machine_illyrium_refinery", "Illyrium": "machine_illyrium_refinery",
          "Magnetic Centrifuge": "machine_magnetic_centrifuge", "Shatter Coins": "machine_coin_factory", "Wave Plasma": "cable_colours", "Batteries": "cable_colours", "Decoys and traps": "raiding"}
NO_RECIPE = [("shatter_totem", "given on your first join"), ("tetrium_nugget", "Tetrium Crucible"), ("tetrium_slag", "Tetrium Crucible"), ("tetrium_dust", "crushing"),
             ("dirty_illyrium_ingot", "Illyrium Crucible"), ("pure_illyrium_dust", "Illyrium Refinery"), ("illyrium_nugget", "Illyrium Crucible"), ("magnatite_nugget", "Magnetic Centrifuge"),
             ("carbon_dust", "Magnetic Centrifuge"), ("charged_magnatite_ingot", "Magnetic Centrifuge"), ("charged_resonance_crystal", "Magnetic Centrifuge"),
             ("copper_shatter_coin", "Shatter Coin Factory"), ("illyrium_shatter_coin", "Shatter Coin Factory"), ("raw_tetrium", "mined")]


def ingredient(o):
    if isinstance(o, list):
        o = o[0]
    if "tag" in o:
        tag = o["tag"]
        if tag.endswith("_cables"):
            return "bsp_core:" + tag.split(":")[1].replace("_cables", "_core_cable")
        return {"bsp_core:illyrium_ores": "bsp_core:illyrium_ore", "bsp_core:tetrium_ores": "bsp_core:tetrium_ore", "bsp_core:magnatite_ores": "bsp_core:magnatite_ore"}.get(tag)
    return o.get("item")


def card(out, n, cells, how):
    """One recipe card: nine slots, a tailed arrow, the result. cells is a list of nine item ids or None."""
    slots = "".join(f'<i>{icon(c, 28) if c else ""}</i>' for c in cells)
    return (f'<figure class="rc"><figcaption>{html.escape(name_of(full(out)))}</figcaption><div class="rrow"><div class="g3">{slots}</div>{arrow("", 26, 12)}{icon(out, 40, n)}</div>'
            f'<small>{html.escape(how)}</small></figure>')


def recipe_card(r):
    kind = r["type"].split(":")[1]
    result = r.get("result")
    out, n = (result, 1) if isinstance(result, str) else (result["item"], result.get("count", 1))
    if kind == "crafting_shaped":
        rows = [row.ljust(3) for row in r["pattern"]] + ["   "] * (3 - len(r["pattern"]))
        cells = [ingredient(r["key"][ch]) if ch in r["key"] else None for row in rows for ch in row[:3]]
        how = "Crafting"
    elif kind == "crafting_shapeless":
        ings = [ingredient(i) for i in r["ingredients"]]
        cells = [ings[k] if k < len(ings) else None for k in range(9)]
        how = "Crafting, any arrangement"
    else:
        cells = [None] * 4 + [ingredient(r["ingredient"])] + [None] * 4
        how = "Furnace" if kind == "smelting" else "Blast Furnace"
    return out, card(out, n, cells, how)


def recipe_cards():
    """(group, result id, card html) in progression order; every recipe file must be placed in a group."""
    files = {}
    for f in sorted(glob.glob(str(DATA / "recipes/*.json"))):
        r = json.loads(open(f).read())
        if r["type"].split(":")[0] != "minecraft" or r["type"].split(":")[1] == "blasting":
            continue
        res = r["result"] if isinstance(r["result"], str) else r["result"]["item"]
        if is_dyed_cable(res.split(":")[1]) and res.split(":")[1] != "blue_illyrium_core_cable":
            continue  # one dye recipe stands for all forty-eight
        files[os.path.basename(f)[:-5]] = r
    hand = {"tetrium_dust": ("tetrium_ingot", "Crafting grid, 1 in 3; or a crusher"), "dirty_illyrium_dust": ("dirty_illyrium_ingot", "Crafting grid, 1 in 4; or a crusher")}
    cards, seen = [], set()
    for group, names in GROUPS:
        for name in names:
            if name.startswith("hand:"):
                out = name[5:]
                src, how = hand[out]
                cards.append((group, out, card(out, 1, [None] * 3 + [src, "minecraft:iron_pickaxe", None] + [None] * 3, how)))
                continue
            out, c = recipe_card(files[name])
            cards.append((group, out, c))
            seen.add(name)
    missing = sorted(set(files) - seen)
    assert not missing, f"recipes not placed in a group: {missing}"
    return cards


def recipe_plates():
    """Chunks of twelve cards; each chunk goes after the anchor plate of its last card's group. Returns {anchor slug: [plate tuples]}."""
    cards = recipe_cards()
    per = 12
    chunks = [cards[i:i + per] for i in range(0, len(cards), per)]
    placed = {}
    for i, chunk in enumerate(chunks):
        groups = list(dict.fromkeys(g for g, _, _ in chunk))
        title = f"Recipes {i + 1} of {len(chunks)}: {', '.join(groups)}"
        body = '<div class="rcs">' + "".join(c for _, _, c in chunk) + "</div>"
        if len(chunk) < per:  # the last plate: fill the space with what has no recipe
            rows = "".join(f'<li>{icon(i, 22)} <b>{html.escape(name_of(full(i)))}</b> {html.escape(how)}</li>' for i, how in NO_RECIPE)
            body = ('<div class="rcs">' + "".join(c for _, _, c in chunk) + "</div>"
                    + f'<div class="norecipe">{h3("Made elsewhere: these have no crafting recipe")}<ul class="legend four">{rows}</ul></div>')
        note = "A small number on the result is how many one craft makes. JEI and EMI show all of these in game, with every machine's jobs."
        PLATES.append(("", "", ""))  # placeholder so plate() can be reused for the html
        PLATES.pop()
        slug = f"recipes_{i + 1:02d}"
        body_html = f'<header><h2>{html.escape(title)}</h2><span>BSP-Core</span></header><div class="body">{body}</div><footer>{note}</footer>'
        placed.setdefault(ANCHOR[groups[-1]], []).append((slug, title, body_html))
    return placed


CSS = """
:root{ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --tq:#19d3b0; --slot:#0c0e12; color-scheme:dark; }
@media (prefers-color-scheme: light){ :root:not([data-theme="dark"]){ --bg:#f3f1ea; } }
:root[data-theme="light"]{ --bg:#f3f1ea; }
*{ box-sizing:border-box; } body{ background:var(--bg); margin:0; font:13px/1.4 "IBM Plex Sans",system-ui,sans-serif; color:#e8eaf0; }
.wrap{ display:flex; flex-direction:column; gap:20px; align-items:center; padding:20px 16px 40px; } .intro{ max-width:800px; color:#9aa3b5; margin:0; } .intro h1{ font:700 24px Silkscreen,monospace; color:#e8eaf0; margin:0 0 6px; }
.scroll{ max-width:100%; overflow-x:auto; }
.plate{ width:800px; height:600px; background:#12151b; border:1px solid #2a2f3a; display:flex; flex-direction:column; overflow:hidden; flex:none; }
.plate header{ display:flex; justify-content:space-between; align-items:baseline; padding:12px 18px 8px; border-bottom:2px solid #19d3b0; } .plate h2{ font:700 17px Silkscreen,monospace; margin:0; color:#19d3b0; } .plate header span{ font:400 11px Silkscreen,monospace; color:#9aa3b5; }
.plate .body{ flex:1; padding:10px 14px; overflow:hidden; display:flex; flex-direction:column; gap:6px; justify-content:space-evenly; } .plate footer{ padding:8px 18px; border-top:1px solid #2a2f3a; color:#9aa3b5; font-size:12px; }
.plate h3{ font:400 12px Silkscreen,monospace; margin:0; color:#ffd23a; } .plate p{ margin:0; color:#c9ced8; } .hint{ color:#9aa3b5 !important; font-size:12px; display:flex; gap:6px; align-items:center; } .hint.big{ font-size:12.5px; color:#c9ced8 !important; gap:12px; } .hint.big b{ color:#e8eaf0; }
.split{ display:flex; gap:18px; flex:1; min-height:0; } .col{ flex:1; display:flex; flex-direction:column; gap:8px; min-width:0; justify-content:space-evenly; }
.shot{ width:100%; aspect-ratio:8/5; object-fit:cover; border:1px solid #2a2f3a; display:block; } .shot.missing{ display:grid; place-items:center; color:#9aa3b5; background:#0c0e12; }
.ic{ position:relative; display:inline-grid; place-items:center; flex:none; vertical-align:middle; } .ic img{ image-rendering:pixelated; display:block; } .ic b{ position:absolute; right:-4px; bottom:-5px; font:700 11px Silkscreen,monospace; color:#fff; text-shadow:0 0 3px #000,0 0 3px #000; }
.tile{ display:grid; place-items:center; border:2px solid #8a909c; color:#8a909c; background:#0c0e12; font-family:Silkscreen,monospace; }
.facts{ margin:0; padding:0 0 0 16px; display:flex; flex-direction:column; gap:9px; font-size:12.5px; color:#c9ced8; } .facts b{ color:#e8eaf0; }
.paths{ display:grid; grid-template-columns:1.6fr 2fr 2.2fr 2.2fr 2fr; gap:0 10px; flex:1; } .path{ display:flex; flex-direction:column; align-items:stretch; gap:2px; } .path h3{ margin-bottom:4px; }
.pw{ background:#171a21; border:1px solid #2a2f3a; border-left:3px solid #19d3b0; padding:4px 7px; display:flex; flex-direction:column; } .pw b{ font-size:12px; color:#e8eaf0; } .pw span{ font-size:11px; color:#9aa3b5; line-height:1.2; }
.varr{ display:block; margin:0 auto; }
.layers{ display:flex; gap:12px; flex-wrap:wrap; } .layers figure{ margin:0; display:flex; flex-direction:column; gap:4px; align-items:center; } .layers figcaption{ font-size:11px; color:#9aa3b5; max-width:130px; text-align:center; }
.grid{ background:#0c0e12; border:1px solid #2a2f3a; padding:3px; display:flex; flex-direction:column; gap:2px; } .grid > div{ display:flex; gap:2px; }
.legend{ list-style:none; margin:0; padding:0; display:grid; grid-template-columns:1fr 1fr; gap:4px 10px; font-size:12px; } .legend li{ display:flex; gap:6px; align-items:center; } .legend.four{ grid-template-columns:repeat(4,1fr); font-size:11.5px; gap:3px 8px; }
.cells{ display:grid; gap:6px; } .swatch{ display:block; height:16px; border:1px solid #2a2f3a; }
.rcs{ display:grid; grid-template-columns:repeat(4,1fr); gap:6px; } .rc .arr{ flex:none; min-width:0; padding:0; } .rc .arr svg{ width:26px; } .rc{ margin:0; background:#171a21; border:1px solid #2a2f3a; padding:6px 6px 5px; display:flex; flex-direction:column; gap:4px; align-items:center; }
.rc figcaption{ font-size:11.5px; font-weight:600; color:#e8eaf0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; max-width:100%; } .rc small{ font-size:10.5px; color:#9aa3b5; }
.rrow{ display:flex; align-items:center; gap:4px; } .g3{ display:grid; grid-template-columns:repeat(3,32px); gap:2px; } .g3 i{ width:32px; height:32px; background:#0c0e12; border:1px solid #2a2f3a; display:grid; place-items:center; }
.jobs{ display:flex; gap:10px; justify-content:space-between; } .jobs .chain{ flex:1; } .jobs .arr{ min-width:40px; } .norecipe{ background:#171a21; border:1px solid #2a2f3a; padding:8px 10px; display:flex; flex-direction:column; gap:6px; }
.node{ display:inline-flex; flex-direction:column; align-items:center; gap:3px; width:88px; text-align:center; flex:none; } .node em{ font-style:normal; font-size:11.5px; line-height:1.15; color:#e8eaf0; }
.chain{ display:flex; align-items:flex-start; gap:2px; flex-wrap:nowrap; } .plus{ color:#9aa3b5; font-size:18px; width:14px; text-align:center; padding-top:12px; flex:none; }
.arr{ display:inline-flex; flex-direction:column; align-items:center; justify-content:flex-end; flex:1; min-width:46px; padding-top:4px; } .arr em{ font-style:normal; font-size:10.5px; line-height:1.15; color:#19d3b0; text-align:center; min-height:13px; padding:0 2px 2px; }
.arr svg{ display:block; width:100%; max-width:120px; height:12px; } .tier{ display:flex; flex-direction:column; gap:2px; border-left:3px solid #19d3b0; padding-left:10px; }
.t{ border-collapse:collapse; font-size:12px; } .t th,.t td{ text-align:left; padding:3px 12px 3px 0; border-bottom:1px solid #2a2f3a; white-space:nowrap; } .t th{ color:#9aa3b5; font-weight:500; }
body.one{ overflow:hidden; background:#12151b; } body.one .wrap{ padding:0; gap:0; } body.one .intro{ display:none; } body.one .plate{ display:none; border:0; width:100vw; height:100vh; } body.one .plate.show{ display:flex; }
"""


URL = "https://raw.githubusercontent.com/Mrgregles/BSP-Core/main/docs/curseforge/images/"


def description(plates):
    """docs/curseforge/DESCRIPTION_WITH_IMAGES.md from DESCRIPTION.md: '<!-- plate: slug -->' becomes that plate's picture and
    '<!-- recipes: slug -->' the recipe plates that follow it. The markers are invisible on the plain page."""
    by_slug = {p["slug"]: p for p in plates}
    out, used = [], set()
    for line in (ROOT / "docs/curseforge/DESCRIPTION.md").read_text().splitlines():
        m = line.strip()
        if m.startswith("<!-- plate: ") and m.endswith(" -->"):
            wanted = [by_slug[m[12:-4]]]
        elif m.startswith("<!-- recipes: ") and m.endswith(" -->"):
            wanted = [p for p in plates if p.get("after") == m[14:-4]]
        else:
            out.append(line)
            continue
        for p in wanted:
            out.append(f'![{p["title"]}]({URL}{p["n"]:02d}_{p["slug"]}.jpg)')
            used.add(p["slug"])
    unused = [p["slug"] for p in plates if p["slug"] not in used]
    assert not unused, f"plates not placed in DESCRIPTION.md: {unused}"
    (ROOT / "docs/curseforge/DESCRIPTION_WITH_IMAGES.md").write_text("\n".join(out) + "\n")


def main():
    totem_plates()
    progression()
    metals()
    machines()
    plasma()
    extras()
    # the metals' build plates sit next to their flow plates: reorder into the reading order, then slot the recipe plates in
    order = ["totem", "totem_powers", "progression", "ores", "tetrium", "machines_single", "illyrium", "machine_illyrium_crucible", "machine_illyrium_refinery", "magnatite",
             "machine_magnetic_centrifuge", "coins", "machine_coin_factory", "plasma_network", "plasma_blocks", "plasma_tank_injector", "batteries", "cable_colours", "raiding", "base_blocks"]
    by_slug = {s: (s, t, b) for s, t, b in PLATES}
    assert set(by_slug) == set(order), set(by_slug) ^ set(order)
    recipes = recipe_plates()
    deck, after = [], {}
    for slug in order:
        deck.append(by_slug[slug])
        for r in recipes.pop(slug, []):
            deck.append(r)
            after[r[0]] = slug
    assert not recipes, recipes.keys()
    PLATES[:] = deck
    figures = "".join(f'<div class="scroll"><section class="plate" id="plate-{i + 1}" data-slug="{slug}" aria-label="{html.escape(title)}">{body}</section></div>' for i, (slug, title, body) in enumerate(PLATES))
    page = f"""<title>BSP-Core Reference Sheet</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Silkscreen:wght@400;700&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>{CSS}</style>
<div class="wrap"><div class="intro"><h1>BSP-Core Reference Sheet</h1>{len(PLATES)} plates in the order a player meets things: the totem and its powers, the three metals and their machines, coins, Wave Plasma, decoys and admin blocks, with every recipe where it is first needed. Each plate is 800 x 600; add ?plate=N to show one.</div>
{figures}</div>
<script>
(function(){{ const n=new URLSearchParams(location.search).get('plate'); if(!n) return; document.body.classList.add('one'); const p=document.getElementById('plate-'+n); if(p) p.classList.add('show'); }})();
</script>
"""
    assert all(ord(c) < 128 for c in page), "page must be ASCII"
    (ROOT / "tools/preview/curseforge_sheet.html").write_text(page)
    plates = [{"n": i + 1, "slug": s, "title": t, **({"after": after[s]} if s in after else {})} for i, (s, t, _) in enumerate(PLATES)]
    (IMAGES / "plates.json").write_text(json.dumps(plates, indent=1))
    description(plates)
    print("sheet written:", len(PLATES), "plates,", len(page) // 1024, "KB")
    for i, (s, t, _) in enumerate(PLATES):
        print(f"  {i + 1:02d} {s:30} {t}")


if __name__ == "__main__":
    main()
