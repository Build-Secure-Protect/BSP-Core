package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardZones;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Who may tag, read and operate a machine: only a block the player could open themselves. That is a block with a block entity, which
 * Region Guard (when installed) lets them open (its chest-access flag: their own claim, a claim they are a member of, or land nobody
 * claimed), and which, when it is a Mekanism machine, its security lets them use (never another player's private machine). Asked when
 * tagging and again on every reading, so a player removed from a claim stops seeing inside its machines.
 */
public final class TrackerAccess {
    private static boolean mekanismWarned;
    @Nullable
    private static Method mekanismCanAccess;
    @Nullable
    private static Object mekanismSecurity;
    private static boolean mekanismLooked;

    private TrackerAccess() {}

    /** Whether there is a machine at {@code pos} to tag: a loaded block with a block entity. Never loads a chunk. */
    public static boolean isMachine(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) != null;
    }

    /** Whether {@code player} may tag or read the machine at {@code pos} (see the class comment). */
    public static boolean mayUse(ServerLevel level, BlockPos pos, Player player) {
        if (!isMachine(level, pos)) {
            return false;
        }
        if (!RegionGuardZones.mayOpenChest(level, pos, player)) {
            return false;
        }
        return mekanismAllows(player.getUUID(), level.getBlockEntity(pos));
    }

    /** Whether Mekanism's security lets the player use this block entity; true when Mekanism is absent or the block is not its. */
    static boolean mekanismAllows(UUID player, @Nullable BlockEntity be) {
        if (be == null || !ModList.get().isLoaded("mekanism")) {
            return true;
        }
        try {
            if (!mekanismLooked) {
                mekanismLooked = true;
                Class<?> utils = Class.forName("mekanism.api.security.ISecurityUtils");
                mekanismSecurity = utils.getField("INSTANCE").get(null);
                mekanismCanAccess = utils.getMethod("canAccess", UUID.class, net.minecraftforge.common.capabilities.ICapabilityProvider.class, boolean.class);
            }
            if (mekanismCanAccess == null || mekanismSecurity == null) {
                return !be.getClass().getName().startsWith("mekanism."); // a Mekanism machine may be private: refused when it cannot be asked
            }
            return (Boolean) mekanismCanAccess.invoke(mekanismSecurity, player, be, false);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (!mekanismWarned) {
                mekanismWarned = true;
                BSPCore.LOGGER.warn("BSP-Core could not ask Mekanism's security about a machine ({}); Mekanism machines cannot be tagged. Is Mekanism's API older than expected?", e.toString());
            }
            return !be.getClass().getName().startsWith("mekanism.");
        }
    }
}
