#!/usr/bin/env python3
"""Writes the recipes other mods' machines get for BSP materials: ingot to dust in every crusher, grinder and mill the modpack has,
each wrapped in a forge:mod_loaded condition so the mod loads without them. Dust is the step the Refinery needs; hand crushing is
the fallback, so none of these skips a BSP machine. Formats were read from each mod's own generated recipes (2026-10-08, see the
vault note "Mod Integration Assessment (1.0)"); a wrong format only logs a recipe error in that mod's presence.

  python3 tools/gen_compat_recipes.py
"""
import json
import shutil

from gen_projector_assets import DATA, write

OUT = DATA / "bsp_core/recipes/compat"

# input ingot -> output dust, by the short name used in the file names
DUSTS = {"tetrium_dust": "tetrium_ingot", "dirty_illyrium_dust": "dirty_illyrium_ingot", "pure_illyrium_dust": "illyrium_ingot"}


def cond(modid, body):
    return {"conditions": [{"type": "forge:mod_loaded", "modid": modid}], **body}


def mekanism(inp, out):
    return cond("mekanism", {"type": "mekanism:crushing", "input": {"ingredient": {"item": inp}}, "output": {"item": out}})


def create(kind, inp, out, ticks):
    return cond("create", {"type": f"create:{kind}", "ingredients": [{"item": inp}], "results": [{"item": out, "count": 1}], "processingTime": ticks})


def thermal(inp, out):
    return cond("thermal", {"type": "thermal:pulverizer", "ingredient": {"item": inp}, "result": [{"item": out, "count": 1}], "energy": 4000})


def enderio(inp, out):
    return cond("enderio", {"type": "enderio:sag_milling", "energy": 2400, "input": {"item": inp}, "outputs": [{"item": {"item": out, "count": 1}, "chance": 1.0, "optional": False}]})


def railcraft(inp, out):
    return cond("railcraft", {"type": "railcraft:crusher", "ingredient": {"item": inp}, "outputs": [{"result": {"item": out}, "count": 1, "probability": 1.0}]})


def integrateddynamics(inp, out):
    return cond("integrateddynamics", {"type": "integrateddynamics:mechanical_squeezer", "input_item": {"item": inp}, "output_items": [{"item": {"item": out, "count": 1}}], "duration": 40})


def electrodynamics(inp, out):
    return cond("electrodynamics", {"type": "electrodynamics:mineral_grinder_recipe", "iteminputs": {"0": {"item": inp, "count": 1}, "count": 1},
                                     "output": {"item": out, "count": 1}, "experience": 0.0, "ticks": 200, "usagepertick": 350.0})


RECIPES = {
    "mekanism_crushing": mekanism,
    "create_crushing": lambda i, o: create("crushing", i, o, 250),
    "create_milling": lambda i, o: create("milling", i, o, 250),
    "thermal_pulverizer": thermal,
    "enderio_sag_milling": enderio,
    "railcraft_crusher": railcraft,
    "integrateddynamics_squeezer": integrateddynamics,
    "electrodynamics_mineral_grinder": electrodynamics,
}


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    n = 0
    for prefix, make in RECIPES.items():
        for dust, ingot in DUSTS.items():
            write(OUT / f"{prefix}_{dust}.json", make(f"bsp_core:{ingot}", f"bsp_core:{dust}"))
            n += 1
    print(f"compat recipes written: {n} in {len(RECIPES)} machines")


if __name__ == "__main__":
    main()
