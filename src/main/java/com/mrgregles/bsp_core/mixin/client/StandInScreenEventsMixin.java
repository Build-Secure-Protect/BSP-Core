package com.mrgregles.bsp_core.mixin.client;

import com.mrgregles.bsp_core.client.tracking.RealScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A machine's real screen laid out or drawn as a Holo Grid picture ({@link RealScreens}) is no screen the player has open, so other mods
 * are not told of it: no screen init or background events (JEI, EMI, Quark and the like would add buttons or overlays to the picture, or
 * take it for the open screen). Every other screen posts its events as usual.
 */
@Mixin(Screen.class)
public abstract class StandInScreenEventsMixin {
    @Redirect(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "renderBackground"},
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z", remap = false),
            require = 0)
    private boolean bspCore$quietEvent(IEventBus bus, Event event) {
        return !RealScreens.quiet() && bus.post(event);
    }
}
