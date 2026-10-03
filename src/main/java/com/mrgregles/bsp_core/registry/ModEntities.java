package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BSPCore.MODID);

    public static final RegistryObject<EntityType<ShatterTotemItemEntity>> SHATTER_TOTEM_ITEM =
            ENTITIES.register("shatter_totem_item", () -> EntityType.Builder
                    .<ShatterTotemItemEntity>of(ShatterTotemItemEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(6)
                    .updateInterval(20)
                    .build(BSPCore.MODID + ":shatter_totem_item"));

    private ModEntities() {}
}
