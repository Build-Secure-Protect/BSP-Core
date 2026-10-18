#!/usr/bin/env python3
"""Writes the wording of the Plasma Accelerator and Decelerator (plasma between worlds) into en_us.json.

  python3 tools/gen_link_lang.py

Edit here and rerun.
"""
import json
from pathlib import Path

LANG = Path(__file__).resolve().parent.parent / "src/main/resources/assets/bsp_core/lang/en_us.json"
KEYS = {
    "block.bsp_core.accelerator_intake": "Accelerator Intake",
    "block.bsp_core.accelerator_frame": "Accelerator Frame",
    "block.bsp_core.decelerator_core": "Decelerator Core",
    "block.bsp_core.decelerator_port": "Decelerator Port",
    "tooltip.bsp_core.accelerator_intake": "The bottom middle of a Plasma Accelerator: seven Accelerator Frames stand round it in a ring, the middle left empty. Plasma cables feed it, and it sends what arrives to the Decelerators it is linked to, in any world on this server.",
    "tooltip.bsp_core.accelerator_frame": "Seven of these round an Accelerator Intake, standing up with the middle empty, make the ring of a Plasma Accelerator. RF goes in through any frame.",
    "tooltip.bsp_core.decelerator_core": "The front of a Plasma Decelerator. Place a Decelerator Port directly behind it. Right-click it with a written Link Card to receive plasma from that Accelerator.",
    "tooltip.bsp_core.decelerator_port": "The back of a Plasma Decelerator: the plasma that arrives in the core leaves through this block into the cables on its free faces.",
    "jade.bsp_core.accelerator_unformed": "Ring unfinished: %s frame(s) missing",
    "jade.bsp_core.accelerator_hole": "Ring unfinished: the middle must be empty",
    "jade.bsp_core.decelerator_unformed": "No Decelerator Port behind it",
    "jade.bsp_core.link_formed": "Formed",
    "jade.bsp_core.accelerator": "Links %s of %s: taking %s mB/t, sending %s mB/t",
    "jade.bsp_core.link_rf": "RF %s / %s",
    "jade.bsp_core.accelerator_frame": "Part of a Plasma Accelerator",
    "jade.bsp_core.accelerator_frame_loose": "Not part of a ring yet",
    "jade.bsp_core.decelerator_linked": "Linked to %s: %s mB/t arriving, %s mB/t sent on",
    "jade.bsp_core.decelerator_unlinked": "No Link Card in it",
    "jade.bsp_core.decelerator_port": "Sending %s mB/t into its cables",
    "jade.bsp_core.decelerator_port_idle": "Nothing to send",
    "jade.bsp_core.decelerator_port_loose": "No Decelerator Core in front of it",
    "jei.bsp_core.info.accelerator": "The Plasma Accelerator sends Wave Plasma to another world on this server. Build it as a ring: an Accelerator Intake at the bottom middle, seven Accelerator Frames round it standing up, the middle empty. Run a plasma cable into the intake and RF into any frame. Right-click the intake with a blank Link Card to write it, then right-click a Decelerator Core anywhere with the card. One link to start; each Link Expander on the intake adds one, two at most. The plasma arrives with every power it carried; the accelerator burns 1,000 RF/t while sending.",
    "jei.bsp_core.info.decelerator": "The Plasma Decelerator receives what an Accelerator sends. A Decelerator Core with a Decelerator Port directly behind it; RF into the core, cables out of the port's free faces. Right-click the core with a written Link Card. It burns 100 RF/t while receiving. A far side whose chunk is not loaded sleeps; the accelerator wakes it for a moment once a minute, so a Projector with Anchor behind it keeps its own chunks.",
    # the Worlds board (task 5)
    "gui.bsp_core.accelerator.status": "Taking %s mB/t, sending %s mB/t, RF %s / %s",
    "gui.bsp_core.accelerator.links": "%s of %s links",
    "gui.bsp_core.accelerator.unformed": "Ring unfinished",
    "gui.bsp_core.accelerator.here": "this accelerator",
    "gui.bsp_core.accelerator.rate": "%s mB/t",
    "gui.bsp_core.accelerator.carries": "Carries %s",
    "gui.bsp_core.accelerator.carries_none": "Carries no powers yet",
    "gui.bsp_core.accelerator.hint": "Click a link, then UNLINK takes its card out at the far end. WAKE loads every sleeping far side for a moment.",
    "gui.bsp_core.accelerator.unlink": "UNLINK",
    "gui.bsp_core.accelerator.wake": "WAKE",
    "gui.bsp_core.accelerator.done": "DONE",
    # link cards and expanders (task 2)
    "item.bsp_core.link_card": "Link Card",
    "item.bsp_core.link_expander": "Link Expander",
    "tooltip.bsp_core.link_card.blank": "Blank. Right-click an Accelerator Intake to write that accelerator into it.",
    "tooltip.bsp_core.link_card.written": "Accelerator: %s",
    "tooltip.bsp_core.link_card.use": "Right-click a Decelerator Core to link it to that accelerator",
    "tooltip.bsp_core.link_expander": "Right-click an Accelerator Intake to fit it: one more link. An accelerator starts with %s link(s) and takes %s expanders.",
    "dimension.bsp_core.overworld": "Overworld",
    "dimension.bsp_core.the_nether": "The Nether",
    "dimension.bsp_core.the_end": "The End",
    "message.bsp_core.link.where": "%s %s, %s, %s",
    "message.bsp_core.link.card_written": "Link Card written: %s",
    "message.bsp_core.link.card_written_already": "This card already names %s",
    "message.bsp_core.link.no_access": "Only the users of the totem feeding this accelerator may write a Link Card here",
    "message.bsp_core.link.expander_fitted": "Link Expander fitted (%s of %s): %s link(s) now",
    "message.bsp_core.link.expander_full": "It already holds %s Link Expanders",
    "message.bsp_core.link.expander_taken": "Link Expander taken out (%s left): %s link(s) now",
    "message.bsp_core.link.expander_in_use": "Every link is in use: unlink a Decelerator first",
    "message.bsp_core.link.expander_none": "No Link Expander fitted",
    "message.bsp_core.link.intake_status": "Plasma Accelerator: taking %s mB/t, sending %s mB/t, %s of %s links, RF %s / %s",
    "message.bsp_core.link.intake_unformed": "Ring unfinished: %s frame(s) missing or the middle is blocked",
    "message.bsp_core.link.plug.linked": "Linked to the accelerator at %s",
    "message.bsp_core.link.plug.already": "This core is already linked: sneak and right-click it empty-handed to take the card out",
    "message.bsp_core.link.plug.full": "That accelerator has no free link",
    "message.bsp_core.link.plug.no_accelerator": "No Accelerator Intake stands where the card points",
    "message.bsp_core.link.plug.no_world": "The card points to a world this server does not have",
    "message.bsp_core.link.plug.blank": "Write the card at an Accelerator Intake first",
    "message.bsp_core.link.plug.blacklisted": "Plasma links are not allowed in that world",
    "message.bsp_core.link.plug.self": "A core cannot link to itself",
    "message.bsp_core.link.no_card": "No Link Card in it",
    "message.bsp_core.link.unlinked": "Unlinked: the Link Card is back with you",
    "message.bsp_core.link.core_status": "Plasma Decelerator, linked to %s: %s mB/t arriving, %s mB/t sent on, RF %s / %s",
    "gui.bsp_core.link.state.delivering": "Delivering",
    "gui.bsp_core.link.state.idle": "Nothing to send",
    "gui.bsp_core.link.state.no_rf_here": "No RF here",
    "gui.bsp_core.link.state.unformed_here": "Ring unfinished",
    "gui.bsp_core.link.state.asleep": "Asleep: far side unloaded",
    "gui.bsp_core.link.state.no_rf_there": "No RF there",
    "gui.bsp_core.link.state.unformed_there": "No port behind that core",
    "gui.bsp_core.link.state.full_there": "Full there",
    "gui.bsp_core.link.state.missing": "Gone",
    "gui.bsp_core.link.state.no_world": "No such world",
    "message.bsp_core.link.core_status_unformed": "Linked to %s, but no Decelerator Port stands behind it",
    "message.bsp_core.link.core_unlinked": "Plasma Decelerator: no link. Right-click it with a written Link Card.",
    "message.bsp_core.link.core_unformed": "No Decelerator Port behind it, and no link",
}
lang = json.loads(LANG.read_text())
lang.update(KEYS)
LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n")
print(f"wrote {len(KEYS)} accelerator lines")
