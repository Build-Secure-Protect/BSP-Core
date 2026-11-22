package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.looks.HatModels;
import com.mrgregles.bsp_core.looks.LookActions;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.looks.LookPixels;
import com.mrgregles.bsp_core.looks.PaintTools;
import com.mrgregles.bsp_core.looks.ScoreLeader;
import com.mrgregles.bsp_core.score.ScoreService;
import com.mrgregles.bsp_core.looks.LookOwner;
import com.mrgregles.bsp_core.looks.LookOwners;
import com.mrgregles.bsp_core.looks.TotemHats;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.looks.TotemLooks;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for totem looks (task 1): the look itself and its checks, the look kept on the totem and its item and dropped
 * when the totem changes owner, and who may dress a totem and which hats they have (through the permission test hook).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class LooksGameTests {
    private static final String EMPTY = "empty", BATCH = "tripwire"; // an existing batch: a new batch name reorders every batch

    private static ServerPlayer player(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    private static TotemOwner owner(ServerPlayer p) {
        return new TotemOwner(p.getUUID(), p.getGameProfile().getName());
    }

    private static ShatterTotemBlockEntity totem(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.setOwner(owner(owner));
        return be;
    }

    /** A painting with every pixel set to {@code index}. */
    private static byte[] paintAll(int index) {
        byte[] paint = new byte[TotemLook.PAINT_BYTES];
        for (int i = 0; i < TotemLook.PAINT_PIXELS; i++) {
            TotemLook.setPixel(paint, i, index);
        }
        return paint;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_look_is_checked_and_saved_whole(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        h.assertTrue(TotemLook.PAINT_PIXELS == 1552 && TotemLook.PAINT_BYTES == 776, "a painting covers the body and head: 1,552 pixels in 776 bytes, got " + TotemLook.PAINT_PIXELS);
        h.assertTrue(TotemLook.solid(id, 0x7A4FD6).problem() == null, "one colour is a sound solid look");
        h.assertTrue(TotemLook.gradient(id, TotemLook.Run.UP, 0xFFD23A, 0xB0203A).problem() == null, "two stops are a sound gradient");
        h.assertTrue(TotemLook.gradient(id, TotemLook.Run.SIDE, 1, 2, 3).problem() == null, "three stops are a sound gradient");
        h.assertTrue("bad_colours".equals(TotemLook.gradient(id, TotemLook.Run.UP, 1, 2, 3, 4).problem()), "four stops are refused");
        h.assertTrue("bad_colours".equals(TotemLook.gradient(id, TotemLook.Run.UP, 1).problem()), "one stop is no gradient");
        h.assertTrue("bad_colour".equals(TotemLook.solid(id, 0x1000000).problem()), "a colour past 0xFFFFFF is refused");
        h.assertTrue("unknown_hat".equals(TotemLook.solid(id, 1).withHat("crown_of_nowhere").problem()), "a hat that is not in the list is refused");
        h.assertTrue(TotemLook.solid(id, 1).withHat("top_hat").problem() == null, "a listed hat is fine");

        int[] sixteen = new int[16];
        h.assertTrue(TotemLook.painted(id, sixteen, paintAll(15)).problem() == null, "16 colours may be painted");
        h.assertTrue("too_many_colours".equals(TotemLook.painted(id, new int[17], paintAll(0)).problem()), "17 colours are refused");
        h.assertTrue("bad_painting".equals(TotemLook.painted(id, new int[4], new byte[10]).problem()), "a painting of the wrong size is refused");
        h.assertTrue("bad_painting".equals(TotemLook.painted(id, new int[4], paintAll(4)).problem()), "a pixel past the palette is refused");

        byte[] paint = paintAll(1);
        TotemLook.setPixel(paint, 0, 2);
        TotemLook.setPixel(paint, 1551, 3);
        TotemLook painted = TotemLook.painted(id, new int[]{0x101418, 0x1B2340, 0xFF7A1E, 0xFFD23A}, paint).withHead(TotemLook.Head.PLAYER).withHat("beanie");
        h.assertTrue(painted.pixel(0) == 2 && painted.pixel(1) == 1 && painted.pixel(1551) == 3, "pixels are packed two to a byte");
        for (TotemLook look : List.of(painted, TotemLook.gradient(id, TotemLook.Run.SIDE, 5, 6, 7).withHat("halo"), TotemLook.solid(id, 0xC8283C))) {
            h.assertTrue(look.equals(TotemLook.load(look.save())), "a look comes back whole from its tag: " + look);
        }
        CompoundTag broken = TotemLook.solid(id, 1).withHat("top_hat").save();
        broken.putString("Hat", "gone_in_an_update");
        h.assertTrue(TotemLook.load(broken) == null, "a saved look with an unknown hat loads as plain");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_look_stays_with_its_owner_and_goes_with_a_new_one(GameTestHelper h) {
        ServerPlayer ann = player(h), bob = player(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        ShatterTotemBlockEntity be = totem(h, pos, ann);
        try {
            TotemLook look = TotemLook.gradient(ann.getUUID(), TotemLook.Run.UP, 0xFFD23A, 0xFF7A3C, 0xB0203A).withHat("wizard");
            be.setLook(look);
            h.assertTrue(look.equals(be.getLook()), "the owner's look is on the totem");

            // saved and loaded with the block (the update tag clients get is the same tag)
            CompoundTag saved = be.saveWithoutMetadata();
            // loaded into a block entity outside the world, so no second placed totem shares this one's identity
            ShatterTotemBlockEntity copy = new ShatterTotemBlockEntity(h.absolutePos(pos), be.getBlockState());
            copy.load(saved);
            h.assertTrue(look.equals(copy.getLook()), "a saved totem loads with its look");
            h.assertTrue(be.getUpdateTag().contains("Shown") && !be.getUpdateTag().contains(TotemLook.TAG), "clients get what the totem shows, never the look itself");

            // picked up: the item carries it, for its owner only
            CompoundTag item = new CompoundTag();
            be.writeToStackTag(item);
            h.assertTrue(look.equals(TotemLook.readFor(item, ann.getUUID())), "the picked-up totem carries its look");
            h.assertTrue(TotemLook.readFor(item, bob.getUUID()) == null, "nobody else may wear it");
            ItemStack stack = new ItemStack(ModItems.SHATTER_TOTEM.get());
            stack.setTag(item.copy());
            owner(bob).applyTo(stack);
            h.assertTrue(!stack.getOrCreateTag().contains(TotemLook.TAG), "an item that changes owner loses the look");

            // a steal (or a transfer) leaves the totem plain, and giving it back does not bring the look back
            be.setOwner(owner(bob));
            h.assertTrue(be.getLook() == null, "the new owner gets a plain totem");
            be.setOwner(owner(ann));
            h.assertTrue(be.getLook() == null, "and the old owner gets it back plain");
            h.assertTrue(!be.saveWithoutMetadata().contains(TotemLook.TAG), "a plain totem saves no look");

            // a look made for someone else is never shown, even when put on directly
            copy.setLook(TotemLook.solid(bob.getUUID(), 0x2FA860));
            h.assertTrue(copy.getLook() == null, "a look made for another player does not show");
        } finally {
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(null);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void dressing_and_hats_follow_the_permissions(GameTestHelper h) {
        ServerPlayer vip = player(h), buyer = player(h), plain = player(h);
        BlockPos vipAt = new BlockPos(1, 2, 1), buyerAt = new BlockPos(3, 2, 1);
        Map<UUID, Set<String>> nodes = Map.of(
                vip.getUUID(), Set.of(TotemLooks.DRESS_PERMISSION),
                buyer.getUUID(), Set.of(TotemHats.permission("beanie"), TotemHats.permission("halo")));
        var realSource = TotemLooks.permissionSource;
        var realExclusive = BSPConfig.LOOKS_EXCLUSIVE_HATS.get();
        ShatterTotemBlockEntity vipTotem = totem(h, vipAt, vip), buyerTotem = totem(h, buyerAt, buyer);
        try {
            if (!LuckPermsBridge.isPresent()) {
                TotemLooks.permissionSource = null;
                h.assertTrue(!TotemLooks.mayDress(vip), "without LuckPerms only operators may dress totems");
            }
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            BSPConfig.LOOKS_EXCLUSIVE_HATS.set(List.of("halo"));

            h.assertTrue(TotemLooks.mayDress(vip) && !TotemLooks.mayDress(buyer) && !TotemLooks.mayDress(plain), "only the VIP may dress");
            h.assertTrue(TotemLooks.ownsHat(vip, "top_hat") && TotemLooks.ownsHat(vip, "propeller"), "VIP has every hat");
            h.assertTrue(!TotemLooks.ownsHat(vip, "halo"), "except an exclusive one");
            h.assertTrue(TotemLooks.ownsHat(buyer, "beanie") && TotemLooks.ownsHat(buyer, "halo") && !TotemLooks.ownsHat(buyer, "top_hat"), "a buyer has the hats they bought, exclusive or not");
            h.assertTrue(TotemLooks.hats(plain).isEmpty() && !TotemLooks.ownsHat(vip, "no_such_hat"), "nobody has an unknown hat");
            h.assertTrue(TotemLooks.hats(buyer).equals(List.of("halo", "beanie")), "hats are listed in the tab's order: " + TotemLooks.hats(buyer));

            TotemLook vipLook = TotemLook.solid(vip.getUUID(), 0x7A4FD6).withHat("top_hat");
            h.assertTrue("not_owner".equals(TotemLooks.refusal(vip, buyerTotem, vipLook)), "only the owner dresses a totem");
            h.assertTrue("not_yours".equals(TotemLooks.refusal(vip, vipTotem, TotemLook.solid(buyer.getUUID(), 1))), "a look is made for its wearer");
            h.assertTrue("unknown_hat".equals(TotemLooks.refusal(vip, vipTotem, vipLook.withHat("crown_of_nowhere"))), "a broken look is refused");
            h.assertTrue("hat_not_owned".equals(TotemLooks.refusal(vip, vipTotem, vipLook.withHat("halo"))), "an exclusive hat needs its own permission");
            h.assertTrue("no_dressing".equals(TotemLooks.refusal(buyer, buyerTotem, TotemLook.solid(buyer.getUUID(), 1).withHat("beanie"))), "a hat buyer does not unlock dressing");

            h.assertTrue(TotemLooks.apply(vip, vipTotem, vipLook) == null && vipTotem.getLook() != null && "top_hat".equals(vipTotem.getLook().hat()), "the VIP dresses their totem");
            h.assertTrue(vipTotem.getLook().made() >= vipLook.made(), "the look put on is timed by the server");
            h.assertTrue(TotemLooks.apply(buyer, buyerTotem, TotemLook.solid(buyer.getUUID(), 1)) != null && buyerTotem.getLook() == null, "a refused look is not put on");
        } finally {
            TotemLooks.permissionSource = realSource;
            BSPConfig.LOOKS_EXCLUSIVE_HATS.set(realExclusive);
            ((ShatterTotemBlockEntity) h.getBlockEntity(vipAt)).setOwner(null);
            ((ShatterTotemBlockEntity) h.getBlockEntity(buyerAt)).setOwner(null);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_totem_shows_its_look_only_with_dressing(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        TotemLook look = TotemLook.solid(id, 0x7A4FD6).withHat("top_hat");
        h.assertTrue(LookOwners.shown(look, null) == LookOwners.Shown.PLAIN, "an owner with no record shows a plain totem");

        LookOwner vip = new LookOwner();
        vip.dressing = true;
        vip.owned.addAll(List.of("top_hat", "beanie"));
        h.assertTrue(look.equals(LookOwners.shown(look, vip).body()) && "top_hat".equals(LookOwners.shown(look, vip).hat()), "a VIP's totem shows its look and hat");
        h.assertTrue("".equals(LookOwners.shown(look.withHat(""), vip).hat()), "a look without a hat shows none");
        h.assertTrue("".equals(LookOwners.shown(look.withHat("wizard"), vip).hat()), "a hat the owner no longer has is not shown");
        h.assertTrue(LookOwners.shown(null, vip).body() == null, "an undressed totem stays plain");

        LookOwner lapsed = new LookOwner();
        lapsed.owned.add("beanie");
        lapsed.bought.put("beanie", 100L);
        LookOwners.Shown s = LookOwners.shown(look, lapsed);
        h.assertTrue(s.body() == null && "beanie".equals(s.hat()), "when VIP lapses the look hides but the bought hat stays on: " + s);

        LookOwner buyer = new LookOwner();
        buyer.owned.addAll(List.of("halo", "beanie"));
        buyer.bought.put("halo", 100L);
        buyer.bought.put("beanie", 200L);
        h.assertTrue("beanie".equals(LookOwners.shown(null, buyer).hat()), "the newest bought hat goes on by itself");
        buyer.hatChoice = "halo";
        h.assertTrue("halo".equals(LookOwners.shown(null, buyer).hat()), "a chosen hat wins");
        buyer.hatChoice = LookOwner.NO_HAT;
        h.assertTrue("".equals(LookOwners.shown(null, buyer).hat()), "no hat, when chosen");
        buyer.hatChoice = "top_hat";
        h.assertTrue("beanie".equals(LookOwners.shown(null, buyer).hat()), "a chosen hat they no longer have falls back to the newest");
        buyer.owned.remove("beanie");
        buyer.hatChoice = "";
        h.assertTrue("halo".equals(LookOwners.shown(null, buyer).hat()), "a hat taken away is not worn");
        h.assertTrue(LookOwner.load(buyer.save()).save().equals(buyer.save()), "the record comes back whole from its tag");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_check_records_dressing_hats_and_a_lapse(GameTestHelper h) {
        ServerPlayer vip = player(h), buyer = player(h), plain = player(h);
        Map<UUID, Set<String>> nodes = new HashMap<>();
        nodes.put(vip.getUUID(), new HashSet<>(Set.of(TotemLooks.DRESS_PERMISSION)));
        nodes.put(buyer.getUUID(), new HashSet<>(Set.of(TotemHats.permission("halo"))));
        var realSource = TotemLooks.permissionSource;
        List<UUID> told = new java.util.ArrayList<>();
        java.util.function.BiConsumer<net.minecraft.server.MinecraftServer, UUID> listener = (srv, id) -> told.add(id);
        LookOwners.CHANGED.add(listener);
        try {
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            LookOwners all = LookOwners.get(h.getLevel().getServer());

            h.assertTrue(!all.check(plain) && all.record(plain.getUUID()) == null, "a player with nothing gets no record");
            h.assertTrue(all.check(vip) && all.record(vip.getUUID()).dressing && all.record(vip.getUUID()).owned.size() == TotemHats.IDS.size(), "a VIP's record has dressing and every hat");
            h.assertTrue(!all.check(vip), "nothing changed, nothing written");
            h.assertTrue(all.check(buyer) && List.of("halo").equals(all.record(buyer.getUUID()).owned) && !all.record(buyer.getUUID()).dressing, "a buyer's record has their hat only");
            long haloAt = all.record(buyer.getUUID()).bought.get("halo");
            nodes.get(buyer.getUUID()).add(TotemHats.permission("beanie"));
            h.assertTrue(all.check(buyer) && all.record(buyer.getUUID()).bought.get("halo") == haloAt, "a hat keeps the time it was first seen");
            h.assertTrue(all.record(buyer.getUUID()).bought.containsKey("beanie"), "a new hat is recorded as bought");

            // VIP lapses: the record says so, listeners hear it, and the look is hidden (not deleted)
            told.clear();
            nodes.get(vip.getUUID()).remove(TotemLooks.DRESS_PERMISSION);
            nodes.get(vip.getUUID()).add(TotemHats.permission("viking"));
            h.assertTrue(all.check(vip) && !all.record(vip.getUUID()).dressing && told.contains(vip.getUUID()), "a lapse is recorded and announced");
            TotemLook look = TotemLook.solid(vip.getUUID(), 1).withHat("top_hat");
            LookOwners.Shown s = LookOwners.shown(look, all.record(vip.getUUID()));
            h.assertTrue(s.body() == null && "viking".equals(s.hat()), "after the lapse only the bought hat shows: " + s);
            nodes.get(vip.getUUID()).add(TotemLooks.DRESS_PERMISSION);
            h.assertTrue(all.check(vip) && LookOwners.shown(look, all.record(vip.getUUID())).body() == look, "VIP back: the look shows again");

            // a newer record from another server replaces this one; an older one does not
            LookOwner theirs = all.record(vip.getUUID()).copy();
            theirs.hatChoice = "viking";
            theirs.updatedMs = all.record(vip.getUUID()).updatedMs + 10;
            all.arrived(h.getLevel().getServer(), vip.getUUID(), theirs.save(), 0);
            h.assertTrue("viking".equals(all.record(vip.getUUID()).hatChoice), "a newer record from the network is taken");
            LookOwner older = theirs.copy();
            older.hatChoice = "";
            older.updatedMs = 1;
            all.arrived(h.getLevel().getServer(), vip.getUUID(), older.save(), 5);
            h.assertTrue("viking".equals(all.record(vip.getUUID()).hatChoice) && all.record(vip.getUUID()).clearedBefore >= 5, "an older one is not, but its staff clear is");
        } finally {
            TotemLooks.permissionSource = realSource;
            LookOwners.CHANGED.remove(listener);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void saved_looks_and_a_staff_clear(GameTestHelper h) {
        ServerPlayer vip = player(h), buyer = player(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        Map<UUID, Set<String>> nodes = Map.of(vip.getUUID(), Set.of(TotemLooks.DRESS_PERMISSION), buyer.getUUID(), Set.of(TotemHats.permission("beanie")));
        var realSource = TotemLooks.permissionSource;
        ShatterTotemBlockEntity totem = totem(h, pos, vip);
        try {
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            var server = h.getLevel().getServer();
            LookOwners all = LookOwners.get(server);
            for (int i = 0; i < LookOwner.MAX_SAVED; i++) {
                h.assertTrue(all.saveLook(vip, -1, TotemLook.solid(vip.getUUID(), i)) == null, "saved look " + (i + 1));
            }
            h.assertTrue("saved_full".equals(all.saveLook(vip, -1, TotemLook.solid(vip.getUUID(), 9))), "five saved looks at most");
            h.assertTrue(all.saveLook(vip, 2, TotemLook.solid(vip.getUUID(), 0x2FA860)) == null && all.record(vip.getUUID()).saved.get(2).colours()[0] == 0x2FA860, "a saved look can be replaced");
            all.deleteLook(vip, 0);
            h.assertTrue(all.record(vip.getUUID()).saved.size() == LookOwner.MAX_SAVED - 1, "and deleted");
            h.assertTrue("not_yours".equals(all.saveLook(vip, -1, TotemLook.solid(buyer.getUUID(), 1))), "only your own looks are saved");
            h.assertTrue("no_dressing".equals(all.saveLook(buyer, -1, TotemLook.solid(buyer.getUUID(), 1))), "saving looks takes dressing");
            h.assertTrue("hat_not_owned".equals(all.chooseHat(buyer, "top_hat")) && all.chooseHat(buyer, "beanie") == null, "a buyer picks among their own hats");
            h.assertTrue("beanie".equals(all.record(buyer.getUUID()).hatChoice), "the pick is recorded");

            h.assertTrue(TotemLooks.apply(vip, totem, TotemLook.solid(vip.getUUID(), 0x7A4FD6)) == null && totem.getLook() != null, "dressed");
            TotemLook old = TotemLook.solid(vip.getUUID(), 1).madeAt(System.currentTimeMillis() - 60_000);
            h.assertTrue(!all.cleared(old), "nothing cleared yet");
            h.assertTrue(all.clearPlayer(server, vip.getUUID()) >= 0, "staff clear");
            h.assertTrue(totem.getLook() == null, "the loaded totem's look comes off at once");
            h.assertTrue(all.record(vip.getUUID()).saved.isEmpty(), "the saved looks go too");
            h.assertTrue(all.cleared(old), "a look made before the clear is taken off wherever it turns up");
            h.assertTrue(!all.cleared(TotemLook.solid(vip.getUUID(), 1).madeAt(System.currentTimeMillis() + 1000)), "a look made after it is not");
        } finally {
            TotemLooks.permissionSource = realSource;
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(null);
        }
        h.succeed();
    }

    private static void standAt(GameTestHelper h, ServerPlayer p, BlockPos pos, double dx) {
        BlockPos at = h.absolutePos(pos);
        p.setPos(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_block_state_follows_what_the_totem_shows(GameTestHelper h) {
        ServerPlayer vip = player(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        Map<UUID, Set<String>> nodes = new HashMap<>();
        nodes.put(vip.getUUID(), new HashSet<>(Set.of(TotemLooks.DRESS_PERMISSION, TotemHats.permission("beanie"))));
        var realSource = TotemLooks.permissionSource;
        ShatterTotemBlockEntity totem = totem(h, pos, vip);
        try {
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            LookOwners all = LookOwners.get(h.getLevel().getServer());
            all.check(vip);
            java.util.function.Supplier<Boolean> state = () -> h.getBlockState(pos).getValue(ShatterTotemBlock.LOOKS);
            h.assertTrue(!state.get(), "a plain totem has the full baked model");
            h.assertTrue(TotemLooks.apply(vip, totem, TotemLook.solid(vip.getUUID(), 0x7A4FD6).withHat("top_hat")) == null, "dressed");
            h.assertTrue(state.get() && totem.shownBody() != null && "top_hat".equals(totem.shownHat()), "a dressed totem has the bare model and shows its look");

            nodes.get(vip.getUUID()).remove(TotemLooks.DRESS_PERMISSION);
            all.check(vip);
            h.assertTrue(!state.get() && totem.shownBody() == null && "beanie".equals(totem.shownHat()), "VIP lapses: the totem turns plain at once and keeps the bought hat");
            h.assertTrue(totem.getLook() != null, "the look itself is kept");
            nodes.get(vip.getUUID()).add(TotemLooks.DRESS_PERMISSION);
            all.check(vip);
            h.assertTrue(state.get() && "top_hat".equals(totem.shownHat()), "VIP back: the look is back");

            totem.setLook(null);
            h.assertTrue(!state.get() && "beanie".equals(totem.shownHat()), "taken off: plain, with the bought hat");
            CompoundTag sent = totem.getUpdateTag().getCompound("Shown");
            h.assertTrue(!sent.contains("Body") && "beanie".equals(sent.getString("Hat")), "clients are told the plain body and the hat: " + sent);
            totem.setLook(TotemLook.solid(vip.getUUID(), 1));
            totem.setOwner(null);
            h.assertTrue(!state.get() && totem.shownBody() == null && totem.shownHat().isEmpty(), "unclaimed: plain, no hat");
        } finally {
            TotemLooks.permissionSource = realSource;
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(null);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_looks_tab_requests_are_checked(GameTestHelper h) {
        ServerPlayer vip = player(h), other = player(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos abs = h.absolutePos(pos);
        Map<UUID, Set<String>> nodes = Map.of(vip.getUUID(), Set.of(TotemLooks.DRESS_PERMISSION));
        var realSource = TotemLooks.permissionSource;
        ShatterTotemBlockEntity totem = totem(h, pos, vip);
        try {
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            LookActions.resetCooldowns();
            LookActions.gapMs = 0; // the short gap between requests is checked below on its own
            CompoundTag look = TotemLook.gradient(vip.getUUID(), TotemLook.Run.UP, 0xFFD23A, 0xB0203A).withHat("halo").save();

            standAt(h, vip, pos, 20);
            h.assertTrue("too_far".equals(LookActions.perform(vip, abs, LookActions.Action.PUT, 0, "", look)), "too far from the totem");
            standAt(h, vip, pos, 2);
            h.assertTrue("bad_look".equals(LookActions.perform(vip, abs, LookActions.Action.PUT, 0, "", new CompoundTag())), "an unreadable look is refused");
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.PUT, 0, "", look) == null && totem.getLook() != null, "put on");
            h.assertTrue("too_soon".equals(LookActions.perform(vip, abs, LookActions.Action.PUT, 0, "", look)), "a second change at once waits");
            h.assertTrue("too_soon".equals(LookActions.perform(vip, abs, LookActions.Action.SAVE, -1, "", look)), "saving waits too");
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.TAKE_OFF, 0, "", null) == null && totem.getLook() == null, "taking off does not wait");
            LookActions.resetCooldowns();
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.SAVE, -1, "", look) == null, "saved");
            LookActions.resetCooldowns();
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.PUT_SAVED, 0, "", null) == null && "halo".equals(totem.getLook().hat()), "a saved look put on");
            LookActions.resetCooldowns();
            h.assertTrue("no_saved".equals(LookActions.perform(vip, abs, LookActions.Action.PUT_SAVED, 4, "", null)), "an empty slot");

            standAt(h, other, pos, 2);
            h.assertTrue("not_owner".equals(LookActions.perform(other, abs, LookActions.Action.TAKE_OFF, 0, "", null)) && totem.getLook() != null, "only the owner takes a look off");
            h.assertTrue("not_owner".equals(LookActions.perform(other, abs, LookActions.Action.PUT, 0, "", TotemLook.solid(other.getUUID(), 1).save())), "or puts one on");
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.DELETE, 0, "", null) == null && LookOwners.get(h.getLevel().getServer()).record(vip.getUUID()).saved.isEmpty(), "a saved look dropped");
            h.assertTrue("hat_not_owned".equals(LookActions.perform(other, abs, LookActions.Action.CHOOSE_HAT, 0, "wizard", null)), "a hat pick needs the hat");
            LookActions.gapMs = 250;
            LookActions.resetCooldowns();
            h.assertTrue(LookActions.perform(vip, abs, LookActions.Action.TAKE_OFF, 0, "", null) == null
                    && "too_soon_short".equals(LookActions.perform(vip, abs, LookActions.Action.TAKE_OFF, 0, "", null)), "requests a quarter second apart at most");
        } finally {
            TotemLooks.permissionSource = realSource;
            LookActions.gapMs = 250;
            LookActions.resetCooldowns();
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(null);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_look_texture_covers_the_model_once(GameTestHelper h) {
        boolean[] used = new boolean[LookLayout.SIZE * LookLayout.SIZE];
        int[] count = {0};
        for (int k = 0; k < LookLayout.PARTS.length; k++) {
            LookLayout.Part p = LookLayout.PARTS[k];
            h.assertTrue(p.name().equals(TotemLook.PARTS[k]), "the parts are in the painting's order");
            int[] size = TotemLook.size(p.name());
            h.assertTrue(p.w() == size[0] && p.h() == size[1] && p.d() == size[2], p.name() + " has the painting's size");
        }
        LookLayout.forEachPixel((n, part, face, i, j) -> {
            int[] at = LookLayout.at(part, face);
            int x = at[0] + i, y = at[1] + j;
            h.assertTrue(x >= 0 && y >= 0 && x < LookLayout.SIZE && y < LookLayout.SIZE, part.name() + " " + face + " fits the texture");
            h.assertTrue(!used[y * LookLayout.SIZE + x], part.name() + " " + face + " overlaps another face at " + x + "," + y);
            used[y * LookLayout.SIZE + x] = true;
            h.assertTrue(n == count[0]++, "pixels are numbered in order");
        });
        h.assertTrue(count[0] == TotemLook.PAINT_PIXELS, "every painted pixel has a place: " + count[0]);

        float[] c = LookLayout.texelCentre(LookLayout.PARTS[1], LookLayout.Face.NORTH, 0, 0);
        h.assertTrue(c[0] == 11.5f && c[1] == 15.5f && c[2] == 5f, "the torso's front starts at its top right as seen from the front");
        float[] top = LookLayout.texelCentre(LookLayout.PARTS[0], LookLayout.Face.UP, 0, 7);
        h.assertTrue(top[1] > 24 && top[2] < 11.5f, "the head is bowed forward: the back of its top rises and moves toward the front: " + top[1] + "," + top[2]);

        UUID id = UUID.randomUUID();
        int[] solid = LookPixels.texture(TotemLook.solid(id, 0x7A4FD6));
        int filled = 0;
        for (int px : solid) {
            filled += (px >>> 24) == 0xFF ? 1 : 0;
        }
        h.assertTrue(filled == TotemLook.PAINT_PIXELS, "a look fills exactly the faces: " + filled);
        int[] headAt = LookLayout.at(LookLayout.PARTS[0], LookLayout.Face.NORTH);
        h.assertTrue((solid[(headAt[1] + 2) * 64 + headAt[0] + 1] & 0xFFFFFF) == 0x101418, "the creeper face is on the head's front");

        byte[] paint = paintAll(0);
        TotemLook.setPixel(paint, 0, 1);
        int[] painted = LookPixels.texture(TotemLook.painted(id, new int[]{0x112233, 0xFF7A1E}, paint));
        int[] first = LookLayout.at(LookLayout.PARTS[0], LookLayout.Face.EAST);
        h.assertTrue(painted[first[1] * 64 + first[0]] == 0xFFFF7A1E && painted[first[1] * 64 + first[0] + 1] == 0xFF112233, "painted pixels land where the layout puts them");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void every_hat_is_built_whole_and_sits_on_the_head(GameTestHelper h) {
        for (String id : TotemHats.IDS) {
            HatModels.Hat hat = HatModels.get(id);
            h.assertTrue(hat != null && hat.quads().size() >= 30, id + " has a real model: " + (hat == null ? 0 : hat.quads().size()) + " faces");
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxXZ = 0;
            for (HatModels.Quad q : hat.quads()) {
                h.assertTrue(hat.colours().containsKey(q.role()), id + " colours every part (" + q.role() + ")");
                for (int k = 0; k < 12; k++) {
                    h.assertTrue(Float.isFinite(q.v()[k]), id + " has no broken corner");
                }
                for (int k = 0; k < 4; k++) {
                    minY = Math.min(minY, q.v()[k * 3 + 1]);
                    maxY = Math.max(maxY, q.v()[k * 3 + 1]);
                    maxXZ = Math.max(maxXZ, Math.max(Math.abs(q.v()[k * 3]), Math.abs(q.v()[k * 3 + 2])));
                }
            }
            h.assertTrue(minY >= 2 && maxY <= 22 && maxXZ <= 9, id + " sits on the head, within its reach: y " + minY + " to " + maxY + ", out to " + maxXZ);
            h.assertTrue(maxY > 9, id + " stands above the top of the head");
        }
        h.assertTrue(HatModels.get("") == null && HatModels.get("no_such_hat") == null, "no hat, no model");

        UUID id = UUID.randomUUID();
        TotemLook look = TotemLook.gradient(id, TotemLook.Run.UP, 0x111111, 0x7A4FD6, 0x222222).withHat("beanie").withHatTint(true);
        h.assertTrue(look.hatTint() && look.equals(TotemLook.load(look.save())), "the hat colour switch is kept");
        h.assertTrue(look.mainColour() == 0x7A4FD6, "a gradient's middle colours the hat");
        byte[] paint = paintAll(1);
        TotemLook.setPixel(paint, 3, 0);
        h.assertTrue(TotemLook.painted(id, new int[]{0x123456, 0xABCDEF}, paint).mainColour() == 0xABCDEF, "a painting's most used colour colours the hat");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_paint_bench_tools_work_on_the_right_pixels(GameTestHelper h) {
        int mirrored = 0;
        for (int i = 0; i < TotemLook.PAINT_PIXELS; i++) {
            int m = PaintTools.mirror(i);
            h.assertTrue(PaintTools.mirror(m) == i, "mirroring twice comes back to pixel " + i);
            mirrored += m != i ? 1 : 0;
        }
        h.assertTrue(mirrored > TotemLook.PAINT_PIXELS / 2, "most pixels have a mirror twin: " + mirrored);
        int armL = PaintTools.index(4, LookLayout.Face.NORTH, 0, 0), armR = PaintTools.index(5, LookLayout.Face.NORTH, 2, 0);
        h.assertTrue(PaintTools.mirror(armL) == armR, "the left arm's front mirrors to the right arm's");
        int eye = PaintTools.index(0, LookLayout.Face.NORTH, 1, 3);
        h.assertTrue(PaintTools.mirror(eye) == PaintTools.index(0, LookLayout.Face.NORTH, 6, 3), "one eye mirrors to the other");
        PaintTools.Where w = PaintTools.where(PaintTools.index(1, LookLayout.Face.SOUTH, 3, 4));
        h.assertTrue(w.part() == 1 && w.face() == LookLayout.Face.SOUTH && w.i() == 3 && w.j() == 4, "a pixel knows where it is");

        // a fill stays on its face and in its colour
        byte[] paint = new byte[TotemLook.PAINT_BYTES];
        int front = PaintTools.index(1, LookLayout.Face.NORTH, 0, 0);
        TotemLook.setPixel(paint, PaintTools.index(1, LookLayout.Face.NORTH, 3, 0), 2); // a wall across the top row
        PaintTools.fill(paint, front, 5);
        int filled = 0;
        for (int i = 0; i < TotemLook.PAINT_PIXELS; i++) {
            if (PaintTools.pixel(paint, i) == 5) {
                filled++;
                h.assertTrue(PaintTools.where(i).part() == 1 && PaintTools.where(i).face() == LookLayout.Face.NORTH, "the fill stayed on the torso's front");
            }
        }
        h.assertTrue(filled == 8 * 10 - 1, "the fill reached the whole face round the one other pixel: " + filled);

        UUID id = UUID.randomUUID();
        PaintTools.Painting from = PaintTools.from(TotemLook.solid(id, 0x7A4FD6));
        h.assertTrue(from.palette().length <= 16 && TotemLook.painted(id, from.palette(), from.paint()).problem() == null, "a colour becomes a sound painting of at most 16 colours");
        int[] tex = LookPixels.texture(TotemLook.painted(id, from.palette(), from.paint()));
        int[] headAt = LookLayout.at(LookLayout.PARTS[0], LookLayout.Face.NORTH);
        h.assertTrue((tex[(headAt[1] + 2) * 64 + headAt[0] + 1] & 0xFFFFFF) == 0x101418, "the painting starts with the creeper face");
        PaintTools.Painting grad = PaintTools.from(TotemLook.gradient(id, TotemLook.Run.UP, 0xFFD23A, 0xFF7A3C, 0xB0203A));
        h.assertTrue(grad.palette().length == 16, "a gradient has more than 16 shades and is cut to 16");
        h.assertTrue(PaintTools.blank(0x123456).palette().length == 1, "a blank painting is one colour");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void only_a_clear_leader_glows(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        ScoreService.Entry ann = new ScoreService.Entry(a, "Ann", 3, 14, 4), bob = new ScoreService.Entry(b, "Bob", 5, 14, 5), cid = new ScoreService.Entry(c, "Cid", 1, 7, 3);
        h.assertTrue(ScoreLeader.leader(List.of()) == null, "an empty board has no leader");
        h.assertTrue(ScoreLeader.leader(List.of(new ScoreService.Entry(a, "Ann", 0, 0, 0))) == null, "no points, no leader");
        h.assertTrue(a.equals(ScoreLeader.leader(List.of(ann, cid))), "the most points leads");
        h.assertTrue(a.equals(ScoreLeader.leader(List.of(cid, ann))), "wherever it stands in the list");
        h.assertTrue(ScoreLeader.leader(List.of(bob, ann, cid)) == null, "two on the top points: nobody glows, more totems or not");
        h.assertTrue(a.equals(ScoreLeader.leader(List.of(ann, cid, new ScoreService.Entry(UUID.randomUUID(), "Dot", 2, 7, 3)))), "a tie lower down does not matter");
        h.assertTrue(c.equals(ScoreLeader.leader(List.of(new ScoreService.Entry(c, "Cid", 1, 1, 1)))), "one point is enough");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void audit_a_clear_reaches_loaded_totems_and_saved_looks(GameTestHelper h) {
        ServerPlayer vip = player(h);
        BlockPos pos = new BlockPos(1, 2, 1);
        Map<UUID, Set<String>> nodes = Map.of(vip.getUUID(), Set.of(TotemLooks.DRESS_PERMISSION));
        var realSource = TotemLooks.permissionSource;
        ShatterTotemBlockEntity totem = totem(h, pos, vip);
        try {
            TotemLooks.permissionSource = (id, node) -> nodes.getOrDefault(id, Set.of()).contains(node);
            var server = h.getLevel().getServer();
            LookOwners all = LookOwners.get(server);
            all.check(vip);
            h.assertTrue(TotemLooks.apply(vip, totem, TotemLook.solid(vip.getUUID(), 0x7A4FD6)) == null, "dressed");
            h.assertTrue(all.saveLook(vip, -1, TotemLook.solid(vip.getUUID(), 1)) == null, "a look saved");
            long clear = System.currentTimeMillis() + 5_000; // a clear made on another server, after both
            LookOwner theirs = all.record(vip.getUUID()).copy();
            theirs.updatedMs = 1; // an older record: only its clear is news
            all.arrived(server, vip.getUUID(), theirs.save(), clear);
            h.assertTrue(totem.getLook() == null && totem.shownBody() == null, "a clear from another server takes the look off a loaded totem here");
            h.assertTrue(all.record(vip.getUUID()).saved.isEmpty(), "and the saved looks made before it");
            LookOwner stale = theirs.copy();
            stale.clearedBefore = clear;
            stale.saved.add(TotemLook.solid(vip.getUUID(), 2).madeAt(clear - 1));
            h.assertTrue(LookOwner.load(stale.save()).saved.isEmpty(), "a record that still holds a saved look older than its clear drops it when read");

            // a look's arrays must fit its mode
            CompoundTag bloated = TotemLook.solid(vip.getUUID(), 1).save();
            bloated.putIntArray("Palette", new int[2000]);
            bloated.putByteArray("Paint", new byte[10]);
            h.assertTrue(TotemLook.load(bloated) == null, "a colour look carrying a palette is refused");

            // a permission this server cannot read is not taken as a no
            TotemLooks.permissionSource = (id, node) -> null;
            h.assertTrue(TotemLooks.dressingKnown(vip) == null && !all.check(vip) && all.record(vip.getUUID()).dressing, "an unknown answer changes nothing");
        } finally {
            TotemLooks.permissionSource = realSource;
            ((ShatterTotemBlockEntity) h.getBlockEntity(pos)).setOwner(null);
        }
        h.succeed();
    }
}
