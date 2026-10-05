#!/usr/bin/env python3
"""Generates everything Magnatite for BSP-Core (no third-party dependencies).

  python3 tools/gen_magnatite_assets.py

Magnatite is the metal between Tetrium and Illyrium ("Iron Blue" scheme: near-black with a cold
blue sheen). Writes the item sprites (the ingot and nugget are the vanilla iron shapes recoloured),
the ore overlay with its block models, block states and loot tables, the ore's world generation,
tags, and the hand-crafting recipes. Rerun after changing anything here; never hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, ROOT, hexc, hsh, item_model, ore_overlay, pal, recolour, vanilla, write_png

DATA = ROOT / "src/main/resources/data"
MAG = pal("#12161C", "#232831", "#39414F", "#5C6B85", "#7FB3FF")
CHARGED = pal("#1A2740", "#2E4A80", "#4F7FD0", "#8FC0FF", "#E8F4FF")
CARBON = pal("#0B0C0E", "#17191C", "#2A2C30", "#44474D", "#6A6E76")
COPPER = pal("#5A2E14", "#8F4A22", "#C87A3C", "#E8A56A", "#FFD9B0")
ORES = {"magnatite_ore": "minecraft:block/stone", "deepslate_magnatite_ore": "minecraft:block/deepslate"}


def charged(base):
    """The ingot in bright blue, with a few white sparks along its top face."""
    px = recolour(base, CHARGED)

    def out(x, y):
        c = px(x, y)
        if c[3] and hsh(x, y, 17) > 0.86:
            return hexc("#FFFFFF")
        return c
    return out


def copper_coil(x, y):
    """A Tetrium Coil wound with copper around a Magnatite core: copper turns on a dark bar with blue ends."""
    if 7 <= y <= 8 and 1 <= x <= 14:
        return MAG["ramp"][4] if x in (1, 14) else MAG["base"]
    if 3 <= x <= 12 and 3 <= y <= 12:
        turn = (x - 3) % 3
        if turn == 2:
            return (0, 0, 0, 0)
        edge = y in (3, 12)
        return COPPER["dark"] if edge else COPPER["light"] if turn == 0 and y < 7 else COPPER["base"]
    return (0, 0, 0, 0)


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2))


def shaped(name, rows, key, result, count=1):
    res = {"item": result}
    if count > 1:
        res["count"] = count
    write(DATA / f"bsp_core/recipes/{name}.json", {"type": "minecraft:crafting_shaped", "pattern": rows, "key": {k: {"item": v} for k, v in key.items()}, "result": res})


def add_tag(path, values):
    tag = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
    for v in values:
        if v not in tag["values"]:
            tag["values"].append(v)
    write(path, tag)


def main():
    ingot, nugget = vanilla("item/iron_ingot.png"), vanilla("item/iron_nugget.png")
    sprites = {"magnatite_ingot": recolour(ingot, MAG), "magnatite_nugget": recolour(nugget, MAG), "charged_magnatite_ingot": charged(ingot),
               "carbon_dust": recolour(vanilla("item/gunpowder.png"), CARBON, 2), "copper_tetrium_coil": copper_coil}
    for name, px in sprites.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, px)
        item_model(name)

    write_png(ASSETS / "textures/block/magnatite_ore_overlay.png", 16, 16, ore_overlay(MAG, 5))
    for block, host in ORES.items():
        write(ASSETS / f"models/block/{block}.json", {"parent": "bsp_core:block/ore_overlay", "textures": {"base": host, "overlay": "bsp_core:block/magnatite_ore_overlay"}})
        write(ASSETS / f"models/item/{block}.json", {"parent": f"bsp_core:block/{block}"})
        write(ASSETS / f"blockstates/{block}.json", {"variants": {"": {"model": f"bsp_core:block/{block}"}}})
        write(DATA / f"bsp_core/loot_tables/blocks/{block}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"bsp_core:{block}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    ids = [f"bsp_core:{b}" for b in ORES]
    add_tag(DATA / "bsp_core/tags/blocks/magnatite_ores.json", ids)
    add_tag(DATA / "bsp_core/tags/items/magnatite_ores.json", ids)
    add_tag(DATA / "minecraft/tags/blocks/mineable/pickaxe.json", ids)
    add_tag(DATA / "minecraft/tags/blocks/needs_diamond_tool.json", ids)

    # World generation: deep, mostly deepslate, a little easier to find than diamond (more veins, slightly larger, less lost to caves).
    def target(tag, block):
        return {"target": {"predicate_type": "minecraft:tag_match", "tag": tag}, "state": {"Name": f"bsp_core:{block}"}}
    write(DATA / "bsp_core/worldgen/configured_feature/magnatite_ore.json", {"type": "minecraft:ore", "config": {
        "size": 6, "discard_chance_on_air_exposure": 0.4,
        "targets": [target("minecraft:stone_ore_replaceables", "magnatite_ore"), target("minecraft:deepslate_ore_replaceables", "deepslate_magnatite_ore")]}})

    def placed(count, lo, hi, shape="minecraft:uniform"):
        return {"feature": "bsp_core:magnatite_ore", "placement": [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": shape, "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}, {"type": "minecraft:biome"}]}
    write(DATA / "bsp_core/worldgen/placed_feature/magnatite_ore.json", placed(7, -64, 8))
    write(DATA / "bsp_core/worldgen/placed_feature/magnatite_ore_mining.json", placed(7, 65, 160))
    write(DATA / "bsp_core/forge/biome_modifier/magnatite_ore.json", {"type": "forge:add_features", "biomes": "#minecraft:is_overworld",
                                                                       "features": "bsp_core:magnatite_ore", "step": "underground_ores"})
    write(DATA / "bsp_core/forge/biome_modifier/magnatite_ore_mining.json", {"type": "forge:add_features", "biomes": "#bsp_core:atm_mining",
                                                                              "features": "bsp_core:magnatite_ore_mining", "step": "underground_ores"})

    shaped("magnatite_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": "bsp_core:magnatite_nugget"}, "bsp_core:magnatite_ingot")
    write(DATA / "bsp_core/recipes/magnatite_nuggets_from_ingot.json", {"type": "minecraft:crafting_shapeless", "ingredients": [{"item": "bsp_core:magnatite_ingot"}],
                                                                        "result": {"item": "bsp_core:magnatite_nugget", "count": 9}})
    shaped("copper_tetrium_coil", ["CDC", "WMW", "CDC"], {"C": "minecraft:copper_ingot", "D": "bsp_core:carbon_dust", "W": "bsp_core:tetrium_coil", "M": "bsp_core:magnatite_ingot"},
           "bsp_core:copper_tetrium_coil")
    print("magnatite assets written")


if __name__ == "__main__":
    main()
