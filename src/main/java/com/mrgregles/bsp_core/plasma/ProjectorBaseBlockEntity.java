package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
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

/**
 * Fed by an interface like a charger: it keeps the plasma in its tank and remembers what the interface
 * offers. The Projector standing on it drains the tank once a second and takes the rest of the feed
 * from here. Nothing is projected by the base itself.
 *
 * <p>Plasma carrying a Mob Chamber power is kept apart in the tank by amount ({@link MobPlasma}), with room of its own
 * ({@link #mobCapacity()}), so plain plasma from an interface never crowds it out. Each second each mob power that has a second's
 * burn of its own plasma ({@code plasma.projectorUse}) pays it, highest levels first, and is given at the lowest level it paid with;
 * a power with less is not given, and once none of it arrives any more what is left of it burns as plain plasma. Plain and totem
 * plasma pay what the mob powers did not of the projector's own burn.
 *
 * <p>Several runs may feed one base (an interface with the totem's powers and a Tank Extractor with a mob power): what each says is
 * kept apart by its source and joined while it keeps feeding, so one never wipes another's powers, team or Anchor.
 */
public class ProjectorBaseBlockEntity extends BlockEntity implements PlasmaReceiver {
    private static final int FEED_TICKS = 50;
    private int tank, delivered, repeaters;
    private final int[] offered = new int[Buff.values().length];
    /** Of the tank, what carries a Mob Chamber power, by power and level. */
    private long[][] mob = MobPlasma.empty();
    private PlasmaAccess access = new PlasmaAccess();
    /** What each run feeding the base said, by its source (not saved: a run feeds again within a second or two). */
    private final java.util.Map<BlockPos, Feed> feeds = new java.util.HashMap<>();
    /** Mob powers some of whose plasma came in since the last burn (not saved). */
    private final boolean[] mobArrived = new boolean[MobPlasma.N];
    @Nullable
    private BlockPos source;
    @Nullable
    private net.minecraft.core.GlobalPos anchorTotem;
    private long fedAt = -1000;
    private boolean signal;
    private int syncedTank = -1;
    /** Client only: the level the renderer is showing, gliding toward the synced tank. */
    public float shown;

    private record Feed(int[] offered, PlasmaAccess access, @Nullable net.minecraft.core.GlobalPos anchor, int repeaters, int delivered, long at) {}

