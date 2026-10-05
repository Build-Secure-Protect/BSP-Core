package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.chunk.ChunkLoading;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemAuras;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A Totem Projector: recreates some of a Shatter Totem's base powers as a second aura, wherever a
 * Totem Cable from a Totem Generator reaches it. It does nothing by itself: its generator tells it,
 * every second, which powers to project and at what level, and for whom. It needs RF of its own.
 *
 * <p>While it is projecting it counts as an aura source exactly like a placed totem (see
 * {@link TotemAuras}) for Fortify, Sanctuary and Overclock, and runs Healing Aura, Ward and Alarm
 * itself. It is not a totem: it cannot be stolen and scores nothing.
 */
public class TotemProjectorBlockEntity extends BlockEntity {
    public static final int CAPACITY = 50_000;
    private static final int FEED_TICKS = 50;

    private final Buffer energy = new Buffer();

    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 2_000, 0);
        }

        void use(int rf) {
            energy = Math.max(0, energy - rf);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) {
                setChanged();
            }
            return got;
        }
    }

    private final LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> energy);
    private final int[] levels = new int[Buff.values().length];
    @Nullable
    private BlockPos source;
    @Nullable
    private UUID owner;
    /** The totem behind the generator feeding it; known only while fed, and used for chunk loading. */
    @Nullable
    private BlockPos totemPos;
    private long fedAt = -1000;
    private boolean active;
    /** A generator is feeding it. With a signal but no RF of its own it stands by: lit amber, projecting nothing. Synced. */
    private boolean signal;
    private final Set<UUID> alarmed = new HashSet<>();

    public TotemProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOTEM_PROJECTOR.get(), pos, state);
    }

    // ------------------------------------------------------------------ what the generator tells it

    /** Whether the generator at {@code generator} may use this projector: it is free, already theirs, or its last generator has gone quiet. */
    public boolean accepts(BlockPos generator) {
        return source == null || source.equals(generator) || level == null || level.getGameTime() - fedAt > FEED_TICKS;
    }

    /** Called by the generator once a second while its totem, cable run and power are all good. */
    public void feed(BlockPos generator, UUID totemOwner, int[] arriving, BlockPos totem) {
        totemPos = totem;
        boolean changed = !generator.equals(source) || !totemOwner.equals(owner) || !Arrays.equals(arriving, levels);
        source = generator;
        owner = totemOwner;
        System.arraycopy(arriving, 0, levels, 0, levels.length);
        fedAt = level == null ? 0 : level.getGameTime();
        if (changed) {
            sync();
        }
        // A projector whose chunk is only loaded for the generator's cable check does not tick, so it could never
        // ask for its chunks back (after a power cut, say). Being fed with RF in store is enough to hold them again.
        if (level instanceof ServerLevel sl && levels[Buff.ANCHOR.ordinal()] > 0 && energy.getEnergyStored() >= BSPConfig.PROJECTOR_RF.get() * 20) {
            ChunkLoading.projector(sl, worldPosition, totem);
        }
    }

    // ------------------------------------------------------------------ state

    public boolean isActive() {
        return active;
    }

    public boolean hasSignal() {
        return signal;
    }

    /** What the projector is doing, for the player who right-clicks it. */
    public net.minecraft.network.chat.Component status() {
        int rf = BSPConfig.PROJECTOR_RF.get();
        if (!signal) {
            return Component.translatable("message.bsp_core.projector.no_signal").withStyle(ChatFormatting.YELLOW);
        }
        if (!active) {
            return Component.translatable("message.bsp_core.projector.no_power", rf).withStyle(ChatFormatting.RED);
        }
        java.util.List<String> powers = new java.util.ArrayList<>();
        for (Buff b : Buff.values()) {
            if (levels[b.ordinal()] > 0) {
                powers.add(Component.translatable("buff.bsp_core." + b.key).getString() + " " + levels[b.ordinal()]);
            }
        }
        return Component.translatable("message.bsp_core.projector.active", String.join(", ", powers)).withStyle(ChatFormatting.AQUA);
    }

    /** Level of {@code buff} being projected right now; 0 when idle. */
    public int level(Buff buff) {
        return active ? levels[buff.ordinal()] : 0;
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.equals(player);
    }

    public int stored() {
        return energy.getEnergyStored();
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
        int need = BSPConfig.PROJECTOR_RF.get() * 20;
        boolean fed = owner != null && sl.getGameTime() - fedAt <= FEED_TICKS, now = fed && energy.getEnergyStored() >= need;
        if (now) {
            energy.use(need);
            effects(sl);
        }
        if (now != active || fed != signal) {
            active = now;
            signal = fed;
            sync();
        }
        ChunkLoading.projector(sl, worldPosition, now && levels[Buff.ANCHOR.ordinal()] > 0 ? totemPos : null);
    }

    /** Level of {@code buff} reaching the projector, whether or not it has the RF to project it. */
    public int arriving(Buff buff) {
        return signal ? levels[buff.ordinal()] : 0;
    }

    /** Healing Aura for the owner; Ward and Alarm for everyone else. The other powers are answered through TotemAuras. */
    private void effects(ServerLevel sl) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        int heal = levels[Buff.HEALING.ordinal()], ward = levels[Buff.WARD.ordinal()], alarm = levels[Buff.ALARM.ordinal()];
        ServerPlayer ownerPlayer = owner == null ? null : sl.getServer().getPlayerList().getPlayer(owner);
        if (heal > 0 && ownerPlayer != null && ownerPlayer.level() == sl && ownerPlayer.getHealth() < ownerPlayer.getMaxHealth()) {
            int r = Buff.HEALING.radius(heal);
            if (ownerPlayer.distanceToSqr(x, y, z) <= (double) r * r) {
                ownerPlayer.heal(BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), heal, 0.0).floatValue());
            }
        }
        if (ward <= 0 && alarm <= 0) {
            return;
        }
        double wardR = Buff.WARD.reach(ward), alarmR = Buff.ALARM.reach(alarm);
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer p : sl.players()) {
            if (isOwner(p.getUUID()) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            double d = p.distanceToSqr(x, y, z);
            if (ward > 0 && d <= wardR * wardR) {
                p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, (ward - 1) / 2, true, false, true));
            }
            if (alarm > 0 && d <= alarmR * alarmR) {
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, false, true));
                inside.add(p.getUUID());
                if (alarmed.add(p.getUUID()) && owner != null) {
                    NetworkStorage.tell(sl.getServer(), owner, Component.translatable("message.bsp_core.alarm", p.getGameProfile().getName(),
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.YELLOW));
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

    /** The aura spheres reach well past the block. */
    @Override
    public AABB getRenderBoundingBox() {
        int r = Math.max(Buff.FORTIFY.radius(levels[Buff.FORTIFY.ordinal()]), Buff.HEALING.radius(levels[Buff.HEALING.ordinal()]));
        return new AABB(worldPosition).inflate(Math.max(2, r + 1));
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY ? cap.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    private void write(CompoundTag tag) {
        tag.putIntArray("Levels", levels);
        tag.putBoolean("Active", active);
        tag.putBoolean("Signal", signal);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
    }

    // fedAt and the totem are saved (not synced) so a restart does not look like a lost signal and drop the projector's chunks

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        tag.put("Energy", energy.serializeNBT());
        tag.putLong("FedAt", fedAt);
        if (totemPos != null) {
            tag.putLong("Totem", totemPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] l = tag.getIntArray("Levels");
        if (l.length == levels.length) {
            System.arraycopy(l, 0, levels, 0, l.length);
        }
        active = tag.getBoolean("Active");
        signal = tag.getBoolean("Signal");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        if (tag.contains("FedAt")) {
            fedAt = tag.getLong("FedAt");
            totemPos = tag.contains("Totem") ? BlockPos.of(tag.getLong("Totem")) : null;
        }
        if (tag.contains("Energy")) {
            energy.deserializeNBT(tag.get("Energy"));
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
