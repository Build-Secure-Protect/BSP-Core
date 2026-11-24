package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Holo Grid box pinned to a corner of the game screen, the owner's pick "Glass" (P10): no frame, the readings on see-through glass
 * with one turquoise line along the top, small (110 x 70) or medium (150 x 96). Hidden with F1, while a screen is open, and with its key.
 */
public final class PinOverlay {
    private PinOverlay() {}

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        private Register() {}

        @SubscribeEvent
        public static void onHoloPinOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("holo_pin", (gui, g, partial, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                GridModel.Box box = GridModel.pinned();
                if (box == null || !BSPClientConfig.pinShown() || mc.options.hideGui || mc.screen != null || mc.player == null) {
                    return;
                }
                int w = GridModel.pinSize == 1 ? 150 : 110, h = GridModel.pinSize == 1 ? 96 : 70;
                int x = GridModel.pinCorner % 2 == 1 ? width - w - 4 : 4, y = GridModel.pinCorner >= 2 ? height - h - 40 : 4;
                ModPanels.glass(true);
                try {
                    GridView.drawBox(g, new GridView.Placed(box, x, y, w, h), -1, -1, false);
                } finally {
                    ModPanels.glass(false);
                }
                Hud.rect(g, x, y, w, 1, 0x9919D3B0);
            });
        }
    }
}
