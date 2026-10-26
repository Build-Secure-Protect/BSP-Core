package com.mrgregles.bsp_core.shop;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a limit's count fills up again: never, a countdown from the moment it runs out, every day
 * or every given weekday at a time on the server's clock, or at each new draw of a rotating shop.
 * A per-player rule may be "same", which follows the card's shop-wide rule. Written as short text,
 * the same in commands, the Workbench and the import file: never, 90m, 12h, 2d, day 00:00,
 * monday 06:30, rotation, same (a bare number is minutes).
 */
public record Refill(Kind kind, long minutes, @Nullable DayOfWeek day, int minuteOfDay) {
    public enum Kind { NEVER, AFTER, DAILY, WEEKLY, ROTATION, SAME }

    public static final Refill NEVER = new Refill(Kind.NEVER, 0, null, 0), ROTATION = new Refill(Kind.ROTATION, 0, null, 0), SAME = new Refill(Kind.SAME, 0, null, 0);
    private static final Pattern SPAN = Pattern.compile("(\\d{1,7})\\s*(m|min|mins|minutes?|h|hours?|d|days?)?");
    private static final Pattern AT = Pattern.compile("(?:every\\s+)?([a-z]+)\\s+(?:at\\s+)?(\\d{1,2}):(\\d{2})");

    public static Refill after(long minutes) {
        return minutes <= 0 ? NEVER : new Refill(Kind.AFTER, minutes, null, 0);
    }

    /** The rule written as text, or null when it is not one. */
    @Nullable
    public static Refill parse(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "never", "off", "0", "" -> {
                return NEVER;
            }
            case "rotation", "each rotation" -> {
                return ROTATION;
            }
            case "same" -> {
                return SAME;
            }
            default -> { }
        }
        Matcher m = SPAN.matcher(s);
        if (m.matches()) {
            long n = Long.parseLong(m.group(1));
            String unit = m.group(2) == null ? "m" : m.group(2);
            return after(unit.startsWith("h") ? n * 60 : unit.startsWith("d") ? n * 1440 : n);
        }
        m = AT.matcher(s);
        if (m.matches()) {
            int h = Integer.parseInt(m.group(2)), min = Integer.parseInt(m.group(3));
            if (h > 23 || min > 59) {
                return null;
            }
            if (m.group(1).equals("day") || m.group(1).equals("daily")) {
                return new Refill(Kind.DAILY, 0, null, h * 60 + min);
            }
            DayOfWeek day = dayOf(m.group(1));
            return day == null ? null : new Refill(Kind.WEEKLY, 0, day, h * 60 + min);
        }
        return null;
    }

    @Nullable
    private static DayOfWeek dayOf(String word) {
        for (DayOfWeek d : DayOfWeek.values()) {
            String name = d.name().toLowerCase(Locale.ROOT);
            if (word.equals(name) || word.length() >= 3 && name.startsWith(word)) {
                return d;
            }
        }
        return null;
    }

    /** The rule as text, as {@link #parse} reads it back. */
    public String text() {
        return switch (kind) {
            case NEVER -> "never";
            case ROTATION -> "rotation";
            case SAME -> "same";
            case AFTER -> minutes % 1440 == 0 ? minutes / 1440 + "d" : minutes % 60 == 0 ? minutes / 60 + "h" : minutes + "m";
            case DAILY -> "day " + clock();
            case WEEKLY -> day.name().toLowerCase(Locale.ROOT) + " " + clock();
        };
    }

    public String clock() {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    public boolean fixedTime() {
        return kind == Kind.DAILY || kind == Kind.WEEKLY;
    }

    /** For a fixed-time rule, the last refill time at or before {@code now}; 0 for the other rules. */
    public long periodStart(long now, ZoneId zone) {
        ZonedDateTime at = boundary(now, zone);
        return at == null ? 0 : at.toInstant().toEpochMilli();
    }

    /** For a fixed-time rule, the next refill time after {@code now}; 0 for the other rules. */
    public long nextAt(long now, ZoneId zone) {
        ZonedDateTime at = boundary(now, zone);
        return at == null ? 0 : (kind == Kind.DAILY ? at.plusDays(1) : at.plusWeeks(1)).toInstant().toEpochMilli();
    }

    @Nullable
    private ZonedDateTime boundary(long now, ZoneId zone) {
        if (!fixedTime()) {
            return null;
        }
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(zone);
        if (kind == Kind.DAILY) {
            ZonedDateTime at = t.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone);
            return at.isAfter(t) ? at.minusDays(1) : at;
        }
        ZonedDateTime at = t.toLocalDate().with(TemporalAdjusters.previousOrSame(day)).atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone);
        return at.isAfter(t) ? at.minusWeeks(1) : at;
    }
}
