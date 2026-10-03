package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class BSPNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BSPCore.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private BSPNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(StealRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StealRequestPacket::encode).decoder(StealRequestPacket::decode)
                .consumerMainThread(StealRequestPacket::handle).add();
        CHANNEL.messageBuilder(UpgradeRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UpgradeRequestPacket::encode).decoder(UpgradeRequestPacket::decode)
                .consumerMainThread(UpgradeRequestPacket::handle).add();
        CHANNEL.messageBuilder(AdminTotemActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminTotemActionPacket::encode).decoder(AdminTotemActionPacket::decode)
                .consumerMainThread(AdminTotemActionPacket::handle).add();
        CHANNEL.messageBuilder(PlacedUpgradeRequestPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlacedUpgradeRequestPacket::encode).decoder(PlacedUpgradeRequestPacket::decode)
                .consumerMainThread(PlacedUpgradeRequestPacket::handle).add();
        CHANNEL.messageBuilder(StealStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StealStatusPacket::encode).decoder(StealStatusPacket::decode)
                .consumerMainThread(StealStatusPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
