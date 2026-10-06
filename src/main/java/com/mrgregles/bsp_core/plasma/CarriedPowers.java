package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Where a player's Carried powers come from: the totem in their offhand, or a Wave Emitter there running on a charged cell. */
public final class CarriedPowers {
    private CarriedPowers() {}

    /** The level of {@code buff} the player carries right now, 0 if none. */
    public static int level(Player player, Buff buff) {
        ItemStack offhand = player.getOffhandItem();
        if (TotemInventories.isTotem(offhand)) {
            return TotemUpgrades.getLevel(offhand, buff);
        }
        if (offhand.getItem() instanceof WaveEmitterItem) {
            return WaveEmitterItem.level(offhand, buff);
        }
        return 0;
    }
}
