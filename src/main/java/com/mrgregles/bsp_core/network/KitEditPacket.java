package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.kit.KitAdmin;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: something pressed on the admin panel's KITS tab. REFRESH is allowed for
 * moderators; everything else needs a full admin. The server answers with a {@link KitAdminPacket}.
 */
public record KitEditPacket(Action action, String kitId, @Nullable CompoundTag kit, UUID target, boolean isNew) {
    public enum Action { REFRESH, SAVE, DELETE, TEST, GIVE }

    private static final UUID NOBODY = new UUID(0, 0);

    public static KitEditPacket refresh() {
        return new KitEditPacket(Action.REFRESH, "", null, NOBODY, false);
    }

    public static KitEditPacket save(CompoundTag kit, boolean isNew) {
        return new KitEditPacket(Action.SAVE, kit.getString("Id"), kit, NOBODY, isNew);
    }

    public static KitEditPacket of(Action action, String kitId) {
        return new KitEditPacket(action, kitId, null, NOBODY, false);
    }

    public static KitEditPacket give(String kitId, UUID target) {
        return new KitEditPacket(Action.GIVE, kitId, null, target, false);
    }

    public static void encode(KitEditPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.kitId, 32);
        buf.writeBoolean(msg.kit != null);
        if (msg.kit != null) {
            buf.writeNbt(msg.kit);
        }
        buf.writeUUID(msg.target);
        buf.writeBoolean(msg.isNew);
    }

    public static KitEditPacket decode(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        String id = buf.readUtf(32);
        CompoundTag kit = buf.readBoolean() ? buf.readNbt() : null;
        return new KitEditPacket(action, id, kit, buf.readUUID(), buf.readBoolean());
    }

    public static void handle(KitEditPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        ctx.get().setPacketHandled(true);
        if (player == null || !Admins.isModerator(player)) {
            return;
        }
        if (msg.action != Action.REFRESH && !Admins.isAdmin(player)) {
            KitAdmin.send(player, false, "read_only");
            return;
        }
        KitAdmin.send(player, false, KitAdmin.apply(player, msg));
    }
}
