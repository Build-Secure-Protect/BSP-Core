package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Machine tracking's keys, all unbound until the player binds them in Controls (owner decisions MT2, MT14, MT15, MT40, MT46): open the
 * Holo Grid, next and previous page, page 1 to 9, show or hide the pinned box, and move into the popout window (pressed again there, or
 * Esc, to come back). The page keys also work in game, changing the popout's page.
 */
public final class TrackerKeys {
    private static final String CAT = "key.categories.bsp_core";
    public static final KeyMapping OPEN = key("key.bsp_core.holo_open", KeyConflictContext.IN_GAME);
    public static final KeyMapping NEXT = key("key.bsp_core.holo_next", KeyConflictContext.UNIVERSAL);
    public static final KeyMapping PREV = key("key.bsp_core.holo_prev", KeyConflictContext.UNIVERSAL);
    public static final KeyMapping PIN = key("key.bsp_core.holo_pin", KeyConflictContext.IN_GAME);
    public static final KeyMapping FOCUS = key("key.bsp_core.holo_focus", KeyConflictContext.UNIVERSAL);
    public static final KeyMapping[] PAGES = new KeyMapping[9];

    static {
        for (int i = 0; i < 9; i++) {
            PAGES[i] = key("key.bsp_core.holo_page" + (i + 1), KeyConflictContext.UNIVERSAL);
        }
    }

    private TrackerKeys() {}

    private static KeyMapping key(String name, KeyConflictContext ctx) {
        return new KeyMapping(name, ctx, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CAT);
    }

    /** Handles a page key pressed while a screen has the keyboard; true when it was one. */
    static boolean pageKey(int key, int scan) {
        if (NEXT.matches(key, scan)) {
            GridModel.next(1);
            return true;
        }
        if (PREV.matches(key, scan)) {
            GridModel.next(-1);
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (PAGES[i].matches(key, scan)) {
                GridModel.show(i);
                return true;
            }
        }
        return false;
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onHoloKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
            event.register(NEXT);
            event.register(PREV);
            for (KeyMapping k : PAGES) {
                event.register(k);
            }
            event.register(PIN);
            event.register(FOCUS);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Tick {
        @SubscribeEvent
        public static void onHoloClientTick(TickEvent.ClientTickEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (event.phase != TickEvent.Phase.END || mc.player == null) {
                return;
            }
            while (OPEN.consumeClick()) {
                HoloGridScreen.open();
            }
            while (NEXT.consumeClick()) {
                GridModel.next(1);
            }
            while (PREV.consumeClick()) {
                GridModel.next(-1);
            }
            for (int i = 0; i < 9; i++) {
                while (PAGES[i].consumeClick()) {
                    GridModel.show(i);
                }
            }
            while (PIN.consumeClick()) {
                BSPClientConfig.set(BSPClientConfig.PIN_SHOWN, !BSPClientConfig.pinShown());
                WatchSet.update();
            }
            while (FOCUS.consumeClick()) {
                Popout.focus();
            }
        }
    }
}
