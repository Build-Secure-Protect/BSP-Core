package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Scores set by hand. A player's score is their totems' points plus this adjustment, so a set score keeps moving when they
 * take, lose or raise a totem. Setting a score stores the difference from what their totems give at that moment. Every
 * adjustment is cleared when a season ends.
 *
 * <p>On a single server the adjustments live in this world's saved data. With network storage on they live in the database
 * for the whole network, and this server keeps the copy read with each leaderboard refresh.
 */
public class ScoreAdjust extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_score_adjust";

    /** The answer to a change: {@code ok} false when the database could not be reached; else the new adjustment and score. */
    public record Result(boolean ok, int adjust, int score) {
    }

    private final Map<UUID, Integer> adjust = new HashMap<>();
    /** The network's adjustments as last read from the database. */
    private static volatile Map<UUID, Integer> network = Map.of();

    public static ScoreAdjust get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ScoreAdjust::load, ScoreAdjust::new, DATA_NAME);
    }

    /** Every adjustment that counts here: the network's on a network, else this world's. */
    public static Map<UUID, Integer> all(MinecraftServer server) {
        return NetworkStorage.enabled() ? network : Map.copyOf(get(server).adjust);
    }

    /** One player's adjustment, 0 when none is set. */
    public static int of(MinecraftServer server, UUID player) {
        return all(server).getOrDefault(player, 0);
    }

    /** A leaderboard refresh read the network's adjustments. */
    static void networkRead(Map<UUID, Integer> read) {
        network = Map.copyOf(read);
    }

    /** Makes {@code player}'s score {@code score} (0 or more) by storing the difference from their totems' points. */
    public static void set(MinecraftServer server, UUID player, int score, String by, Consumer<Result> then) {
        change(server, player, Math.max(0, score), false, by, then);
    }

    /** Adds {@code points} (negative takes away) to {@code player}'s adjustment. */
    public static void add(MinecraftServer server, UUID player, int points, String by, Consumer<Result> then) {
        change(server, player, points, true, by, then);
    }

    private static void change(MinecraftServer server, UUID player, int points, boolean add, String by, Consumer<Result> then) {
        if (!NetworkStorage.enabled()) {
            ScoreAdjust data = get(server);
            int now = data.adjust.getOrDefault(player, 0);
            int next = add ? now + points : points - ScoreService.totemPoints(server, player);
            data.put(player, next);
            finish(server, player, next, add, points, by, then);
            return;
        }
        // this server's rows go to the database first (same worker, in order), so "set" counts every totem this server has now
        ScoreService.refresh(server);
        NetworkStorage.scoreAdjust(player, points, add, by, next -> {
            if (next == null) {
                then.accept(new Result(false, 0, 0));
                return;
            }
            Map<UUID, Integer> copy = new HashMap<>(network);
            if (next == 0) {
                copy.remove(player);
            } else {
                copy.put(player, next);
            }
            network = Map.copyOf(copy);
            finish(server, player, next, add, points, by, then);
        });
    }

    private static void finish(MinecraftServer server, UUID player, int next, boolean add, int points, String by, Consumer<Result> then) {
        int score = Math.max(0, ScoreService.totemPoints(server, player) + next);
        BSPCore.LOGGER.info("BSP-Core score: {} {} {} for {}; score {} ({} on top of totems)", by, add ? "added" : "set", points, ScoreService.name(server, player), score, next);
        ScoreService.refresh(server);
        then.accept(new Result(true, next, score));
    }

    /** Removes {@code player}'s adjustment, so their score is their totems' points again. */
    public static void clear(MinecraftServer server, UUID player, String by, Consumer<Result> then) {
        if (!NetworkStorage.enabled()) {
            get(server).put(player, 0);
            done(server, player, by, then);
            return;
        }
        NetworkStorage.scoreAdjustClear(player, ok -> {
            if (!ok) {
                then.accept(new Result(false, 0, 0));
                return;
            }
            Map<UUID, Integer> copy = new HashMap<>(network);
            copy.remove(player);
            network = Map.copyOf(copy);
            done(server, player, by, then);
        });
    }

    private static void done(MinecraftServer server, UUID player, String by, Consumer<Result> then) {
        BSPCore.LOGGER.info("BSP-Core score: {} cleared the score set for {}", by, ScoreService.name(server, player));
        ScoreService.refresh(server);
        then.accept(new Result(true, 0, ScoreService.totemPoints(server, player)));
    }

    /** A season ended: every adjustment goes, here and on the network. */
    public static void clearAll(MinecraftServer server) {
        ScoreAdjust data = get(server);
        if (!data.adjust.isEmpty()) {
            data.adjust.clear();
            data.setDirty();
        }
        if (NetworkStorage.enabled()) {
            network = Map.of();
            NetworkStorage.scoreAdjustClear(null, ok -> {
                if (!ok) {
                    BSPCore.LOGGER.error("BSP-Core score: the scores set by hand could not be cleared for the new season; run /bsp score clear for each");
                }
            });
        }
        ScoreService.markDirty();
    }

    private void put(UUID player, int value) {
        if (value == 0) {
            adjust.remove(player);
        } else {
            adjust.put(player, value);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        adjust.forEach((id, points) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            e.putInt("Points", points);
            list.add(e);
        });
        tag.put("Adjust", list);
        return tag;
    }

    private static ScoreAdjust load(CompoundTag tag) {
        ScoreAdjust data = new ScoreAdjust();
        for (Tag raw : tag.getList("Adjust", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id")) {
                data.adjust.put(e.getUUID("Id"), e.getInt("Points"));
            }
        }
        return data;
    }

    /** Server stopped: the network copy belongs to that run. */
    static void reset() {
        network = Map.of();
    }
}
