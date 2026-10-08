package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.chunk.ChunkLoading;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemAuras;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Projector at the end of a cable run from a Plasma Interface. The interface feeds it once a second
 * with the powers it offers and the plasma that arrives; the projector's owner chooses which powers
 * to receive here. It projects while at least {@code plasma.projectorNeed} mB/t arrives.
 *
 * <p>With repeaters in the run the choice narrows: one power arrives at full level, two or more each
 * arrive one level lower per repeater. A Channel Expander fitted in the projector adds a channel.
 */
public class TotemProjectorBlockEntity extends BlockEntity {
    /** The powers a projector can receive, in screen order. Indexes into {@link #chosen}. */
    public static final Buff[] SENDABLE = {Buff.FORTIFY, Buff.HEALING, Buff.ALARM, Buff.WARD, Buff.SANCTUARY, Buff.OVERCLOCK, Buff.ANCHOR};
    private static final int FEED_TICKS = 50;

    private final int[] levels = new int[Buff.values().length];
    private final int[] offered = new int[SENDABLE.length];
    /** Bit {@code i} set: {@link #SENDABLE}[i] is chosen. */
    private int chosen;
    private boolean expander;
    private int repeaters, delivered, tank;
    @Nullable
    private BlockPos source, anchorTotem;
    private com.mrgregles.bsp_core.plasma.PlasmaAccess access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
    private long fedAt = -1000;
    private boolean active, signal, powered;
    private final Set<UUID> alarmed = new HashSet<>();

