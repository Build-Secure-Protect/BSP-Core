#!/usr/bin/env python3
"""Generates the raw ore items (Raw Tetrium, Raw Illyrium, Raw Magnatite) and makes the ores drop them
like vanilla iron ore: the raw item with Fortune, or the ore block with Silk Touch. The raw items join
the ore item tags so the machines take them as they take the ore blocks.

  python3 tools/gen_raw_ore_assets.py

Run after gen_material_assets.py and gen_magnatite_assets.py, which write the plain ore loot tables.
"""
import json

from gen_magnatite_assets import MAG, add_tag
from gen_material_assets import ASSETS, ILL, ROOT, TET, item_model, recolour, vanilla, write_png

DATA = ROOT / "src/main/resources/data"
ORES = {"raw_tetrium": (["tetrium_ore", "deepslate_tetrium_ore"], "tetrium_ores"),
        "raw_illyrium": (["illyrium_ore", "deepslate_illyrium_ore", "end_stone_illyrium_ore"], "illyrium_ores"),
        "raw_magnatite": (["magnatite_ore", "deepslate_magnatite_ore"], "magnatite_ores")}
SPRITES = {"raw_tetrium": ("item/raw_gold.png", TET, 3), "raw_illyrium": ("item/raw_copper.png", ILL, 5), "raw_magnatite": ("item/raw_iron.png", MAG, 9)}


def ore_loot(block, raw):
    """Silk Touch gives the block; otherwise one raw item, more with Fortune, fewer if blown up."""
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": f"bsp_core:{block}", "conditions": [{"condition": "minecraft:match_tool", "predicate": {"enchantments": [{"enchantment": "minecraft:silk_touch", "levels": {"min": 1}}]}}]},
        {"type": "minecraft:item", "name": f"bsp_core:{raw}", "functions": [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"}, {"function": "minecraft:explosion_decay"}]}]}]}]}


def main():
    for raw, (template, palette, seed) in SPRITES.items():
        write_png(ASSETS / f"textures/item/{raw}.png", 16, 16, recolour(vanilla(template), palette, seed))
        item_model(raw)
    for raw, (blocks, tag) in ORES.items():
        for block in blocks:
            (DATA / f"bsp_core/loot_tables/blocks/{block}.json").write_text(json.dumps(ore_loot(block, raw), indent=2))
        add_tag(DATA / f"bsp_core/tags/items/{tag}.json", [f"bsp_core:{raw}"])
        # the common ore tag, so X-ray (Tags.Blocks.ORES) and other mods' ore detection see them. Deliberately NOT the material
        # subtags (forge:ores/tetrium, forge:ingots/tetrium ...): JAOPCA, Almost Unified and the big tech mods build ore-processing
        # chains from those, which would let a player skip the crucibles. Keep BSP materials off the material tags.
        add_tag(DATA / "forge/tags/blocks/ores.json", [f"bsp_core:{b}" for b in blocks])
        add_tag(DATA / "forge/tags/items/ores.json", [f"bsp_core:{b}" for b in blocks])
        add_tag(DATA / "forge/tags/items/raw_materials.json", [f"bsp_core:{raw}"])
    print("raw ore assets written")


if __name__ == "__main__":
    main()
