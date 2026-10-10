#!/usr/bin/env python3
"""Writes the ore generation data: configured and placed features for Tetrium, Illyrium and Magnatite in the Overworld and in the
All the Mods mining dimension, the biome modifiers that add them, and the mining-dimension biome tag. One table, ORES, holds
every number; the balance report (tools/balance_report.py) reads the written files back.

  python3 tools/gen_worldgen.py

Density for comparison (1.20.1 vanilla): iron's middle band is count 10 size 9, diamond is count 7 size 4 (discard 0.5) plus
count 4 size 8 buried and 1 in 9 chunks size 12. Tetrium matches iron's middle band. Illyrium was count 4 (a quarter of diamond)
until 2026-10-09 and is now 8 (about half of diamond), per the balancing pass for 1.0.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "src/main/resources/data/bsp_core"

STONE = [{"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}, "state": {"Name": "bsp_core:{ore}"}},
         {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, "state": {"Name": "bsp_core:deepslate_{ore}"}}]
END_STONE = [{"target": {"predicate_type": "minecraft:block_match", "block": "minecraft:end_stone"}, "state": {"Name": "bsp_core:end_stone_{ore}"}}]

# ore: (configured features {name: (size, discard_on_air, targets)}, placed features {name: (configured, count, y_min, y_max, biome tag)})
ORES = {
    "tetrium_ore": ({"tetrium_ore": (9, 0.0, STONE)},
                    {"tetrium_ore": ("tetrium_ore", 10, -32, 80, "#minecraft:is_overworld"),
                     "tetrium_ore_mining": ("tetrium_ore", 10, 65, 247, "#bsp_core:atm_mining")}),
    "illyrium_ore": ({"illyrium_ore": (4, 0.7, STONE), "illyrium_ore_end_stone": (4, 0.5, END_STONE)},
                     {"illyrium_ore": ("illyrium_ore", 8, -64, -16, "#minecraft:is_overworld"),
                      "illyrium_ore_mining": ("illyrium_ore_end_stone", 8, -63, 0, "#bsp_core:atm_mining")}),
    "magnatite_ore": ({"magnatite_ore": (6, 0.4, STONE)},
                      {"magnatite_ore": ("magnatite_ore", 7, -64, 8, "#minecraft:is_overworld"),
                       "magnatite_ore_mining": ("magnatite_ore", 7, 65, 160, "#bsp_core:atm_mining")}),
}


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2))


def main():
    n = 0
    for ore, (configured, placed) in ORES.items():
        for name, (size, discard, targets) in configured.items():
            targets = json.loads(json.dumps(targets).replace("{ore}", ore))
            write(DATA / f"worldgen/configured_feature/{name}.json",
                  {"type": "minecraft:ore", "config": {"size": size, "discard_chance_on_air_exposure": discard, "targets": targets}})
            n += 1
        for name, (feature, count, y0, y1, biomes) in placed.items():
            write(DATA / f"worldgen/placed_feature/{name}.json",
                  {"feature": f"bsp_core:{feature}", "placement": [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                                                                   {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": y0}, "max_inclusive": {"absolute": y1}}},
                                                                   {"type": "minecraft:biome"}]})
            write(DATA / f"forge/biome_modifier/{name}.json", {"type": "forge:add_features", "biomes": biomes, "features": f"bsp_core:{name}", "step": "underground_ores"})
            n += 2
    write(DATA / "tags/worldgen/biome/atm_mining.json", {"replace": False, "values": [{"id": "allthemodium:mining", "required": False}]})
    print(f"worldgen written: {n + 1} files")


if __name__ == "__main__":
    main()
