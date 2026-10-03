#!/usr/bin/env python3
"""Shatter Totem concept generator (no third-party dependencies).

Produces, for three model variants, Minecraft 1.20.1 block-model JSON plus a shared 64x64 texture
atlas rendered in several colour states. Used both to render the concept previews and, once a
variant is chosen, to emit the real mod assets.

Usage:
  python3 tools/gen_totem_concepts.py bundle <out.json>      # preview bundle (base64 PNGs, many frames)
  python3 tools/gen_totem_concepts.py assets <variant>       # write mod assets into src/main/resources
"""
import base64
import json
import random
import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bsp_core"

ATLAS = 64           # px
UV = 16 / ATLAS      # uv units per px

# ----------------------------------------------------------------------------- atlas layout (px)
SKIN = (0, 0, 32, 32)          # mottled skin tile; faces sample 1:1 sub-rects
FACE = (32, 0, 40, 8)          # creeper face (south face of head)
ORB = (40, 0, 48, 8)           # glowing orb
TORSO_FRONT = (48, 0, 56, 12)  # skin with the shatter crack
PLINTH = (32, 16, 48, 32)      # dark stone base
PLINTH_SIDE = (48, 16, 64, 20) # stone side strip
ORBLET = (56, 0, 60, 4)        # white upgrade orb, tinted per buff level in game


def mottle(w, h, seed):
    rnd = random.Random(seed)
    grid = []
    for y in range(h):
        row = []
        for x in range(w):
            r = rnd.random()
            row.append("S1" if r < 0.11 else "S2" if r < 0.20 else "S0")
        grid.append(row)
    return grid


def build_role_map():
    """64x64 grid of role names; palettes turn roles into colours."""
    g = [["X"] * ATLAS for _ in range(ATLAS)]

    def put(region, rows):
        x0, y0, x1, y1 = region
        for dy, row in enumerate(rows):
            for dx, role in enumerate(row):
                if y0 + dy < y1 and x0 + dx < x1:
                    g[y0 + dy][x0 + dx] = role

    put(SKIN, mottle(32, 32, 7))

    # creeper face: eyes and mouth in dark, rest mottled skin
    face = mottle(8, 8, 11)
    for (x, y) in [(1, 2), (2, 2), (1, 3), (2, 3), (5, 2), (6, 2), (5, 3), (6, 3),
                   (3, 4), (4, 4), (2, 5), (3, 5), (4, 5), (5, 5), (2, 6), (5, 6)]:
        face[y][x] = "F"
    put(FACE, face)

    # orb: ender-pearl look; dark teal-black base, green swirl, a few bright flecks
    orb = [list(r) for r in [
        "22222222",
        "21111122",
        "21100112",
        "21011012",
        "21011012",
        "21100112",
        "22111122",
        "22222222"]]
    orb = [["O" + c for c in row] for row in orb]
    orb[2][5] = "O3"; orb[3][2] = "O3"; orb[5][3] = "O3"; orb[4][6] = "O3"
    put(ORB, orb)

    # torso front: skin with a jagged crack and glow at its edges
    torso = mottle(8, 12, 23)
    crack = [(4, 0), (4, 1), (3, 2), (3, 3), (4, 4), (4, 5), (5, 6), (4, 7), (3, 8), (3, 9), (4, 10), (4, 11)]
    for (x, y) in crack:
        torso[y][x] = "C"
        for nx in (x - 1, x + 1):
            if 0 <= nx < 8 and torso[y][nx] != "C":
                torso[y][nx] = "S2"
    put(TORSO_FRONT, torso)

    put(ORBLET, [["W1", "W0", "W0", "W1"], ["W0", "W0", "W0", "W0"], ["W0", "W0", "W0", "W0"], ["W1", "W0", "W0", "W1"]])

    rnd = random.Random(5)
    put(PLINTH, [["P1" if rnd.random() < 0.12 else "P0" for _ in range(16)] for _ in range(16)])
    put(PLINTH_SIDE, [["P0" if y < 3 else "P2" for _ in range(16)] for y in range(4)])
    return g


ROLES = build_role_map()

