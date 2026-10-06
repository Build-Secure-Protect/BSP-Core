#!/usr/bin/env python3
"""Generates the Wave Plasma chain's assets and recipes: the fluid's animated textures, the Plasma
Extractor ("Vortex Drum"), Plasma Interface ("Header Rack") and Plasma Repeater ("Inline Coil")
models, their block states, item models, loot tables, tags and recipes, plus the Projector's and
Channel Expander's recipes.

  python3 tools/gen_plasma_assets.py

Portholes, caps and seams use the plasma texture, which animates by itself. Never hand-edit the outputs.
"""
import json
import math

from gen_factory_assets import loot
from gen_material_assets import ASSETS, ROOT, hexc, hsh, item_model, mbox, pal, recolour, vanilla, write_png
from gen_projector_assets import ATLAS, GUI, model, shaped, write

DATA = ROOT / "src/main/resources/data"
CLEAR = (0, 0, 0, 0)
FRAMES = 32
PLASMA = {"atlas": "bsp_core:block/machine_atlas", "plasma": "bsp_core:block/plasma_still", "particle": "bsp_core:block/machine_atlas"}
RAMP = [hexc("#124F9C"), hexc("#4FB8FF"), hexc("#D8F1FF")]


# ----------------------------------------------------------------------------- the fluid
def _hash(x, y, z):
    n = math.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453
    return n - math.floor(n)


def _noise(x, y, z):
    i, j, k = math.floor(x), math.floor(y), math.floor(z)
    u, v, w = x - i, y - j, z - k
    f = lambda t: t * t * (3 - 2 * t)
    u, v, w = f(u), f(v), f(w)
    l = lambda a, b, t: a + (b - a) * t
    return l(l(l(_hash(i, j, k), _hash(i + 1, j, k), u), l(_hash(i, j + 1, k), _hash(i + 1, j + 1, k), u), v),
             l(l(_hash(i, j, k + 1), _hash(i + 1, j, k + 1), u), l(_hash(i, j + 1, k + 1), _hash(i + 1, j + 1, k + 1), u), v), w)


def plasma_pixel(x, y):
    """16 x 16 tiles stacked into FRAMES frames; each frame is a slice through looping 3D noise so the loop is seamless."""
    frame, py = divmod(y, 16)
    a = frame / FRAMES * 2 * math.pi
    t, s = math.cos(a) * 1.2, math.sin(a) * 1.2  # a circle through the noise: frame 32 is frame 0 again
    # the tile wraps because the noise is sampled on a 4-period torus in x and y
    nx, ny = x / 16 * 4, py / 16 * 4
    n = (_noise(nx + t, ny + s, 3.0) * 0.6 + _noise(nx * 2 + s + 9, ny * 2 + t, 7.0) * 0.3 + _noise(nx * 4 + t, ny * 4 + s + 3, 11.0) * 0.1)
    v = max(0.0, min(0.999, (n - 0.25) * 1.9))
    if v < 0.6:
        a0, b0, k = RAMP[0], RAMP[1], v / 0.6
    else:
        a0, b0, k = RAMP[1], RAMP[2], (v - 0.6) / 0.4
    return tuple(int(a0[i] + (b0[i] - a0[i]) * k) for i in range(3)) + (255,)


def fluid():
    for name in ("plasma_still", "plasma_flow"):
        write_png(ASSETS / f"textures/block/{name}.png", 16, 16 * FRAMES, plasma_pixel)
        (ASSETS / f"textures/block/{name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": 2}}, indent=2))


def pbox(frm, to):
    """A box faced entirely with the plasma texture, lit."""
    return {"from": frm, "to": to, "shade": False, "forge_data": {"block_light": 15, "sky_light": 15},
            "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#plasma"} for f in ("north", "south", "east", "west", "up", "down")}}


