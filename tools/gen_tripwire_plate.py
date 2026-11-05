#!/usr/bin/env python3
"""Writes tools/preview/tripwire_plate.html: Tripwire as built in 1.11.0, for the CurseForge feature plates.

  python3 tools/gen_tripwire_plate.py

It reuses the drawing code of tools/preview/tripwire_concepts.html (the game's font from ascii.png, TreeLayout's numbers,
the card at GUI pixel size) with the owner's picks only (A, A, A) and the built details: the short tier-limit reason, the
real totem model with its baked lamp studs from shatter_totem_geometry.json and the owned texture, and the lit lenses where
ShatterTotemRenderer draws them (0.2 px proud, 1.5 x 0.9 px). Run it again after the totem's model or texture changes.

Headless Chrome draws no WebGL, so the studs plate shows tripwire_studs.png, a still of the 3D view saved from a browser that
draws it: open tripwire_plate.html?still over http in such a browser and POST window.__studs() (a PNG data URL) to a server
that writes it to tools/preview/tripwire_studs.png. Then capture the plate with tools/gen_feature_plates.py.
"""
import base64
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools/preview"
ASSETS = ROOT / "src/main/resources/assets/bsp_core"


def b64(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


def main():
    concept = (PREVIEW / "tripwire_concepts.html").read_text()
    script = concept[concept.index("<script>\n(function(){"):concept.rindex("</script>") + len("</script>")]
    geo = json.loads((ASSETS / "models/block/shatter_totem_geometry.json").read_text())["elements"]
    script = re.sub(r"var GEO = \[.*?\];\n", lambda m: "var GEO = " + json.dumps(geo, separators=(",", ":")) + ";\n", script, count=1, flags=re.S)
    script = re.sub(r'TOTEM_TEX = "data:image/png;base64,[^"]*"', lambda m: 'TOTEM_TEX = "' + b64(ASSETS / "textures/block/shatter_totem_owned.png") + '"', script, count=1)
    # the built card: the short tier-limit reason on Tripwire's card
    old = 'small(g, cut("Tier " + ROMAN[tier] + " limit reached. Raise the totem to Tier " + ROMAN[tier + 1], 128 / 0.75), X, Y + 126, BAD);'
    assert old in script
    script = script.replace(old, 'small(g, "Tier " + ROMAN[tier] + " limit. Raise to Tier " + ROMAN[tier + 1], X, Y + 126, BAD);')
    # the built studs: the baked bezels and dull lenses are in GEO; the renderer's lit lenses go on top
    i = script.index("    a: function(g){ var lenses = [];")
    j = script.index("    b: function(g){", i)
    lit = '''    a: function(g){ var lenses = [];
      [0, 1, 2, 3].forEach(function(side){ [5, 11].forEach(function(c){ var a0 = c - .75, a1 = c + .75, y0 = .55, y1 = 1.45, o = .2, lo = 2, hi = 14, b;
        b = side === 0 ? [a0, y0, lo - o, a1, y1, lo] : side === 1 ? [a0, y0, hi, a1, y1, hi + o] : side === 2 ? [lo - o, y0, a0, lo, y1, a1] : [hi, y0, a0, hi + o, y1, a1];
        lenses.push(bx(g, [b[0], b[1], b[2]], [b[3], b[4], b[5]], 0xff3024, true)); }); });
      return function(t, on){ lenses.forEach(function(l, i){ l.visible = on; l.material.color.setHex(0xff3024); if (!reduce) l.material.color.multiplyScalar(.85 + .15 * Math.sin(t * 5 + i * 1.3)); }); }; },
'''
    script = script[:i] + lit + script[j:]
    # Tripwire's own orb is magenta (hue 310) among the others
    script = script.replace("var cols = [0xe8c45a, 0x6fd3ff, 0xff8f6b, 0xb58cff]", "var cols = [0xe8c45a, 0x6fd3ff, 0xff8f6b, 0xff3fd2]")
    # ?still holds the camera (for the saved still); the page hands its render out as a PNG for the studs plate (WebGL does not draw
    # headless, so the plate shows tripwire_studs.png, saved from a browser that draws it)
    script = script.replace("if (!reduce && !drag) ry += .0025;", "if (!reduce && !drag && !STILL) ry += .0025;")
    script = script.replace("var reduce = window.matchMedia", "var STILL = /[?&]still/.test(location.search); var reduce = window.matchMedia")
    script = script.replace("    var g = new THREE.Group(); scene.add(g); totem(g, swaps[k]);",
                            "    if (k === 'a') window.__studs = function(){ r.render(scene, cam); return r.domElement.toDataURL('image/png'); };\n    var g = new THREE.Group(); scene.add(g); totem(g, swaps[k]);")
    # a closer view of the base, from the statue's front corner, so the studs read on the plate
    script = script.replace("var D = 84, T = [14, 7, 6];", "var D = CAM.d, T = CAM.t;")
    # the camera, also settable from the address (?ry=&rx=&d=&tx=&ty=&tz=) to frame the still
    script = script.replace("var ry = Math.PI - .75, rx = .38,", "var ry = CAM.ry, rx = CAM.rx,")
    script = script.replace("var STILL = /[?&]still/.test(location.search);",
                            "var STILL = /[?&]still/.test(location.search), Q = new URLSearchParams(location.search), qn = function(k, v){ return Q.has(k) ? +Q.get(k) : v; };"
                            " var CAM = {ry: qn('ry', 3.95), rx: qn('rx', .24), d: qn('d', 70), t: [qn('tx', 16), qn('ty', 9), qn('tz', 6)]};")
    head = concept[:concept.index('<div class="wrap">')]
    head = head.replace("<title>Tripwire Concepts</title>", "<title>Tripwire Plate</title>")
    head = head.replace(".view{ width:100%; aspect-ratio:1/1;", ".view{ width:100%; aspect-ratio:4/3;")
    body = '''<div class="wrap">
  <h1>Tripwire Plate</h1>
  <p class="lede">Tripwire as built in 1.11.0, for the CurseForge feature plates (tools/gen_feature_plates.py): the tree with Tripwire's card, the card with the range line, and the lit lamp studs on the real model. Made by tools/gen_tripwire_plate.py from the concept page.</p>
  <div class="card"><h3>The tree and the card</h3><canvas class="gui" id="t-a" width="930" height="540"></canvas><dl class="facts" id="f-a" hidden></dl></div>
  <div class="card"><h3>The range line</h3><canvas class="gui" id="k-a" width="420" height="546"></canvas></div>
  <div class="card"><h3>The lamp studs, lit</h3><div class="view" id="v-a"></div></div>
  <div class="card"><h3>The same, saved for the plate (tripwire_studs.png)</h3><img id="s-img" src="tripwire_studs.png" alt="A Shatter Totem with its eight lamp studs lit red, wired with redstone dust to a lit lamp" style="width:100%;height:auto;border-radius:4px"></div>
</div>

'''
    tail = '<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>\n'
    (PREVIEW / "tripwire_plate.html").write_text(head + body + tail + script + "\n")
    print("written tools/preview/tripwire_plate.html")


if __name__ == "__main__":
    main()
