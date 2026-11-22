package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims;
import com.mrgregles.bsp_core.network.CreditAdminPacket;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The plot names the CREDIT tab offers, from the whole network. Each server publishes its Region Guard plots for sale (every
 * dimension) to the table {@code land_plots}: when it connects, when a claim is bought or given up, and at the five-minute
 * catch-up (only when the list changed, or an hour went by). The tab reads every server's rows when it opens and adds this
 * server's live list. A server that stopped stays listed from its last publish; rows not refreshed for a week drop out.
 * Without network storage the tab lists this server's plots only.
 */
public final class CreditPlots {
    private CreditPlots() {}

    /** How often a server publishes an unchanged list, so its rows never look a week old while it runs. */
    private static final long REPUBLISH_MS = 3_600_000L;

    /** The answer line when other servers' plots could not be read. */
    public static final String UNREAD = "Other servers' plots could not be read; try again shortly.";

    private static List<CreditStore.LandPlot> lastPublished;
    private static long lastPublishMs;
    private static boolean publishing, again;
    /** Other servers' plots as last read (this server's rows left out: its live list is used). */
    private static List<CreditStore.LandPlot> network = List.of();

    /** GameTests only: forgets what was published and read. */
    public static void reset() {
        lastPublished = null;
        lastPublishMs = 0;
        publishing = false;
        again = false;
        network = List.of();
    }

    /** This server's id as the network knows it ({@code storage.serverId}). */
    static String here() {
        // the configured id also while the database is away (Bank.serverId says "local" then)
        return com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted() ? com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.STORAGE_SERVER_ID, "server")
                : com.mrgregles.bsp_core.bank.Bank.serverId();
    }

    /** This server's plots for sale now, in every dimension. */
    public static List<CreditStore.LandPlot> mine(MinecraftServer server) {
        String id = here();
        List<CreditStore.LandPlot> out = new ArrayList<>();
        for (RegionGuardClaims.ForSale p : RegionGuardClaims.forSale(server)) {
            out.add(new CreditStore.LandPlot(id, p.dimension(), p.id(), p.name()));
        }
        return out;
    }

    /**
     * Writes this server's plots for sale to the network: always with {@code force}, otherwise only when the list changed since the
     * last write or an hour went by. Nothing without network storage, or while it is down; a change while a write is on its way is written after it.
     */
    public static void publish(MinecraftServer server, boolean force) {
        if (server == null || !CreditSync.on() || CreditSync.down()) {
            return;
        }
        if (publishing) {
            again = true; // a change while a write is on its way: published once that write answers
            return;
        }
        RegionGuardClaims.ensureListeners(); // a bought or given-up plot publishes again at once
        List<CreditStore.LandPlot> now = mine(server);
        long ms = StoreCredit.now();
        if (!force && now.equals(lastPublished) && ms - lastPublishMs < REPUBLISH_MS) {
            return;
        }
        String id = here();
        publishing = true;
        CreditSync.run("publish this server's plots for sale", s -> {
            s.replacePlots(id, now);
            return Boolean.TRUE;
        }, null, ok -> {
            publishing = false;
            if (ok != null) {
                lastPublished = now;
                lastPublishMs = ms;
            }
            if (again) {
                again = false;
                publish(server, false);
            }
        });
    }

    /** A claim was bought or given up here (Region Guard's events): the network's copy follows. */
    public static void changed(MinecraftServer server) {
        publish(server, false);
    }

    /**
     * Reads every server's plots for the CREDIT tab, then runs {@code then} on the server thread with whether the read worked (true at
     * once without network storage, false at once while a network server's database is away). A failed read keeps the last list read.
     */
    public static void read(java.util.function.Consumer<Boolean> then) {
        if (CreditSync.down()) {
            then.accept(false); // a network server whose database is away (also before it first connects): say so, keep the last list
            return;
        }
        if (!CreditSync.on()) {
            then.accept(true);
            return;
        }
        String id = here();
        CreditSync.run("read the network's plots for sale", CreditStore::readPlots, null, rows -> {
            if (rows != null) {
                List<CreditStore.LandPlot> others = new ArrayList<>();
                for (CreditStore.LandPlot p : rows) {
                    if (!p.server().equals(id)) {
                        others.add(p);
                    }
                }
                network = List.copyOf(others);
            }
            then.accept(rows != null);
        });
    }

    /** The tab's plot names: this server's live plots and the network's last read, then names saved in kinds that nothing sells. */
    public static List<CreditAdminPacket.PlotName> names(MinecraftServer server, Iterable<CreditKind> kinds) {
        boolean networked = CreditSync.on() || CreditSync.down(); // a network server keeps its last read while the database is away
        return combine(mine(server), networked ? network : List.of(), here(), kinds, networked);
    }

    /**
     * Plot names with how many plots share each (one per server, dimension and id) and, on a network, the servers they are on. Names match
     * without case; the first spelling met is shown. Names sold somewhere come first, then saved names nothing sells (count 0), each
     * group by name. Public for the tests.
     */
    public static List<CreditAdminPacket.PlotName> combine(List<CreditStore.LandPlot> here, List<CreditStore.LandPlot> others, String hereId,
                                                           Iterable<CreditKind> kinds, boolean showServers) {
        Map<String, String> spelled = new LinkedHashMap<>();
        Map<String, Set<String>> plots = new LinkedHashMap<>();
        Map<String, Set<String>> servers = new LinkedHashMap<>();
        List<CreditStore.LandPlot> all = new ArrayList<>(here);
        for (CreditStore.LandPlot p : others) {
            if (!p.server().equals(hereId)) {
                all.add(p);
            }
        }
        for (CreditStore.LandPlot p : all) {
            String name = p.name() == null || p.name().isBlank() ? p.id() : p.name().trim();
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            spelled.putIfAbsent(key, name);
            plots.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(p.server() + "\u0000" + p.dimension() + "\u0000" + p.id());
            servers.computeIfAbsent(key, k -> new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER)).add(p.server());
        }
        for (CreditKind k : kinds) {
            for (String p : k.plots) {
                String key = p.toLowerCase(Locale.ROOT);
                spelled.putIfAbsent(key, p);
                plots.putIfAbsent(key, Set.of());
            }
        }
        List<CreditAdminPacket.PlotName> out = new ArrayList<>();
        plots.forEach((key, set) -> out.add(new CreditAdminPacket.PlotName(spelled.get(key), set.size(),
                showServers && servers.containsKey(key) ? List.copyOf(servers.get(key)) : List.of())));
        out.sort(Comparator.comparing((CreditAdminPacket.PlotName p) -> p.count() == 0).thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
        return out;
    }
}
