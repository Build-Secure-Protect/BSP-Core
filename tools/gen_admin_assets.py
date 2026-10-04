#!/usr/bin/env python3
"""Generates the Admin Rack assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_admin_assets.py

Writes the "Server Rack" model (a cabinet with five shelves of status lamps), its block state and
item model, and the animated lamp texture that makes the lamps blink: every lamp samples its own
pixel of the texture, and each frame of the animation lights a different set of pixels. The block
has no loot table: it cannot be mined in survival. Rerun after changing anything here; never
hand-edit the outputs.
"""
import json

from gen_material_assets import ASSETS, hexc, hsh, mbox, write_machine, write_png

ROWS, PER_ROW, FRAMES, FRAME_TICKS = 5, 6, 8, 6
VIOLET, TURQ = "#B58CFF", "#19D3B0"


def lamp_colour(r, i):
    return TURQ if (r * PER_ROW + i) % 7 == 3 else VIOLET


def lamps_pixel(x, y):
    """Frame f occupies rows f*16 to f*16+15; lamp (row r, column i) is the pixel (i, r) of each frame."""
    f, r = divmod(y, 16)
    if x >= PER_ROW or r >= ROWS:
        return (0, 0, 0, 255)
    on = hsh(x + r * 7, f, 91) > 0.4
    c = hexc(lamp_colour(r, x))
    k = 1.0 if on else 0.22
    return tuple(int(v * k) for v in c[:3]) + (255,)


def lamp(frm, to, r, i):
    uv = [i, r, i + 1, r + 1]
    return {"from": frm, "to": to, "shade": False, "forge_data": {"block_light": 15, "sky_light": 15},
            "faces": {face: {"uv": uv, "texture": "#lamps"} for face in ("north", "south", "east", "west", "up", "down")}}


def rack_elements():
    e = [mbox([1, 0, 2], [15, 16, 14], "hull"), mbox([1, 0, 1.4], [15, 1, 2], "trim"), mbox([1, 15, 1.4], [15, 16, 2], "trim")]
    for r in range(ROWS):
        y = 1.6 + r * 2.8
        e.append(mbox([2, y, 1.6], [14, y + 2.2, 2], "hull2"))
        for i in range(PER_ROW):
            e.append(lamp([3 + i * 1.7, y + 0.7, 1.3], [4 + i * 1.7, y + 1.5, 1.6], r, i))
        e.append(mbox([12.6, y + 0.5, 1.3], [13.6, y + 1.7, 1.6], "mid"))
    return e


def main():
    write_png(ASSETS / "textures/block/admin_rack_lamps.png", 16, 16 * FRAMES, lamps_pixel)
    (ASSETS / "textures/block/admin_rack_lamps.png.mcmeta").write_text(json.dumps({"animation": {"frametime": FRAME_TICKS}}, indent=2))
    write_machine("admin_rack", rack_elements())
    # write_machine only knows the shared atlas: add the lamp texture to the model it wrote
    path = ASSETS / "models/block/admin_rack.json"
    model = json.loads(path.read_text())
    model["textures"]["lamps"] = "bsp_core:block/admin_rack_lamps"
    path.write_text(json.dumps(model, indent=1))
    print("admin rack assets written")


if __name__ == "__main__":
    main()