# ----------------------------------------------------------------------------- extractor: the vortex drum
def extractor():
    e = [mbox([1, 0.5, 1], [15, 2.5, 15], "hull2"), mbox([1, 13, 1], [15, 15, 15], "hull2"), mbox([2.5, 2.5, 2.5], [13.5, 13, 13.5], "hull")]
    for y in (4.5, 7.5, 10.5):  # ribs
        e.append(mbox([2.2, y, 2.2], [13.8, y + 0.8, 13.8], "mid"))
    for x, z in ((3.5, 3.5), (10.5, 3.5), (3.5, 10.5), (10.5, 10.5)):  # intake pipes up to the totem, outlet pipes down to the next drum
        e += [mbox([x, 15, z], [x + 2, 16, z + 2], "trim"), mbox([x, 0, z], [x + 2, 0.5, z + 2], "trim")]
    e.append(mbox([6.5, 15, 6.5], [9.5, 16, 9.5], "mid"))
    for x, z in ((2, 2), (13, 2), (2, 13), (13, 13)):  # bolts on the collar
        e.append(mbox([x, 15, z], [x + 1, 15.6, z + 1], "trim"))
    # portholes: a copper ring with the plasma behind it, one per face; outlet flange with a handle under each
    for f in ("n", "s", "e", "w"):
        if f == "n":
            ring, glass, flange, handle = ([5.5, 5.5, 1.8], [10.5, 10.5, 2.6]), ([6, 6, 1.6], [10, 10, 2.5]), ([6, 2.8, 0.6], [10, 5.4, 2.6]), ([7, 3.4, 0], [9, 4.8, 0.6])
        elif f == "s":
            ring, glass, flange, handle = ([5.5, 5.5, 13.4], [10.5, 10.5, 14.2]), ([6, 6, 13.5], [10, 10, 14.4]), ([6, 2.8, 13.4], [10, 5.4, 15.4]), ([7, 3.4, 15.4], [9, 4.8, 16])
        elif f == "w":
            ring, glass, flange, handle = ([1.8, 5.5, 5.5], [2.6, 10.5, 10.5]), ([1.6, 6, 6], [2.5, 10, 10]), ([0.6, 2.8, 6], [2.6, 5.4, 10]), ([0, 3.4, 7], [0.6, 4.8, 9])
        else:
            ring, glass, flange, handle = ([13.4, 5.5, 5.5], [14.2, 10.5, 10.5]), ([13.5, 6, 6], [14.4, 10, 10]), ([13.4, 2.8, 6], [15.4, 5.4, 10]), ([15.4, 3.4, 7], [16, 4.8, 9])
        e += [mbox(ring[0], ring[1], "orange"), pbox(glass[0], glass[1]), mbox(flange[0], flange[1], "trim"), mbox(handle[0], handle[1], "orange")]
    e.append(mbox([6.5, 10.5, 1.2], [9.5, 12.5, 1.8], "panel"))  # the gauge on the north face
    return e


# ----------------------------------------------------------------------------- interface: the header rack
def interface(refused=False):
    e = [mbox([0, 0, 0], [16, 1, 16], "hull2"), mbox([0, 11, 0], [16, 12, 16], "hull2"), mbox([0.5, 1, 0.5], [15.5, 11, 15.5], "hull")]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        e.append(mbox([x, 0, z], [x + 2, 16, z + 2], "hull2"))
    # two headers across the top, risers down into a bed of small pipes
    for z in (4, 10):
        e.append(mbox([0, 12.5, z], [16, 15, z + 2.5], "trim"))
        e.append(pbox([0.2, 13.3, z + 0.7], [15.8, 14.2, z + 1.8]))
        for x in (3, 7, 11):
            e.append(mbox([x, 11, z + 0.5], [x + 1.4, 12.5, z + 2], "mid"))
    for z in (2.5, 6.5, 10.5):
        e.append(mbox([1, 12, z], [15, 12.8, z + 0.8], "mid"))
    for x in (2.5, 7.6, 12.7):
        e.append(mbox([x, 12, 1], [x + 0.8, 12.8, 15], "mid"))
    # nozzles on the four sides where cables plug in
    for f in ("n", "s", "e", "w"):
        box = {"n": ([5.5, 5.5, 0], [10.5, 10.5, 1.2]), "s": ([5.5, 5.5, 14.8], [10.5, 10.5, 16]), "w": ([0, 5.5, 5.5], [1.2, 10.5, 10.5]), "e": ([14.8, 5.5, 5.5], [16, 10.5, 10.5])}[f]
        inner = {"n": ([6.5, 6.5, 0], [9.5, 9.5, 0.4]), "s": ([6.5, 6.5, 15.6], [9.5, 9.5, 16]), "w": ([0, 6.5, 6.5], [0.4, 9.5, 9.5]), "e": ([15.6, 6.5, 6.5], [16, 9.5, 9.5])}[f]
        e += [mbox(box[0], box[1], "trim"), pbox(inner[0], inner[1])]
    e += [mbox([5.5, 0, 5.5], [10.5, 1.2, 10.5], "trim"), mbox([5.5, 14.8, 5.5], [10.5, 16, 10.5], "trim")]
    if refused:  # a lit seam round the middle: this block is not part of a working interface
        for frm, to in (([-0.2, 5.5, -0.2], [16.2, 6.5, 0.4]), ([-0.2, 5.5, 15.6], [16.2, 6.5, 16.2]), ([-0.2, 5.5, 0.4], [0.4, 6.5, 15.6]), ([15.6, 5.5, 0.4], [16.2, 6.5, 15.6])):
            e.append(mbox(frm, to, "orange"))
    return e


