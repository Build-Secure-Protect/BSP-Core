package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One block of a Plasma Tank. Every block knows its master, the tank's lowest corner; the master also holds the size, the plasma
 * and what each port is doing, and syncs those to clients so the plasma can be drawn as one body across the whole tank.
 */
public class TankPartBlockEntity extends BlockEntity {
    @Nullable
    protected BlockPos master;
    protected int w, h, d;
    protected long stored;
    /** Port position to {mode, in mB/t, out mB/t}, kept by the master for the screen and the streams. */
    protected final Map<BlockPos, int[]> ports = new LinkedHashMap<>();
    /** Game time the tank last formed and the block that completed it: the renderer runs a sweep of light from there for a second. */
    protected long formedAt = -1000;
    @Nullable
    protected BlockPos origin;
    /** Client: the level the renderer last showed, so it glides. */
    public float shown = -1;
    private long syncedStored = -1;
    private boolean dirty;

    public TankPartBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.TANK_PART.get(), pos, state);
    }

    protected TankPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static long perBlock() {
        return BSPConfig.getOr(BSPConfig.TANK_PER_BLOCK, 2_500_000);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    public boolean isMaster() {
        return master != null && master.equals(worldPosition);
    }

    /** The master block entity of the tank this block belongs to, or null while not formed. */
    @Nullable
    public TankPartBlockEntity masterEntity() {
        if (master == null || level == null) {
            return null;
        }
        return level.getBlockEntity(master) instanceof TankPartBlockEntity m ? m : null;
    }

    void setMaster(@Nullable BlockPos master) {
        this.master = master == null ? null : master.immutable();
        if (master == null) {
            ports.clear();
        }
        sync();
    }

    void setDims(int w, int h, int d) {
        this.w = w;
        this.h = h;
        this.d = d;
        sync();
    }

    void setFormed(long gameTime, BlockPos origin) {
        formedAt = gameTime;
        this.origin = origin.immutable();
    }

    public long formedAt() {
        return formedAt;
    }

    @Nullable
    public BlockPos origin() {
        return origin;
    }

    public int w() {
        return w;
    }

    public int h() {
        return h;
    }

    public int d() {
        return d;
    }

    /** Blocks in the shell: every one holds {@link #perBlock()}. */
    public int shellBlocks() {
        return w * h * d - Math.max(0, w - 2) * Math.max(0, h - 2) * Math.max(0, d - 2);
    }

    public long capacity() {
        return shellBlocks() * perBlock();
    }

    public long stored() {
        return stored;
    }

    void setStored(long mB) {
        stored = Math.max(0, Math.min(capacity(), mB));
        setChanged();
    }

    /** Puts up to {@code mB} in; returns what fitted. */
    public long add(long mB) {
        long took = Math.max(0, Math.min(mB, capacity() - stored));
        stored += took;
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    /** Takes up to {@code mB} out; returns what came. */
    public long drain(long mB) {
        long took = Math.max(0, Math.min(mB, stored));
        stored -= took;
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    public Map<BlockPos, int[]> ports() {
        return ports;
    }

    /** A port tells the master what it did this second. */
    void portReport(BlockPos port, int mode, int in, int out) {
        int[] was = ports.get(port);
        if (was == null || was[0] != mode || was[1] != in || was[2] != out) {
            ports.put(port.immutable(), new int[]{mode, in, out});
            dirty = true;
        }
    }

    void portGone(BlockPos port) {
        if (ports.remove(port) != null) {
            dirty = true;
        }
    }

    public void serverTick(ServerLevel sl) {
        if (!isMaster() || sl.getGameTime() % 20 != 0) {
            return;
        }
        if (dirty || stored != syncedStored) {
            dirty = false;
            syncedStored = stored;
            sync();
        }
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (isMaster()) {
            return new AABB(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), worldPosition.getX() + w, worldPosition.getY() + h, worldPosition.getZ() + d);
        }
        return super.getRenderBoundingBox();
    }

    protected void write(CompoundTag tag) {
        if (master != null) {
            tag.putLong("Master", master.asLong());
        }
        tag.putInt("W", w);
        tag.putInt("H", h);
        tag.putInt("D", d);
        tag.putLong("Stored", stored);
        tag.putLong("FormedAt", formedAt);
        if (origin != null) {
            tag.putLong("Origin", origin.asLong());
        }
        ListTag list = new ListTag();
        ports.forEach((p, v) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", p.asLong());
            t.putIntArray("V", v);
            list.add(t);
        });
        tag.put("Ports", list);
    }

    protected void read(CompoundTag tag) {
        master = tag.contains("Master") ? BlockPos.of(tag.getLong("Master")) : null;
        w = tag.getInt("W");
        h = tag.getInt("H");
        d = tag.getInt("D");
        stored = tag.getLong("Stored");
        formedAt = tag.contains("FormedAt") ? tag.getLong("FormedAt") : -1000;
        origin = tag.contains("Origin") ? BlockPos.of(tag.getLong("Origin")) : null;
        ports.clear();
        for (Tag t : tag.getList("Ports", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            int[] v = c.getIntArray("V");
            if (v.length == 3) {
                ports.put(BlockPos.of(c.getLong("Pos")), v);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        read(tag);
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
