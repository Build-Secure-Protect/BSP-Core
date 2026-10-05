package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A Coin Vault block: 27 slots that hold only Shatter Coins.
 *
 * <p><b>Joining.</b> Vault blocks of the same owner that touch form one vault, as long as the whole
 * group fits in 3 x 3 x 3. Each block keeps its own 27 slots (shown as pages); the access list and
 * the security upgrades are kept the same on every block of the group.
 * <p><b>Access.</b> The owner, the players on the access list and admins can open it. Nothing can
 * be piped in or out, and only the owner or an admin can break it.
 * <p><b>Lockpicking.</b> Anyone else can pick the lock: they must stay within
 * {@code vault.lockpickRadius} blocks for {@code vault.lockpickSeconds}, plus
 * {@code vault.lockpickSecondsPerLockLevel} for each lock level. On success they take
 * {@code vault.lockpickShare} of the coins, chosen at random; Illyrium coins are left alone while
 * the vault holds no more than {@code vault.illyriumSafeCount}. With the Alarm upgrade, the owner
 * and everyone on the access list is warned when picking starts.
 * <p><b>Breaking.</b> Anyone can mine a vault block, so vaults can never wall a totem in, but only
 * the owner gets the coins that way. When anyone else breaks it the coins are kept for the owner,
 * who collects them by opening any vault of theirs.
 */
public class CoinVaultBlockEntity extends BlockEntity {
    public static final int SLOTS = 27, MAX_SPAN = 3, MAX_LOCK = 3, MAX_ALARM = 1, MAX_ACCESS = 8;

