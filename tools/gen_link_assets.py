#!/usr/bin/env python3
"""Writes the Plasma Accelerator and Decelerator's block assets: Accelerator Intake, Accelerator Frame, Decelerator Core and
Decelerator Port (models from the machine atlas, block states with the formed variants, item models, loot tables, tool tags).
These are the placeholder cube looks; the formed ring and the formed decelerator are drawn by their renderers.

  python3 tools/gen_link_assets.py

Never hand-edit the outputs.
"""
import json

from gen_factory_assets import loot
from gen_material_assets import ASSETS, hexc, item_model, mbox, write_png
from gen_plasma_assets import PLASMA, pbox
from gen_projector_assets import DATA, model, shaped, write

CLEAR = (0, 0, 0, 0)


def link_card(x, y):
    """A pale card with a copper stripe, a dark chip and three turquoise contact lines: the address goes on the chip."""
    if not (3 <= x <= 12 and 1 <= y <= 14):
        return CLEAR
    if x in (3, 12) or y in (1, 14):
        return hexc("#8A909C")
    if y in (3, 4):
        return hexc("#C87A3C")
    if 5 <= x <= 10 and 7 <= y <= 11:
        if y in (8, 10) and x in (6, 8, 10):
            return hexc("#19D3B0")
        return hexc("#1E222B")
    return hexc("#C9CED8")


def link_expander(x, y):
    """A copper coil on a dark core with a blue window: like the Channel Expander, in the accelerator's copper."""
    if 2 <= x <= 13 and 3 <= y <= 12:
        if x in (2, 13) or y in (3, 12):
            return hexc("#8A909C")
        if x in (4, 6, 8, 10) and 5 <= y <= 10:
            return hexc("#C87A3C")
        if x in (5, 7, 9, 11) and 5 <= y <= 10:
            return hexc("#8F5524")
        return hexc("#1E222B")
    if y in (1, 2, 13, 14) and x in (5, 8, 11):
        return hexc("#4FB8FF")
    return CLEAR


HORIZONTAL = {"north": 0, "east": 90, "south": 180, "west": 270}


def bolts(e, y, mat="trim"):
    for x, z in ((1, 1), (13.5, 1), (1, 13.5), (13.5, 13.5)):
        e.append(mbox([x, y, z], [x + 1.5, y + 0.6, z + 1.5], mat))


def frame(formed):
    """A hull block wrapped in three coil bands; the bands light up (orange) once the ring is formed."""
    band = "orange" if formed else "mid"
    e = [mbox([0.5, 0.5, 0.5], [15.5, 15.5, 15.5], "hull2")]
    for y in (2.5, 7, 11.5):
        e.append(mbox([0, y, 0], [16, y + 2, 16], band))
    bolts(e, 15.5)
    return e


def intake(formed):
    """The frame's block with the plasma porthole on the front (north) and the pipe flange below, where the cable comes in."""
    e = frame(formed)
    e += [mbox([4, 4, -0.8], [12, 12, 0], "hull"), pbox([5, 5, -1.2], [11, 11, -0.8]), mbox([4.4, 4.4, -1.4], [11.6, 11.6, -1.2], "trim")]
    e.append(mbox([5, -0.2, 5], [11, 0.5, 11], "steel"))
    return e


def core(formed):
    """A cradle: a hull base, four posts, the dark sphere stand-in held by four trim arms; the arms glow when formed."""
    arm = "orange" if formed else "trim"
    e = [mbox([0, 0, 0], [16, 2.5, 16], "hull2"), mbox([0, 13.5, 0], [16, 16, 16], "hull2")]
    for x, z in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
        e.append(mbox([x, 2.5, z], [x + 2, 13.5, z + 2], "steel"))
    e.append(mbox([5, 5, 5], [11, 11, 11], "hull"))
    e += [mbox([0.5, 7, 7], [5, 9, 9], arm), mbox([11, 7, 7], [15.5, 9, 9], arm), mbox([7, 7, 0.5], [9, 9, 5], arm), mbox([7, 7, 11], [9, 9, 15.5], arm)]
    bolts(e, 16)
    return e


