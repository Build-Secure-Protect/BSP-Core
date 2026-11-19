package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.api.BspBounties;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.BountySummary;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.network.BountySummaryPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * GameTests for the staff side of bounties (task 4): staff add, remove, extend and blank notes; who may; the public api; the
 * summary every client gets. Batch {@code bounty}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyStaffGameTests {
    private static final String EMPTY = "empty";
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS;

    private interface Body {
        void run(MinecraftServer server, BountyLedger ledger);
    }

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

    private static ServerPlayer player(GameTestHelper h, long pieces) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MinecraftServer server = h.getLevel().getServer();
        Bank.ensure(server, p.getUUID(), p.getGameProfile().getName(), g -> {});
        Bank.set(server, p.getUUID(), pieces, "gametest", r -> {});
        return p;
    }

    private static BspBounties.Result[] slot() {
        return new BspBounties.Result[1];
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void staff_add_a_server_bounty_remove_with_or_without_refund_extend_to_the_cap_and_blank_notes(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        run(h, start, (server, ledger) -> {
            UUID target = UUID.randomUUID(), staffer = UUID.randomUUID();
            Bounties.staffCheck = (id, name) -> id.equals(staffer);
            BspBounties.Result[] r = slot();
            BspBounties.add(server, "Mod (panel)", target, "Wanted", -1, 25_000, 5_000, D, 3 * D, "Spawn event", x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].bounty().placer().equals(BspBounties.SERVER) && r[0].bounty().placerName().equals("Server") && r[0].bounty().fee() == 0,
                    "a staff bounty is the server's, with no fee: " + r[0]);
            h.assertTrue(Bank.pieces(server, Bounties.SERVER) == 0, "nothing was taken from anyone");
            BspBounties.add(server, "Mod", staffer, "Staffer", -1, 25_000, 0, 0, D, "", x -> r[0] = x);
            h.assertTrue(r[0].key().equals("staff"), "staff cannot be targets, even for staff: " + r[0].key());
            String id = BspBounties.open(server).get(0).id();
            // extend: +7 d from 3 d is 10 d; then +30 d stops at 30 d from now; then it is already at the cap
            BspBounties.extend(server, id, 7 * D, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].bounty().endsMs() == start + 10 * D, "extended by 7 days");
            BspBounties.extend(server, id, 30 * D, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].bounty().endsMs() == start + Bounties.maxDays() * D, "never past bounty.maxDays from now");
            BspBounties.extend(server, id, H, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].key().equals("max"), "already at the cap: " + r[0].key());
            BspBounties.extend(server, id, 0, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].key().equals("time"), "no time is no extension");
            BspBounties.blankNotes(server, id, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].ok() && BspBounties.find(server, id).get().notes().isEmpty(), "the notes are blank");
            // remove a player's bounty: with a refund the reward and bonus come back, without nothing
            ServerPlayer placer = player(h, 100_000);
            Bounties.Result[] pr = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(target, "Wanted", Bounty.PIECES, 10_000, 2_000, H, D, ""), x -> pr[0] = x);
            Bounties.place(server, placer, new Bounties.Ask(target, "Wanted", Bounty.PIECES, 10_000, 0, 0, D, ""), x -> pr[0] = x);
            long bank = Bank.pieces(server, placer.getUUID());
            List<BspBounties.Info> theirs = BspBounties.placedBy(server, placer.getUUID());
            h.assertTrue(theirs.size() == 2, "two bounties placed by the player");
            BspBounties.Info withBonus = theirs.stream().filter(i -> i.bonus() > 0).findFirst().get(), plain = theirs.stream().filter(i -> i.bonus() == 0).findFirst().get();
            BspBounties.remove(server, withBonus.id(), true, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].ok() && Bank.pieces(server, placer.getUUID()) == bank + 12_000, "removed with a refund: 12,000 back");
            BspBounties.remove(server, plain.id(), false, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].ok() && Bank.pieces(server, placer.getUUID()) == bank + 12_000, "removed with no refund: nothing back");
            BspBounties.remove(server, plain.id(), false, "Mod", x -> r[0] = x);
            h.assertTrue(r[0].key().equals("gone"), "a removed bounty is gone");
            List<BspBounties.Closed> hist = BspBounties.history(server);
            h.assertTrue(hist.size() == 2 && hist.get(0).ending().equals("REMOVED_NO_REFUND") && hist.get(1).ending().equals("REMOVED")
                    && hist.get(0).by().equals("Mod"), "the history names how and who: " + hist);
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_player_who_is_not_staff_may_not_change_bounties(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            Admins.bountyPermissionSource = x -> true;
            h.assertFalse(Admins.canBounty(p), "a player who is not even a moderator may not, whatever the permission says");
        } finally {
            Admins.bountyPermissionSource = null;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_api_reads_notes_and_hunters_and_lists_known_players_online_first(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, start, (server, ledger) -> {
            Bounties.useClock(() -> now[0]);
            ServerPlayer placer = player(h, 100_000), hunter = player(h, 0);
            UUID target = UUID.randomUUID();
            String tag = "Bq" + Long.toString(System.nanoTime() % 100_000_000L, 36);
            Bank.ensure(server, target, tag + "Wanted", g -> {});
            Bounties.Result[] pr = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(target, tag + "Wanted", Bounty.PIECES, 5_000, 0, 0, 2 * H, "Hides in the mine"), x -> pr[0] = x);
            Bounties.accept(server, hunter.getUUID(), "Hunter", pr[0].bounty().id, x -> {});
            BspBounties.Info info = BspBounties.open(server).get(0);
            h.assertTrue(info.notes().equals("Hides in the mine") && info.hunters().size() == 1 && info.hunters().get(0).id().equals(hunter.getUUID())
                    && info.placer().equals(placer.getUUID()) && info.coinTier() == -1 && !info.coins(), "the api gives everything, notes included: " + info);
            h.assertTrue(BspBounties.huntedBy(server, hunter.getUUID()).size() == 1 && BspBounties.on(server, target).size() == 1, "by hunter and by target");
            now[0] = start + 2 * H;
            h.assertTrue(BspBounties.open(server).isEmpty(), "a bounty past its end is left out before the sweep closes it");
            List<BspBounties.Player>[] got = new List[1];
            BspBounties.players(server, tag.toLowerCase(java.util.Locale.ROOT), x -> got[0] = x);
            h.assertTrue(got[0] != null && got[0].size() == 1 && got[0].get(0).id().equals(target) && !got[0].get(0).online(), "the search finds the bank's name: " + got[0]);
            BspBounties.players(server, "", x -> got[0] = x);
            h.assertTrue(got[0].stream().noneMatch(p -> p.id().equals(Market.POT) || p.id().equals(Bounties.SERVER)), "the lottery pot and the server are not players");
            h.assertTrue(BspBounties.targetRefusal(server, target, tag + "Wanted") == null, "a fair target");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_summary_leaves_out_notes_and_ended_bounties_sorts_by_worth_and_travels_whole(GameTestHelper h) {
        long start = 1_800_000_000_000L;
        long[] now = {start};
        run(h, start, (server, ledger) -> {
            Bounties.useClock(() -> now[0]);
            ServerPlayer placer = player(h, 1_000_000);
            placer.getInventory().add(new ItemStack(CoinTier.NETHERITE.coin(), 10));
            UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
            Bounties.Result[] pr = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(a, "Small", Bounty.PIECES, 2_000, 0, 0, D, "secret"), x -> pr[0] = x);
            Bounties.place(server, placer, new Bounties.Ask(b, "Coins", CoinTier.NETHERITE.ordinal(), 2, 0, 0, D, "secret"), x -> pr[0] = x); // 2 x 8 x 1000 = 16,000
            Bounties.place(server, placer, new Bounties.Ask(c, "Soon", Bounty.PIECES, 9_000, 1_000, H / 2, H, "secret"), x -> pr[0] = x);
            List<BountySummary.Entry> list = BountySummary.build(server);
            h.assertTrue(list.size() == 3 && list.get(0).targetName().equals("Coins") && list.get(1).targetName().equals("Soon") && list.get(2).targetName().equals("Small"),
                    "worth first, coins at their bank value: " + list.stream().map(BountySummary.Entry::targetName).toList());
            BountySummaryPacket packet = new BountySummaryPacket(now[0], list);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            BountySummaryPacket.encode(packet, buf);
            BountySummaryPacket back = BountySummaryPacket.decode(buf);
            h.assertTrue(back.now() == now[0] && back.entries().equals(list), "the packet carries the summary whole");
            h.assertTrue(back.entries().get(0).coinTier() == CoinTier.NETHERITE.ordinal() && back.entries().get(2).coinTier() == Bounty.PIECES
                    && back.entries().get(1).bonusEndsMs() == start + H / 2, "kinds and bonus times survive");
            now[0] = start + H;
            h.assertTrue(BountySummary.build(server).size() == 2, "the ended one leaves the summary");
        });
    }
}
