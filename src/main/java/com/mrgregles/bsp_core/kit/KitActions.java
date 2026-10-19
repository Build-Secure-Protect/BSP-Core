package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.KitActionPacket;
import com.mrgregles.bsp_core.network.KitViewPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** What the kit browser's packets do on the server; every action answers with a fresh view. */
public final class KitActions {
    private KitActions() {}

    public static void send(ServerPlayer p, boolean open) {
        BSPNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), KitViewPacket.of(p, open));
    }

    /** Applies an action and answers; returns the outcome of a redeem or take (null for the others, or while a bank answer is pending). */
    public static void apply(ServerPlayer p, KitActionPacket msg) {
        switch (msg.action()) {
            case OPEN -> send(p, true);
            case REFRESH -> send(p, false);
            case REDEEM -> Kits.redeem(p, msg.kitId(), out -> {
                if (!out.ok() && out.message() != null) {
                    p.displayClientMessage(out.message(), false);
                }
                send(p, false);
            });
            case TAKE -> {
                Kits.Outcome out = Kits.take(p, msg.kitId());
                if (!out.ok() && out.message() != null) {
                    p.displayClientMessage(out.message(), false);
                }
                send(p, false);
            }
        }
    }
}
