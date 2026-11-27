package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspKits;
import com.mrgregles.bsp_core.kit.KitLedger;
import com.mrgregles.bsp_core.kit.KitSync;
import com.mrgregles.bsp_core.kit.Kits;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GameTests for the kit api's player side ({@link BspKits} {@code VERSION} 1): a player's records, forgetting them, and the admin
 * gift. Batch {@code kit_api}. The players are made-up ids that are never online (mock players are not in the server's player list),
 * so a gift stays owed; delivery to a player online here is the KITS tab's GIVE TO path, tested with the kit gifts. Every kit and
 * record a test makes is removed in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class KitApiPlayerGameTests {
    private static final String EMPTY = "empty", BATCH = "kit_api", BY = "TestStaff";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static String freshId(String prefix) {
        String id = (prefix + "_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).toLowerCase(Locale.ROOT);
        return id.substring(0, Math.min(24, id.length()));
    }

    private static String made(GameTestHelper h, String prefix, String name) {
        MinecraftServer server = server(h);
        String id = freshId(prefix);
        h.assertTrue(BspKits.create(server, id, name, BY).ok(), "made " + id);
        long at = BspKits.detail(server, id).orElseThrow().edited();
        h.assertTrue(BspKits.setItems(server, id, List.of(new BspKits.ItemRow("minecraft:apple", 2, "")), at, BY).ok(), "items for " + id);
        return id;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_give_is_an_admin_gift_that_waits_for_an_offline_player(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = made(h, "gift", "Gift kit");
        UUID away = UUID.randomUUID();
        try {
            // the kit is switched off (made so): an admin gift goes out anyway
            BspKits.Answer a = BspKits.give(server, away, id.toUpperCase(Locale.ROOT), BY);
            h.assertTrue(a.ok() && a.key().equals("given") && a.kit().equals(id) && a.owed() == 1, "given, owed once: " + a);
            BspKits.Answer b = BspKits.give(server, away, id, BY);
            h.assertTrue(b.ok() && b.owed() == 2, "a second gift is owed twice: " + b);
            h.assertTrue(BspKits.owed(server, away).equals(List.of(id)), "owed: " + BspKits.owed(server, away));
            h.assertTrue(BspKits.give(server, away, "no_such_kit_g", BY).key().equals("no_kit"), "an unknown kit");
            h.assertTrue(BspKits.give(server, null, id, BY).key().equals("bad_value"), "no player");
        } finally {
            Kits.reset(server, away, null);
            Kits.delete(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_records_list_a_players_rows_and_reset_forgets_them(GameTestHelper h) {
        MinecraftServer server = server(h);
        String one = made(h, "recone", "Record one");
        String two = made(h, "rectwo", "Record two");
        UUID player = UUID.randomUUID();
        try {
            // rows as another server would have written them: the network's record of each kit, and a first-login grant on "survival"
            KitSync.applyRows(server, player, List.of(KitLedger.Redeem.of(one, Kits.NETWORK, 3, 1_000L, 0),
                    KitLedger.Redeem.of(one, "survival", 1, 2_000L, 0), KitLedger.Redeem.of(two, Kits.NETWORK, 1, 3_000L, 1),
                    KitLedger.Redeem.of("gone_kit_here", Kits.NETWORK, 2, 4_000L, 0)), false);
            List<List<BspKits.Record>> got = new ArrayList<>();
            List<Boolean> fresh = new ArrayList<>();
            BspKits.records(server, player, (rows, f) -> {
                got.add(rows);
                fresh.add(f);
            });
            h.assertTrue(got.size() == 1 && !fresh.get(0), "answered at once, from this server's copy (no network here)");
            List<BspKits.Record> rows = got.get(0);
            h.assertTrue(rows.size() == 4, "four rows: " + rows);
            h.assertTrue(rows.contains(new BspKits.Record(one, "Record one", "", 3, 1_000L, 0)), "the network row of kit one: " + rows);
            h.assertTrue(rows.contains(new BspKits.Record(one, "Record one", "survival", 1, 2_000L, 0)), "the grant row on survival");
            h.assertTrue(rows.contains(new BspKits.Record(two, "Record two", "", 1, 3_000L, 1)), "kit two, owed once");
            h.assertTrue(rows.contains(new BspKits.Record("gone_kit_here", "", "", 2, 4_000L, 0)), "a kit no longer here has no name");
            // forget one kit
            h.assertTrue(BspKits.reset(server, player, "no_such_kit_r", BY).key().equals("no_kit"), "an unknown kit");
            BspKits.Answer a = BspKits.reset(server, player, one.toUpperCase(Locale.ROOT), BY);
            h.assertTrue(a.ok() && a.key().equals("reset") && a.kit().equals(one) && a.owed() == 2, "kit one's two rows went: " + a);
            h.assertTrue(BspKits.timesTaken(server, player, one) == 0 && BspKits.timesTaken(server, player, two) == 1, "kit two's row stays");
            // a kit deleted since: its row can still be forgotten by its id
            BspKits.Answer gone = BspKits.reset(server, player, "gone_kit_here", BY);
            h.assertTrue(gone.ok() && gone.owed() == 1 && gone.edited() == 0, "the deleted kit's row went: " + gone);
            // forget every kit
            BspKits.Answer all = BspKits.reset(server, player, null, BY);
            h.assertTrue(all.ok() && all.kit().isEmpty() && all.owed() == 1, "the last one went: " + all);
            h.assertTrue(KitLedger.get(server).redeems(player).isEmpty() && BspKits.owed(server, player).isEmpty(), "nothing left, nothing owed");
            BspKits.Answer none = BspKits.reset(server, player, null, BY);
            h.assertTrue(none.ok() && none.owed() == 0, "a reset with nothing to forget forgets nothing");
        } finally {
            Kits.reset(server, player, null);
            Kits.delete(server, one);
            Kits.delete(server, two);
        }
        h.succeed();
    }
}
