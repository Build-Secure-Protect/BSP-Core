package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinFactoryBlock;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.MultiblockControllerBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The build order of one multiblock, as the Assembly Guide shows it: a list of steps, each a group
 * of identical blocks on one layer, positioned relative to the controller. Built from the
 * controller's own structure definition, so the guide can never disagree with the real machine.
 */
public record AssemblyGuide(Component title, Direction facing, List<Step> steps) {
    public record Placed(BlockPos rel, BlockState state) {
    }

    public record Step(Block block, List<Placed> blocks, Component title, @Nullable Component tip) {
    }

    /** Order in which block types are placed within a layer. */
    private static final List<String> ORDER = List.of("illyrium_core", "illyrium_casing", "illyrium_glass", "lava_pylon", "item_hatch", "refinery_pump",
            "factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port", "centrifuge_rotor", "centrifuge_casing", "centrifuge_power_port");

    private static String path(Block block) {
        var key = ForgeRegistries.BLOCKS.getKey(block);
        return key == null ? "" : key.getPath();
    }

    @Nullable
    private static Component tip(String key) {
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    private static Component layer(int dy) {
        return Component.translatable("guide.bsp_core.layer." + Math.max(0, Math.min(2, dy)));
    }

    private static Step step(String machine, int dy, Block block, List<Placed> blocks) {
        return new Step(block, blocks, Component.translatable("guide.bsp_core.step", layer(dy), blocks.size(), block.getName()),
                tip("guide.bsp_core." + machine + "." + dy + "." + path(block)));
    }

    /**
     * The guide for the machine a block item belongs to, or null if the item is not part of a multiblock.
     * Shared parts (Casing, Core, Item Hatch) show the Illyrium Crucible.
     */
    @Nullable
    public static AssemblyGuide ofItem(net.minecraft.world.item.Item item) {
        String id = path(Block.byItem(item));
        if (PLASMA_ITEMS.contains(id) || id.endsWith("_core_cable") || id.startsWith("plasma_battery_")) {
            return plasmaChain();
        }
        Block controller = switch (id) {
            case "illyrium_crucible", "illyrium_casing", "illyrium_core", "lava_pylon", "item_hatch" -> ModBlocks.ILLYRIUM_CRUCIBLE.get();
            case "illyrium_refinery", "illyrium_glass", "refinery_pump" -> ModBlocks.ILLYRIUM_REFINERY.get();
            case "shatter_coin_factory", "factory_frame", "factory_press", "factory_blank_hatch", "factory_power_port", "factory_motivator" -> ModBlocks.COIN_FACTORY.get();
            case "magnetic_centrifuge", "centrifuge_casing", "centrifuge_rotor", "centrifuge_power_port" -> ModBlocks.MAGNETIC_CENTRIFUGE.get();
            default -> null;
        };
        if (!(controller instanceof net.minecraft.world.level.block.EntityBlock entity)) {
            return null;
        }
        // a controller that exists only to describe its structure; it is never placed in a world
        return of(entity.newBlockEntity(BlockPos.ZERO, controller.defaultBlockState()));
    }

    private static final java.util.Set<String> PLASMA_ITEMS = java.util.Set.of("plasma_extractor", "plasma_interface", "projector_base", "totem_projector", "plasma_repeater", "plasma_valve", "battery_charger");

    private static BlockState cable(boolean x) {
        BlockState s = ModBlocks.TOTEM_CABLES.get(com.mrgregles.bsp_core.projector.TotemCableBlock.Kind.TETRIUM).get().defaultBlockState();
        for (Direction d : Direction.values()) {
            s = s.setValue(com.mrgregles.bsp_core.projector.TotemCableBlock.SIDES[d.get3DDataValue()], d.getAxis() == (x ? Direction.Axis.X : Direction.Axis.Z));
        }
        return s;
    }

    private static BlockState iface(Direction drum, Direction... cables) {
        BlockState s = ModBlocks.PLASMA_INTERFACE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock.SIDES[drum.get3DDataValue()], com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock.Link.DRUM);
        for (Direction c : cables) {
            s = s.setValue(com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock.SIDES[c.get3DDataValue()], com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock.Link.CABLE);
        }
        return s;
    }

    private static Step plasmaStep(int n, Block block, List<Placed> blocks) {
        return new Step(block, blocks, Component.translatable("guide.bsp_core.plasma.step." + n), tip("guide.bsp_core.plasma.tip." + n));
    }

