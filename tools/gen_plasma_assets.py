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
def interface_extractor_tube():
    """North side touching an extractor: a tube from the panel into the extractor's porthole, plasma showing through its glass."""
    return [mbox([5, 5, -0.6], [11, 11, 0], "trim"), glass([5.6, 5.6, -0.5], [10.4, 10.4, 0.1]), pbox([6.2, 6.2, -0.45], [9.8, 9.8, 0.05])]


def interface_nozzle():
    """North side touching a cable: the nozzle the cable's arm meets, with its lit window."""
    return [mbox([5.5, 5.5, -1.2], [10.5, 10.5, -0.3], "trim"), pbox([6.5, 6.5, -1.25], [9.5, 9.5, -0.9])]


def interface_face():
    """One outer face (north): a raised rim with a bolt at each corner, a recessed panel inside it, vent slots top and bottom.
    The lit cross on the panel and the frame round the group are drawn by PlasmaInterfaceRenderer, since they depend on the neighbours."""
    e = [mbox([2, 13.2, -0.5], [14, 14, 0.3], "trim"), mbox([2, 2, -0.5], [14, 2.8, 0.3], "trim"), mbox([2, 2.8, -0.5], [2.8, 13.2, 0.3], "trim"), mbox([13.2, 2.8, -0.5], [14, 13.2, 0.3], "trim"),
         mbox([2.8, 2.8, -0.25], [13.2, 13.2, 0.1], "mid"),
         mbox([3.6, 11, -0.45], [6.4, 11.8, -0.2], "hull2"), mbox([9.6, 11, -0.45], [12.4, 11.8, -0.2], "hull2"),
         mbox([3.6, 4.2, -0.45], [6.4, 5, -0.2], "hull2"), mbox([9.6, 4.2, -0.45], [12.4, 5, -0.2], "hull2")]
    for x, y in ((2.1, 13.3), (13.3, 13.3), (2.1, 2.1), (13.3, 2.1)):
        e.append(mbox([x, y, -0.7], [x + 0.6, y + 0.6, -0.45], "hull2"))
    return e


def interface_core():
    """The body: a full cube whose faces are culled against a joined neighbour, so touching interfaces read as one block."""
    core = mbox([0, 0, 0], [16, 16, 16], "hull")
    for f in core["faces"]:
        core["faces"][f]["cullface"] = f
    return [core]


def interface_cross():
    """The lit cross as the item shows it (north face); in the world the renderer draws it in the group's colour."""
    return [mbox([2.8, 7.2, -0.9], [13.2, 8.8, -0.3], "turq"), mbox([7.2, 2.8, -0.9], [8.8, 13.2, -0.3], "turq"), mbox([6.3, 6.3, -1.2], [9.7, 9.7, -0.3], "turq")]


def turned_side(elements, side):
    """Elements built for the north face, turned to another side (the same turns the blockstate applies)."""
    out = []
    for el in elements:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        new = dict(el)
        if side == "south":
            new["from"], new["to"] = [16 - x1, y0, 16 - z1], [16 - x0, y1, 16 - z0]
        elif side == "east":
            new["from"], new["to"] = [16 - z1, y0, x0], [16 - z0, y1, x1]
        elif side == "west":
            new["from"], new["to"] = [z0, y0, 16 - x1], [z1, y1, 16 - x0]
        elif side == "up":
            new["from"], new["to"] = [x0, 16 - z1, y0], [x1, 16 - z0, y1]
        elif side == "down":
            new["from"], new["to"] = [x0, z0, 16 - y1], [x1, z1, 16 - y0]
        new["faces"] = {f: {"uv": v["uv"], "texture": v["texture"]} for f, v in el["faces"].items()}
        out.append(new)
    return out


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


