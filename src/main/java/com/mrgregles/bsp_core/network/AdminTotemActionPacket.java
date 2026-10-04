package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: an operator pressed an admin control on the totem panel. */
public record AdminTotemActionPacket(BlockPos pos, Action action, String text, int value) {
    public enum Action { UNCLAIM, ASSIGN_OWNER, CANCEL_STEAL, FINISH_STEAL, RESET_BUFFS, SET_BUFF }

    public static void encode(AdminTotemActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.text, 64);
        buf.writeVarInt(msg.value);
    }

    public static AdminTotemActionPacket decode(FriendlyByteBuf buf) {
        return new AdminTotemActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(AdminTotemActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.hasPermissions(2)
                && player.level().getBlockEntity(msg.pos) instanceof ShatterTotemBlockEntity totem) {
            apply(player, totem, msg);
        }
        ctx.get().setPacketHandled(true);
    }

    private static void apply(ServerPlayer admin, ShatterTotemBlockEntity totem, AdminTotemActionPacket msg) {
        switch (msg.action) {
            case UNCLAIM -> {
                totem.adminSetOwner(null);
                feedback(admin, "message.bsp_core.admin.unclaimed");
            }
            case ASSIGN_OWNER -> {
                String name = msg.text.trim();
                if (name.isEmpty()) {
                    feedback(admin, "message.bsp_core.admin.need_name");
                    return;
                }
                TotemOwner owner = resolveOwner(admin.server, name);
                totem.adminSetOwner(owner);
                feedback(admin, "message.bsp_core.admin.assigned", owner.name());
            }
            case CANCEL_STEAL -> {
                feedback(admin, totem.adminCancelSteal() ? "message.bsp_core.admin.steal_cancelled" : "message.bsp_core.admin.no_steal");
            }
            case FINISH_STEAL -> {
                feedback(admin, totem.adminFinishSteal() ? "message.bsp_core.admin.steal_finished" : "message.bsp_core.admin.no_steal");
            }
            case RESET_BUFFS -> {
                totem.resetUpgrades();
                feedback(admin, "message.bsp_core.admin.buffs_reset");
            }
            case SET_BUFF -> {
                if (msg.value < 0) { // the tier itself
                    totem.setTier(totem.getTier() + (msg.text.equals("-") ? -1 : 1));
                    feedback(admin, "message.bsp_core.upgrade.tier_raised", TotemUpgrades.roman(totem.getTier()));
                    return;
                }
                TotemUpgrades.Buff buff = TotemUpgrades.Buff.byOrdinal(msg.value);
                if (buff == null) {
                    return;
                }
                int level = totem.getUpgradeLevel(buff) + (msg.text.equals("-") ? -1 : 1);
                totem.setUpgradeLevel(buff, level);
                feedback(admin, "message.bsp_core.admin.buff_set", Component.translatable(buff.translationKey()), totem.getUpgradeLevel(buff));
            }
        }
        BSPCore.LOGGER.info("Admin {} applied {} to totem at {}", admin.getGameProfile().getName(), msg.action, msg.pos);
    }

    /** Online player, then the server's profile cache, then an offline-mode UUID so any name can be used for testing. */
    private static TotemOwner resolveOwner(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return new TotemOwner(online.getUUID(), online.getGameProfile().getName());
        }
        if (server.getProfileCache() != null) {
            GameProfile cached = server.getProfileCache().get(name).orElse(null);
            if (cached != null && cached.getId() != null) {
                return new TotemOwner(cached.getId(), cached.getName());
            }
        }
        return new TotemOwner(UUIDUtil.createOfflinePlayerUUID(name), name);
    }

    private static void feedback(ServerPlayer admin, String key, Object... args) {
        admin.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }
}
