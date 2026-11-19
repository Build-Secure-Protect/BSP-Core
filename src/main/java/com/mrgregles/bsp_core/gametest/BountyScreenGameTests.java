package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyBoardBlock;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyScreens;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.network.BountyViewPacket;
import com.mrgregles.bsp_core.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** GameTests for the Bounty Board screen's server side (task 7). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyScreenGameTests {
    private static final String EMPTY = "empty";
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS;

    private interface Body {
        void run(MinecraftServer server, BountyLedger ledger);
    }

    private static void run(GameTestHelper h, Body body) {
        BountyLedger ledger = new BountyLedger();
        BountyStore oldB = Bounties.useStore(new LocalBounties(ledger));
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        long now = 1_800_000_000_000L;
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

    private static Bounty place(MinecraftServer server, ServerPlayer placer, UUID target, String notes) {
        Bounties.Result[] r = new Bounties.Result[1];
        Bounties.place(server, placer, new Bounties.Ask(target, "Target", Bounty.PIECES, 5_000, 0, 0, D, notes), x -> r[0] = x);
        return r[0].bounty();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void each_player_sees_notes_only_for_bounties_they_accepted_or_placed(GameTestHelper h) {
        run(h, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000), hunter = player(h, 0), other = player(h, 0), target = player(h, 0);
            Bounty a = place(server, placer, target.getUUID(), "Secret A");
            Bounty b = place(server, placer, target.getUUID(), "Secret B");
            Bounties.accept(server, hunter.getUUID(), "H", a.id, x -> {});
            List<BountyScreens.Row> forHunter = BountyScreens.rows(server, hunter.getUUID());
            BountyScreens.Row ha = forHunter.stream().filter(r -> r.id().equals(a.id)).findFirst().orElseThrow();
            BountyScreens.Row hb = forHunter.stream().filter(r -> r.id().equals(b.id)).findFirst().orElseThrow();
            h.assertTrue(ha.accepted() && ha.notes().equals("Secret A") && !hb.accepted() && hb.notes().isEmpty(), "the hunter reads only the notes of what they accepted");
            h.assertTrue(forHunter.get(0).id().equals(a.id), "their own hunts come first");
            List<BountyScreens.Row> forOther = BountyScreens.rows(server, other.getUUID());
            h.assertTrue(forOther.stream().allMatch(r -> r.notes().isEmpty() && r.flags() == 0 && r.refusal().isEmpty()), "anyone else sees no notes and may accept");
            List<BountyScreens.Row> forPlacer = BountyScreens.rows(server, placer.getUUID());
            h.assertTrue(forPlacer.stream().allMatch(r -> r.mine() && !r.notes().isEmpty() && r.refusal().equals("own")), "the placer sees their notes and may not accept their own");
            List<BountyScreens.Row> forTarget = BountyScreens.rows(server, target.getUUID());
            h.assertTrue(forTarget.stream().allMatch(r -> r.notes().isEmpty() && r.refusal().equals("target")), "the target sees no notes and may not hunt themselves");
            h.assertTrue(forTarget.get(0).hunters() == 1 || forTarget.get(1).hunters() == 1, "hunter counts are there for everyone");
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void actions_work_only_while_standing_at_the_board(GameTestHelper h) {
        run(h, (server, ledger) -> {
            BlockPos rel = new BlockPos(4, 1, 4);
            h.setBlock(rel, ModBlocks.BOUNTY_BOARD.get().defaultBlockState().setValue(BountyBoardBlock.FACING, Direction.NORTH));
            BlockPos c = h.absolutePos(rel);
            BountyBoardBlock.placeParts(h.getLevel(), c, Direction.NORTH);
            ServerPlayer placer = player(h, 100_000), hunter = player(h, 0);
            Bounty b = place(server, placer, UUID.randomUUID(), "");
            GlobalPos at = GlobalPos.of(h.getLevel().dimension(), c);
            BountyScreens.openedAt(hunter.getUUID(), at);
            try {
                hunter.setPos(c.getX() + 0.5, c.getY(), c.getZ() - 30);
                h.assertFalse(BountyScreens.near(hunter), "30 blocks away is not at the board");
                BountyScreens.act(hunter, BountyScreens.Action.ACCEPT, b.id, BountyScreens.PAGE_BOARD);
                h.assertFalse(Bounties.find(server, b.id).get().hasHunter(hunter.getUUID()), "so accepting does nothing");
                hunter.setPos(c.getX() + 0.5, c.getY(), c.getZ() - 2);
                h.assertTrue(BountyScreens.near(hunter), "2 blocks in front is at the board");
                BountyScreens.act(hunter, BountyScreens.Action.ACCEPT, b.id, BountyScreens.PAGE_BOARD);
                h.assertTrue(Bounties.find(server, b.id).get().hasHunter(hunter.getUUID()), "and accepting works");
                BountyScreens.act(hunter, BountyScreens.Action.ABANDON, b.id, BountyScreens.PAGE_HUNTS);
                h.assertFalse(Bounties.find(server, b.id).get().hasHunter(hunter.getUUID()), "as does abandoning");
                BountyBoardBlock.removeBoard(h.getLevel(), c, Direction.NORTH, null);
                h.assertFalse(BountyScreens.near(hunter), "once the board is gone, nothing more");
            } finally {
                BountyScreens.forget(hunter.getUUID());
            }
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_collect_action_hands_the_collect_box_over(GameTestHelper h) {
        run(h, (server, ledger) -> {
            BlockPos rel = new BlockPos(4, 1, 4);
            h.setBlock(rel, ModBlocks.BOUNTY_BOARD.get().defaultBlockState().setValue(BountyBoardBlock.FACING, Direction.NORTH));
            BlockPos c = h.absolutePos(rel);
            BountyBoardBlock.placeParts(h.getLevel(), c, Direction.NORTH);
            ServerPlayer p = player(h, 0);
            p.setPos(c.getX() + 0.5, c.getY(), c.getZ() - 2);
            BountyScreens.openedAt(p.getUUID(), GlobalPos.of(h.getLevel().dimension(), c));
            try {
                Market.store(server).addCollect(server, p.getUUID(), List.of(new ItemStack(CoinTier.DIAMOND.coin(), 3), new ItemStack(Items.OAK_LOG, 5)));
                BountyScreens.act(p, BountyScreens.Action.COLLECT, "", BountyScreens.PAGE_BOARD);
                h.assertTrue(p.getInventory().countItem(CoinTier.DIAMOND.coin()) == 3 && p.getInventory().countItem(Items.OAK_LOG) == 5, "the coins and the logs are in the inventory");
                h.assertTrue(Market.store(server).peekCollect(server, p.getUUID()).isEmpty(), "the Collect box is empty");
            } finally {
                BountyScreens.forget(p.getUUID());
            }
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_view_travels_whole(GameTestHelper h) {
        run(h, (server, ledger) -> {
            ServerPlayer placer = player(h, 100_000);
            placer.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 7));
            Bounty b = place(server, placer, UUID.randomUUID(), "Ä note with ü and a line\nbreak");
            List<ItemStack> box = List.of(new ItemStack(Items.DIAMOND, 2));
            BountyViewPacket v = new BountyViewPacket(Bounties.now(), BountyScreens.PAGE_MINE, true, "ok", BountyScreens.rows(server, placer.getUUID()), box,
                    BountyScreens.rules(server, placer));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            BountyViewPacket.encode(v, buf);
            BountyViewPacket back = BountyViewPacket.decode(buf);
            h.assertTrue(back.rows().equals(v.rows()) && back.rows().get(0).notes().equals(b.notes) && back.page() == BountyScreens.PAGE_MINE && back.open()
                    && back.result().equals("ok"), "rows, notes, page and answer survive");
            h.assertTrue(back.collect().size() == 1 && ItemStack.matches(back.collect().get(0), box.get(0)), "the Collect box survives");
            BountyScreens.Rules r = back.rules();
            h.assertTrue(r.coins()[CoinTier.GOLD.ordinal()] == 7 && r.pieces() == Bank.pieces(server, placer.getUUID()) && r.open() == 1 && r.maxOpen() == Bounties.maxOpen()
                    && r.feePercent() == v.rules().feePercent() && r.piecesPerCopper() == Bounties.piecesPerCopper(), "the rules and money survive: " + r);
        });
    }
}
