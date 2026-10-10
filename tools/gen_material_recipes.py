#!/usr/bin/env python3
"""Writes the crafting recipes that no other generator owns: the two single-block machines, the ingot and nugget conversions,
the refinery filters, the Shatter Blank and the coin blanks, the Totem Compass, the guide book and the pickaxe-crushing recipe.
They were hand-written in the first machine session (commit c5c3781); this generator took them over on 2026-10-09 when the
filters changed from cobweb to string (balancing for 1.0), so every recipe in the mod comes from a generator.

  python3 tools/gen_material_recipes.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/bsp_core/recipes"


def write(name, obj):
    (OUT / f"{name}.json").write_text(json.dumps(obj, indent=2))


def mc(item):
    return {"item": item if ":" in item else f"bsp_core:{item}"}


def shaped(name, rows, key, result=None, count=1, extra=None):
    res = {"item": (result if result and ":" in result else f"bsp_core:{result or name}")}
    if count > 1:
        res["count"] = count
    obj = {"type": "minecraft:crafting_shaped", "pattern": rows, "key": {k: mc(v) for k, v in key.items()}, "result": res}
    write(name, obj)


def shapeless(name, ingredients, result, count=1):
    res = {"item": f"bsp_core:{result}"}
    if count > 1:
        res["count"] = count
    write(name, {"type": "minecraft:crafting_shapeless", "ingredients": [mc(i) for i in ingredients], "result": res})


def main():
    # the two single-block machines: vanilla parts only for the first, hand-made Tetrium for the second
    shaped("tetrium_crucible", ["BBB", "BFB", "III"], {"B": "minecraft:bricks", "F": "minecraft:furnace", "I": "minecraft:iron_ingot"})
    shaped("combination_forge", ["TTT", "PAP", "SSS"], {"T": "tetrium_ingot", "P": "minecraft:piston", "A": "minecraft:anvil", "S": "minecraft:smooth_stone"})
    # nine nuggets make an ingot by hand (the forge does the same); an ingot breaks back into nine
    for metal in ("tetrium", "illyrium", "magnatite", "dirty_illyrium"):
        shaped(f"{metal}_ingot_from_nuggets", ["NNN", "NNN", "NNN"], {"N": f"{metal}_nugget"}, result=f"{metal}_ingot")
        if metal != "dirty_illyrium":
            shapeless(f"{metal}_nuggets_from_ingot", [f"{metal}_ingot"], f"{metal}_nugget", 9)
    # refinery filters: four of the metal in the corners, string in the cross (was cobweb until 2026-10-09; cobwebs are not renewable)
    for tier, metal in (("iron", "minecraft:iron_ingot"), ("diamond", "minecraft:diamond"), ("netherite", "minecraft:netherite_ingot"), ("illyrium", "illyrium_ingot")):
        shaped(f"{tier}_filter", ["MWM", "WWW", "MWM"], {"M": metal, "W": "minecraft:string"})
    # coins: a Shatter Blank, then each tier's blank around the one below it
    shaped("shatter_blank", [" T ", "T T", " T "], {"T": "tetrium_ingot"}, count=2)
    prev = "shatter_blank"
    for tier, metal in (("copper", "minecraft:copper_ingot"), ("gold", "minecraft:gold_ingot"), ("diamond", "minecraft:diamond"), ("netherite", "minecraft:netherite_scrap")):
        shaped(f"{tier}_coin_blank", [" M ", "MBM", " M "], {"M": metal, "B": prev})
        prev = f"{tier}_coin_blank"
    shapeless("illyrium_coin_blank", ["netherite_coin_blank", "illyrium_ingot"], "illyrium_coin_blank")
    shaped("totem_compass", [" T ", "TIT", " T "], {"T": "tetrium_ingot", "I": "illyrium_ingot"})
    # the guide book (only with Patchouli) and the crafting-grid crusher
    write("guide_book", {"type": "minecraft:crafting_shapeless", "conditions": [{"type": "forge:mod_loaded", "modid": "patchouli"}],
                         "ingredients": [mc("minecraft:book"), mc("tetrium_nugget")], "result": {"item": "patchouli:guide_book", "nbt": {"patchouli:book": "bsp_core:guide"}}})
    write("pickaxe_crushing", {"type": "bsp_core:pickaxe_crushing", "category": "misc"})
    print("material recipes written: 21")


if __name__ == "__main__":
    main()
