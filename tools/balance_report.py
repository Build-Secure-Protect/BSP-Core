#!/usr/bin/env python3
"""Balance report for BSP-Core: what every craftable costs and how long it takes.

  python3 tools/balance_report.py            # writes docs/BALANCE.md and docs/BALANCE.json
  python3 tools/balance_report.py --item illyrium_crucible   # one item, full breakdown on stdout

Sources (nothing is typed in twice):
  - machine times and yields: parsed from src/main/java/com/mrgregles/bsp_core/BSPConfig.java (the defaults),
  - recipes: every JSON under src/main/resources/data/bsp_core/recipes/ (crusher recipes from compat/),
  - ore density: src/main/resources/data/bsp_core/worldgen/ (vein count and size per chunk).

The model, in one paragraph. A craftable is expanded through its crafting recipes down to the machine steps
(Tetrium Crucible, Combination Forge, crusher, furnace, Illyrium Crucible, Illyrium Refinery, Magnetic
Centrifuge) and then to ore blocks and vanilla items. Ingots are hand-crafted from nuggets (0 s) because that
recipe exists; the Combination Forge only appears for plates. The Tetrium Crucible and the Centrifuge make two
things per run (nuggets + slag, nuggets + carbon dust), so their runs are max(demand A / yield A, demand B /
yield B) and the surplus is listed. Each item gets: raw ore blocks, machine-seconds per machine, the
dependency latency of one unit through the chain, and wall time at a machine count (bottleneck load / count,
or the latency if that is longer). Ore gathering is a separate column: ore blocks / mining rate, with Fortune III.
"""
import argparse
import json
import math
import re
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CONFIG = ROOT / "src/main/java/com/mrgregles/bsp_core/BSPConfig.java"
RECIPES = ROOT / "src/main/resources/data/bsp_core/recipes"
TAGS = ROOT / "src/main/resources/data/bsp_core/tags/items"
WORLDGEN = ROOT / "src/main/resources/data/bsp_core/worldgen"
OUT_MD = ROOT / "docs/BALANCE.md"
OUT_JSON = ROOT / "docs/BALANCE.json"

# ----------------------------------------------------------------------------------------------- assumptions
# Everything a player does that the mod does not time. Change here, rerun.
ASSUME = {
    "crusher_seconds": 10.0,        # Mekanism Crusher 200 ticks base; Thermal Pulverizer 4000 RF at 20 RF/t; Create wheels ~ the same
    "furnace_seconds": 10.0,        # slag brick by smelting (200 ticks); blasting would be 5 s
    "fortune_multiplier": 2.2,      # Fortune III on raw drops (ore_drops formula: average of 1,1,2,3,4)
    # ore blocks a focused player mines per hour, before Fortune. Tetrium is as dense as vanilla iron in the
    # y 16 band (count 10, size 9), Illyrium about a quarter of vanilla diamond, Magnatite about 0.7 diamond.
    "ore_per_hour_hand": {"tetrium": 60.0, "illyrium": 6.0, "magnatite": 18.0},
    "ore_per_hour_machine": {"tetrium": 360.0, "illyrium": 36.0, "magnatite": 108.0},  # Digital Miner / quarry: x6
}

# Machine counts per scenario, and whether the forge runs on RF (x2) and which RF upgrade is fitted (0-3).
SCENARIOS = {
    "early": {"desc": "first hours: one of each, coal forge, no RF, hand mining",
              "machines": {"Tetrium Crucible": 1, "Combination Forge": 1, "Crusher": 1, "Furnace": 1,
                           "Illyrium Crucible": 1, "Illyrium Refinery": 1, "Magnetic Centrifuge": 1},
              "rf_mark": 0, "forge_rf": False, "centrifuge_layers": 1, "mining": "hand"},
    "mid": {"desc": "mid game: 2 Tetrium Crucibles, 2 forges on RF with Mk I, 1 of each multiblock (centrifuge 2 layers), 1 crusher, Digital Miner",
            "machines": {"Tetrium Crucible": 2, "Combination Forge": 2, "Crusher": 1, "Furnace": 2,
                         "Illyrium Crucible": 1, "Illyrium Refinery": 1, "Magnetic Centrifuge": 1},
            "rf_mark": 1, "forge_rf": True, "centrifuge_layers": 2, "mining": "machine"},
    "late": {"desc": "late game: 4 Tetrium Crucibles, 2 forges Mk III, 2 Illyrium Crucibles, 2 Refineries, centrifuge 6 layers, 2 crushers, Digital Miner",
             "machines": {"Tetrium Crucible": 4, "Combination Forge": 2, "Crusher": 2, "Furnace": 4,
                          "Illyrium Crucible": 2, "Illyrium Refinery": 2, "Magnetic Centrifuge": 1},
             "rf_mark": 3, "forge_rf": True, "centrifuge_layers": 6, "mining": "machine"},
}