# Vertical gradient: the body fades to the plinth colour toward its base. Skin-tile rows map to model
# height (row 0 = y 32 ... row 31 = y 1) and elements sample the tile at rows matching their own y,
# so the fade lines up across every cuboid. The torso-front strip is mapped from the torso top down.
TORSO_TOP_Y = 16          # Guardian torso top (set for the chosen variant)
GRADIENT_FLOOR_Y = 2      # plinth top: fully plinth-coloured here
GRADIENT_SPAN = 12        # pure body colour this many px above the floor


def model_y_map():
    m = [[None] * ATLAS for _ in range(ATLAS)]
    x0, y0, x1, y1 = SKIN
    for row in range(y0, y1):
        for col in range(x0, x1):
            m[row][col] = 32 - (row - y0)
    x0, y0, x1, y1 = TORSO_FRONT
    for row in range(y0, y1):
        for col in range(x0, x1):
            m[row][col] = TORSO_TOP_Y - (row - y0)
    return m


MODEL_Y = model_y_map()


def gradient_weight(y):
    """0 = plinth colour, 1 = full body colour."""
    t = max(0.0, min(1.0, (y - GRADIENT_FLOOR_Y) / GRADIENT_SPAN))
    return t ** 0.75


# ----------------------------------------------------------------------------- palettes
def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


# The orb never changes colour: ender-pearl deep green/blue/black with pale flecks.
ORB_COLOURS = {"O0": hexc("#1E7A62"), "O1": hexc("#0F3F3A"), "O2": hexc("#071A1F"), "O3": hexc("#9CF5D6")}


def skin_palette(base, dark, light, *_ignored):
    return {
        "S0": hexc(base), "S1": hexc(dark), "S2": hexc(light),
        "F": hexc("#101418"),
        **ORB_COLOURS,
        "C": hexc("#0B0F14"),
        "P0": hexc("#2B2B33"), "P1": hexc("#3A3A45"), "P2": hexc("#1F1F26"),
        "W0": hexc("#FFFFFF"), "W1": hexc("#C9CED8"),
        "X": (0, 0, 0, 0),
    }


TURQUOISE = skin_palette("#2EC4B6", "#1A8A80", "#7FE8DD", "#FFFFFF", "#CFFFF8", "#5EEAD4")
BLUE = skin_palette("#3A6FE0", "#24479C", "#8DB3FF", "#FFFFFF", "#D6E4FF", "#6E9BFF")
GOLD = skin_palette("#E3B341", "#A87A1F", "#F5DC7A", "#FFFFFF", "#FFF3C4", "#FFD866")
RED = skin_palette("#D63B2F", "#8E2219", "#FF8A7A", "#FFFFFF", "#FFD9D4", "#FF6B5C")


def blend(pa, pb, t):
    return {k: lerp(pa[k], pb[k], t) for k in pa}


def pulse_frames(pa, pb, n):
    """n frames going a -> b -> a smoothly (cosine), for a looping animation."""
    import math
    frames = []
    for i in range(n):
        t = (1 - math.cos(2 * math.pi * i / n)) / 2
        frames.append(blend(pa, pb, t))
    return frames


STATES = {
    # name: (list of palettes = animation frames, mcmeta frametime in ticks)
    "item": (lambda n: pulse_frames(TURQUOISE, BLUE, n), 3),
    "owned": (lambda n: [GOLD], 0),
    "unclaimed": (lambda n: [TURQUOISE], 0),
    "stealing": (lambda n: pulse_frames(GOLD, RED, n), 2),
}


# ----------------------------------------------------------------------------- PNG writer
def png_bytes(frames):
    """Stack frames vertically into one RGBA PNG."""
    h = ATLAS * len(frames)
    raw = bytearray()
    for pal in frames:
        plinth = pal["P0"]
        for ry, row in enumerate(ROLES):
            raw.append(0)
            for rx, role in enumerate(row):
                colour = pal[role]
                y = MODEL_Y[ry][rx]
                if y is not None and role in ("S0", "S1", "S2"):
                    colour = lerp(plinth, colour, gradient_weight(y))
                raw.extend(colour)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", ATLAS, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


# ----------------------------------------------------------------------------- model helpers
def uv_rect(px_region):
    x0, y0, x1, y1 = px_region
    return [x0 * UV, y0 * UV, x1 * UV, y1 * UV]