    public record Access(UUID id, String name) {
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            recordInLedger();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof ShatterCoinItem;
        }
    };

    @Nullable
    private UUID owner;
    private String ownerName = "";
    private final List<Access> access = new ArrayList<>();
    private int lock, alarm;

    /** When this block was placed; the oldest block of a joined vault decides which side its door is on. */
    private long placed;
    // the joined box this block is drawn as part of: its lowest corner, its size in blocks and its door side
    @Nullable
    private BlockPos boxMin;
    private int boxX = 1, boxY = 1, boxZ = 1;
    private Direction door = Direction.NORTH;

    // the pick in progress; not saved, so a restart cancels it
    @Nullable
    private UUID thief;
    private int pickLeft, pickTotal;

    public CoinVaultBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COIN_VAULT.get(), pos, state);
    }

    // ------------------------------------------------------------------ basics

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

    public List<Access> accessList() {
        return access;
    }

    public int lockLevel() {
        return lock;
    }

    public int alarmLevel() {
        return alarm;
    }

    public boolean isOwner(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** Whether the player may change the access list and buy upgrades. */
    public boolean mayManage(Player player) {
        return isOwner(player) || Admins.isAdmin(player);
    }

    /** Whether the player may open the vault and move coins without picking the lock. */
    public boolean mayOpen(Player player) {
        if (mayManage(player)) {
            return true;
        }
        for (Access a : access) {
            if (a.id().equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        placed = System.currentTimeMillis();
        // a vault added to an existing group takes that group's access list and upgrades
        if (level != null) {
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(worldPosition.relative(d)) instanceof CoinVaultBlockEntity other && owner.equals(other.owner)) {
                    access.clear();
                    access.addAll(other.access);
                    int toLock = Math.max(lock, other.lock), toAlarm = Math.max(alarm, other.alarm);
                    lock = toLock;
                    alarm = toAlarm;
                    for (CoinVaultBlockEntity v : other.group()) {
                        v.lock = toLock;
                        v.alarm = toAlarm;
                        v.changed();
                    }
                    break;
                }
            }
        }
        changed();
        recordInLedger();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int[] coinCounts() {
        int[] out = new int[CoinTier.values().length];
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.getItem() instanceof ShatterCoinItem coin) {
                out[coin.tier.ordinal()] += s.getCount();
            }
        }
        return out;
    }

    void recordInLedger() {
        if (level instanceof ServerLevel sl && owner != null) {
            VaultLedger.get(sl.getServer()).record(owner, GlobalPos.of(sl.dimension(), worldPosition), coinCounts());
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        recordInLedger();
    }

    // ------------------------------------------------------------------ joined vaults

    /** Every block of the vault this block belongs to, in a fixed order; always contains this block. */
    public List<CoinVaultBlockEntity> group() {
        List<CoinVaultBlockEntity> out = new ArrayList<>();
        if (level == null) {
            out.add(this);
            return out;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<CoinVaultBlockEntity> queue = new ArrayDeque<>();
        queue.add(this);
        seen.add(worldPosition);
        int[] min = {worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()}, max = min.clone();
        while (!queue.isEmpty()) {
            CoinVaultBlockEntity v = queue.poll();
            out.add(v);
            for (Direction d : Direction.values()) {
                BlockPos p = v.worldPosition.relative(d);
                if (seen.contains(p) || !(level.getBlockEntity(p) instanceof CoinVaultBlockEntity n) || owner == null || !owner.equals(n.owner)) {
                    continue;
                }
                int[] c = {p.getX(), p.getY(), p.getZ()};
                boolean fits = true;
                for (int i = 0; i < 3; i++) {
                    if (Math.max(max[i], c[i]) - Math.min(min[i], c[i]) >= MAX_SPAN) {
                        fits = false;
                    }
                }
                if (!fits) {
                    continue; // beyond 3 x 3 x 3: that block is a separate vault
                }
                for (int i = 0; i < 3; i++) {
                    min[i] = Math.min(min[i], c[i]);
                    max[i] = Math.max(max[i], c[i]);
                }
                seen.add(p);
                queue.add(n);
            }
        }
        out.sort(Comparator.comparing(BlockEntity::getBlockPos));
        return out;
    }

    /** Whether this block draws the joined vault: it is the lowest corner of the box. */
    public boolean drawsBox() {
        return boxMin != null && boxMin.equals(worldPosition);
    }

    public int boxX() {
        return boxX;
    }

    public int boxY() {
        return boxY;
    }

    public int boxZ() {
        return boxZ;
    }

    public Direction door() {
        return door;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return drawsBox() ? new net.minecraft.world.phys.AABB(worldPosition, worldPosition.offset(boxX, boxY, boxZ)).inflate(0.25) : super.getRenderBoundingBox();
    }

    private void setBox(@Nullable BlockPos min, int x, int y, int z, Direction side) {
        boolean same = java.util.Objects.equals(min, boxMin) && x == boxX && y == boxY && z == boxZ && side == door;
        boxMin = min;
        boxX = x;
        boxY = y;
        boxZ = z;
        door = side;
        BlockState state = getBlockState();
        if (level != null && state.getValue(CoinVaultBlock.FORMED) != (min != null)) {
            level.setBlock(worldPosition, state.setValue(CoinVaultBlock.FORMED, min != null), 3);
        }
        if (!same) {
            changed();
        }
    }

    /**
     * Works out how the vault that {@code start} belongs to is drawn. The largest completely filled
     * box of two or more blocks is drawn as one vault, with its door on the side its oldest block
     * faces; any blocks left over stay single safes. Storage and settings always cover the whole group.
     */
    public static void reform(CoinVaultBlockEntity start) {
        List<CoinVaultBlockEntity> group = start.group();
        BlockPos lo = group.get(0).worldPosition;
        for (CoinVaultBlockEntity v : group) {
            lo = new BlockPos(Math.min(lo.getX(), v.worldPosition.getX()), Math.min(lo.getY(), v.worldPosition.getY()), Math.min(lo.getZ(), v.worldPosition.getZ()));
        }
        boolean[][][] has = new boolean[MAX_SPAN][MAX_SPAN][MAX_SPAN];
        for (CoinVaultBlockEntity v : group) {
            BlockPos r = v.worldPosition.subtract(lo);
            has[r.getX()][r.getY()][r.getZ()] = true;
        }
        int best = 1;
        int[] box = null;
        for (int x0 = 0; x0 < MAX_SPAN; x0++) for (int y0 = 0; y0 < MAX_SPAN; y0++) for (int z0 = 0; z0 < MAX_SPAN; z0++)
            for (int x1 = x0; x1 < MAX_SPAN; x1++) for (int y1 = y0; y1 < MAX_SPAN; y1++) for (int z1 = z0; z1 < MAX_SPAN; z1++) {
                int volume = (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
                if (volume <= best) {
                    continue;
                }
                boolean full = true;
                for (int x = x0; x <= x1 && full; x++) for (int y = y0; y <= y1 && full; y++) for (int z = z0; z <= z1 && full; z++) {
                    full = has[x][y][z];
                }
                if (full) {
                    best = volume;
                    box = new int[]{x0, y0, z0, x1, y1, z1};
                }
            }
        if (box == null) {
            for (CoinVaultBlockEntity v : group) {
                v.setBox(null, 1, 1, 1, Direction.NORTH);
            }
            return;
        }
        BlockPos min = lo.offset(box[0], box[1], box[2]);
        CoinVaultBlockEntity oldest = null;
        List<CoinVaultBlockEntity> inside = new ArrayList<>();
        for (CoinVaultBlockEntity v : group) {
            BlockPos r = v.worldPosition.subtract(lo);
            boolean in = r.getX() >= box[0] && r.getX() <= box[3] && r.getY() >= box[1] && r.getY() <= box[4] && r.getZ() >= box[2] && r.getZ() <= box[5];
            if (!in) {
                v.setBox(null, 1, 1, 1, Direction.NORTH);
                continue;
            }
            inside.add(v);
            if (oldest == null || v.placed < oldest.placed) {
                oldest = v;
            }
        }
        Direction side = oldest.getBlockState().getValue(CoinVaultBlock.FACING);
        for (CoinVaultBlockEntity v : inside) {
            v.setBox(min, box[3] - box[0] + 1, box[4] - box[1] + 1, box[5] - box[2] + 1, side);
        }
    }

    // ------------------------------------------------------------------ access and upgrades (kept the same across the group)

    public boolean addAccess(UUID id, String name) {
        if (id.equals(owner)) {
            return false;
        }
        for (Access a : access) {
            if (a.id().equals(id)) {
                return false;
            }
        }
        if (access.size() >= MAX_ACCESS) {
            return false;
        }
        for (CoinVaultBlockEntity v : group()) {
            v.access.removeIf(a -> a.id().equals(id));
            v.access.add(new Access(id, name));
            v.changed();
        }
        return true;
    }

    public void removeAccess(int index) {
        if (index < 0 || index >= access.size()) {
            return;
        }
        UUID id = access.get(index).id();
        for (CoinVaultBlockEntity v : group()) {
            v.access.removeIf(a -> a.id().equals(id));
            v.changed();
        }
    }

    /** The coin and count the next lock level costs, or null when maxed. Lock levels are paid in Gold, Diamond, Netherite. */
    @Nullable
    public static ItemStack lockCost(int level) {
        if (level >= MAX_LOCK) {
            return null;
        }
        int n = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.VAULT_LOCK_COINS, List.<Integer>of()), level + 1, 4);
        return new ItemStack(CoinTier.values()[Math.min(level + 1, CoinTier.values().length - 1)].coin(), n);
    }

    @Nullable
    public static ItemStack alarmCost(int level) {
        return level >= MAX_ALARM ? null : new ItemStack(CoinTier.GOLD.coin(), BSPConfig.getOr(BSPConfig.VAULT_ALARM_COINS, 4));
    }

    private static boolean pay(ServerPlayer player, ItemStack cost) {
        if (player.isCreative()) {
            return true;
        }
        if (player.getInventory().countItem(cost.getItem()) < cost.getCount()) {
            player.displayClientMessage(Component.translatable("gui.bsp_core.tree.why.coins", cost.getCount(), cost.getHoverName()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(cost.getItem()), cost.getCount(), player.inventoryMenu.getCraftSlots());
        return true;
    }

    public void buyLock(ServerPlayer player) {
        ItemStack cost = lockCost(lock);
        if (cost != null && mayManage(player) && pay(player, cost)) {
            int to = lock + 1;
            for (CoinVaultBlockEntity v : group()) {
                v.lock = to;
                v.changed();
            }
        }
    }

    public void buyAlarm(ServerPlayer player) {
        ItemStack cost = alarmCost(alarm);
        if (cost != null && mayManage(player) && pay(player, cost)) {
            int to = alarm + 1;
            for (CoinVaultBlockEntity v : group()) {
                v.alarm = to;
                v.changed();
            }
        }
    }

    // ------------------------------------------------------------------ lockpicking

    public int pickSeconds() {
        return BSPConfig.VAULT_LOCKPICK_SECONDS.get() + lock * BSPConfig.VAULT_LOCK_LEVEL_SECONDS.get();
    }

    public boolean isPicking(Player player) {
        return thief != null && thief.equals(player.getUUID());
    }

    public int pickLeft() {
        return pickLeft;
    }

    public int pickTotal() {
        return pickTotal;
    }

    private boolean inRange(Player p) {
        double r = BSPConfig.VAULT_LOCKPICK_RADIUS.get() + 0.5;
        return p.level() == level && p.distanceToSqr(worldPosition.getCenter()) <= r * r;
    }

    private void tell(ServerLevel sl, UUID id, Component message) {
        com.mrgregles.bsp_core.storage.NetworkStorage.tell(sl.getServer(), id, message); // follows them to another server of the network
    }

    /** Tells the owner and everyone on the access list. */
    private void warnKeyholders(ServerLevel sl, Component message) {
        if (owner != null) {
            tell(sl, owner, message);
        }
        for (Access a : access) {
            tell(sl, a.id(), message);
        }
    }

    public void startPick(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl) || mayOpen(player) || owner == null) {
            return;
        }
        // one pick at a time on the whole vault
        for (CoinVaultBlockEntity v : group()) {
            if (v.thief != null) {
                player.displayClientMessage(Component.translatable("message.bsp_core.vault.busy").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
        }
        thief = player.getUUID();
        pickTotal = pickLeft = pickSeconds() * 20;
        player.displayClientMessage(Component.translatable("message.bsp_core.vault.pick_started", ownerName, pickSeconds()).withStyle(ChatFormatting.GOLD), false);
        if (alarm > 0) {
            warnKeyholders(sl, Component.translatable("message.bsp_core.vault.alarm", player.getGameProfile().getName(),
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        BSPCore.LOGGER.info("{} started lockpicking {}'s vault at {}", player.getGameProfile().getName(), ownerName, worldPosition);
    }

    private void endPick() {
        thief = null;
        pickLeft = pickTotal = 0;
    }

    public void serverTick(ServerLevel sl) {
        if (thief == null) {
            return;
        }
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(thief);
        if (p == null || !p.isAlive() || !inRange(p)) {
            if (p != null) {
                p.displayClientMessage(Component.translatable("message.bsp_core.vault.pick_failed").withStyle(ChatFormatting.RED), false);
            }
            endPick();
            return;
        }
        if (--pickLeft <= 0) {
            loot(sl, p);
            endPick();
        } else if (pickLeft % 20 == 0) {
            p.displayClientMessage(Component.translatable("message.bsp_core.vault.picking", pickLeft / 20), true);
        }
    }

    /** Hands the thief a share of the coins, chosen coin by coin at random from everything in the joined vault. */
    private void loot(ServerLevel sl, ServerPlayer thiefPlayer) {
        List<CoinVaultBlockEntity> group = group();
        int[] counts = new int[CoinTier.values().length];
        for (CoinVaultBlockEntity v : group) {
            int[] c = v.coinCounts();
            for (int i = 0; i < counts.length; i++) {
                counts[i] += c[i];
            }
        }
        int total = 0;
        for (int c : counts) {
            total += c;
        }
        // Illyrium takes days to press: a small stash of it is never taken
        boolean illyriumSafe = counts[CoinTier.ILLYRIUM.ordinal()] <= BSPConfig.VAULT_ILLYRIUM_SAFE.get();
        int take = total == 0 ? 0 : Math.max(1, (int) Math.round(total * BSPConfig.VAULT_LOCKPICK_SHARE.get()));
        int[] taken = new int[counts.length];
        for (int n = 0; n < take; n++) {
            int pool = 0;
            for (int i = 0; i < counts.length; i++) {
                if (!(illyriumSafe && i == CoinTier.ILLYRIUM.ordinal())) {
                    pool += counts[i] - taken[i];
                }
            }
            if (pool <= 0) {
                break;
            }
            int roll = sl.random.nextInt(pool);
            for (int i = 0; i < counts.length; i++) {
                if (illyriumSafe && i == CoinTier.ILLYRIUM.ordinal()) {
                    continue;
                }
                roll -= counts[i] - taken[i];
                if (roll < 0) {
                    taken[i]++;
                    break;
                }
            }
        }
        int got = 0;
        for (CoinTier tier : CoinTier.values()) {
            int need = taken[tier.ordinal()];
            got += need;
            for (CoinVaultBlockEntity v : group) {
                for (int s = 0; s < SLOTS && need > 0; s++) {
                    ItemStack st = v.items.getStackInSlot(s);
                    if (st.is(tier.coin())) {
                        need -= v.items.extractItem(s, need, false).getCount();
                    }
                }
            }
            int give = taken[tier.ordinal()];
            while (give > 0) {
                ItemStack out = new ItemStack(tier.coin(), Math.min(give, ShatterCoinItem.STACK));
                give -= out.getCount();
                if (!thiefPlayer.getInventory().add(out)) {
                    thiefPlayer.drop(out, false);
                }
            }
        }
        thiefPlayer.displayClientMessage(Component.translatable("message.bsp_core.vault.picked", got, ownerName).withStyle(ChatFormatting.GOLD), false);
        if (owner != null) {
            tell(sl, owner, Component.translatable("message.bsp_core.vault.robbed", thiefPlayer.getGameProfile().getName(), got,
                    worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(ChatFormatting.RED));
        }
        BSPCore.LOGGER.info("{} lockpicked {}'s vault at {} and took {} coins", thiefPlayer.getGameProfile().getName(), ownerName, worldPosition, got);
    }

    // ------------------------------------------------------------------ breaking

    private boolean ownerBroke;

    /** Called just before a player's break removes the block. */
    public void brokenBy(Player player) {
        ownerBroke = isOwner(player);
    }

    /** The block is gone: the owner gets the coins as drops, anyone else's break keeps them for the owner. */
    public void onBroken(ServerLevel sl) {
        VaultLedger ledger = VaultLedger.get(sl.getServer());
        ledger.forget(GlobalPos.of(sl.dimension(), worldPosition));
        if (thief != null) {
            tell(sl, thief, Component.translatable("message.bsp_core.vault.pick_failed").withStyle(ChatFormatting.RED));
        }
        if (ownerBroke || owner == null) {
            for (int i = 0; i < SLOTS; i++) {
                net.minecraft.world.Containers.dropItemStack(sl, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), items.getStackInSlot(i));
            }
            return;
        }
        lock = alarm = 0; // only the owner takes the upgrades along with the block
        int[] coins = coinCounts();
        int total = 0;
        for (int c : coins) {
            total += c;
        }
        if (total > 0) {
            ledger.addRecovered(owner, coins);
        }
        tell(sl, owner, Component.translatable("message.bsp_core.vault.broken", worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), total).withStyle(ChatFormatting.RED));
    }

    /** Gives the owner the coins kept from their broken vaults. */
    public static void collectRecovered(ServerPlayer player) {
        int[] coins = VaultLedger.get(player.server).takeRecovered(player.getUUID());
        int total = 0;
        for (CoinTier tier : CoinTier.values()) {
            int give = coins[tier.ordinal()];
            total += give;
            while (give > 0) {
                ItemStack out = new ItemStack(tier.coin(), Math.min(give, ShatterCoinItem.STACK));
                give -= out.getCount();
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            }
        }
        if (total > 0) {
            player.displayClientMessage(Component.translatable("message.bsp_core.vault.recovered", total).withStyle(ChatFormatting.GOLD), false);
        }
    }

    // ------------------------------------------------------------------ persistence + sync

    private void writeSettings(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        ListTag list = new ListTag();
        for (Access a : access) {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", a.id());
            e.putString("Name", a.name());
            list.add(e);
        }
        tag.put("Access", list);
        tag.putInt("Lock", lock);
        tag.putInt("Alarm", alarm);
        tag.putLong("Placed", placed);
        if (boxMin != null) {
            tag.putLong("BoxMin", boxMin.asLong());
            tag.putIntArray("Box", new int[]{boxX, boxY, boxZ, door.get2DDataValue()});
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        writeSettings(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        access.clear();
        for (Tag raw : tag.getList("Access", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id")) {
                access.add(new Access(e.getUUID("Id"), e.getString("Name")));
            }
        }
        lock = tag.getInt("Lock");
        alarm = tag.getInt("Alarm");
        placed = tag.getLong("Placed");
        int[] b = tag.getIntArray("Box");
        boxMin = tag.contains("BoxMin") && b.length == 4 ? BlockPos.of(tag.getLong("BoxMin")) : null;
        if (boxMin != null) {
            boxX = b[0];
            boxY = b[1];
            boxZ = b[2];
            door = Direction.from2DDataValue(b[3]);
        }
    }

    /** Clients get the owner, access list and upgrades, never the contents: those are only sent to a player who has the vault open. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeSettings(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