# Multiblock and set builds, as block counts. The report prices each as one line.
BUILDS = {
    "Illyrium Crucible (27 blocks)": {"illyrium_casing": 12, "lava_pylon": 8, "item_hatch": 2, "illyrium_core": 1, "illyrium_crucible": 1},
    "Illyrium Refinery (14 blocks)": {"illyrium_casing": 8, "illyrium_core": 1, "item_hatch": 1, "illyrium_glass": 2, "refinery_pump": 1, "illyrium_refinery": 1},
    "Magnetic Centrifuge, 1 layer (9 blocks)": {"centrifuge_casing": 5, "centrifuge_rotor": 1, "item_hatch": 1, "centrifuge_power_port": 1, "magnetic_centrifuge": 1},
    "Centrifuge extra layer (9 blocks)": {"centrifuge_casing": 8, "centrifuge_rotor": 1},
    "Coin Factory slice (6 blocks)": {"factory_frame": 2, "factory_press": 1, "factory_blank_hatch": 1, "factory_power_port": 1, "shatter_coin_factory": 1},
    "Plasma hook-up (extractor, interface, 8 cables, base, projector)": {"plasma_extractor": 1, "plasma_interface": 1, "tetrium_core_cable": 8, "projector_base": 1, "totem_projector": 1},
    "Plasma Tank 3x3x3 (20 casing, 5 glass, 1 port)": {"tank_casing": 20, "tank_glass": 5, "tank_port": 1},
    "Battery set (charger + Battery I)": {"battery_charger": 1, "plasma_battery_1": 1},
    "Decoy (totem + power base)": {"decoy_totem": 1, "decoy_power_base": 1},
    "Coin Vault 3x3x3 (27 blocks)": {"coin_vault": 27},
    "Tier V gate coins (4 Copper, 4 Gold, 4 Diamond, 4 Netherite blanks)": {"copper_coin_blank": 4, "gold_coin_blank": 4, "diamond_coin_blank": 4, "netherite_coin_blank": 4},
}

# Timeline milestones: name -> (items, note). Items are {id: count}; builds may be referenced by name.
MILESTONES = [
    ("Tetrium Crucible", {"tetrium_crucible": 1}, "vanilla only: bricks, furnace, iron"),
    ("Combination Forge", {"combination_forge": 1}, "3 hand-made Tetrium Ingots"),
    ("Machine parts for one controller-grade block", {"machine_chassis": 1, "basic_control_circuit": 1, "drive_motor": 1, "tetrium_coil": 2}, "the shared components"),
    ("RF Upgrade Mk I", {"rf_upgrade_mk1": 1}, ""),
    ("Coin Vault (one block)", {"coin_vault": 1}, ""),
    ("Illyrium Crucible", "Illyrium Crucible (27 blocks)", "the big Tetrium sink"),
    ("First Dirty Illyrium Ingot", {"dirty_illyrium_ingot": 1}, ""),
    ("Diamond Filter", {"diamond_filter": 1}, "the Iron Filter has one use, so this is the real first filter"),
    ("Illyrium Refinery", "Illyrium Refinery (14 blocks)", ""),
    ("First Illyrium Ingot", {"illyrium_ingot": 1}, "9 nuggets: smelt, crush, refine, alloy"),
    ("Magnetic Centrifuge", "Magnetic Centrifuge, 1 layer (9 blocks)", "opens Magnatite"),
    ("Copper Tetrium Coil", {"copper_tetrium_coil": 1}, "needs Carbon Dust and a Magnatite Ingot"),
    ("Illyrium Forge Upgrade", {"illyrium_forge_upgrade": 1}, "7 Illyrium Ingots + processor"),
    ("Coin Factory slice", "Coin Factory slice (6 blocks)", ""),
    ("Motivator", {"factory_motivator": 1}, ""),
    ("Plasma hook-up", "Plasma hook-up (extractor, interface, 8 cables, base, projector)", "first projector"),
    ("Battery Charger + Battery I", "Battery set (charger + Battery I)", "needs a Charged Resonance Crystal"),
    ("Plasma Tank 3x3x3", "Plasma Tank 3x3x3 (20 casing, 5 glass, 1 port)", ""),
    ("Decoy Totem + Power Base", "Decoy (totem + power base)", "Magnet Core"),
    ("Tier V gate blanks", "Tier V gate coins (4 Copper, 4 Gold, 4 Diamond, 4 Netherite blanks)", "then 12 h, 24 h, 48 h, 96 h real time per coin in the factory"),
]

ORES = {"bsp_core:raw_tetrium": "tetrium", "bsp_core:raw_illyrium": "illyrium", "bsp_core:raw_magnatite": "magnatite"}


# ----------------------------------------------------------------------------------------------- config parse
def load_config():
    """Reads the defaults out of BSPConfig.java: {section.key: value}. Lists become Python lists."""
    text = CONFIG.read_text()
    out = {}
    stack = []
    # walk the builder calls in source order
    for m in re.finditer(r'\.push\("([a-z_]+)"\)|\.pop\((\d*)\)|\.(defineInRange|defineList|define)\("([A-Za-z]+)",\s*(.*?)(?:,\s*BSPConfig::|,\s*o\s*->|\);)', text, re.S):
        if m.group(1):
            stack.append(m.group(1))
        elif m.group(2) is not None and m.group(0).startswith(".pop"):
            n = int(m.group(2)) if m.group(2) else 1
            for _ in range(n):
                if stack:
                    stack.pop()
        else:
            kind, key, rest = m.group(3), m.group(4), m.group(5)
            rest = rest.strip()
            if kind == "defineList":
                lm = re.match(r"List\.of\((.*?)\)", rest, re.S)
                if not lm:
                    continue
                vals = [v.strip().replace("_", "") for v in lm.group(1).split(",") if v.strip()]
                try:
                    val = [float(v) if "." in v else int(v) for v in vals]
                except ValueError:
                    val = [v.strip('"') for v in vals]
            else:
                first = rest.split(",")[0].strip().replace("_", "")
                try:
                    val = float(eval(first)) if any(c in first for c in "./") else int(first)
                except Exception:
                    val = first.strip('"')
            out[".".join(stack + [key])] = val
    return out


# ----------------------------------------------------------------------------------------------- recipes
def load_tags():
    tags = {}
    for f in TAGS.glob("*.json"):
        tags["bsp_core:" + f.stem] = [v if isinstance(v, str) else v["id"] for v in json.loads(f.read_text())["values"]]
    return tags


