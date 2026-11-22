#!/usr/bin/env python3
"""Writes the Score Leader's spark particle texture (8 x 8, white: the particle tints it gold).

  python3 tools/gen_leader_spark.py
"""
import struct
import zlib
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/textures/particle/leader_spark.png"

# a four-pointed sparkle: a bright core, long arms, a soft halo between them
ROWS = [
    "...#....",
    "...#....",
    "..+#+...",
    "###@###.",
    "..+#+...",
    "...#....",
    "...#....",
    "........",
]
ALPHA = {".": 0, "+": 90, "#": 220, "@": 255}


def png(rows):
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(struct.pack("BBBB", 255, 255, 255, ALPHA[c]) for c in row)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 8, 8, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_bytes(png(ROWS))
print("wrote", OUT.relative_to(OUT.parents[5]))