    public ProjectorBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROJECTOR_BASE.get(), pos, state);
    }

    public int capacity() {
        return BSPConfig.getOr(BSPConfig.PROJECTOR_TANK, 2000);
    }

    /** All the plasma in the base: plain and totem plasma, and the mob powers' plasma kept apart. */
    public int tank() {
        return tank;
    }

    /** Room for the mob powers' plasma, beside the plain tank: two seconds' burn for every mob power at once, at least the tank's size. */
    public long mobCapacity() {
        long use = BSPConfig.getOr(BSPConfig.PROJECTOR_USE, 20) * 20L, n = 0;
        for (int i = 0; i < MobPlasma.N; i++) {
            if (MobPlasma.isMob(i)) {
                n++;
            }
        }
        return Math.max(capacity(), 2 * use * n);
    }

    private long plain() {
        return Math.max(0, tank - MobPlasma.total(mob));
    }

    public boolean hasSignal() {
        return signal;
    }

    public int repeaters() {
        return repeaters;
    }

    public int[] offered() {
        return offered;
    }

    public PlasmaAccess access() {
        return access;
    }

    @Nullable
    public BlockPos source() {
        return source;
    }

    @Nullable
    public net.minecraft.core.GlobalPos anchorTotem() {
        return anchorTotem;
    }

    /** Of the tank, how much carries mob power {@code b} at {@code level}. */
    public long mob(Buff b, int level) {
        return level < 1 || level >= MobPlasma.LEVELS ? 0 : mob[b.ordinal()][level];
    }

    /** Takes up to {@code mB} from the tank and returns what was taken: plain and totem plasma first, then mob-power plasma in proportion. */
    public int drain(int mB) {
        int took = Math.min(mB, tank);
        long free = tank - MobPlasma.total(mob);
        if (took > free) {
            MobPlasma.takeShare(mob, took - Math.max(0, free), null);
        }
        tank -= took;
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    /** Keeps the mob-power plasma within the tank and its room (after the tank lost plasma some other way). */
    private void fitMob() {
        long total = MobPlasma.total(mob), most = Math.min(tank, mobCapacity());
        if (total > most) {
            MobPlasma.takeShare(mob, total - most, null);
        }
    }

    /**
     * The projector's second: each mob power with a full burn of its own plasma pays it from its highest levels and is given at the
     * lowest level it paid with (a trickle of level III beside plenty of level I gives level I); a power that cannot pay and got no
     * more plasma this second lets what it has left burn as plain plasma. Then plain and totem plasma pay the rest of the projector's burn. Returns the levels given by Buff ordinal,
     * and puts the plasma burnt in all in {@code paid[0]}.
     */
    private int[] burnMob(int use, int[] paid) {
        int[] given = new int[Buff.values().length];
        long mobPaid = 0;
        for (int i = 0; i < MobPlasma.N; i++) {
            if (!MobPlasma.isMob(i)) {
                continue;
            }
            boolean arrived = mobArrived[i];
            mobArrived[i] = false;
            long have = 0;
            for (int l = 1; l < MobPlasma.LEVELS; l++) {
                have += mob[i][l];
            }
            if (have < use) {
                if (!arrived) {
                    java.util.Arrays.fill(mob[i], 0); // none of it is coming any more: what is left is only plasma
                }
                continue;
            }
            long left = use;
            int low = 0;
            for (int l = MobPlasma.LEVELS - 1; l >= 1 && left > 0; l--) {
                long t = Math.min(left, mob[i][l]);
                if (t > 0) {
                    low = l;
                }
                mob[i][l] -= t;
                left -= t;
            }
            given[i] = low;
            mobPaid += use;
        }
        tank -= (int) Math.min(tank, mobPaid);
        long free = Math.max(0, tank - MobPlasma.total(mob));
        int rest = (int) Math.min(free, Math.max(0, use - mobPaid));
        tank -= rest;
        paid[0] = (int) Math.min(Integer.MAX_VALUE, mobPaid + rest);
        if (paid[0] > 0) {
            setChanged();
        }
        return given;
    }

    /** Room for plain and totem plasma: the mob powers' plasma has its own. */
    @Override
    public int wanted() {
        return (int) Math.max(0, capacity() - plain());
    }

    @Override
    public int wantedMob() {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, mobCapacity() - MobPlasma.total(mob)));
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, int movedMB, PlasmaAccess access, @Nullable net.minecraft.core.GlobalPos anchorTotem) {
        feed(master, offeredByOrdinal, repeaters, deliveredPerTick, movedMB, access, anchorTotem, null);
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, int movedMB, PlasmaAccess access,
                     @Nullable net.minecraft.core.GlobalPos anchorTotem, @Nullable long[][] mobIn) {
        long mobMoved = 0;
        if (mobIn != null) {
            long room = Math.max(0, mobCapacity() - MobPlasma.total(mob)), came = Math.min(MobPlasma.total(mobIn), movedMB);
            long[][] fits = came <= room ? mobIn : MobPlasma.scaled(mobIn, room / (double) Math.max(1, came));
            mobMoved = Math.min(MobPlasma.total(fits), movedMB);
            MobPlasma.addInto(mob, fits);
            for (int i = 0; i < MobPlasma.N; i++) {
                for (int l = 1; l < MobPlasma.LEVELS; l++) {
                    if (fits[i][l] > 0) {
                        mobArrived[i] = true;
                    }
                }
            }
        }
        long plainIn = Math.max(0, Math.min(movedMB - mobMoved, capacity() - plain()));
        tank = (int) Math.min(Integer.MAX_VALUE, tank + mobMoved + plainIn);
        fitMob();
        long now = level == null ? 0 : level.getGameTime();
        feeds.put(master.immutable(), new Feed(MobPlasma.withoutMob(offeredByOrdinal), access == null ? new PlasmaAccess() : access.copy(),
                anchorTotem, repeaters, deliveredPerTick, now));
        join(now);
        fedAt = now;
        setChanged();
    }

    /** Joins what the runs still feeding said: every power at its highest, every team, the first Anchor; the source is the latest. */
    private void join(long now) {
        feeds.values().removeIf(f -> now - f.at() > FEED_TICKS);
        java.util.Arrays.fill(offered, 0);
        PlasmaAccess joined = new PlasmaAccess();
        net.minecraft.core.GlobalPos anchor = null;
        Feed latest = null;
        BlockPos latestAt = null;
        for (var e : feeds.entrySet()) {
            Feed f = e.getValue();
            for (int i = 0; i < offered.length && i < f.offered().length; i++) {
                offered[i] = Math.max(offered[i], f.offered()[i]);
            }
            joined.addAll(f.access());
            if (anchor == null) {
                anchor = f.anchor();
            }
            if (latest == null || f.at() > latest.at()) {
                latest = f;
                latestAt = e.getKey();
            }
        }
        if (latest == null) {
            return;
        }
        access = joined;
        anchorTotem = anchor;
        source = latestAt;
        repeaters = latest.repeaters();
        delivered = latest.delivered();
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        boolean fed = source != null && sl.getGameTime() - fedAt <= FEED_TICKS;
        if (sl.getBlockEntity(worldPosition.above()) instanceof com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity projector) {
            // the projector burns its second's worth from the tank; what the interface told us travels with it, so a cut run keeps it going until the tank is dry
            int use = BSPConfig.getOr(BSPConfig.PROJECTOR_USE, 20) * 20;
            int[] paid = new int[1], given = burnMob(use, paid); // each mob power its own burn, then the rest of the projector's
            int took = paid[0];
            if (source != null && (fed || took > 0)) {
                int[] send = offered.clone();
                for (int i = 0; i < send.length; i++) {
                    if (given[i] > 0) {
                        send[i] = given[i];
                    }
                }
                projector.feed(source, send, repeaters, fed ? delivered : 0, access, anchorTotem);
                projector.power(took >= use);
            }
        } else if (!fed && tank > 0) {
            tank = Math.max(0, tank - capacity() / 10); // nothing drawing on it and nothing feeding it: it empties on its own
            fitMob();
        }
        if (fed != signal || tank != syncedTank) {
            signal = fed;
            syncedTank = tank;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void write(CompoundTag tag) {
        tag.putInt("Tank", tank);
        tag.putBoolean("Signal", signal);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        tag.putLong("FedAt", fedAt);
        tag.putInt("Delivered", delivered);
        tag.putInt("Repeaters", repeaters);
        tag.putIntArray("Offered", offered);
        access.save(tag, "Access"); // who the plasma's powers know, so a projector fed from the tank after a restart still knows its team
        MobPlasma.save(tag, "Mob", mob);
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
        com.mrgregles.bsp_core.link.Links.save(tag, "Totem", anchorTotem);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank = tag.getInt("Tank");
        signal = tag.getBoolean("Signal");
        fedAt = tag.contains("FedAt") ? tag.getLong("FedAt") : -1000;
        delivered = tag.getInt("Delivered");
        repeaters = tag.getInt("Repeaters");
        int[] o = tag.getIntArray("Offered");
        // a save from before a power was added is shorter: keep what it has
        System.arraycopy(MobPlasma.withoutMob(o), 0, offered, 0, Math.min(o.length, offered.length));
        access = PlasmaAccess.load(tag, "Access");
        mob = MobPlasma.load(tag, "Mob");
        fitMob();
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        anchorTotem = com.mrgregles.bsp_core.link.Links.load(tag, "Totem");
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
