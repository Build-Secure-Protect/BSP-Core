package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.plasma.PlasmaAccess;
import com.mrgregles.bsp_core.plasma.PlasmaNetwork;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * The Tank Extractor (Separator Drum): set against a Tank Port, it sends one of the tank's pools into the cables touching it, with that
 * pool's power: plain plasma (no power), the totem pool (the totem's powers), or one mob power's pool at its highest level. Like a port
 * it pushes at most an interface face's worth a tick, and never into the ports of its own tank. Plasma it sends through an Accelerator
 * keeps its power in the far world.
 */
public class TankExtractorBlockEntity extends BlockEntity implements PlasmaNetwork.Reservoir {
    /** The mob powers in pool order: pool 2 is the first, and so on. */
    public static final List<Buff> MOB_POOLS = java.util.Arrays.stream(Buff.values()).filter(b -> !b.onTotem()).toList();
    public static final int PLAIN = 0, TOTEM = 1, POOLS = 2 + MOB_POOLS.size();

    private int pool;
    private int out;
    /** Who placed it: they, their team and their allies are the team of the plasma it sends (a Projector it feeds knows them). */
    @Nullable
    private java.util.UUID owner;
    /** The level of the mob pool being sent this second (named once a second to the network, so lower levels never go out under it). */
    private int sendingLevel;
    private final PlasmaNetwork network = new PlasmaNetwork(this, this);
    @Nullable
    private BlockPos tankMaster;

