package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyKills;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.team.Alliances;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for the Bounty Board's rules and store (task 1). Batch {@code bounty}. Each test gives the bounties and the market
 * (for Collect boxes) blank stores of their own and a fixed clock, and puts the world's back before the tick ends.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyGameTests {
    private static final String EMPTY = "empty";
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS;

    /** Blank stores, a fixed clock and no staff or protected players for the length of {@code body}; all put back after. */
    private static void run(GameTestHelper h, long now, Body body) {
        BountyLedger ledger = new BountyLedger();
        BountyStore oldB = Bounties.useStore(new LocalBounties(ledger));
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        Bounties.useClock(() -> now);
        Bounties.staffCheck = (id, name) -> false;
        Bounties.protectedCheck = id -> false;
        try {
            body.run(h.getLevel().getServer(), ledger);
        } finally {
            Bounties.useStore(oldB);
            Market.useStore(oldM);
            Bounties.useClock(null);
            Bounties.staffCheck = null;
            Bounties.protectedCheck = null;
        }
        h.succeed();
    }

    private interface Body {
        void run(MinecraftServer server, BountyLedger ledger);
    }

    private static ServerPlayer player(GameTestHelper h, long pieces) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        Bank.ensure(server, p.getUUID(), p.getGameProfile().getName(), g -> {});
        Bank.set(server, p.getUUID(), pieces, "gametest", r -> {});
        return p;
    }

    private static UUID account(MinecraftServer server, String name, long pieces) {
        UUID id = UUID.randomUUID();
        Bank.ensure(server, id, name, g -> {});
        Bank.set(server, id, pieces, "gametest", r -> {});
        return id;
    }

    private static Bounties.Ask pieces(UUID target, long amount, long bonus, long within, long duration) {
        return new Bounties.Ask(target, "Target", Bounty.PIECES, amount, bonus, within, duration, "");
    }

    private static Bounties.Result place(MinecraftServer server, ServerPlayer placer, Bounties.Ask ask) {
        Bounties.Result[] r = new Bounties.Result[1];
        Bounties.place(server, placer, ask, x -> r[0] = x);
        return r[0];
    }

    private static Bounties.Result accept(MinecraftServer server, ServerPlayer hunter, String id) {
        Bounties.Result[] r = new Bounties.Result[1];
        Bounties.accept(server, hunter.getUUID(), hunter.getGameProfile().getName(), id, x -> r[0] = x);
        return r[0];
    }

    private static long collected(MinecraftServer server, UUID player, CoinTier tier) {
        return Market.store(server).peekCollect(server, player).stream().filter(s -> s.is(tier.coin())).mapToLong(ItemStack::getCount).sum();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_pieces_bounty_holds_reward_and_bonus_and_the_fee_goes_to_the_pot(GameTestHelper h) {
        long now = 1_800_000_000_000L;
        run(h, now, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000);
            UUID target = account(server, "Wanted", 0);
            long pot = Bank.pieces(server, Market.POT);
            Bounties.Ask ask = new Bounties.Ask(target, "Wanted", Bounty.PIECES, 45_000, 10_000, D, 7 * D, "At dusk §cin red§r, by the mesa.");
            Bounties.Result r = place(server, placer, ask);
            h.assertTrue(r != null && r.ok(), "the bounty is placed: " + r);
            Bounty b = r.bounty();
            long fee = Market.tax(55_000);
            h.assertTrue(fee == 2_750, "the fee is the market tax on reward and bonus: " + fee);
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == 100_000 - 55_000 - fee, "reward, bonus and fee are taken: " + Bank.pieces(server, placer.getUUID()));
            h.assertTrue(Bank.pieces(server, Market.POT) - pot == fee, "the fee goes into the lottery pot");
            h.assertTrue(b.target.equals(target) && b.placer.equals(placer.getUUID()) && b.amount == 45_000 && b.bonus == 10_000 && b.fee == fee
                    && b.placedMs == now && b.endsMs == now + 7 * D && b.bonusEndsMs == now + D && !b.coins() && b.hunters().isEmpty(), "the bounty's fields: " + b);
            h.assertTrue(b.notes.equals("At dusk in red, by the mesa."), "formatting codes are taken out of the notes: " + b.notes);
            h.assertTrue(Bounties.open(server).size() == 1 && ledger.get(b.id) != null, "the board holds it");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_coin_bounty_takes_coins_with_change_and_pays_its_fee_into_the_pot_as_pieces(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 0);
            placer.getInventory().add(new ItemStack(CoinTier.DIAMOND.coin(), 5));
            UUID target = account(server, "Coined", 0);
            long pot = Bank.pieces(server, Market.POT);
            int before = CoinWallet.totalValue(placer);
            long value = 4L * CoinTier.DIAMOND.value(); // 3 coins and a bonus of 1
            long feeCopper = Market.tax(value);
            Bounties.Result r = place(server, placer, new Bounties.Ask(target, "Coined", CoinTier.DIAMOND.ordinal(), 3, 1, 20 * H, 2 * D, ""));
            h.assertTrue(r != null && r.ok(), "the coin bounty is placed: " + r);
            h.assertTrue(CoinWallet.totalValue(placer) == before - value - feeCopper, "coins worth the reward, bonus and fee are taken, with change: "
                    + CoinWallet.totalValue(placer) + " of " + before);
            h.assertTrue(Bank.pieces(server, Market.POT) - pot == feeCopper * Bounties.piecesPerCopper(), "the coin fee reaches the pot as pieces");
            h.assertTrue(r.bounty().coins() && r.bounty().tier() == CoinTier.DIAMOND && r.bounty().amount == 3 && r.bounty().bonus == 1, "kept in coins: " + r.bounty());
            Bounties.Result poor = place(server, placer, new Bounties.Ask(target, "Coined", CoinTier.NETHERITE.ordinal(), 9, 0, 0, D, ""));
            h.assertTrue(poor != null && poor.key().equals("short"), "more coins than they hold is refused: " + poor);
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void bad_asks_and_targets_are_refused_and_nothing_is_taken(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 1_000_000);
            UUID target = account(server, "Fair", 0), staff = account(server, "Staffer", 0), fresh = account(server, "Newbie", 0);
            Bounties.staffCheck = (id, name) -> id.equals(staff);
            Bounties.protectedCheck = id -> id.equals(fresh);
            String[][] cases = {
                    {"self", key(server, placer, pieces(placer.getUUID(), 5_000, 0, 0, D))},
                    {"staff", key(server, placer, pieces(staff, 5_000, 0, 0, D))},
                    {"protected", key(server, placer, pieces(fresh, 5_000, 0, 0, D))},
                    {"time", key(server, placer, pieces(target, 5_000, 0, 0, H - 1))},
                    {"time", key(server, placer, pieces(target, 5_000, 0, 0, (Bounties.maxDays() + 1) * D))},
                    {"price", key(server, placer, pieces(target, Bounties.minPieces() - 1, 0, 0, D))},
                    {"bonus_time", key(server, placer, pieces(target, 5_000, 1_000, D, D))},
                    {"bonus_time", key(server, placer, pieces(target, 5_000, 1_000, 0, D))},
                    {"short", key(server, placer, pieces(target, 999_999, 0, 0, D))},
                    {"price", key(server, placer, new Bounties.Ask(target, "Fair", 9, 1, 0, 0, D, ""))},
            };
            for (String[] c : cases) {
                h.assertTrue(c[0].equals(c[1]), "expected " + c[0] + " but got " + c[1]);
            }
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == 1_000_000 && Bounties.open(server).isEmpty(), "nothing was taken or placed");
            for (int i = 0; i < Bounties.maxOpen(); i++) {
                h.assertTrue(key(server, placer, pieces(target, 1_000, 0, 0, D)).equals("ok"), "bounty " + (i + 1) + " is placed");
            }
            h.assertTrue(key(server, placer, pieces(target, 1_000, 0, 0, D)).equals("too_many"), "one more than bounty.maxOpenPerPlayer is refused");
        });
    }

    private static String key(MinecraftServer server, ServerPlayer placer, Bounties.Ask ask) {
        Bounties.Result r = place(server, placer, ask);
        return r == null ? "no answer" : r.key();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_target_the_placer_teammates_and_allies_cannot_accept_and_a_hunter_holds_at_most_five(GameTestHelper h) {
        ServerPlayer target = player(h, 0), mate = player(h, 0), allyLead = player(h, 0);
        try {
            run(h, 1_800_000_000_000L, (server, ledger) -> {
                ServerPlayer placer = player(h, 1_000_000), hunter = player(h, 0);
                h.assertTrue(Teams.create(target, "Bt" + Long.toString(System.nanoTime() % 1_000_000_000L, 36), ChatFormatting.RED).ok(), "the target's team");
                h.assertTrue(Teams.invite(target, mate).ok() && Teams.accept(mate).ok(), "a teammate joins");
                h.assertTrue(Teams.create(allyLead, "Ba" + Long.toString(System.nanoTime() % 1_000_000_000L, 36), ChatFormatting.BLUE).ok(), "a second team");
                boolean allied = Alliances.create(target, "Bp" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).ok()
                        && Alliances.invite(target, Teams.teamOf(server, allyLead.getUUID()).id, allyLead).ok() && Alliances.accept(allyLead).ok();
                h.assertTrue(allied, "the two teams are allies");
                Bounty b = place(server, placer, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 2_000, 0, 0, D, "")).bounty();
                h.assertTrue(accept(server, target, b.id).key().equals("target"), "the target cannot accept");
                h.assertTrue(accept(server, placer, b.id).key().equals("own"), "the placer cannot accept");
                h.assertTrue(accept(server, mate, b.id).key().equals("team"), "a teammate cannot accept");
                h.assertTrue(accept(server, allyLead, b.id).key().equals("ally"), "an ally cannot accept");
                Bounties.Result ok = accept(server, hunter, b.id);
                h.assertTrue(ok.ok() && ok.bounty().hasHunter(hunter.getUUID()), "anyone else accepts: " + ok.key());
                h.assertTrue(accept(server, hunter, b.id).key().equals("already"), "accepting twice is refused");
                UUID other = account(server, "Other", 0);
                for (int i = 1; i < Bounties.maxAccepted(); i++) {
                    Bounty more = place(server, player(h, 100_000), new Bounties.Ask(other, "O", Bounty.PIECES, 1_000, 0, 0, D, "")).bounty();
                    h.assertTrue(accept(server, hunter, more.id).ok(), "accepted " + (i + 1));
                }
                Bounty extra = place(server, player(h, 100_000), new Bounties.Ask(other, "O", Bounty.PIECES, 1_000, 0, 0, D, "")).bounty();
                h.assertTrue(accept(server, hunter, extra.id).key().equals("too_many"), "one more than bounty.maxAcceptedPerHunter is refused");
                Bounties.Result[] ab = new Bounties.Result[1];
                Bounties.abandon(server, hunter.getUUID(), b.id, x -> ab[0] = x);
                h.assertTrue(ab[0].ok() && !ab[0].bounty().hasHunter(hunter.getUUID()), "a hunter can drop a bounty");
                h.assertTrue(accept(server, hunter, extra.id).ok(), "which frees a place");
                Bounties.abandon(server, hunter.getUUID(), b.id, x -> ab[0] = x);
                h.assertTrue(ab[0].key().equals("not_hunter"), "dropping one not held is refused");
            });
        } finally {
            if (Alliances.allianceOf(h.getLevel().getServer(), target.getUUID()) != null) {
                Alliances.leave(target);
            }
            for (ServerPlayer p : List.of(target, allyLead)) {
                if (Teams.teamOf(p.server, p.getUUID()) != null) {
                    Teams.disband(p);
                }
            }
        }
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_placer_cancels_only_before_anyone_accepts_and_gets_reward_and_bonus_back_without_the_fee(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000), stranger = player(h, 0), hunter = player(h, 0);
            UUID target = account(server, "Cancelled", 0);
            Bounty b = place(server, placer, pieces(target, 20_000, 5_000, H, D)).bounty();
            long after = Bank.pieces(server, placer.getUUID());
            Bounties.Result[] r = new Bounties.Result[1];
            Bounties.cancel(server, stranger.getUUID(), b.id, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("not_yours"), "only the placer cancels");
            Bounties.cancel(server, placer.getUUID(), b.id, x -> r[0] = x);
            h.assertTrue(r[0].ok(), "the placer cancels: " + r[0].key());
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == after + 25_000, "reward and bonus come back, the fee does not");
            h.assertTrue(ledger.closed().get(0).ending() == BountyStore.Ending.CANCELLED, "the history says cancelled");
            Bounty taken = place(server, placer, pieces(target, 2_000, 0, 0, D)).bounty();
            h.assertTrue(accept(server, hunter, taken.id).ok(), "a hunter accepts the next one");
            Bounties.cancel(server, placer.getUUID(), taken.id, x -> r[0] = x);
            h.assertTrue(r[0].key().equals("accepted"), "once accepted it cannot be cancelled");
            // a coin bounty cancelled: the coins wait in the Collect box
            placer.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 10));
            Bounty c = place(server, placer, new Bounties.Ask(target, "C", CoinTier.GOLD.ordinal(), 4, 0, 0, D, "")).bounty();
            Bounties.cancel(server, placer.getUUID(), c.id, x -> r[0] = x);
            h.assertTrue(r[0].ok() && collected(server, placer.getUUID(), CoinTier.GOLD) == 4, "the coins wait in the Collect box: "
                    + Market.store(server).peekCollect(server, placer.getUUID()));
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_bounty_nobody_claims_gives_reward_and_bonus_back_when_it_runs_out(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, start, (server, ledger) -> {
            Bounties.useClock(() -> now[0]);
            ServerPlayer placer = player(h, 100_000);
            placer.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 20));
            UUID target = account(server, "Expiring", 0);
            Bounty p = place(server, placer, pieces(target, 10_000, 2_000, H, 3 * H)).bounty();
            Bounty c = place(server, placer, new Bounties.Ask(target, "E", CoinTier.COPPER.ordinal(), 6, 2, H, 5 * H, "")).bounty();
            long bank = Bank.pieces(server, placer.getUUID());
            now[0] = start + 3 * H - 1;
            h.assertTrue(Bounties.expireDue(server) == 0, "nothing ends before its time");
            now[0] = start + 3 * H;
            h.assertTrue(Bounties.expireDue(server) == 1 && Bank.pieces(server, placer.getUUID()) == bank + 12_000, "the pieces bounty ends and gives 12,000 back");
            now[0] = start + 6 * H;
            h.assertTrue(Bounties.expireDue(server) == 1 && collected(server, placer.getUUID(), CoinTier.COPPER) == 8, "the coin bounty ends and its 8 coins wait in the Collect box");
            h.assertTrue(Bounties.open(server).isEmpty() && ledger.closed().size() == 2 && ledger.closed().stream().allMatch(x -> x.ending() == BountyStore.Ending.EXPIRED),
                    "both are in the history as run out");
            h.assertTrue(p.id != null && c.id != null, "ids");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_claim_pays_the_hunter_and_the_bonus_only_in_time(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        run(h, start, (server, ledger) -> {
            BountyStore store = Bounties.store(server);
            ServerPlayer placer = player(h, 1_000_000), hunter = player(h, 0), other = player(h, 0);
            UUID target = account(server, "Hunted", 0);
            Bounty early = place(server, placer, pieces(target, 30_000, 10_000, D, 3 * D)).bounty();
            Bounty late = place(server, placer, pieces(target, 20_000, 5_000, H, 3 * D)).bounty();
            accept(server, hunter, early.id);
            accept(server, hunter, late.id);
            BountyStore.Answer[] a = new BountyStore.Answer[1];
            store.claim(server, early.id, other.getUUID(), "Other", start + H, x -> a[0] = x);
            h.assertTrue(a[0].key().equals("not_hunter"), "only a hunter who accepted is paid");
            store.claim(server, late.id, hunter.getUUID(), "Hunter", start + 3 * D, x -> a[0] = x);
            h.assertTrue(a[0].key().equals("ended"), "a kill at or after the end is not paid");
            long placerBank = Bank.pieces(server, placer.getUUID());
            store.claim(server, early.id, hunter.getUUID(), "Hunter", start + D - 1, x -> a[0] = x);
            h.assertTrue(a[0].ok() && a[0].bonusPaid() && Bank.pieces(server, hunter.getUUID()) == 40_000, "inside the bonus window: reward and bonus to the hunter");
            store.claim(server, late.id, hunter.getUUID(), "Hunter", start + 2 * H, x -> a[0] = x);
            h.assertTrue(a[0].ok() && !a[0].bonusPaid() && Bank.pieces(server, hunter.getUUID()) == 60_000, "after the bonus window: the reward only");
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == placerBank + 5_000, "the missed bonus goes back to the placer");
            store.claim(server, early.id, hunter.getUUID(), "Hunter", start + 2 * H, x -> a[0] = x);
            h.assertTrue(a[0].key().equals("gone"), "a bounty is claimed once");
            h.assertTrue(ledger.closed().get(0).ending() == BountyStore.Ending.CLAIMED && hunter.getUUID().equals(ledger.closed().get(0).hunter()), "the history names the hunter");
            // a coin bounty's reward waits in the hunter's Collect box
            placer.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 20));
            Bounty coins = place(server, placer, new Bounties.Ask(target, "H", CoinTier.GOLD.ordinal(), 5, 2, H, D, "")).bounty();
            accept(server, hunter, coins.id);
            store.claim(server, coins.id, hunter.getUUID(), "Hunter", start + 30 * 60_000L, x -> a[0] = x);
            h.assertTrue(a[0].ok() && collected(server, hunter.getUUID(), CoinTier.GOLD) == 7, "7 Gold coins wait in the hunter's Collect box");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void staff_endings_give_back_what_each_one_says(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            BountyStore store = Bounties.store(server);
            ServerPlayer placer = player(h, 1_000_000);
            UUID target = account(server, "Ended", 0);
            Set<String> seen = new HashSet<>();
            long bank = Bank.pieces(server, placer.getUUID());
            Bounty a = place(server, placer, pieces(target, 10_000, 0, 0, D)).bounty();
            Bounty b = place(server, placer, pieces(target, 10_000, 0, 0, D)).bounty();
            Bounty c = place(server, placer, pieces(target, 10_000, 0, 0, D)).bounty();
            long fee = a.fee;
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank - 3 * (10_000 + fee), "three bounties held");
            store.end(server, a.id, BountyStore.Ending.REMOVED_NO_REFUND, "Mod", x -> seen.add(x.key()));
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank - 3 * (10_000 + fee), "removed with no refund: nothing back");
            store.end(server, b.id, BountyStore.Ending.REMOVED, "Mod", x -> seen.add(x.key()));
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank - 2 * (10_000 + fee) - fee, "removed: the reward back, not the fee");
            store.end(server, c.id, BountyStore.Ending.TARGET_STAFF, "", x -> seen.add(x.key()));
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank - 10_000 - 2 * fee, "the target is staff: the reward and the fee back");
            h.assertTrue(seen.equals(Set.of("ok")) && Bounties.open(server).isEmpty(), "every ending answered ok and the board is empty");
            BountyStore.Answer[] g = new BountyStore.Answer[1];
            store.end(server, a.id, BountyStore.Ending.REMOVED, "Mod", x -> g[0] = x);
            h.assertTrue(g[0].key().equals("gone"), "a closed bounty cannot close again");
            // a staff bounty: placed by the server, nothing is taken and nothing goes back
            Bounty staffBounty = new Bounty("bstaff1", target, "Ended", Bounties.SERVER, Bounties.SERVER_NAME, Bounty.PIECES, 5_000, 0, 0, 0, Bounties.now(),
                    Bounties.now() + D, "", Bank.serverId(), List.of());
            String[] k = new String[1];
            store.place(server, staffBounty, x -> k[0] = x);
            store.end(server, staffBounty.id, BountyStore.Ending.REMOVED, "Mod", x -> {});
            h.assertTrue("ok".equals(k[0]) && Bank.pieces(server, Bounties.SERVER) == 0, "the server holds no bank and gets nothing back");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void notes_lose_codes_and_control_characters_and_are_cut_to_the_limit(GameTestHelper h) {
        String clean = Bounties.cleanNotes("  §lBold§r and\u0007 bell\nnext line  ");
        h.assertTrue(clean.equals("Bold and bell\nnext line"), "codes and the bell go, the line break stays: [" + clean + "]");
        String longOne = Bounties.cleanNotes("x".repeat(Bounties.notesMax() + 50));
        h.assertTrue(longOne.length() == Bounties.notesMax(), "cut to bounty.notesMax: " + longOne.length());
        h.assertTrue(Bounties.cleanNotes(null).isEmpty(), "no notes is empty notes");
        h.succeed();
    }

    // ------------------------------------------------------------------ task 2: the kill

    @GameTest(template = EMPTY, batch = "bounty")
    public void only_a_players_own_blow_or_a_fall_lava_fire_or_void_death_soon_after_it_counts_as_their_kill(GameTestHelper h) {
        ServerPlayer victim = MockPlayers.serverPlayer(h, GameType.SURVIVAL), hunter = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        var ds = h.getLevel().damageSources();
        Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
        Wolf wolf = EntityType.WOLF.create(h.getLevel());
        wolf.tame(hunter);
        Arrow arrow = new Arrow(h.getLevel(), hunter);
        PrimedTnt tnt = new PrimedTnt(h.getLevel(), 0, 0, 0, hunter);
        var fake = FakePlayerFactory.getMinecraft(h.getLevel());
        long t = 1000;
        BountyKills.Hit hunterHit = new BountyKills.Hit(hunter.getUUID(), "Hunter", t);
        java.util.function.BiFunction<DamageSource, BountyKills.Hit, UUID> who = (src, last) -> {
            BountyKills.Hit k = BountyKills.killer(victim, src, last, t + 10);
            return k == null ? null : k.attacker();
        };
        h.assertTrue(hunter.getUUID().equals(who.apply(ds.playerAttack(hunter), null)), "the hunter's own hit counts");
        h.assertTrue(hunter.getUUID().equals(who.apply(ds.arrow(arrow, hunter), null)), "the hunter's arrow counts");
        h.assertTrue(who.apply(ds.mobAttack(zombie), hunterHit) == null, "a mob's blow never counts, even right after the hunter's hit");
        h.assertTrue(who.apply(ds.mobAttack(wolf), hunterHit) == null, "the hunter's tamed wolf does not count");
        h.assertTrue(who.apply(ds.explosion(tnt, hunter), hunterHit) == null, "the hunter's TNT does not count");
        h.assertTrue(who.apply(ds.playerAttack(fake), null) == null, "a machine's fake player does not count");
        h.assertTrue(who.apply(ds.playerAttack(victim), null) == null, "a player is not their own killer");
        h.assertTrue(who.apply(ds.generic(), hunterHit) == null, "a death of any other kind does not count");
        h.assertTrue(who.apply(ds.lava(), null) == null, "lava with nobody's hit before it is nobody's kill");
        for (DamageSource env : List.of(ds.fall(), ds.lava(), ds.inFire(), ds.onFire(), ds.fellOutOfWorld())) {
            h.assertTrue(hunter.getUUID().equals(who.apply(env, hunterHit)), "a death by " + env.getMsgId() + " right after the hunter's hit counts for them");
        }
        h.assertTrue(BountyKills.killer(victim, ds.fall(), hunterHit, t + BountyKills.ASSIST_TICKS) != null, "5 seconds after the hit still counts");
        h.assertTrue(BountyKills.killer(victim, ds.fall(), hunterHit, t + BountyKills.ASSIST_TICKS + 1) == null, "later does not");
        // what is remembered between the hits: the hunter, kept through a fall; a mob's hit after it takes the hunter's place
        BountyKills.Hit last = BountyKills.afterDamage(victim, ds.playerAttack(hunter), null, t);
        last = BountyKills.afterDamage(victim, ds.fall(), last, t + 5);
        h.assertTrue(last != null && hunter.getUUID().equals(last.attacker()) && last.tick() == t, "a fall keeps the hunter's hit: " + last);
        last = BountyKills.afterDamage(victim, ds.mobAttack(zombie), last, t + 6);
        h.assertTrue(last != null && last.attacker() == null, "a mob's hit after it is remembered instead");
        h.assertTrue(BountyKills.killer(victim, ds.lava(), last, t + 8) == null, "so lava after the mob's hit is nobody's kill");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_kill_pays_every_bounty_that_hunter_accepted_on_the_target_and_leaves_the_rest_open(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer target = player(h, 0), hunter = player(h, 0), rival = player(h, 0);
            ServerPlayer p1 = player(h, 100_000), p2 = player(h, 100_000), p3 = player(h, 100_000);
            Bounty a = place(server, p1, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 10_000, 0, 0, D, "")).bounty();
            Bounty b = place(server, p2, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 7_000, 0, 0, D, "")).bounty();
            Bounty c = place(server, p3, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 3_000, 0, 0, D, "")).bounty();
            accept(server, hunter, a.id);
            accept(server, rival, b.id);
            accept(server, hunter, c.id);
            // through the handlers: the hunter hits the target, then the blow that kills
            DamageSource blow = h.getLevel().damageSources().playerAttack(hunter);
            BountyKills.onDamage(new LivingDamageEvent(target, blow, 4f));
            BountyKills.onDeath(new LivingDeathEvent(target, blow));
            h.assertTrue(Bank.pieces(server, hunter.getUUID()) == 13_000, "the hunter is paid both bounties they accepted: " + Bank.pieces(server, hunter.getUUID()));
            h.assertTrue(Bounties.open(server).size() == 1 && Bounties.open(server).get(0).id.equals(b.id), "the rival's bounty stays open");
            h.assertTrue(Bank.pieces(server, rival.getUUID()) == 0, "the rival gets nothing for a kill they did not make");
            // a zombie kills the target right after the rival's hit: nothing is claimed
            BountyKills.onDamage(new LivingDamageEvent(target, h.getLevel().damageSources().playerAttack(rival), 4f));
            Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
            BountyKills.onDeath(new LivingDeathEvent(target, h.getLevel().damageSources().mobAttack(zombie)));
            h.assertTrue(Bounties.open(server).size() == 1 && Bank.pieces(server, rival.getUUID()) == 0, "a mob's kill claims nothing");
            // the rival knocks the target off a ledge: the fall counts for them
            BountyKills.onDamage(new LivingDamageEvent(target, h.getLevel().damageSources().playerAttack(rival), 4f));
            BountyKills.onDeath(new LivingDeathEvent(target, h.getLevel().damageSources().fall()));
            h.assertTrue(Bounties.open(server).isEmpty() && Bank.pieces(server, rival.getUUID()) == 7_000, "the fall right after the rival's hit pays the rival");
            h.assertTrue(ledger.closed().stream().filter(x -> x.ending() == BountyStore.Ending.CLAIMED).count() == 3, "three claims in the history");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_kill_after_the_end_claims_nothing(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, start, (server, ledger) -> {
            Bounties.useClock(() -> now[0]);
            ServerPlayer target = player(h, 0), hunter = player(h, 0), placer = player(h, 100_000);
            Bounty a = place(server, placer, new Bounties.Ask(target.getUUID(), "T", Bounty.PIECES, 5_000, 0, 0, 2 * H, "")).bounty();
            accept(server, hunter, a.id);
            now[0] = start + 2 * H;
            int[] n = {-1};
            Bounties.claimKill(server, target.getUUID(), "T", hunter.getUUID(), "H", Bounties.now(), x -> n[0] = x);
            h.assertTrue(n[0] == 0 && Bank.pieces(server, hunter.getUUID()) == 0, "a bounty past its end pays nothing, even before the sweep closes it");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_target_who_is_staff_at_login_has_their_bounties_ended_with_everything_back(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000);
            UUID target = account(server, "Promoted", 0);
            long bank = Bank.pieces(server, placer.getUUID());
            Bounty a = place(server, placer, pieces(target, 10_000, 2_000, H, D)).bounty();
            h.assertTrue(Bounties.endIfStaff(server, target, "Promoted") == 0 && Bounties.open(server).size() == 1, "not staff: the bounty stays");
            Bounties.staffCheck = (id, name) -> id.equals(target);
            h.assertTrue(Bounties.endIfStaff(server, target, "Promoted") == 1 && Bounties.open(server).isEmpty(), "staff now: the bounty ends");
            h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank, "reward, bonus and fee all came back: " + Bank.pieces(server, placer.getUUID()) + " of " + bank);
            h.assertTrue(ledger.closed().get(0).ending() == BountyStore.Ending.TARGET_STAFF && a.fee > 0, "the history says why");
        });
    }
}
