#!/usr/bin/env python3
"""Inventory icons for every BSP-Core item and block, drawn in Python from the mod's own item models and textures
(no browser: headless Chrome stopped drawing WebGL, so the old ?icons=1 capture saved blank pictures).

  python3 tools/gen_icons.py            -> docs/curseforge/icons/<id>.png (96 x 96, transparent) and icons/index.json
  python3 tools/gen_icons.py <id>...    -> only those ids
  python3 tools/check_icons.py          -> fails when an icon is missing, empty or fully transparent

Sprites (minecraft:item/generated) are the texture scaled up with no smoothing. Everything with elements is drawn the way
the inventory draws a block: an orthographic view turned 45 degrees and tipped 30 degrees down, the top face brightest,
with a depth buffer per pixel and nearest-neighbour texture sampling, every model at the same scale so a slab stays a
slab. Animated textures show their first frame.
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
from gen_material_assets import read_png, write_png, vanilla  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/bsp_core"
OUT = ROOT / "docs/curseforge/icons"
SIZE = 96
# a full 16-unit cube fills this share of the icon; the frame is centred on the unit cube, not on the model
FILL = 0.84
# faces by their outward direction: top brightest, then the x sides, then the z sides, as the game lights its icons
SHADE = {"up": 1.0, "down": 0.5, "west": 0.8, "east": 0.8, "north": 0.62, "south": 0.62}
SKIP = {"ghost_block", "molten_tetrium", "molten_illyrium", "molten_magnatite", "forged_shatter_totem"}  # no item form (the molten metals are placed fluids, with buckets; the forged totem is a Shatter Totem with that name)

TEXTURE_CACHE = {}


def texture(ref):
    """(w, h, rows) of a texture by 'bsp_core:block/x' or 'minecraft:item/x'; animated strips are cut to their first frame."""
    if ref in TEXTURE_CACHE:
        return TEXTURE_CACHE[ref]
    ns, path = ref.split(":")
    if ns == "minecraft":
        w, h, rows = vanilla(path + ".png")
    else:
        w, h, rows = read_png((ASSETS / f"textures/{path}.png").read_bytes())
    if h > w and (ASSETS / f"textures/{path}.png.mcmeta").exists() or h >= 2 * w:
        rows, h = rows[:w], w
    TEXTURE_CACHE[ref] = (w, h, rows)
    return TEXTURE_CACHE[ref]


CUBE_FACES = {"north": "#north", "south": "#south", "east": "#east", "west": "#west", "up": "#up", "down": "#down"}


def cube(faces):
    return [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {f: {"uv": [0, 0, 16, 16], "texture": t} for f, t in faces.items()}}]


VANILLA_PARENTS = {
    "minecraft:block/cube_all": lambda: cube({f: "#all" for f in CUBE_FACES}),
    "minecraft:block/cube": lambda: cube(CUBE_FACES),
    "minecraft:block/cube_column": lambda: cube({"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#end", "down": "#end"}),
    "minecraft:block/cube_bottom_top": lambda: cube({"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#bottom"}),
    "minecraft:block/orientable": lambda: cube({"north": "#front", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"}),
}


def resolve(ref):
    """The model with its parent chain folded in: {'elements', 'textures', 'sprite'}; sprite models list their layers."""
    ns, path = ref.split(":")
    if ns != "bsp_core":
        if ref in ("minecraft:item/generated", "minecraft:item/handheld"):
            return {"elements": [], "textures": {}, "sprite": True}
        if ref in VANILLA_PARENTS:
            return {"elements": VANILLA_PARENTS[ref](), "textures": {}, "sprite": False}
        return {"elements": [], "textures": {}, "sprite": False}
    m = json.loads((ASSETS / f"models/{path}.json").read_text())
    if m.get("loader") == "forge:fluid_container":
        return {"elements": [], "textures": {}, "sprite": True, "fluid": m["fluid"]}
    base = resolve(m["parent"]) if "parent" in m else {"elements": [], "textures": {}, "sprite": False}
    return {"elements": m.get("elements") or base["elements"], "textures": dict(base["textures"], **m.get("textures", {})), "sprite": base["sprite"]}


def tex_ref(textures, name):
    """Follows '#a' -> '#b' -> 'ns:path'."""
    seen = 0
    while name.startswith("#") and seen < 10:
        name = textures.get(name[1:], "")
        seen += 1
    return name if ":" in name else None


# ------------------------------------------------------------------ geometry

def rotate(p, origin, axis, angle):
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def default_uv(face, f, t):
    return {"down": [f[0], 16 - t[2], t[0], 16 - f[2]], "up": [f[0], f[2], t[0], t[2]], "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
            "south": [f[0], 16 - t[1], t[0], 16 - f[1]], "west": [f[2], 16 - t[1], t[2], 16 - f[1]], "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[face]


def corners(face, f, t):
    """The face's corners as seen from outside: top-left, top-right, bottom-right, bottom-left."""
    return {
        "north": [(t[0], t[1], f[2]), (f[0], t[1], f[2]), (f[0], f[1], f[2]), (t[0], f[1], f[2])],
        "south": [(f[0], t[1], t[2]), (t[0], t[1], t[2]), (t[0], f[1], t[2]), (f[0], f[1], t[2])],
        "west": [(f[0], t[1], f[2]), (f[0], t[1], t[2]), (f[0], f[1], t[2]), (f[0], f[1], f[2])],
        "east": [(t[0], t[1], t[2]), (t[0], t[1], f[2]), (t[0], f[1], f[2]), (t[0], f[1], t[2])],
        "up": [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])],
        "down": [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], f[1], f[2]), (f[0], f[1], f[2])],
    }[face]


