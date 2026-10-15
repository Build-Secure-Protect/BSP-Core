#!/usr/bin/env python3
"""Generates the bank blocks' assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_bank_assets.py

The Bank ATM ("Vault Pillar") is two blocks tall: the lower half carries the whole model (elements run
from y 0 to 31), the upper half is an empty model so nothing draws twice. The screen text, the turning
beacon and the hopper's pulse come from BankAtmRenderer. Both halves face the way the player stood.
Run gen_material_assets.py first when the atlas materials change. Never hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, ROOT, mbox, write_png, item_model, hexc, CLEAR


def rot(el, origin, axis, angle):
    el["rotation"] = {"origin": origin, "axis": axis, "angle": angle}
    return el


def atm():
    e = [mbox([0, 0, 0], [16, 2, 16], "hull2")]
    for x, z in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5)):
        e.append(mbox([x, 2, z], [x + 1.5, 2.8, z + 1.5], "trim"))
    e.append(mbox([1, 2, 2], [15, 29.5, 15], "steel"))
    e += [mbox([2, 29.5, 3], [14, 30.5, 14], "hull2"), mbox([4, 30.5, 5], [12, 31.2, 12], "hull2"), mbox([6, 31.2, 6], [10, 31.8, 10], "hull2")]
    for y in (11, 25):
        e.append(mbox([0.5, y, 1.5], [15.5, y + 1.6, 15.5], "trim"))
        for x in (2, 6, 10, 14):
            e.append(mbox([x - 0.4, y + 0.4, 1.2], [x + 0.4, y + 1.2, 1.5], "hull2"))
    e.append(mbox([0.5, 27, 0], [15.5, 29, 2.5], "hull2"))                       # hood
    e += [mbox([2, 16.5, 1], [14, 27, 2], "hull"), mbox([3.5, 17.5, 0.8], [12.5, 26, 1], "screen")]   # bezel and screen
    for x in (1.4, 13.4):
        for i in range(4):
            e.append(mbox([x, 18 + i * 2.2, 0.9], [x + 1.2, 19.4 + i * 2.2, 2], "amber"))   # key lamps
    e.append(rot(mbox([3.5, 12.5, 0.3], [12.5, 13.5, 5], "trim"), [8, 13, 2], "x", -22.5))   # coin hopper
    e.append(mbox([3.5, 13.5, 0], [12.5, 14.2, 2.2], "trim"))
    e.append(rot(mbox([4.5, 13.5, 0.6], [11.5, 13.8, 2.4], "amber"), [8, 13, 2], "x", -22.5))   # its slit
    e += [mbox([3, 3, 0.3], [13, 5.5, 2], "hull2"), mbox([3.6, 4.6, 0.5], [12.4, 5.4, 1.8], "amber")]   # cash tray
    return e


def drawer_stack():
    """The Safety Deposit Box: a steel cabinet of three drawers with gold trim; the lamp strip and labels are coloured by the renderer."""
    e = [mbox([0, 0, 1], [16, 16, 16], "hull2"), mbox([1, 0.5, 0.7], [15, 15.5, 1], "hull")]
    e += [mbox([0, 15.4, 0], [16, 16, 1], "gold"), mbox([0, 0, 0], [16, 0.6, 1], "gold")]
    for x in (0.2, 15.2):
        for i in range(5):
            e.append(mbox([x, 1 + i * 3.2, 0.2], [x + 0.6, 1.8 + i * 3.2, 0.7], "trim"))
    e.append(mbox([15.3, 1, -0.3], [15.8, 15, 0.2], "screen"))   # lamp strip, dark until the renderer lights it
    for i in range(3):
        y = 1.2 + i * 4.7
        e.append(mbox([1.5, y, 0], [14, y + 4.2, 0.8], "steel"))
        e += [mbox([1.5, y, -0.1], [14, y + 0.35, 0], "gold"), mbox([1.5, y + 3.85, -0.1], [14, y + 4.2, 0], "gold"),
              mbox([1.5, y, -0.1], [1.85, y + 4.2, 0], "gold"), mbox([13.65, y, -0.1], [14, y + 4.2, 0], "gold")]
        e += [mbox([5.7, y + 1.6, -0.8], [9.8, y + 2.4, 0], "trim"), mbox([5.7, y + 1.6, -0.8], [6.4, y + 2.4, 0.3], "gold"), mbox([9.1, y + 1.6, -0.8], [9.8, y + 2.4, 0.3], "gold")]
        e += [mbox([2.3, y + 2.85, -0.12], [5.7, y + 3.7, 0], "gold"), mbox([2.5, y + 3, -0.1], [5.5, y + 3.55, 0], "panel")]
    return e


def facing_blockstate(name):
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"bsp_core:block/{name}"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    return {"variants": variants}


def forbidden_tag():
    out = ROOT / "src/main/resources/data/bsp_core/tags/items"
    out.mkdir(parents=True, exist_ok=True)
    values = [f"bsp_core:{t}_shatter_coin" for t in ("copper", "gold", "diamond", "netherite", "illyrium")] + ["bsp_core:shatter_totem", "bsp_core:decoy_totem"]
    (out / "no_safety_deposit.json").write_text(json.dumps({"replace": False, "values": values}, indent=2))


def vendor_post(lit):
    """The Vendor Post: a steel post with a bolted base, a front lamp (amber when open, dark when closed) and a name plate; the mob is drawn by the renderer."""
    e = [mbox([0, 0, 0], [16, 2, 16], "hull2"), mbox([2, 2, 2], [14, 14, 14], "steel"), mbox([1, 14, 1], [15, 16, 15], "hull2")]
    for x, z in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5)):
        e.append(mbox([x, 2, z], [x + 1.5, 2.8, z + 1.5], "trim"))
    e.append(mbox([6, 6, -0.4], [10, 8, 2], "amber" if lit else "screen"))
    e.append(mbox([4, 9, -0.3], [12, 12, 2], "panel"))
    return e


def land_office():
    """The Land Office, "Deeds Board" (owner's pick, 2026-10-15): a notice board on two steel posts carrying the land map in
    green phosphor with amber and green plot squares, a gold frame, a shelf in front with a brass bell and a stamp, a lamp on an
    arm above, a name plate on the base. The deed cards, the lamp's pulse and the blinking plots are drawn by the renderer."""
    e = [mbox([0, 0, 0], [16, 1, 16], "hull2"), mbox([4, 1, 2], [12, 1.6, 4], "hull")]
    for x in (1.5, 13):
        e.append(mbox([x, 1, 10], [x + 1.5, 15, 11.5], "steel"))
    e += [mbox([0.5, 6, 9.6], [15.5, 15.5, 10], "hull2"), mbox([0.8, 6.3, 9.5], [15.2, 15.2, 9.6], "gold"), mbox([1.6, 7, 9.4], [14.4, 14.4, 9.5], "screen")]
    # the map's plot squares: three for sale in amber, one green claim, one dim open area
    for (x0, y0, x1, y1, m) in ((3.1, 12.2, 6.9, 14.1, "amber"), (8.6, 11.9, 12.4, 13.5, "amber"), (4.2, 8.3, 7.4, 10.1, "phos"), (9, 8, 12.8, 10.2, "amber"), (2.4, 7.4, 13.6, 7.7, "trim")):
        e.append(mbox([x0, y0, 9.3], [x1, y1, 9.4], m))
    e += [mbox([0.5, 4.5, 4.5], [15.5, 6, 10], "steel"), mbox([0.5, 5.9, 4.2], [15.5, 6.3, 10], "gold"),
          mbox([3, 6.3, 6], [5.4, 7.5, 8.4], "gold"), mbox([3.9, 7.5, 6.9], [4.5, 8.4, 7.5], "gold"),
          mbox([10.5, 6.3, 6.2], [13, 7.2, 8.2], "hull2"), mbox([11.2, 7.2, 6.8], [12.3, 8.6, 7.6], "trim"),
          mbox([7, 15.5, 9], [9, 17.5, 10.5], "hull2"), mbox([6.5, 15.5, 5.5], [9.5, 16.3, 9], "hull2"), mbox([6.8, 14.5, 5.8], [9.2, 15.5, 8.6], "amber")]
    return e


def open_blockstate(name):
    variants = {}
    for o in ("true", "false"):
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"bsp_core:block/{name}" + ("_open" if o == "true" else "")}
            if y:
                v["y"] = y
            variants[f"facing={facing},open={o}"] = v
    return {"variants": variants}


