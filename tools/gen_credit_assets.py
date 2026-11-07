#!/usr/bin/env python3
"""Generates the store credit ticket's assets for BSP-Core (no third-party dependencies).

  python3 tools/gen_credit_assets.py

The Admit-one stub (pick A of tools/preview/store_credit_concepts.html): a paper ticket with a notch in each end, a perforated tear
line and a gold star on the stub. Three 16 x 16 layers:
  store_credit_body     greys, tinted in game with the kind's dye colour (tint index 0, StoreCreditItem.tint);
  store_credit_print    the print lines and top highlight, tinted much lighter (tint index 1, StoreCreditItem.printTint);
  store_credit_details  the gold star, never tinted;
  store_credit_mark_c / _d  the black tab over the top left corner with a white C or a gold D, chosen by the item property
                         bsp_core:mark (0 none, 0.5 C, 1 D) through the model's overrides.
Also writes the item models. Rerun after changing anything here; never hand-edit the outputs.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gen_material_assets import write_png  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/bsp_core"
TEX = ASSETS / "textures/item"

# grey levels of the body roles; the tint (the dye made a little lighter in StoreCreditItem.tint) multiplies them
GREY = {"l": 255, "b": 222, "d": 160, "o": 82, "w": 255}
# the print lines and the top highlight, tinted much lighter than the body (tint index 1, StoreCreditItem.printTint)
PRINT = {"w": 255, "l": 205}
GOLD = {"g": (232, 193, 58), "h": (255, 240, 138)}
LETTERS = {"C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."]}


def shape():
    """The stub's roles by pixel, as the mock-up builds them."""
    p = [[None] * 16 for _ in range(16)]
    for y in range(3, 13):
        for x in range(16):
            p[y][x] = "b"
    for x, y in ((0, 7), (0, 8), (15, 7), (15, 8)):
        p[y][x] = None
    edge = [row[:] for row in p]
    for y in range(16):
        for x in range(16):
            if not p[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, yy = x + dx, y + dy
                if xx < 0 or yy < 0 or xx > 15 or yy > 15 or not p[yy][xx]:
                    edge[y][x] = "o"
    p = edge
    for x in range(16):
        if p[4][x] == "b":
            p[4][x] = "l"
        if p[11][x] == "b":
            p[11][x] = "d"
    for y in range(4, 12, 2):
        p[y][11] = "o"
    for x in range(3, 9):
        p[6][x] = "w"
    for x in range(3, 7):
        p[8][x] = "w"
    for x, y in ((13, 6), (12, 7), (14, 7), (12, 8), (14, 8), (13, 9)):
        p[y][x] = "g"
    for x, y in ((13, 7), (13, 8)):
        p[y][x] = "h"
    return p


def main():
    p = shape()

    def body(x, y):
        r = p[y][x]
        if r in ("l", "w"):  # under the print and the highlight: plain body (the print layer draws them)
            r = "b"
        if r in GREY:
            v = GREY[r]
            return bytes((v, v, v, 255))
        if r in GOLD:  # under the star: body grey, so nothing shows through the details layer's edges
            return bytes((GREY["b"],) * 3 + (255,))
        return bytes((0, 0, 0, 0))

    def details(x, y):
        r = p[y][x]
        return bytes(GOLD[r] + (255,)) if r in GOLD else bytes((0, 0, 0, 0))

    def printed(x, y):
        r = p[y][x]
        v = PRINT.get(r)
        return bytes((v, v, v, 255)) if v else bytes((0, 0, 0, 0))

    write_png(TEX / "store_credit_body.png", 16, 16, body)
    write_png(TEX / "store_credit_print.png", 16, 16, printed)
    write_png(TEX / "store_credit_details.png", 16, 16, details)
    for mark, ink in (("C", (255, 255, 255)), ("D", (255, 210, 58))):
        rows = LETTERS[mark]

        def tab(x, y, rows=rows, ink=ink):
            if x > 4 or y > 6:
                return bytes((0, 0, 0, 0))
            if 1 <= x <= 3 and 1 <= y <= 5 and rows[y - 1][x - 1] == "#":
                return bytes(ink + (255,))
            return bytes((21, 22, 27, 255))
        write_png(TEX / f"store_credit_mark_{mark.lower()}.png", 16, 16, tab)

    models = ASSETS / "models/item"
    models.mkdir(parents=True, exist_ok=True)
    layers = {"layer0": "bsp_core:item/store_credit_body", "layer1": "bsp_core:item/store_credit_print", "layer2": "bsp_core:item/store_credit_details"}
    (models / "store_credit.json").write_text(json.dumps({
        "parent": "minecraft:item/generated",
        "textures": layers,
        "overrides": [
            {"predicate": {"bsp_core:mark": 0.5}, "model": "bsp_core:item/store_credit_c"},
            {"predicate": {"bsp_core:mark": 1}, "model": "bsp_core:item/store_credit_d"},
        ],
    }, indent=2) + "\n")
    for mark in ("c", "d"):
        (models / f"store_credit_{mark}.json").write_text(json.dumps({
            "parent": "minecraft:item/generated",
            "textures": dict(layers, layer3=f"bsp_core:item/store_credit_mark_{mark}"),
        }, indent=2) + "\n")
    print("wrote the store credit ticket: 5 textures, 3 item models")


if __name__ == "__main__":
    main()
