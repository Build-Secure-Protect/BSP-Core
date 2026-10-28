package com.mrgregles.bsp_core.lottery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * What the admin sets on the lottery's admin page: how many days back a player must have been seen to be in a draw, and the
 * LuckPerms ranks left out (lower-cased, sorted). Operators are always left out. One for the whole network (the shared state
 * key {@link #STATE_KEY}), or the world's own on a single server.
 */
public record LotterySettings(int days, List<String> leftOut) {
    public static final String STATE_KEY = "lottery_settings";
    public static final int MIN_DAYS = 1, MAX_DAYS = 90;
    /** Seven days, and the usual names of staff ranks left out; a name LuckPerms does not have changes nothing. */
    public static final LotterySettings DEFAULT = new LotterySettings(7, List.of("admin", "mod", "moderator", "owner", "staff"));

    public LotterySettings {
        days = Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
        leftOut = leftOut == null ? List.of() : List.copyOf(new TreeSet<>(leftOut.stream().map(r -> r.replace(",", "").trim().toLowerCase(Locale.ROOT)).filter(r -> !r.isEmpty()).toList()));
    }

    public long windowMs() {
        return days * 86_400_000L;
    }

    public LotterySettings withDays(int d) {
        return new LotterySettings(d, leftOut);
    }

    /** These settings with {@code rank} left out or, with {@code out} false, let back in (a comma, which separates the stored ranks, is dropped). */
    public LotterySettings withRank(String rank, boolean out) {
        String r = rank.replace(",", "").trim().toLowerCase(Locale.ROOT);
        List<String> next = new java.util.ArrayList<>(leftOut);
        next.remove(r);
        if (out && !r.isEmpty()) {
            next.add(r);
        }
        return new LotterySettings(days, next);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Days", days);
        tag.putString("LeftOut", String.join(",", leftOut));
        return tag;
    }

    public static LotterySettings load(CompoundTag tag) {
        return new LotterySettings(tag.contains("Days") ? tag.getInt("Days") : DEFAULT.days(), tag.contains("LeftOut") ? List.copyOf(LotteryPlayer.ranksOf(tag.getString("LeftOut"))) : DEFAULT.leftOut());
    }

    /** The shared state's text for these settings. */
    public String text() {
        return save().toString();
    }

    /** Settings from {@link #text()}; missing or unreadable text gives the defaults. */
    public static LotterySettings fromText(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT;
        }
        try {
            return load(TagParser.parseTag(text));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return DEFAULT;
        }
    }
}
