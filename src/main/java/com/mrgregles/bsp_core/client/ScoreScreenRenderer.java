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
import com.mrgregles.bsp_core.api.BspBoards;
import javax.annotation.Nullable;
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
    public static final int PX = 64;
    private static final int BG = 0xFF070A0E, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, SILVER = 0xFF8A909C, TEXT = 0xFFE8EAF0, MUTED = 0xFF9AA3B5, STRIP = 0xFF10151C, DARK = 0xFF0B0D11;
    private static final Style[] FONTS = {Style.EMPTY, Style.EMPTY.withFont(new ResourceLocation("minecraft", "uniform")), Style.EMPTY.withBold(true)};

    private final Font font;
    private PoseStack pose;
    private MultiBufferSource buffers;
    private Style style = Style.EMPTY;

    public ScoreScreenRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    // ------------------------------------------------------------------ drawing helpers (canvas pixels, y down)

    /** Fade of the page being drawn, 0.15 to 1: a rotation's new page fades in. */
    private float alpha = 1f;

    private int faded(int argb) {
        if (alpha >= 1f) {
            return argb;
        }
        int a = Math.max(0x26, Math.round((argb >>> 24) * alpha)); // the font treats a near-zero alpha as opaque, so never go below it
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private void rect(float x0, float y0, float x1, float y1, int argb) {
        argb = faded(argb);
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
        text(Component.literal(s), x, y, px, colour, align, maxWidth);
    }

    private void text(Component raw, float x, float y, float px, int colour, float align, float maxWidth) {
        colour = faded(colour);
        Component c = raw.copy().withStyle(style);
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
        float w = wh[0] * PX, h = wh[1] * PX, t = clock(level, partialTick);
        this.pose = pose;
        this.buffers = buffers;
        this.style = FONTS[Mth.clamp(screen.font(), 0, FONTS.length - 1)];

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, wh[1] - 0.5, -0.3125 + 0.004); // top-left corner of the joined face, just in front of the panels
        pose.scale(1f / PX, -1f / PX, 1f / PX);

        float u = unit(screen, wh[0], wh[1]), speed = screen.speed() / 100f;
        rect(0, 0, w, h, BG);
        float th = 22 * u, top = th + 6, area = h - top - 4;
        // which page: a fixed mode, or the rotation's page for this moment
        Shown shown = shown(screen, t);
        String page = shown.page();
        int pageIndex = shown.index(), pageCount = shown.count();
        alpha = shown.alpha();
        boolean side = screen.look() == ScoreScreenBlockEntity.LOOK_SIDE;
        boolean rules = page.equals(ScoreScreenBlockEntity.PAGE_RULES), prizes = page.equals(ScoreScreenBlockEntity.PAGE_PRIZES), scoreboard = page.equals(ScoreScreenBlockEntity.PAGE_SCOREBOARD);
        BspBoards.Board modBoard = rules || prizes || scoreboard ? null : boardOf(page);
        boolean sideBoard = side && !rules && !prizes && (modBoard != null || screen.mode() == ScoreScreenBlockEntity.MODE_ROTATE || screen.mode() == ScoreScreenBlockEntity.MODE_PAGE);
        if (modBoard != null && !sideBoard && modBoard.id().equals(ClientVips.BOARD)) {
            // the VIPs board draws its own banner in place of the title strip
            pose.translate(0, 0, 0.2f);
            vipBoard(screen, modBoard, w, h, u, t, speed, screen.mode() == ScoreScreenBlockEntity.MODE_ROTATE && pageCount > 1 ? pageIndex : -1, pageCount);
            alpha = 1f;
            pose.popPose();
            return;
        }
        float keep = alpha;
        alpha = 1f; // the strip never fades
        Component heading = rules ? Component.translatable("screen.bsp_core.rules.title")
                : prizes ? Component.translatable("screen.bsp_core.prizes.title", ClientScores.season())
                : modBoard != null && !sideBoard ? modBoard.title() : Component.literal(screen.title());
        text(heading, w / 2, th / 2 + 3, 14 * u, TQ, 0.5f, w - 8);
        pose.translate(0, 0, 0.2f);
        rect(2, th + 3, w - 2, th + 4.5f, TQ);
        if (screen.mode() == ScoreScreenBlockEntity.MODE_ROTATE && pageCount > 1 && !sideBoard) {
            dots(w, th + 7.5f, u, pageIndex, pageCount);
            top += 5 * u;
            area -= 5 * u;
        }
        alpha = keep;

        List<ScoreService.Entry> board = ClientScores.board();
        if (prizes) {
            prizes(level, w, top, area);
        } else if (rules) {
            rules(w, top, area);
        } else if (sideBoard) {
            sideColumn(level, screen, board, modBoard, page, w, h, top, area, u, t, speed, pageIndex, pageCount);
        } else if (modBoard != null || !scoreboard) {
            boardPage(level, modBoard, page, screen.look(), w, h, top, area, u, t, speed);
        } else if (board.isEmpty()) {
            text(Component.translatable("screen.bsp_core.empty").getString(), w / 2, top + area / 2, 12 * u, MUTED, 0.5f, w - 12);
        } else if (screen.layout() == ScoreScreenBlockEntity.LAYOUT_PODIUM) {
            podium(board, w, h, top, area, u, t, speed);
        } else if (screen.layout() == ScoreScreenBlockEntity.LAYOUT_SPOTLIGHT) {
            spotlight(board, w, top, area, u, t, speed);
        } else {
            list(board, w, h, top, area, u, t, speed);
        }
        alpha = 1f;
        pose.popPose();
    }

    // ------------------------------------------------------------------ which page is shown

    /** The page a screen shows at time {@code t} (seconds), its place in the rotation, and how far its fade-in has come. */
    public record Shown(String page, int index, int count, float alpha) {}

    public static Shown shown(ScoreScreenBlockEntity screen, float t) {
        if (screen.mode() == ScoreScreenBlockEntity.MODE_ROTATE) {
            List<String> pages = rotation(screen, screen.look() == ScoreScreenBlockEntity.LOOK_SIDE);
            int count = Math.max(1, pages.size()), secs = Math.max(1, screen.rotateSeconds());
            int index = (int) ((long) (t / secs) % count);
            return new Shown(pages.isEmpty() ? ScoreScreenBlockEntity.PAGE_SCOREBOARD : pages.get(index), index, count, Mth.clamp((t % secs) / 0.5f, 0.15f, 1f));
        }
        String page = screen.mode() == ScoreScreenBlockEntity.MODE_RULES ? ScoreScreenBlockEntity.PAGE_RULES
                : screen.mode() == ScoreScreenBlockEntity.MODE_PRIZES ? ScoreScreenBlockEntity.PAGE_PRIZES
                : screen.mode() == ScoreScreenBlockEntity.MODE_PAGE ? screen.boardId() : ScoreScreenBlockEntity.PAGE_SCOREBOARD;
        return new Shown(page, 0, 1, 1f);
    }

    /** The screen's clock in seconds, as the renderer reads it: the world's ticks wrapped every ~14.5 hours so a float keeps its fractions. */
    public static float clock(Level level, float partialTick) {
        return ((level.getGameTime() & 0xFFFFF) + partialTick) / 20f;
    }

    /** Text unit of a {@code panelsW} by {@code panelsH} screen at the screen's size setting. */
    public static float unit(ScoreScreenBlockEntity screen, int panelsW, int panelsH) {
        return Math.min(panelsW / 5f, panelsH / 2.2f) * screen.size() / 100f;
    }

    /** Whether the screen shows the whole VIPs board at time {@code t} (not as the Side look's column). */
    public static boolean showsVips(ScoreScreenBlockEntity screen, float t) {
        return screen.look() != ScoreScreenBlockEntity.LOOK_SIDE && shown(screen, t).page().equals(ScoreScreenBlockEntity.PAGE_VIPS)
                && BspBoards.board(ClientVips.BOARD).isPresent();
    }

    /** The VIPs board's layout on this screen, with the screen's font: what is drawn and what a click is tested against. */
    public static VipBoardLayout vipLayout(Font font, ScoreScreenBlockEntity screen, Component title, float w, float h, float u, boolean dots) {
        return VipBoardLayout.of(font, FONTS[Mth.clamp(screen.font(), 0, FONTS.length - 1)], screen.vip(), title, w, h, u, dots);
    }

    // ------------------------------------------------------------------ the VIPs board (look B, owner's pick 2026-11-22)

    private static final int VIP_PURPLE = 0xFF3B1A5C, VIP_PURPLE_L = 0xFF5B2A86, VIP_GOLD_D = 0xFFA8741A, VIP_FOOT = 0xFF140F1D;

    /**
     * The VIPs board: the admin's top message on a purple banner with gold edges (the board's title when there is none), rows of
     * heads in gold frames with the names under them sliding sideways when a row is wider than the screen (each row half a head
     * behind the one above), and a footer with the bottom message and the SUPPORT key. Only the heads move.
     */
    private void vipBoard(ScoreScreenBlockEntity screen, BspBoards.Board b, float w, float h, float u, float t, float speed, int dotIndex, int dotCount) {
        VipBoardLayout l = vipLayout(font, screen, b.title(), w, h, u, dotIndex >= 0);
        // banner: edges and fill side by side, never stacked, so nothing fights for depth at a distance; the trim one layer up
        float bb = l.bannerBottom();
        rect(2, 2, w - 2, 3, GOLD);
        rect(2, 3, w - 2, 4, VIP_PURPLE_L);
        rect(2, 4, w - 2, bb - 1, VIP_PURPLE);
        rect(2, bb - 1, w - 2, bb, GOLD);
        pose.pushPose();
        pose.translate(0, 0, LAYER);
        for (float x = 8 * u; x < w - 8 * u; x += 10 * u) {
            rect(x, bb - 3.2f, x + 2 * u, bb - 2.4f, VIP_GOLD_D);
        }
        pose.popPose();
        float lh = l.topPx() * VipBoardLayout.LEADING;
        for (int i = 0; i < l.topLines().size(); i++) {
            text(l.topLines().get(i), w / 2, 2 + 4 * u + i * lh + lh / 2, l.topPx(), GOLD, 0.5f, w - 8);
        }
        if (dotIndex >= 0) {
            dots(w, l.dotsY(), u, dotIndex, dotCount);
        }

        // footer: its line and fill side by side; the key and its shadow one layer up, side by side
        float ft = l.footTop();
        if (ft < h - 2) {
            rect(2, ft, w - 2, ft + 1, VIP_GOLD_D);
            rect(2, ft + 1, w - 2, h - 2, VIP_FOOT);
            float[] k = l.key();
            if (k != null) {
                pose.pushPose();
                pose.translate(0, 0, LAYER);
                rect(k[0], k[1], k[2], k[3], GOLD);
                rect(k[0], k[3], k[2], k[3] + 1.2f * u, VIP_GOLD_D);
                pose.popPose();
                text(l.keyText(), (k[0] + k[2]) / 2, (k[1] + k[3]) / 2 + 0.5f, l.keyPx(), VIP_PURPLE, 0.5f, k[2] - k[0] - 4 * u);
            }
            float bh = l.bottomPx() * VipBoardLayout.LEADING, by = ft + (h - 2 - ft - l.bottomLines().size() * bh) / 2;
            for (int i = 0; i < l.bottomLines().size(); i++) {
                if (k != null) {
                    text(l.bottomLines().get(i), 6 * u, by + i * bh + bh / 2, l.bottomPx(), TEXT, 0f, VipBoardLayout.messageWidth(w, k, u));
                } else {
                    text(l.bottomLines().get(i), w / 2, by + i * bh + bh / 2, l.bottomPx(), TEXT, 0.5f, VipBoardLayout.messageWidth(w, null, u));
                }
            }
        }

        // the heads
        List<com.mrgregles.bsp_core.score.VipList.Vip> vips = ClientVips.vips();
        float band = l.tilesBottom() - l.tilesTop();
        if (vips.isEmpty()) {
            if (band > 6 * u) {
                text(Component.translatable("screen.bsp_core.vips.none"), w / 2, l.tilesTop() + band / 2, 10 * u, MUTED, 0.5f, w - 12);
            }
            return;
        }
        int rows = Math.min(l.rows(), vips.size());
        if (rows < 1) {
            return;
        }
        float ts = l.tile(), tw = ts + 16 * u, y0 = l.tilesTop() + (band - rows * l.rowH()) / 2 + 2 * u, left = 4, right = w - 4;
        for (int r = 0; r < rows; r++) {
            List<com.mrgregles.bsp_core.score.VipList.Vip> row = new java.util.ArrayList<>();
            for (int i = r; i < vips.size(); i += rows) {
                row.add(vips.get(i));
            }
            float total = row.size() * tw, ry = y0 + r * l.rowH();
            if (total <= right - left) {
                float x0 = (w - total) / 2;
                for (int i = 0; i < row.size(); i++) {
                    vipTile(row.get(i), x0 + i * tw, ry, ts, tw, u, left, right, false);
                }
                continue;
            }
            float off = ((t * speed * 16 * u + r * tw / 2) % total + total) % total;
            for (int k = 0; left - off + k * total < right; k++) {
                for (int i = 0; i < row.size(); i++) {
                    float x = left - off + k * total + i * tw;
                    if (x + tw > left && x < right) {
                        vipTile(row.get(i), x, ry, ts, tw, u, left, right, true);
                    }
                }
            }
        }
    }

    /** Depth between stacked layers of the board: smaller steps flicker on a screen seen from far off. */
    private static final float LAYER = 0.2f;

    /**
     * One VIP: a gold frame round their face, cut at the band's ends, and their name under it. On a sliding row the name fades out
     * near the ends; on a still row it is always whole.
     */
    private void vipTile(com.mrgregles.bsp_core.score.VipList.Vip v, float x, float y, float ts, float tw, float u, float left, float right, boolean sliding) {
        float fx = x + (tw - ts) / 2, inset = 2 * u;
        // the frame as four strips round the purple square, side by side
        cropRect(fx - 1, y - 1, fx + ts + 1, y, GOLD, left, right);
        cropRect(fx - 1, y + ts, fx + ts + 1, y + ts + 1, GOLD, left, right);
        cropRect(fx - 1, y, fx, y + ts, GOLD, left, right);
        cropRect(fx + ts, y, fx + ts + 1, y + ts, GOLD, left, right);
        cropRect(fx, y, fx + ts, y + ts, VIP_PURPLE, left, right);
        face(v, fx + inset, y + inset, ts - 2 * inset, left, right);
        Component name = Component.literal(v.name()).withStyle(style);
        float px = 7 * u, a = 1f;
        if (sliding) {
            float nw = Math.min(font.width(name) * px / 8f, tw - 2 * u), cx = x + tw / 2;
            float room = Math.min(cx - nw / 2 - left, right - (cx + nw / 2));
            a = Mth.clamp(room / (8 * u), 0f, 1f);
            if (a < 0.15f) {
                return;
            }
        }
        float keep = alpha;
        alpha = keep * a;
        text(name, x + tw / 2, y + ts + 1.5f * u + px / 2, px, TEXT, 0.5f, tw - 2 * u);
        alpha = keep;
    }

    /** A rectangle cut to the band between {@code left} and {@code right}. */
    private void cropRect(float x0, float y0, float x1, float y1, int argb, float left, float right) {
        float a = Math.max(x0, left), b = Math.min(x1, right);
        if (b > a) {
            rect(a, y0, b, y1, argb);
        }
    }

    /** The front of a VIP's head and its hat layer, {@code size} square, cut to the band (the skin's picture is cut with it). */
    private void face(com.mrgregles.bsp_core.score.VipList.Vip v, float x, float y, float size, float left, float right) {
        float a = Math.max(x, left), b = Math.min(x + size, right);
        if (b <= a) {
            return;
        }
        RenderType type = net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,
                BountyBoardRenderer.profile(v.id(), v.name()));
        VertexConsumer vc = buffers.getBuffer(type);
        float s0 = (a - x) / size, s1 = (b - x) / size;
        skinQuad(vc, a, y, b, y + size, 8 / 64f + s0 * 8 / 64f, 8 / 64f + s1 * 8 / 64f, 8 / 64f, 2 * LAYER);
        skinQuad(vc, a, y, b, y + size, 40 / 64f + s0 * 8 / 64f, 40 / 64f + s1 * 8 / 64f, 8 / 64f, 4 * LAYER);
    }

    private void skinQuad(VertexConsumer vc, float x0, float y0, float x1, float y1, float u0, float u1, float v0, float z) {
        Matrix4f m = pose.last().pose();
        org.joml.Matrix3f n = pose.last().normal();
        float v1 = v0 + 8 / 64f;
        int a = Math.round(255 * Mth.clamp(alpha, 0f, 1f));
        float[][] p = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int pass = 0; pass < 2; pass++) { // both windings, as the rectangles
            for (int i = 0; i < 4; i++) {
                int k = pass == 0 ? i : 3 - i;
                vc.vertex(m, p[k][0], p[k][1], z).color(255, 255, 255, a).uv(uv[k][0], uv[k][1]).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT).normal(n, 0, -1, 0).endVertex(); // canvas -y is world up: lit like a top face, the same on every facing
            }
        }
    }

    private void text(net.minecraft.util.FormattedCharSequence seq, float x, float y, float px, int colour, float align, float maxWidth) {
        colour = faded(colour);
        float w = font.width(seq), k = px / 8f;
        if (w * k > maxWidth && w > 0) {
            k = maxWidth / w;
        }
        pose.pushPose();
        pose.translate(x, y, 0.4f);
        pose.scale(k, k, 1f);
        font.drawInBatch(seq, -w * align, -4f, colour, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // ------------------------------------------------------------------ other mods' boards (BSP-Teleport R8)

    /** Boards this client found missing: shown once as "not installed here", then left out of rotations. */
    private static final java.util.Set<String> MISSING_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Nullable
    private static BspBoards.Board boardOf(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : BspBoards.board(rl).orElse(null);
    }

    /** The rotation's pages: the saved list or the default, less the boards this client has already found missing; with the side look, boards only. */
    private static List<String> rotation(ScoreScreenBlockEntity screen, boolean side) {
        List<String> source = screen.pages();
        List<String> out = new java.util.ArrayList<>();
        if (source.isEmpty()) {
            out.add(ScoreScreenBlockEntity.PAGE_SCOREBOARD);
            for (BspBoards.Board b : BspBoards.boards()) {
                if (ScoreScreenBlockEntity.inDefaultRotation(b.id().toString())) {
                    out.add(b.id().toString());
                }
            }
        } else {
            out.addAll(source);
        }
        out.removeIf(id -> {
            boolean builtin = id.equals(ScoreScreenBlockEntity.PAGE_SCOREBOARD) || id.equals(ScoreScreenBlockEntity.PAGE_RULES) || id.equals(ScoreScreenBlockEntity.PAGE_PRIZES);
            if (side) {
                return builtin || MISSING_SEEN.contains(id);
            }
            return !builtin && MISSING_SEEN.contains(id);
        });
        return out;
    }

    private void dots(float w, float y, float u, int current, int count) {
        float d = 3 * u, gap = 3 * u, total = count * d + (count - 1) * gap, x = w / 2 - total / 2;
        for (int i = 0; i < count; i++) {
            float cx = x + i * (d + gap);
            rect(cx, y, cx + d, y + d, i == current ? TQ : DARK);
        }
    }

    private void item(Level level, net.minecraft.world.item.ItemStack stack, float cx, float cy, float size) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        net.minecraft.client.renderer.entity.ItemRenderer items = net.minecraft.client.Minecraft.getInstance().getItemRenderer();
        pose.pushPose();
        pose.translate(cx, cy, 1.2f);
        pose.scale(size, -size, size * 0.02f);
        items.renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

    private void missing(String id, float w, float top, float area, float u) {
        MISSING_SEEN.add(id);
        text(Component.translatable("screen.bsp_core.board.missing", id), w / 2, top + area / 2, 11 * u, MUTED, 0.5f, w - 12);
    }

    /** A board's page in the Ledger or Tiles look. */
    private void boardPage(Level level, @Nullable BspBoards.Board b, String id, int look, float w, float h, float top, float area, float u, float t, float speed) {
        if (b == null) {
            missing(id, w, top, area, u);
            return;
        }
        if (b.id().equals(ClientLottery.BOARD)) {
            lotteryBoard(w, top, area, u, t);
            return;
        }
        List<BspBoards.BoardLine> lines = BspBoards.lines(b.id());
        if (lines.isEmpty()) {
            text(Component.translatable("screen.bsp_core.board.empty").getString(), w / 2, top + area / 2, 11 * u, MUTED, 0.5f, w - 12);
            return;
        }
        if (look == ScoreScreenBlockEntity.LOOK_TILES) {
            tiles(level, lines, w, top, area, u, t, speed);
            return;
        }
        if (look == ScoreScreenBlockEntity.LOOK_SPOTLIGHT) {
            spotlight(level, lines, w, h, top, area, u, t, speed);
            return;
        }
        ledger(level, lines, w * 0.04f, w * 0.96f, h, top, area, u, t, speed, 16 * u, true);
    }

    /**
     * The Lottery board (look B, owner's pick 2026-10-28), whatever the screen's look: a drum of the names of everyone in rolling
     * slowly at the left, the middle name gold between two arrows; it spins while a draw counts down and stands on the winner
     * after. At the right the pot, how many are in (or the count, or the winner) and the last three winners.
     */
    private void lotteryBoard(float w, float top, float area, float u, float t) {
        com.mrgregles.bsp_core.lottery.LotterySummary.Summary s = ClientLottery.summary();
        com.mrgregles.bsp_core.lottery.LotteryDraw.Phase[] phases = com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.values();
        var phase = s.phase() >= 0 && s.phase() < phases.length ? phases[s.phase()] : com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.IDLE;
        float dx = w * 0.03f, dw = w * 0.38f, dy = top + 1, dh = area - 2, rh = 13 * u, mid = dy + dh / 2;
        rect(dx, dy, dx + dw, dy + 1, STRIP);
        rect(dx, dy + dh - 1, dx + dw, dy + dh, STRIP);
        rect(dx, dy, dx + 1, dy + dh, STRIP);
        rect(dx + dw - 1, dy, dx + dw, dy + dh, STRIP);
        List<String> names = s.names();
        if (names.isEmpty()) {
            text(Component.translatable("screen.bsp_core.lottery.nobody"), dx + dw / 2, mid, 8 * u, MUTED, 0.5f, dw - 8);
        } else {
            float total = names.size() * rh;
            int at = names.indexOf(s.winner());
            float roll = phase == com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.WON && at >= 0 ? at * rh
                    : t * (phase == com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.COUNTING ? 200 : 8) * u;
            float off = ((roll % total) + total) % total;
            int copies = (int) (dh / total) + 2; // enough copies of a short list to fill the drum
            for (int k = -copies; k <= copies; k++) {
                for (int i = 0; i < names.size(); i++) {
                    float y = mid + i * rh + k * total - off;
                    if (y < dy + rh * 0.55f || y > dy + dh - rh * 0.55f) {
                        continue;
                    }
                    float d = Math.abs(y - mid);
                    text(names.get(i), dx + dw / 2, y, rh * 0.7f, d < rh / 2 ? GOLD : d < rh * 2 ? TEXT : MUTED, 0.5f, dw - 30 * u); // clear of the arrows
                }
            }
            text("\u25B6", dx + 3 * u, mid, rh * 0.6f, GOLD, 0f, 10 * u);
            text("\u25C0", dx + dw - 3 * u, mid, rh * 0.6f, GOLD, 1f, 10 * u);
        }
        float rx = dx + dw + 8 * u, rw = w - rx - w * 0.03f;
        text(Component.translatable("screen.bsp_core.lottery.pot"), rx, top + 5 * u, 7 * u, MUTED, 0f, rw);
        text(com.mrgregles.bsp_core.bank.Bank.format(s.pot()), rx, top + 18 * u, 20 * u, GOLD, 0f, rw);
        if (phase == com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.COUNTING) {
            long n = Math.max(1, 5 - Math.max(0, ClientLottery.phaseAge() / 1000 - 1));
            text(Component.translatable("screen.bsp_core.lottery.drawing_in", n), rx, top + 33 * u, 9 * u, GOLD, 0f, rw);
        } else if (phase == com.mrgregles.bsp_core.lottery.LotteryDraw.Phase.WON) {
            text(Component.translatable("screen.bsp_core.lottery.won", s.winner(), com.mrgregles.bsp_core.bank.Bank.format(s.winPieces())), rx, top + 33 * u, 9 * u, GOLD, 0f, rw);
        } else {
            text(Component.translatable("screen.bsp_core.lottery.players_in", s.in()), rx, top + 33 * u, 9 * u, TEXT, 0f, rw);
        }
        text(Component.translatable("screen.bsp_core.lottery.winners"), rx, top + 47 * u, 7 * u, MUTED, 0f, rw);
        if (s.wins().isEmpty()) {
            text(Component.translatable("screen.bsp_core.lottery.no_winners"), rx, top + 58 * u, 8 * u, MUTED, 0f, rw);
        }
        for (int i = 0; i < s.wins().size(); i++) {
            var win = s.wins().get(i);
            float y = top + 58 * u + i * 11 * u;
            if (y > top + area - 4 * u) {
                break;
            }
            String amount = com.mrgregles.bsp_core.bank.Bank.format(win.pieces());
            float aw = Math.min(rw * 0.45f, font.width(Component.literal(amount).withStyle(style)) * (8 * u / 8f) + 2);
            text(win.name(), rx, y, 8 * u, TEXT, 0f, rw - aw - 4);
            text(amount, rx + rw, y, 8 * u, GOLD, 1f, aw);
        }
    }

    /** Rows: icon, text, value right-aligned; scrolls at the screen's speed when there are more rows than fit. */
    private void ledger(Level level, List<BspBoards.BoardLine> lines, float x0, float x1, float h, float top, float area, float u, float t, float speed, float rh, boolean dividers) {
        int n = lines.size(), fit = Math.max(1, (int) (area / rh));
        boolean over = n > fit;
        float off = over ? (t * speed * rh * 1.5f) % (n * rh) : 0;
        for (int k = 0; k < n * (over ? 2 : 1); k++) {
            float y = top + k * rh - off + rh / 2;
            if (y < top + rh * 0.45f || y > top + area - rh * 0.45f) {
                continue;
            }
            BspBoards.BoardLine line = lines.get(k % n);
            float x = x0;
            if (!line.icon().isEmpty()) {
                item(level, line.icon(), x + rh * 0.4f, y, rh * 0.8f);
                x += rh * 0.95f;
            }
            float valueW = line.value() == null ? 0 : Math.min((x1 - x) * 0.4f, font.width(line.value().copy().withStyle(style)) * (rh * 0.62f / 8f) + 2);
            // the note (an age, say) small and grey just before the value
            float noteW = line.note() == null ? 0 : Math.min((x1 - x) * 0.25f, font.width(line.note().copy().withStyle(style)) * (rh * 0.45f / 8f) + 4);
            text(line.text(), x, y, rh * 0.62f, TEXT, 0f, x1 - x - valueW - noteW - 4);
            if (line.note() != null) {
                text(line.note(), x1 - valueW - 3, y, rh * 0.45f, MUTED, 1f, noteW - 4);
            }
            if (line.value() != null) {
                text(line.value(), x1, y, rh * 0.62f, line.colour() == 0 ? GOLD : 0xFF000000 | line.colour(), 1f, valueW + (x1 - x) * 0.3f);
            }
            if (dividers) {
                rect(x0, y + rh / 2 - 0.6f, x1, y + rh / 2, STRIP);
            }
        }
    }

    /**
     * Most Wanted: the board's first line as a large card at the left (the icon big, the value in large gold type, the text and the
     * note under it), and the rest as ledger rows at the right. With one line, the card alone fills the middle.
     */
    private void spotlight(Level level, List<BspBoards.BoardLine> lines, float w, float h, float top, float area, float u, float t, float speed) {
        BspBoards.BoardLine first = lines.get(0);
        boolean alone = lines.size() == 1;
        float cx0 = alone ? w * 0.25f : w * 0.03f, cx1 = alone ? w * 0.75f : w * 0.46f, cw = cx1 - cx0, cx = (cx0 + cx1) / 2;
        rect(cx0, top + 1, cx1, top + area - 1, DARK);
        rect(cx0, top + 1, cx1, top + 2, GOLD);
        // the card's text needs about 46u; the icon gets what is left of the area, and goes when that is too little
        float icon = Math.min(Math.min(cw * 0.42f, area * 0.38f), area - 46 * u);
        if (icon < 6 * u) {
            icon = 0;
        }
        float y = top + 4 * u;
        if (!first.icon().isEmpty() && icon > 0) {
            item(level, first.icon(), cx, y + icon / 2, icon);
            y += icon + 3 * u;
        }
        if (first.value() != null) {
            text(first.value(), cx, y + 8 * u, 16 * u, first.colour() == 0 ? GOLD : 0xFF000000 | first.colour(), 0.5f, cw - 8);
            y += 17 * u;
        }
        text(first.text(), cx, y + 4 * u, 9 * u, TEXT, 0.5f, cw - 8);
        y += 10 * u;
        if (first.note() != null && y + 7 * u <= top + area) {
            text(first.note(), cx, y + 3 * u, 7 * u, MUTED, 0.5f, cw - 8);
        }
        if (!alone) {
            ledger(level, lines.subList(1, lines.size()), w * 0.5f, w * 0.97f, h, top, area, u, t, speed, 15 * u, true);
        }
    }

    /**
     * Tiles: the icon above the value in large type, the text under it and any note under that; as many across as fit. When
     * the rows would have to shrink below what a tile's contents need, they keep that size and scroll up at the screen's
     * speed instead (only whole tiles are drawn).
     */
    private void tiles(Level level, List<BspBoards.BoardLine> lines, float w, float top, float area, float u, float t, float speed) {
        int n = lines.size();
        int cols = Math.max(1, Math.min(n, (int) (w / (46 * u))));
        int rows = (n + cols - 1) / cols;
        boolean notes = lines.stream().anyMatch(l -> l.note() != null);
        float need = (notes ? 48 : 40) * u + 4;
        float tw = (w - 8) / cols, thh = Math.min(area / rows, Math.max(48 * u, need));
        boolean over = thh < need;
        if (over) {
            thh = need;
        }
        float off = over ? (t * speed * 24 * u) % (rows * thh) : 0;
        for (int k = 0; k < n * (over ? 2 : 1); k++) {
            int i = k % n, row = (k / n) * rows + i / cols;
            BspBoards.BoardLine line = lines.get(i);
            float cx = 4 + tw * (i % cols + 0.5f), cy = top + thh * row - off;
            if (cy < top - 0.5f || cy + thh > top + area + 0.5f) {
                continue;
            }
            rect(cx - tw / 2 + 2, cy + 1, cx + tw / 2 - 2, cy + thh - 2, DARK);
            float y = cy + 4;
            if (!line.icon().isEmpty()) {
                item(level, line.icon(), cx, y + 7 * u, 12 * u);
                y += 15 * u;
            }
            if (line.value() != null) {
                text(line.value(), cx, y + 7 * u, 14 * u, line.colour() == 0 ? GOLD : 0xFF000000 | line.colour(), 0.5f, tw - 8);
                y += 15 * u;
            }
            text(line.text(), cx, y + 4 * u, 8 * u, TEXT, 0.5f, tw - 8);
            if (line.note() != null) {
                text(line.note(), cx, y + 12 * u, 6.5f * u, MUTED, 0.5f, tw - 8);
            }
        }
    }

    /** The leaderboard in the left two thirds and one board in a column at the right; in a rotation the column turns through the boards. */
    private void sideColumn(Level level, ScoreScreenBlockEntity screen, List<ScoreService.Entry> board, @Nullable BspBoards.Board b, String id, float w, float h, float top, float area, float u, float t, float speed, int pageIndex, int pageCount) {
        float split = w * 0.64f;
        float keep = alpha;
        alpha = 1f;
        if (board.isEmpty()) {
            text(Component.translatable("screen.bsp_core.empty").getString(), split / 2, top + area / 2, 11 * u, MUTED, 0.5f, split - 12);
        } else {
            list(board, split, h, top, area, u, t, speed);
        }
        alpha = keep;
        rect(split, top - 2, split + 1, h - 2, STRIP);
        rect(split + 1, top - 2, w - 2, h - 2, DARK);
        float x0 = split + 5, x1 = w - 6;
        Component title = b == null ? Component.literal(id) : b.title();
        text(title, x0, top + 5 * u, 7 * u, TQ, 0f, x1 - x0 - 16 * u);
        if (pageCount > 1) {
            text((pageIndex + 1) + "/" + pageCount, x1, top + 5 * u, 7 * u, MUTED, 1f, 16 * u);
        }
        float bodyTop = top + 11 * u, bodyArea = area - 11 * u;
        if (b == null) {
            missing(id, w, bodyTop, bodyArea, u);
            return;
        }
        List<BspBoards.BoardLine> lines = BspBoards.lines(b.id());
        if (lines.isEmpty()) {
            text(Component.translatable("screen.bsp_core.board.empty").getString(), (x0 + x1) / 2, bodyTop + bodyArea / 2, 8 * u, MUTED, 0.5f, x1 - x0);
            return;
        }
        ledger(level, lines, x0, x1, h, bodyTop, bodyArea, u, t, speed, 12 * u, false);
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
        return false; // the render box covers the whole joined screen, so it is drawn only when that is in view
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
