package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.AdminService;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import com.mrgregles.bsp_core.totem.TotemDimensions;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests for the fixed rule that a Shatter Totem is never in a Compact Machines room ({@code compactmachines:compact_world}),
 * whatever {@code restrictions.allowedDimensions} says ({@link TotemDimensions}).
 *
 * <p>Compact Machines is not loaded in ordinary test runs, so the in-world rules are checked in the test level's own dimension
 * standing in for a room ({@link TotemDimensions#standInForTests}), set and cleared within one synchronous test call so no other
 * test ever sees it. {@code ./gradlew runGameTestServer -PwithCm=<the pack's mods folder>} loads the real mod, and
 * {@link #in_the_real_compact_machines_dimension_a_totem_never_stands_or_lies} then runs the same rules in the real dimension (it
 * passes at once, saying so, when the dimension is missing).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CompactRoomGameTests {
    private static final String EMPTY = "empty", BATCH = "compact_rooms";
    private static final ResourceLocation ROOMS = ResourceLocation.tryParse("compactmachines:compact_world");

    private static ItemStack totem(UUID owner, String name) {
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        new TotemOwner(owner, name).applyTo(totem);
        TotemIdentity.ensure(totem);
        return totem;
    }

    /** Runs {@code body} with the test level's dimension standing in for a Compact Machines room. */
    private static void asRoom(GameTestHelper h, Runnable body) {
        try {
            TotemDimensions.standInForTests(h.getLevel().dimension().location());
            body.run();
        } finally {
            TotemDimensions.clearStandIns();
        }
    }

    /** Takes away whatever a test left standing or lying, whether or not it got that far: blocks (owner first, so nothing is reissued) and ground totems. */
    private static void clearWorld(ServerLevel level, List<BlockPos> blocks, List<ShatterTotemItemEntity> ground) {
        for (BlockPos pos : blocks) {
            if (level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity be) {
                be.setOwner(null);
            }
            if (level.getBlockState(pos).is(ModBlocks.SHATTER_TOTEM.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        for (ShatterTotemItemEntity e : ground) {
            if (!e.isRemoved()) {
                e.destroy();
            }
        }
    }

    /** The totems with this id waiting for the player (given at their next login). */
    private static int waiting(SeasonData season, UUID player, UUID id) {
        int n = 0;
        for (ItemStack s : season.pendingItems.getOrDefault(player, List.of())) {
            if (id.equals(TotemIdentity.id(s)) && !TotemIdentity.isSpent(s)) {
                n++;
            }
        }
        return n;
    }

    private static int held(ServerPlayer p, UUID id) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.SHATTER_TOTEM.get()) && !TotemIdentity.isSpent(s) && id.equals(TotemIdentity.id(s))) {
                n++;
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_compact_machines_room_is_never_allowed_whatever_the_config_says(GameTestHelper h) {
        List<String> before = new ArrayList<>(BSPConfig.ALLOWED_DIMENSIONS.get());
        try {
            List<String> listed = new ArrayList<>(before);
            listed.add(" compactmachines:compact_world ");
            listed.add(ROOMS.toString());
            BSPConfig.ALLOWED_DIMENSIONS.set(listed);
            h.assertFalse(BSPConfig.isDimensionAllowed(ROOMS), "rooms are refused although the config lists them");
            h.assertFalse(com.mrgregles.bsp_core.api.BspPlayers.isDimensionAllowed(ROOMS), "and through the api");
            h.assertTrue(BSPConfig.isDimensionAllowed(Level.OVERWORLD.location()), "the Overworld is still allowed");
            h.assertTrue(TotemDimensions.ignored(listed).equals(List.of("compactmachines:compact_world", "compactmachines:compact_world")),
                    "both spellings are found to warn about: " + TotemDimensions.ignored(listed));
            h.assertTrue(TotemDimensions.ignored(List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")).isEmpty(),
                    "the default list has nothing to warn about");
            h.assertTrue(TotemDimensions.ignoredWarning("compactmachines:compact_world").contains("cannot be switched off"), "the warning says why");
            h.assertTrue(TotemDimensions.refusalKey(ROOMS).equals("message.bsp_core.shatter_totem.room_blocked"), "a room has its own line");
            h.assertTrue(TotemDimensions.refusalKey(ResourceLocation.tryParse("twilightforest:twilight_forest")).equals("message.bsp_core.shatter_totem.dimension_blocked"),
                    "another unlisted world keeps the plain line");
            h.assertFalse(TotemDimensions.never(ResourceLocation.tryParse("compactmachines:other")), "matched by the exact id");
            // the start sweep waits for a network server's database, but not for ever
            h.assertTrue(TotemDimensions.readyToSweep(false, 20) && !TotemDimensions.readyToSweep(false, 19), "a second after the start");
            h.assertFalse(TotemDimensions.readyToSweep(true, 20 * 60), "a network server waits for its database");
            h.assertTrue(TotemDimensions.readyToSweep(true, 20 * 120), "two minutes at most");
        } finally {
            BSPConfig.ALLOWED_DIMENSIONS.set(before);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void in_a_room_a_totem_is_never_placed_dropped_or_left_lying(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        var ledger = TotemLedger.get(server);
        var season = SeasonData.get(server);
        BlockPos floor = new BlockPos(2, 1, 2);
        h.setBlock(floor, Blocks.STONE);
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID who = player.getUUID();
        BlockPos stand = h.absolutePos(floor.above());
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        ItemStack totem = totem(who, "Rowan");
        UUID id = TotemIdentity.id(totem);
        List<ShatterTotemItemEntity> left = new ArrayList<>();
        try {
            asRoom(h, () -> {
                h.assertFalse(BSPConfig.isDimensionAllowed(level.dimension().location()), "the room is not allowed");
                // placing: the helper every BSP-Core path uses refuses
                h.assertFalse(TotemPlacer.place(level, stand, totem.copy()), "nothing places a totem in a room");
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), floor.above());
                // dropping: refused, and the totem stays in hand
                player.getInventory().setItem(0, totem.copy());
                var tossed = new net.minecraft.world.entity.item.ItemEntity(level, stand.getX(), stand.getY(), stand.getZ(), player.getInventory().removeItem(0, 1));
                var toss = new net.minecraftforge.event.entity.item.ItemTossEvent(tossed, player);
                com.mrgregles.bsp_core.event.TotemRestrictionHandler.onToss(toss);
                h.assertTrue(toss.isCanceled() && held(player, id) == 1, "a totem cannot be dropped in a room, and holding it is fine");
                // a spot remembered in a room (an older config) is never where death or logout sends a totem
                PlayerPersistent.setLastMainPosition(player, level.dimension(), stand);
                var target = TotemPlacer.safeTargetFor(player);
                h.assertFalse(target.level() == level && target.pos().equals(stand), "the remembered spot in a room is skipped: " + target.pos());
                player.getInventory().clearContent();
                // a ground totem lying in a room (a Totem Forge broken while it holds a set totem, a drop by another mod) goes to its owner at its first tick
                var ground = new ShatterTotemItemEntity(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, totem.copy());
                left.add(ground);
                level.addFreshEntity(ground);
                // just after a start it waits (a network server's database may not be connected yet)
                TotemDimensions.justStartedForTests(true);
                try {
                    ground.tick();
                } finally {
                    TotemDimensions.justStartedForTests(false);
                }
                h.assertFalse(ground.isRemoved(), "just after a start the ground totem waits");
                ground.tick();
                h.assertTrue(ground.isRemoved(), "the ground totem is gone from the room");
                h.assertTrue(waiting(season, who, id) == 1, "it waits for its owner, who is away");
                var s = ledger.sighting(id);
                h.assertTrue(s != null && s.where() == TotemLedger.IN_TRANSIT && who.equals(s.holder()), "counted in transit, never lost: " + s);
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), floor.above());
            });
            AdminService.handOverOwed(player);
            h.assertTrue(held(player, id) == 1 && waiting(season, who, id) == 0, "given back at the next login");
            var s = ledger.sighting(id);
            h.assertTrue(s != null && s.where() == TotemLedger.HELD && who.equals(s.holder()), "held again: " + s);
        } finally {
            clearWorld(level, List.of(stand), left);
            player.getInventory().clearContent();
            season.pendingItems.remove(who);
            ledger.forget(id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_standing_in_a_room_goes_back_to_its_owner_and_a_stale_copy_is_just_removed(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        var ledger = TotemLedger.get(server);
        var season = SeasonData.get(server);
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID who = player.getUUID();
        BlockPos live = new BlockPos(2, 1, 2), stale = new BlockPos(4, 1, 2), swept = new BlockPos(2, 1, 4), bare = new BlockPos(4, 1, 4);
        ItemStack first = totem(who, "Rowan"), second = totem(who, "Rowan"), third = totem(who, "Rowan");
        UUID a = TotemIdentity.id(first), b = TotemIdentity.id(second), c = TotemIdentity.id(third);
        try {
            // standing before the rule (as an old world's totem would)
            h.assertTrue(TotemPlacer.place(level, h.absolutePos(live), first.copy()), "placed outside a room");
            h.assertTrue(TotemPlacer.place(level, h.absolutePos(stale), second.copy()), "placed outside a room");
            h.assertTrue(TotemPlacer.place(level, h.absolutePos(swept), third.copy()), "placed outside a room");
            ledger.reissue(b, level.getGameTime()); // the second block's copy is stale now
            h.setBlock(bare, ModBlocks.SHATTER_TOTEM.get()); // a /setblock: no owner, no identity
            asRoom(h, () -> {
                // just after a start it waits (a network server's database may not be connected yet)
                TotemDimensions.justStartedForTests(true);
                try {
                    ((ShatterTotemBlockEntity) h.getBlockEntity(live)).serverTick(level);
                } finally {
                    TotemDimensions.justStartedForTests(false);
                }
                h.assertBlockPresent(ModBlocks.SHATTER_TOTEM.get(), live);
                h.assertTrue(waiting(season, who, a) == 0, "nothing handed on just after a start");
                // a bare block is no totem: removed, and nothing is made of it
                var bareTotem = com.mrgregles.bsp_core.totem.ShatterTotemBlock.toItemStack(level, h.absolutePos(bare));
                h.assertTrue(TotemDimensions.leave(server, bareTotem, level.dimension().location(), h.absolutePos(bare)) == null, "a bare block gives nothing");
                h.assertFalse(((ShatterTotemBlockEntity) h.getBlockEntity(bare)).leaveForbiddenDimension(level), "nothing handed on");
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), bare);
                // a placed block's tick in a room
                ((ShatterTotemBlockEntity) h.getBlockEntity(live)).serverTick(level);
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), live);
                h.assertTrue(waiting(season, who, a) == 1, "the live totem waits for its owner");
                var s = ledger.sighting(a);
                h.assertTrue(s != null && s.where() == TotemLedger.IN_TRANSIT, "in transit, not lost from its place: " + s);
                // a stale copy is removed and nothing is given
                ((ShatterTotemBlockEntity) h.getBlockEntity(stale)).serverTick(level);
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), stale);
                h.assertTrue(waiting(season, who, b) == 0, "a stale copy gives nothing back");
                // the start sweep finds a placed totem from the ledger
                GlobalPos at = GlobalPos.of(level.dimension(), h.absolutePos(swept));
                ShatterTotemBlockEntity sweptEntity = (ShatterTotemBlockEntity) h.getBlockEntity(swept);
                h.assertTrue(TotemDimensions.sweep(server, at::equals) == 1, "the sweep finds the totem standing in a room");
                h.assertBlockNotPresent(ModBlocks.SHATTER_TOTEM.get(), swept);
                h.assertTrue(waiting(season, who, c) == 1, "the swept totem waits for its owner");
                h.assertFalse(ledger.placedFor(who).contains(at), "and it is no longer counted as placed");
                // a chunk the sweep loaded from disk runs its block entities' onLoad at the next tick, after the removal: nothing comes back
                sweptEntity.onLoad();
                h.assertFalse(ledger.placedFor(who).contains(at), "a late onLoad of the removed totem records nothing");
                h.assertTrue(TotemDimensions.sweep(server, at::equals) == 0, "a second sweep finds nothing");
            });
            AdminService.handOverOwed(player);
            h.assertTrue(held(player, a) == 1 && held(player, c) == 1 && held(player, b) == 0, "both live totems are back with their owner");
        } finally {
            clearWorld(level, List.of(h.absolutePos(live), h.absolutePos(stale), h.absolutePos(swept), h.absolutePos(bare)), List.of());
            player.getInventory().clearContent();
            season.pendingItems.remove(who);
            ledger.forget(a);
            ledger.forget(b);
            ledger.forget(c);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void in_the_real_compact_machines_dimension_a_totem_never_stands_or_lies(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerLevel rooms = server.getLevel(ResourceKey.create(Registries.DIMENSION, ROOMS));
        if (rooms == null) {
            BSPCore.LOGGER.info("GameTest: Compact Machines is not loaded (run with -PwithCm=<the pack's mods folder>); the real-dimension test is skipped");
            h.succeed();
            return;
        }
        var ledger = TotemLedger.get(server);
        var season = SeasonData.get(server);
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID who = player.getUUID();
        BlockPos floor = new BlockPos(-30_000, 20, -30_000), spot = floor.above(), lying = spot.east(); // the rooms' world is 48 high (y 0 to 47)
        ItemStack first = totem(who, "Rowan"), second = totem(who, "Rowan");
        UUID a = TotemIdentity.id(first), b = TotemIdentity.id(second);
        List<String> before = new ArrayList<>(BSPConfig.ALLOWED_DIMENSIONS.get());
        List<ShatterTotemItemEntity> left = new ArrayList<>();
        try {
            List<String> listed = new ArrayList<>(before);
            listed.add(ROOMS.toString());
            BSPConfig.ALLOWED_DIMENSIONS.set(listed);
            rooms.getChunk(floor);
            rooms.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            rooms.setBlock(lying.below(), Blocks.STONE.defaultBlockState(), 3);
            h.assertFalse(BSPConfig.isDimensionAllowed(ROOMS), "listed, and still refused");
            h.assertFalse(TotemPlacer.place(rooms, spot, first.copy()), "nothing places a totem in the real rooms dimension");
            h.assertTrue(rooms.getBlockState(spot).isAir(), "nothing stands there");
            // a totem set there by a command (as an old world's would stand): its tick sends it to its owner
            rooms.setBlock(spot, ModBlocks.SHATTER_TOTEM.get().defaultBlockState(), 3);
            h.assertTrue(rooms.getBlockEntity(spot) instanceof ShatterTotemBlockEntity, "a totem set by a command stands there for now");
            ((ShatterTotemBlockEntity) rooms.getBlockEntity(spot)).loadFromStack(first.copy());
            GlobalPos at = GlobalPos.of(rooms.dimension(), spot);
            h.assertTrue(TotemDimensions.sweep(server, at::equals) == 1, "the sweep finds it in the real dimension");
            h.assertTrue(rooms.getBlockState(spot).isAir() && waiting(season, who, a) == 1, "removed and waiting for its owner");
            // a ground totem lying there
            var ground = new ShatterTotemItemEntity(rooms, lying.getX() + 0.5, lying.getY(), lying.getZ() + 0.5, second.copy());
            left.add(ground);
            rooms.addFreshEntity(ground);
            ground.tick();
            h.assertTrue(ground.isRemoved() && waiting(season, who, b) == 1, "the ground totem goes to its owner");
            AdminService.handOverOwed(player);
            h.assertTrue(held(player, a) == 1 && held(player, b) == 1, "both given back at the next login");
        } finally {
            BSPConfig.ALLOWED_DIMENSIONS.set(before);
            clearWorld(rooms, List.of(spot), left);
            rooms.setBlock(floor, Blocks.AIR.defaultBlockState(), 3);
            rooms.setBlock(lying.below(), Blocks.AIR.defaultBlockState(), 3);
            player.getInventory().clearContent();
            season.pendingItems.remove(who);
            ledger.forget(a);
            ledger.forget(b);
        }
        h.succeed();
    }
}
