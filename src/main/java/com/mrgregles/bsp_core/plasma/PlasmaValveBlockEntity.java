package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.network.ValveViewPacket;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The valve's settings and what the interface last reported through it: the limit (mB/t that may pass), whether a
 * redstone signal may shut it, the pressure offered on its inlet side and what goes out, and which end is the inlet.
 */
public class PlasmaValveBlockEntity extends BlockEntity {
    private int limit = maxLimit(), in, out;
    private boolean redstone = true;
    @Nullable
    private Direction inlet;
    private final Set<UUID> users = new HashSet<>();
    private long reportedAt = -1000;

    public PlasmaValveBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_VALVE.get(), pos, state);
    }

    public static int maxLimit() {
        return BSPConfig.getOr(BSPConfig.VALVE_MAX, 2000);
    }

    public int limit() {
        return limit;
    }

    public boolean redstone() {
        return redstone;
    }

    public int in() {
        return in;
    }

    public int out() {
        return out;
    }

    @Nullable
    public Direction inlet() {
        return inlet;
    }

    public boolean powered() {
        return getBlockState().hasProperty(PlasmaValveBlock.POWERED) && getBlockState().getValue(PlasmaValveBlock.POWERED);
    }

    /** Whether plasma may pass: a limit above zero, and no redstone signal while redstone control is on. */
    public boolean open() {
        return limit > 0 && !(redstone && powered());
    }

    /** Owners and friends with Machines access on the totem feeding it may open it; until the interface has reported, anyone may. */
    public boolean mayUse(Player player) {
        return users.isEmpty() || users.contains(player.getUUID()) || Admins.isAdmin(player);
    }

    public void set(int limit, boolean redstone) {
        this.limit = Math.max(0, Math.min(maxLimit(), limit));
        this.redstone = redstone;
        sync();
    }

    /** The interface's report each second: the pressure offered at the inlet, what passes out, and which side the plasma comes from. */
    public void report(int in, int out, @Nullable Direction inlet, @Nullable PlasmaAccess access) {
        if (level != null) {
            reportedAt = level.getGameTime();
        }
        if (access != null) {
            users.clear();
            users.addAll(access.users);
        }
        if (this.in == in && this.out == out && this.inlet == inlet) {
            return;
        }
        this.in = in;
        this.out = out;
        this.inlet = inlet;
        sync();
    }

    public ValveViewPacket view() {
        return new ValveViewPacket(worldPosition, limit, maxLimit(), redstone, powered(), in, out);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void write(CompoundTag tag) {
        tag.putInt("Limit", limit);
        tag.putBoolean("Redstone", redstone);
        tag.putInt("In", in);
        tag.putInt("Out", out);
        tag.putInt("Inlet", inlet == null ? -1 : inlet.get3DDataValue());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        long[] ids = new long[users.size() * 2];
        int i = 0;
        for (UUID u : users) {
            ids[i++] = u.getMostSignificantBits();
            ids[i++] = u.getLeastSignificantBits();
        }
        tag.putLongArray("Users", ids);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        limit = tag.contains("Limit") ? tag.getInt("Limit") : maxLimit();
        redstone = !tag.contains("Redstone") || tag.getBoolean("Redstone");
        in = tag.getInt("In");
        out = tag.getInt("Out");
        int d = tag.contains("Inlet") ? tag.getInt("Inlet") : -1;
        inlet = d < 0 ? null : Direction.from3DDataValue(d);
        users.clear();
        long[] ids = tag.getLongArray("Users");
        for (int i = 0; i + 1 < ids.length; i += 2) {
            users.add(new UUID(ids[i], ids[i + 1]));
        }
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
