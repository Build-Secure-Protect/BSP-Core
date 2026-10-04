#!/usr/bin/env python3
"""Generates the Shatter Coin Factory multiblock assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_factory_assets.py

Writes the cube textures, models, block states, item models and loot tables of the factory slice
blocks (controller, Factory Frame, Factory Press, Blank Hatch, Power Port) and the static model of
the Motivator. The formed machine itself is drawn at run time by CoinFactoryRenderer. Rerun after
changing anything here; never hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, ROOT, hexc, hsh, mbox, write_png

DATA = ROOT / "src/main/resources/data/bsp_core"
HULL, HULL2, TRIM, MID, PANEL, DARK = "#1E222B", "#2B313D", "#8A909C", "#565C6B", "#C9CED8", "#0C1116"
TQ, ITEM, RF, VIO = "#19D3B0", "#3A8BFF", "#FFD23A", "#B58CFF"


def shade(colour, x, y, seed):
    k = 0.9 + hsh(x, y, seed) * 0.2
    return tuple(min(255, int(v * k)) for v in hexc(colour)[:3]) + (255,)


def frame(x, y):
    """Dark plating with a trim border, corner rivets and a seam."""
    if x in (0, 15) or y in (0, 15):
        return shade(TRIM, x, y, 5)
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        return hexc(PANEL)
    if y in (7, 8) and 3 <= x <= 12:
        return shade(HULL, x, y, 7)
    return shade(HULL2, x, y, 9)


def with_socket(colour):
    def px(x, y):
        if 4 <= x <= 11 and 4 <= y <= 11:
            if x in (4, 11) or y in (4, 11):
                return hexc(TRIM)
            return hexc(colour) if 6 <= x <= 9 and 6 <= y <= 9 else hexc(DARK)
        return frame(x, y)
    return px


def press(x, y):
    if 5 <= x <= 10 and 2 <= y <= 9:
        return shade(PANEL, x, y, 3)
    if 3 <= x <= 12 and 10 <= y <= 12:
        return hexc(TQ) if y == 12 else shade(MID, x, y, 4)
    return frame(x, y)


def front(x, y):
    if 3 <= x <= 12 and 4 <= y <= 10:
        if x in (3, 12) or y in (4, 10):
            return hexc(TRIM)
        if y == 6 and 5 <= x <= 9:
            return hexc(TQ)
        if y == 8 and 5 <= x <= 10:
            return hexc(TQ)
        return hexc(RF) if (x, y) == (10, 6) else hexc(DARK)
    if y == 13 and 2 <= x <= 13:
        return hexc("#E3B341")
    return frame(x, y)


def cube_block(name, tex=None):
    tex = tex or name
    (ASSETS / f"models/block/{name}.json").write_text(json.dumps({"parent": "minecraft:block/cube_all", "textures": {"all": f"bsp_core:block/{tex}"}}, indent=2))
    (ASSETS / f"blockstates/{name}.json").write_text(json.dumps({"variants": {"": {"model": f"bsp_core:block/{name}"}}}, indent=2))
    (ASSETS / f"models/item/{name}.json").write_text(json.dumps({"parent": f"bsp_core:block/{name}"}, indent=2))


def loot(name):
    (DATA / f"loot_tables/blocks/{name}.json").write_text(json.dumps({"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"bsp_core:{name}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]}, indent=2))


def motivator_elements():
    """Motivator A at half a block: base, column, two ringed plates with a glow under each, glowing core."""
    e = [mbox([3, 0, 3], [13, 1.4, 13], "hull2"), mbox([6, 1.4, 6], [10, 6, 10], "hull")]
    for a, b in ((3.4, 3.4), (11.6, 3.4), (3.4, 11.6), (11.6, 11.6)):
        e.append(mbox([a, 1.4, b], [a + 1, 1.9, b + 1], "trim"))
    for h in (2.2, 4.2):
        e.append(mbox([4, h, 4], [12, h + 0.6, 12], "trim"))
        e.append(mbox([3.6, h + 0.6, 3.6], [12.4, h + 1, 12.4], "violet"))
    e.append(mbox([6.5, 6, 6.5], [9.5, 8, 9.5], "violet"))
    return e


def main():
    tex = ASSETS / "textures/block"
    write_png(tex / "factory_frame.png", 16, 16, frame)
    write_png(tex / "factory_press.png", 16, 16, press)
    write_png(tex / "factory_blank_hatch.png", 16, 16, with_socket(ITEM))
    write_png(tex / "factory_power_port.png", 16, 16, with_socket(RF))
    write_png(tex / "shatter_coin_factory_front.png", 16, 16, front)
    for name in ("factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port"):
        cube_block(name)
        loot(name)

    # controller: screen on the front, frame plating elsewhere
    (ASSETS / "models/block/shatter_coin_factory.json").write_text(json.dumps({"parent": "minecraft:block/orientable", "textures": {
        "top": "bsp_core:block/factory_frame", "side": "bsp_core:block/factory_frame", "front": "bsp_core:block/shatter_coin_factory_front"}}, indent=2))
    (ASSETS / "models/item/shatter_coin_factory.json").write_text(json.dumps({"parent": "bsp_core:block/shatter_coin_factory"}, indent=2))
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": "bsp_core:block/shatter_coin_factory"}
        if rot:
            v["y"] = rot
        variants[f"facing={facing}"] = v
    (ASSETS / "blockstates/shatter_coin_factory.json").write_text(json.dumps({"variants": variants}, indent=2))

    (ASSETS / "models/block/factory_motivator.json").write_text(json.dumps({
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
        "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": motivator_elements(),
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]}}}, indent=1))
    (ASSETS / "models/item/factory_motivator.json").write_text(json.dumps({"parent": "bsp_core:block/factory_motivator"}, indent=2))
    (ASSETS / "blockstates/factory_motivator.json").write_text(json.dumps({"variants": {"": {"model": "bsp_core:block/factory_motivator"}}}, indent=2))
    loot("factory_motivator")

    # assets of the removed single-block factory and the Speed Gears
    for stale in ("models/block/shatter_coin_factory_head.json", "textures/block/shatter_coin_factory.png",
                  "models/item/speed_gear_mk1.json", "models/item/speed_gear_mk2.json", "models/item/speed_gear_mk3.json",
                  "textures/item/speed_gear_mk1.png", "textures/item/speed_gear_mk2.png", "textures/item/speed_gear_mk3.png"):
        p = ASSETS / stale
        if p.exists():
            p.unlink()
    print("factory assets written")


if __name__ == "__main__":
    main()
