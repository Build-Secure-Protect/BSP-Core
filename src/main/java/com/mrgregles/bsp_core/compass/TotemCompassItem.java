package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Points to the holder's nearest own placed totem. Loaded with Shatter Coins and started from its
 * screen, it instead tracks the nearest totem belonging to someone else until the loaded time runs
 * out, then goes on cooldown. The compass's state lives in the stack's NBT; the server writes the target
 * position once a second and the client's needle reads it. Times are real-clock milliseconds, so a compass carried to another
 * server of the network reads them the same there. The wait also belongs to the player ({@link CompassCooldowns}, and on a
 * network the {@code compass_waits} table), so a new compass cannot skip it, on this server or another.
 */
public class TotemCompassItem extends Item {
    public static final String STORED = "Stored", TRACK_UNTIL = "TrackUntilMs", COOLDOWN_UNTIL = "CooldownUntilMs", LEVEL = "CooldownLevel",
            TARGET = "TargetPos", TARGET_DIM = "TargetDim", RANGE_ACROSS = "RangeAcross", RANGE_DEPTH = "RangeDepth";
    /** The two range ladders: how far across, and how far up or down, a rival-tracking compass finds totems. */
    public enum Range { ACROSS, DEPTH }
    /** Older compasses kept the server's game time in ticks, which differs from server to server. */
    private static final String OLD_TRACK_UNTIL = "TrackUntil", OLD_COOLDOWN_UNTIL = "CooldownUntil";
    /** A converted old time further ahead than this is taken to come from another server's clock, and dropped. */
    private static final long OLD_MAX_MS = 2 * 86_400_000L;
    /** Players whose start is being checked with the network database: a second START waits for the answer. */
    private static final Set<UUID> CLAIMING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A server that stops drops answers still on their way: their players must not stay "being checked" for the next one. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
    public static final class Events {
        private Events() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
            CLAIMING.clear();
        }
    }

    public TotemCompassItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    // ------------------------------------------------------------------ state helpers

    public static int stored(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(STORED) : 0;
    }

    /** Whole seconds from {@code nowMs} to {@code untilMs}, rounded up; 0 once it has passed. */
    public static int secondsTo(long untilMs, long nowMs) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, (untilMs - nowMs + 999) / 1000));
    }

    /** Seconds of rival tracking left at {@code nowMs} (real clock). */
    public static int trackingLeft(ItemStack s, long nowMs) {
        return s.hasTag() ? secondsTo(s.getTag().getLong(TRACK_UNTIL), nowMs) : 0;
    }

    /** Seconds until this compass may start again at {@code nowMs} (real clock): its tracking and the cooldown after it. */
    public static int cooldownLeft(ItemStack s, long nowMs) {
        return s.hasTag() ? secondsTo(s.getTag().getLong(COOLDOWN_UNTIL), nowMs) : 0;
    }

    /**
     * Server: a compass saved by an older BSP-Core holds game-time ticks; they become real-clock times, measured from this server's game
     * time. One that reads more than two days ahead came from another server's clock and is dropped.
     */
    public static void upgradeTimes(ItemStack s, long gameTime, long nowMs) {
        CompoundTag tag = s.getTag();
        if (tag == null || !(tag.contains(OLD_TRACK_UNTIL) || tag.contains(OLD_COOLDOWN_UNTIL))) {
            return;
        }
        for (String[] k : new String[][] {{OLD_TRACK_UNTIL, TRACK_UNTIL}, {OLD_COOLDOWN_UNTIL, COOLDOWN_UNTIL}}) {
            if (tag.contains(k[0])) {
                long left = (tag.getLong(k[0]) - gameTime) * 50L;
                tag.remove(k[0]);
                if (left > 0 && left <= OLD_MAX_MS && !tag.contains(k[1])) {
                    tag.putLong(k[1], nowMs + left);
                }
            }
        }
    }

    public static int level(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(LEVEL) : 0;
    }

    public static int maxLevel() {
        int span = BSPConfig.COMPASS_COOLDOWN.get() - BSPConfig.COMPASS_MIN_COOLDOWN.get();
        int steps = (int) Math.ceil(Math.max(0, span) / (double) BSPConfig.COMPASS_COOLDOWN_STEP.get());
        return Math.min(steps, BSPConfig.COMPASS_UPGRADE_COSTS.get().size());
    }

    public static int cooldownSeconds(ItemStack s) {
        return Math.max(BSPConfig.COMPASS_MIN_COOLDOWN.get(), BSPConfig.COMPASS_COOLDOWN.get() - level(s) * BSPConfig.COMPASS_COOLDOWN_STEP.get());
    }

    /** How many coins the next cooldown upgrade costs, or -1 if fully upgraded. The coin is {@link #upgradeCoin(int)}. */
    public static int upgradeCost(ItemStack s) {
        int lvl = level(s);
        return lvl >= maxLevel() ? -1 : BSPConfig.COMPASS_UPGRADE_COSTS.get().get(lvl);
    }

    /** The Shatter Coin that pays for the upgrade from {@code level} to the next. */
    public static CoinTier upgradeCoin(int level) {
        String key = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.COMPASS_UPGRADE_TIERS, java.util.List.<String>of()), level + 1, "copper");
        for (CoinTier tier : CoinTier.values()) {
            if (tier.key.equals(key)) {
                return tier;
            }
        }
        return CoinTier.COPPER;
    }

    // ------------------------------------------------------------------ range

    private static List<? extends Integer> rangeList(Range r) {
        return BSPConfig.getOr(r == Range.ACROSS ? BSPConfig.COMPASS_RANGE_ACROSS : BSPConfig.COMPASS_RANGE_UP_DOWN, List.<Integer>of());
    }

    /** Range upgrade levels each ladder has: the shortest of the price list and the range list less its first entry. */
    public static int maxRangeLevel() {
        int ladders = Math.min(rangeList(Range.ACROSS).size(), rangeList(Range.DEPTH).size()) - 1;
        return Math.max(0, Math.min(ladders, BSPConfig.getOr(BSPConfig.COMPASS_RANGE_COSTS, List.<Integer>of()).size()));
    }

    /**
     * The compass's level on a range ladder (0 for a new compass). Capped by the range list only, not by the price list: a
     * shorter price list stops further buying but never takes back reach already bought.
     */
    public static int rangeLevel(ItemStack s, Range r) {
        int top = Math.max(0, rangeList(r).size() - 1);
        return s.hasTag() ? Math.max(0, Math.min(top, s.getTag().getInt(r == Range.ACROSS ? RANGE_ACROSS : RANGE_DEPTH))) : 0;
    }

    /** Blocks the ladder reaches at {@code level}. */
    public static int rangeBlocks(Range r, int level) {
        return BSPConfig.levelValue(rangeList(r), level + 1, r == Range.ACROSS ? 100 : 10);
    }

    /** Blocks this compass reaches on the ladder. */
    public static int rangeBlocks(ItemStack s, Range r) {
        return rangeBlocks(r, rangeLevel(s, r));
    }

    /** Coins the range level after {@code level} costs, or -1 at the top. The coin is {@link #rangeCoin(int)}. */
    public static int rangeCost(int level) {
        return level < 0 || level >= maxRangeLevel() ? -1 : BSPConfig.getOr(BSPConfig.COMPASS_RANGE_COSTS, List.<Integer>of()).get(level);
    }

    /** The Shatter Coin that pays for the range level after {@code level}. */
    public static CoinTier rangeCoin(int level) {
        String key = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.COMPASS_RANGE_TIERS, java.util.List.<String>of()), level + 1, "copper");
        for (CoinTier tier : CoinTier.values()) {
            if (tier.key.equals(key)) {
                return tier;
            }
        }
        return CoinTier.COPPER;
    }

    /** Buys the next level of {@code r} on the compass in hand: takes its coins (none in creative). Returns whether it was bought. */
    public static boolean buyRange(ServerPlayer player, ItemStack compass, Range r) {
        int level = rangeLevel(compass, r), cost = rangeCost(level);
        if (cost < 0) {
            return false;
        }
        if (!player.isCreative()) {
            net.minecraft.world.item.Item coin = rangeCoin(level).coin();
            if (player.getInventory().countItem(coin) < cost) {
                return false;
            }
            player.getInventory().clearOrCountMatchingItems(st -> st.is(coin), cost, player.inventoryMenu.getCraftSlots());
        }
        compass.getOrCreateTag().putInt(r == Range.ACROSS ? RANGE_ACROSS : RANGE_DEPTH, level + 1);
        return true;
    }

    /** Whether a totem at {@code totem} is within reach of a compass held at {@code at}: inside the circle across and the band up or down. */
    public static boolean inReach(BlockPos at, BlockPos totem, int across, int upDown) {
        long dx = totem.getX() - at.getX(), dz = totem.getZ() - at.getZ();
        return dx * dx + dz * dz <= (long) across * across && Math.abs(totem.getY() - at.getY()) <= upDown;
    }

    // ------------------------------------------------------------------ actions (server)

    /** Takes one coin of {@code tier} from the player and adds its seconds. */
    public static void loadCoin(ServerPlayer player, ItemStack compass, CoinTier tier) {
        if (player.getInventory().countItem(tier.coin()) <= 0) {
            return;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(tier.coin()), 1, player.inventoryMenu.getCraftSlots());
        int add = BSPConfig.levelValue(BSPConfig.COMPASS_SECONDS_PER_COIN.get(), tier.ordinal() + 1, 1);
        compass.getOrCreateTag().putInt(STORED, stored(compass) + add);
    }

    /**
     * As {@link #upgradeTimes(ItemStack, long, long)} for a compass {@code player} holds, and the wait it carried becomes the
     * player's too (on the network as well), so a new compass cannot skip a wait that started before the update.
     */
    public static void upgradeTimes(ServerPlayer player, ItemStack s) {
        CompoundTag tag = s.getTag();
        if (tag == null || !tag.contains(OLD_COOLDOWN_UNTIL) && !tag.contains(OLD_TRACK_UNTIL)) {
            return;
        }
        long now = System.currentTimeMillis();
        upgradeTimes(s, player.level().getGameTime(), now);
        long until = s.getTag().getLong(COOLDOWN_UNTIL);
        if (until > now) {
            CompassCooldowns.get(player.server).start(player.getUUID(), until);
            com.mrgregles.bsp_core.storage.NetworkStorage.compassRaise(player.getUUID(), until);
        }
    }

    /** Whether {@code stack} is this very stack in {@code player}'s inventory (not a copy left behind when it was moved). */
    private static boolean holds(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || player.hasDisconnected()) {
            return false;
        }
        var inv = player.getInventory();
        for (var list : java.util.List.of(inv.items, inv.offhand, inv.armor)) {
            for (ItemStack it : list) {
                if (it == stack) {
                    return true;
                }
            }
        }
        return player.containerMenu.getCarried() == stack;
    }

    /**
     * Seconds until {@code player} may start tracking with any compass: from the last time they started one, wherever that
     * compass is now and on whichever server of the network. It covers the tracking time and the cooldown after it.
     */
    public static int playerWaitLeft(ServerPlayer player) {
        return secondsTo(CompassCooldowns.get(player.server).until(player.getUUID()), System.currentTimeMillis());
    }

    /** What the compass's screen shows as the cooldown: the longer of this compass's own and the player's. */
    public static int cooldownLeft(ServerPlayer player, ItemStack compass) {
        return Math.max(cooldownLeft(compass, System.currentTimeMillis()), playerWaitLeft(player));
    }

    private static boolean canStart(ServerPlayer player, ItemStack compass, long now) {
        return stored(compass) > 0 && trackingLeft(compass, now) <= 0 && cooldownLeft(compass, now) <= 0 && playerWaitLeft(player) <= 0;
    }

    /**
     * START: spends the loaded seconds on rival tracking, then the cooldown. The player's own wait holds on every compass, so a
     * new compass cannot skip it. On a network the wait is claimed in the database first, so it holds on every server; with the
     * database out of reach nothing starts and the player is told to try again.
     */
    public static void startTracking(ServerPlayer player, ItemStack compass) {
        upgradeTimes(player, compass);
        long now = System.currentTimeMillis();
        if (!canStart(player, compass, now)) {
            return;
        }
        UUID id = player.getUUID();
        // what this START spends and waits, fixed now: coins loaded or an upgrade bought while the network answers change neither
        int seconds = stored(compass), cooldown = cooldownSeconds(compass);
        long until = now + seconds * 1000L + cooldown * 1000L;
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted()) {
            begin(player, compass, now, seconds, cooldown);
            return;
        }
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            player.displayClientMessage(Component.translatable("message.bsp_core.compass.unreachable").withStyle(ChatFormatting.RED), false);
            return;
        }
        if (!CLAIMING.add(id)) {
            return; // the first START is still being checked
        }
        com.mrgregles.bsp_core.storage.NetworkStorage.compassClaim(id, now, until, answer -> {
            CLAIMING.remove(id);
            if (answer == null) {
                player.displayClientMessage(Component.translatable("message.bsp_core.compass.unreachable").withStyle(ChatFormatting.RED), false);
            } else if (answer == until) {
                // claimed: the network holds the wait to {@code until}, so this compass starts with exactly what was claimed
                if (holds(player, compass) && stored(compass) >= seconds && trackingLeft(compass, System.currentTimeMillis()) <= 0) {
                    begin(player, compass, now, seconds, cooldown);
                } else {
                    // the compass was moved (a moved stack is a copy), or the player left, while the network answered: nothing
                    // starts, so the claim is given back (only if it is still this one) and nothing is spent
                    com.mrgregles.bsp_core.storage.NetworkStorage.compassRelease(id, until);
                    if (!player.hasDisconnected()) {
                        player.displayClientMessage(Component.translatable("message.bsp_core.compass.moved").withStyle(ChatFormatting.RED), false);
                    }
                }
            } else {
                CompassCooldowns.get(player.server).start(id, answer); // a wait started elsewhere on the network: the screen shows it now
            }
        });
    }

    /**
     * Starts tracking for {@code seconds} from {@code now}, with {@code cooldown} seconds after it, and records the wait on the
     * compass and the player. Seconds loaded beyond {@code seconds} (while the network answered) stay loaded.
     */
    private static void begin(ServerPlayer player, ItemStack compass, long now, int seconds, int cooldown) {
        long trackUntil = now + seconds * 1000L, cooldownUntil = trackUntil + cooldown * 1000L; // the cooldown runs from the moment tracking ends
        CompoundTag tag = compass.getOrCreateTag();
        tag.putInt(STORED, Math.max(0, stored(compass) - seconds));
        tag.putLong(TRACK_UNTIL, trackUntil);
        tag.putLong(COOLDOWN_UNTIL, cooldownUntil);
        CompassCooldowns waits = CompassCooldowns.get(player.server);
        waits.prune(now);
        waits.start(player.getUUID(), cooldownUntil);
    }

    public static void buyUpgrade(ServerPlayer player, ItemStack compass) {
        int cost = upgradeCost(compass);
        if (cost < 0) {
            return;
        }
        if (!player.isCreative()) {
            net.minecraft.world.item.Item coin = upgradeCoin(level(compass)).coin();
            if (player.getInventory().countItem(coin) < cost) {
                return;
            }
            player.getInventory().clearOrCountMatchingItems(st -> st.is(coin), cost, player.inventoryMenu.getCraftSlots());
        }
        compass.getOrCreateTag().putInt(LEVEL, level(compass) + 1);
    }

    // ------------------------------------------------------------------ targeting

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || !(entity instanceof ServerPlayer player)) {
            return;
        }
        upgradeTimes(player, stack);
        boolean rivals = trackingLeft(stack, System.currentTimeMillis()) > 0;
        BlockPos best = nearest(player, rivals, stack);
        if (rivals) {
            // a working Decoy Totem within its range takes over the needle
            BlockPos decoy = com.mrgregles.bsp_core.decoy.DecoyLedger.get(player.server).fool(level.dimension(), player.blockPosition(), player.getUUID());
            if (decoy != null) {
                best = decoy;
            }
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (best == null) {
            tag.remove(TARGET);
            tag.remove(TARGET_DIM);
        } else {
            tag.put(TARGET, NbtUtils.writeBlockPos(best));
            tag.putString(TARGET_DIM, level.dimension().location().toString());
        }
    }

    /** Nearest placed totem in the player's dimension: their own, or someone else's when {@code rivals}. */
    @Nullable
    /** Whether another owner's totems count as rivals for the needle: anyone outside the player's team (with {@code team.compassIgnoresTeam}). */
    public static boolean isRival(net.minecraft.server.MinecraftServer server, UUID player, UUID owner) {
        if (player.equals(owner)) {
            return false;
        }
        return !com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.TEAM_COMPASS_IGNORES, true)
                || !com.mrgregles.bsp_core.team.Teams.enabled()
                || !com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, player, owner);
    }

    /**
     * Nearest placed totem in the player's dimension: their own at any distance, or with {@code rivals} someone else's within
     * the compass's reach (across and up or down) and within its own seen-at distance ({@link com.mrgregles.bsp_core.totem.TotemRadiation}),
     * or null. For a rival the answer is where the needle points: the totem, or a spot beside it when its signal is weak.
     */
    @Nullable
    public static BlockPos nearest(ServerPlayer player, boolean rivals, ItemStack compass) {
        BlockPos at = player.blockPosition();
        GlobalPos best = null;
        int bestSeenAt = 0;
        double bestDist = Double.MAX_VALUE;
        int across = rangeBlocks(compass, Range.ACROSS), upDown = rangeBlocks(compass, Range.DEPTH);
        TotemLedger ledger = TotemLedger.get(player.server);
        for (Map.Entry<UUID, Set<GlobalPos>> e : ledger.allPlaced().entrySet()) {
            if (e.getKey().equals(player.getUUID()) == rivals) {
                continue;
            }
            if (rivals && !isRival(player.server, player.getUUID(), e.getKey())) {
                continue;
            }
            for (GlobalPos pos : e.getValue()) {
                if (pos.dimension() != player.level().dimension() || rivals && !inReach(at, pos.pos(), across, upDown)) {
                    continue;
                }
                double d = pos.pos().distSqr(at);
                if (d >= bestDist) {
                    continue;
                }
                // a rival totem must also give off enough radiation to be seen from here
                int seenAt = rivals ? com.mrgregles.bsp_core.totem.TotemRadiation.seenAt(player.server, ledger, e.getKey(), pos) : 0;
                if (rivals && !com.mrgregles.bsp_core.totem.TotemRadiation.sees(at, pos.pos(), seenAt)) {
                    continue;
                }
                bestDist = d;
                best = pos;
                bestSeenAt = seenAt;
            }
        }
        if (best == null) {
            return null;
        }
        // a weak signal points beside the totem, so two bearings do not cross on it
        return rivals ? com.mrgregles.bsp_core.totem.TotemRadiation.pointAt(best, at, bestSeenAt, System.currentTimeMillis()) : best.pos();
    }

    /** Client and server: where the needle should point, or null to spin. */
    @Nullable
    public static GlobalPos target(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TARGET)) {
            return null;
        }
        var dim = net.minecraft.resources.ResourceLocation.tryParse(tag.getString(TARGET_DIM));
        if (dim == null) {
            return null;
        }
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim), NbtUtils.readBlockPos(tag.getCompound(TARGET)));
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new TotemCompassMenu(id, inv, hand), Component.translatable("item.bsp_core.totem_compass")),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem(); // the target updates every second; do not bob
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.compass").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bsp_core.compass.reach", String.format(java.util.Locale.ROOT, "%,d", rangeBlocks(stack, Range.ACROSS)),
                String.format(java.util.Locale.ROOT, "%,d", rangeBlocks(stack, Range.DEPTH))).withStyle(ChatFormatting.AQUA));
        if (stored(stack) > 0) {
            tooltip.add(Component.translatable("tooltip.bsp_core.compass.stored", stored(stack)).withStyle(ChatFormatting.GOLD));
        }
    }
}
