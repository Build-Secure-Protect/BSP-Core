package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity;
import com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.machine.IllyriumRefineryBlockEntity;
import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, BSPCore.MODID);

    @SuppressWarnings("DataFlowIssue") // Forge convention: the datafixer type is null
    public static final RegistryObject<BlockEntityType<ShatterTotemBlockEntity>> SHATTER_TOTEM =
            BLOCK_ENTITIES.register("shatter_totem", () -> BlockEntityType.Builder
                    .of(ShatterTotemBlockEntity::new, ModBlocks.SHATTER_TOTEM.get())
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CoinFactoryBlockEntity>> COIN_FACTORY =
            BLOCK_ENTITIES.register("shatter_coin_factory", () -> BlockEntityType.Builder
                    .of(CoinFactoryBlockEntity::new, ModBlocks.COIN_FACTORY.get())
                    .build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TetriumCrucibleBlockEntity>> TETRIUM_CRUCIBLE =
            BLOCK_ENTITIES.register("tetrium_crucible", () -> BlockEntityType.Builder
                    .of(TetriumCrucibleBlockEntity::new, ModBlocks.TETRIUM_CRUCIBLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<CombinationForgeBlockEntity>> COMBINATION_FORGE =
            BLOCK_ENTITIES.register("combination_forge", () -> BlockEntityType.Builder
                    .of(CombinationForgeBlockEntity::new, ModBlocks.COMBINATION_FORGE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<IllyriumCrucibleBlockEntity>> ILLYRIUM_CRUCIBLE =
            BLOCK_ENTITIES.register("illyrium_crucible", () -> BlockEntityType.Builder
                    .of(IllyriumCrucibleBlockEntity::new, ModBlocks.ILLYRIUM_CRUCIBLE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<IllyriumRefineryBlockEntity>> ILLYRIUM_REFINERY =
            BLOCK_ENTITIES.register("illyrium_refinery", () -> BlockEntityType.Builder
                    .of(IllyriumRefineryBlockEntity::new, ModBlocks.ILLYRIUM_REFINERY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.machine.MachinePortBlockEntity>> MACHINE_PORT =
            BLOCK_ENTITIES.register("machine_port", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.machine.MachinePortBlockEntity::new, ModBlocks.LAVA_PYLON.get(), ModBlocks.REFINERY_PUMP.get(), ModBlocks.ITEM_HATCH.get()).build(null));

    private ModBlockEntities() {}
}
