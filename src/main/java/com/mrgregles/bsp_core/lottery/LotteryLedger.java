package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The lottery's records on a single server: every player seen, with their ranks, the admin's settings and the past wins. On
 * a network the database holds them instead (the {@code lottery_players} table and shared state keys) and this stays empty. A ledger
 * made with {@code new LotteryLedger()} and not taken from the world is blank, for GameTests.
 */
public class LotteryLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_lottery";

    private final Map<UUID, LotteryPlayer> players = new HashMap<>();
    private LotterySettings settings = LotterySettings.DEFAULT;
    private List<LotteryWin> wins = new ArrayList<>();

    public static LotteryLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LotteryLedger::load, LotteryLedger::new, DATA_NAME);
    }

    /** Keeps the newer of the two sightings' times; the name, operator status and server are always the latest written. */
    public void seen(LotteryPlayer p) {
        seen(p, true);
    }

    /** As {@link #seen(LotteryPlayer)}; with {@code ranksKnown} false (the lookup failed) the ranks stored before are kept. */
    public void seen(LotteryPlayer p, boolean ranksKnown) {
        LotteryPlayer old = players.get(p.id());
        long ms = old != null ? Math.max(old.seenMs(), p.seenMs()) : p.seenMs();
        players.put(p.id(), new LotteryPlayer(p.id(), p.name(), ms, ranksKnown || old == null ? p.ranks() : old.ranks(), p.op(), p.server()));
        setDirty();
    }

    /** Players seen at or after {@code sinceMs}. */
    public List<LotteryPlayer> since(long sinceMs) {
        List<LotteryPlayer> out = new ArrayList<>();
        for (LotteryPlayer p : players.values()) {
            if (p.seenMs() >= sinceMs) {
                out.add(p);
            }
        }
        return out;
    }

    public LotterySettings settings() {
        return settings;
    }

    public void settings(LotterySettings s) {
        settings = s;
        setDirty();
    }

    /** Past wins, newest first. */
    public List<LotteryWin> wins() {
        return List.copyOf(wins);
    }

    public void won(LotteryWin win) {
        wins = LotteryWin.prepend(win, wins);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        players.values().forEach(p -> list.add(p.save()));
        tag.put("Players", list);
        tag.put("Settings", settings.save());
        tag.putString("Wins", LotteryWin.text(wins));
        return tag;
    }

    public static LotteryLedger load(CompoundTag tag) {
        LotteryLedger l = new LotteryLedger();
        for (Tag t : tag.getList("Players", Tag.TAG_COMPOUND)) {
            LotteryPlayer p = LotteryPlayer.load((CompoundTag) t);
            l.players.put(p.id(), p);
        }
        if (tag.contains("Settings")) {
            l.settings = LotterySettings.load(tag.getCompound("Settings"));
        }
        l.wins = LotteryWin.fromText(tag.getString("Wins"));
        return l;
    }
}