def model(elements, gui_scale=None):
    m = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
         "textures": {"atlas": "bsp_core:block/machine_atlas", "particle": "bsp_core:block/machine_atlas"}, "elements": elements}
    if gui_scale:
        m["display"] = {"gui": {"rotation": [30, 225, 0], "translation": [0, -4, 0], "scale": [gui_scale] * 3},
                        "ground": {"scale": [0.25, 0.25, 0.25]}, "fixed": {"scale": [0.5, 0.5, 0.5]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.2, 0.2, 0.2]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]}}
    return m


def blockstate(name):
    variants = {}
    for half in ("lower", "upper"):
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"bsp_core:block/{name}" + ("" if half == "lower" else "_upper")}
            if y:
                v["y"] = y
            variants[f"facing={facing},half={half}"] = v
    return {"variants": variants}


def banker_pixel(x, y):
    """A handheld terminal: dark steel case, a green screen, an amber key and a gold band."""
    if x < 3 or x > 12 or y < 1 or y > 14:
        return CLEAR
    if x in (3, 12) or y in (1, 14):
        return hexc("#2A2F3A")
    if 4 <= x <= 11 and 2 <= y <= 7:
        if x in (4, 11) or y in (2, 7):
            return hexc("#0D3B22")
        return hexc("#58F29A") if (y == 4 and 5 <= x <= 9) or (y == 5 and x in (5, 6)) else hexc("#061008")
    if y == 9:
        return hexc("#E4B84A")
    if 5 <= x <= 7 and 11 <= y <= 12:
        return hexc("#F2B544")
    if 9 <= x <= 10 and 11 <= y <= 12:
        return hexc("#EAE4D5")
    return hexc("#4A5060")


