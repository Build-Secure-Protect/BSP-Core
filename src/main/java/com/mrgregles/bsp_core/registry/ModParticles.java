package com.mrgregles.bsp_core.registry;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** BSP-Core's particles. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, BSPCore.MODID);

    /** The gold sparks rising from the Score Leader's totems. Client-side only: the server never sends them. */
    public static final RegistryObject<SimpleParticleType> LEADER_SPARK = PARTICLES.register("leader_spark", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