def ingredient_id(ing, tags):
    if isinstance(ing, list):
        ing = ing[0]
    if "item" in ing:
        return ing["item"]
    tag = ing["tag"]
    vals = tags.get(tag, [])
    if not vals:
        return "#" + tag
    # ores: the raw item is what the player mines; cables: the plain one
    for v in vals:
        if v.startswith("bsp_core:raw_"):
            return v
    return vals[0]


def load_recipes(tags):
    """{item: [(inputs {id: count}, out_count, kind)]} for crafting, furnace and crusher recipes."""
    recipes = defaultdict(list)
    for f in sorted(RECIPES.rglob("*.json")):
        d = json.loads(f.read_text())
        t = d["type"]
        inputs = defaultdict(float)
        if t == "minecraft:crafting_shaped":
            for row in d["pattern"]:
                for c in row:
                    if c != " ":
                        inputs[ingredient_id(d["key"][c], tags)] += 1
            res = d["result"]
            recipes[res["item"]].append((dict(inputs), res.get("count", 1), "craft", f.stem))
        elif t == "minecraft:crafting_shapeless":
            for ing in d["ingredients"]:
                inputs[ingredient_id(ing, tags)] += 1
            res = d["result"]
            recipes[res["item"]].append((dict(inputs), res.get("count", 1), "craft", f.stem))
        elif t in ("minecraft:smelting", "minecraft:blasting"):
            inputs[ingredient_id(d["ingredient"], tags)] += 1
            recipes[d["result"]].append((dict(inputs), 1, "furnace" if t.endswith("smelting") else "blasting", f.stem))
        elif t in ("mekanism:crushing",):
            inputs[d["input"]["ingredient"]["item"]] += 1
            recipes[d["output"]["item"]].append((dict(inputs), 1, "crusher", f.stem))
        # create/thermal duplicates of the Mekanism crusher recipes are skipped; pickaxe_crushing is the fallback
    return recipes


