package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.totem.TotemUpgrades;

/**
 * Colours for the upgrade orbs around a placed totem and for the aura spheres. Each buff has a fixed
 * hue; saturation and depth rise from level 1 (pale) to level 5 (vivid).
 */
public final class UpgradeOrbColors {
    /** Hue in degrees per buff, in {@link TotemUpgrades.Buff} order. */
    private static final float[] HUES = {8f, 218f, 48f, 275f, 130f, 165f, 345f, 195f, 60f, 300f, 25f, 100f, 240f, 180f, 0f, 260f, 205f, 90f};

    private UpgradeOrbColors() {}

    public static float hue(TotemUpgrades.Buff buff) {
        return buff.ordinal() < HUES.length ? HUES[buff.ordinal()] : 0f;
    }

    /** RGB 0xRRGGBB for an orb at {@code level} (1..max). */
    public static int levelColor(TotemUpgrades.Buff buff, int level, int maxLevel) {
        float t = maxLevel <= 1 ? 1f : (level - 1) / (float) (maxLevel - 1);
        t = Math.max(0f, Math.min(1f, t));
        float s = 0.25f + 0.75f * t;
        float l = 0.82f - 0.27f * t;
        return hslToRgb(hue(buff) / 360f, s, l);
    }

    /** Vivid colour for an aura sphere. */
    public static int auraColor(TotemUpgrades.Buff buff) {
        return hslToRgb(hue(buff) / 360f, 0.9f, 0.6f);
    }

    static int hslToRgb(float h, float s, float l) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        int r = Math.round(hue(p, q, h + 1f / 3f) * 255);
        int g = Math.round(hue(p, q, h) * 255);
        int b = Math.round(hue(p, q, h - 1f / 3f) * 255);
        return (r << 16) | (g << 8) | b;
    }

    private static float hue(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }
}
