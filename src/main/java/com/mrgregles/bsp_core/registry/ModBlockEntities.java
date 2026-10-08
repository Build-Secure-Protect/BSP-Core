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
                    .of(com.mrgregles.bsp_core.machine.MachinePortBlockEntity::new, ModBlocks.LAVA_PYLON.get(), ModBlocks.REFINERY_PUMP.get(), ModBlocks.ITEM_HATCH.get(), ModBlocks.CENTRIFUGE_POWER_PORT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.coin.FactoryPortBlockEntity>> FACTORY_PORT =
            BLOCK_ENTITIES.register("factory_port", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.coin.FactoryPortBlockEntity::new, ModBlocks.FACTORY_BLANK_HATCH.get(), ModBlocks.FACTORY_POWER_PORT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.score.ScoreScreenBlockEntity>> SCORE_SCREEN =
            BLOCK_ENTITIES.register("score_screen", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity::new, ModBlocks.SCORE_SCREEN.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.vault.CoinVaultBlockEntity>> COIN_VAULT =
            BLOCK_ENTITIES.register("coin_vault", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.vault.CoinVaultBlockEntity::new, ModBlocks.COIN_VAULT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.zone.AntiTotemBlockEntity>> ANTI_TOTEM =
            BLOCK_ENTITIES.register("anti_totem", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.zone.AntiTotemBlockEntity::new, ModBlocks.ANTI_TOTEM.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity>> MAGNETIC_CENTRIFUGE =
            BLOCK_ENTITIES.register("magnetic_centrifuge", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity::new, ModBlocks.MAGNETIC_CENTRIFUGE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity>> DECOY_TOTEM =
            BLOCK_ENTITIES.register("decoy_totem", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity::new, ModBlocks.DECOY_TOTEM.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.decoy.DecoyPowerBaseBlockEntity>> DECOY_POWER_BASE =
            BLOCK_ENTITIES.register("decoy_power_base", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.decoy.DecoyPowerBaseBlockEntity::new, ModBlocks.DECOY_POWER_BASE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.PlasmaExtractorBlockEntity>> PLASMA_EXTRACTOR =
            BLOCK_ENTITIES.register("plasma_extractor", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.PlasmaExtractorBlockEntity::new, ModBlocks.PLASMA_EXTRACTOR.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlockEntity>> PLASMA_INTERFACE =
            BLOCK_ENTITIES.register("plasma_interface", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlockEntity::new, ModBlocks.PLASMA_INTERFACE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity>> PLASMA_CABLE =
            BLOCK_ENTITIES.register("plasma_cable", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity::new, ModBlocks.TOTEM_CABLES.values().stream().map(RegistryObject::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlockEntity>> PLASMA_REPEATER =
            BLOCK_ENTITIES.register("plasma_repeater", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.PlasmaRepeaterBlockEntity::new, ModBlocks.PLASMA_REPEATER.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity>> PLASMA_VALVE =
            BLOCK_ENTITIES.register("plasma_valve", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity::new, ModBlocks.PLASMA_VALVE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity>> PROJECTOR_BASE =
            BLOCK_ENTITIES.register("projector_base", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity::new, ModBlocks.PROJECTOR_BASE.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.PlasmaBatteryBlockEntity>> PLASMA_BATTERY =
            BLOCK_ENTITIES.register("plasma_battery", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.PlasmaBatteryBlockEntity::new, ModBlocks.PLASMA_BATTERIES.stream().map(RegistryObject::get).toArray(net.minecraft.world.level.block.Block[]::new)).build(null));
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.plasma.BatteryChargerBlockEntity>> BATTERY_CHARGER =
            BLOCK_ENTITIES.register("battery_charger", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.plasma.BatteryChargerBlockEntity::new, ModBlocks.BATTERY_CHARGER.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity>> TOTEM_PROJECTOR =
            BLOCK_ENTITIES.register("totem_projector", () -> BlockEntityType.Builder
                    .of(com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity::new, ModBlocks.TOTEM_PROJECTOR.get()).build(null));

    private ModBlockEntities() {}
}