# ----------------------------------------------------------------------------- repeater: the inline coil (along x)
def repeater(up=False):
    """Along +x (east): a dark back cap at x=0, a lit plasma front cap at x=16 with an arrow collar. The up model is the same along +y."""
    e = [mbox([0, 5, 5], [16, 11, 11], "hull2"), mbox([0, 4.5, 4.5], [1.2, 11.5, 11.5], "hull"), pbox([14.8, 4.5, 4.5], [16, 11.5, 11.5]), mbox([13.4, 3.2, 3.2], [14.8, 12.8, 12.8], "trim")]
    # the five copper rings are drawn by PlasmaRepeaterRenderer, so they can pump
    # direction arrows on the top and both sides, pointing out of the front: a shaft and two angled head bars
    for face in ("top", "south", "north"):
        if face == "top":
            shaft, heads, axis = mbox([3, 12.4, 7.4], [11, 13, 8.6], "turq"), (([9, 12.4, 7.4], [13, 13, 8.6]),), "y"
            e.append(shaft)
            for angle, dz in ((45, -1.6), (-45, 1.6)):
                head = mbox([9.5, 12.45, 7.4 + dz], [13.5, 13.05, 8.6 + dz], "turq")
                head["rotation"] = {"origin": [13, 12.7, 8], "axis": "y", "angle": angle}
                e.append(head)
        else:
            z0, z1 = (12.4, 13) if face == "south" else (3, 3.6)
            e.append(mbox([3, 7.4, z0], [11, 8.6, z1], "turq"))
            for angle, dy in ((45, 1.6), (-45, -1.6)):
                head = mbox([9.5, 7.4 + dy, z0 + 0.05], [13.5, 8.6 + dy, z1 + 0.05], "turq")
                head["rotation"] = {"origin": [13, 8, (z0 + z1) / 2], "axis": "z", "angle": angle}
                e.append(head)
    if up:
        out = []
        for el in e:
            (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
            new = dict(el)
            new["from"], new["to"] = [y0, x0, z0], [y1, x1, z1]
            if "rotation" in el:  # swapping x and y turns an arrow head about y into one about x, and mirrors its sense
                r = el["rotation"]
                new["rotation"] = {"origin": [r["origin"][1], r["origin"][0], r["origin"][2]], "axis": {"y": "x", "z": "z", "x": "y"}[r["axis"]], "angle": -r["angle"]}
            out.append(new)
        return out
    return e


# ----------------------------------------------------------------------------- batteries: the capsule, lying along x
TIERS = {"plasma_battery_1": ("#8A909C", "tetrium"), "plasma_battery_2": ("#6F5A7A", "magnatite"), "plasma_battery_3": ("#19D3B0", "illyrium"), "plasma_battery_4": ("#BFE6FF", "charged_illyrium")}
CELLS = {"power_cell_1": "#8A909C", "power_cell_2": "#6F5A7A", "power_cell_3": "#19D3B0"}


def tier_texture(colour):
    """A 16 x 16 tile of the tier's colour with a light edge, for end caps and bands."""
    def px(x, y):
        c = hexc(colour)
        k = 1.15 if x in (0, 15) or y in (0, 15) else 0.9 + hsh(x, y, 7) * 0.2
        return tuple(min(255, int(v * k)) for v in c[:3]) + (255,)
    return px


def tbox(frm, to):
    return {"from": frm, "to": to, "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#tier"} for f in ("north", "south", "east", "west", "up", "down")}}