# ----------------------------------------------------------------------------------------------- the model
class Model:
    def __init__(self, cfg, recipes, scenario):
        self.cfg = cfg
        self.recipes = recipes
        self.sc = scenario
        c = cfg
        rf = c["rf_upgrade.bonus"][scenario["rf_mark"] - 1] if scenario["rf_mark"] else 0.0
        self.rf = rf
        speed = 1.0 + rf
        plasma = scenario.get("plasma_speed", 1.0)   # a plasma feed speeds the multiblocks (task 2 of the 1.0 list)
        forge_speed = (c["combination_forge.rfSpeedMultiplier"] * speed) if scenario["forge_rf"] else 1.0
        layers = scenario["centrifuge_layers"]
        L = layers - 1
        nug_avg = (c["centrifuge.nuggetsMin"][L] + c["centrifuge.nuggetsMax"][L]) / 2
        full = layers >= 6
        # machine processes: item -> (machine, inputs per unit, seconds per unit, extras)
        self.proc = {
            "bsp_core:tetrium_plate": ("Combination Forge", {"bsp_core:tetrium_ingot": 1}, c["combination_forge.ticksPerIngot"] / 20 / forge_speed, {}),
            "bsp_core:dirty_illyrium_ingot": ("Illyrium Crucible", {"bsp_core:raw_illyrium": c["illyrium_crucible.smeltOre"], "bsp_core:tetrium_slag": c["illyrium_crucible.smeltSlag"]},
                                              c["illyrium_crucible.smeltTicks"] / 20 / speed / plasma, {"lava_mb": c["illyrium_crucible.lavaPerJob"] * (1 - rf)}),
            "bsp_core:illyrium_nugget": ("Illyrium Crucible", {"bsp_core:pure_illyrium_dust": c["illyrium_crucible.alloyPureDust"] / c["illyrium_crucible.alloyNuggets"],
                                                               "bsp_core:tetrium_dust": c["illyrium_crucible.alloyTetriumDust"] / c["illyrium_crucible.alloyNuggets"]},
                                         c["illyrium_crucible.alloyTicks"] / 20 / speed / plasma / c["illyrium_crucible.alloyNuggets"], {"lava_mb": c["illyrium_crucible.lavaPerJob"] * (1 - rf) / c["illyrium_crucible.alloyNuggets"]}),
            "bsp_core:pure_illyrium_dust": ("Illyrium Refinery", {"bsp_core:dirty_illyrium_dust": 1}, c["refinery.ticksPerDust"] / 20 / speed / plasma,
                                            {"water_mb": c["refinery.waterPerDust"], "filter_uses": 1}),
            "bsp_core:charged_magnatite_ingot": ("Magnetic Centrifuge", {"bsp_core:magnatite_ingot": 1},
                                                 (c["centrifuge.chargeTicksFullStack"] if full else c["centrifuge.chargeTicks"]) / 20 * c["centrifuge.chargeOdds"][L] / plasma,
                                                 {"rf": (c["centrifuge.chargeTicksFullStack"] if full else c["centrifuge.chargeTicks"]) * c["centrifuge.chargeOdds"][L] * c["centrifuge.rfPerTickCharging"] * layers,
                                                  "needs": "Copper Tetrium Coil fitted"}),
            "bsp_core:charged_resonance_crystal": ("Magnetic Centrifuge", {"bsp_core:resonance_crystal": 1, "bsp_core:magnatite_nugget": 1},
                                                   (c["centrifuge.magnetiseTicksFullStack"] if full else c["centrifuge.magnetiseTicks"]) / 20 / plasma, {}),
        }
        # the two-output machines
        self.tcruc = {"machine": "Tetrium Crucible", "ore": "bsp_core:raw_tetrium", "seconds": c["tetrium_crucible.ticksPerOre"] / 20 / speed,
                      "yield": {"bsp_core:tetrium_nugget": c["tetrium_crucible.nuggetsPerOre"], "bsp_core:tetrium_slag": c["tetrium_crucible.slagPerOre"]}}
        self.cent = {"machine": "Magnetic Centrifuge", "ore": "bsp_core:raw_magnatite", "seconds": c["centrifuge.separateTicks"] / 20 / plasma,
                     "yield": {"bsp_core:magnatite_nugget": nug_avg, "bsp_core:carbon_dust": 1},
                     "rf_per_run": c["centrifuge.separateTicks"] * c["centrifuge.rfPerTickSeparating"] * layers}
        self.pair_items = set(self.tcruc["yield"]) | set(self.cent["yield"])
        self.step_seconds = {"craft": 0.0, "furnace": ASSUME["furnace_seconds"], "blasting": ASSUME["furnace_seconds"] / 2, "crusher": ASSUME["crusher_seconds"]}
        self.step_machine = {"furnace": "Furnace", "blasting": "Furnace", "crusher": "Crusher"}
        self._lat = {}

    def pick(self, item, stack):
        """The recipe used for an item: a machine process first, else the cheapest crafting route that does not loop."""
        if item in self.proc:
            return None
        opts = [r for r in self.recipes.get(item, []) if not any(i in stack for i in r[0])]
        if not opts:
            return None
        # prefer hand crafting (ingots from nuggets), then furnace smelting, then crusher; never the "nuggets from ingot" uncraft
        opts = [o for o in opts if not o[3].endswith("_from_ingot")] or opts
        order = {"craft": 0, "furnace": 1, "blasting": 2, "crusher": 3}
        opts.sort(key=lambda o: order[o[2]])
        return opts[0]

    def expand(self, item, n, acc, stack=()):
        """Adds n of item to acc: machine seconds, ore, vanilla items, fluids, surplus."""
        if item in ORES:
            acc["ore"][item] += n
            return
        if item in self.pair_items:
            acc["pair"][item] += n
            return
        if item in self.proc:
            machine, inputs, secs, extra = self.proc[item]
            acc["machine"][machine] += secs * n
            acc["count"][item] += n
            for k, v in extra.items():
                if isinstance(v, (int, float)):
                    acc["extra"][k] += v * n
            for i, q in inputs.items():
                self.expand(i, q * n, acc, stack + (item,))
            return
        r = self.pick(item, stack + (item,))
        if r is None:
            acc["base"][item] += n
            return
        inputs, out, kind, _ = r
        runs = n / out
        if kind != "craft":
            acc["machine"][self.step_machine[kind]] += self.step_seconds[kind] * runs
            acc["count"][item] += n
        else:
            acc["crafts"] += runs
        for i, q in inputs.items():
            self.expand(i, q * runs, acc, stack + (item,))

    def latency(self, item, stack=()):
        """Seconds for ONE unit to come out of the chain with every machine free (the dependency path)."""
        if item in self._lat:
            return self._lat[item]
        if item in ORES or item in stack:
            return 0.0
        if item in self.tcruc["yield"]:
            return self.tcruc["seconds"]
        if item in self.cent["yield"]:
            return self.cent["seconds"]
        if item in self.proc:
            machine, inputs, secs, _ = self.proc[item]
            val = secs + max((self.latency(i, stack + (item,)) for i in inputs), default=0.0)
        else:
            r = self.pick(item, stack + (item,))
            if r is None:
                return 0.0
            inputs, out, kind, _ = r
            val = self.step_seconds[kind] + max((self.latency(i, stack + (item,)) for i in inputs), default=0.0)
        self._lat[item] = val
        return val

    def bill(self, items):
        acc = {"ore": defaultdict(float), "pair": defaultdict(float), "machine": defaultdict(float), "count": defaultdict(float),
               "extra": defaultdict(float), "base": defaultdict(float), "crafts": 0.0}
        for item, n in items.items():
            self.expand("bsp_core:" + item if ":" not in item else item, n, acc)
        # resolve the two-output machines
        surplus = {}
        for spec in (self.tcruc, self.cent):
            runs = max((acc["pair"][k] / y for k, y in spec["yield"].items()), default=0.0)
            if runs > 0:
                runs = math.ceil(runs - 1e-9)
                acc["machine"][spec["machine"]] += runs * spec["seconds"]
                acc["ore"][spec["ore"]] += runs
                if "rf_per_run" in spec:
                    acc["extra"]["rf"] += spec["rf_per_run"] * runs
                for k, y in spec["yield"].items():
                    s = runs * y - acc["pair"][k]
                    if s > 0.01:
                        surplus[k] = s
        acc["surplus"] = surplus
        acc["latency"] = max((self.latency("bsp_core:" + i if ":" not in i else i) for i in items), default=0.0)
        return acc

    def wall(self, acc):
        """Wall seconds with the scenario's machine counts: the busiest machine's load / its count, or the latency if longer."""
        counts = self.sc["machines"]
        loads = {m: s / counts.get(m, 1) for m, s in acc["machine"].items()}
        bottleneck = max(loads.items(), key=lambda kv: kv[1], default=("", 0.0))
        return max(bottleneck[1], acc["latency"]), bottleneck[0]

    def mining_hours(self, acc):
        rates = ASSUME["ore_per_hour_hand" if self.sc["mining"] == "hand" else "ore_per_hour_machine"]
        hours = 0.0
        for ore, n in acc["ore"].items():
            blocks = n / ASSUME["fortune_multiplier"]
            hours += blocks / rates[ORES[ore]]
        return hours


