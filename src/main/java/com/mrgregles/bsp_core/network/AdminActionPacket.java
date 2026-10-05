package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.AdminService;
import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: something pressed on the admin panel. Refresh is allowed for moderators;
 * everything else needs a full admin. The server answers with fresh panel data.
 */
public record AdminActionPacket(int action, UUID target, String dimension, BlockPos pos) {
    public static final int REFRESH = 0, TP_PLAYER = 1, TP_POS = 2, RESET_TOTEMS = 3, END_SEASON = 4, REWARD_HOLDERS = 5, FULL_RESET = 6, OPEN_REWARDS = 7, SET_HOLDER_HOURS = 8, SET_DECOY_RANGE = 9, SET_CHUNKS_ONLINE = 10;
    private static final UUID NOBODY = new UUID(0, 0);

    public AdminActionPacket(int action) {
        this(action, NOBODY, "", BlockPos.ZERO);
    }

    public AdminActionPacket(int action, UUID target) {
        this(action, target, "", BlockPos.ZERO);
    }

    public static void encode(AdminActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUUID(msg.target);
        buf.writeUtf(msg.dimension, 128);
        buf.writeBlockPos(msg.pos);
    }

    public static AdminActionPacket decode(FriendlyByteBuf buf) {
        return new AdminActionPacket(buf.readVarInt(), buf.readUUID(), buf.readUtf(128), buf.readBlockPos());
    }

    private static void say(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.bsp_core.admin." + key, args).withStyle(ChatFormatting.LIGHT_PURPLE), false);
    }

    public static void handle(AdminActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !Admins.isModerator(player)) {
            return;
        }
        if (msg.action != REFRESH && msg.action != OPEN_REWARDS && !Admins.isAdmin(player)) {
            say(player, "read_only");
            return;
        }
        String by = player.getGameProfile().getName();
        switch (msg.action) {
            case TP_PLAYER -> {
                ServerPlayer target = player.server.getPlayerList().getPlayer(msg.target);
                if (target == null) {
                    say(player, "offline");
                } else {
                    player.teleportTo(target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                }
            }
            case TP_POS -> {
                ResourceLocation id = ResourceLocation.tryParse(msg.dimension);
                ServerLevel level = id == null ? null : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (level != null) {
                    player.teleportTo(level, msg.pos.getX() + 0.5, msg.pos.getY() + 1.0, msg.pos.getZ() + 0.5, player.getYRot(), player.getXRot());
                }
            }
            case RESET_TOTEMS -> say(player, "reset_done", AdminService.resetTotems(player.server, msg.target));
            case END_SEASON -> say(player, "season_done", AdminService.endSeason(player.server, by));
            case FULL_RESET -> say(player, "full_reset_done", AdminService.fullReset(player.server, by));
            case OPEN_REWARDS -> {
                AdminService.openRewards(player);
                return;
            }
            case REWARD_HOLDERS -> {
                int paid = AdminService.rewardHolders(player.server, by);
                if (paid < 0) {
                    say(player, "reward_empty");
                } else {
                    say(player, "reward_done", paid);
                }
            }
            case SET_CHUNKS_ONLINE -> AdminService.setChunksOnlineOnly(player.server, msg.pos.getX() != 0);
            case SET_DECOY_RANGE -> AdminService.setDecoyRange(msg.pos.getX(), msg.pos.getY());
            case SET_HOLDER_HOURS -> AdminService.setHolderHours(player.server, msg.pos.getX());
            default -> {
            }
        }
        BSPNetwork.sendTo(player, AdminService.snapshot(player.server, player, false));
    }
}
