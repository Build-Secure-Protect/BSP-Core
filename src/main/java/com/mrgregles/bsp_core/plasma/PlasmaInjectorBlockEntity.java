package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The injector's state: what arrives, and the extra ticks it gives a machine from another mod.
 * <p>It is a {@link PlasmaReceiver} like a Projector Base, but keeps no tank: it asks for the top of the speed curve every
 * second and what actually arrives ({@code movedMB / 20}) is its rate. The rate is only good for {@link #FEED_TICKS} after
 * the last feed, so a cut cable or a stolen totem drops the machine back to normal speed within a second or two.
 * <p>BSP-Core blocks (namespace {@code bsp_core}) read the injector themselves through {@link PlasmaBoost#rate}, so they
 * keep their fuel, lava and RF accounting. Any other block entity with a ticker is ticked {@code factor - 1} extra times per
 * tick (carried fractionally), the way a time accelerator does, when {@code plasma.injectorForeign} allows it and the block
 * is not in {@code plasma.injectorBlacklist}. Machines that run on real time rather than ticks are not sped up by that.
 */
public class PlasmaInjectorBlockEntity extends BlockEntity implements PlasmaReceiver {
    private static final int FEED_TICKS = 50;
    /** Set while this injector is ticking its target, so an accelerated block can never accelerate back. */
    private static boolean accelerating;

    private int rate, syncedRate = -1;
    private long fedAt = -1000;
    private double carry;
    @Nullable
    private BlockPos source;

    public PlasmaInjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_INJECTOR.get(), pos, state);
    }

    /** The block this injector serves. */
    public BlockPos target() {
        Direction d = getBlockState().hasProperty(PlasmaInjectorBlock.FACING) ? getBlockState().getValue(PlasmaInjectorBlock.FACING) : Direction.DOWN;
        return worldPosition.relative(d);
    }

    /** mB/t arriving now, or 0 when the feed has stopped. */
    public int rate() {
        return level != null && level.getGameTime() - fedAt <= FEED_TICKS ? rate : 0;
    }

    public double factor() {
        return PlasmaBoost.factor(rate());
    }

    @Nullable
    public BlockPos source() {
        return source;
    }

    @Override
    public boolean accepts(BlockPos from, BlockState state, Direction d) {
        return PlasmaInjectorBlock.joins(state, d);
    }

    @Override
    public int wanted() {
        return PlasmaBoost.topRate() * 20; // a second's worth of the most the curve rewards
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access, @Nullable BlockPos anchorTotem) {
        source = master.immutable();
        rate = movedMB / 20;
        fedAt = level == null ? 0 : level.getGameTime();
        setChanged();
    }

    public void serverTick(ServerLevel sl) {
        int now = rate();
        if (now != syncedRate && sl.getGameTime() % 10 == 0) {
            syncedRate = now;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (now <= 0 || accelerating) {
            carry = 0;
            return;
        }
        BlockPos t = target();
        if (!sl.isLoaded(t)) {
            return;
        }
        BlockState ts = sl.getBlockState(t);
        var id = ForgeRegistries.BLOCKS.getKey(ts.getBlock());
        if (id == null || BSPCore.MODID.equals(id.getNamespace()) || !BSPConfig.getOr(BSPConfig.INJECTOR_FOREIGN, true)
                || BSPConfig.getOr(BSPConfig.INJECTOR_BLACKLIST, List.<String>of()).contains(id.toString())) {
            return; // BSP-Core machines pull the rate themselves; the rest only if allowed
        }
        BlockEntity be = sl.getBlockEntity(t);
        if (be == null) {
            return;
        }
        BlockEntityTicker<BlockEntity> ticker = ticker(ts, sl, be);
        if (ticker == null) {
            return;
        }
        carry += factor() - 1.0;
        int extra = (int) carry;
        carry -= extra;
        accelerating = true;
        try {
            for (int i = 0; i < extra && !be.isRemoved() && sl.getBlockState(t) == ts; i++) {
                ticker.tick(sl, t, ts, be);
            }
        } finally {
            accelerating = false;
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static BlockEntityTicker<BlockEntity> ticker(BlockState state, ServerLevel level, BlockEntity be) {
        return (BlockEntityTicker<BlockEntity>) state.getTicker(level, (BlockEntityType<BlockEntity>) be.getType());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Rate", rate);
        tag.putLong("FedAt", fedAt);
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        rate = tag.getInt("Rate");
        fedAt = tag.contains("FedAt") ? tag.getLong("FedAt") : -1000;
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Rate", rate());
        tag.putLong("FedAt", fedAt); // the client reads rate() too, for the renderer
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
