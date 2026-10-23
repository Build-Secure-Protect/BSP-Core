package com.mrgregles.bsp_core.client;

/**
 * When the X-ray hint shows, and which one: every time something that carries X-ray comes into the offhand (a totem or a running
 * Wave Emitter equipped or swapped in, or an emitter switched on there), the action bar says what the power can do right then.
 * Plain arithmetic, so it can be checked without a client.
 */
public final class XrayHint {
    private XrayHint() {}

    /** What the X-ray can do right now, as the hint says it. */
    public enum State { READY, RECHARGING, ON, UNBOUND }

    /** What the offhand holds this tick: a name for the thing (item id, plus the totem's id for a totem) and its X-ray level. */
    public record Held(String kind, int level) {
        public static final Held NOTHING = new Held("", 0);
    }

    /** Whether a hint is due: something with X-ray is in the offhand now, and it was not there (or had none) the tick before. */
    public static boolean due(Held before, Held now) {
        return now.level() > 0 && (before.level() <= 0 || !before.kind().equals(now.kind()));
    }

    /** The state to tell: no key to press, already on, recharging, or ready. */
    public static State state(long now, long onUntil, long readyAt, boolean keyBound) {
        if (!keyBound) {
            return State.UNBOUND;
        }
        if (now < onUntil) {
            return State.ON;
        }
        return now < readyAt ? State.RECHARGING : State.READY;
    }

    /** Whole seconds until {@code until}, rounded up; 0 when it has passed. */
    public static long secondsUntil(long now, long until) {
        return until <= now ? 0 : (until - now + 19) / 20;
    }
}
