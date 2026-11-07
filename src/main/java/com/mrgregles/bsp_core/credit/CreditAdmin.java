package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.CreditAdminActionPacket;
import com.mrgregles.bsp_core.network.CreditAdminPacket;
import com.mrgregles.bsp_core.network.CreditTicketsPacket;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The server side of the admin panel's CREDIT tab (Store Credit task 10). */
public final class CreditAdmin {
    private CreditAdmin() {}

    /** BSP-Core admins (the admin list; every operator when it is empty) and operators. */
    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2) || Admins.isAdmin(player);
    }

    public static void act(ServerPlayer player, CreditAdminActionPacket msg) {
        act(player, msg, allowed(player));
    }

    /** As {@link #act(ServerPlayer, CreditAdminActionPacket)} with the permission already decided (GameTests: no config file is written). */
    public static void act(ServerPlayer player, CreditAdminActionPacket msg, boolean allowed) {
        if (!allowed) {
            return; // a modified client: nothing is answered
        }
        switch (msg.action()) {
            case REQUEST -> send(player, "", true);
            case SAVE -> {
                CreditKind kind = CreditKind.load(msg.kind());
                // the list sizes first: a modified client could send thousands, and tidying them is not free
                String problem = kind.shops.size() + kind.plots.size() > CreditKind.PLACES_MAX ? "A kind may list at most " + CreditKind.PLACES_MAX + " shops and plot names."
                        : StoreCredit.saveKind(player.server, kind);
                if (problem == null) {
                    BSPCore.LOGGER.info("Store credit kind {} saved by {}", kind.id, player.getGameProfile().getName());
                }
                send(player, problem == null ? "Saved " + kind.name.trim() + "." : problem, problem == null);
            }
            case DELETE -> {
                boolean ok = StoreCredit.deleteKind(player.server, msg.id());
                if (ok) {
                    BSPCore.LOGGER.info("Store credit kind {} deleted by {}", msg.id(), player.getGameProfile().getName());
                }
                send(player, ok ? "Deleted. Its tickets show \"Withdrawn\" and spend nothing." : StoreCredit.storageDown() ? StoreCredit.DOWN : "No such kind.", ok);
            }
            case TICKETS -> {
                if (CreditKind.validId(msg.id())) {
                    BSPNetwork.sendTo(player, tickets(player, msg.id()));
                }
            }
            case VOID -> {
                CreditTicket t = StoreCredit.ticket(player.server, msg.id());
                boolean started = t != null && StoreCredit.voidTicket(player.server, msg.id(), ok -> {
                    if (ok) {
                        BSPCore.LOGGER.info("Store credit ticket {} voided by {}", msg.id(), player.getGameProfile().getName());
                    }
                    BSPNetwork.sendTo(player, tickets(player, t.kind));
                    send(player, ok ? "Voided " + msg.id() + "." : "The network did not take the void; it is still live. Try again shortly.", ok);
                });
                if (!started) {
                    send(player, StoreCredit.storageDown() ? StoreCredit.DOWN : "No such ticket.", false);
                }
            }
        }
    }

    /** The tab's data, with {@code message} as the answer to the last action. */
    public static CreditAdminPacket data(ServerPlayer player, String message, boolean ok) {
        CreditLedger l = CreditLedger.get(player.server);
        Map<String, Integer> given = new LinkedHashMap<>();
        for (CreditTicket t : l.tickets()) {
            given.merge(t.kind, 1, Integer::sum);
        }
        List<CompoundTag> kinds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (CreditKind k : l.kinds()) {
            kinds.add(k.save());
            counts.add(given.getOrDefault(k.id, 0));
        }
        List<CreditAdminPacket.Shop> shops = new ArrayList<>();
        for (Shop s : Shops.ledger(player.server).all().values()) {
            shops.add(new CreditAdminPacket.Shop(s.id, s.name, s.parent));
        }
        shops.sort(Comparator.comparing((CreditAdminPacket.Shop s) -> s.name().toLowerCase(Locale.ROOT)));
        return new CreditAdminPacket(kinds, counts, shops, plotNames(player, l), message, ok);
    }

    /** Plot names this server's Land Office sells, with how many plots share each, then names saved in kinds that it does not sell. */
    static List<CreditAdminPacket.PlotName> plotNames(ServerPlayer player, CreditLedger l) {
        Map<String, Integer> byName = new LinkedHashMap<>();
        Map<String, String> spelled = new LinkedHashMap<>();
        for (RegionGuardClaims.Plot p : RegionGuardClaims.listings(player.serverLevel(), player.getUUID(), player.blockPosition(), null)) {
            if (p.kind() == RegionGuardClaims.Plot.FOR_SALE) {
                String name = p.name().isBlank() ? p.id() : p.name().trim();
                String key = name.toLowerCase(Locale.ROOT);
                spelled.putIfAbsent(key, name);
                byName.merge(key, 1, Integer::sum);
            }
        }
        for (CreditKind k : l.kinds()) {
            for (String p : k.plots) {
                String key = p.toLowerCase(Locale.ROOT);
                spelled.putIfAbsent(key, p);
                byName.putIfAbsent(key, 0);
            }
        }
        List<CreditAdminPacket.PlotName> out = new ArrayList<>();
        byName.forEach((key, n) -> out.add(new CreditAdminPacket.PlotName(spelled.get(key), n)));
        out.sort(Comparator.comparing((CreditAdminPacket.PlotName p) -> p.count() == 0).thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static void send(ServerPlayer player, String message, boolean ok) {
        BSPNetwork.sendTo(player, data(player, message, ok));
    }

    /** A kind's tickets, newest first. */
    public static CreditTicketsPacket tickets(ServerPlayer player, String kindId) {
        CreditLedger l = CreditLedger.get(player.server);
        CreditKind kind = l.kind(kindId);
        List<CreditTicket> all = new ArrayList<>();
        for (CreditTicket t : l.tickets()) {
            if (t.kind.equals(kindId)) {
                all.add(t);
            }
        }
        all.sort(Comparator.comparingLong((CreditTicket t) -> t.createdMs).reversed());
        List<CreditTicketsPacket.Row> rows = new ArrayList<>();
        for (CreditTicket t : all.subList(0, Math.min(CreditTicketsPacket.MAX, all.size()))) {
            rows.add(new CreditTicketsPacket.Row(t.serial, t.ownerName, t.left, t.firstUseMs, t.endsMs(kind), t.voided));
        }
        return new CreditTicketsPacket(kindId, all.size(), rows);
    }
}
