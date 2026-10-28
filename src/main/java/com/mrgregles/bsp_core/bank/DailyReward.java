package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The daily login reward: {@code bank.dailyRewardPieces} (default 50, 0 none) into a player's bank once a day, at their first
 * login of the day on any server of the network, or at midnight to everyone online then. Days run midnight to midnight on
 * the shop clock ({@code shop.timeZone}). On a network the day's claim and the payment are one database transaction (the
 * table {@code daily_rewards}), so two servers never both pay a day; on a single server the world keeps each player's last
 * day ({@link Days}). A claim that could not be settled (the database away or its answer lost, the bank account not there
 * yet) is tried again every half minute while the player stays online, for the day it was made for.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class DailyReward {
    private DailyReward() {}

    /** What a claim came to: pieces paid, nothing to pay (already paid today, or the reward is 0), or to be tried again. */
    public enum Outcome { PAID, NONE, RETRY }

    /** The clock; GameTests put their own in its place. */
    private static LongSupplier clock = System::currentTimeMillis;
    /** The day this server last looked at, to notice midnight; -1 before the server has started. */
    private static long dayLooked = -1;
    private static int ticks, retryTicks;
    /** Online players whose claim is to be tried again, with the day it is for. */
    private static final Map<UUID, Long> pending = new HashMap<>();

    public static void useClock(@javax.annotation.Nullable LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    /** The day {@code ms} falls in on the shop clock, as days since 1970-01-01. */
    public static long day(long ms) {
        return LocalDate.ofInstant(Instant.ofEpochMilli(ms), com.mrgregles.bsp_core.shop.Shops.zone()).toEpochDay();
    }

    /** Today on the shop clock. */
    public static long today() {
        return day(clock.getAsLong());
    }

    public static long amount() {
        return BSPConfig.getOr(BSPConfig.BANK_DAILY_REWARD, 50);
    }

    /** A login on {@code day} (read when the player arrived, before the account was opened): that day's reward if still due. */
    public static void atLogin(ServerPlayer player, long day) {
        claim(player.server, player.getUUID(), day);
    }

    /** Claims {@code day} for {@code id}, tells them when paid (wherever they are), and keeps the claim for later when it could not be settled. */
    private static void claim(MinecraftServer server, UUID id, long day) {
        give(server, id, day, (outcome, paid) -> {
            if (outcome == Outcome.RETRY) {
                pending.put(id, day);
                return;
            }
            pending.remove(id);
            if (outcome == Outcome.PAID) {
                NetworkStorage.tell(server, id, Component.translatable("message.bsp_core.bank.daily_reward", Bank.format(paid)).withStyle(ChatFormatting.GOLD));
            }
        });
    }

    /** {@code then} hears the outcome and the pieces paid. */
    public interface Answer {
        void accept(Outcome outcome, long paid);
    }

    /**
     * Pays {@code day}'s reward to {@code id} unless that day (or a later one) was paid already, anywhere on the network, or the
     * reward is 0. A network server without its database answers RETRY.
     */
    public static void give(MinecraftServer server, UUID id, long day, Answer then) {
        long amount = amount();
        if (amount <= 0) {
            then.accept(Outcome.NONE, 0);
            return;
        }
        if (NetworkStorage.countsWanted() && !NetworkStorage.enabled()) {
            then.accept(Outcome.RETRY, 0);
            return;
        }
        if (NetworkStorage.enabled()) {
            NetworkStorage.dailyReward(id, (int) day, amount, d -> {
                switch (d.key()) {
                    case "paid" -> {
                        Bank.changedAndLoggedInDatabase(server, id, d.balance(), BankTransaction.Type.GRANT, "Shatter Bank", "daily reward", d.pieces());
                        then.accept(Outcome.PAID, d.pieces());
                    }
                    case "already" -> then.accept(Outcome.NONE, 0);
                    default -> {
                        if (d.key().equals("unsure")) {
                            Bank.refresh(server, id); // it may have gone in: the balance is read again, and the next try settles it
                        }
                        then.accept(Outcome.RETRY, 0);
                    }
                }
            });
            return;
        }
        Days days = Days.get(server);
        long last = days.last(id);
        if (last >= day && last <= day + 1) { // a day further ahead was written by a clock that ran ahead: not a real payment
            then.accept(Outcome.NONE, 0);
            return;
        }
        Bank.give(server, id, amount, BankTransaction.Type.GRANT, "Shatter Bank", "daily reward", r -> {
            if (r.ok()) {
                days.paid(id, day);
                then.accept(Outcome.PAID, amount);
            } else {
                then.accept(Outcome.RETRY, 0);
            }
        });
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        dayLooked = today();
        if (NetworkStorage.countsWanted() && BSPConfig.SHOP_TIME_ZONE.get().isBlank() && amount() > 0) {
            BSPCore.LOGGER.warn("The daily reward's midnight follows shop.timeZone, which is empty: each server uses its machine's own clock. "
                    + "Set the same shop.timeZone on every server of the network, or a player crossing servers may be paid twice in a day or wait longer.");
        }
    }

    /** Once a second: when the day has changed, everyone online claims the new day; every half minute, claims that failed are tried again. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || dayLooked < 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (++retryTicks >= 600) {
            retryTicks = 0;
            for (Map.Entry<UUID, Long> e : Map.copyOf(pending).entrySet()) {
                if (server.getPlayerList().getPlayer(e.getKey()) == null) {
                    pending.remove(e.getKey());
                } else {
                    claim(server, e.getKey(), e.getValue());
                }
            }
        }
        if (++ticks < 20) {
            return;
        }
        ticks = 0;
        long today = today();
        if (today == dayLooked) {
            return;
        }
        dayLooked = today;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            claim(server, p.getUUID(), today);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        dayLooked = -1;
        ticks = 0;
        retryTicks = 0;
        pending.clear();
    }

    /** A single server's record: the last day each player was paid. */
    public static class Days extends SavedData {
        private static final String DATA_NAME = BSPCore.MODID + "_daily_reward";
        private final Map<UUID, Long> last = new HashMap<>();

        public static Days get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(Days::load, Days::new, DATA_NAME);
        }

        /** The last day {@code id} was paid, -1 never. */
        public long last(UUID id) {
            return last.getOrDefault(id, -1L);
        }

        public void paid(UUID id, long day) {
            last.put(id, day);
            setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            CompoundTag players = new CompoundTag();
            last.forEach((id, day) -> players.putLong(id.toString(), day));
            tag.put("Last", players);
            return tag;
        }

        public static Days load(CompoundTag tag) {
            Days d = new Days();
            CompoundTag players = tag.getCompound("Last");
            for (String k : players.getAllKeys()) {
                try {
                    d.last.put(UUID.fromString(k), players.getLong(k));
                } catch (IllegalArgumentException e) {
                    // not a player id
                }
            }
            return d;
        }
    }
}
