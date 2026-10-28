package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * One server's live trades: who has a Trade Post open here, who has one open on the other servers (each server announces
 * its list every few seconds over the {@link TradeLink}), the requests between them and the trades running. A request
 * lasts 30 seconds; a player has one out at a time; a player who is trading is busy.
 *
 * <p>A trade between two servers is run by the asker's server ({@link TradeSession}); the other player's server holds a
 * {@link RemoteTrade} that sends their actions over and shows the state it gets back. Each server keeps its own player's
 * items until the hand-over, which goes through the {@link TradeDb} so that the items and pieces move once or not at all.
 * Without a link (no Redis) the list and the trades cover this server only; without a database (single server) the pieces
 * move through the local bank. Server thread only.
 */
public final class TradeHub {
    public static final long REQUEST_MS = 30_000, PRESENCE_EVERY_MS = 5_000, PRESENCE_TTL_MS = 15_000, SILENCE_MS = 10_000,
            HEARTBEAT_MS = 2_000, REPLY_MS = 10_000, OUTCOME_MS = 20_000, SWEEP_EVERY_MS = 60_000, ESCROW_AGE_MS = 120_000;
    /** Blocks a trader may stand from the Trade Post they use. */
    public static final double REACH = 8;

    /** A player at a Trade Post here: the post they use (null when not known, as in tests). */
    public record Present(Player player, String name, @Nullable GlobalPos post) {}

    /** One line of the trader list: {@code server} is where they are. */
    public record Trader(UUID id, String name, String server, boolean busy) {}

    public record Request(UUID from, String fromName, String fromServer, UUID to, String toServer, long expires) {}

    String serverId;
    @Nullable
    TradeLink link;
    @Nullable
    TradeDb db;
    private Executor worker = Runnable::run;
    private Consumer<Runnable> onServer = Runnable::run;
    private LongSupplier clock = System::currentTimeMillis;
    @Nullable
    private BiConsumer<Player, Trade> opener;
    @Nullable
    MinecraftServer server;

    private final Map<UUID, Present> present = new LinkedHashMap<>();
    private final Map<String, List<Trader>> remote = new HashMap<>();
    private final Map<String, Long> remoteSeen = new HashMap<>();
    /** Open requests by who asked (made here, or made elsewhere to a player here). */
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<UUID, Trade> trades = new HashMap<>();
    private final Map<String, Trade> byId = new HashMap<>();
    /** Players here who accepted a request from another server and wait for the trade to start there. */
    private final Map<UUID, Long> starting = new HashMap<>();
    private long nextPresence, nextSweep, nextShow;
    /** The posts whose block entity shows someone, to clear when nobody is left. */
    private final java.util.Set<GlobalPos> shown = new java.util.HashSet<>();

    public TradeHub(String serverId) {
        this.serverId = serverId;
    }

    /** Joins the network: the link to the other servers (null: none), the database (null: none), and the threads it runs on. */
    public void connect(String serverId, @Nullable TradeLink link, @Nullable TradeDb db, Executor worker, Consumer<Runnable> onServer) {
        this.serverId = serverId;
        this.link = link;
        this.db = db;
        this.worker = worker;
        this.onServer = onServer;
    }

    public void disconnect() {
        link = null;
        db = null;
        worker = Runnable::run;
        onServer = Runnable::run;
        remote.clear();
        remoteSeen.clear();
    }

    public String serverId() {
        return serverId;
    }

    public long now() {
        return clock.getAsLong();
    }

