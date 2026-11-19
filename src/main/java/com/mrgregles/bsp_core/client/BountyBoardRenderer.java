package com.mrgregles.bsp_core.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyBoardBlock;
import com.mrgregles.bsp_core.bounty.BountyBoardBlockEntity;
import com.mrgregles.bsp_core.bounty.BountySummary;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the live parts of the Bounty Board (look A, the Frontier Notice Board) from its controller, in the model's own frame
 * (pixels, front at z 0 facing north, the second column at x 16 to 32; as you face the board, +x is on your left): a poster on each
 * of the four screens from the bounty summary every client has (the target's face, WANTED, their name, the reward, a bar and the
 * time left, a BONUS tag while the bonus runs, and who offered the reward), worth first and paging every few seconds when there
 * are more than four; the burnt WANTED letters on the sign; the key names painted on the shelf plaques; and the lantern's flicker.
 * Light comes from the air in front of the board; the screens and the lantern are full bright.
 */
public class BountyBoardRenderer implements BlockEntityRenderer<BountyBoardBlockEntity> {
    private static final int PAPER = 0xFFECD9AB, AMBER = 0xFFF2B544, MUTE = 0xFFA88D64, RED = 0xFFD0452E, DIM = 0xFF5E4A30, INK = 0xFF2A1608, PAINT = 0xFFF2DDA8;
    private static final int SCREEN_RGB = 0x1A1008, CARD_RGB = 0x24180C, TRACK_RGB = 0x3A2614, AMBER_RGB = 0xF2B544, RED_RGB = 0xD0452E, LANTERN_RGB = 0xFFA040;
    /** The four screens as the generator lays them out: x from, x to, y from, y to (model px), top left first as seen. */
    private static final float[][] SCREENS = {{17.2f, 26.8f, 14.4f, 21.4f}, {5.2f, 14.8f, 14.4f, 21.4f}, {17.2f, 26.8f, 5.0f, 12.0f}, {5.2f, 14.8f, 5.0f, 12.0f}};
    /** The recess back is at z 6.8 and the bezel front at 6.0: the text is drawn at 6.3 and each layer under it about 0.1 px deeper, so nothing fights for depth at a distance. */
    private static final float SCREEN_Z = 6.30f;
    /** Canvas units per model pixel on a poster: a 9.6 x 7 px screen is 115 x 84 units. */
    private static final float UNITS = 12f;
    private static final int PAGE_TICKS = 120;
    private static final int[] TIER_COLOURS = {0xFFD27D46, 0xFFF5C542, 0xFF5FE3E3, 0xFF9C8A92, 0xFFC08CFF};

    /** Players' profiles with their skin filled in, looked up once each (online players come from the tab list as they are). */
    private static final Map<UUID, GameProfile> PROFILES = new ConcurrentHashMap<>();

    private final Font font;

    public BountyBoardRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public void render(BountyBoardBlockEntity board, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (board.getLevel() == null || !board.getBlockState().hasProperty(BountyBoardBlock.FACING)) {
            return;
        }
        Direction facing = board.getBlockState().getValue(BountyBoardBlock.FACING);
        int lit = LevelRenderer.getLightColor(board.getLevel(), board.getBlockPos().relative(facing));
        long ticks = board.getLevel().getGameTime();
        float t = (ticks & 0xFFFFF) + partial;
        MeshDraw d = new MeshDraw(buffers);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);   // model pixels from here on

        List<BountySummary.Entry> all = ClientBounties.entries();
        int pages = Math.max(1, (all.size() + 3) / 4);
        int page = (int) ((ticks / PAGE_TICKS) % pages);
        long now = ClientBounties.now();
        for (int i = 0; i < 4; i++) {
            int at = page * 4 + i;
            poster(d, pose, buffers, SCREENS[i], at < all.size() ? all.get(at) : null, now, t, i);
        }
        sign(pose, buffers, lit);
        keys(pose, buffers, lit);

