package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.api.BspMarket;
import com.mrgregles.bsp_core.network.AdminBankAskPacket;
import com.mrgregles.bsp_core.network.AdminBankDataPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The admin panel's Bank &amp; Market page on the server: what it shows of one player (pieces, the bank's coins, the bank log, their
 * market lots and requests) and what staff do there (refund a row, take a lot or request off the market). Moderators may look;
 * only those {@link Admins#canBank} may act, checked here for every ask. The same calls the staff web panel makes through
 * {@link BspBank} and {@link BspMarket}.
 */
public final class AdminBank {
    private AdminBank() {}

    /** Most log rows, lots and requests one page carries. */
    public static final int MAX_ROWS = 500, MAX_LOTS = 50, MAX_ORDERS = 50;

    /** A staff member's ask from the page. Moderators only; actions only for {@link Admins#canBank}. */
    public static void act(ServerPlayer staff, AdminBankAskPacket ask) {
        if (!Admins.isModerator(staff)) {
            return;
        }
        MinecraftServer server = staff.server;
        if (ask.action() == AdminBankAskPacket.Action.OPEN) {
            send(staff, ask.player(), "");
            return;
        }
        perform(server, staff.getGameProfile().getName(), Admins.canBank(staff), ask, key -> send(staff, ask.player(), key));
    }

    /**
     * Does what was asked, if {@code allowed}; {@code then} gets "ok" or why not ("no_permission", the refund keys of
     * {@link BspBank#refund}, or a cancel's "gone" / "unreachable"). A cancel acts only on a lot or request of the page's player.
     */
    public static void perform(MinecraftServer server, String by, boolean allowed, AdminBankAskPacket ask, Consumer<String> then) {
        if (!allowed) {
            then.accept("no_permission");
            return;
        }
        UUID player = ask.player();
        switch (ask.action()) {
            case REFUND -> BspBank.refund(server, player, ask.rowId(), ask.pieces(), ask.coins(), ask.other(), by, then);
            case CANCEL_LOT -> {
                if (BspMarket.lotsOf(server, player).stream().noneMatch(l -> l.id().equals(ask.entry()))) {
                    then.accept("gone");
                    return;
                }
                BspMarket.cancelLot(server, ask.entry(), by, c -> then.accept(c.key()));
            }
            case CANCEL_ORDER -> {
                if (BspMarket.ordersOf(server, player).stream().noneMatch(o -> o.id().equals(ask.entry()))) {
                    then.accept("gone");
                    return;
                }
                BspMarket.cancelOrder(server, ask.entry(), by, c -> then.accept(c.key()));
            }
            default -> then.accept("");
        }
    }

    /** The page for {@code player} as a staff member who may ({@code canEdit}) or may not act sees it, with the last ask's {@code result}. */
    public static void view(MinecraftServer server, UUID player, boolean canEdit, String result, Consumer<AdminBankDataPacket> then) {
        boolean online = server.getPlayerList().getPlayer(player) != null;
        BspBank.account(server, player, acc -> {
            if (acc == null) {
                then.accept(new AdminBankDataPacket(player, com.mrgregles.bsp_core.bank.Bank.nameOf(server, player), online, canEdit, false, 0, new int[0],
                        List.of(), List.of(), List.of(), result.isEmpty() ? "unreachable" : result));
                return;
            }
            BspBank.log(server, player, rows -> {
                if (rows == null) { // the database could not be read: never this server's copy, whose row numbers a refund cannot use
                    then.accept(new AdminBankDataPacket(player, acc.name(), online, canEdit, false, 0, new int[0], List.of(), List.of(), List.of(),
                            result.isEmpty() ? "unreachable" : result));
                    return;
                }
                int[] coins = acc.coins().stream().mapToInt(Integer::intValue).toArray();
                List<BspBank.Row> kept = rows.size() > MAX_ROWS ? rows.subList(0, MAX_ROWS) : rows;
                List<BspMarket.Lot> lots = BspMarket.lotsOf(server, player).stream().map(AdminBank::shown).toList();
                List<BspMarket.Order> orders = BspMarket.ordersOf(server, player);
                then.accept(new AdminBankDataPacket(player, acc.name(), online, canEdit, true, acc.pieces(), coins, List.copyOf(kept),
                        lots.size() > MAX_LOTS ? lots.subList(0, MAX_LOTS) : lots, orders.size() > MAX_ORDERS ? orders.subList(0, MAX_ORDERS) : orders, result));
            });
        });
    }

    /**
     * A lot as the page shows it: each stack as its item, count and name only, never its full data (a book or a filled box can be
     * large enough to stop the page being sent).
     */
    static BspMarket.Lot shown(BspMarket.Lot l) {
        List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>();
        for (net.minecraft.world.item.ItemStack st : l.items()) {
            net.minecraft.world.item.ItemStack c = new net.minecraft.world.item.ItemStack(st.getItem(), st.getCount());
            if (st.hasCustomHoverName()) {
                c.setHoverName(st.getHoverName());
            }
            items.add(c);
        }
        return new BspMarket.Lot(l.id(), l.seller(), l.sellerName(), l.price(), l.listedMs(), l.endsMs(), l.serverId(), items);
    }

    private static void send(ServerPlayer staff, UUID player, String result) {
        view(staff.server, player, Admins.canBank(staff), result, p -> {
            if (!BSPNetwork.sendChecked(staff, p, AdminBankDataPacket::encode, "the Bank & Market page")) {
                // still too big (a very long log of long rows): the page without its lots and requests, and say so
                BSPNetwork.sendChecked(staff, new AdminBankDataPacket(p.player(), p.name(), p.online(), p.canEdit(), p.reached(), p.pieces(), p.coins(),
                        p.rows().subList(0, Math.min(p.rows().size(), 200)), List.of(), List.of(), "too_big"), AdminBankDataPacket::encode, "the Bank & Market page (cut)");
            }
        });
    }
}
