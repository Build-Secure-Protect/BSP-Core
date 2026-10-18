package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The key that opens the Team screen: unbound until the player binds it in Controls. */
public final class TeamKeys {
    private TeamKeys() {}

    public static final KeyMapping OPEN = new KeyMapping("key.bsp_core.team_open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.bsp_core");
    /** The team prompts' own pair, apart from the Recall offer's R and N. */
    public static final KeyMapping ACCEPT = new KeyMapping("key.bsp_core.team_accept", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_Y, "key.categories.bsp_core");
    public static final KeyMapping DECLINE = new KeyMapping("key.bsp_core.team_decline", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.bsp_core");

    /** Asks the server for the Team screen. */
    public static void open() {
        BSPNetwork.CHANNEL.sendToServer(TeamActionPacket.of(TeamActionPacket.Action.VIEW));
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
            event.register(ACCEPT);
            event.register(DECLINE);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Tick {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
                return;
            }
            while (OPEN.consumeClick()) {
                open();
            }
            while (ACCEPT.consumeClick()) {
                PromptHud.answer(true);
            }
            while (DECLINE.consumeClick()) {
                PromptHud.answer(false);
            }
        }
    }
}