def skin_uv(w, h, ox, y_top):
    """1:1 sub-rect of the skin tile: random column, rows chosen so tile row == model height."""
    ox %= (32 - w) if w < 32 else 1
    oy = int(round(32 - y_top))
    oy = max(0, min(32 - h, oy))
    return uv_rect((ox, oy, ox + w, oy + h))


def box(frm, to, seed=0, faces=None, shade=True, rotation=None, name=None):
    """A cuboid skinned 1:1 from the skin tile, with optional per-face overrides {face: px_region}."""
    w, h, d = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
    rnd = random.Random(seed)
    el = {"from": list(frm), "to": list(to), "faces": {}}
    if name:
        el["name"] = name
    if not shade:
        el["shade"] = False
    if rotation:
        el["rotation"] = rotation
    top = to[1]
    dims = {"north": (w, h, top), "south": (w, h, top), "east": (d, h, top), "west": (d, h, top),
            "up": (w, d, top), "down": (w, d, frm[1] + d)}
    for face, (fw, fh, y_top) in dims.items():
        if faces and face in faces:
            el["faces"][face] = {"uv": uv_rect(faces[face]), "texture": "#atlas"}
        else:
            el["faces"][face] = {"uv": skin_uv(fw, fh, rnd.randrange(0, 32), y_top), "texture": "#atlas"}
    return el


ORB_FACES = {f: ORB for f in ("north", "south", "east", "west", "up", "down")}
PLINTH_FACES = {"up": PLINTH, "down": PLINTH, "north": PLINTH_SIDE, "south": PLINTH_SIDE, "east": PLINTH_SIDE, "west": PLINTH_SIDE}


# Upgrade orbs around the base. Order = TotemUpgrades.Buff ordinal. Hue is fixed per buff; the game
# tints the white orblet with saturation rising from level 1 to 5 (see UpgradeOrbColors).
UPGRADE_ORBS = [
    ("damage", "Damage", 8),            # red-orange
    ("resistance", "Resistance", 218),  # blue
    ("mining_speed", "Mining Speed", 48),  # amber
    ("fortify", "Fortify", 275),        # violet (placed-only)
    ("healing", "Healing Aura", 130),   # green (placed-only)
]


def upgrade_orb_elements(plinth_from, plinth_to):
    """Small 2x2x2 unlit cubes hovering just outside the plinth edge, spread evenly around it."""
    import math
    cx, cz = (plinth_from[0] + plinth_to[0]) / 2, (plinth_from[2] + plinth_to[2]) / 2
    r = (plinth_to[0] - plinth_from[0]) / 2 + 1.5
    y = plinth_to[1] + 1.0
    out = []
    n = len(UPGRADE_ORBS)
    for i, (key, _, _) in enumerate(UPGRADE_ORBS):
        a = math.pi / 2 + (2 * math.pi * i / n) + math.pi / n   # start front-left, go around
        x, z = cx + math.cos(a) * r, cz + math.sin(a) * r
        el = {"name": "upgrade_" + key, "from": [round(x - 1, 2), round(y, 2), round(z - 1, 2)],
              "to": [round(x + 1, 2), round(y + 2, 2), round(z + 1, 2)], "shade": False, "faces": {}}
        for face in ("north", "south", "east", "west", "up", "down"):
            el["faces"][face] = {"uv": uv_rect(ORBLET), "texture": "#atlas", "tintindex": i}
        out.append(el)
    return out


def flip_z(el):
    """Mirror an element front-to-back so the face designed on 'south' ends up on 'north'."""
    f, t = el["from"], el["to"]
    el["from"], el["to"] = [f[0], f[1], 16 - t[2]], [t[0], t[1], 16 - f[2]]
    faces = el["faces"]
    faces["north"], faces["south"] = faces.get("south"), faces.get("north")
    for k in [k for k, v in faces.items() if v is None]:
        del faces[k]
    if "rotation" in el:
        o = el["rotation"]["origin"]
        el["rotation"]["origin"] = [o[0], o[1], 16 - o[2]]
        if el["rotation"]["axis"] == "x":
            el["rotation"]["angle"] = -el["rotation"]["angle"]
    return el


