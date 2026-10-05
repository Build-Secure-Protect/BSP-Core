#!/usr/bin/env python3
"""Generates the Anti Totem block assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_zone_assets.py

Writes the "Warden Cube" cage: two plates, four corner posts, two rails and violet admin lamps
above and below. The cube that turns inside is drawn by AntiTotemRenderer in the zone's colour.
The block has no loot table: it cannot be mined in survival. Never hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, mbox


def cage(full=False):
    e = [mbox([1, 0, 1], [15, 1.5, 15], "hull2"), mbox([1, 14.5, 1], [15, 16, 15], "hull2")]
    for x, z in ((1, 1), (14, 1), (1, 14), (14, 14)):
        e.append(mbox([x, 1.5, z], [x + 1, 14.5, z + 1], "trim"))
    for y in (5, 10.4):
        e += [mbox([2, y, 1], [14, y + 0.6, 1.5], "mid"), mbox([2, y, 14.5], [14, y + 0.6, 15], "mid"),
              mbox([1, y, 2], [1.5, y + 0.6, 14], "mid"), mbox([14.5, y, 2], [15, y + 0.6, 14], "mid")]
    e += [mbox([6, 1.5, 6], [10, 2.2, 10], "violet"), mbox([6, 13.8, 6], [10, 14.5, 10], "violet")]
    if full:  # the inventory icon has no renderer, so it carries a still cube itself
        e += [mbox([5.4, 5.4, 5.4], [10.6, 10.6, 10.6], "hull"), mbox([6, 6, 5.2], [10, 10, 5.4], "orange"), mbox([10.6, 6, 6], [10.8, 10, 10], "orange"),
              mbox([6, 10.6, 6], [10, 10.8, 10], "orange")]
    return e


def model(elements):
    return {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
            "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": elements}


if __name__ == "__main__":
    (ASSETS / "models/block/anti_totem.json").write_text(json.dumps(model(cage()), indent=1))
    (ASSETS / "models/item/anti_totem.json").write_text(json.dumps(model(cage(True)), indent=1))
    (ASSETS / "blockstates/anti_totem.json").write_text(json.dumps({"variants": {"": {"model": "bsp_core:block/anti_totem"}}}, indent=2))
    print("anti totem assets written")
