package com.mrgregles.bsp_core.credit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The network's store credit records (the MySQL tables {@code credit_kinds} and {@code credit_tickets}, or a GameTest's stand-in).
 * Every call runs on the storage thread. Times are the database's clock.
 */
public interface CreditStore {
    /** One ticket's share of a spend: its serial, the pieces, and its kind's time limit (0 for none). */
    record Claim(String serial, long pieces, long durationMs) {}

    long now() throws Exception;

    void writeKind(CreditKind kind) throws Exception;

    void deleteKind(String id) throws Exception;

    boolean kindExists(String id) throws Exception;

    List<CreditKind> readKinds() throws Exception;

    /** Writes a new ticket; false when the serial is taken. */
    boolean insertTicket(CreditTicket ticket) throws Exception;

    List<CreditTicket> readTickets(Collection<String> serials) throws Exception;

    /** Tickets changed at or after {@code sinceMs}. */
    List<CreditTicket> changedSince(long sinceMs) throws Exception;

    /**
     * Takes every claim or none: each ticket must have its part left, not be voided, be unbound or bound to {@code player}, and not
     * be out of time. The first purchase is stamped and binds the ticket. Returns the time used, or -1 when refused.
     */
    long spend(List<Claim> claims, UUID player, String name) throws Exception;

    /** Puts pieces back on a ticket that is not voided; false otherwise. */
    boolean refund(String serial, long pieces) throws Exception;

    void voidTicket(String serial) throws Exception;

    void warned(String serial, int warned) throws Exception;
}
