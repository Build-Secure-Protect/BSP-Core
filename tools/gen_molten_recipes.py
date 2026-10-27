#!/usr/bin/env python3
"""Writes the melting and casting recipes for the molten metals (Totem Forge brief, task 2): Tinkers' Construct's
melter, smeltery and casting table, and Thermal's Magma Crucible and Blast Chiller. Each recipe sits behind a
forge:mod_loaded condition, so BSP-Core loads without either mod.

  python3 tools/gen_molten_recipes.py

The owner's rule (2026-10-27): another mod may never make more metal than the BSP machines do.
- Ore melts to exactly the nuggets a BSP machine makes from it (90 mB an ingot, 10 mB a nugget): Tetrium 20 mB
  (the Tetrium Crucible's 2 nuggets), Magnatite 30 mB (the Magnetic Centrifuge's 3), in the machine's own time.
  Plain tconstruct:melting, never tconstruct:ore_melting, so the smeltery's ore rate and the foundry's
  by-products never apply. The by-products (slag, Carbon Dust) stay with the BSP machines.
- Illyrium ore, Dirty Illyrium and Charged Magnatite never melt: a melt would skip the refinery, or the charge.
- Nuggets and ingots melt and cast back one for one.
Formats were read from the mods' own recipes in the pack (TConstruct 3.12.1: smeltery/melting/metal/iron and
casting/metal/iron; Thermal Expansion 11.0.1 crucible, Thermal Integration 11.0.1 chiller_tconstruct_*).
"""
import shutil

from gen_projector_assets import DATA, write

OUT = DATA / "bsp_core/recipes/molten"

# metal: (Tinkers melting temperature, [(item, mB, Tinkers time, Thermal energy)])
MELT = {
    "tetrium": (900, [("raw_tetrium", 20, 300, 6000), ("tetrium_ore", 20, 300, 6000), ("deepslate_tetrium_ore", 20, 300, 6000),
                      ("tetrium_nugget", 10, 20, 1000), ("tetrium_ingot", 90, 60, 4000)]),
    "magnatite": (1000, [("raw_magnatite", 30, 600, 12000), ("magnatite_ore", 30, 600, 12000), ("deepslate_magnatite_ore", 30, 600, 12000),
                         ("magnatite_nugget", 10, 20, 1000), ("magnatite_ingot", 90, 60, 4000)]),
    "illyrium": (1400, [("illyrium_nugget", 10, 20, 1000), ("illyrium_ingot", 90, 60, 4000)]),
}


def cond(modid, body):
    return {"conditions": [{"type": "forge:mod_loaded", "modid": modid}], **body}


def tinkers_melt(item, metal, mb, temp, time):
    return cond("tconstruct", {"type": "tconstruct:melting", "ingredient": {"item": f"bsp_core:{item}"},
                               "result": {"fluid": f"bsp_core:molten_{metal}", "amount": mb}, "temperature": temp, "time": time})


def tinkers_cast(metal, shape, sand):
    mb, cool = (90, 60) if shape == "ingot" else (10, 20)
    body = {"type": "tconstruct:casting_table", "cast": {"tag": f"tconstruct:casts/{'single_use' if sand else 'multi_use'}/{shape}"}}
    if sand:
        body["cast_consumed"] = True
    body.update({"cooling_time": cool, "fluid": {"fluid": f"bsp_core:molten_{metal}", "amount": mb}, "result": {"item": f"bsp_core:{metal}_{shape}"}})
    return cond("tconstruct", body)


def thermal_melt(item, metal, mb, energy):
    return cond("thermal_expansion", {"type": "thermal:crucible", "ingredient": {"item": f"bsp_core:{item}"},
                                      "result": [{"fluid": f"bsp_core:molten_{metal}", "amount": mb}], "energy": energy})


def thermal_chill(metal):
    """The Blast Chiller has only an ingot cast."""
    return cond("thermal_expansion", {"type": "thermal:chiller", "ingredients": [{"fluid": f"bsp_core:molten_{metal}", "amount": 90}, {"item": "thermal:chiller_ingot_cast"}],
                                      "result": [{"item": f"bsp_core:{metal}_ingot", "count": 1}], "energy": 4800})


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    n = 0
    for metal, (temp, items) in MELT.items():
        for item, mb, time, energy in items:
            write(OUT / f"tinkers_melting_{item}.json", tinkers_melt(item, metal, mb, temp, time))
            write(OUT / f"thermal_crucible_{item}.json", thermal_melt(item, metal, mb, energy))
            n += 2
        for shape in ("ingot", "nugget"):
            for sand in (False, True):
                write(OUT / f"tinkers_casting_{metal}_{shape}_{'sand' if sand else 'gold'}_cast.json", tinkers_cast(metal, shape, sand))
                n += 1
        write(OUT / f"thermal_chiller_{metal}_ingot.json", thermal_chill(metal))
        n += 1
    print(f"wrote {n} molten metal recipes")


if __name__ == "__main__":
    main()