# the inventory's view: the camera sits up and to the north-west, as the renders page draws it
RY, RX = math.pi * 1.25, math.pi / 6
D = (math.sin(RY) * math.cos(RX), math.sin(RX), math.cos(RY) * math.cos(RX))  # from the block towards the camera
F = (-D[0], -D[1], -D[2])
RIGHT = (-F[2], 0.0, F[0])
RL = math.hypot(RIGHT[0], RIGHT[2])
RIGHT = (RIGHT[0] / RL, 0.0, RIGHT[2] / RL)
UP = (RIGHT[1] * F[2] - RIGHT[2] * F[1], RIGHT[2] * F[0] - RIGHT[0] * F[2], RIGHT[0] * F[1] - RIGHT[1] * F[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def project(p, scale, centre):
    return ((dot(p, RIGHT) - centre[0]) * scale + SIZE / 2, SIZE / 2 - (dot(p, UP) - centre[1]) * scale, dot(p, F))


def frame():
    """Scale and screen centre so a full cube fills FILL of the icon, the same for every model."""
    pts = [(x, y, z) for x in (0, 16) for y in (0, 16) for z in (0, 16)]
    xs = [dot(p, RIGHT) for p in pts]
    ys = [dot(p, UP) for p in pts]
    scale = SIZE * FILL / max(max(xs) - min(xs), max(ys) - min(ys))
    return scale, ((max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2)


SCALE, CENTRE = frame()


def draw_model(model):
    """Returns a SIZE x SIZE RGBA grid."""
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    zb = [[float("inf")] * SIZE for _ in range(SIZE)]
    textures = model["textures"]
    opaque_faces, clear_faces = [], []
    for el in model["elements"]:
        f, t = el["from"], el["to"]
        rot = el.get("rotation")
        for face, spec in el.get("faces", {}).items():
            ref = tex_ref(textures, spec.get("texture", ""))
            if not ref:
                continue
            tw, th, rows = texture(ref)
            uv = spec.get("uv") or default_uv(face, f, t)
            pts = corners(face, f, t)
            if rot:
                pts = [rotate(p, rot.get("origin", [8, 8, 8]), rot.get("axis", "y"), rot.get("angle", 0)) for p in pts]
            # outward normal, for culling and shading
            e1 = tuple(pts[1][i] - pts[0][i] for i in range(3))
            e2 = tuple(pts[3][i] - pts[0][i] for i in range(3))
            n = cross(e2, e1)
            if dot(n, D) <= 1e-9:
                continue
            ax = max(range(3), key=lambda i: abs(n[i]))
            shade_face = ("west" if n[0] < 0 else "east") if ax == 0 else ("down" if n[1] < 0 else "up") if ax == 1 else ("north" if n[2] < 0 else "south")
            shade = SHADE[shade_face]
            # uv corners, turned by the face's rotation
            uvc = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
            turn = int(spec.get("rotation", 0) // 90) % 4
            uvc = uvc[turn:] + uvc[:turn]
            s = [project(p, SCALE, CENTRE) for p in pts]
            entry = (s, uvc, tw, th, rows, shade)
            # a glass-like texture goes in the blended pass
            clear = any(row[x][3] < 250 and row[x][3] > 8 for row in rows[::max(1, th // 8)] for x in range(0, tw, max(1, tw // 8)))
            (clear_faces if clear else opaque_faces).append(entry)

    def raster(entry, blend):
        s, uvc, tw, th, rows, shade = entry
        (x0, y0, z0), (x1, y1, z1), _, (x3, y3, z3) = s
        ax, ay, bx, by = x1 - x0, y1 - y0, x3 - x0, y3 - y0
        det = ax * by - ay * bx
        if abs(det) < 1e-9:
            return
        minx, maxx = max(0, int(min(x0, x1, s[2][0], x3))), min(SIZE - 1, int(max(x0, x1, s[2][0], x3)) + 1)
        miny, maxy = max(0, int(min(y0, y1, s[2][1], y3))), min(SIZE - 1, int(max(y0, y1, s[2][1], y3)) + 1)
        (u0, v0), (u1, v1), _, (u3, v3) = uvc
        for yy in range(miny, maxy + 1):
            for xx in range(minx, maxx + 1):
                qx, qy = xx + 0.5 - x0, yy + 0.5 - y0
                a = (qx * by - qy * bx) / det
                b = (ax * qy - ay * qx) / det
                if a < 0 or a >= 1 or b < 0 or b >= 1:
                    continue
                z = z0 + a * (z1 - z0) + b * (z3 - z0)
                if z > zb[yy][xx] + 1e-4:  # a face on the same plane drawn later wins (ore overlays)
                    continue
                u = u0 + a * (u1 - u0) + b * (u3 - u0)
                v = v0 + a * (v1 - v0) + b * (v3 - v0)
                tx = min(tw - 1, max(0, int(u * tw / 16)))
                ty = min(th - 1, max(0, int(v * th / 16)))
                r, g, bl, al = rows[ty][tx]
                if al <= 8:
                    continue
                r, g, bl = int(r * shade), int(g * shade), int(bl * shade)
                if blend and al < 250:
                    pr, pg, pb, pa = px[yy][xx]
                    if pa <= 8:  # nothing behind: the colour stays its own, only the alpha is partial
                        px[yy][xx] = (r, g, bl, al)
                    else:
                        k = al / 255
                        px[yy][xx] = (int(r * k + pr * (1 - k)), int(g * k + pg * (1 - k)), int(bl * k + pb * (1 - k)), max(pa, al))
                else:
                    px[yy][xx] = (r, g, bl, 255)
                    zb[yy][xx] = z

    for e in opaque_faces:
        raster(e, False)
    clear_faces.sort(key=lambda e: -sum(p[2] for p in e[0]))
    for e in clear_faces:
        raster(e, True)
    return px


def fluid_bucket(fluid):
    """Forge's bucket for a fluid, as the inventory shows it: the vanilla bucket, with the fluid's still texture where the
    water bucket shows water."""
    _, _, bucket = texture("minecraft:item/bucket")
    w, h, water = texture("minecraft:item/water_bucket")
    _, _, still = texture(fluid.replace(":", ":block/") + "_still")
    return w, h, [[still[y][x] if water[y][x] != bucket[y][x] and water[y][x][3] > 8 else water[y][x] for x in range(w)] for y in range(h)]


def draw_sprite(model):
    if "fluid" in model:
        TEXTURE_CACHE["bucket:" + model["fluid"]] = fluid_bucket(model["fluid"])
        model = {"textures": {"layer0": "bucket:" + model["fluid"]}}
    layers = sorted(k for k in model["textures"] if k.startswith("layer"))
    px = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
    for layer in layers:
        ref = tex_ref(model["textures"], "#" + layer)
        if not ref:
            continue
        tw, th, rows = texture(ref)
        k = SIZE // max(tw, th)
        ox, oy = (SIZE - tw * k) // 2, (SIZE - th * k) // 2
        for y in range(SIZE):
            for x in range(SIZE):
                sx, sy = (x - ox) // k, (y - oy) // k
                if 0 <= sx < tw and 0 <= sy < th and rows[sy][sx][3] > 8:
                    px[y][x] = rows[sy][sx]
    return px


def ids():
    lang = json.loads((ASSETS / "lang/en_us.json").read_text())
    out = []
    for key, name in lang.items():
        if key.count(".") == 2 and key.startswith(("item.bsp_core.", "block.bsp_core.")):
            kind, _, id_ = key.split(".")
            if id_ not in SKIP:
                out.append((id_, kind, name))
    return out


def main():
    only = set(sys.argv[1:])
    OUT.mkdir(parents=True, exist_ok=True)
    index = {}
    made = 0
    for id_, kind, name in ids():
        index[id_] = {"file": f"{id_}.png", "name": name, "kind": kind}
        if only and id_ not in only:
            continue
        model = resolve(f"bsp_core:item/{id_}")
        px = draw_sprite(model) if model["sprite"] else draw_model(model)
        write_png(OUT / f"{id_}.png", SIZE, SIZE, lambda x, y: px[y][x])
        made += 1
    (OUT / "index.json").write_text(json.dumps(index, indent=2) + "\n")
    print(f"icons: {made} written, {len(index)} in index.json -> {OUT}")


if __name__ == "__main__":
    main()