def model(elements, name):
    elements = [flip_z(e) for e in elements]
    plinth = next(e for e in elements if e.get("name") == "plinth")
    return {
        "upgrade_orbs": [flip_z(e) for e in upgrade_orb_elements(plinth["from"], plinth["to"])],
        "credit": "BSP-Core Shatter Totem (" + name + ")",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {"particle": "#atlas"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.5, 0.5, 0.5]},
            "ground": {"translation": [0, 1, 0], "scale": [0.3, 0.3, 0.3]},
            "fixed": {"scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.4, 0.4, 0.4]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.4, 0.4, 0.4]},
        },
    }


def variant_idol():
    """Standing creeper-man cradling the orb at the waist with both hands."""
    return model([
        box([2, 0, 2], [14, 2, 14], faces=PLINTH_FACES, name="plinth"),
        box([4, 2, 6], [8, 9, 10], seed=1, name="leg_l"),
        box([8, 2, 6], [12, 9, 10], seed=2, name="leg_r"),
        box([4, 9, 5], [12, 19, 11], seed=3, faces={"south": TORSO_FRONT}, name="torso"),
        box([4, 19, 4], [12, 27, 12], seed=4, faces={"south": FACE}, name="head"),
        box([1, 12, 6], [4, 16, 15], seed=5, name="arm_l"),
        box([12, 12, 6], [15, 16, 15], seed=6, name="arm_r"),
        box([2, 11, 13], [5, 17, 17], seed=7, name="hand_l"),
        box([11, 11, 13], [14, 17, 17], seed=8, name="hand_r"),
        box([5, 11, 12], [11, 17, 18], faces=ORB_FACES, shade=False, name="orb"),
    ], "Idol")


def variant_guardian():
    """Kneeling, head bowed, orb raised to the chest."""
    return model([
        box([2, 0, 2], [14, 2, 14], faces=PLINTH_FACES, name="plinth"),
        box([3, 2, 5], [8, 6, 13], seed=1, name="knee_l"),
        box([8, 2, 5], [13, 6, 13], seed=2, name="knee_r"),
        box([4, 6, 6], [12, 16, 11], seed=3, faces={"south": TORSO_FRONT}, name="torso"),
        box([4, 16, 4], [12, 24, 12], seed=4, faces={"south": FACE}, name="head",
            rotation={"origin": [8, 16, 8], "axis": "x", "angle": 22.5}),
        box([1, 8, 7], [4, 12, 14], seed=5, name="arm_l"),
        box([12, 8, 7], [15, 12, 14], seed=6, name="arm_r"),
        box([2, 7, 13], [5, 13, 17], seed=7, name="hand_l"),
        box([11, 7, 13], [14, 13, 17], seed=8, name="hand_r"),
        box([5, 7, 12], [11, 13, 18], faces=ORB_FACES, shade=False, name="orb"),
    ], "Guardian")


def variant_sentinel():
    """Tall and slender, holding the orb up at face height."""
    return model([
        box([3, 0, 3], [13, 2, 13], faces=PLINTH_FACES, name="plinth"),
        box([5, 2, 6], [8, 11, 10], seed=1, name="leg_l"),
        box([8, 2, 6], [11, 11, 10], seed=2, name="leg_r"),
        box([5, 11, 6], [11, 23, 10], seed=3, faces={"south": TORSO_FRONT}, name="torso"),
        box([4, 23, 4], [12, 31, 12], seed=4, faces={"south": FACE}, name="head"),
        box([2, 14, 6], [5, 18, 14], seed=5, name="arm_l"),
        box([11, 14, 6], [14, 18, 14], seed=6, name="arm_r"),
        box([2, 17, 12], [5, 23, 16], seed=7, name="hand_l"),
        box([11, 17, 12], [14, 23, 16], seed=8, name="hand_r"),
        box([5, 17, 11], [11, 23, 17], faces=ORB_FACES, shade=False, name="orb"),
    ], "Sentinel")


VARIANTS = {
    "idol": ("Idol", "Standing, orb cradled at the waist. Reads clearly at inventory size.", variant_idol),
    "guardian": ("Guardian", "Kneeling with bowed head, orb held at the waist below the face. Chosen design.", variant_guardian),
    "sentinel": ("Sentinel", "Tall and slender, orb held up at face height. Nearly two blocks high when placed.", variant_sentinel),
}


