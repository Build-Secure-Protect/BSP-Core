package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** Places a Remote Control Transmitter, refusing one over the owner's limit on this server ({@code transmittersPerPlayer}). */
public class RemoteTransmitterBlockItem extends BlockItem {
    public RemoteTransmitterBlockItem(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer p && overLimit(p)) {
            p.displayClientMessage(Component.translatable("message.bsp_core.transmitter.limit", BSPConfig.getOr(BSPConfig.TRANSMITTER_MAX_PER_PLAYER, 4))
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        return super.place(ctx);
    }

    /** Whether the player already has as many transmitters here as they may. */
    public static boolean overLimit(ServerPlayer p) {
        int max = BSPConfig.getOr(BSPConfig.TRANSMITTER_MAX_PER_PLAYER, 4);
        return max > 0 && TransmitterLedger.get(p.server).of(p.server, p.getUUID()).size() >= max;
    }
}
