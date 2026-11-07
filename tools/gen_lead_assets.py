#!/usr/bin/env python3
"""Generates the Lead-Lined Chest's assets and data for BSP-Core (no third-party dependencies).

  python3 tools/gen_lead_assets.py

Writes the Lead-Lined Chest's textures, block models (the body, and the lid the renderer swings open, each single and as either half
of a double chest), item model, block state, loot table, mining tag and Voltaic shielding entry. The look is A's lead box with C's
padded front and gauge and the Rail Frame lid (Lead-Lined Chest brief, round 2 of the mock-ups). Rerun after changing anything here; never hand-edit the outputs.
"""
import json
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_factory_assets import shade  # noqa: E402
from gen_material_assets import hexc  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/bsp_core"
DATA = RES / "data"


def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n")


def tag(name, blocks, kind="blocks"):
    path = DATA / f"minecraft/tags/{kind}/{name}.json"
    data = json.loads(path.read_text()) if path.exists() else {"replace": False, "values": []}
    path.parent.mkdir(parents=True, exist_ok=True)
    for b in blocks:
        if f"bsp_core:{b}" not in data["values"]:
            data["values"].append(f"bsp_core:{b}")
    path.write_text(json.dumps(data, indent=2) + "\n")


# ----------------------------------------------------------------------------- textures
LEAD, LEAD2, LEAD3, LINING, IRON, WELL = "#5D6573", "#3D434C", "#737C8A", "#AAB0B8", "#2A2E33", "#15171A"
YELLOW, BLACK, DIAL, RED = "#E7C51D", "#16181A", "#DFE3E6", "#C0392B"


def flat(colour, seed):
    return lambda x, y: shade(colour, x, y, seed)


def plate(x, y):
    """The radiation plate: a black trefoil on yellow. On a lid's top face it is seen turned half round from the front, so the
    blade drawn pointing up (towards v = 0) points down at a player standing at the chest."""
    cx, cy = 7.5, 7.5
    dx, dy = x - cx, cy - y
    r = math.hypot(dx, dy)
    a = math.degrees(math.atan2(dy, dx)) % 360
    inside = r <= 1.6 or (2.6 <= r <= 7.2 and any(abs(((a - c + 180) % 360) - 180) <= 30 for c in (90, 210, 330)))
    if x in (0, 15) or y in (0, 15):
        return shade("#B89A14", x, y, 61)
    return hexc(BLACK) if inside else shade(YELLOW, x, y, 63)


def dial(x, y):
    """The gauge: an off-white face in a dark ring, five ticks along the top and a red zone at the right."""
    cx, cy = 7.5, 7.5
    r = math.hypot(x - cx, y - cy)
    if r > 7.6:
        return shade(IRON, x, y, 71)
    if r > 6.4:
        return shade(LEAD2, x, y, 73)
    a = math.degrees(math.atan2(cy - y, x - cx))
    if r > 4.6 and 0 <= a <= 40:
        return hexc(RED)
    if r > 4.8 and any(abs(a - t) < 7 for t in (150, 115, 90, 65)):
        return hexc(BLACK)
    if r < 1.2:
        return hexc(IRON)
    return shade(DIAL, x, y, 75)


def inside(open_side):
    """The chest's well seen from above: dark, with the pale lead lining round its rim, except on the side that joins its other half."""
    def px(x, y):
        rim = y in (0, 15) or (x == 0 and open_side != "w") or (x == 15 and open_side != "e")
        return shade(LINING, x, y, 81) if rim else shade(WELL, x, y, 83)
    return px


def textures():
    from gen_material_assets import write_png
    tex = ASSETS / "textures/block"
    for name, pixel in (("lead_chest_lead", flat(LEAD, 11)), ("lead_chest_dark", flat(LEAD2, 13)), ("lead_chest_light", flat(LEAD3, 17)),
                        ("lead_chest_lining", flat(LINING, 19)), ("lead_chest_iron", flat(IRON, 23)), ("lead_chest_plate", plate),
                        ("lead_chest_dial", dial), ("lead_chest_inside", inside("")), ("lead_chest_inside_e", inside("e")),
                        ("lead_chest_inside_w", inside("w")), ("lead_chest_white", lambda x, y: (255, 255, 255, 255))):
        write_png(tex / f"{name}.png", 16, 16, pixel)


