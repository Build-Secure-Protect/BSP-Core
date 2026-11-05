package com.mrgregles.bsp_core.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The upgrade levels of the totems placed on the network's other servers, as last read from the shared {@code totems} table
 * (BSP-Teleport request R11). Per owner, the highest level of each upgrade across all their totems elsewhere. Read every
 * {@code storage.noticeSeconds} by {@link NetworkStorage}; empty without network storage. A totem removed or stolen elsewhere
 * leaves its old owner's levels at the next read: its row goes, or is rewritten under the new owner.
 */
public final class RemoteTotemLevels {
    private RemoteTotemLevels() {}

    /** One placed totem on another server: its owner and its levels in the column's text form ({@link #encode}). */
    public record Row(UUID owner, String levels) {}

    private static volatile Map<UUID, Map<String, Integer>> byOwner = Map.of();
    /** How many totems each owner has placed on the other servers that were heard from lately (kit conditions). */
    private static volatile Map<UUID, Integer> counts = Map.of();

    /** Replaces what is known with a fresh read of the other servers' totems. Any thread. */
    public static void apply(List<Row> rows) {
        Map<UUID, Map<String, Integer>> out = new HashMap<>();
        for (Row r : rows) {
            Map<String, Integer> best = out.computeIfAbsent(r.owner(), k -> new HashMap<>());
            decode(r.levels()).forEach((key, level) -> best.merge(key, level, Math::max));
        }
        out.replaceAll((k, v) -> Map.copyOf(v));
        byOwner = Map.copyOf(out);
    }

    /** Replaces the counts of totems placed elsewhere with a fresh read ({@code MySqlStore.placedElsewhereCounts}). Any thread. */
    public static void applyCounts(Map<UUID, Integer> read) {
        counts = Map.copyOf(read);
    }

    /** Forgets everything (the storage stopped). */
    public static void clear() {
        byOwner = Map.of();
        counts = Map.of();
    }

    /** How many totems {@code owner} has placed on the network's other servers, as last read; 0 without network storage. */
    public static int placedElsewhere(UUID owner) {
        return counts.getOrDefault(owner, 0);
    }

    /** The highest level of upgrade {@code key} on {@code owner}'s totems placed on the other servers; 0 when none has it. */
    public static int highest(UUID owner, String key) {
        Map<String, Integer> l = byOwner.get(owner);
        return l == null ? 0 : l.getOrDefault(key, 0);
    }

    /** The column's text form: {@code key=level} pairs joined by commas, sorted by key, only levels above 0 ("" when none). */
    public static String encode(Map<String, Integer> levels) {
        StringBuilder sb = new StringBuilder();
        levels.entrySet().stream().filter(e -> e.getValue() != null && e.getValue() > 0).sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('=').append(e.getValue());
        });
        return sb.toString();
    }

    /** Reads {@link #encode}'s form. Pairs that cannot be read, and levels of 0 or less, are skipped. */
    public static Map<String, Integer> decode(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, Integer> out = new HashMap<>();
        for (String pair : text.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int level = Integer.parseInt(pair.substring(eq + 1).trim());
                if (level > 0) {
                    out.merge(pair.substring(0, eq).trim(), level, Math::max);
                }
            } catch (NumberFormatException ignored) {
                // not a level
            }
        }
        return out;
    }
}
