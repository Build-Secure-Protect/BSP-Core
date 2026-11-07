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
            TARGET = "TargetPos", TARGET_DIM = "TargetDim";
    /** Older compasses kept the server's game time in ticks, which differs from server to server. */
    private static final String OLD_TRACK_UNTIL = "TrackUntil", OLD_COOLDOWN_UNTIL = "CooldownUntil";
    /** A converted old time further ahead than this is taken to come from another server's clock, and dropped. */
    private static final long OLD_MAX_MS = 2 * 86_400_000L;
    /** Players whose start is being checked with the network database: a second START waits for the answer. */
    private static final Set<UUID> CLAIMING = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        long now = System.currentTimeMillis();
        upgradeTimes(compass, player.level().getGameTime(), now);
        if (!canStart(player, compass, now)) {
            return;
        }
        UUID id = player.getUUID();
        long until = now + stored(compass) * 1000L + cooldownSeconds(compass) * 1000L;
        if (!com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted()) {
            begin(player, compass, now);
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
                // claimed: start this compass, unless it changed meanwhile (its wait then stands, and nothing was spent)
                if (stored(compass) > 0 && trackingLeft(compass, System.currentTimeMillis()) <= 0) {
                    begin(player, compass, now);
                } else {
                    CompassCooldowns.get(player.server).start(id, until);
                }
            } else {
                CompassCooldowns.get(player.server).start(id, answer); // a wait started elsewhere on the network: the screen shows it now
            }
        });
    }

    /** Starts tracking with the loaded seconds from {@code now} and records the wait on the compass and the player. */
    private static void begin(ServerPlayer player, ItemStack compass, long now) {
        int seconds = stored(compass);
        long trackUntil = now + seconds * 1000L, cooldownUntil = trackUntil + cooldownSeconds(compass) * 1000L; // the cooldown runs from the moment tracking ends
        CompoundTag tag = compass.getOrCreateTag();
        tag.putInt(STORED, 0);
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
        upgradeTimes(stack, level.getGameTime(), System.currentTimeMillis());
        boolean rivals = trackingLeft(stack, System.currentTimeMillis()) > 0;
        BlockPos best = nearest(player, rivals);
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

    private static BlockPos nearest(ServerPlayer player, boolean rivals) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<UUID, Set<GlobalPos>> e : TotemLedger.get(player.server).allPlaced().entrySet()) {
            if (e.getKey().equals(player.getUUID()) == rivals) {
                continue;
            }
            if (rivals && !isRival(player.server, player.getUUID(), e.getKey())) {
                continue;
            }
            for (GlobalPos pos : e.getValue()) {
                if (pos.dimension() != player.level().dimension()) {
                    continue;
                }
                double d = pos.pos().distSqr(player.blockPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.pos();
                }
            }
        }
        return best;
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
        if (stored(stack) > 0) {
            tooltip.add(Component.translatable("tooltip.bsp_core.compass.stored", stored(stack)).withStyle(ChatFormatting.GOLD));
        }
    }
}
