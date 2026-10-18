package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.plasma.PlasmaAccess;
import com.mrgregles.bsp_core.plasma.PlasmaNetwork;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The back block of a Plasma Decelerator: a source for the cable runs leaving it, like a Tank Port giving out of its tank. Once a
 * second it pushes what the core received through the link into the cables on its free faces, with the powers the plasma carries,
 * the feeding totem's access list and its Anchor totem (in whatever world it stands), by the same fair-share rules an interface uses.
 */
public class DeceleratorPortBlockEntity extends LinkPartBlockEntity implements PlasmaNetwork.Reservoir {
    private final PlasmaNetwork network = new PlasmaNetwork(this, this);
    private int out;

    public DeceleratorPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECELERATOR_PORT.get(), pos, state);
    }

    @Nullable
    private DeceleratorBlockEntity core() {
        return controller() != null && level != null && level.getBlockEntity(controller()) instanceof DeceleratorBlockEntity core && core.isFormed() ? core : null;
    }

    public PlasmaNetwork network() {
        return network;
    }

    /** mB/t sent into the runs leaving the port, last second. */
    public int out() {
        return out;
    }

    // ---- as a source

    @Override
    public int flow() {
        DeceleratorBlockEntity core = core();
        return core == null ? 0 : (int) Math.min(AcceleratorBlockEntity.acceleratorMax(), core.store().stored() / 20);
    }

    @Override
    public int tank() {
        DeceleratorBlockEntity core = core();
        return core == null ? 0 : (int) Math.min(Integer.MAX_VALUE, core.store().stored());
    }

    @Override
    public int drain(int mB) {
        DeceleratorBlockEntity core = core();
        return core == null ? 0 : (int) core.store().drain(mB);
    }

    @Override
    public boolean claim(BlockPos by) {
        return true;
    }

    @Override
    public PlasmaAccess access() {
        DeceleratorBlockEntity core = core();
        return core == null ? new PlasmaAccess() : core.access().copy();
    }

    @Override
    public int[] offered() {
        DeceleratorBlockEntity core = core();
        return core == null ? new int[Buff.values().length] : core.store().offered();
    }

    @Nullable
    @Override
    public BlockPos totemPos() {
        return null;
    }

    @Override
    public int cloak() {
        return 0;
    }

    @Nullable
    @Override
    public GlobalPos anchor() {
        DeceleratorBlockEntity core = core();
        return core == null ? null : core.anchorTotem();
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        DeceleratorBlockEntity core = core();
        int was = out;
        if (core != null && core.store().stored() > 0) {
            network.tick(sl);
            out = network.lastDeliveredTotal();
        } else {
            out = 0;
        }
        if (out != was) {
            setChanged();
            if (core != null) {
                core.portReport(out);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Out", out);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        out = tag.getInt("Out");
    }
}
