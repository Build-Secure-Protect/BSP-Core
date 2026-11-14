#!/usr/bin/env python3
"""Writes the Plasma Tank's assets: Tank Casing (and its formed look: glass body, rails and corner nodes), Tank Glass, Tank Port, the
Spawn Grate (a face block that makes the tank a Mob Chamber), the Tank Extractor (a Separator Drum that sends one pool out) and Tetrium Glass (textures, models, blockstates, items, loot, tags, recipes). The plasma inside, the streams, the lit uprights, the
forming sweep and the port rings are drawn by TankRenderer.

  python3 tools/gen_tank_assets.py
"""
import json

from gen_factory_assets import loot
from gen_material_assets import ASSETS, CLEAR, hexc, hsh, mbox, write_png
from gen_projector_assets import DATA, model, shaped, write

PLASMA = {"atlas": "bsp_core:block/machine_atlas", "plasma": "bsp_core:block/plasma_still", "particle": "bsp_core:block/tank_casing", "casing": "bsp_core:block/tank_casing"}


def casing(x, y):
    """Dark hull with a pale rim and four rivets: the rims meet across blocks as the frame of the tank."""
    if x in (0, 15) or y in (0, 15):
        k = 0.9 + hsh(x, y, 71) * 0.2
        return tuple(min(255, int(v * k)) for v in hexc("#8A909C")[:3]) + (255,)
    if x in (1, 14) or y in (1, 14):
        return hexc("#565C6B")
    if (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):
        return hexc("#6B7280")
    k = 0.85 + hsh(x, y, 73) * 0.3
    return tuple(min(255, int(v * k)) for v in hexc("#1E222B")[:3]) + (255,)


def tank_glass(x, y):
    """A clear pane with no rim at all, so a wall of it is one sheet."""
    k = 0.92 + hsh(x, y, 79) * 0.16
    return (int(158 * k), int(197 * k), int(232 * k), 44)


def tetrium_glass(x, y):
    """Glass with a tetrium tint and a thin rim: the ingredient."""
    if x in (0, 15) or y in (0, 15):
        return (110, 90, 160, 150)
    k = 0.9 + hsh(x, y, 83) * 0.2
    return (int(150 * k), int(120 * k), int(210 * k), 72)


AXES = ("x", "y", "z")


def rail_model(axis, hi):
    """A formed casing's rail along one axis: a 3 px dark bar on the block's outer corner, the full length of the block. hi gives, for the
    other two axes, whether the corner is on the positive side. Horizontal rails carry a lit strip on the outer edge; the vertical
    ones do not, because TankRenderer draws their strip only as high as the plasma stands (the frame as a level gauge)."""
    a = AXES.index(axis)
    frm, to = [0, 0, 0], [16, 16, 16]
    sf, st = [0, 0, 0], [16, 16, 16]
    for i in range(3):
        if i == a:
            continue
        if hi[i]:
            frm[i], to[i] = 13, 16
            sf[i], st[i] = 15.1, 16.3
        else:
            frm[i], to[i] = 0, 3
            sf[i], st[i] = -0.3, 0.9
    elements = [mbox(frm, to, "hull")]
    if axis != "y":
        elements.append(mbox(sf, st, "turq"))
    return elements


def node_model(hi):
    """The lit corner node where three rails meet: a 4 px cube on the block's outer corner."""
    frm = [12 if hi[i] else -0.3 for i in range(3)]
    to = [16.3 if hi[i] else 4 for i in range(3)]
    return [mbox(frm, to, "turq")]


def hi_name(hi):
    return "".join("1" if v else "0" for v in hi)


def casing_blockstate():
    """Unformed: the riveted cube. Formed: a glass body, a rail per flagged axis on the corner the HI flags give, a node at a corner."""
    parts = [{"when": {"formed": "false"}, "apply": {"model": "bsp_core:block/tank_casing"}},
             {"when": {"formed": "true"}, "apply": {"model": "bsp_core:block/tank_casing_formed"}}]
    for axis in AXES:
        others = [b for b in AXES if b != axis]
        for p in range(2):
            for q in range(2):
                hi = {others[0]: bool(p), others[1]: bool(q)}
                hi3 = [hi.get(b, False) for b in AXES]
                when = {"formed": "true", f"along_{axis}": "true"}
                for b in others:
                    when[f"hi_{b}"] = "true" if hi[b] else "false"
                parts.append({"when": when, "apply": {"model": f"bsp_core:block/tank_rail_{axis}_{hi_name(hi3)}"}})
    for n in range(8):
        hi = [bool(n >> i & 1) for i in range(3)]
        when = {"formed": "true", "along_x": "true", "along_y": "true", "along_z": "true"}
        for i, b in enumerate(AXES):
            when[f"hi_{b}"] = "true" if hi[i] else "false"
        parts.append({"when": when, "apply": {"model": f"bsp_core:block/tank_node_{hi_name(hi)}"}})
    return {"multipart": parts}


