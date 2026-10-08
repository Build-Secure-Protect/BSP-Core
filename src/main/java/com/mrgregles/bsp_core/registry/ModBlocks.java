package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryBlock;
import com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity;
import com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.machine.IllyriumRefineryBlockEntity;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BSPCore.MODID);

    public static final RegistryObject<ShatterTotemBlock> SHATTER_TOTEM =
            BLOCKS.register("shatter_totem", ShatterTotemBlock::new);

    public static final RegistryObject<CoinFactoryBlock> COIN_FACTORY =
            BLOCKS.register("shatter_coin_factory", CoinFactoryBlock::new);

    // --- factory slice parts: used by the Shatter Coin Factory only
    public static final RegistryObject<Block> FACTORY_FRAME = BLOCKS.register("factory_frame", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> FACTORY_PRESS = BLOCKS.register("factory_press", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> FACTORY_BLANK_HATCH = BLOCKS.register("factory_blank_hatch", com.mrgregles.bsp_core.coin.FactoryPortBlock::new);
    public static final RegistryObject<Block> FACTORY_POWER_PORT = BLOCKS.register("factory_power_port", com.mrgregles.bsp_core.coin.FactoryPortBlock::new);
    public static final RegistryObject<Block> FACTORY_MOTIVATOR = BLOCKS.register("factory_motivator", com.mrgregles.bsp_core.coin.MotivatorBlock::new);

    public static final RegistryObject<Block> ADMIN_RACK = BLOCKS.register("admin_rack", com.mrgregles.bsp_core.admin.AdminRackBlock::new);

    public static final RegistryObject<Block> ANTI_TOTEM = BLOCKS.register("anti_totem", com.mrgregles.bsp_core.zone.AntiTotemBlock::new);

    public static final RegistryObject<Block> DECOY_TOTEM = BLOCKS.register("decoy_totem", com.mrgregles.bsp_core.decoy.DecoyTotemBlock::new);
    public static final RegistryObject<Block> DECOY_POWER_BASE = BLOCKS.register("decoy_power_base", com.mrgregles.bsp_core.decoy.DecoyPowerBaseBlock::new);

    public static final RegistryObject<Block> PLASMA_EXTRACTOR = BLOCKS.register("plasma_extractor", com.mrgregles.bsp_core.plasma.PlasmaExtractorBlock::new);
    public static final RegistryObject<Block> PLASMA_INTERFACE = BLOCKS.register("plasma_interface", com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlock::new);
    public static final RegistryObject<Block> PLASMA_REPEATER = BLOCKS.register("plasma_repeater", com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlock::new);
    public static final RegistryObject<Block> PLASMA_VALVE = BLOCKS.register("plasma_valve", com.mrgregles.bsp_core.plasma.PlasmaValveBlock::new);
    public static final RegistryObject<Block> TANK_CASING = BLOCKS.register("tank_casing", () -> new com.mrgregles.bsp_core.tank.TankBlock(com.mrgregles.bsp_core.tank.TankBlock.Part.CASING));
    public static final RegistryObject<Block> TANK_GLASS = BLOCKS.register("tank_glass", () -> new com.mrgregles.bsp_core.tank.TankBlock(com.mrgregles.bsp_core.tank.TankBlock.Part.GLASS));
    public static final RegistryObject<Block> TANK_PORT = BLOCKS.register("tank_port", () -> new com.mrgregles.bsp_core.tank.TankBlock(com.mrgregles.bsp_core.tank.TankBlock.Part.PORT));
    /** A plain see-through block of Tetrium-strengthened glass: the ingredient for Tank Glass. */
    public static final RegistryObject<Block> TETRIUM_GLASS = BLOCKS.register("tetrium_glass", () -> new net.minecraft.world.level.block.GlassBlock(BlockBehaviour.Properties.of()
            .strength(1.0F, 6.0F).sound(SoundType.GLASS).noOcclusion().isViewBlocking((st, l, pos) -> false).isSuffocating((st, l, pos) -> false)));
    public static final RegistryObject<Block> BATTERY_CHARGER = BLOCKS.register("battery_charger", com.mrgregles.bsp_core.plasma.BatteryChargerBlock::new);
    public static final RegistryObject<Block> PROJECTOR_BASE = BLOCKS.register("projector_base", com.mrgregles.bsp_core.plasma.ProjectorBaseBlock::new);
    /** Plasma Batteries I to IV, by tier. */
    public static final java.util.List<RegistryObject<com.mrgregles.bsp_core.plasma.PlasmaBatteryBlock>> PLASMA_BATTERIES = new java.util.ArrayList<>();

    static {
        for (int tier = 0; tier < 4; tier++) {
            int t = tier;
            PLASMA_BATTERIES.add(BLOCKS.register("plasma_battery_" + (tier + 1), () -> new com.mrgregles.bsp_core.plasma.PlasmaBatteryBlock(t)));
        }
    }
    public static final RegistryObject<Block> TOTEM_PROJECTOR = BLOCKS.register("totem_projector", com.mrgregles.bsp_core.projector.TotemProjectorBlock::new);
    /** bsp_core:tetrium_core_cable, magnatite_core_cable, illyrium_core_cable, charged_illyrium_core_cable */
    public static final java.util.Map<com.mrgregles.bsp_core.projector.TotemCableBlock.Kind, RegistryObject<Block>> TOTEM_CABLES = new java.util.EnumMap<>(com.mrgregles.bsp_core.projector.TotemCableBlock.Kind.class);

    /** bsp_core:{colour}_{kind}_core_cable for every dye and every kind but Tetrium, in creative-tab order. */
    public static final java.util.List<RegistryObject<Block>> COLOURED_CABLES = new java.util.ArrayList<>();

    static {
        for (var kind : com.mrgregles.bsp_core.projector.TotemCableBlock.Kind.values()) {
            TOTEM_CABLES.put(kind, BLOCKS.register(kind.name().toLowerCase(java.util.Locale.ROOT) + "_core_cable", () -> new com.mrgregles.bsp_core.projector.TotemCableBlock(kind)));
        }
        for (var kind : com.mrgregles.bsp_core.projector.TotemCableBlock.Kind.values()) {
            if (kind == com.mrgregles.bsp_core.projector.TotemCableBlock.Kind.TETRIUM) {
                continue;
            }
            for (var dye : net.minecraft.world.item.DyeColor.values()) {
                COLOURED_CABLES.add(BLOCKS.register(dye.getSerializedName() + "_" + kind.name().toLowerCase(java.util.Locale.ROOT) + "_core_cable", () -> new com.mrgregles.bsp_core.projector.TotemCableBlock(kind, dye)));
            }
        }
    }

    /** Every cable block, plain and coloured. */
    public static java.util.List<RegistryObject<Block>> allCables() {
        java.util.List<RegistryObject<Block>> all = new java.util.ArrayList<>(TOTEM_CABLES.values());
        all.addAll(COLOURED_CABLES);
        return all;
    }

    public static final RegistryObject<Block> COIN_VAULT = BLOCKS.register("coin_vault", com.mrgregles.bsp_core.vault.CoinVaultBlock::new);

    public static final RegistryObject<Block> SCORE_SCREEN = BLOCKS.register("score_screen", com.mrgregles.bsp_core.score.ScoreScreenBlock::new);

    public static final RegistryObject<MachineBlock> TETRIUM_CRUCIBLE =
            BLOCKS.register("tetrium_crucible", () -> new MachineBlock(TetriumCrucibleBlockEntity::new));
    public static final RegistryObject<MachineBlock> COMBINATION_FORGE =
            BLOCKS.register("combination_forge", () -> new MachineBlock(CombinationForgeBlockEntity::new));

    // --- multiblock parts and controllers
    public static final RegistryObject<Block> ILLYRIUM_CASING = BLOCKS.register("illyrium_casing", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4.0F, 8.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> ILLYRIUM_GLASS = BLOCKS.register("illyrium_glass", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .strength(1.5F, 6.0F).sound(SoundType.GLASS).noOcclusion().isViewBlocking((st, l, pos) -> false).isSuffocating((st, l, pos) -> false)));
    public static final RegistryObject<MachineBlock> ILLYRIUM_CRUCIBLE =
            BLOCKS.register("illyrium_crucible", () -> new MachineBlock(IllyriumCrucibleBlockEntity::new));
    public static final RegistryObject<MachineBlock> ILLYRIUM_REFINERY =
            BLOCKS.register("illyrium_refinery", () -> new MachineBlock(IllyriumRefineryBlockEntity::new));

    /** Heart of both multiblocks: centre of the crucible, bottom centre of the refinery. */
    public static final RegistryObject<Block> ILLYRIUM_CORE = BLOCKS.register("illyrium_core", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.DIAMOND).requiresCorrectToolForDrops().strength(5.0F, 9.0F).sound(SoundType.METAL).lightLevel(s -> 9).noOcclusion()));
    public static final RegistryObject<Block> LAVA_PYLON = BLOCKS.register("lava_pylon", com.mrgregles.bsp_core.machine.MachinePortBlock::new);
    public static final RegistryObject<Block> REFINERY_PUMP = BLOCKS.register("refinery_pump", com.mrgregles.bsp_core.machine.MachinePortBlock::new);
    public static final RegistryObject<Block> ITEM_HATCH = BLOCKS.register("item_hatch", com.mrgregles.bsp_core.machine.MachinePortBlock::new);

    // --- ores: they drop themselves, because the ore block is what the crucibles take
    public static final RegistryObject<Block> TETRIUM_ORE = ore("tetrium_ore", MapColor.STONE, 3.0F, SoundType.STONE);
    public static final RegistryObject<Block> DEEPSLATE_TETRIUM_ORE = ore("deepslate_tetrium_ore", MapColor.DEEPSLATE, 4.5F, SoundType.DEEPSLATE);
    public static final RegistryObject<Block> ILLYRIUM_ORE = ore("illyrium_ore", MapColor.STONE, 3.0F, SoundType.STONE);
    public static final RegistryObject<Block> DEEPSLATE_ILLYRIUM_ORE = ore("deepslate_illyrium_ore", MapColor.DEEPSLATE, 4.5F, SoundType.DEEPSLATE);
    // --- Magnetic Centrifuge: one layer is the controller, a Rotor, an Item Hatch, a Power Port and five Casing
    public static final RegistryObject<MachineBlock> MAGNETIC_CENTRIFUGE =
            BLOCKS.register("magnetic_centrifuge", () -> new MachineBlock(com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity::new));
    public static final RegistryObject<Block> CENTRIFUGE_CASING = BLOCKS.register("centrifuge_casing", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> CENTRIFUGE_ROTOR = BLOCKS.register("centrifuge_rotor", () -> new com.mrgregles.bsp_core.machine.StructurePartBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> CENTRIFUGE_POWER_PORT = BLOCKS.register("centrifuge_power_port", com.mrgregles.bsp_core.machine.MachinePortBlock::new);

    /** Deep Overworld ore, a little easier to find than diamond. Processed in the Magnetic Centrifuge. */
    public static final RegistryObject<Block> MAGNATITE_ORE = ore("magnatite_ore", MapColor.STONE, 3.0F, SoundType.STONE);
    public static final RegistryObject<Block> DEEPSLATE_MAGNATITE_ORE = ore("deepslate_magnatite_ore", MapColor.DEEPSLATE, 4.5F, SoundType.DEEPSLATE);
    public static final RegistryObject<Block> END_STONE_ILLYRIUM_ORE = ore("end_stone_illyrium_ore", MapColor.SAND, 3.0F, SoundType.STONE);

    private static RegistryObject<Block> ore(String name, MapColor color, float hardness, SoundType sound) {
        return BLOCKS.register(name, () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                .mapColor(color).requiresCorrectToolForDrops().strength(hardness, 3.0F).sound(sound)));
    }

    private ModBlocks() {}
}
