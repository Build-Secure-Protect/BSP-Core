package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.BountyScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the Bounty Board screen's whole view for one player ({@link BountyScreens}): the server's clock, the page,
 * whether to open the screen, the last action's answer, the bounties as this player sees them, what waits in their Collect box,
 * and the rules and money the place page shows.
 */
public record BountyViewPacket(long now, int page, boolean open, String result, List<BountyScreens.Row> rows, List<ItemStack> collect, BountyScreens.Rules rules) {
    public static final int MAX_COLLECT = 54, NOTES_MAX = 1000;

    public static void encode(BountyViewPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.now);
        buf.writeVarInt(msg.page);
        buf.writeBoolean(msg.open);
        buf.writeUtf(BSPNetwork.cut(msg.result, 32), 32);
        buf.writeVarInt(Math.min(msg.rows.size(), BountyScreens.MAX_ROWS + 20));
        int n = 0;
        for (BountyScreens.Row r : msg.rows) {
            if (n++ >= BountyScreens.MAX_ROWS + 20) {
                break;
            }
            buf.writeUtf(BSPNetwork.cut(r.id(), 16), 16);
            buf.writeUUID(r.target());
            buf.writeUtf(BSPNetwork.cut(r.targetName(), 16), 16);
            buf.writeUtf(BSPNetwork.cut(r.placerName(), 16), 16);
            buf.writeVarInt(r.coinTier() + 1);
            buf.writeVarLong(r.amount());
            buf.writeVarLong(r.bonus());
            buf.writeLong(r.bonusEndsMs());
            buf.writeLong(r.placedMs());
            buf.writeLong(r.endsMs());
            buf.writeVarInt(r.hunters());
            buf.writeVarInt(r.flags());
            buf.writeUtf(BSPNetwork.cut(r.notes(), NOTES_MAX), NOTES_MAX);
            buf.writeUtf(BSPNetwork.cut(r.refusal(), 16), 16);
        }
        int c = Math.min(msg.collect.size(), MAX_COLLECT);
        buf.writeVarInt(c);
        for (int i = 0; i < c; i++) {
            buf.writeItem(msg.collect.get(i));
        }
        BountyScreens.Rules r = msg.rules;
        buf.writeVarLong(r.pieces());
        buf.writeVarInt(r.coins().length);
        for (int v : r.coins()) {
            buf.writeVarInt(v);
        }
        buf.writeVarInt(r.feePercent());
        buf.writeVarLong(r.minPieces());
        buf.writeVarInt(r.maxDays());
        buf.writeVarInt(r.notesMax());
        buf.writeVarInt(r.maxOpen());
        buf.writeVarInt(r.maxAccepted());
        buf.writeVarInt(r.open());
        buf.writeVarInt(r.accepted());
        buf.writeVarLong(r.piecesPerCopper());
        buf.writeVarInt(r.tierValues().length);
        for (int v : r.tierValues()) {
            buf.writeVarInt(v);
        }
    }

    public static BountyViewPacket decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        int page = buf.readVarInt();
        boolean open = buf.readBoolean();
        String result = buf.readUtf(32);
        int n = Math.min(buf.readVarInt(), BountyScreens.MAX_ROWS + 20);
        List<BountyScreens.Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new BountyScreens.Row(buf.readUtf(16), buf.readUUID(), buf.readUtf(16), buf.readUtf(16), buf.readVarInt() - 1, buf.readVarLong(), buf.readVarLong(),
                    buf.readLong(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(NOTES_MAX), buf.readUtf(16)));
        }
        int c = Math.min(buf.readVarInt(), MAX_COLLECT);
        List<ItemStack> collect = new ArrayList<>(c);
        for (int i = 0; i < c; i++) {
            collect.add(buf.readItem());
        }
        long pieces = buf.readVarLong();
        int tiers = Math.min(buf.readVarInt(), 16);
        int[] coins = new int[tiers];
        for (int i = 0; i < tiers; i++) {
            coins[i] = buf.readVarInt();
        }
        int fee = buf.readVarInt();
        long minPieces = buf.readVarLong();
        int maxDays = buf.readVarInt(), notesMax = buf.readVarInt(), maxOpen = buf.readVarInt(), maxAccepted = buf.readVarInt(), open2 = buf.readVarInt(), accepted = buf.readVarInt();
        long ppc = buf.readVarLong();
        int tv = Math.min(buf.readVarInt(), 16);
        int[] tierValues = new int[tv];
        for (int i = 0; i < tv; i++) {
            tierValues[i] = buf.readVarInt();
        }
        BountyScreens.Rules rules = new BountyScreens.Rules(pieces, coins, fee, minPieces, maxDays, notesMax, maxOpen, maxAccepted, open2, accepted, ppc, tierValues);
        return new BountyViewPacket(now, page, open, result, rows, collect, rules);
    }

    public static void handle(BountyViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.BountyBoardScreen.take(msg));
        ctx.get().setPacketHandled(true);
    }
}
