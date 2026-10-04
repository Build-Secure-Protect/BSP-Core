#!/usr/bin/env python3
"""Recipe plan for BSP-Core: one source of truth for the proposed crafting tree.

  python3 tools/gen_recipe_plan.py

Writes tools/preview/recipe_plan.html: what exists today, the new component items, every
proposed recipe as a 3x3 grid, flow charts of how components combine, and a bill of raw materials
per machine. Nothing here touches the mod; once the plan is approved the same tables can be used
to write the real recipe JSON.
"""
import base64
import html
import math
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/bsp_core/textures"

# id: (name, kind, abbreviation, texture or None). kind: v = vanilla, b = BSP today, n = proposed new item
ITEMS = {
    # vanilla
    "iron_ingot": ("Iron Ingot", "v", "Fe"), "copper_ingot": ("Copper Ingot", "v", "Cu"), "gold_ingot": ("Gold Ingot", "v", "Au"),
    "redstone": ("Redstone Dust", "v", "Rs"), "redstone_block": ("Block of Redstone", "v", "RB"), "diamond": ("Diamond", "v", "Di"),
    "glass": ("Glass", "v", "Gl"), "glass_pane": ("Glass Pane", "v", "GP"), "hopper": ("Hopper", "v", "Hp"), "bucket": ("Bucket", "v", "Bk"),
    "piston": ("Piston", "v", "Pi"), "magma_block": ("Magma Block", "v", "Mg"), "blaze_powder": ("Blaze Powder", "v", "Bz"),
    "lapis": ("Lapis Lazuli", "v", "La"), "amethyst_shard": ("Amethyst Shard", "v", "Am"), "dried_kelp": ("Dried Kelp", "v", "Kp"),
    # BSP materials that exist today
    "tetrium_ingot": ("Tetrium Ingot", "b", "TI", "item/tetrium_ingot.png"), "tetrium_nugget": ("Tetrium Nugget", "b", "TN", "item/tetrium_nugget.png"),
    "tetrium_dust": ("Tetrium Dust", "b", "TD", "item/tetrium_dust.png"), "tetrium_slag": ("Tetrium Slag", "b", "Sl", "item/tetrium_slag.png"),
    "dirty_illyrium_ingot": ("Dirty Illyrium Ingot", "b", "DI", "item/dirty_illyrium_ingot.png"),
    "dirty_illyrium_dust": ("Dirty Illyrium Dust", "b", "DD", "item/dirty_illyrium_dust.png"),
    "pure_illyrium_dust": ("Pure Illyrium Dust", "b", "PD", "item/pure_illyrium_dust.png"),
    "illyrium_nugget": ("Illyrium Nugget", "b", "IN", "item/illyrium_nugget.png"), "illyrium_ore": ("Illyrium Ore (any)", "b", "IO"),
    "illyrium_ingot": ("Illyrium Ingot", "b", "II", "item/illyrium_ingot.png"),
    "rf_upgrade_mk1": ("RF Upgrade Mk I", "b", "R1", "item/rf_upgrade_mk1.png"), "rf_upgrade_mk2": ("RF Upgrade Mk II", "b", "R2", "item/rf_upgrade_mk2.png"),
    "rf_upgrade_mk3": ("RF Upgrade Mk III", "b", "R3", "item/rf_upgrade_mk3.png"), "illyrium_forge_upgrade": ("Illyrium Forge Upgrade", "b", "FU", "item/illyrium_forge_upgrade.png"),
    # BSP blocks that need recipes
    "illyrium_casing": ("Illyrium Casing", "b", "Ca", "block/illyrium_casing.png"), "illyrium_glass": ("Illyrium Tank Glass", "b", "TG", "block/illyrium_glass.png"),
    "illyrium_core": ("Illyrium Core", "b", "Co", "block/illyrium_core.png"), "lava_pylon": ("Lava Pylon", "b", "LP", "block/lava_pylon.png"),
    "refinery_pump": ("Refinery Pump", "b", "RP", "block/refinery_pump.png"), "item_hatch": ("Item Hatch", "b", "IH", "block/item_hatch.png"),
    "illyrium_crucible": ("Illyrium Crucible Controller", "b", "CC", "block/illyrium_crucible_front.png"),
    "illyrium_refinery": ("Illyrium Refinery Controller", "b", "RC", "block/illyrium_refinery_front.png"),
    "shatter_coin_factory": ("Shatter Coin Factory Controller", "b", "FC", "block/shatter_coin_factory_front.png"),
    "factory_frame": ("Factory Frame", "b", "FF", "block/factory_frame.png"), "factory_press": ("Factory Press", "b", "FP", "block/factory_press.png"),
    "factory_blank_hatch": ("Factory Blank Hatch", "b", "BH", "block/factory_blank_hatch.png"),
    "factory_power_port": ("Factory Power Port", "b", "PP", "block/factory_power_port.png"), "factory_motivator": ("Motivator", "b", "Mo"),
    # proposed new component items
    "slag_brick": ("Slag Brick", "n", "SB"), "tetrium_plate": ("Tetrium Plate", "n", "TP"), "machine_chassis": ("Machine Chassis", "n", "MC"),
    "tetrium_coil": ("Tetrium Coil", "n", "TC"), "drive_motor": ("Drive Motor", "n", "DM"), "circuit_substrate": ("Circuit Substrate", "n", "CS"),
    "basic_control_circuit": ("Basic Control Circuit", "n", "BC"), "crucible_control_circuit": ("Crucible Control Circuit", "n", "CrC"),
    "refinery_control_circuit": ("Refinery Control Circuit", "n", "ReC"), "mint_control_circuit": ("Mint Control Circuit", "n", "MiC"),
    "thermal_lining": ("Thermal Lining", "n", "TL"), "conveyor_belt": ("Conveyor Belt", "n", "CB"), "press_die": ("Press Die", "n", "PD"),
    "resonance_crystal": ("Resonance Crystal", "n", "RX"), "illyrium_processor": ("Illyrium Processor", "n", "IP"),
}

