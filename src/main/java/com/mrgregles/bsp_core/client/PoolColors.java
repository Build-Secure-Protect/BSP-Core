package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;

/**
 * The colour of each plasma pool in a tank, as the owner picked them on the Mob Chamber mock-ups (tools/preview/mob_chamber_concepts.html):
 * plain plasma, totem-powered plasma, and each Mob Chamber power. Used for the bands on the tank's fill bar.
 */
public final class PoolColors {
    public static final int PLAIN = 0xFF4CB2FA, TOTEM = 0xFF19D3B0;

    private PoolColors() {}

    /** ARGB for a Tank Extractor's pool {@code index}: 0 plain, 1 totem, then the mob powers in order. */
    public static int ofPool(int index) {
        var b = com.mrgregles.bsp_core.tank.TankExtractorBlockEntity.mobOf(index);
        return b != null ? mob(b) : index == com.mrgregles.bsp_core.tank.TankExtractorBlockEntity.TOTEM ? TOTEM : PLAIN;
    }

    /** ARGB for a mob power's pool; the totem colour for any other power. */
    public static int mob(Buff b) {
        return switch (b) {
            case GROWTH -> 0xFF6BD34A;
            case WITHER -> 0xFF8A8A96;
            case WARP_WARD -> 0xFFB06BFF;
            case UNDEAD_WARD -> 0xFF9AA83A;
            case HASTE -> 0xFFFFD23A;
            case SNARE -> 0xFFD8D8D8;
            case WIREFRAME -> 0xFFFF5A7A;
            case KEEP -> 0xFF9BE05A;
            case SCORCH -> 0xFFFF7A2A;
            case FIRE_WARD -> 0xFFE04A1A;
            case MENDING -> 0xFFFF8FD0;
            case LANTERN -> 0xFFFFE27A;
            case TIDE -> 0xFF3AD6E0;
            default -> TOTEM;
        };
    }
}
