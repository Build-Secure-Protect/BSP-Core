package com.mrgregles.bsp_core.mixin;

import com.mrgregles.bsp_core.tracking.ScreenMirror;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraftforge.network.NetworkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Machine tracking's real screens: when the Holo Grid's fake player uses an AE2 or Refined Storage block to get its menu
 * ({@link ScreenMirror#openForMirror}), the menu is kept with the title its screen opens with, and no opening is sent. Every other
 * opening is Forge's own. {@code require = 0}: without it those mods' boxes still work, with the block's name as the title.
 */
@Mixin(value = NetworkHooks.class, remap = false)
public abstract class FakeMenuOpenMixin {
    @Inject(method = "openScreen(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void bsp_core$openForMirror(ServerPlayer player, MenuProvider provider, Consumer<FriendlyByteBuf> extraData, CallbackInfo ci) {
        if (ScreenMirror.openForMirror(player, provider)) {
            ci.cancel();
        }
    }
}
