package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.Market;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A live trade run by another server of the network, as this server's player takes part in it. Their offer slots are real
 * and stay here; every change, READY, un-ready, pieces and chat line goes to the running server, whose state comes back
 * after each change and every 2 seconds and is what the screen shows. For the hand-over this server checks its player's
 * room and puts their offer in escrow in the database; the running server's outcome then delivers what they got, or the
 * escrow comes back. If the running server goes quiet for 10 seconds the trade ends here and the items come back; if it goes
 * quiet during the hand-over this server decides the attempt in the database itself, so nothing is lost or doubled.
 */
public final class RemoteTrade implements Trade {
    private final TradeHub hub;
    private final String id, host;
    private final Player me;
    private final UUID meId, theirId;
    private final String meName, theirName;
    private final SimpleContainer mine = new SimpleContainer(TradeSession.SLOTS), theirs = new SimpleContainer(TradeSession.SLOTS);
    private boolean quiet, finished;
    // the running server's view, as last heard
    private String hostState = "OPEN";
    private long myPieces, theirPieces, left;
    private boolean myReady, theirReady;
    private List<TradeSession.Line> chat = new ArrayList<>();
    private long lastHeard, nextBeat;
    // the hand-over
    private String attempt = "";
    private boolean waitingOutcome;
    private long outcomeBy;
    /** An offer whose escrow's answer was lost and the database could not yet say what became of it; asked again every 5 s. */
    @javax.annotation.Nullable
    private List<ItemStack> parked;
    private String parkedAt = "";
    private long parkedRetry;

