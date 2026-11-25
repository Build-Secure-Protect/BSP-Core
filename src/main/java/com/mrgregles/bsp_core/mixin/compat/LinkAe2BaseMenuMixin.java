package com.mrgregles.bsp_core.mixin.compat;

import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2's other screens: the same actions on their slots (shift-click, move region) are refused while locked. Only with AE2. */
@Pseudo
@Mixin(targets = "appeng.menu.AEBaseMenu", remap = false)
public abstract class LinkAe2BaseMenuMixin {
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true, require = 0)
    private void bsp_core$lockedAction(ServerPlayer player, @Coerce Object action, int slot, long id, CallbackInfo ci) {
        if (!InventoryLink.allowAe2((AbstractContainerMenu) (Object) this, action)) {
            ci.cancel();
        }
    }
}
