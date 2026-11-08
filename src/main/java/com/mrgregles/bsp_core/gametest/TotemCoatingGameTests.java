package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.shield.TotemCoating;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** GameTests for the Totem Radiation Coating on the radiation shielding set: applying it, doors, and keeping it through a break. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TotemCoatingGameTests {
    private static final String EMPTY = "empty", BATCH = "totem_coating";

    private static BlockHitResult hit(GameTestHelper h, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
    }

    private static InteractionResult coat(GameTestHelper h, ServerPlayer player, BlockPos rel) {
        ItemStack tins = player.getItemInHand(InteractionHand.MAIN_HAND);
        return tins.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(h, rel)));
    }

    private static final BlockPos CENTRE = new BlockPos(12, 4, 12);

    /** A hollow cube of {@code state} at Chebyshev distance {@code d} round {@code centre}. */
    private static void shell(GameTestHelper h, BlockPos centre, int d, BlockState state) {
        for (int x = -d; x <= d; x++) {
            for (int y = -d; y <= d; y++) {
                for (int z = -d; z <= d; z++) {
                    if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) == d) {
                        h.setBlock(centre.offset(x, y, z), state);
                    }
                }
            }
        }
    }

    private static int layers(GameTestHelper h) {
        return com.mrgregles.bsp_core.totem.TotemRadiation.countLayers(h.getLevel(), h.absolutePos(CENTRE), 4);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void coated_layers_count_the_thinnest_wall_round_a_totem(GameTestHelper h) {
        BlockState plain = ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState(), coated = plain.setValue(TotemCoating.COATED, true);
        h.assertTrue(layers(h) == 0, "nothing round it: no layers");
        shell(h, CENTRE, 2, plain);
        h.assertTrue(layers(h) == 0, "plain shielding does not count");
        shell(h, CENTRE, 2, coated);
        h.assertTrue(layers(h) == 1, "a coated box all round: 1 layer: " + layers(h));
        shell(h, CENTRE, 3, coated);
        h.assertTrue(layers(h) == 2, "a second box round it: 2 layers: " + layers(h));
        h.setBlock(CENTRE.offset(0, 2, 0), plain);
        h.assertTrue(layers(h) == 1, "one plain block in the inner roof leaves that line with 1: " + layers(h));
        h.setBlock(CENTRE.offset(0, 2, 0), coated);
        BlockState door = ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState().setValue(TotemCoating.COATED, true);
        h.setBlock(CENTRE.offset(2, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(CENTRE.offset(2, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        h.assertTrue(layers(h) == 2, "a shut coated door in the inner wall counts: " + layers(h));
        h.setBlock(CENTRE.offset(2, 0, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, true));
        h.setBlock(CENTRE.offset(2, 1, 0), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).setValue(DoorBlock.OPEN, true));
        h.assertTrue(layers(h) == 1, "open, it does not: " + layers(h));
        BlockPos nowhere = h.absolutePos(CENTRE).offset(3_000_000, 0, 3_000_000);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemRadiation.countLayers(h.getLevel(), nowhere, 4) == -1,
                "round a spot whose chunks are not loaded there is no count, and nothing is loaded for it");
        h.assertTrue(!h.getLevel().hasChunkAt(nowhere), "the chunk was not loaded");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void only_whole_coated_walls_count_as_layers(GameTestHelper h) {
        BlockState coated = ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState().setValue(TotemCoating.COATED, true);
        // coated blocks round the totem with gaps between them: every block of a box at distance 2 except the middle of each face
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    int edge = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
                    boolean faceMiddle = (x == 0 ? 1 : 0) + (y == 0 ? 1 : 0) + (z == 0 ? 1 : 0) == 2;
                    if (edge == 2 && !faceMiddle) {
                        h.setBlock(CENTRE.offset(x, y, z), coated);
                    }
                }
            }
        }
        h.assertTrue(layers(h) == 0, "a box with an opening in each face is no layer: " + layers(h));
        h.setBlock(CENTRE.offset(2, 0, 0), coated);
        h.setBlock(CENTRE.offset(-2, 0, 0), coated);
        h.setBlock(CENTRE.offset(0, 2, 0), coated);
        h.setBlock(CENTRE.offset(0, -2, 0), coated);
        h.setBlock(CENTRE.offset(0, 0, 2), coated);
        h.assertTrue(layers(h) == 0, "one opening left is still no layer: " + layers(h));
        h.setBlock(CENTRE.offset(0, 0, -2), coated);
        h.assertTrue(layers(h) == 1, "closed, it is one layer: " + layers(h));
        // a thicker wall on one side does not make up for a thin one elsewhere
        for (int y = -3; y <= 3; y++) {
            for (int z = -3; z <= 3; z++) {
                h.setBlock(CENTRE.offset(3, y, z), coated);
                h.setBlock(CENTRE.offset(4, y, z), coated);
            }
        }
        h.assertTrue(layers(h) == 1, "extra walls on one side only: still 1 (the thinnest way out): " + layers(h));
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "radiation_scan")
    public void a_placed_totem_counts_its_coating_into_the_ledger(GameTestHelper h) {
        int radius = com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.get();
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockState coated = ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState().setValue(TotemCoating.COATED, true);
        com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.set(4); // inside this test's own space
        com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity totem;
        try {
            shell(h, CENTRE, 2, coated);
            shell(h, CENTRE, 3, coated);
            h.setBlock(CENTRE, ModBlocks.SHATTER_TOTEM.get());
            totem = (com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity) h.getBlockEntity(CENTRE);
            totem.loadFromStack(com.mrgregles.bsp_core.totem.TotemMint.fresh(owner));
        } catch (RuntimeException e) {
            com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.set(radius); // never leave the test world with a radius of 4
            throw e;
        }
        var at = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(CENTRE));
        var ledger = com.mrgregles.bsp_core.data.TotemLedger.get(h.getLevel().getServer());
        Runnable done = () -> {
            com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.set(radius);
            java.util.UUID id = totem.totemId();
            totem.setOwner(null);
            h.getLevel().setBlock(h.absolutePos(CENTRE), Blocks.AIR.defaultBlockState(), 3);
            if (id != null) {
                ledger.forget(id);
            }
        };
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(ledger.coatedLayers(at) == 2, "within its first ticks the totem counts 2 coated layers into the ledger: " + ledger.coatedLayers(at));
                totem.setTier(3);
                totem.setUpgradeLevel(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.OUTPUT, 10);
                h.assertTrue(com.mrgregles.bsp_core.totem.TotemRadiation.level(ledger, at) == 13,
                        "the ledger has the totem's level: 3 tier steps and 10 Output levels: " + com.mrgregles.bsp_core.totem.TotemRadiation.level(ledger, at));
                h.assertTrue(com.mrgregles.bsp_core.totem.TotemRadiation.seenAt(ledger, at) == com.mrgregles.bsp_core.totem.TotemRadiation.seenAt(13, 2),
                        "and the compass's seen-at distance takes the 2 layers off, kept through the tier change");
            } finally {
                done.run();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_coating_tin_coats_a_shielding_panel_once(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TOTEM_RADIATION_COATING.get(), 3));
        BlockPos panel = new BlockPos(1, 1, 1), window = new BlockPos(2, 1, 1), stone = new BlockPos(3, 1, 1);
        h.setBlock(panel, ModBlocks.RADIATION_SHIELDING_PANEL.get());
        h.setBlock(window, ModBlocks.RADIATION_SHIELDING_WINDOW.get());
        h.setBlock(stone, Blocks.STONE);
        h.assertTrue(!TotemCoating.coated(h.getBlockState(panel)), "a new panel is not coated");
        coat(h, player, panel);
        h.assertTrue(TotemCoating.coated(h.getBlockState(panel)), "the tin coats the panel");
        h.assertTrue(player.getMainHandItem().getCount() == 2, "and one tin is used: " + player.getMainHandItem().getCount());
        h.assertTrue(coat(h, player, panel) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 2, "a coated panel takes no second tin");
        coat(h, player, window);
        h.assertTrue(TotemCoating.coated(h.getBlockState(window)) && player.getMainHandItem().getCount() == 1, "a window takes the coating too");
        h.assertTrue(coat(h, player, stone) == InteractionResult.FAIL && player.getMainHandItem().getCount() == 1, "stone does not take it");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_door_is_coated_whole_with_one_tin_and_does_not_open(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TOTEM_RADIATION_COATING.get(), 2));
        BlockPos lower = new BlockPos(1, 1, 1), upper = lower.above();
        BlockState door = ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState();
        h.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(upper, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        InteractionResult used = h.getBlockState(upper).use(h.getLevel(), player, InteractionHand.MAIN_HAND, hit(h, upper));
        h.assertTrue(used == InteractionResult.PASS && !h.getBlockState(upper).getValue(DoorBlock.OPEN), "with a tin in hand the door hands the click to the tin and stays shut");
        coat(h, player, upper);
        h.assertTrue(TotemCoating.coated(h.getBlockState(lower)) && TotemCoating.coated(h.getBlockState(upper)), "coating the upper half coats both halves");
        h.assertTrue(player.getMainHandItem().getCount() == 1, "for one tin: " + player.getMainHandItem().getCount());
        h.assertTrue(coat(h, player, lower) == InteractionResult.FAIL, "the lower half is coated already");
        h.assertTrue(!h.getBlockState(lower).getValue(DoorBlock.OPEN), "and the door never opened");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_coated_block_drops_coated_and_is_placed_coated(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos panel = new BlockPos(1, 1, 1), lower = new BlockPos(3, 1, 1);
        h.setBlock(panel, ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState().setValue(TotemCoating.COATED, true));
        BlockState door = ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState().setValue(TotemCoating.COATED, true);
        h.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        List<ItemStack> panelDrops = Block.getDrops(h.getBlockState(panel), h.getLevel(), h.absolutePos(panel), null);
        List<ItemStack> doorDrops = Block.getDrops(h.getBlockState(lower), h.getLevel(), h.absolutePos(lower), null);
        h.assertTrue(panelDrops.size() == 1 && TotemCoating.coated(panelDrops.get(0)), "a coated panel drops a coated panel: " + panelDrops);
        h.assertTrue(doorDrops.size() == 1 && TotemCoating.coated(doorDrops.get(0)), "a coated door drops a coated door: " + doorDrops);
        List<ItemStack> plain = Block.getDrops(ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState(), h.getLevel(), h.absolutePos(panel), null);
        h.assertTrue(plain.size() == 1 && plain.get(0).getTag() == null, "a plain panel drops a plain one with no tag, so it stacks with crafted ones: " + plain);

        BlockPos target = new BlockPos(5, 1, 1);
        h.setBlock(target.below(), Blocks.STONE);
        ItemStack coated = panelDrops.get(0).copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, coated);
        BlockPos below = h.absolutePos(target.below());
        ((BlockItem) coated.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, coated,
                new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false)));
        h.assertTrue(h.getBlockState(target).is(ModBlocks.RADIATION_SHIELDING_PANEL.get()) && TotemCoating.coated(h.getBlockState(target)),
                "placed again, it is coated: " + h.getBlockState(target));

        BlockPos doorAt = new BlockPos(7, 1, 1);
        h.setBlock(doorAt.below(), Blocks.STONE);
        ItemStack coatedDoor = doorDrops.get(0).copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, coatedDoor);
        BlockPos under = h.absolutePos(doorAt.below());
        ((BlockItem) coatedDoor.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, coatedDoor,
                new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), Direction.UP, under, false)));
        h.assertTrue(TotemCoating.coated(h.getBlockState(doorAt)) && TotemCoating.coated(h.getBlockState(doorAt.above())),
                "a coated door placed again is coated in both halves: " + h.getBlockState(doorAt) + " / " + h.getBlockState(doorAt.above()));
        h.succeed();
    }
}
