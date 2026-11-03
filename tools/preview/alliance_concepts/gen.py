"""Writes the alliance mock-up boards (Design canvas .dc.html files). Team screen drawn at 2x its GUI size (340x236 -> 680x472)."""
import json, os, sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)

VIO, TEAL, BG, SLOT, DIM, LINE, MUTED, TEXT = "#B58CFF", "#4FD8C8", "#10151C", "#0C0E12", "#2A2F3A", "#1A1F29", "#9AA3B5", "#E6EAF2"
GREEN, AMBER, GREY, RED, GOLD = "#58F29A", "#F2B544", "#555C6A", "#FF6B5C", "#FFD23A"
TEAMS = {"Emberfall": "#FF5555", "Saltmarsh": "#55FFFF", "Thornwood": "#55FF55", "Ravensgate": "#AAAAAA", "Copperhill": "#FFAA00", "Duskwater": "#5555FF"}

CSS = f"""
body{{margin:0}}
.ab{{font-family:'Pixelify Sans',monospace}}
.a{{position:absolute}}
.lbl{{font-size:15px;color:#b8b1a3;letter-spacing:.06em}}
.cap{{font-size:14px;color:#8f897d;max-width:680px;line-height:1.35}}
.scr{{position:relative;width:684px;height:476px;background:{BG};border:2px solid {VIO};box-sizing:border-box;flex:none;overflow:hidden}}
.scr.wide{{width:924px}}
.box{{position:absolute;background:{SLOT};outline:2px solid {DIM}}}
.k{{position:absolute;font-family:inherit;font-size:12px;letter-spacing:.03em;background:{SLOT};color:{MUTED};border:2px solid {DIM};padding:0;display:flex;align-items:center;justify-content:center;box-sizing:border-box;white-space:nowrap}}
.k.on{{color:{VIO};border-color:{VIO}}}
.k.al{{color:{TEAL};border-color:{TEAL}}}
.k.warn{{color:{RED};border-color:{RED}}}
.k.lock{{color:{GREY}}}
.t{{font-size:16px;color:{TEXT};white-space:nowrap}}
.m{{font-size:13px;color:{MUTED};white-space:nowrap}}
.mw{{font-size:13px;color:{MUTED};line-height:1.4}}
.row{{position:absolute;left:8px;height:26px;border-bottom:2px solid {LINE};box-sizing:border-box}}
.dot{{position:absolute;width:10px;height:10px;top:6px;left:6px}}
.badge{{position:absolute;height:16px;padding:0 6px;font-size:11px;color:#10151C;display:flex;align-items:center}}
.tog{{position:absolute;width:44px;height:20px;border:2px solid {DIM};box-sizing:border-box;font-size:11px;display:flex;align-items:center;justify-content:center}}
.tog.on{{border-color:{TEAL};color:{TEAL};background:#0E2624}}
.tog.off{{color:{GREY}}}
.chip{{position:absolute;width:10px;height:10px}}
.card{{position:relative;width:300px;background:#141A22;border:2px solid {RED};box-sizing:border-box;padding:10px 12px}}
.mc{{position:relative;width:924px;background:#2c2f24;box-sizing:border-box;padding:14px 16px;font-size:15px;line-height:1.7;color:#fff}}
"""

def head(title):
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@400;600&amp;display=swap">
<style>{CSS}</style>
</helmet>
"""

def tail(w, h):
    return f"""</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{w},"height":{h}}}}}'>
