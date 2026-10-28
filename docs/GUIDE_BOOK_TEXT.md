# BSP Field Guide: text for review

Every page of the in-game guide book, in the order it appears. Reword anything you like and send the file back:
the changes are then copied into `tools/gen_guide_book.py`, which builds the book. Keep each page short: a book page
holds roughly 100 words, or about 60 next to a recipe or picture.

*Italics* are item names (shown in colour in the book) and **bold** is bold. Lines marked `[recipe]`, `[picture]` or
`[3D structure]` show what else is on that page and are not text you need to edit.

---

# The Shatter Totem

Your totem is the heart of BSP: build around it, protect it, and take other players' totems if you can.

## Your Shatter Totem

**Page 1**

Every player is given one *Shatter Totem* the first time they join. It cannot be destroyed, and the only way to make another is to pour one in a Totem Forge.

Carry it, or place it in your base. A placed totem is where your upgrades and auras work from, so most players build their base around it.

**Page 2: Keeping it**

Only one totem is ever yours by right, but you can hold others: any totem you steal becomes yours.

If you die to another player while carrying your totem, it is placed where you fell. A totem dropped on the ground places itself after a short while.

**Page 3: Logging out**

A totem never goes offline with you. When you log out, every totem you carry is placed where you stood, so it can still be stolen; if it is still there, untouched, when you come back, it returns to you.

## Stealing and Defending

**Page 1**

Right-click another player's placed totem and choose to steal it. A timer starts, and the owner is warned.

Stay close to the totem until the timer runs out and it is yours, with every upgrade on it.

**Page 2: Stopping a thief**

The steal is cancelled if the thief is killed, or stays too far from the totem for too long.

So defend in person, and make the room around your totem hard to stand in. The Fortify upgrade helps your walls survive explosions.

## The Upgrade Tree

**Page 1**

Right-click with the totem in your hand, or right-click your placed totem, to open its upgrade tree. Upgrades belong to the totem, so they move with it if it is stolen.

Your totem has five tiers, I to V. Each tier has its own Shatter Coin: Copper, Gold, Diamond, Netherite, Illyrium.

**Page 2: Tiers and paths**

Raising the totem a tier costs coins of the tier you are leaving, plus XP levels. A new tier opens new upgrades and two more levels on the ones you have.

Upgrades sit on paths. The next one opens when the one before it reaches level 2. Every level costs the coin of the tier it is bought at, plus XP.

**Page 3: Carried**

Work while the totem is in your **offhand**. Paid in experience levels.


- Damage, Resistance, Mining Speed
- Swiftness: move faster
- Vitality: extra hearts
- Featherfall: less fall damage
- Night Sight: see in the dark
- Bouncy: bounce back up from a fall
- X-ray: see ores through the walls for a while
- Extra Homes, Swift Travel: more homes and quicker teleports with the pack's teleport mod

Bouncy and X-ray are described under Powers in Detail.

**Page 4: Base: walls**

Work only while the totem is **placed**. Paid in Shatter Coins.


- Fortify: blocks nearby resist explosions and intruders mine slower
- Alarm: outlines intruders and warns you
- Sentinel: tells you who is inside, from anywhere
- Herald: calls you on Discord
- Ward: weakens intruders
- Deadlock: your totem takes longer to steal
- Cloaking: outsiders see untouched land

**Page 5: Base: aura and plasma**

- Healing Aura: heals you nearby
- Sanctuary: no hostile spawns
- Overclock: every injector fed by this totem's plasma speeds its machine up more
- Recall: teleport to the totem while it is being stolen
- Output: more Wave Plasma
- Anchor and Survey: keep chunks loaded, see Chunk Loading
- Harvest: mined ores grow back

**Page 6: Raid**

Help you take other totems. They count when the totem that has them is in your offhand as you start a steal. Paid in Shatter Coins.


- Lockpick: your steals take less time
- Shroud: the owner is warned late
- Recall Block: delays the owner's Recall
- Thief's Step: silent steps, hidden from weak Alarms
- Siege: mine at full speed inside Fortify for a while

No upgrade can make a totem impossible to steal: blocks can always be mined, and a steal always has a time limit.

## The Totem Compass

**Page 1**  `[item shown] totem_compass`

Points to your own nearest totem. Load it with Shatter Coins and start tracking, and for a while it points to the nearest rival totem instead.

After tracking it needs to cool down. Upgrades shorten the cooldown.

**Page 2**  `[recipe] totem_compass`

## Scores and Seasons

**Page 1**

Every totem you own scores points by its tier:


- Tier I: 1 point
- Tier II: 2 points
- Tier III: 4 points
- Tier IV: 7 points
- Tier V: 10 points

Your score is the total for all your totems, so stealing totems and upgrading them both move you up.

**Page 2: Seasons and Prizes**

The server runs in seasons. When a season ends, the top three players receive that season's prizes, every totem is removed, and every player gets one fresh Tier I totem.

The screens the admins put up show what first, second and third place will win. If you are offline when the season ends, your prizes and your new totem are waiting when you next log in.

**Page 3: How it is counted**

A placed totem counts for its owner. A carried totem counts for whoever is carrying it.

Ties go to the player with more totems, then to the higher tier. The leaderboard screens at spawn show the standings, and on a network they cover every server. A screen can also show other boards, such as which servers players call home, and turn through its pages.

## Decoy Totems

**Page 1**  `[item shown] decoy_totem`

A fake totem. While it has power, rival Totem Compasses within its range point at it instead of a real totem, and everyone but you sees an ordinary Shatter Totem. You see the idol with a ghost of the totem around it.

You may have five placed.

**Page 2**  `[recipe] magnet_core, decoy_totem`

The *Magnet Core* is the hard part: six Charged Magnatite Ingots around an Illyrium Processor.

**Page 3**  `[recipe] decoy_power_base`

A decoy must stand on a *Decoy Power Base*, fed with RF from its sides or from below. It draws 100 RF every tick. Without power the disguise drops and everyone sees the bare idol.

**Page 4: Taking the bait**

An enemy who tries to steal a decoy finds out it is fake. Its traps go off and it takes a hit. When it has taken more hits than it has casings it breaks, and does nothing until you repair it with Magnatite Ingots.

Only you can mine a working decoy. Anyone can mine a broken or unpowered one.

**Page 5: Sockets**