    RemoteTrade(TradeHub hub, String id, String host, Player me, UUID theirId, String theirName) {
        this.hub = hub;
        this.id = id;
        this.host = host;
        this.me = me;
        this.meId = me.getUUID();
        this.meName = me.getGameProfile().getName();
        this.theirId = theirId;
        this.theirName = theirName;
        this.lastHeard = hub.now();
        mine.addListener(c -> {
            if (!quiet && !finished) {
                CompoundTag m = act("offer");
                m.put("items", TradeHub.save(items(mine)));
                send(m);
            }
        });
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public boolean has(UUID player) {
        return meId.equals(player) || theirId.equals(player);
    }

    @Override
    public boolean finished() {
        return finished;
    }

    @Override
    public boolean locked() {
        return finished || waitingOutcome || hostState.equals("COUNTDOWN") || hostState.equals("SETTLING");
    }

    @Override
    public Container mine(UUID player) {
        return mine;
    }

    @Override
    public Container theirs(UUID player) {
        return theirs;
    }

    public boolean myReady() {
        return myReady;
    }

    public boolean theirReady() {
        return theirReady;
    }

    public long myPieces() {
        return myPieces;
    }

    public long theirPieces() {
        return theirPieces;
    }

    public String hostState() {
        return hostState;
    }

    public long countdownLeft() {
        return left;
    }

    @Override
    public List<TradeSession.Line> chat() {
        return List.copyOf(chat);
    }

    @Override
    public List<String> names() {
        return List.of(theirName, meName);
    }

    @Override
    public TradeMenu.State state(UUID player) {
        return new TradeMenu.State(theirName, host, myPieces, theirPieces, myReady, theirReady, waitingOutcome ? "SETTLING" : hostState, left, locked(),
                Bank.pieces(me.getServer(), meId), chat());
    }

    // ------------------------------------------------------------------ this player's actions, sent to the running server

    private CompoundTag act(String op) {
        CompoundTag m = TradeHub.msg("act");
        m.putString("trade", id);
        m.putString("op", op);
        return m;
    }

    private void send(CompoundTag m) {
        hub.send(host, m);
    }

    @Override
    public String setPieces(UUID player, long pieces) {
        if (locked()) {
            return "locked";
        }
        long p = Math.max(0, Math.min(pieces, Market.MAX_PRICE));
        if (p == myPieces) {
            return "ok"; // the same again: nothing to tell (it would turn both READY lights off for nothing)
        }
        myPieces = p;
        CompoundTag m = act("pieces");
        m.putLong("pieces", myPieces);
        send(m);
        return "ok";
    }

    @Override
    public String ready(UUID player) {
        if (locked()) {
            return "locked";
        }
        if (items(mine).stream().anyMatch(Market::refused)) {
            return "refused";
        }
        if (myPieces > Bank.pieces(me.getServer(), meId)) {
            return "short";
        }
        send(act("ready"));
        return "ok";
    }

    @Override
    public String unready(UUID player) {
        if (finished || waitingOutcome || hostState.equals("SETTLING")) {
            return "locked";
        }
        send(act("unready"));
        return "ok";
    }

    @Override
    public void say(UUID player, String text) {
        String t = TradeSession.clean(text);
        if (t.isEmpty() || finished) {
            return;
        }
        BSPCore.LOGGER.info("BSP-Core trade {}: {}: {}", id, meName, t);
        CompoundTag m = act("say");
        m.putString("text", t);
        send(m);
    }

    @Override
    public void leave(UUID player, String reasonKey, String by) {
        if (finished) {
            return;
        }
        CompoundTag m = act("cancel");
        m.putString("key", reasonKey);
        m.putString("by", by);
        send(m);
        // during the hand-over the offer is in escrow: the outcome (or the sweep) gets it to its owner
        end(!waitingOutcome, Component.translatable("message.bsp_core.trade.cancelled." + reasonKey, by));
    }

    // ------------------------------------------------------------------ the running server's messages

    void onHost(CompoundTag m) {
        lastHeard = hub.now();
        switch (m.getString("t")) {
            case "state" -> {
                hostState = m.getString("state");
                left = m.getLong("left");
                theirPieces = m.getLong("theirPieces");
                theirReady = m.getBoolean("theirReady");
                myPieces = m.getLong("myPieces");
                myReady = m.getBoolean("myReady");
                List<ItemStack> items = TradeHub.load(m.getList("theirs", Tag.TAG_COMPOUND));
                theirs.clearContent();
                for (int i = 0; i < items.size() && i < TradeSession.SLOTS; i++) {
                    theirs.setItem(i, items.get(i));
                }
                List<TradeSession.Line> lines = new ArrayList<>();
                ListTag l = m.getList("chat", Tag.TAG_COMPOUND);
                for (int i = 0; i < l.size(); i++) {
                    lines.add(new TradeSession.Line(l.getCompound(i).getString("name"), l.getCompound(i).getString("text")));
                }
                chat = lines;
            }
            case "prepare" -> prepare(m.getString("attempt"), TradeHub.load(m.getList("items", Tag.TAG_COMPOUND)));
            case "outcome" -> {
                if (waitingOutcome && m.getString("attempt").equals(attempt)) {
                    resolve(m.getString("outcome"), false);
                }
            }
            case "cancel" -> {
                if (!waitingOutcome) {
                    end(true, Component.translatable("message.bsp_core.trade.cancelled." + m.getString("key"), m.getString("by")));
                }
            }
            default -> { }
        }
    }

    /** The running server asks for the hand-over: room for what is coming, then this player's offer into escrow. */
    private void prepare(String at, List<ItemStack> coming) {
        if (finished) {
            return;
        }
        attempt = at;
        int room = TradeSession.fits(me.getInventory(), coming);
        if (room < coming.size()) {
            CompoundTag s = TradeHub.msg("short");
            s.putString("trade", id);
            s.putString("attempt", at);
            s.putInt("room", room);
            s.putInt("need", coming.size());
            send(s);
            return;
        }
        List<ItemStack> offered = items(mine);
        quiet = true;
        mine.clearContent();
        quiet = false;
        waitingOutcome = true;
        outcomeBy = hub.now() + TradeHub.OUTCOME_MS;
        hub.dbOr("put a trade offer in escrow", d -> {
            d.escrow(at, meId, offered);
            return true;
        }, ok -> {
            CompoundTag e = TradeHub.msg("escrowed");
            e.putString("trade", id);
            e.putString("attempt", at);
            e.putBoolean("ok", true);
            e.put("items", TradeHub.save(offered));
            send(e);
        }, untouched -> {
            waitingOutcome = false;
            if (untouched) {
                backIntoOffer(offered);
                escrowRefused(at);
                resyncOffer();
                return;
            }
            // the escrow may have gone in with only its answer lost: ask before giving anything back
            parked = offered;
            parkedAt = at;
            recoverParked();
        });
    }

    private void escrowRefused(String at) {
        CompoundTag e = TradeHub.msg("escrowed");
        e.putString("trade", id);
        e.putString("attempt", at);
        e.putBoolean("ok", false);
        send(e);
    }

    private void recoverParked() {
        String at = parkedAt;
        hub.db("find out what became of a trade escrow", d -> d.recoverEscrow(at, meId), this::settleParked, () -> parkedRetry = hub.now() + 5000);
    }

    /**
     * The database said what became of the parked offer. Not escrowed: the memory copy comes back. Escrowed: what is still in
     * escrow comes back (the attempt is now aborted), or, if the sweep already sent it as pending items, those are delivered.
     * The attempt cannot be done (this side never told the running server its escrow was in), but if it is, it is delivered.
     */
    private void settleParked(TradeDb.Recovered rec) {
        List<ItemStack> memory = parked;
        String at = parkedAt;
        parked = null;
        parkedRetry = 0;
        if (memory == null) {
            return;
        }
        if (rec.outcome().equals("done") || (rec.escrowed() && rec.items().isEmpty())) {
            hub.db("deliver a trade", d -> d.takePending(meId), this::give, () -> {});
        }
        if (rec.outcome().equals("done")) {
            BSPCore.LOGGER.warn("BSP-Core trade {}: attempt {} was found done after its escrow's answer was lost", id, at);
            return;
        }
        List<ItemStack> back = rec.escrowed() ? rec.items() : memory;
        if (finished) {
            give(back);
        } else {
            backIntoOffer(back);
            escrowRefused(at);
            resyncOffer();
        }
    }

    /** After the trade ended here, a parked offer is still settled (from {@link TradeHub#tick}); true once it is. */
    boolean tickParked(long now) {
        if (parked == null) {
            return true;
        }
        if (parkedRetry > 0 && now >= parkedRetry) {
            parkedRetry = 0;
            recoverParked();
        }
        return false;
    }

    /** The attempt's outcome: "done" delivers what this player got; "aborted" brings their escrow back. {@code end}: the trade is over either way. */
    private void resolve(String outcome, boolean end) {
        waitingOutcome = false;
        String at = attempt;
        if (outcome.equals("done")) {
            hub.db("deliver a trade", d -> d.takePending(meId), this::give, () -> {});
            finished = true;
            tell(Component.translatable("message.bsp_core.trade.done", theirName));
            hub.ended(this);
            return;
        }
        hub.db("take a trade offer back from escrow", d -> d.takeEscrow(at, meId), back -> {
            if (finished || end) {
                give(back);
            } else {
                backIntoOffer(back);
                resyncOffer();
            }
        }, () -> {});
        if (end) {
            end(false, Component.translatable("message.bsp_core.trade.cancelled.lost", theirName));
        }
    }

    // ------------------------------------------------------------------ the clock

    @Override
    public void tick(long now) {
        if (finished) {
            return;
        }
        tickParked(now);
        if (waitingOutcome) {
            if (now >= outcomeBy) {
                // the running server has gone quiet mid hand-over: the database decides
                outcomeBy = now + TradeHub.REPLY_MS;
                String at = attempt;
                hub.db("decide a trade's hand-over", d -> d.decideAbort(at), outcome -> {
                    if (waitingOutcome && at.equals(attempt)) {
                        resolve(outcome, true);
                    }
                }, () -> {});
            }
        } else if (now - lastHeard > TradeHub.SILENCE_MS) {
            CompoundTag m = act("cancel");
            m.putString("key", "lost");
            m.putString("by", meName);
            send(m);
            end(true, Component.translatable("message.bsp_core.trade.cancelled.lost", theirName));
            return;
        }
        if (now >= nextBeat) {
            nextBeat = now + TradeHub.HEARTBEAT_MS;
            send(act("ping"));
        }
    }

    // ------------------------------------------------------------------ inside

    /** Ends the trade here; {@code giveBack}: this player's offer goes back to them. */
    private void end(boolean giveBack, Component why) {
        if (finished) {
            return;
        }
        finished = true;
        if (giveBack) {
            List<ItemStack> back = items(mine);
            quiet = true;
            mine.clearContent();
            quiet = false;
            give(back);
        }
        tell(why);
        BSPCore.LOGGER.info("BSP-Core trade {} ended on this server for {}", id, meName);
        if (parked != null) {
            hub.keepSettling(this);
        }
        hub.ended(this);
    }

    /** Items back into the offer (stacks may merge, 32 and 32 into 64); what does not fit goes to the player. Follow with {@link #resyncOffer} once the running server knows the attempt is over. */
    private void backIntoOffer(List<ItemStack> items) {
        quiet = true;
        List<ItemStack> spill = new ArrayList<>();
        for (ItemStack s : items) {
            ItemStack left = mine.addItem(s);
            if (!left.isEmpty()) {
                spill.add(left);
            }
        }
        quiet = false;
        give(spill);
    }

    /** Tells the running server what the offer holds now, so its copy matches the next hand-over's escrow (a merged stack would not). */
    private void resyncOffer() {
        if (!finished) {
            CompoundTag m = act("offer");
            m.put("items", TradeHub.save(items(mine)));
            send(m);
        }
    }

    private void give(List<ItemStack> items) {
        if (items.isEmpty() || me.getServer() == null) {
            return;
        }
        Trades.give(me.getServer(), me, meId, items);
    }

    private void tell(Component c) {
        if (me instanceof ServerPlayer sp && !sp.hasDisconnected()) {
            sp.displayClientMessage(c, false);
        }
    }

    private static List<ItemStack> items(Container c) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            if (!c.getItem(i).isEmpty()) {
                out.add(c.getItem(i).copy());
            }
        }
        return out;
    }
}
