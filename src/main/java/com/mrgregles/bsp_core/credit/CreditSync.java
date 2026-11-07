package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Store credit across the network (Store Credit task 3). The database holds every kind and every ticket; each server keeps a
 * copy in {@link CreditLedger}. Kinds are written when an admin saves them and announced over Redis (kind {@code credit},
 * {@code k:<id>} or {@code k:-<id>}); a ticket is written when it is minted, and every change to it (a spend, a refund, a void) is
 * made in the database first and announced ({@code t:<serial>[,<serial>...]}), so two servers can never spend the same credit.
 * A ticket a server has never seen (carried in by the inventory sync) is read from the database the first time it turns up.
 * Every five minutes each server catches up on the kinds and on the tickets changed since its last read. Without network storage
 * none of this runs and the local ledger is the record.
 */
public final class CreditSync {
    private CreditSync() {}

    @Nullable
    private static CreditStore testStore;
    private static boolean testDown;
    /** The latest change read so far (the database's clock); the catch-up reads from a little before it. */
    private static long lastChange;
    /** Writes the database refused or missed (it was away): tried again before every catch-up's read. */
    private static final java.util.Set<String> UNWRITTEN_KINDS = new java.util.HashSet<>(), UNWRITTEN_TICKETS = new java.util.HashSet<>();

    /** Serials read lately, with when they may be read again (a ticket the database does not have is asked about once a minute at most). */
    private static final java.util.Map<String, Long> FETCHED = new java.util.HashMap<>();

    /** GameTests only: an in-memory stand-in for the database (answering at once); null to go back to the real one. */
    public static void useStore(@Nullable CreditStore store) {
        testStore = store;
        testDown = false;
        lastChange = 0;
        FETCHED.clear();
        UNWRITTEN_KINDS.clear();
        UNWRITTEN_TICKETS.clear();
    }

    /** GameTests only: with a stand-in store, act as a network server whose database is not connected. */
    public static void useDown(boolean down) {
        testDown = down;
    }

    /** Whether the network keeps the records: credit changes go through the database. */
    public static boolean on() {
        return testStore != null || NetworkStorage.enabled();
    }

    /** A network server whose database is not connected: no credit is minted, spent or changed until it is. */
    public static boolean down() {
        return testStore != null ? testDown : NetworkStorage.down();
    }

    /** Runs work on the database (storage thread), answering on the server thread with the result, or null when it failed. */
    static <T> void run(String what, NetworkStorage.CreditWork<T> work, @Nullable String notice, Consumer<T> then) {
        CreditStore t = testStore;
        if (t == null) {
            NetworkStorage.credit(what, work, notice, then);
            return;
        }
        T answer = null;
        if (!testDown) {
            try {
                answer = work.run(t);
            } catch (Exception e) {
                BSPCore.LOGGER.error("Store credit test store: could not {}: {}", what, e.toString());
            }
        }
        then.accept(answer);
    }

    private static <T> void ignore(T answer) {
    }

    // ------------------------------------------------------------------ writes

    static void kindSaved(CreditKind kind) {
        if (on()) {
            CreditKind copy = kind.copy();
            run("write a store credit kind", s -> {
                s.writeKind(copy);
                return Boolean.TRUE;
            }, "k:" + kind.id, ok -> {
                if (ok == null) {
                    UNWRITTEN_KINDS.add(kind.id); // written again at the next catch-up, before its read could drop it
                }
            });
        }
    }

    static void kindDeleted(String id) {
        if (on()) {
            run("delete a store credit kind", s -> {
                s.deleteKind(id);
                return Boolean.TRUE;
            }, "k:-" + id, CreditSync::ignore);
        }
    }

    static void minted(CreditTicket ticket) {
        if (on()) {
            CreditTicket copy = CreditTicket.load(ticket.save());
            run("write a store credit ticket", s -> {
                if (!s.insertTicket(copy)) {
                    BSPCore.LOGGER.error("Store credit: serial {} is already taken on the network; this copy of the ticket will follow the network's record", copy.serial);
                }
                return Boolean.TRUE;
            }, null, ok -> {
                if (ok == null) {
                    UNWRITTEN_TICKETS.add(copy.serial); // written at the next catch-up; until then the network refuses to spend it
                }
            });
        }
    }

    /** Voids a ticket on the network; {@code then} gets whether the database took it. */
    static void voided(String serial, Consumer<Boolean> then) {
        if (!on()) {
            then.accept(true);
            return;
        }
        run("void a store credit ticket", s -> {
            s.voidTicket(serial);
            return Boolean.TRUE;
        }, "t:" + serial, ok -> then.accept(ok != null));
    }