# output: (count, rows, key, how). Rows use one character per cell; space = empty.
R = {
    # --- shared components
    "slag_brick": (1, ["S"], {"S": "tetrium_slag"}, "Furnace"),
    "tetrium_plate": (1, ["T"], {"T": "tetrium_ingot"}, "Combination Forge"),
    "machine_chassis": (2, ["PBP", "B B", "PBP"], {"P": "tetrium_plate", "B": "slag_brick"}, "Crafting"),
    "tetrium_coil": (2, ["NNN", "NCN", "NNN"], {"N": "tetrium_nugget", "C": "copper_ingot"}, "Crafting"),
    "drive_motor": (1, [" W ", "WIW", " R "], {"W": "tetrium_coil", "I": "iron_ingot", "R": "redstone"}, "Crafting"),
    "circuit_substrate": (3, ["NNN", "BBB"], {"N": "tetrium_nugget", "B": "slag_brick"}, "Crafting"),
    "basic_control_circuit": (1, [" R ", "CSC", " R "], {"R": "redstone", "C": "copper_ingot", "S": "circuit_substrate"}, "Crafting"),
    # --- Illyrium Crucible and Refinery
    "thermal_lining": (4, ["BBB", "MMM", "BBB"], {"B": "slag_brick", "M": "magma_block"}, "Crafting"),
    "crucible_control_circuit": (1, ["ZDZ", "DKD", "ZDZ"], {"Z": "blaze_powder", "D": "tetrium_dust", "K": "basic_control_circuit"}, "Crafting"),
    "refinery_control_circuit": (1, ["LDL", "DKD", "LDL"], {"L": "lapis", "D": "dirty_illyrium_dust", "K": "basic_control_circuit"}, "Crafting"),
    "illyrium_casing": (4, [" P ", "PCP", " P "], {"P": "tetrium_plate", "C": "machine_chassis"}, "Crafting"),
    "illyrium_glass": (4, ["NGN", "G G", "NGN"], {"N": "tetrium_nugget", "G": "glass"}, "Crafting"),
    "illyrium_core": (1, ["PDP", "DOD", "PDP"], {"P": "tetrium_plate", "D": "diamond", "O": "illyrium_ore"}, "Crafting"),
    "lava_pylon": (2, ["PLP", "LCL", "PLP"], {"P": "tetrium_plate", "L": "thermal_lining", "C": "machine_chassis"}, "Crafting"),
    "item_hatch": (1, [" H ", "PCP", " P "], {"H": "hopper", "P": "tetrium_plate", "C": "machine_chassis"}, "Crafting"),
    "refinery_pump": (1, ["PMP", "BCB", "PPP"], {"P": "tetrium_plate", "M": "drive_motor", "B": "bucket", "C": "machine_chassis"}, "Crafting"),
    "illyrium_crucible": (1, ["PGP", "LKL", "PCP"], {"P": "tetrium_plate", "G": "glass_pane", "L": "thermal_lining", "K": "crucible_control_circuit", "C": "machine_chassis"}, "Crafting"),
    "illyrium_refinery": (1, ["PGP", "MKM", "PCP"], {"P": "tetrium_plate", "G": "glass_pane", "M": "drive_motor", "K": "refinery_control_circuit", "C": "machine_chassis"}, "Crafting"),
    # --- Shatter Coin Factory
    "mint_control_circuit": (1, ["ADA", "DKD", "ADA"], {"A": "gold_ingot", "D": "tetrium_dust", "K": "basic_control_circuit"}, "Crafting"),
    "conveyor_belt": (3, ["KKK", "NMN"], {"K": "dried_kelp", "N": "tetrium_nugget", "M": "drive_motor"}, "Crafting"),
    "press_die": (1, [" P ", "IDI", " P "], {"P": "tetrium_plate", "I": "dirty_illyrium_ingot", "D": "diamond"}, "Crafting"),
    "factory_frame": (2, ["PGP", "VCV", "PPP"], {"P": "tetrium_plate", "G": "glass", "V": "conveyor_belt", "C": "machine_chassis"}, "Crafting"),
    "factory_press": (1, ["PXP", "MCM", "PEP"], {"P": "tetrium_plate", "X": "piston", "M": "drive_motor", "C": "machine_chassis", "E": "press_die"}, "Crafting"),
    "factory_blank_hatch": (1, [" H ", "VCV", " P "], {"H": "hopper", "V": "conveyor_belt", "C": "machine_chassis", "P": "tetrium_plate"}, "Crafting"),
    "factory_power_port": (1, ["PWP", "WCW", "PRP"], {"P": "tetrium_plate", "W": "tetrium_coil", "C": "machine_chassis", "R": "redstone_block"}, "Crafting"),
    "shatter_coin_factory": (1, ["PGP", "WKW", "PCP"], {"P": "tetrium_plate", "G": "glass_pane", "W": "tetrium_coil", "K": "mint_control_circuit", "C": "machine_chassis"}, "Crafting"),
    # --- Motivator
    "resonance_crystal": (1, [" A ", "AUA", " A "], {"A": "amethyst_shard", "U": "pure_illyrium_dust"}, "Crafting"),
    "illyrium_processor": (1, ["NRN", "RKR", "NRN"], {"N": "illyrium_nugget", "R": "redstone", "K": "basic_control_circuit"}, "Crafting"),
    "factory_motivator": (1, [" Y ", "WZW", "PPP"], {"Y": "resonance_crystal", "W": "tetrium_coil", "Z": "illyrium_processor", "P": "tetrium_plate"}, "Crafting"),
    # --- existing items, reworked to use the new parts
    "rf_upgrade_mk1": (1, ["CWC", "WRW", "CTC"], {"C": "copper_ingot", "W": "tetrium_coil", "R": "redstone_block", "T": "tetrium_ingot"}, "Crafting"),
    "rf_upgrade_mk2": (1, ["TGT", "WRW", "TGT"], {"T": "tetrium_ingot", "G": "gold_ingot", "W": "tetrium_coil", "R": "redstone_block"}, "Crafting"),
    "rf_upgrade_mk3": (1, ["TIT", "WRW", "TIT"], {"T": "tetrium_ingot", "I": "illyrium_nugget", "W": "tetrium_coil", "R": "redstone_block"}, "Crafting"),
    "illyrium_forge_upgrade": (1, ["III", "IZI", "IFI"], {"I": "illyrium_ingot", "Z": "illyrium_processor", "F": "iron_ingot"}, "Crafting"),
}