Right-click your decoy to fit parts. A socket opens when the one before it is filled.


- **Range**: Range Coils Mk I, II, III widen the compass range from 24 blocks to 40, 64 and 96.
- **Traps**: a charge, then a Trap Amplifier, then a second charge.
- **Casing**: each Reinforced Casing survives one more attempt.

**Page 6: Trap charges**

Each charge is used up when it goes off.


- Blast: an explosion that hurts the thief and breaks nothing.
- Hex: Slowness, Weakness and Glowing.
- Poison.
- Fatigue: Mining Fatigue.
- Warp: throws the thief up to 30 blocks away.

**Page 7**  `[recipe] range_coil_mk1, range_coil_mk2`

**Page 8**  `[recipe] range_coil_mk3, trap_amplifier`

**Page 9**  `[recipe] blast_charge, warp_charge`

The other charges swap the middle item: Fermented Spider Eye, Spider Eye or Prismarine Shard.

**Page 10**  `[recipe] reinforced_casing`

## Plasma and Projectors

**Page 1**

A placed totem gives off **Wave Plasma**: 100 mB every tick, up to 6,000 with the ten levels of the **Output** upgrade. The totem panel shows the figure. Drawn out and piped to a *Projector*, it recreates the totem's base powers as a second aura somewhere else: over a mine, a farm, or a second wall.

The projector works for whoever owns the totem. If your totem is stolen, so is the aura.

**Page 2**  `[picture] The hook-up`

Totem on extractor, interfaces beside it, a run through a valve and a repeater to a base and its projector, and a second run into the back of a charger.

**Page 3: How it joins up**

The chain, in order:

- *Plasma Extractor* directly under the totem. More stack below; the flow is split between them.
- *Plasma Interface* touching an open face of an extractor. Cables plug into its outer faces.
- *Plasma Cable* from the interface onward.
- *Projector Base* at the end of the cable, holding what arrives.
- *Projector* standing on the base.

**Page 4**  `[recipe] plasma_extractor, plasma_interface`

Stack extractors under the totem to split the flow between several interfaces. An interface touching an extractor another group already serves is refused: its lit parts turn red and it does nothing.

**Page 5**  `[picture] Interfaces joined`

Touching interfaces become one body: the faces between them vanish and the frame runs round the outside. Up to twelve, any shape. A cable on any outer face is fed by the whole group, and each face sends up to 1,000 mB/t: a 6,000 mB/t totem needs six runs. The red one touches an extractor another group holds.

**Page 6: Seeing the flow**

Right-click any interface for its **flow view**: the group and every cable, valve, repeater, extractor, base and charger it reaches in 3D, labelled with the mB/t passing. Drag to turn, right-drag to pan, scroll to zoom.

Jade shows the same figures block by block: an extractor's draw, a cable's flow, a valve's limit, a base's tank.

**Page 7**  `[recipe] projector_base, totem_projector`

Cables plug into the base's four sides; the projector on top draws from it. A projector needs 100 mB/t arriving. Right-click it: POWERS lists what the interface offers with a RECEIVE switch each; CHUNKS is the chunk picker; STATUS shows the pressure.

**Page 8: The base's tank**

The base holds 5,000 mB and the projector burns 20 mB/t of it, so a base that was full keeps its projector going for about four minutes after a cable is cut. A base with nothing feeding it and no projector drains by itself.

A projector runs on plasma alone: no RF. With less than 100 mB/t arriving and a dry base it shows no aura.

**Page 9**  `[recipe] channel_expander`

A projector receives two powers at once, three with a *Channel Expander* fitted by right-clicking the projector with it; sneak and right-click empty-handed takes it out.

**Page 10: Powers and auras**

What a projector can receive: Fortify, Healing Aura, Alarm, Ward, Sanctuary, Cloaking, Recall and Anchor, at the highest level any totem on the interface has.

Auras are cubes: a power with radius 5 covers 11 by 11 by 11 blocks around the totem or projector, so you can measure and build to the edge.

## Runs and Valves

**Page 1**

Plasma moves in **runs**: a line of cables from an interface to whatever is at its end. Every cable in a run carries the same figure, the run's **pressure**, in mB/t. Nothing is lost along a cable, however long, up to its reach.

Each kind of cable has a longest run:

- Tetrium: 15 blocks, 250 mB/t
- Magnatite: 25 blocks, 500 mB/t
- Illyrium: 40 blocks, 1,000 mB/t
- Charged Illyrium: 80 blocks, 1,000 mB/t

A mixed run goes as far, and carries as much, as its weakest cable.

**Page 2**  `[recipe] tetrium_core_cable, magnatite_core_cable`

**Page 3**  `[recipe] illyrium_core_cable, charged_illyrium_core_cable`

**Page 4: Sharing**

An interface shares what its extractors give **equally** between the runs leaving it. A run that can take less than its share, because a valve caps it or its end is full, leaves the rest to the others.

So two bases on one totem get 50 mB/t each; cap one at 20 and the other gets 80. The cables leaving the interface always add up to the supply.

**Page 5**  `[recipe] plasma_repeater`

A *Plasma Repeater* ends one run and starts a fresh one, so the reach count begins again. Plasma goes in its dark back and out its lit front; placed pointing the way you look, the wrench turns it. It costs a tenth of the pressure, and a projector behind it receives one power at full level, or two or more each one level lower per repeater.

**Page 6**  `[recipe] plasma_valve`

A *Plasma Valve* sets the most that may pass it, from 0 to 1,000 mB/t. Right-click it and drag the dial, type a number, or use the buttons. A lever on it or any redstone signal shuts it, unless you switch redstone control off on its screen. Powers pass unchanged; it counts as one cable of reach and does not start a fresh run.

**Page 7: What you see**

The valve's hand wheel turns as far as the limit is set. Its windows show plasma arriving on one side and leaving on the other at the limited rate; the lamp by the gauge is blue while open and red while shut.

Each extractor also keeps a reserve of 4,000 mB. Take the totem away and the runs keep drinking from it for a short while, then stop.

**Page 8**  `[recipe] wrench`

The *Wrench* turns blocks in place: repeaters, valves, chargers, batteries and placed totems. Sneak and right-click turns the other way, or turns a repeater round.

**Page 9**  `[recipe] blue_illyrium_core_cable`

