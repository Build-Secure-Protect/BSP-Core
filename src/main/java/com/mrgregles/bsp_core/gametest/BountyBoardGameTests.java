package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bounty.BountyBoardBlock;
import com.mrgregles.bsp_core.bounty.BountyBoardBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** GameTests for the Bounty Board block (task 5). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyBoardGameTests {
    private static final String EMPTY = "empty";

    /** A board facing {@code facing} with its controller at {@code rel}; returns the controller's absolute position. */
    private static BlockPos board(GameTestHelper h, BlockPos rel, Direction facing) {
        h.setBlock(rel, ModBlocks.BOUNTY_BOARD.get().defaultBlockState().setValue(BountyBoardBlock.FACING, facing));
        BlockPos c = h.absolutePos(rel);
        BountyBoardBlock.placeParts(h.getLevel(), c, facing);
        return c;
    }

    private static boolean standing(ServerLevel level, BlockPos c, Direction facing) {
        return BountyBoardBlock.parts(c, facing).stream().allMatch(p -> level.getBlockState(p).getBlock() instanceof BountyBoardBlock);
    }

    private static boolean gone(ServerLevel level, BlockPos c, Direction facing) {
        return BountyBoardBlock.parts(c, facing).stream().noneMatch(p -> level.getBlockState(p).getBlock() instanceof BountyBoardBlock);
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos c) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(10), e -> e.getItem().is(ModItems.BOUNTY_BOARD.get()));
    }

    private static int dropCount(ServerLevel level, BlockPos c) {
        return drops(level, c).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_board_stands_as_four_wooden_parts_facing_the_placer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Direction east = Direction.EAST;
        BlockPos c = board(h, new BlockPos(4, 1, 4), east);
        List<BlockPos> parts = BountyBoardBlock.parts(c, east);
        h.assertTrue(parts.get(2).equals(c.south()) && parts.get(1).equals(c.above()), "facing east, the wing is to the south");
        for (BlockPos p : parts) {
            BlockState st = level.getBlockState(p);
            h.assertTrue(st.getValue(BountyBoardBlock.FACING) == east && BountyBoardBlock.controller(st, p).equals(c), "every part faces east and finds the controller");
            h.assertTrue(st.is(BlockTags.MINEABLE_WITH_AXE), "an axe is the tool for it");
        }
        h.assertTrue(level.getBlockEntity(c) instanceof BountyBoardBlockEntity && parts.stream().skip(1).allMatch(p -> level.getBlockEntity(p) == null),
                "only the controller has a block entity");
        h.assertTrue(level.getBlockState(c).getLightEmission(level, c) == 7, "the lantern gives light");
        // the outline follows the facing: facing east, the lower parts reach from x 6 to 14 (the shelf forward, the boards behind)
        AABB box = level.getBlockState(c).getShape(level, c, CollisionContext.empty()).bounds();
        h.assertTrue(Math.abs(box.minX - 6 / 16.0) < 1e-6 && Math.abs(box.maxX - 14 / 16.0) < 1e-6 && box.minZ == 0 && box.maxZ == 1, "the shape turns with it: " + box);
        BlockPos free = h.absolutePos(new BlockPos(10, 1, 10));
        h.assertTrue(BountyBoardBlock.roomFor(level, free, Direction.NORTH), "room in open air");
        h.setBlock(new BlockPos(11, 2, 10), Blocks.STONE);
        h.assertTrue(!BountyBoardBlock.roomFor(level, free, Direction.NORTH), "a stone where a part goes: no room");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void broken_by_a_player_it_drops_one_board_whichever_part_and_none_in_creative(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Direction north = Direction.NORTH;
        BlockPos c = board(h, new BlockPos(3, 1, 3), north);
        drops(level, c).forEach(e -> e.discard());
        ServerPlayer survivor = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos wingTop = BountyBoardBlock.parts(c, north).get(3);
        BlockState st = level.getBlockState(wingTop);
        st.onDestroyedByPlayer(level, wingTop, survivor, true, level.getFluidState(wingTop));
        h.assertTrue(gone(level, c, north), "all four parts went together");
        h.assertTrue(dropCount(level, c) == 1, "one Bounty Board dropped: " + dropCount(level, c));
        drops(level, c).forEach(e -> e.discard());
        // in creative: nothing drops
        BlockPos c2 = board(h, new BlockPos(3, 1, 3), north);
        ServerPlayer builder = MockPlayers.serverPlayer(h, GameType.CREATIVE);
        level.getBlockState(c2).onDestroyedByPlayer(level, c2, builder, false, level.getFluidState(c2));
        h.assertTrue(gone(level, c2, north) && dropCount(level, c2) == 0, "broken in creative: gone, nothing dropped");
        // one part removed by a command or world edit: the others go too, nothing drops
        BlockPos c3 = board(h, new BlockPos(3, 1, 3), north);
        h.setBlock(new BlockPos(3, 2, 3), Blocks.AIR);
        h.assertTrue(gone(level, c3, north) && dropCount(level, c3) == 0, "a part taken away takes the rest");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty", timeoutTicks = 40)
    public void an_explosion_drops_one_board_not_four(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Direction north = Direction.NORTH;
        BlockPos c = board(h, new BlockPos(6, 2, 6), north);
        drops(level, c).forEach(e -> e.discard());
        Vec3 at = Vec3.atCenterOf(c.above()).add(0.5, 0, -1);
        level.explode(null, at.x, at.y, at.z, 3.0F, Level.ExplosionInteraction.TNT);
        h.runAfterDelay(2, () -> {
            h.assertTrue(gone(level, c, north), "the board is blown up");
            int n = dropCount(level, c);
            drops(level, c).forEach(e -> e.discard());
            h.assertTrue(n == 1, "one Bounty Board dropped: " + n);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_shelf_keys_open_their_page_whichever_way_it_faces(GameTestHelper h) {
        BlockPos c = new BlockPos(100, 64, 100);
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            for (BountyBoardBlock.Key key : BountyBoardBlock.Key.values()) {
                h.assertTrue(BountyBoardBlock.keyAt(facing, c, world(c, facing, (key.x0 + key.x1) / 2, 1.8, 2.0)) == key, key + " key, facing " + facing);
            }
            h.assertTrue(BountyBoardBlock.keyAt(facing, c, world(c, facing, 10, 16, 6.8)) == BountyBoardBlock.Key.BOARD, "a poster opens BOARD, facing " + facing);
            h.assertTrue(BountyBoardBlock.keyAt(facing, c, world(c, facing, 24, 8, 6.8)) == BountyBoardBlock.Key.BOARD, "so does the other column, facing " + facing);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void players_craft_it_from_dark_oak_glass_a_circuit_and_gold(GameTestHelper h) {
        var recipe = h.getLevel().getServer().getRecipeManager().byKey(new ResourceLocation(BSPCore.MODID, "bounty_board"));
        h.assertTrue(recipe.isPresent(), "the recipe is loaded");
        var r = recipe.get();
        h.assertTrue(r.getResultItem(h.getLevel().registryAccess()).is(ModItems.BOUNTY_BOARD.get()), "it makes a Bounty Board");
        var needs = r.getIngredients().stream().filter(i -> !i.isEmpty()).map(i -> i.getItems()[0].getItem()).toList();
        h.assertTrue(needs.size() == 9 && needs.contains(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, "basic_control_circuit"))) && needs.contains(net.minecraft.world.item.Items.GOLD_INGOT)
                && needs.stream().filter(i -> i == net.minecraft.world.item.Items.DARK_OAK_SLAB).count() == 3, "slabs, panes, circuit, logs and gold: " + needs);
        h.assertTrue(!(ModItems.BOUNTY_BOARD.get() instanceof com.mrgregles.bsp_core.admin.AdminBlockItem), "anyone may place it");
        h.succeed();
    }

    /** A point given in the board's model pixels (front north), in world coordinates for a board facing {@code facing}. */
    private static Vec3 world(BlockPos c, Direction facing, double mx, double my, double mz) {
        double wx, wz;
        switch (facing) {
            case EAST -> {
                wx = 16 - mz;
                wz = mx;
            }
            case SOUTH -> {
                wx = 16 - mx;
                wz = 16 - mz;
            }
            case WEST -> {
                wx = mz;
                wz = 16 - mx;
            }
            default -> {
                wx = mx;
                wz = mz;
            }
        }
        return new Vec3(c.getX() + wx / 16, c.getY() + my / 16, c.getZ() + wz / 16);
    }
}