SHARED = ["slag_brick", "tetrium_plate", "machine_chassis", "tetrium_coil", "drive_motor", "circuit_substrate", "basic_control_circuit"]
GROUPS = [
    ("Shared components", "Seven parts every machine block is built from. Slag finally has a use: it becomes the brick that hardens plates, chassis and circuit boards.",
     SHARED, SHARED),
    ("Illyrium Crucible", "Needs the Nether for Blaze Powder and Magma Blocks, which suits a lava machine. Nothing here needs Illyrium, so it can be built with Tetrium alone.",
     ["thermal_lining", "crucible_control_circuit", "illyrium_casing", "illyrium_core", "lava_pylon", "item_hatch", "illyrium_crucible"],
     ["illyrium_casing", "illyrium_core", "lava_pylon", "item_hatch", "illyrium_crucible"]),
    ("Illyrium Refinery", "Reuses Casing, Core and Item Hatch. Its circuit needs Dirty Illyrium Dust, so the Crucible has to come first.",
     ["refinery_control_circuit", "illyrium_glass", "refinery_pump", "illyrium_refinery"], ["illyrium_glass", "refinery_pump", "illyrium_refinery"]),
    ("Shatter Coin Factory slice", "Mid tier: Tetrium parts plus a Press Die that needs Dirty Illyrium Ingots, so the factory comes after the Illyrium Crucible.",
     ["mint_control_circuit", "conveyor_belt", "press_die", "factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port", "shatter_coin_factory"],
     ["factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port", "shatter_coin_factory"]),
    ("Motivator", "Top tier: the only part that needs refined Illyrium, so speeding a factory up is the reward for finishing the whole chain.",
     ["resonance_crystal", "illyrium_processor", "factory_motivator"], ["factory_motivator"]),
    ("Reworked existing recipes", "The RF Upgrades now use Tetrium Coils and the Illyrium Forge Upgrade an Illyrium Processor.",
     ["rf_upgrade_mk1", "rf_upgrade_mk2", "rf_upgrade_mk3", "illyrium_forge_upgrade"], ["rf_upgrade_mk1", "rf_upgrade_mk2", "rf_upgrade_mk3", "illyrium_forge_upgrade"]),
]
BUILDS = [
    ("Illyrium Crucible (27 blocks)", {"illyrium_casing": 12, "lava_pylon": 8, "item_hatch": 2, "illyrium_core": 1, "illyrium_crucible": 1}),
    ("Illyrium Refinery (14 blocks)", {"illyrium_casing": 8, "illyrium_core": 1, "item_hatch": 1, "illyrium_glass": 2, "refinery_pump": 1, "illyrium_refinery": 1}),
    ("One factory slice (6 blocks)", {"factory_frame": 2, "factory_press": 1, "factory_blank_hatch": 1, "factory_power_port": 1, "shatter_coin_factory": 1}),
    ("One Motivator", {"factory_motivator": 1}),
]

