package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.looks.LookActions;
import com.mrgregles.bsp_core.looks.LookOwner;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LookActionPacket;
import com.mrgregles.bsp_core.network.LookDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;

/** The LOOKS tab's data as the server last sent it, and the requests the tab sends. */
public final class ClientLooks {
    private ClientLooks() {}

    private static BlockPos pos = BlockPos.ZERO;
    private static LookOwner record = new LookOwner();
    @Nullable
    private static TotemLook look;
    private static long waitUntil;

    private static int version;

    /** Counts the data that arrived, so the tab can tell new data from what it already used. */
    public static int version() {
        return version;
    }

    public static void accept(LookDataPacket msg) {
        version++;
        pos = msg.pos();
        record = LookOwner.load(msg.record());
        look = TotemLook.load(msg.look());
        waitUntil = System.currentTimeMillis() + msg.waitMs();
    }

    /** The totem the data was sent for. */
    public static BlockPos pos() {
        return pos;
    }

    /** The player's own record: dressing, hats, hat choice, saved looks. */
    public static LookOwner record() {
        return record;
    }

    /** The look on the totem at {@link #pos()} when it is the player's, shown or hidden; null when plain. */
    @Nullable
    public static TotemLook look() {
        return look;
    }

    /** Milliseconds until the player may put on or save a look again. */
    public static long waitMs() {
        return Math.max(0, waitUntil - System.currentTimeMillis());
    }

    public static void send(BlockPos at, LookActions.Action action, int slot, String hat, @Nullable TotemLook l) {
        CompoundTag tag = l == null ? null : l.save();
        BSPNetwork.CHANNEL.sendToServer(new LookActionPacket(at, action, slot, hat, tag));
    }

    /** Asks for the tab's data for the totem at {@code at}. */
    public static void request(BlockPos at) {
        send(at, LookActions.Action.REQUEST, 0, "", null);
    }
}
