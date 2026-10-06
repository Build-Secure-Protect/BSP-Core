package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;

/**
 * One drum of a stack of extractors under a totem. Once a second it finds the totem at the top of its
 * stack, takes its even share of the totem's output into its own tank, and remembers the totem's
 * owner and Base powers so a Plasma Interface touching it can offer them.
 *
 * <p>The tank is deliberately not a fluid capability: nothing but a Plasma Interface can take from it.
 */
public class PlasmaExtractorBlockEntity extends BlockEntity {
    private int tank;
    /** mB per tick this drum is receiving right now; 0 when there is no owned totem above the stack. */
    private int flow;
    @Nullable
    private UUID owner;
    @Nullable
    private BlockPos totemPos;
    private final int[] offered = new int[Buff.values().length];
    /** Master position of the interface group this drum is served by, so a second group cannot also take from it. */
    private PlasmaAccess access = new PlasmaAccess();
    @Nullable
    private BlockPos servedBy;
    private long servedAt = -1000;

    public PlasmaExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_EXTRACTOR.get(), pos, state);
    }

    public int capacity() {
        return BSPConfig.getOr(BSPConfig.EXTRACTOR_TANK, 4000);
    }

    public int tank() {
        return tank;
    }

    public int flow() {
        return flow;
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    @Nullable
    public BlockPos totemPos() {
        return totemPos;
    }

    /** Level of each power the totem (or battery) above holds, by {@link Buff#ordinal()}; zero while nothing is above. */
    public int[] offered() {
        return offered;
    }

    public boolean isActive() {
        return flow > 0;
    }

    /** Who may edit, use or walk past the blocks this drum feeds, from its totem's access list. */
    public PlasmaAccess access() {
        return access;
    }

    /** Takes up to {@code mB} from the tank and returns what was taken. */
    public int drain(int mB) {
        int took = Math.min(mB, tank);
        tank -= took;
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    /** The interface group at {@code master} wants this drum. True if it may have it (nobody else holds it, or that group let go). */
    public boolean claim(BlockPos master) {
        if (servedBy != null && !servedBy.equals(master) && level != null && level.getGameTime() - servedAt <= 60) {
            return false;
        }
        servedBy = master.immutable();
        servedAt = level == null ? 0 : level.getGameTime();
        return true;
    }

    @Nullable
    public BlockPos servedBy() {
        return servedBy != null && level != null && level.getGameTime() - servedAt <= 60 ? servedBy : null;
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        // the stack: this drum and every extractor touching it above and below; the totem sits on the top one
        BlockPos top = worldPosition;
        while (sl.getBlockState(top.above()).getBlock() instanceof PlasmaExtractorBlock) {
            top = top.above();
        }
        int count = 0;
        for (BlockPos p = top; sl.getBlockState(p).getBlock() instanceof PlasmaExtractorBlock; p = p.below()) {
            count++;
        }
        int oldFlow = flow;
        UUID oldOwner = owner;
        flow = 0;
        Arrays.fill(offered, 0);
        owner = null;
        totemPos = null;
        access = new PlasmaAccess();
        BlockEntity above = sl.getBlockEntity(top.above());
        if (above instanceof ShatterTotemBlockEntity totem && totem.getOwner().isPresent()) {
            owner = totem.getOwner().get().uuid();
            totemPos = top.above();
            access = PlasmaAccess.of(totem);
            flow = Math.max(1, totem.plasmaOutput() / Math.max(1, count));
            for (Buff b : Buff.values()) {
                offered[b.ordinal()] = totem.getUpgradeLevel(b);
            }
            tank = Math.min(capacity(), tank + flow * 20);
        } else if (above instanceof PlasmaBatteryBlockEntity battery && battery.stored() > 0) {
            // a battery feeds the stack like a totem would, at the battery rate, with the powers stamped into it
            int rate = Math.max(1, BSPConfig.getOr(BSPConfig.BATTERY_FEED, 100) / Math.max(1, count));
            int took = battery.take(Math.min(rate * 20, Math.max(0, capacity() - tank)));
            flow = took / 20;
            System.arraycopy(battery.powers(), 0, offered, 0, offered.length);
            tank = Math.min(capacity(), tank + took);
            owner = null;
        }
        setChanged();
        if (oldFlow != flow || (oldOwner == null) != (owner == null)) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void write(CompoundTag tag) {
        tag.putInt("Tank", tank);
        tag.putInt("Flow", flow);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank = tag.getInt("Tank");
        flow = tag.getInt("Flow");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        write(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