# what exists today: (name, type, how you get it, status). status: ok = crafting recipe, mach = made in a machine, world = found or given, none = no recipe yet
TODAY = [
    ("Shatter Totem", "Item / block", "Given once on first join", "world"), ("Totem Compass", "Item", "Crafting: Tetrium Ingots around an Illyrium Ingot", "ok"),
    ("Tetrium Ore, Deepslate Tetrium Ore", "Blocks", "World generation", "world"), ("Illyrium Ore, Deepslate and End Stone variants", "Blocks", "World generation", "world"),
    ("Tetrium Nugget", "Item", "Tetrium Crucible; or 1 ingot = 9 nuggets", "mach"), ("Tetrium Slag", "Item", "Crucible by-product. No use yet", "mach"),
    ("Tetrium Ingot", "Item", "Combination Forge; or 9 nuggets", "mach"), ("Tetrium Dust", "Item", "Crushing an ingot (by hand or in a crusher)", "mach"),
    ("Crushed Tetrium / Crushed Dirty Illyrium Ingot", "Items", "Result of crushing by hand; resolves into dust or nuggets", "mach"),
    ("Dirty Illyrium Ingot", "Item", "Illyrium Crucible; or 9 Dirty Illyrium Nuggets", "mach"), ("Dirty Illyrium Nugget", "Item", "Failed hand crushing", "mach"),
    ("Dirty Illyrium Dust", "Item", "Crushing a Dirty Illyrium Ingot", "mach"), ("Pure Illyrium Dust", "Item", "Illyrium Refinery", "mach"),
    ("Illyrium Nugget", "Item", "Illyrium Crucible (alloying); or 1 ingot = 9", "mach"), ("Illyrium Ingot", "Item", "Combination Forge with upgrade; or 9 nuggets", "mach"),
    ("Iron, Diamond, Netherite, Illyrium Filter", "Items", "Crafting: metal corners, cobweb cross", "ok"), ("Illyrium Forge Upgrade", "Item", "Crafting: Illyrium Ingots, redstone, iron", "ok"),
    ("RF Upgrade Mk I, II, III", "Items", "Crafting: copper / Tetrium / Illyrium with redstone", "ok"), ("Shatter Blank", "Item", "Crafting: 4 Tetrium Ingots make 2", "ok"),
    ("Copper, Gold, Diamond, Netherite, Illyrium Coin Blank", "Items", "Crafting: previous blank plus the tier's metal", "ok"),
    ("Copper to Illyrium Shatter Coin", "Items", "Shatter Coin Factory", "mach"),
    ("Tetrium Crucible", "Machine", "Crafting: bricks, furnace, iron", "ok"), ("Combination Forge", "Machine", "Crafting: Tetrium Ingots, pistons, anvil, smooth stone", "ok"),
    ("Illyrium Crucible Controller", "Multiblock", "Creative only", "none"), ("Illyrium Refinery Controller", "Multiblock", "Creative only", "none"),
    ("Illyrium Casing", "Multiblock", "Creative only", "none"), ("Illyrium Tank Glass", "Multiblock", "Creative only", "none"), ("Illyrium Core", "Multiblock", "Creative only", "none"),
    ("Lava Pylon", "Multiblock", "Creative only", "none"), ("Refinery Pump", "Multiblock", "Creative only", "none"), ("Item Hatch", "Multiblock", "Creative only", "none"),
    ("Shatter Coin Factory Controller", "Multiblock", "Creative only", "none"), ("Factory Frame", "Multiblock", "Creative only", "none"), ("Factory Press", "Multiblock", "Creative only", "none"),
    ("Factory Blank Hatch", "Multiblock", "Creative only", "none"), ("Factory Power Port", "Multiblock", "Creative only", "none"), ("Motivator", "Multiblock", "Creative only", "none"),
]
NEW_NOTES = {
    "slag_brick": "Smelt Tetrium Slag. Heat-proof filler, like Mekanism's steel casing starts from plain steel.",
    "tetrium_plate": "A Tetrium Ingot pressed flat in the Combination Forge. The outer skin of every machine block.",
    "machine_chassis": "The empty shell every machine block is built around, the same idea as Mekanism's Steel Casing.",
    "tetrium_coil": "Wound nuggets on a copper core. Anything that carries or moves power.",
    "drive_motor": "Coils around an iron rotor. Anything that moves: pumps, belts, the press.",
    "circuit_substrate": "Blank board, like AE2's printed silicon before it becomes a processor.",
    "basic_control_circuit": "The base circuit. Each machine upgrades it into its own control circuit, like Mekanism's circuit tiers.",
    "crucible_control_circuit": "Basic circuit tuned for heat with Blaze Powder and Tetrium Dust.",
    "refinery_control_circuit": "Basic circuit tuned for filtering with Lapis and Dirty Illyrium Dust.",
    "mint_control_circuit": "Basic circuit tuned for coin pressing with Gold and Tetrium Dust.",
    "thermal_lining": "Slag Brick and Magma. Lines the Lava Pylons and the Crucible controller.",
    "conveyor_belt": "Kelp belt on a Drive Motor. Used in Factory Frames and the Blank Hatch.",
    "press_die": "Diamond-faced die on Dirty Illyrium. The stamping face of the Factory Press.",
    "resonance_crystal": "Amethyst charged with Pure Illyrium Dust. The glowing core of a Motivator.",
    "illyrium_processor": "Basic circuit with Illyrium Nuggets, like AE2's top-tier processors.",
}


