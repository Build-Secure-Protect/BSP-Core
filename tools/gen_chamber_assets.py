#!/usr/bin/env python3
"""Writes the Plasma Tank Mob Chamber's assets and wording: the Wither Bone item (texture, model), the Spawn Grate's name (its model is
in gen_tank_assets.py with the other tank blocks), the names of the thirteen mob powers and what each does, into en_us.json; and the
"plasma" damage type a chamber's mobs die of (it goes through armour).

  python3 tools/gen_chamber_assets.py
"""
import json

from gen_material_assets import ASSETS, CLEAR, hexc, item_model, write_png

LANG = ASSETS / "lang/en_us.json"
DATA = ASSETS.parent.parent / "data"

# The Wither Bone, design "A. Charred" (tools/preview/mob_chamber_concepts.html): a bone burned black in the plasma, ash-grey knuckles.
BONE = [
    "................",
    "............##..",
    "...........#++#.",
    "...........#+++#",
    "..........#+++#.",
    ".........#+#.#..",
    "........#+#.....",
    ".......#+#......",
    "......#+#.......",
    ".....#+#........",
    "..#.#+#.........",
    ".#+++#..........",
    "#+++#...........",
    ".#++#...........",
    "..##............",
    "................",
]
CHARRED = {"#": hexc("#141414"), "+": hexc("#3a3a3a")}


def wither_bone(x, y):
    return CHARRED.get(BONE[y][x], CLEAR)


# Name, then what it does in a Projector's reach. Team: the owner, their team, Ward access, allies with the perk. Outsiders: everyone else.
POWERS = {
    "growth": ("Growth", "Crops, saplings and cane grow faster"),
    "wither": ("Wither", "Outsiders wither"),
    "warp_ward": ("Warp Ward", "Outsiders cannot teleport in or out by pearl or chorus fruit"),
    "undead_ward": ("Undead Ward", "Zombies and skeletons do not spawn"),
    "haste": ("Haste", "The team mines faster"),
    "snare": ("Snare", "Outsiders move slowly"),
    "wireframe": ("Wireframe", "Outsiders show as wireframes to the owner"),
    "keep": ("Keep", "Dropped items last 20 minutes"),
    "scorch": ("Scorch", "Outsiders catch fire every second"),
    "fire_ward": ("Fire Ward", "Fire and lava do not hurt the team"),
    "mending": ("Mending", "The team's held tools slowly mend"),
    "lantern": ("Lantern", "No hostile mobs spawn"),
    "tide": ("Tide", "The team breathes under water"),
}

