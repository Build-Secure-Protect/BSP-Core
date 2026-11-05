package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot;
import com.mrgregles.bsp_core.land.LandOfficeMenu;
import com.mrgregles.bsp_core.network.LandOfficeActionPacket.Action;
import com.mrgregles.bsp_core.network.LandOfficeDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for Region Guard's plot lists per Land Office (requests RG-C15 and RG-C16): an office block lists what Region Guard
 * lists for it and a wand outline's office lists every plot, an older Region Guard falls back to every plot, the office refuses
 * to quote or sell a plot it does not list, and the office's position and the admin flag reach the client. Region Guard is not
 * loaded in the test server, so its answers come from lambdas and {@link RegionGuardClaims#useListed}; the calls into Region
 * Guard itself only run in game.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class LandOfficeGameTests {
    private static final String EMPTY = "empty", BATCH = "land_office";

    @GameTest(template = EMPTY, batch = BATCH)
    public void land_office_lists_by_office(GameTestHelper h) {
        BlockPos office = h.absolutePos(new BlockPos(1, 1, 1));
        h.assertTrue(RegionGuardClaims.byOffice(null, () -> "office", () -> "world").equals("world"), "a wand outline's office lists every plot in the world");
        h.assertTrue(RegionGuardClaims.byOffice(office, () -> "office", () -> "world").equals("office"), "an office block lists what Region Guard lists for it");
        h.assertTrue(RegionGuardClaims.byOffice(office, () -> {
            throw new NoSuchMethodError("plotsForSale(ServerLevel, BlockPos)");
        }, () -> "world").equals("world"), "a Region Guard older than API 8 lists every plot");
        h.assertTrue(RegionGuardClaims.checkListed(() -> true) && !RegionGuardClaims.checkListed(() -> false), "Region Guard's yes and no stand");
        h.assertTrue(RegionGuardClaims.checkListed(() -> {
            throw new NoSuchMethodError("listedAt");
        }), "an older Region Guard lists every plot, so it is listed");
        h.assertTrue(!RegionGuardClaims.checkListed(() -> {
            throw new IllegalStateException("broken");
        }), "a failing answer sells nothing");
        if (!RegionGuardClaims.present()) {
            h.assertTrue(RegionGuardClaims.listings(h.getLevel(), java.util.UUID.randomUUID(), office, office).isEmpty(), "no Region Guard, nothing listed at an office either");
        }

        // the office and the admin flag travel to the client, for the ADMIN key
        var plots = List.of(new Plot("plot1", "Plot One", 0, 0, 15, 15, 300, 0, 0, 0, Plot.FOR_SALE, ""));
        var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        LandOfficeDataPacket.encode(new LandOfficeDataPacket(plots, 500, true, true, null, "", true, null, office, true), buf);
        var back = LandOfficeDataPacket.decode(buf);
        h.assertTrue(office.equals(back.office()) && back.admin() && back.plots().size() == 1 && back.plots().get(0).id().equals("plot1"), "office and admin travel");
        buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        LandOfficeDataPacket.encode(new LandOfficeDataPacket(plots, 500, true, true, new int[]{0, 0, 9, 9}, "", true), buf);
        back = LandOfficeDataPacket.decode(buf);
        h.assertTrue(back.office() == null && !back.admin() && back.selection() != null, "a wand outline's office has no office and no ADMIN key");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void land_office_refuses_a_plot_it_does_not_list(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1)), other = h.absolutePos(new BlockPos(3, 1, 3));
        var here = new LandOfficeMenu(0, player.getInventory(), at);
        var there = new LandOfficeMenu(0, player.getInventory(), other);
        var wand = new LandOfficeMenu(0, player.getInventory(), null);
        var level = h.getLevel();
        try {
            RegionGuardClaims.useListed((office, id) -> office.equals(at) && (id.equals("listed_plot") || id.equals("listed_area")));
            for (Action a : new Action[]{Action.QUOTE_PLOT, Action.BUY_PLOT, Action.QUOTE_OPEN, Action.BUY_OPEN}) {
                String no = LandOfficeMenu.refusal(level, here, a, "elsewhere");
                h.assertTrue(no != null && no.contains("does not sell"), a + ": a plot this office does not list is refused, in words: " + no);
                h.assertTrue(LandOfficeMenu.refusal(level, here, a, a == Action.QUOTE_OPEN || a == Action.BUY_OPEN ? "listed_area" : "listed_plot") == null, a + ": a listed one goes ahead");
                h.assertTrue(LandOfficeMenu.refusal(level, there, a, "listed_plot") != null, a + ": another office's list does not count");
                h.assertTrue(LandOfficeMenu.refusal(level, wand, a, "elsewhere") == null, a + ": a wand outline's office sells anywhere");
            }
            for (Action a : new Action[]{Action.ENLARGE, Action.ABANDON, Action.REFRESH}) {
                h.assertTrue(LandOfficeMenu.refusal(level, here, a, "elsewhere") == null, a + ": the player's own claims are never refused here");
            }
            h.assertTrue(here.at() != null && here.at().equals(at) && wand.at() == null, "the menu keeps its office block");
        } finally {
            RegionGuardClaims.useListed(null);
        }
        if (!RegionGuardClaims.present()) {
            h.assertTrue(RegionGuardClaims.listedAt(level, at, "anything"), "without Region Guard nothing is for sale, so nothing is refused here");
        }
        h.succeed();
    }
}
