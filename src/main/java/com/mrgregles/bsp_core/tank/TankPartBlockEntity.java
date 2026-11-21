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
    /** Port position to {mode, in mB/t, out mB/t}, kept by the master for the screen and the streams. */
    protected final Map<BlockPos, int[]> ports = new LinkedHashMap<>();
    /** Game time the tank last formed and the block that completed it: the renderer runs a sweep of light from there for a second. */
    protected long formedAt = -1000;
    @Nullable
    protected BlockPos origin;
    /** Client: the level the renderer last showed, so it glides. */
    public float shown = -1;
    /** Master: the tank has a Spawn Grate in its shell, so it is a Mob Chamber. Set when it forms. */
    protected boolean chamber;
    /** Master, chamber: which mobs it spawns (the CHAMBER tab's switch), and the game time of its next spawn (-1: not started). */
    private com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode chamberMode = com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode.ALL;
    private long nextSpawnAt = -1;
    /** Master, chamber: game time of the last spawn, sent to clients so the Spawn Grates' lamps flash. */
    private long lastSpawnAt = -1000;
    /** Master, chamber: the drop the tank feeds into its plasma, and at which level (CHAMBER tab); null feeds nothing. */
    @Nullable
    private net.minecraft.world.item.Item feedItem;
    private int feedLevel = 1;
    /**
     * Master: who fed the tank, from every network that put plasma in (the totems' owners, teams, friends with access, allies), kept
     * while its plain and totem plasma last. Its ports pass it on, so a projector fed from the tank knows its team (Ward spares them).
     */
    private com.mrgregles.bsp_core.plasma.PlasmaAccess fedBy = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
    /**
     * Master: the totem whose Anchor the stored plasma carries, in its own world, kept while the plasma still carries Anchor. Its ports
     * and a Tank Extractor on the totem pool pass it on, so a projector fed from the tank holds chunks under that totem's allowance.
     */
    @Nullable
    private net.minecraft.core.GlobalPos anchorTotem;
    /** Master: who owned the Anchor totem when its plasma came in; null when the chunk ledger did not know the totem then. */
    @Nullable
    private java.util.UUID anchorOwner;
    /** The plasma, in pools: plain, the totem pool with its powers, and one pool per Mob Chamber power ({@link TankPools}). */
    private final TankPools pools = new TankPools();
    public static final int LOOT_SLOTS = 27;
    /** Master, chamber: what its kills dropped. Tank Ports give it to hoppers and pipes; nothing goes in from outside. */
    private final net.minecraftforge.items.ItemStackHandler loot = new net.minecraftforge.items.ItemStackHandler(LOOT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
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

    /** Whether this tank is a Mob Chamber: a Spawn Grate is part of its shell. Read on the master. */
    public boolean chamber() {
        return chamber;
    }

    void setChamber(boolean chamber) {
        this.chamber = chamber;
        nextSpawnAt = -1;
        sync();
    }

    public com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode chamberMode() {
        return chamberMode;
    }

    public void setChamberMode(com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode mode) {
        chamberMode = mode;
        setChanged();
    }

    @Nullable
    public net.minecraft.world.item.Item feedItem() {
        return feedItem;
    }

    public int feedLevel() {
        return feedLevel;
    }

    /** Sets what the tank feeds: {@code item} (null for nothing) at {@code lvl} (1 to 3). */
    public void setFeed(@Nullable net.minecraft.world.item.Item item, int lvl) {
        feedItem = item;
        feedLevel = Math.max(1, Math.min(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.MOB_LEVELS, lvl));
        setChanged();
    }

    public long nextSpawnAt() {
        return nextSpawnAt;
    }

    public void setNextSpawnAt(long gameTime) {
        nextSpawnAt = gameTime;
    }

    public long lastSpawnAt() {
        return lastSpawnAt;
    }

    /** A mob just spawned here: the grates' lamps flash on every client. */
    public void markSpawned(long gameTime) {
        lastSpawnAt = gameTime;
        sync();
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
        return pools.total();
    }

    /** The pools the plasma is kept in. Change them through this entity's methods, which mark the tank changed. */
    public TankPools pools() {
        return pools;
    }

    /** What a port may give out: the plain and the totem plasma; mob pools leave only through a Tank Extractor. */
    public long open() {
        return pools.open();
    }

    /** Who fed the tank, while its plain and totem plasma last (a copy). */
    public com.mrgregles.bsp_core.plasma.PlasmaAccess fedBy() {
        return fedBy.copy();
    }

    /**
     * A network put plasma in: its access becomes the tank's when the tank knows nobody yet, or when it comes from the tank's own
     * owners or players they trust (so a kicked friend drops off with the next feed). Anyone else's plasma goes in without a say in
     * who the tank's projectors treat as the team.
     */
    public void addFedBy(com.mrgregles.bsp_core.plasma.PlasmaAccess access) {
        if (access != null && !access.isEmpty() && (fedBy.isEmpty() || fedBy.trusts(access))) {
            fedBy = access.copy();
            setChanged();
        }
    }

    void setFedBy(com.mrgregles.bsp_core.plasma.PlasmaAccess access) {
        fedBy = access == null ? new com.mrgregles.bsp_core.plasma.PlasmaAccess() : access.copy();
        setChanged();
    }

    /**
     * The totem whose Anchor the stored plasma carries, or null once no stored plasma carries Anchor, and null once that totem has
     * changed hands since its plasma came in (the plasma belongs to the owner who fed it, not to whoever holds the totem now).
     */
    @Nullable
    public net.minecraft.core.GlobalPos anchorTotem() {
        if (anchorTotem == null || pools.offered()[com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.ANCHOR.ordinal()] <= 0) {
            return null;
        }
        java.util.UUID now = ledgerOwner(anchorTotem);
        return anchorOwner != null && now != null && !now.equals(anchorOwner) ? null : anchorTotem;
    }

    @Nullable
    java.util.UUID anchorOwner() {
        return anchorOwner;
    }

    /** Who the chunk ledger says owns the totem at {@code totem} right now, or null when it does not know it (or off the server). */
    @Nullable
    private java.util.UUID ledgerOwner(net.minecraft.core.GlobalPos totem) {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }
        var entry = com.mrgregles.bsp_core.chunk.ChunkLedger.get(sl.getServer()).totems.get(totem);
        return entry == null ? null : entry.owner;
    }

    /**
     * A network put plasma carrying Anchor in, from the totem at {@code totem}, with {@code access}. It becomes the tank's Anchor
     * totem only when the tank trusts that access (the tank's own feeders or players they trust, as {@link #addFedBy} decides; call
     * that first, so a first feed into an empty tank counts). Anyone else's Anchor goes in without naming its totem.
     */
    public void addAnchor(@Nullable net.minecraft.core.GlobalPos totem, com.mrgregles.bsp_core.plasma.PlasmaAccess access) {
        if (totem != null && access != null && !access.isEmpty() && fedBy.trusts(access)) {
            java.util.UUID owner = ledgerOwner(totem);
            if (!totem.equals(anchorTotem) || !java.util.Objects.equals(owner, anchorOwner)) {
                anchorTotem = totem;
                anchorOwner = owner;
                setChanged();
            }
        }
    }

    void setAnchorTotem(@Nullable net.minecraft.core.GlobalPos totem, @Nullable java.util.UUID owner) {
        anchorTotem = totem;
        anchorOwner = totem == null ? null : owner;
        setChanged();
    }

    /** Puts remembered plasma back, as much as fits (a tank re-formed from a dormant one). */
    void setPools(TankPools from) {
        pools.clear();
        pools.merge(from);
        pools.trimTo(capacity());
        setChanged();
    }

    /** Puts up to {@code mB} of plain plasma in; returns what fitted. */
    public long add(long mB) {
        return add(mB, null);
    }

    /**
     * Puts plasma in as it arrives, carrying {@code offered} (levels by Buff ordinal, or null for plain): plain plasma, plasma carrying
     * a totem's powers, and plasma carrying a mob power each go to their own pool ({@link TankPools#add}). The tank passes the totem
     * powers on with what its ports give out until that plasma is used up, totem or no totem. Returns what fitted.
     */
    public long add(long mB, int[] offered) {
        long took = Math.max(0, Math.min(mB, capacity() - stored()));
        if (took > 0) {
            pools.add(took, offered, level == null ? 0 : level.getGameTime());
            setChanged();
        }
        return took;
    }

    /** The chamber's loot store (on the master). */
    public net.minecraftforge.items.ItemStackHandler loot() {
        return loot;
    }

    /** Puts {@code stack} in the loot store; returns what did not fit. */
    public net.minecraft.world.item.ItemStack storeLoot(net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.ItemStack rest = stack;
        for (int i = 0; i < loot.getSlots() && !rest.isEmpty(); i++) {
            rest = loot.insertItem(i, rest, false);
        }
        return rest;
    }

    /** Turns up to {@code mB} of plain plasma into mob power {@code b}'s pool at level {@code lvl}, as a kill (I) or feeding does; returns how much. */
    public long tagFromPlain(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff b, int lvl, long mB) {
        long took = pools.tagFromPlain(b, lvl, mB, level == null ? 0 : level.getGameTime());
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    /** Puts up to {@code mB} into the pool of mob power {@code b} at {@code lvl}; returns what fitted. */
    public long addMob(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff b, int lvl, long mB) {
        if (b.onTotem() || lvl <= 0) {
            return 0;
        }
        long took = Math.max(0, Math.min(mB, capacity() - stored()));
        if (took > 0) {
            pools.addMob(b, lvl, took, level == null ? 0 : level.getGameTime());
            setChanged();
        }
        return took;
    }

    /** Takes up to {@code mB} out as a port gives it: plain and totem plasma together, each power's share in proportion. Returns what came. */
    public long drain(long mB) {
        long took = pools.drainOpen(mB);
        if (took > 0) {
            forgetFeeders();
            setChanged();
        }
        return took;
    }

    /** Once the plain and totem plasma they fed is gone, so is who fed it (a Tank Extractor, a kill or feeding may have used it up). */
    void forgetFeeders() {
        if (pools.open() <= 0 && !fedBy.isEmpty()) {
            fedBy = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            setChanged();
        }
        if (anchorTotem != null && anchorTotem() == null) { // the plasma that carried Anchor is used up, or the totem changed hands
            anchorTotem = null;
            anchorOwner = null;
            setChanged();
        }
    }

    /** Takes up to {@code mB} from one mob power's pool; returns what came. */
    public long drainMob(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff b, long mB) {
        long took = pools.drainMob(b, mB);
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    /** The totem powers the stored plasma still carries, by Buff ordinal (0 = none), for the port that gives it out. */
    public int[] offered() {
        return pools.offered();
    }

    /** For a totem power, how much of the totem pool carries it; for a mob power, its pool. */
    public long buffedMb(int ordinal) {
        return pools.mb(ordinal);
    }

    public int buffedLevel(int ordinal) {
        return pools.level(ordinal);
    }

    /** Whether plasma carrying this power came in lately (within the last couple of seconds). */
    public boolean buffLive(int ordinal) {
        return pools.mb(ordinal) > 0 && level != null && level.getGameTime() - pools.fedAt(ordinal) <= 45;
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
        long stored = stored();
        if (dirty || stored != syncedStored) {
            dirty = false;
            syncedStored = stored;
            sync();
        }
        if (chamber) {
            com.mrgregles.bsp_core.chamber.ChamberSpawner.tick(sl, this);
            com.mrgregles.bsp_core.chamber.ChamberFeeder.feed(this, com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.CHAMBER_FEED_PER_SECOND, 64));
        }
        forgetFeeders();
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
        tag.putLong("Stored", stored());
        tag.putBoolean("Chamber", chamber);
        tag.putLong("LastSpawn", lastSpawnAt);
        fedBy.save(tag, "FedBy");
        com.mrgregles.bsp_core.link.Links.save(tag, "Anchor", anchorTotem);
        if (anchorTotem != null && anchorOwner != null) {
            tag.putUUID("AnchorOwner", anchorOwner);
        }
        tag.put("Loot", loot.serializeNBT());
        if (feedItem != null) {
            tag.putString("FeedItem", String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(feedItem)));
        }
        tag.putByte("FeedLevel", (byte) feedLevel);
        tag.putByte("ChamberMode", (byte) chamberMode.ordinal());
        pools.save(tag);
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
        pools.load(tag); // a tank saved before the pools loads with its powers in the totem pool
        chamber = tag.getBoolean("Chamber");
        lastSpawnAt = tag.contains("LastSpawn") ? tag.getLong("LastSpawn") : -1000;
        fedBy = com.mrgregles.bsp_core.plasma.PlasmaAccess.load(tag, "FedBy");
        anchorTotem = com.mrgregles.bsp_core.link.Links.load(tag, "Anchor");
        anchorOwner = anchorTotem != null && tag.hasUUID("AnchorOwner") ? tag.getUUID("AnchorOwner") : null;
        var feedId = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("FeedItem"));
        feedItem = feedId != null && tag.contains("FeedItem") && net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(feedId)
                ? net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(feedId) : null;
        feedLevel = Math.max(1, Math.min(3, tag.getByte("FeedLevel")));
        if (tag.contains("Loot")) {
            CompoundTag l = tag.getCompound("Loot");
            l.putInt("Size", LOOT_SLOTS);
            loot.deserializeNBT(l);
        }
        var modes = com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode.values();
        chamberMode = modes[Math.floorMod(tag.getByte("ChamberMode"), modes.length)];
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
        // who fed the tank and what its loot store holds stay on the server: the screen gets the loot from its own view packet
        tag.remove("FedBy");
        tag.remove("AnchorDim");
        tag.remove("AnchorPos");
        tag.remove("AnchorOwner");
        tag.remove("Loot");
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