def port_model():
    """A casing cube with, on every face, a ring of trim proud of the face and a lit window inside it. The setting ring is drawn by the renderer."""
    core = mbox([0, 0, 0], [16, 16, 16], "hull")
    for f in core["faces"]:
        core["faces"][f] = {"uv": [0, 0, 16, 16], "texture": "#casing", "cullface": f}
    e = [core]
    for face in ("north", "south", "east", "west", "up", "down"):
        ring = [mbox([3, 3, -1.2], [13, 4, 0], "trim"), mbox([3, 12, -1.2], [13, 13, 0], "trim"), mbox([3, 4, -1.2], [4, 12, 0], "trim"), mbox([12, 4, -1.2], [13, 12, 0], "trim")]
        window = {"from": [5, 5, -1.1], "to": [11, 11, -0.9], "shade": False, "forge_data": {"block_light": 15, "sky_light": 15}, "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#plasma"}}}
        for el in ring + [window]:
            e.append(turn(el, face))
    return e


def grate_model():
    """Design A of the Mob Chamber mock-ups, on every face (which face is outside is not known until the tank forms): a hull frame round a
    real recess, five grate bars set back in it, and above them a skull plate with two dark eyes and a red lamp below them. The lamp's
    pulse and the red glow inside are the renderer's."""
    core = mbox([0, 0, 0], [16, 16, 16], "hull")
    for f in core["faces"]:
        core["faces"][f] = {"uv": [0, 0, 16, 16], "texture": "#casing", "cullface": f}
    e = [core]
    for face in ("north", "south", "east", "west", "up", "down"):
        frame = [mbox([1, 1, -1], [15, 2.5, 0], "hull2"), mbox([1, 10.5, -1], [15, 15, 0], "hull2"), mbox([1, 2.5, -1], [2.5, 10.5, 0], "hull2"),
                 mbox([13.5, 2.5, -1], [15, 10.5, 0], "hull2")]
        hollow = [mbox([2.5, 2.5, -0.3], [13.5, 10.5, 0], "screen")]
        bars = [mbox([2.5, 3 + i * 1.6, -0.8], [13.5, 3.7 + i * 1.6, -0.3], "trim") for i in range(5)]
        skull = [mbox([5, 11.5, -1.2], [11, 14.6, -1], "hull"), mbox([6.2, 13.2, -1.35], [7.4, 14.2, -1.2], "screen"),
                 mbox([8.6, 13.2, -1.35], [9.8, 14.2, -1.2], "screen"), mbox([7.4, 11.9, -1.4], [8.6, 12.9, -1.2], "red")]
        for el in frame + hollow + bars + skull:
            e.append(turn(el, face))
    return e


# The Tank Extractor's lettering, 3 x 5: POOL on its plate, and the two step buttons.
GLYPHS = {"P": ["111", "101", "111", "100", "100"], "O": ["111", "101", "101", "101", "111"], "L": ["100", "100", "100", "100", "111"],
          "<": ["001", "011", "111", "011", "001"], ">": ["100", "110", "111", "110", "100"]}


def extractor_label(x, y):
    """Top half: the POOL plate (dark, pale letters). Bottom half: the two buttons, trim grey with a dark arrow: back on the left, on at the right."""
    if y < 7:
        word, x0 = "POOL", 1
        i, gx = (x - x0) // 4, (x - x0) % 4
        if 1 <= y <= 5 and 0 <= i < len(word) and gx < 3 and GLYPHS[word[i]][y - 1][gx] == "1":
            return hexc("#E8EAF0")
        return hexc("#1E222B")
    if y == 7:
        return CLEAR
    half, gx, gy = x // 8, x % 8 - 2, y - 9
    arrow = GLYPHS["<" if half == 0 else ">"]
    if 0 <= gx < 3 and 0 <= gy < 5 and arrow[gy][gx] == "1":
        return hexc("#0B0D11")
    if x % 8 == 7:
        return CLEAR
    return hexc("#8A909C") if 0 < y - 8 < 7 else hexc("#565C6B")


def extractor_glass(x, y):
    """The sight glass, drawn pale so the pool colour tints it; a brighter streak across it."""
    if x in (0, 15) or y in (0, 15):
        return hexc("#9AA0AA")
    return hexc("#FFFFFF") if x + y in (6, 7, 8) else hexc("#D8DCE4")


