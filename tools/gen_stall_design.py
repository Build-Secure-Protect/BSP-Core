#!/usr/bin/env python3
"""Designs the Market Stall block models for BSP-Core (no third-party dependencies).

  python3 tools/gen_stall_design.py

Writes the deliverable of docs/STALL_MODEL_HANDOFF.md into tools/stall/ (eight block models, their
textures, layout.json, NOTES.md) and the preview page tools/preview/stall_models.html, which renders
those exact files. The mod's asset generator copies tools/stall/ into the assets. Rerun after
changes; never hand-edit outputs.
"""
import base64
import json
import math
import shutil
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/stall"
PREVIEW = ROOT / "tools/preview/stall_models.html"
NS = "bsp_core:block/stall/"
CLEAR = (0, 0, 0, 0)


# ----------------------------------------------------------------------------- colour helpers
def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def sh(c, k):
    return tuple(max(0, min(255, int(round(c[i] * k)))) for i in range(3)) + (c[3],)


def hsh(x, y, s):
    h = (x * 374761393 + y * 668265263 + s * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFFFF) / 4294967296


def jit(c, x, y, seed, amt):
    return sh(c, 1 + (hsh(x, y, seed) - 0.5) * 2 * amt)


# ----------------------------------------------------------------------------- textures
FONT = {
    "A": ["111", "101", "111", "101", "101"], "C": ["111", "100", "100", "100", "111"],
    "D": ["110", "101", "101", "101", "110"], "E": ["111", "100", "110", "100", "111"],
    "F": ["111", "100", "110", "100", "100"], "H": ["101", "101", "111", "101", "101"],
    "L": ["100", "100", "100", "100", "111"], "O": ["111", "101", "101", "101", "111"],
    "P": ["111", "101", "111", "100", "100"], "R": ["110", "101", "110", "101", "101"],
    "S": ["111", "100", "111", "001", "111"], "T": ["111", "010", "010", "010", "010"],
    "U": ["101", "101", "101", "101", "111"], " ": ["000"] * 5,
}


def text_width(msg, sx=1):
    return len(msg) * 4 * sx - sx


