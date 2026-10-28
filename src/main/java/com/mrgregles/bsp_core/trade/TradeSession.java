package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.TradeEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One live trade, run by the asker's server. Each side puts up to 12 stacks and a number of pieces in its offer; the items
 * wait in the offer, out of the player's inventory, until the trade ends. When the other player is on another server their
 * side here is a copy of their offer, kept up to date by their server ({@link RemoteTrade}), which gets this trade's state
 * back after every change and every 2 seconds. The rules:
 * <ul>
 *   <li>any change to either offer turns both READY lights off, so nobody is shown one thing and given another;</li>
 *   <li>READY checks the side's pieces against its bank balance (the other server checks its own player); both READY start a
 *       5-second countdown in which the offers are locked and only un-ready stops it;</li>
 *   <li>at the end the space is worked out for both players, stacking included; if either is short nothing moves, both
 *       lights go off and both are told who; then the pieces move and the items cross;</li>
 *   <li>a cancel (closing, walking away, logging out, dying, the post broken, 10 seconds without word from the other
 *       server) gives each side its own offer back, into the inventory or at the feet when it is full.</li>
 * </ul>
 *
 * <p>The hand-over between two servers: this server asks the other to check its player's room and put their offer in
 * escrow in the database; then one transaction writes the attempt's "done" row, moves the pieces and turns both offers into
 * items waiting for the other player, delivered at once. If anything goes wrong first, the attempt's "aborted" row is
 * written instead and the escrow goes back; whichever row comes first is final, so the items cross once or not at all. On a
 * network the pieces of a trade on one server move in that same kind of transaction.
 */
public final class TradeSession implements Trade {
    public enum State { OPEN, COUNTDOWN, SETTLING, DONE, CANCELLED }

    public static final int SLOTS = 12;
    public static final long COUNTDOWN_MS = 5_000;
    public static final int CHAT_MAX = 120, CHAT_KEEP = 50, CHAT_SENT = 20;
    private static final String UNREACHABLE = "\u0000unreachable";

    /** One player's side of the trade; {@code server} is set (and {@code player} null) for a player on another server. */
    public static final class Side {
        @Nullable
        public final Player player;
        public final UUID id;
        public final String name;
        @Nullable
        public final String server;
        public final SimpleContainer offer = new SimpleContainer(SLOTS);
        long pieces;
        boolean ready;

        Side(Player player) {
            this.player = player;
            this.id = player.getUUID();
            this.name = player.getGameProfile().getName();
            this.server = null;
        }

        Side(UUID id, String name, String server) {
            this.player = null;
            this.id = id;
            this.name = name;
            this.server = server;
        }

        public boolean remote() {
            return server != null;
        }

        public long pieces() {
            return pieces;
        }

        public boolean ready() {
            return ready;
        }

