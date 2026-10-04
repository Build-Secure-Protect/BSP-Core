package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.score.ScoreScreenBlock;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import com.mrgregles.bsp_core.score.ScoreService;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a joined Score Screen. Only the bottom-left panel draws; it measures how many panels are
 * joined and lays the whole display out in a canvas of 64 pixels per panel:
 * <ul>
 *   <li><b>Scoreboard</b> in one of three layouts: a ranked list that scrolls when it overflows, a
 *       podium for the top three with everyone else on a ticker, or a spotlight on one player at a time.</li>
 *   <li><b>Scoring rules</b>: what each totem tier is worth and how scores are counted.</li>
 *   <li><b>Season prizes</b>: the items first, second and third place win, as set in the admin panel.</li>
 * </ul>
 * The leaderboard comes from {@link ClientScores}, which the server keeps up to date.
 */
public class ScoreScreenRenderer implements BlockEntityRenderer<ScoreScreenBlockEntity> {
    private static final int PX = 64, BG = 0xFF070A0E, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, SILVER = 0xFF8A909C, TEXT = 0xFFE8EAF0, MUTED = 0xFF9AA3B5, STRIP = 0xFF10151C, DARK = 0xFF0B0D11;
    private static final Style[] FONTS = {Style.EMPTY, Style.EMPTY.withFont(new ResourceLocation("minecraft", "uniform")), Style.EMPTY.withBold(true)};

    private final Font font;
    private PoseStack pose;
    private MultiBufferSource buffers;
    private Style style = Style.EMPTY;

