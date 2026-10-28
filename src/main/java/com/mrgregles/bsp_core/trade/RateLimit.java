package com.mrgregles.bsp_core.trade;

/** A screen's allowance of actions: {@code burst} at once, then {@code perSecond}; anything past it is dropped. Server thread. */
public final class RateLimit {
    private final double burst, perSecond;
    private double tokens;
    private long at;

    public RateLimit(double burst, double perSecond) {
        this.burst = burst;
        this.perSecond = perSecond;
        this.tokens = burst;
    }

    /** Whether one more action is allowed at {@code nowMs}; spends it if so. */
    public boolean allow(long nowMs) {
        tokens = Math.min(burst, tokens + Math.max(0, nowMs - at) * perSecond / 1000.0);
        at = nowMs;
        if (tokens < 1) {
            return false;
        }
        tokens--;
        return true;
    }
}
