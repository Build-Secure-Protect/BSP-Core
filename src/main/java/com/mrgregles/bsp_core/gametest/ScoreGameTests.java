package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.score.ScoreAdjust;
import com.mrgregles.bsp_core.score.ScoreService;
import com.mrgregles.bsp_core.score.ScoreService.Entry;
import com.mrgregles.bsp_core.score.ScoreService.ServerRow;
import com.mrgregles.bsp_core.totem.LedgerCheck;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * GameTests for the leaderboard staying true to the totems that exist: rows of a network server that went silent stop counting,
 * the ledger's entries in chunks nobody loads are swept against the world, a totem listed in a world the server does not have
 * counts for nobody, and {@code /bsp score why} shows what a score is made of.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ScoreGameTests {
    private static final String EMPTY = "empty", BATCH = "scores";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static Entry entry(UUID id, String name, int totems, int points, int bestTier) {
        return new Entry(id, name, totems, points, bestTier);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_board_leaves_out_the_rows_of_a_server_that_went_silent(GameTestHelper h) {
        UUID p = UUID.randomUUID(), q = UUID.randomUUID(), r = UUID.randomUUID();
        long timeout = 30 * 60;
        List<ServerRow> rows = List.of(
                // p: one Tier I totem here, and three left on a server that stopped writing an hour ago (the duplicates, long gone)
                new ServerRow("BSP-Here", entry(p, "Pat", 1, 1, 0), 5),
                new ServerRow("BSP-Gone", entry(p, "Pat", 3, 3, 0), 3600),
                // q: only on the silent server
                new ServerRow("BSP-Gone", entry(q, "Quinn", 2, 14, 3), 3600),
                // r: here and on a server heard from a minute ago
                new ServerRow("BSP-Here", entry(r, "", 1, 4, 2), 5),
                new ServerRow("BSP-Alive", entry(r, "Robin", 2, 9, 4), 60),
                // this server's own rows always count, whatever their age (its own clock wrote them)
                new ServerRow("BSP-Here", entry(q, "Quinn", 1, 2, 1), timeout + 600));
        List<Entry> board = ScoreService.combine(rows, "BSP-Here", timeout);
        Entry pat = board.stream().filter(e -> e.id().equals(p)).findFirst().orElse(null);
        h.assertTrue(pat != null && pat.points() == 1 && pat.totems() == 1, "the silent server's three totems no longer count: " + pat);
        Entry quinn = board.stream().filter(e -> e.id().equals(q)).findFirst().orElse(null);
        h.assertTrue(quinn != null && quinn.points() == 2 && quinn.totems() == 1 && quinn.bestTier() == 1,
                "a row on the silent server adds nothing, not even its best tier: " + quinn);
        Entry robin = board.stream().filter(e -> e.id().equals(r)).findFirst().orElse(null);
        h.assertTrue(robin != null && robin.points() == 13 && robin.totems() == 3 && robin.bestTier() == 4 && robin.name().equals("Robin"),
                "servers heard from add up, best tier the highest, the name from a row that has one: " + robin);
        h.assertTrue(board.get(0).id().equals(r), "best first");
        List<Entry> onlyGone = ScoreService.combine(List.of(new ServerRow("BSP-Gone", entry(p, "Pat", 3, 3, 0), 3600)), "BSP-Here", timeout);
        h.assertTrue(onlyGone.isEmpty(), "a player whose only rows are on a silent server is not on the board");
        h.assertTrue(ScoreService.counts("BSP-Alive", "BSP-Here", timeout, timeout) && !ScoreService.counts("BSP-Alive", "BSP-Here", timeout + 1, timeout),
                "a row counts up to the timeout and not a second after");
        h.assertTrue(ScoreService.serverTimeoutSeconds() >= 120, "the timeout is at least two minutes: " + ScoreService.serverTimeoutSeconds());
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void the_ledger_sweep_forgets_a_totem_gone_with_a_chunk_nobody_loads(GameTestHelper h) {
        MinecraftServer server = server(h);
        TotemLedger ledger = TotemLedger.get(server);
        UUID owner = UUID.randomUUID();
        // far from the test area and from spawn, so its chunk is not loaded
        BlockPos far = h.absolutePos(new BlockPos(1, 2, 1)).offset(1024, 0, 1024);
        GlobalPos ghost = GlobalPos.of(h.getLevel().dimension(), far);
        h.assertFalse(h.getLevel().hasChunkAt(far), "the ghost's chunk starts unloaded");
        try {
            ledger.recordPlaced(owner, ghost, 2);
            h.assertTrue(ScoreService.spots(server).stream().anyMatch(s -> s.owner().equals(owner)), "before the sweep the ghost counts");
            var loaded = LedgerCheck.check(server, false);
            h.assertTrue(ledger.placedFor(owner).contains(ghost), "the minute check of loaded chunks cannot see it: " + loaded);
            boolean gone = false;
            for (int round = 0; round < 50 && !gone; round++) { // the sweep goes round every unloaded entry in turn
                LedgerCheck.sweep(server, 8);
                gone = !ledger.placedFor(owner).contains(ghost);
            }
            h.assertTrue(gone, "the sweep loaded its chunk, found no totem there and forgot the entry");
            h.assertTrue(ScoreService.spots(server).stream().noneMatch(s -> s.owner().equals(owner)), "and it counts for nobody");
            h.assertTrue(LedgerCheck.sweep(server, 0).checked() == 0, "a sweep of 0 (turned off) loads nothing");
        } finally {
            ledger.recordRemoved(ghost);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_listed_in_a_world_this_server_lacks_counts_for_nobody(GameTestHelper h) {
        MinecraftServer server = server(h);
        TotemLedger ledger = TotemLedger.get(server);
        UUID owner = UUID.randomUUID();
        var world = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(BSPCore.MODID, "gametest_gone_world"));
        GlobalPos lost = GlobalPos.of(world, new BlockPos(10, 64, 10));
        try {
            ledger.recordPlaced(owner, lost, 4);
            h.assertTrue(ledger.placedFor(owner).contains(lost), "the ledger keeps the entry, in case the world comes back");
            h.assertTrue(ScoreService.spots(server).stream().noneMatch(s -> s.owner().equals(owner)), "but the board and the admin panel do not count it");
            h.assertTrue(ScoreService.local(server).stream().noneMatch(e -> e.id().equals(owner)), "no line on the board");
            h.assertTrue(ScoreService.explain(server, owner).stream().anyMatch(l -> l.contains("worlds this server does not have")),
                    "score why says why: " + ScoreService.explain(server, owner));
            LedgerCheck.sweep(server, 8);
            h.assertTrue(ledger.placedFor(owner).contains(lost), "the sweep leaves it alone (it cannot load a world that is not there)");
        } finally {
            ledger.recordRemoved(lost);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_wipe_clears_the_score_set_by_hand(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID away = UUID.randomUUID(); // offline, for /bsp totem forget
        BlockPos placedAt = new BlockPos(3, 1, 3);
        try {
            ScoreAdjust.add(server, a.getUUID(), 3, "test", r -> {});
            h.assertTrue(ScoreAdjust.of(server, a.getUUID()) == 3, "a score set by hand before the wipe");
            var r = com.mrgregles.bsp_core.totem.TotemWipe.wipe(a);
            h.assertTrue(r.scoreCleared() && ScoreAdjust.of(server, a.getUUID()) == 0, "the wipe clears it, so the fresh totem's points are the whole score: " + r);
            h.setBlock(placedAt, ModBlocks.SHATTER_TOTEM.get()); // a placed totem, so the board has a line for a (a mock player's carried ones are not seen)
            ((ShatterTotemBlockEntity) h.getBlockEntity(placedAt)).setOwner(new TotemOwner(a.getUUID(), a.getGameProfile().getName()));
            ScoreService.refresh(server);
            h.assertTrue(ScoreService.local(server).stream().anyMatch(e -> e.id().equals(a.getUUID()) && e.points() == ScoreService.tierPoints(0)),
                    "the board shows the totem's points alone, nothing set by hand on top: " + ScoreService.local(server));
            var again = com.mrgregles.bsp_core.totem.TotemWipe.wipe(a);
            h.assertFalse(again.scoreCleared(), "with nothing set, the wipe reports nothing cleared");
            ScoreAdjust.add(server, away, 5, "test", x -> {});
            h.assertTrue(com.mrgregles.bsp_core.totem.TotemWipe.forget(server, away).scoreCleared() && ScoreAdjust.of(server, away) == 0,
                    "/bsp totem forget clears it too, for an offline player");
        } finally {
            ScoreAdjust.clear(server, a.getUUID(), "test", r -> {});
            ScoreAdjust.clear(server, away, "test", r -> {});
            TotemLedger ledger = TotemLedger.get(server);
            for (var fresh : com.mrgregles.bsp_core.totem.TotemInventories.removeAll(a)) { // the wipe's fresh totems: none left to come back
                UUID id = com.mrgregles.bsp_core.totem.TotemIdentity.id(fresh);
                if (id != null) {
                    ledger.forget(id);
                }
            }
            ledger.clearGranted(a.getUUID());
            if (h.getBlockEntity(placedAt) instanceof ShatterTotemBlockEntity totem) {
                totem.setOwner(null);
            }
            h.setBlock(placedAt, Blocks.AIR);
            ScoreService.refresh(server);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_config_lists_every_dimension_this_server_has(GameTestHelper h) throws java.io.IOException {
        MinecraftServer server = server(h);
        List<String> active = com.mrgregles.bsp_core.event.ActiveDimensions.names(server);
        h.assertTrue(active.contains("minecraft:overworld") && active.contains("minecraft:the_nether") && active.contains("minecraft:the_end"), "the three vanilla worlds: " + active);
        h.assertTrue(com.mrgregles.bsp_core.BSPConfig.ACTIVE_DIMENSIONS.get().equals(active), "the start wrote them into the config: "
                + com.mrgregles.bsp_core.BSPConfig.ACTIVE_DIMENSIONS.get());
        var file = server.getWorldPath(new net.minecraft.world.level.storage.LevelResource("serverconfig")).resolve(BSPCore.MODID + "-server.toml");
        try (var read = com.electronwill.nightconfig.core.file.CommentedFileConfig.of(file)) {
            read.load();
            List<String> inFile = read.get("restrictions.activeDimensions");
            h.assertTrue(active.equals(inFile), "and into the file an admin edits, under [restrictions]: " + inFile + " in " + file);
        }
        long written = java.nio.file.Files.getLastModifiedTime(file).toMillis();
        h.assertTrue(com.mrgregles.bsp_core.event.ActiveDimensions.missing(List.of("minecraft:overworld", "minecraft:the_nethr", "twilightforest:twilight_forest"), active)
                .equals(List.of("minecraft:the_nethr", "twilightforest:twilight_forest")), "a misspelt or missing name is picked out for the warning");
        h.assertTrue(com.mrgregles.bsp_core.event.ActiveDimensions.write(server).equals(active)
                && java.nio.file.Files.getLastModifiedTime(file).toMillis() == written, "an unchanged list is not written again");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void score_why_shows_the_totems_here_and_the_score_set_by_hand(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(2, 1, 2);
        int one = ScoreService.tierPoints(0);
        try {
            h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(new TotemOwner(a.getUUID(), a.getGameProfile().getName()));
            ScoreAdjust.set(server, a.getUUID(), one + 3, "test", r -> {});
            ScoreService.refresh(server);
            List<String> why = ScoreService.explain(server, a.getUUID());
            h.assertTrue(why.get(0).contains((one + 3) + " points on the board") && why.get(0).contains("1 totem"), "the board's line first: " + why);
            h.assertTrue(why.stream().anyMatch(l -> l.contains("1 placed, 0 carried, " + one + " points")), "what the totems here give: " + why);
            h.assertTrue(why.stream().anyMatch(l -> l.contains("Tier I at")), "and where they stand: " + why);
            h.assertTrue(why.stream().anyMatch(l -> l.contains("Set by hand: +3")), "the score set by hand riding on top: " + why);
            ScoreAdjust.clear(server, a.getUUID(), "test", r -> {});
            ScoreService.refresh(server);
            h.assertTrue(ScoreService.explain(server, a.getUUID()).stream().anyMatch(l -> l.equals("No score set by hand.")), "cleared, it says so");
            h.assertTrue(ScoreService.board().stream().anyMatch(e -> e.id().equals(a.getUUID()) && e.points() == one), "and the board is the totems' points again");
        } finally {
            ScoreAdjust.clear(server, a.getUUID(), "test", r -> {});
            if (h.getBlockEntity(pos) instanceof ShatterTotemBlockEntity totem) {
                totem.setOwner(null); // no lost totem left to hand back to a mock player
            }
            h.setBlock(pos, Blocks.AIR);
            ScoreService.refresh(server);
        }
        h.succeed();
    }
}