def recipe():
    """The Portable Banker: deliberately dear, so the walk to the bank stays the early-game norm."""
    out = ROOT / "src/main/resources/data/bsp_core/recipes"
    (out / "portable_banker.json").write_text(json.dumps({
        "type": "minecraft:crafting_shaped",
        "pattern": ["ICI", "PRP", "IGI"],
        "key": {"I": {"item": "bsp_core:illyrium_ingot"}, "C": {"item": "bsp_core:charged_resonance_crystal"}, "P": {"item": "bsp_core:tetrium_plate"},
                "R": {"item": "bsp_core:illyrium_processor"}, "G": {"item": "bsp_core:gold_shatter_coin"}},
        "result": {"item": "bsp_core:portable_banker"}}, indent=2))


if __name__ == "__main__":
    write_png(ASSETS / "textures/item/portable_banker.png", 16, 16, banker_pixel)
    item_model("portable_banker")
    recipe()
    (ASSETS / "models/block/bank_atm.json").write_text(json.dumps(model(atm()), indent=1))
    (ASSETS / "models/block/bank_atm_upper.json").write_text(json.dumps(model([]), indent=1))
    (ASSETS / "models/item/bank_atm.json").write_text(json.dumps(model(atm(), 0.3), indent=1))
    (ASSETS / "blockstates/bank_atm.json").write_text(json.dumps(blockstate("bank_atm"), indent=2))
    (ASSETS / "models/block/safety_deposit_box.json").write_text(json.dumps(model(drawer_stack()), indent=1))
    (ASSETS / "models/item/safety_deposit_box.json").write_text(json.dumps(model(drawer_stack(), 0.6), indent=1))
    (ASSETS / "blockstates/safety_deposit_box.json").write_text(json.dumps(facing_blockstate("safety_deposit_box"), indent=2))
    forbidden_tag()
    (ASSETS / "models/block/vendor_post.json").write_text(json.dumps(model(vendor_post(False)), indent=1))
    (ASSETS / "models/block/vendor_post_open.json").write_text(json.dumps(model(vendor_post(True)), indent=1))
    (ASSETS / "models/item/vendor_post.json").write_text(json.dumps(model(vendor_post(True), 0.6), indent=1))
    (ASSETS / "blockstates/vendor_post.json").write_text(json.dumps(open_blockstate("vendor_post"), indent=2))
    (ASSETS / "models/block/land_office.json").write_text(json.dumps(model(land_office()), indent=1))
    (ASSETS / "models/item/land_office.json").write_text(json.dumps(model(land_office(), 0.6), indent=1))
    (ASSETS / "blockstates/land_office.json").write_text(json.dumps(facing_blockstate("land_office"), indent=2))
    print("bank assets written")
