package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The Compass Tuner. It keeps the compass upgrades a player buys (Across, Up or down and Cooldown, which it tunes into a
 * compass, and its own Tune speed), so they are not lost with a compass. Only the owner and their teammates can open,
 * use or mine it, and a player may have {@code compassTuner.maxPerPlayer} placed ({@link TunerLedger}). Mined, it drops
 * as an item that keeps the upgrades (the loot table copies them into {@code BlockEntityTag}).
 *
 * <p>Power: FE from any side into a buffer of {@code compassTuner.energyCapacity}. Wave Plasma from Plasma Injectors
 * standing against it speeds a tune up, on the injectors' usual curve and scaled by the feeding totem's Overclock.
 *
 * <p>Slots: 0 is the compass, 1 to 4 the materials a tune uses.
 */
public class CompassTunerBlockEntity extends BlockEntity {
    /** NBT keys of the four upgrade levels; the loot table copies exactly these. */
    public static final String ACROSS = "Across", DEPTH = "Depth", COOLDOWN = "Cooldown", SPEED = "Speed";
    public static final int COMPASS_SLOT = 0, MATERIAL_SLOTS = 4, SLOTS = 1 + MATERIAL_SLOTS;

    private UUID owner;
    private String ownerName = "";
    private int across, depth, cooldown, speed;
    /** mB/t of Wave Plasma arriving at the injectors against this tuner, and the speed factor it gives. Server side, refreshed once a second. */
    private int plasmaRate;
    private double plasmaFactor = 1.0;
    /** Client: whether a compass sits on the pin, and whether plasma arrives (the bulb glows). */
    private boolean clientCompass, clientPlasma;
    /** Server: whether plasma arrived at the last check, so a change is sent to clients once. */
    private boolean sentPlasma;

    /**
     * The tune. It runs on the real clock: {@code progress} goes from 0 to 1. While the tuner ticks, each moment counts at
     * the plasma's speed as long as the FE for it is there (none, and it stops). Time the tuner did not tick (its chunk
     * unloaded, the server down) counts at the Tune speed level alone, with no FE and no plasma. The levels it gives are
     * fixed when TUNE is pressed; the materials are taken when it finishes.
     */
    private boolean tuning;
    private double progress;
    private long lastMs;
    private int targetAcross, targetDepth, targetCooldown;
    /** FE owed for less than a whole unit, carried to the next tick. */
    private double feCarry;
    /** Progress per second as last sent to clients (0 while stopped), so a change of speed is sent once. */
    private double sentRate = -1;
    /** Why the tune is not moving, as last worked out: NONE, NO_FE or NO_MATERIALS. Server side. */
    private Stall stall = Stall.NONE;

    public enum Stall { NONE, NO_FE, NO_MATERIALS }

    /** The levels the compass now in the slot handed over (across, up or down, cooldown), for the screen; null if it handed none. */
    @Nullable
    private int[] handover;

