package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPConfig;
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
 * This server's bank records: every account it has seen and each player's transaction log, saved
 * with the overworld. The record on a single server; a cache of the database on a network.
 */
public class BankLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_bank";

    private final Map<UUID, BankAccount> accounts = new HashMap<>();
    private final Map<UUID, List<BankTransaction>> logs = new HashMap<>();

    public static BankLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BankLedger::load, BankLedger::new, DATA_NAME);
    }

    public BankAccount account(UUID id) {
        return accounts.computeIfAbsent(id, k -> new BankAccount());
    }

    public boolean has(UUID id) {
        return accounts.containsKey(id);
    }

    public Map<UUID, BankAccount> all() {
        return accounts;
    }

    /** The player's log, newest first. */
    public List<BankTransaction> log(UUID id) {
        return logs.getOrDefault(id, List.of());
    }

    public void record(UUID id, BankTransaction tx) {
        List<BankTransaction> list = logs.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(0, tx);
        long oldest = System.currentTimeMillis() - BSPConfig.getOr(BSPConfig.BANK_LOG_DAYS, 90) * 86_400_000L;
        int max = BSPConfig.getOr(BSPConfig.BANK_LOG_ENTRIES, 500);
        list.removeIf(t -> t.ms() < oldest);
        while (list.size() > max) {
            list.remove(list.size() - 1);
        }
        setDirty();
    }

    /** Replaces a player's log with what the database holds (network mode). */
    public void replaceLog(UUID id, List<BankTransaction> newestFirst) {
        logs.put(id, new ArrayList<>(newestFirst));
        setDirty();
    }

    /** Finds an account by player name, case-insensitively; null if this server never saw them. */
    public UUID byName(String name) {
        for (var e : accounts.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) {
                return e.getKey();
            }
        }
        return null;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (var e : accounts.entrySet()) {
            CompoundTag a = e.getValue().save();
            a.putUUID("Id", e.getKey());
            ListTag log = new ListTag();
            for (BankTransaction t : logs.getOrDefault(e.getKey(), List.of())) {
                log.add(t.save());
            }
            a.put("Log", log);
            list.add(a);
        }
        tag.put("Accounts", list);
        return tag;
    }

    public static BankLedger load(CompoundTag tag) {
        BankLedger l = new BankLedger();
        for (Tag t : tag.getList("Accounts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            UUID id = a.getUUID("Id");
            l.accounts.put(id, BankAccount.load(a));
            List<BankTransaction> log = new ArrayList<>();
            for (Tag x : a.getList("Log", Tag.TAG_COMPOUND)) {
                log.add(BankTransaction.load((CompoundTag) x));
            }
            l.logs.put(id, log);
        }
        return l;
    }
}