# ----------------------------------------------------------------------------- models
# Facing north (front at z 0). A LEFT half's partner is east (+x), a RIGHT half's is west, as vanilla's ChestBlock.getConnectedDirection;
# so a double chest's LEFT half is the one on the right as you face it, and it carries the gauge.
SPAN = {"single": (1, 15), "left": (1, 16), "right": (0, 15)}
FACES = ("down", "up", "north", "south", "east", "west")


def el(frm, to, tex, up=None, north=None, rot=None, uv_up=None):
    faces = {}
    for d in FACES:
        t = up if d == "up" and up else north if d == "north" and north else tex
        f = {"texture": f"#{t}"}
        if d == "up" and uv_up:
            f["uv"] = uv_up
        faces[d] = f
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": faces}
    if rot:
        e["rotation"] = rot
    return e


TEXTURES = {"particle": "bsp_core:block/lead_chest_lead", "lead": "bsp_core:block/lead_chest_lead", "dark": "bsp_core:block/lead_chest_dark",
            "light": "bsp_core:block/lead_chest_light", "lining": "bsp_core:block/lead_chest_lining", "iron": "bsp_core:block/lead_chest_iron",
            "plate": "bsp_core:block/lead_chest_plate", "dial": "bsp_core:block/lead_chest_dial", "inside": "bsp_core:block/lead_chest_inside",
            "inside_e": "bsp_core:block/lead_chest_inside_e", "inside_w": "bsp_core:block/lead_chest_inside_w"}

# where the gauge's centre is, in model pixels, for the halves that carry it (the renderer draws its needle there)
GAUGE = {"single": (4.0, 7.6), "left": (2 + 14 / 6, 7.6)}


def body(kind):
    x0, x1 = SPAN[kind]
    west_out, east_out = kind != "right", kind != "left"
    els = [el([0.5 if west_out else 0, 0, 0.5], [15.5 if east_out else 16, 1, 15.5], "dark")]
    well = "inside" if kind == "single" else "inside_e" if kind == "left" else "inside_w"
    els.append(el([x0, 1, 1], [x1, 10, 15], "lead", up=well, uv_up=[0, 0, 16, 16]))  # whole texture: the lining rim is its outer pixels
    # the sunk bed and its padded squares (C's front): three columns a half, two rows
    bx0, bx1 = (1.6 if west_out else 0), (14.4 if east_out else 16)
    els.append(el([bx0, 1.4, 0.7], [bx1, 9.6, 1], "dark"))
    cx0, cx1 = (2 if west_out else 0), (14 if east_out else 16)
    cw = (cx1 - cx0) / 3
    for i in range(3):
        for j in range(2):
            px0, py0 = cx0 + i * cw, 1.8 + j * 4
            if kind in GAUGE and i == 0 and j == 1:
                continue  # this square holds the gauge
            els.append(el([px0 + 0.2, py0, 0.4], [px0 + cw - 0.2, py0 + 3.6, 0.8], "light"))
            els.append(el([px0 + 0.9, py0 + 0.7, 0.2], [px0 + cw - 0.9, py0 + 2.9, 0.4], "lead"))
    if kind in GAUGE:
        gx, gy = GAUGE[kind]
        els.append(el([gx - 1.75, gy - 1.05, 0.3], [gx + 1.75, gy + 1.05, 0.8], "iron"))
        els.append(el([gx - 1.05, gy - 1.75, 0.3], [gx + 1.05, gy + 1.75, 0.8], "iron"))
        dial_el = el([gx - 1.4, gy - 1.4, 0.2], [gx + 1.4, gy + 1.4, 0.3], "iron", north="dial")
        dial_el["faces"]["north"]["uv"] = [0, 0, 16, 16]  # the whole dial on its small face, not the patch default uvs would cut
        els.append(dial_el)
    return els


