#!/usr/bin/env python3
"""Generates the Coin Vault assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_vault_assets.py

Writes the "Classic Safe" textures (door, side plating, top), the model, block state, item model
and loot table of the Coin Vault. Rerun after changing anything here; never hand-edit the outputs.
"""
import json
import math

from gen_factory_assets import DATA, shade
from gen_material_assets import ASSETS, hexc

STEEL, STEEL2, EDGE, DARK, GOLD, GOLD2, PANEL = "#3A4150", "#2B313D", "#8A909C", "#0C1116", "#FFD23A", "#B8922A", "#C9CED8"


def side(x, y):
    """Heavy plating: a bright edge, corner rivets and two weld seams."""
    if x in (0, 15) or y in (0, 15):
        return shade(EDGE, x, y, 21)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        return hexc(PANEL)
    if y in (5, 10) and 2 <= x <= 13:
        return shade(STEEL2, x, y, 23)
    return shade(STEEL, x, y, 25)


def top(x, y):
    if x in (0, 15) or y in (0, 15):
        return shade(EDGE, x, y, 31)
    if x in (2, 13) or y in (2, 13):
        return hexc(GOLD2) if 2 <= x <= 13 and 2 <= y <= 13 else shade(STEEL, x, y, 33)
    return shade(STEEL2, x, y, 35)


def door(x, y):
    """The safe door: gold-trimmed panel, a combination dial, a bar handle and two hinges."""
    if x in (0, 15) or y in (0, 15):
        return shade(EDGE, x, y, 41)
    if x in (2, 13) or y in (2, 13):
        if 2 <= x <= 13 and 2 <= y <= 13:
            return hexc(GOLD2)
    if x == 1 and y in (4, 5, 10, 11):
        return hexc(PANEL)  # hinges
    d = math.hypot(x - 6.5, y - 7.5)
    if d < 1.2:
        return hexc(GOLD)
    if d < 2.9:
        return hexc(DARK) if (x + y) % 2 else hexc(PANEL)
    if d < 3.6:
        return hexc(GOLD)
    if 10 <= x <= 11 and 5 <= y <= 10:
        return hexc(PANEL) if x == 10 else hexc(EDGE)  # handle
    if 3 <= x <= 12 and 3 <= y <= 12:
        return shade(STEEL2, x, y, 43)
    return shade(STEEL, x, y, 45)


def main():
    from gen_material_assets import write_png
    tex = ASSETS / "textures/block"
    write_png(tex / "coin_vault_side.png", 16, 16, side)
    write_png(tex / "coin_vault_top.png", 16, 16, top)
    write_png(tex / "coin_vault_front.png", 16, 16, door)
    (ASSETS / "models/block/coin_vault.json").write_text(json.dumps({"parent": "minecraft:block/orientable", "textures": {
        "top": "bsp_core:block/coin_vault_top", "side": "bsp_core:block/coin_vault_side", "front": "bsp_core:block/coin_vault_front"}}, indent=2))
    (ASSETS / "models/item/coin_vault.json").write_text(json.dumps({"parent": "bsp_core:block/coin_vault"}, indent=2))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "bsp_core:block/coin_vault"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing},formed=false"] = v
        variants[f"facing={facing},formed=true"] = v  # not drawn: CoinVaultRenderer draws the joined vault
    (ASSETS / "blockstates/coin_vault.json").write_text(json.dumps({"variants": variants}, indent=2))
    # the block keeps its lock and alarm upgrades as an item (a non-owner's break clears them first)
    (DATA / "loot_tables/blocks/coin_vault.json").write_text(json.dumps({"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "bsp_core:coin_vault", "functions": [{
            "function": "minecraft:copy_nbt", "source": "block_entity", "ops": [
                {"source": "Lock", "target": "BlockEntityTag.Lock", "op": "replace"},
                {"source": "Alarm", "target": "BlockEntityTag.Alarm", "op": "replace"}]}]}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, indent=2))
    print("vault assets written")


if __name__ == "__main__":
    main()
