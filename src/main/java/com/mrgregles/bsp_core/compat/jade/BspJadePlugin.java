package com.mrgregles.bsp_core.compat.jade;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlock;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity;
import com.mrgregles.bsp_core.machine.MultiblockControllerBlockEntity;
import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

import javax.annotation.Nullable;

/**
 * Jade ("what am I looking at") support. Jade already shows every block's name and its RF, and that is
 * all BSP-Core wants shown, so this adds no lines of its own. It only corrects what Jade points at:
 * <ul>
 *   <li>a working Decoy Totem reads as a Shatter Totem to everyone but its owner, like its renderer;</li>
 *   <li>the hidden part blocks of an assembled machine read as the machine itself.</li>
 * </ul>
 * Only loaded when Jade is installed.
 */
@WailaPlugin
public class BspJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CableFlow.INSTANCE, com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.class);
        registration.registerBlockDataProvider(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.PlasmaExtractorBlockEntity.class);
        registration.registerBlockDataProvider(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity.class);
        registration.registerBlockDataProvider(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity.class);
        registration.registerBlockDataProvider(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.tank.TankPartBlockEntity.class);
        registration.registerBlockDataProvider(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.tank.TankPortBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CableFlow.INSTANCE, com.mrgregles.bsp_core.projector.TotemCableBlock.class);
        registration.registerBlockComponent(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.PlasmaExtractorBlock.class);
        registration.registerBlockComponent(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.ProjectorBaseBlock.class);
        registration.registerBlockComponent(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.plasma.PlasmaValveBlock.class);
        registration.registerBlockComponent(PlasmaLine.INSTANCE, com.mrgregles.bsp_core.tank.TankBlock.class);
        registration.addRayTraceCallback((hit, accessor, original) -> redirect(registration, accessor));
    }

    /** One line on a Plasma Cable: what is flowing through it, as the interface last reported. */
    public enum CableFlow implements snownee.jade.api.IBlockComponentProvider, snownee.jade.api.IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final net.minecraft.resources.ResourceLocation UID = new net.minecraft.resources.ResourceLocation(com.mrgregles.bsp_core.BSPCore.MODID, "cable_flow");

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity cable) {
                data.putInt("PlasmaFlow", cable.flow());
                if (cable.anyEndSet()) {
                    data.putByteArray("PlasmaEnds", cable.ends());
                }
            }
        }

        @Override
        public void appendTooltip(snownee.jade.api.ITooltip tooltip, BlockAccessor accessor, snownee.jade.api.config.IPluginConfig config) {
            int flow = accessor.getServerData().getInt("PlasmaFlow");
            tooltip.add(net.minecraft.network.chat.Component.translatable(flow > 0 ? "jade.bsp_core.cable_flow" : "jade.bsp_core.cable_idle", flow)
                    .withStyle(flow > 0 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY));
            if (accessor.getServerData().contains("PlasmaEnds")) {
                byte[] ends = accessor.getServerData().getByteArray("PlasmaEnds");
                net.minecraft.network.chat.MutableComponent list = net.minecraft.network.chat.Component.empty();
                boolean first = true;
                for (int i = 0; i < 6 && i < ends.length; i++) {
                    if (ends[i] == 0) {
                        continue;
                    }
                    var end = com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.values()[Math.min(ends[i], 4)];
                    if (!first) {
                        list.append(", ");
                    }
                    first = false;
                    list.append(net.minecraft.network.chat.Component.translatable("gui.bsp_core.wrench.side." + net.minecraft.core.Direction.from3DDataValue(i).getSerializedName())).append(" ").append(net.minecraft.network.chat.Component.translatable(end.key()));
                }
                tooltip.add(net.minecraft.network.chat.Component.translatable("jade.bsp_core.cable_ends", list).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        }

        @Override
        public net.minecraft.resources.ResourceLocation getUid() {
            return UID;
        }
    }

    /** One line on an extractor (mB/t drawn from the totem), a Projector Base (its tank) or a valve (its limit, or shut). */
    public enum PlasmaLine implements snownee.jade.api.IBlockComponentProvider, snownee.jade.api.IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final net.minecraft.resources.ResourceLocation UID = new net.minecraft.resources.ResourceLocation(com.mrgregles.bsp_core.BSPCore.MODID, "plasma");

        @Override
        public void appendServerData(net.minecraft.nbt.CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof com.mrgregles.bsp_core.plasma.PlasmaExtractorBlockEntity extractor) {
                data.putInt("PlasmaDraw", extractor.flow());
            } else if (accessor.getBlockEntity() instanceof com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity base) {
                data.putInt("PlasmaTank", base.tank());
                data.putInt("PlasmaCap", base.capacity());
            } else if (accessor.getBlockEntity() instanceof com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity valve) {
                data.putInt("ValveLimit", valve.limit());
                data.putBoolean("ValveOpen", valve.open());
            } else if (accessor.getBlockEntity() instanceof com.mrgregles.bsp_core.tank.TankPartBlockEntity part) {
                var master = part.masterEntity();
                data.putBoolean("TankFormed", master != null);
                if (master != null) {
                    data.putLong("TankStored", master.stored());
                    data.putLong("TankCap", master.capacity());
                }
                if (part instanceof com.mrgregles.bsp_core.tank.TankPortBlockEntity port) {
                    data.putByte("PortMode", (byte) port.mode().ordinal());
                    data.putInt("PortIn", port.in());
                    data.putInt("PortOut", port.out());
                }
            }
        }

        @Override
        public void appendTooltip(snownee.jade.api.ITooltip tooltip, BlockAccessor accessor, snownee.jade.api.config.IPluginConfig config) {
            net.minecraft.nbt.CompoundTag d = accessor.getServerData();
            if (d.contains("PlasmaDraw")) {
                int draw = d.getInt("PlasmaDraw");
                tooltip.add(net.minecraft.network.chat.Component.translatable(draw > 0 ? "jade.bsp_core.extractor_draw" : "jade.bsp_core.extractor_idle", draw)
                        .withStyle(draw > 0 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY));
            } else if (d.contains("PlasmaTank")) {
                int tank = d.getInt("PlasmaTank"), cap = Math.max(1, d.getInt("PlasmaCap"));
                tooltip.add(net.minecraft.network.chat.Component.translatable("jade.bsp_core.base_tank", tank, cap)
                        .withStyle(tank > 0 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY));
            } else if (d.contains("ValveLimit")) {
                boolean open = d.getBoolean("ValveOpen");
                tooltip.add(net.minecraft.network.chat.Component.translatable(open ? "jade.bsp_core.valve_limit" : "jade.bsp_core.valve_shut", d.getInt("ValveLimit"))
                        .withStyle(open ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.RED));
            } else if (d.contains("TankFormed")) {
                if (d.getBoolean("TankFormed")) {
                    tooltip.add(net.minecraft.network.chat.Component.translatable("jade.bsp_core.tank_level", String.format("%,d", d.getLong("TankStored")), String.format("%,d", d.getLong("TankCap")))
                            .withStyle(d.getLong("TankStored") > 0 ? net.minecraft.ChatFormatting.AQUA : net.minecraft.ChatFormatting.GRAY));
                } else {
                    tooltip.add(net.minecraft.network.chat.Component.translatable("jade.bsp_core.tank_unformed").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                if (d.contains("PortMode")) {
                    var mode = com.mrgregles.bsp_core.tank.TankPortBlockEntity.Mode.values()[Math.min(2, Math.max(0, d.getByte("PortMode")))];
                    tooltip.add(net.minecraft.network.chat.Component.translatable("jade.bsp_core.tank_port", net.minecraft.network.chat.Component.translatable(mode.key()), d.getInt("PortIn"), d.getInt("PortOut"))
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                }
            }
        }

        @Override
        public net.minecraft.resources.ResourceLocation getUid() {
            return UID;
        }
    }

    private static Accessor<?> redirect(IWailaClientRegistration registration, @Nullable Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor target)) {
            return accessor;
        }
        BlockState state = target.getBlockState();
        if (target.getBlockEntity() instanceof DecoyTotemBlockEntity decoy) {
            if (decoy.isActive() && !decoy.isOwner(target.getPlayer())) {
                return registration.blockAccessor().from(target).blockState(ModBlocks.SHATTER_TOTEM.get().defaultBlockState()
                        .setValue(ShatterTotemBlock.FACING, state.getValue(DecoyTotemBlock.FACING))).build();
            }
            return accessor;
        }
        if (state.hasProperty(StructurePartBlock.FORMED) && state.getValue(StructurePartBlock.FORMED)) {
            BlockEntity controller = controllerOf(target.getLevel(), target.getPosition());
            if (controller != null && !controller.getBlockPos().equals(target.getPosition())) {
                BlockHitResult old = target.getHitResult();
                return registration.blockAccessor().from(target)
                        .hit(new BlockHitResult(old.getLocation(), old.getDirection(), controller.getBlockPos(), old.isInside()))
                        .blockState(controller.getBlockState()).blockEntity(controller).build();
            }
        }
        return accessor;
    }

    private static BlockPos lastPart = BlockPos.ZERO, lastController;
    private static long lastLook = Long.MIN_VALUE;

    /** The controller of the assembled machine that {@code part} belongs to. The answer is kept for a second, as Jade asks every tick. */
    @Nullable
    private static BlockEntity controllerOf(Level level, BlockPos part) {
        long now = level.getGameTime();
        if (!part.equals(lastPart) || now - lastLook >= 20 || now < lastLook) {
            lastPart = part.immutable();
            lastLook = now;
            lastController = null;
            search:
            for (int cx = (part.getX() >> 4) - 1; cx <= (part.getX() >> 4) + 1; cx++) {
                for (int cz = (part.getZ() >> 4) - 1; cz <= (part.getZ() >> 4) + 1; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (owns(be, part)) {
                            lastController = be.getBlockPos();
                            break search;
                        }
                    }
                }
            }
        }
        return lastController == null ? null : level.getBlockEntity(lastController);
    }

    private static boolean owns(BlockEntity be, BlockPos part) {
        if (be.getBlockPos().equals(part)) {
            return be instanceof MultiblockControllerBlockEntity || be instanceof CoinFactoryBlockEntity;
        }
        if (be instanceof CoinFactoryBlockEntity factory) {
            return factory.isFormed() && factory.parts().stream().anyMatch(p -> p.pos().equals(part));
        }
        if (be instanceof MultiblockControllerBlockEntity machine && machine.isFormed()) {
            if (machine.parts().stream().anyMatch(p -> p.pos().equals(part))) {
                return true;
            }
            if (machine instanceof MagneticCentrifugeBlockEntity centrifuge) {
                for (int n = 1; n < centrifuge.layers(); n++) {
                    if (centrifuge.upperLayer(n).stream().anyMatch(p -> p.pos().equals(part))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
