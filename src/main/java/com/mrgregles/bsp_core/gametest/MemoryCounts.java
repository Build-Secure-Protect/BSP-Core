package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.shop.CountStore;
import com.mrgregles.bsp_core.shop.ShopLedger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

/** A stand-in for the network's shop counts: one map, shared the way every server shares the database. */
final class MemoryCounts implements CountStore {
    final Map<String, Map<String, ShopLedger.Count>> rows = new HashMap<>();
    final Map<String, ShopLedger.Draw> draws = new HashMap<>();
    private boolean moved;
    /** Runs inside each locked call, as if it happened while the database worked (a player leaving, say). */
    Runnable duringLock = () -> {};
    /** Runs inside each draw call before the change, as {@link #duringLock}; and how many draw calls were made. */
    Runnable duringDraw = () -> {};
    int drawCalls;
    /** How many times a whole shop's counts were read. */
    int fullReads;

    @Override
    public synchronized <T> T locked(String shop, int card, boolean selling, UUID player, BiFunction<ShopLedger.Count, ShopLedger.Count, T> change) {
        duringLock.run();
        Map<String, ShopLedger.Count> m = rows.computeIfAbsent(shop, k -> new HashMap<>());
        String allKey = CountStore.key(card, selling, null), mineKey = CountStore.key(card, selling, player);
        ShopLedger.Count all = m.getOrDefault(allKey, new ShopLedger.Count()).copy(), mine = m.getOrDefault(mineKey, new ShopLedger.Count()).copy();
        T out = change.apply(all, mine);
        m.put(allKey, all.copy());
        if (mine.left < 0) {
            m.remove(mineKey);
        } else {
            m.put(mineKey, mine.copy());
        }
        return out;
    }

    @Override
    public synchronized Map<String, ShopLedger.Count> read(String shop) {
        fullReads++;
        Map<String, ShopLedger.Count> out = new HashMap<>();
        rows.getOrDefault(shop, Map.of()).forEach((k, v) -> out.put(k, v.copy()));
        return out;
    }

    @Override
    public synchronized void reset(String shop, int from, int to) {
        rows.getOrDefault(shop, new HashMap<>()).keySet().removeIf(k -> {
            int card = Integer.parseInt(k.substring(0, k.indexOf(':')));
            return card >= from && card <= to;
        });
    }

    @Override
    public synchronized void dropIf(String shop, int card, boolean selling, UUID player, int left, long at) {
        Map<String, ShopLedger.Count> m = rows.getOrDefault(shop, new HashMap<>());
        ShopLedger.Count c = m.get(CountStore.key(card, selling, player));
        if (c != null && c.left == left && c.at == at) {
            m.remove(CountStore.key(card, selling, player));
        }
    }

    @Override
    public synchronized ShopLedger.Draw drawLocked(String shop, java.util.function.UnaryOperator<ShopLedger.Draw> change) {
        drawCalls++;
        duringDraw.run();
        ShopLedger.Draw d = draws.getOrDefault(shop, new ShopLedger.Draw()).copy();
        ShopLedger.Draw out = change.apply(d);
        draws.put(shop, out.copy());
        return out.copy();
    }

    @Override
    public synchronized Map<String, ShopLedger.Count> readCard(String shop, int card, boolean selling, UUID player) {
        Map<String, ShopLedger.Count> m = rows.getOrDefault(shop, Map.of()), out = new HashMap<>();
        for (String k : java.util.List.of(CountStore.key(card, selling, null), CountStore.key(card, selling, player))) {
            if (m.containsKey(k)) {
                out.put(k, m.get(k).copy());
            }
        }
        return out;
    }

    @Override
    public synchronized ShopLedger.Draw readDraw(String shop) {
        return draws.getOrDefault(shop, new ShopLedger.Draw()).copy();
    }

    @Override
    public synchronized void dropDraw(String shop) {
        draws.remove(shop);
    }

    @Override
    public synchronized boolean moveOnce(String serverId, Map<String, Map<String, ShopLedger.Count>> counts) {
        if (moved) {
            return false;
        }
        moved = true;
        counts.forEach((shop, m) -> m.forEach((k, v) -> rows.computeIfAbsent(shop, s -> new HashMap<>()).putIfAbsent(k, v.copy())));
        return true;
    }
}
