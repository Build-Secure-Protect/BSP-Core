package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.KitActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The key that opens the kit browser: unbound until the player binds it in Controls. */
public final class KitKeys {
    private KitKeys() {}

    public static final KeyMapping OPEN = new KeyMapping("key.bsp_core.kits_open", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), "key.categories.bsp_core");

    /** Asks the server for the kit browser. */
    public static void open() {
        BSPNetwork.CHANNEL.sendToServer(KitActionPacket.of(KitActionPacket.Action.OPEN));
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        // a name of its own: Forge names each listener by class simple name and method, so same-named handlers in this package would collapse into one
        @SubscribeEvent
        public static void onKitKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Tick {
        // a name of its own: Forge names each listener by class simple name and method, so same-named handlers in this package would collapse into one
        @SubscribeEvent
        public static void onKitClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().player == null) {
                return;
            }
            while (OPEN.consumeClick()) {
                open();
            }
        }
    }
}
