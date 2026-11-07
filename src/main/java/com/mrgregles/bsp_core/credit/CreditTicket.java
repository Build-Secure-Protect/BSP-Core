package com.mrgregles.bsp_core.credit;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * One store credit ticket, by its serial: which kind it is, the pieces left on it, when its first purchase was made (its clock
 * starts there), the player it is bound to, and whether an admin voided it. Every copy of a ticket item carries the same serial
 * and so spends from this one record.
 */
public final class CreditTicket {
    public final String serial;
    public final String kind;
    public long left;
    /** Wall-clock ms of the first purchase; 0 until then. */
    public long firstUseMs;
    /** The player it is bound to, or null while unbound. */
    @Nullable
    public UUID owner;
    public String ownerName = "";
    public boolean voided;
    public long createdMs;
    /** Warnings already sent: {@link #WARNED_DAY} (a day left) and {@link #WARNED_HOUR} (an hour left). */
    public int warned;
    /** On a network: when the row last changed (the database's clock). */
    public long updatedMs;

    public static final int WARNED_DAY = 1, WARNED_HOUR = 2;

    public CreditTicket(String serial, String kind) {
        this.serial = serial;
        this.kind = kind;
    }

    /** When the ticket runs out under {@code kind}'s time limit (ms), or 0 when it has no end (no limit, or not used yet). */
    public long endsMs(@Nullable CreditKind kind) {
        return firstUseMs <= 0 || kind == null || kind.durationMs <= 0 ? 0 : firstUseMs + kind.durationMs;
    }

    /** Whether the time limit has run out at {@code now}. */
    public boolean expired(@Nullable CreditKind kind, long now) {
        long end = endsMs(kind);
        return end > 0 && now >= end;
    }

    /** Whether {@code player} may spend it: unbound, or bound to them. */
    public boolean usableBy(UUID player) {
        return owner == null || owner.equals(player);
    }

    /** Takes another copy's state (the network's), keeping this object, which others may hold. */
    public void adopt(CreditTicket o) {
        left = o.left;
        firstUseMs = o.firstUseMs;
        owner = o.owner;
        ownerName = o.ownerName;
        voided = o.voided;
        createdMs = o.createdMs;
        warned = o.warned;
        updatedMs = o.updatedMs;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Serial", serial);
        t.putString("Kind", kind);
        t.putLong("Left", left);
        t.putLong("FirstUse", firstUseMs);
        if (owner != null) {
            t.putUUID("Owner", owner);
        }
        t.putString("OwnerName", ownerName);
        t.putBoolean("Voided", voided);
        t.putLong("Created", createdMs);
        t.putInt("Warned", warned);
        t.putLong("Updated", updatedMs);
        return t;
    }

    public static CreditTicket load(CompoundTag t) {
        CreditTicket c = new CreditTicket(t.getString("Serial"), t.getString("Kind"));
        c.left = t.getLong("Left");
        c.firstUseMs = t.getLong("FirstUse");
        c.owner = t.hasUUID("Owner") ? t.getUUID("Owner") : null;
        c.ownerName = t.getString("OwnerName");
        c.voided = t.getBoolean("Voided");
        c.createdMs = t.getLong("Created");
        c.warned = t.getInt("Warned");
        c.updatedMs = t.getLong("Updated");
        return c;
    }
}