    private final Buffer energy = new Buffer();
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            if (slot == COMPASS_SLOT && level != null && !level.isClientSide) {
                if (tuning) {
                    cancelTune(); // the compass was taken out or swapped: the tune is lost, nothing is spent
                }
                handover = null;
                takeOldUpgrades(getStackInSlot(COMPASS_SLOT));
                changed(); // the renderer shows the compass on its pin, or not
            }
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == COMPASS_SLOT) {
                return stack.getItem() instanceof TotemCompassItem;
            }
            TunerMaterials.Entry e = TunerMaterials.forSlot(slot - 1);
            return e != null && e.matches(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == COMPASS_SLOT ? 1 : 64;
        }
    };

    /** What pipes and hoppers see, from any side: materials go in, nothing comes out, and the compass slot is by hand only. */
    private final net.minecraftforge.items.IItemHandler piped = new net.minecraftforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return MATERIAL_SLOTS;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(1 + slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return items.insertItem(1 + slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(1 + slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return items.isItemValid(1 + slot, stack);
        }
    };
    private final LazyOptional<net.minecraftforge.items.IItemHandler> itemCap = LazyOptional.of(() -> piped);

    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(BSPConfig.getOr(BSPConfig.TUNER_ENERGY_CAPACITY, 200_000), 10_000, 0);
        }

        void use(int fe) {
            energy = Math.max(0, energy - fe);
        }

        /** The capacity follows the config, so an admin's change applies to tuners already placed. */
        void sync() {
            capacity = BSPConfig.getOr(BSPConfig.TUNER_ENERGY_CAPACITY, 200_000);
            maxReceive = capacity; // never less than a tune draws in a tick, whatever fePerTick is
            energy = Math.min(energy, capacity);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            sync();
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) {
                setChanged();
            }
            return got;
        }
    }

    public CompassTunerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMPASS_TUNER.get(), pos, state);
    }

    // ------------------------------------------------------------------ owner and access

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

    /** The owner and their teammates (while teams are on) may open, use and mine the tuner. One with no owner yet anyone may. */
    public boolean mayUse(Player player) {
        if (owner == null || isOwner(player)) {
            return true;
        }
        if (level != null && level.isClientSide) {
            return clientTeam.contains(player.getUUID()); // as the server last sent it, so a stranger sees no cracks
        }
        return player.getServer() != null && com.mrgregles.bsp_core.team.Teams.enabled()
                && com.mrgregles.bsp_core.api.BspTeams.sameTeam(player.getServer(), owner, player.getUUID());
    }

    /** The owner's teammates who may use the tuner (none while teams are off). Server side. */
    private List<UUID> team() {
        if (owner == null || !(level instanceof ServerLevel sl) || !com.mrgregles.bsp_core.team.Teams.enabled()) {
            return List.of();
        }
        return com.mrgregles.bsp_core.api.BspTeams.teammates(sl.getServer(), owner);
    }

    /** Client: the owner's teammates as last sent. */
    private java.util.Set<UUID> clientTeam = java.util.Set.of();
    /** Server: the team as last sent to clients, so a change is sent once. */
    private List<UUID> sentTeam = List.of();

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        record();
        changed();
    }

    private void record() {
        if (level instanceof ServerLevel sl && owner != null) {
            TunerLedger.get(sl.getServer()).put(GlobalPos.of(sl.dimension(), worldPosition), owner);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        record(); // a tuner from before the ledger, or one the ledger lost, counts again once it loads
    }

    // ------------------------------------------------------------------ upgrades

    public int across() {
        return across;
    }

    public int depth() {
        return depth;
    }

    public int cooldown() {
        return cooldown;
    }

    public int speed() {
        return speed;
    }

    /**
     * The four upgrade ladders. Across, Up or down and Cooldown are tuned into a compass and cost what they cost on the
     * compass before the tuner ({@code compass} config); Tune speed is the tuner's own ({@code compassTuner} config).
     * Compass upgrades never pay the team surcharge, and neither do these.
     */
    public enum Ladder {
        ACROSS, DEPTH, COOLDOWN, SPEED;

        /** Levels this ladder has with the current config. */
        public int max() {
            return switch (this) {
                case ACROSS, DEPTH -> TotemCompassItem.maxRangeLevel();
                case COOLDOWN -> TotemCompassItem.maxLevel();
                case SPEED -> maxSpeedLevel();
            };
        }

        /** Coins the level after {@code level} costs, or -1 at the top. */
        public int cost(int level) {
            if (level < 0 || level >= max()) {
                return -1;
            }
            return switch (this) {
                case ACROSS, DEPTH -> TotemCompassItem.rangeCost(level);
                case COOLDOWN -> TotemCompassItem.cooldownCost(level);
                case SPEED -> BSPConfig.getOr(BSPConfig.TUNER_SPEED_COSTS, List.<Integer>of()).get(level);
            };
        }

        /** The Shatter Coin that pays for the level after {@code level}. */
        public com.mrgregles.bsp_core.coin.CoinTier coin(int level) {
            return switch (this) {
                case ACROSS, DEPTH -> TotemCompassItem.rangeCoin(level);
                case COOLDOWN -> TotemCompassItem.upgradeCoin(level);
                case SPEED -> tier(BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.TUNER_SPEED_TIERS, List.<String>of()), level + 1, "gold"));
            };
        }
    }

    private static com.mrgregles.bsp_core.coin.CoinTier tier(String key) {
        for (com.mrgregles.bsp_core.coin.CoinTier t : com.mrgregles.bsp_core.coin.CoinTier.values()) {
            if (t.key.equals(key)) {
                return t;
            }
        }
        return com.mrgregles.bsp_core.coin.CoinTier.GOLD;
    }

    /** Tune speed levels: the shortest of the price list and the minutes list less its first entry. */
    public static int maxSpeedLevel() {
        int minutes = BSPConfig.getOr(BSPConfig.TUNER_MINUTES, List.<Integer>of()).size() - 1;
        return Math.max(0, Math.min(minutes, BSPConfig.getOr(BSPConfig.TUNER_SPEED_COSTS, List.<Integer>of()).size()));
    }

    /** Minutes one tune takes at Tune speed {@code level}, before Wave Plasma. A level past the list reads its last entry. */
    public static int tuneMinutes(int level) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.TUNER_MINUTES, List.<Integer>of()), Math.max(0, level) + 1, 120);
    }

    public int level(Ladder ladder) {
        return switch (ladder) {
            case ACROSS -> across;
            case DEPTH -> depth;
            case COOLDOWN -> cooldown;
            case SPEED -> speed;
        };
    }

    /** Coins the next level of {@code ladder} costs on this tuner, or -1 at the top. */
    public int nextCost(Ladder ladder) {
        return ladder.cost(level(ladder));
    }

    /**
     * Buys the next level of {@code ladder} for {@code player}, who must be the owner or a teammate: takes its coins from
     * their inventory (none in creative). Returns whether it was bought.
     */
    public boolean buy(net.minecraft.server.level.ServerPlayer player, Ladder ladder) {
        int level = level(ladder), cost = ladder.cost(level);
        if (cost < 0 || !mayUse(player)) {
            return false;
        }
        if (!player.isCreative()) {
            net.minecraft.world.item.Item coin = ladder.coin(level).coin();
            if (player.getInventory().countItem(coin) < cost) {
                return false;
            }
            player.getInventory().clearOrCountMatchingItems(st -> st.is(coin), cost, player.inventoryMenu.getCraftSlots());
        }
        switch (ladder) {
            case ACROSS -> across = level + 1;
            case DEPTH -> depth = level + 1;
            case COOLDOWN -> cooldown = level + 1;
            case SPEED -> speed = level + 1;
        }
        changed();
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} bought {} level {} on {}'s Compass Tuner at {} ({} {} coins)", player.getGameProfile().getName(),
                ladder, level + 1, ownerName, worldPosition, player.isCreative() ? 0 : cost, ladder.coin(level).key);
        return true;
    }

    /** Sets the four levels (tests, and the upgrade and handover code). Negative values count as 0. */
    public void setLevels(int across, int depth, int cooldown, int speed) {
        this.across = Math.max(0, across);
        this.depth = Math.max(0, depth);
        this.cooldown = Math.max(0, cooldown);
        this.speed = Math.max(0, speed);
        changed();
    }

    // ------------------------------------------------------------------ power and plasma

    public int energyStored() {
        return energy.getEnergyStored();
    }

    public int energyCapacity() {
        energy.sync();
        return energy.getMaxEnergyStored();
    }

    /** Uses {@code fe} if the buffer holds that much. */
    public boolean takeEnergy(int fe) {
        if (energy.getEnergyStored() < fe) {
            return false;
        }
        energy.use(fe);
        setChanged();
        return true;
    }

    /** Test hook: adds {@code fe} to the buffer (capped at the capacity). */
    public void putEnergy(int fe) {
        energy.sync();
        energy.receiveEnergy(fe, false);
        setChanged();
    }

    public int plasmaRate() {
        return plasmaRate;
    }

    /** The speed factor the injectors give: 1.0 with none, up to the curve's last point, scaled by Overclock. */
    public double plasmaFactor() {
        return plasmaFactor;
    }

    /** Test hook: plasma arriving at {@code rate} mB/t with this speed factor, until the next once-a-second check. */
    public void plasmaForTest(int rate, double factor) {
        plasmaRate = rate;
        plasmaFactor = factor;
    }

    public ItemStackHandler getItems() {
        return items;
    }

    // ------------------------------------------------------------------ old compasses

    /**
     * A compass upgraded on itself before the tuner (levels and no tuned mark) hands its upgrades over the first time it goes
     * into a tuner: on each ladder the tuner keeps the higher of its own level and the compass's. The compass keeps its levels
     * and is marked tuned, so it never hands over again. Returns whether it handed over.
     */
    public boolean takeOldUpgrades(ItemStack compass) {
        if (!TotemCompassItem.upgradedBeforeTuner(compass)) {
            return false;
        }
        int a = TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.ACROSS), d = TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.DEPTH),
                c = TotemCompassItem.level(compass);
        across = Math.max(across, a);
        depth = Math.max(depth, d);
        cooldown = Math.max(cooldown, c);
        TotemCompassItem.markHandedOver(compass);
        handover = new int[] {a, d, c};
        changed();
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("An old compass handed its upgrades (across {}, up or down {}, cooldown {}) to {}'s Compass Tuner at {}; it now has {}, {}, {}",
                a, d, c, ownerName, worldPosition, across, depth, cooldown);
        return true;
    }

    /** The levels the compass in the slot handed over when it went in, or null. */
    @Nullable
    public int[] handover() {
        return handover;
    }

    // ------------------------------------------------------------------ tuning

    /** Why TUNE can or cannot start. */
    public enum TuneCheck { OK, NOT_ALLOWED, TUNING, NO_COMPASS, NOTHING_TO_TUNE, NO_MATERIALS, NO_FE }

    public boolean isTuning() {
        return tuning;
    }

    /** 0 to 1. */
    public double progress() {
        return progress;
    }

    public Stall stall() {
        return stall;
    }

    /** The levels a tune started now would give: on each ladder the higher of the compass's and the tuner's. */
    public int[] targetFor(ItemStack compass) {
        // the tuner's levels held to what the config still has, so a shortened list never leaves a compass forever "to tune"
        return new int[] {Math.max(TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.ACROSS), Math.min(across, TotemCompassItem.rangeTop(TotemCompassItem.Range.ACROSS))),
                Math.max(TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.DEPTH), Math.min(depth, TotemCompassItem.rangeTop(TotemCompassItem.Range.DEPTH))),
                Math.max(TotemCompassItem.level(compass), Math.min(cooldown, TotemCompassItem.cooldownTop()))};
    }

    /** The levels the running tune gives (across, up or down, cooldown). */
    public int[] target() {
        return new int[] {targetAcross, targetDepth, targetCooldown};
    }

    /** Whether the compass already has everything this tuner would give it. */
    public boolean nothingToTune(ItemStack compass) {
        int[] t = targetFor(compass);
        return t[0] == TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.ACROSS)
                && t[1] == TotemCompassItem.rangeLevel(compass, TotemCompassItem.Range.DEPTH) && t[2] == TotemCompassItem.level(compass);
    }

    /** Whether every material slot holds at least what a tune takes. */
    public boolean hasMaterials() {
        List<TunerMaterials.Entry> need = TunerMaterials.entries();
        for (int i = 0; i < need.size(); i++) {
            ItemStack have = items.getStackInSlot(1 + i);
            if (need.get(i) != null && (!need.get(i).matches(have) || have.getCount() < need.get(i).count())) {
                return false;
            }
        }
        return true;
    }

    /** FE one tick of tuning takes now: {@code compassTuner.fePerTick}, raised with the plasma's speed. */
    public int fePerTick() {
        return (int) Math.ceil(BSPConfig.getOr(BSPConfig.TUNER_FE_PER_TICK, 100) * plasmaFactor);
    }

    /** Milliseconds one whole tune takes at the current Tune speed level, before plasma. */
    public long tuneMs() {
        return tuneMinutes(speed) * 60_000L;
    }

    public TuneCheck checkTune(@Nullable Player player) {
        if (player != null && !mayUse(player)) {
            return TuneCheck.NOT_ALLOWED;
        }
        if (tuning) {
            return TuneCheck.TUNING;
        }
        ItemStack compass = items.getStackInSlot(COMPASS_SLOT);
        if (!(compass.getItem() instanceof TotemCompassItem)) {
            return TuneCheck.NO_COMPASS;
        }
        if (nothingToTune(compass)) {
            return TuneCheck.NOTHING_TO_TUNE;
        }
        if (!hasMaterials()) {
            return TuneCheck.NO_MATERIALS;
        }
        if (energy.getEnergyStored() < fePerTick()) {
            return TuneCheck.NO_FE;
        }
        return TuneCheck.OK;
    }

    /** TUNE: starts a tune of the compass in the slot to the levels it would get now. Returns why not, or OK. */
    public TuneCheck startTune(@Nullable Player player) {
        return startTune(player, System.currentTimeMillis());
    }

    public TuneCheck startTune(@Nullable Player player, long nowMs) {
        TuneCheck check = checkTune(player);
        if (check != TuneCheck.OK) {
            return check;
        }
        int[] t = targetFor(items.getStackInSlot(COMPASS_SLOT));
        targetAcross = t[0];
        targetDepth = t[1];
        targetCooldown = t[2];
        tuning = true;
        progress = 0;
        lastMs = nowMs;
        stall = Stall.NONE;
        changed();
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} started a compass tune on {}'s Compass Tuner at {} (across {}, up or down {}, cooldown {})",
                player == null ? "A machine" : player.getGameProfile().getName(), ownerName, worldPosition, t[0], t[1], t[2]);
        return TuneCheck.OK;
    }

    /** Stops the tune with nothing spent. */
    public void cancelTune() {
        if (!tuning) {
            return;
        }
        tuning = false;
        progress = 0;
        stall = Stall.NONE;
        changed();
    }

    /**
     * Moves the tune on to {@code nowMs}: the time since the last tick, the first second of it counted as ticking time (FE
     * and plasma) and anything beyond as time the tuner did not tick (Tune speed alone, no FE). Then finishes it if it is done.
     */
    public void advance(long nowMs) {
        if (!tuning) {
            return;
        }
        long elapsed = Math.max(0, nowMs - lastMs);
        lastMs = nowMs;
        long ticking = Math.min(elapsed, 1_000L), away = elapsed - ticking;
        double perMs = 1.0 / Math.max(1, tuneMs());
        if (progress < 1 && away > 0) {
            progress = Math.min(1, progress + away * perMs);
        }
        if (progress < 1 && ticking > 0) {
            // FE for exactly this much time (a tick is 50 ms), the fraction carried to the next, so catch-up ticks cost no extra
            double owed = fePerTick() * ticking / 50.0 + feCarry;
            int fe = (int) Math.floor(owed);
            if (energy.getEnergyStored() >= fe) {
                energy.use(fe);
                feCarry = owed - fe;
                progress = Math.min(1, progress + ticking * perMs * plasmaFactor);
                stall = Stall.NONE;
            } else {
                stall = Stall.NO_FE;
            }
        }
        if (progress >= 1) {
            if (hasMaterials()) {
                finishTune();
            } else {
                stall = Stall.NO_MATERIALS; // done but waiting: the materials were taken out
            }
        }
        setChanged();
    }

    /** The tune is done: the compass gets its levels and the tuned mark, and the materials are taken. */
    private void finishTune() {
        ItemStack compass = items.getStackInSlot(COMPASS_SLOT);
        tuning = false;
        progress = 0;
        stall = Stall.NONE;
        if (!(compass.getItem() instanceof TotemCompassItem)) {
            changed();
            return;
        }
        TotemCompassItem.applyTune(compass, targetAcross, targetDepth, targetCooldown);
        List<TunerMaterials.Entry> need = TunerMaterials.entries();
        for (int i = 0; i < need.size(); i++) {
            if (need.get(i) != null) {
                items.getStackInSlot(1 + i).shrink(need.get(i).count());
            }
        }
        changed();
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("A compass tune finished on {}'s Compass Tuner at {} (across {}, up or down {}, cooldown {})",
                ownerName, worldPosition, targetAcross, targetDepth, targetCooldown);
    }

    /** Progress per second right now: 0 while stopped, else the plasma's speed over the tune's length. */
    public double ratePerSecond() {
        if (!tuning || stall != Stall.NONE) {
            return 0;
        }
        return 1000.0 * plasmaFactor / Math.max(1, tuneMs());
    }

    // ------------------------------------------------------------------ tick

    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            List<BlockPos> self = List.of(worldPosition);
            plasmaRate = com.mrgregles.bsp_core.plasma.PlasmaBoost.rate(level, self);
            plasmaFactor = com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(com.mrgregles.bsp_core.plasma.PlasmaBoost.factor(plasmaRate),
                    com.mrgregles.bsp_core.plasma.PlasmaBoost.scale(level, self));
            if (sentPlasma != plasmaRate > 0) {
                sentPlasma = plasmaRate > 0;
                changed();
            }
        }
        if (tuning) {
            advance(System.currentTimeMillis());
        }
        if (level.getGameTime() % 20 == 0) {
            // once a second at most, so FE arriving just short of the draw does not send an update every few ticks
            double rate = ratePerSecond();
            List<UUID> mates = team();
            if (Math.abs(rate - sentRate) > 1e-12 || stall != sentStall || !mates.equals(sentTeam)) {
                sentRate = rate;
                sentStall = stall;
                sentTeam = mates;
                changed(); // clients count the progress on from here, and know who may mine it
            }
        }
    }

    private Stall sentStall = Stall.NONE;

    /** Server: the block is gone. Its compass and materials drop; the upgrades go with the block's own drop. */
    public void onBroken(ServerLevel sl) {
        TunerLedger.get(sl.getServer()).remove(GlobalPos.of(sl.dimension(), worldPosition));
        for (int i = 0; i < SLOTS; i++) {
            net.minecraft.world.Containers.dropItemStack(sl, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    // ------------------------------------------------------------------ capabilities

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    // ------------------------------------------------------------------ save and sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void writeLevels(CompoundTag tag) {
        tag.putInt(ACROSS, across);
        tag.putInt(DEPTH, depth);
        tag.putInt(COOLDOWN, cooldown);
        tag.putInt(SPEED, speed);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        writeLevels(tag);
        tag.put("Energy", energy.serializeNBT());
        if (tuning) {
            tag.putBoolean("Tuning", true);
            tag.putDouble("Progress", progress);
            tag.putLong("LastMs", lastMs);
            tag.putIntArray("Target", new int[] {targetAcross, targetDepth, targetCooldown});
        }
        tag.put("Items", items.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("Owner")) {
            owner = tag.getUUID("Owner");
            ownerName = tag.getString("OwnerName");
        }
        across = Math.max(0, tag.getInt(ACROSS));
        depth = Math.max(0, tag.getInt(DEPTH));
        cooldown = Math.max(0, tag.getInt(COOLDOWN));
        speed = Math.max(0, tag.getInt(SPEED));
        if (tag.contains("Energy")) {
            energy.deserializeNBT(tag.get("Energy"));
        }
        tuning = tag.getBoolean("Tuning");
        progress = Math.max(0, Math.min(1, tag.getDouble("Progress")));
        lastMs = tag.getLong("LastMs");
        int[] t = tag.getIntArray("Target");
        targetAcross = t.length > 0 ? t[0] : 0;
        targetDepth = t.length > 1 ? t[1] : 0;
        targetCooldown = t.length > 2 ? t[2] : 0;
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
    }

    /** What the client needs to draw the tuner: the owner's name and the levels. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putString("OwnerName", ownerName);
        writeLevels(tag);
        tag.putBoolean("Tuning", tuning);
        tag.putDouble("Progress", progress);
        tag.putDouble("Rate", ratePerSecond());
        tag.putInt("Stall", tuning ? stall.ordinal() : 0);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        net.minecraft.nbt.ListTag mates = new net.minecraft.nbt.ListTag();
        for (UUID m : team()) {
            mates.add(net.minecraft.nbt.NbtUtils.createUUID(m));
        }
        tag.put("Team", mates);
        tag.putBoolean("HasCompass", items.getStackInSlot(COMPASS_SLOT).getItem() instanceof TotemCompassItem);
        tag.putBoolean("Plasma", plasmaRate > 0);
        return tag;
    }

    /** Client: progress as last sent, and how fast it moves, so the renderer can count it on between updates. */
    private double clientRate;
    private long clientAt;

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        ownerName = tag.getString("OwnerName");
        across = tag.getInt(ACROSS);
        depth = tag.getInt(DEPTH);
        cooldown = tag.getInt(COOLDOWN);
        speed = tag.getInt(SPEED);
        tuning = tag.getBoolean("Tuning");
        progress = tag.getDouble("Progress");
        clientRate = tag.getDouble("Rate");
        clientAt = System.currentTimeMillis();
        Stall[] all = Stall.values();
        clientStall = all[Math.max(0, Math.min(all.length - 1, tag.getInt("Stall")))];
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        java.util.Set<UUID> mates = new java.util.HashSet<>();
        for (net.minecraft.nbt.Tag t : tag.getList("Team", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            mates.add(net.minecraft.nbt.NbtUtils.loadUUID(t));
        }
        clientTeam = mates;
        clientCompass = tag.getBoolean("HasCompass");
        clientPlasma = tag.getBoolean("Plasma");
    }

    private Stall clientStall = Stall.NONE;

    /** Client: a tune is running but stopped (no FE, or waiting for its materials). */
    public boolean clientStalled() {
        return clientStall != Stall.NONE;
    }

    /** Client: why a running tune is stopped, as last sent. */
    public Stall clientStall() {
        return clientStall;
    }

    /** Client: a compass sits on the pin. */
    public boolean clientCompass() {
        return clientCompass;
    }

    /** Client: plasma arrives at the tuner. */
    public boolean clientPlasma() {
        return clientPlasma;
    }

    /** Client: the progress now, counted on from the last update. */
    public double clientProgress() {
        return tuning ? Math.min(1, progress + clientRate * (System.currentTimeMillis() - clientAt) / 1000.0) : 0;
    }

    /** Client: progress per second as last sent (0 while stopped). */
    public double clientRate() {
        return clientRate;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag());
        }
    }
}
