package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.AdminBankDataPacket;

import javax.annotation.Nullable;

/** Client side of the admin panel's Bank &amp; Market page: the last page the server sent, and the screen showing it (told when a new one comes). */
public final class AdminBankClient {
    private AdminBankClient() {}

    @Nullable
    private static AdminBankDataPacket latest;
    @Nullable
    private static Runnable listener;

    public static void receive(AdminBankDataPacket page) {
        latest = page;
        if (listener != null) {
            listener.run();
        }
    }

    @Nullable
    public static AdminBankDataPacket latest() {
        return latest;
    }

    /** Forgets the last page (a page is opened afresh). */
    public static void clear() {
        latest = null;
    }

    /** The open screen's update; null when none is open. */
    public static void listen(@Nullable Runnable onPage) {
        listener = onPage;
    }
}
