package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.SiegePacket;
import com.mrgregles.bsp_core.network.SiegeStatePacket;
import com.mrgregles.bsp_core.plasma.CarriedPowers;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Siege's key and its ring by the crosshair (orange while on, grey while recharging), the mirror of X-ray's on the other side. */
public final class SiegeClient implements IGuiOverlay {
    public static final SiegeClient INSTANCE = new SiegeClient();
    public static final KeyMapping TOGGLE = new KeyMapping("key.bsp_core.siege", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_V, "key.categories.bsp_core");
    private static long onUntil, readyAt, total, rechargeTotal;

    private SiegeClient() {}

    public static void receive(SiegeStatePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        onUntil = now + msg.activeTicks();
        readyAt = now + msg.readyInTicks();
        total = Math.max(1, msg.activeTicks());
        rechargeTotal = Math.max(1, msg.readyInTicks() - msg.activeTicks());
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (TOGGLE.consumeClick()) {
                BSPNetwork.CHANNEL.sendToServer(new SiegePacket());
            }
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || CarriedPowers.level(mc.player, Buff.SIEGE) <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        boolean on = now < onUntil, charging = !on && now < readyAt;
        if (!on && !charging) {
            return;
        }
        float k = on ? (onUntil - now) / (float) total : 1f - (readyAt - now) / (float) rechargeTotal;
        int cx = width / 2 - 24, cy = height / 2;
        for (int i = 0; i < 24; i++) {
            double a = -Math.PI / 2 + i / 24.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * 7), py = cy + (int) Math.round(Math.sin(a) * 7);
            g.fill(px, py, px + 1, py + 1, i / 24f < k ? (on ? 0xFFF9801D : 0xFF9AA3B5) : 0x802A2F3A);
        }
    }
}