# ----------------------------------------------------------------------------------------------- the upgrade tree
def full_tree_coins(cfg):
    """Coins of each tier to buy every upgrade to its top level. Mirrors TotemUpgrades.price(): the enum and the maxLevel switch are parsed from the Java."""
    src = (ROOT / "src/main/java/com/mrgregles/bsp_core/totem/TotemUpgrades.java").read_text()
    max_tier, per_tier = 4, 2
    special = {}
    for m in re.finditer(r"case ([A-Z_, ]+) -> (\d+);", src):
        for nm in m.group(1).split(","):
            special[nm.strip()] = int(m.group(2))
    coins = {"CARRIED": cfg["upgrades.costs.carriedLevelCoins"], "BASE": cfg["upgrades.costs.baseLevelCoins"], "RAID": cfg["upgrades.costs.raidLevelCoins"]}
    out = defaultdict(int)
    names = ["copper", "gold", "diamond", "netherite", "illyrium"]
    for m in re.finditer(r'^\s+([A-Z_]+)\("[a-z_]+", Branch\.([A-Z]+), (\d), (?:null|"[a-z_]+"), \d, (true|false)\)', src, re.M):
        name, branch, tier, single = m.group(1), m.group(2), int(m.group(3)), m.group(4) == "true"
        levels = special.get(name, 1 if single else per_tier * (max_tier + 1 - tier))
        for level in range(levels):
            t = min(max_tier, tier if single else tier + level // per_tier)
            step = 0 if single else level % per_tier
            out[names[t]] += coins[branch][min(step, len(coins[branch]) - 1)]
    return dict(out)


# ----------------------------------------------------------------------------------------------- report
def fmt_t(sec):
    if sec < 90:
        return f"{sec:.0f} s"
    if sec < 5400:
        return f"{sec / 60:.1f} min"
    return f"{sec / 3600:.1f} h"


def fmt_n(x):
    return f"{x:.0f}" if abs(x - round(x)) < 0.05 else f"{x:.1f}"


def short(i):
    return i.split(":", 1)[1] if i.startswith("bsp_core:") else i.replace("minecraft:", "")


def ore_density():
    """Max ore blocks per chunk from the worldgen files (count x size); the real mean is lower, the same share for every ore."""
    rows = []
    for pf in sorted((WORLDGEN / "placed_feature").glob("*.json")):
        p = json.loads(pf.read_text())
        cf = json.loads((WORLDGEN / "configured_feature" / (p["feature"].split(":")[1] + ".json")).read_text())
        count = next((x["count"] for x in p["placement"] if x["type"] == "minecraft:count"), 1)
        hr = next((x["height"] for x in p["placement"] if x["type"] == "minecraft:height_range"), None)
        y = f'{hr["min_inclusive"]["absolute"]}..{hr["max_inclusive"]["absolute"]}' if hr else "?"
        rows.append((pf.stem, count, cf["config"]["size"], cf["config"].get("discard_chance_on_air_exposure", 0), y, count * cf["config"]["size"]))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--item", help="print one item's breakdown (id without namespace) and exit")
    ap.add_argument("--scenario", default="mid", choices=SCENARIOS)
    ap.add_argument("--set", action="append", default=[], metavar="KEY=JSON", help="override a config default, e.g. illyrium_crucible.alloyTicks=1200 or factory.pressHours=[8,16,32,64,96]")
    ap.add_argument("--yield", dest="yields", action="append", default=[], metavar="ITEM=N", help="override how many an item's crafting recipe gives, e.g. lava_pylon=4")
    ap.add_argument("--mining", action="append", default=[], metavar="ORE=PER_HOUR", help="hand mining rate override (machine stays x6), e.g. illyrium=12")
    ap.add_argument("--plasma-speed", type=float, default=1.0, help="speed multiplier for the multiblocks (Illyrium Crucible, Refinery, Centrifuge) from a plasma feed, e.g. 1.5")
    ap.add_argument("--out", help="write the Markdown here instead of docs/BALANCE.md (JSON goes next to it); for trying proposals")
    ap.add_argument("--label", default="", help="a line printed under the title, e.g. the proposal being tried")
    args = ap.parse_args()

    cfg = load_config()
    overrides = []
    for kv in args.set:
        k, v = kv.split("=", 1)
        if k not in cfg:
            raise SystemExit(f"unknown config key {k}; known: {', '.join(sorted(cfg))}")
        cfg[k] = json.loads(v)
        overrides.append(f"`{k}` = {v}")
    for kv in args.mining:
        k, v = kv.split("=", 1)
        ASSUME["ore_per_hour_hand"][k] = float(v)
        ASSUME["ore_per_hour_machine"][k] = float(v) * 6
        overrides.append(f"hand mining {k} = {v} blocks/h")
    if args.plasma_speed != 1.0:
        overrides.append(f"plasma feed: multiblocks x{args.plasma_speed}")
    tags = load_tags()
    recipes = load_recipes(tags)
    for kv in args.yields:
        k, v = kv.split("=", 1)
        rid = "bsp_core:" + k
        if rid not in recipes:
            raise SystemExit(f"no recipe for {k}")
        recipes[rid] = [(r[0], int(v), r[2], r[3]) if r[2] == "craft" else r for r in recipes[rid]]
        overrides.append(f"recipe {k} gives {v}")
    for v in SCENARIOS.values():
        v["plasma_speed"] = args.plasma_speed
    models = {k: Model(cfg, recipes, v) for k, v in SCENARIOS.items()}
    global OUT_MD, OUT_JSON
    if args.out:
        OUT_MD = Path(args.out)
        OUT_JSON = OUT_MD.with_suffix(".json")

    if args.item:
        m = models[args.scenario]
        acc = m.bill({args.item: 1})
        w, b = m.wall(acc)
        print(json.dumps({"ore": dict(acc["ore"]), "machine_seconds": dict(acc["machine"]), "vanilla": dict(acc["base"]), "extra": dict(acc["extra"]),
                          "surplus": acc["surplus"], "latency_s": acc["latency"], "wall_s": w, "bottleneck": b, "mining_h": m.mining_hours(acc)}, indent=1, default=float))
        return

    craftables = sorted(i for i in recipes if i.startswith("bsp_core:")) + sorted(models["mid"].proc)
    craftables = [c for c in craftables if c not in ("bsp_core:tetrium_nuggets_from_ingot",)]
    machines = ["Tetrium Crucible", "Combination Forge", "Furnace", "Crusher", "Illyrium Crucible", "Illyrium Refinery", "Magnetic Centrifuge"]

    data = {"assumptions": ASSUME, "scenarios": {k: v["desc"] for k, v in SCENARIOS.items()}, "config": {k: v for k, v in cfg.items() if k.split(".")[0] in
            ("tetrium_crucible", "combination_forge", "illyrium_crucible", "refinery", "centrifuge", "rf_upgrade", "factory", "coins", "upgrades", "hand_crushing")},
            "items": {}, "builds": {}, "milestones": {}}

    md = []
    md.append("# BSP-Core balance report\n")
    md.append("Generated by `tools/balance_report.py` from the config defaults, the recipe JSON and the worldgen files. Do not edit; rerun the script.\n")
    if args.label:
        md.append(f"**{args.label}**\n")
    if overrides:
        md.append("Overrides in this run (not the files): " + "; ".join(overrides) + "\n")
    md.append("## How to read it\n")
    md.append("- **Ore** is ore blocks mined (raw drops / 2.2 for Fortune III). **Machine seconds** are per machine kind, summed over every step in the tree.")
    md.append("- **Latency** is one unit through the whole chain with every machine idle. **Wall** is the busiest machine's load divided by how many of it the scenario owns, or the latency if that is longer: the time the player waits once the ore is in hand.")
    md.append("- **Mining** is ore blocks divided by the scenario's mining rate. Mining and machines overlap in practice, so the player's time is about max(mining, wall), not the sum.")
    md.append("- Ingots are hand-crafted from nuggets (free). Dust comes from a crusher (10 s); hand crushing costs 2.3 Tetrium Ingots or 4.4 Dirty Illyrium Ingots per dust on average and is not modelled.")
    md.append("- Tetrium Crucible runs = max(nuggets/2, slag/1); surplus slag or nuggets are listed. The Magnetic Centrifuge likewise with nuggets and Carbon Dust.\n")

    md.append("## Machine numbers in force (config defaults)\n")
    md.append("| Step | Input | Output | Time | Notes |\n|---|---|---|---|---|")
    md.append(f"| Tetrium Crucible | 1 ore | {cfg['tetrium_crucible.nuggetsPerOre']} nuggets + {cfg['tetrium_crucible.slagPerOre']} slag | {cfg['tetrium_crucible.ticksPerOre'] / 20:.0f} s | furnace fuel |")
    md.append(f"| Combination Forge | 9 nuggets or 1 ingot | 1 ingot or 1 plate | {cfg['combination_forge.ticksPerIngot'] / 20:.0f} s on coal, {cfg['combination_forge.ticksPerIngot'] / 20 / cfg['combination_forge.rfSpeedMultiplier']:.0f} s on RF | RF needs an upgrade |")
    md.append(f"| Illyrium Crucible smelt | {cfg['illyrium_crucible.smeltOre']} Illyrium ore + {cfg['illyrium_crucible.smeltSlag']} slag | 1 Dirty Illyrium Ingot | {cfg['illyrium_crucible.smeltTicks'] / 20:.0f} s | {cfg['illyrium_crucible.lavaPerJob']} mB lava |")
    md.append(f"| Illyrium Crucible alloy | {cfg['illyrium_crucible.alloyPureDust']} pure dust + {cfg['illyrium_crucible.alloyTetriumDust']} Tetrium dust | {cfg['illyrium_crucible.alloyNuggets']} Illyrium nugget | {cfg['illyrium_crucible.alloyTicks'] / 20:.0f} s | {cfg['illyrium_crucible.lavaPerJob']} mB lava |")
    md.append(f"| Illyrium Refinery | 1 dirty dust + {cfg['refinery.waterPerDust']} mB water + 1 filter use | 1 pure dust | {cfg['refinery.ticksPerDust'] / 20:.0f} s | filters: {', '.join(str(x) for x in cfg['refinery.filterUses'])} uses |")
    md.append(f"| Crusher (other mod) | 1 ingot | 1 dust | {ASSUME['crusher_seconds']:.0f} s | assumption |")
    md.append(f"| Centrifuge separate | 1 Magnatite ore | {cfg['centrifuge.nuggetsMin'][0]}-{cfg['centrifuge.nuggetsMax'][0]} nuggets + 1 Carbon Dust (1 layer) | {cfg['centrifuge.separateTicks'] / 20:.0f} s | {cfg['centrifuge.rfPerTickSeparating']} RF/t per layer |")
    md.append(f"| Centrifuge charge | 1 Magnatite Ingot | Charged ingot, 1 in {cfg['centrifuge.chargeOdds'][0]} per try (1 layer) | {cfg['centrifuge.chargeTicks'] / 20:.0f} s per try | expected {cfg['centrifuge.chargeTicks'] / 20 * cfg['centrifuge.chargeOdds'][0]:.0f} s per ingot |")
    md.append(f"| Centrifuge magnetise | 1 Resonance Crystal + 1 Magnatite Nugget | 1 Charged Resonance Crystal | {cfg['centrifuge.magnetiseTicks'] / 20:.0f} s | |")
    md.append(f"| Coin Factory | 1 blank + RF | 1 coin | {', '.join(str(h) for h in cfg['factory.pressHours'])} real hours by tier | Motivators -{', -'.join(f'{int(r * 100)}%' for r in cfg['factory.motivatorTimeReduction'])} |")
    md.append(f"| RF Upgrade | | | Mk I/II/III +{'/'.join(f'{int(b * 100)}%' for b in cfg['rf_upgrade.bonus'])} speed | same share less lava |\n")

    md.append("## Ore generation (per chunk, from worldgen)\n")
    md.append("| Feature | Veins per chunk | Vein size | Discard on air | Y | Max blocks per chunk |\n|---|---|---|---|---|---|")
    for r in ore_density():
        md.append(f"| {r[0]} | {r[1]} | {r[2]} | {r[3]} | {r[4]} | {r[5]} |")
    md.append("\nVanilla for comparison (1.20.1): iron middle band count 10 size 9 (y -24..56), diamond count 7 size 4 discard 0.5 + buried count 4 size 8 + large 1 in 9 chunks size 12 (y -80..80 triangle). Tetrium matches iron's middle band; Illyrium is about a quarter of diamond; Magnatite about 0.7 diamond.\n")
    md.append(f"Mining rates assumed (ore blocks per hour before Fortune): hand {ASSUME['ore_per_hour_hand']}, machine {ASSUME['ore_per_hour_machine']}; Fortune III x{ASSUME['fortune_multiplier']}.\n")

    # ---- per-item table, one per scenario
    for sk, m in models.items():
        md.append(f"## Every craftable: scenario `{sk}`\n")
        md.append(f"{SCENARIOS[sk]['desc']}.\n")
        md.append("| Item | Tetrium ore | Illyrium ore | Magnatite ore | " + " | ".join(mn.replace("Illyrium ", "I. ").replace("Magnetic ", "") for mn in machines) + " | Total machine | Latency | Wall | Mining | Bottleneck | Vanilla and other |")
        md.append("|---|" + "---|" * (len(machines) + 9))
        for item in craftables:
            acc = m.bill({item: 1})
            w, b = m.wall(acc)
            total = sum(acc["machine"].values())
            base = ", ".join(f"{fmt_n(v)} {short(k)}" for k, v in sorted(acc["base"].items(), key=lambda kv: -kv[1]))
            extra = []
            if acc["extra"].get("lava_mb"):
                extra.append(f"{acc['extra']['lava_mb']:.0f} mB lava")
            if acc["extra"].get("water_mb"):
                extra.append(f"{acc['extra']['water_mb']:.0f} mB water")
            if acc["extra"].get("filter_uses"):
                extra.append(f"{acc['extra']['filter_uses']:.0f} filter uses")
            if acc["surplus"]:
                extra.append("surplus " + ", ".join(f"{fmt_n(v)} {short(k)}" for k, v in acc["surplus"].items()))
            cells = [short(item), fmt_n(acc["ore"].get("bsp_core:raw_tetrium", 0)), fmt_n(acc["ore"].get("bsp_core:raw_illyrium", 0)), fmt_n(acc["ore"].get("bsp_core:raw_magnatite", 0))]
            cells += [fmt_t(acc["machine"][mn]) if acc["machine"].get(mn) else "" for mn in machines]
            cells += [fmt_t(total) if total else "0", fmt_t(acc["latency"]) if acc["latency"] else "0", fmt_t(w) if w else "0", fmt_t(m.mining_hours(acc) * 3600) if acc["ore"] else "0", b.replace("Illyrium ", "I. ") or "", (base + ("; " + "; ".join(extra) if extra else ""))]
            md.append("| " + " | ".join(cells) + " |")
            if sk == "mid":
                data["items"][short(item)] = {"ore": {ORES[k]: v for k, v in acc["ore"].items()}, "machine_seconds": dict(acc["machine"]), "latency_s": acc["latency"], "wall_s": w,
                                              "mining_h": m.mining_hours(acc), "vanilla": {short(k): v for k, v in acc["base"].items()}, "extra": dict(acc["extra"]), "surplus": {short(k): v for k, v in acc["surplus"].items()}}
        md.append("")

    # ---- builds
    md.append("## Builds (whole structures), scenario `mid`\n")
    md.append("| Build | Tetrium ore | Illyrium ore | Magnatite ore | Plates | Machine seconds by machine | Total machine | Wall | Mining | Vanilla highlights |\n|---|---|---|---|---|---|---|---|---|---|")
    m = models["mid"]
    for name, items in BUILDS.items():
        acc = m.bill(items)
        w, b = m.wall(acc)
        plates = acc["count"].get("bsp_core:tetrium_plate", 0)
        by = ", ".join(f"{mn.replace('Illyrium ', 'I. ').replace('Magnetic ', '')} {fmt_t(s)}" for mn, s in sorted(acc["machine"].items(), key=lambda kv: -kv[1]) if s)
        top = ", ".join(f"{fmt_n(v)} {short(k)}" for k, v in sorted(acc["base"].items(), key=lambda kv: -kv[1])[:6])
        md.append(f"| {name} | {fmt_n(acc['ore'].get('bsp_core:raw_tetrium', 0))} | {fmt_n(acc['ore'].get('bsp_core:raw_illyrium', 0))} | {fmt_n(acc['ore'].get('bsp_core:raw_magnatite', 0))} | {fmt_n(plates)} | {by} | {fmt_t(sum(acc['machine'].values()))} | {fmt_t(w)} ({b.replace('Illyrium ', 'I. ')}) | {fmt_t(m.mining_hours(acc) * 3600)} | {top} |")
        data["builds"][name] = {"ore": {ORES[k]: v for k, v in acc["ore"].items()}, "machine_seconds": dict(acc["machine"]), "wall_s": w, "mining_h": m.mining_hours(acc), "plates": plates}
    md.append("")

    # ---- timeline
    md.append("## Progression timeline\n")
    md.append("Cumulative: each milestone adds its own ore and machine time to everything before it. `Player hours` = running sum of max(mining, wall) per step, i.e. the player mines while the machines run. Three scenarios: early (one of each, hand mining) for the first milestones, mid for the middle, late for the end; the table shows all three so the effect of more machines is visible.\n")
    md.append("`Realistic` uses the scenario a player would have at that point: early for milestones 1-5, mid from the Illyrium Crucible to the Motivator, late from the plasma hook-up on.\n")
    md.append("| # | Milestone | Tetrium ore | Illyrium ore | Magnatite ore | Step wall (early / mid / late) | Step mining (early / mid / late) | Player hours so far (early / mid / late) | Realistic hours so far | Slowest machine (mid) | Note |\n|---|---|---|---|---|---|---|---|---|---|---|")
    run = {k: 0.0 for k in models}
    real = 0.0
    for n, (name, items, note) in enumerate(MILESTONES, 1):
        spec = BUILDS[items] if isinstance(items, str) else items
        walls, mines, bott = [], [], ""
        acc0 = None
        stage = "early" if n <= 5 else "mid" if n <= 15 else "late"
        for sk, mm in models.items():
            acc = mm.bill(spec)
            w, b = mm.wall(acc)
            mh = mm.mining_hours(acc) * 3600
            walls.append(w)
            mines.append(mh)
            run[sk] += max(w, mh)
            if sk == stage:
                real += max(w, mh)
            if sk == "mid":
                acc0, bott = acc, b
        md.append(f"| {n} | {name} | {fmt_n(acc0['ore'].get('bsp_core:raw_tetrium', 0))} | {fmt_n(acc0['ore'].get('bsp_core:raw_illyrium', 0))} | {fmt_n(acc0['ore'].get('bsp_core:raw_magnatite', 0))} | "
                  f"{' / '.join(fmt_t(x) for x in walls)} | {' / '.join(fmt_t(x) for x in mines)} | {' / '.join(f'{run[k] / 3600:.1f}' for k in models)} | {real / 3600:.1f} ({stage}) | {bott.replace('Illyrium ', 'I. ')} | {note} |")
        data["milestones"][name] = {"ore": {ORES[k]: v for k, v in acc0["ore"].items()}, "wall_s": dict(zip(models, walls)), "mining_s": dict(zip(models, mines)), "cumulative_h": {k: run[k] / 3600 for k in models}, "realistic_h": real / 3600, "stage": stage}
    md.append("")

    # ---- coins and the totem
    md.append("## Coins and the totem (real time, not machine time)\n")
    ph = cfg["factory.pressHours"]
    red = cfg["factory.motivatorTimeReduction"]
    md.append("| Coin | Blank cost | Press hours, no Motivator | With 3 Motivators | RF per coin |\n|---|---|---|---|---|")
    names = ["copper", "gold", "diamond", "netherite", "illyrium"]
    for i, nm in enumerate(names):
        acc = m.bill({f"{nm}_coin_blank": 1})
        cost = ", ".join(f"{fmt_n(v)} {short(k)}" for k, v in sorted(acc["base"].items(), key=lambda kv: -kv[1]))
        tet = acc["ore"].get("bsp_core:raw_tetrium", 0)
        ill = acc["ore"].get("bsp_core:raw_illyrium", 0)
        md.append(f"| {nm.capitalize()} | {fmt_n(tet)} Tetrium ore{(', ' + fmt_n(ill) + ' Illyrium ore') if ill else ''}; {cost} | {ph[i]:.0f} h | {ph[i] * (1 - red[-1]):.0f} h | {cfg['factory.energyPerCoin'][i]:,} |")
    gate_c, gate_x = cfg["upgrades.costs.tierGateCoins"], cfg["upgrades.costs.tierGateXpLevels"]
    md.append(f"\nTier gates: {', '.join(f'{c} {n.capitalize()}' for c, n in zip(gate_c, names))} coins and {', '.join(str(x) for x in gate_x)} XP levels. "
              f"Pressing the gate coins on four slices in parallel (one tier at a time, each tier's four coins on four slices): "
              f"{sum(ph[:4]):.0f} h real time without Motivators, {sum(ph[:4]) * (1 - red[-1]):.0f} h with three on every slice. On one slice: {sum(4 * h for h in ph[:4]):.0f} h.")
    tree = full_tree_coins(cfg)
    md.append(f"\nThe whole upgrade tree (every upgrade to its top level, parsed from `TotemUpgrades.Buff`): "
              + ", ".join(f"{tree[n]} {n.capitalize()}" for n in names) + " coins, plus the gates. "
              f"Pressing all of it on 10 slices: {sum(tree[n] * ph[i] for i, n in enumerate(names)) / 10:.0f} h real time without Motivators, "
              f"{sum(tree[n] * ph[i] for i, n in enumerate(names)) / 10 * (1 - red[-1]):.0f} h with three on each; the Illyrium coins alone are {tree['illyrium'] * ph[4] / 10:.0f} h of that.")
    data["tree_coins"] = tree
    md.append(f"\nOutput to level 10 (6,000 mB/t): Base path, two levels per tier at {cfg['upgrades.costs.baseLevelCoins']} coins: 3 coins of every tier including Illyrium, i.e. 3 x {ph[4]:.0f} h of Illyrium pressing on top.\n")

    OUT_MD.write_text("\n".join(md) + "\n")
    OUT_JSON.write_text(json.dumps(data, indent=1, default=float))
    print(f"wrote {OUT_MD} and {OUT_JSON.name}: {len(craftables)} craftables, {len(BUILDS)} builds, {len(MILESTONES)} milestones")


if __name__ == "__main__":
    main()