Magnatite, Illyrium and Charged Illyrium cables take any of the sixteen dyes: eight cables round a dye, and a coloured cable can be dyed again. A coloured cable joins only cables of its own colour, a plain one only plain ones, so two runs can cross without mixing. Machines take any colour.

**Page 10: Cable ends**

Point the wrench at an end of a cable and right-click to set what that end does:

- **Normal**: plasma flows either way.
- **Output**: plasma may only leave the cable here.
- **Input**: plasma may only enter here.
- **Off**: not joined.

Where another colour meets the end, the first click **Links** them. Sneak and right-click puts an end back to Normal.

**Page 11: What you see**

While you hold the wrench a line above the crosshair names the end you point at and its setting. Output ends wear a copper collar with an arrow pointing out, Input ends an arrow pointing in, Off ends a dark plate, and Links a copper and white collar.

Sneak and right-click the **core** of a cable to pick it up with its settings kept on the item.

## The Plasma Tank

**Page 1**

Wave Plasma can be kept in bulk. A *Plasma Tank* is a hollow box you build from three blocks: *Tank Casing* on every edge, *Tank Glass* or casing on the faces, and *Tank Ports* wherever a cable should meet it. Three to twelve blocks a side, nothing inside. It forms by itself when the last block goes in.

Every block of the shell holds 2,500,000 mB.

**Page 2**  `[picture] A 5 x 4 x 5 tank, formed`

Casing on the twelve edges, glass on the faces, a port on the top and one on a side. Once formed, the casing turns to glass with a lit outline along the edges and the uprights glow as high as the plasma stands. Any box from 3 x 3 x 3 up to 12 x 12 x 12 works the same way.

**Page 3**  `[recipe] tetrium_glass, tank_glass`

Tetrium Glass is ordinary glass round a Tetrium Nugget; Tank Glass is Tetrium Glass round a Tetrium Plate. A wall of Tank Glass draws as one sheet.

**Page 4**  `[recipe] tank_casing, tank_port`

A port takes plasma from a run that comes from an interface, and gives plasma to a run that only leads to bases, chargers or other tanks. Point the wrench at it to set Input or Output only. Each port gives up to 1,000 mB/t, like an interface face.

**Page 5: Seeing it**

Right-click any block of the tank: the screen shows the tank in 3D with its plasma at the level it holds, each port with its setting and what passes, and the first few cables out of each port. Jade reads the level on any block.

When the tank forms, a sweep of light runs over it and the casing turns to glass with a lit outline along the edges; the four uprights glow as high as the plasma stands, so the level can be read from outside. Plasma coming in through a port above the surface pours down to it; the level rises evenly through the whole tank.

**Page 6: Powers in the plasma**

Plasma fed in from a totem keeps that totem's powers: Overclock, Output and the rest. The tank's Output ports pass them on to injectors with every mB they give, with or without the totem, until that plasma is used up.

Plain plasma fed in mixes with it, and the powers fade as the mix runs down. The tank screen lists the powers the plasma carries.

**Page 7: Breaking and rebuilding**

Break one block by accident and the tank goes dormant but keeps its plasma: put the block back and it is whole again. Rebuild it smaller and it keeps what fits; bigger and it keeps what it had. Take the last block away and the plasma is gone.

## Chunk Loading

**Page 1**

A placed totem can keep the land around it running while you are away: machines work and crops grow as if you were standing there.

Two base upgrades on the totem's tree do it. **Anchor** sets how many chunks stay loaded. **Survey** sets how far from the totem they can be.

**Page 2: Anchor and Survey**

**Anchor**, from Tier II:

- Level 1: 1 chunk
- Level 2: 3 chunks
- Level 3: 6 chunks

**Survey**, from Tier III, once Anchor is level 2:

- Without it: pick within 3 by 3 chunks
- Level 1: 5 by 5
- Level 2: 7 by 7

The chunk the totem stands in is always loaded and counts as one.

**Page 3: Choosing chunks**

Once Anchor has a level, your totem's panel gains a **CHUNKS** tab. It shows a map of the ground around the totem, one square for each chunk, north at the top.

Click a square to load that chunk. Click it again to let it go. Turquoise squares are loaded from here, violet ones from a projector, dark ones are out of range.

**Page 4: Through a projector**

Receive **Anchor** on a Projector and it gets a chunk map of its own, centred on the projector.

A projector adds no chunks. It lets you spend whatever is left of the totem's chunks further away: click any chunks within range of the projector. The projector's own chunk is always one of them, so it keeps running and keeps drawing plasma. Its chunks stay loaded while plasma reaches it.

**Page 5: More than one totem**

Only your main totem, the one you have held longest, gets the full allowance. Every other totem you place loads the chunk it stands in and no more, and cannot take Anchor past level 1.

Lose your main totem and the next one you hold takes its place, with whatever upgrades it has.

**Page 6: Stolen and moved totems**

When a totem is stolen, every chunk chosen for it is let go, at the totem and at its projectors. The thief keeps the upgrades and chooses again.

If you pick your own totem up and place it somewhere else, it remembers the layout.

The server may be set to load chunks only while you are online.

## Batteries and Cells

**Page 1**

Wave Plasma can be bottled. A *Battery Charger* with a Plasma Cable into its back fills a *Plasma Battery* or *Power Cell* standing in it, and **stamps** powers from the interface into it, chosen on the charger's screen.

Every battery and cell is built around a *Charged Resonance Crystal*.

**Page 2: The Charged Crystal**  `[item shown] charged_resonance_crystal`

A Resonance Crystal magnetised in the Magnetic Centrifuge, with a Magnatite Nugget in the upgrade slot. Ten seconds a crystal, faster with the centrifuge at full speed.

**Page 3**  `[recipe] battery_charger`

Place the charger facing you: the cable goes into its **back**, and only there. The wrench turns it. Pipes and hoppers may put an empty battery or cell in from any other side and take it out once it is full. Its screen shows the tank, what is arriving, the item filling, and one row per power with a STAMP button. Only players with Machines access on the totem may open it.

**Page 4: What gets stamped**

Stamping copies a power at the level the totem has. While the item sits in a fed charger, a stamped power follows the totem's level if it changes. Take the item out and the levels are fixed.

**Batteries** take Base powers: the ones a projector can receive. **Cells** take Carried powers: the ones that work from your offhand.

**Page 5**  `[recipe] plasma_battery_1, plasma_battery_2`

