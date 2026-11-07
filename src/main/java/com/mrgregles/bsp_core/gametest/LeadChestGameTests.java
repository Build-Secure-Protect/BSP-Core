package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.shield.LeadLinedChestBlockEntity;
import com.mrgregles.bsp_core.shield.LeadLinedChestMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.shield.LeadLinedChestBlock;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * GameTests for the Lead-Lined Chest: it is never a {@link Container} (so Voltaic's radiation scan cannot see it), it is a chest to
 * claim mods (a menu provider), it keeps its items through a save, hoppers fill and empty it through the item-handler capability,
 * breaking it spills its contents, shift-click moves stacks both ways, and comparators read it. Two side by side join into a double
 * chest as vanilla chests do (not when sneak-placed or facing another way), reach 54 slots in vanilla's order from either half, spill
 * from a full half into the other, and split cleanly when a half is broken.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class LeadChestGameTests {
    private static final String EMPTY = "empty", BATCH = "lead_chest";

    private static LeadLinedChestBlockEntity chest(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, ModBlocks.LEAD_LINED_CHEST.get());
        return (LeadLinedChestBlockEntity) h.getBlockEntity(pos);
    }

    /** Places a chest at {@code rel} the way a player would: standing facing {@code yaw}, clicking the top of a stone below it. */
    private static void place(GameTestHelper h, ServerPlayer p, BlockPos rel, float yaw, boolean sneak) {
        h.setBlock(rel.below(), Blocks.STONE);
        BlockPos abs = h.absolutePos(rel);
        p.setPos(abs.getX() + 0.5, abs.getY() + 3, abs.getZ() + 0.5);
        p.setYRot(yaw);
        p.setShiftKeyDown(sneak);
        ItemStack stack = new ItemStack(ModItems.LEAD_LINED_CHEST.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP, abs.below(), false);
        ((BlockItem) stack.getItem()).place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack, hit));
        h.assertBlockPresent(ModBlocks.LEAD_LINED_CHEST.get(), rel);
    }

    private static ChestType type(GameTestHelper h, BlockPos rel) {
        return h.getBlockState(rel).getValue(LeadLinedChestBlock.TYPE);
    }

    private static IItemHandler cap(GameTestHelper h, BlockPos rel) {
        return h.getBlockEntity(rel).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /** Two chests facing north at (1,1,1) and (2,1,1): the first ends LEFT, the second RIGHT (its partner is west), as vanilla. */
    private static ServerPlayer pair(GameTestHelper h, BlockPos a, BlockPos b) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        place(h, p, a, 0, false); // yaw 0 faces south, so the chest faces north
        place(h, p, b, 0, false);
        return p;
    }

    private static int count(IItemHandler items, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < items.getSlots(); i++) {
            if (items.getStackInSlot(i).is(item)) {
                n += items.getStackInSlot(i).getCount();
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_is_not_a_container(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        h.assertFalse(be instanceof Container, "a Container is what Voltaic's radiation scan counts: the chest must never be one");
        for (Direction side : Direction.values()) {
            IItemHandler items = be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
            h.assertTrue(items != null && items.getSlots() == 27, "the chest's 27 slots are reached from the " + side + " side");
        }
        h.assertTrue(be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(), "and with no side");
        BlockPos abs = h.absolutePos(pos);
        h.assertTrue(h.getLevel().getBlockState(abs).getMenuProvider(h.getLevel(), abs) != null,
                "the block has a menu provider, so claim mods (Region Guard's chest-access flag) treat it as a chest");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_keeps_items_through_a_save(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        be.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 64));
        be.items().setStackInSlot(26, new ItemStack(Items.DIAMOND, 3));
        CompoundTag saved = be.saveWithFullMetadata();
        BlockState state = h.getLevel().getBlockState(h.absolutePos(pos));
        BlockEntity copy = BlockEntity.loadStatic(h.absolutePos(pos), state, saved);
        h.assertTrue(copy instanceof LeadLinedChestBlockEntity, "the saved chest loads as a lead-lined chest");
        LeadLinedChestBlockEntity back = (LeadLinedChestBlockEntity) copy;
        h.assertTrue(back.items().getSlots() == 27, "it still has 27 slots");
        h.assertTrue(back.items().getStackInSlot(0).is(Items.IRON_INGOT) && back.items().getStackInSlot(0).getCount() == 64, "the first slot came back");
        h.assertTrue(back.items().getStackInSlot(26).is(Items.DIAMOND) && back.items().getStackInSlot(26).getCount() == 3, "the last slot came back");
        // a save with a different slot count cannot resize the chest
        CompoundTag odd = saved.copy();
        odd.getCompound("Items").putInt("Size", 54);
        back.load(odd);
        h.assertTrue(back.items().getSlots() == 27, "a saved size of 54 leaves the chest at 27 slots");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void lead_chest_hopper_fills_it(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1), hopper = pos.above();
        LeadLinedChestBlockEntity be = chest(h, pos);
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(hopper)).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.succeedWhen(() -> h.assertTrue(count(be.items(), Items.COBBLESTONE) == 5, "the hopper above pushes all five cobblestone into the chest"));
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void lead_chest_hopper_empties_it(GameTestHelper h) {
        BlockPos hopper = new BlockPos(1, 1, 1), pos = hopper.above();
        h.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        LeadLinedChestBlockEntity be = chest(h, pos);
        be.items().setStackInSlot(4, new ItemStack(Items.REDSTONE, 3));
        h.succeedWhen(() -> {
            h.assertTrue(count(be.items(), Items.REDSTONE) == 0, "the hopper below pulls the redstone out of the chest");
            HopperBlockEntity hb = (HopperBlockEntity) h.getBlockEntity(hopper);
            int n = 0;
            for (int i = 0; i < hb.getContainerSize(); i++) {
                n += hb.getItem(i).is(Items.REDSTONE) ? hb.getItem(i).getCount() : 0;
            }
            h.assertTrue(n == 3, "all three are in the hopper");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_spills_its_contents_when_broken(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        be.items().setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 20));
        be.items().setStackInSlot(13, new ItemStack(Items.GOLD_INGOT, 7));
        BlockPos abs = h.absolutePos(pos);
        h.getLevel().destroyBlock(abs, true);
        int gold = 0, chests = 0;
        for (ItemEntity e : h.getLevel().getEntities(EntityType.ITEM, new AABB(abs).inflate(2), e -> true)) {
            gold += e.getItem().is(Items.GOLD_INGOT) ? e.getItem().getCount() : 0;
            chests += e.getItem().is(ModBlocks.LEAD_LINED_CHEST.get().asItem()) ? e.getItem().getCount() : 0;
            e.discard();
        }
        h.assertTrue(gold == 27, "all 27 gold ingots are spilled (found " + gold + ")");
        h.assertTrue(chests == 1, "the chest drops itself (found " + chests + ")");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_shift_click_moves_both_ways(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(pos);
        player.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() + 1.5);
        player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 40)); // first slot of the main inventory
        LeadLinedChestMenu menu = (LeadLinedChestMenu) be.createMenu(1, player.getInventory(), player);
        h.assertTrue(menu.slots.size() == 27 + 36, "27 chest slots and the player's 36");
        h.assertTrue(menu.slots.get(27).y == LeadLinedChestMenu.inventoryY(3) && LeadLinedChestMenu.inventoryY(3) == 85, "the inventory starts at y 85, as a vanilla chest");
        h.assertTrue(menu.stillValid(player), "a player beside the chest may use it");
        menu.quickMoveStack(player, 27); // menu slot 27 is inventory slot 9
        h.assertTrue(count(be.items(), Items.EMERALD) == 40 && player.getInventory().getItem(9).isEmpty(), "shift-click puts the stack in the chest");
        int slot = -1;
        for (int i = 0; i < 27; i++) {
            if (be.items().getStackInSlot(i).is(Items.EMERALD)) {
                slot = i;
            }
        }
        menu.quickMoveStack(player, slot);
        h.assertTrue(count(be.items(), Items.EMERALD) == 0 && player.getInventory().countItem(Items.EMERALD) == 40, "shift-click takes it back out");
        player.setPos(abs.getX() + 20.5, abs.getY(), abs.getZ() + 0.5);
        h.assertFalse(menu.stillValid(player), "a player 20 blocks away may not");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_feeds_a_comparator(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        BlockPos abs = h.absolutePos(pos);
        BlockState state = h.getLevel().getBlockState(abs);
        h.assertTrue(state.getAnalogOutputSignal(h.getLevel(), abs) == 0, "an empty chest gives no signal");
        be.items().setStackInSlot(0, new ItemStack(Items.STONE, 64));
        int one = state.getAnalogOutputSignal(h.getLevel(), abs);
        for (int i = 0; i < 27; i++) {
            be.items().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        h.assertTrue(one >= 1 && state.getAnalogOutputSignal(h.getLevel(), abs) == 15, "one stack gives a signal, a full chest gives 15");
        h.succeed();
    }

    // ------------------------------------------------------------------ double chest

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chests_side_by_side_join(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        ServerPlayer p = pair(h, a, b);
        h.assertTrue(type(h, a) == ChestType.LEFT && type(h, b) == ChestType.RIGHT, "the pair joins: the first LEFT, the second RIGHT (" + type(h, a) + ", " + type(h, b) + ")");
        h.assertTrue(cap(h, a).getSlots() == 54 && cap(h, b).getSlots() == 54, "either half reaches all 54 slots");
        LeadLinedChestBlockEntity be = (LeadLinedChestBlockEntity) h.getBlockEntity(a);
        LeadLinedChestMenu menu = (LeadLinedChestMenu) be.createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.rows() == 6 && menu.slots.size() == 54 + 36, "the screen has six rows");
        h.assertTrue(menu.slots.get(54).y == LeadLinedChestMenu.inventoryY(6) && LeadLinedChestMenu.inventoryY(6) == 139, "the inventory starts at y 139, as a vanilla double chest");
        h.assertTrue(be.getDisplayName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("container.bsp_core.lead_lined_chest_double"), "it is called a Large Lead-Lined Chest");
        h.assertFalse(be instanceof Container, "joined, it is still no Container");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_sneak_placed_stays_single(GameTestHelper h) {
        BlockPos a = new BlockPos(2, 1, 1), b = new BlockPos(3, 1, 1), c = new BlockPos(1, 1, 1);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        place(h, p, a, 0, false);
        place(h, p, b, 0, true);
        h.assertTrue(type(h, a) == ChestType.SINGLE && type(h, b) == ChestType.SINGLE, "a chest placed while sneaking stays single");
        place(h, p, c, 90, false); // yaw 90 faces west, so this chest faces east, across the other's facing
        h.assertTrue(type(h, a) == ChestType.SINGLE && type(h, c) == ChestType.SINGLE, "chests facing different ways do not join");
        h.assertTrue(cap(h, a).getSlots() == 27 && cap(h, b).getSlots() == 27 && cap(h, c).getSlots() == 27, "each keeps 27 slots");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_double_chest_slots_in_vanilla_order(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        ServerPlayer p = pair(h, a, b);
        ((LeadLinedChestBlockEntity) h.getBlockEntity(b)).items().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        ((LeadLinedChestBlockEntity) h.getBlockEntity(a)).items().setStackInSlot(0, new ItemStack(Items.EMERALD));
        for (BlockPos from : new BlockPos[] {a, b}) {
            IItemHandler all = cap(h, from);
            h.assertTrue(all.getStackInSlot(0).is(Items.DIAMOND) && all.getStackInSlot(27).is(Items.EMERALD),
                    "from either half the RIGHT half's slots come first, as vanilla");
        }
        LeadLinedChestMenu menu = (LeadLinedChestMenu) ((LeadLinedChestBlockEntity) h.getBlockEntity(a)).createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.slots.get(0).getItem().is(Items.DIAMOND) && menu.slots.get(27).getItem().is(Items.EMERALD), "the screen shows them in the same order");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void lead_double_chest_hopper_spills_into_the_other_half(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        pair(h, a, b);
        LeadLinedChestBlockEntity first = (LeadLinedChestBlockEntity) h.getBlockEntity(b), second = (LeadLinedChestBlockEntity) h.getBlockEntity(a);
        for (int i = 0; i < 27; i++) {
            first.items().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        h.setBlock(b.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        ((HopperBlockEntity) h.getBlockEntity(b.above())).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        h.succeedWhen(() -> h.assertTrue(count(second.items(), Items.COBBLESTONE) == 5,
                "a hopper on the full half pushes into the other half"));
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_double_chest_splits_when_a_half_breaks(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        ServerPlayer p = pair(h, a, b);
        LeadLinedChestBlockEntity left = (LeadLinedChestBlockEntity) h.getBlockEntity(a), right = (LeadLinedChestBlockEntity) h.getBlockEntity(b);
        left.items().setStackInSlot(5, new ItemStack(Items.EMERALD, 9));
        right.items().setStackInSlot(5, new ItemStack(Items.GOLD_INGOT, 4));
        BlockPos abs = h.absolutePos(a);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        LeadLinedChestMenu menu = (LeadLinedChestMenu) left.createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.stillValid(p), "the double chest's screen is open");
        BlockPos absB = h.absolutePos(b);
        h.getLevel().destroyBlock(absB, true);
        h.assertTrue(type(h, a) == ChestType.SINGLE, "the half left behind is single again");
        h.assertTrue(cap(h, a).getSlots() == 27 && count(left.items(), Items.EMERALD) == 9, "it keeps its own 27 slots and items");
        h.assertFalse(menu.stillValid(p), "the open double screen closes, so nothing goes into the broken half");
        int gold = 0;
        for (ItemEntity e : h.getLevel().getEntities(EntityType.ITEM, new AABB(absB).inflate(2), e -> true)) {
            gold += e.getItem().is(Items.GOLD_INGOT) ? e.getItem().getCount() : 0;
            e.discard();
        }
        h.assertTrue(gold == 4, "the broken half spills only its own items");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_double_chest_feeds_a_comparator_for_both_halves(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        pair(h, a, b);
        LeadLinedChestBlockEntity right = (LeadLinedChestBlockEntity) h.getBlockEntity(b);
        for (int i = 0; i < 27; i++) {
            right.items().setStackInSlot(i, new ItemStack(Items.STONE, 64));
        }
        for (BlockPos at : new BlockPos[] {a, b}) {
            BlockPos abs = h.absolutePos(at);
            int signal = h.getLevel().getBlockState(abs).getAnalogOutputSignal(h.getLevel(), abs);
            h.assertTrue(signal == 8, "half of a double chest full reads 8 from either half, as vanilla (read " + signal + ")");
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ radiation (Voltaic from the pack: -PpackMods)

    private static boolean voltaic() {
        return net.minecraftforge.fml.ModList.get().isLoaded("voltaic");
    }

    /** Something Voltaic counts as radioactive in a chest: Mekanism's raw uranium (amount 6 through Electrodynamics' list), else Electrodynamics' own. */
    private static net.minecraft.world.item.Item rawUranium() {
        for (String id : new String[] {"mekanism:raw_uranium", "electrodynamics:raworeuranium"}) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(id));
            if (item != null && item != Items.AIR) {
                return item;
            }
        }
        return Items.AIR;
    }

    /** Voltaic's {@code RadiationSystem.getRadiationSources(level)}, by reflection: BSP-Core has no compile dependency on Voltaic. */
    @SuppressWarnings("unchecked")
    private static java.util.List<BlockPos> radiationSources(GameTestHelper h) throws ReflectiveOperationException {
        Class<?> system = Class.forName("voltaic.api.radiation.RadiationSystem");
        return (java.util.List<BlockPos>) system.getMethod("getRadiationSources", net.minecraft.world.level.Level.class).invoke(null, h.getLevel());
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void lead_chest_does_not_radiate_where_a_chest_does(GameTestHelper h) {
        BlockPos lead = new BlockPos(1, 1, 1), plain = new BlockPos(4, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, lead);
        if (!voltaic()) {
            // a plain run has no radiation system: all it can check is the rule the scan keys on (run with -PpackMods for the real one)
            h.assertFalse(be instanceof Container, "the chest is no Container, the only thing Voltaic's scan looks for");
            h.succeed();
            return;
        }
        var uranium = rawUranium();
        h.assertTrue(uranium != Items.AIR, "the pack's raw uranium is loaded");
        be.items().setStackInSlot(0, new ItemStack(uranium, 64));
        h.setBlock(plain, Blocks.CHEST);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) h.getBlockEntity(plain)).setItem(0, new ItemStack(uranium, 64));
        // Voltaic scans each loaded chunk once every 20 ticks and keeps a chest's emitter 21: two full scans by 41
        h.runAfterDelay(41, () -> {
            try {
                java.util.List<BlockPos> sources = radiationSources(h);
                h.assertTrue(sources.contains(h.absolutePos(plain)), "a vanilla chest of 64 " + uranium + " radiates (Voltaic's sources: " + sources + ")");
                h.assertFalse(sources.contains(h.absolutePos(lead)), "the lead-lined chest with the same stack does not");
            } catch (ReflectiveOperationException e) {
                h.fail("Voltaic's RadiationSystem could not be read: " + e);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_shielding_entry_is_loaded(GameTestHelper h) {
        if (!voltaic()) {
            h.succeed(); // the file is data only; without Voltaic nobody reads it
            return;
        }
        try {
            Class<?> register = Class.forName("voltaic.common.reloadlistener.RadiationShieldingRegister");
            Object shielding = register.getMethod("getValue", net.minecraft.world.level.block.Block.class).invoke(null, ModBlocks.LEAD_LINED_CHEST.get());
            double transmission = (double) shielding.getClass().getMethod("transmission").invoke(shielding);
            double level = (double) shielding.getClass().getMethod("level").invoke(shielding);
            h.assertTrue(transmission == 0.05 && level == 1.0, "Voltaic reads the chest as lead: transmission 0.05, level 1 (read " + transmission + ", " + level + ")");
            java.util.Map<net.minecraft.world.level.block.Block, Double> set = java.util.Map.of(ModBlocks.RADIATION_SHIELDING_PANEL.get(), 0.0,
                    ModBlocks.RADIATION_SHIELDING_WINDOW.get(), 0.1, ModBlocks.RADIATION_SHIELDING_DOOR.get(), 0.03);
            for (var e : set.entrySet()) {
                Object sh = register.getMethod("getValue", net.minecraft.world.level.block.Block.class).invoke(null, e.getKey());
                double t = (double) sh.getClass().getMethod("transmission").invoke(sh), l = (double) sh.getClass().getMethod("level").invoke(sh);
                h.assertTrue(t == e.getValue() && l == 1.0, e.getKey() + " lets " + e.getValue() + " through (read " + t + ", level " + l + ")");
            }
        } catch (ReflectiveOperationException e) {
            h.fail("Voltaic's shielding register could not be read: " + e);
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the lid (who has it open)

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void lead_chest_lid_follows_its_viewers(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1);
        ServerPlayer p = pair(h, a, b), q = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(a);
        for (ServerPlayer viewer : new ServerPlayer[] {p, q}) {
            viewer.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        }
        LeadLinedChestBlockEntity left = (LeadLinedChestBlockEntity) h.getBlockEntity(a), right = (LeadLinedChestBlockEntity) h.getBlockEntity(b);
        LeadLinedChestMenu m1 = (LeadLinedChestMenu) left.createMenu(1, p.getInventory(), p);
        LeadLinedChestMenu m2 = (LeadLinedChestMenu) right.createMenu(2, q.getInventory(), q);
        h.assertTrue(left.openers() == 2 && right.openers() == 2, "two players at the double chest: both halves count two (" + left.openers() + ", " + right.openers() + ")");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(left.openCount() == 2 && right.openCount() == 2, "the block event tells both halves, so both lids lift"))
                .thenExecute(() -> {
                    m1.removed(p);
                    m2.removed(q);
                })
                .thenWaitUntil(() -> h.assertTrue(left.openers() == 0 && right.openers() == 0 && left.openCount() == 0 && right.openCount() == 0,
                        "both closed: the count is back to 0 and the lids shut"))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 60)
    public void lead_chest_lid_lets_go_of_a_vanished_viewer(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL); // never in the level, so like a player whose connection went
        BlockPos abs = h.absolutePos(pos);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        be.createMenu(1, p.getInventory(), p);
        h.assertTrue(be.openers() == 1, "the screen opens the lid");
        h.succeedWhen(() -> h.assertTrue(be.openers() == 0 && be.openCount() == 0, "within a few ticks the recount finds nobody looking and shuts the lid"));
    }

    // ------------------------------------------------------------------ the screen's layout and the recipe

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_screen_layout_fits_the_menu(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        for (int rows : new int[] {3, 6}) {
            LeadLinedChestMenu menu = new LeadLinedChestMenu(1, p.getInventory(), new net.minecraftforge.items.ItemStackHandler(rows * 9), rows, BlockPos.ZERO, null);
            int chest = rows * 9, height = LeadLinedChestMenu.imageHeight(rows), label = LeadLinedChestMenu.inventoryLabelY(rows);
            for (int i = 0; i < chest; i++) {
                var s = menu.slots.get(i);
                h.assertTrue(s.x == 8 + (i % 9) * 18 && s.y == 18 + (i / 9) * 18, "chest slot " + i + " sits on the grid at " + rows + " rows");
            }
            h.assertTrue(menu.slots.get(chest).y == LeadLinedChestMenu.inventoryY(rows) && menu.slots.get(chest + 27).y == LeadLinedChestMenu.inventoryY(rows) + 58,
                    "the inventory and the hotbar follow at " + rows + " rows");
            h.assertTrue(menu.slots.get(chest + 35).y + 16 + 7 <= height, "the hotbar ends inside the screen at " + rows + " rows");
            h.assertTrue(label - 2 >= 18 + rows * 18 && label - 2 + 11 <= LeadLinedChestMenu.inventoryY(rows) - 1,
                    "the SHIELDED tag (2 above the Inventory label, 11 high) sits between the chest rows and the inventory at " + rows + " rows");
            h.assertTrue(height == (rows == 3 ? 168 : 222), "the screen is a vanilla chest's height at " + rows + " rows");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_recipe_is_eight_lead_plates_round_a_chest(GameTestHelper h) {
        var plates = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags().getTag(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "plates/lead")));
        var recipe = h.getLevel().getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "lead_lined_chest"));
        if (plates.isEmpty()) {
            // no mod here makes lead plates (a plain run has Mekanism only): the recipe is held back, not shown unmakeable
            h.assertFalse(recipe.isPresent(), "with no lead plates in the game the recipe is left out");
            h.succeed();
            return;
        }
        h.assertTrue(recipe.isPresent(), "with lead plates in the game the recipe loads");
        var r = recipe.get();
        h.assertTrue(r.getResultItem(h.getLevel().registryAccess()).is(ModItems.LEAD_LINED_CHEST.get()) && r.getResultItem(h.getLevel().registryAccess()).getCount() == 1,
                "it makes one Lead-Lined Chest");
        var ingredients = r.getIngredients();
        ItemStack plate = new ItemStack(plates.iterator().next());
        h.assertTrue(ingredients.size() == 9, "a full three by three grid");
        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                h.assertTrue(ingredients.get(i).test(new ItemStack(Items.CHEST)), "a wooden chest in the middle");
            } else {
                h.assertTrue(ingredients.get(i).test(plate) && !ingredients.get(i).test(new ItemStack(Items.IRON_INGOT)), "a lead plate (" + plate.getItem() + ") in slot " + i);
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ the radiation shielding set

    private static java.util.List<net.minecraft.world.level.block.Block> shieldingBlocks() {
        return java.util.List.of(ModBlocks.RADIATION_SHIELDING_PANEL.get(), ModBlocks.RADIATION_SHIELDING_WINDOW.get(), ModBlocks.RADIATION_SHIELDING_DOOR.get());
    }

    /** A shut Radiation Shielding Door facing north with its lower half at {@code lower}. */
    private static void door(GameTestHelper h, BlockPos lower, boolean open) {
        BlockState base = ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH).setValue(net.minecraft.world.level.block.DoorBlock.OPEN, open);
        h.setBlock(lower, base.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER));
        h.setBlock(lower.above(), base.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_blocks_are_as_hard_as_iron(GameTestHelper h) {
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        for (var block : shieldingBlocks()) {
            BlockState st = block.defaultBlockState();
            h.assertTrue(st.getDestroySpeed(h.getLevel(), at) == iron.getDestroySpeed(h.getLevel(), at), block + " takes as long to mine as an iron block");
            h.assertTrue(block.getExplosionResistance() == Blocks.IRON_BLOCK.getExplosionResistance(), block + " resists explosions like an iron block");
            h.assertTrue(st.requiresCorrectToolForDrops() && st.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) && st.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL),
                    block + " needs a stone pickaxe or better, as iron does");
        }
        BlockPos w = new BlockPos(1, 1, 1);
        h.setBlock(w, ModBlocks.RADIATION_SHIELDING_WINDOW.get());
        BlockState window = h.getBlockState(w);
        h.assertTrue(window.getLightBlock(h.getLevel(), h.absolutePos(w)) == 0 && window.propagatesSkylightDown(h.getLevel(), h.absolutePos(w)), "the window lets light through");
        h.assertTrue(h.getBlockState(new BlockPos(1, 1, 1)).is(net.minecraft.tags.BlockTags.DOORS) == false
                && ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState().is(net.minecraft.tags.BlockTags.DOORS), "the door counts as a door");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_door_opens_by_hand_and_by_redstone(GameTestHelper h) {
        BlockPos lower = new BlockPos(1, 1, 1);
        door(h, lower, false);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(lower);
        p.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() - 1.5);
        h.useBlock(lower, p);
        h.assertTrue(h.getBlockState(lower).getValue(net.minecraft.world.level.block.DoorBlock.OPEN) && h.getBlockState(lower.above()).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                "a hand opens it, both halves");
        h.useBlock(lower, p);
        h.assertFalse(h.getBlockState(lower).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "and shuts it");
        h.setBlock(lower.east(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(h.getBlockState(lower).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "redstone opens it");
        h.setBlock(lower.east(), Blocks.AIR);
        h.assertFalse(h.getBlockState(lower).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "and it shuts when the power goes");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_recipes(GameTestHelper h) {
        var rm = h.getLevel().getRecipeManager();
        var access = h.getLevel().registryAccess();
        var plates = net.minecraftforge.registries.ForgeRegistries.ITEMS.tags().getTag(net.minecraft.tags.ItemTags.create(new net.minecraft.resources.ResourceLocation("forge", "plates/lead")));
        var panel = rm.byKey(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "radiation_shielding_panel"));
        var window = rm.byKey(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "radiation_shielding_window"));
        var door = rm.byKey(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "radiation_shielding_door"));
        h.assertTrue(door.isPresent() && door.get().getResultItem(access).getCount() == 3 && door.get().getIngredients().size() == 6
                && door.get().getIngredients().stream().allMatch(i -> i.test(new ItemStack(ModItems.RADIATION_SHIELDING_PANEL.get()))), "six panels make three doors");
        if (plates.isEmpty()) {
            h.assertFalse(panel.isPresent() || window.isPresent(), "with no lead plates in the game the panel and window recipes are left out");
            h.succeed();
            return;
        }
        ItemStack plate = new ItemStack(plates.iterator().next());
        h.assertTrue(panel.isPresent() && panel.get().getResultItem(access).is(ModItems.RADIATION_SHIELDING_PANEL.get()) && panel.get().getResultItem(access).getCount() == 2,
                "the panel recipe makes two");
        var pi = panel.get().getIngredients();
        for (int i = 0; i < 9; i++) {
            h.assertTrue(i == 4 ? pi.get(i).test(new ItemStack(Items.STONE)) : pi.get(i).test(plate), "eight plates round a stone block (slot " + i + ")");
        }
        h.assertTrue(window.isPresent() && window.get().getResultItem(access).is(ModItems.RADIATION_SHIELDING_WINDOW.get()) && window.get().getResultItem(access).getCount() == 4,
                "the window recipe makes four");
        var wi = window.get().getIngredients();
        for (int i = 0; i < 9; i++) {
            h.assertTrue(i % 3 == 1 ? wi.get(i).test(new ItemStack(Items.GLASS)) : wi.get(i).test(plate), "a column of glass between two of plates (slot " + i + ")");
        }
        h.succeed();
    }

    /** Voltaic's {@code RadiationManager.getAppliedRadiation(level, source, entity, amount, strength)}, by reflection. */
    private static double ray(GameTestHelper h, BlockPos from, BlockPos to, double amount) throws ReflectiveOperationException {
        Class<?> manager = Class.forName("voltaic.api.radiation.RadiationManager");
        return (double) manager.getMethod("getAppliedRadiation", net.minecraft.world.level.Level.class, BlockPos.class, BlockPos.class, double.class, double.class)
                .invoke(null, h.getLevel(), h.absolutePos(from), h.absolutePos(to), amount, 1.0);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_blocks_cut_voltaics_rays(GameTestHelper h) {
        if (!voltaic()) {
            h.succeed(); // the values are Voltaic data; run with -PpackMods to measure them
            return;
        }
        BlockPos source = new BlockPos(1, 1, 2), target = new BlockPos(5, 1, 2), wall = new BlockPos(3, 1, 2);
        try {
            double air = ray(h, source, target, 100);
            h.assertTrue(Math.abs(air - 100 / 16.0) < 1e-6, "through open air, four blocks: 100 / 16 (" + air + ")");
            h.setBlock(wall, ModBlocks.RADIATION_SHIELDING_PANEL.get());
            h.assertTrue(ray(h, source, target, 100) == 0, "a panel stops it all");
            h.setBlock(wall, ModBlocks.RADIATION_SHIELDING_WINDOW.get());
            double win = ray(h, source, target, 100);
            h.assertTrue(Math.abs(win - air * 0.1) < 1e-6, "a window lets a tenth through (" + win + ")");
            h.setBlock(wall, Blocks.AIR);
            door(h, wall, false);
            double shut = ray(h, source, target, 100);
            h.assertTrue(Math.abs(shut - air * 0.03) < 1e-6, "a shut door lets 3% through (" + shut + ")");
            door(h, wall, true);
            double open = ray(h, source, target, 100);
            // Voltaic weakens a block with a door's OPEN, then again one with a trapdoor's OPEN; both are BlockStateProperties.OPEN,
            // so any open door is weakened twice and keeps only 4% of its blocking (Voltaic 1.2.2, RadiationManager.getAdjustedTransmission)
            h.assertTrue(Math.abs(open - air * (1 - 0.97 * 0.2 * 0.2)) < 1e-6, "an open door keeps only 4% of its blocking, as Voltaic treats every door (" + open + ")");
            h.setBlock(wall.above(), Blocks.AIR);
            h.setBlock(wall, ModBlocks.LEAD_LINED_CHEST.get());
            double chestRay = ray(h, source, target, 100);
            h.assertTrue(Math.abs(chestRay - air * 0.05) < 1e-6, "a lead-lined chest in the way lets 5% through, as a lead block (" + chestRay + ")");
        } catch (ReflectiveOperationException e) {
            h.fail("Voltaic's RadiationManager could not be read: " + e);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_window_is_a_pane_across_the_view(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(3, 1, 1);
        for (Object[] c : new Object[][] {{a, 180f, Direction.Axis.X}, {b, 270f, Direction.Axis.Z}}) {
            BlockPos at = (BlockPos) c[0];
            h.setBlock(at.below(), Blocks.STONE);
            BlockPos abs = h.absolutePos(at);
            p.setPos(abs.getX() + 0.5, abs.getY() + 3, abs.getZ() + 0.5);
            p.setYRot((float) c[1]);
            ItemStack stack = new ItemStack(ModItems.RADIATION_SHIELDING_WINDOW.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            ((BlockItem) stack.getItem()).place(new BlockPlaceContext(p, InteractionHand.MAIN_HAND, stack,
                    new BlockHitResult(Vec3.atCenterOf(abs.below()).add(0, 0.5, 0), Direction.UP, abs.below(), false)));
            h.assertTrue(h.getBlockState(at).getValue(com.mrgregles.bsp_core.shield.ShieldingWindowBlock.AXIS) == c[2],
                    "placed facing " + (c[1].equals(180f) ? "north" : "east") + ", it runs across the view (" + c[2] + ")");
        }
        var box = h.getBlockState(a).getShape(h.getLevel(), h.absolutePos(a)).bounds();
        h.assertTrue(box.minZ == 5 / 16.0 && box.maxZ == 11 / 16.0 && box.minX == 0 && box.maxX == 1, "a pane six pixels deep in the middle of its block");
        BlockState x = ModBlocks.RADIATION_SHIELDING_WINDOW.get().defaultBlockState().setValue(com.mrgregles.bsp_core.shield.ShieldingWindowBlock.AXIS, Direction.Axis.X);
        BlockState z = x.setValue(com.mrgregles.bsp_core.shield.ShieldingWindowBlock.AXIS, Direction.Axis.Z);
        h.assertTrue(x.skipRendering(x, Direction.EAST) && x.skipRendering(x, Direction.UP) && x.skipRendering(x, Direction.DOWN),
                "windows beside, above and below in the same plane draw no face between them");
        h.assertFalse(x.skipRendering(x, Direction.NORTH), "a window in front of another is a second sheet, drawn");
        h.assertFalse(x.skipRendering(z, Direction.EAST), "a window turned the other way is not joined");
        h.assertTrue(x.getLightBlock(h.getLevel(), h.absolutePos(a)) == 0, "light passes");
        h.succeed();
    }

    // ------------------------------------------------------------------ joined panels and windows

    /** The three walls of the mock-up page (round 2): P panel, W window, D door, . air; rows top to bottom, columns as seen from the north. */
    private static final String[][] WALLS = {
            {"PPPPP", "PWWPD", "PPPPD"},
            {"PP...", "PPP..", "PPPPD", "PPPPD"},
            {"PPPPPP", "PWWPWP", "PWWPPP"}};

    private static char cell(String[] rows, int c, int r) {
        return r < 0 || r >= rows.length || c < 0 || c >= rows[r].length() ? '.' : rows[r].charAt(c);
    }

    /** The mask the grid says a face should have; {@code mirror} for the south face, whose left is the grid's right. */
    private static int expected(String[] rows, int c, int r, boolean mirror) {
        char k = cell(rows, c, r);
        int dl = mirror ? 1 : -1;
        java.util.function.BiPredicate<Integer, Integer> same = (dc, dr) -> cell(rows, c + dc, r + dr) == k;
        boolean t = same.test(0, -1), b = same.test(0, 1), l = same.test(dl, 0), rt = same.test(-dl, 0);
        int m = (t ? 0 : com.mrgregles.bsp_core.shield.ShieldJoins.TOP) | (b ? 0 : com.mrgregles.bsp_core.shield.ShieldJoins.BOTTOM)
                | (l ? 0 : com.mrgregles.bsp_core.shield.ShieldJoins.LEFT) | (rt ? 0 : com.mrgregles.bsp_core.shield.ShieldJoins.RIGHT);
        if (t && l && !same.test(dl, -1)) m |= com.mrgregles.bsp_core.shield.ShieldJoins.INNER_TL;
        if (t && rt && !same.test(-dl, -1)) m |= com.mrgregles.bsp_core.shield.ShieldJoins.INNER_TR;
        if (b && l && !same.test(dl, 1)) m |= com.mrgregles.bsp_core.shield.ShieldJoins.INNER_BL;
        if (b && rt && !same.test(-dl, 1)) m |= com.mrgregles.bsp_core.shield.ShieldJoins.INNER_BR;
        return m;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_joins_like_the_mock_up_walls(GameTestHelper h) {
        BlockState panel = ModBlocks.RADIATION_SHIELDING_PANEL.get().defaultBlockState();
        BlockState window = ModBlocks.RADIATION_SHIELDING_WINDOW.get().defaultBlockState().setValue(com.mrgregles.bsp_core.shield.ShieldingWindowBlock.AXIS, Direction.Axis.X);
        int checked = 0;
        for (int w = 0; w < WALLS.length; w++) {
            String[] rows = WALLS[w];
            int cols = 0;
            for (String row : rows) cols = Math.max(cols, row.length());
            int z = 2 + w * 4;
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < cols; c++) {
                    BlockPos pos = new BlockPos(1 + cols - 1 - c, rows.length - r, z); // seen from the north, east is on the left
                    char k = cell(rows, c, r);
                    if (k == 'P') h.setBlock(pos, panel);
                    else if (k == 'W') h.setBlock(pos, window);
                    else if (k == 'D' && cell(rows, c, r + 1) != 'D') door(h, pos, false);
                }
            }
            for (int r = 0; r < rows.length; r++) {
                for (int c = 0; c < cols; c++) {
                    char k = cell(rows, c, r);
                    if (k != 'P' && k != 'W') continue;
                    BlockPos abs = h.absolutePos(new BlockPos(1 + cols - 1 - c, rows.length - r, z));
                    BlockState st = h.getLevel().getBlockState(abs);
                    int north = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.NORTH);
                    int south = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.SOUTH);
                    h.assertTrue(north == expected(rows, c, r, false), "wall " + (w + 1) + " row " + r + " column " + c + " (" + k + "), north face: mask " + north + ", grid says " + expected(rows, c, r, false));
                    h.assertTrue(south == expected(rows, c, r, true), "wall " + (w + 1) + " row " + r + " column " + c + " (" + k + "), south face: mask " + south + ", grid says " + expected(rows, c, r, true));
                    checked++;
                }
            }
        }
        h.assertTrue(checked == 44, "every panel and window of the three walls was checked (" + checked + ")");
        // the quarters' pieces
        int all = com.mrgregles.bsp_core.shield.ShieldJoins.ALONE;
        h.assertTrue(com.mrgregles.bsp_core.shield.ShieldJoins.piece(all, true, true) == com.mrgregles.bsp_core.shield.ShieldJoins.Piece.FULL, "a block alone shows every edge");
        h.assertTrue(com.mrgregles.bsp_core.shield.ShieldJoins.piece(com.mrgregles.bsp_core.shield.ShieldJoins.TOP, true, false) == com.mrgregles.bsp_core.shield.ShieldJoins.Piece.H
                && com.mrgregles.bsp_core.shield.ShieldJoins.piece(com.mrgregles.bsp_core.shield.ShieldJoins.TOP, false, false) == com.mrgregles.bsp_core.shield.ShieldJoins.Piece.PLAIN,
                "a top edge shows on the top quarters only");
        h.assertTrue(com.mrgregles.bsp_core.shield.ShieldJoins.piece(com.mrgregles.bsp_core.shield.ShieldJoins.INNER_BR, false, false) == com.mrgregles.bsp_core.shield.ShieldJoins.Piece.INNER,
                "an inside corner closes in its quarter");
        // a window turned the other way is not joined to one running along x
        BlockPos turned = new BlockPos(2, 2, 1);
        h.setBlock(turned, window.setValue(com.mrgregles.bsp_core.shield.ShieldingWindowBlock.AXIS, Direction.Axis.Z));
        h.assertFalse(com.mrgregles.bsp_core.shield.ShieldJoins.joins(window, h.getBlockState(turned)), "windows turned different ways do not join");
        h.succeed();
    }

    // ------------------------------------------------------------------ audit fixes

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_shift_click_top_up_is_saved(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        be.items().setStackInSlot(0, new ItemStack(Items.STONE, 10));
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(pos);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        p.getInventory().setItem(9, new ItemStack(Items.STONE, 32));
        LeadLinedChestMenu menu = (LeadLinedChestMenu) be.createMenu(1, p.getInventory(), p);
        var chunk = h.getLevel().getChunkAt(abs);
        chunk.setUnsaved(false);
        menu.quickMoveStack(p, 27); // tops up the 10 in the chest: the stack's count changes in place
        h.assertTrue(be.items().getStackInSlot(0).getCount() == 42, "the chest's stack grew to 42");
        h.assertTrue(chunk.isUnsaved(), "and the chest is marked changed, so the 42 is saved, not the 10");
        // the other way: a part of a stack leaves the chest because the inventory only has room for some
        for (int i = 0; i < 36; i++) {
            p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        p.getInventory().setItem(9, new ItemStack(Items.STONE, 60));
        chunk.setUnsaved(false);
        menu.quickMoveStack(p, 0);
        h.assertTrue(be.items().getStackInSlot(0).getCount() == 38 && chunk.isUnsaved(), "four moved out, 38 left, marked changed (" + be.items().getStackInSlot(0).getCount() + ")");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_cleared_before_replacing_spills_nothing(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        be.items().setStackInSlot(3, new ItemStack(Items.DIAMOND, 5));
        h.assertTrue(be instanceof net.minecraft.world.Clearable && !(be instanceof Container), "it can be cleared, and is still no Container");
        net.minecraft.world.Clearable.tryClear(be); // what structures, /clone move, /setblock and Region Guard's plot moves do first
        h.setBlock(pos, Blocks.STONE);
        BlockPos abs = h.absolutePos(pos);
        int spilled = h.getLevel().getEntities(EntityType.ITEM, new AABB(abs).inflate(2), e -> e.getItem().is(Items.DIAMOND)).size();
        h.assertTrue(spilled == 0, "a cleared chest spills nothing when replaced, so a moved copy is the only copy");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void lead_chest_screen_closes_when_its_chest_is_replaced(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        LeadLinedChestBlockEntity be = chest(h, pos);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(pos);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        LeadLinedChestMenu menu = (LeadLinedChestMenu) be.createMenu(1, p.getInventory(), p);
        h.assertTrue(menu.stillValid(p), "open");
        h.setBlock(pos, Blocks.AIR);
        chest(h, pos); // broken and a new chest placed in the same tick
        h.assertFalse(menu.stillValid(p), "the screen of the old chest closes: its slots belong to a chest that is gone");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void lead_double_chest_comparator_on_the_other_half_updates(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1), comparator = new BlockPos(3, 1, 1);
        pair(h, a, b);
        h.setBlock(comparator.below(), Blocks.STONE);
        h.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(net.minecraft.world.level.block.ComparatorBlock.FACING, Direction.WEST));
        LeadLinedChestBlockEntity left = (LeadLinedChestBlockEntity) h.getBlockEntity(a);
        h.runAfterDelay(5, () -> {
            for (int i = 0; i < 27; i++) {
                left.items().setStackInSlot(i, new ItemStack(Items.STONE, 64)); // only the far half changes
            }
        });
        h.succeedWhen(() -> {
            var be = (net.minecraft.world.level.block.entity.ComparatorBlockEntity) h.getBlockEntity(comparator);
            h.assertTrue(be.getOutputSignal() == 8, "a comparator on the near half reads the far half filling (" + be.getOutputSignal() + ")");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_door_is_no_wooden_door_to_mobs_and_the_pane_no_path(GameTestHelper h) {
        BlockState door = ModBlocks.RADIATION_SHIELDING_DOOR.get().defaultBlockState();
        h.assertFalse(net.minecraft.world.level.block.DoorBlock.isWoodenDoor(door), "zombies cannot break it and villagers do not path through it, as an iron door");
        BlockPos w = new BlockPos(1, 1, 1);
        h.setBlock(w, ModBlocks.RADIATION_SHIELDING_WINDOW.get());
        h.assertFalse(h.getBlockState(w).isPathfindable(h.getLevel(), h.absolutePos(w), net.minecraft.world.level.pathfinder.PathComputationType.LAND), "mobs do not path into the pane");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void shielding_border_runs_where_a_covered_panel_meets(GameTestHelper h) {
        BlockPos lower = new BlockPos(1, 1, 3), upper = lower.above();
        h.setBlock(lower, ModBlocks.RADIATION_SHIELDING_PANEL.get());
        h.setBlock(upper, ModBlocks.RADIATION_SHIELDING_PANEL.get());
        BlockPos abs = h.absolutePos(upper);
        BlockState st = h.getBlockState(upper);
        int open = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.NORTH);
        h.assertTrue((open & com.mrgregles.bsp_core.shield.ShieldJoins.BOTTOM) == 0, "two panels stacked in the open: no border between them");
        h.setBlock(lower.north(), Blocks.STONE); // the lower panel's front is covered
        int covered = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.NORTH);
        h.assertTrue((covered & com.mrgregles.bsp_core.shield.ShieldJoins.BOTTOM) != 0, "with stone in front of the lower one, the upper one's border runs along the stone");
        h.setBlock(lower.north(), ModBlocks.RADIATION_SHIELDING_PANEL.get()); // a panel floor meeting the wall
        int floor = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.NORTH);
        h.assertTrue((floor & com.mrgregles.bsp_core.shield.ShieldJoins.BOTTOM) != 0, "and where a panel floor meets the wall");
        h.setBlock(lower.north(), Blocks.GLASS); // glass does not hide it
        int glass = com.mrgregles.bsp_core.shield.ShieldJoins.mask(h.getLevel(), abs, st, Direction.NORTH);
        h.assertTrue((glass & com.mrgregles.bsp_core.shield.ShieldJoins.BOTTOM) == 0, "glass in front hides nothing: still joined");
        h.succeed();
    }
}