class Tex:
    def __init__(self, name, n=16, frames=1, frametime=2):
        self.name, self.n, self.frames, self.frametime = name, n, frames, frametime
        self.px = [[CLEAR] * n for _ in range(n * frames)]

    def set(self, x, y, c, f=0):
        if c is not None and 0 <= x < self.n and 0 <= y < self.n:
            self.px[f * self.n + y][x] = c

    def get(self, x, y, f=0):
        return self.px[f * self.n + y][x]

    def fill(self, fn, f=0, box=None):
        x0, y0, x1, y1 = box or (0, 0, self.n, self.n)
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.set(x, y, fn(x, y), f)

    def rect(self, x0, y0, x1, y1, c, f=0):
        self.fill(lambda x, y: c, f, (x0, y0, x1, y1))

    def line(self, x0, y0, x1, y1, c, f=0):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.set(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c, f)

    def path(self, pts, c, f=0):
        for a, b in zip(pts, pts[1:]):
            self.line(a[0], a[1], b[0], b[1], c, f)

    def text(self, msg, x, y, c, sx=1, sy=1, f=0):
        for ch in msg:
            for r, row in enumerate(FONT[ch]):
                for k, bit in enumerate(row):
                    if bit == "1":
                        for dx in range(sx):
                            for dy in range(sy):
                                self.set(x + k * sx + dx, y + r * sy + dy, c, f)
            x += 4 * sx

    def text_c(self, msg, y, c, sx=1, sy=1, f=0, x0=0, x1=None):
        x1 = self.n if x1 is None else x1
        self.text(msg, x0 + (x1 - x0 - text_width(msg, sx)) // 2, y, c, sx, sy, f)

    def png(self):
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for c in row:
                raw.extend(c)

        def chunk(tag, data):
            return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

        return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", self.n, self.n * self.frames, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


TEXTURES = {}


def T(name, n=16, frames=1, frametime=2):
    t = Tex(name, n, frames, frametime)
    TEXTURES[name] = t
    return t


# --- material painters
def planks_h(t, base, seam, seed, rows=4, box=None):
    def fn(x, y):
        p = y // rows
        if y % rows == rows - 1:
            return jit(seam, x, y, seed, 0.05)
        if x == (p * 7 + seed * 3) % 16:
            return sh(seam, 1.15)
        c = sh(base, 1 + (hsh(p, seed, 7) - 0.5) * 0.18)
        g = hsh(x // 3 + p * 11, y, seed + 1)
        c = sh(c, 0.86) if g < 0.18 else sh(c, 1.08) if g > 0.9 else c
        return jit(c, x, y, seed + 2, 0.04)
    t.fill(fn, box=box)


def grain_v(t, base, seed, dark=0.8):
    def fn(x, y):
        c = sh(base, 1 + (hsh(x, 0, seed) - 0.5) * 0.22)
        if hsh(x, y // 4, seed + 1) < 0.2:
            c = sh(c, dark)
        if (x - 5) ** 2 + (y - 10) ** 2 < 2.3:
            c = sh(base, 0.7)
        return jit(c, x, y, seed + 2, 0.05)
    t.fill(fn)


def weather(t, seed, grey=hexc("807a70"), amt=0.4):
    for y in range(t.n):
        for x in range(t.n):
            c = t.get(x, y)
            if c[3]:
                c = mix(c, grey, amt * (0.7 + hsh(x // 2, y // 2, seed) * 0.6))
                if hsh(x, y, seed + 1) < 0.05:
                    c = sh(c, 0.7)
                t.set(x, y, c)
    for i in range(3):
        x, y = int(hsh(i, 1, seed) * 14) + 1, int(hsh(i, 2, seed) * 14) + 1
        t.path([(x, y), (x + 2, y + 1), (x + 3, y + 1), (x + 5, y + 2)], sh(grey, 0.45))


def cobble(t, base, mortar, seed, moss=None):
    pts = [(hsh(i, 0, seed) * 16, hsh(i, 1, seed) * 16) for i in range(10)]

    def fn(x, y):
        ds = []
        for i, (px, py) in enumerate(pts):
            dx = min(abs(x + 0.5 - px), 16 - abs(x + 0.5 - px))
            dy = min(abs(y + 0.5 - py), 16 - abs(y + 0.5 - py))
            ds.append((math.hypot(dx, dy), i, y + 0.5 - py))
        ds.sort()
        if ds[1][0] - ds[0][0] < 0.85:
            c = jit(mortar, x, y, seed, 0.06)
        else:
            c = sh(base, 0.86 + hsh(ds[0][1], 3, seed) * 0.26)
            if ds[0][2] < -1.5:
                c = sh(c, 1.1)
            c = jit(c, x, y, seed + 1, 0.06)
        if moss and hsh(x // 2, y // 3, seed + 5) < 0.4 and hsh(x, y, seed + 6) < 0.8:
            c = jit(mix(c, moss, 0.75), x, y, seed + 7, 0.1)
        return c
    t.fill(fn)


def metal(t, base, light, dark, seed, bevel=True):
    def fn(x, y):
        if bevel and (x == 0 or y == 0):
            return light
        if bevel and (x == t.n - 1 or y == t.n - 1):
            return dark
        c = jit(base, x, y, seed, 0.05)
        if (x + y) % 9 == 0:
            c = mix(c, light, 0.35)
        return c
    t.fill(fn)


def plate(t, base, light, dark, rivet, seed, step=8, patina=None):
    def fn(x, y):
        mx, my = x % step, y % step
        if mx == step - 1 or my == step - 1:
            c = dark
        elif mx == 0 or my == 0:
            c = mix(base, light, 0.5)
        else:
            c = jit(base, x, y, seed, 0.06)
            if hsh(x, y // 3, seed + 1) < 0.12:
                c = sh(c, 0.9)
        if mx in (2, step - 3) and my in (2, step - 3):
            c = rivet
        elif mx in (2, step - 3) and my in (3, step - 2):
            c = sh(dark, 0.9)
        if patina is not None:
            drip = mx in (2, step - 3) and my > 2 and hsh(x, 9, seed) < 0.7
            if drip or hsh(x, y // 2, seed + 3) < 0.3:
                c = jit(mix(c, patina, 0.65 if drip else 0.4), x, y, seed + 4, 0.08)
        return c
    t.fill(fn)


def stripes(t, a, b, seed, w=4, wool=False):
    def fn(x, y):
        c = a if (x // w) % 2 == 0 else b
        if x % w == 0:
            c = sh(c, 0.9)
        if wool and (x * 3 + y * 5) % 7 == 0:
            c = sh(c, 0.92)
        c = jit(c, x, y, seed, 0.05)
        return sh(c, 0.97) if (x + y) % 2 == 0 else c
    t.fill(fn)


def holes(t, seed, n, rmax=2.2, f=0):
    for i in range(n):
        cx, cy, r = hsh(i, 1, seed) * t.n, hsh(i, 2, seed) * t.n, 0.9 + hsh(i, 3, seed) * rmax
        for y in range(t.n):
            for x in range(t.n):
                if math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.4) < r + hsh(x, y, seed) * 0.8:
                    t.set(x, y, CLEAR, f)
                    if hsh(x, y, seed + 9) < 0.0:
                        pass


def fade(t, toward, amt, seed):
    for y in range(t.n * t.frames):
        for x in range(t.n):
            c = t.px[y][x]
            if c[3]:
                c = mix(c, toward, amt)
                if hsh(x // 3, y // 2, seed) < 0.25:
                    c = sh(c, 0.82)
                t.px[y][x] = c


def lantern_tex(t, lit, frame, seed):
    def fn(x, y):
        if x in (0, 1, 14, 15) or y in (0, 1, 14, 15) or (x in (7, 8) and y < 4):
            return sh(frame, 1.4) if (x in (0, 15) and y in (0, 15)) else jit(frame, x, y, seed, 0.08)
        r = math.hypot(x - 7.5, y - 8)
        if lit:
            return hexc("fff4c0") if r < 2.6 else hexc("ffd56b") if r < 4.6 else hexc("f0a440")
        return jit(hexc("3a332b"), x, y, seed, 0.1) if (x + y) % 5 else hexc("4f463a")
    t.fill(fn)


def web_tex(t, col, seed):
    rays = [0.08, 0.42, 0.8, 1.17, 1.5]
    for y in range(16):
        for x in range(16):
            r, a = math.hypot(x + 0.5, y + 0.5), math.atan2(y + 0.5, x + 0.5)
            on = any(abs(a - ra) * r < 0.55 for ra in rays) and r < 15.5
            on = on or (any(abs(r - R) < 0.5 for R in (4, 7.5, 11, 14.5)) and hsh(x, y, seed) < 0.8)
            if on and hsh(x, y, seed + 1) > 0.12:
                t.set(x, y, jit(col, x, y, seed, 0.08))


def lamp_tex(t, rim, glass, glass_rows=(3, 13), glass_cols=(0, 16), seed=1):
    def fn(x, y):
        if glass_rows[0] <= y < glass_rows[1] and glass_cols[0] <= x < glass_cols[1]:
            return hexc("8a8a8a") if (x, y) in ((glass_cols[0] + 2, glass_rows[0] + 1), (glass_cols[0] + 3, glass_rows[0] + 1)) \
                else jit(glass, x, y, seed, 0.06)
        return jit(rim, x, y, seed + 1, 0.08)
    t.fill(fn)


def note_tex(t, lines, ink, paper, seed, pin=hexc("b02a2a"), shape="note"):
    for y in range(32):
        for x in range(32):
            inside = 2 <= x <= 29 and 2 <= y <= 29
            if shape == "tag":
                inside = 4 <= x <= 27 and 1 <= y <= 30 and not (y < 6 and (x - 4 < 6 - y or 27 - x < 6 - y))
            if not inside:
                continue
            edge = min(x - 2, 29 - x, y - 2, 29 - y) if shape == "note" else min(x - 4, 27 - x, y - 1, 30 - y)
            if edge == 0 and hsh(x, y, seed) < 0.3:
                continue
            if shape == "note" and x + y > 54:
                continue
            c = jit(paper, x, y, seed, 0.05)
            if edge < 2:
                c = sh(c, 0.88)
            if hsh(x // 4, y // 3, seed + 2) < 0.15:
                c = sh(c, 0.93)
            t.set(x, y, c)
    if shape == "tag":
        for y in range(2, 7):
            for x in range(13, 19):
                r = math.hypot(x - 15.5, y - 4)
                if r < 1.3:
                    t.set(x, y, CLEAR)
                elif r < 2.4:
                    t.set(x, y, hexc("b8913a"))
    else:
        for y in range(2, 8):
            for x in range(13, 19):
                r = math.hypot(x - 15.5, y - 4.5)
                if r < 1.8:
                    t.set(x, y, hexc("f08a7a") if r < 0.7 else pin)
    for msg, y, sx, sy in lines:
        if msg == "~":
            for x in range(6, 25):
                if hsh(x, y, seed + 4) < 0.75:
                    t.set(x, y + (1 if hsh(x // 3, y, seed) < 0.3 else 0), sh(ink, 1.3))
        else:
            t.text_c(msg, y, ink, sx, sy)


# --- the four texture sets
def fantasy_textures():
    grain_v(T("stall_fantasy_timber"), hexc("4f3424"), 11)
    planks_h(T("stall_fantasy_planks"), hexc("7d5534"), hexc("452b18"), 12)
    t = T("stall_fantasy_planks_worn")
    planks_h(t, hexc("7d5534"), hexc("452b18"), 12)
    weather(t, 13)
    t.rect(9, 8, 16, 11, hexc("1d140c"))
    crim, cream = hexc("a3262e"), hexc("eadcbf")
    stripes(T("stall_fantasy_cloth"), crim, cream, 14)
    t = T("stall_fantasy_cloth_torn")
    stripes(t, mix(crim, hexc("6b6b63"), 0.45), mix(cream, hexc("8a8170"), 0.35), 14)
    fade(t, hexc("5a5248"), 0.12, 15)
    holes(t, 16, 6)
    t = T("stall_fantasy_valance")
    for x in range(16):
        c = crim if (x // 4) % 2 == 0 else cream
        t.set(x, 0, jit(hexc("d9a63a"), x, 0, 3, 0.1))
        t.set(x, 1, c)
        t.set(x, 2, sh(c, 0.92))
        if x % 4 in (1, 2):
            t.set(x, 3, sh(c, 0.85))
    for torn in (False, True):
        t = T("stall_fantasy_banner_torn" if torn else "stall_fantasy_banner")
        blue, gold, red = hexc("27427e"), hexc("d9a63a"), hexc("a3262e")
        if torn:
            blue, gold, red = mix(blue, hexc("5a5a5a"), 0.4), mix(gold, hexc("6a6050"), 0.4), mix(red, hexc("5a5a5a"), 0.4)
        for y in range(10):
            for lx in range(8):
                if (y == 8 and lx in (3, 4)) or (y == 9 and lx in (2, 3, 4, 5)):
                    continue
                if lx in (0, 7) or y == 0 or (y == 8 and lx in (2, 5)) or y == 9:
                    c = gold
                else:
                    c = sh(blue, 0.88) if lx in (2, 5) else blue
                    r = math.hypot(lx - 3.5, y - 4)
                    if 1.2 < r < 2.3:
                        c = gold
                    elif r <= 1.2:
                        c = red
                t.set(lx + 4, y, jit(c, lx, y, 21, 0.05))
        if torn:
            for (x, y) in ((10, 8), (11, 8), (10, 9), (11, 9), (11, 7), (6, 5), (7, 6), (7, 7), (8, 7), (5, 9), (4, 8), (9, 2)):
                t.set(x, y, CLEAR)
    metal(T("stall_fantasy_gold"), hexc("c9973a"), hexc("f0cf72"), hexc("8a6420"), 17)
    t = T("stall_fantasy_goods")
    for y in range(8):                       # crate side
        for x in range(8):
            if x in (0, 7) or y in (0, 7):
                c = hexc("a07a48")
            elif y == 3 or y == 5:
                c = hexc("3d2a18")
            else:
                c = hexc("8a6638")
            if x == y and 0 < x < 7:
                c = hexc("b08a55")
            t.set(x, y, jit(c, x, y, 22, 0.06))
    t.fill(lambda x, y: sh(jit(hexc("b59d6c"), x, y, 23, 0.07), 0.9 if (x + y) % 2 else 1.0)
           if not (y < 2 and 10 <= x <= 13) else hexc("8c774e"), box=(8, 0, 16, 8))
    for x0, glass, hi in ((0, hexc("2f7d4f"), hexc("7fcf9a")), (4, hexc("8e2433"), hexc("e07a8a"))):
        for x in range(x0, x0 + 4):
            t.set(x, 8, hexc("9c7a52"))
            for y in range(9, 16):
                t.set(x, y, hi if x == x0 + 1 else sh(glass, 0.75) if x == x0 + 3 else glass)

    def apples(x, y):
        for (ax, ay) in ((10, 10), (13.5, 10.5), (11, 13.5), (14, 14)):
            r = math.hypot(x + 0.5 - ax, y + 0.5 - ay)
            if r < 1.6:
                return hexc("f07a6a") if (x + 0.5 - ax < -0.4 and y + 0.5 - ay < -0.4) else hexc("c8302c")
        return hexc("4f7a2a") if (x, y) in ((10, 8), (14, 9)) else jit(hexc("3d2a18"), x, y, 24, 0.1)
    t.fill(apples, box=(8, 8, 16, 16))
    lantern_tex(T("stall_fantasy_lantern"), True, hexc("2c2a2e"), 25)
    lantern_tex(T("stall_fantasy_lantern_off"), False, hexc("2c2a2e"), 25)
    lamp_tex(T("stall_fantasy_lamp"), hexc("c9973a"), hexc("1e1b17"))
    t = T("stall_fantasy_curtain")
    t.fill(lambda x, y: jit(sh(hexc("2f4a35"), 0.92 + 0.12 * math.sin(x * math.pi / 3)), x, y, 26, 0.04))
    web_tex(T("stall_fantasy_web"), hexc("d8d8d4"), 27)
    note_tex(T("stall_fantasy_note", 32), [("CLOSED", 9, 1, 2), ("~", 22, 1, 1), ("~", 25, 1, 1)],
             hexc("3a2a1a"), hexc("e3d3a8"), 28)


def scifi_textures():
    def panel(t, dirty):
        def fn(x, y):
            if x == 0 or y == 0:
                c = hexc("f4f7fa")
            elif x == 15 or y == 15:
                c = hexc("aab2bd")
            elif y == 8:
                c = hexc("c3cad3")
            elif (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
                c = hexc("9aa3ae")
            else:
                c = jit(hexc("dfe4ea"), x, y, 31, 0.025)
            if dirty:
                c = mix(c, hexc("8a8f80"), 0.25 + 0.3 * hsh(x // 3, y // 2, 32))
                d = math.hypot(x - 11, y - 5)
                if d < 3.5:
                    c = mix(c, hexc("2a2420"), max(0, 1 - d / 3.5) * 0.9 + hsh(x, y, 33) * 0.1)
            return c
        t.fill(fn)
        if dirty:
            t.path([(1, 11), (4, 12), (7, 12)], hexc("6a6e70"))
    panel(T("stall_scifi_panel"), False)
    panel(T("stall_scifi_panel_dirty"), True)
    t = T("stall_scifi_hull")
    t.fill(lambda x, y: hexc("1f242b") if x % 8 == 7 or y % 8 == 7 else hexc("39414c") if x % 8 == 0 or y % 8 == 0
           else jit(hexc("2b313b"), x, y, 34, 0.04))
    T("stall_scifi_strip").fill(lambda x, y: jit(hexc("6fe6ff"), x, y, 35, 0.05) if (x + y) % 5 else hexc("b8f6ff"))
    T("stall_scifi_strip_off").fill(lambda x, y: jit(hexc("1f2a31"), x, y, 36, 0.08))
    T("stall_scifi_error").fill(lambda x, y: hexc("ffd0c8") if math.hypot(x - 7.5, y - 7.5) < 3 else hexc("ff4b3b"))
    C1, C2, C3 = hexc("8ff4ff"), hexc("3cc4ea"), hexc("1f8fc0")
    for name, label in (("stall_scifi_holo", "SHOP"), ("stall_scifi_holo_label", None)):
        holo_frames(T(name, 32, 6, 3), label, C1, C2, C3)


def holo_frames(t, label, C1, C2, C3):
    for f in range(6):
        for i in range(4):
            for (x, y) in ((i, 0), (0, i), (31 - i, 0), (31, i), (i, 17), (0, 17 - i), (31 - i, 17), (31, 17 - i)):
                t.set(x, y, C1, f)
        if label:
            t.text(label, 3, 2, C1, f=f)
        else:
            t.rect(3, 3, 17, 5, C2, f)
        for x in range(3, 21, 2):
            t.set(x, 8, C3, f)
        if f % 2 == 0:
            for y in range(2, 7):
                for x in range(23, 29):
                    r = math.hypot(x - 25.5, y - 4)
                    if 1.4 < r < 2.6:
                        t.set(x, y, C2, f)
        for k, y in enumerate((10, 13)):
            t.rect(3, y, 5, y + 2, C2, f)
            t.rect(7, y, 7 + 8 + k * 5, y + 1, C3, f)
            t.rect(24, y, 29, y + 2, C1, f)
        sy = (f * 3 + 1) % 18
        for x in range(1, 31, 2):
            t.set(x, sy, C1, f)


def scifi_textures_rest():
    C1, C2, C3 = hexc("8ff4ff"), hexc("3cc4ea"), hexc("1f8fc0")
    t = T("stall_scifi_holo_broken", 32, 8, 3)
    for f in range(8):
        for y in range(18):
            for x in range(32):
                if math.hypot(x - 23, y - 5) < 2.2 + hsh(x, y, 37) * 1.2:
                    continue
                t.set(x, y, jit(hexc("0e171c"), x, y, 38, 0.15), f)
        for p in ([(23, 5), (17, 8), (14, 7), (9, 12), (3, 13)], [(23, 5), (27, 11), (30, 16)], [(23, 5), (21, 1)],
                  [(23, 5), (29, 2)], [(17, 8), (18, 15)]):
            t.path(p, hexc("6c8a96"), f)
        if f in (2, 5):
            for k in range(4):
                y = int(hsh(k, f, 39) * 16)
                off = int(hsh(k, f, 40) * 10)
                for x in range(2 + off, 14 + off):
                    t.set(x, y, C2 if k % 2 else hexc("ff4fd8"), f)
        if f == 5:
            t.text("ERR", 4, 6, hexc("ff4b3b"), f=f)
    for name, ring, mid in (("stall_scifi_pad", hexc("6fe6ff"), hexc("3cc4ea")), ("stall_scifi_pad_off", hexc("4a5562"), hexc("39424d"))):
        def pad(x, y, ring=ring, mid=mid):
            r = math.hypot(x - 7.5, y - 7.5)
            return ring if 5.2 < r < 6.6 else mid if 2.4 < r < 3.3 else ring if r < 1 else jit(hexc("1d232b"), x, y, 41, 0.05)
        T(name).fill(pad)
    t = T("stall_scifi_detail")
    t.rect(0, 0, 8, 8, hexc("20262e"))
    for kx in (1, 3, 5):
        for ky in (1, 3, 5):
            t.set(kx, ky, hexc("58f29a") if (kx, ky) == (5, 5) else hexc("c8d0da"))
            t.set(kx + 1, ky, hexc("8a939e"))
    t.rect(1, 7, 7, 8, hexc("3cc4ea"))
    t.rect(8, 0, 16, 1, hexc("6fe6ff"))
    t.rect(8, 1, 16, 4, hexc("07090c"))
    t.rect(8, 4, 16, 8, hexc("2b313b"))
    t.rect(9, 5, 15, 6, hexc("07090c"))
    t.fill(lambda x, y: hexc("3a424d") if y % 2 == 0 else hexc("151a20"), box=(0, 8, 8, 16))
    t.rect(8, 8, 16, 16, hexc("2b313b"))
    t.path([(12, 9), (14, 12), (12, 15), (10, 12), (12, 9)], hexc("3cc4ea"))
    t = T("stall_scifi_circuit")
    t.fill(lambda x, y: jit(hexc("1e5a3c"), x, y, 42, 0.06))
    for y in (3, 8, 12):
        t.line(1, y, 14, y, hexc("c9a74a"))
    for x in (5, 11):
        t.line(x, 1, x, 14, hexc("b8963e"))
    t.rect(2, 5, 6, 7, hexc("111316"))
    t.rect(9, 9, 14, 11, hexc("111316"))
    for c, x0 in ((hexc("c8302c"), 0), (hexc("e8c23a"), 4), (hexc("3a6fd8"), 8), (hexc("2a2a2e"), 12)):
        T("stall_scifi_cable") if x0 == 0 else None
        TEXTURES["stall_scifi_cable"].fill(lambda x, y, c=c: sh(c, 1.15) if x % 4 == 1 else jit(c, x, y, 43, 0.06),
                                           box=(x0, 0, x0 + 4, 16))
    t = T("stall_scifi_tape", 32)
    for band, msg in ((0, "OUT OF"), (8, "ORDER")):
        for y in range(band, band + 8):
            for x in range(32):
                if y in (band, band + 7):
                    c = hexc("1d1d1d")
                elif x < 4 or x > 27:
                    c = hexc("1d1d1d") if ((x + y) // 2) % 2 else hexc("f2c330")
                else:
                    c = jit(hexc("f2c330"), x, y, 44, 0.04)
                if (x == 0 or x == 31) and hsh(x, y, 45) < 0.5:
                    continue
                t.set(x, y, c)
        t.text_c(msg, band + 1, hexc("161616"))
    lamp_tex(T("stall_scifi_lamp"), hexc("556070"), hexc("12161b"), (2, 14), (2, 14))


def steampunk_textures():
    copper = (hexc("b8693a"), hexc("e0955a"), hexc("6e381e"), hexc("f2c08a"))
    green = hexc("5fa38a")
    plate(T("stall_steampunk_copper"), *copper, 51)
    plate(T("stall_steampunk_copper_patina"), *copper, 51, patina=green)
    plate(T("stall_steampunk_iron"), hexc("3b3d42"), hexc("55585f"), hexc("232428"), hexc("8a8e96"), 52)
    metal(T("stall_steampunk_brass"), hexc("c9a03f"), hexc("f1d77a"), hexc("7c5c1c"), 53)
    planks_h(T("stall_steampunk_wood"), hexc("5a3624"), hexc("2e1a10"), 54, rows=5)
    T("stall_steampunk_pipe").fill(lambda x, y: jit(hexc("c0703c") if x % 6 else hexc("e0955a"), x, y, 55, 0.06))
    t = T("stall_steampunk_pipe_patina")
    t.fill(lambda x, y: jit(mix(hexc("b8693a"), green, 0.2 + 0.4 * hsh(x, y // 2, 56)), x, y, 55, 0.08))
    for broken in (False, True):
        t = T("stall_steampunk_dial_broken" if broken else "stall_steampunk_dial")
        needle = math.radians(-140 if broken else 55)
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - 8, y + 0.5 - 8
                r = math.hypot(dx, dy)
                a = math.degrees(math.atan2(dx, -dy))
                if r >= 7.9:
                    continue
                if r >= 6.4:
                    c = hexc("f1d77a") if dx + dy < -2 else hexc("7c5c1c") if dx + dy > 3 else hexc("c9a03f")
                elif r < 1.3:
                    c = hexc("c9a03f")
                else:
                    c = jit(hexc("efe6cc"), x, y, 57, 0.03)
                    if 4.8 < r < 6.1 and abs(((a + 135) % 27) - 13.5) > 11 and -140 < a < 140:
                        c = hexc("2a2622")
                    if 5.0 < r < 6.2 and 75 < a < 135:
                        c = hexc("c0302a")
                    nx, ny = math.sin(needle), -math.cos(needle)
                    along = dx * nx + dy * ny
                    if 0 < along < 5.4 and abs(dx * ny - dy * nx) < 0.6:
                        c = hexc("1a1614")
                t.set(x, y, c)
        if broken:
            t.path([(3, 4), (6, 6), (7, 9), (11, 11), (12, 14)], hexc("ffffff"))
            t.path([(7, 9), (4, 11)], hexc("e8e8e8"))
            t.path([(6, 6), (9, 3)], hexc("e8e8e8"))
    for lit in (True, False):
        t = T("stall_steampunk_firebox" if lit else "stall_steampunk_firebox_cold", 16, 4 if lit else 1, 3)
        for f in range(t.frames):
            def fb(x, y, f=f):
                if x < 2 or x > 13 or y < 2 or y > 13:
                    return hexc("8a8e96") if (x in (0, 15) and y in (0, 15)) else jit(hexc("2a2b2f"), x, y, 58, 0.08)
                if x % 3 == 1:
                    return hexc("1a1a1c")
                n = hsh(x, y + f * 17, 59) * 0.6 + (y - 2) / 12 * 0.6
                if not lit:
                    return jit(hexc("5a5652") if n > 0.7 else hexc("2e2c2a"), x, y, 60, 0.1)
                return hexc("fff0a0") if n > 0.95 else hexc("ffb43a") if n > 0.72 else hexc("e0561e") if n > 0.45 else hexc("7a1e10")
            t.fill(fb, f)
    for broken in (False, True):
        t = T("stall_steampunk_glass_broken" if broken else "stall_steampunk_glass")
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y == 0:
                    t.set(x, y, hexc("a9c8d0"))
                elif (x in (2, 3) and 2 <= y <= 13) or (x == 12 and 3 <= y <= 9):
                    t.set(x, y, hexc("e8f4f6") if x != 12 else hexc("cfe4ea"))
        if broken:
            for y in range(16):
                for x in range(16):
                    if x + y * 0.8 > 17 and math.hypot(x - 12, y - 6) < 5.5:
                        t.set(x, y, CLEAR)
            t.path([(8, 1), (7, 5), (9, 8), (8, 12), (10, 15)], hexc("e8f4f6"))
            t.path([(9, 8), (13, 9)], hexc("e8f4f6"))
    t = T("stall_steampunk_gear")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            body = r < 5.4 or (r < 7.6 and math.cos(a * 8) > 0.2)
            window = 2.3 < r < 4.2 and abs(math.sin(a * 2)) > 0.45
            if body and r >= 1.5 and not window:
                c = hexc("f1d77a") if dx + dy < -4 else hexc("7c5c1c") if dx + dy > 4 else hexc("c9a03f")
                t.set(x, y, jit(c, x, y, 61, 0.05))
    note_tex(T("stall_steampunk_tag", 32), [("OUT OF", 9, 1, 2), ("ORDER", 20, 1, 2)], hexc("6a2a1a"), hexc("d9c79a"), 62,
             shape="tag")
    t = T("stall_steampunk_steam")
    for y in range(16):
        for x in range(16):
            d = min(math.hypot(x - cx, y - cy) - rr for cx, cy, rr in ((8, 11, 4), (5, 6, 3), (10, 4, 2.6), (12, 8, 2.8)))
            if d + hsh(x, y, 63) * 1.4 < 0.6:
                t.set(x, y, hexc("f2f4f4") if hsh(x, y, 64) > 0.3 else hexc("d6dcdc"))
    t = T("stall_steampunk_puddle")
    for y in range(16):
        for x in range(16):
            d = min(math.hypot((x - cx) * 0.8, y - cy) - rr for cx, cy, rr in ((8, 8, 5), (4, 11, 3), (12, 5, 3.2)))
            if d + hsh(x, y, 65) * 1.2 < 0.5:
                t.set(x, y, hexc("9fc6da") if (x + y) % 7 == 0 else jit(hexc("3d6a8a"), x, y, 66, 0.06))
    lamp_tex(T("stall_steampunk_lamp"), hexc("c9a03f"), hexc("231d16"), (3, 13), (3, 13))


def vanilla_textures():
    oak, seam = hexc("a5844f"), hexc("6d5532")
    planks_h(T("stall_vanilla_planks"), oak, seam, 71)
    t = T("stall_vanilla_planks_old")
    planks_h(t, oak, seam, 71)
    weather(t, 72, hexc("7d7466"), 0.3)
    cobble(T("stall_vanilla_cobble"), hexc("838383"), hexc("575757"), 73)
    cobble(T("stall_vanilla_cobble_mossy"), hexc("838383"), hexc("575757"), 73, moss=hexc("5b7a3a"))
    red, white = hexc("b02e26"), hexc("e9ecec")
    stripes(T("stall_vanilla_awning"), red, white, 74, wool=True)
    t = T("stall_vanilla_awning_torn")
    stripes(t, mix(red, hexc("6b5b50"), 0.35), mix(white, hexc("9a968a"), 0.35), 74, wool=True)
    holes(t, 75, 5)
    t = T("stall_vanilla_trapdoor")
    planks_h(t, sh(oak, 0.9), seam, 76)
    for y in range(7):
        for x in range(7):
            if x in (0, 6) or y in (0, 6) or x == 3 or y == 3:
                t.set(x, y, jit(sh(oak, 0.82) if (x in (0, 6) or y in (0, 6)) else sh(oak, 0.95), x, y, 77, 0.05))
            else:
                t.set(x, y, CLEAR)
    t.set(0, 1, hexc("3a3a3a"))
    t.set(0, 5, hexc("3a3a3a"))
    for name, msg, sx in (("stall_vanilla_sign", "SHOP", 2), ("stall_vanilla_sign_closed", "CLOSED", 1), ("stall_vanilla_sign_blank", "", 1)):
        t = T(name, 32)
        planks_h(t, oak, seam, 78, rows=5)
        t.fill(lambda x, y: seam if x in (0, 31) or y in (0, 15) else None, box=(0, 0, 32, 16))
        if msg:
            t.text_c(msg, 3, hexc("2a1d10"), sx, 2)
    lantern_tex(T("stall_vanilla_lantern"), True, hexc("3a3f48"), 79)
    lantern_tex(T("stall_vanilla_lantern_off"), False, hexc("3a3f48"), 79)
    t = T("stall_vanilla_misc")
    t.fill(lambda x, y: jit(hexc("a35b3b") if y > 1 else hexc("7a3f28"), x, y, 80, 0.06), box=(0, 0, 8, 8))
    t.fill(lambda x, y: jit(hexc("6b4a2f"), x, y, 81, 0.12), box=(8, 0, 16, 4))
    t.fill(lambda x, y: jit(hexc("33363c"), x, y, 82, 0.1), box=(8, 4, 16, 8))
    for x0, stem, bloom in ((0, hexc("3f7a2a"), hexc("d42a1e")), (8, hexc("6b5a35"), hexc("7a5a3a"))):
        for y in range(12, 16):
            t.set(x0 + 3, y, stem)
        t.set(x0 + 2, 13, stem)
        t.set(x0 + 4, 14, stem)
        if x0 == 0:
            for (x, y) in ((2, 9), (3, 9), (4, 9), (1, 10), (2, 10), (3, 10), (4, 10), (5, 10), (2, 11), (3, 11), (4, 11)):
                t.set(x, y, hexc("1a1a1a") if (x, y) == (3, 10) else bloom)
        else:
            for (x, y) in ((12, 11), (13, 12), (10, 11), (9, 12), (11, 10)):
                t.set(x, y, bloom)
    metal(T("stall_vanilla_bell"), hexc("e8b83a"), hexc("fff09a"), hexc("a07a1a"), 83)
    t = T("stall_vanilla_lamp")
    t.fill(lambda x, y: hexc("3d2a1a") if x % 5 == 0 or y % 5 == 0 else jit(hexc("6b4a2c"), x, y, 84, 0.08))
    web_tex(T("stall_vanilla_web"), hexc("dcdcdc"), 85)


# ----------------------------------------------------------------------------- models
FACES = ("north", "south", "east", "west", "up", "down")


def num(v):
    v = round(float(v), 4)
    return int(v) if v.is_integer() else v


def wrap(a, b):
    span = b - a
    if span >= 16:
        return 0.0, 16.0
    s = a % 16
    if s + span > 16:
        s = 16 - span
    return s, s + span


def auto_uv(face, f, t, mirror_north):
    x1, y1, z1 = f
    x2, y2, z2 = t
    u, v = {"north": ((16 - x2, 16 - x1), (16 - y2, 16 - y1)), "south": ((x1, x2), (16 - y2, 16 - y1)),
            "west": ((z1, z2), (16 - y2, 16 - y1)), "east": ((16 - z2, 16 - z1), (16 - y2, 16 - y1)),
            "up": ((x1, x2), (z1, z2)), "down": ((x1, x2), (16 - z2, 16 - z1))}[face]
    if face == "north" and mirror_north:
        a, b = wrap(x1, x2)
        vv = wrap(*v)
        return [b, vv[0], a, vv[1]]
    uu, vv = wrap(*u), wrap(*v)
    return [uu[0], vv[0], uu[1], vv[1]]


class Model:
    def __init__(self, name, tex, particle):
        self.name, self.tex, self.particle, self.els, self.display = name, tex, particle, [], None

    def box(self, name, f, t, tex, uv=None, faces=None, rot=None, light=False, only=None, skip=(), mirror_north=False):
        flat = [i for i in range(3) if f[i] == t[i]]
        names = list(FACES)
        if flat:
            names = {0: ["east", "west"], 1: ["up", "down"], 2: ["north", "south"]}[flat[0]]
        names = [n for n in names if (not only or n in only) and n not in skip]
        fd = {}
        for fc in names:
            key, fuv = tex, None
            ov = (faces or {}).get(fc)
            if isinstance(ov, tuple):
                key, fuv = ov
            elif isinstance(ov, str):
                key = ov
            if fuv is None:
                fuv = [0, 0, 16, 16] if uv == "full" else list(uv) if uv else auto_uv(fc, f, t, mirror_north)
            if fuv == "full":
                fuv = [0, 0, 16, 16]
            face = {"uv": [num(v) for v in fuv], "texture": "#" + key}
            if light:
                face["shade"] = False
            if fc == "down" and f[1] == 0 and not rot:
                face["cullface"] = "down"
            fd[fc] = face
        el = {"name": name, "from": [num(v) for v in f], "to": [num(v) for v in t]}
        if rot:
            axis, ang, origin = rot
            origin = origin or [(f[i] + t[i]) / 2 for i in range(3)]
            el["rotation"] = {"angle": ang, "axis": axis, "origin": [num(v) for v in origin]}
        if light:
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
        el["faces"] = fd
        self.els.append(el)

    def json(self):
        used = sorted({fc["texture"][1:] for el in self.els for fc in el["faces"].values()})
        tex = {k: NS + self.tex[k] for k in used}
        tex["particle"] = NS + self.particle
        out = {"credit": "BSP-Core Market Stall, made by tools/gen_stall_design.py", "parent": "minecraft:block/block",
               "render_type": "minecraft:cutout", "ambientocclusion": False, "textures": tex, "elements": self.els}
        if self.display:
            out["display"] = self.display
        return out


def tx(skin, **parts):
    return {k: f"stall_{skin}_{v}" for k, v in parts.items()}


def fantasy(broken):
    m = Model("market_stall_fantasy" + ("_broken" if broken else ""), tx(
        "fantasy", timber="timber", planks="planks_worn" if broken else "planks", cloth="cloth_torn" if broken else "cloth",
        valance="valance", banner="banner_torn" if broken else "banner", gold="gold", goods="goods",
        lantern="lantern_off" if broken else "lantern", lamp="lamp", curtain="curtain", web="web", note="note"),
        "stall_fantasy_planks")
    b = m.box
    b("sill", [0.5, 0, 1.5], [15.5, 1, 15.5], "timber")
    b("counter_body", [1, 1, 2.5], [15, 11, 15], "planks")
    b("counter_frame_a", [0.5, 1, 2], [2, 11, 15.5], "timber")
    b("counter_frame_b", [14, 1, 2], [15.5, 11, 15.5], "timber")
    b("counter_rail_low", [2, 1, 2], [14, 2, 2.5], "timber")
    b("counter_rail_high", [2, 9.5, 2], [14, 10.5, 2.5], "timber", rot=("z", -22.5, [2, 10, 2.25]) if broken else None)
    b("counter_top", [0, 11, 0.5], [16, 12.5, 16], "planks")
    brot = ("z", -22.5, [4, 10.5, 0.6]) if broken else None
    b("banner_rod", [3.5, 10.25, 0.35], [12.5, 10.75, 0.85], "gold", rot=brot)
    b("banner", [4, 0.5, 0.6], [12, 10.5, 0.6], "banner", uv=[4, 0, 12, 10], only=("north",), rot=brot)
    b("post_front_a", [0, 12.5, 0.5], [2, 23, 2.5], "timber")
    b("post_front_b", [14, 12.5, 0.5], [16, 16.5 if broken else 23, 2.5], "timber")
    b("post_back_a", [0, 12.5, 14], [2, 29, 16], "timber")
    b("post_back_b", [14, 12.5, 14], [16, 29, 16], "timber")
    b("beam_back", [0, 27.5, 14], [16, 29, 16], "timber")
    b("beam_front", [0, 21.5, 0.5], [16, 23, 2.5], "timber", rot=("z", -22.5, [1, 22.25, 1.5]) if broken else None)
    b("brace_a", [0.5, 19.5, 1], [5.5, 20.5, 2], "timber", rot=("z", 45, [3, 20, 1.5]))
    b("lamp_stock", [0, 12.75, -0.5], [2, 15.75, 0.5], "gold", faces={"north": ("lamp", [0, 0, 16, 16])})
    b("lamp_open", [14, 12.75, -0.5], [16, 15.75, 0.5], "gold", faces={"north": ("lamp", [0, 0, 16, 16])})
    b("curtain", [2, 12.5, 15.25], [14, 27.5, 15.25], "curtain", only=("north",))
    if not broken:
        b("brace_b", [10.5, 19.5, 1], [15.5, 20.5, 2], "timber", rot=("z", -45, [13, 20, 1.5]))
        b("canopy", [0, 23, -3.5], [16, 23.75, 16.5], "cloth", rot=("x", -22.5, [8, 23, 1.5]), mirror_north=True)
        b("canopy_valance", [0, 17.75, -3.25], [16, 21.75, -3.25], "valance", uv=[16, 0, 0, 4], only=("north", "south"))
        b("lantern_chain", [3.25, 21, 5.75], [3.75, 25, 6.25], "gold")
        b("lantern_cap", [2.5, 20.5, 5], [4.5, 21, 7], "gold")
        b("lantern", [2.25, 18, 4.75], [4.75, 20.5, 7.25], "lantern", uv="full", light=True)
        b("lantern_base", [2.5, 17.75, 5], [4.5, 18, 7], "gold")
        b("crate", [1.5, 12.5, 10], [6, 16.5, 14.5], "goods", uv=[0, 0, 8, 8], faces={"up": ("goods", [8, 8, 16, 16])})
        b("sack", [10.5, 12.5, 10.5], [14, 16, 14], "goods", uv=[8, 0, 16, 8])
        b("sack_tie", [11.5, 16, 11.5], [13, 17, 13], "goods", uv=[10, 0, 14, 2])
        b("bottle_green", [7, 12.5, 12], [8.25, 15.5, 13.25], "goods", uv=[0, 9, 4, 16])
        b("bottle_green_neck", [7.25, 15.5, 12.25], [8, 16.5, 13], "goods", uv=[0, 8, 4, 9])
        b("bottle_red", [8.75, 12.5, 12.5], [10, 14.75, 13.75], "goods", uv=[4, 9, 8, 16])
        b("bottle_red_neck", [9, 14.75, 12.75], [9.75, 15.5, 13.5], "goods", uv=[4, 8, 8, 9])
    else:
        b("post_splinter", [14.5, 16.5, 1], [15.5, 19, 2], "timber", rot=("z", 22.5, [15, 16.5, 1.5]))
        b("canopy_collapsed", [0, 28.25, -1.5], [16, 29, 15.5], "cloth", rot=("x", -45, [8, 28.25, 15.5]), mirror_north=True)
        b("rag_a", [3, 15, 1.4], [4.5, 21.5, 1.4], "cloth", uv=[0, 2, 1.5, 8.5], only=("north", "south"), rot=("z", 22.5, [3.75, 21.5, 1.4]))
        b("rag_b", [9, 14.5, 0.4], [10.5, 20, 0.4], "cloth", uv=[4, 6, 5.5, 11.5], only=("north", "south"))
        b("crate_toppled", [6, 12.5, 9], [10.5, 16.5, 13.5], "goods", uv=[0, 0, 8, 8], rot=("y", 22.5, None))
        b("bottle_lying", [3, 12.5, 7], [6.5, 13.75, 8.25], "goods", uv=[0, 9, 4, 16], rot=("y", 22.5, None))
        b("lantern_fallen", [11.5, 12.5, 5], [14, 15, 7.5], "lantern", uv="full", rot=("z", 22.5, [12.75, 12.5, 6.25]))
        b("web_front", [2, 16.5, 2.6], [7, 21.5, 2.6], "web", uv=[16, 0, 0, 16], only=("north", "south"))
        b("web_back", [9, 22, 13.9], [14, 27, 13.9], "web", uv=[0, 0, 16, 16], only=("north", "south"))
        b("note", [0, 16.5, 0.35], [5, 21.5, 0.35], "note", uv="full", only=("north",), rot=("z", 22.5, None))
    return m


def scifi(broken):
    m = Model("market_stall_scifi" + ("_broken" if broken else ""), tx(
        "scifi", panel="panel_dirty" if broken else "panel", hull="hull", strip="strip_off" if broken else "strip",
        holo="holo_broken" if broken else "holo", pad="pad_off" if broken else "pad", detail="detail", lamp="lamp",
        error="error", circuit="circuit", cable="cable", tape="tape"), "stall_scifi_panel")
    b, L = m.box, not broken
    b("plinth", [0.5, 0, 0.5], [15.5, 1, 15.5], "hull")
    b("base_glow", [1, 1, 1], [15, 1.5, 15], "strip", light=L)
    b("cabinet", [1, 1.5, 1.5], [15, 12, 15], "panel")
    b("dispense_slot", [3, 2.75, 1.25], [13, 4.75, 1.5], "hull", faces={"north": ("detail", [8, 0, 16, 4])})
    b("keypad", [10, 6.5, 1.1], [13, 9.5, 1.5], "hull", faces={"north": ("detail", [0, 0, 8, 8])})
    b("cabinet_lip", [0.5, 12, 1], [15.5, 12.75, 15.5], "hull")
    b("pad", [5, 12.75, 5], [11, 13.25, 11], "hull", faces={"up": ("pad", [0, 0, 16, 16])}, light=L)
    b("alcove_wall_a", [1, 12.75, 2], [3, 22, 15], "panel")
    b("alcove_wall_b", [13, 12.75, 2], [15, 22, 15], "panel")
    b("alcove_back", [3, 12.75, 13], [13, 22, 15], "hull")
    b("alcove_strip_a", [2.5, 12.75, 1.75], [3, 22, 2], "strip", light=L)
    b("alcove_strip_b", [13, 12.75, 1.75], [13.5, 22, 2], "strip", light=L)
    b("alcove_ceiling", [3, 21.25, 2], [13, 22, 13], "hull")
    b("downlight", [6, 21, 6], [10, 21.25, 10], "strip", light=L)
    b("upper", [1, 22, 2.5], [15, 29, 15], "panel")
    b("holo_emitter", [2.5, 22, 1], [13.5, 22.75, 2.5], "hull")
    b("holo_emitter_glow", [3, 22.75, 1.25], [13, 23, 2.25], "strip", light=L)
    b("fin_a", [0, 1.5, 3], [1, 28, 14], "hull")
    b("fin_b", [15, 1.5, 3], [16, 28, 14], "hull")
    b("fin_strip_a", [0.25, 2, 2.5], [0.75, 28, 3], "strip", light=L)
    b("fin_strip_b", [15.25, 2, 2.5], [15.75, 28, 3], "strip", light=L)
    b("hood", [0, 29, 0], [16, 30.5, 16], "hull")
    b("hood_trim", [0, 29.5, -0.25], [16, 30, 0], "panel")
    b("hood_glow", [1, 28.75, 0.5], [15, 29, 2.5], "strip", light=L)
    b("vent", [3, 30.5, 7], [8, 31.25, 12], "hull", faces={"up": ("detail", [0, 8, 8, 16])})
    arot = ("x", 45, [13, 30.5, 12]) if broken else None
    b("antenna", [12.75, 30.5, 11.75], [13.25, 32, 12.25], "hull", rot=arot)
    b("antenna_tip", [12.6, 31.4, 11.6], [13.4, 32, 12.4], "hull" if broken else "error", light=L, rot=arot)
    if not broken:
        b("lamp_bar", [2.5, 8, 1.25], [8.5, 11, 1.5], "hull")
        b("lamp_stock", [3, 8.5, 1], [5, 10.5, 1.25], "lamp", uv="full")
        b("lamp_open", [6, 8.5, 1], [8, 10.5, 1.25], "lamp", uv="full")
        b("holo", [2.5, 23, 1.75], [13.5, 29, 1.75], "holo", uv=[0, 0, 16, 9], only=("north",),
          rot=("x", -22.5, [8, 23, 1.75]), light=True)
    else:
        b("holo_hanging", [2.5, 22.75, 0.75], [13.5, 28.75, 0.75], "holo", uv=[0, 0, 16, 9], only=("north",),
          rot=("z", 22.5, [13.5, 28.75, 0.75]))
        b("exposed_board", [1.75, 2, 1.45], [7.75, 11.25, 1.45], "circuit", only=("north",))
        b("door_open", [1.5, 2, 0.75], [7.5, 11.25, 1.25], "panel", rot=("y", 45, [1.5, 6.5, 1]))
        b("error_light", [11, 10, 1], [12, 10.75, 1.25], "error", uv="full", light=True)
        b("cable_red", [3, 6, -2.5], [3.5, 6.5, 1.5], "cable", uv=[0, 0, 4, 4], rot=("x", -22.5, [3.25, 6.25, 1.5]))
        b("cable_yellow", [4.5, 4.5, -2], [5, 5, 1.5], "cable", uv=[4, 0, 8, 4], rot=("x", -45, [4.75, 4.75, 1.5]))
        b("cable_blue", [5.5, 1, -3.5], [6, 1.5, 1.5], "cable", uv=[8, 0, 12, 4], rot=("x", -22.5, [5.75, 1.25, 1.5]))
        b("tape_a", [0, 16, 0.6], [16, 18, 0.6], "tape", uv=[0, 0, 16, 4], only=("north",), rot=("z", 22.5, [8, 17, 0.6]))
        b("tape_b", [0, 16, 0.5], [16, 18, 0.5], "tape", uv=[0, 4, 16, 8], only=("north",), rot=("z", -22.5, [8, 17, 0.5]))
    return m


def steampunk(broken):
    m = Model("market_stall_steampunk" + ("_broken" if broken else ""), tx(
        "steampunk", wood="wood", iron="iron", brass="brass", copper="copper_patina" if broken else "copper",
        pipe="pipe_patina" if broken else "pipe", dial="dial_broken" if broken else "dial",
        fire="firebox_cold" if broken else "firebox", glass="glass_broken" if broken else "glass", gear="gear",
        lamp="lamp", tag="tag", steam="steam", puddle="puddle"), "stall_steampunk_copper")
    b = m.box
    b("floor_plate", [0, 0, 0.5], [16, 0.75, 16], "iron")
    b("counter_body", [0.5, 0.75, 2.5], [10, 11, 15.5], "wood")
    b("counter_front", [0.5, 1, 2], [10, 10.75, 2.5], "iron")
    b("trim_a", [0.25, 0.75, 1.75], [1.25, 11, 2.75], "brass")
    b("trim_b", [9.25, 0.75, 1.75], [10.25, 11, 2.75], "brass")
    b("counter_top", [0, 11, 1.5], [10.5, 12.25, 16], "wood")
    b("counter_edge", [0, 11.25, 1.25], [10.5, 12, 1.5], "brass")
    b("gear_small", [1.25, 5, 1.85], [3.75, 7.5, 1.85], "gear", uv="full", only=("north",))
    b("lamp_stock_bezel", [1.25, 8, 1.5], [3.75, 10.5, 2], "brass")
    b("lamp_stock", [1.5, 8.25, 1.25], [3.5, 10.25, 1.5], "lamp", uv="full")
    b("lamp_open_bezel", [5.75, 8, 1.5], [8.25, 10.5, 2], "brass")
    b("lamp_open", [6, 8.25, 1.25], [8, 10.25, 1.5], "lamp", uv="full")
    b("boiler_a", [10.25, 0.75, 4.5], [15.25, 21, 10.5], "copper")
    b("boiler_b", [9.75, 0.75, 5], [15.75, 21, 10], "copper")
    for i, y in enumerate((7.5, 17.5)):
        b(f"boiler_band_{i}a", [10, y, 4.25], [15.5, y + 1, 10.75], "brass")
        b(f"boiler_band_{i}b", [9.5, y, 4.75], [16, y + 1, 10.25], "brass")
    b("boiler_dome", [10.75, 21, 5.5], [14.75, 22.5, 9.5], "copper")
    b("boiler_dome_cap", [11.75, 22.5, 6.5], [13.75, 23.25, 8.5], "brass")
    b("chimney", [12, 23.25, 6.75], [13.5, 30, 8.25], "iron")
    crot = ("z", 22.5, [12.75, 30, 7.5]) if broken else None
    b("chimney_cap", [11.25, 30, 6], [14.25, 31, 9], "iron", rot=crot)
    b("chimney_top", [11.75, 31, 6.5], [13.75, 31.5, 8.5], "iron", rot=crot)
    b("whistle", [14, 21, 8], [14.75, 24, 8.75], "brass")
    b("firebox", [10.75, 1.75, 4.25], [14.75, 6.25, 4.5], "iron", faces={"north": ("fire", [0, 0, 16, 16])}, light=not broken)
    b("dial_back_a", [11.25, 11, 4], [14.25, 15, 4.5], "brass")
    b("dial_back_b", [10.75, 11.5, 4], [14.75, 14.5, 4.5], "brass")
    b("dial", [10.75, 11, 3.95], [14.75, 15, 3.95], "dial", uv="full", only=("north",))
    b("back_panel", [0.5, 12.25, 14], [10, 25, 15.5], "copper")
    b("back_cornice", [0.25, 25, 13.75], [10.25, 26, 15.75], "brass")
    b("nameplate", [3, 21.5, 13.75], [8, 23, 14], "brass")
    b("pipe_v", [0.75, 12.25, 10], [2.25, 20.5, 11.5], "pipe")
    b("pipe_elbow", [0.5, 18.75, 9.75], [2.5, 20.75, 11.75], "brass")
    b("valve_wheel", [0.25, 14, 9.9], [2.75, 16.5, 9.9], "gear", uv="full", only=("north",))
    b("jar_base", [1.75, 12.25, 2.75], [9.25, 13, 9.75], "brass")
    b("jar_glass", [2.25, 13, 3.25], [8.75, 19.5, 9.25], "glass", uv="full", skip=("down",))
    if not broken:
        b("gear_big", [3.5, 2, 1.9], [8.5, 7, 1.9], "gear", uv="full", only=("north",), rot=("z", 22.5, [6, 4.5, 1.9]))
        b("pipe_h", [2, 19, 10], [10, 20.5, 11.5], "pipe")
        b("pipe_flange", [6, 18.75, 9.75], [6.75, 20.75, 11.75], "brass")
        b("jar_knob", [5, 19.5, 5.75], [6, 20.25, 6.75], "brass")
    else:
        b("gear_big_fallen", [3, 0.75, 0.5], [8, 5.75, 0.5], "gear", uv="full", only=("north",), rot=("x", 22.5, [5.5, 0.75, 0.5]))
        b("pipe_stub", [6.5, 19, 10], [10, 20.5, 11.5], "pipe")
        b("pipe_broken", [2, 19, 10], [5.5, 20.5, 11.5], "pipe", rot=("z", -45, [2.25, 20.5, 10.75]))
        b("steam_a", [4.5, 19, 10.75], [9, 24.5, 10.75], "steam", uv="full", only=("north", "south"), rot=("y", 45, [6.75, 21.75, 10.75]))
        b("steam_b", [4.5, 19, 10.75], [9, 24.5, 10.75], "steam", uv="full", only=("north", "south"), rot=("y", -45, [6.75, 21.75, 10.75]))
        b("puddle", [2.5, 12.3, 9.75], [9.75, 12.3, 13.75], "puddle", uv="full", only=("up",))
        b("tag_string", [12.65, 9.75, 3.75], [12.85, 11, 3.85], "iron")
        b("tag", [10.5, 5.75, 3.7], [15, 9.75, 3.7], "tag", uv="full", only=("north",), rot=("z", 22.5, [12.75, 9.75, 3.7]))
    return m


def vanilla(broken):
    m = Model("market_stall_vanilla" + ("_broken" if broken else ""), tx(
        "vanilla", planks="planks_old" if broken else "planks", cobble="cobble_mossy" if broken else "cobble",
        awning="awning_torn" if broken else "awning", trapdoor="trapdoor", sign="sign_closed" if broken else "sign",
        lantern="lantern_off" if broken else "lantern", misc="misc", bell="bell", lamp="lamp", web="web"),
        "stall_vanilla_planks")
    b = m.box
    b("cobble_base", [0, 0, 1], [16, 3, 16], "cobble")
    b("counter_body", [0.5, 3, 2.25], [15.5, 11, 15.5], "planks")
    b("slab_top", [0, 11, 1], [16, 13, 16], "planks")
    b("post_front_a", [0, 13, 1], [2, 24, 3], "planks")
    b("post_front_b", [14, 13, 1], [16, 15 if broken else 24, 3], "planks")
    b("post_back_a", [0, 13, 14], [2, 27.5, 16], "planks")
    b("post_back_b", [14, 13, 14], [16, 27.5, 16], "planks")
    b("rail_back_low", [2, 18, 14.5], [14, 19.5, 15.5], "planks")
    b("rail_back_high", [2, 22, 14.5], [14, 23.5, 15.5], "planks")
    b("rail_side_a", [0.5, 22, 3], [1.5, 23.5, 14], "planks")
    b("rail_side_b", [14.5, 22, 3], [15.5, 23.5, 14], "planks")
    b("awning_front", [0, 24, -1.5], [16, 26, 3], "awning", mirror_north=True, rot=("z", -22.5, [0, 25, 0.75]) if broken else None)
    b("awning_middle", [0, 26, 3], [16, 28, 9.5], "awning", mirror_north=True)
    b("awning_back", [0, 27.5, 9.5], [16, 29.5, 16], "awning", mirror_north=True)
    b("lamp_stock", [0, 14, 0.5], [2, 16, 1], "lamp", uv="full")
    b("lamp_open", [14, 14, 0.5], [16, 16, 1], "lamp", uv="full")
    b("pot", [1.75, 13, 11.5], [4.25, 15.5, 14], "misc", uv=[0, 0, 8, 8], faces={"up": ("misc", [8, 0, 16, 4])})
    flower = [8, 8, 16, 16] if broken else [0, 8, 8, 16]
    b("flower_a", [2, 15.5, 12.75], [4, 19.5, 12.75], "misc", uv=flower, only=("north", "south"), rot=("y", 45, [3, 15.5, 12.75]))
    b("flower_b", [2, 15.5, 12.75], [4, 19.5, 12.75], "misc", uv=flower, only=("north", "south"), rot=("y", -45, [3, 15.5, 12.75]))
    lrot = ("z", 45, [12.75, 13, 12.25]) if broken else None
    b("lantern", [11.75, 13, 11], [13.75, 15.5, 13.5], "lantern", uv="full", light=not broken, rot=lrot)
    b("lantern_cap", [12.25, 15.5, 11.5], [13.25, 16, 13], "misc", uv=[8, 4, 16, 8], rot=lrot)
    trap = {"north": ("trapdoor", [0, 0, 7, 7]), "south": ("trapdoor", [0, 0, 7, 7])}
    if not broken:
        b("trapdoor_a", [8, 3.5, 1.75], [15, 10.5, 2.25], "planks", faces=trap)
        b("trapdoor_b", [1, 3.5, 1.75], [8, 10.5, 2.25], "planks", faces=trap)
        b("sign", [4, 20, -1], [12, 24, -0.5], "planks", faces={"north": ("sign", [0, 0, 16, 8]), "south": ("sign", [0, 0, 16, 8])})
        b("bell_lip", [11.25, 13, 3.25], [13.75, 13.5, 5.75], "bell")
        b("bell", [11.5, 13.5, 3.5], [13.5, 15.25, 5.5], "bell")
        b("bell_top", [12, 15.25, 4], [13, 16, 5], "bell")
        m.display = {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.2, 0.2, 0.2]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, -2.5, 0], "scale": [0.4, 0.4, 0.4]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 1.5, 0], "scale": [0.25, 0.25, 0.25]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -1, 0], "scale": [0.25, 0.25, 0.25]}}
    else:
        b("trapdoor_crooked", [1, 3.5, 1.75], [8, 10.5, 2.25], "planks", faces=trap, rot=("z", 22.5, None))
        b("trapdoor_gone", [8, 3.5, 2.2], [15, 10.5, 2.2], "misc", uv=[8, 4, 15, 8], only=("north",))
        b("board_a", [0.5, 6, 1.25], [15.5, 7.5, 1.75], "planks", rot=("z", 22.5, [8, 6.75, 1.5]))
        b("board_b", [0.5, 6, 0.75], [15.5, 7.5, 1.25], "planks", rot=("z", -22.5, [8, 6.75, 1]))
        b("sign_chain", [4.25, 20.5, -0.9], [4.75, 22.5, -0.6], "misc", uv=[8, 4, 9, 8])
        b("sign_closed", [4, 16.5, -1], [12, 20.5, -0.5], "planks", rot=("z", -22.5, [4, 20.5, -0.75]),
          faces={"north": ("sign", [0, 0, 16, 8]), "south": ("sign", [0, 0, 16, 8])})
        b("web_front", [2, 19, 3.2], [7, 24, 3.2], "web", uv=[16, 0, 0, 16], only=("north", "south"))
        b("web_back", [9, 21, 13.9], [14, 26, 13.9], "web", uv="full", only=("north", "south"))
    return m


# ----------------------------------------------------------------------------- second set: nether, yatai, nautical, arcane
def bricks(t, base, mortar, seed, bh=4, bw=8, box=None):
    def fn(x, y):
        row = y // bh
        off = (bw // 2) * (row % 2)
        if y % bh == bh - 1 or (x + off) % bw == bw - 1:
            return jit(mortar, x, y, seed, 0.05)
        c = jit(sh(base, 1 + (hsh((x + off) // bw, row, seed) - 0.5) * 0.2), x, y, seed + 1, 0.06)
        return sh(c, 1.08) if y % bh == 0 else c
    t.fill(fn, box=box)


def crack_over(t, seed, col, n=3):
    for i in range(n):
        x, y = int(hsh(i, 1, seed) * 12) + 2, int(hsh(i, 2, seed) * 12) + 2
        t.path([(x, y), (x + 1, y + 2), (x + 3, y + 3), (x + 3, y + 5), (x + 5, y + 6)], col)


def nether_textures():
    bricks(T("stall_nether_blackstone"), hexc("2c262d"), hexc("161216"), 101)
    t = T("stall_nether_blackstone_cracked")
    bricks(t, hexc("2c262d"), hexc("161216"), 101)
    crack_over(t, 102, hexc("0a080a"), 4)
    t.fill(lambda x, y: hexc("6b5a2a") if hsh(x, y, 103) < 0.04 else None)
    T("stall_nether_basalt").fill(lambda x, y: jit(sh(hexc("4a4a52"), 0.85 + 0.25 * hsh(x, 0, 104)), x, y, 105, 0.06)
                                  if x % 5 else hexc("2e2e34"))
    metal(T("stall_nether_gold"), hexc("e0b03a"), hexc("fff09a"), hexc("8a5a10"), 106)
    T("stall_nether_wart").fill(lambda x, y: jit(hexc("8a1a1a") if hsh(x // 2, y // 2, 107) > 0.3 else hexc("b02a22"), x, y, 108, 0.1))
    for lit in (True, False):
        t = T("stall_nether_magma" if lit else "stall_nether_magma_cold", 16, 3 if lit else 1, 4)
        pts = [(hsh(i, 0, 109) * 16, hsh(i, 1, 109) * 16) for i in range(7)]
        for f in range(t.frames):
            def mg(x, y, f=f):
                d = sorted(math.hypot(min(abs(x + .5 - px), 16 - abs(x + .5 - px)), min(abs(y + .5 - py), 16 - abs(y + .5 - py))) for px, py in pts)
                if d[1] - d[0] < 1.1:
                    if not lit:
                        return hexc("4a2a22")
                    k = 0.8 + 0.25 * hsh(x, y + f * 31, 110)
                    return sh(hexc("ffb02a") if d[1] - d[0] < 0.5 else hexc("e0561e"), k)
                return jit(hexc("3a1a14") if lit else hexc("2a1c1a"), x, y, 111, 0.12)
            t.fill(mg, f)
    for lit in (True, False):
        t = T("stall_nether_soul_lantern" if lit else "stall_nether_soul_lantern_off")
        lantern_tex(t, False, hexc("2a2630"), 112)
        if lit:
            t.fill(lambda x, y: (hexc("e8ffff") if math.hypot(x - 7.5, y - 8) < 2.4 else hexc("6fe8f0") if math.hypot(x - 7.5, y - 8) < 4.4
                                 else hexc("2aa8c8")) if 2 <= x <= 13 and 2 <= y <= 13 and not (x in (7, 8) and y < 4) else None)
    t = T("stall_nether_chain")
    for y in range(16):
        link = (y // 4) % 2
        for x in range(6, 10):
            ring = (x in (6, 9) or y % 4 in (0, 3)) if link == 0 else (x in (7, 8))
            if ring:
                t.set(x, y, hexc("5a5e6a") if x < 8 else hexc("3a3d46"))
    lamp_tex(T("stall_nether_lamp"), hexc("e0b03a"), hexc("1a1214"), (3, 13), (3, 13))
    note_tex(T("stall_nether_note", 32), [("CLOSED", 9, 1, 2), ("~", 22, 1, 1), ("~", 25, 1, 1)], hexc("2a1a10"), hexc("b89a72"), 113)


def yatai_textures():
    planks_h(T("stall_yatai_wood"), hexc("9a6a42"), hexc("5a3a20"), 121)
    t = T("stall_yatai_wood_old")
    planks_h(t, hexc("9a6a42"), hexc("5a3a20"), 121)
    weather(t, 122, hexc("6e665c"), 0.35)
    for broken in (False, True):
        t = T("stall_yatai_lacquer_worn" if broken else "stall_yatai_lacquer")
        t.fill(lambda x, y: hexc("e0605a") if (x + y) % 11 == 0 else jit(hexc("a8282a"), x, y, 123, 0.04))
        if broken:
            t.fill(lambda x, y: jit(hexc("6a4a3a"), x, y, 124, 0.1) if hsh(x // 2, y // 2, 125) < 0.3 else None)
    for broken in (False, True):
        t = T("stall_yatai_roof_broken" if broken else "stall_yatai_roof")
        def tile(x, y):
            c = hexc("3c4048") if (x // 4) % 2 == 0 else hexc("4a4f58")
            if y % 4 == 3:
                c = hexc("23262c")
            elif x % 4 == 1:
                c = sh(c, 1.18)
            return jit(c, x, y, 126, 0.05)
        t.fill(tile)
        if broken:
            for (x0, y0) in ((4, 4), (8, 8), (0, 12), (12, 0)):
                t.rect(x0, y0, x0 + 4, y0 + 4, CLEAR)
    for broken in (False, True):
        t = T("stall_yatai_noren_torn" if broken else "stall_yatai_noren")
        def nr(x, y):
            if x in (5, 10):
                return None
            c = jit(hexc("23305a"), x, y, 127, 0.05)
            if y == 0:
                return hexc("161e3a")
            r = math.hypot(x - 7.5, y - 7.5)
            if 3.2 < r < 4.6 or (r < 1.6):
                c = hexc("e8e4d8")
            return c
        t.fill(nr)
        if broken:
            fade(t, hexc("4a4f5a"), 0.25, 128)
            holes(t, 129, 4, 1.6)
            t.rect(11, 6, 16, 16, CLEAR)
    for lit in (True, False):
        t = T("stall_yatai_paper_lantern" if lit else "stall_yatai_paper_lantern_torn")
        def pl(x, y):
            if y < 2 or y > 13:
                return hexc("1a1a1a")
            if y % 3 == 2:
                return hexc("7a1a14") if lit else hexc("5a2a22")
            r = math.hypot(x - 7.5, y - 7.5)
            if lit:
                return hexc("ffd08a") if r < 3 else hexc("f05a3a") if r < 6 else hexc("d0302a")
            return jit(hexc("8a4a40"), x, y, 130, 0.1)
        t.fill(pl)
        if not lit:
            holes(t, 131, 3, 1.4)
    t = T("stall_yatai_wheel")
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r, a = math.hypot(dx, dy), math.atan2(dy, dx)
            if 6.4 <= r < 7.9:
                t.set(x, y, hexc("3a2a1a") if r > 7.2 else hexc("6a4a2a"))
            elif r < 1.6:
                t.set(x, y, hexc("2a2a2e"))
            elif r < 6.4 and abs(math.sin(a * 3)) < 0.22:
                t.set(x, y, hexc("7a5532"))
    T("stall_yatai_bamboo").fill(lambda x, y: hexc("6a7a3a") if y % 8 == 0 else jit(hexc("a8a058") if x % 4 else hexc("c8c070"), x, y, 132, 0.05))
    t = T("stall_yatai_misc")
    t.fill(lambda x, y: hexc("4a4e56") if y % 7 else hexc("6a6e78"), box=(0, 0, 8, 8))          # iron pot
    t.fill(lambda x, y: hexc("2a5aa8") if y in (9, 10) else hexc("f0ece0"), box=(8, 8, 16, 16))  # bowls
    t.fill(lambda x, y: jit(hexc("c09a6a"), x, y, 133, 0.06), box=(8, 0, 16, 8))               # ladle wood
    t.fill(lambda x, y: hexc("1a1a1a") if (x + y) % 3 else hexc("3a3a3a"), box=(0, 8, 8, 16))   # lid / cap black
    lamp_tex(T("stall_yatai_lamp"), hexc("1a1a1a"), hexc("2a1a14"), (2, 14), (2, 14))
    note_tex(T("stall_yatai_note", 32), [("CLOSED", 9, 1, 2), ("~", 22, 1, 1)], hexc("1a1a1a"), hexc("f0ead8"), 134)


def nautical_textures():
    planks_h(T("stall_nautical_planks"), hexc("6a4e34"), hexc("2e2014"), 141)
    t = T("stall_nautical_planks_rot")
    planks_h(t, hexc("6a4e34"), hexc("2e2014"), 141)
    t.fill(lambda x, y: jit(mix(t.get(x, y), hexc("3a6a4a"), 0.35), x, y, 142, 0.08) if hsh(x, y, 143) < 0.22 else None)
    for old in (False, True):
        t = T("stall_nautical_barrel_old" if old else "stall_nautical_barrel")
        def br(x, y):
            if y in (2, 3, 12, 13):
                return hexc("4a4e56") if y in (2, 12) else hexc("33363c")
            c = jit(sh(hexc("8a6038"), 0.9 + 0.2 * hsh(x // 3, 0, 144)), x, y, 145, 0.05)
            if x % 3 == 2:
                c = sh(c, 0.75)
            if old and hsh(x, y // 2, 146) < 0.25:
                c = hexc("d8d0c0") if hsh(x, y, 147) < 0.5 else hexc("4a7a5a")
            return c
        t.fill(br)
    T("stall_nautical_barrel_top").fill(lambda x, y: hexc("4a4e56") if math.hypot(x - 7.5, y - 7.5) > 6.5 else
                                        jit(hexc("9a7048") if (x // 3) % 2 else hexc("8a6038"), x, y, 148, 0.05))
    T("stall_nautical_rope").fill(lambda x, y: hexc("a88a5a") if (x + y) % 4 < 2 else hexc("c8aa72"))
    for torn in (False, True):
        t = T("stall_nautical_sail_torn" if torn else "stall_nautical_sail")
        t.fill(lambda x, y: hexc("b8b0a0") if y % 5 == 0 else jit(hexc("e6dfcc"), x, y, 149, 0.04))
        t.rect(9, 3, 14, 7, hexc("c8b890"))
        if torn:
            fade(t, hexc("8a8478"), 0.2, 150)
            holes(t, 151, 5, 2)
    for torn in (False, True):
        t = T("stall_nautical_net_torn" if torn else "stall_nautical_net")
        t.fill(lambda x, y: hexc("8a7a5a") if (x % 4 == 0 or y % 4 == 0) else None)
        if torn:
            holes(t, 152, 4, 2.2)
    metal(T("stall_nautical_iron"), hexc("3a3d44"), hexc("5a5e68"), hexc("1e2024"), 153)
    for lit in (True, False):
        lantern_tex(T("stall_nautical_lantern" if lit else "stall_nautical_lantern_off"), lit, hexc("b8913a"), 154)
    t = T("stall_nautical_crate")
    t.fill(lambda x, y: hexc("a07a48") if x in (0, 7) or y in (0, 7) else hexc("3d2a18") if y in (3, 5) else jit(hexc("8a6638"), x, y, 155, 0.06),
           box=(0, 0, 8, 8))
    def fish(x, y):
        for (fx, fy) in ((10, 10), (13, 13), (11, 14)):
            if abs(x - fx) + abs(y - fy) * 2 < 3:
                return hexc("d8e4ea") if (x + y) % 2 else hexc("8aa0b0")
        return hexc("cfe8f0") if (x + y) % 3 else hexc("e8f6fa")
    t.fill(fish, box=(8, 8, 16, 16))
    t.fill(lambda x, y: jit(hexc("1e1610"), x, y, 156, 0.1), box=(0, 8, 8, 16))
    t.fill(lambda x, y: jit(hexc("2a5a3a"), x, y, 157, 0.15) if x % 2 == 0 else None, box=(8, 0, 16, 8))   # seaweed
    lamp_tex(T("stall_nautical_lamp"), hexc("b8913a"), hexc("161a1e"), (3, 13), (3, 13))
    t = T("stall_nautical_sign", 32)
    planks_h(t, hexc("6a4e34"), hexc("2e2014"), 158, rows=5)
    t.text_c("CLOSED", 3, hexc("e8e4d8"), 1, 2)


def arcane_textures():
    for cracked in (False, True):
        t = T("stall_arcane_stone_cracked" if cracked else "stall_arcane_stone")
        bricks(t, hexc("4a3f5e"), hexc("2a2238"), 161, bh=8, bw=8)
        if cracked:
            crack_over(t, 162, hexc("1a1424"), 4)
    metal(T("stall_arcane_trim"), hexc("d8c88a"), hexc("fff4c0"), hexc("8a7a4a"), 163)
    for lit in (True, False):
        t = T("stall_arcane_crystal" if lit else "stall_arcane_crystal_dark")
        def cr(x, y):
            facet = (x + y) // 4 % 3
            if lit:
                return [hexc("c49af8"), hexc("9a6af0"), hexc("e8d8ff")][facet] if (x * 7 + y) % 13 else hexc("7af0f0")
            return [hexc("4a4458"), hexc("3a3446"), hexc("5a5468")][facet]
        t.fill(cr)
        if not lit:
            crack_over(t, 164, hexc("1a1622"), 3)
    for lit in (True, False):
        t = T("stall_arcane_rune" if lit else "stall_arcane_rune_broken", 16, 4 if lit else 1, 5)
        for f in range(t.frames):
            for y in range(16):
                for x in range(16):
                    r, a = math.hypot(x + 0.5 - 8, y + 0.5 - 8), math.atan2(y + 0.5 - 8, x + 0.5 - 8)
                    seg = int((a + math.pi) / (2 * math.pi) * 12)
                    on = 6.6 < r < 7.6 or (4.6 < r < 6.4 and hsh(seg, int(r * 2), 165) < 0.5 and (seg + f) % 3 != 0)
                    if not lit and (seg in (2, 3, 7) or hsh(x, y, 166) < 0.3):
                        on = False
                    if on:
                        t.set(x, y, (hexc("b8fff8") if (seg + f) % 4 == 0 else hexc("5ae8e0")) if lit else hexc("3a4a58"), f)
    for closed in (False, True):
        t = T("stall_arcane_book_closed" if closed else "stall_arcane_book")
        if closed:
            t.fill(lambda x, y: hexc("d8c88a") if x in (0, 15) or y in (0, 15) else jit(mix(hexc("5a2a3a"), hexc("8a8478"), 0.35), x, y, 167, 0.1))
        else:
            def pg(x, y):
                if x in (7, 8):
                    return hexc("8a7a5a")
                if y in (0, 15) or x in (0, 15):
                    return hexc("5a2a3a")
                c = hexc("efe4c8")
                if y % 3 == 1 and 2 <= x <= 13 and hsh(x, y, 168) < 0.7:
                    c = hexc("3a2a5a")
                return c
            t.fill(pg)
    t = T("stall_arcane_cover")
    t.fill(lambda x, y: hexc("d8c88a") if x in (0, 15) else jit(hexc("5a2a3a"), x, y, 169, 0.06))
    for lit in (True, False):
        t = T("stall_arcane_candle" if lit else "stall_arcane_candle_out")
        t.fill(lambda x, y: jit(hexc("eee4c8"), x, y, 170, 0.04) if x < 8 else None)               # wax at left half
        if lit:
            t.fill(lambda x, y: hexc("fff4b0") if abs(x - 11.5) + (y - 9) * 0.4 < 1.4 else hexc("ffb43a")
                   if abs(x - 11.5) + (y - 7) * 0.35 < 2.6 and y > 2 else None, box=(8, 0, 16, 16))
        else:
            t.fill(lambda x, y: hexc("2a2a2a") if x == 11 and y > 11 else None, box=(8, 0, 16, 16))
    for torn in (False, True):
        t = T("stall_arcane_cloth_torn" if torn else "stall_arcane_cloth")
        def cl(x, y):
            if y > 11 and abs(x - 7.5) > (15 - y) * 2.2:
                return None
            if y in (0, 1) or (y > 11 and abs(x - 7.5) > (15 - y) * 2.2 - 1.2):
                return hexc("d8c88a")
            if (x * 5 + y * 3) % 17 == 0:
                return hexc("fff4c0")
            return jit(hexc("1e2a5a"), x, y, 171, 0.06)
        t.fill(cl)
        if torn:
            fade(t, hexc("4a4a58"), 0.25, 172)
            holes(t, 173, 4, 1.6)
    lamp_tex(T("stall_arcane_lamp"), hexc("d8c88a"), hexc("161226"), (3, 13), (3, 13))
    note_tex(T("stall_arcane_note", 32), [("CLOSED", 9, 1, 2), ("~", 22, 1, 1), ("~", 25, 1, 1)], hexc("2a1a3a"), hexc("e6dcc0"), 174)


def nether(broken):
    m = Model("market_stall_nether" + ("_broken" if broken else ""), tx(
        "nether", stone="blackstone_cracked" if broken else "blackstone", basalt="basalt", gold="gold", wart="wart",
        magma="magma_cold" if broken else "magma", lantern="soul_lantern_off" if broken else "soul_lantern", chain="chain",
        lamp="lamp", note="note"), "stall_nether_blackstone")
    b, L = m.box, not broken
    b("plinth", [0, 0, 1], [16, 2, 16], "stone")
    b("counter", [0.5, 2, 2.5], [15.5, 11, 15.5], "stone")
    b("magma_panel", [2.5, 3, 2.25], [13.5, 10, 2.5], "magma", light=L)
    b("counter_top", [0, 11, 2], [16, 12.5, 16], "stone")
    b("counter_gold_edge", [0, 11.25, 1.5], [16, 12.25, 2], "gold")
    b("barter_plate", [5, 12.5, 5.5], [11, 13, 11.5], "gold")
    b("pillar_front_a", [0, 12.5, 2], [2.5, 26, 4.5], "basalt")
    b("pillar_front_b", [13.5, 12.5, 2], [16, 26, 4.5], "basalt")
    b("pillar_back_a", [0, 12.5, 13.5], [2.5, 28, 16], "basalt")
    b("pillar_back_b", [13.5, 12.5, 13.5], [16, 28, 16], "basalt")
    b("back_wall", [2.5, 12.5, 14.5], [13.5, 28, 15.5], "basalt")
    b("lintel", [0, 26, 1.5], [16, 28.5, 5], "stone")
    b("roof", [0, 28, 5], [16, 29.5, 16], "stone")
    b("tooth_c", [7, 28, 2.25], [9, 30, 4.25], "stone", rot=("z", 45, None))
    b("tooth_a", [2.5, 27.5, 2.25], [4.5, 29.5, 4.25], "stone", rot=("z", 45, None))
    if not broken:
        b("tooth_b", [11.5, 27.5, 2.25], [13.5, 29.5, 4.25], "stone", rot=("z", 45, None))
    else:
        b("tooth_fallen", [9.5, 12.5, 8], [11.5, 14.5, 10], "stone", rot=("y", 22.5, None))
    b("wart_a", [9, 29.5, 8], [13, 31, 12], "wart")
    b("wart_b", [3, 29.5, 10], [6, 30.5, 13], "wart")
    b("lamp_stock", [0.25, 13, 1.5], [2.25, 15, 2], "lamp", uv="full")
    b("lamp_open", [13.75, 13, 1.5], [15.75, 15, 2], "lamp", uv="full")
    for side, x in (("a", 4), ("b", 12)):
        if broken and side == "a":
            b("chain_a_snapped", [x - 0.75, 23, 3.25], [x + 0.75, 26, 3.25], "chain", uv=[4, 0, 12, 8], only=("north", "south"))
            b("lantern_a_fallen", [x - 1.25, 12.5, 6], [x + 1.25, 15, 8.5], "lantern", uv="full", rot=("z", 45, [x, 12.5, 7.25]))
            continue
        for k, ang in (("1", 45), ("2", -45)):
            b(f"chain_{side}{k}", [x - 0.75, 21, 3.25], [x + 0.75, 26, 3.25], "chain", uv=[4, 0, 12, 10], only=("north", "south"),
              rot=("y", ang, [x, 21, 3.25]))
        b(f"lantern_{side}", [x - 1.25, 18, 2], [x + 1.25, 21, 4.5], "lantern", uv="full", light=L)
        b(f"lantern_{side}_cap", [x - 0.75, 21, 2.5], [x + 0.75, 21.5, 4], "gold")
    if broken:
        b("note", [5.5, 4, 2.1], [10.5, 9, 2.1], "note", uv="full", only=("north",), rot=("z", -22.5, None))
    return m


def yatai(broken):
    m = Model("market_stall_yatai" + ("_broken" if broken else ""), tx(
        "yatai", wood="wood_old" if broken else "wood", lacquer="lacquer_worn" if broken else "lacquer",
        roof="roof_broken" if broken else "roof", noren="noren_torn" if broken else "noren",
        lantern="paper_lantern_torn" if broken else "paper_lantern", wheel="wheel", bamboo="bamboo", misc="misc",
        lamp="lamp", note="note"), "stall_yatai_wood")
    b, L = m.box, not broken
    b("leg_a", [1.5, 0, 2.5], [3, 3, 4], "wood")
    b("leg_b", [13, 0, 2.5], [14.5, 3, 4], "wood")
    b("axle", [0.5, 3.75, 9.25], [15.5, 4.75, 10.25], "misc", uv=[0, 8, 8, 9])
    b("body", [1, 3, 2.5], [15, 11, 15], "wood")
    b("front_panel", [1.5, 3.5, 2.25], [14.5, 10.5, 2.5], "lacquer")
    b("counter_top", [0, 11, 1.5], [16, 12.25, 16], "lacquer")
    for nm, x0 in (("a", 0.5), ("b", 14)):
        b(f"post_front_{nm}", [x0, 12.25, 1.75], [x0 + 1.5, 24, 3.25], "bamboo")
        b(f"post_back_{nm}", [x0, 12.25, 13.5], [x0 + 1.5, 26, 15], "bamboo")
    b("beam_front", [0, 22.5, 0.75], [16, 23.5, 2], "lacquer")
    froof = ("x", -45, [8, 28, 8]) if broken else ("x", -22.5, [8, 28, 8])
    b("roof_front", [0, 28, -2.5], [16, 28.75, 8.5], "roof", rot=froof, mirror_north=True)
    b("roof_back", [0, 28, 7.5], [16, 28.75, 18], "roof", rot=("x", 22.5, [8, 28, 8]))
    b("ridge", [0, 28.25, 7], [16, 29.5, 9], "lacquer")
    b("noren", [0.5, 18.5, 1], [15.5, 22.5, 1], "noren", uv=[0, 2, 16, 13], only=("north", "south"))
    b("lamp_stock", [0.5, 13, 1.25], [2, 15, 1.75], "lamp", uv="full")
    b("lamp_open", [14, 13, 1.25], [15.5, 15, 1.75], "lamp", uv="full")
    b("pot", [10.5, 12.25, 10], [14, 15, 13.5], "misc", uv=[0, 0, 8, 8])
    b("pot_lid", [10.75, 15, 10.25], [13.75, 15.5, 13.25], "misc", uv=[0, 8, 8, 16])
    b("bowls", [2, 12.25, 11], [4.5, 13.75, 13.5], "misc", uv=[8, 8, 16, 16])
    if not broken:
        b("wheel_a", [0.25, 0.5, 5.75], [0.25, 8.5, 13.75], "wheel", uv="full", only=("east", "west"))
        b("wheel_b", [15.75, 0.5, 5.75], [15.75, 8.5, 13.75], "wheel", uv="full", only=("east", "west"))
        b("ladle", [12, 15.5, 11.5], [12.5, 19, 12], "misc", uv=[8, 0, 9, 8], rot=("z", 22.5, None))
        for nm, x in (("a", 1.25), ("b", 14.75)):
            b(f"lantern_{nm}_cord", [x - 0.15, 21, -0.6], [x + 0.15, 24, -0.3], "misc", uv=[0, 8, 1, 16])
            b(f"lantern_{nm}", [x - 1.25, 17.25, -1.75], [x + 1.25, 21, 0.75], "lantern", uv="full", light=True)
    else:
        b("wheel_a_off", [0.25, 0.5, 5.75], [0.25, 8.5, 13.75], "wheel", uv="full", only=("east", "west"), rot=("x", 22.5, [0.25, 0.5, 9.75]))
        b("wheel_b", [15.75, 0.5, 5.75], [15.75, 8.5, 13.75], "wheel", uv="full", only=("east", "west"))
        b("lantern_fallen", [5, 12.25, 6], [7.5, 16, 8.5], "lantern", uv="full", rot=("z", 45, [6.25, 12.25, 7.25]))
        b("lantern_b_cord", [14.6, 19, -0.6], [14.9, 24, -0.3], "misc", uv=[0, 8, 1, 16])
        b("lantern_b", [13.5, 15.5, -1.75], [16, 19.25, 0.75], "lantern", uv="full", rot=("z", -22.5, [14.75, 19.25, -0.5]))
        b("note", [3, 4.5, 2], [7.5, 9, 2], "note", uv="full", only=("north",), rot=("z", 22.5, None))
    return m


def nautical(broken):
    m = Model("market_stall_nautical" + ("_broken" if broken else ""), tx(
        "nautical", planks="planks_rot" if broken else "planks", barrel="barrel_old" if broken else "barrel",
        top="barrel_top", rope="rope", sail="sail_torn" if broken else "sail", net="net_torn" if broken else "net",
        iron="iron", lantern="lantern_off" if broken else "lantern", crate="crate", lamp="lamp", sign="sign"),
        "stall_nautical_planks")
    b = m.box
    b("deck", [0, 0, 0.5], [16, 0.75, 16], "planks")
    for nm, cx in (("a", 4), ("b", 12)):
        b(f"barrel_{nm}_x", [cx - 3.5, 0.75, 5.5], [cx + 3.5, 11, 12.5], "barrel", uv=[0, 0, 16, 16], faces={"up": ("top", [0, 0, 16, 16])})
        b(f"barrel_{nm}_z", [cx - 3, 0.75, 5], [cx + 3, 11, 13], "barrel", uv=[0, 0, 16, 16], faces={"up": ("top", [0, 0, 16, 16])})
    b("plank_top", [0, 11, 2], [16, 12.5, 14], "planks")
    b("lamp_stock_rim", [2.75, 7.75, 4.5], [5.25, 10.25, 5], "lantern", uv=[0, 0, 2, 2])
    b("lamp_stock", [3, 8, 4.25], [5, 10, 4.5], "lamp", uv="full")
    b("lamp_open_rim", [10.75, 7.75, 4.5], [13.25, 10.25, 5], "lantern", uv=[0, 0, 2, 2])
    b("lamp_open", [11, 8, 4.25], [13, 10, 4.5], "lamp", uv="full")
    b("mast", [13.25, 0.75, 14], [14.75, 31.5, 15.5], "planks")
    yard = ("z", -22.5, [14, 27.5, 14.75]) if broken else None
    b("yard", [3, 27, 13.75], [16, 28, 14.75], "planks", rot=yard)
    b("sail", [3.5, 14, 13.9], [13, 27, 13.9], "sail", uv="full", only=("north", "south"),
      rot=("z", -22.5, [13, 27, 13.9]) if broken else None)
    b("rope_mast", [12.75, 12.5, 13.75], [13.25, 27, 14], "rope")
    b("rope_coil", [1.5, 12.5, 9.5], [5, 13.5, 13], "rope")
    b("net", [0.5, 1.5, 4.75], [15.5, 10.5, 4.75], "net", uv=[0, 0, 15, 9], only=("north", "south"))
    b("anchor_shank", [0.75, 1, 3.25], [1.75, 10, 4.25], "iron")
    b("anchor_stock", [0, 8.5, 3.25], [2.5, 9.25, 4.25], "iron")
    b("anchor_arm_a", [0, 1, 3.25], [2.5, 1.75, 4.25], "iron", rot=("z", 45, [1.25, 1.25, 3.75]))
    if not broken:
        b("fish_crate", [9.5, 12.5, 9], [14, 15, 13], "crate", uv=[0, 0, 8, 8], faces={"up": ("crate", [8, 8, 16, 16])})
        b("lantern_hook", [5.25, 24, 13.9], [5.75, 27, 14.4], "iron")
        b("lantern", [4.25, 20.5, 12.9], [6.75, 24, 15.4], "lantern", uv="full", light=True)
    else:
        b("fish_crate_empty", [9.5, 12.5, 9], [14, 15, 13], "crate", uv=[0, 0, 8, 8], faces={"up": ("crate", [0, 8, 8, 16])})
        b("lantern_smashed", [6, 12.5, 4], [8.5, 14, 6.5], "lantern", uv="full", rot=("y", 22.5, None))
        b("seaweed_a", [3, 3, 4.6], [5, 11, 4.6], "crate", uv=[8, 0, 16, 8], only=("north", "south"))
        b("seaweed_b", [11, 5, 4.6], [13, 11, 4.6], "crate", uv=[8, 0, 16, 8], only=("north", "south"))
        b("sign_closed", [4, 12.5, 2.5], [12, 16.5, 2.5], "sign", uv=[0, 0, 16, 8], only=("north", "south"), rot=("x", 22.5, [8, 12.5, 2.5]))
    return m


def arcane(broken):
    m = Model("market_stall_arcane" + ("_broken" if broken else ""), tx(
        "arcane", stone="stone_cracked" if broken else "stone", trim="trim", crystal="crystal_dark" if broken else "crystal",
        rune="rune_broken" if broken else "rune", book="book_closed" if broken else "book", cover="cover",
        candle="candle_out" if broken else "candle", cloth="cloth_torn" if broken else "cloth", lamp="lamp", note="note"),
        "stall_arcane_stone")
    b, L = m.box, not broken
    b("step", [0.5, 0, 0.5], [15.5, 2, 15.5], "stone")
    b("counter", [1.5, 2, 2.5], [14.5, 11, 14.5], "stone")
    b("counter_top", [1, 11, 2], [15, 12.5, 15], "stone")
    b("counter_trim", [1, 11.25, 1.75], [15, 12.25, 2], "trim")
    b("cloth", [4.5, 2.5, 2.25], [11.5, 11, 2.25], "cloth", uv=[0, 0, 16, 16], only=("north",))
    b("lamp_stock", [2, 8, 2], [4, 10, 2.5], "lamp", uv="full")
    b("lamp_open", [12, 8, 2], [14, 10, 2.5], "lamp", uv="full")
    for nm, x0 in (("a", 1), ("b", 12)):
        b(f"obelisk_{nm}_base", [x0, 12.5, 11.5], [x0 + 3, 14, 14.5], "trim")
        b(f"obelisk_{nm}", [x0 + 0.25, 14, 11.75], [x0 + 2.75, 26, 14.25], "stone")
        b(f"obelisk_{nm}_cap", [x0 + 0.5, 26, 12], [x0 + 2.5, 27.5, 14], "trim", rot=("y", 45, None))
        if not broken or nm == "b":
            b(f"crystal_{nm}", [x0 + 1, 27.5, 12.5], [x0 + 2, 31.5, 13.5], "crystal", uv=[4, 0, 8, 16], light=L, rot=("y", 45, None))
            b(f"crystal_{nm}_side", [x0 + 0.5, 27.5, 12.75], [x0 + 1.25, 29.5, 13.5], "crystal", uv=[8, 0, 12, 8], light=L, rot=("z", 22.5, None))
    if not broken:
        b("rune_ring_flat", [2.5, 13.25, 3], [13.5, 13.25, 14], "rune", uv="full", only=("up", "down"), light=True)
        b("rune_ring_a", [3, 12.75, 8.5], [13, 22.75, 8.5], "rune", uv="full", only=("north", "south"), light=True, rot=("y", 45, [8, 17.75, 8.5]))
        b("rune_ring_b", [3, 12.75, 8.5], [13, 22.75, 8.5], "rune", uv="full", only=("north", "south"), light=True, rot=("y", -45, [8, 17.75, 8.5]))
        b("book", [9.5, 13.5, 2.75], [14, 13.5, 6.25], "book", uv="full", only=("up",), rot=("x", -22.5, [11.75, 13, 4.5]))
        b("book_stand", [10.5, 12.5, 3.5], [13, 13.25, 6], "cover", uv=[0, 0, 16, 4])
        for nm, x in (("a", 2.5), ("b", 5)):
            b(f"candle_{nm}", [x, 12.5, 3], [x + 1, 15 if nm == "a" else 14, 4], "candle", uv=[2, 4, 6, 16])
            b(f"flame_{nm}", [x, 15 if nm == "a" else 14, 3.5], [x + 1, 16.5 if nm == "a" else 15.5, 3.5], "candle", uv=[10, 6, 14, 12],
              only=("north", "south"), light=True)
    else:
        b("rune_ring_fallen", [3, 12.5, 4], [13, 12.5, 14], "rune", uv="full", only=("up", "down"), rot=("x", 22.5, None))
        b("crystal_a_fallen", [5, 12.5, 9], [9, 13.5, 10], "crystal", uv=[4, 0, 8, 16], rot=("y", -22.5, None))
        b("book_closed", [9.5, 12.5, 3], [13.5, 13.75, 6.5], "book", uv="full", faces={"north": ("cover", [0, 0, 16, 4]), "south": ("cover", [0, 0, 16, 4])},
          rot=("y", 22.5, None))
        b("candle_a_out", [2.5, 12.5, 3], [3.5, 13.5, 4], "candle", uv=[2, 10, 6, 16])
        b("candle_b_fallen", [4, 12.5, 4], [7, 13.5, 5], "candle", uv=[2, 4, 6, 16], rot=("y", 22.5, None))
        b("note", [5.5, 3.5, 2.05], [10.5, 8.5, 2.05], "note", uv="full", only=("north",), rot=("z", 22.5, None))
    return m


# ----------------------------------------------------------------------------- stall displays (one block, item floats over or inside)
def dmodel(skin, broken, **parts):
    return Model(f"stall_display_{skin}" + ("_broken" if broken else ""), tx(skin, **parts), f"stall_{skin}_" + next(iter(parts.values())))


def display_fantasy(broken):
    m = dmodel("fantasy", broken, planks="planks_worn" if broken else "planks", timber="timber", cloth="cloth_torn" if broken else "cloth",
               banner="banner_torn" if broken else "banner", gold="gold", note="note")
    b = m.box
    b("sill", [2, 0, 2], [14, 1.5, 14], "timber")
    b("crate", [3, 1.5, 3], [13, 9, 13], "planks")
    for nm, (x, z) in (("a", (2.5, 2.5)), ("b", (12, 2.5)), ("c", (2.5, 12)), ("d", (12, 12))):
        b(f"post_{nm}", [x, 1.5, z], [x + 1.5, 10, z + 1.5], "timber")
    b("top", [2, 9, 2], [14, 10.5, 14], "planks")
    b("cushion", [4, 10.5, 4], [12, 12, 12], "cloth", uv=[0.5, 0, 3.5, 3])
    b("tassel_a", [3.5, 10, 3.5], [4.5, 11, 4.5], "gold")
    b("tassel_b", [11.5, 10, 3.5], [12.5, 11, 4.5], "gold")
    brot = ("z", 22.5, [11, 8.75, 2.4]) if broken else None
    b("banner_rod", [4.5, 8.5, 2.15], [11.5, 9, 2.65], "gold", rot=brot)
    b("banner", [5, 1, 2.4], [11, 8.75, 2.4], "banner", uv=[4, 0, 12, 10], only=("north",), rot=brot)
    if broken:
        b("note", [8.5, 2.5, 2.3], [12.5, 6.5, 2.3], "note", uv="full", only=("north",), rot=("z", -22.5, None))
    return m


def display_scifi(broken):
    m = dmodel("scifi", broken, panel="panel_dirty" if broken else "panel", hull="hull", strip="strip_off" if broken else "strip",
               pad="pad_off" if broken else "pad", holo="holo_broken" if broken else "holo_label", tape="tape", error="error")
    b, L = m.box, not broken
    b("plinth", [1.5, 0, 1.5], [14.5, 1.5, 14.5], "hull")
    b("base_glow", [2, 1.5, 2], [14, 2, 14], "strip", light=L)
    b("column", [4, 2, 4], [12, 11, 12], "panel")
    b("strip_a", [3.75, 2.5, 3.75], [4.25, 10.5, 4.25], "strip", light=L)
    b("strip_b", [11.75, 2.5, 3.75], [12.25, 10.5, 4.25], "strip", light=L)
    b("label", [5, 5, 3.75], [11, 9, 3.75], "holo", uv=[0, 0, 16, 9], only=("north",), light=L)
    b("collar", [2.5, 11, 2.5], [13.5, 12.5, 13.5], "hull")
    b("collar_glow", [3, 10.75, 3], [13, 11, 13], "strip", light=L)
    b("pad", [4, 12.5, 4], [12, 13, 12], "hull", faces={"up": ("pad", [0, 0, 16, 16])}, light=L)
    if broken:
        b("tape_a", [2, 5.5, 3.45], [14, 7.5, 3.45], "tape", uv=[0, 0, 16, 4], only=("north",), rot=("z", 22.5, None))
        b("tape_b", [2, 5.5, 3.35], [14, 7.5, 3.35], "tape", uv=[0, 4, 16, 8], only=("north",), rot=("z", -22.5, None))
        b("error_light", [9.75, 9.75, 3.7], [10.5, 10.5, 4], "error", uv="full", light=True)
    return m


def display_steampunk(broken):
    m = dmodel("steampunk", broken, copper="copper_patina" if broken else "copper", iron="iron", brass="brass",
               pipe="pipe_patina" if broken else "pipe", dial="dial_broken" if broken else "dial",
               glass="glass_broken" if broken else "glass", gear="gear", tag="tag")
    b = m.box
    b("floor_plate", [1, 0, 1], [15, 0.75, 15], "iron")
    b("stand_a", [5, 0.75, 4.5], [11, 8, 11.5], "copper")
    b("stand_b", [4.5, 0.75, 5], [11.5, 8, 11], "copper")
    b("band_a", [4.75, 6, 4.25], [11.25, 7, 11.75], "brass")
    b("band_b", [4.25, 6, 4.75], [11.75, 7, 11.25], "brass")
    b("dial", [5.5, 1.5, 4.2], [9, 5, 4.2], "dial", uv="full", only=("north",))
    b("gear", [9, 1.25, 4.25], [12, 4.25, 4.25], "gear", uv="full", only=("north",), rot=("z", 22.5, None))
    b("pipe", [11.75, 0.75, 11.75], [13.25, 8.5, 13.25], "pipe")
    b("pipe_elbow", [11.5, 7.5, 11.5], [13.5, 9, 13.5], "brass")
    b("jar_base", [3, 8, 3], [13, 9, 13], "brass")
    b("jar_glass", [3.5, 9, 3.5], [12.5, 15.5, 12.5], "glass", uv="full", skip=("down",))
    if not broken:
        b("jar_knob", [7.5, 15.5, 7.5], [8.5, 16, 8.5], "brass")
    else:
        b("tag_string", [7.4, 7, 2.95], [7.6, 8, 3.05], "iron")
        b("tag", [5.5, 3, 2.9], [9.5, 7, 2.9], "tag", uv="full", only=("north",), rot=("z", 22.5, [7.5, 7, 2.9]))
    return m


def display_vanilla(broken):
    m = dmodel("vanilla", broken, planks="planks_old" if broken else "planks", cobble="cobble_mossy" if broken else "cobble",
               awning="awning_torn" if broken else "awning", sign="sign_closed" if broken else "sign_blank",
               lantern="lantern_off" if broken else "lantern", misc="misc", web="web")
    b = m.box
    b("cobble_base", [2, 0, 2], [14, 3, 14], "cobble")
    b("post", [6, 3, 6], [10, 11, 10], "planks")
    b("slab", [2, 11, 2], [14, 13, 14], "planks")
    b("carpet", [3, 13, 3], [13, 13.5, 13], "awning", mirror_north=True)
    b("sign", [4, 5.5, 5.25], [12, 9.5, 5.75], "planks", faces={"north": ("sign", [0, 0, 16, 8]), "south": ("sign", [0, 0, 16, 8])},
      rot=("z", -22.5, [4, 9.5, 5.5]) if broken else None)
    lrot = ("z", 45, [12.5, 13.5, 12.5]) if broken else None
    b("lantern", [11.5, 13.5, 11.5], [13.5, 15.5, 13.5], "lantern", uv="full", light=not broken, rot=lrot)
    b("lantern_cap", [12, 15.5, 12], [13, 16, 13], "misc", uv=[8, 4, 16, 8], rot=lrot)
    if broken:
        b("web", [2, 6.5, 5.9], [6, 11, 5.9], "web", uv=[16, 0, 0, 16], only=("north", "south"))
    return m


def display_nether(broken):
    m = dmodel("nether", broken, stone="blackstone_cracked" if broken else "blackstone", basalt="basalt", gold="gold",
               magma="magma_cold" if broken else "magma", lantern="soul_lantern_off" if broken else "soul_lantern", note="note")
    b, L = m.box, not broken
    b("plinth", [1, 0, 1], [15, 2, 15], "stone")
    b("column", [4, 2, 4], [12, 10, 12], "stone")
    b("magma_panel", [5, 3, 3.75], [11, 9, 4], "magma", light=L)
    b("gold_rim", [3, 10, 3], [13, 11.5, 13], "gold")
    b("spike_a", [3, 11.5, 10.5], [5, 16, 12.5], "basalt")
    b("spike_b", [11, 11.5, 10.5], [13, 16 if not broken else 13, 12.5], "basalt")
    lrot = ("z", 45, [4.25, 11.5, 4.25]) if broken else None
    b("soul_lantern", [3.25, 11.5, 3.25], [5.25, 14, 5.25], "lantern", uv="full", light=L, rot=lrot)
    b("soul_lantern_cap", [3.75, 14, 3.75], [4.75, 14.5, 4.75], "gold", rot=lrot)
    if broken:
        b("spike_b_piece", [9, 11.5, 6], [11, 13.5, 8], "basalt", rot=("y", 22.5, None))
        b("note", [5.5, 3.5, 3.6], [10.5, 8.5, 3.6], "note", uv="full", only=("north",), rot=("z", -22.5, None))
    return m


def display_yatai(broken):
    m = dmodel("yatai", broken, wood="wood_old" if broken else "wood", lacquer="lacquer_worn" if broken else "lacquer",
               bamboo="bamboo", noren="noren_torn" if broken else "noren",
               lantern="paper_lantern_torn" if broken else "paper_lantern", misc="misc", note="note")
    b = m.box
    b("base", [2, 0, 2], [14, 2, 14], "lacquer")
    b("tray", [3, 2, 3], [13, 7, 13], "wood")
    b("tray_front", [3.5, 2.5, 2.75], [12.5, 6.5, 3], "lacquer")
    b("tray_top", [2.5, 7, 2.5], [13.5, 8, 13.5], "lacquer")
    b("post_a", [3, 8, 11], [4.5, 15, 12.5], "bamboo")
    b("post_b", [11.5, 8, 11], [13, 15, 12.5], "bamboo", rot=("z", -22.5, [12.25, 8, 11.75]) if broken else None)
    b("crossbar", [2.5, 14.5, 11], [13.5, 15.5, 12.5], "lacquer", rot=("z", -22.5, [3, 15, 11.75]) if broken else None)
    b("noren", [3.5, 9.5, 11.5], [12.5, 14.5, 11.5], "noren", uv=[0, 2, 16, 13], only=("north", "south"))
    lrot = ("z", 45, [4, 8, 4]) if broken else None
    b("lantern", [2.75, 8, 2.75], [5.25, 11.5, 5.25], "lantern", uv="full", light=not broken, rot=lrot)
    b("lantern_cap", [3.25, 11.5, 3.25], [4.75, 12, 4.75], "misc", uv=[0, 8, 8, 16], rot=lrot)
    if broken:
        b("note", [7, 2.75, 2.6], [11, 6.75, 2.6], "note", uv="full", only=("north",), rot=("z", 22.5, None))
    return m


def display_nautical(broken):
    m = dmodel("nautical", broken, planks="planks_rot" if broken else "planks", barrel="barrel_old" if broken else "barrel",
               top="barrel_top", net="net_torn" if broken else "net", iron="iron",
               lantern="lantern_off" if broken else "lantern", crate="crate", rope="rope")
    b = m.box
    b("deck", [1, 0, 1], [15, 0.75, 15], "planks")
    b("barrel_x", [4.5, 0.75, 4], [11.5, 11, 12], "barrel", uv=[0, 0, 16, 16], faces={"up": ("top", [0, 0, 16, 16])})
    b("barrel_z", [4, 0.75, 4.5], [12, 11, 11.5], "barrel", uv=[0, 0, 16, 16], faces={"up": ("top", [0, 0, 16, 16])})
    b("net", [4, 3, 3.9], [12, 10, 3.9], "net", uv=[0, 0, 8, 7], only=("north", "south"))
    b("rope", [3.75, 10, 4.25], [12.25, 10.75, 11.75], "rope")
    b("anchor_shank", [12.5, 0.75, 7], [13.5, 8, 8], "iron")
    b("anchor_stock", [11.75, 6.5, 7], [14.25, 7.25, 8], "iron")
    b("anchor_arm", [12, 0.75, 7], [14, 1.5, 8], "iron", rot=("z", 45, [13, 1.1, 7.5]))
    if not broken:
        b("lantern", [9.5, 11, 9.5], [11.5, 13.5, 11.5], "lantern", uv="full", light=True)
    else:
        b("lantern_smashed", [8.5, 11, 8.5], [11, 12.5, 11], "lantern", uv="full", rot=("y", 22.5, None))
        b("seaweed", [5, 4, 3.8], [7, 11, 3.8], "crate", uv=[8, 0, 16, 8], only=("north", "south"))
    return m


def display_arcane(broken):
    m = dmodel("arcane", broken, stone="stone_cracked" if broken else "stone", trim="trim", cloth="cloth_torn" if broken else "cloth",
               crystal="crystal_dark" if broken else "crystal", rune="rune_broken" if broken else "rune", note="note")
    b, L = m.box, not broken
    b("step", [1.5, 0, 1.5], [14.5, 1.5, 14.5], "stone")
    b("column", [4.5, 1.5, 4.5], [11.5, 10, 11.5], "stone")
    b("cloth", [5.5, 2.5, 4.25], [10.5, 10, 4.25], "cloth", uv="full", only=("north",))
    b("top", [3.5, 10, 3.5], [12.5, 11.5, 12.5], "trim")
    b("crystal_b", [11.25, 11.5, 11.25], [12.25, 15.5, 12.25], "crystal", uv=[4, 0, 8, 16], light=L, rot=("y", 45, None))
    if not broken:
        b("crystal_a", [3.75, 11.5, 11.25], [4.75, 15.5, 12.25], "crystal", uv=[4, 0, 8, 16], light=True, rot=("y", 45, None))
        b("rune_flat", [3, 12, 3], [13, 12, 13], "rune", uv="full", only=("up", "down"), light=True)
        b("rune_a", [3.5, 12, 8], [12.5, 21, 8], "rune", uv="full", only=("north", "south"), light=True, rot=("y", 45, [8, 16.5, 8]))
        b("rune_b", [3.5, 12, 8], [12.5, 21, 8], "rune", uv="full", only=("north", "south"), light=True, rot=("y", -45, [8, 16.5, 8]))
    else:
        b("crystal_a_fallen", [5, 11.5, 6], [9, 12.5, 7], "crystal", uv=[4, 0, 8, 16], rot=("y", -22.5, None))
        b("rune_fallen", [3, 11.75, 3], [13, 11.75, 13], "rune", uv="full", only=("up", "down"), rot=("x", 22.5, None))
        b("note", [5.5, 3.5, 4.05], [10.5, 8.5, 4.05], "note", uv="full", only=("north",), rot=("z", 22.5, None))
    return m


DISPLAYS = {"fantasy": display_fantasy, "scifi": display_scifi, "steampunk": display_steampunk, "vanilla": display_vanilla,
            "nether": display_nether, "yatai": display_yatai, "nautical": display_nautical, "arcane": display_arcane}
# Where the display's item floats (model units, centre of the item) and how wide it should look in units.
# ground_scale is the value for StallDisplayRenderer's SCALE with ItemDisplayContext.GROUND (a ground item at scale 1 is 8 units wide).
DISPLAY_LAYOUT = {
    "fantasy": {"item": [8, 17, 8], "width": 7}, "scifi": {"item": [8, 17.5, 8], "width": 7},
    "steampunk": {"item": [8, 12.25, 8], "width": 5.5}, "vanilla": {"item": [8, 18.5, 8], "width": 7},
    "nether": {"item": [8, 16.5, 7.5], "width": 6.5}, "yatai": {"item": [8, 12.5, 7], "width": 5.5},
    "nautical": {"item": [8, 16, 7], "width": 7}, "arcane": {"item": [8, 16.5, 8], "width": 6},
}
for _v in DISPLAY_LAYOUT.values():
    _v["ground_scale"] = round(_v["width"] / 8, 3)


# Overlay points for the mod's renderer (model space, front = north = -z). Lamp faces are drawn facing north.
LAYOUT = {
    "fantasy": {"stock_lamp": [[0, 13.25, -0.5], [2, 15.25, -0.5]], "open_lamp": [[14, 13.25, -0.5], [16, 15.25, -0.5]],
                "item": [8, 16.25, 6], "item_scale": 0.4},
    "scifi": {"stock_lamp": [[3, 8.5, 1], [5, 10.5, 1]], "open_lamp": [[6, 8.5, 1], [8, 10.5, 1]],
              "item": [8, 17.25, 8], "item_scale": 0.45},
    "steampunk": {"stock_lamp": [[1.5, 8.25, 1.25], [3.5, 10.25, 1.25]], "open_lamp": [[6, 8.25, 1.25], [8, 10.25, 1.25]],
                  "item": [5.5, 16.25, 6.25], "item_scale": 0.375},
    "vanilla": {"stock_lamp": [[0, 14, 0.5], [2, 16, 0.5]], "open_lamp": [[14, 14, 0.5], [16, 16, 0.5]],
                "item": [8, 16.5, 7], "item_scale": 0.45},
    "nether": {"stock_lamp": [[0.25, 13, 1.5], [2.25, 15, 1.5]], "open_lamp": [[13.75, 13, 1.5], [15.75, 15, 1.5]],
               "item": [8, 16.75, 8.5], "item_scale": 0.45},
    "yatai": {"stock_lamp": [[0.5, 13, 1.25], [2, 15, 1.25]], "open_lamp": [[14, 13, 1.25], [15.5, 15, 1.25]],
              "item": [8, 16, 8], "item_scale": 0.4},
    "nautical": {"stock_lamp": [[3, 8, 4.25], [5, 10, 4.25]], "open_lamp": [[11, 8, 4.25], [13, 10, 4.25]],
                 "item": [8, 16.25, 7], "item_scale": 0.45},
    "arcane": {"stock_lamp": [[2, 8, 2], [4, 10, 2]], "open_lamp": [[12, 8, 2], [14, 10, 2]],
               "item": [8, 17.75, 8.5], "item_scale": 0.45},
}
SKINS = {"fantasy": fantasy, "scifi": scifi, "steampunk": steampunk, "vanilla": vanilla,
         "nether": nether, "yatai": yatai, "nautical": nautical, "arcane": arcane}


# ----------------------------------------------------------------------------- validation
def validate(models):
    used = set()
    for name, mj in models.items():
        els = mj["elements"]
        assert len(els) < 120, f"{name}: {len(els)} elements"
        assert "particle" in mj["textures"], name
        for el in els:
            for v in el["from"] + el["to"]:
                assert -16 <= v <= 32, f"{name}/{el['name']}: coordinate {v}"
            assert all(el["from"][i] <= el["to"][i] for i in range(3)), f"{name}/{el['name']}: from > to"
            r = el.get("rotation")
            if r:
                assert r["angle"] in (-45, -22.5, 0, 22.5, 45) and r["axis"] in "xyz", f"{name}/{el['name']}: rotation"
            assert el["faces"], f"{name}/{el['name']}: no faces"
            for fc in el["faces"].values():
                assert all(0 <= v <= 16 for v in fc["uv"]), f"{name}/{el['name']}: uv {fc['uv']}"
                assert fc["texture"][1:] in mj["textures"], f"{name}/{el['name']}: {fc['texture']}"
        for ref in mj["textures"].values():
            tname = ref.split("/")[-1]
            assert ref.startswith(NS) and tname in TEXTURES, f"{name}: missing texture {ref}"
            used.add(tname)
    unused = set(TEXTURES) - used
    assert not unused, f"textures drawn but unused: {sorted(unused)}"
    for t in TEXTURES.values():
        assert t.n in (16, 32), t.name


# ----------------------------------------------------------------------------- notes
def notes(models):
    def face(c):
        (x1, y1, z1), (x2, y2, _) = c
        return f"x {num(x1)}..{num(x2)}, y {num(y1)}..{num(y2)}, at z = {num(z1)}, facing north"

    looks = {
        "fantasy": ("Timber market booth", "Dark-oak counter with a plank front, four posts, knee braces, a striped crimson and cream "
                    "canopy sloping forward over the front with a scalloped valance, a blue and gold banner draped over the counter "
                    "front, a lantern hung under the canopy, a green curtain at the back, a crate of apples, a sack and two bottles.",
                    "Canopy collapsed steeply to the back beam and torn, banner faded and shredded and hanging askew, one front post "
                    "snapped (the front beam drops onto it), the upper counter rail loose, goods gone except a toppled crate and a "
                    "lying bottle, lantern dark and fallen, cobwebs front and back, a pinned CLOSED note.",
                    "`canopy` (rotated -22.5 on x around the front beam) and `canopy_valance`; broken: `canopy_collapsed`, `rag_a`, `rag_b`."),
        "scifi": ("Vending terminal", "Two-block monolith: dark plinth with a glowing base line, white cabinet with a dispense slot, keypad "
                  "and lamp bar, an open alcove with a glowing projector pad (the item hovers over it), alcove light strips and "
                  "downlight, a holo pane leaning out above the alcove (animated SHOP screen), side fins with light strips, a "
                  "hood with an under-glow, a vent and an antenna with a red tip.",
                  "Every strip dark, the holo pane cracked and hanging by one corner (animated flicker with glitch frames and ERR), "
                  "the access door swung open showing the circuit board with three cables spilling out, one red error light, "
                  "hazard tape crossed over the alcove (OUT OF / ORDER), pad dark, antenna bent, panels scorched and grimy.",
                  "No awning; the `hood` is the top. `holo` is the leaning pane (cutout, emissive)."),
        "steampunk": ("Tinkerer's boiler booth", "Lopsided on purpose: a walnut counter with a riveted iron front, brass trims, two gears "
                      "and brass-bezel lamps on the customer's right; a copper boiler with brass bands, a glowing firebox, a pressure "
                      "dial, a whistle and an iron chimney to the top of the second block on the customer's left; a copper back "
                      "panel with a brass cornice and nameplate; a pipe from the boiler across to a valve wheel; the item floats "
                      "under a brass-based glass bell jar.",
                      "Copper and pipes gone green, firebox cold, the pipe burst (a stub, a sagging length, a steam puff, a puddle "
                      "on the counter), dial glass cracked with the needle dropped, an OUT OF ORDER tag hanging from the dial, the "
                      "big gear fallen and leaning on the counter, the bell jar cracked, chimney cap knocked askew.",
                      "No awning; the chimney is the top (`chimney_cap`, `chimney_top`)."),
        "vanilla": ("Player-built stall", "Cobblestone base, oak counter with two trapdoor fronts and a slab top, four fence-style posts "
                    "with rails, a stepped red and white wool awning (no slopes, as a player would build it), a SHOP sign hung "
                    "under the awning lip, a lantern and a flower pot at the back of the counter, a village bell at the front.",
                    "Mossy cobble, weathered planks, the customer's-left front post gone and the awning's front step sagging into "
                    "the gap with holes torn in the wool, the sign hanging by one chain reading CLOSED, one trapdoor crooked and "
                    "the other missing, two boards nailed in an X across the counter, a cobweb front and back, the lantern knocked "
                    "over and dark, the flower dead, the bell gone.",
                    "`awning_front`, `awning_middle`, `awning_back` (stepped, unrotated; broken rotates `awning_front` -22.5 on z)."),
        "nether": ("Blackstone shrine", "Polished blackstone counter with a glowing magma panel (animated) and a gold edge, a gold barter "
                   "plate for the item, basalt pillars, a basalt back wall, a heavy lintel with jagged teeth, nether wart on the roof, "
                   "two soul lanterns hung on chains.",
                   "Cracked blackstone with gold flecks, the magma cold, one lantern's chain snapped and the lantern fallen on the "
                   "counter, the other lantern dark, a tooth broken off the lintel and lying on the counter, a scorched CLOSED note.",
                   "`lintel`, `roof`, `tooth_*` (teeth rotated 45 on z)."),
        "yatai": ("Eastern street cart", "A cedar cart on two side wheels with a red lacquer front and top, bamboo posts, a tiled gable "
                  "roof, an indigo noren curtain in three panels, two red paper lanterns, a covered pot with a ladle and a stack of bowls.",
                  "Lacquer and wood faded, tiles missing, the front roof slope dropped steeply, the noren torn with a panel gone, one "
                  "wheel knocked askew, one lantern torn and fallen on the counter, the other hanging crooked and dark, a CLOSED note.",
                  "`roof_front` (rotated -22.5 on x; -45 broken), `roof_back` (+22.5 on x, overhangs 2 units behind the block), `ridge`."),
        "nautical": ("Dockside stall", "A deck plank on two barrels, a fishing net across the barrel fronts, an anchor at the side, a "
                     "rope coil and a crate of fish on the plank, a mast at the back with a yard, a canvas sail as the backdrop and "
                     "a brass ship's lantern.",
                     "Rotting planks with algae, barnacled barrels, the net and sail torn, the yard and sail slipped, the lantern "
                     "smashed on the plank, the fish crate empty, seaweed hanging off the barrels, a painted CLOSED board leaning on the front.",
                     "`mast`, `yard`, `sail` (the mast reaches 31.5)."),
        "arcane": ("Mage's pedestal", "A violet stone counter with a gold trim and a starry blue cloth, two obelisks with gold caps "
                   "and floating crystals, three animated rune rings (one flat, two crossed) around the item, an open spellbook on a "
                   "stand, two candles.",
                   "Stone cracked, crystals dark (one fallen), the rune rings broken and dim with one fallen flat, the book closed "
                   "and dusty, the candles out and knocked over, the cloth torn, a CLOSED note.",
                   "No roof; the crystals are the top (`crystal_*`)."),
    }
    out = ["# Market Stall models: notes for the build conversation", "",
           "**Status: approved by the owner (2026-10-11), ready to hook up.** Eight skins, each with a broken variant.", "",
           "Made by `tools/gen_stall_design.py` (stdlib Python; rerun it, never hand-edit). Everything in this folder is the "
           "deliverable of `docs/STALL_MODEL_HANDOFF.md`. The preview page `tools/preview/stall_models.html` renders these exact "
           "files. `layout.json` holds the overlay points below in machine-readable form.", "",
           "## Common to all eight models", "",
           "- Vanilla JSON block models, `parent: minecraft:block/block`, `render_type: minecraft:cutout` (Forge reads it), "
           "`ambientocclusion: false` (as the bank models). Front faces north (-z). Every element has a `name`.",
           "- **x stays inside 0..16 everywhere**, so stalls in a row never overlap or z-fight; only the front (z < 0) and the "
           "top (y > 16) overhang. Collision stays one block; the shape is two blocks tall, so the block should also block or "
           "be paired with the space above (the build conversation decides; an invisible upper block like `bank_atm_upper` works).",
           "- Glowing elements carry `forge_data: {block_light: 15, sky_light: 15}` and `shade: false` on their faces (the mod's "
           "existing models use the same field). Unlit in the broken variants.",
           "- Animated textures: `stall_scifi_holo` (6 frames), `stall_scifi_holo_broken` (8 frames, flicker), "
           "`stall_scifi_holo_label` (6), `stall_steampunk_firebox` (4 frames), `stall_nether_magma` (3), `stall_arcane_rune` (4); each has a `.mcmeta`.",
           "- Lamp faces: the model draws an unlit lamp (dark glass) there; draw the lit quad 0.01 to 0.02 in front of it (towards -z). "
           "\"Customer's left\" is +x (the customer stands at -z looking south).",
           "- Broken variants keep the lamp housings, unlit, so the lamp overlay simply is not drawn. No display item.",
           "- Inventory icon: `market_stall_vanilla.json` carries `display` transforms (gui scale 0.4, shifted down 3); check the "
           "slot in game.", ""]
    out += ["| Model | Elements |", "|---|---|"] + [f"| `{n}` | {len(m['elements'])} |" for n, m in models.items()] + [""]
    for skin, (title, look, broke, top) in looks.items():
        lay = LAYOUT[skin]
        out += [f"## {skin}: {title}", "", look, "", f"**Broken:** {broke}", "", f"**Top:** {top}", "",
                f"- Stock lamp: {face(lay['stock_lamp'])}",
                f"- Open lamp: {face(lay['open_lamp'])}",
                f"- Display item: point ({', '.join(str(num(v)) for v in lay['item'])}), scale {lay['item_scale']}", ""]
    skins = ", ".join(f"`{k}`" for k in LAYOUT)
    out += ["## Hook-up checklist for the build conversation", "",
            f"Skin ids, in order (use this order for the enum): {skins}. Models: `market_stall_<skin>.json` and "
            "`market_stall_<skin>_broken.json`.", "",
            "1. **Assets generator** (`tools/gen_stall_assets.py`): import or run `gen_stall_design.py`, then copy "
            "`tools/stall/models/*.json` to `assets/bsp_core/models/block/` and `tools/stall/textures/*` (PNGs and `.mcmeta`) to "
            "`assets/bsp_core/textures/block/stall/`. The models already reference `bsp_core:block/stall/<name>`; keep the file "
            "names. Re-run the validator after copying (element count, rotations, every texture present).",
            "2. **Blockstate** `market_stall.json`: `facing` x `skin` x `broken` x `half` (see 4). Lower half: model "
            "`bsp_core:block/market_stall_<skin>` or `..._broken`, `y` 0 / 90 / 180 / 270 for north / east / south / west (the "
            "same as `facing_blockstate` in `gen_bank_assets.py`). Upper half: no model (an empty `minecraft:block/air`-style "
            "model or a particle-only model), as `bank_atm_upper`.",
            "3. **Item model** `models/item/market_stall.json`: `{\"parent\": \"bsp_core:block/market_stall_vanilla\"}`. The "
            "vanilla model carries the `display` transforms (gui, ground, fixed, hands).",
            "4. **Block** `shop/MarketStallBlock`: `FACING` (horizontal, placed facing the player), `SKIN` (an `EnumProperty` of "
            "a `StallSkin` enum with the eight ids above as serialized names), `BROKEN` (boolean, true while no shop is assigned; "
            "the block entity sets it on assign and unassign), `HALF` (two blocks tall, copy `BankAtmBlock`: the lower half "
            "holds the block entity and the whole model, the upper half only keeps the space and forwards clicks and breaking). "
            "`noOcclusion()`; collision one full block per half. The models set `render_type: minecraft:cutout` themselves.",
            "5. **Choosing a skin** (owner's decision, 2026-10-11): the admin picks the skin in the stall setup panel (the "
            "panel used to assign the shop and set opening times); it is stored in the block state. One item places every "
            "skin; a fresh stall starts as `vanilla` and broken until assigned.",
            "6. **Renderer** `client/MarketStallRenderer` (lower half only): use the same transform as `BankAtmRenderer`: "
            "`translate(0.5, 0, 0.5)`, `mulPose(Axis.YP.rotationDegrees(-yRot(facing)))`, `translate(-0.5, 0, -0.5)`, then "
            "model units / 16. Values come from `layout.json` (have the generator emit them as Java constants or a JSON in the "
            "assets). Not broken: the **stock lamp** quad at `stock_lamp` (green `0x58F29A` while the shop has stock, red "
            "`0xE24B3B` when sold out) and the **open lamp** at `open_lamp` (amber `0xF2B544` while open, nothing while closed), "
            "both facing north, 0.01 in front of the given z (towards -z), full bright. The **display item**: the shop's first "
            "item at `item`, scaled by `item_scale`, `ItemDisplayContext.FIXED`, spinning on game time, using the level's light. "
            "Broken: draw nothing. Inflate `getRenderBoundingBox` to two blocks tall.",
            "7. **Glow**: the lit parts glow through `forge_data` in the models; the block itself needs no light level. "
            "Optionally `lightLevel` 7 while not broken for the nether, sci-fi, steampunk, yatai and arcane skins.",
            "8. **Rest of the feature**: assignment and opening times like the Vendor Post, redstone to the block below while "
            "open, Jade line (shop, skin, stock state), JEI info, guide page (no history words), lang through `gen_shop_lang.py`, "
            "game tests (assigning clears `BROKEN`, unassigning sets it, the skin survives a reload), `TESTING_BANKING` part.",
            "9. **Check in game** (not verifiable here): facing for all four directions, the inventory icon angle, the sci-fi "
            "hazard tape text reads the right way round, animated textures run, lamps sit on their housings.", ""]
    out += ["## Stall Displays", "",
            "**Status: approved by the owner (2026-10-11), ready to hook up.** One-block displays for the existing `StallDisplayBlock`, "
            "one per stall skin, each with a broken variant, built from the stall textures plus two wordless ones "
            "(`stall_scifi_holo_label`, `stall_vanilla_sign_blank`: the owner wants no SHOP word on the displays). Models "
            "`stall_display_<skin>.json` and `stall_display_<skin>_broken.json` in `models/`; item points in "
            "`display_layout.json`.", "",
            "| Skin | Look | Item centre (model units) | Item width | `SCALE` with GROUND |", "|---|---|---|---|---|"]
    dlooks = {"fantasy": "Timber-cornered crate, crimson cushion with gold tassels, a small banner on the front",
              "scifi": "Holo projector column: glowing base and collar, corner strips, a wordless holo label pane, a lit pad",
              "steampunk": "Riveted copper stand with a dial, a gear and a pipe; the item floats inside a bell jar",
              "vanilla": "Cobble base, fence post, oak slab with a striped carpet, a blank oak sign, a lantern",
              "nether": "Blackstone column with a magma front, a gold rim, two basalt spikes, a soul lantern",
              "yatai": "Lacquered tray stand, bamboo frame with a noren backdrop, a paper lantern",
              "nautical": "Barrel with a net and a rope ring, an anchor at the side, a ship's lantern on the lid",
              "arcane": "Violet column with a starry cloth, gold top, crystals, three rune rings around the item"}
    for k, v in DISPLAY_LAYOUT.items():
        out.append(f"| `{k}` | {dlooks[k]} | ({', '.join(str(num(c)) for c in v['item'])}) | {num(v['width'])} | {v['ground_scale']} |")
    out += ["", "**Broken** (owner's decision: while unlinked, or while the linked shop is closed or missing): the same build neglected, as the stalls "
            "(cold glows, torn cloth, cracks, a fallen piece, a CLOSED note or OUT OF ORDER tape or tag). No item.", "",
            "### Hook-up for the display", "",
            "1. Copy the 16 `stall_display_*` models with the stall models (same generator); they use the stall textures.",
            "2. Blockstate `stall_display.json`: `facing` x `skin` x `broken`, model `bsp_core:block/stall_display_<skin>` or "
            "`..._broken`, `y` 0 / 90 / 180 / 270. The placeholder model from `gen_bank_assets.py` goes.",
            "3. `StallDisplayBlock`: add `SKIN` (the same `StallSkin` enum as the stall) and `BROKEN`. Item model parent "
            "`stall_display_vanilla`.",
            "4. Skin (owner's decision): **the display copies the skin of the stall it is linked to**, updated when it links and "
            "when the stall's skin changes; linked to a Vendor Post or unlinked, it shows `vanilla`.",
            "5. `StallDisplayRenderer`: replace the fixed `HEIGHT` and `SCALE` with the per-skin point and `SCALE` above: "
            "translate to the item centre / 16 (rotated with the block facing like the stall renderer, although the points "
            "are centred so only the z of nether, yatai and nautical moves), keep the bob and spin, GROUND context. Nothing "
            "drawn while broken. The steampunk and yatai items sit inside the jar and frame, so keep their bob small.", ""]
    out += ["## Textures", "", "All under `textures/`, referenced as `bsp_core:block/stall/<name>`; 16x16 except the text-bearing "
            "ones (notes, tags, signs, tape, holo) at 32x32.", ""]
    out += [", ".join(f"`{n}`" for n in sorted(TEXTURES)), ""]
    return "\n".join(out)


# ----------------------------------------------------------------------------- preview page
PAGE = r"""<title>Market Stall Models</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=Oswald:wght@500;600&display=swap">
<style>
:root{ --bg:#f3f1ea; --panel:#fff; --line:#d9d4c7; --fg:#1a1b20; --muted:#5f6472; --acc:#0c8f78;
  --display:"Oswald",system-ui,sans-serif; --body:"IBM Plex Sans",system-ui,-apple-system,"Segoe UI",sans-serif; color-scheme:light; }
@media (prefers-color-scheme: dark){ :root:not([data-theme="light"]){ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --acc:#19d3b0; color-scheme:dark; } }
:root[data-theme="dark"]{ --bg:#0f1115; --panel:#171a21; --line:#2a2f3a; --fg:#e8eaf0; --muted:#9aa3b5; --acc:#19d3b0; color-scheme:dark; }
body{ background:var(--bg); color:var(--fg); font-family:var(--body); margin:0; font-size:14px; line-height:1.5; }
.wrap{ max-width:1180px; margin:0 auto; padding-block:28px 56px; padding-inline:16px; display:flex; flex-direction:column; gap:28px; }
h1{ font-family:var(--display); font-size:clamp(22px,3.4vw,32px); margin:0; letter-spacing:.02em; }
h2{ font-family:var(--display); font-size:19px; margin:0; color:var(--acc); letter-spacing:.02em; }
h3{ font-family:var(--display); font-size:15px; margin:0; letter-spacing:.03em; }
.lede{ color:var(--muted); margin:0; max-width:80ch; }
section{ display:flex; flex-direction:column; gap:14px; min-width:0; }
.card{ background:var(--panel); border:1px solid var(--line); border-radius:6px; padding:14px; display:flex; flex-direction:column; gap:10px; min-width:0; }
.card p{ margin:0; font-size:13px; color:var(--muted); }
.grid{ display:grid; grid-template-columns:repeat(auto-fit,minmax(270px,1fr)); gap:14px; }
.view{ width:100%; aspect-ratio:1/1; background:radial-gradient(ellipse at 50% 70%,#2f3646 0%,#0c0e12 75%); cursor:grab; touch-action:none; border-radius:4px; }
.view.wide{ aspect-ratio:2.4/1; } .view.icon{ width:128px; aspect-ratio:1/1; background:#8b8b8b; border:4px solid #373737; border-radius:0; cursor:default; }
.ctl{ display:flex; flex-wrap:wrap; gap:10px 20px; align-items:center; font-size:13px; } .ctl label{ display:flex; gap:8px; align-items:center; }
input:focus-visible{ outline:2px solid var(--acc); outline-offset:2px; }
table{ border-collapse:collapse; font-size:13px; width:100%; } th,td{ text-align:left; padding:6px 8px; border-bottom:1px solid var(--line); vertical-align:top; } th{ color:var(--muted); font-weight:600; }
.scroll{ overflow-x:auto; } code{ font-size:12px; }
ol{ margin:0; padding-left:20px; } ol li{ margin-bottom:6px; max-width:80ch; }
.row{ display:flex; gap:16px; align-items:center; flex-wrap:wrap; }
</style>
<div class="wrap">
<section><h1>Market Stall Models</h1>
<p class="lede">The sixteen block models for the Market Stall (eight skins, normal and broken), rendered from the exact JSON and PNG files in <code>tools/stall/</code> (Minecraft face shading, cutout, nearest-pixel textures, animated textures running). Each skin is its own build. The front faces north, towards you at the start; drag to turn. The lamps and the floating item are what the mod draws on top, at the points in NOTES.md. Each skin card also shows its Stall Display, one block to the stall's right.</p>
<div class="card"><div class="ctl">
<label><input type="checkbox" id="broken"> Broken (no shop assigned)</label>
<label><input type="checkbox" id="stock" checked> In stock</label>
<label><input type="checkbox" id="open" checked> Open</label>
<label><input type="checkbox" id="night"> Night</label>
<label><input type="checkbox" id="spin" checked> Turn slowly</label></div></div></section>
<section><h2>1. The eight skins, each with its Stall Display</h2><div class="grid" id="skins"></div></section>
<section><h2>2. In a row</h2><div class="card"><div class="view wide" id="rowview"></div><p>Eight stalls side by side on one-block spacing, as they would stand in a market street. Nothing reaches sideways into the next block.</p></div></section>
<section><h2>3. Inventory icon</h2><div class="card"><div class="row"><div class="view icon" id="iconview"></div><p>The normal vanilla stall in a slot, using its <code>display.gui</code> transform (rotation 30, 225, 0; scale 0.4; shifted down 3). Worth a check in game.</p></div></div></section>
<section><h2>4. Overlay points and size</h2><div class="card scroll"><table id="pts"></table></div></section>
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
<script>
(function(){
const D=/*DATA*/null;
const V3=THREE.Vector3, el=id=>document.getElementById(id), reduce=matchMedia('(prefers-reduced-motion: reduce)').matches;
const state={broken:false,stock:true,open:true,night:false,spin:!reduce};
const SH={up:1,down:.5,north:.8,south:.8,east:.6,west:.6};
const TITLES={fantasy:'Fantasy: timber market booth',scifi:'Sci-fi: vending terminal',steampunk:'Steampunk: tinkerer’s boiler booth',vanilla:'Vanilla: player-built stall',nether:'Nether: blackstone shrine',yatai:'Yatai: eastern street cart',nautical:'Nautical: dockside stall',arcane:'Arcane: mage\u2019s pedestal'};
const texCache={};
function tex(name){ if(texCache[name]) return texCache[name]; const d=D.textures[name], c=document.createElement('canvas'); c.width=c.height=d.n;
  const t=new THREE.CanvasTexture(c); t.magFilter=t.minFilter=THREE.NearestFilter; t.generateMipmaps=false;
  const e={c,ctx:c.getContext('2d'),img:new Image(),t,d,frame:-1}; e.img.onload=()=>draw(e,0); e.img.src=d.src; return texCache[name]=e; }
function draw(e,f){ e.ctx.clearRect(0,0,e.d.n,e.d.n); e.ctx.drawImage(e.img,0,f*e.d.n,e.d.n,e.d.n,0,0,e.d.n,e.d.n); e.frame=f; e.t.needsUpdate=true; }
const mats=[];
function mat(name,light){ const m=new THREE.MeshBasicMaterial({map:tex(name).t,vertexColors:true,alphaTest:.5}); m.userData.light=light; m.userData.base=new THREE.Color(1,1,1); mats.push(m); return m; }
function corners(f,a,b){ const [x1,y1,z1]=a,[x2,y2,z2]=b; return {
  north:[[x2,y2,z1],[x1,y2,z1],[x1,y1,z1],[x2,y1,z1]], south:[[x1,y2,z2],[x2,y2,z2],[x2,y1,z2],[x1,y1,z2]],
  west:[[x1,y2,z1],[x1,y2,z2],[x1,y1,z2],[x1,y1,z1]], east:[[x2,y2,z2],[x2,y2,z1],[x2,y1,z1],[x2,y1,z2]],
  up:[[x1,y2,z1],[x2,y2,z1],[x2,y2,z2],[x1,y2,z2]], down:[[x1,y1,z2],[x2,y1,z2],[x2,y1,z1],[x1,y1,z1]]}[f]; }
function buildModel(j){ const buckets={};
  for(const e of j.elements){ const light=!!e.forge_data; let rot=null;
    if(e.rotation){ const a=e.rotation.axis; rot={ax:new V3(a==='x'?1:0,a==='y'?1:0,a==='z'?1:0),ang:e.rotation.angle*Math.PI/180,o:new V3(...e.rotation.origin)}; }
    for(const [fn,fc] of Object.entries(e.faces)){ const tn=j.textures[fc.texture.slice(1)].split('/').pop(), key=tn+'|'+light;
      const b=buckets[key]||(buckets[key]={tn,light,pos:[],uv:[],col:[],idx:[]});
      const cs=corners(fn,e.from,e.to).map(p=>{ const v=new V3(...p); if(rot) v.sub(rot.o).applyAxisAngle(rot.ax,rot.ang).add(rot.o); return v; });
      const [u1,v1,u2,v2]=fc.uv, uvs=[[u1,v1],[u2,v1],[u2,v2],[u1,v2]], s=fc.shade===false?1:SH[fn], n=b.pos.length/3;
      cs.forEach((v,i)=>{ b.pos.push(v.x,v.y,v.z); b.uv.push(uvs[i][0]/16,1-uvs[i][1]/16); b.col.push(s,s,s); });
      b.idx.push(n,n+3,n+2,n,n+2,n+1); } }
  const g=new THREE.Group();
  for(const b of Object.values(buckets)){ const geo=new THREE.BufferGeometry(); geo.setAttribute('position',new THREE.Float32BufferAttribute(b.pos,3));
    geo.setAttribute('uv',new THREE.Float32BufferAttribute(b.uv,2)); geo.setAttribute('color',new THREE.Float32BufferAttribute(b.col,3)); geo.setIndex(b.idx);
    g.add(new THREE.Mesh(geo,mat(b.tn,b.light))); }
  return g; }
const GEM=['................','.......aa.......','......abba......','.....abccba.....','....abccccba....','...abccddccba...','..abccddddccba..','..abcddddddcba..','..abccddddccba..','...abccddccba...','....abccccba....','.....abccba.....','......abba......','.......aa.......','................','................'];
const gemTex=(()=>{ const c=document.createElement('canvas'); c.width=c.height=16; const x=c.getContext('2d'), P={a:'#0b5a2a',b:'#17b04a',c:'#41f07a',d:'#a8ffc8'};
  GEM.forEach((r,y)=>[...r].forEach((ch,i)=>{ if(P[ch]){ x.fillStyle=P[ch]; x.fillRect(i,y,1,1); } })); const t=new THREE.CanvasTexture(c); t.magFilter=t.minFilter=THREE.NearestFilter; return t; })();
const itemMat=new THREE.MeshBasicMaterial({map:gemTex,alphaTest:.5,side:THREE.DoubleSide}); itemMat.userData.light=false; itemMat.userData.base=new THREE.Color(1,1,1); mats.push(itemMat);
function lampQuad(c,color){ const [a,b]=c, cs=corners('north',[a[0],a[1],a[2]-0.03],[b[0],b[1],a[2]-0.03]); const geo=new THREE.BufferGeometry();
  geo.setAttribute('position',new THREE.Float32BufferAttribute(cs.flat(),3)); geo.setIndex([0,3,2,0,2,1]); const m=new THREE.MeshBasicMaterial({color}); return new THREE.Mesh(geo,m); }
function stall(skin,withDisplay){ const g=new THREE.Group(), L=D.layout[skin];
  const normal=buildModel(D.models['market_stall_'+skin]), broken=buildModel(D.models['market_stall_'+skin+'_broken']); g.add(normal,broken);
  const stock=lampQuad(L.stock_lamp,0x58f29a), open=lampQuad(L.open_lamp,0xf2b544); g.add(stock,open);
  const item=new THREE.Mesh(new THREE.PlaneGeometry(16,16),itemMat); item.position.set(...L.item); item.scale.setScalar(L.item_scale); g.add(item);
  let dn=null, db=null, ditem=null;
  if(withDisplay){ const DL=D.dlayout[skin]; dn=buildModel(D.models['stall_display_'+skin]); db=buildModel(D.models['stall_display_'+skin+'_broken']);
    dn.position.x=db.position.x=-20; ditem=new THREE.Mesh(new THREE.PlaneGeometry(16,16),itemMat); ditem.position.set(DL.item[0]-20,DL.item[1],DL.item[2]); ditem.scale.setScalar(DL.width/16); g.add(dn,db,ditem); }
  return {g,tick(t){ if(dn){ dn.visible=!state.broken; db.visible=state.broken; ditem.visible=!state.broken; ditem.rotation.y=reduce?0.6:t*1.2; ditem.position.y=D.dlayout[skin].item[1]+(reduce?0:Math.sin(t*1.6)*0.4); }
    normal.visible=!state.broken; broken.visible=state.broken; stock.visible=!state.broken; open.visible=!state.broken&&state.open;
    stock.material.color.setHex(state.stock?0x58f29a:0xe24b3b); item.visible=!state.broken; item.rotation.y=reduce?0.6:t*1.4; }}; }
function ground(g,x0,x1){ const geo=new THREE.PlaneGeometry(x1-x0,64), m=new THREE.MeshBasicMaterial({color:0x3d4a35}); m.userData.light=false; m.userData.base=m.color.clone(); mats.push(m);
  const p=new THREE.Mesh(geo,m); p.rotation.x=-Math.PI/2; p.position.set((x0+x1)/2,-0.01,8); g.add(p);
  const grid=new THREE.GridHelper(Math.max(x1-x0,64),Math.max(x1-x0,64)/16,0x55654a,0x55654a); grid.position.set((x0+x1)/2,0,8); g.add(grid); }
const canvas=document.createElement('canvas'); canvas.style.cssText='position:fixed;left:0;top:0;width:100%;height:100%;pointer-events:none;z-index:1'; document.body.appendChild(canvas);
const renderer=new THREE.WebGLRenderer({canvas,antialias:true,alpha:true}); renderer.setPixelRatio(Math.min(devicePixelRatio,2)); renderer.setClearColor(0,0);
const views=[];
function view(node,build,opts){ const scene=new THREE.Scene(), cam=opts.ortho?new THREE.OrthographicCamera(-8,8,8,-8,-200,200):new THREE.PerspectiveCamera(30,1,1,4000);
  const ticks=build(scene); const v={node,scene,cam,ticks,opts,ry:opts.ry??2.6,rx:opts.rx??.32,drag:false}; let lx=0,ly=0;
  if(!opts.ortho){ node.addEventListener('pointerdown',e=>{v.drag=true;lx=e.clientX;ly=e.clientY;node.setPointerCapture(e.pointerId);});
    node.addEventListener('pointermove',e=>{ if(!v.drag) return; v.ry-=(e.clientX-lx)*.01; v.rx=Math.max(-.15,Math.min(1.4,v.rx+(e.clientY-ly)*.01)); lx=e.clientX; ly=e.clientY; });
    node.addEventListener('pointerup',()=>v.drag=false); node.addEventListener('pointercancel',()=>v.drag=false); }
  views.push(v); }
for(const skin of Object.keys(D.layout)){ const c=document.createElement('div'); c.className='card'; c.innerHTML='<h3>'+TITLES[skin]+'</h3>'; el('skins').appendChild(c);
  const n=document.createElement('div'); n.className='view'; c.appendChild(n); const p=document.createElement('p'); p.textContent=D.blurb[skin]; c.appendChild(p);
  view(n,s=>{ ground(s,-44,40); const st=stall(skin,true); s.add(st.g); return [st.tick]; },{dist:100,target:[-2,12,7]}); }
view(el('rowview'),s=>{ ground(s,-24,152); return Object.keys(D.layout).map((skin,i)=>{ const st=stall(skin); st.g.position.x=112-i*16; s.add(st.g); return st.tick; }); },{dist:250,target:[64,13,8],ry:2.95,rx:.18});
view(el('iconview'),s=>{ const pivot=new THREE.Group(), gd=D.models.market_stall_vanilla.display.gui, m=buildModel(D.models.market_stall_vanilla);
  m.position.set(-8,-8,-8); pivot.add(m); pivot.rotation.set(...gd.rotation.map(a=>a*Math.PI/180),'XYZ'); pivot.scale.set(...gd.scale); pivot.position.set(...gd.translation); s.add(pivot); return []; },{ortho:true});
let rows='<tr><th>Skin</th><th>Stock lamp (faces north)</th><th>Open lamp (faces north)</th><th>Item point, scale</th><th>Elements normal / broken</th></tr>';
const f=a=>'('+a.join(', ')+')';
for(const [k,L] of Object.entries(D.layout)) rows+='<tr><td>'+k+'</td><td>'+f(L.stock_lamp[0])+' to '+f(L.stock_lamp[1])+'</td><td>'+f(L.open_lamp[0])+' to '+f(L.open_lamp[1])+'</td><td>'+f(L.item)+', '+L.item_scale+'</td><td>'+D.models['market_stall_'+k].elements.length+' / '+D.models['market_stall_'+k+'_broken'].elements.length+'</td></tr>';
el('pts').innerHTML=rows;
el('broken').onchange=e=>state.broken=e.target.checked; el('stock').onchange=e=>state.stock=e.target.checked; el('open').onchange=e=>state.open=e.target.checked;
el('night').onchange=e=>state.night=e.target.checked; el('spin').checked=state.spin; el('spin').onchange=e=>state.spin=e.target.checked;
const t0=performance.now();
(function loop(now){ const t=(now-t0)/1000, W=innerWidth, H=innerHeight, pr=renderer.getPixelRatio();
  if(canvas.width!==Math.round(W*pr)||canvas.height!==Math.round(H*pr)) renderer.setSize(W,H,false);
  for(const e of Object.values(texCache)) if(e.d.frames>1&&e.img.complete){ const fr=Math.floor(t*20/e.d.ft)%e.d.frames; if(fr!==e.frame) draw(e,fr); }
  for(const m of mats) if(!m.userData.light) m.color.copy(m.userData.base).multiplyScalar(state.night?.28:1);
  renderer.setScissorTest(false); renderer.clear(); renderer.setScissorTest(true);
  for(const v of views){ const r=v.node.getBoundingClientRect(); if(r.bottom<0||r.top>H||r.width<2) continue; v.ticks.forEach(k=>k(t));
    if(!v.opts.ortho){ if(state.spin&&!v.drag) v.ry+=.0025; const D_=v.opts.dist, T_=v.opts.target;
      v.cam.position.set(T_[0]+Math.sin(v.ry)*D_*Math.cos(v.rx),T_[1]+Math.sin(v.rx)*D_,T_[2]+Math.cos(v.ry)*D_*Math.cos(v.rx)); v.cam.lookAt(...T_); v.cam.aspect=r.width/r.height; v.cam.updateProjectionMatrix(); }
    else { v.cam.position.set(0,0,100); v.cam.lookAt(0,0,0); }
    const bottom=H-r.bottom; renderer.setViewport(r.left,bottom,r.width,r.height); renderer.setScissor(r.left,bottom,r.width,r.height); renderer.render(v.scene,v.cam); }
  requestAnimationFrame(loop); })(t0);
})();
</script>
"""


def main():
    fantasy_textures()
    scifi_textures()
    scifi_textures_rest()
    steampunk_textures()
    vanilla_textures()
    nether_textures()
    yatai_textures()
    nautical_textures()
    arcane_textures()
    models = {}
    for skin, fn in SKINS.items():
        for broken in (False, True):
            m = fn(broken)
            models[m.name] = m.json()
    for skin, fn in DISPLAYS.items():
        for broken in (False, True):
            m = fn(broken)
            models[m.name] = m.json()
    validate(models)
    if OUT.exists():
        shutil.rmtree(OUT)
    (OUT / "models").mkdir(parents=True)
    (OUT / "textures").mkdir()
    for name, mj in models.items():
        (OUT / "models" / f"{name}.json").write_text(json.dumps(mj, indent=1) + "\n")
    for t in TEXTURES.values():
        (OUT / "textures" / f"{t.name}.png").write_bytes(t.png())
        if t.frames > 1:
            (OUT / "textures" / f"{t.name}.png.mcmeta").write_text(json.dumps({"animation": {"frametime": t.frametime}}) + "\n")
    (OUT / "layout.json").write_text(json.dumps(LAYOUT, indent=1) + "\n")
    (OUT / "display_layout.json").write_text(json.dumps(DISPLAY_LAYOUT, indent=1) + "\n")
    (OUT / "NOTES.md").write_text(notes(models))
    blurb = {
        "fantasy": "Timber counter, striped canopy with a scalloped edge, a banner draped over the front, a lantern, goods. Broken: canopy collapsed and torn, a post snapped, a CLOSED note, cobwebs.",
        "scifi": "Two-block terminal: lit strips, an alcove with a projector pad, a leaning holo screen (animated). Broken: dark, the screen cracked and hanging, a door open with cables out, hazard tape.",
        "steampunk": "Counter on one side, copper boiler with firebox, dial and chimney on the other; the item sits in a bell jar. Broken: green copper, cold firebox, burst pipe with steam, a tag on the dial.",
        "vanilla": "Cobble, oak, trapdoors, fence posts, a stepped wool awning, a SHOP sign, a lantern, a flower pot, a bell. Broken: a post gone, the awning sagging, boards nailed across, CLOSED.",
        "nether": "Blackstone counter with a glowing magma front, basalt pillars, a spiked lintel, soul lanterns on chains, a gold barter plate. Broken: magma cold, a lantern fallen, a tooth broken off, cracks.",
        "yatai": "A cart on side wheels: red lacquer, bamboo posts, a tiled gable roof, an indigo noren, paper lanterns, a pot and bowls. Broken: tiles gone, roof slumped, noren torn, a wheel askew.",
        "nautical": "A plank on two barrels with a net, an anchor, rope, a crate of fish, a mast, a sail and a ship's lantern. Broken: rot and barnacles, torn net and sail, the lantern smashed, CLOSED board.",
        "arcane": "Violet stone, a starry cloth, two obelisks with floating crystals, three rune rings around the item, a spellbook, candles. Broken: crystals dark, rings fallen, the book shut, candles out.",
    }
    data = {"models": models, "layout": LAYOUT, "dlayout": DISPLAY_LAYOUT, "blurb": blurb,
            "textures": {t.name: {"n": t.n, "frames": t.frames, "ft": t.frametime,
                                  "src": "data:image/png;base64," + base64.b64encode(t.png()).decode()} for t in TEXTURES.values()}}
    PREVIEW.write_text(PAGE.replace("/*DATA*/null", json.dumps(data, separators=(",", ":"))))
    counts = ", ".join(f"{n.replace('market_stall_', '')} {len(m['elements'])}" for n, m in models.items())
    print(f"{len(models)} models ({counts}), {len(TEXTURES)} textures -> {OUT.relative_to(ROOT)}; preview {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