Tier I holds 40,000 mB and no powers. Tiers II, III and IV hold 200,000, 1,000,000 and 5,000,000 mB and two, three and four Base powers. Each tier is built around the one below.

**Page 6**  `[recipe] plasma_battery_3, plasma_battery_4`

**Page 7**  `[picture] A battery as source`

The four tiers, and a charged battery standing on an extractor: it feeds at 100 mB/t with the powers stamped into it, until it runs dry. The projector gets those powers as if a totem stood there.

**Page 8: Batteries as a source**

A battery on an extractor feeds it at 100 mB/t with its stamped powers until it runs dry, so projectors and chargers keep working while your totem is away or stolen. The battery lies along an axis; the wrench turns it. A battery keeps its charge and powers when mined.

**Page 9**  `[recipe] power_cell_1, power_cell_2`

A *Power Cell* holds Carried powers: 8,000 mB and one power for Tier I, 24,000 and two for II, 60,000 and three for III.

**Page 10**  `[recipe] power_cell_3, wave_emitter`

The *Wave Emitter* runs on one cell. Its hover text and the cell's own show the charge and the powers stamped in.

**Page 11: The Wave Emitter**

Right-click the emitter at the air (no block within four blocks) to open it: put a Power Cell in the slot, press ON, and read the cell's powers and how long it will last. Put the emitter in your **offhand**: you get the cell's powers as if you carried the totem, while it draws 20 mB/t. It switches off when the cell runs dry.

Right-click at a nearby block to switch it without opening the screen; sneak there to take the cell out.

**Page 12: Out and about**

So the totem can stay safe at home while you go out: a Tier III cell at 20 mB/t lasts 50 minutes.

Carried powers from the emitter: Damage, Resistance, Mining Speed, Swiftness, Vitality, Featherfall, Night Sight, Bouncy and X-ray, at the stamped level.

## The Plasma Injector

**Page 1**  `[item shown] plasma_injector`

Wave Plasma can drive machines. A *Plasma Injector* stands against a machine, with its port on the far end for the cable (the only face a cable connects to), and the machine runs faster the more plasma arrives: twice as fast at 300 mB/t, three times at 500. Only what is arriving counts, so a cut cable or a stolen totem slows the machine back down within a second or two.

**Page 2**

It works on every BSP-Core machine: against any block of the Illyrium Crucible, Refinery or Magnetic Centrifuge, or against a Tetrium Crucible or Combination Forge. On a *Shatter Coin Factory* it stands in one of the three Motivator cells and speeds that slice only, on top of what the Motivators do.

It also works on most machines from other mods, which it ticks extra; a few that keep their own clock will ignore it.

**Page 3**  `[recipe] plasma_injector`

**Page 4**  `[picture] Injector on a crucible`

The cable comes in at the far end, the nozzle end touches the machine. The barrel fills as high as the rate, and a slug of plasma runs down each feed tube while it works.

**Page 5: Overclock**

**Overclock** on a totem travels with the plasma. Every injector fed from that totem's extractors, however far the cables run, is stronger: the speed-up above normal is multiplied by 1.25, 1.5, 1.75 or 2 by level, so a run that gives a machine twice its speed gives three times at Overclock IV. Plasma from a tank keeps the Overclock of the totem that filled it, and a projector cannot send it.

## Letting Friends In

**Page 1**

Your placed totem's panel has an **ACCESS** tab. Type an online player's name and Add: they get four switches.


- **Upgrades**: may buy upgrades and change chunks and projector settings.
- **Alarm**: does not set it off.
- **Ward**: is not weakened by it.
- **Machines**: may open the totem's chargers and see through its Cloaking.

**Page 2: Rules**

Up to eight people. The list stays with the totem when you pick it up, and is wiped when the totem is stolen. Friends still cannot pick the totem up, and they do not get its Carried powers. Teammates have all four switches without being listed: see Teams.

## Teams

**Page 1**

Friends who build together can form a **team**. Every member counts as a friend with all four switches on every teammate's totem: they open its panel, buy its upgrades, pass its Ward and Alarm, use its machines and see through its Cloaking. Teammates cannot steal from each other, and the Totem Compass leaves teammates' totems alone.

**Page 2: Making one**

Open the Team screen from the **TEAM** button above your inventory, the TEAM tab on your placed totem, the TEAM tab on your carried totem's screen, or a key of your choice in Controls. Name the team, pick its colour and press CREATE. Invite players who are online from the INVITE view; a seat is held for a minute while they decide.

**Page 3: The invite**

An invited player sees a card at the top right with your face and the team's name, hears a chime, and finds the same offer in chat with Accept and Decline. Two keys answer the card (Y and H unless rebound); the chat buttons work from inside any screen.

**Page 4: Members and the lead**

A team holds up to five. The leader renames, recolours, removes members, hands the lead over and disbands; everyone may invite. Leaving, being removed or a disband takes the shared access away at once, and a totem stolen by an outsider is out of the team's reach by itself. The leader hands the lead over before leaving.

**Page 5: Talking**

Press **TEAM CHAT** on the Team screen and what you type goes to the team until you press it again; a pill above the chat shows while it is on. **/t message** sends one line without switching. The team's board keeps the last fifty lines for members who were away, counts the unread ones at login, and the leader can pin one note above it.

**Page 6: Upgrading together**

Any member may buy upgrades on a teammate's placed totem and raise its tier; the one buying pays, with coins and experience as for their own. The upgrade belongs to the totem and stays with it whoever leaves the team.

## Plasma Across Worlds

**Page 1**  `[item shown] accelerator_intake`

Wave Plasma can cross into another world on the same server, the Nether, the End or a mining world, so a *Projector* there keeps chunks loaded with **Anchor** from a totem that stays safe at home. The *Plasma Accelerator* sends it; a *Plasma Decelerator* receives it. Plasma goes one way only, and every power it carried arrives with it.

**Page 2: The ring**

The accelerator is a ring, three blocks high and wide, one deep: an *Accelerator Intake* at the bottom middle, placed facing you, and seven *Accelerator Frames* round it with the middle left empty. It forms within a second, the blocks vanish and the ring takes their place. Run a plasma cable into the intake (any face) and RF into any frame. It takes up to 3,000 mB/t over every run feeding it.

**Page 3**  `[recipe] accelerator_intake, accelerator_frame`