# ----------------------------------------------------------------------------- outputs
def write_bundle(out):
    textures = {}
    for state, (make, _) in STATES.items():
        frames = make(24)
        textures[state] = {"frames": len(frames), "png": base64.b64encode(png_bytes(frames)).decode()}
    bundle = {
        "atlas": ATLAS,
        "textures": textures,
        "variants": [{"id": k, "name": n, "blurb": b, "model": f()} for k, (n, b, f) in VARIANTS.items()],
    }
    Path(out).write_text(json.dumps(bundle))
    print("bundle written:", out, "(%d KB)" % (Path(out).stat().st_size // 1024))


COIN = """
....GGGGGGGG....
..GGLLLLLLLLGG..
.GLLLLLLLLLLLLG.
.GLLLLLGGLLLLLG.
GLLLLLGCGLLLLLLG
GLLLLGCCGLLLLLLG
GLLLLGCGLLLLLLLG
GLLLLGCGGLLLLLLG
GLLLLLGCGLLLLLLG
GLLLLLGCCGLLLLLG
GLLLLLLGCGLLLLLG
GLLLLLLGGLLLLLLG
.GLLLLLLLLLLLLg.
.GgLLLLLLLLLLgg.
..GGggggggggGG..
....GGGGGGGG....
"""


def write_coin_icon(path):
    """16x16 Shatter Coin: gold disc with the shatter crack."""
    pal = {".": (0, 0, 0, 0), "G": hexc("#A87A1F"), "L": hexc("#F5DC7A"), "g": hexc("#E3B341"), "C": hexc("#1E7A62")}
    rows = [r for r in COIN.strip("\n").splitlines()]
    raw = bytearray()
    for r in rows:
        raw.append(0)
        for ch in r:
            raw.extend(pal[ch])

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    path.write_bytes(png)


def write_assets(variant):
    name, _, make = VARIANTS[variant]
    m = make()
    models = ASSETS / "models"
    tex = ASSETS / "textures"
    (models / "block").mkdir(parents=True, exist_ok=True)
    (models / "item").mkdir(parents=True, exist_ok=True)
    (tex / "block").mkdir(parents=True, exist_ok=True)
    (tex / "item").mkdir(parents=True, exist_ok=True)

    # geometry once; state models just bind the atlas texture
    geo = dict(m)
    orbs = geo.pop("upgrade_orbs")
    geo["textures"] = {"particle": "#atlas"}
    for stale in models.glob("block/shatter_totem_orb_*.json"):
        stale.unlink()
    (models / "block/shatter_totem_geometry.json").write_text(json.dumps(geo, indent=2))
    for state in ("owned", "unclaimed", "stealing"):
        (models / f"block/shatter_totem_{state}.json").write_text(json.dumps({
            "parent": "bsp_core:block/shatter_totem_geometry",
            "textures": {"atlas": f"bsp_core:block/shatter_totem_{state}", "particle": f"bsp_core:block/shatter_totem_{state}"},
        }, indent=2))
    (models / "item/shatter_totem.json").write_text(json.dumps({
        "parent": "bsp_core:block/shatter_totem_geometry",
        "textures": {"atlas": "bsp_core:item/shatter_totem", "particle": "bsp_core:item/shatter_totem"},
    }, indent=2))

    (ASSETS / "blockstates").mkdir(parents=True, exist_ok=True)
    variants = {}
    for st in ("owned", "unclaimed", "stealing"):
        for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            v = {"model": f"bsp_core:block/shatter_totem_{st}"}
            if rot:
                v["y"] = rot
            variants[f"facing={facing},glow={st}"] = v
    (ASSETS / "blockstates/shatter_totem.json").write_text(json.dumps({"variants": variants}, indent=2))

    for state, (make_frames, frametime) in STATES.items():
        frames = make_frames(8)
        folder = tex / ("item" if state == "item" else "block")
        fname = "shatter_totem" if state == "item" else f"shatter_totem_{state}"
        (folder / f"{fname}.png").write_bytes(png_bytes(frames))
        meta = folder / f"{fname}.png.mcmeta"
        if len(frames) > 1:
            meta.write_text(json.dumps({"animation": {"frametime": frametime, "interpolate": True}}, indent=2))
        elif meta.exists():
            meta.unlink()
    print("assets written for variant:", name)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "bundle":
        write_bundle(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == "assets" and sys.argv[2] in VARIANTS:
        write_assets(sys.argv[2])
    else:
        print(__doc__)
        sys.exit(1)
