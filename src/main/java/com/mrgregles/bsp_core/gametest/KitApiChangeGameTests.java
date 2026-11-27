package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspKits;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.Kits;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * GameTests for the kit api's changes ({@link BspKits} {@code VERSION} 1): create, copy, set, setItems, setIcon, setEnabled and
 * delete, each with its stamp check and its refusals. Batch {@code kit_api}, with {@link KitApiGameTests}. Every kit a test makes is
 * deleted in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class KitApiChangeGameTests {
    private static final String EMPTY = "empty", BATCH = "kit_api", BY = "TestStaff";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static String freshId(String prefix) {
        String id = (prefix + "_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).toLowerCase(Locale.ROOT);
        return id.substring(0, Math.min(24, id.length()));
    }

    private static long stamp(MinecraftServer server, String id) {
        return BspKits.detail(server, id).orElseThrow().edited();
    }

    private static void cleanUp(MinecraftServer server, String... ids) {
        for (String id : ids) {
            if (id != null) {
                Kits.delete(server, id);
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_create_makes_an_empty_kit_switched_off(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("Make");
        List<KitEvent.Changed> seen = new ArrayList<>();
        Consumer<KitEvent.Changed> listener = e -> {
            if (e.getKitId().equals(id)) {
                seen.add(e);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            h.assertTrue(BspKits.create(server, "a", "Too short id", BY).key().equals("bad_id"), "a one-letter id is refused");
            h.assertTrue(BspKits.create(server, "has space", "X", BY).key().equals("bad_id"), "a space in the id is refused");
            h.assertTrue(BspKits.create(server, id, "  ", BY).key().equals("bad_value"), "a blank name is refused");
            h.assertTrue(BspKits.create(server, id, "x".repeat(25), BY).key().equals("bad_value"), "a 25-character name is refused");
            BspKits.Answer made = BspKits.create(server, id.toUpperCase(Locale.ROOT), "Starter \u00a7cRed", BY);
            h.assertTrue(made.ok() && made.key().equals("created") && made.kit().equals(id), "made, the id kept lower-case: " + made);
            BspKits.Detail d = BspKits.detail(server, id).orElseThrow();
            h.assertTrue(!d.enabled() && d.items().isEmpty() && d.name().equals("Starter cRed"), "off, empty, the colour code cleaned: " + d.name());
            h.assertTrue(made.edited() == d.edited() && d.edited() > 0, "the answer carries the stamp");
            h.assertTrue(seen.size() == 1 && !seen.get(0).isDeleted(), "one change event");
            BspKits.Answer again = BspKits.create(server, id, "Again", BY);
            h.assertTrue(!again.ok() && again.key().equals("taken") && again.edited() == d.edited(), "the id is taken, with the kit's stamp: " + again);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            cleanUp(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_set_checks_the_stamp_and_every_field(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("set");
        try {
            BspKits.create(server, id, "Settings", BY);
            long s0 = stamp(server, id);
            BspKits.Answer stale = BspKits.set(server, id, "price", "5", s0 - 1, BY);
            h.assertTrue(!stale.ok() && stale.key().equals("stale") && stale.edited() == s0, "an old stamp is refused, with the stamp now: " + stale);
            h.assertTrue(BspKits.set(server, "no_such_kit_x", "price", "5", 0, BY).key().equals("no_kit"), "no such kit");
            h.assertTrue(BspKits.set(server, id, "enabled", "true", s0, BY).key().equals("bad_field"), "the switch is not a set field");
            h.assertTrue(BspKits.set(server, id, "colour", "red", s0, BY).key().equals("bad_field"), "an unknown field");
            // refusals leave the kit and its stamp as they were
            String[][] bad = {{"price", "-1"}, {"price", "abc"}, {"cooldownSeconds", "1.5"}, {"once", "yes"}, {"inBrowser", ""},
                    {"grant", "ALL"}, {"conditions", "no_totem,bogus"}, {"name", "x".repeat(25)}, {"description", "d".repeat(81)},
                    {"ranks", "r".repeat(513)}};
            for (String[] b : bad) {
                BspKits.Answer a = BspKits.set(server, id, b[0], b[1], s0, BY);
                h.assertTrue(!a.ok() && a.key().equals("bad_value"), b[0] + " = " + b[1] + " is refused: " + a);
            }
            h.assertTrue(stamp(server, id) == s0, "nothing refused moved the stamp");
            // each field in turn, each change naming the stamp the one before answered
            String[][] good = {{"name", "Spring Kit"}, {"description", "For the spring"}, {"inBrowser", "false"}, {"once", "true"},
                    {"cooldownSeconds", "3600"}, {"price", "250"}, {"ranks", "VIP, Elite,vip"}, {"servers", "spawn,survival"},
                    {"conditions", "no_totem"}, {"grant", "servers"}, {"grantServers", "survival"}};
            long at = s0;
            for (String[] g : good) {
                BspKits.Answer a = BspKits.set(server, id, g[0], g[1], at, BY);
                h.assertTrue(a.ok() && a.key().equals("saved") && a.edited() > at, g[0] + " saves and moves the stamp: " + a);
                h.assertTrue(BspKits.set(server, id, g[0], g[1], at, BY).key().equals("stale"), g[0] + " again with the old stamp is stale");
                at = a.edited();
            }
            BspKits.Detail d = BspKits.detail(server, id).orElseThrow();
            h.assertTrue(d.name().equals("Spring Kit") && d.description().equals("For the spring") && !d.inBrowser() && d.once(), "name, description, flags");
            h.assertTrue(d.cooldownSeconds() == 3600 && d.price() == 250, "numbers");
            h.assertTrue(d.ranks().equals(List.of("vip", "elite")), "ranks lower-case, each once: " + d.ranks());
            h.assertTrue(d.servers().equals(List.of("spawn", "survival")) && d.grantServers().equals(List.of("survival")) && d.grant().equals("SERVERS"), "servers and the grant");
            h.assertTrue(d.conditions().equals(List.of("no_totem")) && d.edited() == at, "conditions and the last stamp");
            BspKits.Answer clear = BspKits.set(server, id, "ranks", "", at, BY);
            h.assertTrue(clear.ok() && BspKits.detail(server, id).orElseThrow().ranks().isEmpty(), "an empty list clears the ranks");
        } finally {
            cleanUp(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_set_items_and_icon_take_rows_and_refuse_what_is_not_an_item(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("items");
        try {
            BspKits.create(server, id, "Items", BY);
            long s0 = stamp(server, id);
            BspKits.ItemRow sword = new BspKits.ItemRow("minecraft:diamond_sword", 1, "{Enchantments:[{id:\"minecraft:sharpness\",lvl:3s}]}");
            BspKits.ItemRow bread = new BspKits.ItemRow("minecraft:bread", 16, "");
            List<List<BspKits.ItemRow>> bad = List.of(
                    List.of(bread, new BspKits.ItemRow("nomod:thing", 1, "")),
                    List.of(new BspKits.ItemRow("minecraft:air", 1, "")),
                    List.of(new BspKits.ItemRow("minecraft:bread", 0, "")),
                    List.of(new BspKits.ItemRow("minecraft:diamond_sword", 2, "")),
                    List.of(new BspKits.ItemRow("minecraft:bread", 1, "{not snbt")),
                    List.of(new BspKits.ItemRow("minecraft:bread", 1, "{a:\"" + "x".repeat(BspKits.SNBT_MAX) + "\"}")),
                    List.of(new BspKits.ItemRow("minecraft:bread", 1, "{\"bsp_inventorysync:original\":{id:\"minecraft:diamond\",Count:1b}}")),
                    List.of(new BspKits.ItemRow("minecraft:shulker_box", 1, "{BlockEntityTag:{Items:[{Slot:0b,id:\"bsp_core:shatter_totem\",Count:1b}]}}")),
                    List.of(new BspKits.ItemRow("minecraft:shulker_box", 1, "{BlockEntityTag:{Items:[{Slot:0b,id:\"bsp_core:store_credit\",Count:1b,tag:{Serial:\"A1\"}}]}}")),
                    List.of(new BspKits.ItemRow("minecraft:shulker_box", 1, "{BlockEntityTag:{Items:[{Slot:0b,id:\"minecraft:stone\",Count:1b,tag:{\"bsp_inventorysync:original\":{id:\"minecraft:diamond\",Count:1b}}}]}}")));
            for (List<BspKits.ItemRow> rows : bad) {
                BspKits.Answer a = BspKits.setItems(server, id, rows, s0, BY);
                h.assertTrue(!a.ok() && a.key().equals("bad_item"), "refused: " + rows + " -> " + a);
            }
            List<BspKits.ItemRow> tooMany = new ArrayList<>();
            for (int i = 0; i <= Kits.maxItems(); i++) {
                tooMany.add(bread);
            }
            h.assertTrue(BspKits.setItems(server, id, tooMany, s0, BY).key().equals("too_many_items"), "more stacks than the limit");
            BspKits.ItemRow big = new BspKits.ItemRow("minecraft:paper", 1, "{display:{Name:'\"" + "w".repeat(20_000) + "\"'}}");
            BspKits.Answer large = BspKits.setItems(server, id, List.of(big, big), s0, BY);
            h.assertTrue(!large.ok() && large.key().equals("too_large"), "two big items are more than the KITS tab can send back: " + large);
            h.assertTrue(stamp(server, id) == s0 && BspKits.detail(server, id).orElseThrow().items().isEmpty(), "nothing refused changed the kit");
            // a totem with an owner is kept blank
            String owner = UUID.randomUUID().toString();
            String owned = "{Owner:\"" + owner + "\"}";
            BspKits.Answer ok = BspKits.setItems(server, id, List.of(sword, bread, new BspKits.ItemRow("bsp_core:shatter_totem", 1, owned)), s0, BY);
            h.assertTrue(ok.ok() && ok.edited() > s0, "three stacks saved: " + ok);
            BspKits.Detail d = BspKits.detail(server, id).orElseThrow();
            h.assertTrue(d.items().size() == 3 && d.items().get(0).nbt().contains("sharpness") && d.items().get(1).equals(bread), "the rows read back: " + d.items());
            Kit k = Kits.kit(server, id);
            h.assertTrue(k.items.get(2).is(ModItems.SHATTER_TOTEM.get()) && !k.items.get(2).hasTag(), "the totem is kept blank: " + k.items.get(2).getTag());
            // the icon
            BspKits.Answer icon = BspKits.setIcon(server, id, new BspKits.ItemRow("minecraft:bow", 5, ""), ok.edited(), BY);
            BspKits.ItemRow bow = BspKits.detail(server, id).orElseThrow().icon();
            h.assertTrue(icon.ok() && bow.item().equals("minecraft:bow") && bow.count() == 1, "the icon set, count 1 (a damageable item reads back with Damage:0): " + bow);
            h.assertTrue(BspKits.setIcon(server, id, new BspKits.ItemRow("nomod:icon", 1, ""), icon.edited(), BY).key().equals("bad_item"), "an unknown icon is refused");
            BspKits.Answer cleared = BspKits.setIcon(server, id, null, icon.edited(), BY);
            h.assertTrue(cleared.ok() && BspKits.detail(server, id).orElseThrow().icon().item().isEmpty(), "null clears the icon");
            BspKits.Answer empty = BspKits.setItems(server, id, List.of(), cleared.edited(), BY);
            h.assertTrue(empty.ok() && BspKits.detail(server, id).orElseThrow().items().isEmpty(), "an empty list empties the kit");
        } finally {
            cleanUp(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_switch_copy_and_kits_with_unreadable_items(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("orig");
        String copyId = freshId("copy");
        try {
            BspKits.create(server, id, "Original", BY);
            long at = BspKits.setItems(server, id, List.of(new BspKits.ItemRow("minecraft:apple", 3, "")), stamp(server, id), BY).edited();
            at = BspKits.set(server, id, "price", "40", at, BY).edited();
            at = BspKits.set(server, id, "ranks", "vip", at, BY).edited();
            BspKits.Answer on = BspKits.setEnabled(server, id, true, at, BY);
            h.assertTrue(on.ok() && BspKits.detail(server, id).orElseThrow().enabled(), "switched on");
            h.assertTrue(BspKits.setEnabled(server, id, false, at, BY).key().equals("stale"), "the switch checks the stamp too");
            Kits.kit(server, id).taken = 7;
            // copy
            h.assertTrue(BspKits.copy(server, "no_such_kit_y", copyId, "C", BY).key().equals("no_kit"), "copy of nothing");
            h.assertTrue(BspKits.copy(server, id, id, "C", BY).key().equals("taken"), "copy onto an id in use");
            h.assertTrue(BspKits.copy(server, id, "x", "C", BY).key().equals("bad_id"), "copy to a bad id");
            BspKits.Answer c = BspKits.copy(server, id, copyId, "Copy of it", BY);
            h.assertTrue(c.ok() && c.key().equals("created") && c.kit().equals(copyId), "copied: " + c);
            BspKits.Detail o = BspKits.detail(server, id).orElseThrow(), d = BspKits.detail(server, copyId).orElseThrow();
            h.assertTrue(!d.enabled() && d.name().equals("Copy of it") && d.items().equals(o.items()) && d.price() == 40 && d.ranks().equals(List.of("vip")), "settings and items copied, made off");
            h.assertTrue(d.taken() == 0 && d.lastTakenBy().isEmpty() && o.taken() == 7, "the times taken stay with the original");
            // a kit holding items this server cannot read is never saved here
            Kits.kit(server, id).missing = 1;
            long now = stamp(server, id);
            h.assertTrue(BspKits.set(server, id, "price", "1", now, BY).key().equals("missing_items"), "set refused");
            h.assertTrue(BspKits.setItems(server, id, List.of(), now, BY).key().equals("missing_items"), "setItems refused");
            h.assertTrue(BspKits.setIcon(server, id, null, now, BY).key().equals("missing_items"), "setIcon refused");
            h.assertTrue(BspKits.setEnabled(server, id, false, now, BY).key().equals("missing_items"), "setEnabled refused");
            h.assertTrue(BspKits.copy(server, id, freshId("copy2"), "C", BY).key().equals("missing_items"), "copy refused");
            h.assertTrue(stamp(server, id) == now && BspKits.detail(server, id).orElseThrow().price() == 40, "the kit is as it was");
            h.assertTrue(BspKits.delete(server, id, now, BY).ok(), "but it can be deleted");
        } finally {
            cleanUp(server, id, copyId);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_delete_checks_the_stamp_and_counts_who_was_owed_it(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("gone");
        UUID away = UUID.randomUUID(), other = UUID.randomUUID();
        List<KitEvent.Changed> seen = new ArrayList<>();
        Consumer<KitEvent.Changed> listener = e -> {
            if (e.getKitId().equals(id)) {
                seen.add(e);
            }
        };
        try {
            BspKits.create(server, id, "Owed kit", BY);
            long at = BspKits.setItems(server, id, List.of(new BspKits.ItemRow("minecraft:apple", 1, "")), stamp(server, id), BY).edited();
            h.assertTrue(Kits.give(server, away, id, KitEvent.How.ADMIN).ok() && Kits.give(server, other, id, KitEvent.How.ADMIN).ok(), "owed to two players who are not online");
            h.assertTrue(BspKits.owed(server, away).contains(id), "it is owed");
            h.assertTrue(BspKits.delete(server, id, at - 1, BY).key().equals("stale"), "an old stamp is refused");
            h.assertTrue(BspKits.delete(server, "no_such_kit_z", 0, BY).key().equals("no_kit"), "no such kit");
            MinecraftForge.EVENT_BUS.addListener(listener);
            BspKits.Answer a = BspKits.delete(server, id, at, BY);
            h.assertTrue(a.ok() && a.key().equals("deleted") && a.owed() == 2 && a.edited() == 0, "deleted, two were owed it: " + a);
            h.assertTrue(BspKits.detail(server, id).isEmpty() && !BspKits.owed(server, away).contains(id), "gone, and no longer owed");
            h.assertTrue(seen.size() == 1 && seen.get(0).isDeleted(), "one delete event");
            h.assertTrue(BspKits.delete(server, id, at, BY).key().equals("no_kit"), "a second delete finds nothing");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            cleanUp(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_the_kits_tab_refuses_a_save_over_a_newer_kit(GameTestHelper h) {
        MinecraftServer server = server(h);
        net.minecraft.server.level.ServerPlayer admin = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.CREATIVE);
        String id = freshId("tab");
        try {
            BspKits.create(server, id, "Tab kit", BY);
            Kit opened = Kits.kit(server, id).copy(); // the admin opens it in the KITS tab
            // a staff tool changes it meanwhile
            h.assertTrue(BspKits.set(server, id, "price", "99", opened.edited, BY).ok(), "the panel's change");
            opened.description = "From the tab";
            String note = com.mrgregles.bsp_core.kit.KitAdmin.apply(admin, com.mrgregles.bsp_core.network.KitEditPacket.save(opened.save(), false));
            h.assertTrue(note.equals("stale"), "the tab's SAVE over the newer kit is refused: " + note);
            Kit now = Kits.kit(server, id);
            h.assertTrue(now.price == 99 && now.description.isEmpty(), "the panel's change stands");
            // opened again, it saves, and the stamp moves on
            Kit again = now.copy();
            again.description = "From the tab";
            h.assertTrue(com.mrgregles.bsp_core.kit.KitAdmin.apply(admin, com.mrgregles.bsp_core.network.KitEditPacket.save(again.save(), false)).equals("saved"), "a fresh draft saves");
            h.assertTrue(Kits.kit(server, id).description.equals("From the tab") && Kits.kit(server, id).price == 99 && Kits.kit(server, id).edited > now.edited, "both changes kept");
            // deleted elsewhere while open: the tab does not make it again
            Kit open2 = Kits.kit(server, id).copy();
            BspKits.delete(server, id, open2.edited, BY);
            h.assertTrue(com.mrgregles.bsp_core.kit.KitAdmin.apply(admin, com.mrgregles.bsp_core.network.KitEditPacket.save(open2.save(), false)).equals("stale"), "a save of a kit deleted meanwhile is refused");
            h.assertTrue(Kits.kit(server, id) == null, "and the kit stays deleted");
            // a new kit with the id saves as new
            Kit made = new Kit(id, "Tab kit again", System.currentTimeMillis());
            h.assertTrue(com.mrgregles.bsp_core.kit.KitAdmin.apply(admin, com.mrgregles.bsp_core.network.KitEditPacket.save(made.save(), true)).equals("saved"), "a new kit is never stale");
        } finally {
            cleanUp(server, id);
        }
        h.succeed();
    }
}
