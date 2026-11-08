package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.coin.CoinStamp;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The team stamps of the coins kept in a bank account. The account's coin counts per tier stay the record of how many; this
 * breakdown says how many of them carry each stamp, and the rest are clean. Coins put in keep their stamps, and coins taken out
 * get them back: the taker's current team first, then clean coins, then the rest.
 */
public final class BankStamps {
    private BankStamps() {}

    /** Some coins of one tier with one stamp ({@code stamp} null: clean). */
    public record Lot(@Nullable String stamp, String name, int count) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            if (stamp != null) {
                t.putString("Stamp", stamp);
            }
            t.putString("Name", name);
            t.putInt("Count", count);
            return t;
        }

        public static Lot load(CompoundTag t) {
            return new Lot(t.contains("Stamp") ? t.getString("Stamp") : null, t.getString("Name"), t.getInt("Count"));
        }
    }

    /** One stamp's coins in an account: its team name and a count per tier. */
    public static final class Held {
        public String name;
        public final int[] counts = new int[CoinTier.values().length];

        public Held(String name) {
            this.name = name;
        }
    }

    /** The coins of {@code tier} in the player's inventory, inventory, armour and offhand: how many of them in all. */
    public static int count(Player player, CoinTier tier) {
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(tier.coin())) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Takes {@code count} coins of {@code tier} from the inventory, in inventory order, and says which stamps they had. */
    public static List<Lot> takeFromInventory(Player player, CoinTier tier, int count) {
        Map<String, Lot> out = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.is(tier.coin())) {
                continue;
            }
            int n = Math.min(left, st.getCount());
            String stamp = CoinStamp.of(st);
            String key = stamp == null ? "" : stamp;
            Lot had = out.get(key);
            out.put(key, new Lot(stamp, CoinStamp.teamName(st), (had == null ? 0 : had.count()) + n));
            st.shrink(n);
            if (st.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
            left -= n;
        }
        inv.setChanged();
        return new ArrayList<>(out.values());
    }

    /** Adds the stamped lots to the account's breakdown (clean lots need nothing: clean is what is not stamped). */
    public static void add(BankAccount a, CoinTier tier, List<Lot> lots) {
        for (Lot l : lots) {
            if (l.stamp() != null && l.count() > 0) {
                Held h = a.stamps.computeIfAbsent(l.stamp(), k -> new Held(l.name()));
                if (!l.name().isEmpty()) {
                    h.name = l.name();
                }
                h.counts[tier.ordinal()] += l.count();
            }
        }
    }

    /**
     * Which coins a withdrawal of {@code count} of {@code tier} hands out, read from the account before the coins are taken off:
     * those pressed for {@code team} first, then clean ones, then the rest (outside a team, other teams).
     */
    public static List<Lot> pick(BankAccount a, CoinTier tier, int count, @Nullable String team) {
        return pick(a.coins(tier), a.stamps, tier, count, team);
    }

    /** {@link #pick(BankAccount, CoinTier, int, String)} on a total and a breakdown. */
    public static List<Lot> pick(int total, Map<String, Held> stamps, CoinTier tier, int count, @Nullable String team) {
        List<Lot> out = new ArrayList<>();
        int left = count, stamped = 0;
        for (Held h : stamps.values()) {
            stamped += Math.max(0, h.counts[tier.ordinal()]);
        }
        Held mine = team == null ? null : stamps.get(team);
        if (mine != null && mine.counts[tier.ordinal()] > 0 && left > 0) {
            int n = Math.min(left, mine.counts[tier.ordinal()]);
            out.add(new Lot(team, mine.name, n));
            left -= n;
        }
        int clean = Math.max(0, total - stamped);
        if (clean > 0 && left > 0) {
            int n = Math.min(left, clean);
            out.add(new Lot(null, "", n));
            left -= n;
        }
        for (Map.Entry<String, Held> e : stamps.entrySet()) {
            if (left <= 0) {
                break;
            }
            if (e.getKey().equals(team)) {
                continue;
            }
            int have = e.getValue().counts[tier.ordinal()];
            if (have > 0) {
                int n = Math.min(left, have);
                out.add(new Lot(e.getKey(), e.getValue().name, n));
                left -= n;
            }
        }
        if (left > 0) {
            out.add(new Lot(null, "", left)); // the breakdown said fewer than the total: the rest are clean
        }
        return out;
    }

    /** Takes the picked lots off the breakdown. */
    public static void remove(BankAccount a, CoinTier tier, List<Lot> lots) {
        for (Lot l : lots) {
            Held h = l.stamp() == null ? null : a.stamps.get(l.stamp());
            if (h != null) {
                h.counts[tier.ordinal()] = Math.max(0, h.counts[tier.ordinal()] - l.count());
            }
        }
        a.stamps.values().removeIf(h -> {
            for (int c : h.counts) {
                if (c > 0) {
                    return false;
                }
            }
            return true;
        });
    }

    /** The lots as coin stacks with their stamps, a stack at most {@link ShatterCoinItem#STACK}. */
    public static List<ItemStack> stacks(CoinTier tier, List<Lot> lots) {
        List<ItemStack> out = new ArrayList<>();
        for (Lot l : lots) {
            int n = l.count();
            while (n > 0) {
                ItemStack st = new ItemStack(tier.coin(), Math.min(n, ShatterCoinItem.STACK));
                if (l.stamp() != null) {
                    CompoundTag tag = st.getOrCreateTag();
                    tag.putString(CoinStamp.TAG, l.stamp());
                    if (!l.stamp().equals(CoinStamp.NONE) && !l.name().isEmpty()) {
                        tag.putString(CoinStamp.NAME_TAG, l.name());
                    }
                }
                n -= st.getCount();
                out.add(st);
            }
        }
        return out;
    }

    public static ListTag save(Map<String, Held> stamps) {
        ListTag list = new ListTag();
        stamps.forEach((stamp, h) -> {
            CompoundTag t = new CompoundTag();
            t.putString("Stamp", stamp);
            t.putString("Name", h.name);
            t.putIntArray("Counts", h.counts.clone());
            list.add(t);
        });
        return list;
    }

    public static void load(ListTag list, Map<String, Held> into) {
        into.clear();
        for (Tag raw : list) {
            CompoundTag t = (CompoundTag) raw;
            Held h = new Held(t.getString("Name"));
            int[] c = t.getIntArray("Counts");
            System.arraycopy(c, 0, h.counts, 0, Math.min(c.length, h.counts.length));
            into.put(t.getString("Stamp"), h);
        }
    }

    /** A deep copy of a breakdown. */
    public static void copy(Map<String, Held> from, Map<String, Held> into) {
        into.clear();
        from.forEach((k, v) -> {
            Held h = new Held(v.name);
            System.arraycopy(v.counts, 0, h.counts, 0, h.counts.length);
            into.put(k, h);
        });
    }
}