    public void useClock(@Nullable LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    public void useOpener(@Nullable BiConsumer<Player, Trade> o) {
        opener = o;
    }

    /** Database work on the storage thread; the answer, or the failure, on the server thread. */
    /** Database work for a trade: {@code work} gets the database as it was when the call was made (a stop may disconnect the hub meanwhile). */
    interface Work<T> {
        T run(TradeDb db) throws Exception;
    }

    <T> void db(String what, Work<T> work, Consumer<T> done, Runnable failed) {
        dbOr(what, work, done, untouched -> failed.run());
    }

    /** As {@link #db}; {@code failed} hears whether the database surely did not change ({@link com.mrgregles.bsp_core.market.MarketDb.Untouched}) or might have. */
    <T> void dbOr(String what, Work<T> work, Consumer<T> done, Consumer<Boolean> failed) {
        TradeDb d = db;
        Executor w = worker;
        Consumer<Runnable> back = onServer;
        if (d == null) {
            failed.accept(true);
            return;
        }
        w.execute(() -> {
            T result;
            try {
                result = work.run(d);
            } catch (Exception e) {
                BSPCore.LOGGER.error("BSP-Core trade: could not {}: {}", what, e.toString());
                boolean untouched = e instanceof com.mrgregles.bsp_core.market.MarketDb.Untouched;
                back.accept(() -> failed.accept(untouched));
                return;
            }
            back.accept(() -> done.accept(result));
        });
    }

    void send(String toServer, CompoundTag message) {
        TradeLink l = link;
        if (l != null) {
            l.send(toServer, message);
        }
    }

    static CompoundTag msg(String type) {
        CompoundTag t = new CompoundTag();
        t.putString("t", type);
        return t;
    }

    // ------------------------------------------------------------------ the trader list

    public void join(Player player, @Nullable GlobalPos post) {
        if (server == null) {
            server = player.getServer();
        }
        present.put(player.getUUID(), new Present(player, player.getGameProfile().getName(), post));
        nextPresence = 0;
    }

    /**
     * The player is no longer at a Trade Post: their requests both ways end, here and on the other servers, and a trade they
     * are in ends for {@code reasonKey}.
     */
    public void leave(Player player, String reasonKey) {
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        present.remove(id);
        starting.remove(id);
        nextPresence = 0;
        Request mine = requests.remove(id);
        if (mine != null) {
            endRequestFor(mine.to, mine.toServer, mine.from, name, "request_ended");
        }
        for (Request r : new ArrayList<>(requests.values())) {
            if (r.to.equals(id)) {
                requests.remove(r.from);
                endRequestFor(r.from, r.fromServer, r.to, name, "away");
            }
        }
        Trade t = trades.get(id);
        if (t != null && !t.finished()) {
            t.leave(id, reasonKey, name);
        }
    }

    /** Tells {@code who} (here or on {@code where}) that the request with {@code other} ended. */
    private void endRequestFor(UUID who, String where, UUID other, String otherName, String key) {
        if (where.equals(serverId)) {
            tell(who, Component.translatable("message.bsp_core.trade." + key, otherName));
            return;
        }
        CompoundTag m = msg("ended");
        m.putUUID("who", who);
        m.putUUID("other", other);
        m.putString("name", otherName);
        m.putString("key", key);
        send(where, m);
    }

    /** The Trade Post at {@code post} is gone: everyone using it leaves, and their trades end. */
    public void postBroken(GlobalPos post) {
        for (Present p : new ArrayList<>(present.values())) {
            if (post.equals(p.post)) {
                leave(p.player, "broken");
                // the post is gone: its screen closes too (the lot places go back)
                if (p.player instanceof ServerPlayer sp && sp.containerMenu instanceof TradePostMenu m && post.equals(m.post)) {
                    sp.closeContainer();
                }
            }
        }
    }

    public Optional<Present> present(UUID id) {
        return Optional.ofNullable(present.get(id));
    }

    public boolean busy(UUID id) {
        Trade t = trades.get(id);
        return t != null && !t.finished() || starting.containsKey(id);
    }

    public boolean inTrade(UUID id) {
        Trade t = trades.get(id);
        return t != null && !t.finished();
    }

    @Nullable
    private Trader remoteTrader(UUID id) {
        long now = now();
        for (var e : remote.entrySet()) {
            if (now - remoteSeen.getOrDefault(e.getKey(), 0L) > PRESENCE_TTL_MS) {
                continue;
            }
            for (Trader t : e.getValue()) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
        }
        return null;
    }

    /** Everyone at a Trade Post on the network but the viewer: this server's first, in the order they came. */
    public List<Trader> traders(UUID viewer) {
        List<Trader> out = new ArrayList<>();
        for (Present p : present.values()) {
            if (!p.player.getUUID().equals(viewer)) {
                out.add(new Trader(p.player.getUUID(), p.name, serverId, busy(p.player.getUUID())));
            }
        }
        long now = now();
        remote.forEach((srv, list) -> {
            if (now - remoteSeen.getOrDefault(srv, 0L) <= PRESENCE_TTL_MS) {
                list.stream().filter(t -> !t.id().equals(viewer) && !present.containsKey(t.id())).forEach(out::add);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ requests

    public Optional<Request> requestFrom(UUID from) {
        return Optional.ofNullable(requests.get(from));
    }

    public List<Request> requestsTo(UUID to) {
        return requests.values().stream().filter(r -> r.to.equals(to)).sorted((x, y) -> Long.compare(x.expires, y.expires)).toList();
    }

    /** Asks {@code to}, here or on another server. "ok", "not_here", "self", "away", "busy" or "waiting". */
    public String ask(Player from, UUID to) {
        UUID id = from.getUUID();
        if (!present.containsKey(id)) {
            return "not_here";
        }
        if (id.equals(to)) {
            return "self";
        }
        Present here = present.get(to);
        Trader there = here == null ? remoteTrader(to) : null;
        if (here == null && there == null) {
            return "away";
        }
        if (busy(id) || (here != null ? busy(to) : there.busy())) {
            return "busy";
        }
        if (requests.containsKey(id)) {
            return "waiting";
        }
        String name = from.getGameProfile().getName();
        String where = here != null ? serverId : there.server();
        requests.put(id, new Request(id, name, serverId, to, where, now() + REQUEST_MS));
        if (here != null) {
            tell(to, Component.translatable("message.bsp_core.trade.asked", name));
        } else {
            CompoundTag m = msg("request");
            m.putUUID("from", id);
            m.putString("fromName", name);
            m.putUUID("to", to);
            send(where, m);
        }
        return "ok";
    }

    public void withdraw(UUID from) {
        Request r = requests.remove(from);
        if (r != null) {
            endRequestFor(r.to, r.toServer, r.from, r.fromName, "request_ended");
        }
    }

    /**
     * Answers {@code from}'s request. Accepting starts the trade and opens both trade screens: "ok" (for a request from
     * another server, the trade opens once that server confirms), "gone", "busy" or "away". Declining: "declined".
     */
    public String answer(Player to, UUID from, boolean accept) {
        Request r = requests.get(from);
        if (r == null || !r.to.equals(to.getUUID()) || now() >= r.expires) {
            return "gone";
        }
        requests.remove(from);
        String toName = to.getGameProfile().getName();
        if (!r.fromServer.equals(serverId)) {
            if (accept && (busy(to.getUUID()) || !present.containsKey(to.getUUID()))) {
                accept = false;
            }
            CompoundTag m = msg("answer");
            m.putUUID("from", from);
            m.putUUID("to", to.getUUID());
            m.putString("toName", toName);
            m.putBoolean("accept", accept);
            m.putString("key", accept ? "ok" : "declined");
            send(r.fromServer, m);
            if (accept) {
                withdraw(to.getUUID());
                starting.put(to.getUUID(), now() + REPLY_MS);
                return "ok";
            }
            return "declined";
        }
        if (!accept) {
            tell(from, Component.translatable("message.bsp_core.trade.declined", toName));
            return "declined";
        }
        Present asker = present.get(from);
        if (asker == null || !present.containsKey(to.getUUID())) {
            return "away";
        }
        if (busy(from) || busy(to.getUUID())) {
            return "busy";
        }
        withdraw(to.getUUID());
        TradeSession s = new TradeSession(this, newId(), to.getServer(), new TradeSession.Side(asker.player), new TradeSession.Side(to));
        started(s, asker.player, to);
        return "ok";
    }

    private String newId() {
        return serverId + "-" + Long.toString(ThreadLocalRandom.current().nextLong(1L << 40, 1L << 41), 36);
    }

    private void started(Trade t, @Nullable Player first, @Nullable Player second) {
        byId.put(t.id(), t);
        for (Player p : new Player[]{first, second}) {
            if (p != null) {
                trades.put(p.getUUID(), t);
                starting.remove(p.getUUID());
                if (opener != null) {
                    opener.accept(p, t);
                }
            }
        }
        nextPresence = 0;
        BSPCore.LOGGER.info("BSP-Core trade {}: started", t.id());
    }

    /** The trade here a player is in, of either kind. */
    public Optional<Trade> trade(UUID player) {
        Trade t = trades.get(player);
        return t == null || t.finished() ? Optional.empty() : Optional.of(t);
    }

    /** The trade this server runs that a player is in. */
    public Optional<TradeSession> session(UUID player) {
        return trade(player).filter(t -> t instanceof TradeSession).map(t -> (TradeSession) t);
    }

    void ended(Trade t) {
        byId.remove(t.id());
        trades.values().removeIf(x -> x == t);
        nextPresence = 0;
        for (Present p : new ArrayList<>(present.values())) {
            if (t.has(p.player.getUUID()) && p.player instanceof ServerPlayer sp && sp.containerMenu instanceof TradeMenu m && m.trade() == t) {
                sp.closeContainer();
                // still at the post: back to its screen (a player who closed or walked away has already left the list)
                if (p.post != null && present.containsKey(p.player.getUUID()) && opener != null && !sp.hasDisconnected()) {
                    TradePostMenu.open(sp, new TradePostBlock.Opening(p.post, TradePostBlock.Tab.MARKET));
                }
            }
        }
    }

    // ------------------------------------------------------------------ messages from the other servers

    public void onMessage(String fromServer, CompoundTag m) {
        if (fromServer.equals(serverId)) {
            return;
        }
        switch (m.getString("t")) {
            case "presence" -> {
                List<Trader> list = new ArrayList<>();
                ListTag l = m.getList("list", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    CompoundTag e = l.getCompound(i);
                    list.add(new Trader(e.getUUID("id"), e.getString("name"), fromServer, e.getBoolean("busy")));
                }
                remote.put(fromServer, list);
                remoteSeen.put(fromServer, now());
            }
            case "request" -> {
                UUID from = m.getUUID("from"), to = m.getUUID("to");
                String fromName = m.getString("fromName");
                if (!present.containsKey(to) || busy(to)) {
                    CompoundTag a = msg("answer");
                    a.putUUID("from", from);
                    a.putUUID("to", to);
                    a.putString("toName", present(to).map(Present::name).orElse("?"));
                    a.putBoolean("accept", false);
                    a.putString("key", present.containsKey(to) ? "busy" : "away");
                    send(fromServer, a);
                    return;
                }
                requests.put(from, new Request(from, fromName, fromServer, to, serverId, now() + REQUEST_MS));
                tell(to, Component.translatable("message.bsp_core.trade.asked", fromName));
            }
            case "ended" -> {
                UUID who = m.getUUID("who"), other = m.getUUID("other");
                requests.values().removeIf(r -> r.from.equals(other) && r.to.equals(who) || r.from.equals(who) && r.to.equals(other));
                tell(who, Component.translatable("message.bsp_core.trade." + m.getString("key"), m.getString("name")));
            }
            case "answer" -> onAnswer(fromServer, m);
            case "start" -> {
                UUID b = m.getUUID("b");
                Present p = present.get(b);
                if (p == null || !starting.containsKey(b)) {
                    CompoundTag c = msg("act");
                    c.putString("trade", m.getString("trade"));
                    c.putString("op", "cancel");
                    c.putString("key", "closed");
                    c.putString("by", p == null ? "?" : p.name);
                    send(fromServer, c);
                    return;
                }
                RemoteTrade t = new RemoteTrade(this, m.getString("trade"), fromServer, p.player, m.getUUID("a"), m.getString("aName"));
                started(t, null, p.player);
            }
            case "refuse" -> {
                UUID to = m.getUUID("to");
                starting.remove(to);
                tell(to, Component.translatable("message.bsp_core.trade." + m.getString("key"), m.getString("name")));
            }
            default -> {
                Trade t = byId.get(m.getString("trade"));
                if (t instanceof TradeSession s) {
                    s.onRemote(m);
                } else if (t instanceof RemoteTrade r) {
                    r.onHost(m);
                }
            }
        }
    }

    /** At the asker's server: the other server's answer. Accepted, this server starts the trade and runs it. */
    private void onAnswer(String fromServer, CompoundTag m) {
        UUID from = m.getUUID("from"), to = m.getUUID("to");
        String toName = m.getString("toName");
        Request r = requests.get(from);
        boolean accept = m.getBoolean("accept");
        if (r == null || !r.to.equals(to)) {
            if (accept) {
                refuse(fromServer, to, "gone", "");
            }
            return;
        }
        requests.remove(from);
        if (!accept) {
            String key = m.getString("key");
            tell(from, Component.translatable("message.bsp_core.trade." + (key.equals("busy") || key.equals("away") ? key : "declined"), toName));
            return;
        }
        Present asker = present.get(from);
        if (asker == null || busy(from)) {
            refuse(fromServer, to, asker == null ? "away" : "busy", asker == null ? r.fromName : asker.name);
            return;
        }
        TradeSession s = new TradeSession(this, newId(), asker.player.getServer(), new TradeSession.Side(asker.player), new TradeSession.Side(to, toName, fromServer));
        CompoundTag st = msg("start");
        st.putString("trade", s.id);
        st.putUUID("a", from);
        st.putString("aName", asker.name);
        st.putUUID("b", to);
        send(fromServer, st);
        started(s, asker.player, null);
    }

    private void refuse(String where, UUID to, String key, String name) {
        CompoundTag m = msg("refuse");
        m.putUUID("to", to);
        m.putString("key", key);
        m.putString("name", name);
        send(where, m);
    }

    // ------------------------------------------------------------------ the clock

    /** Every tick: requests run out, trades tick, the trader list goes out, old escrow is swept. */
    /** Ended trades still settling an offer whose escrow's answer was lost. */
    private final List<RemoteTrade> settling = new ArrayList<>();

    void keepSettling(RemoteTrade t) {
        if (!settling.contains(t)) {
            settling.add(t);
        }
    }

    public void tick() {
        long now = now();
        settling.removeIf(t -> t.tickParked(now));
        requests.values().removeIf(r -> {
            if (now >= r.expires) {
                if (r.fromServer.equals(serverId)) {
                    tell(r.from, Component.translatable("message.bsp_core.trade.no_answer", present(r.to).map(Present::name).orElseGet(() -> {
                        Trader t = remoteTrader(r.to);
                        return t == null ? "?" : t.name();
                    })));
                }
                return true;
            }
            return false;
        });
        starting.values().removeIf(until -> now >= until);
        for (Trade t : new ArrayList<>(new LinkedHashSet<>(trades.values()))) {
            t.tick(now);
        }
        if (now >= nextShow) {
            nextShow = now + 500;
            showPosts();
        }
        if (link != null && now >= nextPresence) {
            nextPresence = now + PRESENCE_EVERY_MS;
            CompoundTag m = msg("presence");
            ListTag l = new ListTag();
            present.forEach((id, p) -> {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", id);
                e.putString("name", p.name);
                e.putBoolean("busy", busy(id));
                l.add(e);
            });
            m.put("list", l);
            send("*", m);
        }
        if (db != null && now >= nextSweep) {
            nextSweep = now + SWEEP_EVERY_MS;
            long before = now - ESCROW_AGE_MS;
            db("return old trade escrow", d -> d.sweep(before), n -> {
                if (n > 0) {
                    BSPCore.LOGGER.info("BSP-Core trade: {} stack(s) of an unfinished trade's escrow went back to their owners", n);
                }
            }, () -> {});
        }
    }

    /** Each Trade Post with someone at it shows how many, and the first trade running there; posts left empty are cleared. */
    public void showPosts() {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        Map<GlobalPos, int[]> users = new HashMap<>();
        Map<GlobalPos, List<String>> names = new HashMap<>();
        for (Present p : present.values()) {
            if (p.post == null) {
                continue;
            }
            users.computeIfAbsent(p.post, k -> new int[1])[0]++;
            Trade t = trades.get(p.player.getUUID());
            if (t != null && !t.finished() && !names.containsKey(p.post)) {
                names.put(p.post, t.names());
            }
        }
        java.util.Set<GlobalPos> now = new java.util.HashSet<>(users.keySet());
        for (GlobalPos g : new java.util.HashSet<>(shown)) {
            if (!now.contains(g)) {
                show(srv, g, 0, List.of());
            }
        }
        for (GlobalPos g : now) {
            show(srv, g, users.get(g)[0], names.getOrDefault(g, List.of()));
        }
        shown.clear();
        shown.addAll(now);
    }

    private static void show(MinecraftServer srv, GlobalPos g, int users, List<String> names) {
        var level = srv.getLevel(g.dimension());
        if (level != null && level.isLoaded(g.pos()) && level.getBlockEntity(g.pos()) instanceof TradePostBlockEntity be) {
            be.show(users, names.isEmpty() ? "" : names.get(0), names.size() < 2 ? "" : names.get(1));
        }
    }

    /** The server is stopping: every trade is cancelled and gives its items back. */
    public void stopAll() {
        for (Trade t : new ArrayList<>(new LinkedHashSet<>(trades.values()))) {
            for (Present p : new ArrayList<>(present.values())) {
                if (t.has(p.player.getUUID()) && !t.finished()) {
                    t.leave(p.player.getUUID(), "stopping", "");
                }
            }
        }
        present.clear();
        requests.clear();
        trades.clear();
        byId.clear();
        starting.clear();
        // a single-player game can start another world in the same session: nothing of this one's may stay
        server = null;
        shown.clear();
    }

    void tell(UUID to, Component c) {
        Present p = present.get(to);
        if (p != null && p.player instanceof ServerPlayer sp && !sp.hasDisconnected()) {
            sp.displayClientMessage(c, false);
        }
    }

    static ListTag save(List<ItemStack> items) {
        ListTag l = new ListTag();
        items.forEach(s -> l.add(s.save(new CompoundTag())));
        return l;
    }

    static List<ItemStack> load(ListTag l) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            ItemStack s = ItemStack.of(l.getCompound(i));
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
}
