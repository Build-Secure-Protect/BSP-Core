package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.chamber.ChamberSpawner;
import com.mrgregles.bsp_core.chamber.MobPowers;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Client → server, from a Mob Chamber's CHAMBER tab, for the tank whose block is at {@code pos}: set the ALL / HOSTILE / PASSIVE switch
 * ({@code value} the mode), feed a drop ({@code item} at level {@code value}), or stop feeding. Taken only from a player whose screen is
 * open on that tank ({@link TankStructure#mayUse}); the tank's screen is sent again after.
 */
public record ChamberActionPacket(BlockPos pos, int action, int value, String item) {
    public static final int MODE = 0, FEED = 1, FEED_OFF = 2;

    public static void encode(ChamberActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.action);
        buf.writeVarInt(msg.value);
        buf.writeUtf(msg.item, 256);
    }

    public static ChamberActionPacket decode(FriendlyByteBuf buf) {
        return new ChamberActionPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
    }

    public static void handle(ChamberActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        // only from a screen opened by right-clicking the tank (where protection mods have their say), near the tank
        if (player != null && player.level().isLoaded(msg.pos) && player.level().getBlockEntity(msg.pos) instanceof TankPartBlockEntity part) {
            TankPartBlockEntity m = part.masterEntity();
            if (m != null && m.chamber() && TankStructure.mayUse(player, m)) {
                apply(m, msg.action, msg.value, msg.item);
                TankStructure.sendView(player, msg.pos);
            }
        }
        ctx.get().setPacketHandled(true);
    }

    /** Applies an action to the chamber master {@code m}; returns false if it was not valid (a drop with no power, a mode out of range). */
    public static boolean apply(TankPartBlockEntity m, int action, int value, String itemId) {
        switch (action) {
            case MODE -> {
                var modes = ChamberSpawner.Mode.values();
                if (value < 0 || value >= modes.length) {
                    return false;
                }
                m.setChamberMode(modes[value]);
                return true;
            }
            case FEED -> {
                Item item = item(itemId);
                if (item == null || MobPowers.of(item) == null || value < 1 || value > 3) {
                    return false;
                }
                m.setFeed(item, value);
                return true;
            }
            case FEED_OFF -> {
                m.setFeed(null, m.feedLevel());
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Nullable
    private static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && ForgeRegistries.ITEMS.containsKey(rl) ? ForgeRegistries.ITEMS.getValue(rl) : null;
    }
}