def extractor_model():
    """Design B of the Mob Chamber mock-ups, the Separator Drum, built facing north: a drum lying east to west between two end plates, two
    copper bands round it, a sight glass on its front and top (tinted with the pool's colour), and a low ledge in front carrying the
    two step buttons (◀ ▶) under a POOL plate. The back (south) goes against the Tank Port."""
    e = [mbox([0, 0, 2], [2.4, 14, 16], "hull"), mbox([13.6, 0, 2], [16, 14, 16], "hull"),
         mbox([2.4, 3, 3], [13.6, 11, 15], "mid"), mbox([2.4, 1, 5], [13.6, 13, 13], "mid")]
    for x in (4, 11):
        e += [mbox([x, 2.5, 2.5], [x + 1, 11.5, 15.5], "gold"), mbox([x, 0.5, 4.5], [x + 1, 13.5, 13.5], "gold")]
    e.append(mbox([0, 0, 0], [16, 4.5, 3], "hull2"))  # the ledge
    lab = {"from": [5.2, 5.4, 2.7], "to": [10.8, 8.2, 3], "faces": {"north": {"uv": [0, 0, 16, 7], "texture": "#label"}}}
    back = {"from": [9.4, 1, -0.6], "to": [13.4, 4, 0], "faces": {"north": {"uv": [0, 8, 7, 15], "texture": "#label"}, "up": {"uv": [0, 8, 7, 9], "texture": "#label"},
                                                                    "east": {"uv": [0, 8, 1, 15], "texture": "#label"}, "west": {"uv": [0, 8, 1, 15], "texture": "#label"}}}
    on = {"from": [2.6, 1, -0.6], "to": [6.6, 4, 0], "faces": {"north": {"uv": [8, 8, 15, 15], "texture": "#label"}, "up": {"uv": [8, 8, 15, 9], "texture": "#label"},
                                                                 "east": {"uv": [8, 8, 9, 15], "texture": "#label"}, "west": {"uv": [8, 8, 9, 15], "texture": "#label"}}}
    glow = {"shade": False, "forge_data": {"block_light": 15, "sky_light": 15}}
    front_glass = dict({"from": [5.6, 9, 2.4], "to": [10.4, 12, 3], "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#glass", "tintindex": 0}}}, **glow)
    top_glass = dict({"from": [5.6, 13, 6], "to": [10.4, 13.6, 12], "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#glass", "tintindex": 0},
                                                                              "north": {"uv": [0, 0, 16, 2], "texture": "#glass", "tintindex": 0}}}, **glow)
    return e + [lab, back, on, front_glass, top_glass]


