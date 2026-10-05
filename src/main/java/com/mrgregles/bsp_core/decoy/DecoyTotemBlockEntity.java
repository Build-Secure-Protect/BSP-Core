package com.mrgregles.bsp_core.decoy;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.decoy.DecoyUpgradeItem.Kind;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A Decoy Totem. While it stands on a powered Decoy Power Base it is <b>active</b>:
 * <ul>
 *   <li>Totem Compasses tracking rivals within its range point at it instead of a real totem
 *       (never the owner's own compass).</li>
 *   <li>Everyone but the owner sees an ordinary Shatter Totem; the owner sees the idol with a
 *       ghost of the totem around it.</li>
 * </ul>
 * An enemy who tries to steal it sets off its trap charges (which are used up) and counts as one
 * hit. It breaks when it has taken more hits than it has Reinforced Casings, and then does nothing
 * until the owner repairs it. Without power, or broken, it shows as the bare idol to everyone. Only
 * the owner can mine a working decoy; anyone can mine a broken one.
 *
 * <p>Sockets, in tree order: three range coils, a trap charge, the Trap Amplifier, a second trap
 * charge, two casings. A socket only takes its part once the one before it is filled.
 */
public class DecoyTotemBlockEntity extends BlockEntity {
    public static final int R1 = 0, R2 = 1, R3 = 2, C1 = 3, AMP = 4, C2 = 5, K1 = 6, K2 = 7, SOCKETS = 8;
    /** The socket that must be filled before each socket opens, or -1. */
    private static final int[] BEFORE = {-1, R1, R2, -1, C1, AMP, -1, K1};

    private final ItemStackHandler items = new ItemStackHandler(SOCKETS) {
        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean powered, broken;
    private int hits;

    public DecoyTotemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOY_TOTEM.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    public ItemStackHandler getItems() {
        return items;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        changed();
    }

    public boolean isPowered() {
        return powered;
    }

    public boolean isBroken() {
        return broken;
    }

    /** Powered and not broken: fooling compasses and wearing its disguise. */
    public boolean isActive() {
        return powered && !broken;
    }

    public int hits() {
        return hits;
    }

    @Nullable
    private Kind kind(int slot) {
        return DecoyUpgradeItem.kindOf(items.getStackInSlot(slot));
    }

    /** Whether {@code stack} may go into socket {@code slot} right now. */
    public boolean fits(int slot, ItemStack stack) {
        Kind k = DecoyUpgradeItem.kindOf(stack);
        if (k == null || (BEFORE[slot] >= 0 && items.getStackInSlot(BEFORE[slot]).isEmpty())) {
            return false;
        }
        return switch (slot) {
            case R1 -> k == Kind.RANGE1;
            case R2 -> k == Kind.RANGE2;
            case R3 -> k == Kind.RANGE3;
            case C1, C2 -> k.charge();
            case AMP -> k == Kind.AMPLIFIER;
            default -> k == Kind.CASING;
        };
    }

    /** A part can only come out while nothing that depends on it is fitted. Trap charges can always come out. */
    public boolean removable(int slot) {
        if (slot == C1 || slot == C2) {
            return true;
        }
        for (int s = 0; s < SOCKETS; s++) {
            if (BEFORE[s] == slot && !items.getStackInSlot(s).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int coils() {
        int n = 0;
        for (int s = R1; s <= R3; s++) {
            n += items.getStackInSlot(s).isEmpty() ? 0 : 1;
        }
        return n;
    }

    public int casings() {
        return (items.getStackInSlot(K1).isEmpty() ? 0 : 1) + (items.getStackInSlot(K2).isEmpty() ? 0 : 1);
    }

    /** Blocks within which a rival-tracking compass is fooled. */
    public int range() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.DECOY_RANGES, List.<Integer>of()), coils() + 1, 24);
    }

    private void changed() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            if (owner != null) {
                DecoyLedger.get(sl.getServer()).put(GlobalPos.of(sl.dimension(), worldPosition), owner, range(), isActive());
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && owner != null) {
            changed();
        }
    }

    public void serverTick(ServerLevel sl) {
        boolean now = sl.getBlockEntity(worldPosition.below()) instanceof DecoyPowerBaseBlockEntity base && base.take(BSPConfig.DECOY_RF.get());
        if (now != powered) {
            powered = now;
            changed();
        } else if (owner != null && sl.getGameTime() % 100 == 0) {
            // ranges can be changed in the config or the admin panel while the decoy stands
            DecoyLedger.get(sl.getServer()).put(GlobalPos.of(sl.dimension(), worldPosition), owner, range(), isActive());
        }
    }

    // ------------------------------------------------------------------ steal attempts and traps

    /** An enemy pressed Steal on what looked like a totem. */
    public void stealAttempt(ServerPlayer thief) {
        if (!(level instanceof ServerLevel sl) || !isActive() || isOwner(thief) || thief.distanceToSqr(worldPosition.getCenter()) > 36) {
            return;
        }
        for (int slot : new int[]{C1, C2}) {
            Kind trap = kind(slot);
            if (trap != null) {
                items.setStackInSlot(slot, ItemStack.EMPTY); // a charge is used up when it goes off
                spring(sl, thief, trap);
            }
        }
        hits++;
        if (hits > casings()) {
            broken = true;
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 60, 0.3, 0.5, 0.3, 0.2);
        sl.playSound(null, worldPosition, broken ? SoundEvents.ANVIL_DESTROY : SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8F, broken ? 0.7F : 1.4F);
        thief.displayClientMessage(Component.translatable("message.bsp_core.decoy.fooled", ownerName).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        if (owner != null) {
            NetworkStorage.tell(sl.getServer(), owner, Component.translatable(broken ? "message.bsp_core.decoy.broken" : "message.bsp_core.decoy.triggered",
                    thief.getGameProfile().getName(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.GOLD));
        }
        BSPCore.LOGGER.info("{} tried to steal {}'s decoy at {} ({})", thief.getGameProfile().getName(), ownerName, worldPosition, broken ? "broken" : "held");
        changed();
    }

    private static void effect(ServerPlayer p, MobEffect effect, int amplifier) {
        p.addEffect(new MobEffectInstance(effect, BSPConfig.DECOY_EFFECT_SECONDS.get() * 20, amplifier));
    }

    private void spring(ServerLevel sl, ServerPlayer thief, Kind trap) {
        switch (trap) {
            case BLAST -> sl.explode(null, thief.getX(), thief.getY() + 0.5, thief.getZ(), BSPConfig.DECOY_BLAST_POWER.get().floatValue(), Level.ExplosionInteraction.NONE);
            case HEX -> {
                effect(thief, MobEffects.MOVEMENT_SLOWDOWN, 1);
                effect(thief, MobEffects.WEAKNESS, 1);
                effect(thief, MobEffects.GLOWING, 0);
            }
            case POISON -> effect(thief, MobEffects.POISON, 1);
            case FATIGUE -> effect(thief, MobEffects.DIG_SLOWDOWN, 2);
            case WARP -> warp(sl, thief);
            default -> {
            }
        }
    }

    /** Throws the thief to a random spot up to the configured distance away, standing on the surface there with room for a player. */
    private void warp(ServerLevel sl, ServerPlayer thief) {
        int max = BSPConfig.DECOY_WARP_DISTANCE.get();
        for (int tries = 0; tries < 24; tries++) {
            double angle = sl.random.nextDouble() * Math.PI * 2, dist = max * (0.4 + 0.6 * sl.random.nextDouble());
            BlockPos column = BlockPos.containing(thief.getX() + Math.cos(angle) * dist, thief.getY(), thief.getZ() + Math.sin(angle) * dist);
            BlockPos feet = sl.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (sl.getBlockState(feet).getCollisionShape(sl, feet).isEmpty() && sl.getBlockState(feet.above()).getCollisionShape(sl, feet.above()).isEmpty()
                    && sl.getFluidState(feet).isEmpty() && sl.getWorldBorder().isWithinBounds(feet)) {
                sl.sendParticles(ParticleTypes.PORTAL, thief.getX(), thief.getY() + 1, thief.getZ(), 40, 0.3, 0.6, 0.3, 0.4);
                thief.teleportTo(sl, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, thief.getYRot(), thief.getXRot());
                sl.playSound(null, feet, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1F, 1F);
                return;
            }
        }
    }

    /** The owner mends a broken decoy with Magnatite Ingots. */
    public void repair(ServerPlayer player) {
        if (!broken || !isOwner(player)) {
            return;
        }
        int cost = BSPConfig.DECOY_REPAIR_INGOTS.get();
        if (!player.isCreative()) {
            if (player.getInventory().countItem(ModItems.MAGNATITE_INGOT.get()) < cost) {
                player.displayClientMessage(Component.translatable("gui.bsp_core.tree.why.coins", cost, new ItemStack(ModItems.MAGNATITE_INGOT.get()).getHoverName()).withStyle(ChatFormatting.RED), true);
                return;
            }
            player.getInventory().clearOrCountMatchingItems(s -> s.is(ModItems.MAGNATITE_INGOT.get()), cost, player.inventoryMenu.getCraftSlots());
        }
        broken = false;
        hits = 0;
        changed();
    }

    // ------------------------------------------------------------------ persistence + sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Broken", broken);
        tag.putInt("Hits", hits);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        powered = tag.getBoolean("Powered");
        broken = tag.getBoolean("Broken");
        hits = tag.getInt("Hits");
    }

    /** Clients learn who owns it and whether it is working, never what is fitted: that would give the disguise away. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Broken", broken);
        tag.putInt("Hits", hits);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