class Component extends DCLogic {{
renderVals() {{
return {{}};
}}
}}
</script>
</body>
</html>
"""

def a(x, y, cls, text, extra=""):
    return f'<div class="a {cls}" style="left:{x}px;top:{y}px;{extra}">{text}</div>'

def key(x, y, w, label, look="", h=40, label_aria=None):
    aria = f' aria-label="{label_aria}"' if label_aria else ""
    return f'<button class="k {look}" type="button"{aria} style="left:{x}px;top:{y}px;width:{w}px;height:{h}px">{label}</button>'

def tname(name, extra=""):
    return f'<span style="color:{TEAMS[name]}{extra}">{name}</span>'

def frame(inner, header_left, header_right, keys, wide=False, accent=VIO):
    w = 924 if wide else 684
    style = f' style="border-color:{accent}"' if accent != VIO else ""
    return (f'<div class="scr{" wide" if wide else ""}"{style}>' + header_left
            + a(w - 20 - 300, 10, "m", header_right, "width:300px;text-align:right")
            + inner + keys + "</div>")

def mainbox(w=648, h=364, x=16, y=40):
    return f'<div class="box" style="left:{x}px;top:{y}px;width:{w}px;height:{h}px"></div>'

def ally_row(y, team, name, lead, state, where, coords, w=632, x0=16, tp=False):
    dot = GREEN if state == "here" else AMBER if state == "else" else GREY
    s = f'<div class="row" style="left:{x0 + 8}px;top:{y}px;width:{w - 16}px">'
    s += f'<div class="dot" style="background:{dot}"></div>'
    s += a(24, 3, "t", name + (f' <span style="color:{GOLD};font-size:11px">LEAD</span>' if lead else ""))
    if state == "off":
        s += a(220, 5, "m", where)
    else:
        s += a(200, 5, "m", where)
        s += a(380, 5, "m", coords, f"color:{TEXT if coords[0].isdigit() or coords[0] == '-' else GREY}")
    if tp and state != "off":
        s += key(w - 16 - 96, 1, 92, "TELEPORT", "al", 22)
    s += "</div>"
    return s

def team_head(y, team, note, x0=16, w=632):
    return (f'<div class="chip" style="left:{x0 + 14}px;top:{y + 6}px;background:{TEAMS[team]}"></div>'
            + a(x0 + 30, y, "t", tname(team)) + a(x0 + w - 300, y + 2, "m", note, "width:290px;text-align:right"))

def roster(x0=16, y0=40, w=632, tp=True, compact=False):
    s, y = "", y0 + 8
    groups = [
        ("Saltmarsh", "3 online · shares coordinates", [
            ("TallyFern", True, "here", "online here · Overworld", "1204, 71, -388"),
            ("Pellmouth", False, "else", "on BSP-Survival · The Nether", "152, 40, 96"),
            ("Mirewick", False, "off", "offline · seen 3 h ago", "")]),
        ("Thornwood", "2 online · coordinates hidden", [
            ("Quillon", True, "else", "on BSP-Survival · Overworld", "not shared"),
            ("Harrowgate", False, "else", "on BSP-Spawn · Overworld", "not shared")]),
    ]
    if compact:
        groups = [(t, n, rows[:2]) for t, n, rows in groups]
    for team, note, rows in groups:
        s += team_head(y, team, note, x0, w)
        y += 28
        for r in rows:
            s += ally_row(y, team, *r, w=w, x0=x0, tp=tp)
            y += 28
        y += 8
    return s, y

def chat(x0=16, y0=40, w=632, h=364, box_label="Type to the alliance"):
    lines = [
        ("Tue 19:02", "Saltmarsh", "TallyFern", "anyone spare blaze rods? paying 30 each"),
        ("Tue 19:04", "Emberfall", "Brindlewick", "got 12, sending them to your Collect box"),
        ("Tue 19:31", "Thornwood", "Quillon", "raid on Ravensgate tonight at 9, who is in"),
        ("Tue 19:33", "Saltmarsh", "Pellmouth", "me, bring fire res"),
        ("Tue 20:58", "Emberfall", "Okra_Moss", "on my way, meet at the nether hub"),
    ]
    s, y = "", y0 + 10
    for when, team, who, text in lines:
        s += a(x0 + 12, y, "m", when, f"color:{GREY}")
        s += a(x0 + 96, y - 2, "t", f'<span style="color:{TEAMS[team]}">[{team}]</span> {who}: <span style="color:{MUTED}">{text}</span>',
               f"width:{w - 110}px;overflow:hidden;text-overflow:ellipsis;font-size:15px")
        y += 26
    s += a(x0 + 12, y + 4, "m", "5 new since you last looked", f"color:{TEAL}")
    s += (f'<div class="a" style="left:{x0 + 12}px;top:{y0 + h - 40}px;width:{w - 24 - 96}px;height:28px;background:#0F141B;'
          f'border:2px solid {DIM};box-sizing:border-box;padding:4px 8px;font-size:13px;color:{GREY}">{box_label}</div>')
    s += key(x0 + w - 12 - 88, y0 + h - 40, 88, "SEND", "al", 28)
    return s

def perks(x0=16, y0=40, w=632, h=364):
    s = a(x0 + 12, y0 + 10, "m", f"WHAT {tname('Emberfall')} GIVES ITS ALLIES · only the leader changes these")
    rows = [("They see through our cloaks", True), ("Our Ward leaves them alone", False),
            ("Our Alarm stays quiet for them", False), ("They may use our machines and plasma", False)]
    y = y0 + 34
    for text, on in rows:
        s += a(x0 + 12, y + 1, "t", text, "font-size:15px")
        s += f'<div class="tog {"on" if on else "off"}" style="left:{x0 + w - 12 - 44}px;top:{y}px">{"ON" if on else "OFF"}</div>'
        y += 28
    y += 6
    s += f'<div class="a" style="left:{x0 + 12}px;top:{y}px;width:{w - 24}px;height:2px;background:{LINE}"></div>'
    y += 10
    s += a(x0 + 12, y + 1, "t", "Share our coordinates with allies", "font-size:15px")
    s += f'<div class="tog off" style="left:{x0 + w - 12 - 44}px;top:{y}px">OFF</div>'
    s += a(x0 + 12, y + 22, "m", "Off: allies see our server and dimension only.")
    y += 50
    s += a(x0 + 12, y, "m", "WHAT WE GET FROM THEM")
    s += a(x0 + 12, y + 20, "m", f"{tname('Saltmarsh')}  cloak · ward · coordinates")
    s += a(x0 + 12, y + 40, "m", f"{tname('Thornwood')}  nothing yet")
    s += a(x0 + 12, y0 + h - 44, "mw", "Allies can never upgrade, pick up or recall our totems. An ally who starts a steal on any of us breaks the alliance at once.",
           f"width:{w - 24}px;color:{GREY}")
    return s

def invite(x0=16, y0=40, w=632, h=364):
    s = a(x0 + 12, y0 + 10, "m", "Teams not in an alliance. The invite goes to their leader.")
    rows = [("Copperhill", "Wrenna leads · 4 members", "invite"),
            ("Duskwater", "Ossory leads · 2 members", "wait"),
            ("Ravensgate", "Corvane leads · 5 members", "lock")]
    y = y0 + 34
    for team, note, state in rows:
        s += f'<div class="row" style="left:{x0 + 8}px;top:{y}px;width:{w - 16}px">'
        s += f'<div class="chip" style="left:6px;top:6px;background:{TEAMS[team]}"></div>'
        s += a(24, 3, "t", tname(team)) + a(170, 5, "m", note)
        if state == "invite":
            s += key(w - 16 - 84, 1, 80, "invite", "al", 22)
        elif state == "wait":
            s += a(w - 16 - 150, 5, "m", "waiting 41 s", f"color:{AMBER};width:144px;text-align:right")
        else:
            s += a(w - 16 - 250, 5, "m", "broke an alliance: 18 h to wait", f"color:{GREY};width:244px;text-align:right")
        s += "</div>"
        y += 28
    s += a(x0 + 12, y0 + h - 30, "m", "The Iron Pact: 2 of 3 teams. Invites wait 60 s.")
    return s

def header_team(title="TEAM", sub=None, color=VIO):
    return a(20, 10, "t", title, f"color:{color};font-size:16px")

def bar(keys, y=416):
    s, x = "", 16
    for k in keys:
        if k is None:
            x += 12
            continue
        label, w, look = k
        s += key(x, y, w, label, look)
        x += w + 6
    return s

def board(title, panels, notes, wide=False):
    cols = ""
    for (lbl, html, cap) in panels:
        cols += (f'<div style="display:flex;flex-direction:column;gap:10px"><div class="lbl">{lbl}</div>{html}'
                 + (f'<div class="cap">{cap}</div>' if cap else "") + "</div>")
    return cols

# ---------------------------------------------------------------- option A: two sections, tabs in the header
def tabs_header(active):
    s = a(20, 8, "t", "TEAM", f"color:{VIO if active == 'team' else GREY};font-size:16px")
    s += f'<div class="a" style="left:18px;top:30px;width:52px;height:2px;background:{VIO if active == "team" else "transparent"}"></div>'
    s += a(92, 8, "t", "ALLIANCE", f"color:{TEAL if active == 'al' else GREY};font-size:16px")
    s += f'<div class="a" style="left:90px;top:30px;width:92px;height:2px;background:{TEAL if active == "al" else "transparent"}"></div>'
    if active != "al":
        s += f'<div class="badge" style="left:186px;top:6px;background:{TEAL}">5</div>'
    return s

A_BAR = lambda on: bar([("ALLIES", 92, "al" if on == "allies" else ""), ("INVITE TEAM", 112, "al" if on == "invite" else ""),
                         ("ALLY BOARD", 108, "al" if on == "chat" else ""), None, ("ALLY CHAT off", 112, ""), None,
                         ("PERKS", 74, "al" if on == "perks" else ""), ("LEAVE", 66, "warn")])

def a_screen(on, inner):
    sub = {"allies": f"{tname('Emberfall')} in The Iron Pact · 3 teams"}.get(on, "The Iron Pact · you lead Emberfall")
    return frame(mainbox() + inner, tabs_header("al"), sub, A_BAR(on), accent=TEAL)

def option_a():
    r, _ = roster()
    panels = [
        ("1  ALLIANCE TAB: ALLIES", a_screen("allies", r), "TEAM and ALLIANCE are two tabs in the header. Each has its own row of keys at the bottom. The TEAM tab is unchanged."),
        ("2  ALLY BOARD: THE ALLIANCE CHAT PAGE", a_screen("chat", chat()), "Like MESSAGES: the history, a type box and SEND. ALLY CHAT off/on switches your normal chat to the alliance, and /a sends one line."),
        ("3  PERKS (LEADER)", a_screen("perks", perks()), "What your team gives, the coordinates switch, and what you get back. Members see this page but can't change it."),
        ("4  INVITE A TEAM", a_screen("invite", invite()), "With no alliance yet, the ALLIANCE tab opens here with a name box and CREATE above the list."),
    ]
    team = frame(mainbox() + team_members(), tabs_header("team"), "you lead this team",
                 bar([("MEMBERS", 92, "on"), ("INVITE", 80, ""), ("MESSAGES", 108, ""), None, ("TEAM CHAT off", 108, ""), None, ("SETTINGS", 100, ""), ("DISBAND", 96, "warn")]))
    panels.insert(0, ("0  TEAM TAB, WITH THE ALLIANCE TAB BESIDE IT", team, "The teal 5 is new lines on the alliance board."))
    return panels

def team_members():
    rows = [("Brindlewick", True, "here", "online here", "3 totems"), ("Okra_Moss", False, "else", "on BSP-Survival", "1 totem"),
            ("Fenwhistle", False, "off", "seen 2 d ago", "no totem placed")]
    s, y = "", 48
    s += a(28, 50, "t", tname("Emberfall")) + a(460, 52, "m", "3 of 5 members", "width:190px;text-align:right")
    y = 76
    for name, lead, st, where, tot in rows:
        dot = GREEN if st == "here" else AMBER if st == "else" else GREY
        s += f'<div class="row" style="left:24px;top:{y}px;width:616px"><div class="dot" style="background:{dot}"></div>'
        s += a(24, 3, "t", name + (f' <span style="color:{GOLD};font-size:11px">LEAD</span>' if lead else ""))
        s += a(300, 5, "m", tot) + a(440, 5, "m", where, f"width:170px;text-align:right;color:{GREEN if st == 'here' else MUTED}") + "</div>"
        y += 28
    return s

# ---------------------------------------------------------------- option B: one ALLIANCE key, sub-tabs inside the box
B_BAR = bar([("MEMBERS", 84, ""), ("INVITE", 70, ""), ("MESSAGES", 96, ""), ("ALLIANCE", 96, "al"), None, ("CHAT: TEAM", 108, ""), None, ("SETTINGS", 86, ""), ("LEAVE", 60, "warn")])

def subtabs(on):
    s, x = "", 28
    for name in ("ROSTER", "BOARD", "PERKS", "TEAMS"):
        w = 92
        s += key(x, 50, w, name, "al" if name == on else "", 26)
        x += w + 6
    s += a(440, 56, "m", "The Iron Pact · 3 of 3", "width:200px;text-align:right")
    if on != "BOARD":
        s += f'<div class="badge" style="left:{28 + 98 + 78}px;top:44px;background:{TEAL}">5</div>'
    return s

def b_screen(on, inner):
    return frame(mainbox() + subtabs(on) + inner, header_team(), f"you lead {tname('Emberfall')}", B_BAR)

def option_b():
    r, _ = roster(y0=78, compact=False)
    c = chat(y0=78, h=326)
    p = perks(y0=78, h=326)
    i = invite(y0=78, h=326)
    return [
        ("1  ALLIANCE KEY ON THE TEAM BAR: ROSTER", b_screen("ROSTER", r), "One ALLIANCE key joins MEMBERS, INVITE and MESSAGES. Inside it, four small tabs: ROSTER, BOARD, PERKS, TEAMS. DISBAND moves into SETTINGS to make room."),
        ("2  BOARD: THE ALLIANCE CHAT PAGE", b_screen("BOARD", c), "The chat key cycles CHAT: TEAM, CHAT: ALLY, CHAT: OFF, so your normal chat goes to one of them. /t and /a send one line."),
        ("3  PERKS (LEADER)", b_screen("PERKS", p), "The same switches as A, drawn in the shorter box under the tabs."),
        ("4  TEAMS: INVITE OR LEAVE", b_screen("TEAMS", i + key(560, 352, 88, "LEAVE PACT", "warn", 26)), "The alliance's own LEAVE sits on this page, apart from LEAVE TEAM on the bar."),
    ]

# ---------------------------------------------------------------- option C: wider screen, alliance column always on the right
def c_column(on):
    x = 680
    s = f'<div class="box" style="left:{x}px;top:40px;width:228px;height:418px;outline-color:{TEAL}"></div>'
    s += a(x + 12, 50, "t", "THE IRON PACT", f"color:{TEAL}")
    y = 78
    for team, online, total in (("Emberfall", 2, 3), ("Saltmarsh", 2, 3), ("Thornwood", 2, 2)):
        s += f'<div class="chip" style="left:{x + 12}px;top:{y + 5}px;background:{TEAMS[team]}"></div>'
        s += a(x + 30, y, "t", tname(team), "font-size:15px") + a(x + 150, y + 2, "m", f"{online}/{total} on", "width:66px;text-align:right")
        y += 24
    y += 8
    for label, k in (("ALLIES", "allies"), ("ALLY BOARD", "chat"), ("PERKS", "perks"), ("INVITE TEAM", "invite")):
        s += key(x + 12, y, 204, label, "al" if on == k else "", 32)
        if k == "chat" and on != "chat":
            s += f'<div class="badge" style="left:{x + 196}px;top:{y - 6}px;background:{TEAL}">5</div>'
        y += 38
    s += key(x + 12, y + 2, 204, "ALLY CHAT off", "", 32)
    s += key(x + 12, 414, 204, "LEAVE ALLIANCE", "warn", 32)
    return s

C_BAR = bar([("MEMBERS", 92, ""), ("INVITE", 80, ""), ("MESSAGES", 108, ""), None, ("TEAM CHAT off", 108, ""), None, ("SETTINGS", 100, ""), ("DISBAND", 96, "warn")])

def c_screen(on, inner):
    return frame(mainbox() + inner + c_column(on), header_team(), "you lead this team", C_BAR, wide=True)

def option_c():
    r, _ = roster(tp=True)
    return [
        ("1  ALLIANCE COLUMN, ALWAYS ON THE RIGHT: ALLIES", c_screen("allies", r), "The screen is wider. A teal column shows the alliance's teams with how many are online, and its own keys. The team keys stay at the bottom, unchanged. A team with no alliance sees CREATE ALLIANCE in the column."),
        ("2  ALLY BOARD", c_screen("chat", chat()), "The board opens in the big box on the left, so the team and the alliance never share a key."),
        ("3  PERKS (LEADER)", c_screen("perks", perks()), ""),
        ("4  INVITE A TEAM", c_screen("invite", invite()), ""),
    ]

# ---------------------------------------------------------------- the break, the same for every option
def break_board():
    card = (f'<div class="card" style="left:0;top:0"><div class="t" style="color:{RED};font-size:15px">ALLIANCE BROKEN</div>'
            f'<div class="mw" style="margin-top:6px;color:{TEXT}">Quillon of {tname("Thornwood")} started stealing Okra_Moss\'s totem.</div>'
            f'<div class="mw" style="margin-top:4px">{tname("Thornwood")} is out of The Iron Pact. Their perks have stopped.</div></div>')
    invite_card = (f'<div class="card" style="left:0;top:0;border-color:{TEAL}"><div class="t" style="color:{TEAL};font-size:15px">ALLIANCE INVITE</div>'
                   f'<div class="mw" style="margin-top:6px;color:{TEXT}">{tname("Saltmarsh")} asks {tname("Emberfall")} to join The Iron Pact.</div>'
                   f'<div class="mw" style="margin-top:6px">[ ACCEPT ]  [ DECLINE ]   41 s</div></div>')
    chat_lines = (f'<div class="mc"><span style="color:{RED}">[Alliance] Quillon of Thornwood started stealing Okra_Moss\'s totem. The Iron Pact is broken for Thornwood.</span><br>'
                  f'<span style="color:{RED}">[Alliance] Thornwood can\'t ally with Emberfall or Saltmarsh for 24 h.</span></div>')
    thief = (f'<div class="mc"><span style="color:{AMBER}">You broke The Iron Pact. Thornwood is out of it.</span><br>'
             f'<span style="color:{AMBER}">Stealing from an ally takes longer: 7:30 instead of 5:00.</span></div>')
    hud = (f'<div style="position:relative;width:300px;height:64px;background:#0C0E12cc;border:2px solid {RED};box-sizing:border-box">'
           + a(12, 8, "t", "STEALING Okra_Moss's totem", f"color:{RED};font-size:14px")
           + a(12, 32, "t", "7:30", f"font-size:20px;color:{TEXT}") + a(80, 37, "m", "ally steal: half as long again")
           + "</div>")
    panels = [
        ("5  ALLIES' SCREENS: THE BREAK CARD", card, "The same right-edge card as team invites, in red, with a warning sound. Everyone in the alliance's teams sees it, on any server."),
        ("6  THEIR CHAT", chat_lines, "Two lines stay in chat after the card has gone."),
        ("7  THE THIEF'S SIDE", f'<div style="display:flex;flex-direction:column;gap:12px">{thief}{hud}</div>', "The steal goes ahead but takes 7:30 instead of 5:00, so the victim's allies have time to come."),
        ("8  A LEADER'S INVITE CARD", invite_card, "Only the leader gets this card. Members see a chat line that an invite is waiting."),
    ]
    return panels

def write_board(fname, title, panels, w, h, rows_split=None):
    body = f'<div class="ab" style="width:{w}px;height:{h}px;box-sizing:border-box;padding:40px;background:#1c1e23;display:flex;flex-wrap:wrap;gap:48px 40px;align-content:flex-start">'
    for lbl, html, cap in panels:
        body += (f'<div style="display:flex;flex-direction:column;gap:10px">' f'<div class="lbl">{lbl}</div>{html}'
                 + (f'<div class="cap">{cap}</div>' if cap else "") + "</div>")
    body += "</div>\n"
    open(os.path.join(OUT, fname), "w").write(head(title) + body + tail(w, h))

write_board("Main.dc.html", "Alliances, option A: two tabs", option_a(), 2280, 1260)
write_board("OptionB.dc.html", "Alliances, option B: one ALLIANCE key", option_b(), 1580, 1260)
write_board("OptionC.dc.html", "Alliances, option C: alliance column", option_c(), 2060, 1260)
write_board("Break.dc.html", "Alliances: the break and the invite", break_board(), 2060, 560)

canvas = {"v": 3, "createdOnFiles": {"v": 1, "at": "2026-11-03T12:00:00Z"}, "title": "Alliances Concepts", "launch": {"view": "canvas"}, "pages": [],
          "boards": {"Main.dc.html": {"x": 0, "y": 0, "w": 2280, "h": 1260, "title": "A. TEAM and ALLIANCE tabs"},
                     "OptionB.dc.html": {"x": 0, "y": 1640, "w": 1580, "h": 1260, "title": "B. One ALLIANCE key"},
                     "OptionC.dc.html": {"x": 0, "y": 3280, "w": 2060, "h": 1260, "title": "C. Alliance column on the right"},
                     "Break.dc.html": {"x": 0, "y": 4920, "w": 2060, "h": 560, "title": "Every option: the break and the invite"}},
          "order": ["Main.dc.html", "OptionB.dc.html", "OptionC.dc.html", "Break.dc.html"],
          "notes": {"tA": {"x": 0, "y": -280, "text": "A. TEAM and ALLIANCE tabs in the header", "kind": "title1", "maxW": 2280},
                    "tB": {"x": 0, "y": 1360, "text": "B. One ALLIANCE key on the team bar", "kind": "title1", "maxW": 1580},
                    "tC": {"x": 0, "y": 3000, "text": "C. A wider screen with the alliance always on the right", "kind": "title1", "maxW": 2060},
                    "tX": {"x": 0, "y": 4640, "text": "Every option: the break and the invite", "kind": "title1", "maxW": 2060}},
          "designSystems": []}
json.dump(canvas, open(os.path.join(OUT, "canvas.json"), "w"))
print("ok")