def lid(kind):
    """Rail Frame: a raised rail round the top (round the pair on a double chest) with cut corners, the radiation plate sunk in the
    middle (across the join on a double chest), a dark seam on the front edge and the pale lining underneath. The light line inside
    the rail is drawn by the renderer, full bright."""
    x0, x1 = SPAN[kind]
    west_out, east_out = kind != "right", kind != "left"
    els = [el([x0, 10, 1], [x1, 10.6, 15], "lining"), el([x0, 10.6, 1], [x1, 14, 15], "lead")]
    els.append(el([2.3 if west_out else 0, 10.6, 0.85], [13.7 if east_out else 16, 11.2, 1], "dark"))
    rx0, rx1 = (3 if west_out else 0), (13 if east_out else 16)
    els.append(el([rx0, 14, 1], [rx1, 14.9, 2.3], "dark"))
    els.append(el([rx0, 14, 13.7], [rx1, 14.9, 15], "dark"))
    for out, sx0, sx1, cx in ((west_out, 1, 2.3, 2.4), (east_out, 13.7, 15, 13.6)):
        if out:
            els.append(el([sx0, 14, 3], [sx1, 14.9, 13], "dark"))
            for cz in (2.4, 13.6):
                els.append(el([cx - 1.3, 14, cz - 1.3], [cx + 1.3, 14.95, cz + 1.3], "dark",
                              rot={"origin": [cx, 14, cz], "axis": "y", "angle": 45}))
    if kind == "single":
        els.append(el([4.4, 14, 4.4], [11.6, 14.15, 11.6], "dark"))
        els.append(el([5, 14.15, 5], [11, 14.25, 11], "dark", up="plate", uv_up=[0, 0, 16, 16]))
    elif kind == "left":  # the west half of a plate centred on the join
        els.append(el([12.4, 14, 4.4], [16, 14.15, 11.6], "dark"))
        els.append(el([13, 14.15, 5], [16, 14.25, 11], "dark", up="plate", uv_up=[0, 0, 8, 16]))
    else:
        els.append(el([0, 14, 4.4], [3.6, 14.15, 11.6], "dark"))
        els.append(el([0, 14.15, 5], [3, 14.25, 11], "dark", up="plate", uv_up=[8, 0, 16, 16]))
    return els


def model(name, elements):
    write(ASSETS / f"models/block/{name}.json", {"parent": "minecraft:block/block", "textures": TEXTURES, "elements": elements})