KEYS = {
    "item.bsp_core.wither_bone": "Wither Bone",
    "block.bsp_core.spawn_grate": "Spawn Grate",
    "block.bsp_core.tank_extractor": "Tank Extractor",
    "gui.bsp_core.pool.plain": "Plain plasma",
    "gui.bsp_core.pool.totem": "Totem powers",
    "message.bsp_core.extractor.pool": "Sending: %s (%s mB)",
    "message.bsp_core.extractor.no_tank": "Set it against a Tank Port",
    "message.bsp_core.warp_ward.out": "A Warp Ward holds you here",
    "message.bsp_core.warp_ward.in": "A Warp Ward keeps you out",
    # the tank screen's CHAMBER tab
    "gui.bsp_core.tank.tab.tank": "TANK",
    "gui.bsp_core.tank.tab.chamber": "CHAMBER",
    "gui.bsp_core.tank.chamber.mark": "MOBS SPAWN",
    "gui.bsp_core.tank.chamber.spawns": "SPAWNS HERE \u00b7 %s",
    "gui.bsp_core.tank.chamber.mode.all": "ALL",
    "gui.bsp_core.tank.chamber.mode.hostile": "HOSTILE",
    "gui.bsp_core.tank.chamber.mode.passive": "PASSIVE",
    "gui.bsp_core.tank.chamber.status.low": "Mobs spawn from %s%% full",
    "gui.bsp_core.tank.chamber.status.nothing": "Nothing here can spawn in this tank",
    "gui.bsp_core.tank.chamber.status.server_full": "Server limit of chamber mobs reached",
    "gui.bsp_core.tree.kind.mob": "Mob Chamber power, from a Projector",
    "gui.bsp_core.tank.chamber.status.full": "Full: %s of %s alive",
    "gui.bsp_core.tank.chamber.status.next": "Next in %s s \u00b7 %s of %s alive",
    "gui.bsp_core.tank.chamber.loot_only": "loot only",
    "gui.bsp_core.tank.chamber.more": "+%s more",
    "gui.bsp_core.tank.chamber.loot": "LOOT",
    "gui.bsp_core.tank.chamber.loot_hint": "out through a port",
    "gui.bsp_core.tank.chamber.feed": "FEED",
    "gui.bsp_core.tank.chamber.feed_pick": "Click a drop with a power to feed it into the plasma. Click it again to stop.",
    "gui.bsp_core.tank.chamber.feed_have": "%s %s in the store",
    "gui.bsp_core.tank.chamber.feed_mb": "%s mB of %s %s an item",
    "gui.bsp_core.tank.chamber.feed_minutes": "about %s projector-minutes an item",
    "gui.bsp_core.tank.chamber.fits": "Mobs up to %s blocks tall fit",
    "gui.bsp_core.tank.chamber.stop": "STOP",
    # the recipe viewers' page per mob
    "jei.bsp_core.mob_chamber": "Mob Chamber",
    "emi.category.bsp_core.mob_chamber": "Mob Chamber",
    "jei.bsp_core.chamber.tank": "Tank %sx%sx%s or bigger",
    "jei.bsp_core.chamber.hostile": "Hostile, %s places",
    "jei.bsp_core.chamber.passive": "Passive, %s places",
    "jei.bsp_core.chamber.gives": "Gives %s",
    "jei.bsp_core.chamber.loot_only": "Loot only",
    "jei.bsp_core.chamber.no_drop": "No drops",
    "jei.bsp_core.chamber.spawns_in": "Spawns in a Mob Chamber in:",
    "jei.bsp_core.chamber.structure": "%s (structure)",
    "jei.bsp_core.chamber.more": "+%s more",
    "gui.bsp_core.tree.why.mob": "A Mob Chamber power, never on a totem",
    "death.attack.bsp_core.plasma": "%1$s burned up in plasma",
    "death.attack.bsp_core.plasma.player": "%1$s burned up in plasma whilst fighting %2$s",
}
for key, (name, effect) in POWERS.items():
    KEYS[f"buff.bsp_core.{key}"] = name
    KEYS[f"buff.bsp_core.{key}.effect"] = effect

if __name__ == "__main__":
    write_png(ASSETS / "textures/item/wither_bone.png", 16, 16, wither_bone)
    item_model("wither_bone")
    (DATA / "bsp_core/damage_type").mkdir(parents=True, exist_ok=True)
    (DATA / "bsp_core/damage_type/plasma.json").write_text(json.dumps({"exhaustion": 0.0, "message_id": "bsp_core.plasma", "scaling": "never"}, indent=2) + "\n")
    # what Growth makes grow, and what Undead Ward keeps from spawning: tags, so a pack can add its own crops and zombies
    for path, values in ((DATA / "bsp_core/tags/blocks/growth.json", ["#minecraft:crops", "#minecraft:saplings", "minecraft:sugar_cane", "minecraft:cactus",
                                                                          "minecraft:bamboo", "minecraft:bamboo_sapling", "minecraft:nether_wart", "minecraft:cocoa",
                                                                          "minecraft:melon_stem", "minecraft:pumpkin_stem", "minecraft:sweet_berry_bush", "minecraft:kelp",
                                                                          "minecraft:cave_vines", "minecraft:cave_vines_plant"]),
                         (DATA / "bsp_core/tags/entity_types/undead_ward.json", ["minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
                                                                                   "minecraft:zombified_piglin", "#minecraft:skeletons"])):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({"replace": False, "values": values}, indent=2) + "\n")
    tag = DATA / "minecraft/tags/damage_type/bypasses_armor.json"
    tag.parent.mkdir(parents=True, exist_ok=True)
    data = json.loads(tag.read_text()) if tag.exists() else {"replace": False, "values": []}
    if "bsp_core:plasma" not in data["values"]:
        data["values"].append("bsp_core:plasma")
    tag.write_text(json.dumps(data, indent=2) + "\n")
    lang = json.loads(LANG.read_text())
    lang.update(KEYS)
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
    print(f"wrote the Wither Bone and {len(KEYS)} lines")