for _i, _v in list(ITEMS.items()):
    if _v[1] == "n" and (TEX / f"item/{_i}.png").exists():
        ITEMS[_i] = _v + (f"item/{_i}.png",)


def mc_id(i):
    if i == "illyrium_ore":
        return {"tag": "bsp_core:illyrium_ores"}
    return {"item": ("minecraft:" + {"lapis": "lapis_lazuli"}.get(i, i)) if ITEMS[i][1] == "v" else "bsp_core:" + i}


def write_recipes():
    """Writes the real recipe files from the table above."""
    import json
    out = ROOT / "src/main/resources/data/bsp_core/recipes"
    n = 0
    for item, (count, rows, key, how) in R.items():
        if how == "Crafting":
            result = {"item": "bsp_core:" + item}
            if count > 1:
                result["count"] = count
            (out / f"{item}.json").write_text(json.dumps({"type": "minecraft:crafting_shaped", "pattern": rows,
                                                          "key": {k: mc_id(v) for k, v in key.items()}, "result": result}, indent=2))
            n += 1
        elif how == "Furnace":
            src = mc_id(next(iter(key.values())))
            for kind, time in (("smelting", 200), ("blasting", 100)):
                (out / f"{item}_from_{kind}.json").write_text(json.dumps({"type": "minecraft:" + kind, "ingredient": src,
                                                                          "result": "bsp_core:" + item, "experience": 0.1, "cookingtime": time}, indent=2))
                n += 1
    print("recipe files written:", n)