**Page 4**  `[picture] A formed accelerator`

The ring stands across the intake's facing, the cable in at the intake's socket, the black hole in its cage in the middle. With RF the coil heads light in turn round the ring; while a link delivers, pulses race round the copper track and arcs jump to the cage.

**Page 5: The far end**

The decelerator is two blocks: a *Decelerator Core*, placed facing you, with a *Decelerator Port* directly behind it. RF goes into the core; cables leave the port's free faces, carrying the plasma on to bases, chargers, injectors, tanks or another accelerator.

**Page 6**  `[recipe] decelerator_core, decelerator_port`

**Page 7**  `[picture] Port into a projector`

The core's barrel holds the small black hole; the port behind it has a socket on every free face. Here a cable leaves its back into a Projector Base, so the projector keeps its chunks with the far totem's Anchor.

**Page 8: Linking**

A blank *Link Card* right-clicked on an intake takes that accelerator's world and position. Right-click a Decelerator Core with it and the two are linked, in whatever world the core stands. Sneak and right-click the core empty-handed to take the card out.

An accelerator holds one link. Each *Link Expander* right-clicked onto the intake adds one, two at most, and the flow is shared equally between the links that can take it.

**Page 9**  `[recipe] link_card, link_expander`

**Page 10: RF and powers**

Both ends burn RF: 1,000 RF/t at the intake while any link delivers, 100 RF/t at a core while it receives, from pools of 200,000. Without RF on either side nothing crosses and the plasma stays with the source.

Once the plasma arrives it is ordinary plasma: an Overworld totem's **Overclock** speeds a Nether injector, its **Anchor** holds a Nether projector's chunks. Only the users of the totem feeding an intake may write cards there.

**Page 11: Sleeping and waking**

A far side whose chunk is not loaded sleeps, and its share goes to the other links. Once a minute the accelerator loads it for a moment and delivers, so a projector there relights its own chunk loading by itself; after that the projector keeps the chunk and the link runs on its own.

Right-click the intake empty-handed for the **Worlds board**: a column per world, every link as a pip with its state, UNLINK for the one you pick and WAKE to load every sleeping far side at once.

## Powers in Detail

**Page 1**

The higher-tier powers, one by one. Auras are cubes: a radius of 5 covers 11 by 11 by 11 blocks, so the edge can be measured and built to. Hide the cube drawings for yourself with the AURAS switch in the totem panel's header or the keybind.

**Page 2: Carried**

**Bouncy** (Carried, Dig path after Featherfall, Tier III): less fall damage, and landing from three blocks or more throws you back up with part of your landing speed, so a bigger fall means a bigger bounce. Sneak to land flat.

**X-ray** (Carried, after Night Sight, Tier V): press its key and the solid blocks around you turn to faint glass, fainter the deeper they are, for a while; ores show through them bright, containers and spawners are marked. The level sets how far it reaches and how long it lasts; then it recharges.

**Page 3: Base**

**Cloaking** (Base, Walls path after Deadlock, Tier IV): outsiders see the land as the world generated it, with its trees, plants, snow and ice but no caves, in place of everything inside the cube, and the players and mobs inside are hidden from them too. Uses 50 mB/t of the totem's plasma output.

**Recall** (Base, Home path after Overclock, Tier V): when your totem is being stolen, a card above the hotbar offers to bring you back to within its distance of the totem. Twenty seconds to decide, ten minutes' rest after.

**Page 4: Raid**

**Recall Block** (Raid, after Shroud, Tier IV): with it on the totem in your offhand while you steal, the owner's Recall offer comes later, on top of Shroud.

**Thief's Step** (Raid, beside Recall Block, Tier IV): your footsteps make no sound, and an Alarm up to the level shown does not see you.

