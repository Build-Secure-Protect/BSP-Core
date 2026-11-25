package com.mrgregles.bsp_core.mixin.client;

import com.mrgregles.bsp_core.client.tracking.RealScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** As {@link StandInScreenEventsMixin}, for a container screen's own render events (EMI draws its panels on them). */
@Mixin(AbstractContainerScreen.class)
public abstract class StandInContainerEventsMixin {
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z", remap = false),
            require = 0)
    private boolean bspCore$quietEvent(IEventBus bus, Event event) {
        return !RealScreens.quiet() && bus.post(event);
    }
}
