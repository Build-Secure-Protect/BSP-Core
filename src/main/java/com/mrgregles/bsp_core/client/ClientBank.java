package com.mrgregles.bsp_core.client;

/** The client's copy of its own bank state that blocks need to draw: the deposit box and the Shopping Network. */
public final class ClientBank {
    private ClientBank() {}

    public static int boxSlots;
    public static long boxUntilMs;
    public static boolean remoteShops;

    public static void set(int slots, long until, boolean remote) {
        boxSlots = slots;
        boxUntilMs = until;
        remoteShops = remote;
    }
}
