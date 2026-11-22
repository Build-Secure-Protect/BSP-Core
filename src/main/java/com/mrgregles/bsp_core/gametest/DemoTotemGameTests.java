package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.demo.DemoTotemBlock;
import com.mrgregles.bsp_core.demo.DemoTotemBlockEntity;
import com.mrgregles.bsp_core.event.TotemLogoutHandler;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Demo Totems: an ordinary block for admins, outside every totem rule, that keeps its look when moved. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class DemoTotemGameTests {
    private static final String EMPTY = "empty", BATCH = "tripwire"; // an existing batch: a new batch name reorders every batch

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        return MockPlayers.serverPlayer(h, mode);
    }

    /** Puts a demo at {@code pos} (relative) the way a block is set, with no placer. */
    private static DemoTotemBlockEntity demo(GameTestHelper h, BlockPos pos) {
        h.getLevel().setBlockAndUpdate(h.absolutePos(pos), ModBlocks.DEMO_TOTEM.get().defaultBlockState());
        return (DemoTotemBlockEntity) h.getBlockEntity(pos);
    }

    private static List<ItemEntity> dropsAt(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pos)).inflate(1.5), e -> e.getItem().is(ModItems.DEMO_TOTEM.get()));
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_demo_totem_is_no_totem_to_the_rules(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL);
        var realAdmins = Admins.adminSource;
        try {
            Admins.adminSource = p -> p == admin;
            ItemStack stack = new ItemStack(ModItems.DEMO_TOTEM.get(), 3);
            h.assertFalse(TotemInventories.isTotem(stack), "the carrying rules do not take a demo for a totem");
            admin.getInventory().add(stack);
            h.assertFalse(TotemLogoutHandler.travelsWithAdmin(admin), "an admin carrying only demos is not on a server transfer");
            TotemLogoutHandler.placeOnLogout(admin);
            h.assertTrue(admin.getInventory().countItem(ModItems.DEMO_TOTEM.get()) == 3, "the demos stay in the admin's inventory through a logout");
        } finally {
            Admins.adminSource = realAdmins;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void only_admins_place_a_demo_totem(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 2, 1), abs = h.absolutePos(pos), ground = abs.below();
        h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
        var realAdmins = Admins.adminSource;
        try {
            Admins.adminSource = p -> p == admin;
            ItemStack theirs = new ItemStack(ModItems.DEMO_TOTEM.get(), 2);
            other.setItemInHand(InteractionHand.MAIN_HAND, theirs);
            theirs.useOn(new UseOnContext(other, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "a player who is not an admin cannot place a demo");
            h.assertTrue(theirs.getCount() == 2, "and keeps the item");

            ItemStack mine = new ItemStack(ModItems.DEMO_TOTEM.get(), 2);
            DemoTotemBlockEntity.putLook(mine, TotemLook.solid(admin.getUUID(), 0x7A4FD6).withHat("top_hat"));
            admin.setItemInHand(InteractionHand.MAIN_HAND, mine);
            mine.useOn(new UseOnContext(admin, InteractionHand.MAIN_HAND, hit));
            BlockState placed = h.getLevel().getBlockState(abs);
            h.assertTrue(placed.is(ModBlocks.DEMO_TOTEM.get()), "an admin places a demo");
            h.assertTrue(mine.getCount() == 1, "one demo is used from the stack");
            h.assertTrue(placed.getValue(DemoTotemBlock.LOOKS), "placed with the item's look, the bare model is baked");
            h.assertTrue(h.getBlockEntity(pos) instanceof DemoTotemBlockEntity d && d.getLook() != null && "top_hat".equals(d.getLook().hat()),
                    "the item's look and hat are on the placed demo");
        } finally {
            Admins.adminSource = realAdmins;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void only_admins_break_a_demo_totem(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL), creative = player(h, GameType.CREATIVE);
        BlockPos pos = new BlockPos(1, 2, 1), abs = h.absolutePos(pos);
        demo(h, pos);
        BlockState state = h.getLevel().getBlockState(abs);
        var realAdmins = Admins.adminSource;
        try {
            Admins.adminSource = p -> p == admin;
            h.assertTrue(state.getDestroyProgress(other, h.getLevel(), abs) == 0f, "someone else makes no progress mining it");
            h.assertTrue(state.getDestroyProgress(admin, h.getLevel(), abs) > 0f, "an admin mines it");
            for (ServerPlayer p : List.of(other, creative)) {
                BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(h.getLevel(), abs, state, p);
                MinecraftForge.EVENT_BUS.post(event);
                h.assertTrue(event.isCanceled(), "a break by someone who is not an admin is refused, in creative too");
            }
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(h.getLevel(), abs, state, admin);
            MinecraftForge.EVENT_BUS.post(event);
            h.assertFalse(event.isCanceled(), "an admin's break goes ahead");
            h.assertFalse(state.canEntityDestroy(h.getLevel(), abs, other), "no mob can break it");
        } finally {
            Admins.adminSource = realAdmins;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_demo_totem_keeps_its_look_when_mined(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), builder = player(h, GameType.CREATIVE);
        BlockPos pos = new BlockPos(1, 2, 1), again = new BlockPos(3, 2, 1), plain = new BlockPos(1, 2, 3), abs = h.absolutePos(pos);
        TotemLook look = TotemLook.gradient(admin.getUUID(), TotemLook.Run.UP, 0xFFD23A, 0xB0203A).withHat("halo").madeAt(1234L);

        DemoTotemBlockEntity dressed = demo(h, pos);
        dressed.setLook(look);
        h.assertTrue(h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS), "a dressed demo bakes the bare model");
        h.assertTrue(look.equals(DemoTotemBlockEntity.lookOn(DemoTotemBlock.toItemStack(h.getLevel(), abs))), "middle-click copies the look");

        BlockState state = h.getLevel().getBlockState(abs);
        dropsAt(h, pos).forEach(ItemEntity::discard);
        state.onDestroyedByPlayer(h.getLevel(), abs, admin, true, h.getLevel().getFluidState(abs));
        h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "the admin mined it");
        List<ItemEntity> drops = dropsAt(h, pos);
        h.assertTrue(drops.size() == 1, "it drops one demo item, got " + drops.size());
        ItemStack item = drops.get(0).getItem().copy();
        drops.forEach(ItemEntity::discard);
        h.assertTrue(look.equals(DemoTotemBlockEntity.lookOn(item)), "the item carries the look");

        BlockPos absAgain = h.absolutePos(again);
        h.getLevel().setBlockAndUpdate(absAgain, ModBlocks.DEMO_TOTEM.get().defaultBlockState());
        ModBlocks.DEMO_TOTEM.get().setPlacedBy(h.getLevel(), absAgain, h.getLevel().getBlockState(absAgain), admin, item);
        h.assertTrue(h.getBlockEntity(again) instanceof DemoTotemBlockEntity d && look.equals(d.getLook()), "placing it again puts the look back");

        // a plain demo in creative drops nothing (the creative tab has it), and plain items carry no tag, so they stack together
        demo(h, plain);
        BlockPos absPlain = h.absolutePos(plain);
        h.assertTrue(DemoTotemBlock.toItemStack(h.getLevel(), absPlain).getTag() == null, "a plain demo's item has no tag");
        dropsAt(h, plain).forEach(ItemEntity::discard);
        h.getLevel().getBlockState(absPlain).onDestroyedByPlayer(h.getLevel(), absPlain, builder, true, h.getLevel().getFluidState(absPlain));
        h.assertTrue(dropsAt(h, plain).isEmpty(), "a plain demo mined in creative drops nothing");

        // a look that is not sound (an unknown hat) is not put on
        ItemStack broken = new ItemStack(ModItems.DEMO_TOTEM.get());
        CompoundTag bad = TotemLook.solid(admin.getUUID(), 1).save();
        bad.putString("Hat", "crown_of_nowhere");
        broken.getOrCreateTag().put(TotemLook.TAG, bad);
        h.assertTrue(DemoTotemBlockEntity.lookOn(broken) == null, "a look with an unknown hat is dropped");
        h.assertTrue(Set.of(DemoTotemBlock.LOOKS, DemoTotemBlock.FACING).equals(Set.copyOf(state.getProperties())), "only facing and looks in the state");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_demo_command_gives_only_to_admins(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL);
        var realAdmins = Admins.adminSource;
        try {
            Admins.adminSource = p -> p == admin;
            h.assertTrue("command.bsp_core.demo.not_admin".equals(com.mrgregles.bsp_core.demo.DemoTotems.give(other, 5)), "a player who is not an admin is refused");
            h.assertTrue(other.getInventory().countItem(ModItems.DEMO_TOTEM.get()) == 0, "and gets nothing");
            h.assertTrue(com.mrgregles.bsp_core.demo.DemoTotems.give(admin, 5) == null, "an admin is given demos");
            h.assertTrue(admin.getInventory().countItem(ModItems.DEMO_TOTEM.get()) == 5, "five of them");
            com.mrgregles.bsp_core.demo.DemoTotems.give(admin, 1000);
            h.assertTrue(admin.getInventory().countItem(ModItems.DEMO_TOTEM.get()) == 5 + com.mrgregles.bsp_core.demo.DemoTotems.MAX_COUNT, "at most a stack at a time");
            for (ItemStack s : admin.getInventory().items) {
                h.assertTrue(!s.is(ModItems.DEMO_TOTEM.get()) || s.getTag() == null, "given demos are plain");
            }
        } finally {
            Admins.adminSource = realAdmins;
        }
        h.succeed();
    }

    private static void standAt(GameTestHelper h, ServerPlayer p, BlockPos pos, double dx) {
        BlockPos at = h.absolutePos(pos);
        p.setPos(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void admins_dress_a_demo_totem(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 2, 1), abs = h.absolutePos(pos);
        DemoTotemBlockEntity demo = demo(h, pos);
        var realAdmins = Admins.adminSource;
        var realPermissions = com.mrgregles.bsp_core.looks.TotemLooks.permissionSource;
        long realGap = com.mrgregles.bsp_core.looks.LookActions.gapMs;
        try {
            Admins.adminSource = p -> p == admin;
            com.mrgregles.bsp_core.looks.TotemLooks.permissionSource = (id, node) -> false; // nobody is a VIP or has a hat
            com.mrgregles.bsp_core.looks.LookActions.resetCooldowns();
            com.mrgregles.bsp_core.looks.LookActions.gapMs = 0;
            var PUT = com.mrgregles.bsp_core.looks.LookActions.Action.PUT;
            var TAKE_OFF = com.mrgregles.bsp_core.looks.LookActions.Action.TAKE_OFF;
            CompoundTag wizard = TotemLook.solid(admin.getUUID(), 0x2FA860).withHat("wizard").save();
            standAt(h, admin, pos, 1.5);
            standAt(h, other, pos, 1.5);

            h.assertTrue("demo_admins".equals(com.mrgregles.bsp_core.looks.LookActions.perform(other, abs, PUT, 0, "",
                    TotemLook.solid(other.getUUID(), 1).save())), "a player who is not an admin cannot dress a demo");
            h.assertTrue(demo.getLook() == null, "and the demo stays plain");

            standAt(h, admin, pos, 20);
            h.assertTrue("too_far".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, PUT, 0, "", wizard)), "too far from the demo");
            standAt(h, admin, pos, 1.5);
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, PUT, 0, "", wizard) == null,
                    "an admin with no VIP rank and no hat permission dresses a demo, hat and all");
            h.assertTrue(demo.getLook() != null && "wizard".equals(demo.getLook().hat()) && h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS),
                    "the demo wears the look");
            h.assertTrue(admin.getGameProfile().getName().equals(demo.getDresser())
                    && admin.getGameProfile().getName().equals(DemoTotemBlockEntity.dresserOn(DemoTotemBlock.toItemStack(h.getLevel(), abs))),
                    "the dresser's name is kept (MY HEAD looks the skin up by it), on the item too");
            h.assertTrue("not_yours".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, PUT, 0, "",
                    TotemLook.solid(other.getUUID(), 1).save())), "a look made for someone else is refused");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, PUT, 0, "",
                    TotemLook.gradient(admin.getUUID(), TotemLook.Run.SIDE, 0xFFD23A, 0x19D3B0).save()) == null, "no 10 s wait between looks on a demo");

            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, com.mrgregles.bsp_core.looks.LookActions.Action.SAVE, -1, "", wizard) == null,
                    "an admin saves a look from a demo without the VIP rank");
            var owners = com.mrgregles.bsp_core.looks.LookOwners.get(h.getLevel().getServer());
            h.assertTrue(owners.record(admin.getUUID()) != null && owners.record(admin.getUUID()).saved.size() == 1, "the look is in the admin's saved looks");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, com.mrgregles.bsp_core.looks.LookActions.Action.PUT_SAVED, 0, "", null) == null
                    && "wizard".equals(demo.getLook().hat()), "a saved look goes on a demo");

            var tabRecord = com.mrgregles.bsp_core.looks.TotemLooks.demoRecord(owners.record(admin.getUUID()));
            h.assertTrue(tabRecord.dressing && tabRecord.owned.equals(com.mrgregles.bsp_core.looks.TotemHats.IDS) && tabRecord.saved.size() == 1,
                    "at a demo the tab is told the admin dresses, with every hat and their saved looks");
            h.assertFalse(owners.record(admin.getUUID()).dressing, "and the stored record is not changed");

            owners.clearPlayer(h.getLevel().getServer(), admin.getUUID());
            h.assertTrue(demo.getLook() != null, "a staff clear of the admin's looks leaves demos dressed");

            h.assertTrue("demo_admins".equals(com.mrgregles.bsp_core.looks.LookActions.perform(other, abs, TAKE_OFF, 0, "", null)), "only admins take a demo's look off");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, TAKE_OFF, 0, "", null) == null && demo.getLook() == null
                    && !h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS), "an admin takes it off: a plain gold demo again");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(other, abs, com.mrgregles.bsp_core.looks.LookActions.Action.REQUEST, 0, "", null) == null,
                    "anyone may ask for a demo's data");
        } finally {
            Admins.adminSource = realAdmins;
            com.mrgregles.bsp_core.looks.TotemLooks.permissionSource = realPermissions;
            com.mrgregles.bsp_core.looks.LookActions.gapMs = realGap;
            com.mrgregles.bsp_core.looks.LookActions.resetCooldowns();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void audit_demo_totem_checks(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 2, 1), abs = h.absolutePos(pos);
        var realAdmins = Admins.adminSource;
        try {
            Admins.adminSource = p -> true; // everyone is an admin, a machine's fake player included
            var fake = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
            h.assertFalse(com.mrgregles.bsp_core.demo.DemoTotems.isAdmin(fake), "a fake player (a deployer) is never an admin for demos");
            h.assertTrue(com.mrgregles.bsp_core.demo.DemoTotems.isAdmin(admin), "a real admin is");

            // a place that skips the item's check (a Construction Wand) still fires the place event
            Admins.adminSource = p -> p == admin;
            demo(h, pos);
            var snapshot = net.minecraftforge.common.util.BlockSnapshot.create(h.getLevel().dimension(), h.getLevel(), abs);
            BlockEvent.EntityPlaceEvent byOther = new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), other);
            MinecraftForge.EVENT_BUS.post(byOther);
            h.assertTrue(byOther.isCanceled(), "a demo put down by someone who is not an admin is refused");
            BlockEvent.EntityPlaceEvent byAdmin = new BlockEvent.EntityPlaceEvent(snapshot, Blocks.STONE.defaultBlockState(), admin);
            MinecraftForge.EVENT_BUS.post(byAdmin);
            h.assertFalse(byAdmin.isCanceled(), "an admin's goes ahead");

            // a demo loaded with a look but a plain state (a /setblock with NBT, a paste) gets the state that matches
            DemoTotemBlockEntity demo = (DemoTotemBlockEntity) h.getBlockEntity(pos);
            CompoundTag tag = demo.saveWithoutMetadata();
            tag.put(TotemLook.TAG, TotemLook.solid(admin.getUUID(), 0x7A4FD6).save());
            tag.putString("LookBy", "x".repeat(40));
            demo.load(tag);
            h.assertFalse(h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS), "loaded with a look, the state is still plain");
            h.assertTrue(demo.getDresser().isEmpty() && demo.skinOwner() == null, "a dresser name longer than a player name is dropped, and no skin is looked up");
            demo.serverTick();
            h.assertTrue(h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS), "the first tick sets the state to match the look");

            // taking the look off reaches clients: a plain demo's update is not empty
            ((DemoTotemBlockEntity) h.getBlockEntity(pos)).setLook(null);
            h.assertFalse(((DemoTotemBlockEntity) h.getBlockEntity(pos)).getUpdateTag().isEmpty(), "a plain demo's update tag is not empty");
        } finally {
            Admins.adminSource = realAdmins;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_demo_wears_a_hat_on_its_plain_body(GameTestHelper h) {
        ServerPlayer admin = player(h, GameType.SURVIVAL), other = player(h, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 2, 1), again = new BlockPos(3, 2, 1), abs = h.absolutePos(pos);
        DemoTotemBlockEntity demo = demo(h, pos);
        var realAdmins = Admins.adminSource;
        long realGap = com.mrgregles.bsp_core.looks.LookActions.gapMs;
        try {
            Admins.adminSource = p -> p == admin;
            com.mrgregles.bsp_core.looks.LookActions.resetCooldowns();
            com.mrgregles.bsp_core.looks.LookActions.gapMs = 0;
            var HAT = com.mrgregles.bsp_core.looks.LookActions.Action.CHOOSE_HAT;
            standAt(h, admin, pos, 1.5);
            standAt(h, other, pos, 1.5);

            h.assertTrue("demo_admins".equals(com.mrgregles.bsp_core.looks.LookActions.perform(other, abs, HAT, 0, "top_hat", null)), "only admins choose a demo's hat");
            h.assertTrue("unknown_hat".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, 0, "crown_of_nowhere", null)), "an unknown hat is refused");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, 0, "top_hat", null) == null, "an admin puts a hat on the plain body");
            h.assertTrue(demo.getLook() == null && "top_hat".equals(demo.getPlainHat()) && "top_hat".equals(demo.shownHat())
                    && !h.getLevel().getBlockState(abs).getValue(DemoTotemBlock.LOOKS), "no look, the baked gold body, the hat on it");
            var owners = com.mrgregles.bsp_core.looks.LookOwners.get(h.getLevel().getServer());
            h.assertTrue(owners.record(admin.getUUID()) == null || owners.record(admin.getUUID()).hatChoice.isEmpty(), "the admin's own hat choice is untouched");
            h.assertTrue("top_hat".equals(com.mrgregles.bsp_core.looks.TotemLooks.demoRecord(null, demo.getPlainHat()).hatChoice), "the tab is told the plain hat");

            ItemStack item = DemoTotemBlock.toItemStack(h.getLevel(), abs);
            h.assertTrue("top_hat".equals(DemoTotemBlockEntity.plainHatOn(item)) && DemoTotemBlockEntity.lookOn(item) == null, "the item carries the hat, no look");
            BlockPos absAgain = h.absolutePos(again);
            h.getLevel().setBlockAndUpdate(absAgain, ModBlocks.DEMO_TOTEM.get().defaultBlockState());
            ModBlocks.DEMO_TOTEM.get().setPlacedBy(h.getLevel(), absAgain, h.getLevel().getBlockState(absAgain), admin, item);
            h.assertTrue(h.getBlockEntity(again) instanceof DemoTotemBlockEntity d && "top_hat".equals(d.getPlainHat()), "placing it again puts the hat back");

            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, com.mrgregles.bsp_core.looks.LookActions.Action.PUT, 0, "",
                    TotemLook.solid(admin.getUUID(), 0x2FA860).withHat("halo").save()) == null && demo.getPlainHat().isEmpty() && "halo".equals(demo.shownHat()),
                    "a look brings its own hat");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, 0, com.mrgregles.bsp_core.looks.LookOwner.NO_HAT, null) == null
                    && demo.getLook() == null && demo.shownHat().isEmpty(), "NO HAT on the plain body: plain gold");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, com.mrgregles.bsp_core.looks.LookActions.DEMO_SLOT, "beanie", null) == null
                    && "beanie".equals(demo.getPlainHat()), "the beanie on the plain body");
            CompoundTag saved = demo.saveWithoutMetadata();
            h.assertTrue("beanie".equals(saved.getString("PlainHat")) && "beanie".equals(demo.getUpdateTag().getString("PlainHat")),
                    "the plain hat is saved and sent to clients");

            // out of reach, or gone: a demo's hat is never taken for the admin's own hat choice
            standAt(h, admin, pos, 20);
            h.assertTrue("too_far".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, com.mrgregles.bsp_core.looks.LookActions.DEMO_SLOT, "wizard", null)),
                    "the demo tab's hat from too far is refused");
            h.assertTrue("too_far".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, HAT, 0, "wizard", null)),
                    "any hat aimed at a demo from too far is refused");
            standAt(h, admin, pos, 1.5);
            BlockPos gone = h.absolutePos(new BlockPos(1, 2, 3));
            h.assertTrue("too_far".equals(com.mrgregles.bsp_core.looks.LookActions.perform(admin, gone, HAT, com.mrgregles.bsp_core.looks.LookActions.DEMO_SLOT, "wizard", null)),
                    "the demo tab's hat where no demo stands (mined meanwhile) is refused");
            h.assertTrue(owners.record(admin.getUUID()) == null || owners.record(admin.getUUID()).hatChoice.isEmpty(), "and the admin's own hat choice is still untouched");
            h.assertTrue("beanie".equals(demo.getPlainHat()), "the demo still wears the beanie");
            h.assertTrue(com.mrgregles.bsp_core.looks.LookActions.perform(admin, abs, com.mrgregles.bsp_core.looks.LookActions.Action.TAKE_OFF, 0, "", null) == null
                    && demo.shownHat().isEmpty(), "RESET takes the plain body's hat off too");
        } finally {
            Admins.adminSource = realAdmins;
            com.mrgregles.bsp_core.looks.LookActions.gapMs = realGap;
            com.mrgregles.bsp_core.looks.LookActions.resetCooldowns();
        }
        h.succeed();
    }
}