def turn(el, face):
    """An element built on the north face, moved to another face of the block."""
    (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
    new = dict(el)
    new["faces"] = {}
    rot = {"north": lambda x, y, z: (x, y, z), "south": lambda x, y, z: (16 - x, y, 16 - z), "east": lambda x, y, z: (16 - z, y, x), "west": lambda x, y, z: (z, y, 16 - x),
           "up": lambda x, y, z: (x, 16 - z, y), "down": lambda x, y, z: (x, z, 16 - y)}[face]
    a, b = rot(x0, y0, z0), rot(x1, y1, z1)
    new["from"], new["to"] = [round(min(a[i], b[i]), 3) for i in range(3)], [round(max(a[i], b[i]), 3) for i in range(3)]
    fmap = {"north": {"north": "north", "south": "south", "east": "east", "west": "west", "up": "up", "down": "down"},
            "south": {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"},
            "east": {"north": "east", "south": "west", "east": "south", "west": "north", "up": "up", "down": "down"},
            "west": {"north": "west", "south": "east", "east": "north", "west": "south", "up": "up", "down": "down"},
            "up": {"north": "up", "south": "down", "east": "east", "west": "west", "up": "south", "down": "north"},
            "down": {"north": "down", "south": "up", "east": "east", "west": "west", "up": "north", "down": "south"}}[face]
    for f, v in el["faces"].items():
        new["faces"][fmap[f]] = {"uv": v["uv"], "texture": v["texture"]}
    return new


def main():
    write_png(ASSETS / "textures/block/tank_casing.png", 16, 16, casing)
    write_png(ASSETS / "textures/block/tank_glass.png", 16, 16, tank_glass)
    write_png(ASSETS / "textures/block/tetrium_glass.png", 16, 16, tetrium_glass)
    for name, render in (("tank_casing", None), ("tank_glass", "minecraft:translucent"), ("tetrium_glass", "minecraft:translucent")):
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": f"bsp_core:block/{name}"}}
        if render:
            m["render_type"] = render
        write(ASSETS / f"models/block/{name}.json", m)
        write(ASSETS / f"models/item/{name}.json", {"parent": f"bsp_core:block/{name}"})
        write(ASSETS / f"blockstates/{name}.json", {"variants": {"": {"model": f"bsp_core:block/{name}"}}})
        loot(name)
    # the formed look of the casing: glass body, rails and nodes (the blockstate is multipart; the item keeps the cube)
    write(ASSETS / "models/block/tank_casing_formed.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent", "textures": {"all": "bsp_core:block/tank_glass", "particle": "bsp_core:block/tank_casing"}})
    for axis in AXES:
        for n in range(8):
            hi = [bool(n >> i & 1) for i in range(3)]
            if hi[AXES.index(axis)]:
                continue  # the rail's own axis has no side
            m = model(rail_model(axis, hi), PLASMA, gui=False)
            write(ASSETS / f"models/block/tank_rail_{axis}_{hi_name(hi)}.json", m)
    for n in range(8):
        hi = [bool(n >> i & 1) for i in range(3)]
        write(ASSETS / f"models/block/tank_node_{hi_name(hi)}.json", model(node_model(hi), PLASMA, gui=False))
    write(ASSETS / "blockstates/tank_casing.json", casing_blockstate())
    port = model(port_model(), PLASMA, gui=False)
    port["render_type"] = "minecraft:cutout"
    write(ASSETS / "models/block/tank_port.json", port)
    write(ASSETS / "models/item/tank_port.json", model(port_model(), PLASMA))
    write(ASSETS / "blockstates/tank_port.json", {"variants": {"": {"model": "bsp_core:block/tank_port"}}})
    loot("tank_port")
    grate = model(grate_model(), PLASMA, gui=False)
    grate["render_type"] = "minecraft:cutout"
    write(ASSETS / "models/block/spawn_grate.json", grate)
    write(ASSETS / "models/item/spawn_grate.json", model(grate_model(), PLASMA))
    write(ASSETS / "blockstates/spawn_grate.json", {"variants": {"": {"model": "bsp_core:block/spawn_grate"}}})
    loot("spawn_grate")
    write_png(ASSETS / "textures/block/tank_extractor_label.png", 16, 16, extractor_label)
    write_png(ASSETS / "textures/block/tank_extractor_glass.png", 16, 16, extractor_glass)
    ex_tex = dict(PLASMA, label="bsp_core:block/tank_extractor_label", glass="bsp_core:block/tank_extractor_glass", particle="bsp_core:block/tank_casing")
    ex = model(extractor_model(), ex_tex, gui=False)
    write(ASSETS / "models/block/tank_extractor.json", ex)
    write(ASSETS / "models/item/tank_extractor.json", model(extractor_model(), ex_tex))
    write(ASSETS / "blockstates/tank_extractor.json", {"multipart": [{"when": {"facing": f}, "apply": {"model": "bsp_core:block/tank_extractor", "y": r}}
                                                                    for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))]})
    loot("tank_extractor")
    for tag, blocks in (("mineable/pickaxe", ["tank_casing", "tank_port", "tank_glass", "tetrium_glass", "spawn_grate", "tank_extractor"]), ("needs_iron_tool", ["tank_casing", "tank_port", "spawn_grate", "tank_extractor"])):
        path = DATA / f"minecraft/tags/blocks/{tag}.json"
        data = json.loads(path.read_text())
        for b in blocks:
            if f"bsp_core:{b}" not in data["values"]:
                data["values"].append(f"bsp_core:{b}")
        path.write_text(json.dumps(data, indent=2) + "\n")
    shaped("tetrium_glass", ["GGG", "GNG", "GGG"], {"G": "minecraft:glass", "N": "tetrium_nugget"}, 8)
    shaped("tank_casing", ["PPP", "PCP", "PPP"], {"P": "tetrium_plate", "C": "machine_chassis"}, 8)
    shaped("tank_glass", ["GGG", "GPG", "GGG"], {"G": "tetrium_glass", "P": "tetrium_plate"}, 8)
    shaped("tank_port", [" R ", "LCL", " P "], {"R": "charged_resonance_crystal", "L": "tetrium_core_cable", "C": "tank_casing", "P": "tetrium_plate"})
    shaped("spawn_grate", [" L ", "ICI", " P "], {"L": "minecraft:redstone_lamp", "I": "minecraft:iron_bars", "C": "tank_casing", "P": "tetrium_plate"})
    shaped("tank_extractor", [" G ", "BCB", " P "], {"G": "tetrium_glass", "B": "minecraft:copper_ingot", "C": "tank_casing", "P": "tetrium_plate"})
    print("tank assets written")


if __name__ == "__main__":
    main()
