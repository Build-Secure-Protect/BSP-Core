package com.mrgregles.bsp_core.proxy;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The backend-confirmed chat relay for a Velocity proxy (request VA-C2). With {@code proxy.relayChat}
 * on, a public chat line that passed every listener on this server is sent to the proxy as a plugin
 * message on {@code bsp_core:proxy}, through the sender's own connection, so the proxy can show it to
 * players on the other servers. Team chat and chat a mod cancelled never go. The byte layout is the
 * contract with the proxy plugin and is written out in {@code docs/API.md}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ProxyRelay {
    private ProxyRelay() {}

    public static final ResourceLocation CHANNEL = new ResourceLocation(BSPCore.MODID, "proxy");
    /** The layout version at the head of every message; the proxy ignores versions it does not know. */
    public static final int VERSION = 1;
    public static final String KIND_CHAT = "chat";

    /** One relayed line as the proxy reads it. */
    public record Relayed(int version, String kind, UUID sender, String name, String text, String json, String server) {}

    @Nullable
    private static volatile byte[] last;

    public static boolean enabled() {
        return BSPConfig.getOr(BSPConfig.PROXY_RELAY_CHAT, false);
    }

    /** Last of all: whatever is still standing here is public. A cancelled event never reaches this listener. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChat(ServerChatEvent event) {
        if (!enabled() || event.isCanceled()) {
            return;
        }
        relay(event.getPlayer(), event.getMessage());
    }

    /**
     * Sends one public line to the proxy as the player's: the final message (after any listener's
     * changes) and the decorated line the server would show ({@code <Name> text} by the chat type).
     * For mods that show chat another way and want it relayed too.
     */
    public static void relay(ServerPlayer player, Component message) {
        if (!enabled()) {
            return;
        }
        Component line = ChatType.bind(ChatType.CHAT, player).decorate(message);
        byte[] bytes = encode(new Relayed(VERSION, KIND_CHAT, player.getUUID(), player.getGameProfile().getName(), message.getString(),
                Component.Serializer.toJson(line), BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "server")));
        last = bytes;
        player.connection.send(new ClientboundCustomPayloadPacket(CHANNEL, new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes))));
    }

    /** The bytes of a message: version, kind, sender, name, text, the decorated line as JSON, this server's id. */
    public static byte[] encode(Relayed r) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(r.version());
        buf.writeUtf(r.kind(), 32);
        buf.writeUUID(r.sender());
        buf.writeUtf(r.name(), 64);
        buf.writeUtf(r.text(), 32767);
        buf.writeUtf(r.json(), 32767);
        buf.writeUtf(r.server(), 64);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        buf.release();
        return out;
    }

    /** Reads a message back; what the proxy plugin does on its side. */
    public static Relayed decode(byte[] bytes) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        Relayed r = new Relayed(buf.readByte(), buf.readUtf(32), buf.readUUID(), buf.readUtf(64), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(64));
        buf.release();
        return r;
    }

    /** The last message sent, for tests. */
    @Nullable
    public static byte[] last() {
        return last;
    }

    public static void forget() {
        last = null;
    }
}
