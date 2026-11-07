package com.mrgregles.bsp_core.storage;

import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.credit.CreditStore;
import com.mrgregles.bsp_core.credit.CreditTicket;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@link CreditStore} over the network database. */
final class DbCredit implements CreditStore {
    private final MySqlStore s;

    DbCredit(MySqlStore s) {
        this.s = s;
    }

    @Override
    public long now() throws Exception {
        return s.dbNowMs();
    }

    @Override
    public void writeKind(CreditKind kind) throws Exception {
        s.creditKindWrite(kind.id, kind.save().toString());
    }

    @Override
    public void deleteKind(String id) throws Exception {
        s.creditKindDelete(id);
    }

    @Override
    public boolean kindExists(String id) throws Exception {
        return s.creditKindExists(id);
    }

    @Override
    public List<CreditKind> readKinds() throws Exception {
        List<CreditKind> out = new ArrayList<>();
        for (Map.Entry<String, String> e : s.creditKindsRead().entrySet()) {
            try {
                CreditKind k = CreditKind.load(TagParser.parseTag(e.getValue()));
                if (CreditKind.validId(k.id)) {
                    out.add(k);
                }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException bad) {
                com.mrgregles.bsp_core.BSPCore.LOGGER.warn("BSP-Core storage: store credit kind {} could not be read: {}", e.getKey(), bad.getMessage());
            }
        }
        return out;
    }

    @Override
    public boolean insertTicket(CreditTicket t) throws Exception {
        return s.creditTicketInsert(t.serial, t.kind, t.left, t.owner, t.ownerName, t.createdMs);
    }

    @Override
    public List<CreditTicket> readTickets(Collection<String> serials) throws Exception {
        return tickets(s.creditTicketsRead(serials));
    }

    @Override
    public List<CreditTicket> changedSince(long sinceMs) throws Exception {
        return tickets(s.creditTicketsChanged(sinceMs));
    }

    private static List<CreditTicket> tickets(List<Object[]> rows) {
        List<CreditTicket> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            CreditTicket t = new CreditTicket((String) r[0], (String) r[1]);
            t.left = (Long) r[2];
            t.firstUseMs = (Long) r[3];
            String owner = (String) r[4];
            t.owner = owner == null || owner.isEmpty() ? null : UUID.fromString(owner);
            t.ownerName = (String) r[5];
            t.voided = (Integer) r[6] != 0;
            t.createdMs = (Long) r[7];
            t.warned = (Integer) r[8];
            t.updatedMs = (Long) r[9];
            out.add(t);
        }
        return out;
    }

    @Override
    public long spend(List<Claim> claims, UUID player, String name) throws Exception {
        List<String> serials = new ArrayList<>();
        List<Long> pieces = new ArrayList<>(), durations = new ArrayList<>();
        for (Claim c : claims) {
            serials.add(c.serial());
            pieces.add(c.pieces());
            durations.add(c.durationMs());
        }
        return s.creditSpend(serials, pieces, durations, player, name);
    }

    @Override
    public boolean refund(String serial, long pieces) throws Exception {
        return s.creditRefund(serial, pieces);
    }

    @Override
    public void voidTicket(String serial) throws Exception {
        s.creditVoid(serial);
    }

    @Override
    public void warned(String serial, int warned) throws Exception {
        s.creditWarned(serial, warned);
    }
}
