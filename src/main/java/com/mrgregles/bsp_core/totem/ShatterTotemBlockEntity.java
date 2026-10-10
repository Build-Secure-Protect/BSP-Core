package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chunk.ChunkLoading;
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
    /** The totem's id and instance ({@link TotemIdentity}), kept so the item that comes back out is the same totem. */
    private CompoundTag identity = new CompoundTag();
    /** When the current owner got this totem (ms since 1970); 0 for totems from before chunk loading. */
    private long ownedSince;
    /** Friends the owner has let in and what each may do. Cleared when the totem changes hands. */
    private java.util.List<TotemAccess> access = new java.util.ArrayList<>();
    /** The Cloaking cube's copy of the land, taken when Cloaking first came on; null while there is none. */
    @Nullable
    private CloakSnapshot cloak;
    /** A snapshot being generated, one chunk a tick; null when none is wanted. */
    @Nullable
    private CloakSnapshot.Builder cloakBuilder;

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

    public long ownedSince() {
        return ownedSince;
    }

    public void setOwner(@Nullable TotemOwner owner) {
        if (owner != null && (this.owner == null || !this.owner.uuid().equals(owner.uuid()))) {
            ownedSince = System.currentTimeMillis();
            access.clear(); // a new owner starts with nobody let in
        }
        this.owner = owner;
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            sync();
            updateBlockState();
            TotemLedger ledger = TotemLedger.get(serverLevel.getServer());
            GlobalPos here = GlobalPos.of(serverLevel.dimension(), worldPosition);
            if (owner != null) {
                ledger.recordPlaced(owner.uuid(), here, getTier());
            } else {
                ledger.recordRemoved(here);
            }
            ChunkLoading.totemChanged(this);
        }
    }

    /** Reads everything the item form carried (owner, later upgrades) into this block. */
    public void loadFromStack(ItemStack stack) {
        TotemIdentity.ensure(stack);
        identity = new CompoundTag();
        TotemIdentity.copy(stack.getTag(), identity);
        upgrades = TotemUpgrades.copyFrom(stack);
        setOwner(TotemOwner.fromStack(stack).orElse(null));
        CompoundTag tag = stack.getTag();
        if (tag != null && owner != null && level instanceof ServerLevel serverLevel) {
            // placing it is not a change of owner: keep the time the item carried, its access list and its chunk choices
            ownedSince = tag.getLong(TotemOwner.TAG_OWNED_SINCE);
            access = TotemAccess.load(tag);
            setChanged();
            ChunkLoading.totemChanged(this);
            ChunkLoading.restore(serverLevel, worldPosition, tag.getIntArray(TAG_CHUNK_PATTERN));
        }
    }

    /** Writes everything that must survive into the item when the block is picked up. */
    public void writeToStackTag(CompoundTag tag) {
        TotemIdentity.copy(identity, tag);
        if (owner != null) {
            owner.save(tag);
            tag.putLong(TotemOwner.TAG_OWNED_SINCE, ownedSince);
            if (!access.isEmpty()) {
                tag.put(TotemAccess.TAG, TotemAccess.save(access));
            }
            int[] pattern = level instanceof ServerLevel serverLevel ? ChunkLoading.pattern(serverLevel, worldPosition) : new int[0];
            if (pattern.length > 0) {
                tag.putIntArray(TAG_CHUNK_PATTERN, pattern);
            }
        }
        if (!upgrades.isEmpty()) {
            tag.put(TotemUpgrades.TAG_UPGRADES, upgrades.copy());
        }
    }

    /** Item tag: the chunks picked around the totem, as offsets, so a totem that is moved keeps its layout. */
    public static final String TAG_CHUNK_PATTERN = "ChunkPattern";

    /** Called by the block when it is removed from the world. */
    public void onRemovedFromWorld() {
        if (level instanceof ServerLevel serverLevel) {
            ChunkLoading.totemRemoved(serverLevel, worldPosition);
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
        // Recall: the owner is offered a teleport back, after the thief's Shroud and Recall Block have run out
        int block = raiding ? BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.RECALL_BLOCK_SECONDS, java.util.List.<Integer>of()), TotemUpgrades.getLevel(raidTotem, TotemUpgrades.Buff.RECALL_BLOCK), 0) : 0;
        recallDelay = getUpgradeLevel(TotemUpgrades.Buff.RECALL) > 0 && owner != null ? Math.max(1, Math.min(seconds - 1, Math.max(shroud, 0) + block) * 20) : 0;
        sendStatus(serverLevel, StealStatusPacket.OUTCOME_ACTIVE);
    }

    /** Ticks until the owner is told about the running steal; above 0 only while a thief's Shroud is hiding it. */
    private int warnDelay;
    /** Ticks until the owner is offered a Recall; 0 when none is due. */
    private int recallDelay;
    /** Intruders the Alarm has already reported, so the owner is told once per visit. */
    private final java.util.Set<UUID> alarmed = new java.util.HashSet<>();
    /** Harvest: the BSP ores mined within reach, oldest first, waiting to grow back; and when the last one did. */
    private final java.util.ArrayDeque<net.minecraft.util.Tuple<BlockPos, BlockState>> harvestQueue = new java.util.ArrayDeque<>();
    private long harvestLast;
    private static final int HARVEST_QUEUE_MAX = 256;

    /** The break handler saw a BSP ore mined within this totem's Harvest reach. */
    public void harvestRecord(BlockPos pos, BlockState ore) {
        if (harvestQueue.size() >= HARVEST_QUEUE_MAX) {
            harvestQueue.pollFirst();
        }
        harvestQueue.addLast(new net.minecraft.util.Tuple<>(pos.immutable(), ore));
        setChanged();
    }

    /** mB per tick Harvest is taking from the output: only while ores are waiting. */
    public int harvestDraw() {
        return harvestQueue.isEmpty() || getUpgradeLevel(TotemUpgrades.Buff.HARVEST) <= 0 || owner == null ? 0 : BSPConfig.getOr(BSPConfig.HARVEST_DRAW, 25);
    }

    /** Once a second: when the level's interval has passed, the oldest waiting ore grows back if its spot is clear; otherwise it goes to the back of the queue. */
    private void harvestTick(ServerLevel level) {
        int lvl = getUpgradeLevel(TotemUpgrades.Buff.HARVEST);
        if (lvl <= 0 || harvestQueue.isEmpty() || owner == null) {
            return;
        }
        long now = level.getGameTime();
        int every = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.HARVEST_SECONDS, java.util.List.<Integer>of()), lvl, 120) * 20;
        if (now - harvestLast < every) {
            return;
        }
        var next = harvestQueue.pollFirst();
        if (next == null) {
            return;
        }
        BlockPos pos = next.getA();
        if (!level.isLoaded(pos)) {
            harvestQueue.addLast(next);
            return;
        }
        BlockState there = level.getBlockState(pos);
        if (there.isAir() || there.canBeReplaced()) {
            level.setBlock(pos, next.getB(), 3);
            level.levelEvent(2001, pos, Block.getId(next.getB())); // the break particles, as the ore knits back
            harvestLast = now;
        } else {
            harvestQueue.addLast(next); // something stands there: wait for the spot to clear
            harvestLast = now; // and do not spin through the queue every tick
        }
    }

    private void warnOwner(ServerLevel serverLevel) {
        if (owner != null && steal != null) {
            // reaches the owner on whichever server of the network they are on
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(serverLevel.getServer(), owner.uuid(), Component.translatable("message.bsp_core.steal.warning", steal.thiefName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
    }

    /** Ward weakens intruders near the totem; Alarm outlines them and tells the owner. Runs once a second. */
    private void intruderTick(ServerLevel level) {
        int ward = getUpgradeLevel(TotemUpgrades.Buff.WARD), alarm = getUpgradeLevel(TotemUpgrades.Buff.ALARM);
        if ((ward <= 0 && alarm <= 0) || owner == null) {
            return;
        }
        int wardR = TotemUpgrades.Buff.WARD.reach(ward), alarmR = TotemUpgrades.Buff.ALARM.reach(alarm);
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.5, cz = worldPosition.getZ() + 0.5;
        java.util.Set<UUID> inside = new java.util.HashSet<>();
        for (ServerPlayer p : level.players()) {
            if (isOwner(p.getUUID()) || p.isSpectator() || p.isCreative()) {
                continue;
            }
            if (ward > 0 && !hasAccess(p.getUUID(), TotemAccess.WARD) && TotemAuras.inCube(p, cx, cy, cz, wardR)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 60, (ward - 1) / 2, true, false, true));
            }
            int thief = com.mrgregles.bsp_core.plasma.CarriedPowers.level(p, TotemUpgrades.Buff.THIEF_STEP);
            boolean unseen = thief > 0 && alarm <= BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.THIEF_STEP_ALARM, java.util.List.<Integer>of()), thief, 0);
            if (alarm > 0 && !unseen && !hasAccess(p.getUUID(), TotemAccess.ALARM) && TotemAuras.inCube(p, cx, cy, cz, alarmR)) {
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING, 60, 0, true, false, true));
                inside.add(p.getUUID());
                if (alarmed.add(p.getUUID())) {
                    com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), owner.uuid(), Component.translatable("message.bsp_core.alarm",
                            p.getGameProfile().getName(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.YELLOW));
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
            cloakTick(level);
            reportToLedger(level);
            harvestTick(level);
        }
        if (cloakBuilder != null && cloakBuilder.step()) {
            cloak = cloakBuilder.result();
            cloakBuilder = null;
            setChanged();
            sync();
        }
        if (steal != null && warnDelay > 0 && --warnDelay == 0) {
            warnOwner(level); // Shroud ran out: the owner now hears about the steal
        }
        if (steal != null && recallDelay > 0 && --recallDelay == 0 && owner != null) {
            RecallService.offer(level, worldPosition, owner.uuid(), getUpgradeLevel(TotemUpgrades.Buff.RECALL));
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
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), previous.uuid(),
                    Component.translatable("message.bsp_core.steal.lost", finished.thiefName()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
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
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), owner.uuid(),
                    Component.translatable("message.bsp_core.steal.defended", steal.thiefName()).withStyle(ChatFormatting.GREEN));
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
            recordInLedger(serverLevel);
            ChunkLoading.totemChanged(this);
        } else if (level != null && level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.CloakClient.register(this));
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            TotemAuras.unregister(serverLevel, worldPosition);
        } else if (level != null && level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.CloakClient.unregister(this));
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
        if (!TotemAuras.inCube(ownerPlayer, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, TotemUpgrades.Buff.HEALING.radius(lvl))) {
            return;
        }
        double amount = BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), lvl, 0.0);
        ownerPlayer.heal((float) amount);
    }

    // ------------------------------------------------------------------ cloaking

    public int cloakRadius() {
        int lvl = getUpgradeLevel(TotemUpgrades.Buff.CLOAKING);
        return lvl <= 0 ? 0 : BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.CLOAK_RADIUS, java.util.List.<Integer>of()), lvl, 6);
    }

    @Nullable
    public CloakSnapshot cloak() {
        return cloak;
    }

    /** Whether {@code player} sees the real blocks inside the cloak: the owner, friends with Machines access, or an operator. */
    public boolean seesThroughCloak(net.minecraft.world.entity.player.Player player) {
        return isOwner(player.getUUID()) || player.hasPermissions(2) && !CloakBlind.isBlind(player) || hasAccess(player.getUUID(), TotemAccess.MACHINES);
    }

    /** Operator: take the Cloaking snapshot again now (for example after building the base that should be hidden). */
    public void recloak() {
        cloak = null;
        cloakBuilder = null;
        if (level instanceof ServerLevel sl) {
            cloakTick(sl);
        }
    }

    /** Tells the ledger this totem stands here as a block (a placed totem is never lost). */
    private void reportToLedger(ServerLevel level) {
        if (identity.hasUUID(TotemIdentity.TAG_ID) && identity.hasUUID(TotemIdentity.TAG_INSTANCE)) {
            com.mrgregles.bsp_core.data.TotemLedger.get(level.getServer()).seen(identity.getUUID(TotemIdentity.TAG_ID), identity.getUUID(TotemIdentity.TAG_INSTANCE),
                    owner == null ? null : owner.uuid(), com.mrgregles.bsp_core.data.TotemLedger.PLACED, null, level.getServer().overworld().getGameTime());
        }
    }

    /** Starts generating the snapshot the first time Cloaking is on (or its radius changed), and drops it when the upgrade is gone. */
    private void cloakTick(ServerLevel level) {
        int r = cloakRadius();
        if (r <= 0 || owner == null) {
            cloakBuilder = null;
            if (cloak != null) {
                cloak = null;
                setChanged();
                sync();
            }
            return;
        }
        boolean building = cloakBuilder != null && cloakBuilder.radius() == r;
        if (!building && (cloak == null || cloak.radius != r || cloak.version < CloakSnapshot.VERSION)) {
            cloakBuilder = new CloakSnapshot.Builder(level, worldPosition, r);
        }
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

    /**
     * The owner (or an operator) buys the next level of the upgrade with this ordinal, or raises the
     * totem's tier when the ordinal is {@link com.mrgregles.bsp_core.network.UpgradeRequestPacket#RAISE_TIER}.
     */
    public void tryBuy(ServerPlayer player, int ordinal) {
        if (!mayUpgrade(player)) {
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.not_owner").withStyle(ChatFormatting.RED), true);
            return;
        }
        int tier = getTier();
        if (ordinal == com.mrgregles.bsp_core.network.UpgradeRequestPacket.RAISE_TIER) {
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(tier);
            Component why = TotemUpgrades.whyNotGate(tier, player);
            if (price == null || why != null) {
                player.displayClientMessage((why == null ? Component.translatable("gui.bsp_core.tree.gate.max") : why).copy().withStyle(ChatFormatting.RED), true);
                return;
            }
            TotemUpgrades.pay(player, price);
            setTier(tier + 1);
            player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.tier_raised", TotemUpgrades.roman(tier + 1)).withStyle(ChatFormatting.GOLD), true);
            BSPCore.LOGGER.info("{} raised the totem at {} to tier {}", player.getGameProfile().getName(), worldPosition, tier + 2);
            return;
        }
        TotemUpgrades.Buff buff = TotemUpgrades.Buff.byOrdinal(ordinal);
        if (buff == null) {
            return;
        }
        int lvl = getUpgradeLevel(buff);
        if ((buff == TotemUpgrades.Buff.ANCHOR && lvl >= 1 || buff == TotemUpgrades.Buff.SURVEY) && level instanceof ServerLevel sl && !ChunkLoading.isMain(sl, worldPosition)) {
            // a player's second totem loads its own chunk and no more
            player.displayClientMessage(Component.translatable("message.bsp_core.chunks.second_totem").withStyle(ChatFormatting.RED), true);
            return;
        }
        TotemUpgrades.Price price = TotemUpgrades.price(buff, lvl);
        Component why = TotemUpgrades.whyNot(buff, this::getUpgradeLevel, tier, player);
        if (price == null || why != null) {
            player.displayClientMessage((why == null ? Component.translatable("message.bsp_core.upgrade.maxed") : why).copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        TotemUpgrades.pay(player, price);
        setUpgradeLevel(buff, lvl + 1);
        player.displayClientMessage(Component.translatable("message.bsp_core.upgrade.bought",
                Component.translatable(buff.translationKey()), lvl + 1).withStyle(ChatFormatting.GOLD), true);
        BSPCore.LOGGER.info("{} bought {} level {} at {}", player.getGameProfile().getName(), buff.key, lvl + 1, worldPosition);
    }

    /** Keeps the ledger's owner and tier for this totem current; scores are worked out from the ledger. */
    private void recordInLedger(ServerLevel serverLevel) {
        if (owner != null) {
            TotemLedger.get(serverLevel.getServer()).recordPlaced(owner.uuid(), GlobalPos.of(serverLevel.dimension(), worldPosition), getTier());
        }
    }

    // ------------------------------------------------------------------ access list

    public java.util.List<TotemAccess> access() {
        return java.util.Collections.unmodifiableList(access);
    }

    public boolean hasAccess(UUID player, int flag) {
        for (TotemAccess a : access) {
            if (a.id().equals(player) && a.has(flag)) {
                return true;
            }
        }
        return false;
    }

    /** The owner, an operator, or someone let in with Upgrades. */
    public boolean mayUpgrade(net.minecraft.world.entity.player.Player player) {
        return isOwner(player.getUUID()) || player.hasPermissions(2) || hasAccess(player.getUUID(), TotemAccess.UPGRADES);
    }

    /** Lets {@code id} in with every switch on. False if they are already in or the list is full. */
    public boolean addAccess(UUID id, String name) {
        for (TotemAccess a : access) {
            if (a.id().equals(id)) {
                return false;
            }
        }
        if (access.size() >= TotemAccess.MAX) {
            return false;
        }
        access.add(new TotemAccess(id, name, TotemAccess.ALL));
        setChanged();
        sync();
        return true;
    }

    public void removeAccess(int index) {
        if (index >= 0 && index < access.size()) {
            access.remove(index);
            setChanged();
            sync();
        }
    }

    public void toggleAccess(int index, int flag) {
        if (index >= 0 && index < access.size()) {
            TotemAccess a = access.get(index);
            access.set(index, a.with(flag, !a.has(flag)));
            setChanged();
            sync();
        }
    }

    /** Wave Plasma this totem gives off, in mB per tick, by its Output level (config {@code plasma.outputPerLevel}). */
    public int plasmaOutput() {
        int out = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.PLASMA_OUTPUT, java.util.List.<Integer>of()), getUpgradeLevel(TotemUpgrades.Buff.OUTPUT) + 1, 100);
        int draw = (cloak != null ? BSPConfig.getOr(BSPConfig.CLOAK_DRAW, 50) : 0) + harvestDraw();
        return Math.max(0, out - draw); // Cloaking and Harvest take their share first
    }

    /** mB per tick Cloaking is taking from the output right now, 0 while not cloaked. */
    public int cloakDraw() {
        return cloak == null ? 0 : Math.min(BSPConfig.getOr(BSPConfig.CLOAK_DRAW, 50), BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.PLASMA_OUTPUT, java.util.List.<Integer>of()), getUpgradeLevel(TotemUpgrades.Buff.OUTPUT) + 1, 100));
    }

    /** Totem tier, 0 (I) to 4 (V). */
    public int getTier() {
        return Math.max(0, Math.min(TotemUpgrades.MAX_TIER, upgrades.getInt(TotemUpgrades.TAG_TIER)));
    }

    public void setTier(int tier) {
        TotemUpgrades.stamp(upgrades).putInt(TotemUpgrades.TAG_TIER, Math.max(0, Math.min(TotemUpgrades.MAX_TIER, tier)));
        if (level instanceof ServerLevel serverLevel) {
            recordInLedger(serverLevel);
        }
        setChanged();
        sync();
        updateBlockState();
    }

    // ------------------------------------------------------------------ upgrades

    public CompoundTag getUpgrades() {
        return upgrades;
    }

    public int getUpgradeLevel(TotemUpgrades.Buff buff) {
        return upgrades.getInt(buff.key);
    }

    public void setUpgradeLevel(TotemUpgrades.Buff buff, int level) {
        TotemUpgrades.stamp(upgrades).putInt(buff.key, Math.max(0, Math.min(level, buff.maxLevel())));
        setChanged();
        sync();
        updateBlockState();
        ChunkLoading.totemChanged(this);
    }

    public void resetUpgrades() {
        upgrades = new CompoundTag();
        setChanged();
        sync();
        updateBlockState();
        ChunkLoading.totemChanged(this);
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

    /**
     * Sends this block entity's data to every player tracking the chunk. Only the data, not a block update: a block update would put
     * the totem block back in the world of a client that is hiding it under a cloak, for a frame each time.
     */
    private void sync() {
        if (level instanceof ServerLevel sl) {
            var packet = getUpdatePacket();
            if (packet != null) {
                sl.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false).forEach(p -> p.connection.send(packet));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            owner.save(tag);
            tag.putLong(TotemOwner.TAG_OWNED_SINCE, ownedSince);
        }
        if (!access.isEmpty()) {
            tag.put(TotemAccess.TAG, TotemAccess.save(access));
        }
        if (cloak != null) {
            tag.put("Cloak", cloak.save());
        }
        if (steal != null) {
            steal.save(tag);
        }
        if (!upgrades.isEmpty()) {
            tag.put(TotemUpgrades.TAG_UPGRADES, upgrades.copy());
        }
        TotemIdentity.copy(identity, tag);
        if (!harvestQueue.isEmpty()) {
            net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
            for (var e : harvestQueue) {
                CompoundTag t = new CompoundTag();
                t.putLong("Pos", e.getA().asLong());
                t.put("Ore", net.minecraft.nbt.NbtUtils.writeBlockState(e.getB()));
                list.add(t);
            }
            tag.put("Harvest", list);
            tag.putLong("HarvestLast", harvestLast);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        identity = new CompoundTag();
        TotemIdentity.copy(tag, identity);
        harvestQueue.clear();
        if (tag.contains("Harvest") && level != null) {
            for (net.minecraft.nbt.Tag raw : tag.getList("Harvest", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) raw;
                harvestQueue.addLast(new net.minecraft.util.Tuple<>(BlockPos.of(t.getLong("Pos")), net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), t.getCompound("Ore"))));
            }
            harvestLast = tag.getLong("HarvestLast");
        }
        owner = TotemOwner.load(tag).orElse(null);
        ownedSince = tag.getLong(TotemOwner.TAG_OWNED_SINCE);
        access = TotemAccess.load(tag);
        cloak = tag.contains("Cloak") && level != null ? CloakSnapshot.load(tag.getCompound("Cloak"), CloakSnapshot.blocks(level)) : null;
        steal = StealState.load(tag);
        upgrades = TotemUpgrades.current(tag.getCompound(TotemUpgrades.TAG_UPGRADES).copy());
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
