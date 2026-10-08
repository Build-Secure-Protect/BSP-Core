package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.CloakBlindPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * An operator's choice to be treated like any outsider by Cloaking, so the effect can be tested: {@code /bsp cloak see off}.
 * Kept in the player's persistent data on the server, mirrored to the client's copy of the player (where the cloak is drawn),
 * and sent again at login.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class CloakBlind {
    private static final String TAG = BSPCore.MODID + ":CloakBlind";

    private CloakBlind() {}

    /** Whether this operator has chosen not to see through cloaks. Read on either side. */
    public static boolean isBlind(Player player) {
        return player.getPersistentData().getBoolean(TAG);
    }

    /** Client side: what the server last said. */
    public static void mirror(Player player, boolean blind) {
        player.getPersistentData().putBoolean(TAG, blind);
    }

    /** Server side: store and tell the client. */
    public static void set(ServerPlayer player, boolean blind) {
        player.getPersistentData().putBoolean(TAG, blind);
        BSPNetwork.sendTo(player, new CloakBlindPacket(blind));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && isBlind(sp)) {
            BSPNetwork.sendTo(sp, new CloakBlindPacket(true));
        }
    }
}
