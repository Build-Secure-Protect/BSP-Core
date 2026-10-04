package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.StealStatusPacket;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import com.mrgregles.bsp_core.coin.CoinWallet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * Block entity for a placed Shatter Totem: holds the owner and runs steal attempts.
 *
 * <p>Steal rules: one thief at a time; the thief must stay within {@code steal.radiusBlocks} for
 * {@code steal.stealSeconds}; leaving the radius starts a {@code steal.graceSeconds} countdown
 * after which the attempt fails; the thief dying or logging out fails it at once. Success makes
 * the thief the new owner.
 */
public class ShatterTotemBlockEntity extends BlockEntity {
    private static final int SYNC_INTERVAL = 20;

    @Nullable
    private TotemOwner owner;
    @Nullable
    private StealState steal;
    private CompoundTag upgrades = new CompoundTag();

    public ShatterTotemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHATTER_TOTEM.get(), pos, state);
    }

    // ------------------------------------------------------------------ owner

    public Optional<TotemOwner> getOwner() {
        return Optional.ofNullable(owner);
    }

    public boolean isOwner(UUID player) {
        return owner != null && owner.uuid().equals(player);
    }

    public void setOwner(@Nullable TotemOwner owner) {
        this.owner = owner;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            sync();
            updateBlockState();
            TotemLedger ledger = TotemLedger.get(serverLevel.getServer());
            GlobalPos here = GlobalPos.of(serverLevel.dimension(), worldPosition);
            if (owner != null) {
                ledger.recordPlaced(owner.uuid(), here);
            } else {
                ledger.recordRemoved(here);
            }
        }
    }

    /** Reads everything the item form carried (owner, later upgrades) into this block. */
    public void loadFromStack(ItemStack stack) {
        upgrades = TotemUpgrades.copyFrom(stack);
        setOwner(TotemOwner.fromStack(stack).orElse(null));
    }

    /** Writes everything that must survive into the item when the block is picked up. */
    public void writeToStackTag(CompoundTag tag) {
        if (owner != null) {
            owner.save(tag);
        }
        if (!upgrades.isEmpty()) {
            tag.put(TotemUpgrades.TAG_UPGRADES, upgrades.copy());
        }
    }

    /** Called by the block when it is removed from the world. */
    public void onRemovedFromWorld() {
        if (level instanceof ServerLevel serverLevel) {
            if (steal != null) {
                endSteal(serverLevel, StealStatusPacket.OUTCOME_FAILED, "message.bsp_core.steal.totem_gone");
            }
            TotemLedger.get(serverLevel.getServer()).recordRemoved(GlobalPos.of(serverLevel.dimension(), worldPosition));
        }
    }

    // ------------------------------------------------------------------ steal

    public Optional<StealState> getSteal() {
        return Optional.ofNullable(steal);
    }

    /** Server side: validates and starts a steal attempt by {@code thief}. */
    public void tryStartSteal(ServerPlayer thief) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (isOwner(thief.getUUID())) {
            thief.displayClientMessage(Component.translatable("message.bsp_core.steal.own_totem").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (steal != null) {
            MutableComponent msg = steal.thief().equals(thief.getUUID())
                    ? Component.translatable("message.bsp_core.steal.already_you")
                    : Component.translatable("message.bsp_core.steal.already_other", steal.thiefName());
            thief.displayClientMessage(msg.withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!isWithinRadius(thief)) {
            thief.displayClientMessage(Component.translatable("message.bsp_core.steal.too_far", BSPConfig.STEAL_RADIUS.get()).withStyle(ChatFormatting.RED), true);
            return;
        }
        int grace = BSPConfig.STEAL_GRACE_SECONDS.get() * 20;
        int seconds = owner == null ? BSPConfig.UNCLAIMED_STEAL_SECONDS.get() : BSPConfig.STEAL_SECONDS.get();
        net.minecraft.world.item.ItemStack raidTotem = thief.getOffhandItem();
        boolean raiding = TotemInventories.isTotem(raidTotem);
        if (owner != null) {
            // Deadlock on this totem adds time; Lockpick on the thief's offhand totem takes time off; limits keep every totem stealable
            seconds += BSPConfig.levelValue(BSPConfig.DEADLOCK_SECONDS.get(), getUpgradeLevel(TotemUpgrades.Buff.DEADLOCK), 0);
            if (raiding) {
                seconds -= BSPConfig.levelValue(BSPConfig.LOCKPICK_SECONDS.get(), TotemUpgrades.getLevel(raidTotem, TotemUpgrades.Buff.LOCKPICK), 0);
            }
            seconds = Math.max(BSPConfig.MIN_STEAL_SECONDS.get(), Math.min(BSPConfig.MAX_STEAL_SECONDS.get(), seconds));
        }
        int shroud = raiding ? BSPConfig.levelValue(BSPConfig.SHROUD_SECONDS.get(), TotemUpgrades.getLevel(raidTotem, TotemUpgrades.Buff.SHROUD), 0) : 0;
        steal = new StealState(thief.getUUID(), thief.getGameProfile().getName(), seconds * 20, seconds * 20, grace, false);
        setChanged();
        sync();
        updateBlockState();
        BSPCore.LOGGER.info("{} started stealing {}'s totem at {}", steal.thiefName(), ownerName(), worldPosition);

        thief.displayClientMessage(Component.translatable("message.bsp_core.steal.started", ownerName(), seconds).withStyle(ChatFormatting.GOLD), false);
        warnDelay = Math.min(shroud, seconds - 1) * 20;
        if (warnDelay <= 0) {
            warnOwner(serverLevel);
        }
        sendStatus(serverLevel, StealStatusPacket.OUTCOME_ACTIVE);
    }

    /** Ticks until the owner is told about the running steal; above 0 only while a thief's Shroud is hiding it. */
    private int warnDelay;
    /** Intruders the Alarm has already reported, so the owner is told once per visit. */
    private final java.util.Set<UUID> alarmed = new java.util.HashSet<>();

    private void warnOwner(ServerLevel serverLevel) {
        ServerPlayer ownerPlayer = owner == null || steal == null ? null : serverLevel.getServer().getPlayerList().getPlayer(owner.uuid());
        if (ownerPlayer != null) {
            ownerPlayer.displayClientMessage(Component.translatable("message.bsp_core.steal.warning", steal.thiefName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        }
    }

    /** Ward weakens intruders near the totem; Alarm outlines them and tells the owner. Runs once a second. */
    private void intruderTick(ServerLevel level) {
        int ward = getUpgradeLevel(TotemUpgrades.Buff.WARD), alarm = getUpgradeLevel(TotemUpgrades.Buff.ALARM);
        if ((ward <= 0 && alarm <= 0) || owner == null) {
            return;
        }
        double wardR = TotemUpgrades.Buff.WARD.reach(ward), alarmR = TotemUpgrades.Buff.ALARM.reach(alarm);
        java.util.Set<UUID> inside = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (isOwner(p.getUUID()) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            double d = p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
            if (ward > 0 && d <= wardR * wardR) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 60, ward - 1, true, false, true));
            }
            if (alarm > 0 && d <= alarmR * alarmR) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, true, false, true));
                inside.add(p.getUUID());
                if (alarmed.add(p.getUUID())) {
                    ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner.uuid());
                    if (ownerPlayer != null) {
                        ownerPlayer.displayClientMessage(Component.translatable("message.bsp_core.alarm", p.getGameProfile().getName(),
                                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.YELLOW), false);
                    }
                }
            }
        }
        alarmed.retainAll(inside);
    }

    /** Server tick, registered by the block. */
    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            healingTick(level);
            intruderTick(level);
        }
        if (steal != null && warnDelay > 0 && --warnDelay == 0) {
            warnOwner(level); // Shroud ran out: the owner now hears about the steal
        }
        if (steal == null) {
            return;
        }
        ServerPlayer thief = level.getServer().getPlayerList().getPlayer(steal.thief());
        if (thief == null || !thief.isAlive() || thief.level() != level) {
            endSteal(level, StealStatusPacket.OUTCOME_FAILED, "message.bsp_core.steal.failed_thief_gone");
            return;
        }

        boolean outside = !isWithinRadius(thief);
        int graceMax = BSPConfig.STEAL_GRACE_SECONDS.get() * 20;
        int ticksLeft = steal.ticksLeft();
        int graceLeft = steal.graceLeft();
        if (outside) {
            graceLeft--;
            if (graceLeft <= 0) {
                endSteal(level, StealStatusPacket.OUTCOME_FAILED, "message.bsp_core.steal.failed_left");
                return;
            }
        } else {
            graceLeft = graceMax;
            ticksLeft--;
        }
        boolean stateChanged = outside != steal.outside();
        steal = steal.withTick(ticksLeft, graceLeft, outside);

        if (ticksLeft <= 0) {
            completeSteal(level, thief);
            return;
        }
        if (stateChanged || level.getGameTime() % SYNC_INTERVAL == 0) {
            setChanged();
            sync();
            sendStatus(level, StealStatusPacket.OUTCOME_ACTIVE);
        }
    }

    private void completeSteal(ServerLevel level, ServerPlayer thief) {
        TotemOwner previous = owner;
        StealState finished = steal;
        sendStatus(level, StealStatusPacket.OUTCOME_SUCCESS);
        steal = null;
        setOwner(new TotemOwner(thief.getUUID(), thief.getGameProfile().getName()));
        blast(level, thief);
        thief.displayClientMessage(Component.translatable("message.bsp_core.steal.success", previous == null ? "?" : previous.name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        if (previous != null) {
            ServerPlayer prevPlayer = level.getServer().getPlayerList().getPlayer(previous.uuid());
            if (prevPlayer != null) {
                prevPlayer.displayClientMessage(Component.translatable("message.bsp_core.steal.lost", finished.thiefName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            }
        }
        BSPCore.LOGGER.info("{} stole {}'s totem at {}", finished.thiefName(), previous == null ? "nobody" : previous.name(), worldPosition);
    }

    /** Harmless visual shockwave on a completed steal, plus temporary invincibility for the thief. */
    private void blast(ServerLevel level, ServerPlayer thief) {
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.9, z = worldPosition.getZ() + 0.5;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 160, 0.4, 0.6, 0.4, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.3, 0.3, 0.3, 0.35);
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.4F, 0.8F);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.8F, 1.3F);

        int seconds = BSPConfig.STEAL_INVINCIBILITY_SECONDS.get();
        if (seconds > 0) {
            // Resistance V makes all ordinary damage do nothing; fire resistance covers burning ticks.
            thief.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, seconds * 20, 4, false, true, true));
            thief.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, seconds * 20, 0, false, false, true));
            thief.displayClientMessage(Component.translatable("message.bsp_core.steal.invincible", seconds).withStyle(ChatFormatting.AQUA), true);
        }
    }

    private void endSteal(ServerLevel level, byte outcome, String reasonKey) {
        if (steal == null) {
            return;
        }
        sendStatus(level, outcome);
        ServerPlayer thief = level.getServer().getPlayerList().getPlayer(steal.thief());
        if (thief != null) {
            thief.displayClientMessage(Component.translatable(reasonKey).withStyle(ChatFormatting.RED), false);
        }
        if (owner != null) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner.uuid());
            if (ownerPlayer != null) {
                ownerPlayer.displayClientMessage(Component.translatable("message.bsp_core.steal.defended", steal.thiefName()).withStyle(ChatFormatting.GREEN), false);
            }
        }
        BSPCore.LOGGER.info("Steal of totem at {} by {} ended: {}", worldPosition, steal.thiefName(), reasonKey);
        steal = null;
        setChanged();
        sync();
        updateBlockState();
    }

    private boolean isWithinRadius(ServerPlayer player) {
        int r = BSPConfig.STEAL_RADIUS.get();
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= (double) r * r;
    }

    private void sendStatus(ServerLevel level, byte outcome) {
        if (steal == null) {
            return;
        }
        String ownerName = ownerName();
        StealState payload = outcome == StealStatusPacket.OUTCOME_ACTIVE ? steal : null;
        ServerPlayer thief = level.getServer().getPlayerList().getPlayer(steal.thief());
        if (thief != null) {
            BSPNetwork.sendTo(thief, new StealStatusPacket(worldPosition, ownerName, payload, StealStatusPacket.ROLE_THIEF, outcome));
        }
        if (owner != null) {
            ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner.uuid());
            if (ownerPlayer != null) {
                BSPNetwork.sendTo(ownerPlayer, new StealStatusPacket(worldPosition, ownerName, payload, StealStatusPacket.ROLE_OWNER, outcome));
            }
        }
    }

    // ------------------------------------------------------------------ auras (placed-only upgrades)

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            TotemAuras.register(serverLevel, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            TotemAuras.unregister(serverLevel, worldPosition);
        }
    }

    /** Heals the owner once a second while they stand inside the Healing Aura. */
    private void healingTick(ServerLevel level) {
        int lvl = getUpgradeLevel(TotemUpgrades.Buff.HEALING);
        if (lvl <= 0 || owner == null) {
            return;
        }
        ServerPlayer ownerPlayer = level.getServer().getPlayerList().getPlayer(owner.uuid());
        if (ownerPlayer == null || ownerPlayer.level() != level || ownerPlayer.getHealth() >= ownerPlayer.getMaxHealth()) {
            return;
        }
        int r = TotemUpgrades.Buff.HEALING.radius(lvl);
        if (ownerPlayer.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) > (double) r * r) {
            return;
        }
        double amount = BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), lvl, 0.0);
        ownerPlayer.heal((float) amount);
    }

    /** Largest aura radius currently active, for render culling. */
    public int largestAuraRadius() {
        int r = 0;
        for (TotemUpgrades.Buff buff : TotemUpgrades.Buff.values()) {
            if (buff.placedOnly) {
                r = Math.max(r, buff.radius(getUpgradeLevel(buff)));
            }
        }
        return r;
    }

    @Override
    public AABB getRenderBoundingBox() {
        int r = largestAuraRadius() + 2;
        return new AABB(worldPosition).inflate(r);
    }

    /** Owner (or operator) buys the next level of a placed-only upgrade with Shatter Coins. */
    public void tryBuyPlacedUpgrade(ServerPlayer player, TotemUpgrades.Buff buff) {
        if (!isOwner(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        int lvl = getUpgradeLevel(buff);
        int cost = buff.costToUpgrade(lvl);
        if (cost < 0) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.maxed").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (buff.currency == TotemUpgrades.Currency.XP) {
            if (!player.isCreative() && player.experienceLevel < cost) {
                player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_enough_xp", cost).withStyle(ChatFormatting.RED), true);
                return;
            }
            if (!player.isCreative()) {
                player.giveExperienceLevels(-cost);
            }
        } else if (!player.isCreative() && !CoinWallet.pay(player, cost)) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_enough_coins", cost).withStyle(ChatFormatting.RED), true);
            return;
        }
        setUpgradeLevel(buff, lvl + 1);
        player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.bought",
                Component.translatable(buff.translationKey()), lvl + 1).withStyle(ChatFormatting.GOLD), true);
        BSPCore.LOGGER.info("{} bought placed upgrade {} level {} for {} coins at {}", player.getGameProfile().getName(), buff.key, lvl + 1, cost, worldPosition);
    }

    // ------------------------------------------------------------------ upgrades

    public CompoundTag getUpgrades() {
        return upgrades;
    }

    public int getUpgradeLevel(TotemUpgrades.Buff buff) {
        return upgrades.getInt(buff.key);
    }

    public void setUpgradeLevel(TotemUpgrades.Buff buff, int level) {
        upgrades.putInt(buff.key, Math.max(0, Math.min(level, buff.maxLevel())));
        setChanged();
        sync();
        updateBlockState();
    }

    public void resetUpgrades() {
        upgrades = new CompoundTag();
        setChanged();
        sync();
        updateBlockState();
    }

    // ------------------------------------------------------------------ block state (visuals)

    /** The glow the block should show right now. */
    public ShatterTotemBlock.Glow desiredGlow() {
        if (steal != null) {
            return ShatterTotemBlock.Glow.STEALING;
        }
        return owner != null ? ShatterTotemBlock.Glow.OWNED : ShatterTotemBlock.Glow.UNCLAIMED;
    }

    /**
     * Pushes owner / steal / upgrade levels into the block state so the model (body colour, upgrade
     * orbs) matches. Safe to call while the block is being removed: it only writes if the totem is
     * still there.
     */
    private void updateBlockState() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState current = serverLevel.getBlockState(worldPosition);
        if (!(current.getBlock() instanceof ShatterTotemBlock)) {
            return;
        }
        BlockState wanted = current.setValue(ShatterTotemBlock.GLOW, desiredGlow());
        if (wanted != current) {
            serverLevel.setBlock(worldPosition, wanted, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ admin

    /** Operator: cancel any steal in progress. */
    public boolean adminCancelSteal() {
        if (level instanceof ServerLevel serverLevel && steal != null) {
            endSteal(serverLevel, StealStatusPacket.OUTCOME_FAILED, "message.bsp_core.steal.cancelled_admin");
            return true;
        }
        return false;
    }

    /** Operator: complete the steal in progress immediately. */
    public boolean adminFinishSteal() {
        if (level instanceof ServerLevel serverLevel && steal != null) {
            ServerPlayer thief = serverLevel.getServer().getPlayerList().getPlayer(steal.thief());
            if (thief != null) {
                completeSteal(serverLevel, thief);
            } else {
                endSteal(serverLevel, StealStatusPacket.OUTCOME_FAILED, "message.bsp_core.steal.failed_thief_gone");
            }
            return true;
        }
        return false;
    }

    /** Operator: set or clear the owner. Cancels any steal in progress first. */
    public void adminSetOwner(@Nullable TotemOwner newOwner) {
        adminCancelSteal();
        setOwner(newOwner);
    }

    /** Display name of the current owner, or the "unclaimed" word. */
    public String ownerName() {
        return owner == null ? Component.translatable("tooltip.bsp_core.shatter_totem.unowned").getString() : owner.name();
    }

    // ------------------------------------------------------------------ persistence + sync

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            owner.save(tag);
        }
        if (steal != null) {
            steal.save(tag);
        }
        if (!upgrades.isEmpty()) {
            tag.put(TotemUpgrades.TAG_UPGRADES, upgrades.copy());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = TotemOwner.load(tag).orElse(null);
        steal = StealState.load(tag);
        upgrades = tag.getCompound(TotemUpgrades.TAG_UPGRADES).copy();
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        // An unclaimed totem with no upgrades and no steal writes nothing, and Minecraft drops update
        // packets whose tag is empty. Without this marker the client would never learn that the owner,
        // steal or upgrades were cleared.
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
