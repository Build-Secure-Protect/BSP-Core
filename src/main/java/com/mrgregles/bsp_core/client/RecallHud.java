package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.RecallAnswerPacket;
import com.mrgregles.bsp_core.network.RecallOfferPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Locale;

/** The Recall offer ("Compass Card"): a small card above the hotbar with an arrow toward the totem, its distance, a draining ring and the two keys. */
public final class RecallHud implements IGuiOverlay {
    public static final RecallHud INSTANCE = new RecallHud();
    public static final KeyMapping ACCEPT = new KeyMapping("key.bsp_core.recall_accept", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.bsp_core");
    public static final KeyMapping DECLINE = new KeyMapping("key.bsp_core.recall_decline", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_N, "key.categories.bsp_core");
    private static final int BG = 0xE010151C, RED = 0xFFFF6B5C, TQ = 0xFF19D3B0, DIM = 0xFF2A2F3A, TEXT = 0xE6EAF2, MUTED = 0x9AA3B5;
    @Nullable
    private static RecallOfferPacket offer;
    private static long until;

    private RecallHud() {}

    public static void receive(RecallOfferPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (msg.seconds() <= 0 || mc.level == null) {
            offer = null;
            return;
        }
        offer = msg;
        until = mc.level.getGameTime() + msg.seconds() * 20L;
    }

    private static void answer(boolean accept) {
        if (offer != null) {
            BSPNetwork.CHANNEL.sendToServer(new RecallAnswerPacket(accept));
            offer = null;
        }
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (offer == null || mc.level == null || mc.player == null) {
            return;
        }
        long left = until - mc.level.getGameTime();
        if (left <= 0) {
            offer = null;
            return;
        }
        Font font = gui.getFont();
        int w = 190, h = 46, x = width / 2 - w / 2, y = height - 100;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, RED);
        g.fill(x, y, x + w, y + h, BG);
        // the draining ring, drawn as a wedge of dots
        int cx = x + 22, cy = y + h / 2;
        float k = left / (float) (offer.seconds() * 20);
        for (int i = 0; i < 48; i++) {
            double a = -Math.PI / 2 + i / 48.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * 14), py = cy + (int) Math.round(Math.sin(a) * 14);
            g.fill(px - 1, py - 1, px + 1, py + 1, i / 48f < k ? RED : DIM);
        }
        String secs = Integer.toString((int) Math.ceil(left / 20.0));
        g.drawString(font, secs, cx - font.width(secs) / 2, cy - 4, TEXT, false);
        // arrow toward the totem (only in the same dimension) and the distance
        boolean here = offer.dimension().equals(mc.level.dimension().location().toString());
        String arrow = "?";
        if (here) {
            double dx = offer.pos().getX() + 0.5 - mc.player.getX(), dz = offer.pos().getZ() + 0.5 - mc.player.getZ();
            double ang = Math.toDegrees(Math.atan2(dz, dx)) - mc.player.getYRot() - 90;
            ang = Mth.wrapDegrees(ang);
            arrow = ang > -22.5 && ang <= 22.5 ? "^" : ang > 22.5 && ang <= 67.5 ? "/" : ang > 67.5 && ang <= 112.5 ? ">" : ang > 112.5 && ang <= 157.5 ? "\\" : ang < -22.5 && ang >= -67.5 ? "\\" : ang < -67.5 && ang >= -112.5 ? "<" : ang < -112.5 && ang >= -157.5 ? "/" : "v";
        }
        g.drawString(font, Component.translatable("hud.bsp_core.recall.title").getString().toUpperCase(Locale.ROOT), x + 44, y + 5, RED & 0xFFFFFF, false);
        String dist = here ? Component.translatable("hud.bsp_core.recall.distance", arrow, (int) Math.sqrt(mc.player.blockPosition().distSqr(offer.pos()))).getString()
                : Component.translatable("hud.bsp_core.recall.elsewhere").getString();
        g.drawString(font, dist, x + 44, y + 16, TEXT, false);
        g.drawString(font, Component.translatable("hud.bsp_core.recall.lands", offer.radius()), x + 44, y + 27, MUTED, false);
        g.drawString(font, Component.translatable("hud.bsp_core.recall.keys", ACCEPT.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT), DECLINE.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT)), x + 44, y + 37, TQ & 0xFFFFFF, false);
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(ACCEPT);
            event.register(DECLINE);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Tick {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (ACCEPT.consumeClick()) {
                answer(true);
            }
            while (DECLINE.consumeClick()) {
                answer(false);
            }
        }
    }
}
