package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.network.ShopConfigActionPacket;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.shop.MarketStallBlock;
import com.mrgregles.bsp_core.shop.MarketStallBlockEntity;
import com.mrgregles.bsp_core.shop.OpenHours;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopExport;
import com.mrgregles.bsp_core.shop.ShopImport;
import com.mrgregles.bsp_core.shop.ShopItem;
import com.mrgregles.bsp_core.shop.ShopListMenu;
import com.mrgregles.bsp_core.shop.ShopMenu;
import com.mrgregles.bsp_core.shop.ShopOpener;
import com.mrgregles.bsp_core.shop.Shops;
import com.mrgregles.bsp_core.shop.StallDisplayBlockEntity;
import com.mrgregles.bsp_core.shop.VendorPostBlock;
import com.mrgregles.bsp_core.shop.VendorPostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for a shop's on/off switch (BSP-Server Utilities request SU-BC8) and a delete that takes
 * a shop's sections with it. In the default batch (a new batch name reorders every batch).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ShopSwitchGameTests {
    private static final String EMPTY = "empty";

    private static String tag() {
        return Long.toHexString(System.nanoTime());
    }

    private static ShopItem card(net.minecraft.world.item.Item item, long buy) {
        ShopItem it = new ShopItem();
        it.stack = new ItemStack(item);
        it.buy = buy;
        return it;
    }

    private static ShopItem sectionCard(String section) {
        ShopItem it = new ShopItem();
        it.stack = new ItemStack(Items.CHEST);
        it.section = section;
        return it;
    }

    /** A top shop with an apple card and a section (with a bread card) behind a section card, saved and on. */
    private static Shop[] tree(MinecraftServer server, String tag) {
        Shop top = ShopTestKit.on(server, "sw_top_" + tag, "Switch Top", Shop.Kind.SHOP);
        Shop sub = ShopTestKit.on(server, "sw_sub_" + tag, "Switch Sub", Shop.Kind.SUB);
        sub.parent = top.id;
        sub.add(card(Items.BREAD, 2));
        Shops.save(server, sub);
        top.add(card(Items.APPLE, 3));
        top.add(sectionCard(sub.id));
        top.remote = true;
        Shops.save(server, top);
        return new Shop[]{top, sub};
    }

    @GameTest(template = EMPTY)
    public void a_new_shop_starts_off_and_a_record_from_before_the_switch_reads_as_on(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop made = Shops.create(server, "sw_new_" + tag, "New", Shop.Kind.SHOP);
        try {
            h.assertTrue(made != null && !made.enabled && Shops.off(server, made), "a new shop is made off");
            h.assertTrue(ShopConfigActionPacket.apply(server, null, new ShopConfigActionPacket(ShopConfigActionPacket.Action.CREATE, "sw_wb_" + tag, "SHOP", 0, 0), "").equals("created")
                    && !Shops.ledger(server).shop("sw_wb_" + tag).enabled, "so is one made in the Workbench");
            CompoundTag old = made.save();
            old.remove("Enabled");
            h.assertTrue(Shop.load(old).enabled, "a record saved before the switch existed is on");
            made.enabled = false;
            h.assertTrue(!Shop.load(made.save()).enabled, "off survives a save and load");
            var json = ShopExport.write(List.of(made));
            h.assertTrue(json.getAsJsonArray("shops").get(0).getAsJsonObject().get("enabled").getAsBoolean() == false, "the export writes enabled");
            // a file without the key imports as on, one with it keeps it
            String file = "sw_import_" + tag;
            var root = ShopExport.write(List.of(made));
            var o = root.getAsJsonArray("shops").get(0).getAsJsonObject();
            o.remove("enabled");
            o.addProperty("id", "sw_imp_" + tag);
            java.nio.file.Files.createDirectories(ShopImport.folder());
            java.nio.file.Files.writeString(ShopImport.file(file), root.toString());
            var r = ShopImport.run(server, file, false);
            h.assertTrue(r.ok() && Shops.ledger(server).shop("sw_imp_" + tag).enabled, "a file from before the switch imports on: " + r.errors());
            o.addProperty("enabled", false);
            java.nio.file.Files.writeString(ShopImport.file(file), root.toString());
            r = ShopImport.run(server, file, false);
            h.assertTrue(r.ok() && !Shops.ledger(server).shop("sw_imp_" + tag).enabled, "enabled false in the file imports off: " + r.errors());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            for (String id : List.of("sw_new_" + tag, "sw_wb_" + tag, "sw_imp_" + tag)) {
                Shops.delete(server, id);
            }
            deleteImport("sw_import_" + tag);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void an_off_shop_never_trades_opens_or_lists_and_its_sections_follow_it(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop[] t = tree(server, tag);
        Shop top = t[0], sub = t[1];
        var player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        Bank.ensure(server, player.getUUID(), "Sam", g -> {});
        Bank.give(server, player.getUUID(), 1_000, BankTransaction.Type.ADMIN, "test", "", r -> {});
        List<String> keys = new ArrayList<>();
        try {
            h.assertTrue(Shops.setEnabled(server, sub, false).equals("not_top") && !Shops.off(server, sub), "a section has no switch of its own");
            h.assertTrue(ShopConfigActionPacket.apply(server, null, new ShopConfigActionPacket(ShopConfigActionPacket.Action.SET, "enabled", "false", 0, 0), sub.id).equals("not_top"),
                    "nor in the Workbench");
            ShopOpener.open(player, top, null);
            h.assertTrue(player.containerMenu instanceof ShopMenu m && m.shopId.equals(top.id), "on: the shop opens");
            h.assertTrue(Shops.setEnabled(server, top, false).equals("saved") && Shops.off(server, top) && Shops.off(server, sub), "off, and its section with it");
            ShopOpener.closeOff(server, List.of(player)); // what the save ran for the server's players (a mock player is on no list)
            h.assertTrue(!(player.containerMenu instanceof ShopMenu), "a player in the shop has the screen closed when it turns off");
            ShopOpener.open(player, top, null);
            ShopOpener.open(player, sub, null);
            h.assertTrue(!(player.containerMenu instanceof ShopMenu), "an off shop and its section never open for a player");
            ShopOpener.open(player, sub, null, false, null, true);
            h.assertTrue(player.containerMenu instanceof ShopMenu m && m.test, "the Workbench's TEST still opens it");
            player.closeContainer();
            Shops.buy(server, player, top, 0, 1, false, o -> keys.add(o.key()));
            Shops.sell(server, player, sub, 0, 1, false, o -> keys.add(o.key()));
            Shops.sellAll(server, player, top, false, o -> keys.add(o.key()));
            Shops.buy(server, player, top, 0, 1, true, o -> keys.add("test:" + o.key()));
            h.assertTrue(keys.equals(List.of("closed_for_changes", "closed_for_changes", "closed_for_changes", "test:test_bought")), "off: every trade is refused but a test: " + keys);
            h.assertTrue(ShopListMenu.remote(server, h.getLevel()).stream().noneMatch(e -> e.id().equals(top.id)), "an off shop is not on the Shopping Network");
            h.assertTrue(ShopConfigActionPacket.apply(server, null, new ShopConfigActionPacket(ShopConfigActionPacket.Action.SET, "enabled", "true", 0, 0), top.id).equals("saved")
                    && !Shops.off(server, sub), "the Workbench turns it on again");
            h.assertTrue(ShopListMenu.remote(server, h.getLevel()).stream().anyMatch(e -> e.id().equals(top.id)), "on: back on the network");
            keys.clear();
            Shops.buy(server, player, top, 0, 1, false, o -> keys.add(o.key()));
            h.assertTrue(keys.equals(List.of("bought")), "on: a player buys again: " + keys);
        } finally {
            Shops.delete(server, top.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_section_card_into_a_switched_off_tree_shows_nothing_to_players(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop[] t = tree(server, tag);
        Shop other = ShopTestKit.on(server, "sw_other_" + tag, "Other", Shop.Kind.SHOP);
        other.add(sectionCard(t[1].id)); // a link into the first shop's section
        Shops.save(server, other);
        try {
            long now = Shops.now();
            h.assertTrue(Shops.reachable(server, other, now).stream().anyMatch(c -> c.shop() == t[1]), "on: the linked section's cards are reached");
            Shops.setEnabled(server, t[0], false);
            h.assertTrue(Shops.reachable(server, other, now).stream().noneMatch(c -> c.shop() == t[1]), "off: they are not");
        } finally {
            Shops.delete(server, t[0].id);
            Shops.delete(server, other.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void an_off_shop_darkens_its_stall_post_and_display(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop[] t = tree(server, tag);
        Shop top = t[0];
        try {
            BlockPos lamp = new BlockPos(5, 1, 5), lower = new BlockPos(5, 2, 5), upper = new BlockPos(5, 3, 5);
            h.setBlock(lamp, Blocks.REDSTONE_LAMP);
            var state = ModBlocks.MARKET_STALL.get().defaultBlockState().setValue(MarketStallBlock.FACING, Direction.EAST);
            h.setBlock(lower, state);
            h.setBlock(upper, state.setValue(MarketStallBlock.HALF, DoubleBlockHalf.UPPER));
            var stall = (MarketStallBlockEntity) h.getBlockEntity(lower);
            stall.assign(top.id, OpenHours.ALWAYS, "", "");
            BlockPos postLamp = new BlockPos(9, 1, 9), post = postLamp.above();
            h.setBlock(postLamp, Blocks.REDSTONE_LAMP);
            h.setBlock(post, ModBlocks.VENDOR_POST.get().defaultBlockState());
            var postBe = (VendorPostBlockEntity) h.getBlockEntity(post);
            postBe.set(top.id, OpenHours.ALWAYS, "minecraft:villager", "");
            BlockPos displayPos = new BlockPos(7, 2, 5);
            h.setBlock(displayPos, ModBlocks.STALL_DISPLAY.get().defaultBlockState());
            var display = (StallDisplayBlockEntity) h.getBlockEntity(displayPos);
            display.linkShop(top.id);
            Runnable tick = () -> {
                for (int i = 0; i < 20; i++) {
                    MarketStallBlockEntity.serverTick(h.getLevel(), h.absolutePos(lower), h.getBlockState(lower), stall);
                    VendorPostBlockEntity.serverTick(h.getLevel(), h.absolutePos(post), h.getBlockState(post), postBe);
                }
                display.refreshNow();
            };
            tick.run();
            h.assertTrue(stall.open && stall.stocked && stall.shown.is(Items.APPLE), "on: the stall is open with the apple on its counter");
            h.assertTrue(h.getBlockState(post).getValue(VendorPostBlock.OPEN) && h.getBlockState(postLamp).getValue(RedstoneLampBlock.LIT), "on: the post is open and powers its lamp");
            h.assertTrue(display.shopIfOpen(h.getLevel()) != null && !display.shown.isEmpty(), "on: the display floats a card");
            Shops.setEnabled(server, top, false);
            tick.run();
            h.assertTrue(!stall.open && !stall.stocked && stall.shown.isEmpty(), "off: the stall's lamps are dark and its counter empty");
            h.assertTrue(!h.getBlockState(post).getValue(VendorPostBlock.OPEN), "off: the post is closed");
            h.assertTrue(display.shopIfOpen(h.getLevel()) == null && display.shown.isEmpty(), "off: the display shows nothing");
            var player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            MarketStallBlock.use(player, stall);
            h.assertTrue(!(player.containerMenu instanceof ShopMenu), "off: a click on the stall opens nothing");
            Shops.setEnabled(server, top, true);
            tick.run();
            h.assertTrue(stall.open && stall.shown.is(Items.APPLE) && h.getBlockState(post).getValue(VendorPostBlock.OPEN), "on again: open again");
        } finally {
            Shops.delete(server, top.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_delete_takes_every_section_under_the_shop_and_its_card_on_the_parent(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop root = ShopTestKit.on(server, "del_root_" + tag, "Root", Shop.Kind.SHOP);
        Shop mid = ShopTestKit.on(server, "del_mid_" + tag, "Mid", Shop.Kind.SUB);
        Shop leaf = ShopTestKit.on(server, "del_leaf_" + tag, "Leaf", Shop.Kind.SUB);
        Shop side = ShopTestKit.on(server, "del_side_" + tag, "Side", Shop.Kind.SUB);
        mid.parent = root.id;
        leaf.parent = mid.id;
        side.parent = root.id;
        mid.add(sectionCard(leaf.id));
        root.add(card(Items.APPLE, 1));
        root.add(sectionCard(mid.id));
        root.add(card(Items.BREAD, 1));
        root.add(sectionCard(side.id));
        for (Shop s : List.of(leaf, mid, side, root)) {
            Shops.save(server, s);
        }
        try {
            long before = root.edited;
            List<String> gone = Shops.delete(server, mid.id);
            var ledger = Shops.ledger(server);
            h.assertTrue(gone.equals(List.of(mid.id, leaf.id)) && ledger.shop(mid.id) == null && ledger.shop(leaf.id) == null, "the shop and its section are gone: " + gone);
            h.assertTrue(root.all().size() == 3 && root.item(0).stack.is(Items.APPLE) && root.item(1).stack.is(Items.BREAD) && root.item(2).section.equals(side.id),
                    "its card left the parent's page, the rest kept in order");
            h.assertTrue(root.edited > before && ledger.shop(side.id) != null, "the parent is saved with a new stamp; the other section stays");
            h.assertTrue(Shops.delete(server, "no_such_" + tag).isEmpty(), "a missing shop deletes nothing");
            h.assertTrue(ShopConfigActionPacket.apply(server, null, new ShopConfigActionPacket(ShopConfigActionPacket.Action.DELETE, "", "", 0, 0), root.id).equals("deleted")
                    && ledger.shop(root.id) == null && ledger.shop(side.id) == null, "the Workbench's delete takes the sections too");
            // a section made a top shop in the Workbench starts off
            Shop a = ShopTestKit.on(server, "del_a_" + tag, "A", Shop.Kind.SHOP);
            Shop b = ShopTestKit.on(server, "del_b_" + tag, "B", Shop.Kind.SUB);
            b.parent = a.id;
            Shops.save(server, b);
            h.assertTrue(ShopConfigActionPacket.apply(server, null, new ShopConfigActionPacket(ShopConfigActionPacket.Action.SET, "parent", "", 0, 0), b.id).equals("saved")
                    && Shops.off(server, b) && !Shops.off(server, a), "a section moved to the top starts off");
        } finally {
            for (String id : List.of(root.id, mid.id, leaf.id, side.id, "del_a_" + tag, "del_b_" + tag)) {
                Shops.delete(server, id);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void the_workbench_list_carries_each_shops_parent_and_switch(GameTestHelper h) {
        var list = List.of(new com.mrgregles.bsp_core.shop.ShopConfigMenu.Summary("spring_sale", "Spring Sale", Shop.Kind.SHOP, 9, true, "", false),
                new com.mrgregles.bsp_core.shop.ShopConfigMenu.Summary("spring_potions", "Spring Potions", Shop.Kind.SUB, 6, false, "spring_sale", false),
                new com.mrgregles.bsp_core.shop.ShopConfigMenu.Summary("x".repeat(32), "N".repeat(64), Shop.Kind.ROTATING, 54, true, "y".repeat(32), true));
        var packet = new com.mrgregles.bsp_core.network.ShopConfigDataPacket(list, null, new int[0], new int[0], "saved", new String[0]);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.mrgregles.bsp_core.network.ShopConfigDataPacket.encode(packet, buf);
        var got = com.mrgregles.bsp_core.network.ShopConfigDataPacket.decode(buf).list();
        h.assertTrue(got.equals(list), "the parent and the switch reach the Workbench, at their longest too: " + got);
        h.succeed();
    }

    /** Removes an import file a test wrote, and the report the import writes beside it. */
    static void deleteImport(String file) {
        try {
            java.nio.file.Files.deleteIfExists(ShopImport.file(file));
            java.nio.file.Files.deleteIfExists(ShopImport.folder().resolve(file + "-report.txt"));
        } catch (java.io.IOException ignored) {
            // nothing to clean
        }
    }

    @GameTest(template = EMPTY)
    public void a_section_whose_parent_is_gone_is_off_and_a_gone_shop_closes_its_screen(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        String tag = tag();
        Shop[] t = tree(server, tag);
        Shop top = t[0], sub = t[1];
        var player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        try {
            h.assertTrue(!Shops.off(server, sub), "under its on top shop: on");
            Shops.ledger(server).remove(top.id); // as another server's delete of the top is heard before the section's
            h.assertTrue(Shops.off(server, sub) && Shops.setEnabled(server, sub, true).equals("not_top"), "its parent gone: off, and no switch of its own");
            ShopOpener.open(player, sub, null);
            h.assertTrue(!(player.containerMenu instanceof ShopMenu), "it never opens for a player");
            Shops.ledger(server).put(top);
            ShopOpener.open(player, sub, null);
            h.assertTrue(player.containerMenu instanceof ShopMenu, "its parent back: it opens");
            Shops.ledger(server).remove(sub.id);
            ShopOpener.closeOff(server, List.of(player));
            h.assertTrue(!(player.containerMenu instanceof ShopMenu), "a shop gone closes the screen of a player in it");
        } finally {
            Shops.delete(server, top.id);
            Shops.delete(server, sub.id);
        }
        h.succeed();
    }
}
