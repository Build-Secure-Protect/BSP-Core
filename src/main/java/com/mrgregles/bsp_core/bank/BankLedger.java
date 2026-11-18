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
    /** The next row id on this server (a single server's rows are numbered here; a network's by the database). */
    private long nextId = 1;

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

    /** Puts a row at the top of the player's log, numbered if it has no id yet; returns the row as kept. */
    public BankTransaction record(UUID id, BankTransaction tx) {
        if (tx.id() == 0) {
            tx = tx.withId(nextId++);
        } else {
            nextId = Math.max(nextId, tx.id() + 1);
        }
        List<BankTransaction> list = logs.computeIfAbsent(id, k -> new ArrayList<>());
        list.add(0, tx);
        long oldest = System.currentTimeMillis() - BSPConfig.getOr(BSPConfig.BANK_LOG_DAYS, 90) * 86_400_000L;
        int max = BSPConfig.getOr(BSPConfig.BANK_LOG_ENTRIES, 500);
        list.removeIf(t -> t.ms() < oldest);
        while (list.size() > max) {
            list.remove(list.size() - 1);
        }
        setDirty();
        return tx;
    }

    /** The player's row with this id, or null (never kept, or trimmed away). */
    @javax.annotation.Nullable
    public BankTransaction find(UUID player, long rowId) {
        for (BankTransaction t : logs.getOrDefault(player, List.of())) {
            if (t.id() == rowId) {
                return t;
            }
        }
        return null;
    }

    /** Puts {@code row} in place of the player's row with the same id; false if it is not in the log any more. */
    public boolean replace(UUID player, BankTransaction row) {
        List<BankTransaction> list = logs.get(player);
        if (list == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == row.id()) {
                list.set(i, row);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /** Replaces a player's log with what the database holds (network mode). */
    public void replaceLog(UUID id, List<BankTransaction> newestFirst) {
        logs.put(id, new ArrayList<>(newestFirst));
        for (BankTransaction t : newestFirst) {
            // keeps this server's numbers above the database's; a row kept here between reads still carries this server's own
            // number, which may be another row's in the database, so refunds and staff pages read the database's rows first (Bank.logFresh)
            nextId = Math.max(nextId, t.id() + 1);
        }
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
        tag.putLong("NextId", nextId);
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
        l.nextId = Math.max(1, tag.getLong("NextId"));
        for (List<BankTransaction> log : l.logs.values()) {
            for (BankTransaction t : log) {
                l.nextId = Math.max(l.nextId, t.id() + 1);
            }
        }
        for (List<BankTransaction> log : l.logs.values()) { // rows saved before rows had ids are numbered once, oldest first
            for (int i = log.size() - 1; i >= 0; i--) {
                if (log.get(i).id() == 0) {
                    log.set(i, log.get(i).withId(l.nextId++));
                }
            }
        }
        return l;
    }
}