    public ScoreScreenRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    // ------------------------------------------------------------------ drawing helpers (canvas pixels, y down)

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        Matrix4f m = pose.last().pose();
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[][] quad = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        for (int pass = 0; pass < 2; pass++) { // both windings, so it shows whichever way the layer culls
            for (int i = 0; i < 4; i++) {
                float[] p = quad[pass == 0 ? i : 3 - i];
                vc.vertex(m, p[0], p[1], 0).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
            }
        }
    }

    /** Draws text {@code px} pixels tall, anchored at (x, y) vertical centre; shrunk to fit {@code maxWidth} if needed. */
    private void text(String s, float x, float y, float px, int colour, float align, float maxWidth) {
        Component c = Component.literal(s).withStyle(style);
        float w = font.width(c), k = px / 8f;
        if (w * k > maxWidth && w > 0) {
            k = maxWidth / w;
        }
        pose.pushPose();
        pose.translate(x, y, 0.4f);
        pose.scale(k, k, 1f);
        font.drawInBatch(c, -w * align, -4f, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private static String pts(ScoreService.Entry e) {
        return Component.translatable("screen.bsp_core.pts", e.points()).getString();
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(ScoreScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = screen.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = screen.getBlockState().getValue(ScoreScreenBlock.FACING);
        if (!ScoreScreenBlock.origin(level, screen.getBlockPos(), facing).equals(screen.getBlockPos())) {
            return; // another panel of this screen draws it
        }
        int[] wh = ScoreScreenBlock.size(level, screen.getBlockPos(), facing);
        float w = wh[0] * PX, h = wh[1] * PX, t = (level.getGameTime() + partialTick) / 20f;
        this.pose = pose;
        this.buffers = buffers;
        this.style = FONTS[Mth.clamp(screen.font(), 0, FONTS.length - 1)];

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, wh[1] - 0.5, -0.3125 + 0.004); // top-left corner of the joined face, just in front of the panels
        pose.scale(1f / PX, -1f / PX, 1f / PX);

        float u = Math.min(wh[0] / 5f, wh[1] / 2.2f) * screen.size() / 100f, speed = screen.speed() / 100f;
        rect(0, 0, w, h, BG);
        float th = 22 * u, top = th + 6, area = h - top - 4;
        boolean rules = screen.mode() == ScoreScreenBlockEntity.MODE_RULES, prizes = screen.mode() == ScoreScreenBlockEntity.MODE_PRIZES;
        text(rules ? Component.translatable("screen.bsp_core.rules.title").getString()
                : prizes ? Component.translatable("screen.bsp_core.prizes.title", ClientScores.season()).getString() : screen.title(), w / 2, th / 2 + 3, 14 * u, TQ, 0.5f, w - 8);
        pose.translate(0, 0, 0.2f);
        rect(2, th + 3, w - 2, th + 4.5f, TQ);

        List<ScoreService.Entry> board = ClientScores.board();
        if (prizes) {
            prizes(level, w, top, area);
        } else if (rules) {
            rules(w, top, area);
        } else if (board.isEmpty()) {
            text(Component.translatable("screen.bsp_core.empty").getString(), w / 2, top + area / 2, 12 * u, MUTED, 0.5f, w - 12);
        } else if (screen.layout() == ScoreScreenBlockEntity.LAYOUT_PODIUM) {
            podium(board, w, h, top, area, u, t, speed);
        } else if (screen.layout() == ScoreScreenBlockEntity.LAYOUT_SPOTLIGHT) {
            spotlight(board, w, top, area, u, t, speed);
        } else {
            list(board, w, h, top, area, u, t, speed);
        }
        pose.popPose();
    }

    private void list(List<ScoreService.Entry> board, float w, float h, float top, float area, float u, float t, float speed) {
        float rh = 18 * u;
        int n = board.size(), fit = Math.max(1, (int) (area / rh));
        boolean over = n > fit;
        float off = over ? (t * speed * rh * 1.5f) % (n * rh) : 0;
        for (int k = 0; k < n * (over ? 2 : 1); k++) {
            float y = top + k * rh - off + rh / 2;
            if (y < top + rh * 0.45f || y > h - rh * 0.45f) {
                continue; // only whole rows are drawn; there is no clipping on a block face
            }
            ScoreService.Entry e = board.get(k % n);
            int colour = k % n < 3 ? GOLD : TEXT;
            text((k % n + 1) + ". " + e.name(), w * 0.05f, y, 12 * u, colour, 0f, w * 0.62f);
            text(pts(e), w * 0.95f, y, 12 * u, colour, 1f, w * 0.26f);
        }
    }

    private void podium(List<ScoreService.Entry> board, float w, float h, float top, float area, float u, float t, float speed) {
        float tk = 16 * u, ph = area - tk - 6, bw = w / 4.2f;
        float[] heights = {0.62f, 1f, 0.45f};
        int[] order = {1, 0, 2};
        for (int i = 0; i < 3; i++) {
            int rank = order[i];
            if (rank >= board.size()) {
                continue;
            }
            ScoreService.Entry e = board.get(rank);
            float x = w / 2 + (i - 1) * bw * 1.15f, bh = ph * 0.62f * heights[i];
            rect(x - bw / 2, top + ph - bh, x + bw / 2, top + ph, rank == 0 ? GOLD : SILVER);
            text("#" + (rank + 1), x, top + ph - bh / 2, 16 * u, DARK, 0.5f, bw - 4);
            text(e.name(), x, top + ph - bh - 18 * u, 10 * u, TEXT, 0.5f, bw * 1.1f);
            text(pts(e), x, top + ph - bh - 7 * u, 10 * u, TQ, 0.5f, bw * 1.1f);
        }
        rect(0, h - tk - 4, w, h, STRIP);
        // ticker: everyone below the podium, moving right to left; an entry shows only while it is wholly on the screen
        float x = w - (t * speed * 90 * u) % ticker(board, u, w), gap = 24 * u;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 3; i < board.size(); i++) {
                ScoreService.Entry e = board.get(i);
                String s = (i + 1) + ". " + e.name() + "  " + pts(e);
                float sw = font.width(Component.literal(s).withStyle(style)) * (10 * u / 8f);
                if (x >= 2 && x + sw <= w - 2) {
                    text(s, x, h - tk / 2 - 2, 10 * u, MUTED, 0f, w);
                }
                x += sw + gap;
            }
        }
    }

    private float ticker(List<ScoreService.Entry> board, float u, float w) {
        float total = 0;
        for (int i = 3; i < board.size(); i++) {
            ScoreService.Entry e = board.get(i);
            total += font.width(Component.literal((i + 1) + ". " + e.name() + "  " + pts(e)).withStyle(style)) * (10 * u / 8f) + 24 * u;
        }
        return Math.max(w, total);
    }

    private void spotlight(List<ScoreService.Entry> board, float w, float top, float area, float u, float t, float speed) {
        float dur = Math.max(1.2f, 5 - speed * 4);
        int i = (int) (t / dur) % board.size();
        ScoreService.Entry e = board.get(i);
        text("#" + (i + 1), w / 2, top + area * 0.22f, 30 * u, i < 3 ? GOLD : TQ, 0.5f, w - 12);
        text(e.name(), w / 2, top + area * 0.52f, 20 * u, TEXT, 0.5f, w - 12);
        text(Component.translatable("screen.bsp_core.totems", e.totems()).getString() + "   " + pts(e), w / 2, top + area * 0.8f, 11 * u, MUTED, 0.5f, w - 12);
    }

    private static final int[] PLACE = {GOLD, 0xFFC9CED8, 0xFFC87A3C, TQ};

    /** Three rows, one per place: its label, then each prize item with its count underneath. */
    private void prizes(Level level, float w, float top, float area) {
        List<net.minecraft.world.item.ItemStack> all = ClientScores.prizes();
        int rows = 3;
        for (int i = 30; i < all.size(); i++) {
            if (!all.get(i).isEmpty()) {
                rows = 4; // the holder reward row is only shown when something is in it
            }
        }
        float rh = area / rows, labelW = w * 0.16f, cell = Math.min((w - labelW - 6) / 10f, rh * 0.82f), icon = cell * 0.7f;
        net.minecraft.client.renderer.entity.ItemRenderer items = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
        for (int place = 0; place < rows; place++) {
            float cy = top + rh * (place + 0.5f);
            text(Component.translatable("gui.bsp_core.rewards.place." + place).getString(), labelW / 2 + 2, cy, rh * 0.34f, PLACE[place], 0.5f, labelW - 2);
            int shown = 0;
            for (int i = 0; i < 10; i++) {
                int slot = place * 10 + i;
                net.minecraft.world.item.ItemStack stack = slot < all.size() ? all.get(slot) : net.minecraft.world.item.ItemStack.EMPTY;
                if (stack.isEmpty()) {
                    continue;
                }
                float cx = labelW + 4 + cell * (shown++ + 0.5f);
                pose.pushPose();
                pose.translate(cx, cy - cell * 0.1f, 1.2f);
                pose.scale(icon, -icon, icon * 0.02f); // flat against the screen; the canvas has y pointing down
                items.renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
                pose.popPose();
                if (stack.getCount() > 1) {
                    text("x" + stack.getCount(), cx, cy + cell * 0.4f, cell * 0.22f, TEXT, 0.5f, cell);
                }
            }
            if (shown == 0) {
                text(Component.translatable("screen.bsp_core.prizes.none").getString(), labelW + 6, cy, rh * 0.22f, MUTED, 0f, w - labelW - 10);
            }
        }
    }

    private void rules(float w, float top, float area) {
        float lh = area / 8.6f, px = lh * 0.62f;
        for (int tier = 0; tier <= TotemUpgrades.MAX_TIER; tier++) {
            float y = top + lh * (tier + 0.6f);
            text(Component.translatable("screen.bsp_core.rules.tier", TotemUpgrades.roman(tier)).getString(), w * 0.08f, y, px, TEXT, 0f, w * 0.6f);
            text(Component.translatable("screen.bsp_core.pts", ScoreService.tierPoints(tier)).getString(), w * 0.92f, y, px, GOLD, 1f, w * 0.28f);
        }
        String[] notes = {"screen.bsp_core.rules.sum", "screen.bsp_core.rules.count", "screen.bsp_core.rules.tie"};
        for (int i = 0; i < notes.length; i++) {
            text(Component.translatable(notes[i]).getString(), w / 2, top + lh * (5.75f + i), px * 0.78f, MUTED, 0.5f, w - 10);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(ScoreScreenBlockEntity screen) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
