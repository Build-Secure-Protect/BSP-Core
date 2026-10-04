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
    public static final RegistryObject<Block> END_STONE_ILLYRIUM_ORE = ore("end_stone_illyrium_ore", MapColor.SAND, 3.0F, SoundType.STONE);

    private static RegistryObject<Block> ore(String name, MapColor color, float hardness, SoundType sound) {
        return BLOCKS.register(name, () -> new DropExperienceBlock(BlockBehaviour.Properties.of()
                .mapColor(color).requiresCorrectToolForDrops().strength(hardness, 3.0F).sound(sound)));
    }

    private ModBlocks() {}
}