        // the lantern's flame: a flickering glow inside the baked glass
        float flicker = 0.78f + 0.12f * Mth.sin(t * 0.9f) + 0.07f * Mth.sin(t * 2.3f + 1.7f);
        d.glow(pose, 0.35f, 19.25f, 1.35f, 2.65f, 22.95f, 3.65f, LANTERN_RGB, flicker); // just over the baked glass, so the flicker shows
        pose.popPose();
    }

    // ------------------------------------------------------------------ the posters

    /**
     * One poster on a screen. The canvas origin is the screen's top left as seen from the front, x to the viewer's right and y down,
     * {@link #UNITS} units to a model pixel.
     */
    private void poster(MeshDraw d, PoseStack pose, MultiBufferSource buffers, float[] s, BountySummary.Entry e, long now, float t, int slot) {
        float w = (s[1] - s[0]) * UNITS, h = (s[3] - s[2]) * UNITS;
        pose.pushPose();
        pose.translate(s[1], s[3], SCREEN_Z);
        pose.mulPose(Axis.YP.rotationDegrees(180));   // reads from the front (north)
        pose.scale(1 / UNITS, -1 / UNITS, 1 / UNITS);
        rect(d, pose, 0, 0, w, h, SCREEN_RGB, -5.4f);
        if (e == null) {
            // no bounty for this screen: a dim slow scan line
            float y = (t * 0.6f + slot * 21) % h;
            rect(d, pose, 2, y, w - 2, y + 1, 0x2A1C10, -4.2f);
            centre(pose, buffers, Component.translatable("gui.bsp_core.bounty.none"), w / 2, h / 2 - 3, 0.7f, DIM, w - 8);
            pose.popPose();
            return;
        }
        rect(d, pose, 1, 1, w - 1, h - 1, CARD_RGB, -4.2f);
        centre(pose, buffers, Component.translatable("gui.bsp_core.bounty.wanted"), w / 2, 3, 1.15f, AMBER, w - 30);
        if (e.bonus() > 0 && now < e.bonusEndsMs()) {
            rect(d, pose, w - 25, 2, w - 2, 10, RED_RGB, -3.0f);
            centre(pose, buffers, Component.translatable("gui.bsp_core.bounty.bonus"), w - 13.5f, 3.2f, 0.6f, 0xFFFFE0C0, 21);
        }
        // the target's face
        float fs = 26, fx = w / 2 - fs / 2, fy = 14;
        rect(d, pose, fx - 1, fy - 1, fx + fs + 1, fy + fs + 1, 0x3B2412, -3.0f);
        face(pose, buffers, e.target(), e.targetName(), fx, fy, fs);
        centre(pose, buffers, Component.literal(e.targetName()), w / 2, 42, 0.75f, PAPER, w - 6);
        int rewardColour = e.coinTier() == Bounty.PIECES ? AMBER : TIER_COLOURS[Mth.clamp(e.coinTier(), 0, TIER_COLOURS.length - 1)];
        centre(pose, buffers, Component.literal(reward(e)), w / 2, 51, 1.0f, rewardColour, w - 6);
        // time left: a bar that empties, red in the last day
        long left = e.endsMs() - now, total = Math.max(1, e.endsMs() - e.placedMs());
        float f = Mth.clamp(left / (float) total, 0, 1);
        rect(d, pose, 6, 62, w - 6, 64, TRACK_RGB, -3.0f);
        rect(d, pose, 6, 62, 6 + (w - 12) * f, 64, left < 24 * 3_600_000L ? RED_RGB : AMBER_RGB, -1.8f);
        centre(pose, buffers, ClientBounties.left(left), w / 2, 66, 0.6f, MUTE, w - 6);
        centre(pose, buffers, Component.translatable("gui.bsp_core.bounty.by", e.placerName()), w / 2, 74, 0.55f, DIM, w - 6);
        pose.popPose();
    }

    static String reward(BountySummary.Entry e) {
        if (e.coinTier() == Bounty.PIECES) {
            return com.mrgregles.bsp_core.bank.Bank.format(e.amount());
        }
        CoinTier tier = CoinTier.byOrdinal(e.coinTier());
        return e.amount() + " " + (tier == null ? "?" : com.mrgregles.bsp_core.bank.Bank.tierName(tier));
    }

    /** A flat full-bright rectangle on the canvas, {@code z} units toward the screen (negative is behind the text). */
    private static void rect(MeshDraw d, PoseStack pose, float x0, float y0, float x1, float y1, int rgb, float z) {
        d.glowQuad(pose, new float[]{x0, y0, z}, new float[]{x0, y1, z}, new float[]{x1, y1, z}, new float[]{x1, y0, z}, rgb, 255);
    }

    /** The front of a player's head (and its hat layer) from their skin, {@code size} units square with its top left at (x, y). */
    private static void face(PoseStack pose, MultiBufferSource buffers, UUID id, String name, float x, float y, float size) {
        RenderType type = SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER, profile(id, name));
        VertexConsumer vc = buffers.getBuffer(type);
        skinQuad(pose, vc, x, y, size, 8 / 64f, 8 / 64f, -1.8f);
        skinQuad(pose, vc, x, y, size, 40 / 64f, 8 / 64f, -1.2f);
    }

    private static void skinQuad(PoseStack pose, VertexConsumer vc, float x, float y, float size, float u0, float v0, float z) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float u1 = u0 + 8 / 64f, v1 = v0 + 8 / 64f;
        float[][] p = {{x, y}, {x, y + size}, {x + size, y + size}, {x + size, y}};
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int k = 0; k < 4; k++) {
            vc.vertex(m, p[k][0], p[k][1], z).color(255, 255, 255, 255).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, -1).endVertex();
        }
        for (int k = 3; k >= 0; k--) {
            vc.vertex(m, p[k][0], p[k][1], z).color(255, 255, 255, 255).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
        }
    }

    /** The profile to draw a player's skin from: an online player's from the tab list, else one looked up once (Steve or Alex until it comes). */
    static GameProfile profile(UUID id, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getProfile();
            }
        }
        GameProfile known = PROFILES.get(id);
        if (known != null) {
            return known;
        }
        GameProfile bare = new GameProfile(id, name);
        PROFILES.put(id, bare);
        SkullBlockEntity.updateGameprofile(bare, filled -> {
            if (filled != null) {
                PROFILES.put(id, filled);
            }
        });
        return bare;
    }

    // ------------------------------------------------------------------ the sign and the keys

    /** WANTED burnt into the sign board (x 9 to 23, y 23.2 to 26.8, its face at z 6.2), lit by the world. */
    private void sign(PoseStack pose, MultiBufferSource buffers, int lit) {
        pose.pushPose();
        pose.translate(23, 26.8f, 6.1f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(1 / UNITS, -1 / UNITS, 1 / UNITS);
        Component c = Component.translatable("gui.bsp_core.bounty.wanted");
        float k = Math.min((14 - 2) * UNITS / font.width(c), 3.2f * UNITS / 8f);
        pose.pushPose();
        pose.translate(7 * UNITS - font.width(c) * k / 2, 1.8f * UNITS - 4 * k, 0);
        pose.scale(k, k, 1);
        font.drawInBatch(c, 0, 0, INK, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, lit);
        pose.popPose();
        pose.popPose();
    }

    /** The key names painted on the shelf plaques (faces at z 2, y 0.6 to 3), lit by the world. */
    private void keys(PoseStack pose, MultiBufferSource buffers, int lit) {
        for (BountyBoardBlock.Key key : BountyBoardBlock.Key.values()) {
            float cx = (float) (key.x0 + key.x1) / 2, w = (float) (key.x1 - key.x0) - 0.8f;
            pose.pushPose();
            pose.translate(cx, 1.8f, 1.9f);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            Component c = Component.translatable("gui.bsp_core.bounty.key." + key.name().toLowerCase(java.util.Locale.ROOT));
            float k = Math.min(w / font.width(c), 1.5f / 8f);
            pose.scale(k, -k, k);
            font.drawInBatch(c, -font.width(c) / 2f, -4, PAINT, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, lit);
            pose.popPose();
        }
    }

    /** A line centred on canvas x {@code cx} at canvas y {@code y}, {@code k} times the font's size, shrunk to {@code maxW}. */
    private void centre(PoseStack pose, MultiBufferSource buffers, Component c, float cx, float y, float k, int colour, float maxW) {
        float w = font.width(c);
        if (w * k > maxW) {
            k = maxW / w;
        }
        pose.pushPose();
        pose.translate(cx - w * k / 2, y, 0);
        pose.scale(k, k, 1f);
        font.drawInBatch(c, 0, 0, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(BountyBoardBlockEntity board) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
