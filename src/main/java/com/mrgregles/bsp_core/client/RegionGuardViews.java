package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.ModList;

import java.util.function.IntConsumer;

/**
 * Region Guard's player-side screens, for the Land Office: BANNED HERE (its {@code BannedListView}, API 7, request RG-C12) and
 * OFFICE PLOTS (its {@code OfficePlotsView}, API 8, request RG-C16). <b>Client only:</b> called from {@link LandOfficeScreen},
 * never from a class the server loads. Region Guard is optional and reached lazily; a Region Guard too old for a call (a
 * {@link LinkageError}) turns that call off for the session, and the screen leaves out its line or key.
 */
public final class RegionGuardViews {
    private static final String MOD_ID = "bsp_region_guard";
    private static Boolean present;
    private static boolean bannedBroken, officeBroken;

    private RegionGuardViews() {}

    private static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded(MOD_ID);
        }
        return present;
    }

    /** Whether BANNED HERE can be asked for on this client. */
    public static boolean bannedAvailable() {
        return present() && !bannedBroken;
    }

    /** Whether OFFICE PLOTS can be opened from this client. */
    public static boolean officePlotsAvailable() {
        return present() && !officeBroken;
    }

    /** Asks Region Guard how many items are banned in a region; {@code answer} runs on the client thread. Does nothing when it can't ask. */
    public static void bannedCount(String regionId, IntConsumer answer) {
        if (!bannedAvailable()) {
            return;
        }
        try {
            Api.count(regionId, answer);
        } catch (LinkageError e) {
            bannedBroken = true;
            BSPCore.LOGGER.warn("Region Guard has no BANNED HERE list for the Land Office ({}); it needs Region Guard API 7.", e.toString());
        }
    }

    /** Opens Region Guard's BANNED HERE screen for a region; Esc comes back to the Land Office. */
    public static void openBanned(String regionId) {
        if (!bannedAvailable()) {
            return;
        }
        try {
            Api.openBanned(regionId);
        } catch (LinkageError e) {
            bannedBroken = true;
            BSPCore.LOGGER.warn("Region Guard could not open BANNED HERE ({}); it needs Region Guard API 7.", e.toString());
        }
    }

    /** Opens Region Guard's OFFICE PLOTS for the Land Office at {@code office}; the server checks admin rights again. */
    public static void openOfficePlots(BlockPos office) {
        if (!officePlotsAvailable()) {
            return;
        }
        try {
            Api.openOfficePlots(office);
        } catch (LinkageError e) {
            officeBroken = true;
            BSPCore.LOGGER.warn("Region Guard could not open OFFICE PLOTS ({}); it needs Region Guard API 8.", e.toString());
        }
    }

    /** Touches Region Guard's classes only when it is installed. */
    private static final class Api {
        static void count(String regionId, IntConsumer answer) {
            com.mrgregles.bsp_region_guard.api.client.BannedListView.count(regionId, answer);
        }

        static void openBanned(String regionId) {
            com.mrgregles.bsp_region_guard.api.client.BannedListView.open(regionId);
        }

        static void openOfficePlots(BlockPos office) {
            com.mrgregles.bsp_region_guard.api.client.OfficePlotsView.open(office);
        }
    }
}
