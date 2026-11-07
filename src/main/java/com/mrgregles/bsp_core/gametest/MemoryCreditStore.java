package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.credit.CreditStore;
import com.mrgregles.bsp_core.credit.CreditTicket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * An in-memory stand-in for the network's store credit tables, with the same rules as the SQL in {@code MySqlStore}: a spend
 * takes every claim or none, and only from tickets that still have their part, are not voided, are unbound or bound to the
 * payer, and are not out of time. Its clock is the test's.
 */
final class MemoryCreditStore implements CreditStore {
    final Map<String, CreditKind> kinds = new LinkedHashMap<>();
    final Map<String, CreditTicket> tickets = new LinkedHashMap<>();
    private final LongSupplier clock;

    MemoryCreditStore(LongSupplier clock) {
        this.clock = clock;
    }

    private static CreditTicket copy(CreditTicket t) {
        return CreditTicket.load(t.save());
    }

    @Override
    public long now() {
        return clock.getAsLong();
    }

    @Override
    public void writeKind(CreditKind kind) {
        kinds.put(kind.id, kind.copy());
    }

    @Override
    public void deleteKind(String id) {
        kinds.remove(id);
    }

    @Override
    public boolean kindExists(String id) {
        return kinds.containsKey(id);
    }

    @Override
    public List<CreditKind> readKinds() {
        List<CreditKind> out = new ArrayList<>();
        kinds.values().forEach(k -> out.add(k.copy()));
        return out;
    }

    @Override
    public boolean insertTicket(CreditTicket ticket) {
        if (tickets.containsKey(ticket.serial)) {
            return false;
        }
        CreditTicket t = copy(ticket);
        t.updatedMs = now();
        tickets.put(t.serial, t);
        return true;
    }

    @Override
    public List<CreditTicket> readTickets(Collection<String> serials) {
        List<CreditTicket> out = new ArrayList<>();
        for (String s : serials) {
            CreditTicket t = tickets.get(s);
            if (t != null) {
                out.add(copy(t));
            }
        }
        return out;
    }

    @Override
    public List<CreditTicket> changedSince(long sinceMs) {
        List<CreditTicket> out = new ArrayList<>();
        tickets.values().stream().filter(t -> t.updatedMs >= sinceMs).forEach(t -> out.add(copy(t)));
        return out;
    }

    @Override
    public long spend(List<Claim> claims, UUID player, String name) {
        long now = now();
        for (Claim c : claims) {
            CreditTicket t = tickets.get(c.serial());
            if (t == null || t.voided || t.left < c.pieces() || t.owner != null && !t.owner.equals(player)
                    || t.firstUseMs != 0 && c.durationMs() != 0 && t.firstUseMs + c.durationMs() <= now) {
                return -1;
            }
        }
        for (Claim c : claims) {
            CreditTicket t = tickets.get(c.serial());
            t.left -= c.pieces();
            if (t.firstUseMs == 0) {
                t.firstUseMs = now;
            }
            if (t.owner == null) {
                t.owner = player;
                t.ownerName = name;
            }
            t.updatedMs = now;
        }
        return now;
    }

    @Override
    public boolean refund(String serial, long pieces) {
        CreditTicket t = tickets.get(serial);
        if (t == null || t.voided) {
            return false;
        }
        t.left += pieces;
        t.updatedMs = now();
        return true;
    }

    @Override
    public void voidTicket(String serial) {
        CreditTicket t = tickets.get(serial);
        if (t != null) {
            t.voided = true;
            t.updatedMs = now();
        }
    }

    @Override
    public void warned(String serial, int warned) {
        CreditTicket t = tickets.get(serial);
        if (t != null) {
            t.warned |= warned;
            t.updatedMs = now();
        }
    }
}
