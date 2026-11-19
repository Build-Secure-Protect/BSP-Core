package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server → client: the admin panel's BOUNTIES tab: every open bounty in full (notes, placer, hunters), the newest closed ones, whether this staff member may change them. */
public record BountyAdminPacket(long now, boolean canEdit, String result, List<Bounty> open, List<BountyStore.Closed> closed, int maxDays) {
    public static final int MAX_OPEN = 200, MAX_CLOSED = 60;

    public static void encode(BountyAdminPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.now);
        buf.writeBoolean(msg.canEdit);
        buf.writeUtf(BSPNetwork.cut(msg.result, 32), 32);
        buf.writeVarInt(msg.maxDays);
        int n = Math.min(msg.open.size(), MAX_OPEN);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeNbt(msg.open.get(i).save());
        }
        int c = Math.min(msg.closed.size(), MAX_CLOSED);
        buf.writeVarInt(c);
        for (int i = 0; i < c; i++) {
            BountyStore.Closed x = msg.closed.get(i);
            buf.writeNbt(x.bounty().save());
            buf.writeUtf(x.ending().name(), 24);
            buf.writeLong(x.closedMs());
            buf.writeBoolean(x.hunter() != null);
            if (x.hunter() != null) {
                buf.writeUUID(x.hunter());
            }
            buf.writeUtf(BSPNetwork.cut(x.hunterName(), 16), 16);
            buf.writeUtf(BSPNetwork.cut(x.by(), 64), 64);
            buf.writeBoolean(x.bonusPaid());
        }
    }

    public static BountyAdminPacket decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        boolean canEdit = buf.readBoolean();
        String result = buf.readUtf(32);
        int maxDays = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), MAX_OPEN);
        List<Bounty> open = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CompoundTag t = buf.readNbt();
            if (t != null) {
                open.add(Bounty.load(t));
            }
        }
        int c = Math.min(buf.readVarInt(), MAX_CLOSED);
        List<BountyStore.Closed> closed = new ArrayList<>(c);
        for (int i = 0; i < c; i++) {
            CompoundTag t = buf.readNbt();
            String ending = buf.readUtf(24);
            long at = buf.readLong();
            UUID hunter = buf.readBoolean() ? buf.readUUID() : null;
            String hunterName = buf.readUtf(16), by = buf.readUtf(64);
            boolean bonus = buf.readBoolean();
            BountyStore.Ending e;
            try {
                e = BountyStore.Ending.valueOf(ending);
            } catch (IllegalArgumentException ex) {
                e = BountyStore.Ending.REMOVED;
            }
            if (t != null) {
                closed.add(new BountyStore.Closed(Bounty.load(t), e, at, hunter, hunterName, by, bonus));
            }
        }
        return new BountyAdminPacket(now, canEdit, result, open, closed, maxDays);
    }

    public static void handle(BountyAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.AdminScreen.receiveBounties(msg));
        ctx.get().setPacketHandled(true);
    }
}
