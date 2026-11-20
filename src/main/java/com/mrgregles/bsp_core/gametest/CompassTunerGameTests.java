package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.Ladder;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.Stall;
import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.TuneCheck;
import com.mrgregles.bsp_core.compass.CompassTunerMenu;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import com.mrgregles.bsp_core.compass.TunerLedger;
import com.mrgregles.bsp_core.compass.TunerMaterials;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * GameTests for the Compass Tuner: its four upgrade ladders and who may buy them, who may mine it and what its drop keeps, the
 * per-player limit, the materials config, a tune on the real clock (FE, plasma, time away, stopping, finishing), piping, the
 * one-time handover of an old compass, the screen's keys, and saving. In the compass range batch: a batch of its own would
 * reorder the batches (see the brief).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CompassTunerGameTests {
    private static final String EMPTY = "empty", BATCH = "compass_range";
    private static final long MIN = 60_000L;

    private static CompassTunerBlockEntity tuner(GameTestHelper h, BlockPos rel, ServerPlayer owner) {
        h.setBlock(rel, ModBlocks.COMPASS_TUNER.get().defaultBlockState());
        CompassTunerBlockEntity t = (CompassTunerBlockEntity) h.getBlockEntity(rel);
        t.setOwner(owner);
        return t;
    }

    private static ItemStack compass() {
        return new ItemStack(ModItems.TOTEM_COMPASS.get());
    }

    /** The default materials, each slot with {@code n}. */
    private static void materials(CompassTunerBlockEntity t, int n) {
        t.getItems().setStackInSlot(1, new ItemStack(Items.LAPIS_LAZULI, n));
        t.getItems().setStackInSlot(2, new ItemStack(Items.QUARTZ, n));
        t.getItems().setStackInSlot(3, new ItemStack(Items.REDSTONE, n));
    }

    private static String teamName() {
        return "Tn " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void tuner_ladders_cost_the_compass_prices_and_only_the_team_may_buy(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL), stranger = MockPlayers.serverPlayer(h, GameType.SURVIVAL),
                mate = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        h.assertTrue(Ladder.ACROSS.max() == 6 && Ladder.DEPTH.max() == 6 && Ladder.COOLDOWN.max() == 7 && Ladder.SPEED.max() == 4,
                "6, 6, 7 and 4 levels");
        h.assertTrue(Ladder.ACROSS.cost(0) == 10 && Ladder.ACROSS.coin(0) == CoinTier.COPPER && Ladder.DEPTH.cost(5) == 40 && Ladder.DEPTH.coin(5) == CoinTier.ILLYRIUM,
                "range levels at the compass prices: 10 Copper first, 40 Illyrium last");
        h.assertTrue(Ladder.COOLDOWN.cost(0) == 4 && Ladder.COOLDOWN.coin(0) == CoinTier.COPPER && Ladder.COOLDOWN.cost(4) == 2 && Ladder.COOLDOWN.coin(4) == CoinTier.ILLYRIUM,
                "cooldown levels at the compass prices");
        h.assertTrue(Ladder.SPEED.cost(0) == 10 && Ladder.SPEED.coin(0) == CoinTier.GOLD && Ladder.SPEED.cost(1) == 10 && Ladder.SPEED.coin(1) == CoinTier.DIAMOND
                && Ladder.SPEED.cost(2) == 10 && Ladder.SPEED.coin(2) == CoinTier.NETHERITE && Ladder.SPEED.cost(3) == 20 && Ladder.SPEED.coin(3) == CoinTier.ILLYRIUM
                && Ladder.SPEED.cost(4) == -1, "Tune speed: 10 Gold, 10 Diamond, 10 Netherite, 20 Illyrium, then the top");
        h.assertTrue(CompassTunerBlockEntity.tuneMinutes(0) == 120 && CompassTunerBlockEntity.tuneMinutes(1) == 90 && CompassTunerBlockEntity.tuneMinutes(2) == 60
                && CompassTunerBlockEntity.tuneMinutes(3) == 45 && CompassTunerBlockEntity.tuneMinutes(4) == 30, "2 h, 1 h 30, 1 h, 45 min, 30 min");

        h.assertFalse(t.buy(owner, Ladder.ACROSS), "no coins, no level");
        owner.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 12));
        h.assertTrue(t.buy(owner, Ladder.ACROSS) && t.across() == 1 && owner.getInventory().countItem(CoinTier.COPPER.coin()) == 2,
                "10 Copper buys Across 1 and takes exactly 10");
        stranger.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 10));
        h.assertFalse(t.buy(stranger, Ladder.DEPTH), "a stranger cannot buy on someone's tuner");
        h.assertTrue(stranger.getInventory().countItem(CoinTier.COPPER.coin()) == 10 && t.depth() == 0, "and nothing was taken");

        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(owner, teamName(), net.minecraft.ChatFormatting.AQUA).ok(), "a team");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(owner, mate).ok() && com.mrgregles.bsp_core.team.Teams.accept(mate).ok(), "with a mate");
        try {
            mate.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 10));
            h.assertTrue(t.buy(mate, Ladder.DEPTH) && t.depth() == 1, "a teammate may buy");
        } finally {
            com.mrgregles.bsp_core.team.Teams.disband(owner);
        }

        owner.setGameMode(GameType.CREATIVE);
        h.assertTrue(t.buy(owner, Ladder.SPEED) && t.speed() == 1, "free in creative");
        t.setLevels(6, 6, 7, 4);
        for (Ladder l : Ladder.values()) {
            h.assertFalse(t.buy(owner, l), "nothing past the top of " + l);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void only_the_team_mines_a_tuner_and_its_drop_keeps_the_upgrades(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL), stranger = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos rel = new BlockPos(1, 1, 1), abs = h.absolutePos(rel);
        CompassTunerBlockEntity t = tuner(h, rel, owner);
        t.setLevels(3, 2, 4, 1);
        var state = h.getBlockState(rel);
        h.assertTrue(state.getDestroyProgress(stranger, h.getLevel(), abs) == 0f, "a stranger cannot mine it at all");
        h.assertTrue(state.getDestroyProgress(owner, h.getLevel(), abs) > 0f, "the owner can");
        h.assertTrue(state.canHarvestBlock(h.getLevel(), abs, owner), "and it drops whatever they mine it with, a bare hand too");
        h.assertFalse(state.getBlock().onDestroyedByPlayer(state, h.getLevel(), abs, stranger, true, h.getLevel().getFluidState(abs)),
                "the server refuses a stranger's break");
        h.assertTrue(h.getBlockState(rel).is(ModBlocks.COMPASS_TUNER.get()), "and it stays");
        h.assertTrue(state.getBlock().getExplosionResistance() > 1_000_000f, "explosions do not break it");
        h.assertTrue(state.getDestroySpeed(h.getLevel(), abs) < 0, "machines that break blocks pass it by, as bedrock");
        var wither = net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel());
        h.assertFalse(state.canEntityDestroy(h.getLevel(), abs, wither), "a Wither cannot break it");
        ServerPlayer mate = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(owner, teamName(), net.minecraft.ChatFormatting.GOLD).ok()
                && com.mrgregles.bsp_core.team.Teams.invite(owner, mate).ok() && com.mrgregles.bsp_core.team.Teams.accept(mate).ok(), "a team");
        try {
            h.assertTrue(state.getDestroyProgress(mate, h.getLevel(), abs) > 0f, "a teammate can mine it");
            CompoundTag sync = t.getUpdateTag();
            h.assertTrue(sync.hasUUID("Owner") && sync.getList("Team", net.minecraft.nbt.Tag.TAG_INT_ARRAY).size() == 1,
                    "clients are told the owner and the team, so a stranger's client shows no cracks");
        } finally {
            com.mrgregles.bsp_core.team.Teams.disband(owner);
        }

        List<ItemStack> drops = Block.getDrops(state, h.getLevel(), abs, t, owner, new ItemStack(Items.IRON_PICKAXE));
        h.assertTrue(drops.size() == 1 && drops.get(0).is(ModItems.COMPASS_TUNER.get()), "it drops itself: " + drops);
        CompoundTag kept = drops.get(0).getTagElement("BlockEntityTag");
        h.assertTrue(kept != null && kept.getInt("Across") == 3 && kept.getInt("Depth") == 2 && kept.getInt("Cooldown") == 4 && kept.getInt("Speed") == 1,
                "the drop keeps the four levels: " + kept);

        // placed again, it has them back
        BlockPos rel2 = new BlockPos(3, 1, 1);
        h.setBlock(rel2, ModBlocks.COMPASS_TUNER.get().defaultBlockState());
        BlockItem.updateCustomBlockEntityTag(h.getLevel(), owner, h.absolutePos(rel2), drops.get(0));
        CompassTunerBlockEntity again = (CompassTunerBlockEntity) h.getBlockEntity(rel2);
        h.assertTrue(again.across() == 3 && again.depth() == 2 && again.cooldown() == 4 && again.speed() == 1, "placed again, the levels come back");

        // what is inside drops when it goes
        t.getItems().setStackInSlot(0, compass());
        materials(t, 5);
        h.setBlock(rel, Blocks.AIR.defaultBlockState());
        h.assertItemEntityPresent(ModItems.TOTEM_COMPASS.get(), rel, 2.0);
        h.assertItemEntityPresent(Items.LAPIS_LAZULI, rel, 2.0);
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_player_may_have_one_tuner_placed(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        TunerLedger ledger = TunerLedger.get(h.getLevel().getServer());
        BlockItem item = (BlockItem) ModItems.COMPASS_TUNER.get();
        BlockPos floorA = h.absolutePos(new BlockPos(1, 0, 1)), floorB = h.absolutePos(new BlockPos(3, 0, 1));
        h.setBlock(new BlockPos(1, 0, 1), Blocks.STONE.defaultBlockState());
        h.setBlock(new BlockPos(3, 0, 1), Blocks.STONE.defaultBlockState());
        ItemStack stack = new ItemStack(item, 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var first = item.place(new BlockPlaceContext(h.getLevel(), p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(floorA), Direction.UP, floorA, false)));
        h.assertTrue(first.consumesAction() && ledger.count(p.getUUID()) == 1, "the first goes down and counts");
        h.assertTrue(((CompassTunerBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1))).isOwner(p), "and is theirs");
        var second = item.place(new BlockPlaceContext(h.getLevel(), p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(floorB), Direction.UP, floorB, false)));
        h.assertFalse(second.consumesAction(), "a second is refused");
        h.assertTrue(h.getBlockState(new BlockPos(3, 1, 1)).isAir(), "nothing was placed");
        h.setBlock(new BlockPos(1, 1, 1), Blocks.AIR.defaultBlockState());
        h.assertTrue(ledger.count(p.getUUID()) == 0, "mined, it no longer counts");
        var third = item.place(new BlockPlaceContext(h.getLevel(), p, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(floorB), Direction.UP, floorB, false)));
        h.assertTrue(third.consumesAction(), "then another may go down");
        h.setBlock(new BlockPos(3, 1, 1), Blocks.AIR.defaultBlockState());
        // an entry whose block went without its removal running (a world edit) is dropped when the limit is checked
        ledger.put(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(5, 1, 1))), p.getUUID());
        h.assertTrue(ledger.count(p.getUUID()) == 1 && ledger.count(h.getLevel().getServer(), p.getUUID()) == 0, "a lost entry does not block the player");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void tuner_materials_come_from_the_config(GameTestHelper h) {
        List<TunerMaterials.Entry> d = TunerMaterials.entries();
        h.assertTrue(d.size() == 3 && d.get(0).matches(new ItemStack(Items.LAPIS_LAZULI)) && d.get(1).matches(new ItemStack(Items.QUARTZ))
                && d.get(2).matches(new ItemStack(Items.REDSTONE)) && d.stream().allMatch(e -> e.count() == 16), "16 lapis, 16 quartz, 16 redstone by default");
        List<TunerMaterials.Entry> p = TunerMaterials.parse(List.of("minecraft:diamond 2", "#forge:dusts/redstone 8", "minecraft:no_such_item 3",
                "minecraft:quartz 99", "minecraft:iron_ingot", "minecraft:gold_ingot 2"));
        h.assertTrue(p.size() == 4, "only four lines are read: " + p.size());
        h.assertTrue(p.get(0).matches(new ItemStack(Items.DIAMOND)) && p.get(0).count() == 2, "an item and its count");
        h.assertTrue(p.get(1).tag() != null && p.get(1).matches(new ItemStack(Items.REDSTONE)) && p.get(1).count() == 8, "a tag");
        h.assertTrue(p.get(2) == null, "an unknown item keeps its place as an empty slot, so the others keep theirs");
        h.assertTrue(p.get(3).matches(new ItemStack(Items.QUARTZ)) && p.get(3).count() == 64, "counts are kept to 64 at most");
        h.assertTrue(TunerMaterials.parse(List.of("minecraft:iron_ingot")).get(0).count() == 1, "no count means one");

        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        var items = t.getItems();
        h.assertTrue(items.isItemValid(0, compass()) && !items.isItemValid(0, new ItemStack(Items.LAPIS_LAZULI)), "slot 0 takes a compass only");
        h.assertTrue(items.isItemValid(1, new ItemStack(Items.LAPIS_LAZULI)) && !items.isItemValid(1, new ItemStack(Items.QUARTZ)), "each slot its own material");
        h.assertTrue(items.isItemValid(3, new ItemStack(Items.REDSTONE)) && !items.isItemValid(4, new ItemStack(Items.REDSTONE)), "the unused fourth slot takes nothing");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tune_runs_on_the_clock_and_finishes_with_its_materials(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL), stranger = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        t.setLevels(2, 1, 3, 0);
        h.assertTrue(t.checkTune(owner) == TuneCheck.NO_COMPASS, "no compass yet");
        ItemStack c = compass();
        t.getItems().setStackInSlot(0, c);
        c = t.getItems().getStackInSlot(0);
        h.assertTrue(t.checkTune(owner) == TuneCheck.NO_MATERIALS, "no materials yet");
        materials(t, 16);
        t.getItems().setStackInSlot(1, new ItemStack(Items.LAPIS_LAZULI, 20));
        h.assertTrue(t.checkTune(owner) == TuneCheck.NO_FE, "no FE yet");
        t.putEnergy(100_000);
        h.assertTrue(t.checkTune(stranger) == TuneCheck.NOT_ALLOWED && t.startTune(stranger) == TuneCheck.NOT_ALLOWED, "a stranger cannot tune");
        long t0 = 1_000_000L;
        h.assertTrue(t.startTune(owner, t0) == TuneCheck.OK && t.isTuning(), "TUNE starts it");
        h.assertTrue(t.checkTune(owner) == TuneCheck.TUNING, "and it cannot start twice");
        h.assertTrue(t.tuneMs() == 120 * MIN, "two hours at Tune speed 0");
        int[] target = t.target();
        h.assertTrue(target[0] == 2 && target[1] == 1 && target[2] == 3, "it will give 2, 1, 3");

        // a tick while loaded: FE taken, time counted
        int fe = t.energyStored();
        t.advance(t0 + 50);
        h.assertTrue(fe - t.energyStored() == 100 && t.progress() > 0, "one tick takes 100 FE and moves it on");
        // an hour away (chunk unloaded): counted at the Tune speed alone, with only the first second's FE
        fe = t.energyStored();
        t.advance(t0 + 50 + 60 * MIN);
        h.assertTrue(fe - t.energyStored() == 2_000, "only the first second of the gap takes FE: " + (fe - t.energyStored()));
        h.assertTrue(Math.abs(t.progress() - 0.5) < 0.001, "an hour of two is half: " + t.progress());

        // levels bought now are not in this tune
        t.setLevels(5, 5, 5, 0);
        h.assertTrue(t.target()[0] == 2, "the target was fixed at TUNE");

        // no FE: the tune stops while loaded
        t.takeEnergy(t.energyStored());
        double before = t.progress();
        t.advance(t0 + 50 + 60 * MIN + 50);
        h.assertTrue(t.stall() == Stall.NO_FE && t.progress() == before, "no FE, no progress");

        // the second hour passes away from it: done, materials taken, the compass tuned
        t.advance(t0 + 50 + 121 * MIN);
        h.assertFalse(t.isTuning(), "finished");
        h.assertTrue(TotemCompassItem.isTuned(c) && TotemCompassItem.rangeLevel(c, Range.ACROSS) == 2 && TotemCompassItem.rangeLevel(c, Range.DEPTH) == 1
                && TotemCompassItem.level(c) == 3, "the compass has 2, 1, 3 and the tuned mark");
        h.assertTrue(t.getItems().getStackInSlot(1).getCount() == 4 && t.getItems().getStackInSlot(2).isEmpty() && t.getItems().getStackInSlot(3).isEmpty(),
                "16 of each taken");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void tune_speed_and_plasma_shorten_a_tune(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        t.setLevels(1, 0, 0, 4);
        t.getItems().setStackInSlot(0, compass());
        materials(t, 16);
        t.putEnergy(100_000);
        h.assertTrue(t.tuneMs() == 30 * MIN, "Tune speed 4: 30 minutes");
        t.plasmaForTest(500, 3.0);
        h.assertTrue(t.fePerTick() == 300, "plasma raises the FE draw with the speed: " + t.fePerTick());
        long t0 = 5_000_000L;
        t.startTune(owner, t0);
        int fe = t.energyStored();
        t.advance(t0 + 1_000);
        h.assertTrue(fe - t.energyStored() == 6_000, "a second at x3: 20 ticks of 300 FE");
        h.assertTrue(Math.abs(t.progress() - 3_000.0 / (30 * MIN)) < 1e-9, "a second counts three times over");
        // the length follows the Tune speed level as it is, so buying FASTER mid-tune shortens the rest of the tune
        t.setLevels(1, 0, 0, 0);
        h.assertTrue(t.tuneMs() == 120 * MIN, "at level 0 the same tune is two hours long");
        // FE in proportion to the time: 10 ms at 300 FE a tick is 60 FE, not a whole tick's
        t.plasmaForTest(500, 3.0);
        fe = t.energyStored();
        t.advance(t0 + 1_010);
        h.assertTrue(fe - t.energyStored() == 60, "10 ms takes 60 FE: " + (fe - t.energyStored()));
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void taking_the_compass_out_stops_a_tune_and_a_finished_tune_waits_for_its_materials(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        t.setLevels(2, 2, 2, 0);
        t.getItems().setStackInSlot(0, compass());
        materials(t, 16);
        t.putEnergy(100_000);
        t.startTune(owner, 0);
        t.advance(50 * MIN);
        ItemStack out = t.getItems().extractItem(0, 1, false);
        h.assertTrue(!out.isEmpty() && !t.isTuning() && t.progress() == 0, "taken out: the tune is gone");
        h.assertTrue(t.getItems().getStackInSlot(1).getCount() == 16 && !TotemCompassItem.isTuned(out), "and nothing was spent or given");

        t.getItems().setStackInSlot(0, out);
        h.assertTrue(t.startTune(owner, 0) == TuneCheck.OK, "started again");
        ItemStack lapis = t.getItems().getStackInSlot(1).copy();
        t.getItems().setStackInSlot(1, ItemStack.EMPTY);
        t.advance(200 * MIN);
        h.assertTrue(t.isTuning() && t.stall() == Stall.NO_MATERIALS && !TotemCompassItem.isTuned(t.getItems().getStackInSlot(0)),
                "done but waiting for the lapis");
        t.getItems().setStackInSlot(1, lapis);
        t.advance(200 * MIN + 50);
        h.assertTrue(!t.isTuning() && TotemCompassItem.isTuned(t.getItems().getStackInSlot(0)), "with it back, it finishes");
        h.assertTrue(t.checkTune(owner) == TuneCheck.NOTHING_TO_TUNE, "a compass at the tuner's levels has nothing to tune");
        // levels past the top of the config's lists give nothing more, so a compass at the top is never left "to tune"
        t.setLevels(9, 9, 9, 0);
        int[] capped = t.targetFor(t.getItems().getStackInSlot(0));
        h.assertTrue(capped[0] == 6 && capped[1] == 6 && capped[2] == 7, "targets held to 6, 6, 7: " + java.util.Arrays.toString(capped));
        TotemCompassItem.applyTune(t.getItems().getStackInSlot(0), 6, 6, 7);
        h.assertTrue(t.checkTune(owner) == TuneCheck.NOTHING_TO_TUNE, "a compass at the top has nothing to tune");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void pipes_only_bring_materials_and_take_nothing_out(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        IItemHandler pipe = t.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
        h.assertTrue(pipe.getSlots() == 4, "the four material slots only");
        h.assertTrue(pipe.insertItem(0, new ItemStack(Items.LAPIS_LAZULI, 10), false).isEmpty() && t.getItems().getStackInSlot(1).getCount() == 10,
                "lapis goes in");
        ItemStack c = compass();
        h.assertTrue(pipe.insertItem(1, c, false).getCount() == 1 && t.getItems().getStackInSlot(0).isEmpty(), "a compass does not");
        h.assertTrue(pipe.extractItem(0, 5, false).isEmpty() && t.getItems().getStackInSlot(1).getCount() == 10, "nothing comes out");
        var energy = t.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH).resolve().orElseThrow();
        h.assertTrue(energy.receiveEnergy(5_000, false) == 5_000 && t.energyStored() == 5_000, "FE goes in from any side");
        h.assertTrue(energy.extractEnergy(1_000, false) == 0, "and does not come out");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_old_upgraded_compass_hands_its_upgrades_to_a_tuner_once(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        t.setLevels(2, 4, 1, 0);
        ItemStack old = compass();
        old.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 4);
        old.getOrCreateTag().putInt(TotemCompassItem.RANGE_DEPTH, 2);
        old.getOrCreateTag().putInt(TotemCompassItem.LEVEL, 3);
        h.assertTrue(TotemCompassItem.upgradedBeforeTuner(old), "an old upgraded compass");
        h.assertFalse(TotemCompassItem.upgradedBeforeTuner(compass()), "a new compass is not");
        t.getItems().setStackInSlot(0, old);
        ItemStack in = t.getItems().getStackInSlot(0);
        h.assertTrue(t.across() == 4 && t.depth() == 4 && t.cooldown() == 3 && t.speed() == 0, "the tuner keeps the higher of each: 4, 4, 3");
        int[] hand = t.handover();
        h.assertTrue(hand != null && hand[0] == 4 && hand[1] == 2 && hand[2] == 3, "and says what came over");
        h.assertTrue(TotemCompassItem.isTuned(in) && TotemCompassItem.rangeLevel(in, Range.ACROSS) == 4 && TotemCompassItem.level(in) == 3,
                "the compass keeps its levels and counts as tuned");
        h.assertFalse(TotemCompassItem.upgradedBeforeTuner(in), "so it never hands over again");

        ItemStack again = t.getItems().extractItem(0, 1, false);
        CompassTunerBlockEntity other = tuner(h, new BlockPos(3, 1, 1), owner);
        other.getItems().setStackInSlot(0, again);
        h.assertTrue(other.across() == 0 && other.handover() == null, "a second tuner gets nothing from it");

        ItemStack small = compass();
        small.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 1);
        CompassTunerBlockEntity big = tuner(h, new BlockPos(5, 1, 1), owner);
        big.setLevels(5, 5, 5, 0);
        big.getItems().setStackInSlot(0, small);
        h.assertTrue(big.across() == 5 && !TotemCompassItem.upgradedBeforeTuner(big.getItems().getStackInSlot(0)),
                "a lower old compass changes nothing, and has used its one handover");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_tuner_screen_buys_and_tunes_for_the_team_only(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL), stranger = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos rel = new BlockPos(1, 1, 1);
        CompassTunerBlockEntity t = tuner(h, rel, owner);
        Vec3 at = Vec3.atCenterOf(h.absolutePos(rel));
        owner.setPos(at.x, at.y + 1, at.z);
        stranger.setPos(at.x, at.y + 1, at.z);
        CompassTunerMenu menu = new CompassTunerMenu(1, owner.getInventory(), t);
        CompassTunerMenu theirs = new CompassTunerMenu(2, stranger.getInventory(), t);
        h.assertTrue(menu.stillValid(owner) && !theirs.stillValid(stranger), "the owner may keep it open, a stranger may not");
        owner.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 10));
        h.assertTrue(menu.clickMenuButton(owner, Ladder.SPEED.ordinal()) && t.speed() == 1 && menu.level(Ladder.SPEED) == 1, "+ on Tune speed buys it");
        h.assertTrue(menu.check() == TuneCheck.NO_COMPASS, "the screen knows why TUNE cannot start");
        t.getItems().setStackInSlot(0, compass());
        t.setLevels(1, 0, 0, 1);
        materials(t, 16);
        t.putEnergy(100_000);
        stranger.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 10));
        h.assertFalse(theirs.clickMenuButton(stranger, CompassTunerMenu.BTN_TUNE) || theirs.clickMenuButton(stranger, 0), "a stranger's keys do nothing");
        h.assertTrue(menu.clickMenuButton(owner, CompassTunerMenu.BTN_TUNE) && t.isTuning() && menu.tuning(), "TUNE starts a tune");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tuner_saves_its_owner_levels_and_tune(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassTunerBlockEntity t = tuner(h, new BlockPos(1, 1, 1), owner);
        t.setLevels(3, 2, 1, 2);
        t.getItems().setStackInSlot(0, compass());
        materials(t, 16);
        t.putEnergy(50_000);
        t.startTune(owner, 0);
        t.advance(20 * MIN);
        CompoundTag tag = t.saveWithoutMetadata();
        CompassTunerBlockEntity copy = new CompassTunerBlockEntity(t.getBlockPos(), t.getBlockState());
        copy.load(tag);
        h.assertTrue(copy.getOwner().equals(owner.getUUID()) && copy.across() == 3 && copy.depth() == 2 && copy.cooldown() == 1 && copy.speed() == 2,
                "owner and levels");
        h.assertTrue(copy.isTuning() && Math.abs(copy.progress() - t.progress()) < 1e-12 && copy.target()[0] == 3, "and the running tune");
        h.assertTrue(copy.energyStored() == t.energyStored() && copy.getItems().getStackInSlot(2).getCount() == 16, "FE and slots");
        h.succeed();
    }
}
