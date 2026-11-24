package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.PlayerReset;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.tracking.TrackedTag;
import com.mrgregles.bsp_core.tracking.TrackerOwners;
import com.mrgregles.bsp_core.tracking.TrackerRecord;
import com.mrgregles.bsp_core.tracking.TrackerRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Machine tracking's server rules (vault note "Machine Tracking - Brief"): tag slots bought with Copper coins at a rising price up to
 * the cap, the Remote Control unlock for Diamond coins, tagging only machines and only into a free slot, names cleaned, a broken
 * machine's tag removed, a player reset clearing tags, slots and the unlock but keeping the layout, a late reset keeping what changed
 * after it, an older copy never bringing a reset's losses back, and purchases and tags from an earlier season counting for nothing. In
 * the {@code player_data} batch.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TrackingGameTests {
    private static final String BATCH = "player_data";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static int coins(ServerPlayer p, CoinTier tier) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(tier.coin())) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static TrackerRecord record(GameTestHelper h, ServerPlayer p) {
        return TrackerOwners.get(server(h)).record(server(h), p.getUUID());
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_slots_cost_rising_copper_and_stop_at_the_cap(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        int season = TrackerOwners.season(server);
        int free = TrackerRules.freeSlots();
        h.assertTrue(TrackerRules.slots(record(h, p), season) == free, "a new player has the free slots only");
        h.assertTrue(TrackerRules.nextPrice(record(h, p), season) == 8, "the first slot costs 8 Copper");
        h.assertTrue("no_copper".equals(TrackerRules.buySlot(p)), "no coins, no slot");
        p.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 30));
        h.assertTrue(TrackerRules.buySlot(p) == null && coins(p, CoinTier.COPPER) == 22, "the first slot takes 8 Copper");
        h.assertTrue(TrackerRules.nextPrice(record(h, p), season) == 16, "the next costs 16");
        h.assertTrue(TrackerRules.buySlot(p) == null && coins(p, CoinTier.COPPER) == 6, "the second takes 16");
        h.assertTrue(TrackerRules.slots(record(h, p), season) == free + 2, "two slots on top of the free ones");
        h.assertTrue("no_copper".equals(TrackerRules.buySlot(p)) && coins(p, CoinTier.COPPER) == 6, "6 Copper cannot buy a 24 Copper slot, and nothing is taken");
        // at the cap: nothing more to buy
        TrackerRecord r = record(h, p);
        r.bought = TrackerRules.maxSlots() - free;
        r.boughtSeason = season;
        TrackerOwners.get(server).put(server, p.getUUID(), r);
        h.assertTrue(TrackerRules.nextPrice(record(h, p), season) == -1 && "slots_max".equals(TrackerRules.buySlot(p)), "every slot bought: none for sale");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_remote_unlock_costs_diamond_coins_once(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        int season = TrackerOwners.season(server);
        h.assertTrue("no_diamond".equals(TrackerRules.unlockRemote(p)) && !record(h, p).remoteIn(season), "no Diamond coins, no unlock");
        p.getInventory().add(new ItemStack(CoinTier.DIAMOND.coin(), TrackerRules.remotePrice() + 1));
        h.assertTrue(TrackerRules.unlockRemote(p) == null && record(h, p).remoteIn(season), "the unlock is bought");
        h.assertTrue(coins(p, CoinTier.DIAMOND) == 1, "it took " + TrackerRules.remotePrice() + " Diamond coins");
        h.assertTrue("remote_owned".equals(TrackerRules.unlockRemote(p)) && coins(p, CoinTier.DIAMOND) == 1, "a second unlock is refused and charges nothing");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_tags_only_machines_and_only_into_a_free_slot(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        int free = TrackerRules.freeSlots();
        BlockPos stone = h.absolutePos(new BlockPos(0, 1, 0));
        h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        h.assertTrue("not_machine".equals(TrackerRules.tag(p, h.getLevel(), stone).refusal()), "a plain block is no machine");
        BlockPos[] chests = new BlockPos[free + 1];
        for (int i = 0; i < chests.length; i++) {
            chests[i] = h.absolutePos(new BlockPos(1 + i, 1, 1));
            h.getLevel().setBlockAndUpdate(chests[i], Blocks.FURNACE.defaultBlockState());
        }
        TrackerRules.TagResult first = TrackerRules.tag(p, h.getLevel(), chests[0]);
        h.assertTrue(first.refusal() == null && first.tag() != null && first.tag().name().isEmpty(), "a furnace is tagged, under its own name");
        h.assertTrue("already_tagged".equals(TrackerRules.tag(p, h.getLevel(), chests[0]).refusal()), "the same machine twice is refused");
        for (int i = 1; i < free; i++) {
            h.assertTrue(TrackerRules.tag(p, h.getLevel(), chests[i]).refusal() == null, "free slot " + (i + 1) + " takes a tag");
        }
        h.assertTrue("slots_full".equals(TrackerRules.tag(p, h.getLevel(), chests[free]).refusal()), "every slot used: the next is refused");
        h.assertTrue(TrackerRules.untagAt(p, h.getLevel(), chests[0]), "untagging frees a slot");
        h.assertTrue(TrackerRules.tag(p, h.getLevel(), chests[free]).refusal() == null, "and the freed slot takes a new tag");
        h.assertTrue(record(h, p).tags.size() == free, "as many tags as slots");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_names_are_cleaned_and_capped(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.FURNACE.defaultBlockState());
        TrackedTag t = TrackerRules.tag(p, h.getLevel(), at).tag();
        h.assertTrue(t != null, "tagged");
        h.assertTrue(TrackerRules.rename(p, t.id(), "  §cBig§r   Bertha  ") == null, "renamed");
        h.assertTrue("Big Bertha".equals(record(h, p).tags.get(0).name()), "colour codes go, spaces are squeezed: '" + record(h, p).tags.get(0).name() + "'");
        TrackerRules.rename(p, t.id(), "A very long machine name that goes on and on");
        h.assertTrue(record(h, p).tags.get(0).name().length() <= TrackedTag.NAME_MAX, "at most " + TrackedTag.NAME_MAX + " characters");
        TrackerRules.rename(p, t.id(), "   ");
        h.assertTrue(record(h, p).tags.get(0).name().isEmpty(), "a blank name gives the block's own name back");
        h.assertTrue("no_tag".equals(TrackerRules.rename(p, "nope0000", "x")), "an unknown tag is refused");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_broken_machine_loses_its_tag(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.FURNACE.defaultBlockState());
        h.assertTrue(TrackerRules.tag(p, h.getLevel(), at).refusal() == null, "tagged");
        TrackedTag t = record(h, p).tags.get(0);
        h.assertTrue(TrackerRules.stillThere(h.getLevel(), t), "the furnace is still there");
        h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        h.assertTrue(!TrackerRules.stillThere(h.getLevel(), t), "a different block now");
        TrackerRules.machineGone(h.getLevel(), at);
        h.assertTrue(record(h, p).tags.isEmpty(), "the tag is removed and its slot free");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    /** A record with a tag, a bought slot, the unlock and a layout. */
    private static TrackerRecord full(MinecraftServer server, int season) {
        TrackerRecord r = new TrackerRecord();
        r.bought = 3;
        r.boughtSeason = season;
        r.remote = true;
        r.remoteSeason = season;
        r.tags.add(new TrackedTag("abcd1234", TrackerRules.serverId(), new ResourceLocation("minecraft", "overworld"), new BlockPos(5, 64, 5), "Smelter",
                new ResourceLocation("minecraft", "furnace"), season, 1));
        CompoundTag layout = new CompoundTag();
        layout.putString("pages", "my layout");
        r.layout = layout;
        return r;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public void tracking_player_reset_clears_tags_slots_and_unlock_but_keeps_the_layout(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID id = p.getUUID();
        int season = TrackerOwners.season(server);
        TrackerOwners.get(server).put(server, id, full(server, season));
        PlayerReset.reset(server, id, p.getGameProfile().getName(), "test admin", p, r -> {});
        TrackerRecord r = record(h, p);
        h.assertTrue(r.tags.isEmpty() && r.boughtIn(season) == 0 && !r.remoteIn(season), "tags, bought slots and the unlock are gone");
        h.assertTrue(TrackerRules.slots(r, season) == TrackerRules.freeSlots(), "back to the free slots");
        h.assertTrue("my layout".equals(r.layout.getString("pages")), "the Holo Grid layout and presets stay");
        h.assertTrue(r.resetMs > 0, "the record carries the reset's stamp");
        TrackerOwners.get(server).forgetForTest(id);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_late_reset_keeps_what_changed_after_it_and_old_copies_stay_reset(GameTestHelper h) {
        MinecraftServer server = server(h);
        TrackerOwners owners = TrackerOwners.get(server);
        UUID id = UUID.randomUUID();
        int season = TrackerOwners.season(server);
        // a copy changed after the reset, still holding what was made before it (a server that missed the notice): the reset reaching it late
        // still takes what was made before it, and keeps a tag made after it
        TrackerRecord before = full(server, season);
        long resetAt = System.currentTimeMillis() - 60_000L;
        before.tags.add(new TrackedTag("aaaa0000", TrackerRules.serverId(), new ResourceLocation("minecraft", "overworld"), new BlockPos(9, 64, 9), "After",
                new ResourceLocation("minecraft", "furnace"), season, resetAt + 1_000L));
        owners.put(server, id, before);
        long changed = owners.rawForTest(id).updatedMs;
        h.assertTrue(owners.resetPlayer(server, id, resetAt, false), "a late reset drops what was made before it, though the copy changed after it");
        TrackerRecord kept = owners.rawForTest(id);
        h.assertTrue(kept.tags.size() == 1 && kept.tags.get(0).id().equals("aaaa0000"), "the tag made after the reset stays: " + kept.tags);
        h.assertTrue(kept.bought == 0 && !kept.remote && kept.resetMs == resetAt, "slots and the unlock bought before it go; the stamp is raised");
        h.assertTrue("my layout".equals(kept.layout.getString("pages")), "the layout stays");
        // a newer reset clears the rest
        h.assertTrue(owners.resetPlayer(server, id, changed + 1_000L, false), "a newer reset clears the tag made before it");
        h.assertTrue(owners.rawForTest(id).tags.isEmpty(), "cleared");
        // an older copy from the network (from before that reset) does not bring the tags back
        TrackerRecord old = full(server, season);
        old.updatedMs = changed + 500L;
        old.resetMs = 0;
        owners.arrived(server, id, old.save());
        h.assertTrue(owners.rawForTest(id).tags.isEmpty() && owners.rawForTest(id).bought == 0, "a copy from before the reset brings nothing back");
        // a newer copy that missed the reset keeps what was made after the reset
        TrackerRecord mine = owners.rawForTest(id);
        TrackerRecord newer = full(server, season);
        newer.updatedMs = mine.updatedMs + 10_000L;
        newer.resetMs = 0;
        newer.tags.clear();
        newer.tags.add(new TrackedTag("ffff0000", TrackerRules.serverId(), new ResourceLocation("minecraft", "overworld"), new BlockPos(1, 1, 1), "New", new ResourceLocation("minecraft", "furnace"), season, newer.updatedMs));
        owners.arrived(server, id, newer.save());
        TrackerRecord got = owners.rawForTest(id);
        h.assertTrue(got.resetMs == changed + 1_000L, "the reset stamp is never lost");
        h.assertTrue(got.tags.size() == 1 && got.tags.get(0).id().equals("ffff0000"), "a copy changed after the reset is kept as it is");
        owners.forgetForTest(id);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_an_earlier_seasons_slots_unlock_and_tags_count_for_nothing(GameTestHelper h) {
        MinecraftServer server = server(h);
        TrackerOwners owners = TrackerOwners.get(server);
        UUID id = UUID.randomUUID();
        int season = TrackerOwners.season(server);
        owners.put(server, id, full(server, season - 1)); // made in the season before this one (or 0 in a fresh test world)
        TrackerRecord r = owners.record(server, id);
        h.assertTrue(r.tags.isEmpty() && r.boughtIn(season) == 0 && !r.remoteIn(season), "nothing from the earlier season counts");
        h.assertTrue(TrackerRules.slots(r, season) == TrackerRules.freeSlots(), "the free slots only");
        h.assertTrue("my layout".equals(r.layout.getString("pages")), "the layout is kept");
        h.assertTrue(owners.rawForTest(id).tags.isEmpty(), "the stored record is pruned too");
        // the pure rule, a season on
        TrackerRecord now = full(server, season);
        h.assertTrue(now.prune(season + 1) && now.bought == 0 && !now.remote && now.tags.isEmpty(), "a season end prunes the lot");
        owners.forgetForTest(id);
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_tagger_tags_on_right_click_and_untags_when_sneaking(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.FURNACE.defaultBlockState());
        ItemStack tagger = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.MACHINE_TAGGER.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tagger);
        var hitAt = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), net.minecraft.core.Direction.UP, at, false);
        var ctx = new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND, hitAt);
        var used = tagger.getItem().onItemUseFirst(tagger, ctx);
        h.assertTrue(used.consumesAction(), "the right-click is the tagger's, so the furnace does not open: " + used);
        h.assertTrue(record(h, p).tags.size() == 1, "the furnace is tagged");
        p.setShiftKeyDown(true);
        tagger.getItem().onItemUseFirst(tagger, ctx);
        h.assertTrue(record(h, p).tags.isEmpty(), "sneaking untags it");
        // a plain block lets the click go on as usual
        BlockPos stone = h.absolutePos(new BlockPos(2, 1, 1));
        h.getLevel().setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var onStone = new net.minecraft.world.item.context.UseOnContext(p, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(stone), net.minecraft.core.Direction.UP, stone, false));
        h.assertTrue(tagger.getItem().onItemUseFirst(tagger, onStone) == net.minecraft.world.InteractionResult.PASS, "a plain block is passed on");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_machine_reading_has_its_items_and_says_when_it_is_gone(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.FURNACE.defaultBlockState());
        if (h.getLevel().getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity f) {
            f.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL, 9));
        }
        TrackedTag t = TrackerRules.tag(p, h.getLevel(), at).tag();
        h.assertTrue(t != null, "tagged");
        var reading = com.mrgregles.bsp_core.tracking.WatchService.readHere(server, t, p, new java.util.HashMap<>());
        h.assertTrue(reading.getByte("st") == com.mrgregles.bsp_core.tracking.MachineSnapshot.OK, "a loaded machine reads OK");
        h.assertTrue(reading.getInt("slots") == 3, "the furnace's three slots: " + reading.getInt("slots"));
        var items = reading.getList("items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(items.size() == 1 && items.getCompound(0).getString("id").equals("minecraft:coal") && items.getCompound(0).getInt("n") == 9,
                "the coal in its fuel slot: " + items);
        h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
        var gone = com.mrgregles.bsp_core.tracking.WatchService.readHere(server, t, p, new java.util.HashMap<>());
        h.assertTrue(gone.getByte("st") == com.mrgregles.bsp_core.tracking.MachineSnapshot.GONE && !gone.contains("items"), "a broken machine reads GONE, with nothing else");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_player_watches_only_their_own_tags_and_at_most_the_cap(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.FURNACE.defaultBlockState());
        TrackedTag t = TrackerRules.tag(p, h.getLevel(), at).tag();
        java.util.List<String> asked = new java.util.ArrayList<>();
        asked.add(t.id());
        asked.add("deadbeef"); // not theirs
        for (int i = 0; i < 40; i++) {
            asked.add(t.id());
        }
        com.mrgregles.bsp_core.tracking.WatchService.watch(p, asked);
        var watching = com.mrgregles.bsp_core.tracking.WatchService.watching(p.getUUID());
        h.assertTrue(watching.equals(java.util.Set.of(t.id())), "only their own tag, once: " + watching);
        com.mrgregles.bsp_core.tracking.WatchService.watch(p, java.util.List.of());
        h.assertTrue(com.mrgregles.bsp_core.tracking.WatchService.watching(p.getUUID()).isEmpty(), "an empty list stops watching");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_layout_from_a_client_is_cut_to_the_rules(GameTestHelper h) {
        CompoundTag in = new CompoundTag();
        net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 12; i++) {
            CompoundTag page = new CompoundTag();
            page.putString("name", i == 0 ? "\u00a7aMain base" : "");
            page.putInt("cols", i == 0 ? 99 : 12);
            page.putInt("rows", i == 0 ? 1 : 8);
            net.minecraft.nbt.ListTag boxes = new net.minecraft.nbt.ListTag();
            boxes.add(box(0, 0, 5, 3, "map"));
            boxes.add(box(2, 1, 4, 4, "tag:abcd1234")); // overlaps the first
            boxes.add(box(20, 0, 50, 50, "tag:bad id!"));
            page.put("boxes", boxes);
            pages.add(page);
        }
        in.put("pages", pages);
        CompoundTag out = com.mrgregles.bsp_core.tracking.GridLayout.clean(in);
        var outPages = out.getList("pages", net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(outPages.size() == com.mrgregles.bsp_core.tracking.GridLayout.MAX_PAGES, "at most 9 pages: " + outPages.size());
        CompoundTag first = outPages.getCompound(0);
        h.assertTrue(first.getInt("cols") == 32 && first.getInt("rows") == 3, "the grid size is clamped: " + first.getInt("cols") + " x " + first.getInt("rows"));
        h.assertTrue("Main base".equals(first.getString("name")) && "Page 2".equals(outPages.getCompound(1).getString("name")), "names are cleaned, blank ones numbered");
        var boxes = first.getList("boxes", net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(boxes.size() == 2, "the overlapping box is dropped: " + boxes);
        CompoundTag clamped = boxes.getCompound(1);
        h.assertTrue(clamped.getInt("x") == 20 && clamped.getInt("x") + clamped.getInt("w") <= 32 && clamped.getInt("y") + clamped.getInt("h") <= 3, "a box is kept inside its grid");
        h.assertTrue(clamped.getString("src").isEmpty(), "a source that is no tag id is emptied");
        h.succeed();
    }

    private static CompoundTag box(int x, int y, int w, int hgt, String src) {
        CompoundTag b = new CompoundTag();
        b.putInt("x", x);
        b.putInt("y", y);
        b.putInt("w", w);
        b.putInt("h", hgt);
        b.putString("src", src);
        return b;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_layout_saves_are_kept_through_a_reset_and_limited_in_a_burst(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompoundTag layout = new CompoundTag();
        net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
        CompoundTag page = new CompoundTag();
        page.putString("name", "Power");
        pages.add(page);
        layout.put("pages", pages);
        h.assertTrue(com.mrgregles.bsp_core.tracking.TrackerActions.saveLayout(p, layout), "saved");
        h.assertTrue("Power".equals(record(h, p).layout.getList("pages", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("name")), "the layout is in the record");
        TrackerOwners.get(server).resetPlayer(server, p.getUUID(), System.currentTimeMillis() + 5, true);
        h.assertTrue("Power".equals(record(h, p).layout.getList("pages", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).getString("name")), "a reset keeps it");
        boolean refused = false;
        for (int i = 0; i < com.mrgregles.bsp_core.tracking.TrackerActions.LAYOUT_BURST + 5; i++) {
            page.putString("name", "Power " + i);
            refused |= !com.mrgregles.bsp_core.tracking.TrackerActions.saveLayout(p, layout);
        }
        h.assertTrue(refused, "a flood of layout saves is cut off");
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_watch_from_another_server_is_kept_until_stopped(GameTestHelper h) {
        MinecraftServer server = server(h);
        com.mrgregles.bsp_core.tracking.TrackerRelay.clearForTest();
        UUID player = UUID.randomUUID();
        String here = TrackerRules.serverId();
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        String dim = h.getLevel().dimension().location().toString();
        com.mrgregles.bsp_core.tracking.TrackerRelay.onWatch(server, "other-server|survival-9|" + player + "|abcd1234;" + dim + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";minecraft:furnace");
        h.assertTrue(here.equals("other-server") || com.mrgregles.bsp_core.tracking.TrackerRelay.remoteWatchers().isEmpty(), "a watch meant for another server is ignored");
        com.mrgregles.bsp_core.tracking.TrackerRelay.onWatch(server, here + "|survival-9|" + player + "|abcd1234;" + dim + ";" + at.getX() + ";" + at.getY() + ";" + at.getZ() + ";minecraft:furnace|junk");
        h.assertTrue(com.mrgregles.bsp_core.tracking.TrackerRelay.remoteWatchers().contains(player), "a player elsewhere watches a machine here");
        com.mrgregles.bsp_core.tracking.TrackerRelay.onWatch(server, here + "|survival-9|" + player);
        h.assertTrue(!com.mrgregles.bsp_core.tracking.TrackerRelay.remoteWatchers().contains(player), "a watch with no tags stops it");
        com.mrgregles.bsp_core.tracking.TrackerRelay.clearForTest();
        h.succeed();
    }
    /** A transmitter at {@code rel}, placed by {@code owner}, with {@code cards} range cards and {@code fe} FE. */
    private static com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity transmitter(GameTestHelper h, BlockPos rel, ServerPlayer owner, int cards, int fe) {
        BlockPos at = h.absolutePos(rel);
        h.getLevel().setBlockAndUpdate(at, com.mrgregles.bsp_core.registry.ModBlocks.REMOTE_TRANSMITTER.get().defaultBlockState());
        var tx = (com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity) h.getLevel().getBlockEntity(at);
        tx.setOwner(owner.getUUID(), owner.getGameProfile().getName());
        com.mrgregles.bsp_core.tracking.TransmitterLedger.get(server(h)).add(h.getLevel(), at, owner.getUUID());
        if (cards > 0) {
            tx.getCards().setStackInSlot(0, new ItemStack(com.mrgregles.bsp_core.registry.ModItems.RANGE_CARD.get(), Math.min(64, cards)));
        }
        if (fe > 0) {
            tx.fillForTest(fe);
        }
        return tx;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_transmitter_range_grows_by_card_to_the_cap_and_draws_once_a_tick(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        var tx = transmitter(h, new BlockPos(2, 1, 2), p, 0, 0);
        h.assertTrue(tx.range() == 16, "no cards: 16 blocks: " + tx.range());
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity.range(1) == 32 && com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity.range(64) == 1040,
                "16 more per card");
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity.range(191) == 3072 && com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockEntity.range(192) == 3072,
                "191 cards reach the 3072 cap, more change nothing");
        h.assertTrue(!tx.getCards().isItemValid(0, new ItemStack(net.minecraft.world.item.Items.PAPER)), "only range cards go in");
        tx.getCards().setStackInSlot(1, new ItemStack(com.mrgregles.bsp_core.registry.ModItems.RANGE_CARD.get(), 4));
        BlockPos at = tx.getBlockPos();
        h.assertTrue(tx.range() == 80 && tx.covers(at.offset(80, -80, 80)) && !tx.covers(at.offset(81, 0, 0)), "a cube of 80 each way with 4 cards");
        h.assertTrue(!tx.powered() && !tx.operate(), "no FE, no operating");
        tx.fillForTest(1000);
        int before = tx.energyStored();
        h.assertTrue(tx.operate() && tx.operate(), "powered: operating works, twice in one tick");
        h.assertTrue(before - tx.energyStored() == tx.fePerTick() && tx.fePerTick() == 24, "one tick's draw (20 + 1 per card), however many operate: " + (before - tx.energyStored()));
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_transmitters_are_limited_per_player_and_serve_only_their_team(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        ServerPlayer stranger = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        int max = com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.TRANSMITTER_MAX_PER_PLAYER, 4);
        for (int i = 0; i < max; i++) {
            transmitter(h, new BlockPos(1 + i, 1, 4), p, 0, 0);
        }
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockItem.overLimit(p), "at the limit, no more");
        h.assertTrue(!com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockItem.overLimit(stranger), "another player may still place theirs");
        h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(1, 1, 4)), Blocks.AIR.defaultBlockState());
        h.assertTrue(!com.mrgregles.bsp_core.tracking.RemoteTransmitterBlockItem.overLimit(p), "a broken one frees its place");
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteOperate.mayUse(server, p.getUUID(), p.getUUID()), "the owner uses it");
        h.assertTrue(!com.mrgregles.bsp_core.tracking.RemoteOperate.mayUse(server, p.getUUID(), stranger.getUUID()), "a stranger does not");
        for (int i = 2; i <= max; i++) {
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(i, 1, 4)), Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_operating_needs_the_unlock_and_a_powered_transmitter_in_range(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos furnace = h.absolutePos(new BlockPos(1, 1, 1));
        h.getLevel().setBlockAndUpdate(furnace, Blocks.FURNACE.defaultBlockState());
        TrackedTag t = TrackerRules.tag(p, h.getLevel(), furnace).tag();
        h.assertTrue(t != null, "tagged");
        h.assertTrue("op_locked".equals(com.mrgregles.bsp_core.tracking.RemoteOperate.open(p, t.id())), "no unlock: no operating");
        TrackerRecord r = record(h, p);
        r.remote = true;
        r.remoteSeason = TrackerOwners.season(server);
        TrackerOwners.get(server).put(server, p.getUUID(), r);
        p.setPos(furnace.getX() + 0.5, furnace.getY(), furnace.getZ() + 2.5); // standing by the machine
        h.assertTrue("op_no_transmitter".equals(com.mrgregles.bsp_core.tracking.RemoteOperate.open(p, t.id())), "no transmitter in range: read-only");
        var tx = transmitter(h, new BlockPos(3, 1, 3), p, 0, 0);
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteOperate.transmitterFor(p, h.getLevel(), furnace) == tx, "the transmitter covers the player and the machine");
        h.assertTrue("op_no_power".equals(com.mrgregles.bsp_core.tracking.RemoteOperate.open(p, t.id())), "without FE: no operating");
        h.assertTrue("no_tag".equals(com.mrgregles.bsp_core.tracking.RemoteOperate.open(p, "00000000")), "an unknown tag is refused");
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteOperate.stillValid(p, p.containerMenu) == null, "a screen not opened from the grid keeps Minecraft's own check");
        // far away: the player stands outside the 16-block range
        p.setPos(furnace.getX() + 200, furnace.getY(), furnace.getZ());
        h.assertTrue(com.mrgregles.bsp_core.tracking.RemoteOperate.transmitterFor(p, h.getLevel(), furnace) == null, "out of range, no transmitter serves");
        h.getLevel().setBlockAndUpdate(tx.getBlockPos(), Blocks.AIR.defaultBlockState());
        TrackerOwners.get(server).forgetForTest(p.getUUID());
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_bsp_and_mekanism_machines_give_their_own_readings(GameTestHelper h) {
        MinecraftServer server = server(h);
        com.mrgregles.bsp_core.tracking.MekanismAdapter.register();
        com.mrgregles.bsp_core.tracking.BspMachineAdapter.register();
        BlockPos crucible = h.absolutePos(new BlockPos(1, 1, 1));
        h.setBlock(new BlockPos(1, 1, 1), com.mrgregles.bsp_core.registry.ModBlocks.TETRIUM_CRUCIBLE.get());
        var bsp = com.mrgregles.bsp_core.tracking.MachineSnapshot.read(h.getLevel(), h.getLevel().getBlockEntity(crucible));
        h.assertTrue("bsp_machine".equals(bsp.getString("kind")) && bsp.getCompound("x").contains("progress"), "a BSP-Core machine reads as one: " + bsp);
        var factoryBlock = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekanism", "basic_smelting_factory"));
        if (factoryBlock != null && factoryBlock != Blocks.AIR) {
            BlockPos factory = h.absolutePos(new BlockPos(3, 1, 1));
            h.getLevel().setBlockAndUpdate(factory, factoryBlock.defaultBlockState());
            var mek = com.mrgregles.bsp_core.tracking.MachineSnapshot.read(h.getLevel(), h.getLevel().getBlockEntity(factory));
            h.assertTrue("mek_factory".equals(mek.getString("kind")), "a Mekanism factory reads as one: " + mek.getString("kind"));
            h.assertTrue(mek.getCompound("x").getList("procs", net.minecraft.nbt.Tag.TAG_INT).size() == 3, "a basic factory has three processes: " + mek.getCompound("x"));
            h.assertTrue(mek.getCompound("x").contains("redstone") && mek.getCompound("x").contains("active"), "with its redstone mode and active lamp");
            h.assertTrue(mek.contains("feMax"), "and its energy, through Forge's energy capability");
        }
        h.succeed();
    }
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_server_behind_on_seasons_keeps_the_new_seasons_tags(GameTestHelper h) {
        MinecraftServer server = server(h);
        int season = TrackerOwners.season(server);
        TrackerRecord later = full(server, season + 1); // made in a season this server has not heard of yet
        h.assertTrue(!later.prune(season) && later.tags.size() == 1 && later.bought == 3 && later.remote, "nothing of a later season is pruned");
        h.assertTrue(later.boughtIn(season) == 0 && !later.remoteIn(season), "but it does not count here until this server is in that season");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_a_layout_keeps_at_most_the_total_box_budget(GameTestHelper h) {
        CompoundTag in = new CompoundTag();
        net.minecraft.nbt.ListTag presets = new net.minecraft.nbt.ListTag();
        for (int p = 0; p < 10; p++) {
            net.minecraft.nbt.ListTag pages = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 9; i++) {
                CompoundTag page = new CompoundTag();
                page.putInt("cols", 32);
                page.putInt("rows", 24);
                net.minecraft.nbt.ListTag boxes = new net.minecraft.nbt.ListTag();
                for (int b = 0; b < 64; b++) {
                    boxes.add(box(b % 32, b / 32, 1, 1, "map"));
                }
                page.put("boxes", boxes);
                pages.add(page);
            }
            CompoundTag preset = new CompoundTag();
            preset.put("pages", pages);
            presets.add(preset);
        }
        in.put("presets", presets);
        CompoundTag out = com.mrgregles.bsp_core.tracking.GridLayout.clean(in);
        int total = 0;
        for (net.minecraft.nbt.Tag pr : out.getList("presets", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            for (net.minecraft.nbt.Tag pg : ((CompoundTag) pr).getList("pages", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                total += ((CompoundTag) pg).getList("boxes", net.minecraft.nbt.Tag.TAG_COMPOUND).size();
            }
        }
        h.assertTrue(total == com.mrgregles.bsp_core.tracking.GridLayout.MAX_TOTAL_BOXES, "every box together is capped: " + total);
        h.assertTrue(new com.mrgregles.bsp_core.network.TrackerActionPacket(4, "", "", out).layout().toString().length() < 32_767 * 4, "and stays small");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void tracking_pipes_put_range_cards_in_but_never_take_them_out(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        var tx = transmitter(h, new BlockPos(2, 1, 2), p, 10, 0);
        var cap = tx.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.DOWN).orElse(null);
        h.assertTrue(cap != null && cap.extractItem(0, 64, false).isEmpty() && tx.cardCount() == 10, "nothing comes out through a pipe");
        h.assertTrue(cap.insertItem(1, new ItemStack(com.mrgregles.bsp_core.registry.ModItems.RANGE_CARD.get(), 5), false).isEmpty() && tx.cardCount() == 15, "cards go in");
        h.getLevel().setBlockAndUpdate(tx.getBlockPos(), Blocks.AIR.defaultBlockState());
        h.succeed();
    }
}