def battery(tier):
    e = [mbox([4, 3, 4.5], [12, 11, 11.5], "hull"), tbox([1, 2.5, 4], [4, 11.5, 12]), tbox([12, 2.5, 4], [15, 11.5, 12])]
    for k in range(tier + 1):  # one band per tier along the body
        x = 5 + k * (6 / max(1, tier))
        e.append(tbox([x - 0.4, 2.6, 4.2], [x + 0.4, 11.4, 11.8]))
    e += [mbox([4.5, 11, 6.5], [11.5, 11.6, 9.5], "trim"), pbox([4.8, 11.2, 6.8], [11.2, 11.9, 9.2]), mbox([5, 2, 5.5], [11, 3, 10.5], "trim")]
    return e


# ----------------------------------------------------------------------------- charger: the open cradle
def charger():
    e = [mbox([0, 0, 0], [16, 2, 16], "hull2"), mbox([0, 2, 12], [16, 16, 16], "hull2"), mbox([0, 2, 4], [3, 11, 16], "hull2"), mbox([13, 2, 4], [16, 11, 16], "hull2"), mbox([3, 13, 8], [13, 15, 14], "hull2"),
         mbox([1, 11, 8], [2, 15, 14], "trim"), mbox([14, 11, 8], [15, 15, 14], "trim"), mbox([4, 2, 4], [12, 3, 12], "trim"), mbox([4, 12, 4], [12, 13, 12], "trim"),
         pbox([4, 3, 4], [12, 3.6, 12]), pbox([4, 11.4, 4], [12, 12, 12]), mbox([0, 6, 6], [1.2, 10, 10], "trim")]
    for z in (5, 11):
        e.append(mbox([1, 8, z], [2, 13, z + 1], "orange"))
    return e


def cell_sprite(colour):
    """A flat cartridge: dark body, tier cap at the bottom, copper contacts at the top, a plasma window strip."""
    def px(x, y):
        if 4 <= x <= 11 and 2 <= y <= 14:
            if y >= 13:
                return hexc(colour)
            if x in (4, 11) or y == 2:
                return hexc("#565C6B")
            if 6 <= x <= 9 and 4 <= y <= 11:
                return hexc("#4FB8FF") if (x + y) % 3 else hexc("#D8F1FF")
            return hexc("#1E222B")
        if y in (0, 1) and x in (5, 7, 9):
            return hexc("#C87A3C")
        return CLEAR
    return px


ION = pal("#0B3C7A", "#1E6FB8", "#4FB8FF", "#A8DCFF", "#F0FAFF")


def emitter():
    """The Slab tricorder as a held item: a flat body, a plasma screen on the top half, three pads, the cartridge in the bottom edge with its contacts inside."""
    e = [mbox([3, 0, 6], [13, 16, 9], "hull"), mbox([3, 0, 5.6], [13, 16, 6], "trim"), mbox([3.5, 8, 5.4], [12.5, 15, 5.6], "hull2"), pbox([4, 8.5, 5.3], [12, 14.5, 5.7])]
    for x in (4, 7.3, 10.6):
        e.append(mbox([x, 5.5, 5.5], [x + 2.4, 7.5, 5.8], "trim"))
    e += [mbox([4, 3, 5.5], [6, 4, 5.8], "orange"), mbox([5, -2, 6.5], [11, 0.5, 8.5], "hull"), mbox([5, -2, 6.5], [11, -1.4, 8.5], "trim"), pbox([5.5, -1.3, 6.3], [10.5, 0, 6.6])]
    return e


def wrench(x, y):
    """A wrench sprite: a tetrium handle from bottom-left to the head at top-right, open jaws."""
    on_handle = abs((x - 2) - (13 - y)) <= 1 and 2 <= x <= 10 and 5 <= y <= 13
    head = 9 <= x <= 14 and 1 <= y <= 6 and not (12 <= x <= 14 and 1 <= y <= 3) and not (x == 11 and y == 4)
    if on_handle:
        return hexc("#B49CE6") if (x - 2) - (13 - y) == 0 else hexc("#6B4FA3")
    if head:
        return hexc("#C9CED8") if (x + y) % 3 else hexc("#8A909C")
    return CLEAR


