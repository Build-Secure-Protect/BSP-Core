package com.mrgregles.bsp_core.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Server players for tests, the way Region Guard makes them: the vanilla mock server player gets no channel and Forge's packet
 * listener touches the channel in its constructor, so the player is built on an in-memory netty channel instead. Packets sent to
 * the player go nowhere. The player is not in the server's player list.
 */
public final class MockPlayers {
    private static int counter;

    private MockPlayers() {}

    public static ServerPlayer serverPlayer(GameTestHelper h, GameType mode) {
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // registers and activates the handler, which gives the connection its channel
        ServerPlayer player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "bsp_test_" + (++counter)));
        player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), connection, player);
        player.setGameMode(mode);
        return player;
    }
}
