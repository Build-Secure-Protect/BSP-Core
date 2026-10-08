#!/usr/bin/env python3
"""Writes the Plasma Tank's assets: Tank Casing (and its formed look: glass body, rails and corner nodes), Tank Glass, Tank Port and
Tetrium Glass (textures, models, blockstates, items, loot, tags, recipes). The plasma inside, the streams, the lit uprights, the
forming sweep and the port rings are drawn by TankRenderer.

  python3 tools/gen_tank_assets.py
"""
import json

from gen_factory_assets import loot
from gen_material_assets import ASSETS, hexc, hsh, mbox, write_png
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
    for tag, blocks in (("mineable/pickaxe", ["tank_casing", "tank_port", "tank_glass", "tetrium_glass"]), ("needs_iron_tool", ["tank_casing", "tank_port"])):
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
    print("tank assets written")


if __name__ == "__main__":
    main()