def port(formed):
    """A hull block with a plasma window on each side; the windows are dark until the decelerator is formed."""
    e = [mbox([0.5, 0.5, 0.5], [15.5, 15.5, 15.5], "hull2"), mbox([0, 6, 0], [16, 10, 16], "mid")]
    if formed:
        for f in ("n", "s", "e", "w"):
            if f == "n":
                e.append(pbox([5, 5, -0.4], [11, 11, 0.5]))
            elif f == "s":
                e.append(pbox([5, 5, 15.5], [11, 11, 16.4]))
            elif f == "e":
                e.append(pbox([15.5, 5, 5], [16.4, 11, 11]))
            else:
                e.append(pbox([-0.4, 5, 5], [0.5, 11, 11]))
    else:
        e += [mbox([5, 5, -0.4], [11, 11, 0.5], "hull"), mbox([5, 5, 15.5], [11, 11, 16.4], "hull"), mbox([15.5, 5, 5], [16.4, 11, 11], "hull"), mbox([-0.4, 5, 5], [0.5, 11, 11], "hull")]
    bolts(e, 15.5)
    return e


def main():
    tex = dict(PLASMA, steel="bsp_core:block/machine_atlas")
    for name, build, facing in (("accelerator_frame", frame, False), ("accelerator_intake", intake, True), ("decelerator_core", core, True), ("decelerator_port", port, False)):
        for formed in (False, True):
            suffix = "_formed" if formed else ""
            write(ASSETS / f"models/block/{name}{suffix}.json", model(build(formed), tex, gui=False))
        variants = {}
        for formed in (False, True):
            m = f"bsp_core:block/{name}{'_formed' if formed else ''}"
            if facing:
                for d, y in HORIZONTAL.items():
                    v = {"model": m}
                    if y:
                        v["y"] = y
                    if name == "accelerator_intake":  # glow is the block's light level; the look does not change with it
                        for glow in range(3):
                            variants[f"facing={d},formed={str(formed).lower()},glow={glow}"] = v
                    else:
                        variants[f"facing={d},formed={str(formed).lower()}"] = v
            else:
                variants[f"formed={str(formed).lower()}"] = {"model": m}
        write(ASSETS / f"blockstates/{name}.json", {"variants": variants})
        write(ASSETS / f"models/item/{name}.json", model(build(False), tex))
        loot(name)
    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        path = DATA / f"minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        for b in ("accelerator_intake", "accelerator_frame", "decelerator_core", "decelerator_port"):
            if f"bsp_core:{b}" not in data["values"]:
                data["values"].append(f"bsp_core:{b}")
        path.write_text(json.dumps(data, indent=2) + "\n")
    # recipes: end game, through Illyrium, the charged crystal and the Ender's things; the intake asks for a Nether Star
    P, H, I, R, Z, C, E = "tetrium_plate", "machine_chassis", "illyrium_ingot", "charged_resonance_crystal", "illyrium_processor", "charged_illyrium_core_cable", "minecraft:ender_eye"
    shaped("accelerator_frame", ["PIP", "IHI", "PIP"], {"P": P, "I": I, "H": H}, 2)
    shaped("accelerator_intake", ["RSR", "ZCZ", "PHP"], {"R": R, "S": "minecraft:nether_star", "Z": Z, "C": C, "P": P, "H": H})
    shaped("decelerator_core", ["RER", "IGI", "PHP"], {"R": R, "E": E, "I": I, "G": "illyrium_glass", "P": P, "H": H})
    shaped("decelerator_port", ["PCP", "CHC", "PCP"], {"P": P, "C": C, "H": H})
    shaped("link_card", ["NEN", "QBQ"], {"N": "illyrium_nugget", "E": "minecraft:ender_pearl", "Q": "minecraft:paper", "B": "basic_control_circuit"})
    shaped("link_expander", [" I ", "ZEZ", " I "], {"I": I, "Z": Z, "E": E})  # two ingots and two processors round the eye: half the first cut, which cost more than the intake
    for name, pixel in (("link_card", link_card), ("link_expander", link_expander)):
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, pixel)
        item_model(name)
    print("wrote the accelerator and decelerator assets")


if __name__ == "__main__":
    main()