def main():
    fluid()
    write_png(ASSETS / "textures/item/wrench.png", 16, 16, wrench)
    item_model("wrench")
    em = model(emitter(), PLASMA)
    em["display"] = {"gui": {"rotation": [20, 200, 0], "translation": [0, -1, 0], "scale": [0.7, 0.7, 0.7]},
                     "thirdperson_righthand": {"rotation": [60, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]}, "thirdperson_lefthand": {"rotation": [60, 0, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
                     "firstperson_righthand": {"rotation": [20, -30, 0], "translation": [1, 2, 0], "scale": [0.5, 0.5, 0.5]}, "firstperson_lefthand": {"rotation": [20, 30, 0], "translation": [-1, 2, 0], "scale": [0.5, 0.5, 0.5]}}
    write(ASSETS / "models/item/wave_emitter.json", em)
    for name, (colour, _) in TIERS.items():
        write_png(ASSETS / f"textures/block/{name}_tier.png", 16, 16, tier_texture(colour))
        tex = dict(PLASMA, tier=f"bsp_core:block/{name}_tier")
        tier = int(name[-1]) - 1
        write(ASSETS / f"models/block/{name}.json", model(battery(tier), tex, gui=False))
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"axis=x": {"model": f"bsp_core:block/{name}"}, "axis=z": {"model": f"bsp_core:block/{name}", "y": 90}}})
        write(ASSETS / f"models/item/{name}.json", model(battery(tier), tex))
    write(ASSETS / "models/block/battery_charger.json", model(charger(), PLASMA, gui=False))
    write(ASSETS / "blockstates/battery_charger.json", {"variants": {"": {"model": "bsp_core:block/battery_charger"}}})
    write(ASSETS / "models/item/battery_charger.json", model(charger(), PLASMA))
    loot("battery_charger")
    for name, colour in CELLS.items():
        write_png(ASSETS / f"textures/item/{name}.png", 16, 16, cell_sprite(colour))
        item_model(name)
    write_png(ASSETS / "textures/item/charged_resonance_crystal.png", 16, 16, recolour(vanilla("item/amethyst_shard.png"), ION))
    item_model("charged_resonance_crystal")
    write(ASSETS / "models/block/plasma_extractor.json", model(extractor(), PLASMA, gui=False))
    write(ASSETS / "blockstates/plasma_extractor.json", {"variants": {"": {"model": "bsp_core:block/plasma_extractor"}}})
    icon = model(extractor(), PLASMA)
    icon["display"]["gui"] = {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}
    write(ASSETS / "models/item/plasma_extractor.json", icon)
    write(ASSETS / "models/block/plasma_interface.json", model(interface(), PLASMA, gui=False))
    write(ASSETS / "models/block/plasma_interface_refused.json", model(interface(True), PLASMA, gui=False))
    write(ASSETS / "blockstates/plasma_interface.json", {"variants": {"refused=false": {"model": "bsp_core:block/plasma_interface"}, "refused=true": {"model": "bsp_core:block/plasma_interface_refused"}}})
    write(ASSETS / "models/item/plasma_interface.json", model(interface(), PLASMA))
    write(ASSETS / "models/block/plasma_repeater.json", model(repeater(), PLASMA, gui=False))
    write(ASSETS / "models/block/plasma_repeater_up.json", model(repeater(True), PLASMA, gui=False))
    write(ASSETS / "blockstates/plasma_repeater.json", {"variants": {
        "facing=east": {"model": "bsp_core:block/plasma_repeater"}, "facing=west": {"model": "bsp_core:block/plasma_repeater", "y": 180},
        "facing=south": {"model": "bsp_core:block/plasma_repeater", "y": 90}, "facing=north": {"model": "bsp_core:block/plasma_repeater", "y": 270},
        "facing=up": {"model": "bsp_core:block/plasma_repeater_up"}, "facing=down": {"model": "bsp_core:block/plasma_repeater_up", "x": 180}}})
    write(ASSETS / "models/item/plasma_repeater.json", model(repeater(), PLASMA))
    for name in ("plasma_extractor", "plasma_interface", "plasma_repeater"):
        loot(name)
    for tag, blocks in (("mineable/pickaxe", ["plasma_extractor", "plasma_interface", "plasma_repeater", "battery_charger"] + list(TIERS)), ("needs_iron_tool", ["plasma_extractor", "plasma_interface", "battery_charger"])):
        path = DATA / f"minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        data["values"] = [v for v in data["values"] if v != "bsp_core:totem_generator"]
        for b in blocks:
            if f"bsp_core:{b}" not in data["values"]:
                data["values"].append(f"bsp_core:{b}")
        path.write_text(json.dumps(data, indent=2) + "\n")
    D, C, I, X, S = "carbon_dust", "charged_magnatite_ingot", "illyrium_ingot", "resonance_crystal", "slag_brick"
    shaped("plasma_extractor", ["PXP", "DHD", "SWS"], {"P": "tetrium_plate", "X": X, "D": D, "H": "machine_chassis", "S": S, "W": "copper_tetrium_coil"})
    shaped("plasma_interface", ["SCS", "CBC", "SAS"], {"S": S, "C": "tetrium_coil", "B": "basic_control_circuit", "A": "minecraft:amethyst_shard"}, 2)
    shaped("plasma_repeater", ["DCD", "TXT", "BNB"], {"D": D, "C": C, "T": "tetrium_core_cable", "X": X, "B": "minecraft:blaze_powder", "N": "illyrium_nugget"})
    shaped("totem_projector", ["DXD", "CKC", "XHP"], {"D": D, "X": X, "C": C, "K": "illyrium_processor", "P": "tetrium_plate", "H": "machine_chassis"})
    shaped("channel_expander", ["CBC", "BKB", "CDC"], {"C": C, "B": "basic_control_circuit", "K": "illyrium_processor", "D": D})
    R, G = "charged_resonance_crystal", "illyrium_glass"
    shaped("battery_charger", ["SWS", "THT", "PBP"], {"S": S, "W": "copper_tetrium_coil", "T": "tetrium_core_cable", "H": "machine_chassis", "P": "tetrium_plate", "B": "basic_control_circuit"})
    shaped("plasma_battery_1", ["PGP", "RDR", "SWS"], {"P": "tetrium_plate", "G": G, "R": R, "D": D, "S": S, "W": "copper_tetrium_coil"})
    shaped("plasma_battery_2", ["MRM", "GBG", "MQM"], {"M": "magnatite_ingot", "R": R, "G": G, "B": "plasma_battery_1", "Q": "minecraft:quartz"})
    shaped("plasma_battery_3", ["IRI", "GBG", "IEI"], {"I": I, "R": R, "G": G, "B": "plasma_battery_2", "E": "minecraft:ender_pearl"})
    shaped("plasma_battery_4", ["CRC", "GBG", "NKN"], {"C": C, "R": R, "G": G, "B": "plasma_battery_3", "N": "minecraft:netherite_ingot", "K": "minecraft:shulker_shell"})
    shaped("power_cell_1", ["DRD", "PLP", " W "], {"D": D, "R": R, "P": "tetrium_plate", "L": "minecraft:glass_bottle", "W": "copper_tetrium_coil"})
    shaped("power_cell_2", ["MRM", "MBM", "MQM"], {"M": "magnatite_ingot", "R": R, "B": "power_cell_1", "Q": "minecraft:quartz"})
    shaped("power_cell_3", ["IRI", "IBI", "IEI"], {"I": I, "R": R, "B": "power_cell_2", "E": "minecraft:ender_pearl"})
    shaped("wrench", ["T T", " I ", " S "], {"T": "tetrium_ingot", "I": "tetrium_ingot", "S": "minecraft:stick"})
    shaped("wave_emitter", ["PXP", "ZKZ", "DCD"], {"P": "tetrium_plate", "X": X, "Z": "minecraft:blaze_rod", "K": "illyrium_processor", "D": D, "C": C})
    print("plasma assets written")


if __name__ == "__main__":
    main()
