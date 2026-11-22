package com.mrgregles.bsp_core.demo;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

/** Handing out Demo Totems: only to admins ({@code /bsp totem demo}). */
public final class DemoTotems {
    private DemoTotems() {}

    /**
     * Whether this player may place, break or dress Demo Totems: an admin ({@link Admins#isAdmin}), never a fake player (a Create
     * deployer or another machine acting with its placer's name or id).
     */
    public static boolean isAdmin(@Nullable net.minecraft.world.entity.player.Player player) {
        return player != null && !(player instanceof net.minecraftforge.common.util.FakePlayer) && Admins.isAdmin(player);
    }

    /** The most one command gives. */
    public static final int MAX_COUNT = 64;

    /**
     * Gives {@code count} plain Demo Totems to {@code target} (what does not fit drops at their feet), or returns why not as a message
     * key: {@code command.bsp_core.demo.not_admin} when the target is not an admin.
     */
    @Nullable
    public static String give(ServerPlayer target, int count) {
        if (!isAdmin(target)) {
            return "command.bsp_core.demo.not_admin";
        }
        ItemHandlerHelper.giveItemToPlayer(target, new ItemStack(ModItems.DEMO_TOTEM.get(), Math.max(1, Math.min(MAX_COUNT, count))));
        return null;
    }
}
