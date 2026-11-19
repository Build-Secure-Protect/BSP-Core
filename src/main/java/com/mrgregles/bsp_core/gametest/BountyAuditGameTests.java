package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.coin.CoinStamp;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** GameTests for the fixes from the Bounty Board audit (task 12). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyAuditGameTests {
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

    private static ItemStack stamped(CoinTier tier, int n, String team) {
        ItemStack s = new ItemStack(tier.coin(), n);
        s.getOrCreateTag().putString(CoinStamp.TAG, team);
        s.getOrCreateTag().putString(CoinStamp.NAME_TAG, "Reds");
        return s;
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_coin_bounty_holds_its_own_coins_and_gives_them_back_with_their_team_stamps(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 0), hunter = player(h, 0);
            String team = UUID.randomUUID().toString();
            placer.getInventory().add(stamped(CoinTier.GOLD, 6, team));
            placer.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 4)); // for the fee
            UUID target = UUID.randomUUID();
            Bounties.Result[] r = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(target, "T", CoinTier.DIAMOND.ordinal(), 2, 0, 0, D, ""), x -> r[0] = x);
            h.assertTrue(r[0].key().equals("short"), "Gold coins do not pay a Diamond reward: the reward is coins of its own tier");
            Bounties.place(server, placer, new Bounties.Ask(target, "T", CoinTier.GOLD.ordinal(), 4, 1, H, D, ""), x -> r[0] = x);
            h.assertTrue(r[0].ok() && r[0].bounty().lots().stream().allMatch(l -> team.equals(l.stamp())), "the 5 Gold coins are held with their stamp: " + r[0].bounty().lots());
            String id = r[0].bounty().id;
            Bounties.Result[] c = new Bounties.Result[1];
            Bounties.cancel(server, placer.getUUID(), id, x -> c[0] = x);
            List<ItemStack> back = Market.store(server).peekCollect(server, placer.getUUID());
            int gold = back.stream().filter(s -> s.is(CoinTier.GOLD.coin())).mapToInt(ItemStack::getCount).sum();
            h.assertTrue(c[0].ok() && gold == 5 && back.stream().filter(s -> s.is(CoinTier.GOLD.coin())).allMatch(s -> team.equals(CoinStamp.of(s))),
                    "cancelled: the same 5 Gold coins come back stamped: " + back);
            // claimed after the bonus window: the hunter gets the reward's coins, the placer the bonus's, each as stamped
            ItemStack more = stamped(CoinTier.GOLD, 5, team);
            placer.getInventory().add(more);
            Bounties.place(server, placer, new Bounties.Ask(target, "T", CoinTier.GOLD.ordinal(), 4, 1, H, D, ""), x -> r[0] = x);
            Bounties.accept(server, hunter.getUUID(), "H", r[0].bounty().id, x -> {});
            Bounties.store(server).claim(server, r[0].bounty().id, hunter.getUUID(), "H", Bounties.now() + 2 * H, x -> {});
            List<ItemStack> paid = Market.store(server).peekCollect(server, hunter.getUUID());
            h.assertTrue(paid.stream().mapToInt(ItemStack::getCount).sum() == 4 && paid.stream().allMatch(s -> team.equals(CoinStamp.of(s))), "the hunter's 4 coins keep the stamp: " + paid);
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_store_itself_refuses_a_cancel_with_a_hunter_and_an_expiry_with_time_left(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000), hunter = player(h, 0);
            Bounties.Result[] r = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(UUID.randomUUID(), "T", Bounty.PIECES, 5_000, 0, 0, D, ""), x -> r[0] = x);
            String id = r[0].bounty().id;
            Bounties.accept(server, hunter.getUUID(), "H", id, x -> {});
            BountyStore.Answer[] a = new BountyStore.Answer[1];
            Bounties.store(server).end(server, id, BountyStore.Ending.CANCELLED, "P", x -> a[0] = x);
            h.assertTrue(a[0].key().equals("accepted") && ledger.get(id) != null, "a cancel from a stale copy cannot take a bounty a hunter holds");
            Bounties.store(server).end(server, id, BountyStore.Ending.EXPIRED, "", x -> a[0] = x);
            h.assertTrue(a[0].key().equals("not_ended") && ledger.get(id) != null, "nor can an expiry from a stale end time");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_staff_target_returns_only_the_fee_the_pot_still_holds(GameTestHelper h) {
        run(h, 1_800_000_000_000L, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000);
            Bounties.Result[] r = new Bounties.Result[1];
            Bounties.place(server, placer, new Bounties.Ask(UUID.randomUUID(), "T", Bounty.PIECES, 10_000, 0, 0, D, ""), x -> r[0] = x);
            long bank = Bank.pieces(server, placer.getUUID()), pot = Bank.pieces(server, Market.POT);
            try {
                Bank.set(server, Market.POT, 0, "gametest", x -> {}); // the lottery was just drawn
                Bounties.store(server).end(server, r[0].bounty().id, BountyStore.Ending.TARGET_STAFF, "", x -> {});
                h.assertTrue(Bank.pieces(server, placer.getUUID()) == bank + 10_000, "the reward comes back, but no fee out of an empty pot: " + Bank.pieces(server, placer.getUUID()));
            } finally {
                Bank.set(server, Market.POT, pot, "gametest", x -> {});
            }
        });
    }
}