# ----------------------------------------------------------------------------- valve: the gate wheel (along x)
def valve(up=False):
    """A tube between two collars with a window pair on each side, a bonnet on top for the hand wheel, a gauge on the south
    face and a contact plate underneath. The wheel, needle, lamp and the plasma in the windows are drawn by PlasmaValveRenderer.
    The cavity behind the windows is lined dark like the Projector Base. The up model is the same turned a quarter about z."""
    left, right = mbox([0, 3.6, 3.6], [1.4, 12.4, 12.4], "trim"), mbox([14.6, 3.6, 3.6], [16, 12.4, 12.4], "trim")
    lined(left, "east")
    lined(right, "west")
    top, bottom = mbox([1.4, 10.7, 4.5], [14.6, 11.5, 11.5], "hull2"), mbox([1.4, 4.5, 4.5], [14.6, 5.3, 11.5], "hull2")
    lined(top, "down")
    lined(bottom, "up")
    e = [left, right, top, bottom, mbox([5.5, 11.5, 5.5], [10.5, 14, 10.5], "hull"), mbox([7.5, 14, 7.5], [8.5, 15, 8.5], "trim"),
         mbox([5.5, 5.5, 11.5], [10.5, 10.5, 12], "mid"), mbox([5, 3.9, 5], [11, 4.5, 11], "mid")]
    for z0, z1, inward in ((4.5, 5.3, "south"), (10.7, 11.5, "north")):
        for x0, x1 in ((1.4, 2), (5.4, 10.6), (14, 14.6)):  # the wall between and beside the windows
            seg = mbox([x0, 5.3, z0], [x1, 10.7, z1], "hull2")
            lined(seg, inward)
            e.append(seg)
        for x0, x1 in ((2, 5.4), (10.6, 14)):  # the windows: inlet pair and outlet pair
            e.append(glass([x0, 5.3, z0 + 0.1], [x1, 10.7, z1 - 0.1], ("north", "south")))
    if not up:
        return e
    turn = {"east": "up", "up": "west", "west": "down", "down": "east", "north": "north", "south": "south"}
    out = []
    for el in e:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        new = dict(el)
        new["from"], new["to"] = [round(16 - y1, 3), x0, z0], [round(16 - y0, 3), x1, z1]
        new["faces"] = {turn[f]: v for f, v in el["faces"].items()}
        out.append(new)
    return out


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
         mbox([5.2, 5.2, 15.6], [10.8, 10.8, 16.4], "trim")]  # the cable port on the back; the pads and the port window are lit by BatteryChargerRenderer while fed
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


def projector_base():
    """A full block whose middle is a glass tank: solid plates top and bottom, four corner posts, window frames on every face with the glass behind them,
    a cable nozzle in the middle of each face at cable height, and a collar on top for the projector. The cavity behind the glass is lined dark so an
    empty tank reads as empty; the plasma inside is drawn by ProjectorBaseRenderer (through PlasmaRender) at the tank's level."""
    floor, ceiling = mbox([0.5, 2, 0.5], [15.5, 4.5, 15.5], "hull"), mbox([0.5, 11.5, 0.5], [15.5, 14, 15.5], "hull")
    lined(floor, "up")
    lined(ceiling, "down")
    e = [mbox([0, 0, 0], [16, 2, 16], "hull2"), mbox([0, 14, 0], [16, 16, 16], "hull2"), floor, ceiling,
         glass([0.4, 4.5, 0.4], [15.6, 11.5, 15.6], ("north", "south", "east", "west")), mbox([1.5, 16, 1.5], [14.5, 16.6, 14.5], "trim")]
    for x, z in ((0, 0), (14, 0), (0, 14), (14, 14)):
        # each post in three: the part inside the band shows its inward faces, lined dark like the rest of the cavity
        e.append(mbox([x, 0, z], [x + 2, 4.5, z + 2], "hull2"))
        e.append(mbox([x, 11.5, z], [x + 2, 16, z + 2], "hull2"))
        post = mbox([x, 4.5, z], [x + 2, 11.5, z + 2], "hull2")
        lined(post, "east" if x == 0 else "west")
        lined(post, "south" if z == 0 else "north")
        e.append(post)
    for y in (3.7, 11.5):  # rims above and below the windows
        e.append(mbox([-0.1, y, -0.1], [16.1, y + 0.8, 16.1], "mid"))
    for f in ("n", "s", "e", "w"):
        # window frames either side of the nozzle: thin mullions at the window edges
        for a0, a1 in ((2, 2.6), (4.6, 5.2), (10.8, 11.4), (13.4, 14)):
            bar = {"n": ([a0, 4.5, -0.1], [a1, 11.5, 0.5]), "s": ([a0, 4.5, 15.5], [a1, 11.5, 16.1]), "w": ([-0.1, 4.5, a0], [0.5, 11.5, a1]), "e": ([15.5, 4.5, a0], [16.1, 11.5, a1])}[f]
            e.append(mbox(bar[0], bar[1], "trim"))
        box = {"n": ([5.2, 5.2, -0.4], [10.8, 10.8, 1.2]), "s": ([5.2, 5.2, 14.8], [10.8, 10.8, 16.4]), "w": ([-0.4, 5.2, 5.2], [1.2, 10.8, 10.8]), "e": ([14.8, 5.2, 5.2], [16.4, 10.8, 10.8])}[f]
        e.append(mbox(box[0], box[1], "trim"))  # the nozzle; the window inside is the plasma, drawn by the renderer while the base is fed
    return e


