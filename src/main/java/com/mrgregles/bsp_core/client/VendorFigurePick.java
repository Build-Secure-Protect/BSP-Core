package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.VendorUsePacket;
import com.mrgregles.bsp_core.shop.VendorPostBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Lets players right-click the figure standing on a Vendor Post. The figure is drawn, not spawned,
 * so the game's own ray misses it; on a use click this finds the nearest post whose figure's box
 * the player is looking at, closer than whatever the game hit, and sends the click to the post.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class VendorFigurePick {
    private static final int RANGE = 6;

    private VendorFigurePick() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()) {
            return;
        }
        double reach = player.getBlockReach();
        Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1f).scale(reach));
        double best = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceToSqr(eye) : Double.MAX_VALUE;
        BlockPos found = null;
        BlockPos base = player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-RANGE, -RANGE, -RANGE), base.offset(RANGE, RANGE, RANGE))) {
            if (!(mc.level.getBlockEntity(p) instanceof VendorPostBlockEntity post) || !post.assigned()) {
                continue;
            }
            float h = height(post.mob), w = 0.45f;
            AABB box = new AABB(p.getX() + 0.5 - w, p.getY() + 1, p.getZ() + 0.5 - w, p.getX() + 0.5 + w, p.getY() + 1 + h, p.getZ() + 0.5 + w);
            var hit = box.clip(eye, end);
            if (hit.isPresent()) {
                double d = hit.get().distanceToSqr(eye);
                if (d < best) {
                    best = d;
                    found = p.immutable();
                }
            }
        }
        if (found != null) {
            BSPNetwork.CHANNEL.sendToServer(new VendorUsePacket(found));
            player.swing(InteractionHand.MAIN_HAND);
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    private static float height(String mob) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(mob));
        return type == null ? 2f : Math.max(1f, type.getHeight());
    }
}
