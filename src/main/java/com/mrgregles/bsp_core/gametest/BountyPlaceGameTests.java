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
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.network.BountyPlacePacket;
import com.mrgregles.bsp_core.network.BountyPlayersPacket;
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
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** GameTests for the Bounty Board's place page on the server (task 8). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyPlaceGameTests {
    private static final String EMPTY = "empty";
    private static final long D = Bounties.DAY_MS;

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_place_page_only_puts_bounties_on_players_the_bank_knows_by_that_name(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BountyLedger ledger = new BountyLedger();
        BountyStore oldB = Bounties.useStore(new LocalBounties(ledger));
        MarketStore oldM = Market.useStore(new LocalMarket(new MarketLedger()));
        Bounties.staffCheck = (id, name) -> false;
        Bounties.protectedCheck = id -> false;
        ServerPlayer placer = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            Bank.ensure(server, placer.getUUID(), placer.getGameProfile().getName(), g -> {});
            Bank.set(server, placer.getUUID(), 100_000, "gametest", r -> {});
            BlockPos rel = new BlockPos(4, 1, 4);
            h.setBlock(rel, ModBlocks.BOUNTY_BOARD.get().defaultBlockState().setValue(BountyBoardBlock.FACING, Direction.NORTH));
            BlockPos c = h.absolutePos(rel);
            BountyBoardBlock.placeParts(h.getLevel(), c, Direction.NORTH);
            placer.setPos(c.getX() + 0.5, c.getY(), c.getZ() - 2);
            BountyScreens.openedAt(placer.getUUID(), GlobalPos.of(h.getLevel().dimension(), c));
            String name = "Bp" + Long.toString(System.nanoTime() % 100_000_000L, 36);
            UUID known = UUID.randomUUID();
            Bank.ensure(server, known, name, g -> {});
            BountyScreens.place(placer, new Bounties.Ask(UUID.randomUUID(), "Nobody" + name, Bounty.PIECES, 5_000, 0, 0, D, ""));
            h.assertTrue(ledger.open().isEmpty(), "a player the bank never saw gets no bounty");
            BountyScreens.place(placer, new Bounties.Ask(known, "Other" + name, Bounty.PIECES, 5_000, 0, 0, D, ""));
            h.assertTrue(ledger.open().isEmpty(), "a known id with another name gets none");
            BountyScreens.place(placer, new Bounties.Ask(known, name.toLowerCase(java.util.Locale.ROOT), Bounty.PIECES, 5_000, 0, 0, D, "Be quick"));
            h.assertTrue(ledger.open().size() == 1 && ledger.open().get(0).targetName.equals(name) && ledger.open().get(0).notes.equals("Be quick"),
                    "the known player gets it, under the bank's spelling of the name: " + ledger.open());
            placer.setPos(c.getX() + 0.5, c.getY(), c.getZ() - 40);
            BountyScreens.place(placer, new Bounties.Ask(known, name, Bounty.PIECES, 5_000, 0, 0, D, ""));
            h.assertTrue(ledger.open().size() == 1, "away from the board, nothing is placed");
        } finally {
            BountyScreens.forget(placer.getUUID());
            Bounties.useStore(oldB);
            Market.useStore(oldM);
            Bounties.staffCheck = null;
            Bounties.protectedCheck = null;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_place_and_player_list_packets_travel_whole(GameTestHelper h) {
        UUID t = UUID.randomUUID();
        BountyPlacePacket p = new BountyPlacePacket(false, t, "Wren_88", 2, 3, 1, 3_600_000L, 7 * D, "Notes ü");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        BountyPlacePacket.encode(p, buf);
        h.assertTrue(BountyPlacePacket.decode(buf).equals(p), "the place request survives");
        BountyPlacePacket s = BountyPlacePacket.search("wre");
        buf = new FriendlyByteBuf(Unpooled.buffer());
        BountyPlacePacket.encode(s, buf);
        BountyPlacePacket sb = BountyPlacePacket.decode(buf);
        h.assertTrue(sb.search() && sb.targetName().equals("wre"), "a search survives");
        BountyPlayersPacket list = new BountyPlayersPacket("wre", List.of(new BountyScreens.Pick(t, "Wren_88", true, ""), new BountyScreens.Pick(UUID.randomUUID(), "WrenMod", false, "staff")));
        buf = new FriendlyByteBuf(Unpooled.buffer());
        BountyPlayersPacket.encode(list, buf);
        h.assertTrue(BountyPlayersPacket.decode(buf).equals(list), "the player list survives with each refusal");
        h.succeed();
    }
}
