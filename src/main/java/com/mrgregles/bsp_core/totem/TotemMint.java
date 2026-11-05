package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Makes a brand-new Shatter Totem for a player: Tier I, no upgrades, in their name, made in the season in progress, with its own
 * id and instance. The first-join grant and every kit that hands out a totem use it, so no two players ever get copies of one
 * totem (a copy would turn into a husk) and a kit saved last season still gives a live totem.
 */
public final class TotemMint {
    private TotemMint() {}

    public static ItemStack fresh(ServerPlayer player) {
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        new TotemOwner(player.getUUID(), player.getGameProfile().getName()).applyTo(totem);
        TotemIdentity.stampSeason(totem, SeasonData.get(player.server).number);
        TotemIdentity.ensure(totem);
        return totem;
    }

    /** A blank totem for a kit to keep: no id, owner, season, tier or upgrades; {@link #fresh} replaces it at every hand-out. */
    public static ItemStack blank(int count) {
        return new ItemStack(ModItems.SHATTER_TOTEM.get(), Math.max(1, count));
    }
}
