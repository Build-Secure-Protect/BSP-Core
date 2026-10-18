package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Keeps key presses inside BSP-Core's screens. Forge offers every key pressed in a screen to other
 * mods before and after the screen itself, and a mod whose binding is a plain letter (G for a
 * minimap or an inventory add-on, say) acts on it even while a player is typing a team name here,
 * closing or replacing the screen. For any screen of this mod the key goes to the screen and no
 * further; Escape keeps its normal path so it still closes screens as it always has.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class ScreenKeys {
    private ScreenKeys() {}

    private static final String OURS = "com.mrgregles.bsp_core.";

    public static boolean isOurs(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(OURS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isOurs(screen) || event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        screen.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKeyUp(ScreenEvent.KeyReleased.Pre event) {
        Screen screen = event.getScreen();
        if (!isOurs(screen) || event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        screen.keyReleased(event.getKeyCode(), event.getScanCode(), event.getModifiers());
        event.setCanceled(true);
    }
}