        /** The offered stacks, as copies. */
        public List<ItemStack> items() {
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < offer.getContainerSize(); i++) {
                if (!offer.getItem(i).isEmpty()) {
                    out.add(offer.getItem(i).copy());
                }
            }
            return out;
        }
    }

    /** A chat line; {@code name} is "" for the Trade Post's own notes. */
    public record Line(String name, String text) {}

    public final String id;
    public final MinecraftServer server;
    private final TradeHub hub;
    private final Side a, b;
    private State state = State.OPEN;
    private long endsAt;
    private final List<Line> chat = new ArrayList<>();
    private boolean quiet;
    private String cancelAfter = "";

    // a player on another server
    private long lastHeard, nextBeat;
    private boolean dirty = true;
    private int attempts;
    private String attempt = "";
    private long deadline;
    private boolean waitingEscrow, retryAbort;
    /** A one-server settle whose answer was lost and could not be asked about yet: asked again before the next payment. */
    private String unsettled = "";
    private int hostRoom, hostNeed;
    private List<ItemStack> inFlight = List.of();

    TradeSession(TradeHub hub, String id, MinecraftServer server, Side first, Side second) {
        this.hub = hub;
        this.id = id;
        this.server = server;
        this.a = first;
        this.b = second;
        this.lastHeard = hub.now();
        a.offer.addListener(c -> changed());
        b.offer.addListener(c -> changed());
    }

    // ------------------------------------------------------------------ reading

    @Override
    public String id() {
        return id;
    }

    public State state() {
        return state;
    }

    @Override
    public boolean finished() {
        return state == State.DONE || state == State.CANCELLED;
    }

    @Override
    public boolean locked() {
        return state == State.COUNTDOWN || state == State.SETTLING || finished();
    }

    public long countdownLeft(long now) {
        return state == State.COUNTDOWN ? Math.max(0, endsAt - now) : 0;
    }

    public Side first() {
        return a;
    }

    public Side second() {
        return b;
    }

    public Side side(UUID player) {
        return a.id.equals(player) ? a : b;
    }

    @Override
    public boolean has(UUID player) {
        return a.id.equals(player) || b.id.equals(player);
    }

    public Side other(Side s) {
        return s == a ? b : a;
    }

    @Override
    public Container mine(UUID player) {
        return side(player).offer;
    }

    @Override
    public Container theirs(UUID player) {
        return other(side(player)).offer;
    }

    @Override
    public List<Line> chat() {
        return List.copyOf(chat);
    }

    @Override
    public List<String> names() {
        return List.of(a.name, b.name);
    }

    @Override
    public TradeMenu.State state(UUID player) {
        Side s = side(player), o = other(s);
        return new TradeMenu.State(o.name, o.server == null ? hub.serverId() : o.server, s.pieces, o.pieces, s.ready, o.ready, state.name(),
                countdownLeft(hub.now()), locked(), Bank.pieces(server, s.id), chat());
    }

    @Nullable
    private Side remoteSide() {
        return b.remote() ? b : a.remote() ? a : null;
    }

    // ------------------------------------------------------------------ changing the offers

    private void changed() {
        if (quiet || finished()) {
            return;
        }
        dirty = true;
        if (a.ready || b.ready || state == State.COUNTDOWN) {
            a.ready = false;
            b.ready = false;
            state = State.OPEN;
            note("message.bsp_core.trade.changed");
        }
    }

    public String setPieces(Side s, long pieces) {
        if (locked()) {
            return "locked";
        }
        long p = Math.max(0, Math.min(pieces, Market.MAX_PRICE));
        if (p != s.pieces) {
            s.pieces = p;
            changed();
        }
        return "ok";
    }

    /** READY: "ok", "locked", "short", "refused" or "empty". A side on another server was checked by its own server. */
    public String ready(Side s) {
        if (locked()) {
            return "locked";
        }
        if (s.items().stream().anyMatch(Market::refused)) {
            return "refused";
        }
        if (!s.remote() && s.pieces > Bank.pieces(server, s.id)) {
            return "short";
        }
        if (a.items().isEmpty() && b.items().isEmpty() && a.pieces == 0 && b.pieces == 0) {
            return "empty";
        }
        s.ready = true;
        dirty = true;
        if (other(s).ready) {
            state = State.COUNTDOWN;
            endsAt = hub.now() + COUNTDOWN_MS;
            note("message.bsp_core.trade.countdown");
        }
        return "ok";
    }

    public String unready(Side s) {
        if (state == State.SETTLING || finished()) {
            return "locked";
        }
        s.ready = false;
        dirty = true;
        if (state == State.COUNTDOWN) {
            state = State.OPEN;
            note("message.bsp_core.trade.stopped", s.name);
        }
        return "ok";
    }

    /** A chat line from one side: formatting codes taken out, at most 120 characters; logged. */
    public void say(Side s, String text) {
        String t = clean(text);
        if (t.isEmpty() || finished()) {
            return;
        }
        addLine(new Line(s.name, t));
        BSPCore.LOGGER.info("BSP-Core trade {}: {}: {}", id, s.name, t);
        Component line = Component.translatable("message.bsp_core.trade.chat", s.name, t);
        tell(a, line);
        tell(b, line);
    }

    static String clean(String text) {
        String t = text.replaceAll("§.?", "").strip();
        return t.length() > CHAT_MAX ? t.substring(0, CHAT_MAX) : t;
    }

    @Override
    public String setPieces(UUID player, long pieces) {
        return setPieces(side(player), pieces);
    }

    @Override
    public String ready(UUID player) {
        return ready(side(player));
    }

    @Override
    public String unready(UUID player) {
        return unready(side(player));
    }

    @Override
    public void say(UUID player, String text) {
        say(side(player), text);
    }

    @Override
    public void leave(UUID player, String reasonKey, String by) {
        cancel(reasonKey, by);
    }

    // ------------------------------------------------------------------ the other server's messages

    void onRemote(CompoundTag m) {
        Side r = remoteSide();
        if (r == null) {
            return;
        }
        lastHeard = hub.now();
        switch (m.getString("t")) {
            case "act" -> {
                switch (m.getString("op")) {
                    case "offer" -> {
                        List<ItemStack> items = TradeHub.load(m.getList("items", Tag.TAG_COMPOUND));
                        if (!same(items, r.items())) {
                            quiet = true;
                            r.offer.clearContent();
                            for (int i = 0; i < items.size() && i < SLOTS; i++) {
                                r.offer.setItem(i, items.get(i));
                            }
                            quiet = false;
                            changed();
                        }
                    }
                    case "pieces" -> setPieces(r, m.getLong("pieces"));
                    case "ready" -> ready(r);
                    case "unready" -> unready(r);
                    case "say" -> say(r, m.getString("text"));
                    case "cancel" -> cancel(m.getString("key"), m.getString("by"));
                    default -> { } // "ping"
                }
            }
            case "short" -> {
                if (!waitingEscrow || !m.getString("attempt").equals(attempt)) {
                    return;
                }
                waitingEscrow = false;
                backToOpen();
                note("message.bsp_core.trade.no_room", r.name, m.getInt("room"), m.getInt("need"));
                Side here = other(r);
                if (hostRoom < hostNeed) {
                    note("message.bsp_core.trade.no_room", here.name, hostRoom, hostNeed);
                }
            }
            case "escrowed" -> {
                if (!waitingEscrow || !m.getString("attempt").equals(attempt)) {
                    return;
                }
                waitingEscrow = false;
                Side here = other(r);
                if (!m.getBoolean("ok")) {
                    backToOpen();
                    note("message.bsp_core.trade.unreachable");
                } else if (hostRoom < hostNeed) {
                    abortAttempt(() -> note("message.bsp_core.trade.no_room", here.name, hostRoom, hostNeed));
                } else if (!same(TradeHub.load(m.getList("items", Tag.TAG_COMPOUND)), r.items())) {
                    abortAttempt(() -> note("message.bsp_core.trade.changed"));
                } else {
                    handOver(here, r);
                }
            }
            default -> { }
        }
    }

    private void pushState(long now) {
        Side r = remoteSide();
        if (r == null) {
            return;
        }
        Side here = other(r);
        CompoundTag m = TradeHub.msg("state");
        m.putString("trade", id);
        m.putString("state", state.name());
        m.putLong("left", countdownLeft(now));
        m.put("theirs", TradeHub.save(here.items()));
        m.putLong("theirPieces", here.pieces);
        m.putBoolean("theirReady", here.ready);
        m.putLong("myPieces", r.pieces);
        m.putBoolean("myReady", r.ready);
        ListTag lines = new ListTag();
        for (Line l : chat.subList(Math.max(0, chat.size() - CHAT_SENT), chat.size())) {
            CompoundTag t = new CompoundTag();
            t.putString("name", l.name());
            t.putString("text", l.text());
            lines.add(t);
        }
        m.put("chat", lines);
        hub.send(r.server, m);
    }

    // ------------------------------------------------------------------ the end

    @Override
    public void tick(long now) {
        if (state == State.COUNTDOWN && now >= endsAt) {
            settle();
        }
        Side r = remoteSide();
        if (r == null || finished()) {
            return;
        }
        if (state == State.SETTLING) {
            if (now >= deadline && waitingEscrow) {
                waitingEscrow = false;
                abortAttempt(() -> note("message.bsp_core.trade.lost", r.name));
            } else if (now >= deadline && retryAbort) {
                retryAbort = false;
                abortAttempt(() -> note("message.bsp_core.trade.unreachable"));
            }
        } else if (now - lastHeard > TradeHub.SILENCE_MS) {
            cancel("lost", r.name);
            return;
        }
        if (dirty || now >= nextBeat) {
            dirty = false;
            nextBeat = now + TradeHub.HEARTBEAT_MS;
            pushState(now);
        }
    }

    private void settle() {
        Side r = remoteSide();
        if (r != null) {
            Side here = other(r);
            List<ItemStack> toHere = r.items();
            hostNeed = toHere.size();
            hostRoom = fits(here.player.getInventory(), toHere);
            state = State.SETTLING;
            attempt = id + "-" + (++attempts);
            deadline = hub.now() + TradeHub.REPLY_MS;
            waitingEscrow = true;
            dirty = true;
            CompoundTag m = TradeHub.msg("prepare");
            m.putString("trade", id);
            m.putString("attempt", attempt);
            m.put("items", TradeHub.save(here.items()));
            hub.send(r.server, m);
            return;
        }
        List<ItemStack> fromA = a.items(), fromB = b.items();
        int roomB = fits(b.player.getInventory(), fromA), roomA = fits(a.player.getInventory(), fromB);
        if (roomB < fromA.size() || roomA < fromB.size()) {
            backToOpen();
            if (roomB < fromA.size()) {
                note("message.bsp_core.trade.no_room", b.name, roomB, fromA.size());
            }
            if (roomA < fromB.size()) {
                note("message.bsp_core.trade.no_room", a.name, roomA, fromB.size());
            }
            return;
        }
        state = State.SETTLING;
        pay(who -> {
            if (!who.isEmpty()) {
                backToOpen();
                if (who.equals(UNREACHABLE)) {
                    note("message.bsp_core.trade.unreachable");
                } else {
                    note("message.bsp_core.trade.short", who);
                }
                if (!cancelAfter.isEmpty()) {
                    String why = cancelAfter;
                    cancelAfter = "";
                    cancel(why, "");
                }
                return;
            }
            quiet = true;
            a.offer.clearContent();
            b.offer.clearContent();
            quiet = false;
            give(b, fromA);
            give(a, fromB);
            done(fromA, fromB);
        });
    }

    private void backToOpen() {
        a.ready = false;
        b.ready = false;
        state = State.OPEN;
        dirty = true;
    }

    /**
     * Both sides here: moves the pieces; {@code then} hears "" when done, or the name of the side that could not pay (nothing
     * moved). On a network a settle whose answer was lost is asked about (it writes its "done" row first); while the
     * database cannot say, that attempt is {@code unsettled} and the next try asks again before paying anything.
     */
    private void pay(Consumer<String> then) {
        if (hub.db != null) {
            if (!unsettled.isEmpty()) {
                String old = unsettled;
                hub.db("find out whether a trade was paid", d -> d.decideAbort(old), outcome -> {
                    unsettled = "";
                    if (outcome.equals("done")) {
                        BSPCore.LOGGER.warn("BSP-Core trade {}: attempt {} was found paid after its answer was lost", id, old);
                        moved(null);
                        then.accept("");
                    } else {
                        pay(then);
                    }
                }, () -> then.accept(UNREACHABLE));
                return;
            }
            String at = id + "-" + (++attempts);
            hub.dbOr("settle a trade", d -> d.settle(at, a.id, a.pieces, b.id, b.pieces, List.of(), null, null, null), st -> {
                if (st.ok()) {
                    moved(st);
                    then.accept("");
                } else {
                    then.accept(st.key().equals("short_b") ? b.name : a.name);
                }
            }, untouched -> {
                if (untouched) {
                    then.accept(UNREACHABLE);
                    return;
                }
                unsettled = at;
                pay(then); // asks about it at once; if the database cannot say, the trade waits as unreachable
            });
            return;
        }
        take(a, ra -> {
            if (!ra.ok()) {
                then.accept(a.name);
                return;
            }
            take(b, rb -> {
                if (!rb.ok()) {
                    if (a.pieces > 0) {
                        Bank.give(server, a.id, a.pieces, BankTransaction.Type.RECEIVED, b.name, "trade not made: pieces back", x -> {});
                    }
                    then.accept(b.name);
                    return;
                }
                if (a.pieces > 0) {
                    Bank.give(server, b.id, a.pieces, BankTransaction.Type.RECEIVED, a.name, "trade", x -> {});
                }
                if (b.pieces > 0) {
                    Bank.give(server, a.id, b.pieces, BankTransaction.Type.RECEIVED, b.name, "trade", x -> {});
                }
                then.accept("");
            });
        });
    }

    /** After a database settle: the cached balances and the bank log of both players. */
    private void moved(@Nullable TradeDb.Settled st) {
        long aAfter = st == null ? -1 : st.aPieces(), bAfter = st == null ? -1 : st.bPieces();
        if (a.pieces > 0) {
            Bank.changedInDatabase(server, a.id, aAfter, BankTransaction.Type.PAID, b.name, "trade", -a.pieces);
            Bank.changedInDatabase(server, b.id, bAfter, BankTransaction.Type.RECEIVED, a.name, "trade", a.pieces);
        }
        if (b.pieces > 0) {
            Bank.changedInDatabase(server, b.id, bAfter, BankTransaction.Type.PAID, a.name, "trade", -b.pieces);
            Bank.changedInDatabase(server, a.id, aAfter, BankTransaction.Type.RECEIVED, b.name, "trade", b.pieces);
        }
        if (st == null) {
            Bank.refresh(server, a.id);
            Bank.refresh(server, b.id);
        }
    }

    private void take(Side s, Consumer<BankResult> then) {
        if (s.pieces <= 0) {
            then.accept(BankResult.ok(-1));
            return;
        }
        Bank.take(server, s.id, s.pieces, BankTransaction.Type.PAID, other(s).name, "trade", then);
    }

    /** Two servers, both offers checked: one transaction moves the money and both offers; this server delivers its player's share. */
    private void handOver(Side here, Side there) {
        List<ItemStack> fromHere = here.items(), fromThere = there.items();
        inFlight = fromHere;
        quiet = true;
        here.offer.clearContent();
        quiet = false;
        String at = attempt;
        hub.db("hand a trade over", d -> d.settle(at, a.id, a.pieces, b.id, b.pieces, fromHere, there.id, there.id, here.id), st -> {
            if (!st.ok()) {
                putBack(here);
                if (st.key().equals("aborted")) {
                    lostTouch(here, there);
                } else {
                    abortAttempt(() -> note("message.bsp_core.trade.short", st.key().equals("short_b") ? b.name : a.name));
                }
                return;
            }
            inFlight = List.of();
            moved(st);
            finishHere(here, fromHere, fromThere);
        }, () -> {
            // the settle may have gone through with only its answer lost: the database decides first; "aborted" puts this
            // side's items back (abortAttempt), "done" finishes the trade; until it answers they stay out of everyone's hands
            abortAttempt(() -> note("message.bsp_core.trade.unreachable"));
        });
    }

    /** The attempt is done (here, or found done in the database): tell the other server, deliver this side's share, end. */
    private void finishHere(Side here, List<ItemStack> fromHere, List<ItemStack> fromThere) {
        Side there = other(here);
        CompoundTag m = TradeHub.msg("outcome");
        m.putString("trade", id);
        m.putString("attempt", attempt);
        m.putString("outcome", "done");
        hub.send(there.server, m);
        hub.db("deliver a trade", d -> d.takePending(here.id), items -> give(here, items), () -> {});
        if (here == a) {
            done(fromHere, fromThere);
        } else {
            done(fromThere, fromHere);
        }
    }

    /** The attempt failed before the hand-over: decide it "aborted" in the database, so the other server takes its escrow back. */
    private void abortAttempt(Runnable noteIt) {
        Side r = remoteSide();
        String at = attempt;
        hub.db("give up a trade's hand-over", d -> d.decideAbort(at), outcome -> {
            if (outcome.equals("done")) {
                // it went through after all (an answer lost on the way): finish it
                inFlight = List.of();
                moved(null);
                Side here = other(r);
                finishHere(here, List.of(), r.items());
                return;
            }
            putBack(other(r));
            CompoundTag m = TradeHub.msg("outcome");
            m.putString("trade", id);
            m.putString("attempt", at);
            m.putString("outcome", "aborted");
            hub.send(r.server, m);
            backToOpen();
            noteIt.run();
            if (!cancelAfter.isEmpty()) {
                String why = cancelAfter;
                cancelAfter = "";
                cancel(why, "");
            }
        }, () -> {
            retryAbort = true;
            deadline = hub.now() + TradeHub.REPLY_MS;
        });
    }

    /** The other server gave the attempt up first (it heard nothing): this side's items are back; the trade ends. */
    private void lostTouch(Side here, Side there) {
        backToOpen();
        cancel("lost", there.name);
    }

    private void putBack(Side here) {
        if (inFlight.isEmpty()) {
            return;
        }
        quiet = true;
        for (ItemStack s : inFlight) {
            ItemStack left = here.offer.addItem(s);
            if (!left.isEmpty() && here.player != null) {
                here.player.drop(left, false);
            }
        }
        quiet = false;
        inFlight = List.of();
    }

    private void done(List<ItemStack> fromA, List<ItemStack> fromB) {
        state = State.DONE;
        BSPCore.LOGGER.info("BSP-Core trade {}: {} gave {} stack(s) and {} pieces, {} gave {} stack(s) and {} pieces",
                id, a.name, fromA.size(), a.pieces, b.name, fromB.size(), b.pieces);
        tell(a, Component.translatable("message.bsp_core.trade.done", b.name));
        tell(b, Component.translatable("message.bsp_core.trade.done", a.name));
        MinecraftForge.EVENT_BUS.post(new TradeEvent.Completed(server, a.id, a.name, fromA, a.pieces, b.id, b.name, fromB, b.pieces));
        hub.ended(this);
    }

    /** Ends the trade without making it: each side's offer goes back to its player (a side on another server, by its server). */
    public void cancel(String reasonKey, String by) {
        if (finished()) {
            return;
        }
        // stopping while the other server is still putting its offer in escrow: nothing of this side has left, so give it back
        // now; the other server finds the attempt undecided, decides it aborted and takes its escrow back
        boolean stoppingBeforeHandOver = reasonKey.equals("stopping") && waitingEscrow && inFlight.isEmpty();
        if (state == State.SETTLING && !stoppingBeforeHandOver) {
            cancelAfter = reasonKey;
            return;
        }
        state = State.CANCELLED;
        quiet = true;
        List<ItemStack> backA = a.items(), backB = b.items();
        a.offer.clearContent();
        b.offer.clearContent();
        quiet = false;
        give(a, backA);
        give(b, backB);
        Component why = Component.translatable("message.bsp_core.trade.cancelled." + reasonKey, by);
        tell(a, why);
        tell(b, why);
        Side r = remoteSide();
        if (r != null) {
            CompoundTag m = TradeHub.msg("cancel");
            m.putString("trade", id);
            m.putString("key", reasonKey);
            m.putString("by", by);
            hub.send(r.server, m);
        }
        BSPCore.LOGGER.info("BSP-Core trade {} between {} and {} cancelled: {}{}", id, a.name, b.name, reasonKey, by.isEmpty() ? "" : " (" + by + ")");
        hub.ended(this);
    }

    // ------------------------------------------------------------------ inside

    private void addLine(Line line) {
        chat.add(line);
        if (chat.size() > CHAT_KEEP) {
            chat.remove(0);
        }
        dirty = true;
    }

    private void note(String key, Object... args) {
        Component c = Component.translatable(key, args);
        addLine(new Line("", c.getString()));
        tell(a, c);
        tell(b, c);
    }

    private static void tell(Side s, Component c) {
        if (s.player instanceof ServerPlayer sp && !sp.hasDisconnected()) {
            sp.displayClientMessage(c, false);
        }
    }

    /**
     * Items for a player here: into their inventory, the rest at their feet. A player who has left the server meanwhile
     * gets them in their market Collect box instead. A side on another server gets nothing from here.
     */
    private void give(Side s, List<ItemStack> items) {
        if (s.player == null || items.isEmpty()) {
            return;
        }
        Trades.give(server, s.player, s.id, items);
    }

    static boolean same(List<ItemStack> x, List<ItemStack> y) {
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            if (!ItemStack.matches(x.get(i), y.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** How many of {@code items} fit whole into the inventory's 36 main slots, stacking onto part stacks first, in order. */
    public static int fits(Inventory inv, List<ItemStack> items) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            slots.add(inv.items.get(i).copy());
        }
        int n = 0;
        for (ItemStack stack : items) {
            ItemStack rest = stack.copy();
            for (ItemStack slot : slots) {
                if (rest.isEmpty()) {
                    break;
                }
                if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, rest) && slot.getCount() < slot.getMaxStackSize()) {
                    int move = Math.min(rest.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(move);
                    rest.shrink(move);
                }
            }
            if (!rest.isEmpty()) {
                for (int i = 0; i < slots.size(); i++) {
                    if (slots.get(i).isEmpty()) {
                        slots.set(i, rest.copy());
                        rest = ItemStack.EMPTY;
                        break;
                    }
                }
            }
            if (rest.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