    public TankExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK_EXTRACTOR.get(), pos, state);
        // never back into the tank it draws from
        network.receiverFilter(p -> !(level != null && level.getBlockEntity(p) instanceof TankPartBlockEntity part && tankMaster != null && Objects.equals(part.master(), tankMaster)));
    }

    /** The mob power of pool {@code index}, or null for plain or totem. */
    @Nullable
    public static Buff mobOf(int index) {
        return index >= 2 && index < POOLS ? MOB_POOLS.get(index - 2) : null;
    }

    /** The pool's name: Plain plasma, Totem powers, or the power's own. */
    public static Component poolName(int index) {
        Buff b = mobOf(index);
        return b != null ? Component.translatable(b.translationKey()) : Component.translatable(index == TOTEM ? "gui.bsp_core.pool.totem" : "gui.bsp_core.pool.plain");
    }

    public int pool() {
        return pool;
    }

    public void setPool(int index) {
        pool = Math.floorMod(index, POOLS);
        sendingLevel = 0; // another pool: its level is named afresh
        setChanged();
        if (level != null && getBlockState().hasProperty(TankExtractorBlock.POOL) && getBlockState().getValue(TankExtractorBlock.POOL) != pool) {
            level.setBlock(worldPosition, getBlockState().setValue(TankExtractorBlock.POOL, pool), 3);
        }
    }

    public int out() {
        return out;
    }

    public void setOwner(@Nullable java.util.UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Nullable
    public java.util.UUID owner() {
        return owner;
    }

    /** The master of the tank whose Tank Port this block touches, or null. A port set to Input gives nothing out, here as to cables. */
    @Nullable
    public TankPartBlockEntity attachedTank() {
        if (level == null) {
            return null;
        }
        for (Direction d : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(d)) instanceof TankPortBlockEntity port && port.mode() != TankPortBlockEntity.Mode.INPUT) {
                TankPartBlockEntity m = port.masterEntity();
                if (m != null) {
                    return m;
                }
            }
        }
        return null;
    }

    /** How much the chosen pool holds that would leave now: for a mob power, its highest level only. */
    public long available() {
        TankPartBlockEntity m = attachedTank();
        if (m == null) {
            return 0;
        }
        Buff b = mobOf(pool);
        if (b != null) {
            return m.pools().mbAt(b, sendingLevel > 0 ? sendingLevel : m.pools().level(b));
        }
        return pool == TOTEM ? m.pools().totem() : m.pools().plain();
    }

    /** Names the mob pool's level this second goes out at: its top level now. Until the next second, only that level leaves. */
    public void nameLevel() {
        TankPartBlockEntity m = attachedTank();
        Buff mob = mobOf(pool);
        sendingLevel = m != null && mob != null ? m.pools().level(mob) : 0;
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        TankPartBlockEntity m = attachedTank();
        tankMaster = m == null ? null : m.master();
        nameLevel();
        if (m != null && flow() > 0) { // too little to move a mB a tick: nothing is sent, and no power is named with nothing
            network.tick(sl);
            out = network.lastDeliveredTotal();
        } else {
            out = 0;
        }
    }

    // ---- as the network's one reservoir

    @Override
    public int flow() {
        return (int) Math.min(BSPConfig.getOr(BSPConfig.INTERFACE_SIDE_MAX, 1000), Math.min(Integer.MAX_VALUE, available() / 20));
    }

    @Override
    public int tank() {
        return (int) Math.min(Integer.MAX_VALUE, available());
    }

    @Override
    public int drain(int mB) {
        TankPartBlockEntity m = attachedTank();
        if (m == null || mB <= 0) {
            return 0;
        }
        Buff b = mobOf(pool);
        long took;
        if (b != null) {
            // only the level named this second (the top one, before the first second): what leaves carries that level, no more
            took = m.pools().drainMobAt(b, sendingLevel > 0 ? sendingLevel : m.pools().level(b), mB);
        } else if (pool == TOTEM) {
            took = m.pools().drainTotem(mB);
        } else {
            took = m.pools().drainPlain(mB);
        }
        if (took > 0) {
            m.setChanged();
            m.forgetFeeders(); // the last of the totem plasma gone takes who fed it and its Anchor totem with it at once
        }
        return (int) took;
    }

    @Override
    public boolean sendsMob() {
        return mobOf(pool) != null;
    }

    /** A mob pool goes out as that power's plasma by amount, at the level named this second; plain and totem plasma as before. */
    @Override
    public int drain(int mB, long[][] mob) {
        Buff b = mobOf(pool);
        int lvl = b == null ? 0 : sendingLevel > 0 ? sendingLevel : attachedTank() == null ? 0 : attachedTank().pools().level(b);
        int took = drain(mB);
        if (b != null && lvl > 0 && took > 0) {
            mob[b.ordinal()][lvl] += took;
        }
        return took;
    }

    @Override
    public boolean claim(BlockPos by) {
        return true;
    }

    /** The player who placed it, their team and allies, and whoever fed the tank. */
    @Override
    public PlasmaAccess access() {
        PlasmaAccess out = owner != null && level instanceof ServerLevel sl ? PlasmaAccess.forPlayer(sl.getServer(), owner) : new PlasmaAccess();
        TankPartBlockEntity m = attachedTank();
        if (m != null) {
            out.addAll(m.fedBy());
        }
        return out;
    }

    @Override
    public int[] offered() {
        int[] o = new int[Buff.values().length];
        TankPartBlockEntity m = attachedTank();
        if (m == null) {
            return o;
        }
        Buff b = mobOf(pool);
        if (b != null) {
            o[b.ordinal()] = sendingLevel > 0 ? sendingLevel : m.pools().level(b);
        } else if (pool == TOTEM) {
            return m.pools().offered();
        }
        return o;
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

    /**
     * Set to the totem pool, it passes on the totem whose Anchor that plasma carries, but only when the player who placed it is one
     * of those who fed the tank (an owner or Upgrades friend of the feeding totem): its own access lets its placer run what it feeds.
     */
    @Nullable
    @Override
    public net.minecraft.core.GlobalPos anchor() {
        TankPartBlockEntity m = attachedTank();
        return m == null || pool != TOTEM || owner == null || !m.fedBy().editors.contains(owner) ? null : m.anchorTotem();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putByte("Pool", (byte) pool);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        pool = Math.floorMod(tag.getByte("Pool"), POOLS);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
