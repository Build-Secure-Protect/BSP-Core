package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
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

    private ModBlockEntities() {}
}
