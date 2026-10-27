#!/usr/bin/env python3
"""Generates the Totem Forge's part blocks (Totem Forge brief, task 3): cube textures and models for the controller,
Metal Intake, Forge Hearth, Melt Tank, Pour Head and Forge Cap, their item models, loot tables, the pickaxe tag,
the crafting recipes the owner accepted (tools/preview/totem_forge_concepts.html, section 4) and the lang lines.

  python3 tools/gen_totem_forge_assets.py

The cubes are what a part looks like on its own and while the forge is being built; the formed forge (look A,
"Hearth and Crucible") is drawn by the controller's renderer in task 4. Colours follow the mock-up: dark hull,
grey trim, the controller's turquoise screen, blue item ring, orange hearth vents and gold power socket.
"""
import json

from gen_magnatite_assets import add_tag, shaped, write
from gen_material_assets import ASSETS, ROOT, hsh, write_png

DATA = ROOT / "src/main/resources/data"
LANG = ASSETS / "lang/en_us.json"

HULL, HULL2, TRIM, MID, DARK = (0x1e, 0x22, 0x2b), (0x2b, 0x31, 0x3d), (0x8a, 0x90, 0x9c), (0x56, 0x5c, 0x6b), (0x10, 0x14, 0x18)
TQ, BLUE, ORANGE, GOLD, HOT, RED, LAMP = (0x19, 0xd3, 0xb0), (0x3a, 0x8b, 0xff), (0xff, 0x7a, 0x3a), (0xff, 0xd2, 0x3a), (0xff, 0xb3, 0x47), (0xd8, 0x32, 0x2a), (0x8f, 0xe0, 0x4a)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def hull(x, y, seed):
    """A riveted panel: a lighter rim, a dark seam inside it, corner rivets and a faint brushed grain."""
    if x in (0, 15) or y in (0, 15):
        return shade(TRIM, .9 if (x + y) % 2 else .8)
    if x in (1, 14) or y in (1, 14):
        return HULL
    if (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        return MID
    return shade(HULL2, .92 + hsh(x, y // 2, seed) * .14)


def tex(name, px, seed=1):
    write_png(ASSETS / f"textures/block/{name}.png", 16, 16, lambda x, y: (*(px(x, y) or hull(x, y, seed)), 255))


def front(x, y):
    """The controller's panel: a screen, three lamps (mould, tanks, owner) and the red pour button."""
    if 4 <= x <= 11 and 3 <= y <= 7:
        return DARK if x in (4, 11) or y in (3, 7) else shade(TQ, .75 + .25 * ((x + y) % 3 == 0))
    if y == 10 and x in (4, 7, 10):
        return (LAMP, HOT, LAMP)[(x - 4) // 3]
    if 4 <= x <= 6 and 12 <= y <= 13:
        return RED
    if 9 <= x <= 11 and 12 <= y <= 13:
        return TRIM
    return None


def intake(x, y):
    """The item hatch, ringed in blue."""
    if 4 <= x <= 11 and 4 <= y <= 11:
        return BLUE if x in (4, 11) or y in (4, 11) else DARK if (x + y) % 4 else shade(DARK, 1.6)
    return None


def hearth(x, y):
    """Three glowing vents over the gold power socket."""
    if 3 <= x <= 12 and y in (3, 5, 7):
        return shade(ORANGE, .8 + .2 * (x % 2))
    if 5 <= x <= 10 and 10 <= y <= 13:
        return GOLD if 6 <= x <= 9 and 11 <= y <= 12 else shade((0xc8, 0x7a, 0x3c), .9)
    return None


def tank(x, y):
    """A window onto molten metal, two thirds full."""
    if 3 <= x <= 12 and 3 <= y <= 12:
        if x in (3, 12) or y in (3, 12):
            return DARK
        return shade(HOT, .85 + .15 * ((x * 3 + y) % 4 == 0)) if y >= 7 else (0x18, 0x1c, 0x24)
    return None


def nozzle(x, y):
    """The Pour Head's underside: the nozzle in a ceramic collar."""
    d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** .5
    return HOT if d < 1.6 else (0xc9, 0xce, 0xd8) if d < 3.6 else MID if d < 4.4 else None


def cap(x, y):
    """The Forge Cap's top: three vent grilles and the chimney collar."""
    if 3 <= x <= 12 and y in (3, 4, 6, 7, 9, 10) and x % 2:
        return DARK
    d = ((x - 11.5) ** 2 + (y - 12.5) ** 2) ** .5
    return HULL if d < 1.6 else TRIM if d < 2.6 else None


BLOCKS = {
    # id: (name, model textures)
    "totem_forge": ("Totem Forge", {"parent": "minecraft:block/orientable", "textures": {"front": "totem_forge_front", "side": "forge_hull", "top": "forge_hull"}}),
    "metal_intake": ("Metal Intake", {"parent": "minecraft:block/cube_column", "textures": {"side": "metal_intake_side", "end": "forge_hull"}}),
    "forge_hearth": ("Forge Hearth", {"parent": "minecraft:block/cube_column", "textures": {"side": "forge_hearth_side", "end": "forge_hull"}}),
    "melt_tank": ("Melt Tank", {"parent": "minecraft:block/cube_column", "textures": {"side": "melt_tank_side", "end": "forge_hull"}}),
    "pour_head": ("Pour Head", {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": "forge_hull", "top": "forge_hull", "bottom": "pour_head_bottom"}}),
    "forge_cap": ("Forge Cap", {"parent": "minecraft:block/cube_bottom_top", "textures": {"side": "forge_hull", "top": "forge_cap_top", "bottom": "forge_hull"}}),
}

T, SB, II, IN, IP, MI, CM, MC, CTC, CRC = ("bsp_core:tetrium_plate", "bsp_core:slag_brick", "bsp_core:illyrium_ingot", "bsp_core:illyrium_nugget", "bsp_core:illyrium_processor",
                                           "bsp_core:magnatite_ingot", "bsp_core:charged_magnatite_ingot", "bsp_core:magnet_core", "bsp_core:copper_tetrium_coil", "bsp_core:charged_resonance_crystal")
RECIPES = {
    "forge_hearth": (["TST", "SBS", "TST"], {"T": T, "S": SB, "B": "minecraft:blast_furnace"}),
    "totem_forge": (["IPI", "CHC", "TMT"], {"I": II, "P": IP, "C": CM, "H": "bsp_core:forge_hearth", "T": T, "M": MC}),
    "metal_intake": (["THT", "SCS", "TKT"], {"T": T, "H": "minecraft:hopper", "S": SB, "C": "minecraft:cauldron", "K": CTC}),
    "melt_tank": (["MGM", "GCG", "MLM"], {"M": MI, "G": "bsp_core:illyrium_glass", "C": "minecraft:cauldron", "L": "minecraft:magma_block"}),
    "pour_head": (["TIT", "MDM", " N "], {"T": T, "I": II, "M": MI, "D": "minecraft:dispenser", "N": IN}),
    "forge_cap": (["TTT", "TRT", "SSS"], {"T": T, "R": CRC, "S": SB}),
}

LANG_LINES = {
    "hint.bsp_core.totem_forge_input": "Ingots for the totem's metal",
    "message.bsp_core.multiblock.clear": "Clear the %s at %s %s %s: that space must stay empty.",
    "guide.bsp_core.totem_forge.0.metal_intake": "The Metal Intake goes behind the controller. Ingots and molten metal go in here.",
    "guide.bsp_core.totem_forge.0.forge_hearth": "A Forge Hearth on the right of the controller and of the intake. The hearths burn lava and take RF.",
    "guide.bsp_core.totem_forge.2.melt_tank": "Leave the middle layer empty: the mould stands there. Two Melt Tanks go on top, on the left.",
    "guide.bsp_core.totem_forge.2.pour_head": "The Pour Head goes on top at the front right.",
    "guide.bsp_core.totem_forge.2.forge_cap": "The Forge Cap closes the back right corner.",
    "message.bsp_core.totem_forge.placed": "%s in the mould (%s of 8).",
    "message.bsp_core.totem_forge.next": "The mould takes the %s next.",
    "message.bsp_core.totem_forge.complete": "The mould is complete.",
    "message.bsp_core.totem_forge.cracked": "The %s has no pours left: it has cracked.",
    "message.bsp_core.totem_forge.poured": "The metal is in the mould: it stays until the totem has set.",
    "message.bsp_core.totem_forge.took": "Took the %s out of the mould.",
    "need.bsp_core.totem_forge_metal": "Ingots for the totem's metal",
    "need.bsp_core.totem_forge_mould": "The mould: %s next",
    "gui.bsp_core.totem_forge.pour": "POUR",
    "message.bsp_core.totem_forge.pour.checking": "Checking the network for a pour of yours elsewhere...",
    "message.bsp_core.totem_forge.pour.no_answer": "The network did not answer, so nothing was poured. Try again in a moment.",
    "message.bsp_core.totem_forge.pour.free": "The pour has begun. You own no totem, so it is free, and the totem sets in %s days.",
    "message.bsp_core.totem_forge.pour.paid": "Paid the worth of %s %s (any change is in your inventory). The pour has begun, and the totem sets in %s days.",
    "message.bsp_core.totem_forge.pour.mould": "The mould is not complete: it takes the %s next.",
    "message.bsp_core.totem_forge.pour.metal": "The tanks are not full yet.",
    "message.bsp_core.totem_forge.pour.busy": "This forge already has a totem pouring or setting.",
    "message.bsp_core.totem_forge.pour.already": "You already have a totem pouring or setting in another forge. One at a time.",
    "message.bsp_core.totem_forge.pour.coins": "You own %s totem(s): starting a pour costs %s %s, or the same worth in other Shatter Coins. Carry them and press POUR again.",
    "message.bsp_core.totem_forge.pour.changed": "Counting the totems on their way to you, you own %s: the pour costs %s %s now. Press POUR again to pay it.",
    "item.bsp_core.forged_shatter_totem": "Forged Shatter Totem",
    "jei.bsp_core.info.totem_forge": "The Totem Forge pours a new Shatter Totem. Build it 2 by 2 by 3: on the bottom the Totem Forge, a Metal Intake behind it and a Forge Hearth on the right of each; leave the middle layer empty; on top two Melt Tanks on the left, the Pour Head at the front right and the Forge Cap behind it. Ingots of the metal bill melt into its tanks, lava and RF go into the hearths. Put the mould in the hollow, then press POUR.",
    "jei.bsp_core.info.totem_mould": "Goes into the Totem Forge's hollow, in order: Base, Foot, Foot, Torso, Arm, Arm, Head, then a Totem Core. Each piece lasts three pours.",
    "jei.bsp_core.info.totem_core": "The last piece of the Totem Forge's mould. It becomes the forged totem's orb, so every pour uses one.",
    "jei.bsp_core.info.molten": "Melted from Tetrium, Magnatite or Illyrium in a Tinkers' smeltery or a Thermal Magma Crucible, at the same yield as the BSP machines. Pipe it into a Totem Forge's Metal Intake, or cast it back into nuggets and ingots.",
    "gui.bsp_core.totem_forge.take_piece": "TAKE PIECE OUT",
    "gui.bsp_core.totem_forge.take_totem": "TAKE TOTEM",
    "gui.bsp_core.totem_forge.steal": "STEAL",
    "gui.bsp_core.totem_forge.pouring": "POURING",
    "gui.bsp_core.totem_forge.setting": "SETTING",
    "gui.bsp_core.totem_forge.pay": "PAY %s AND POUR",
    "gui.bsp_core.totem_forge.pay_tip": "Costs %s %s, or the same worth in other coins, for the %s totem(s) you own.",
    "gui.bsp_core.totem_forge.state.unformed": "unformed",
    "gui.bsp_core.totem_forge.state.melting": "melting",
    "gui.bsp_core.totem_forge.state.ready": "ready",
    "gui.bsp_core.totem_forge.state.pouring": "pouring",
    "gui.bsp_core.totem_forge.state.setting": "setting",
    "gui.bsp_core.totem_forge.state.set": "set",
    "gui.bsp_core.totem_forge.mould": "MOULD (%s OF 8)",
    "gui.bsp_core.totem_forge.metal": "METAL",
    "gui.bsp_core.totem_forge.slot.ingots": "INGOTS",
    "gui.bsp_core.totem_forge.slot.lava": "LAVA",
    "gui.bsp_core.totem_forge.slot.rf": "RF",
    "gui.bsp_core.totem_forge.lava": "LAVA %s mB",
    "gui.bsp_core.totem_forge.rf": "RF buffer: %s%%",
    "gui.bsp_core.totem_forge.empty_place": "Empty: the %s goes here",
    "gui.bsp_core.totem_forge.tube": "%s: %s of %s ingots (%s of %s mB)",
    "gui.bsp_core.totem_forge.short": "%s %s of %s",
    "gui.bsp_core.totem_forge.msg.waiting": "Waiting for metal: %s.",
    "gui.bsp_core.totem_forge.msg.ready": "Ready to pour.",
    "gui.bsp_core.totem_forge.msg.free": "You own no Shatter Totem. The pour is free, and the totem sets in %s days.",
    "gui.bsp_core.totem_forge.msg.paid": "You own %s totem(s). The pour costs %s %s (or their worth in other coins), and the totem sets in %s days.",
    "gui.bsp_core.totem_forge.msg.pouring": "Pouring: %s%%, for %s.",
    "gui.bsp_core.totem_forge.msg.setting": "Setting: %s d %s h %s min left of %s days. Poured by %s.",
    "gui.bsp_core.totem_forge.msg.set_mine": "Your totem has set. Take it out: it is yours.",
    "gui.bsp_core.totem_forge.msg.set_other": "Set, poured by %s. Taking it is a steal: stay by the forge for %s seconds.",
    "message.bsp_core.totem_forge.set": "Your forged totem has set in the Totem Forge at %s %s %s. Take it out before someone steals it.",
    "message.bsp_core.totem_forge.took_totem": "You take the Forged Shatter Totem out of the forge. It is yours.",
    "message.bsp_core.totem_forge.steal.started": "Stealing the forged totem: stay within %2$s blocks of the forge for %1$s seconds.",
    "message.bsp_core.totem_forge.steal.left": "Stealing the forged totem: %2$s s left. Stay close.",
    "message.bsp_core.totem_forge.steal.too_far": "Stand within %s blocks of the forge to steal the totem.",
    "message.bsp_core.totem_forge.steal.creative": "Creative and spectator players cannot steal a forged totem.",
    "message.bsp_core.totem_forge.steal.broken": "The Totem Forge was broken. The steal has failed.",
    "message.bsp_core.totem_forge.steal.busy": "%s is already stealing this totem (%s s left).",
    "message.bsp_core.totem_forge.steal.failed": "The steal of the forged totem has failed.",
    "message.bsp_core.totem_forge.steal.taken_back": "%s took the totem out first. The steal has failed.",
    "message.bsp_core.totem_forge.steal.warning": "%s is stealing the forged totem from your Totem Forge at %s %s %s!",
    "message.bsp_core.totem_forge.steal.lost": "%s stole the forged totem from your Totem Forge at %s %s %s.",
}


def main():
    tex("forge_hull", lambda x, y: None, 3)
    tex("totem_forge_front", front, 4)
    tex("metal_intake_side", intake, 5)
    tex("forge_hearth_side", hearth, 6)
    tex("melt_tank_side", tank, 7)
    tex("pour_head_bottom", nozzle, 8)
    tex("forge_cap_top", cap, 9)
    lang = json.loads(LANG.read_text())
    for bid, (name, model) in BLOCKS.items():
        model = {"parent": model["parent"], "textures": {k: f"bsp_core:block/{v}" for k, v in model["textures"].items()}}
        model["textures"]["particle"] = "bsp_core:block/forge_hull"
        write(ASSETS / f"models/block/{bid}.json", model)
        write(ASSETS / f"models/item/{bid}.json", {"parent": f"bsp_core:block/{bid}"})
        if bid == "totem_forge":
            variants = {f"facing={f}": {"model": "bsp_core:block/totem_forge", **({"y": r} if r else {})} for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}
        else:
            variants = {"": {"model": f"bsp_core:block/{bid}"}}
        write(ASSETS / f"blockstates/{bid}.json", {"variants": variants})
        write(DATA / f"bsp_core/loot_tables/blocks/{bid}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"bsp_core:{bid}"}],
                                                                                                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        lang[f"block.bsp_core.{bid}"] = name
        rows, key = RECIPES[bid]
        shaped(bid, rows, key, f"bsp_core:{bid}")
    add_tag(DATA / "minecraft/tags/blocks/mineable/pickaxe.json", [f"bsp_core:{b}" for b in BLOCKS])
    lang.update(LANG_LINES)
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print("totem forge part assets written")


if __name__ == "__main__":
    main()
