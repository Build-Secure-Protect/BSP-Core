package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspKits;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.KitSync;
import com.mrgregles.bsp_core.kit.Kits;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * GameTests for the kit api for staff tools ({@link BspKits} {@code VERSION} 1): the edit stamp, the whole-kit read, the pickers
 * and {@link KitEvent.Changed}. Own batch: the kits these tests make never meet another batch's, and nothing here does a full
 * (pruning) load. Every kit a test makes is deleted in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class KitApiGameTests {
    private static final String EMPTY = "empty", BATCH = "kit_api";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static String freshId(String prefix) {
        String id = (prefix + "_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).toLowerCase(Locale.ROOT);
        return id.substring(0, Math.min(24, id.length()));
    }

    private static Kit saved(GameTestHelper h, String prefix) {
        Kit k = new Kit(freshId(prefix), "Api " + prefix, System.currentTimeMillis());
        k.items.add(new ItemStack(Items.BREAD, 4));
        Kits.Outcome out = Kits.put(server(h), k);
        h.assertTrue(out.ok(), "the kit saves: " + out.message());
        return k;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_every_save_moves_the_edit_stamp_on_and_storage_keeps_it(GameTestHelper h) {
        MinecraftServer server = server(h);
        long before = System.currentTimeMillis();
        Kit k = saved(h, "stamp");
        try {
            long first = Kits.kit(server, k.id).edited;
            h.assertTrue(first >= before, "a new kit is stamped with the save time: " + first + " vs " + before);
            // two saves in the same millisecond still give two stamps
            Kit edit = Kits.kit(server, k.id).copy();
            edit.price = 25;
            Kits.put(server, edit);
            long second = Kits.kit(server, k.id).edited;
            Kit again = Kits.kit(server, k.id).copy();
            again.price = 30;
            Kits.put(server, again);
            long third = Kits.kit(server, k.id).edited;
            h.assertTrue(second > first && third > second, "each save moves the stamp on: " + first + ", " + second + ", " + third);
            // a draft carrying an old stamp (or none) never takes the stamp back
            Kit stale = Kits.kit(server, k.id).copy();
            stale.edited = 0;
            Kits.put(server, stale);
            h.assertTrue(Kits.kit(server, k.id).edited > third, "a save from a draft with no stamp still moves it on");
            // the world copy and the copy for drafts keep it
            Kit now = Kits.kit(server, k.id);
            h.assertTrue(Kit.load(now.save()).edited == now.edited, "save and load keep the stamp");
            h.assertTrue(now.copy().edited == now.edited, "a copy keeps the stamp");
            net.minecraft.nbt.CompoundTag before1 = new Kit("old_kit", "Old", 1000L).save();
            before1.remove("Edited");
            h.assertTrue(Kit.load(before1).edited == 0, "a kit saved before stamps reads 0");
            h.assertTrue(BspKits.detail(server, k.id).orElseThrow().edited() == now.edited, "the read answers the stamp");
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_detail_reads_a_kit_whole_with_items_as_text(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = new Kit(freshId("detail"), "Api detail", System.currentTimeMillis());
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(Enchantments.SHARPNESS, 3);
        k.items.add(sword);
        k.items.add(new ItemStack(Items.ARROW, 32));
        k.icon = new ItemStack(Items.BOW);
        k.description = "For testing";
        k.once = true;
        k.cooldownSeconds = 60;
        k.price = 120;
        k.ranks.add("vip");
        k.servers.add("spawn");
        k.conditions.add("no_totem");
        k.grant = Kit.Grant.SERVERS;
        k.grantServers.add("survival");
        k.enabled = false;
        k.inBrowser = false;
        h.assertTrue(Kits.put(server, k).ok(), "the kit saves");
        try {
            BspKits.Detail d = BspKits.detail(server, k.id.toUpperCase(Locale.ROOT)).orElse(null);
            h.assertTrue(d != null, "found by id in any case");
            h.assertTrue(d.id().equals(k.id) && d.name().equals("Api detail") && d.description().equals("For testing"), "id, name, description");
            h.assertTrue(d.items().size() == 2, "two item rows: " + d.items());
            BspKits.ItemRow first = d.items().get(0);
            h.assertTrue(first.item().equals("minecraft:diamond_sword") && first.count() == 1 && first.nbt().contains("sharpness"), "the sword as text with its enchantment: " + first);
            h.assertTrue(d.items().get(1).equals(new BspKits.ItemRow("minecraft:arrow", 32, "")), "the arrows, no NBT: " + d.items().get(1));
            h.assertTrue(d.icon().item().equals("minecraft:bow") && d.icon().count() == 1, "the icon as set: " + d.icon());
            h.assertTrue(!d.enabled() && !d.inBrowser() && d.once() && d.cooldownSeconds() == 60 && d.price() == 120, "the rules");
            h.assertTrue(d.ranks().equals(List.of("vip")) && d.servers().equals(List.of("spawn")) && d.conditions().equals(List.of("no_totem")), "ranks, servers, conditions");
            h.assertTrue(d.grant().equals("SERVERS") && d.grantServers().equals(List.of("survival")), "the grant and its servers");
            h.assertTrue(d.created() == k.createdMs && d.edited() == Kits.kit(server, k.id).edited && d.taken() == 0 && d.lastTakenBy().isEmpty() && d.missing() == 0, "stamps and counts");
            h.assertTrue(BspKits.details(server).stream().anyMatch(x -> x.id().equals(k.id)), "the list has it");
            // the list answers copies: changing the kit afterwards leaves an earlier answer as it was
            Kit edit = Kits.kit(server, k.id).copy();
            edit.items.clear();
            Kits.put(server, edit);
            h.assertTrue(d.items().size() == 2 && BspKits.detail(server, k.id).orElseThrow().items().isEmpty(), "an answer is a snapshot");
            h.assertTrue(BspKits.detail(server, "no_such_kit_here").isEmpty() && BspKits.detail(server, null).isEmpty(), "an unknown id answers nothing");
        } finally {
            Kits.delete(server, k.id);
        }
        Kit plain = saved(h, "noicon");
        try {
            h.assertTrue(BspKits.detail(server, plain.id).orElseThrow().icon().equals(new BspKits.ItemRow("", 0, "")), "no icon set reads as an empty row");
        } finally {
            Kits.delete(server, plain.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_choices_list_what_a_kit_is_picked_from(GameTestHelper h) {
        MinecraftServer server = server(h);
        BspKits.Choices c = BspKits.choices(server);
        h.assertTrue(BspKits.VERSION == 1, "version 1");
        h.assertTrue(c.grants().equals(List.of("NONE", "NETWORK", "SERVERS")), "the grant kinds: " + c.grants());
        h.assertTrue(c.maxItems() == Kits.maxItems() && c.maxItems() >= 1 && c.maxItems() <= 36, "the item limit: " + c.maxItems());
        h.assertTrue(!c.servers().isEmpty() && c.servers().get(0).equals(c.thisServer()) && c.thisServer().equals(Kits.serverId()), "this server first: " + c.servers());
        BspKits.Condition noTotem = c.conditions().stream().filter(x -> x.key().equals("no_totem")).findFirst().orElse(null);
        h.assertTrue(noTotem != null, "no_totem is offered: " + c.conditions());
        h.assertTrue(noTotem.label().equals("Owns no Shatter Totem anywhere on the network"), "with the editor's sentence: " + noTotem.label());
        h.assertTrue(c.ranks() != null, "ranks are a list (empty without LuckPerms)");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_changed_events_come_for_edits_and_deletes_here_and_from_elsewhere(GameTestHelper h) {
        MinecraftServer server = server(h);
        String id = freshId("events");
        List<KitEvent.Changed> seen = new ArrayList<>();
        Consumer<KitEvent.Changed> listener = e -> {
            if (e.getKitId().equals(id)) {
                seen.add(e);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            Kit k = new Kit(id, "Api events", System.currentTimeMillis());
            k.items.add(new ItemStack(Items.APPLE));
            Kits.put(server, k);
            h.assertTrue(seen.size() == 1 && !seen.get(0).isDeleted() && !seen.get(0).isHeard() && seen.get(0).getKitName().equals("Api events"), "a save here posts one, not heard");
            // the same record read back (this server's own write, the catch-up, a taken count) posts nothing
            Kit same = Kits.kit(server, id).copy();
            same.taken = 5;
            KitSync.apply(server, List.of(same), id);
            h.assertTrue(seen.size() == 1, "a re-read with the same stamp posts nothing: " + seen.size());
            // an edit made elsewhere
            Kit newer = Kits.kit(server, id).copy();
            newer.name = "Renamed elsewhere";
            newer.edited = newer.edited + 10;
            KitSync.apply(server, List.of(newer), id);
            h.assertTrue(seen.size() == 2 && seen.get(1).isHeard() && !seen.get(1).isDeleted() && seen.get(1).getKitName().equals("Renamed elsewhere"), "an edit read from elsewhere posts one, heard");
            // deleted elsewhere
            KitSync.apply(server, List.of(), id);
            h.assertTrue(seen.size() == 3 && seen.get(2).isHeard() && seen.get(2).isDeleted(), "a delete read from elsewhere posts one, heard and deleted");
            KitSync.apply(server, List.of(), id);
            h.assertTrue(seen.size() == 3, "a delete of a kit already gone posts nothing");
            // made again and deleted here
            Kit back = new Kit(id, "Api events", System.currentTimeMillis());
            back.items.add(new ItemStack(Items.APPLE));
            Kits.put(server, back);
            Kits.delete(server, id);
            h.assertTrue(seen.size() == 5 && seen.get(4).isDeleted() && !seen.get(4).isHeard(), "a delete here posts one, not heard");
            Kits.delete(server, id);
            h.assertTrue(seen.size() == 5, "deleting nothing posts nothing");
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
            Kits.delete(server, id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void kit_api_a_read_older_than_a_save_here_never_undoes_it(GameTestHelper h) {
        MinecraftServer server = server(h);
        long asked = KitSync.mark(); // a database read starts here...
        Kit k = saved(h, "race");
        String id = k.id;
        try {
            Kit mine = Kits.kit(server, id);
            Kit older = mine.copy();
            older.name = "From the database";
            older.edited = mine.edited - 1;
            KitSync.apply(server, List.of(older), id, false, asked); // ...and lands after this server's save
            h.assertTrue(Kits.kit(server, id).name.equals(mine.name) && Kits.kit(server, id).edited == mine.edited, "the save made here stays");
            KitSync.apply(server, List.of(), id, false, asked); // a read of this one kit that found nothing, from before it was written
            h.assertTrue(Kits.kit(server, id) != null, "an empty read from before the save does not delete the kit made here");
            List<Kit> others = new java.util.ArrayList<>(); // a full read holding every kit but the one made here a moment ago
            for (Kit o : Kits.all(server)) {
                if (!o.id.equals(id)) {
                    others.add(o.copy());
                }
            }
            KitSync.apply(server, others, null, true, asked);
            h.assertTrue(Kits.kit(server, id) != null, "a full read from before the kit was made does not prune it");
            long later = KitSync.mark();
            Kit newer = mine.copy();
            newer.name = "Edited elsewhere";
            newer.edited = mine.edited + 5;
            KitSync.apply(server, List.of(newer), id, false, later);
            h.assertTrue(Kits.kit(server, id).name.equals("Edited elsewhere"), "a read started after the save is taken");
        } finally {
            Kits.delete(server, id);
        }
        h.succeed();
    }
}
