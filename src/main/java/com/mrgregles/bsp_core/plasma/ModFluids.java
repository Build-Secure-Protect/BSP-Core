package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Consumer;

/**
 * Wave Plasma, the fluid a placed Shatter Totem gives off. It is a real fluid so tanks and screens
 * can show it, but it is never placed in the world, has no bucket, and no block of BSP-Core offers
 * it to other mods' pipes: only Plasma Cables and the blocks of the plasma chain carry it.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, BSPCore.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, BSPCore.MODID);
    private static final ResourceLocation STILL = new ResourceLocation(BSPCore.MODID, "block/plasma_still"), FLOW = new ResourceLocation(BSPCore.MODID, "block/plasma_flow");

    public static final RegistryObject<FluidType> WAVE_PLASMA_TYPE = FLUID_TYPES.register("wave_plasma", () -> new FluidType(FluidType.Properties.create()
            .descriptionId("fluid.bsp_core.wave_plasma").lightLevel(10).density(500).viscosity(800).canConvertToSource(false).canDrown(false).canSwim(false).canPushEntity(false)) {
        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return STILL;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return FLOW;
                }
            });
        }
    });

    public static final RegistryObject<Fluid> WAVE_PLASMA = FLUIDS.register("wave_plasma", WavePlasmaFluid::new);

    private ModFluids() {}

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
    }
}