def uri(rel):
    return "data:image/png;base64," + base64.b64encode((TEX / rel).read_bytes()).decode()


def name(i):
    return ITEMS[i][0]


def cell(i):
    if not i:
        return '<span class="c"></span>'
    it = ITEMS[i]
    inner = f'<img alt="" src="{uri(it[3])}">' if len(it) > 3 else html.escape(it[2])
    return f'<span class="c k{it[1]}" title="{html.escape(it[0])}">{inner}</span>'


def card(out):
    count, rows, key, how = R[out]
    grid = [list(r.center(3) if len(r) < 3 else r) for r in rows]
    while len(grid) < 3:
        grid.append([" "] * 3)
    cells = "".join(cell(key.get(ch)) for row in grid for ch in row)
    need = defaultdict(int)
    for row in rows:
        for ch in row:
            if ch in key:
                need[key[ch]] += 1
    legend = "".join(f"<li><b>{n}</b> {html.escape(name(i))}</li>" for i, n in need.items())
    tag = '<span class="new">new</span>' if ITEMS[out][1] == "n" else ""
    return (f'<article class="rc"><h4>{html.escape(name(out))} {tag}</h4><div class="rr"><div class="g">{cells}</div><span class="ar">&gt;</span>'
            f'<div class="o">{cell(out)}<b>x{count}</b></div></div><p class="how">{how}</p><ul>{legend}</ul></article>')


def flow(recipes, finals):
    made = set(recipes)
    lines, nodes = ["flowchart LR"], {}
    for out in recipes:
        need = defaultdict(int)
        for row in R[out][1]:
            for ch in row:
                if ch in R[out][2]:
                    need[R[out][2][ch]] += 1
        for i, n in need.items():
            nodes[i] = None
            lines.append(f"  {i} -->|{n}| {out}")
        nodes[out] = None
    for i in nodes:
        label = name(i) + (f" x{R[i][0]}" if i in made and R[i][0] > 1 else "")
        cls = "fin" if i in finals else "new" if i in made else "shr" if i in R else "bsp" if ITEMS[i][1] == "b" else "van"
        lines.append(f'  {i}["{label}"]:::{cls}')
    lines += ["  classDef van fill:#3a3f4b,stroke:#8a909c,color:#ffffff", "  classDef bsp fill:#0f5c4f,stroke:#19d3b0,color:#ffffff",
              "  classDef shr fill:#1f3f7a,stroke:#3a8bff,color:#ffffff", "  classDef new fill:#4a3378,stroke:#b58cff,color:#ffffff",
              "  classDef fin fill:#8a4a12,stroke:#ff8a3a,color:#ffffff"]
    return "\n".join(lines)


def depth(i, seen=()):
    if i not in R or i in seen:
        return 0
    return 1 + max(depth(x, seen + (i,)) for x in R[i][2].values())


def bill(want):
    need = defaultdict(float, want)
    for i in sorted(R, key=depth, reverse=True):
        if need.get(i, 0) <= 0:
            continue
        crafts = math.ceil(need[i] / R[i][0])
        for row in R[i][1]:
            for ch in row:
                if ch in R[i][2]:
                    need[R[i][2][ch]] += crafts
    return {i: int(n) for i, n in need.items() if i not in R and n > 0}


