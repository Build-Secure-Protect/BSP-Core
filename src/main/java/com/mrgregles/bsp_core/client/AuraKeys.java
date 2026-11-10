package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The "show totem auras" keybind: unbound by default, toggles the client setting and says so on the action bar. */
public final class AuraKeys {
    public static final KeyMapping TOGGLE_AURAS = new KeyMapping("key.bsp_core.toggle_auras", KeyConflictContext.IN_GAME, InputConstants.UNKNOWN, "key.categories.bsp_core");

    private AuraKeys() {}

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        // a name of its own: Forge names each listener by class simple name and method, so same-named handlers in this package would collapse into one
        @SubscribeEvent
        public static void onAuraKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE_AURAS);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Tick {
        // a name of its own: Forge names each listener by class simple name and method, so same-named handlers in this package would collapse into one
        @SubscribeEvent
        public static void onAuraClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (TOGGLE_AURAS.consumeClick()) {
                BSPClientConfig.toggleAuras();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.translatable(BSPClientConfig.showAuras() ? "message.bsp_core.auras.shown" : "message.bsp_core.auras.hidden").withStyle(ChatFormatting.AQUA), true);
                }
            }
        }
    }
}
