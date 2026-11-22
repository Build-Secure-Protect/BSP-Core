package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;

import javax.annotation.Nullable;

/**
 * Draws what a look adds to the bare totem model for a disguised decoy and a Demo Totem: the body and head, the gold state band round
 * the plinth top, and the hat on the head. {@link ShatterTotemRenderer} draws the same parts with its own code (it adds the steal
 * pulse and the leader glow between them): a change to one must be made to the other.
 */
public final class TotemDress {
    private TotemDress() {}

    /**
     * @param body  the look's body, or null for the plain baked body (only a hat then)
     * @param hat   the hat id, or "" for none
     * @param light the light to draw with (taken from the air above, as a totem's look is)
     * @param skinOf whose skin MY HEAD shows, or null for the totem's own face
     */
    public static void draw(PoseStack pose, MultiBufferSource buffers, @Nullable TotemLook body, String hat, Direction facing, int light, float time,
                            @Nullable TotemOwner skinOf) {
        if (body != null) {
            var skin = body.head() == TotemLook.Head.PLAYER && skinOf != null ? PlayerSkins.get(skinOf) : null; // the totem's face until the skin is known
            LookBodyDraw.draw(pose, buffers, body, facing, light, 0, skin);
            ShatterTotemRenderer.band(pose, buffers, 0);
        }
        if (!hat.isEmpty()) {
            HatDraw.draw(pose, buffers, hat, facing, body != null && body.hatTint() ? body.mainColour() : null, light, time);
        }
    }
}
