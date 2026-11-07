package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.CreditStripPacket;
import com.mrgregles.bsp_core.network.CreditStripPacket.Summary;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** What the shop and Land Office screens are told about the player's store credit (Store Credit task 8). */
public final class CreditStrip {
    private CreditStrip() {}

    /** A place's summary: the total its tickets can pay and the ticket that pays first; null when none can. */
    public static Summary summary(ServerPlayer player, CreditPlace place, String key) {
        List<CreditSpend.Usable> usable = CreditSpend.usable(player, place);
        if (usable.isEmpty()) {
            return null;
        }
        long total = 0;
        for (CreditSpend.Usable u : usable) {
            total += u.ticket().left;
        }
        CreditSpend.Usable first = usable.get(0);
        return new Summary(key, total, first.kind().name, first.kind().colour, first.kind().mark, first.ticket().endsMs(first.kind()), first.kind().durationMs);
    }

    public static CreditStripPacket forShop(ServerPlayer player, Shop shop) {
        List<Summary> out = new ArrayList<>();
        Summary s = summary(player, Shops.creditPlace(player.server, shop), "shop");
        if (s != null) {
            out.add(s);
        }
        return new CreditStripPacket(CreditSpend.wants(player.getUUID()), out);
    }

    /** The key a plot's summary goes under: {@code plot:} and its name in lower case. */
    public static String plotKey(String name) {
        return "plot:" + name.trim().toLowerCase(Locale.ROOT);
    }

    public static CreditStripPacket forOffice(ServerPlayer player, List<Plot> plots) {
        List<Summary> out = new ArrayList<>();
        Summary ground = summary(player, CreditPlace.ground(), "ground");
        if (ground != null) {
            out.add(ground);
        }
        Set<String> names = new LinkedHashSet<>();
        for (Plot p : plots) {
            if (p.kind() == Plot.FOR_SALE) {
                names.add(p.name().isBlank() ? p.id() : p.name());
            }
        }
        for (String n : names) {
            if (out.size() >= CreditStripPacket.MAX) {
                break;
            }
            Summary s = summary(player, CreditPlace.plot(n), plotKey(n));
            if (s != null) {
                out.add(s);
            }
        }
        return new CreditStripPacket(CreditSpend.wants(player.getUUID()), out);
    }

    public static void sendShop(ServerPlayer player, Shop shop) {
        BSPNetwork.sendTo(player, forShop(player, shop));
    }

    public static void sendOffice(ServerPlayer player, List<Plot> plots) {
        BSPNetwork.sendTo(player, forOffice(player, plots));
    }
}
