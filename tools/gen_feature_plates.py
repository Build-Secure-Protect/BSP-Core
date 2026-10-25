#!/usr/bin/env python3
"""Feature plates for the CurseForge description: one screen of a mock-up page, isolated on an 800 x 600 plate with a title
band and a caption, captured headlessly like the other pictures.

  python3 tools/gen_feature_plates.py          -> tools/preview/feature_plates/<slug>.html and docs/curseforge/images/feature_<slug>.jpg
  python3 tools/gen_feature_plates.py html     -> the pages only (open one with file:// to check it)

Each plate copies its source page whole (so the page's own styles and scripts run), hides everything, then shows the one
element the JS picks, fixed in the middle of the plate. Add a plate to PLATES: slug, source page, a JS expression giving the
element, the band title, the caption, and an optional CSS width for elements whose width comes from their parent.
"""
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
PREVIEW = ROOT / "tools/preview"
OUT = PREVIEW / "feature_plates"
IMAGES = ROOT / "docs/curseforge/images"

PLATES = [
    ("bank_atm", "bank_ui_concepts.html", "document.querySelector('.screen')", "The Shatter Bank",
     "Your account on the ATM: pieces, the coins you keep by tier, and the keys for paying, the log, deposits and withdrawals.", None),
    ("shop_screen", "shop_ui_concepts.html", "document.querySelector('.screen')", "A shop",
     "Cards with the item, buy in gold, sell dim, a stock badge and a star for instant buy. Left click buys, right click sells.", None),
    ("land_office", "land_office_concepts.html", "document.querySelectorAll('.scr')[1]", "The Land Office",
     "The land around you drawn from the terrain: plots for sale in amber, your claims in green, your outline in white.", None),
    ("teams_screen", "team_popup_concepts.html", "document.querySelectorAll('.screen')[0]", "The Team screen",
     "Members with where they are and what they hold; INVITE, MESSAGES, TEAM CHAT and the leader's SETTINGS along the bottom.", 500),
    ("teams_messages", "team_popup_concepts.html", "document.querySelectorAll('.screen')[1]", "The team's board",
     "The leader's pinned note on top, the last fifty lines with a mark on the ones new to you, and a line to type.", 500),
    ("teams_invite", "team_popup_concepts.html", "[...document.querySelectorAll('h3')].find(h => h.textContent.startsWith('P3')).parentElement.querySelector('.game')",
     "An invite arrives", "A card at the top right with the inviter, the team, two keys and the seconds left, plus a clickable chat line.", None),
    ("score_boards", "score_board_concepts.html", "document.querySelectorAll('.scr')[0]", "Boards on a Score Screen",
     "A screen can show another mod's board, here the home servers, and turn through the scoreboard, the rules, the prizes and every board.", None),
    ("kits_browser", "kit_refined.html", "document.querySelectorAll('.scr.q')[0]", "The kit browser",
     "Every kit is a card: REDEEM when ready, greyed with the time left after a redeem, the rank, server, price or slots that lock it, violet when one waits for you.", None),
    ("kits_card", "kit_refined.html", "document.querySelectorAll('.scr.q')[1]", "Inside a kit",
     "Click a card's picture: the items with their counts, the rule, and every condition ticked or crossed.", None),
    ("kits_admin", "kit_refined.html", "document.querySelectorAll('.scr.v')[0]", "The KITS tab",
     "Admins make kits in the admin panel: the list, the chosen kit's summary, NEW KIT, EDIT, GIVE TO and DELETE.", None),
    ("kits_editor", "kit_refined.html", "document.querySelectorAll('.scr.v')[1]", "The kit editor",
     "The kit's slots, FROM HAND and FROM INVENTORY, an item browser with search and mod chips, and every rule as a row of chips.", None),
    ("herald_discord", "discord_herald_concepts.html", "document.querySelectorAll('.grid3')[0].children[0].querySelectorAll('.dc')[0]", "Herald on Discord",
     "A message from the BSP bot when your totem is being stolen, and another when it is over. The alert channel only ever reads \"A totem is being stolen on Frostwarf\".", 560),
    ("herald_card", "herald_tree_concepts.html", "document.getElementById('cv-a')", "Herald on the tree",
     "Herald sits off Alarm on the tier II ring. Its card carries your own Discord line: LINK gives a code for /link in the BSP Discord.", 700),
]

CSS = """
html, body { margin: 0 !important; padding: 0 !important; width: 800px; height: 600px; overflow: hidden; background: #0f1115 !important; }
body > * { visibility: hidden; }
.bsp-band, .bsp-cap { visibility: visible; position: fixed; left: 0; width: 800px; box-sizing: border-box; z-index: 9;
  font-family: "IBM Plex Sans", "Segoe UI", system-ui, sans-serif; color: #e8eaf0; }
.bsp-band { top: 0; height: 44px; padding: 10px 20px; background: #171a21; border-bottom: 1px solid #2a2f3a; font-size: 19px; font-weight: 600; letter-spacing: .01em; }
.bsp-band b { color: #19d3b0; font-weight: 600; }
.bsp-cap { bottom: 0; min-height: 52px; padding: 9px 20px; background: #171a21; border-top: 1px solid #2a2f3a; font-size: 13px; line-height: 1.35; color: #9aa3b5; }
.bsp-target { visibility: visible !important; position: fixed !important; left: 50% !important; top: 62px !important; margin: 0 !important;
  transform: translateX(-50%) !important; transform-origin: top center; z-index: 5; }
"""


def page(slug, source, pick, title, caption, width):
    src = (PREVIEW / source).read_text()
    extra = f".bsp-target {{ width: {width}px !important; max-width: none !important; }}" if width else ""
    head = f"<style id=\"bsp-plate\">{CSS}{extra}</style>"
    tail = (f"<div class=\"bsp-band\"><b>BSP-Core</b> · {title}</div><div class=\"bsp-cap\">{caption}</div>"
            f"<script>document.documentElement.setAttribute('data-theme','dark');"
            f"setTimeout(function(){{ var el = ({pick}); if (el) el.classList.add('bsp-target'); else document.title = 'NO TARGET'; }}, 300);</script>")
    if "</head>" in src:
        html = src.replace("</head>", head + "</head>", 1)
    elif "<body" in src:
        i = src.index("<body")
        html = src[:i] + head + src[i:]
    else:
        html = src.replace("<html", head + "<html", 1) if "<html" in src else head + src
    end = html.rfind("</body>") if "</body>" in html else html.rfind("</html>") if "</html>" in html else len(html)
    html = html[:end] + tail + html[end:]
    # the copy sits one folder down: relative references point back up
    html = html.replace('src="', 'src="../').replace("src='", "src='../")
    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{slug}.html"
    out.write_text(html)
    return out


def main():
    pages = [page(*p) for p in PLATES]
    print("pages:", len(pages), "->", OUT)
    if len(sys.argv) > 1 and sys.argv[1] == "html":
        return
    from capture_images import shot, jpeg
    IMAGES.mkdir(parents=True, exist_ok=True)
    for (slug, *_), p in zip(PLATES, pages):
        tmp = Path(tempfile.gettempdir()) / f"bsp_feature_{slug}.png"
        shot(f"file://{p}", 800, 600, tmp, budget=8000)
        jpeg(tmp, IMAGES / f"feature_{slug}.jpg")
        print("captured", slug)


if __name__ == "__main__":
    main()
