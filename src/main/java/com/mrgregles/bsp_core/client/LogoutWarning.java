package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * Wraps the pause menu's quit button: if the player carries a Shatter Totem, ask before leaving and
 * explain that it will be placed next to where they log off.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class LogoutWarning {
    private LogoutWarning() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause)) {
            return;
        }
        for (var listener : new ArrayList<>(event.getListenersList())) {
            if (!(listener instanceof Button original) || !isQuitButton(original)) {
                continue;
            }
            Button wrapper = Button.builder(original.getMessage(), b -> onQuitPressed(pause, original))
                    .bounds(original.getX(), original.getY(), original.getWidth(), original.getHeight())
                    .build();
            event.removeListener(original);
            event.addListener(wrapper);
        }
    }

    private static boolean isQuitButton(Button button) {
        return button.getMessage().getContents() instanceof TranslatableContents tc
                && (tc.getKey().equals("menu.returnToMenu") || tc.getKey().equals("menu.disconnect"));
    }

    private static void onQuitPressed(Screen pause, Button original) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !carriesTotem(mc.player.getInventory())) {
            original.onPress();
            return;
        }
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                original.onPress();
            } else {
                mc.setScreen(pause);
            }
        }, Component.translatable("gui.bsp_core.logout.title"), Component.translatable("gui.bsp_core.logout.body")));
    }

    private static boolean carriesTotem(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (TotemInventories.isTotem(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }
}
