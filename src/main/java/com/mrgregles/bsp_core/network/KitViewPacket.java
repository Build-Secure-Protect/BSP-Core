package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.KitFit;
import com.mrgregles.bsp_core.kit.Kits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server to client: everything the kit browser shows for one player: a card per kit they may see,
 * each with the player's state for it (the server decides every rule; the client only draws), the
 * player's rank on RankUp's ladder when known, their bank balance and free slots. {@code open} asks
 * the client to open the screen; otherwise an open screen takes the new data.
 */
public record KitViewPacket(boolean open, boolean enabled, String rank, int rankIndex, List<String> ladder, long pieces, int freeSlots, List<Card> cards) {
    /** One kit as the player sees it. {@code state} is a {@link Kits.State} name; {@code number} and {@code text} go with it. */
    public record Card(String id, String name, String description, ItemStack icon, List<ItemStack> items, String state, long number, String text,
                       boolean once, long cooldownSeconds, long price, String ranks, String servers, int slotsNeeded) {}

    public static KitViewPacket of(ServerPlayer player, boolean open) {
        List<Card> cards = new ArrayList<>();
        boolean enabled = Kits.enabled();
        Kits.RankNames names = Kits.rankNames();
        Set<String> mine = Kits.ranksOf(player.getUUID());
        int rankIndex = -1;
        for (int i = 0; i < names.names().size(); i++) {
            if (mine.contains(names.names().get(i))) {
                rankIndex = i;
            }
        }
        String rank = rankIndex >= 0 ? names.names().get(rankIndex) : mine.isEmpty() ? "" : mine.iterator().next();
        if (enabled) {
            List<Card> owed = new ArrayList<>(), rest = new ArrayList<>();
            for (Kit k : Kits.all(player.server)) {
                Kits.Status s = Kits.status(player, k);
                if (s.state() == Kits.State.HIDDEN || (s.state() == Kits.State.REDEEMED && !Kits.showRedeemed())) {
                    continue;
                }
                Card c = new Card(k.id, k.name, k.description, k.iconOrFirst().copy(), k.copies(), s.state().name(), s.number(), s.text(), k.once, k.cooldownSeconds, k.price,
                        String.join(", ", k.ranks), String.join(", ", k.servers), slotsNeeded(k));
                (s.state() == Kits.State.OWED ? owed : rest).add(c);
            }
            cards.addAll(owed);
            cards.addAll(rest);
        }
        return new KitViewPacket(open, enabled, rank, rankIndex, names.ladder() ? names.names() : List.of(), BspBank.balance(player.server, player.getUUID()),
                KitFit.freeSlots(player.getInventory()), cards);
    }

    /** How many empty slots the kit takes on its own (no part stacks to top up). */
    static int slotsNeeded(Kit k) {
        int n = 0;
        for (ItemStack s : k.items) {
            n += (s.getCount() + s.getMaxStackSize() - 1) / s.getMaxStackSize();
        }
        return n;
    }

    public static void encode(KitViewPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.open);
        buf.writeBoolean(m.enabled);
        buf.writeUtf(m.rank, 64);
        buf.writeVarInt(m.rankIndex + 1);
        buf.writeCollection(m.ladder, (b, s) -> b.writeUtf(s, 64));
        buf.writeLong(m.pieces);
        buf.writeVarInt(m.freeSlots);
        buf.writeVarInt(m.cards.size());
        for (Card c : m.cards) {
            buf.writeUtf(c.id, 32);
            buf.writeUtf(c.name, 64);
            buf.writeUtf(c.description, 128);
            buf.writeItem(c.icon);
            buf.writeVarInt(c.items.size());
            for (ItemStack s : c.items) {
                buf.writeItem(s);
            }
            buf.writeUtf(c.state, 16);
            buf.writeLong(c.number);
            buf.writeUtf(c.text, 256);
            buf.writeBoolean(c.once);
            buf.writeLong(c.cooldownSeconds);
            buf.writeLong(c.price);
            buf.writeUtf(c.ranks, 256);
            buf.writeUtf(c.servers, 256);
            buf.writeVarInt(c.slotsNeeded);
        }
    }

    public static KitViewPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean(), enabled = buf.readBoolean();
        String rank = buf.readUtf(64);
        int rankIndex = buf.readVarInt() - 1;
        List<String> ladder = buf.readList(b -> b.readUtf(64));
        long pieces = buf.readLong();
        int free = buf.readVarInt(), n = buf.readVarInt();
        List<Card> cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(32), name = buf.readUtf(64), description = buf.readUtf(128);
            ItemStack icon = buf.readItem();
            int in = buf.readVarInt();
            List<ItemStack> items = new ArrayList<>(in);
            for (int k = 0; k < in; k++) {
                items.add(buf.readItem());
            }
            cards.add(new Card(id, name, description, icon, items, buf.readUtf(16), buf.readLong(), buf.readUtf(256), buf.readBoolean(), buf.readLong(), buf.readLong(),
                    buf.readUtf(256), buf.readUtf(256), buf.readVarInt()));
        }
        return new KitViewPacket(open, enabled, rank, rankIndex, ladder, pieces, free, cards);
    }

    public static void handle(KitViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.KitScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