def main():
    status = {"ok": ("Has a recipe", "s-ok"), "mach": ("Made in a machine", "s-mach"), "world": ("Found or given", "s-world"), "none": ("No recipe", "s-none")}
    rows = "".join(f'<tr><td>{html.escape(n)}</td><td>{t}</td><td>{html.escape(h)}</td><td><span class="st {status[s][1]}">{status[s][0]}</span></td></tr>' for n, t, h, s in TODAY)
    newrows = "".join(f'<tr><td>{cell(i)}</td><td><b>{html.escape(name(i))}</b></td><td>{html.escape(NEW_NOTES[i])}</td></tr>' for i in NEW_NOTES)
    sections = ""
    for title, blurb, recipes, finals in GROUPS:
        sections += (f'<section><h2>{html.escape(title)}</h2><p class="lede">{html.escape(blurb)}</p><div class="scroll"><pre class="mermaid">{html.escape(flow(recipes, finals))}</pre></div>'
                     f'<div class="cards">{"".join(card(o) for o in recipes)}</div></section>')
    raw = sorted({i for _, w in BUILDS for i in bill(w)}, key=lambda i: (ITEMS[i][1], name(i)))
    bills = [bill(w) for _, w in BUILDS]
    head = "".join(f"<th>{html.escape(t)}</th>" for t, _ in BUILDS)
    body = "".join(f"<tr><td>{html.escape(name(i))}</td>" + "".join(f"<td class='num'>{b.get(i, '')}</td>" for b in bills) + "</tr>" for i in raw)
    none = sum(1 for t in TODAY if t[3] == "none")
    page = TEMPLATE.replace("__ROWS__", rows).replace("__NEW__", newrows).replace("__SECTIONS__", sections).replace("__BHEAD__", head).replace("__BBODY__", body) \
        .replace("__NONE__", str(none)).replace("__NEWN__", str(len(NEW_NOTES))).replace("__RECN__", str(len(R)))
    out = ROOT / "tools/preview/recipe_plan.html"
    out.write_text(page)
    assert all(ord(c) < 128 for c in page)
    print("wrote", out, len(page), "bytes;", len(R), "recipes")


