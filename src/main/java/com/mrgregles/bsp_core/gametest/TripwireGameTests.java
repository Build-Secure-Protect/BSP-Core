package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.team.Alliances;
import com.mrgregles.bsp_core.team.Teams;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for Tripwire, the totem's redstone sensor: its four ranges, one per totem tier from Tier II, at Raid prices; who sets
 * it off (outsiders only: never the owner, a teammate, an ally, a listed friend, or a creative or spectating player); the cube;
 * the signal on all six sides as weak power; the hold after the last outsider goes; the range setting; and Thief's Step.
 * Mock players are not in the level's player list, so the tests hand them to {@link ShatterTotemBlockEntity#tripwireCheck}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TripwireGameTests {
    private static final String EMPTY = "empty", BATCH = "tripwire";

    private static ServerPlayer player(GameTestHelper h, GameType mode) {
        return MockPlayers.serverPlayer(h, mode);
    }

    /** A placed Tier V totem owned by {@code owner}, with Alarm at 2 and Tripwire at {@code level}. */
    private static ShatterTotemBlockEntity totem(GameTestHelper h, BlockPos pos, ServerPlayer owner, int level) {
        h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.setOwner(new TotemOwner(owner.getUUID(), owner.getGameProfile().getName()));
        be.setTier(TotemUpgrades.MAX_TIER);
        be.setUpgradeLevel(Buff.ALARM, 2);
        be.setUpgradeLevel(Buff.TRIPWIRE, level);
        return be;
    }

    /** Stands {@code p} {@code dx} blocks east of the centre of the totem at {@code pos}, level with it. */
    private static ServerPlayer standEast(GameTestHelper h, ServerPlayer p, BlockPos pos, double dx) {
        BlockPos at = h.absolutePos(pos);
        p.setPos(at.getX() + 0.5 + dx, at.getY(), at.getZ() + 0.5);
        return p;
    }

    private static String name(String prefix) {
        String n = prefix + " " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        return n.substring(0, Math.min(16, n.length()));
    }

    /**
     * Takes the totem away without an owner, so it is not sent back to its (mock) owner: the test world keeps its data between runs,
     * and every returned totem would leave another scoring player behind in it.
     */
    private static void clear(GameTestHelper h, ShatterTotemBlockEntity be, BlockPos pos) {
        be.setOwner(null);
        h.setBlock(pos, Blocks.AIR);
    }

    /** The strongest signal any block around {@code rel} gives it, as a lamp or dust there would see it. */
    private static int signalAt(GameTestHelper h, BlockPos rel) {
        return h.getLevel().getBestNeighborSignal(h.absolutePos(rel));
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void tripwire_has_four_ranges_one_per_tier_at_raid_prices(GameTestHelper h) {
        Buff tw = Buff.TRIPWIRE;
        h.assertTrue(tw.parent() == Buff.ALARM && tw.branch() == TotemUpgrades.Branch.BASE && tw.placedOnly && !tw.single && tw.tier == 1 && tw.maxLevel() == 4,
                "a four-level Base power after Alarm, from Tier II");
        h.assertTrue(tw.ordinal() > Buff.HERALD.ordinal(), "appended after Herald, so the ordinals already saved and sent keep their meaning");
        int[] caps = {0, 1, 2, 3, 4};
        for (int t = 0; t <= TotemUpgrades.MAX_TIER; t++) {
            h.assertTrue(tw.cap(t) == caps[t], "a Tier " + TotemUpgrades.roman(t) + " totem holds " + caps[t] + " ranges, not " + tw.cap(t));
        }
        List<? extends Integer> ranges = BSPConfig.TRIPWIRE_RANGES.get();
        for (int l = 1; l <= 4; l++) {
            h.assertTrue(tw.reach(l) == ranges.get(l - 1), "level " + l + " reaches " + ranges.get(l - 1));
        }
        h.assertTrue(tw.reach(0) == 0, "nothing at level 0");
        int coins = BSPConfig.RAID_LEVEL_COINS.get().get(0), xp = BSPConfig.RAID_LEVEL_XP.get().get(0);
        int lastXp = 0;
        for (int l = 0; l < 4; l++) {
            var price = TotemUpgrades.price(tw, l);
            int tier = tw.tier + l;
            h.assertTrue(price != null && price.tier() == tier && price.coin() == CoinTier.values()[tier] && price.coins() == coins && price.xp() == xp * (tier + 1),
                    "level " + (l + 1) + " costs " + coins + " " + CoinTier.values()[tier] + " and " + xp * (tier + 1) + " XP: " + (price == null ? null : price.coins() + " " + price.coin() + ", " + price.xp() + " XP"));
            h.assertTrue(price.xp() > lastXp, "each range costs more than the last");
            lastXp = price.xp();
        }
        h.assertTrue(TotemUpgrades.price(tw, 4) == null, "nothing after 128 blocks");
        // through the real buy path at a placed Tier III totem: two ranges, then the tier limit
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer owner = player(h, GameType.CREATIVE); // creative: nothing to pay
        ShatterTotemBlockEntity be = totem(h, pos, owner, 0);
        be.setTier(2);
        h.assertTrue(TotemUpgrades.whyNot(tw, b -> b == Buff.ALARM ? 1 : 0, 2, owner) != null, "Alarm must reach level 2 first");
        be.tryBuy(owner, tw.ordinal());
        h.assertTrue(be.getUpgradeLevel(tw) == 1 && be.tripwireLevel() == 1, "16 blocks bought and in use");
        be.setTripwireRange(0);
        h.assertTrue(be.tripwireLevel() == 0, "switched off");
        be.tryBuy(owner, tw.ordinal());
        h.assertTrue(be.getUpgradeLevel(tw) == 2 && be.tripwireSetting() == -1 && be.tripwireLevel() == 2, "a new range goes into use at once, even from off");
        be.tryBuy(owner, tw.ordinal());
        h.assertTrue(be.getUpgradeLevel(tw) == 2, "the third range needs Tier IV");
        clear(h, be, pos);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_outsider_powers_every_side_and_friends_never_do(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(4, 2, 4);
        ServerPlayer owner = player(h, GameType.SURVIVAL), mate = player(h, GameType.SURVIVAL), ally = player(h, GameType.SURVIVAL),
                friend = player(h, GameType.SURVIVAL), creative = player(h, GameType.CREATIVE), spectator = player(h, GameType.SPECTATOR),
                stranger = player(h, GameType.SURVIVAL);
        try {
            boolean teams = Teams.enabled(), alliances = Alliances.enabled();
            if (teams) {
                h.assertTrue(Teams.create(owner, name("Wire"), ChatFormatting.RED).ok(), "the owner's team");
                h.assertTrue(Teams.invite(owner, mate).ok() && Teams.accept(mate).ok(), "a teammate joins");
            }
            if (alliances) {
                h.assertTrue(Teams.create(ally, name("Pal"), ChatFormatting.BLUE).ok(), "the ally's team");
                h.assertTrue(Alliances.create(owner, name("Pact")).ok(), "the alliance");
                h.assertTrue(Alliances.invite(owner, Teams.teamOf(level.getServer(), ally.getUUID()).id, ally).ok() && Alliances.accept(ally).ok(), "the ally's team joins it");
            }
            ShatterTotemBlockEntity be = totem(h, pos, owner, 1);
            h.assertTrue(be.addAccess(friend.getUUID(), friend.getGameProfile().getName()), "a friend on the access list");
            int r = Buff.TRIPWIRE.reach(1);
            List<ServerPlayer> friends = new java.util.ArrayList<>(List.of(owner, friend, creative, spectator));
            if (teams) {
                friends.add(mate);
            }
            if (alliances) {
                friends.add(ally);
            }
            for (ServerPlayer p : friends) {
                standEast(h, p, pos, 3);
                h.assertFalse(be.tripsTripwire(p, 1), p.getGameProfile().getName() + " is not an outsider");
            }
            be.tripwireCheck(level, friends);
            h.assertFalse(be.tripwireSignal(), "friends never set it off");
            for (Direction d : Direction.values()) {
                h.assertTrue(signalAt(h, pos.relative(d)) == 0, "no signal yet " + d);
            }
            // the cube: inside at its edge, not one block past it
            standEast(h, stranger, pos, r);
            h.assertTrue(be.tripsTripwire(stranger, 1), "an outsider " + r + " blocks away is inside the cube");
            standEast(h, stranger, pos, r + 1);
            h.assertFalse(be.tripsTripwire(stranger, 1), "one block past the edge is outside");
            h.assertTrue(be.tripsTripwire(stranger, 2), "the next range reaches further");
            be.tripwireCheck(level, List.of(stranger));
            h.assertFalse(be.tripwireSignal(), "outside the range in use, no signal");
            standEast(h, stranger, pos, 3);
            be.tripwireCheck(level, List.of(stranger));
            h.assertTrue(be.tripwireSignal(), "an outsider in range sets it off");
            for (Direction d : Direction.values()) {
                h.assertTrue(signalAt(h, pos.relative(d)) == 15, "full signal on the " + d + " side, not " + signalAt(h, pos.relative(d)));
            }
            // weak power: a block beside the totem does not pass it on
            h.setBlock(pos.east(), Blocks.STONE);
            h.assertTrue(signalAt(h, pos.east(2)) == 0, "weak power does not pass through the stone beside it");
            CompoundTag saved = be.saveWithoutMetadata();
            h.assertTrue(saved.getBoolean("TripSignal") && !saved.contains("TripRange"), "the signal is saved; the range follows the top");
            // no owner, no signal
            be.setOwner(null);
            be.tripwireCheck(level, List.of(stranger));
            h.assertFalse(be.tripwireSignal(), "an unclaimed totem gives no signal");
            h.setBlock(pos.east(), Blocks.AIR);
            clear(h, be, pos);
            h.succeed();
        } finally {
            for (ServerPlayer p : List.of(owner, ally)) {
                if (Teams.teamOf(p.server, p.getUUID()) != null) {
                    Teams.disband(p);
                }
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void the_signal_holds_after_the_last_outsider_goes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer owner = player(h, GameType.SURVIVAL), stranger = standEast(h, player(h, GameType.SURVIVAL), pos, 2);
        ShatterTotemBlockEntity be = totem(h, pos, owner, 1);
        BlockPos lamp = pos.west();
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        be.tripwireCheck(level, List.of(stranger));
        h.assertTrue(be.tripwireSignal(), "on while the outsider is near");
        h.assertTrue(h.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "the lamp beside it is told at once and lights");
        h.assertTrue(be.getUpdateTag().getBoolean("TripSignal"), "watching clients are told, so the studs light");
        long seen = level.getGameTime();
        int hold = BSPConfig.TRIPWIRE_HOLD_SECONDS.get() * 20;
        // the outsider is gone: the totem's own looks find nobody, and the signal holds, then goes off
        pollOff(h, be, hold + 200, () -> {
            long held = level.getGameTime() - seen;
            h.assertTrue(held >= Math.max(20, hold), "held " + held + " ticks, at least the " + hold + " the config asks");
            h.assertTrue(signalAt(h, pos.east()) == 0, "and the neighbours see it go");
            h.assertFalse(be.getUpdateTag().getBoolean("TripSignal"), "and the clients see the studs go dark");
            // a lamp goes dark a few ticks after it loses power
            poll(h, 40, () -> !h.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), () -> {
                h.setBlock(lamp, Blocks.AIR);
                clear(h, be, pos);
                h.succeed();
            }, "the lamp never went dark: the totem did not tell its neighbours");
        });
    }

    /** Runs {@code then} once {@code ok} holds, checking each tick for up to {@code ticks}; fails with {@code why} after that. */
    private static void poll(GameTestHelper h, int ticks, java.util.function.BooleanSupplier ok, Runnable then, String why) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail(why);
        } else {
            h.runAfterDelay(1, () -> poll(h, ticks - 1, ok, then, why));
        }
    }

    /** Runs {@code then} once the totem's signal is off, checking each tick for up to {@code ticks}. */
    private static void pollOff(GameTestHelper h, ShatterTotemBlockEntity be, int ticks, Runnable then) {
        if (!be.tripwireSignal()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail("the signal never went off");
        } else {
            h.runAfterDelay(1, () -> pollOff(h, be, ticks - 1, then));
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_range_setting_and_thief_step(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer owner = player(h, GameType.SURVIVAL), thief = standEast(h, player(h, GameType.SURVIVAL), pos, 10);
        ShatterTotemBlockEntity be = totem(h, pos, owner, 4);
        h.assertTrue(be.tripwireLevel() == 4, "follows the top range by default");
        be.setTripwireRange(2);
        h.assertTrue(be.tripwireLevel() == 2 && be.saveWithoutMetadata().getInt("TripRange") == 2, "a chosen range is used and saved");
        be.setTripwireRange(0);
        be.tripwireCheck(level, List.of(thief));
        h.assertFalse(be.tripwireSignal(), "off gives nothing");
        // Thief's Step 2 hides from ranges 1 and 2 (thiefStepHidesFromTripwire 1, 2, 3, 4), not from 3
        ItemStack carried = new ItemStack(ModItems.SHATTER_TOTEM.get());
        TotemUpgrades.setLevel(carried, Buff.THIEF_STEP, 2);
        thief.setItemInHand(InteractionHand.OFF_HAND, carried);
        int hides = BSPConfig.levelValue(BSPConfig.THIEF_STEP_TRIPWIRE.get(), 2, 0);
        for (int l = 1; l <= 4; l++) {
            h.assertTrue(be.tripsTripwire(thief, l) == (l > hides), "Thief's Step 2 against range level " + l);
        }
        be.setTripwireRange(2);
        be.tripwireCheck(level, List.of(thief));
        h.assertTrue(be.tripwireSignal() == (2 > hides), "a Tripwire set to 32 against Thief's Step 2 (hides up to " + hides + ")");
        be.setTripwireRange(-1);
        be.tripwireCheck(level, List.of(thief));
        h.assertTrue(be.tripwireSignal() == (4 > hides), "at 128 against Thief's Step 2 (hides up to " + hides + ")");
        be.setTripwireRange(0);
        h.assertFalse(be.tripwireSignal(), "switching it off drops the signal at once");
        // a narrower range looks again at once: the hold earned at the wider one does not carry over
        thief.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        standEast(h, thief, pos, 100);
        be.setTripwireRange(-1);
        be.tripwireCheck(level, List.of(thief));
        h.assertTrue(be.tripwireSignal(), "128 blocks sees the outsider 100 blocks out");
        be.setTripwireRange(1);
        h.assertFalse(be.tripwireSignal(), "set to 16, the signal goes at once instead of holding");
        // the setting goes with the totem when it is picked up and placed again
        be.setTripwireRange(2);
        CompoundTag item = new CompoundTag();
        be.writeToStackTag(item);
        h.assertTrue(item.getInt("TripRange") == 2, "the item keeps the setting");
        be.setTripwireRange(-1);
        ItemStack stack = new ItemStack(ModItems.SHATTER_TOTEM.get());
        stack.setTag(item);
        be.loadFromStack(stack);
        h.assertTrue(be.tripwireSetting() == 2, "placing it again restores the setting");
        // a new owner does not inherit the old owner's hold
        be.setTripwireRange(-1);
        be.tripwireCheck(level, List.of(thief));
        h.assertTrue(be.tripwireSignal(), "on for the outsider");
        be.setOwner(new TotemOwner(thief.getUUID(), thief.getGameProfile().getName()));
        h.assertFalse(be.tripwireSignal(), "the totem changed hands: the signal for the old owner stops at once");
        be.setOwner(new TotemOwner(owner.getUUID(), owner.getGameProfile().getName()));
        // a totem without Tripwire never gives a signal
        be.setTripwireRange(-1);
        be.setUpgradeLevel(Buff.TRIPWIRE, 0);
        be.tripwireCheck(level, List.of(thief));
        h.assertFalse(be.tripwireSignal() || be.tripwireLevel() != 0, "not bought: nothing");
        clear(h, be, pos);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_next_key_steps_through_the_ranges_bought_for_those_who_may_upgrade(GameTestHelper h) {
        // the order: each range bought, then off, then the first; the top is kept as "follow the top" (-1)
        int[][] steps = {{4, -1, 0}, {4, 0, 1}, {4, 1, 2}, {4, 2, 3}, {4, 3, -1}, {2, -1, 0}, {2, 0, 1}, {2, 1, -1}, {1, -1, 0}, {1, 0, -1}, {2, 3, 0}};
        for (int[] st : steps) {
            int got = ShatterTotemBlockEntity.nextTripwireRange(st[1], st[0]);
            h.assertTrue(got == st[2], "with " + st[0] + " bought, NEXT after " + st[1] + " is " + st[2] + ", not " + got);
        }
        BlockPos pos = new BlockPos(2, 1, 2);
        ServerPlayer owner = player(h, GameType.SURVIVAL), stranger = player(h, GameType.SURVIVAL), friend = player(h, GameType.SURVIVAL),
                mate = player(h, GameType.SURVIVAL);
        try {
            ShatterTotemBlockEntity be = totem(h, pos, owner, 2);
            h.assertTrue(be.stepTripwireRange(owner) && be.tripwireSetting() == 0 && be.tripwireLevel() == 0, "the owner switches it off");
            h.assertTrue(be.stepTripwireRange(owner) && be.tripwireLevel() == 1, "then 16 blocks");
            h.assertFalse(be.stepTripwireRange(stranger), "an outsider is refused");
            h.assertTrue(be.tripwireLevel() == 1, "and nothing changed");
            h.assertTrue(be.addAccess(friend.getUUID(), friend.getGameProfile().getName()), "a friend with every switch");
            h.assertTrue(be.stepTripwireRange(friend) && be.tripwireSetting() == -1 && be.tripwireLevel() == 2, "a friend with Upgrades steps it to the top");
            be.toggleAccess(0, com.mrgregles.bsp_core.totem.TotemAccess.UPGRADES);
            h.assertFalse(be.stepTripwireRange(friend), "without the Upgrades switch the friend is refused");
            if (Teams.enabled()) {
                h.assertTrue(Teams.create(owner, name("Next"), ChatFormatting.GOLD).ok() && Teams.invite(owner, mate).ok() && Teams.accept(mate).ok(), "a teammate");
                h.assertTrue(be.stepTripwireRange(mate) && be.tripwireSetting() == 0, "a teammate may change it");
            }
            be.setUpgradeLevel(Buff.TRIPWIRE, 0);
            h.assertFalse(be.stepTripwireRange(owner), "nothing to step before a range is bought");
            // the packet carries only the place
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.TripwireRangePacket.encode(new com.mrgregles.bsp_core.network.TripwireRangePacket(h.absolutePos(pos)), buf);
            h.assertTrue(com.mrgregles.bsp_core.network.TripwireRangePacket.decode(buf).pos().equals(h.absolutePos(pos)), "encode and decode agree");
            clear(h, be, pos);
            h.succeed();
        } finally {
            if (Teams.teamOf(owner.server, owner.getUUID()) != null) {
                Teams.disband(owner);
            }
        }
    }

    /** A generated asset from the mod's own resources, as JSON. */
    private static com.google.gson.JsonObject asset(GameTestHelper h, String path) {
        try (var in = TripwireGameTests.class.getResourceAsStream("/assets/bsp_core/" + path)) {
            h.assertTrue(in != null, path + " is in the mod");
            return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void every_totem_has_eight_lamp_studs_in_its_base(GameTestHelper h) {
        var elements = asset(h, "models/block/shatter_totem_geometry.json").getAsJsonArray("elements");
        int lenses = 0, bars = 0;
        java.util.Set<String> sides = new java.util.HashSet<>();
        for (var raw : elements) {
            var e = raw.getAsJsonObject();
            String name = e.get("name").getAsString();
            if (!name.startsWith("stud_")) {
                continue;
            }
            var from = e.getAsJsonArray("from");
            var to = e.getAsJsonArray("to");
            float y0 = from.get(1).getAsFloat(), y1 = to.get(1).getAsFloat();
            h.assertTrue(y0 >= 0 && y1 <= 2, name + " sits within the base's height");
            for (int k : new int[]{0, 2}) {
                h.assertTrue(from.get(k).getAsFloat() >= 1.75f && to.get(k).getAsFloat() <= 14.25f, name + " stands no more than a quarter pixel off the base");
            }
            if (name.endsWith("_lens")) {
                lenses++;
                sides.add(name.substring(5, name.length() - 6));
            } else {
                bars++;
            }
        }
        h.assertTrue(lenses == 8 && bars == 32, "eight studs of a lens and four bars: " + lenses + " lenses, " + bars + " bars");
        h.assertTrue(sides.containsAll(List.of("north", "south", "east", "west")), "two on every side: " + sides);
        // the studs come out of the Base Mould with the base; every other piece shows them as ghost
        for (String mould : List.of("base_mould", "torso_mould", "head_mould")) {
            boolean base = mould.equals("base_mould");
            for (var raw : asset(h, "models/item/" + mould + ".json").getAsJsonArray("elements")) {
                var e = raw.getAsJsonObject();
                if (e.get("name").getAsString().startsWith("stud_")) {
                    String tex = e.getAsJsonObject("faces").entrySet().iterator().next().getValue().getAsJsonObject().get("texture").getAsString();
                    h.assertTrue(tex.equals(base ? "#piece" : "#ghost"), mould + " draws " + e.get("name").getAsString() + " as " + tex);
                }
            }
        }
        h.succeed();
    }
}
