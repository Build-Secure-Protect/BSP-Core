package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.chamber.ChamberRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client, with the datapacks: every mob a Mob Chamber could spawn ({@link ChamberRoster}), for the recipe viewers' pages. */
public record ChamberRosterPacket(List<ChamberRoster.Entry> roster) {
    private static final int MAX_MOBS = 2048, MAX_PLACES = 512;

    public static void encode(ChamberRosterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.roster.size());
        for (ChamberRoster.Entry e : msg.roster) {
            buf.writeUtf(e.entity(), 256);
            buf.writeBoolean(e.hostile());
            buf.writeCollection(e.biomes(), (b, s) -> b.writeUtf(s, 256));
            buf.writeCollection(e.structures(), (b, s) -> b.writeUtf(s, 256));
            buf.writeVarInt(e.places());
            buf.writeVarInt(e.side());
            buf.writeVarInt(e.height());
            buf.writeUtf(e.drop(), 256);
            buf.writeVarInt(e.power() + 1);
        }
    }

    public static ChamberRosterPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX_MOBS);
        List<ChamberRoster.Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String entity = buf.readUtf(256);
            boolean hostile = buf.readBoolean();
            List<String> biomes = buf.readCollection(k -> new ArrayList<>(Math.min(k, MAX_PLACES)), b -> b.readUtf(256));
            List<String> structures = buf.readCollection(k -> new ArrayList<>(Math.min(k, MAX_PLACES)), b -> b.readUtf(256));
            out.add(new ChamberRoster.Entry(entity, hostile, biomes, structures, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt() - 1));
        }
        return new ChamberRosterPacket(out);
    }

    public static void handle(ChamberRosterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ChamberRoster.receive(msg.roster);
        ctx.get().setPacketHandled(true);
    }
}
