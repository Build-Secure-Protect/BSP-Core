package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.MobPowerEffects;
import com.mrgregles.bsp_core.plasma.PlasmaAccess;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

/** The Mob Chamber's team and area powers at a Projector: each in reach, none outside it, and no channel taken. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class MobPowerGameTests {
    private static final String EMPTY = "empty", BATCH = "mob_powers";
    private static final BlockPos PROJECTOR = new BlockPos(12, 2, 12);

    private static int[] offered(Object... buffLevel) {
        int[] o = new int[Buff.values().length];
        for (int i = 0; i < buffLevel.length; i += 2) {
            o[((Buff) buffLevel[i]).ordinal()] = (Integer) buffLevel[i + 1];
        }
        return o;
    }

    /**
     * Places a projector fed with {@code offered} for {@code access} (as its base would, once a second) and runs {@code then} once it
     * projects, then removes it.
     */
    private static void withProjector(GameTestHelper h, int[] offered, PlasmaAccess access, Consumer<TotemProjectorBlockEntity> then) {
        h.setBlock(PROJECTOR, ModBlocks.TOTEM_PROJECTOR.get());
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            TotemProjectorBlockEntity p = (TotemProjectorBlockEntity) h.getBlockEntity(PROJECTOR);
            p.feed(h.absolutePos(PROJECTOR.below()), offered, 0, p.need(), access, null);
            p.power(true);
            if (p.isActive()) {
                done[0] = true;
                then.accept(p);
                h.setBlock(PROJECTOR, Blocks.AIR);
                h.succeed();
            }
        });
    }

    private static ServerPlayer playerAt(GameTestHelper h, BlockPos rel) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(rel);
        p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        return p;
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void mob_powers_arrive_without_a_channel(GameTestHelper h) {
        withProjector(h, offered(Buff.GROWTH, 2, Buff.HASTE, 3, Buff.TIDE, 1, Buff.FORTIFY, 1, Buff.HEALING, 1), new PlasmaAccess(), p -> {
            h.assertTrue(p.level(Buff.GROWTH) == 2 && p.level(Buff.HASTE) == 3 && p.level(Buff.TIDE) == 1, "Growth II, Haste III and Tide I all arrive");
            h.assertTrue(p.chosen() == 0 && p.level(Buff.FORTIFY) == 0, "and nothing was chosen for them");
            p.toggle(0);
            p.toggle(1);
            h.assertTrue(Integer.bitCount(p.chosen()) == 2 && p.channels() == 2, "both channels are still free for Fortify and Healing");
            h.assertTrue(p.saveWithoutMetadata().getIntArray("MobOffered")[Buff.GROWTH.ordinal()] == 2, "saved with the projector");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void team_powers_reach_the_team_in_reach_only(GameTestHelper h) {
        ServerPlayer friend = playerAt(h, PROJECTOR.east(5)), far = playerAt(h, PROJECTOR.east(5)), stranger = playerAt(h, PROJECTOR.west(5));
        PlasmaAccess access = new PlasmaAccess();
        access.editors.add(friend.getUUID());
        access.wardSafe.add(far.getUUID());
        BlockPos farAt = h.absolutePos(PROJECTOR).offset(20, 0, 0); // Haste I reaches 16
        far.moveTo(farAt.getX() + 0.5, farAt.getY(), farAt.getZ() + 0.5);
        withProjector(h, offered(Buff.HASTE, 1, Buff.FIRE_WARD, 1, Buff.TIDE, 1, Buff.MENDING, 3), access, p -> {
            ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
            pick.setDamageValue(100);
            friend.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, pick);
            for (ServerPlayer pl : new ServerPlayer[]{friend, far, stranger}) {
                MobPowerEffects.onPlayer(p, pl);
            }
            h.assertTrue(friend.hasEffect(MobEffects.DIG_SPEED) && friend.getEffect(MobEffects.DIG_SPEED).getAmplifier() == 0, "the owner has Haste I");
            h.assertTrue(friend.hasEffect(MobEffects.FIRE_RESISTANCE) && friend.hasEffect(MobEffects.WATER_BREATHING), "and Fire Ward and Tide");
            h.assertTrue(friend.getMainHandItem().getDamageValue() == 96, "Mending III gave back 4: " + friend.getMainHandItem().getDamageValue());
            h.assertFalse(stranger.hasEffect(MobEffects.DIG_SPEED) || stranger.hasEffect(MobEffects.FIRE_RESISTANCE), "a stranger gets nothing");
            h.assertFalse(far.hasEffect(MobEffects.DIG_SPEED), "a Ward friend 20 blocks off is out of Haste I's reach");
            h.assertTrue(far.hasEffect(MobEffects.FIRE_RESISTANCE) == (MobPowerEffects.reach(1) >= 20), "Fire Ward I reaches as far as Haste I");
        });
    }

    // each area power in a batch of its own: a test's projector reaches 16 to 32 blocks, as far as the next test's area
    @GameTest(template = EMPTY, batch = BATCH + "_keep", timeoutTicks = 200)
    public void keep_holds_items_dropped_in_reach(GameTestHelper h) {
        withProjector(h, offered(Buff.KEEP, 1), new PlasmaAccess(), p -> {
            ServerLevel sl = h.getLevel();
            BlockPos near = h.absolutePos(PROJECTOR.east(3)), far = h.absolutePos(PROJECTOR).offset(40, 0, 0);
            ItemEntity a = new ItemEntity(sl, near.getX() + 0.5, near.getY(), near.getZ() + 0.5, new ItemStack(Items.DIRT));
            ItemEntity b = new ItemEntity(sl, far.getX() + 0.5, far.getY(), far.getZ() + 0.5, new ItemStack(Items.DIRT));
            sl.addFreshEntity(a);
            sl.addFreshEntity(b);
            h.assertTrue(a.lifespan == 20 * 60 * 20, "dropped in reach: 20 minutes (" + a.lifespan + ")");
            h.assertTrue(b.lifespan == 6000, "out of reach: the usual 5 minutes (" + b.lifespan + ")");
            a.discard();
            b.discard();
        });
    }

    private static Event.Result spawn(GameTestHelper h, EntityType<?> type, BlockPos abs) {
        var e = new MobSpawnEvent.SpawnPlacementCheck(type, h.getLevel(), MobSpawnType.NATURAL, abs, h.getLevel().getRandom(), true);
        MinecraftForge.EVENT_BUS.post(e);
        return e.getResult();
    }

    @GameTest(template = EMPTY, batch = BATCH + "_undead", timeoutTicks = 200)
    public void undead_ward_stops_zombies_and_skeletons(GameTestHelper h) {
        withProjector(h, offered(Buff.UNDEAD_WARD, 1), new PlasmaAccess(), p -> {
            BlockPos near = h.absolutePos(PROJECTOR.east(4)), far = h.absolutePos(PROJECTOR).offset(40, 0, 0);
            h.assertTrue(spawn(h, EntityType.ZOMBIE, near) == Event.Result.DENY && spawn(h, EntityType.SKELETON, near) == Event.Result.DENY
                    && spawn(h, EntityType.HUSK, near) == Event.Result.DENY, "zombies, skeletons and husks do not spawn in reach");
            h.assertTrue(spawn(h, EntityType.CREEPER, near) != Event.Result.DENY && spawn(h, EntityType.SPIDER, near) != Event.Result.DENY, "creepers and spiders still do");
            h.assertTrue(spawn(h, EntityType.ZOMBIE, far) != Event.Result.DENY, "out of reach, zombies still do");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH + "_lantern", timeoutTicks = 200)
    public void lantern_stops_every_monster_but_not_animals(GameTestHelper h) {
        withProjector(h, offered(Buff.LANTERN, 2), new PlasmaAccess(), p -> {
            BlockPos near = h.absolutePos(PROJECTOR.east(4)), edge = h.absolutePos(PROJECTOR).offset(MobPowerEffects.reach(2), 0, 0);
            h.assertTrue(spawn(h, EntityType.CREEPER, near) == Event.Result.DENY && spawn(h, EntityType.ENDERMAN, edge) == Event.Result.DENY, "no monster spawns in reach, to its edge");
            h.assertTrue(spawn(h, EntityType.COW, near) != Event.Result.DENY, "animals still do");
            BlockPos past = edge.east(2);
            h.assertTrue(spawn(h, EntityType.CREEPER, past) != Event.Result.DENY, "past the edge, monsters still do");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void growth_ticks_crops_and_scales_with_the_world(GameTestHelper h) {
        ServerLevel sl = h.getLevel();
        BlockPos soil = new BlockPos(3, 1, 3), crop = soil.above(), stone = new BlockPos(7, 2, 7);
        // nothing is placed next to the wheat after it (a neighbour update before the light is worked out would break it), and a lamp
        // two blocks off keeps it lit whatever the time; the light is worked out over the next ticks, crops grow only in light
        h.setBlock(crop.west(2), Blocks.GLOWSTONE);
        h.setBlock(stone, Blocks.STONE);
        h.setBlock(soil, Blocks.FARMLAND);
        h.setBlock(crop, Blocks.WHEAT);
        h.runAfterDelay(20, () -> {
            var wheat = sl.getBlockState(h.absolutePos(crop));
            h.assertTrue(wheat.is(Blocks.WHEAT) && wheat.is(MobPowerEffects.GROWS) && wheat.isRandomlyTicking(), "young wheat, in the Growth tag: " + wheat);
            int grew = MobPowerEffects.grow(sl, h.absolutePos(crop), 0, 60, sl.getRandom());
            h.assertTrue(grew > 0 && grew <= 60, "the spots were the wheat, until it was ripe (ripe wheat takes no more ticks): " + grew);
            h.assertTrue(sl.getBlockState(h.absolutePos(crop)).getValue(CropBlock.AGE) > 0, "and it grew");
            h.assertTrue(MobPowerEffects.grow(sl, h.absolutePos(stone), 0, 60, sl.getRandom()) == 0, "stone does not count");
            int one = MobPowerEffects.samples(sl, 16, 1), three = MobPowerEffects.samples(sl, 16, 3);
            h.assertTrue(one > 0 && Math.abs(three - one * 4) <= 4, "level III ticks four times as often as level I over the same cube: " + one + " / " + three);
            h.succeed();
        });
    }

    // its own batch: it turns new-player protection on for a moment, which other tests read
    @GameTest(template = EMPTY, batch = BATCH + "_outsiders", timeoutTicks = 200)
    public void outsider_powers_spare_the_team_allies_and_protected_players(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var protection = com.mrgregles.bsp_core.totem.NewPlayerProtection.get(server);
        var mode = protection.mode;
        String rank = protection.rank;
        int hours = protection.hours;
        ServerPlayer owner = playerAt(h, PROJECTOR.east(4)), ally = playerAt(h, PROJECTOR.east(4)), stranger = playerAt(h, PROJECTOR.west(4)),
                newcomer = playerAt(h, PROJECTOR.west(4)), builder = playerAt(h, PROJECTOR.west(4)), distant = playerAt(h, PROJECTOR.west(4));
        builder.setGameMode(GameType.CREATIVE);
        BlockPos farAt = h.absolutePos(PROJECTOR).offset(-30, 0, 0); // Wither II reaches 24, Snare and Scorch I 16
        distant.moveTo(farAt.getX() + 0.5, farAt.getY(), farAt.getZ() + 0.5);
        PlasmaAccess access = new PlasmaAccess();
        access.editors.add(owner.getUUID());
        access.wardSafe.add(ally.getUUID());
        withProjector(h, offered(Buff.WITHER, 2, Buff.SNARE, 1, Buff.SCORCH, 1), access, p -> {
            try {
                protection.applySettings(com.mrgregles.bsp_core.totem.NewPlayerProtection.Mode.PLAYTIME, "", 24);
                for (ServerPlayer old : new ServerPlayer[]{stranger, builder, distant, owner, ally}) {
                    protection.raise(old.getUUID(), -1, 25 * 60); // past the protection
                }
                for (ServerPlayer pl : new ServerPlayer[]{owner, ally, stranger, newcomer, builder, distant}) {
                    MobPowerEffects.onPlayer(p, pl);
                }
                h.assertTrue(stranger.hasEffect(MobEffects.WITHER) && stranger.getEffect(MobEffects.WITHER).getAmplifier() == 1, "a stranger in reach withers (II)");
                h.assertTrue(stranger.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && stranger.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 0, "is slowed (I)");
                h.assertTrue(stranger.getRemainingFireTicks() > 0 && stranger.getRemainingFireTicks() <= 20, "and burns for a second: " + stranger.getRemainingFireTicks());
                for (ServerPlayer spared : new ServerPlayer[]{owner, ally, newcomer, builder, distant}) {
                    h.assertFalse(spared.hasEffect(MobEffects.WITHER) || spared.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || spared.getRemainingFireTicks() > 0,
                            spared.getGameProfile().getName() + " is spared");
                }
                h.assertFalse(MobPowerEffects.outsider(p, owner) || MobPowerEffects.outsider(p, ally) || MobPowerEffects.outsider(p, newcomer) || MobPowerEffects.outsider(p, builder),
                        "the owner, a Ward friend, a protected newcomer and a creative player are not outsiders");
                h.assertTrue(MobPowerEffects.outsider(p, stranger) && MobPowerEffects.outsider(p, distant), "the stranger is, near or far");
            } finally {
                protection.applySettings(mode, rank, hours);
            }
        });
    }

    private static boolean pearl(ServerPlayer player, BlockPos to) {
        var pearl = new net.minecraft.world.entity.projectile.ThrownEnderpearl(player.serverLevel(), player);
        var hit = net.minecraft.world.phys.BlockHitResult.miss(net.minecraft.world.phys.Vec3.atCenterOf(to), net.minecraft.core.Direction.UP, to);
        return net.minecraftforge.event.ForgeEventFactory.onEnderPearlLand(player, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, pearl, 5f, hit).isCanceled();
    }

    private static boolean chorus(ServerPlayer player, BlockPos to) {
        return net.minecraftforge.event.ForgeEventFactory.onChorusFruitTeleport(player, to.getX() + 0.5, to.getY(), to.getZ() + 0.5).isCanceled();
    }

    @GameTest(template = EMPTY, batch = BATCH + "_warp", timeoutTicks = 200)
    public void warp_ward_holds_outsiders_in_and_keeps_them_out(GameTestHelper h) {
        ServerPlayer owner = playerAt(h, PROJECTOR.east(4)), inside = playerAt(h, PROJECTOR.east(4)), outside = playerAt(h, PROJECTOR.west(4)),
                builder = playerAt(h, PROJECTOR.east(4));
        BlockPos farAbs = h.absolutePos(PROJECTOR).offset(-40, 0, 0), farAbs2 = h.absolutePos(PROJECTOR).offset(-40, 0, 20), nearAbs = h.absolutePos(PROJECTOR.east(6));
        outside.moveTo(farAbs.getX() + 0.5, farAbs.getY(), farAbs.getZ() + 0.5);
        builder.setGameMode(GameType.CREATIVE);
        PlasmaAccess access = new PlasmaAccess();
        access.editors.add(owner.getUUID());
        withProjector(h, offered(Buff.WARP_WARD, 1), access, p -> {
            ServerLevel sl = h.getLevel();
            h.assertTrue(pearl(inside, farAbs) && chorus(inside, farAbs), "an outsider in reach cannot pearl or chorus out");
            h.assertTrue(com.mrgregles.bsp_core.api.BspWarpWard.why(inside, sl, farAbs).getString().contains("holds you here"), "held in, in BSP-Core's words");
            h.assertTrue(pearl(outside, nearAbs) && chorus(outside, nearAbs), "an outsider cannot pearl or chorus in");
            h.assertTrue(com.mrgregles.bsp_core.api.BspWarpWard.why(outside, sl, nearAbs).getString().contains("keeps you out"), "kept out");
            h.assertFalse(pearl(outside, farAbs2) || com.mrgregles.bsp_core.api.BspWarpWard.blocks(outside, sl, farAbs2), "outside to outside is left alone");
            h.assertFalse(pearl(owner, farAbs) || chorus(owner, farAbs) || pearl(owner, nearAbs), "the owner comes and goes");
            h.assertFalse(pearl(builder, farAbs), "a player in creative too");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH + "_wireframe", timeoutTicks = 200)
    public void wireframe_shows_outsiders_to_the_team_in_reach_after_shroud(GameTestHelper h) {
        ServerPlayer owner = playerAt(h, PROJECTOR.east(4)), mate = playerAt(h, PROJECTOR.east(4)), stranger = playerAt(h, PROJECTOR.west(4)),
                raider = playerAt(h, PROJECTOR.west(5)), far = playerAt(h, PROJECTOR.west(4));
        BlockPos away = h.absolutePos(PROJECTOR).offset(-40, 0, 0);
        far.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
        mate.moveTo(away.getX() + 0.5, away.getY(), away.getZ() + 3.5);
        ItemStack shrouded = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        com.mrgregles.bsp_core.totem.TotemUpgrades.setTier(shrouded, 4);
        com.mrgregles.bsp_core.totem.TotemUpgrades.setLevel(shrouded, Buff.SHROUD, 1);
        raider.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shrouded);
        PlasmaAccess access = new PlasmaAccess();
        access.editors.add(owner.getUUID());
        access.wardSafe.add(mate.getUUID());
        withProjector(h, offered(Buff.WIREFRAME, 1), access, p -> {
            ServerLevel sl = h.getLevel();
            var everyone = java.util.List.of(owner, mate, stranger, raider, far);
            long t = sl.getGameTime();
            int hidden = com.mrgregles.bsp_core.chamber.Wireframe.shroudTicks(raider);
            h.assertTrue(hidden > 0 && com.mrgregles.bsp_core.chamber.Wireframe.shroudTicks(stranger) == 0, "Shroud I in the offhand hides for a while: " + hidden + " ticks");
            var sight = com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, everyone, t);
            h.assertTrue(sight.size() == 1 && sight.containsKey(owner), "only the owner in reach sees: the teammate is out of reach");
            h.assertTrue(sight.get(owner).equals(java.util.List.of(stranger)), "the stranger in reach shows; the raider is shrouded; the far stranger is out of reach");
            sight = com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, everyone, t + hidden);
            h.assertTrue(sight.get(owner).contains(raider) && sight.get(owner).size() == 2, "when Shroud runs out the raider shows too");
            h.assertTrue(com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, java.util.List.of(stranger, raider, far), t + hidden).isEmpty(),
                    "with no team member in reach, nobody is shown anything");
            com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, java.util.List.of(owner, stranger), t + hidden + 20); // the raider stepped out
            sight = com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, everyone, t + hidden + 40);
            h.assertTrue(sight.get(owner).contains(raider), "a step out and back does not start Shroud again");
            long back = t + hidden + 40 + 60 * 20 + 1;
            com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, java.util.List.of(owner, stranger), back); // a minute away
            sight = com.mrgregles.bsp_core.chamber.Wireframe.sight(sl, p, everyone, back + 20);
            h.assertFalse(sight.get(owner).contains(raider), "after a minute out of reach, stepping back in starts Shroud again");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_store_keeps_mob_plasma_apart_by_amount(GameTestHelper h) {
        var store = new com.mrgregles.bsp_core.plasma.PlasmaStore(() -> 100_000L);
        store.add(1_000, offered(Buff.WARD, 1));
        long[][] haste = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
        haste[Buff.HASTE.ordinal()][3] = 100;
        store.add(100, null, haste);
        h.assertTrue(store.stored() == 1_100 && store.mob(Buff.HASTE, 3) == 100 && store.offered()[Buff.HASTE.ordinal()] == 0, "a trickle of Haste III is kept by amount, not as a label");
        long[][] out = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
        h.assertTrue(store.drain(550, out) == 550 && out[Buff.HASTE.ordinal()][3] == 50 && store.mob(Buff.HASTE, 3) == 50, "half out: half the Haste goes with it, no more");
        h.assertTrue(store.offered()[Buff.WARD.ordinal()] == 1, "the totem's Ward still rides the rest as before");
        var tag = new net.minecraft.nbt.CompoundTag();
        store.save(tag, "S");
        var back = new com.mrgregles.bsp_core.plasma.PlasmaStore(() -> 100_000L);
        back.load(tag, "S");
        h.assertTrue(back.mob(Buff.HASTE, 3) == 50 && back.stored() == 550, "saved and loaded");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tank_port_pools_each_kind_by_amount(GameTestHelper h) {
        BlockPos o = new BlockPos(3, 1, 3), port = o.offset(1, 2, 1);
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    int edges = (x == 0 || x == 2 ? 1 : 0) + (y == 0 || y == 2 ? 1 : 0) + (z == 0 || z == 2 ? 1 : 0);
                    BlockPos p = o.offset(x, y, z);
                    if (edges > 0) {
                        h.setBlock(p, p.equals(port) ? ModBlocks.TANK_PORT.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get());
                    }
                }
            }
        }
        h.succeedWhen(() -> {
            var tp = (com.mrgregles.bsp_core.tank.TankPortBlockEntity) h.getBlockEntity(port);
            var m = tp.masterEntity();
            h.assertTrue(m != null, "the tank formed");
            m.drain(m.stored());
            long[][] mob = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
            mob[Buff.GROWTH.ordinal()][2] = 300;
            tp.feed(h.absolutePos(o), offered(Buff.WARD, 1, Buff.GROWTH, 2), 0, 50, 1_000, new PlasmaAccess(), null, mob);
            h.assertTrue(m.pools().mbAt(Buff.GROWTH, 2) == 300 && m.pools().totem() == 700 && m.pools().mb(Buff.WARD) == 700 && m.pools().plain() == 0,
                    "300 mB to Growth II, the other 700 to the totem pool with Ward: " + m.pools().mbAt(Buff.GROWTH, 2) + " / " + m.pools().totem());
            m.drain(m.stored());
            m.drainMob(Buff.GROWTH, Long.MAX_VALUE);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH + "_room", timeoutTicks = 200)
    public void a_base_keeps_room_for_mob_plasma_and_gives_the_level_that_paid(GameTestHelper h) {
        BlockPos base = PROJECTOR.below();
        h.setBlock(base, ModBlocks.PROJECTOR_BASE.get());
        h.setBlock(PROJECTOR, ModBlocks.TOTEM_PROJECTOR.get());
        int use = com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.PROJECTOR_USE, 20) * 20;
        boolean[] first = {true};
        h.onEachTick(() -> {
            var pb = (com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity) h.getBlockEntity(base);
            var p = (TotemProjectorBlockEntity) h.getBlockEntity(PROJECTOR);
            // an interface keeps the base full of plain plasma with Fortify
            pb.feed(h.absolutePos(base.west()), offered(Buff.FORTIFY, 1), 0, 200, pb.capacity(), new PlasmaAccess(), null, null);
            if (first[0]) {
                first[0] = false;
                h.assertTrue(pb.wanted() == 0 && pb.wantedMob() >= 4 * use, "full of plain plasma, the base still has room for mob plasma: " + pb.wantedMob());
                long[][] mob = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
                mob[Buff.HASTE.ordinal()][3] = 1; // a trickle of Haste III
                mob[Buff.HASTE.ordinal()][1] = 4L * use; // beside plenty of Haste I
                pb.feed(h.absolutePos(base.east()), new int[Buff.values().length], 0, 200, (int) (4L * use + 1), new PlasmaAccess(), null, mob);
                h.assertTrue(pb.mob(Buff.HASTE, 1) == 4L * use, "the mob plasma went in beside the full plain tank");
            }
            if (p.isActive() && p.level(Buff.HASTE) > 0) {
                h.assertTrue(p.level(Buff.HASTE) == 1, "a level III trickle beside level I gives level I: " + p.level(Buff.HASTE));
                h.assertTrue(p.level(Buff.FORTIFY) > 0 || pb.offered()[Buff.FORTIFY.ordinal()] > 0, "the interface's Fortify is kept beside the extractor's feed");
                h.setBlock(PROJECTOR, Blocks.AIR);
                h.setBlock(base, Blocks.AIR);
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_never_carries_mob_powers_from_its_nbt(GameTestHelper h) {
        ItemStack totem = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        net.minecraft.nbt.CompoundTag up = new net.minecraft.nbt.CompoundTag();
        up.putInt(Buff.KEEP.key, 3);
        up.putInt(Buff.LANTERN.key, 3);
        totem.getOrCreateTag().put(com.mrgregles.bsp_core.totem.TotemUpgrades.TAG_UPGRADES, up);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemUpgrades.getLevel(totem, Buff.KEEP) == 0
                && com.mrgregles.bsp_core.totem.TotemUpgrades.getLevel(totem, Buff.LANTERN) == 0, "mob powers in a totem's NBT count for nothing");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH + "_base", timeoutTicks = 400)
    public void a_trickle_gives_its_power_only_while_its_own_plasma_lasts(GameTestHelper h) {
        BlockPos base = PROJECTOR.below();
        h.setBlock(base, ModBlocks.PROJECTOR_BASE.get());
        h.setBlock(PROJECTOR, ModBlocks.TOTEM_PROJECTOR.get());
        int use = com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.PROJECTOR_USE, 20) * 20;
        boolean[] first = {true}, sawHaste = {false};
        long[] emptyAt = {-1};
        h.onEachTick(() -> {
            var pb = (com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity) h.getBlockEntity(base);
            var p = (TotemProjectorBlockEntity) h.getBlockEntity(PROJECTOR);
            long[][] mob = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
            int moved = 200;
            if (first[0]) {
                mob[Buff.HASTE.ordinal()][3] = 2L * use; // two seconds of Haste III, among plenty of plain plasma
                moved += 2 * use;
                first[0] = false;
            }
            pb.feed(h.absolutePos(base.west()), new int[Buff.values().length], 0, 200, moved, new PlasmaAccess(), null, mob);
            long now = h.getLevel().getGameTime();
            if (p.isActive() && p.level(Buff.HASTE) == 3) {
                sawHaste[0] = true;
            }
            if (emptyAt[0] < 0 && pb.mob(Buff.HASTE, 3) == 0 && sawHaste[0]) {
                emptyAt[0] = now;
            }
            if (emptyAt[0] >= 0 && now - emptyAt[0] >= 45) {
                h.assertTrue(p.isActive() && p.level(Buff.HASTE) == 0, "its plasma used up, Haste stops while plain plasma keeps the projector going");
                h.setBlock(PROJECTOR, Blocks.AIR);
                h.setBlock(base, Blocks.AIR);
                h.succeed();
            }
        });
    }
}