def lined(element, face):
    """Turns one face of a box into the dark tank lining."""
    element["faces"][face] = {"uv": [0, 0, 16, 16], "texture": "#inner"}


def tank_inner(x, y):
    """The inside of a tank: near-black blue with a faint panel seam, so plasma stands out against it and an empty tank looks hollow."""
    k = 0.85 + hsh(x, y, 67) * 0.3
    if x == 7 or y == 7:
        k *= 1.35
    return (int(10 * k), int(15 * k), int(22 * k), 255)


def tank_glass(x, y):
    """A clear pane with a faint blue cast and a lighter rim, for tank walls: what is behind it must stay easy to see."""
    if x in (0, 15) or y in (0, 15):
        return (130, 165, 190, 140)
    k = 0.9 + hsh(x, y, 61) * 0.2
    return (int(90 * k), int(130 * k), int(160 * k), 34)


def glass(frm, to, faces=("north", "south", "east", "west", "up", "down")):
    """A see-through tank wall; the plasma inside is drawn by the renderer. A band between plates passes four faces so nothing fights the plates."""
    return {"from": frm, "to": to, "shade": False, "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#glass"} for f in faces}}


def main():
    fluid()
    write_png(ASSETS / "textures/block/tank_glass.png", 16, 16, tank_glass)
    write_png(ASSETS / "textures/block/tank_inner.png", 16, 16, tank_inner)
    base_tex = dict(PLASMA, glass="bsp_core:block/tank_glass", inner="bsp_core:block/tank_inner")
    base = model(projector_base(), base_tex, gui=False)
    base["render_type"] = "minecraft:translucent"
    write(ASSETS / "models/block/projector_base.json", base)
    write(ASSETS / "blockstates/projector_base.json", {"variants": {"": {"model": "bsp_core:block/projector_base"}}})
    base_item = model(projector_base(), base_tex)
    base_item["render_type"] = "minecraft:translucent"
    write(ASSETS / "models/item/projector_base.json", base_item)
    loot("projector_base")
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
    write(ASSETS / "blockstates/battery_charger.json", {"variants": {"facing=north": {"model": "bsp_core:block/battery_charger"}, "facing=east": {"model": "bsp_core:block/battery_charger", "y": 90},
                                                                     "facing=south": {"model": "bsp_core:block/battery_charger", "y": 180}, "facing=west": {"model": "bsp_core:block/battery_charger", "y": 270}}})
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
    tube = model(interface_extractor_tube(), dict(PLASMA, glass="bsp_core:block/tank_glass"), gui=False)
    tube["render_type"] = "minecraft:translucent"
    write(ASSETS / "models/block/plasma_interface_tube.json", tube)
    write(ASSETS / "models/block/plasma_interface_nozzle.json", model(interface_nozzle(), PLASMA, gui=False))
    write(ASSETS / "models/block/plasma_interface_face.json", model(interface_face(), PLASMA, gui=False))
    write(ASSETS / "models/block/plasma_interface.json", model(interface_core(), PLASMA, gui=False))
    parts = [{"apply": {"model": "bsp_core:block/plasma_interface"}}]
    for side, rot in (("north", {}), ("east", {"y": 90}), ("south", {"y": 180}), ("west", {"y": 270}), ("up", {"x": 270}), ("down", {"x": 90})):
        parts.append({"when": {side: "none|drum|cable"}, "apply": dict({"model": "bsp_core:block/plasma_interface_face"}, **rot)})
        parts.append({"when": {side: "drum"}, "apply": dict({"model": "bsp_core:block/plasma_interface_tube"}, **rot)})
        parts.append({"when": {side: "cable"}, "apply": dict({"model": "bsp_core:block/plasma_interface_nozzle"}, **rot)})
    write(ASSETS / "blockstates/plasma_interface.json", {"multipart": parts})
    item = interface_core()
    for side in ("north", "south", "east", "west", "up", "down"):
        item += turned_side(interface_face() + interface_cross(), side)
    write(ASSETS / "models/item/plasma_interface.json", model(item, PLASMA))
    write(ASSETS / "models/block/plasma_repeater.json", model(repeater(), PLASMA, gui=False))
    write(ASSETS / "models/block/plasma_repeater_up.json", model(repeater(True), PLASMA, gui=False))
    write(ASSETS / "blockstates/plasma_repeater.json", {"variants": {
        "facing=east": {"model": "bsp_core:block/plasma_repeater"}, "facing=west": {"model": "bsp_core:block/plasma_repeater", "y": 180},
        "facing=south": {"model": "bsp_core:block/plasma_repeater", "y": 90}, "facing=north": {"model": "bsp_core:block/plasma_repeater", "y": 270},
        "facing=up": {"model": "bsp_core:block/plasma_repeater_up"}, "facing=down": {"model": "bsp_core:block/plasma_repeater_up", "x": 180}}})
    write(ASSETS / "models/item/plasma_repeater.json", model(repeater(), PLASMA))
    valve_tex = dict(PLASMA, glass="bsp_core:block/tank_glass", inner="bsp_core:block/tank_inner")
    for name, geo in (("plasma_valve", valve()), ("plasma_valve_up", valve(True))):
        m = model(geo, valve_tex, gui=False)
        m["render_type"] = "minecraft:translucent"
        write(ASSETS / f"models/block/{name}.json", m)
    write(ASSETS / "blockstates/plasma_valve.json", {"variants": {"axis=x": {"model": "bsp_core:block/plasma_valve"}, "axis=z": {"model": "bsp_core:block/plasma_valve", "y": 90},
                                                                  "axis=y": {"model": "bsp_core:block/plasma_valve_up"}}})
    vi = model(valve(), valve_tex)
    vi["render_type"] = "minecraft:translucent"
    write(ASSETS / "models/item/plasma_valve.json", vi)
    loot("plasma_valve")
    for name in ("plasma_extractor", "plasma_interface", "plasma_repeater"):
        loot(name)
    for tag, blocks in (("mineable/pickaxe", ["plasma_extractor", "plasma_interface", "plasma_repeater", "plasma_valve", "battery_charger", "projector_base"] + list(TIERS)), ("needs_iron_tool", ["plasma_extractor", "plasma_interface", "plasma_valve", "battery_charger", "projector_base"])):
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
    shaped("plasma_valve", ["PCP", "TLT", "PRP"], {"P": "tetrium_plate", "C": "minecraft:copper_ingot", "T": "tetrium_core_cable", "L": "minecraft:lever", "R": "minecraft:redstone"})
    shaped("plasma_repeater", ["DCD", "TXT", "BNB"], {"D": D, "C": C, "T": "tetrium_core_cable", "X": X, "B": "minecraft:blaze_powder", "N": "illyrium_nugget"})
    shaped("projector_base", ["PGP", "THT", "SWS"], {"P": "tetrium_plate", "G": "illyrium_glass", "T": "tetrium_core_cable", "H": "machine_chassis", "S": S, "W": "copper_tetrium_coil"})
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
