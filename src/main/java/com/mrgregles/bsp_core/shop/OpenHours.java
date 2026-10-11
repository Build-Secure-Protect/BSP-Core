package com.mrgregles.bsp_core.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * When a vendor or stall is open: always, or between two times of day on the server's real clock
 * or on the Minecraft day (0 to 24000 ticks, 6000 being noon). A window may wrap past midnight.
 *
 * @param openMin  minutes after midnight (real) or ticks (game) when it opens
 * @param closeMin minutes after midnight (real) or ticks (game) when it closes
 */
public record OpenHours(Mode mode, int openMin, int closeMin) {
    public enum Mode { ALWAYS, REAL, GAME }

    public static final OpenHours ALWAYS = new OpenHours(Mode.ALWAYS, 0, 0);

    /** Minutes into the real day on the server, or ticks into the Minecraft day. */
    public static int now(Mode mode, Level level) {
        if (mode == Mode.GAME) {
            return (int) (level.getDayTime() % 24000);
        }
        LocalTime t = LocalTime.now(ZoneId.systemDefault());
        return t.getHour() * 60 + t.getMinute();
    }

    public boolean isOpen(Level level) {
        return isOpenAt(now(mode, level));
    }

    /** Pure rule for tests: open when {@code at} lies in the window; a window that ends before it starts wraps past midnight. */
    public boolean isOpenAt(int at) {
        if (mode == Mode.ALWAYS || openMin == closeMin) {
            return true;
        }
        return openMin < closeMin ? at >= openMin && at < closeMin : at >= openMin || at < closeMin;
    }

    /** Minutes (real) or ticks (game) until it next opens; 0 when open. */
    public int untilOpen(int at) {
        if (isOpenAt(at)) {
            return 0;
        }
        int day = mode == Mode.GAME ? 24000 : 1440;
        return (openMin - at + day) % day;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Mode", mode.name());
        t.putInt("Open", openMin);
        t.putInt("Close", closeMin);
        return t;
    }

    public static OpenHours load(CompoundTag t) {
        if (!t.contains("Mode")) {
            return ALWAYS;
        }
        Mode m;
        try {
            m = Mode.valueOf(t.getString("Mode"));
        } catch (IllegalArgumentException e) {
            m = Mode.ALWAYS;
        }
        return new OpenHours(m, t.getInt("Open"), t.getInt("Close"));
    }
}
