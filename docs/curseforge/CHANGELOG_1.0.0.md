# BSP-Core 1.0.0

The first full release. Everything since 0.3.0: Wave Plasma now drives machines, the Plasma Tank is finished, Cloaking and X-ray are the real thing, four new totem powers, the totem can no longer be hidden away, and a balancing pass over the whole progression. The mod now talks to more of the pack's machines, and EMI shows everything JEI does.

**Existing worlds:** new settings are added to `serverconfig/bsp_core-server.toml` by themselves, but settings that changed keep their old value until you edit them: the Plasma Tank's capacity (`tankPerBlock`, now 2,500,000), the machine times and yields in the balancing section below, and Overclock, whose two old keys (`overclockSpeedBonus`, `overclockRadius`) are replaced by `overclockPlasmaScale`. A Cloaking snapshot made by an earlier version is thrown away and remade from the seed by itself. The Illyrium ore change reaches new chunks only.

## Wave Plasma drives machines

- **Plasma Injector.** A column you place against a machine; the plasma port is on the far end, and only a cable on that face connects. Feed it and the machine runs faster the more arrives: twice the speed at 300 mB/t, three times at 500, with a straight line between. Only plasma actually arriving counts, so a cut cable or a stolen totem drops the machine back to normal within a second or two. Works on every BSP machine (against any block of a formed multiblock, or against a Tetrium Crucible or Combination Forge, and it stacks with RF upgrades), on a Shatter Coin Factory slice by standing in one of its three Motivator cells, and on other mods' machines too. Jade shows the rate and the factor on the injector and on the machine; the factory screen has a PLASMA gauge beside its RF gauge. The injector shows its work: the barrel fills as high as the rate, a slug of plasma runs down each feed tube into the machine, and the tip, lamp and port light.
- **Overclock has changed.** It no longer speeds machines by itself and has no radius. It travels with the plasma: every injector fed from a totem with Overclock, however far the cables run, is stronger, by a quarter, a half, three quarters or double its speed-up by level. A machine with no injector gets nothing from it. Plasma from a tank carries no Overclock, and projectors no longer offer it.

## Plasma Tank

- A hollow box of Tank Casing (edges), Tank Glass or casing (faces) and Tank Ports, 3 to 12 blocks a side. It forms by itself when the last block goes in and holds 2,500,000 mB for every block of the shell, plasma only.
- **It shows it is finished.** When the last block goes in, a sweep of light runs over the shell from that block, the casing turns to glass with a thin dark rail and a lit strip along every edge and a lit node at each corner, and the four uprights glow only as high as the plasma stands, so you can read the level from outside. Break a block and the cubes come back until it is replaced.
- Ports take plasma from a run that comes from an interface and give it to runs that only lead to bases, chargers or other tanks, up to a face's worth each; the wrench sets a port to Input or Output only. Cables draw an arm into the port they feed.
- The plasma fills the hollow and half of every shell block, so the walls look thin, and shows as a thin layer as soon as there is any. A port above the surface pours a stream down to it.
- Right-click any block for a screen with the tank in 3D, its level, its ports and the first cables out of each. While the tank is unfinished, right-click does nothing, so you can build without sneaking; Jade says "Tank unfinished".
- Break one block and the tank goes dormant but keeps its plasma; put it back and it is whole. Rebuild smaller and it keeps what fits.

## Totem powers

- **Sentinel** (Base, Walls path beside Ward, priced like a Raid power). While anyone is inside your Alarm cube, a strip slides down from the top of your screen anywhere on the server: the totem, how many are inside, and from level 2 their names, from level 3 how far they are from the totem, and at level 4 live positions with a marker through the walls when they are near you. A steal in progress shows the thief and the seconds left. Friends with the Alarm switch see it too.
- **Thief's Step** (Raid, beside Recall Block). Your footsteps and landings make no sound, and an Alarm of level 2, 4, 6 or 8 or lower, by your level, neither outlines you nor warns the owner.
- **Siege** (Raid, after Thief's Step). Press V and for 8 to 20 seconds Fortify stops slowing your mining; an orange ring left of the crosshair drains, then it recharges for 90 to 180 seconds.
- **Harvest** (Base, after Survey). BSP ores anyone mines within 8 to 24 blocks of your totem grow back where they were, one at a time every 30 to 120 seconds, while the totem spends 25 mB/t of plasma on it.
- Mining Speed, Swiftness, Featherfall and Night Sight cost half the XP they did; the pack gives those cheaply elsewhere and they are steps on the way to Bouncy and X-ray. Damage now reaches +8 at level 10.

## Cloaking, finished

- Outsiders see the land as the world generated it in place of your base: ground, stone, water, trees, plants, ores, and in a cold biome snow on the ground and ice on the water, so the cube matches its surroundings. Any village or other structure that stood there shows too. Caves are not made. It is made from the world seed over a couple of seconds when Cloaking is bought or its size changes.
- The players and mobs inside the cube are hidden from outsiders, the owner included, name tags and all.
- An outsider walking in sees the base fade in over seven seconds, and fade out again over three on the way out; the players inside fade with the blocks. Both react the moment the cube's edge is crossed.
- Operators: `/bsp cloak see off` lets you see cloaks like an outsider, to test; `on` puts it back.

## X-ray, finished

- The solid blocks around you turn to faint glass, fainter the deeper they are, for the level's seconds. Ores show through them bright even in the dark; containers get a faint gold tint and spawners a red mark. You walk and mine as normal. It never reveals a cloaked base that hides from you. BSP ores are highlighted like vanilla ones now.

## The totem stays in play

- A dropped totem is no longer an item that hoppers, funnels, collectors or magnets can pick up; only its owner can, by walking into it.
- Every totem has an identity the server tracks. One put somewhere it should not be (an AE2 or Refined Storage grid, a pipe) comes back to its holder within a few seconds, and the copy left behind becomes a spent husk that cannot be placed, upgraded or used. A totem can never be hidden from stealing by storing it away.

## Other mods

- Ingot-to-dust recipes for the pack's crushers: Mekanism, Create (crushing wheels and millstone), Thermal, Ender IO SAG Mill, Railcraft Crusher, Integrated Dynamics Mechanical Squeezer and Electrodynamics Mineral Grinder. No mod can turn BSP ore into ingots: that stays with the crucibles and the centrifuge.
- The Battery Charger takes empty batteries and cells from pipes and hoppers on any side but the back, and gives them back when full.
- **EMI** shows every machine's jobs and every information page, the same as JEI.

## Balancing

- Machine times: Tetrium Crucible 300 ticks per ore (was 400); Illyrium Crucible smelt 800 (was 1,200) and alloy 1,200 (was 1,800); Illyrium Refinery 1,800 per dust (was 2,400); Magnetic Centrifuge separate 600 (was 900) and charge 600 per try (was 900).
- Yields: the Iron Filter refines 5 dust (was 1); a Dirty Illyrium Ingot crushed by hand becomes dust 1 time in 4 (was 1 in 6).
- Coins press in 8, 16, 32, 64 and 96 hours by tier (were 12, 24, 48, 96 and 168). The Lava Pylon recipe gives 4 (was 2); filters use string instead of cobweb.
- Illyrium generates in 8 veins per chunk (was 4), about half as dense as diamond.

## Fixed

- A Plasma Extractor could never hand out more than 4,000 mB a second (200 mB/t) however high the totem's Output was. Bases and chargers hid it because they want little; a tank showed it. A 6,000 mB/t totem really moves 6,000 mB/t now.
- A tank with only a little plasma in it showed nothing but the stream.
- Right-clicking an unfinished tank block opened the tank screen and got in the way of building.