    static void warned(String serial, int warned) {
        if (on()) {
            run("note a store credit warning", s -> {
                s.warned(serial, warned);
                return Boolean.TRUE;
            }, null, CreditSync::ignore);
        }
    }

    // ------------------------------------------------------------------ reads

    /** At connect: every kind and every ticket. {@code prune} drops kinds the network does not have (after the migrate). */
    public static void load(MinecraftServer server, boolean prune) {
        read(server, 0, prune, "read store credit");
    }

    /** Every five minutes: the kinds, and the tickets changed since the last read (a Redis message may have been missed). */
    public static void catchUp(MinecraftServer server) {
        if (server != null && on()) {
            read(server, Math.max(0, lastChange - 10_000L), NetworkStorage.migratedHere() || testStore != null, "catch up on store credit");
        }
    }

    private record Read(List<CreditKind> kinds, List<CreditTicket> tickets, long now) {}

    private static void read(MinecraftServer server, long since, boolean prune, String what) {
        CreditLedger local = CreditLedger.get(server);
        List<CreditKind> kindsToWrite = new ArrayList<>();
        for (String id : List.copyOf(UNWRITTEN_KINDS)) {
            CreditKind k = local.kind(id);
            if (k != null) {
                kindsToWrite.add(k.copy());
            }
        }
        List<CreditTicket> ticketsToWrite = new ArrayList<>();
        for (String serial : List.copyOf(UNWRITTEN_TICKETS)) {
            CreditTicket t = local.ticket(serial);
            if (t != null) {
                ticketsToWrite.add(CreditTicket.load(t.save()));
            }
        }
        run(what, s -> {
            for (CreditKind k : kindsToWrite) { // writes that failed earlier go first, so the read below cannot drop them
                s.writeKind(k);
            }
            for (CreditTicket t : ticketsToWrite) {
                s.insertTicket(t);
            }
            return new Read(s.readKinds(), s.changedSince(since), s.now());
        }, null, read -> {
            if (read == null) {
                return;
            }
            kindsToWrite.forEach(k -> UNWRITTEN_KINDS.remove(k.id));
            ticketsToWrite.forEach(t -> UNWRITTEN_TICKETS.remove(t.serial));
            CreditLedger l = CreditLedger.get(server);
            l.adoptKinds(read.kinds(), prune);
            for (CreditTicket t : read.tickets()) {
                l.adoptTicket(t);
            }
            lastChange = Math.max(lastChange, read.now()); // the next catch-up reads from here (less a margin), not the whole table
        });
    }

    /** A notice from another server (Redis kind {@code credit}). */
    public static void heard(MinecraftServer server, String payload) {
        CreditLedger l = CreditLedger.get(server);
        if (payload.startsWith("k:-")) {
            l.removeKind(payload.substring(3));
        } else if (payload.startsWith("k:")) {
            run("read store credit kinds", CreditStore::readKinds, null, kinds -> {
                if (kinds != null) {
                    CreditLedger.get(server).adoptKinds(kinds, false);
                }
            });
        } else if (payload.startsWith("t:")) {
            List<String> known = new ArrayList<>();
            for (String serial : payload.substring(2).split(",")) {
                if (l.ticket(serial) != null) {
                    known.add(serial);
                }
            }
            reload(server, known, null);
        }
    }

    /** Reads tickets from the database into the ledger; {@code then} runs after (also when the read failed). */
    public static void reload(MinecraftServer server, Collection<String> serials, @Nullable Runnable then) {
        if (serials.isEmpty() || !on()) {
            if (then != null) {
                then.run();
            }
            return;
        }
        List<String> list = List.copyOf(serials);
        run("read store credit tickets", s -> s.readTickets(list), null, rows -> {
            if (rows != null) {
                CreditLedger l = CreditLedger.get(server);
                for (CreditTicket t : rows) {
                    l.adoptTicket(t);
                    lastChange = Math.max(lastChange, t.updatedMs);
                }
            }
            if (then != null) {
                then.run();
            }
        });
    }

    /** A ticket this server has no record of turned up in an inventory: read it once (again only after the read answered). */
    static void fetch(MinecraftServer server, String serial) {
        long now = System.currentTimeMillis();
        Long next = FETCHED.get(serial);
        if (!on() || next != null && now < next) {
            return;
        }
        if (FETCHED.size() > 4096) {
            FETCHED.values().removeIf(at -> at < now);
        }
        FETCHED.put(serial, now + 60_000L);
        reload(server, List.of(serial), null);
    }
}
