package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BSPCore.MODID);

    public static final RegistryObject<ShatterTotemBlock> SHATTER_TOTEM =
            BLOCKS.register("shatter_totem", ShatterTotemBlock::new);

    private ModBlocks() {}
}