def chest():
    textures()
    for kind in SPAN:
        suffix = "" if kind == "single" else f"_{kind}"
        model(f"lead_lined_chest{suffix}", body(kind))
        model(f"lead_lined_chest_lid{suffix}", lid(kind))  # not in the block state: LeadLinedChestRenderer draws it, swung open
    # the item shows the whole closed chest; the display transforms are a block's
    write(ASSETS / "models/item/lead_lined_chest.json", {"parent": "minecraft:block/block", "textures": TEXTURES,
                                                          "elements": body("single") + lid("single")})
    variants = {}
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for kind in SPAN:
            for wet in ("false", "true"):
                v = {"model": f"bsp_core:block/lead_lined_chest{'' if kind == 'single' else '_' + kind}"}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},type={kind},waterlogged={wet}"] = v
    write(ASSETS / "blockstates/lead_lined_chest.json", {"variants": variants})
    # the chest drops itself; its contents are spilled by the block when it is removed
    write(DATA / "bsp_core/loot_tables/blocks/lead_lined_chest.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "bsp_core:lead_lined_chest"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag("mineable/pickaxe", ["lead_lined_chest"])


# Voltaic's radiation shielding (read from every namespace's data/<ns>/radiation/*radiation_shielding.json, keyed by block, not state):
# transmission is what passes through one block, level the strongest source it works on (every source in the pack is 1.0).
# An unread file when Voltaic is absent. Not how the chest keeps its contents quiet: that is its block entity not being a Container.
SHIELDING = {
    "lead_lined_chest": 0.05,  # as a lead block, so a wall of chests also shields what is behind it
    "radiation_shielding_panel": 0.0,  # nothing gets through, as Nuclear Science's shielding
    "radiation_shielding_window": 0.1,  # as Nuclear Science's shielding glass
    "radiation_shielding_door": 0.03,  # shut; Voltaic itself weakens any open door twice (door and trapdoor rules, one OPEN property): about 0.96 open
}


def shielding():
    write(DATA / "bsp_core/radiation/bsp_core_radiation_shielding.json",
          {f"bsp_core:{b}": {"transmission": t, "level": 1.0} for b, t in SHIELDING.items()})


def recipes():
    # eight lead plates round a wooden chest (the owner's pick, 4c). Lead plates come from Electrodynamics, AllTheOres, Railcraft
    # and Thermal in the pack, not Mekanism; with none of them the tag is empty and the recipe is left out rather than shown unmakeable
    write(DATA / "bsp_core/recipes/lead_lined_chest.json", {
        "conditions": [{"type": "forge:not", "value": {"type": "forge:tag_empty", "tag": "forge:plates/lead"}}],
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["PPP", "PCP", "PPP"],
        "key": {"P": {"tag": "forge:plates/lead"}, "C": {"tag": "forge:chests/wooden"}},
        "result": {"item": "bsp_core:lead_lined_chest", "count": 1}})


# ----------------------------------------------------------------------------- the shielding set (A's panel and door, B's window)
GLASS = "#9FD8C8"
SHIELD_BLOCKS = ["radiation_shielding_panel", "radiation_shielding_window", "radiation_shielding_door"]


RIVET_HI, RIVET, RIVET_SHADOW = "#C3CAD4", "#9AA3B0", "#2E333B"


def rivet(x, y, spots):
    """A raised 2 by 2 stud at each spot (its top-left pixel), lit from the top left, with a shadow pixel below and to the right."""
    for sx, sy in spots:
        if (x, y) == (sx, sy):
            return hexc(RIVET_HI)
        if sx <= x <= sx + 1 and sy <= y <= sy + 1:
            return hexc(RIVET)
        if (x, y) in ((sx + 2, sy + 1), (sx + 1, sy + 2), (sx + 2, sy + 2)):
            return hexc(RIVET_SHADOW)
    return None


# Joined panels and windows (ShieldJoins, client/ShieldJoinedModel) cut each face into quarters and give each quarter the same quarter of
# one of five textures: FULL (every edge), H (top and bottom edges), V (left and right edges), INNER (only the four corner pieces that
# close an inside corner), PLAIN (no edges). Everything an edge carries (border, studs and their shadows) is drawn exactly when that edge
# is, so the quarters of neighbouring pieces always meet.
PIECES = {"": "tblr", "_h": "tb", "_v": "lr", "_inner": "", "_plain": ""}
PANEL_STUDS = {(3, 3): "tl", (11, 3): "tr", (3, 11): "bl", (11, 11): "br", (7, 3): "t", (3, 7): "l", (11, 7): "r", (7, 11): "b"}


def panel_piece(edges, inner):
    """A's Riveted Lead Sheet: a raised border (dark outside, lit on its top and left, shaded on its bottom and right) on each edge in
    {@code edges}, the studs of those edges (a corner stud needs both its edges), faint hammered patches; INNER: 2 by 2 corner pieces."""
    studs = [pos for pos, needs in PANEL_STUDS.items() if all(c in edges for c in needs)]

    def px(x, y):
        if ("l" in edges and x == 0) or ("r" in edges and x == 15) or ("t" in edges and y == 0) or ("b" in edges and y == 15):
            return shade(LEAD2, x, y, 101)
        if ("l" in edges and x == 1) or ("t" in edges and y == 1):
            return shade(LEAD3, x, y, 102)
        if ("r" in edges and x == 14) or ("b" in edges and y == 14):
            return shade("#4A515C", x, y, 104)
        if inner and (x in (0, 15) and y in (0, 1, 14, 15) or y in (0, 15) and x in (0, 1, 14, 15)):
            return shade(LEAD2, x, y, 106)
        if inner and (x in (1, 14) and y in (1, 14)):
            return shade(LEAD3, x, y, 108)
        r = rivet(x, y, studs)
        if r:
            return r
        if (5 <= x <= 6 and 5 <= y <= 6) or (9 <= x <= 10 and 9 <= y <= 10):
            return shade("#565D69", x, y, 107)
        return shade(LEAD, x, y, 109)
    return px


def window_piece(edges, inner):
    """B's window in lead: a two-pixel outer frame and a one-pixel inner step on each edge in {@code edges}, thick glass with a pale
    streak; INNER: the frame's 3 by 3 corner closing an inside corner."""
    def px(x, y):
        dist = []
        if "l" in edges:
            dist.append(x)
        if "r" in edges:
            dist.append(15 - x)
        if "t" in edges:
            dist.append(y)
        if "b" in edges:
            dist.append(15 - y)
        if inner and min(x, 15 - x) <= 2 and min(y, 15 - y) <= 2:
            dist.append(max(min(x, 15 - x), min(y, 15 - y)))
        edge = min(dist) if dist else 99
        if edge <= 1:
            return shade(LEAD2, x, y, 111)
        if edge == 2:
            return shade(LEAD, x, y, 113)
        if x + y in (14, 15) and 4 <= x <= 11:
            return hexc("#D8F2EA", 140)
        return hexc(GLASS, 105)
    return px


def door_px(top):
    """A's door face: lead with a dark edge and rivets at the corners and the middle rail (the raised panels and the handle are model
    elements). Rivets sit at both ends of the door's height and where the halves meet."""
    def px(x, y):
        if x in (0, 15) or (y == 0 and top) or (y == 15 and not top):
            return shade(LEAD2, x, y, 121)
        r = rivet(x, y, ((1, 1), (12, 1)) if top else ((1, 12), (12, 12)))
        return r if r else shade(LEAD, x, y, 125)
    return px


def door_item_px(x, y):
    if not 3 <= x <= 12:
        return (0, 0, 0, 0)
    if x in (3, 12) or y in (0, 15):
        return hexc(LEAD2)
    if 5 <= x <= 10 and (2 <= y <= 6 or 9 <= y <= 13):
        if 6 <= x <= 9 and 3 <= y <= 5:
            return hexc(YELLOW)
        return hexc(LEAD3)
    if x == 10 and y == 8:
        return hexc(IRON)
    return shade(LEAD, x, y, 131)


# vanilla's door elements (models/block/door_*.json), with the faces' uvs as vanilla has them; "#face" is the half's texture
DOOR_UV = {
    "bottom_left": {"down": [16, 13, 0, 16], "north": [3, 0, 0, 16], "south": [0, 0, 3, 16], "west": [0, 0, 16, 16], "east": [16, 0, 0, 16]},
    "bottom_left_open": {"down": [0, 16, 16, 13], "north": [0, 0, 3, 16], "south": [0, 0, 3, 16], "west": [16, 0, 0, 16], "east": [0, 0, 16, 16]},
    "bottom_right": {"down": [0, 13, 16, 16], "north": [3, 0, 0, 16], "south": [0, 0, 3, 16], "west": [16, 0, 0, 16], "east": [0, 0, 16, 16]},
    "bottom_right_open": {"down": [16, 16, 0, 13], "north": [3, 0, 0, 16], "south": [3, 0, 0, 16], "west": [0, 0, 16, 16], "east": [16, 0, 0, 16]},
}
for k in list(DOOR_UV):
    t = dict(DOOR_UV[k])
    down = t.pop("down")
    DOOR_UV[k.replace("bottom", "top")] = t
# vanilla's top halves: the up face instead of the down face
DOOR_UV["top_left"]["up"] = [0, 3, 16, 0]
DOOR_UV["top_left_open"]["up"] = [16, 0, 0, 3]
DOOR_UV["top_right"]["up"] = [16, 3, 0, 0]
DOOR_UV["top_right_open"]["up"] = [0, 0, 16, 3]


def door_model(variant):
    """A's door on vanilla's door slab (x 0 to 3): a raised panel on each face of each half, the radiation plate on the upper panel,
    and on the lower half a lever handle on the side away from the hinge (z 14 or z 2, as the variant puts the hinge)."""
    top = variant.startswith("top")
    face_tex = "top" if top else "bottom"
    faces = {}
    for d, uv in DOOR_UV[variant].items():
        f = {"uv": uv, "texture": f"#{face_tex}"}
        if d in ("north", "south", "west", "up", "down"):
            f["cullface"] = d
        if d in ("up", "down"):
            f["rotation"] = 90
        faces[d] = f
    els = [{"from": [0, 0, 0], "to": [3, 16, 16], "faces": faces}]
    y0, y1 = (2, 13) if top else (3, 14)
    for xa, xb, xc, xd in ((-0.4, 0, -0.6, -0.4), (3, 3.4, 3.4, 3.6)):
        els.append(el([xa, y0, 2.5], [xb, y1, 13.5], "dark"))
        els.append(el([xc, y0 + 1, 3.5], [xd, y1 - 1, 12.5], "light"))
    if top:
        for xa, xb, d in ((-0.65, -0.6, "west"), (3.6, 3.65, "east")):
            e = el([xa, 5.5, 4.5], [xb, 12.5, 11.5], "plate")
            e["faces"][d]["uv"] = [0, 0, 16, 16]  # the whole sign on the plate, not the middle of it
            e["faces"][d]["rotation"] = 180  # the plate texture is drawn blade-up for the chest's lid
            els.append(e)
    else:
        hz = 14 if variant in ("bottom_left", "bottom_right_open") else 2
        b0, b1 = (hz - 2.6, hz + 0.4) if hz == 14 else (hz - 0.4, hz + 2.6)
        els += [el([-0.6, 13.6, hz - 0.5], [0, 15, hz + 0.5], "iron"), el([-1.4, 14, b0], [-0.6, 14.6, b1], "iron"),
                el([3, 13.6, hz - 0.5], [3.6, 15, hz + 0.5], "iron"), el([3.6, 14, b0], [4.4, 14.6, b1], "iron")]
    return {"ambientocclusion": False, "textures": {"particle": f"bsp_core:block/radiation_shielding_door_{face_tex}",
            face_tex: f"bsp_core:block/radiation_shielding_door_{face_tex}", "dark": TEXTURES["dark"], "light": TEXTURES["light"],
            "plate": TEXTURES["plate"], "iron": TEXTURES["iron"]}, "elements": els}


def shield_set():
    from gen_material_assets import write_png
    tex = ASSETS / "textures/block"
    for suffix, edges in PIECES.items():
        write_png(tex / f"radiation_shielding_panel{suffix}.png", 16, 16, panel_piece(edges, suffix == "_inner"))
        write_png(tex / f"radiation_shielding_window{suffix}.png", 16, 16, window_piece(edges, suffix == "_inner"))
    write_png(tex / "radiation_shielding_door_bottom.png", 16, 16, door_px(False))
    write_png(tex / "radiation_shielding_door_top.png", 16, 16, door_px(True))
    write_png(ASSETS / "textures/item/radiation_shielding_door.png", 16, 16, door_item_px)
    write(ASSETS / "models/block/radiation_shielding_panel.json", {"parent": "minecraft:block/cube_all",
                                                                     "textures": {"all": "bsp_core:block/radiation_shielding_panel"}})
    # the window: a pane 6 pixels deep (z 5 to 11) running east to west; the glass faces front and back, the lead frame's edges on the
    # four sides, which are dropped between windows in one plane (cullface; ShieldingWindowBlock.skipRendering)
    write(ASSETS / "models/block/radiation_shielding_window.json", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
          "textures": {"particle": "bsp_core:block/radiation_shielding_window", "glass": "bsp_core:block/radiation_shielding_window", "edge": TEXTURES["dark"]},
          "elements": [{"from": [0, 0, 5], "to": [16, 16, 11], "faces": {
              "north": {"texture": "#glass"}, "south": {"texture": "#glass"},
              **{d: {"texture": "#edge", "cullface": d} for d in ("east", "west", "up", "down")}}}]})
    write(ASSETS / "blockstates/radiation_shielding_window.json", {"variants": {
        "axis=x": {"model": "bsp_core:block/radiation_shielding_window"},
        "axis=z": {"model": "bsp_core:block/radiation_shielding_window", "y": 90}}})
    write(ASSETS / "blockstates/radiation_shielding_panel.json", {"variants": {"": {"model": "bsp_core:block/radiation_shielding_panel"}}})
    for b in ("radiation_shielding_panel", "radiation_shielding_window"):
        write(ASSETS / f"models/item/{b}.json", {"parent": f"bsp_core:block/{b}"})
        write(DATA / f"bsp_core/loot_tables/blocks/{b}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"bsp_core:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    for v in DOOR_UV:
        write(ASSETS / f"models/block/radiation_shielding_door_{v}.json", door_model(v))
    write(ASSETS / "models/item/radiation_shielding_door.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "bsp_core:item/radiation_shielding_door"}})
    # vanilla's iron door block state, with this door's models
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, base in rot.items():
        for half, part in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for is_open in (False, True):
                    y = (base + (90 if hinge == "left" else 270)) % 360 if is_open else base
                    v = {"model": f"bsp_core:block/radiation_shielding_door_{part}_{hinge}{'_open' if is_open else ''}"}
                    if y:
                        v["y"] = y
                    variants[f"facing={facing},half={half},hinge={hinge},open={'true' if is_open else 'false'}"] = v
    write(ASSETS / "blockstates/radiation_shielding_door.json", {"variants": variants})
    # a door drops once, from its lower half, as vanilla's doors
    write(DATA / "bsp_core/loot_tables/blocks/radiation_shielding_door.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": "bsp_core:radiation_shielding_door", "conditions": [{
            "condition": "minecraft:block_state_property", "block": "bsp_core:radiation_shielding_door", "properties": {"half": "lower"}}]}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    tag("mineable/pickaxe", SHIELD_BLOCKS)
    tag("needs_stone_tool", SHIELD_BLOCKS)
    tag("doors", ["radiation_shielding_door"])
    tag("doors", ["radiation_shielding_door"], kind="items")
    plates = {"conditions": [{"type": "forge:not", "value": {"type": "forge:tag_empty", "tag": "forge:plates/lead"}}]}
    write(DATA / "bsp_core/recipes/radiation_shielding_panel.json", {**plates, "type": "minecraft:crafting_shaped", "category": "building",
          "pattern": ["PPP", "PSP", "PPP"], "key": {"P": {"tag": "forge:plates/lead"}, "S": {"tag": "forge:stone"}},
          "result": {"item": "bsp_core:radiation_shielding_panel", "count": 2}})
    write(DATA / "bsp_core/recipes/radiation_shielding_window.json", {**plates, "type": "minecraft:crafting_shaped", "category": "building",
          "pattern": ["PGP", "PGP", "PGP"], "key": {"P": {"tag": "forge:plates/lead"}, "G": {"tag": "forge:glass"}},
          "result": {"item": "bsp_core:radiation_shielding_window", "count": 4}})
    write(DATA / "bsp_core/recipes/radiation_shielding_door.json", {"type": "minecraft:crafting_shaped", "category": "redstone",
          "pattern": ["PP", "PP", "PP"], "key": {"P": {"item": "bsp_core:radiation_shielding_panel"}},
          "result": {"item": "bsp_core:radiation_shielding_door", "count": 3}})


def main():
    chest()
    shield_set()
    shielding()
    recipes()
    print("lead assets written")


if __name__ == "__main__":
    main()
