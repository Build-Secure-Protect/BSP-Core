package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.AdminBounty;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyLedger;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.bounty.LocalBounties;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.network.BountyAdminActionPacket;
import com.mrgregles.bsp_core.network.BountyAdminPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** GameTests for the admin panel's BOUNTIES tab on the server (task 10). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyAdminGameTests {
    private static final String EMPTY = "empty";
    private static final long D = Bounties.DAY_MS;

    @GameTest(template = EMPTY, batch = "bounty")
    public void only_staff_change_bounties_from_the_admin_tab(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BountyLedger ledger = new BountyLedger();
        BountyStore oldB = Bounties.useStore(new LocalBounties(ledger));
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        long now = 1_800_000_000_000L;
        Bounties.useClock(() -> now);
        Bounties.staffCheck = (id, name) -> false;
        Bounties.protectedCheck = id -> false;
        ServerPlayer admin = MockPlayers.serverPlayer(h, GameType.CREATIVE), player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            Bank.ensure(server, player.getUUID(), player.getGameProfile().getName(), g -> {});
            Bank.set(server, player.getUUID(), 100_000, "gametest", r -> {});
            Bounties.Result[] r = new Bounties.Result[1];
            Bounties.place(server, player, new Bounties.Ask(UUID.randomUUID(), "Wanted", Bounty.PIECES, 10_000, 0, 0, D, "Notes"), x -> r[0] = x);
            String id = r[0].bounty().id;
            // a player who is not staff: nothing happens
            AdminBounty.handle(player, BountyAdminActionPacket.of(BountyAdminActionPacket.REMOVE, id, 0));
            AdminBounty.handle(player, BountyAdminActionPacket.of(BountyAdminActionPacket.BLANK, id, 0));
            h.assertTrue(ledger.get(id) != null && ledger.get(id).notes.equals("Notes"), "a player's admin packets change nothing");
            // an admin may
            com.mrgregles.bsp_core.admin.Admins.adminSource = p -> p == admin;
            h.assertTrue(com.mrgregles.bsp_core.admin.Admins.canBounty(admin) && !com.mrgregles.bsp_core.admin.Admins.canBounty(player), "the admin may, the player may not");
            AdminBounty.handle(admin, BountyAdminActionPacket.of(BountyAdminActionPacket.BLANK, id, 0));
            h.assertTrue(ledger.get(id).notes.isEmpty(), "the admin blanks the notes");
            AdminBounty.handle(admin, BountyAdminActionPacket.of(BountyAdminActionPacket.EXTEND, id, D));
            h.assertTrue(ledger.get(id).endsMs == now + 2 * D, "and extends it a day");
            long bank = Bank.pieces(server, player.getUUID());
            AdminBounty.handle(admin, BountyAdminActionPacket.of(BountyAdminActionPacket.REMOVE, id, 0));
            h.assertTrue(ledger.get(id) == null && Bank.pieces(server, player.getUUID()) == bank + 10_000, "and removes it with a refund");
            // a staff bounty on a player the bank knows by name
            String name = "Ba" + Long.toString(System.nanoTime() % 100_000_000L, 36);
            UUID target = UUID.randomUUID();
            Bank.ensure(server, target, name, g -> {});
            AdminBounty.handle(admin, new BountyAdminActionPacket(BountyAdminActionPacket.ADD, "", 0, name, -1, 25_000, 0, 0, 3 * D, "Event"));
            h.assertTrue(ledger.open().size() == 1 && ledger.open().get(0).placer.equals(Bounties.SERVER) && ledger.open().get(0).target.equals(target)
                    && ledger.open().get(0).notes.equals("Event"), "the staff bounty is the server's, on the named player: " + ledger.open());
            AdminBounty.handle(admin, new BountyAdminActionPacket(BountyAdminActionPacket.ADD, "", 0, "Nobody" + name, -1, 25_000, 0, 0, 3 * D, ""));
            h.assertTrue(ledger.open().size() == 1, "an unknown name adds nothing");
        } finally {
            com.mrgregles.bsp_core.admin.Admins.adminSource = null;
            Bounties.useStore(oldB);
            Market.useStore(oldM);
            Bounties.useClock(null);
            Bounties.staffCheck = null;
            Bounties.protectedCheck = null;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_admin_tab_packets_travel_whole(GameTestHelper h) {
        long now = 1_800_000_000_000L;
        Bounty b = new Bounty("bx1", UUID.randomUUID(), "T", UUID.randomUUID(), "P", 2, 3, 1, now + 3_600_000L, 1000, now, now + D, "Ünïcode notes", "survival-1",
                List.of(new Bounty.Hunter(UUID.randomUUID(), "H", now)));
        BountyStore.Closed c = new BountyStore.Closed(b, BountyStore.Ending.CLAIMED, now + 5, UUID.randomUUID(), "H", "H", true);
        BountyAdminPacket p = new BountyAdminPacket(now, true, "ok", List.of(b), List.of(c), 30);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BountyAdminPacket.encode(p, buf);
        BountyAdminPacket back = BountyAdminPacket.decode(buf);
        h.assertTrue(back.open().size() == 1 && back.open().get(0).notes.equals(b.notes) && back.open().get(0).hunters().size() == 1 && back.open().get(0).coinTier == 2,
                "the open bounty survives with its notes and hunters");
        h.assertTrue(back.closed().size() == 1 && back.closed().get(0).ending() == BountyStore.Ending.CLAIMED && back.closed().get(0).bonusPaid() && back.canEdit()
                && back.maxDays() == 30, "the history row and the flags survive");
        BountyAdminActionPacket a = new BountyAdminActionPacket(BountyAdminActionPacket.ADD, "", 0, "Wren", 1, 4, 1, 3_600_000L, 2 * D, "x");
        buf = new FriendlyByteBuf(Unpooled.buffer());
        BountyAdminActionPacket.encode(a, buf);
        h.assertTrue(BountyAdminActionPacket.decode(buf).equals(a), "the action survives");
        h.succeed();
    }
}
