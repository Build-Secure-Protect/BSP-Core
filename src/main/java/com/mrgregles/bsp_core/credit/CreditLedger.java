package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This server's store credit kinds and tickets, saved with the overworld. On a network it becomes the local copy of the shared
 * tables (Store Credit task 3).
 */
public class CreditLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_store_credit";

    private final Map<String, CreditKind> kinds = new LinkedHashMap<>();
    private final Map<String, CreditTicket> tickets = new HashMap<>();
    /** Land Office claims paid partly with credit, by "dimension|claim id": what credit paid and who paid it. */
    private final Map<String, ClaimCredit> claims = new HashMap<>();

    /** What store credit paid towards one claim, and the player who paid it. */
    public static final class ClaimCredit {
        public long credit;
        @Nullable
        public java.util.UUID payer;
    }

    public static CreditLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(CreditLedger::load, CreditLedger::new, DATA_NAME);
    }

    @Nullable
    public CreditKind kind(String id) {
        return kinds.get(id);
    }

    /** Every kind, in the order they were made. */
    public Collection<CreditKind> kinds() {
        return kinds.values();
    }

    void putKind(CreditKind kind) {
        kinds.put(kind.id, kind);
        setDirty();
    }

    @Nullable
    CreditKind removeKind(String id) {
        CreditKind k = kinds.remove(id);
        if (k != null) {
            setDirty();
        }
        return k;
    }

    @Nullable
    public CreditTicket ticket(String serial) {
        return tickets.get(serial);
    }

    public Collection<CreditTicket> tickets() {
        return tickets.values();
    }

    boolean hasSerial(String serial) {
        return tickets.containsKey(serial);
    }

    void putTicket(CreditTicket ticket) {
        tickets.put(ticket.serial, ticket);
        setDirty();
    }

    void addClaim(String key, long credit, java.util.UUID payer) {
        ClaimCredit have = claims.computeIfAbsent(key, k -> new ClaimCredit());
        have.credit += credit;
        have.payer = payer;
        setDirty();
    }

    /** What credit paid for a claim, forgotten as it is read; null when credit paid nothing. */
    @Nullable
    ClaimCredit removeClaim(String key) {
        ClaimCredit share = claims.remove(key);
        if (share != null) {
            setDirty();
        }
        return share;
    }

    /** Takes a ticket read from the network: the existing record takes its state, or it is added. */
    void adoptTicket(CreditTicket fresh) {
        CreditTicket have = tickets.get(fresh.serial);
        if (have == null || !have.kind.equals(fresh.kind)) {
            tickets.put(fresh.serial, fresh);
        } else {
            have.adopt(fresh);
        }
        setDirty();
    }

    /** Takes the network's kinds; with {@code prune}, kinds the network does not have are dropped here too. */
    void adoptKinds(java.util.List<CreditKind> fresh, boolean prune) {
        java.util.Set<String> keep = new java.util.HashSet<>();
        for (CreditKind k : fresh) {
            kinds.put(k.id, k);
            keep.add(k.id);
        }
        if (prune) {
            kinds.keySet().removeIf(id -> !keep.contains(id));
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag k = new ListTag();
        kinds.values().forEach(v -> k.add(v.save()));
        tag.put("Kinds", k);
        ListTag t = new ListTag();
        tickets.values().forEach(v -> t.add(v.save()));
        tag.put("Tickets", t);
        ListTag c = new ListTag();
        claims.forEach((id, share) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Claim", id);
            e.putLong("Credit", share.credit);
            if (share.payer != null) {
                e.putUUID("Payer", share.payer);
            }
            c.add(e);
        });
        tag.put("Claims", c);
        return tag;
    }

    public static CreditLedger load(CompoundTag tag) {
        CreditLedger l = new CreditLedger();
        for (Tag raw : tag.getList("Kinds", Tag.TAG_COMPOUND)) {
            CreditKind k = CreditKind.load((CompoundTag) raw);
            if (CreditKind.validId(k.id)) {
                l.kinds.put(k.id, k);
            }
        }
        for (Tag raw : tag.getList("Tickets", Tag.TAG_COMPOUND)) {
            CreditTicket t = CreditTicket.load((CompoundTag) raw);
            if (!t.serial.isEmpty()) {
                l.tickets.put(t.serial, t);
            }
        }
        for (Tag raw : tag.getList("Claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            ClaimCredit share = new ClaimCredit();
            share.credit = e.getLong("Credit");
            share.payer = e.hasUUID("Payer") ? e.getUUID("Payer") : null;
            l.claims.put(e.getString("Claim"), share);
        }
        return l;
    }
}