    /**
     * The Wave Plasma hook-up, shown for every plasma block: a totem on an extractor, the interface beside it, a cable run east through
     * a valve and a repeater to a Projector Base with its projector, and a second run south into the back of a Battery Charger.
     */
    public static AssemblyGuide plasmaChain() {
        List<Step> steps = new ArrayList<>();
        BlockState extractor = ModBlocks.PLASMA_EXTRACTOR.get().defaultBlockState();
        steps.add(plasmaStep(0, extractor.getBlock(), List.of(new Placed(new BlockPos(-1, 0, 0), extractor))));
        BlockState totem = ModBlocks.SHATTER_TOTEM.get().defaultBlockState();
        steps.add(plasmaStep(1, totem.getBlock(), List.of(new Placed(new BlockPos(-1, 1, 0), totem))));
        BlockState iface = iface(Direction.WEST, Direction.EAST, Direction.SOUTH);
        steps.add(plasmaStep(2, iface.getBlock(), List.of(new Placed(BlockPos.ZERO, iface))));
        List<Placed> run = new ArrayList<>();
        for (int x : new int[]{1, 2, 3, 5, 7}) {
            run.add(new Placed(new BlockPos(x, 0, 0), cable(true)));
        }
        steps.add(plasmaStep(3, cable(true).getBlock(), run));
        BlockState valve = ModBlocks.PLASMA_VALVE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.PlasmaValveBlock.AXIS, Direction.Axis.X);
        steps.add(plasmaStep(4, valve.getBlock(), List.of(new Placed(new BlockPos(4, 0, 0), valve))));
        BlockState repeater = ModBlocks.PLASMA_REPEATER.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlock.FACING, Direction.EAST);
        steps.add(plasmaStep(5, repeater.getBlock(), List.of(new Placed(new BlockPos(6, 0, 0), repeater))));
        BlockState base = ModBlocks.PROJECTOR_BASE.get().defaultBlockState();
        steps.add(plasmaStep(6, base.getBlock(), List.of(new Placed(new BlockPos(8, 0, 0), base))));
        BlockState projector = ModBlocks.TOTEM_PROJECTOR.get().defaultBlockState();
        steps.add(plasmaStep(7, projector.getBlock(), List.of(new Placed(new BlockPos(8, 1, 0), projector))));
        steps.add(plasmaStep(8, cable(false).getBlock(), List.of(new Placed(new BlockPos(0, 0, 1), cable(false)), new Placed(new BlockPos(0, 0, 2), cable(false)))));
        BlockState charger = ModBlocks.BATTERY_CHARGER.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.BatteryChargerBlock.FACING, Direction.SOUTH);
        steps.add(plasmaStep(9, charger.getBlock(), List.of(new Placed(new BlockPos(0, 0, 3), charger))));
        return new AssemblyGuide(Component.translatable("guide.bsp_core.plasma.title"), Direction.NORTH, steps);
    }

    /** The guide for the controller at this block entity, or null if it is not a multiblock controller. */
    @Nullable
    public static AssemblyGuide of(@Nullable BlockEntity be) {
        if (be instanceof MultiblockControllerBlockEntity c) {
            BlockPos origin = c.getBlockPos();
            String machine = path(c.getBlockState().getBlock());
            List<Step> steps = new ArrayList<>();
            steps.add(new Step(c.getBlockState().getBlock(), List.of(new Placed(BlockPos.ZERO, c.getBlockState())),
                    Component.translatable("guide.bsp_core.step", layer(0), 1, c.getBlockState().getBlock().getName()), tip("guide.bsp_core.controller")));
            // group the parts by layer, then by block
            Map<Integer, Map<Block, List<Placed>>> layers = new java.util.TreeMap<>();
            for (MultiblockControllerBlockEntity.Part part : c.parts()) {
                BlockPos rel = part.pos().subtract(origin);
                if (rel.equals(BlockPos.ZERO)) {
                    continue;
                }
                layers.computeIfAbsent(rel.getY(), k -> new LinkedHashMap<>()).computeIfAbsent(part.block(), k -> new ArrayList<>())
                        .add(new Placed(rel, part.block().defaultBlockState()));
            }
            layers.forEach((dy, byBlock) -> byBlock.entrySet().stream()
                    .sorted(Comparator.comparingInt(e -> ORDER.indexOf(path(e.getKey()))))
                    .forEach(e -> steps.add(step(machine, dy, e.getKey(), e.getValue()))));
            Direction facing = c.getBlockState().hasProperty(MachineBlock.FACING) ? c.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
            return new AssemblyGuide(Component.translatable(c.titleKey()), facing, steps);
        }
        if (be instanceof CoinFactoryBlockEntity f) {
            BlockPos origin = f.getBlockPos();
            BlockState controller = f.getBlockState().setValue(com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
            List<Step> steps = new ArrayList<>();
            steps.add(new Step(controller.getBlock(), List.of(new Placed(BlockPos.ZERO, controller)),
                    Component.translatable("guide.bsp_core.step", layer(0), 1, controller.getBlock().getName()), tip("guide.bsp_core.controller")));
            List<Placed> slice = new ArrayList<>();
            for (CoinFactoryBlockEntity.Part part : f.parts()) {
                BlockPos rel = part.pos().subtract(origin);
                slice.add(new Placed(rel, part.block().defaultBlockState()));
                steps.add(step("shatter_coin_factory", rel.getY(), part.block(), List.of(new Placed(rel, part.block().defaultBlockState()))));
            }
            // a second slice beside the first, then Motivators on both
            BlockPos side = BlockPos.ZERO.relative(f.rowDirection());
            List<Placed> second = new ArrayList<>();
            second.add(new Placed(side, controller));
            slice.forEach(p -> second.add(new Placed(p.rel().offset(side), p.state())));
            steps.add(new Step(controller.getBlock(), second, Component.translatable("guide.bsp_core.factory.join"), tip("guide.bsp_core.factory.join.tip")));
            List<Placed> motivators = new ArrayList<>();
            BlockState motivator = ModBlocks.FACTORY_MOTIVATOR.get().defaultBlockState();
            for (int i = 0; i < CoinFactoryBlockEntity.MOTIVATOR_CELLS; i++) {
                BlockPos rel = f.motivatorPos(i).subtract(origin);
                motivators.add(new Placed(rel, motivator));
                motivators.add(new Placed(rel.offset(side), motivator));
            }
            steps.add(new Step(motivator.getBlock(), motivators, Component.translatable("guide.bsp_core.factory.motivators"), tip("guide.bsp_core.factory.motivators.tip")));
            return new AssemblyGuide(Component.translatable("block.bsp_core.shatter_coin_factory"), f.getBlockState().getValue(CoinFactoryBlock.FACING), steps);
        }
        return null;
    }
}
