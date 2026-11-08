package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryBlock;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinFactoryMenu;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.FactoryCap;
import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * GameTests for the team factory limit: the number of Coin Factory slices each player may own by their team's size, the refusal
 * when placing over it, the newest slices pausing over it and pressing again under it, a slice that was unloaded when the team grew,
 * and the ranking with slices on other servers of a network. Every test makes its own players and team, and removes its ledger rows.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class FactoryCapGameTests {
    private static final String EMPTY = "empty", BATCH = "factory_cap";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    private static String name(String prefix) {
        String n = prefix + " " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        return n.substring(0, Math.min(16, n.length()));
    }

    /** A team of {@code leader} and {@code mates}. */
    private static Team team(GameTestHelper h, ServerPlayer leader, ServerPlayer... mates) {
        h.assertTrue(Teams.create(leader, name("Cap"), ChatFormatting.GOLD).ok(), "a team is made");
        for (ServerPlayer m : mates) {
            h.assertTrue(Teams.invite(leader, m).ok() && Teams.accept(m).ok(), "a mate joins");
        }
        return Teams.teamOf(server(h), leader.getUUID());
    }

    private static void disband(ServerPlayer leader) {
        if (Teams.teamOf(leader.server, leader.getUUID()) != null) {
            Teams.disband(leader);
        }
    }

    /** A ledger row for a slice that is not really there, far outside the test's structure. */
    private static GlobalPos fake(GameTestHelper h, int i) {
        return GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(200 + i, 1, 200)));
    }

    private static GlobalPos at(GameTestHelper h, BlockPos rel) {
        return GlobalPos.of(h.getLevel().dimension(), h.absolutePos(rel));
    }

    /** A formed factory slice with its controller at {@code c}, facing north, owned by {@code owner}. */
    private static CoinFactoryBlockEntity slice(GameTestHelper h, BlockPos c, ServerPlayer owner) {
        h.setBlock(c, ModBlocks.COIN_FACTORY.get().defaultBlockState().setValue(CoinFactoryBlock.FACING, Direction.NORTH));
        h.setBlock(c.south(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.south(2), ModBlocks.FACTORY_BLANK_HATCH.get());
        h.setBlock(c.above(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.above().south(), ModBlocks.FACTORY_PRESS.get());
        h.setBlock(c.above().south(2), ModBlocks.FACTORY_POWER_PORT.get());
        BlockEntity be = h.getBlockEntity(c);
        h.assertTrue(be instanceof CoinFactoryBlockEntity, "a factory controller at " + c);
        CoinFactoryBlockEntity f = (CoinFactoryBlockEntity) be;
        f.setOwner(owner);
        return f;
    }

    /** Fills the slice's pool and lays a Copper blank on its line. */
    private static void feed(CoinFactoryBlockEntity f) {
        CoinTier tier = CoinTier.values()[0];
        for (int i = 0; i < 1000 && f.poolStored() < tier.energyPerCoin(); i++) {
            f.poolReceive(Integer.MAX_VALUE, false);
        }
        f.getItems().setStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT, new ItemStack(ModItems.BLANKS.get(tier).get()));
    }

    private static void pollThen(GameTestHelper h, int ticks, BooleanSupplier ok, Runnable then, Runnable fail) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            fail.run();
        } else {
            h.runAfterDelay(1, () -> pollThen(h, ticks - 1, ok, then, fail));
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void team_factory_limit_follows_the_team_size(GameTestHelper h) {
        int[] want = {10, 8, 7, 6, 5, 5};
        for (int size = 1; size <= want.length; size++) {
            h.assertTrue(FactoryCap.capFor(size) == want[size - 1], "a team of " + size + " may own " + want[size - 1] + " each, got " + FactoryCap.capFor(size));
        }
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h), d = survivor(h);
        try {
            h.assertTrue(FactoryCap.teamSize(server, a.getUUID()) == 1 && FactoryCap.cap(server, a.getUUID()) == 10, "alone: 10");
            team(h, a, b, c, d);
            h.assertTrue(FactoryCap.teamSize(server, b.getUUID()) == 4 && FactoryCap.cap(server, b.getUUID()) == 6,
                    "a member of a team of 4: 6, got " + FactoryCap.cap(server, b.getUUID()) + " at size " + FactoryCap.teamSize(server, b.getUUID()));
            h.assertTrue(Teams.leave(d).ok(), "d leaves");
            h.assertTrue(FactoryCap.cap(server, a.getUUID()) == 7 && FactoryCap.cap(server, d.getUUID()) == 10, "the team of 3 takes 7 and d alone takes 10 again");
        } finally {
            disband(a);
        }
        h.succeed();
    }

    /** Placing a controller is refused at the team's limit, and allowed once under it; the factory screen gets the owner's limit and team size from the server. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void placing_a_factory_is_refused_at_the_team_limit(GameTestHelper h) {
        MinecraftServer server = server(h);
        FactoryLedger ledger = FactoryLedger.get(server);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h), d = survivor(h);
        List<GlobalPos> fakes = new ArrayList<>();
        BlockPos spot = new BlockPos(2, 1, 2);
        try {
            team(h, a, b, c, d); // 6 each
            for (int i = 0; i < 6; i++) {
                GlobalPos p = fake(h, i);
                fakes.add(p);
                ledger.add(a.getUUID(), p, 1000 + i);
            }
            h.setBlock(spot.below(), Blocks.STONE); // clicked on its top face
            ItemStack stack = new ItemStack(ModItems.COIN_FACTORY.get(), 2);
            a.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos ground = h.absolutePos(spot.below());
            UseOnContext use = new UseOnContext(a, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
            stack.useOn(use);
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(spot)).isAir() && stack.getCount() == 2, "the seventh slice in a team of 4 is refused");
            ledger.remove(fakes.remove(fakes.size() - 1));
            stack.useOn(new UseOnContext(a, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false)));
            h.assertTrue(h.getBlockEntity(spot) instanceof CoinFactoryBlockEntity f && a.getUUID().equals(f.getOwner()) && stack.getCount() == 1,
                    "the sixth is placed once one is removed");
            h.assertTrue(ledger.count(a.getUUID()) == 6, "a owns 6, got " + ledger.count(a.getUUID()));
            CoinFactoryBlockEntity f = (CoinFactoryBlockEntity) h.getBlockEntity(spot);
            CoinFactoryMenu menu = new CoinFactoryMenu(0, b.getInventory(), f, List.of(f));
            h.assertTrue(menu.ownedCount() == 6 && menu.ownerCap() == 6 && menu.ownerTeamSize() == 4,
                    "the screen reads 6 of 6, team of 4 (for a teammate looking too), got " + menu.ownedCount() + " of " + menu.ownerCap() + ", team of " + menu.ownerTeamSize());
        } finally {
            h.setBlock(spot, Blocks.AIR);
            h.setBlock(spot.below(), Blocks.AIR);
            fakes.forEach(ledger::remove);
            disband(a);
        }
        h.succeed();
    }

    /** Over the limit the newest slice pauses and starts nothing, even with time passing; when a member leaves it presses again. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void newest_slice_over_the_team_limit_pauses_and_presses_again_when_the_team_shrinks(GameTestHelper h) {
        MinecraftServer server = server(h);
        FactoryLedger ledger = FactoryLedger.get(server);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h), d = survivor(h), e = survivor(h);
        List<GlobalPos> fakes = new ArrayList<>();
        BlockPos cx = new BlockPos(3, 1, 3), cy = new BlockPos(8, 1, 3);
        Runnable cleanup = () -> {
            h.setBlock(cx, Blocks.AIR);
            h.setBlock(cy, Blocks.AIR);
            fakes.forEach(ledger::remove);
            disband(a);
        };
        try {
            team(h, a, b, c, d, e); // 5 each
            for (int i = 0; i < 4; i++) {
                GlobalPos p = fake(h, i);
                fakes.add(p);
                ledger.add(a.getUUID(), p, 1000 + i);
            }
            CoinFactoryBlockEntity x = slice(h, cx, a), y = slice(h, cy, a);
            ledger.add(a.getUUID(), at(h, cx), 5000); // the fifth: within the limit
            ledger.add(a.getUUID(), at(h, cy), 6000); // the sixth and newest: over it
            feed(x);
            feed(y);
            pollThen(h, 100, () -> y.isOverCap() && x.isFormed() && x.getJobTier() != null, () -> {
                h.assertFalse(x.isOverCap(), "the older slice keeps pressing");
                h.assertTrue(y.stateCode() == CoinFactoryBlockEntity.STATE_OVER_CAP && !y.isWorking(), "the newest shows it is over the team limit");
                y.advance(System.currentTimeMillis() + 600_000);
                h.assertTrue(y.getJobTier() == null && y.progressFraction() == 0f, "a paused slice starts no press, however long it waits");
                h.assertTrue(Teams.leave(e).ok(), "e leaves: a team of 4 owns 6 each");
                pollThen(h, 100, () -> !y.isOverCap() && y.getJobTier() != null, () -> {
                    cleanup.run();
                    h.succeed();
                }, () -> {
                    cleanup.run();
                    h.fail("under the limit again the slice presses: over " + y.isOverCap() + ", job " + y.getJobTier());
                });
            }, () -> {
                cleanup.run();
                h.fail("the sixth slice in a team of 5 pauses: over " + y.isOverCap() + ", x over " + x.isOverCap() + ", x job " + x.getJobTier());
            });
        } catch (RuntimeException ex) {
            cleanup.run();
            throw ex;
        }
    }

    /**
     * A slice unloaded while its owner's team grew counts its press only up to the moment the team reached that size: on load it does
     * not catch up the whole time away and then pause.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void a_slice_unloaded_when_the_team_grew_counts_its_press_only_until_then(GameTestHelper h) {
        MinecraftServer server = server(h);
        FactoryLedger ledger = FactoryLedger.get(server);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        List<GlobalPos> fakes = new ArrayList<>();
        BlockPos cy = new BlockPos(5, 1, 3);
        Runnable cleanup = () -> {
            h.setBlock(cy, Blocks.AIR);
            fakes.forEach(ledger::remove);
            disband(a);
        };
        try {
            Team team = team(h, a, b); // 8 each
            for (int i = 0; i < 7; i++) {
                GlobalPos p = fake(h, i);
                fakes.add(p);
                ledger.add(a.getUUID(), p, 1000 + i);
            }
            CoinFactoryBlockEntity y = slice(h, cy, a); // the eighth and newest
            feed(y);
            pollThen(h, 100, () -> y.isFormed() && y.getJobTier() != null, () -> {
                h.assertFalse(y.isOverCap(), "eight in a team of 2 is within the limit");
                long now = System.currentTimeMillis(), away = 600_000, grewAgo = 300_000;
                var tag = y.saveWithFullMetadata();
                tag.putLong("LastUpdateMs", now - away); // saved and unloaded ten minutes ago
                tag.putDouble("ProgressMs", 0);
                // c joined five minutes ago, while the slice was away: a team of 3 owns 7 each
                h.assertTrue(Teams.invite(a, c).ok() && Teams.accept(c).ok(), "c joins");
                // a and b were in it an hour before (the test made them moments ago: their join times are set back too)
                for (var entry : List.copyOf(team.members().entrySet())) {
                    Team.Member m = entry.getValue();
                    long joined = entry.getKey().equals(c.getUUID()) ? now - grewAgo : now - 3_600_000;
                    team.members().put(entry.getKey(), new Team.Member(m.name, joined, m.readSeq));
                }
                BlockPos abs = h.absolutePos(cy);
                var fresh = (CoinFactoryBlockEntity) BlockEntity.loadStatic(abs, h.getLevel().getBlockState(abs), tag);
                h.assertTrue(fresh != null, "the slice loads from its saved data");
                h.getLevel().setBlockEntity(fresh);
                double press = CoinTier.values()[0].pressMillis();
                pollThen(h, 60, fresh::isOverCap, () -> {
                    double counted = fresh.progressFraction() * press;
                    h.assertTrue(Math.abs(counted - (away - grewAgo)) < 15_000,
                            "the five minutes before the team grew are counted and not the five after, got " + Math.round(counted / 1000) + " s");
                    cleanup.run();
                    h.succeed();
                }, () -> {
                    cleanup.run();
                    h.fail("the reloaded slice pauses in a team of 3");
                });
            }, () -> {
                cleanup.run();
                h.fail("the slice starts a press: job " + y.getJobTier());
            });
        } catch (RuntimeException ex) {
            cleanup.run();
            throw ex;
        }
    }

    /** With slices on other servers, every server ranks the owner's slices by placing time the same way, and only the newest beyond the limit pause. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void slices_on_other_servers_count_and_rank_by_placing_time(GameTestHelper h) {
        MinecraftServer server = server(h);
        FactoryLedger ledger = FactoryLedger.get(server);
        UUID owner = UUID.randomUUID(); // in no team: 10
        GlobalPos older = fake(h, 20), newer = fake(h, 21);
        try {
            List<FactoryLedger.Slice> elsewhere = new ArrayList<>();
            long[] times = {50, 100, 200, 300, 600, 700, 800, 900, 10_000};
            for (int i = 0; i < times.length; i++) {
                elsewhere.add(new FactoryLedger.Slice(times[i], "other", "minecraft:overworld", BlockPos.asLong(i, 64, 0)));
            }
            NetworkStorage.slicesElsewhereForTest(owner, elsewhere);
            ledger.add(owner, older, 500);
            ledger.add(owner, newer, 9_000);
            h.assertTrue(ledger.count(owner) == 11, "nine elsewhere and two here, got " + ledger.count(owner));
            h.assertFalse(FactoryCap.overCap(server, owner, older) || FactoryCap.overCap(server, owner, newer),
                    "both here are within the ten oldest: the other server's newest is the one over");
            ledger.add(owner, newer, 20_000);
            h.assertTrue(FactoryCap.overCap(server, owner, newer) && !FactoryCap.overCap(server, owner, older), "placed after the other server's newest, it is the one over");
            NetworkStorage.slicesElsewhereForTest(owner, elsewhere.subList(0, 8));
            h.assertFalse(FactoryCap.overCap(server, owner, newer), "a slice removed elsewhere brings it back under");
        } finally {
            NetworkStorage.slicesElsewhereForTest(owner, null);
            ledger.remove(older);
            ledger.remove(newer);
        }
        h.succeed();
    }

    /** The pause starts at the join that put a slice over the limit, not at the latest join (a 4th member after the 3rd changes nothing). */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_slice_goes_over_at_the_join_that_crossed_the_limit(GameTestHelper h) {
        MinecraftServer server = server(h);
        FactoryLedger ledger = FactoryLedger.get(server);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h), d = survivor(h);
        List<GlobalPos> fakes = new ArrayList<>();
        try {
            Team team = team(h, a, b, c, d); // 6 each
            for (int i = 0; i < 8; i++) { // the eighth (rank 7) is within 8 (a team of 2) and over 7 (a team of 3)
                GlobalPos p = fake(h, 30 + i);
                fakes.add(p);
                ledger.add(a.getUUID(), p, 1000 + i);
            }
            long now = System.currentTimeMillis();
            long[] joins = {now - 3 * 3_600_000L, now - 3 * 3_600_000L + 1, now - 2 * 3_600_000L, now - 3_600_000L};
            ServerPlayer[] who = {a, b, c, d};
            for (int i = 0; i < who.length; i++) {
                Team.Member m = team.members().get(who[i].getUUID());
                team.members().put(who[i].getUUID(), new Team.Member(m.name, joins[i], m.readSeq));
            }
            h.assertTrue(FactoryCap.overSince(server, a.getUUID(), fakes.get(7)) == joins[2], "the eighth slice went over when the third member joined");
            h.assertTrue(FactoryCap.overSince(server, a.getUUID(), fakes.get(6)) == joins[3], "the seventh went over when the fourth joined");
        } finally {
            fakes.forEach(ledger::remove);
            disband(a);
        }
        h.succeed();
    }
}
