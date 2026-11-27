package com.mrgregles.bsp_core.gametest;

import com.google.gson.JsonParser;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspShops;
import com.mrgregles.bsp_core.api.event.ShopEvent;
import com.mrgregles.bsp_core.shop.OpenHours;
import com.mrgregles.bsp_core.shop.Refill;
import com.mrgregles.bsp_core.shop.Requirement;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopItem;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GameTests for the shop api {@link BspShops} (BSP-Server Utilities requests SU-BC5 to SU-BC10). In the default batch (a new
 * batch name reorders every batch).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ShopApiGameTests {
    private static final String EMPTY = "empty";

    private static String tag() {
        return Long.toHexString(System.nanoTime());
    }

    private static ShopItem card(net.minecraft.world.item.Item item, int count, long buy, long sell) {
        ShopItem it = new ShopItem();
        it.stack = new ItemStack(item, count);
        it.buy = buy;
        it.sell = sell;
        return it;
    }

    @GameTest(template = EMPTY)
    public void the_api_lists_every_shop_and_reads_one_whole_with_every_card_field(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop top = ShopTestKit.on(server, "api_top_" + tag, "Api Top", Shop.Kind.SHOP);
        Shop sub = ShopTestKit.on(server, "api_sub_" + tag, "Api Sub", Shop.Kind.SUB);
        try {
            sub.parent = top.id;
            sub.add(card(Items.BREAD, 4, 2, -1));
            Shops.save(server, sub);
            ShopItem potion = card(Items.POTION, 1, 140, -1);
            potion.stack.getOrCreateTag().putString("Potion", "minecraft:swiftness");
            ShopItem iron = card(Items.IRON_INGOT, 8, 30, 12);
            iron.currency = 0;
            iron.stockGlobal = 5;
            iron.stockPlayer = 2;
            iron.stockRefill = Refill.after(90);
            iron.sellGlobal = 40;
            iron.sellRefill = Refill.parse("monday 00:00");
            iron.matchItem = true;
            iron.instant = true;
            iron.weight = 7;
            iron.setNote("Fresh|from the mines");
            iron.requires = new Requirement("bsp.shop.iron", 10, false, "Iron rank");
            ShopItem secret = card(Items.DIAMOND, 1, 999, -1);
            secret.hidden = true;
            ShopItem section = card(Items.CHEST, 1, -1, -1);
            section.section = sub.id;
            for (ShopItem it : List.of(potion, iron, secret, section, card(Items.APPLE, 1, 1, -1), card(Items.STICK, 1, 1, -1), card(Items.COAL, 1, 1, -1))) {
                top.add(it);
            }
            top.remote = true;
            top.hours = new OpenHours(OpenHours.Mode.REAL, 9 * 60, 21 * 60);
            top.requires = new Requirement("", 5, true, "New players wait");
            Shops.save(server, top);
            h.assertTrue(BspShops.VERSION >= 1 && BspShops.ready(server) && !BspShops.timeZone().isEmpty(), "a version to gate on; alone, always ready");
            var list = BspShops.list(server);
            var t = list.stream().filter(s -> s.id().equals(top.id)).findFirst().orElseThrow();
            var s = list.stream().filter(x -> x.id().equals(sub.id)).findFirst().orElseThrow();
            h.assertTrue(t.kind().equals("shop") && t.name().equals("Api Top") && t.parent().isEmpty() && t.enabled() && t.remote() && t.cards() == 7
                    && t.hours().mode().equals("REAL") && t.hours().open() == 540 && t.hours().close() == 1260 && t.requires().playtimeHours() == 5 && t.requires().hide()
                    && t.edited() == top.edited && t.rotateAt().equals("none") && t.nextChange() == 0, "the top shop's summary: " + t);
            h.assertTrue(t.openNow() == top.hours.isOpen(h.getLevel()) && (t.openNow() ? t.opensAt().isEmpty() : t.opensAt().equals("09:00")), "open now by its hours, or when it opens: " + t.opensAt());
            h.assertTrue(t.peek().size() == 4 && t.peek().get(0).item().equals("minecraft:potion") && t.peek().get(0).v().equals("minecraft:swiftness")
                    && t.peek().get(1).item().equals("minecraft:iron_ingot") && t.peek().get(2).item().equals("minecraft:apple"), "the first four item cards on show, hidden ones and section cards left out: " + t.peek());
            h.assertTrue(s.kind().equals("sub") && s.parent().equals(top.id) && s.enabled() && s.cards() == 1, "the section's summary: " + s);
            var d = BspShops.read(server, top.id);
            h.assertTrue(d != null && d.cards().size() == 7 && d.onShow().isEmpty() && d.cycleLeft() == -1 && d.draws().isEmpty(), "read whole; not rotating");
            var c = d.cards().get(1);
            h.assertTrue(c.i() == 1 && c.item().equals("minecraft:iron_ingot") && c.count() == 8 && c.buy() == 30 && c.sell() == 12 && c.currency() == 0
                    && c.stockGlobal() == 5 && c.stockPlayer() == 2 && c.sellGlobal() == 40 && c.sellPlayer() == -1
                    && c.stockRefill().equals("90m") && c.stockPlayerRefill().equals("same") && c.sellRefill().equals("monday 00:00") && c.sellPlayerRefill().equals("same")
                    && c.instant() && !c.hidden() && c.section().isEmpty() && c.match().equals("item") && c.weight() == 7
                    && c.note().equals(List.of("Fresh", "from the mines")) && c.requires().permission().equals("bsp.shop.iron") && c.requires().playtimeHours() == 10 && c.requires().reason().equals("Iron rank")
                    && c.stockLeft() == 5 && c.sellLeft() == 40, "every card field: " + c);
            var p = d.cards().get(0);
            h.assertTrue(p.v().equals("minecraft:swiftness") && p.nbt().contains("minecraft:swiftness") && d.cards().get(2).hidden() && d.cards().get(3).section().equals(sub.id)
                    && d.cards().get(3).stockLeft() == -1 && d.cards().get(4).nbt().isEmpty(), "the potion's data, a hidden card, a section card");
            h.assertTrue(BspShops.read(server, "no_such_" + tag) == null, "no shop: null");
            Shops.setEnabled(server, top, false);
            var off = BspShops.list(server).stream().filter(x -> x.id().equals(sub.id) || x.id().equals(top.id)).toList();
            h.assertTrue(off.size() == 2 && off.stream().noneMatch(BspShops.Summary::enabled) && off.stream().noneMatch(BspShops.Summary::openNow), "off: the shop and its section are off and not open");
            // the export as a string: format 1, the shop and its section
            String json = BspShops.export(server, top.id);
            var root = JsonParser.parseString(json).getAsJsonObject();
            h.assertTrue(root.get("format").getAsInt() == 1 && root.getAsJsonArray("shops").size() == 2
                    && root.getAsJsonArray("shops").get(0).getAsJsonObject().get("id").getAsString().equals(top.id)
                    && !root.getAsJsonArray("shops").get(0).getAsJsonObject().get("enabled").getAsBoolean(), "the export: the shop then its section, format 1, switched off");
            h.assertTrue(BspShops.export(server, "no_such_" + tag) == null && BspShops.export(server, "all") == null, "nothing for a missing shop, nor for every shop at once");
        } finally {
            Shops.delete(server, top.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_rotating_shop_reads_its_cards_on_show_cycle_and_next_change(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop rot = ShopTestKit.on(server, "api_rot_" + tag, "Api Rot", Shop.Kind.ROTATING);
        try {
            rot.displaySlots = 2;
            rot.noRepeat = true;
            for (var item : List.of(Items.APPLE, Items.BREAD, Items.STICK, Items.COAL, Items.FLINT)) {
                rot.add(card(item, 1, 1, -1));
            }
            Shops.save(server, rot);
            var d = BspShops.read(server, rot.id);
            h.assertTrue(d.shop().kind().equals("rotating") && d.onShow().size() == 2 && d.cycleLeft() == 3 && d.draws().size() == 1
                    && d.draws().get(0).cards().equals(d.onShow()) && d.shop().nextChange() > Shops.now() && d.shop().intervalHours() == 12, "on show, three left this cycle, the draw and the next change: " + d);
            h.assertTrue(d.shop().peek().size() == 2, "a rotating shop's peek is its cards on show");
        } finally {
            Shops.delete(server, rot.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_change_event_follows_every_save_and_delete(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        List<String> seen = new ArrayList<>();
        Consumer<ShopEvent.Changed> listener = e -> {
            if (e.getShopId().endsWith(tag)) {
                seen.add(e.getShopId() + (e.isDeleted() ? " deleted" : " saved") + (e.isHeard() ? " heard" : ""));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            Shop top = ShopTestKit.on(server, "ev_top_" + tag, "Ev", Shop.Kind.SHOP);
            Shop sub = ShopTestKit.on(server, "ev_sub_" + tag, "Ev Sub", Shop.Kind.SUB);
            sub.parent = top.id;
            Shops.save(server, sub);
            ShopItem section = card(Items.CHEST, 1, -1, -1);
            section.section = sub.id;
            top.add(section);
            Shops.save(server, top);
            seen.clear();
            Shops.setEnabled(server, top, false);
            h.assertTrue(seen.equals(List.of(top.id + " saved")), "a save posts one change: " + seen);
            seen.clear();
            Shops.delete(server, top.id);
            h.assertTrue(seen.equals(List.of(sub.id + " deleted", top.id + " deleted")), "a delete posts one for each shop gone, sections first: " + seen);
            Shop parent = ShopTestKit.on(server, "ev_par_" + tag, "Ev Par", Shop.Kind.SHOP);
            Shop child = ShopTestKit.on(server, "ev_chi_" + tag, "Ev Chi", Shop.Kind.SUB);
            child.parent = parent.id;
            Shops.save(server, child);
            ShopItem link = card(Items.CHEST, 1, -1, -1);
            link.section = child.id;
            parent.add(link);
            Shops.save(server, parent);
            seen.clear();
            Shops.delete(server, child.id);
            h.assertTrue(seen.equals(List.of(child.id + " deleted", parent.id + " saved")), "the parent whose section card left posts its own: " + seen);
            seen.clear();
            var made = BspShops.createSection(server, "ev_new_" + tag, "New", parent.id, "minecraft:chest", "", parent.edited, "Ann");
            h.assertTrue(made.ok() && seen.equals(List.of("ev_new_" + tag + " saved", parent.id + " saved")), "a new section posts one change, then its parent: " + seen);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            for (String id : List.of("ev_top_" + tag, "ev_par_" + tag, "ev_new_" + tag)) {
                Shops.delete(server, id);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_changes_check_the_stamp_and_every_rule(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String id = "chg_" + tag;
        try {
            h.assertTrue(BspShops.create(server, "Bad Id", "X", "shop", "Ann").key().equals("bad_id") && BspShops.create(server, id, "X", "sub", "Ann").key().equals("bad_value")
                    && BspShops.create(server, id, "\u00a7\u0007 ", "shop", "Ann").key().equals("bad_value"), "a bad id, a kind that is not a top shop's, a name of nothing but codes");
            var made = BspShops.create(server, id, "Spring\u00a7a\nSale", "rotating", "Ann");
            Shop s = Shops.ledger(server).shop(id);
            h.assertTrue(made.ok() && made.key().equals("created") && s != null && !s.enabled && s.kind == Shop.Kind.ROTATING && s.name.equals("Spring a Sale") && made.edited() == s.edited,
                    "made off, rotating, its name cleaned: " + (s == null ? "" : s.name));
            h.assertTrue(BspShops.create(server, id, "Again", "shop", "Ann").key().equals("taken"), "a taken id");
            long stamp = made.edited();
            var old = BspShops.set(server, id, "name", "Older", stamp - 1, "Ann");
            h.assertTrue(!old.ok() && old.key().equals("stale") && old.edited() == stamp && s.name.equals("Spring a Sale"), "a change at an old stamp is refused, nothing changed, the stamp now given");
            var named = BspShops.set(server, id, "name", "Spring", stamp, "Ann");
            h.assertTrue(named.ok() && named.edited() > stamp && s.name.equals("Spring"), "the stamp read: saved, a new stamp");
            stamp = named.edited();
            // every rule, refused without a change
            String[][] bad = {{"columns", "3"}, {"columns", "8"}, {"rows", "4"}, {"displaySlots", "0"}, {"displaySlots", "55"}, {"intervalHours", "0"}, {"intervalHours", "8761"},
                    {"rotateAt", "90m"}, {"offerPercent", "100"}, {"offerEnd", "1"}, {"offerEnd", String.valueOf(System.currentTimeMillis() + 11L * 366 * 86_400_000L)},
                    {"remote", "yes"}, {"style", "neon"}, {"kind", "sub"}, {"parent", "no_such_" + tag}, {"name", ""}, {"name", "x".repeat(65)}};
            for (String[] b : bad) {
                var a = BspShops.set(server, id, b[0], b[1], stamp, "Ann");
                h.assertTrue(!a.ok() && (a.key().equals("bad_value") || a.key().equals("no_parent")) && a.edited() == stamp, "refused: " + b[0] + " " + b[1] + " -> " + a.key());
            }
            h.assertTrue(BspShops.set(server, id, "colour", "red", stamp, "Ann").key().equals("bad_field"), "an unknown field");
            String[][] good = {{"columns", "0"}, {"columns", "5"}, {"rows", "2"}, {"displaySlots", "54"}, {"intervalHours", "8760"}, {"rotateAt", "monday 06:00"}, {"rotateAt", "none"},
                    {"offerPercent", "25"}, {"offerEnd", String.valueOf(System.currentTimeMillis() + 86_400_000L)}, {"offerEnd", "0"}, {"remote", "true"}, {"buyOnly", "true"},
                    {"noRepeat", "true"}, {"broadcast", "true"}, {"style", "passbook"}, {"kind", "offer"}, {"kind", "rotating"}};
            for (String[] g : good) {
                var a = BspShops.set(server, id, g[0], g[1], stamp, "Ann");
                h.assertTrue(a.ok() && a.edited() > stamp, "saved: " + g[0] + " " + g[1] + " -> " + a.key());
                stamp = a.edited();
            }
            h.assertTrue(s.columns == 5 && s.rows == 2 && s.displaySlots == 54 && s.intervalMs == 8760 * 3_600_000L && !s.rotateAt.fixedTime() && s.offerPercent == 25 && s.offerEndMs == 0
                    && s.remote && s.buyOnly && s.noRepeat && s.broadcast && s.style.equals("passbook") && s.kind == Shop.Kind.ROTATING, "every setting landed");
            // hours and requirements
            h.assertTrue(!BspShops.setHours(server, id, new BspShops.Hours("REAL", 0, 1440), stamp, "Ann").ok() && !BspShops.setHours(server, id, new BspShops.Hours("GAME", 0, 24000), stamp, "Ann").ok()
                    && !BspShops.setHours(server, id, new BspShops.Hours("NOON", 0, 0), stamp, "Ann").ok(), "hours past the day, or no such mode, are refused");
            var hrs = BspShops.setHours(server, id, new BspShops.Hours("GAME", 0, 12000), stamp, "Ann");
            h.assertTrue(hrs.ok() && s.hours.mode() == OpenHours.Mode.GAME && s.hours.closeMin() == 12000, "game hours set");
            stamp = hrs.edited();
            h.assertTrue(!BspShops.setRequires(server, id, new BspShops.Needs("bsp shop", 0, false, ""), stamp, "Ann").ok()
                    && !BspShops.setRequires(server, id, new BspShops.Needs("bsp.a", 100_001, false, ""), stamp, "Ann").ok()
                    && !BspShops.setRequires(server, id, new BspShops.Needs("bsp.a", 0, false, "r".repeat(65)), stamp, "Ann").ok(), "a node with a space, too many hours, a long reason");
            var req = BspShops.setRequires(server, id, new BspShops.Needs("bsp.shop.vip-*", 12, true, "VIPs\u00a7r only"), stamp, "Ann");
            h.assertTrue(req.ok() && s.requires.permission().equals("bsp.shop.vip-*") && s.requires.playtimeHours() == 12 && s.requires.hide() && s.requires.reason().equals("VIPs r only"), "a requirement set, its reason cleaned");
            stamp = req.edited();
            var clear = BspShops.setRequires(server, id, new BspShops.Needs("", 0, false, ""), stamp, "Ann");
            h.assertTrue(clear.ok() && !s.requires.any(), "and cleared");
            stamp = clear.edited();
            // the switch
            h.assertTrue(!BspShops.setEnabled(server, id, true, stamp - 1, "Ann").ok(), "the switch checks the stamp too");
            var on = BspShops.setEnabled(server, id, true, stamp, "Ann");
            h.assertTrue(on.ok() && s.enabled, "on");
            stamp = on.edited();
            // a card that refills each rotation keeps the shop rotating
            ShopItem it = card(Items.APPLE, 1, 1, -1);
            it.stockGlobal = 3;
            it.stockRefill = Refill.ROTATION;
            s.add(it);
            Shops.save(server, s);
            h.assertTrue(BspShops.set(server, id, "kind", "shop", s.edited, "Ann").key().equals("rotation_cards"), "rotation-only rules keep it rotating");
            var gone = BspShops.delete(server, id, s.edited - 1, "Ann");
            h.assertTrue(!gone.ok() && gone.key().equals("stale") && Shops.ledger(server).shop(id) != null, "a delete at an old stamp is refused");
            gone = BspShops.delete(server, id, s.edited, "Ann");
            h.assertTrue(gone.ok() && gone.deleted().equals(List.of(id)) && Shops.ledger(server).shop(id) == null, "deleted");
            h.assertTrue(BspShops.set(server, id, "name", "X", 0, "Ann").key().equals("no_shop"), "a missing shop");
        } finally {
            Shops.delete(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_section_is_made_with_its_card_and_moves_with_it(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String top = "sec_top_" + tag, other = "sec_oth_" + tag, sec = "sec_s_" + tag;
        try {
            var t = BspShops.create(server, top, "Top", "shop", "Ann");
            var o = BspShops.create(server, other, "Other", "shop", "Ann");
            Shop topShop = Shops.ledger(server).shop(top), otherShop = Shops.ledger(server).shop(other);
            topShop.add(card(Items.APPLE, 1, 1, -1));
            topShop.remote = true;
            Shops.save(server, topShop);
            h.assertTrue(BspShops.createSection(server, sec, "Potions", top, "minecraft:potion", "{Potion:\"minecraft:swiftness\"}", t.edited(), "Ann").key().equals("stale"), "the parent's stamp is checked");
            h.assertTrue(BspShops.createSection(server, sec, "Potions", top, "minecraft:not_an_item", "", topShop.edited, "Ann").key().equals("bad_item")
                    && BspShops.createSection(server, sec, "Potions", top, "minecraft:potion", "{Potion:", topShop.edited, "Ann").key().equals("bad_item")
                    && BspShops.createSection(server, sec, "Potions", top, "minecraft:potion", "x".repeat(4097), topShop.edited, "Ann").key().equals("bad_item"), "a picture that is no item, bad SNBT, SNBT too long");
            long before = topShop.edited;
            var made = BspShops.createSection(server, sec, "Potions", top, "minecraft:potion", "{Potion:\"minecraft:swiftness\"}", before, "Ann");
            Shop s = Shops.ledger(server).shop(sec);
            h.assertTrue(made.ok() && s != null && s.kind == Shop.Kind.SUB && s.parent.equals(top) && s.remote && !Shops.off(server, s) == topShop.enabled, "the section, under its parent, on the network as the parent is");
            ShopItem card = topShop.item(1);
            h.assertTrue(topShop.all().size() == 2 && card.section.equals(sec) && card.stack.is(Items.POTION) && card.stack.getTag().getString("Potion").equals("minecraft:swiftness")
                    && made.parent().equals(top) && made.parentEdited() == topShop.edited && topShop.edited > before, "its card at the end of the parent's page, in the same step, with the parent's new stamp");
            card.hidden = true;
            Shops.save(server, topShop);
            h.assertTrue(BspShops.set(server, sec, "parent", "", s.edited, "Ann").key().equals("bad_value"), "a section never moves to the top level");
            h.assertTrue(BspShops.set(server, top, "parent", sec, topShop.edited, "Ann").key().equals("parent_loop"), "a shop never goes under its own section");
            var moved = BspShops.set(server, sec, "parent", other, s.edited, "Ann");
            h.assertTrue(moved.ok() && s.parent.equals(other) && topShop.all().size() == 1 && otherShop.all().size() == 1
                    && otherShop.item(0).section.equals(sec) && otherShop.item(0).hidden && otherShop.item(0).stack.is(Items.POTION)
                    && moved.parent().equals(other) && moved.parentEdited() == otherShop.edited, "moved: its card left the old page and went to the new one as it was");
            // in the Workbench too, and to the top level there it starts off
            h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "parent", top, 0, 0), sec).equals("saved")
                    && topShop.all().size() == 2 && topShop.item(1).section.equals(sec) && otherShop.all().isEmpty(), "the Workbench's parent moves the card too");
            Shops.setEnabled(server, topShop, true);
            h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "parent", "", 0, 0), sec).equals("saved")
                    && s.parent.isEmpty() && Shops.off(server, s) && topShop.all().size() == 1, "made a top shop in the Workbench: its card leaves and it starts off");
            // a top shop given a parent gets a chest card and keeps its kind
            var under = BspShops.set(server, other, "parent", top, otherShop.edited, "Ann");
            h.assertTrue(under.ok() && otherShop.kind == Shop.Kind.SHOP && topShop.all().size() == 2 && topShop.item(1).section.equals(other) && topShop.item(1).stack.is(Items.CHEST),
                    "a top shop put under a parent keeps its kind and gets a chest card");
            var gone = BspShops.delete(server, top, topShop.edited, "Ann");
            h.assertTrue(gone.ok() && gone.deleted().containsAll(List.of(top, other)) && Shops.ledger(server).shop(other) == null, "the delete takes the section under it");
        } finally {
            for (String id : List.of(top, other, sec)) {
                Shops.delete(server, id);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void names_notes_and_requirements_are_cleaned_in_the_workbench_and_commands_too(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop s = Shops.create(server, "cln_" + tag, "Bad\u00a74 \u0085Name", Shop.Kind.SHOP);
        try {
            h.assertTrue(s.name.equals("Bad 4 Name"), "create cleans the name: " + s.name);
            h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "name", "Fine\u2028Name", 0, 0), s.id).equals("saved")
                    && s.name.equals("Fine Name"), "the Workbench cleans it: " + s.name);
            ShopItem it = card(Items.APPLE, 1, 1, -1);
            it.setNote("Line \u00a7cone|two\u0000");
            h.assertTrue(it.note.equals(List.of("Line cone", "two")), "a note is cleaned: " + it.note);
            h.assertTrue(Requirement.parse("permission bsp.shop.gold reason Gold\u00a7e rank") != null && Requirement.parse("permission bsp.shop.gold reason Gold\u00a7e rank").reason().equals("Gold e rank")
                    && Requirement.parse("permission bsp/shop") == null && Requirement.parse("playtime 100001") == null, "the command form keeps the same rules");
        } finally {
            Shops.delete(server, s.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_card_changes_follow_the_workbench_rules(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String id = "crd_" + tag, other = "crd_o_" + tag;
        try {
            BspShops.create(server, id, "Cards", "shop", "Ann");
            BspShops.create(server, other, "Other", "shop", "Ann");
            Shop s = Shops.ledger(server).shop(id);
            h.assertTrue(BspShops.addItem(server, id, "minecraft:apple", "", 1, 0, s.edited - 1, "Ann").key().equals("stale"), "the stamp is checked");
            h.assertTrue(BspShops.addItem(server, id, "minecraft:nope", "", 1, 0, s.edited, "Ann").key().equals("bad_item")
                    && BspShops.addItem(server, id, "minecraft:apple", "{bad", 1, 0, s.edited, "Ann").key().equals("bad_item")
                    && BspShops.addItem(server, id, "minecraft:apple", "", 0, 0, s.edited, "Ann").key().equals("bad_value")
                    && BspShops.addItem(server, id, "minecraft:apple", "", 65, 0, s.edited, "Ann").key().equals("bad_value"), "no item, bad SNBT, a lot of 0 or 65");
            var a = BspShops.addItem(server, id, "minecraft:ender_pearl", "", 64, 99, s.edited, "Ann");
            h.assertTrue(a.ok() && a.index() == 0 && s.item(0).stack.getCount() == 16 && s.item(0).buy == -1 && s.item(0).sell == -1, "added at the end, its lot kept to a stack of the item, no prices yet");
            var b = BspShops.addItem(server, id, "minecraft:potion", "{Potion:\"minecraft:healing\"}", 1, 0, a.edited(), "Ann");
            h.assertTrue(b.ok() && b.index() == 0 && s.item(0).stack.is(Items.POTION) && s.item(0).stack.getTag().getString("Potion").equals("minecraft:healing") && s.item(1).stack.is(Items.ENDER_PEARL),
                    "a potion with its data, put first: the pearl moves up a place");
            long st = b.edited();
            // the fields, with refusals that change nothing
            String[][] bad = {{"buy", "-2"}, {"buy", "1000000000001"}, {"currency", "5"}, {"currency", "-2"}, {"stockGlobal", "-2"}, {"stockGlobal", "1000000001"},
                    {"stockRefill", "same"}, {"stockRefill", "rotation"}, {"stockRefill", "soon"}, {"instant", "yes"}, {"match", "any"}, {"weight", "101"}, {"count", "0"},
                    {"section", "no_such_" + tag}, {"section", id}, {"note", "1\n2\n3\n4"}, {"note", "x".repeat(61)}};
            for (String[] f : bad) {
                var r = BspShops.setItem(server, id, 1, f[0], f[1], st, "Ann");
                h.assertTrue(!r.ok() && r.edited() == st, "refused: " + f[0] + " " + f[1] + " -> " + r.key());
            }
            h.assertTrue(BspShops.setItem(server, id, 1, "colour", "red", st, "Ann").key().equals("bad_field") && BspShops.setItem(server, id, 9, "buy", "1", st, "Ann").key().equals("no_item"), "an unknown field, a missing card");
            String[][] good = {{"buy", "250"}, {"sell", "40"}, {"stockGlobal", "10"}, {"stockPlayer", "2"}, {"sellGlobal", "100"}, {"sellPlayer", "5"}, {"stockRefill", "day 06:00"},
                    {"stockPlayerRefill", "same"}, {"sellRefill", "12h"}, {"sellPlayerRefill", "monday 00:00"}, {"instant", "true"}, {"match", "item"}, {"weight", "50"}, {"count", "8"},
                    {"note", "Shiny|new\nfrom the end"}};
            for (String[] f : good) {
                var r = BspShops.setItem(server, id, 1, f[0], f[1], st, "Ann");
                h.assertTrue(r.ok() && r.edited() > st && r.index() == 1, "saved: " + f[0] + " " + f[1] + " -> " + r.key());
                st = r.edited();
            }
            ShopItem pearl = s.item(1);
            h.assertTrue(pearl.buy == 250 && pearl.sell == 40 && pearl.stockGlobal == 10 && pearl.stockPlayer == 2 && pearl.sellGlobal == 100 && pearl.sellPlayer == 5
                    && pearl.stockRefill.text().equals("day 06:00") && pearl.sellRefill.text().equals("12h") && pearl.sellPlayerRefill.text().equals("monday 00:00")
                    && pearl.instant && pearl.matchItem && pearl.weight == 50 && pearl.stack.getCount() == 8 && pearl.note.equals(List.of("Shiny new", "from the end")), "every field landed: " + pearl.note);
            // the coin cap, also when the currency changes
            var coins = BspShops.setItem(server, id, 1, "currency", "2", st, "Ann");
            h.assertTrue(coins.ok() && pearl.currency == 2, "250 and 40 fit as coins");
            st = coins.edited();
            h.assertTrue(BspShops.setItem(server, id, 1, "buy", "10001", st, "Ann").key().equals("bad_price") && pearl.buy == 250, "more than 10,000 coins is refused");
            BspShops.setItem(server, id, 1, "currency", "-1", st, "Ann");
            st = s.edited;
            BspShops.setItem(server, id, 1, "buy", "50000", st, "Ann");
            st = s.edited;
            h.assertTrue(BspShops.setItem(server, id, 1, "currency", "0", st, "Ann").key().equals("bad_price") && pearl.currency == -1 && pearl.buy == 50000, "nor may a currency change make 50,000 pieces 50,000 coins");
            h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET_ITEM, "currency", "0", 1, 0), id).equals("bad_price")
                    && pearl.currency == -1, "the Workbench keeps the same cap");
            // requirements, a section card, move, remove, refill
            st = s.edited;
            var req = BspShops.setItemRequires(server, id, 1, new BspShops.Needs("bsp.shop.pearl", 0, true, "Pearl rank"), st, "Ann");
            h.assertTrue(req.ok() && pearl.requires.permission().equals("bsp.shop.pearl") && pearl.requires.hide(), "a card's requirement");
            st = req.edited();
            ShopItem link = card(Items.CHEST, 1, -1, -1);
            link.section = other;
            s.add(link);
            Shops.save(server, s);
            st = s.edited;
            h.assertTrue(BspShops.setItem(server, id, 2, "buy", "5", st, "Ann").key().equals("bad_field") && BspShops.setItem(server, id, 2, "hidden", "true", st, "Ann").ok() && s.item(2).hidden,
                    "a section card takes only hidden, its section and requires");
            st = s.edited;
            h.assertTrue(BspShops.setItem(server, id, 2, "section", "", st, "Ann").ok() && s.item(2).section.isEmpty(), "an empty section makes it an item card again");
            h.assertTrue(BspShops.setItem(server, id, 2, "section", other, s.edited, "Ann").ok() && s.item(2).section.equals(other), "and a shop id a section card again");
            st = s.edited;
            var mv = BspShops.moveItem(server, id, 1, 0, st, "Ann");
            h.assertTrue(mv.ok() && mv.index() == 0 && s.item(0) == pearl, "moved first");
            h.assertTrue(BspShops.moveItem(server, id, 0, 7, mv.edited(), "Ann").key().equals("no_item"), "a move past the end is refused");
            var ref = BspShops.refill(server, id, 0, mv.edited(), "Ann");
            h.assertTrue(ref.ok() && ref.edited() == mv.edited(), "refilled: the record and its stamp unchanged");
            var rm = BspShops.removeItem(server, id, 1, mv.edited(), "Ann");
            h.assertTrue(rm.ok() && s.all().size() == 2 && s.item(0) == pearl && s.item(1).section.equals(other), "removed the potion");
        } finally {
            Shops.delete(server, id);
            Shops.delete(server, other);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void coin_prices_over_ten_thousand_are_refused_by_the_command_and_the_import_too(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String file = "cap_" + tag;
        try {
            var root = com.google.gson.JsonParser.parseString("{\"format\": 1, \"shops\": [{\"id\": \"cap_" + tag + "\", \"name\": \"Cap\", \"pages\": [[{\"item\": \"minecraft:apple\", \"buy\": 10001, \"currency\": \"gold\"}]]}]}");
            java.nio.file.Files.createDirectories(com.mrgregles.bsp_core.shop.ShopImport.folder());
            java.nio.file.Files.writeString(com.mrgregles.bsp_core.shop.ShopImport.file(file), root.toString());
            var r = com.mrgregles.bsp_core.shop.ShopImport.run(server, file, true);
            h.assertTrue(!r.ok() && r.errors().stream().anyMatch(e -> e.contains("10000 coins")), "the import refuses it: " + r.errors());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            ShopSwitchGameTests.deleteImport(file);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_copy_takes_every_section_under_new_ids_made_off_with_full_counts(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String top = "cp_" + tag, mid = "cp_m_" + tag, leaf = "cp_l_" + tag, outside = "cp_x_" + tag, copy = "cq" + tag;
        List<String> made = new ArrayList<>();
        try {
            Shop t = ShopTestKit.on(server, top, "Original", Shop.Kind.SHOP);
            Shop m = ShopTestKit.on(server, mid, "Mid", Shop.Kind.SUB);
            Shop l = ShopTestKit.on(server, leaf, "Leaf", Shop.Kind.ROTATING);
            Shop x = ShopTestKit.on(server, outside, "Outside", Shop.Kind.SHOP);
            m.parent = top;
            l.parent = mid;
            ShopItem sold = card(Items.DIAMOND, 1, 100, -1);
            sold.stockGlobal = 3;
            sold.setNote("Rare");
            t.add(sold);
            ShopItem toMid = card(Items.CHEST, 1, -1, -1);
            toMid.section = mid;
            toMid.hidden = true;
            t.add(toMid);
            ShopItem toOutside = card(Items.CHEST, 1, -1, -1);
            toOutside.section = outside;
            t.add(toOutside);
            ShopItem toLeaf = card(Items.BARREL, 1, -1, -1);
            toLeaf.section = leaf;
            m.add(toLeaf);
            l.add(card(Items.APPLE, 1, 1, -1));
            l.add(card(Items.BREAD, 1, 1, -1));
            t.remote = true;
            t.hours = new OpenHours(OpenHours.Mode.REAL, 60, 120);
            for (Shop s0 : List.of(t, m, l, x)) {
                Shops.save(server, s0);
            }
            // a sale, so the original has a count the copy must not take
            var player = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            com.mrgregles.bsp_core.bank.Bank.ensure(server, player.getUUID(), "Cy", g -> {});
            com.mrgregles.bsp_core.bank.Bank.give(server, player.getUUID(), 1_000, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "", r -> {});
            t.hours = OpenHours.ALWAYS;
            Shops.save(server, t);
            Shops.buy(server, player, t, 0, 1, false, o -> {});
            h.assertTrue(Shops.everyoneLeft(server, t, 0, false, Shops.now()) == 2, "the original sold one");
            // refusals, nothing made
            int before = Shops.ledger(server).all().size();
            h.assertTrue(BspShops.copy(server, mid, copy, "C", "Ann").key().equals("not_top") && BspShops.copy(server, top, "Bad Id", "C", "Ann").key().equals("bad_id")
                    && BspShops.copy(server, top, outside, "C", "Ann").key().equals("taken") && BspShops.copy(server, top, copy, "\u00a7", "Ann").key().equals("bad_value")
                    && BspShops.copy(server, "no_such_" + tag, copy, "C", "Ann").key().equals("no_shop") && Shops.ledger(server).all().size() == before, "refused without making anything");
            // a taken section id is skipped
            Shop squat = ShopTestKit.on(server, copy + "_1", "In the way", Shop.Kind.SHOP);
            var a = BspShops.copy(server, top, copy, "The Copy", "Ann");
            made.addAll(a.ids().values());
            h.assertTrue(a.ok() && a.key().equals("created") && a.shop().equals(copy) && a.ids().size() == 3
                    && a.ids().get(top).equals(copy) && a.ids().get(mid).equals(copy + "_2") && a.ids().get(leaf).equals(copy + "_3")
                    && new ArrayList<>(a.ids().keySet()).equals(List.of(top, mid, leaf)), "the top and each section under new ids, in tree order, a taken id skipped: " + a.ids());
            Shop ct = Shops.ledger(server).shop(copy), cm = Shops.ledger(server).shop(copy + "_2"), cl = Shops.ledger(server).shop(copy + "_3");
            h.assertTrue(ct.name.equals("The Copy") && !ct.enabled && Shops.off(server, cm) && Shops.off(server, cl) && t.enabled, "made off, the original still on");
            h.assertTrue(cm.parent.equals(copy) && cl.parent.equals(copy + "_2") && cm.name.equals("Mid") && cl.kind == Shop.Kind.ROTATING, "parents point into the copy, names and kinds kept");
            h.assertTrue(ct.all().size() == 3 && ct.item(0).stack.is(Items.DIAMOND) && ct.item(0).buy == 100 && ct.item(0).stockGlobal == 3 && ct.item(0).note.equals(List.of("Rare"))
                    && ct.item(1).section.equals(copy + "_2") && ct.item(1).hidden && ct.item(2).section.equals(outside) && cm.item(0).section.equals(copy + "_3"),
                    "every card copied; section cards inside the copy rewritten, one to a shop outside kept");
            h.assertTrue(ct.remote && ct.hours.mode() == OpenHours.Mode.ALWAYS && Shops.everyoneLeft(server, ct, 0, false, Shops.now()) == 3, "settings copied; the copy's counts start full");
            h.assertTrue(BspShops.read(server, copy + "_3").onShow().size() > 0, "the rotating section draws afresh");
            // a long new id still fits
            String longId = ("z" + tag + "_long_long_long_long_long_long").substring(0, 32); // long enough to cut however short the tag
            var b = BspShops.copy(server, top, longId, "Long", "Ann");
            made.addAll(b.ids().values());
            h.assertTrue(b.ok() && b.ids().values().stream().allMatch(v -> v.length() <= 32 && v.matches("[a-z0-9_]{1,32}")) && b.ids().get(mid).endsWith("_1"), "every id fits 32: " + b.ids());
        } finally {
            Shops.delete(server, copy + "_1");
            for (String id : List.of(top, outside)) {
                Shops.delete(server, id);
            }
            made.forEach(id -> Shops.delete(server, id));
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void the_venue_register_keeps_this_servers_stalls_posts_and_displays(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop shop = ShopTestKit.on(server, "ven_" + tag, "Venues", Shop.Kind.SHOP);
        shop.add(card(Items.APPLE, 1, 1, -1));
        shop.add(card(Items.BREAD, 1, 1, -1));
        Shops.save(server, shop);
        List<String> events = new ArrayList<>();
        Consumer<ShopEvent.VenuesChanged> listener = e -> {
            if (shop.id.equals(e.getShopId())) {
                events.add("changed");
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        var level = h.getLevel();
        String dim = com.mrgregles.bsp_core.shop.VenueRegister.dim(level);
        net.minecraft.core.BlockPos lower = new net.minecraft.core.BlockPos(4, 2, 4), post = new net.minecraft.core.BlockPos(8, 2, 8), display = new net.minecraft.core.BlockPos(12, 2, 4);
        var state = com.mrgregles.bsp_core.registry.ModBlocks.MARKET_STALL.get().defaultBlockState();
        h.setBlock(lower, state);
        h.setBlock(lower.above(), state.setValue(com.mrgregles.bsp_core.shop.MarketStallBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        h.setBlock(post, com.mrgregles.bsp_core.registry.ModBlocks.VENDOR_POST.get().defaultBlockState());
        h.setBlock(display, com.mrgregles.bsp_core.registry.ModBlocks.STALL_DISPLAY.get().defaultBlockState());
        var stallBe = (com.mrgregles.bsp_core.shop.MarketStallBlockEntity) h.getBlockEntity(lower);
        var postBe = (com.mrgregles.bsp_core.shop.VendorPostBlockEntity) h.getBlockEntity(post);
        var displayBe = (com.mrgregles.bsp_core.shop.StallDisplayBlockEntity) h.getBlockEntity(display);
        stallBe.assign(shop.id, OpenHours.ALWAYS, "", "");
        postBe.set(shop.id, new OpenHours(OpenHours.Mode.REAL, 540, 1020), "minecraft:villager", "");
        displayBe.linkShop(shop.id);
        displayBe.pinned = 1;
        displayBe.setChanged();
        java.util.function.Function<net.minecraft.core.BlockPos, BspShops.Venue> at = p -> {
            var a = h.absolutePos(p);
            return BspShops.venues(server).stream().filter(v -> v.dim().equals(dim) && v.x() == a.getX() && v.y() == a.getY() && v.z() == a.getZ()).findFirst().orElse(null);
        };
        var st = at.apply(lower);
        var po = at.apply(post);
        var di = at.apply(display);
        h.assertTrue(st != null && st.kind().equals("stall") && st.shop().equals(shop.id) && st.hours() == null && st.pin().isEmpty(), "the stall, with the shop's hours: " + st);
        h.assertTrue(po != null && po.kind().equals("post") && po.hours() != null && po.hours().mode().equals("REAL") && po.hours().open() == 540, "the post, with its own hours: " + po);
        h.assertTrue(di != null && di.kind().equals("display") && di.pin().equals("minecraft:bread"), "the display, with its pinned item: " + di);
        h.assertTrue(at.apply(lower.above()) == null, "the stall's upper half is no venue of its own");
        h.assertTrue(!events.isEmpty() && BspShops.venuesChangedAt(server) > 0, "changes are told");
        // a display from before the register is learned when its chunk loads
        com.mrgregles.bsp_core.shop.VenueRegister.forget(level, h.absolutePos(display));
        h.assertTrue(at.apply(display) == null, "forgotten");
        displayBe.onLoad();
        // a broken stall is forgotten
        h.setBlock(lower.above(), net.minecraft.world.level.block.Blocks.AIR);
        h.setBlock(lower, net.minecraft.world.level.block.Blocks.AIR);
        h.succeedWhen(() -> {
            h.assertTrue(at.apply(display) != null, "the display is learned again as it loads");
            h.assertTrue(at.apply(lower) == null, "the broken stall is forgotten");
            h.assertTrue(at.apply(post) != null, "the post stays");
            MinecraftForge.EVENT_BUS.unregister(listener);
            Shops.delete(server, shop.id);
        });
    }

    @GameTest(template = EMPTY)
    public void the_report_adds_up_a_shop_and_its_sections_without_a_file(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop top = ShopTestKit.on(server, "rep_" + tag, "Report Top", Shop.Kind.SHOP);
        Shop sub = ShopTestKit.on(server, "rep_s_" + tag, "Report Sub", Shop.Kind.SUB);
        try {
            sub.parent = top.id;
            int bread = sub.add(card(Items.BREAD, 1, 5, -1));
            int iron = sub.add(card(Items.IRON_INGOT, 1, -1, 4));
            Shops.save(server, sub);
            int apple = top.add(card(Items.APPLE, 1, 10, -1));
            Shops.save(server, top);
            var p = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            com.mrgregles.bsp_core.bank.Bank.ensure(server, p.getUUID(), "Rae", g -> {});
            com.mrgregles.bsp_core.bank.Bank.give(server, p.getUUID(), 1_000, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "", r -> {});
            List<Boolean> ok = new ArrayList<>();
            Shops.buy(server, p, top, apple, 1, false, o -> ok.add(o.ok()));
            Shops.buy(server, p, sub, bread, 2, false, o -> ok.add(o.ok()));
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 3));
            Shops.sell(server, p, sub, iron, 3, false, o -> ok.add(o.ok()));
            h.assertTrue(ok.equals(List.of(true, true, true)), "three trades: " + ok);
            long files = files();
            BspShops.Report[] got = new BspShops.Report[3];
            BspShops.report(server, 7, top.id, r -> got[0] = r);
            BspShops.report(server, 7, sub.id, r -> got[1] = r);
            BspShops.report(server, 7, "no_such_" + tag, r -> got[2] = r);
            var all = got[0];
            h.assertTrue(all != null && all.ok() && all.days() == 7 && !all.capped() && all.rows() == 3 && all.shops().size() == 2 && all.items().size() == 3, "the shop with its section: " + all);
            h.assertTrue(all.total().piecesIn() == 20 && all.total().piecesOut() == 12 && all.total().bought() == 3 && all.total().sold() == 3, "in 10 + 10, out 12; three bought, three sold: " + all.total());
            var ironRow = all.items().stream().filter(r -> r.item().equals("minecraft:iron_ingot")).findFirst().orElseThrow();
            h.assertTrue(ironRow.shop().equals(sub.id) && ironRow.shopName().equals("Report Sub") && ironRow.sold() == 3 && ironRow.piecesOut() == 12 && ironRow.coinsIn().size() == 5, "a card's line names its section: " + ironRow);
            h.assertTrue(all.sellers().size() == 1 && all.sellers().get(0).name().equals("Rae") && all.sellers().get(0).pieces() == 12, "the top seller");
            h.assertTrue(got[1] != null && got[1].rows() == 2 && got[1].shops().size() == 1, "the section alone: its own two trades");
            h.assertTrue(got[2] != null && got[2].ok() && got[2].rows() == 0, "an unknown shop: an empty report");
            h.assertTrue(files() == files, "no CSV was written");
        } finally {
            Shops.delete(server, top.id);
        }
        h.succeed();
    }

    private static long files() {
        try (var s = java.nio.file.Files.list(com.mrgregles.bsp_core.shop.ShopImport.folder())) {
            return s.filter(f -> f.getFileName().toString().startsWith("report-")).count();
        } catch (java.io.IOException e) {
            return 0;
        }
    }

    @GameTest(template = EMPTY)
    public void odd_input_is_refused_without_touching_the_shop(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String id = "odd_" + tag;
        try {
            BspShops.create(server, id, "Odd", "shop", "Ann");
            Shop s = Shops.ledger(server).shop(id);
            s.add(card(Items.APPLE, 1, 1, -1));
            Shops.save(server, s);
            long st = s.edited;
            h.assertTrue(BspShops.removeItem(server, id, -1, st, "Ann").key().equals("no_item") && BspShops.moveItem(server, id, 0, -3, st, "Ann").key().equals("no_item")
                    && BspShops.refill(server, id, Integer.MIN_VALUE, st, "Ann").key().equals("no_item") && s.all().size() == 1, "negative places are no card, never a crash");
            String deep = "{a:".repeat(520) + "1" + "}".repeat(520);
            h.assertTrue(BspShops.addItem(server, id, "minecraft:apple", deep, 1, 0, st, "Ann").key().equals("bad_item")
                    && BspShops.addItem(server, id, "minecraft:apple", "{a:" + "[".repeat(2000) + "]".repeat(2000) + "}", 1, 0, st, "Ann").key().equals("bad_item")
                    && BspShops.addItem(server, id, "minecraft:apple", null, 1, 0, st, "Ann").key().equals("bad_item") && s.all().size() == 1, "data nested too deep, or none at all, is refused");
            h.assertTrue(BspShops.addItem(server, id, "minecraft:apple", "{a:".repeat(60) + "1" + "}".repeat(60), 1, 0, st, "Ann").ok(), "data nested 60 deep is fine");
            st = s.edited;
            h.assertTrue(BspShops.set(server, id, "name", "\u202e\u200b\u3000 ", st, "Ann").key().equals("bad_value")
                    && com.mrgregles.bsp_core.shop.ShopRules.clean("Gold\u202e\u3000Shop").equals("Gold Shop"), "a name of only invisible or wide spaces is no name; overrides become spaces");
            h.assertTrue(BspShops.create(server, "all", "All", "shop", "Ann").key().equals("bad_id") && Shops.create(server, "all", "All", Shop.Kind.SHOP) == null
                    && BspShops.copy(server, id, "all", "All", "Ann").key().equals("bad_id"), "\"all\" is no shop id: it names every shop");
            h.assertTrue(BspShops.setRequires(server, id, new BspShops.Needs("", 0, false, null), st, "Ann").ok(), "a null reason is no reason");
            // a section whose parent is gone: off, not copied, and may be made a top shop through the api
            Shop orphan = ShopTestKit.on(server, "odd_o_" + tag, "Orphan", Shop.Kind.SUB);
            orphan.parent = "gone_" + tag;
            Shops.save(server, orphan);
            h.assertTrue(Shops.off(server, orphan) && BspShops.copy(server, orphan.id, "odd_c_" + tag, "C", "Ann").key().equals("not_top"), "an orphan is off and not copied");
            h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "enabled", "true", 0, 0), orphan.id).equals("not_top"),
                    "nor switched on in the Workbench");
            var top = BspShops.set(server, orphan.id, "parent", "", orphan.edited, "Ann");
            h.assertTrue(top.ok() && orphan.parent.isEmpty() && Shops.off(server, orphan) && BspShops.setEnabled(server, orphan.id, true, orphan.edited, "Ann").ok() && !Shops.off(server, orphan),
                    "given no parent it is a top shop, off, with a switch");
        } finally {
            for (String d : List.of(id, "odd_o_" + tag, "odd_c_" + tag)) {
                Shops.delete(server, d);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_local_change_is_never_undone_by_an_older_read_and_imports_keep_the_switch(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        String id = "mark_" + tag, file = "mark_" + tag;
        try {
            long before = Shops.localChangeMark();
            Shop s = ShopTestKit.on(server, id, "Mark", Shop.Kind.SHOP);
            h.assertTrue(Shops.changedHereSince(id, before) && !Shops.changedHereSince(id, Shops.localChangeMark()) && !Shops.changedHereSince("never_" + tag, before),
                    "a save here is newer than a read made before it, and older than one made after");
            Shops.setEnabled(server, s, false);
            var root = com.google.gson.JsonParser.parseString(BspShops.export(server, id)).getAsJsonObject();
            root.getAsJsonArray("shops").get(0).getAsJsonObject().remove("enabled");
            java.nio.file.Files.createDirectories(com.mrgregles.bsp_core.shop.ShopImport.folder());
            java.nio.file.Files.writeString(com.mrgregles.bsp_core.shop.ShopImport.file(file), root.toString());
            var r = com.mrgregles.bsp_core.shop.ShopImport.run(server, file, false);
            h.assertTrue(r.ok() && !Shops.ledger(server).shop(id).enabled, "a file without the switch keeps an existing shop off: " + r.errors());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            Shops.delete(server, id);
            ShopSwitchGameTests.deleteImport(file);
        }
        h.succeed();
    }
}
