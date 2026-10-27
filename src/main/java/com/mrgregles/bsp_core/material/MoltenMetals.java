package com.mrgregles.bsp_core.material;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.SoundActions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Molten Tetrium, Illyrium and Magnatite: Forge fluids with a source and a flowing form, a block that
 * burns like lava and meets water as lava does, and a bucket a dispenser can use, so Tinkers, Thermal,
 * Mekanism, AE2 and every pipe mod can pump and store BSP metal. Each carries only the fluid tag {@code forge:molten_<metal>}; the metals stay off the
 * item material tags ({@code forge:ingots/...}, {@code forge:ores/...}), so no mod builds ore doubling for
 * them. What melts into them, and how much, is set by recipe data at the BSP machines' own yields.
 */
public final class MoltenMetals {
    public enum Metal {
        TETRIUM("tetrium", MapColor.COLOR_PURPLE, 1200),
        ILLYRIUM("illyrium", MapColor.DIAMOND, 1500),
        MAGNATITE("magnatite", MapColor.COLOR_GRAY, 1350);

        public final String key;
        final MapColor map;
        final int temperature;

        Metal(String key, MapColor map, int temperature) {
            this.key = key;
            this.map = map;
            this.temperature = temperature;
        }

        public String id() {
            return "molten_" + key;
        }
    }

    /** One metal's registry entries. */
    public record Molten(RegistryObject<FluidType> type, RegistryObject<ForgeFlowingFluid.Source> source, RegistryObject<ForgeFlowingFluid.Flowing> flowing,
                         RegistryObject<LiquidBlock> block, RegistryObject<BucketItem> bucket) {}

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, BSPCore.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, BSPCore.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BSPCore.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BSPCore.MODID);

    public static final Map<Metal, Molten> ALL = new EnumMap<>(Metal.class);

    static {
        for (Metal metal : Metal.values()) {
            ALL.put(metal, make(metal));
        }
    }

    private MoltenMetals() {}

    public static void register(IEventBus bus) {
        FLUID_TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static Molten of(Metal metal) {
        return ALL.get(metal);
    }

    /**
     * Common setup, on the main thread: a dispenser pours and fills the buckets as it does lava's, and the metal meets water
     * as lava does (owner, audit D7): a source turns to Obsidian, a flowing block to Cobblestone, with lava's hiss.
     */
    public static void setup() {
        for (Molten m : ALL.values()) {
            net.minecraft.world.level.block.DispenserBlock.registerBehavior(m.bucket().get(), net.minecraftforge.fluids.DispenseFluidContainer.getInstance());
            net.minecraftforge.fluids.FluidInteractionRegistry.addInteraction(m.type().get(), new net.minecraftforge.fluids.FluidInteractionRegistry.InteractionInformation(
                    net.minecraftforge.common.ForgeMod.WATER_TYPE.get(),
                    state -> state.isSource() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState()));
        }
    }

    private static Molten make(Metal metal) {
        String id = metal.id();
        RegistryObject<FluidType> type = FLUID_TYPES.register(id, () -> new MoltenType(metal));
        // the properties name the other entries through their registry objects, so they can be built before any of them exist
        ForgeFlowingFluid.Properties[] props = new ForgeFlowingFluid.Properties[1];
        RegistryObject<ForgeFlowingFluid.Source> source = FLUIDS.register(id, () -> new ForgeFlowingFluid.Source(props[0]));
        RegistryObject<ForgeFlowingFluid.Flowing> flowing = FLUIDS.register("flowing_" + id, () -> new ForgeFlowingFluid.Flowing(props[0]));
        RegistryObject<LiquidBlock> block = BLOCKS.register(id, () -> new MoltenBlock(source, BlockBehaviour.Properties.copy(Blocks.LAVA).mapColor(metal.map).lightLevel(s -> 12)));
        RegistryObject<BucketItem> bucket = ITEMS.register(id + "_bucket", () -> new BucketItem(source, new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
        // like lava: slow, short runs, never makes new sources
        props[0] = new ForgeFlowingFluid.Properties(type, source, flowing).bucket(bucket).block(block)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(30).explosionResistance(100f);
        return new Molten(type, source, flowing, block, bucket);
    }

    /** Lava's physics and sounds; the still and flowing textures carry the metal's own colours. */
    private static final class MoltenType extends FluidType {
        private final ResourceLocation still, flow;

        MoltenType(Metal metal) {
            super(FluidType.Properties.create().descriptionId("fluid_type." + BSPCore.MODID + "." + metal.id())
                    .canSwim(false).canDrown(false).pathType(BlockPathTypes.LAVA).adjacentPathType(null)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA).sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)
                    .lightLevel(12).density(3000).viscosity(6000).temperature(metal.temperature));
            still = new ResourceLocation(BSPCore.MODID, "block/" + metal.id() + "_still");
            flow = new ResourceLocation(BSPCore.MODID, "block/" + metal.id() + "_flow");
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                @Override
                public ResourceLocation getStillTexture() {
                    return still;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    return flow;
                }
            });
        }
    }

    /** The placed fluid: anything that wades in catches fire and takes lava damage. */
    private static final class MoltenBlock extends LiquidBlock {
        MoltenBlock(Supplier<? extends FlowingFluid> fluid, BlockBehaviour.Properties props) {
            super(fluid, props);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            if (!level.isClientSide && !entity.fireImmune()) {
                entity.setSecondsOnFire(8);
                entity.hurt(level.damageSources().lava(), 4.0F);
            }
        }
    }
}