TEMPLATE = """<title>BSP-Core Recipe Plan</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Silkscreen:wght@400;700&family=IBM+Plex+Sans:wght@400;500;600&display=swap">
<style>
/* Layout: dark board like the other BSP concept pages. Summary, what exists today (table), proposed parts (table),
   then one section per build: a flow chart above its recipe cards. Bill of materials and open decisions close the page. */
:root{ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --tq:#19d3b0; --van:#8a909c; --bsp:#19d3b0; --new:#b58cff; --ok:#19d3b0; --mach:#3a8bff; --world:#9aa3b5; --none:#ff6b5c; --slot:#0c0e12; --on:#0b0d11;
  --display:"Silkscreen","Courier New",monospace; --body:"IBM Plex Sans",system-ui,-apple-system,"Segoe UI",sans-serif; color-scheme:dark; }
@media (prefers-color-scheme: light){ :root:not([data-theme="dark"]){ --bg:#f3f1ea; --panel:#fff; --line:#d9d4c7; --fg:#1a1b20; --muted:#5f6472; --tq:#0c8f78; --van:#6b7080; --bsp:#0c8f78; --new:#6b45c4; --ok:#0c8f78; --mach:#1f63c8; --world:#5f6472; --none:#c0392b; --slot:#e9e6dc; --on:#fff; color-scheme:light; } }
:root[data-theme="light"]{ --bg:#f3f1ea; --panel:#fff; --line:#d9d4c7; --fg:#1a1b20; --muted:#5f6472; --tq:#0c8f78; --van:#6b7080; --bsp:#0c8f78; --new:#6b45c4; --ok:#0c8f78; --mach:#1f63c8; --world:#5f6472; --none:#c0392b; --slot:#e9e6dc; --on:#fff; color-scheme:light; }
body{ background:var(--bg); color:var(--fg); font-family:var(--body); margin:0; font-size:14px; line-height:1.5; }
.wrap{ max-width:1180px; margin:0 auto; padding-block:28px 56px; padding-inline:16px; display:flex; flex-direction:column; gap:26px; }
section{ display:flex; flex-direction:column; gap:12px; min-width:0; }
h1{ font-family:var(--display); font-size:clamp(20px,3.4vw,30px); margin:0; text-wrap:balance; }
h2{ font-family:var(--display); font-size:17px; margin:0; color:var(--tq); text-wrap:balance; }
h4{ font:600 13.5px var(--body); margin:0; }
.lede{ color:var(--muted); margin:0; max-width:75ch; }
.facts{ display:flex; flex-wrap:wrap; gap:10px 28px; margin:0; padding:0; list-style:none; }
.facts b{ font-family:var(--display); font-size:20px; color:var(--tq); margin-right:6px; }
.key{ display:flex; flex-wrap:wrap; gap:8px 18px; font-size:12.5px; color:var(--muted); }
.key i{ display:inline-block; width:10px; height:10px; margin-right:6px; vertical-align:-1px; border:2px solid var(--c); }
.scroll{ overflow-x:auto; background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:10px; }
pre.mermaid{ margin:0; min-width:720px; font-size:12px; }
table{ border-collapse:collapse; width:100%; min-width:560px; }
th,td{ text-align:left; padding:6px 12px 6px 0; border-bottom:1px solid var(--line); vertical-align:middle; }
th{ font:600 11px var(--body); letter-spacing:.08em; text-transform:uppercase; color:var(--muted); }
td.num{ font-variant-numeric:tabular-nums; }
.st{ font-size:12px; padding:2px 8px; border:1px solid var(--c); color:var(--c); border-radius:3px; white-space:nowrap; }
.s-ok{ --c:var(--ok); } .s-mach{ --c:var(--mach); } .s-world{ --c:var(--world); } .s-none{ --c:var(--none); }
.cards{ display:grid; grid-template-columns:repeat(auto-fill,minmax(230px,1fr)); gap:12px; }
.rc{ background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:12px; display:flex; flex-direction:column; gap:8px; min-width:0; }
.rr{ display:flex; align-items:center; gap:10px; }
.g{ display:grid; grid-template-columns:repeat(3,34px); gap:2px; }
.c{ width:34px; height:34px; box-sizing:border-box; background:var(--slot); border:2px solid var(--line); display:grid; place-items:center; font:700 11px var(--body); }
.c img{ width:26px; height:26px; image-rendering:pixelated; }
.kv{ border-color:var(--van); color:var(--van); } .kb{ border-color:var(--bsp); color:var(--bsp); } .kn{ border-color:var(--new); color:var(--new); }
.ar{ font-family:var(--display); color:var(--muted); } .o{ display:flex; align-items:center; gap:6px; } .o b{ font-family:var(--display); font-size:12px; }
.how{ margin:0; font-size:11px; letter-spacing:.08em; text-transform:uppercase; color:var(--muted); }
.rc ul{ margin:0; padding:0; list-style:none; font-size:12.5px; color:var(--muted); } .rc li b{ color:var(--fg); display:inline-block; min-width:1.4em; }
.new{ font:600 10px var(--body); letter-spacing:.08em; text-transform:uppercase; color:var(--on); background:var(--new); padding:1px 5px; border-radius:3px; vertical-align:1px; }
ol.dec{ margin:0; padding-left:20px; max-width:80ch; } ol.dec li{ margin-bottom:6px; }
</style>
<div class="wrap">
<section>
<h1>BSP-Core Recipe Plan</h1>
<p class="lede">What the mod had before this plan, and the component items and recipes that now make everything craftable. These items and recipes are now in the mod. Hover a grid cell for the item name.</p>
<ul class="facts"><li><b>__NONE__</b>blocks had no recipe</li><li><b>__NEWN__</b>new component items</li><li><b>__RECN__</b>recipes added or reworked</li></ul>
<div class="key"><span><i style="--c:var(--van)"></i>Vanilla item</span><span><i style="--c:var(--bsp)"></i>BSP-Core item that exists today</span><span><i style="--c:var(--new)"></i>New component item</span></div>
</section>
<section><h2>Before this plan</h2><div class="scroll"><table><tr><th>Item or block</th><th>Type</th><th>How you got it</th><th>Status</th></tr>__ROWS__</table></div></section>
<section><h2>Component items</h2><p class="lede">Fifteen parts, in the spirit of Mekanism's casing, alloys and circuit tiers and AE2's printed circuits and processors. Seven are shared by every machine; the rest are specialised.</p>
<div class="scroll"><table><tr><th></th><th>Item</th><th>What it is</th></tr>__NEW__</table></div></section>
__SECTIONS__
<section><h2>Bill of raw materials</h2><p class="lede">Everything broken down to items you mine, smelt or make in the existing machines, rounding batch recipes up.</p>
<div class="scroll"><table><tr><th>Raw item</th>__BHEAD__</tr>__BBODY__</table></div></section>
<section><h2>Decisions taken</h2><ol class="dec">
<li><b>Tiering.</b> Crucible and Refinery need only Tetrium. The factory needs Dirty Illyrium Ingots. The Motivator needs refined Illyrium. Approved: the factory is an end-game machine.</li>
<li><b>Nether gate.</b> The Illyrium Crucible needs Blaze Powder and Magma Blocks. Kept.</li>
<li><b>Cost.</b> Approved as shown in the bill of materials.</li>
<li><b>Existing recipes.</b> RF Upgrades use Tetrium Coils and the Illyrium Forge Upgrade uses an Illyrium Processor.</li>
<li><b>Plates.</b> Pressed in the Combination Forge: one Tetrium Ingot makes one Tetrium Plate.</li>
</ol></section>
</div>
"""

if __name__ == "__main__":
    main()
    write_recipes()