    public TotemProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOTEM_PROJECTOR.get(), pos, state);
    }

    // ------------------------------------------------------------------ what the interface tells it

    /** Fed from the Projector Base below once a second: {@code deliveredPerTick} is what the base could give this second. */
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, com.mrgregles.bsp_core.plasma.PlasmaAccess access, @Nullable BlockPos anchorTotem) {
        int[] was = offered.clone();
        for (int i = 0; i < SENDABLE.length; i++) {
            offered[i] = offeredByOrdinal[SENDABLE[i].ordinal()];
        }
        boolean changed = !master.equals(source) || !Arrays.equals(was, offered) || this.repeaters != repeaters || this.delivered != deliveredPerTick || !this.access.editors.equals(access.editors);
        source = master.immutable();
        this.repeaters = repeaters;
        this.delivered = deliveredPerTick;
        this.access = access;
        this.anchorTotem = anchorTotem;
        fedAt = level == null ? 0 : level.getGameTime();
        recompute();
        if (changed) {
            sync();
        }
    }

    /** Called by the base with its feed: whether it had a full second's worth of plasma for the projector. */
    public void power(boolean enough) {
        powered = enough;
    }

    /** Drops choices the interface no longer offers or beyond the channels, then works out what arrives. */
    private void recompute() {
        int kept = 0;
        for (int i = 0; i < SENDABLE.length; i++) {
            if ((chosen & (1 << i)) != 0 && (offered[i] <= 0 || kept >= channels())) {
                chosen &= ~(1 << i);
            } else if ((chosen & (1 << i)) != 0) {
                kept++;
            }
        }
        Arrays.fill(levels, 0);
        for (int i = 0; i < SENDABLE.length; i++) {
            if ((chosen & (1 << i)) != 0) {
                levels[SENDABLE[i].ordinal()] = arriving(i);
            }
        }
    }

    /** The level of {@link #SENDABLE}[i] that would arrive if it is chosen: full alone, lower by one per repeater with company. */
    public int arriving(int i) {
        int lvl = offered[i];
        if (lvl <= 0) {
            return 0;
        }
        return repeaters == 0 || Integer.bitCount(chosen) <= 1 ? lvl : Math.max(0, lvl - repeaters);
    }

    public int capacity() {
        return BSPConfig.getOr(BSPConfig.PROJECTOR_TANK, 2000);
    }

    public int need() {
        return BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100);
    }

    public int channels() {
        return BSPConfig.getOr(BSPConfig.PROJECTOR_CHANNELS, 2) + (expander ? 1 : 0);
    }

    /** Switches power {@code index} of {@link #SENDABLE} on or off, within the number of channels. */
    public void toggle(int index) {
        if (index < 0 || index >= SENDABLE.length) {
            return;
        }
        int bit = 1 << index;
        if ((chosen & bit) != 0) {
            chosen &= ~bit;
        } else if (Integer.bitCount(chosen) < channels() && offered[index] > 0) {
            chosen |= bit;
        }
        recompute();
        sync();
    }

    public boolean hasExpander() {
        return expander;
    }

    /** Fits or removes the Channel Expander. Returns false if nothing changed. */
    public boolean setExpander(boolean fitted) {
        if (expander == fitted) {
            return false;
        }
        expander = fitted;
        recompute();
        sync();
        return true;
    }

    // ------------------------------------------------------------------ state

    public boolean isActive() {
        return active;
    }

    public boolean hasSignal() {
        return signal;
    }

    /** Level of {@code buff} being projected right now; 0 when idle. */
    public int level(Buff buff) {
        return active ? levels[buff.ordinal()] : 0;
    }

    /** Level of {@code buff} that will be projected once powered, whether or not plasma is short. */
    public int chosenLevel(Buff buff) {
        return signal ? levels[buff.ordinal()] : 0;
    }

    public int offered(int i) {
        return signal ? offered[i] : 0;
    }

    public int chosen() {
        return chosen;
    }

    public int repeaters() {
        return repeaters;
    }

    public int delivered() {
        return signal ? delivered : 0;
    }

    public int tank() {
        return tank;
    }

    @Nullable
    public BlockPos source() {
        return signal ? source : null;
    }

    public boolean isOwner(UUID player) {
        return access.editors.contains(player);
    }

    /** Whether the player may change what this projector receives and its chunks: an owner (or an Upgrades friend) of a totem on its interface, or an admin. */
    public boolean mayEdit(Player player) {
        return access.editors.isEmpty() || access.editors.contains(player.getUUID()) || Admins.isAdmin(player);
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        // the base below holds the plasma and feeds this projector from its own tick; here we only mirror its tank for the screen
        if (sl.getBlockEntity(worldPosition.below()) instanceof com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity base) {
            tank = base.tank();
        }
        boolean fed = source != null && sl.getGameTime() - fedAt <= FEED_TICKS, now = fed && powered;
        if (now) {
            effects(sl);
        }
        if (now != active || fed != signal) {
            active = now;
            signal = fed;
            sync();
        }
        ChunkLoading.projector(sl, worldPosition, now && levels[Buff.ANCHOR.ordinal()] > 0 ? anchorTotem : null);
    }

    /** Healing Aura for the owners; Ward and Alarm for everyone else. The other powers are answered through TotemAuras. */
    private void effects(ServerLevel sl) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        int heal = levels[Buff.HEALING.ordinal()], ward = levels[Buff.WARD.ordinal()], alarm = levels[Buff.ALARM.ordinal()];
        if (heal > 0) {
            int r = Buff.HEALING.radius(heal);
            for (UUID id : access.editors) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(id);
                if (p != null && p.level() == sl && p.getHealth() < p.getMaxHealth() && TotemAuras.inCube(p, x, y, z, r)) {
                    p.heal(BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), heal, 0.0).floatValue());
                }
            }
        }
        if (ward <= 0 && alarm <= 0) {
            return;
        }
        int wardR = Buff.WARD.reach(ward), alarmR = Buff.ALARM.reach(alarm);
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer p : sl.players()) {
            if (isOwner(p.getUUID()) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            if (ward > 0 && !access.wardSafe.contains(p.getUUID()) && TotemAuras.inCube(p, x, y, z, wardR)) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, (ward - 1) / 2, true, false, true));
            }
            if (alarm > 0 && !access.alarmSafe.contains(p.getUUID()) && TotemAuras.inCube(p, x, y, z, alarmR)) {
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, true));
                inside.add(p.getUUID());
                if (alarmed.add(p.getUUID())) {
                    for (UUID id : access.editors) {
                        NetworkStorage.tell(sl.getServer(), id, Component.translatable("message.bsp_core.alarm", p.getGameProfile().getName(),
                                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.YELLOW));
                    }
                }
            }
        }
        alarmed.retainAll(inside);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) {
            TotemAuras.register(sl, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel sl) {
            TotemAuras.unregisterProjector(sl, worldPosition);
        }
    }

    /** The aura cubes reach well past the block. */
    @Override
    public AABB getRenderBoundingBox() {
        int r = Math.max(Buff.FORTIFY.radius(levels[Buff.FORTIFY.ordinal()]), Buff.HEALING.radius(levels[Buff.HEALING.ordinal()]));
        return new AABB(worldPosition).inflate(Math.max(2, r + 1));
    }

    private void write(CompoundTag tag) {
        tag.putIntArray("Levels", levels);
        tag.putIntArray("Offered", offered);
        tag.putInt("Chosen", chosen);
        tag.putBoolean("Expander", expander);
        tag.putInt("Repeaters", repeaters);
        tag.putInt("Delivered", delivered);
        tag.putInt("Tank", tank);
        tag.putBoolean("Active", active);
        tag.putBoolean("Signal", signal);
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
        ListTag list = new ListTag();
        access.editors.forEach(id -> list.add(NbtUtils.createUUID(id)));
        tag.put("Owners", list);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        // fedAt and the anchor totem are saved (not synced) so a restart does not look like a lost signal and drop the projector's chunks
        tag.putLong("FedAt", fedAt);
        if (anchorTotem != null) {
            tag.putLong("Totem", anchorTotem.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] l = tag.getIntArray("Levels");
        if (l.length == levels.length) {
            System.arraycopy(l, 0, levels, 0, l.length);
        }
        int[] o = tag.getIntArray("Offered");
        if (o.length == offered.length) {
            System.arraycopy(o, 0, offered, 0, o.length);
        }
        chosen = tag.getInt("Chosen");
        expander = tag.getBoolean("Expander");
        repeaters = tag.getInt("Repeaters");
        delivered = tag.getInt("Delivered");
        tank = tag.getInt("Tank");
        active = tag.getBoolean("Active");
        signal = tag.getBoolean("Signal");
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
        for (Tag t : tag.getList("Owners", Tag.TAG_INT_ARRAY)) {
            access.editors.add(NbtUtils.loadUUID(t));
        }
        if (tag.contains("FedAt")) {
            fedAt = tag.getLong("FedAt");
            anchorTotem = tag.contains("Totem") ? BlockPos.of(tag.getLong("Totem")) : null;
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