**Siege** (Raid, after Thief's Step, Tier V): press its key and for a while Fortify does not slow your mining; then it recharges.

**Page 5: Sentinel and Harvest**

**Sentinel** (Base, Walls path beside Ward after Alarm, Tier III, priced like a Raid power): while anyone is inside your Alarm cube, a strip at the top of your screen tells you, from anywhere on the server: how many (level 1), who (2), how far from the totem (3), and at level 4 where they are right now, with a marker through the walls when they are near you. A steal in progress shows the thief and the seconds left. Friends with the Alarm switch see it too.

**Harvest** (Base, Anchor path after Survey, Tier IV): BSP ores you mine within its reach grow back where they were, one at a time, as long as the totem has plasma to spare for it.

**Page 6: Herald**

**Herald** (Base, Walls path after Alarm, Tier II, one level): when your totem is being stolen, the BSP Discord bot sends you a direct message with the server, the place, the thief and the time left, and another when the steal ends. It comes when the game warns you, so a thief's Shroud delays it too. Where the server has no Discord link, Herald stays locked.

**Page 7: Linking Discord**

Type **/bsp discord link**, or press LINK on Herald's card, for a six-letter code that lasts ten minutes. In the BSP Discord, type **/link** and the code. Only you see the bot's answer.

You never type a Discord name or password in the game. **/bsp discord status** shows the link; **/bsp discord unlink** ends it.

---

# The Three Metals

Tetrium, Illyrium and Magnatite, from first ore to refined ingot.

## Tetrium

**Page 1**

*Tetrium Ore* is found underground in stone and deepslate. Mine it with an iron pickaxe or better. Without Silk Touch the ore drops *Raw Tetrium*; the crucibles take raw chunks and ore blocks alike, so do not smelt either.

**Page 2**  `[recipe] tetrium_crucible`

The *Tetrium Crucible* burns furnace fuel and turns each ore into Tetrium Nuggets and Tetrium Slag.

**Page 3**  `[recipe] tetrium_ingot_from_nuggets, combination_forge`

Nine nuggets make an ingot by hand. The *Combination Forge* does the same job for you and also presses plates.

**Page 4**  `[recipe] slag_brick_from_smelting`

Keep your slag. Smelted into *Slag Bricks* it goes into almost every machine part.

## Dust and Crushing

**Page 1**

Several recipes need metal dust. A crusher from another mod turns an ingot into dust every time: Mekanism's Crusher, Create's crushing wheels and millstone, Thermal's Pulverizer, Ender IO's SAG Mill, Railcraft's Crusher, Integrated Dynamics' Squeezer and Mechanical Squeezer, and Electrodynamics' Mineral Grinder.

With no crusher, put any pickaxe and one ingot in a crafting grid. The pickaxe loses a little durability, and you get either dust or a few nuggets back, by chance.

## Illyrium

**Page 1**

*Illyrium Ore* is rarer and needs a diamond pickaxe. Refining it takes four steps:


- Illyrium Crucible: ore and slag become a Dirty Illyrium Ingot.
- Crush it into Dirty Illyrium Dust.
- Illyrium Refinery: dust, water and a filter give Pure Illyrium Dust.
- Back in the Crucible: Pure Illyrium Dust and Tetrium Dust alloy into Illyrium Nuggets.

**Page 2**  `[recipe] illyrium_ingot_from_nuggets, illyrium_forge_upgrade`

Nine nuggets make an ingot by hand. To forge Illyrium in the Combination Forge, fit the *Illyrium Forge Upgrade*.

**Page 3**  `[recipe] iron_filter, diamond_filter`

Filters wear out. Better filters refine far more dust before they are used up.

## Magnatite

**Page 1**

*Magnatite Ore* lies deep underground, mostly in deepslate, and needs a diamond pickaxe. It is a little easier to find than diamond. As with the other ores, keep the ore block: the machine takes it as it is.

The Magnetic Centrifuge spins each ore into Magnatite Nuggets and one *Carbon Dust*.

**Page 2**  `[recipe] magnatite_ingot_from_nuggets`

Nine nuggets make a *Magnatite Ingot* by hand or in the Combination Forge.

**Page 3**  `[recipe] copper_tetrium_coil`

Fit the *Copper Tetrium Coil* to a centrifuge and it can magnetise ingots into *Charged Magnatite Ingots*. Each try may fail; a failed try keeps the ingot.

## Molten Metals

**Page 1**

Tetrium, Magnatite and Illyrium melt in a Tinkers' smeltery or a Thermal Magma Crucible, and pipe and store like any fluid. A nugget is 10 mB and an ingot 90 mB, and both cast back one for one.

Ore melts to exactly what the BSP machines make from it: Tetrium ore 20 mB, two nuggets; Magnatite ore 30 mB, three. The slag and the Carbon Dust come only from the BSP machines.

Illyrium ore, Dirty Illyrium and Charged Magnatite do not melt: the Crucible, the Refinery and the Centrifuge are their only way.

**Page 2: Spills**

Spilled, molten metal burns whatever wades into it, as lava does, and meets water as lava does: a still pool sets to *Obsidian*, a flowing edge to *Cobblestone*, and the metal is gone.

A dispenser pours a molten bucket, and fills an empty one from a pool in front of it.

---

# Machines

The parts every machine is made of, and how to build the multiblocks, the Totem Forge among them.

## Machine Parts

**Page 1**

Every machine block is built from the same few parts. Make these first:


- *Slag Brick*: smelt Tetrium Slag.
- *Tetrium Plate*: put a Tetrium Ingot in the Combination Forge.
- *Machine Chassis*, *Tetrium Coil*, *Drive Motor* and the *Basic Control Circuit*: see the next pages.

**Page 2**  `[recipe] machine_chassis, tetrium_coil`

**Page 3**  `[recipe] drive_motor, circuit_substrate`

**Page 4**  `[recipe] basic_control_circuit`

Each controller needs this circuit upgraded for its job: Crucible, Refinery or Mint.

## Illyrium Crucible

**Page 1**

A 3 x 3 x 3 multiblock that runs on lava. It smelts Illyrium Ore with slag into Dirty Illyrium Ingots, and alloys Pure Illyrium Dust with Tetrium Dust into Illyrium Nuggets.

The lower Lava Pylons (orange sockets) take lava by pipe. The upper ones (yellow sockets) take RF once an RF Upgrade is fitted.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[picture] Illyrium Crucible`

Controller at the bottom front centre. 12 Casing, 8 Lava Pylons, 2 Item Hatches, 1 Core.

**Page 3**  `[recipe] thermal_lining, crucible_control_circuit`

**Page 4**  `[recipe] illyrium_casing, illyrium_core`

**Page 5**  `[recipe] lava_pylon, item_hatch`

**Page 6**  `[recipe] illyrium_crucible`

## Illyrium Refinery

**Page 1**

Washes Dirty Illyrium Dust into Pure Illyrium Dust using water and a filter.

The Refinery Pump takes water (blue socket) and RF (yellow socket). The Item Hatch takes dust in and gives pure dust out.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[picture] Illyrium Refinery`

Controller at the bottom front centre. 8 Casing, 1 Core, 2 Tank Glass, 1 Item Hatch, 1 Pump.

**Page 3**  `[recipe] refinery_control_circuit, illyrium_glass`

**Page 4**  `[recipe] refinery_pump, illyrium_refinery`

## RF Upgrades

**Page 1**

Fit an RF Upgrade to a machine and it can take RF. It then runs faster and, on the Crucible, uses less lava. Mk I, II and III give 5%, 10% and 30%.

The Combination Forge runs on RF alone with an upgrade, at twice its coal speed.

**Page 2**  `[recipe] rf_upgrade_mk1, rf_upgrade_mk2`

**Page 3**  `[recipe] rf_upgrade_mk3`

## Magnetic Centrifuge

**Page 1**

A multiblock one block high and three by three. It separates Magnatite Ore and, with a Copper Tetrium Coil, charges Magnatite Ingots. It always needs RF, fed into the Power Port (yellow socket); the Item Hatch (blue socket) takes items in and out.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[picture] Magnetic Centrifuge`

Controller front centre, Rotor in the middle, Item Hatch left, Power Port right, 5 Casing.

**Page 3: Stacking**

Put another Rotor on top of the first and ring it with eight Casing to add a layer, up to six layers.


- More layers: more nuggets from each ore, up to 7 to 9 with six.
- More layers: a better chance to charge an ingot, from 1 in 6 up to 1 in 2 with five.
- Six layers: each charging try is much quicker.

Every layer draws power, and charging draws far more than separating.

**Page 4**  `[recipe] centrifuge_casing, centrifuge_rotor`

**Page 5**  `[recipe] centrifuge_power_port, magnetic_centrifuge`

## The Totem Forge

**Page 1**

A 2 x 2 x 3 multiblock that pours a new *Shatter Totem*. It melts a long bill of metals into its tanks, fills a mould of eight pieces standing in its hollow middle, and lets the totem set.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[3D structure] Totem Forge`

Bottom: the Totem Forge front left, the Metal Intake behind it, a Forge Hearth on the right of each. Middle: empty. Top: Melt Tanks left, Pour Head front right, Forge Cap behind it.

**Page 3: The metal**

The tanks must hold the whole metal bill before the forge pours. Its screen shows a tube for each metal; by default 128 Tetrium, 32 Magnatite, 8 Charged Magnatite, 12 Illyrium, 32 Allthemodium, 16 Vibranium, 8 Unobtainium and 2 Netherite Ingots.

Ingots go into the Metal Intake or the screen's slot and melt one at a time, burning lava from the Forge Hearths. An RF Upgrade speeds it up. Molten Tetrium, Magnatite and Illyrium can be piped straight into the intake.

**Page 4: The mould**

The Totem Mould goes into the hollow one piece at a time, in order: the Base, two Feet, the Torso, two Arms, the Head, and the Totem Core last. Right-click the forge with each piece.

**Sneak + right-click** with an empty hand, or TAKE PIECE OUT on the screen, takes the last piece back out, until the metal is poured.

**Page 5: Pouring**

With the mould complete and the tanks full, press **POUR**.

If you own no Shatter Totem, the pour is free and the totem sets in 5 days. If you own any, you pay 4 Illyrium Shatter Coins for each one first (or their worth in any other Shatter Coins, with change), and it sets in 15 days.

You can have one totem pouring or setting at a time, on any server.

**Page 6: Setting and stealing**

The totem sets on real time, even while you are away, and you are told when it is ready. Right-click the forge with an empty hand to take it out: a *Forged Shatter Totem*, tier I, yours.

Anyone else must steal it the same way: they stay by the forge for two and a half minutes while you are warned. Break the forge before the totem has set and the pour is lost.

**Page 7**  `[recipe] forge_hearth, totem_forge`

**Page 8**  `[recipe] metal_intake, melt_tank`

**Page 9**  `[recipe] pour_head, forge_cap`

## The Totem Mould

**Page 1**

Seven mould pieces and a core make one mould. Each piece looks like the totem with its own part picked out: the Base, the Foot and Arm (two of each go in, left and right), the Torso and the Head.

A piece lasts three pours, then cracks. It takes no enchantment and no repair. The *Totem Core* becomes the forged totem's orb, so every pour needs a new one.

**Page 2**  `[recipe] mould_shell, base_mould`

Mould Shells come two at a time; every piece is built from them.

**Page 3**  `[recipe] foot_mould, torso_mould`

**Page 4**  `[recipe] arm_mould, head_mould`

**Page 5**  `[recipe] totem_core`

---

# Shatter Coins

Coins pay for the strongest totem upgrades. You press them yourself.

## Blanks and Coins

**Page 1**

There are five coins: Copper, Gold, Diamond, Netherite and Illyrium. Each is worth twice the one before.

A coin starts as a *Shatter Blank*, is built up into a tier's coin blank, and is then pressed in the Shatter Coin Factory.

**Page 2**  `[recipe] shatter_blank, copper_coin_blank`

Each tier's blank is crafted around the blank of the tier below it.

**Page 3**  `[recipe] gold_coin_blank, diamond_coin_blank`

## The Shatter Coin Factory

**Page 1**

An end-game machine built in slices. One slice is 1 wide, 2 high and 3 long. Put blanks in the Blank Hatch and RF in the Power Port, both at the back.

Pressing takes real time, from hours to days by tier, and carries on while you are offline. Coins collect in the tray and must be taken by hand.

Stuck on a build? Empty your hand and **sneak + right-click** the controller for the step-by-step Assembly Guide.

**Page 2**  `[picture] Factory slice`

Bottom from the front: Controller, Frame, Blank Hatch. Top: Frame, Press, Power Port.

**Page 3: Joining slices**

Build slices side by side, facing the same way, and they join into one machine with one screen. You may own up to 10 slices.

Each slice presses its own blanks. RF is shared, and cabling more Power Ports charges the machine faster.

**Page 4**  `[recipe] mint_control_circuit, conveyor_belt`

**Page 5**  `[recipe] press_die, factory_frame`

**Page 6**  `[recipe] factory_press, factory_blank_hatch`

**Page 7**  `[recipe] factory_power_port, shatter_coin_factory`

## Motivators

**Page 1**  `[item shown] factory_motivator`

Place up to three on top of a complete factory slice. One, two or three cut that slice's press time by 15%, 30% or 50%.

They pulse faster the more a slice has, and link up with their neighbours.

**Page 2**  `[recipe] resonance_crystal, illyrium_processor`

**Page 3**  `[recipe] factory_motivator`

## The Coin Vault

**Page 1**  `[item shown] coin_vault`

A safe that holds only Shatter Coins, 27 stacks of 12 per block. Nothing can pipe coins in or out.

Vault blocks of yours that touch join into one vault, up to 3 x 3 x 3. You may own 27 vault blocks.

**Page 2**  `[recipe] coin_vault`

The block keeps its lock and alarm when you break it and place it again.

**Page 3: Access and Security**

On the **Access** tab, add the players who may open the vault. They must be online when you add them. They can move coins but cannot change the settings.

On the **Security** tab, buy up to three lock levels and an Alarm with coins. The Alarm warns everyone with access the moment someone starts picking the lock.

**Page 4: Lockpicking**

Anyone without access can pick the lock: 2 minutes, plus 1 minute for each lock level, staying within 4 blocks. If they finish, they take a quarter of the coins, chosen at random. Illyrium coins are safe while the vault holds 3 or fewer.

Breaking a vault that is not yours gives no coins: they are kept for the owner, who collects them by opening any vault of theirs.

**Page 5: Interest**

Coins in your vaults earn interest: XP levels and Tetrium Ingots for every 50 coin value stored, counted across all your vaults. With a Netherite or Illyrium coin stored you also earn up to 2 Illyrium Ingots every 3 days.

Interest builds up while you are offline, but stops at 3 days' worth. Press **Redeem** on the Storage tab to collect it and start it building again.

## The Shatter Bank

**Page 1**

The *Shatter Bank* is a two-block machine admins place at spawn and in safe zones. Right-click it to open your account.

Your account holds **Shatter Pieces**, the everyday money. Pieces are never an item: they live in the account, and the same balance follows you to every server of the network.

Every player is given 500 pieces on their first join.

**Page 2: Coins and pieces**

The bank also keeps *Shatter Coins* for you, by tier. Coins in the bank are safe from raids but earn **no interest**: a Coin Vault pays interest and can be robbed. Choose.

Deposit coins to keep them, or **convert** them: a Copper coin becomes 1,000 pieces, each higher tier double the one below. Converting is one way. Pieces never turn back into coins.

**Page 3: Paying and the log**

From the account screen you can pay any player on the network, online or not; large payments ask you to confirm, and you can refuse incoming payments.

The transaction log lists what you bought and sold, who you paid and who paid you, with filters and a search. It is kept for 90 days.

Commands are a backup: **/bank**, **/bank pay**, **/bank log**.

## Safety Deposit Box

**Page 1**

A *Safety Deposit Box* is a cabinet admins place beside the bank. Right-click it to rent **your own box**: the same box opens from any of these cabinets on the network, and what is inside is safe at all times.

Rent is paid by the day, in pieces or coins, for as long ahead as you like. A box starts with **6 slots**; buy **3 more** at a time, up to 18.

**Page 2: Rent and fees**

When the rent runs out nothing is lost: your slots and their contents are kept, but the drawer is locked until you pay rent again, plus a **holding fee** for every day the box sat unpaid. Leave it long and it costs more to get back.

Shatter Coins and totems cannot be stored in a box. The bank keeps coins; totems belong in the world.

## The Land Office

**Page 1**

The *Land Office* sells land. Right-click it for a map of the plots around you: **amber** plots are for sale at a set price, **dashed** areas are open ground where you mark out your own plot, and **green** plots are yours.

Click a plot to see its size and price, then press **Buy** and confirm. The price comes out of your bank account.

**Page 2: Open ground**

For open ground, mark the corners with the land wand (**/claim tool**); the office opens by itself with the price of what you marked, and shows how near it lies to other plots.

**Enlarge** grows one of your plots to an outline that covers it, paying for the new ground. **Give up** returns part of what you paid and frees the land.

## Kits

**Page 1**

A kit is a bundle of items the server hands out. Open the kit browser with the KITS button above your inventory, a key you bind in Controls, or **/kits**.

Every kit you may see is a card: its picture, its name, and what stands between you and it.

**Page 2: Reading a card**

A green card is ready: press REDEEM on it and the items land in your inventory.

A grey card is one you took lately. It says how long until it is ready again, or that it was once only.

A card with a rank on it needs that rank; the steps show where you stand and how far there is to go. A card naming a server is taken on that server. A price in pieces comes from your bank account. A card counting slots needs that much room in your inventory first.

**Page 3: Waiting for you**

A kit given to you as a reward, or at your first login, waits in violet when it did not fit or came while you were away. Free the slots it names and press TAKE IT.

Nothing is lost: a kit that cannot fit stays waiting until it can.

**Page 4: Looking inside**

Click a card's picture to see what is in the kit, with the counts, and every condition ticked or crossed. BACK returns to the wall.

On a network your kits and what you have taken are the same on every server.

## The Trade Post

**Page 1**

The *Trade Post* is a steel trading desk two blocks wide and two high that admins place at spawn and in hubs. It is where players trade with each other, and where the market is.

Its four keys open their pages: **MARKET**, **SELL**, **COLLECT** and **TRADE**. Click anywhere else on the desk for the market.

**Page 2**  `[3D structure] Trade Post`

The Exchange Desk. The scales on the counter tip while a trade runs, and the beacons on the hood turn while anyone uses the post.

**Page 3: Who is here**

At the right of the screen is everyone standing at a Trade Post on any server of the network, with their server's name. A grey mark means they are busy in a trade.

Pick a name and press **TRADE** to ask them. See Trading with Players.

## Trading with Players

**Page 1**

A request lasts 30 seconds and comes up as a card on the other player's screen, with **ACCEPT** and **DECLINE**. You can have one request out at a time; **TAKE BACK** withdraws it.

Accepted, you both get the trade screen: your 12 offer places at the left, theirs at the right, a pieces box under each, and a chat between the two of you.

**Page 2: Ready**

You see what the other player puts in as they do it. Any change to either offer turns both **READY** lights off, so nobody is shown one thing and given another.

When you are both ready, five lights count down 5 seconds and the offers lock. Press READY again to stop. At the end the items and pieces cross over in one go.

**Page 3: Room and leaving**

Before anything moves, the trade checks that both of you have room for what you are getting. If not, nothing moves and you are told who is short of space.

Closing the screen, walking more than 8 blocks from the post, logging out or dying ends the trade, and your offer comes back to you.

Shatter Totems and Totem Cores never go into an offer.

## The Market

**Page 1**

The market is one list for the whole network. On **SELL**, put up to 9 stacks into a lot, type a price in pieces and press **LIST LOT**. A lot stays up for 7 days, and you can have 10 at once.

On **MARKET**, search by item or seller and sort by newest, cheapest or dearest. A card opens the lot: every stack, the price and what you pay.

**Page 2: The tax**

Listing costs a **5% tax** on the price, rounded up, paid when you list and kept if the lot does not sell. The buyer pays the price plus the same 5%. Both taxes go into the network's lottery pot.

A lot is bought whole. The seller is paid at once, with a message, or at their next login.

**Page 3: Collecting**

A lot you cancel, and one whose 7 days run out, waits in your **Collect** box. **COLLECT ALL** fills your inventory and leaves the rest in the box.

Shatter Totems and Totem Cores cannot be sold, not even inside a shulker box.

Score Screens can show the market as the **Market Board**: the newest lots with their prices and how long ago each was listed.

## Market on the Go

**Page 1**  `[item shown] portable_banker`

The *Portable Banker* has a **MARKET** key under the Shopping Network. The market is its own one-time unlock: **UNLOCK MARKET** takes 12 Netherite Shatter Coins from your inventory, and it stays with your account on every server.

**Page 2: Buying and selling**

MARKET opens the same market as a Trade Post: browse, buy, list lots and collect, wherever you are.

There is no list of traders on the Portable Banker. Trading with another player needs a Trade Post, and nobody can ask you to trade while you are only on the banker.

The unlock lasts the season, like the Shopping Network.

