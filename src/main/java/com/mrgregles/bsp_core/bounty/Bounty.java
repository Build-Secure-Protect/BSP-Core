package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One open bounty: a price a player (or the server, for a staff bounty) put on another player's head, paid to a hunter who
 * accepted it and then kills the target. The reward is in pieces, or a count of one Shatter Coin tier; the bonus is an extra
 * amount of the same kind, paid only if the kill comes before {@code bonusEndsMs}. Both are held from placing until the bounty
 * is claimed, cancelled, removed or runs out. {@code fee} is what the placer paid into the lottery pot, in pieces (for a coin
 * bounty, the coin fee's worth). Never changed in place: the {@code with...} methods make a changed copy.
 */
public final class Bounty {
    /** {@link #coinTier} of a bounty paid in pieces. */
    public static final int PIECES = -1;

    /** A player who accepted the bounty, and when. */
    public record Hunter(UUID id, String name, long acceptedMs) {}

    public final String id;
    public final UUID target, placer;
    public final String targetName, placerName;
    /** {@link #PIECES}, or the ordinal of the {@link CoinTier} the reward and bonus are counted in. */
    public final int coinTier;
    /** The reward and the bonus: pieces, or a number of coins of {@link #coinTier}. */
    public final long amount, bonus;
    /** Until when the bonus is paid on top (0 with no bonus). */
    public final long bonusEndsMs;
    /** Pieces the placer paid into the lottery pot. */
    public final long fee;
    public final long placedMs, endsMs;
    /** What the placer wrote for hunters: seen only by those who accepted, and by staff. */
    public final String notes;
    /** The server it was placed on (its {@code storage.serverId}, or "local"). */
    public final String server;
    private final List<Hunter> hunters;
    /** A coin bounty's coins as taken from the placer, with their team stamps (reward first, then bonus); empty for pieces or a staff bounty. */
    private final List<com.mrgregles.bsp_core.bank.BankStamps.Lot> lots;

    public Bounty(String id, UUID target, String targetName, UUID placer, String placerName, int coinTier, long amount, long bonus, long bonusEndsMs,
                  long fee, long placedMs, long endsMs, String notes, String server, List<Hunter> hunters) {
        this(id, target, targetName, placer, placerName, coinTier, amount, bonus, bonusEndsMs, fee, placedMs, endsMs, notes, server, hunters, List.of());
    }

    public Bounty(String id, UUID target, String targetName, UUID placer, String placerName, int coinTier, long amount, long bonus, long bonusEndsMs,
                  long fee, long placedMs, long endsMs, String notes, String server, List<Hunter> hunters, List<com.mrgregles.bsp_core.bank.BankStamps.Lot> lots) {
        this.id = id;
        this.target = target;
        this.targetName = targetName;
        this.placer = placer;
        this.placerName = placerName;
        this.coinTier = coinTier;
        this.amount = amount;
        this.bonus = bonus;
        this.bonusEndsMs = bonus > 0 ? bonusEndsMs : 0;
        this.fee = fee;
        this.placedMs = placedMs;
        this.endsMs = endsMs;
        this.notes = notes == null ? "" : notes;
        this.server = server;
        this.hunters = List.copyOf(hunters);
        this.lots = List.copyOf(lots);
    }

    public List<com.mrgregles.bsp_core.bank.BankStamps.Lot> lots() {
        return lots;
    }

    public Bounty withLots(List<com.mrgregles.bsp_core.bank.BankStamps.Lot> l) {
        return new Bounty(id, target, targetName, placer, placerName, coinTier, amount, bonus, bonusEndsMs, fee, placedMs, endsMs, notes, server, hunters, l);
    }

    /**
     * {@code n} of a coin bounty's coins, starting {@code skip} coins in (the reward is first, the bonus after it), as stacks with the
     * stamps they were taken with; coins beyond the recorded lots (a staff bounty's) are clean.
     */
    public List<ItemStack> coinPart(long skip, long n) {
        CoinTier t = tier();
        if (t == null || n <= 0) {
            return List.of();
        }
        List<com.mrgregles.bsp_core.bank.BankStamps.Lot> part = new ArrayList<>();
        long at = 0, left = n;
        for (com.mrgregles.bsp_core.bank.BankStamps.Lot l : lots) {
            long from = Math.max(at, skip), to = at + l.count();
            if (to > from && left > 0) {
                int take = (int) Math.min(left, to - from);
                part.add(new com.mrgregles.bsp_core.bank.BankStamps.Lot(l.stamp(), l.name(), take));
                left -= take;
            }
            at = to;
        }
        if (left > 0) {
            part.add(new com.mrgregles.bsp_core.bank.BankStamps.Lot(null, "", (int) left));
        }
        return com.mrgregles.bsp_core.bank.BankStamps.stacks(t, part);
    }

    public boolean coins() {
        return coinTier != PIECES;
    }

    /** The coin tier, or null for a bounty in pieces. */
    @Nullable
    public CoinTier tier() {
        return coins() ? CoinTier.byOrdinal(coinTier) : null;
    }

    public List<Hunter> hunters() {
        return hunters;
    }

    public boolean hasHunter(UUID player) {
        return hunters.stream().anyMatch(h -> h.id().equals(player));
    }

    public boolean endedBy(long now) {
        return now >= endsMs;
    }

    public boolean bonusLive(long now) {
        return bonus > 0 && now < bonusEndsMs;
    }

    /** What a kill at {@code killMs} pays: the reward, plus the bonus when it came before the bonus deadline. */
    public long payFor(long killMs) {
        return amount + (bonusLive(killMs) ? bonus : 0);
    }

    /** What the placer holds in this bounty: the reward and the bonus. */
    public long held() {
        return amount + bonus;
    }

    public Bounty withHunters(List<Hunter> list) {
        return new Bounty(id, target, targetName, placer, placerName, coinTier, amount, bonus, bonusEndsMs, fee, placedMs, endsMs, notes, server, list, lots);
    }

    public Bounty withHunter(Hunter h) {
        List<Hunter> list = new ArrayList<>(hunters);
        list.add(h);
        return withHunters(list);
    }

    public Bounty withoutHunter(UUID player) {
        List<Hunter> list = new ArrayList<>(hunters);
        list.removeIf(h -> h.id().equals(player));
        return withHunters(list);
    }

    public Bounty withEnds(long ends) {
        return new Bounty(id, target, targetName, placer, placerName, coinTier, amount, bonus, bonusEndsMs, fee, placedMs, ends, notes, server, hunters, lots);
    }

    public Bounty withNotes(String text) {
        return new Bounty(id, target, targetName, placer, placerName, coinTier, amount, bonus, bonusEndsMs, fee, placedMs, endsMs, text, server, hunters, lots);
    }

    /** "45,000 pieces" or "3 Diamond coins": {@code n} in this bounty's kind. */
    public String describe(long n) {
        if (!coins()) {
            return Bank.format(n) + " pieces";
        }
        CoinTier t = tier();
        return n + " " + (t == null ? "?" : Bank.tierName(t)) + (n == 1 ? " coin" : " coins");
    }

    /** {@code count} coins of {@code tier} as stacks no bigger than the coin's stack size. */
    public static List<ItemStack> coinStacks(CoinTier tier, long count) {
        List<ItemStack> out = new ArrayList<>();
        int max = new ItemStack(tier.coin()).getMaxStackSize();
        while (count > 0) {
            int n = (int) Math.min(max, count);
            out.add(new ItemStack(tier.coin(), n));
            count -= n;
        }
        return Collections.unmodifiableList(out);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putUUID("Target", target);
        tag.putString("TargetName", targetName);
        tag.putUUID("Placer", placer);
        tag.putString("PlacerName", placerName);
        tag.putInt("Tier", coinTier);
        tag.putLong("Amount", amount);
        tag.putLong("Bonus", bonus);
        tag.putLong("BonusEnds", bonusEndsMs);
        tag.putLong("Fee", fee);
        tag.putLong("Placed", placedMs);
        tag.putLong("Ends", endsMs);
        tag.putString("Notes", notes);
        tag.putString("Server", server);
        ListTag hs = new ListTag();
        for (Hunter h : hunters) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", h.id());
            t.putString("Name", h.name());
            t.putLong("At", h.acceptedMs());
            hs.add(t);
        }
        tag.put("Hunters", hs);
        if (!lots.isEmpty()) {
            ListTag ls = new ListTag();
            lots.forEach(l -> ls.add(l.save()));
            tag.put("Lots", ls);
        }
        return tag;
    }

    public static Bounty load(CompoundTag tag) {
        List<Hunter> hs = new ArrayList<>();
        for (Tag t : tag.getList("Hunters", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            hs.add(new Hunter(c.getUUID("Id"), c.getString("Name"), c.getLong("At")));
        }
        List<com.mrgregles.bsp_core.bank.BankStamps.Lot> ls = new ArrayList<>();
        for (Tag t : tag.getList("Lots", Tag.TAG_COMPOUND)) {
            ls.add(com.mrgregles.bsp_core.bank.BankStamps.Lot.load((CompoundTag) t));
        }
        return new Bounty(tag.getString("Id"), tag.getUUID("Target"), tag.getString("TargetName"), tag.getUUID("Placer"), tag.getString("PlacerName"),
                tag.getInt("Tier"), tag.getLong("Amount"), tag.getLong("Bonus"), tag.getLong("BonusEnds"), tag.getLong("Fee"), tag.getLong("Placed"),
                tag.getLong("Ends"), tag.getString("Notes"), tag.getString("Server"), hs, ls);
    }

    @Override
    public String toString() {
        return "Bounty[" + id + " on " + targetName + " by " + placerName + ", " + describe(amount) + (bonus > 0 ? " +" + bonus : "") + ", hunters " + hunters.size() + "]";
    }
}
