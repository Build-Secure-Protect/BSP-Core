package com.mrgregles.bsp_core.lottery;

import net.minecraft.nbt.CompoundTag;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What the lottery knows of a player: when they were last on the network ({@code seenMs}, at login and at logout), on which
 * server, and their LuckPerms ranks (lower-cased) and operator status as they were then. Ranks are kept here because
 * LuckPerms only knows the ranks of players who are online, and the draw must leave out the ranks the admin picked.
 */
public record LotteryPlayer(UUID id, String name, long seenMs, Set<String> ranks, boolean op, String server) {
    public LotteryPlayer {
        ranks = ranks == null ? Set.of() : Set.copyOf(ranks.stream().map(r -> r.toLowerCase(java.util.Locale.ROOT)).toList());
    }

    /** The ranks as one comma-separated string, sorted (the database's {@code ranks} column). */
    public String ranksText() {
        return String.join(",", new TreeSet<>(ranks));
    }

    /** Ranks from {@link #ranksText()}; blank gives none. */
    public static Set<String> ranksOf(String text) {
        return text == null || text.isBlank() ? Set.of() : Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    /** True when any of {@code ranks} (lower-cased) is one of this player's. */
    public boolean hasAny(Collection<String> ranks) {
        for (String r : ranks) {
            if (this.ranks.contains(r.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putLong("Seen", seenMs);
        tag.putString("Ranks", ranksText());
        tag.putBoolean("Op", op);
        tag.putString("Server", server);
        return tag;
    }

    public static LotteryPlayer load(CompoundTag tag) {
        return new LotteryPlayer(tag.getUUID("Id"), tag.getString("Name"), tag.getLong("Seen"), ranksOf(tag.getString("Ranks")), tag.getBoolean("Op"), tag.getString("Server"));
    }
}
