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
        CHANNEL.messageBuilder(ScoreScreenOpenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScoreScreenOpenPacket::encode).decoder(ScoreScreenOpenPacket::decode)
                .consumerMainThread(ScoreScreenOpenPacket::handle).add();
        CHANNEL.messageBuilder(ScoreScreenConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScoreScreenConfigPacket::encode).decoder(ScoreScreenConfigPacket::decode)
                .consumerMainThread(ScoreScreenConfigPacket::handle).add();
        CHANNEL.messageBuilder(AdminDataPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AdminDataPacket::encode).decoder(AdminDataPacket::decode)
                .consumerMainThread(AdminDataPacket::handle).add();
        CHANNEL.messageBuilder(AdminActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AdminActionPacket::encode).decoder(AdminActionPacket::decode)
                .consumerMainThread(AdminActionPacket::handle).add();
        CHANNEL.messageBuilder(PrizeSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PrizeSyncPacket::encode).decoder(PrizeSyncPacket::decode)
                .consumerMainThread(PrizeSyncPacket::handle).add();
        CHANNEL.messageBuilder(ZoneOpenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ZoneOpenPacket::encode).decoder(ZoneOpenPacket::decode)
                .consumerMainThread(ZoneOpenPacket::handle).add();
        CHANNEL.messageBuilder(ZoneConfigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ZoneConfigPacket::encode).decoder(ZoneConfigPacket::decode)
                .consumerMainThread(ZoneConfigPacket::handle).add();
        CHANNEL.messageBuilder(DecoyFakeOpenPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DecoyFakeOpenPacket::encode).decoder(DecoyFakeOpenPacket::decode)
                .consumerMainThread(DecoyFakeOpenPacket::handle).add();
        CHANNEL.messageBuilder(DecoyStealPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DecoyStealPacket::encode).decoder(DecoyStealPacket::decode)
                .consumerMainThread(DecoyStealPacket::handle).add();
        CHANNEL.messageBuilder(VaultAccessPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(VaultAccessPacket::encode).decoder(VaultAccessPacket::decode)
                .consumerMainThread(VaultAccessPacket::handle).add();
        CHANNEL.messageBuilder(ScoreSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScoreSyncPacket::encode).decoder(ScoreSyncPacket::decode)
                .consumerMainThread(ScoreSyncPacket::handle).add();
        CHANNEL.messageBuilder(ChunkViewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ChunkViewPacket::encode).decoder(ChunkViewPacket::decode)
                .consumerMainThread(ChunkViewPacket::handle).add();
        CHANNEL.messageBuilder(InterfaceViewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(InterfaceViewPacket::encode).decoder(InterfaceViewPacket::decode)
                .consumerMainThread(InterfaceViewPacket::handle).add();
        CHANNEL.messageBuilder(SiegePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SiegePacket::encode).decoder(SiegePacket::decode)
                .consumerMainThread(SiegePacket::handle).add();
        CHANNEL.messageBuilder(SiegeStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SiegeStatePacket::encode).decoder(SiegeStatePacket::decode)
                .consumerMainThread(SiegeStatePacket::handle).add();
        CHANNEL.messageBuilder(CloakRefreshPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CloakRefreshPacket::encode).decoder(CloakRefreshPacket::decode)
                .consumerMainThread(CloakRefreshPacket::handle).add();
        CHANNEL.messageBuilder(CloakStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloakStatusPacket::encode).decoder(CloakStatusPacket::decode)
                .consumerMainThread(CloakStatusPacket::handle).add();
        CHANNEL.messageBuilder(CloakBlindPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CloakBlindPacket::encode).decoder(CloakBlindPacket::decode)
                .consumerMainThread(CloakBlindPacket::handle).add();
        CHANNEL.messageBuilder(RecallOfferPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RecallOfferPacket::encode).decoder(RecallOfferPacket::decode)
                .consumerMainThread(RecallOfferPacket::handle).add();
        CHANNEL.messageBuilder(RecallAnswerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RecallAnswerPacket::encode).decoder(RecallAnswerPacket::decode)
                .consumerMainThread(RecallAnswerPacket::handle).add();
        CHANNEL.messageBuilder(TotemAccessPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TotemAccessPacket::encode).decoder(TotemAccessPacket::decode)
                .consumerMainThread(TotemAccessPacket::handle).add();
        CHANNEL.messageBuilder(ChunkActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChunkActionPacket::encode).decoder(ChunkActionPacket::decode)
                .consumerMainThread(ChunkActionPacket::handle).add();
        CHANNEL.messageBuilder(StealStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StealStatusPacket::encode).decoder(StealStatusPacket::decode)
                .consumerMainThread(StealStatusPacket::handle).add();
        CHANNEL.messageBuilder(ValveViewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ValveViewPacket::encode).decoder(ValveViewPacket::decode)
                .consumerMainThread(ValveViewPacket::handle).add();
        CHANNEL.messageBuilder(TankViewPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TankViewPacket::encode).decoder(TankViewPacket::decode)
                .consumerMainThread(TankViewPacket::handle).add();
        CHANNEL.messageBuilder(ValveSetPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ValveSetPacket::encode).decoder(ValveSetPacket::decode)
                .consumerMainThread(ValveSetPacket::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
