#!/usr/bin/env python3
"""Generates the BSP-Core advancement chain (no third-party dependencies).

  python3 tools/gen_advancements.py

One tab that walks a player from their Shatter Totem to a Motivator. Each advancement is earned by
holding the item. Titles and descriptions go into en_us.json. Edit here and rerun.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/data/bsp_core/advancements"
LANG = ROOT / "src/main/resources/assets/bsp_core/lang/en_us.json"
COINS = [f"bsp_core:{t}_shatter_coin" for t in ("copper", "gold", "diamond", "netherite", "illyrium")]

# id: (parent, icon, title, description, items or {"tag": ...}, frame)
ADV = [
    ("root", None, "bsp_core:shatter_totem", "Build Secure Protect", "Hold your Shatter Totem. Guard it well.", ["bsp_core:shatter_totem"], "task"),
    ("totem_compass", "root", "bsp_core:totem_compass", "Which Way Home", "Craft a Totem Compass", ["bsp_core:totem_compass"], "task"),
    ("tetrium_ore", "root", "bsp_core:tetrium_ore", "Something Purple", "Mine Tetrium Ore with an iron pickaxe or better", {"tag": "bsp_core:tetrium_ores"}, "task"),
    ("tetrium_crucible", "tetrium_ore", "bsp_core:tetrium_crucible", "First Melt", "Craft a Tetrium Crucible", ["bsp_core:tetrium_crucible"], "task"),
    ("tetrium_ingot", "tetrium_crucible", "bsp_core:tetrium_ingot", "Nine Makes One", "Make a Tetrium Ingot from nine nuggets", ["bsp_core:tetrium_ingot"], "task"),
    ("slag_brick", "tetrium_crucible", "bsp_core:slag_brick", "Waste Not", "Smelt Tetrium Slag into a Slag Brick", ["bsp_core:slag_brick"], "task"),
    ("combination_forge", "tetrium_ingot", "bsp_core:combination_forge", "Hammer Time", "Craft a Combination Forge", ["bsp_core:combination_forge"], "task"),
    ("tetrium_plate", "combination_forge", "bsp_core:tetrium_plate", "Flat Out", "Press a Tetrium Plate in the Combination Forge", ["bsp_core:tetrium_plate"], "task"),
    ("machine_chassis", "tetrium_plate", "bsp_core:machine_chassis", "Empty Shell", "Craft a Machine Chassis, the start of every machine block", ["bsp_core:machine_chassis"], "task"),
    ("basic_control_circuit", "machine_chassis", "bsp_core:basic_control_circuit", "Thinking Parts", "Craft a Basic Control Circuit", ["bsp_core:basic_control_circuit"], "task"),
    ("coin_vault", "basic_control_circuit", "bsp_core:coin_vault", "Safe Keeping", "Craft a Coin Vault. Coins kept in it earn interest", ["bsp_core:coin_vault"], "task"),
    ("magnatite_ore", "tetrium_ore", "bsp_core:magnatite_ore", "Strangely Attractive", "Mine Magnatite Ore, deep down, with a diamond pickaxe", {"tag": "bsp_core:magnatite_ores"}, "task"),
    ("magnetic_centrifuge", "dirty_illyrium_ingot", "bsp_core:magnetic_centrifuge", "Round and Round", "Craft the Magnetic Centrifuge Controller", ["bsp_core:magnetic_centrifuge"], "goal"),
    ("charged_magnatite", "magnetic_centrifuge", "bsp_core:charged_magnatite_ingot", "Fully Charged", "Charge a Magnatite Ingot in a centrifuge fitted with a Copper Tetrium Coil", ["bsp_core:charged_magnatite_ingot"], "challenge"),
    ("decoy_totem", "charged_magnatite", "bsp_core:decoy_totem", "Not the Totem You Are Looking For", "Craft a Decoy Totem", ["bsp_core:decoy_totem"], "challenge"),
    ("totem_projector", "charged_magnatite", "bsp_core:totem_projector", "In Two Places at Once", "Craft a Totem Projector", ["bsp_core:totem_projector"], "challenge"),
    ("rf_upgrade", "machine_chassis", "bsp_core:rf_upgrade_mk1", "Plugged In", "Craft an RF Upgrade", ["bsp_core:rf_upgrade_mk1", "bsp_core:rf_upgrade_mk2", "bsp_core:rf_upgrade_mk3"], "task"),
    ("illyrium_crucible", "basic_control_circuit", "bsp_core:illyrium_crucible", "Playing With Lava", "Craft the Illyrium Crucible Controller. Sneak + right-click it for the Assembly Guide", ["bsp_core:illyrium_crucible"], "goal"),
    ("dirty_illyrium_ingot", "illyrium_crucible", "bsp_core:dirty_illyrium_ingot", "Needs a Wash", "Smelt a Dirty Illyrium Ingot in the Illyrium Crucible", ["bsp_core:dirty_illyrium_ingot"], "task"),
    ("illyrium_refinery", "dirty_illyrium_ingot", "bsp_core:illyrium_refinery", "Rinse Cycle", "Craft the Illyrium Refinery Controller", ["bsp_core:illyrium_refinery"], "goal"),
    ("pure_illyrium_dust", "illyrium_refinery", "bsp_core:pure_illyrium_dust", "Squeaky Clean", "Refine Pure Illyrium Dust", ["bsp_core:pure_illyrium_dust"], "task"),
    ("illyrium_ingot", "pure_illyrium_dust", "bsp_core:illyrium_ingot", "The Good Stuff", "Make an Illyrium Ingot", ["bsp_core:illyrium_ingot"], "goal"),
    ("shatter_coin_factory", "dirty_illyrium_ingot", "bsp_core:shatter_coin_factory", "Licence to Mint", "Craft the Shatter Coin Factory Controller", ["bsp_core:shatter_coin_factory"], "goal"),
    ("first_coin", "shatter_coin_factory", "bsp_core:copper_shatter_coin", "Fresh Off the Press", "Take a Shatter Coin from your factory's tray", COINS, "task"),
    ("illyrium_coin", "first_coin", "bsp_core:illyrium_shatter_coin", "Top Coin", "Press an Illyrium Shatter Coin", ["bsp_core:illyrium_shatter_coin"], "challenge"),
    ("motivator", "illyrium_ingot", "bsp_core:factory_motivator", "Properly Motivated", "Craft a Motivator and put it on top of a factory slice", ["bsp_core:factory_motivator"], "challenge"),
    ("raw_ore", "tetrium_ore", "bsp_core:raw_tetrium", "In the Rough", "Pick up a raw ore chunk. The crucibles take them like ore blocks", ["bsp_core:raw_tetrium", "bsp_core:raw_illyrium", "bsp_core:raw_magnatite"], "task"),
    ("wrench", "machine_chassis", "bsp_core:wrench", "Righty Tighty", "Craft a Wrench. It turns repeaters, valves, chargers, batteries and totems in place", ["bsp_core:wrench"], "task"),
    ("plasma_extractor", "totem_projector", "bsp_core:plasma_extractor", "Tap the Source", "Craft a Plasma Extractor and stand your totem on it", ["bsp_core:plasma_extractor"], "task"),
    ("plasma_interface", "plasma_extractor", "bsp_core:plasma_interface", "Header Rack", "Craft a Plasma Interface. Touching interfaces join into one", ["bsp_core:plasma_interface"], "task"),
    ("plasma_cable", "plasma_interface", "bsp_core:tetrium_core_cable", "Down the Line", "Craft a Plasma Cable of any kind", ["bsp_core:tetrium_core_cable", "bsp_core:magnatite_core_cable", "bsp_core:illyrium_core_cable", "bsp_core:charged_illyrium_core_cable"], "task"),
    ("projector_base", "plasma_cable", "bsp_core:projector_base", "Firm Footing", "Craft a Projector Base. The projector stands on it and drinks from it", ["bsp_core:projector_base"], "task"),
    ("channel_expander", "projector_base", "bsp_core:channel_expander", "Third Channel", "Craft a Channel Expander and fit it to a projector", ["bsp_core:channel_expander"], "task"),
    ("plasma_repeater", "plasma_cable", "bsp_core:plasma_repeater", "Second Wind", "Craft a Plasma Repeater to start a fresh cable run", ["bsp_core:plasma_repeater"], "task"),
    ("plasma_valve", "plasma_cable", "bsp_core:plasma_valve", "Turn It Down", "Craft a Plasma Valve to limit or shut a run", ["bsp_core:plasma_valve"], "task"),
    ("charged_resonance_crystal", "magnetic_centrifuge", "bsp_core:charged_resonance_crystal", "Humming", "Magnetise a Resonance Crystal in the centrifuge with a Magnatite Nugget in the upgrade slot", ["bsp_core:charged_resonance_crystal"], "task"),
    ("battery_charger", "charged_resonance_crystal", "bsp_core:battery_charger", "Open Cradle", "Craft a Battery Charger", ["bsp_core:battery_charger"], "goal"),
    ("plasma_battery", "battery_charger", "bsp_core:plasma_battery_1", "Bottled Lightning", "Craft a Plasma Battery of any tier", ["bsp_core:plasma_battery_1", "bsp_core:plasma_battery_2", "bsp_core:plasma_battery_3", "bsp_core:plasma_battery_4"], "task"),
    ("plasma_battery_4", "plasma_battery", "bsp_core:plasma_battery_4", "Five Million", "Craft a Tier IV Plasma Battery", ["bsp_core:plasma_battery_4"], "challenge"),
    ("power_cell", "battery_charger", "bsp_core:power_cell_1", "Pocket Power", "Craft a Power Cell of any tier", ["bsp_core:power_cell_1", "bsp_core:power_cell_2", "bsp_core:power_cell_3"], "task"),
    ("wave_emitter", "power_cell", "bsp_core:wave_emitter", "Carry It With You", "Craft a Wave Emitter. With a charged cell in your offhand it gives you the cell's powers", ["bsp_core:wave_emitter"], "goal"),
    ("plasma_tank", "plasma_cable", "bsp_core:tank_port", "Big Bottle", "Craft a Tank Port. A hollow box of Tank Casing and Tank Glass with ports in it holds Wave Plasma by the million", ["bsp_core:tank_port"], "goal"),
    ("dyed_cable", "plasma_cable", "bsp_core:blue_illyrium_core_cable", "A Splash of Colour", "Dye a cable. Coloured cables only join their own colour, so runs stay apart",
     [f"bsp_core:{dye}_{kind}_core_cable" for kind in ("magnatite", "illyrium", "charged_illyrium") for dye in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")], "task"),
]


def main():
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    lang = json.loads(LANG.read_text())
    for key in [k for k in lang if k.startswith("advancement.bsp_core.")]:
        del lang[key]
    for aid, parent, icon, title, desc, items, frame in ADV:
        lang[f"advancement.bsp_core.{aid}"] = title
        lang[f"advancement.bsp_core.{aid}.desc"] = desc
        predicate = items if isinstance(items, dict) else {"items": items}
        adv = {"display": {"icon": {"item": icon}, "title": {"translate": f"advancement.bsp_core.{aid}"},
                           "description": {"translate": f"advancement.bsp_core.{aid}.desc"}, "frame": frame,
                           "show_toast": True, "announce_to_chat": frame != "task", "hidden": False},
               "criteria": {"has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [predicate]}}}}
        if parent:
            adv["parent"] = f"bsp_core:{parent}"
        else:
            adv["display"]["background"] = "minecraft:textures/block/deepslate_tiles.png"
        (OUT / f"{aid}.json").write_text(json.dumps(adv, indent=2))
    LANG.write_text(json.dumps(lang, indent=2) + "\n")
    print("advancements written:", len(ADV))


if __name__ == "__main__":
    main()
