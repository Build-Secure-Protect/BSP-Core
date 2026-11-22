package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.looks.LookActions;
import com.mrgregles.bsp_core.looks.LookOwner;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.looks.LookPixels;
import com.mrgregles.bsp_core.looks.PaintTools;
import com.mrgregles.bsp_core.looks.TotemHats;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The totem screen's LOOKS tab (layout A, "Dressing Room"): a turning preview with the saved looks under it on the left; on the
 * right BODY (one colour or a gradient, a colour square with a hue strip, eight quick colours, the gradient's stops), HEAD, HAT and
 * SAVE / RESET. The edits are a draft until SAVE puts the look on this totem; the server checks everything. A player without
 * dressing sees the tab locked but for a HAT row of the hats they bought, which chooses the hat for all their undressed totems.
 */
public final class LooksTab {
    private static final int TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0xFF9AA3B5, TEXT = 0xFFE8EAF0, HOVER = 0xFF14362F,
            GOLD = 0xFFFFD23A, BAD = 0xFFFF6B5C, OFF = 0xFF6B7385, WELL = 0xFF0A0C10, BG = 0xF010151C;
    private static final int X = 142, W = 174;
    private static final int WELL_X = 10, WELL_Y = 22, WELL_W = 124, WELL_H = 178;
    private static final int SV_X = X, SV_Y = 49, SV_W = 92, SV_H = 50, HUE_X = X + 97, HUE_W = 8;
    private static final int GRAD_Y = 106, HEAD_Y = 150, HAT_Y = 177, CELL = 20, SAVE_Y = 238, SAVED_Y = 212;
    private static final int[] QUICK = {0x7A4FD6, 0xC8283C, 0x2FA860, 0x4AA3F0, 0x2A2438, 0xE9ECF2, 0xF27BBD, 0xC8743A};

    private final Font font;
    private BlockPos pos = BlockPos.ZERO;

    // the draft
    private TotemLook.Mode mode = TotemLook.Mode.SOLID;
    private int solid = 0xE3B341;
    private final int[] stops = {0xFFD23A, 0xFF7A3C, 0xB0203A};
    private int stopCount = 3, selStop;
    private TotemLook.Run run = TotemLook.Run.UP;
    private TotemLook.Head head = TotemLook.Head.TOTEM;
    private String hat = "";
    private boolean hatTint;
    /** The body mode before PAINT, for FROM BODY on the bench. */
    private TotemLook.Mode bodyMode = TotemLook.Mode.SOLID;

    // the paint bench
    private static final int[] DEFAULT_PALETTE = {0x1B2340, 0x141A33, 0xC8202A, 0xA8141C, 0xFF7A1E, 0xFFD23A, 0xE8EAF0, 0x101418,
            0x2FA860, 0x4AA3F0, 0x7A4FD6, 0xF27BBD, 0x8A5A32, 0x9AA3B5, 0xE3B341, 0x3A6FE0};
    private static final String[] PART_KEYS = {"head", "torso", "knee_l", "knee_r", "arm_l", "arm_r", "hand_l", "hand_r"};
    private static final String[] TOOLS = {"pen", "fill", "pick"};
    private boolean bench;
    /** A painting is in the bench (kept when the owner looks at SOLID or GRADIENT and comes back). */
    private boolean hasPainting;
    private final int[] palette = DEFAULT_PALETTE.clone();
    private byte[] paint = new byte[TotemLook.PAINT_BYTES];
    private int selColour = 4, part = 1;
    private String tool = "pen";
    private boolean mirror = true;
    private final java.util.Deque<byte[]> undo = new java.util.ArrayDeque<>();
    private int hoverPx = -1;
    /** Where the colour square and hue strip in use are (the main page's or the bench's). */
    private int pickX = SV_X, pickY = SV_Y, pickW = SV_W, pickH = SV_H, pickHue = HUE_X;
    /** The colour being edited, as hue, saturation and value (kept so a grey keeps its hue). */
    private float hue, sat, val;
    private boolean touched;
    private int seenVersion = -1;

    // the view
    private float yaw = 200, spinFrom;
    private boolean seeSteal;
    @Nullable
    private String dragging;

    public LooksTab(Font font) {
        this.font = font;
    }

    /** The tab was opened on the totem at {@code at}: ask for its data and start from what it wears. */
    public void open(BlockPos at) {
        pos = at;
        touched = false;
        seenVersion = -1;
        ClientLooks.request(at);
    }

    private boolean dressing() {
        return ClientLooks.pos().equals(pos) && ClientLooks.record().dressing;
    }

    private List<String> owned() {
        return ClientLooks.pos().equals(pos) ? ClientLooks.record().owned : List.of();
    }

    /** Takes the totem's own look into the draft the first time data arrives (and again after a SAVE or RESET came back). */
    private void sync(@Nullable ShatterTotemBlockEntity totem) {
        if (!ClientLooks.pos().equals(pos) || ClientLooks.version() == seenVersion) {
            return;
        }
        seenVersion = ClientLooks.version();
        if (touched) {
            return;
        }
        TotemLook l = ClientLooks.look();
        if (l != null) {
            mode = l.mode();
            int[] c = l.colours();
            if (l.mode() == TotemLook.Mode.SOLID) {
                solid = c[0];
            } else if (l.mode() == TotemLook.Mode.GRADIENT) {
                stopCount = c.length;
                System.arraycopy(c, 0, stops, 0, c.length);
            } else {
                takePainting(PaintTools.from(l));
            }
            if (l.mode() != TotemLook.Mode.PAINT) {
                bodyMode = l.mode();
                hasPainting = false; // PAINT starts from this body
            }
            run = l.run();
            head = l.head();
            hat = l.hat();
            hatTint = l.hatTint();
        } else {
            // a plain totem: start from plain gold
            mode = TotemLook.Mode.SOLID;
            bodyMode = mode;
            solid = 0xE3B341;
            head = TotemLook.Head.TOTEM;
            hatTint = false;
            hat = totem == null ? "" : totem.shownHat();
            hasPainting = false;
        }
        takeColour();
    }

    /** The draft as a look for this player. */
    private TotemLook draft() {
        java.util.UUID me = Minecraft.getInstance().player.getUUID();
        TotemLook l = mode == TotemLook.Mode.PAINT ? TotemLook.painted(me, palette, paint)
                : mode == TotemLook.Mode.GRADIENT ? TotemLook.gradient(me, run, java.util.Arrays.copyOf(stops, stopCount))
                : TotemLook.solid(me, solid);
        return l.withHead(head).withHat(hat).withHatTint(hatTint);
    }

    /** The draft's body as a colour or gradient, whatever PAINT holds: what FROM BODY paints from. */
    private TotemLook bodyDraft() {
        java.util.UUID me = Minecraft.getInstance().player.getUUID();
        return bodyMode == TotemLook.Mode.GRADIENT ? TotemLook.gradient(me, run, java.util.Arrays.copyOf(stops, stopCount)) : TotemLook.solid(me, solid);
    }

    /** Takes a painting into the bench: its colours into the 16 slots (the rest keep the default colours), its pixels as they are. */
    private void takePainting(PaintTools.Painting p) {
        System.arraycopy(DEFAULT_PALETTE, 0, palette, 0, palette.length);
        System.arraycopy(p.palette(), 0, palette, 0, Math.min(palette.length, p.palette().length));
        paint = p.paint().clone();
        hasPainting = true;
    }

    private int active() {
        return bench || mode == TotemLook.Mode.PAINT ? palette[selColour] : mode == TotemLook.Mode.GRADIENT ? stops[selStop] : solid;
    }

    private void setActive(int rgb) {
        if (bench || mode == TotemLook.Mode.PAINT) {
            palette[selColour] = rgb;
        } else if (mode == TotemLook.Mode.GRADIENT) {
            stops[selStop] = rgb;
        } else {
            solid = rgb;
        }
        touched = true;
    }

    /** Reads the active colour into the hue, saturation and value the picker shows. */
    private void takeColour() {
        float[] hsv = hsv(active());
        if (hsv[1] > 0.01f && hsv[2] > 0.01f) {
            hue = hsv[0];
        }
        sat = hsv[1];
        val = hsv[2];
    }

    // ------------------------------------------------------------------ drawing

    public void renderBg(GuiGraphics g, int x, int y, int mouseX, int mouseY, float partial, @Nullable ShatterTotemBlockEntity totem) {
        sync(totem);
        boolean dress = dressing();
        if (bench && !dress) {
            bench = false; // dressing went while the bench was open
        }
        if (bench) {
            benchBg(g, x, y, mouseX, mouseY, partial, totem);
            return;
        }
        pickX = SV_X;
        pickY = SV_Y;
        pickW = SV_W;
        pickH = SV_H;
        pickHue = HUE_X;
        // the preview
        g.fill(x + WELL_X, y + WELL_Y, x + WELL_X + WELL_W, y + WELL_Y + WELL_H, DIM);
        g.fill(x + WELL_X + 1, y + WELL_Y + 1, x + WELL_X + WELL_W - 1, y + WELL_Y + WELL_H - 1, WELL);
        preview(g, x + WELL_X + WELL_W / 2, y + WELL_Y + WELL_H - 14, 64, totem, partial, dress);

        // the saved looks
        small(g, Component.translatable("gui.bsp_core.looks.saved"), x + WELL_X, y + SAVED_Y - 8, MUTED);
        List<TotemLook> saved = dress ? ClientLooks.record().saved : List.of();
        for (int k = 0; k < LookOwner.MAX_SAVED; k++) {
            int sx = x + WELL_X + k * 21, sy = y + SAVED_Y;
            boolean hot = in(mouseX, mouseY, sx, sy, 20, 14);
            g.fill(sx, sy, sx + 20, sy + 14, hot && k < saved.size() ? TQ : DIM);
            g.fill(sx + 1, sy + 1, sx + 19, sy + 13, SLOT_BG);
            if (k < saved.size()) {
                swatchOf(g, saved.get(k), sx + 3, sy + 3, 14, 8);
            }
        }
        long wait = ClientLooks.waitMs();
        key(g, x + WELL_X + 105, y + SAVED_Y, 19, 14, wait > 0 ? String.valueOf((wait + 999) / 1000) : "+", dress, false, mouseX, mouseY);

        key(g, x + WELL_X, y + 230, 18, 14, "<", true, false, mouseX, mouseY);
        key(g, x + WELL_X + 20, y + 230, 18, 14, ">", true, false, mouseX, mouseY);
        keyC(g, x + WELL_X + 40, y + 230, 84, 14, Component.translatable("gui.bsp_core.looks.see_steal"), true, seeSteal, mouseX, mouseY, BAD);

        if (!dress) {
            locked(g, x, y, mouseX, mouseY);
            return;
        }
        rule(g, x + X, y + 22, W, Component.translatable("gui.bsp_core.looks.body"));
        keyC(g, x + X, y + 31, 56, 13, Component.translatable("gui.bsp_core.looks.solid"), true, mode == TotemLook.Mode.SOLID, mouseX, mouseY, TQ);
        keyC(g, x + X + 59, y + 31, 56, 13, Component.translatable("gui.bsp_core.looks.gradient"), true, mode == TotemLook.Mode.GRADIENT, mouseX, mouseY, TQ);
        keyC(g, x + X + 118, y + 31, 56, 13, Component.translatable("gui.bsp_core.looks.paint"), true, mode == TotemLook.Mode.PAINT, mouseX, mouseY, TQ);
        if (mode == TotemLook.Mode.PAINT) {
            long used = java.util.stream.IntStream.range(0, TotemLook.PAINT_PIXELS).map(i -> PaintTools.pixel(paint, i)).distinct().count();
            int ly = y + SV_Y;
            for (var line : font.split(Component.translatable("gui.bsp_core.looks.painted", used), Math.round(W / 0.75f))) {
                g.pose().pushPose();
                g.pose().translate(x + X, ly, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, line, 0, 0, TEXT, false);
                g.pose().popPose();
                ly += 8;
            }
        } else {
            g.drawManaged(() -> colourPage(g, x, y, mouseX, mouseY)); // thousands of one-pixel fills: one flush
        }
        headHatSave(g, x, y, mouseX, mouseY, wait);
    }

    /** The colour square, hue strip, quick colours and the gradient's stops (SOLID and GRADIENT). */
    private void colourPage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // colour square, hue strip, the colour itself and its hex
        int sx = x + SV_X, sy = y + SV_Y;
        picker(g, x, y);
        g.fill(x + X + 112, sy, x + X + 134, sy + 22, DIM);
        g.fill(x + X + 113, sy + 1, x + X + 133, sy + 21, 0xFF000000 | active());
        small(g, Component.literal(String.format(Locale.ROOT, "#%06X", active())), x + X + 137, sy + 3, TEXT);
        small(g, Component.translatable(mode == TotemLook.Mode.GRADIENT ? "gui.bsp_core.looks.stop." + stopKey(selStop) : "gui.bsp_core.looks.colour"), x + X + 137, sy + 12, MUTED);
        for (int k = 0; k < QUICK.length; k++) {
            int qx = x + X + 112 + (k % 4) * 15, qy = y + 76 + (k / 4) * 13;
            g.fill(qx, qy, qx + 12, qy + 12, active() == QUICK[k] ? 0xFFFFFFFF : DIM);
            g.fill(qx + 1, qy + 1, qx + 11, qy + 11, 0xFF000000 | QUICK[k]);
        }

        // the gradient's stops
        boolean grad = mode == TotemLook.Mode.GRADIENT;
        int gx = x + X, gy = y + GRAD_Y;
        for (int i = 0; i < W; i++) {
            float t = i / (float) (W - 1);
            int c = grad ? gradAt(t) : solid;
            g.fill(gx + i, gy, gx + i + 1, gy + 8, 0xFF000000 | c);
        }
        outline(g, gx - 1, gy - 1, W + 2, 10, DIM);
        if (grad) {
            for (int k = 0; k < stopCount; k++) {
                int hx = stopX(gx, k);
                g.fill(hx - 3, gy + 10, hx + 4, gy + 17, k == selStop ? 0xFFFFFFFF : DIM);
                g.fill(hx - 2, gy + 11, hx + 3, gy + 16, 0xFF000000 | stops[k]);
            }
        } else {
            g.fill(gx - 1, gy - 1, gx + W + 1, gy + 9, 0xB010151C);
            small(g, Component.translatable("gui.bsp_core.looks.gradient_hint"), gx, gy + 11, MUTED);
        }
        if (grad) {
            keyC(g, x + X, y + 125, 56, 12, Component.translatable(stopCount == 3 ? "gui.bsp_core.looks.three_stops" : "gui.bsp_core.looks.two_stops"), true, false, mouseX, mouseY, TQ);
            keyC(g, x + X + 60, y + 125, 86, 12, Component.translatable(run == TotemLook.Run.UP ? "gui.bsp_core.looks.run_up" : "gui.bsp_core.looks.run_side"), true, false, mouseX, mouseY, TQ);
        }

    }

    private void headHatSave(GuiGraphics g, int x, int y, int mouseX, int mouseY, long wait) {
        rule(g, x + X, y + 141, W, Component.translatable("gui.bsp_core.looks.head"));
        keyC(g, x + X, y + HEAD_Y, 85, 13, Component.translatable("gui.bsp_core.looks.totem_face"), true, head == TotemLook.Head.TOTEM, mouseX, mouseY, TQ);
        keyC(g, x + X + 89, y + HEAD_Y, 85, 13, Component.translatable("gui.bsp_core.looks.my_head"), true, head == TotemLook.Head.PLAYER, mouseX, mouseY, TQ);

        rule(g, x + X, y + 168, W, Component.translatable("gui.bsp_core.looks.hat"));
        hatGrid(g, x, y, mouseX, mouseY, hat, true);
        small(g, hat.isEmpty() ? Component.translatable("gui.bsp_core.looks.no_hat") : Component.translatable("gui.bsp_core.looks.hat." + hat), x + X, y + 225, GOLD);
        keyC(g, x + X + 96, y + 221, 78, 12, Component.translatable(hatTint ? "gui.bsp_core.looks.hat_body" : "gui.bsp_core.looks.hat_own"), !hat.isEmpty(), hatTint, mouseX, mouseY, TQ);

        keyC(g, x + X, y + SAVE_Y, 85, 16, wait > 0 ? Component.translatable("gui.bsp_core.looks.wait", (wait + 999) / 1000) : Component.translatable("gui.bsp_core.looks.save"), wait <= 0, wait <= 0, mouseX, mouseY, TQ);
        keyC(g, x + X + 89, y + SAVE_Y, 85, 16, Component.translatable("gui.bsp_core.looks.reset"), true, false, mouseX, mouseY, TQ);
    }

    /** The tab without dressing: one line saying what gives it, and the hats the player has for all their undressed totems. */
    private void locked(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        rule(g, x + X, y + 22, W, Component.translatable("gui.bsp_core.looks.body"));
        int ly = y + 34;
        for (var line : font.split(Component.translatable("gui.bsp_core.looks.locked"), Math.round(W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x + X, ly, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, TEXT, false);
            g.pose().popPose();
            ly += 8;
        }
        rule(g, x + X, y + 168, W, Component.translatable("gui.bsp_core.looks.hat"));
        LookOwner r = ClientLooks.pos().equals(pos) ? ClientLooks.record() : new LookOwner();
        String worn = r.autoHat();
        hatGrid(g, x, y, mouseX, mouseY, worn, false);
        small(g, Component.translatable(owned().isEmpty() ? "gui.bsp_core.looks.no_hats" : "gui.bsp_core.looks.hat_all"), x + X, y + 225, MUTED);
    }

    private void hatGrid(GuiGraphics g, int x, int y, int mouseX, int mouseY, String chosen, boolean dress) {
        Lighting.setupForEntityInInventory();
        g.drawManaged(() -> hatCells(g, x, y, mouseX, mouseY, chosen)); // one flush for every cell and hat picture
        Lighting.setupFor3DItems();
    }

    private void hatCells(GuiGraphics g, int x, int y, int mouseX, int mouseY, String chosen) {
        List<String> ids = new ArrayList<>();
        ids.add("");
        ids.addAll(TotemHats.IDS);
        for (int k = 0; k < 16; k++) {
            int cx = x + X + (k % 8) * (CELL + 2), cy = y + HAT_Y + (k / 8) * (CELL + 2);
            if (k >= ids.size()) {
                g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF161A22);
                continue;
            }
            String id = ids.get(k);
            boolean have = id.isEmpty() || owned().contains(id);
            boolean on = id.equals(chosen);
            g.fill(cx, cy, cx + CELL, cy + CELL, on ? TQ : in(mouseX, mouseY, cx, cy, CELL, CELL) && have ? 0xFF3A6058 : DIM);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, SLOT_BG);
            if (id.isEmpty()) {
                for (int d = 0; d < CELL - 8; d++) {
                    g.fill(cx + 4 + d, cy + CELL - 5 - d, cx + 5 + d, cy + CELL - 4 - d, BAD);
                }
            } else {
                hatIcon(g, id, cx + CELL / 2, cy + CELL - 3, have);
                if (!have) {
                    g.pose().pushPose();
                    g.pose().translate(0, 0, 200); // over the hat picture (drawn at 120 and up)
                    g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, 0xA00C0E12);
                    g.fill(cx + CELL - 7, cy + 3, cx + CELL - 3, cy + 7, OFF); // a small lock
                    g.pose().popPose();
                }
            }
        }
    }

    private void hatIcon(GuiGraphics g, String id, int cx, int bottom, boolean have) {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, bottom, 120);
        pose.mulPoseMatrix(new org.joml.Matrix4f().scaling(1, -1, 1)); // GUI y runs down: mirror once, so normals stay sound
        pose.scale(22, 22, 22);
        pose.mulPose(Axis.XP.rotationDegrees(-15));
        pose.mulPose(Axis.YP.rotationDegrees(200));
        pose.translate(-0.5, -1.45, -0.5);
        HatDraw.draw(pose, g.bufferSource(), id, Direction.NORTH, null, LightTexture.FULL_BRIGHT, 0);
        pose.popPose();
    }

    /** The totem turning in the well: the draft on a dressed totem, or what the totem wears now when locked. */
    private void preview(GuiGraphics g, int cx, int bottom, float scale, @Nullable ShatterTotemBlockEntity totem, float partial, boolean dress) {
        Minecraft mc = Minecraft.getInstance();
        float time = mc.level == null ? 0 : mc.level.getGameTime() + partial;
        TotemLook body = dress ? draft() : totem == null ? null : totem.shownBody();
        String hatNow = dress ? hat : totem == null ? "" : totem.shownHat();
        if (!dress && ClientLooks.pos().equals(pos)) {
            hatNow = ClientLooks.record().autoHat().isEmpty() ? hatNow : ClientLooks.record().autoHat();
        }
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, bottom, 150);
        pose.mulPoseMatrix(new org.joml.Matrix4f().scaling(1, -1, 1));
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(-12));
        float turn = dragging != null && dragging.equals("well") ? yaw : yaw + (time - spinFrom) * 0.6f;
        pose.mulPose(Axis.YP.rotationDegrees(turn));
        pose.translate(-0.5, 0, -0.5);
        Lighting.setupForEntityInInventory();
        BlockState state = ModBlocks.SHATTER_TOTEM.get().defaultBlockState()
                .setValue(ShatterTotemBlock.GLOW, seeSteal && body == null ? ShatterTotemBlock.Glow.STEALING : ShatterTotemBlock.Glow.OWNED)
                .setValue(ShatterTotemBlock.FACING, Direction.NORTH)
                .setValue(ShatterTotemBlock.LOOKS, body != null);
        mc.getBlockRenderer().renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        if (body != null) {
            var skin = body.head() == TotemLook.Head.PLAYER && mc.player != null
                    ? PlayerSkins.get(new com.mrgregles.bsp_core.totem.TotemOwner(mc.player.getUUID(), mc.player.getGameProfile().getName())) : null;
            var texture = dress ? LookTextures.bench(body) : LookTextures.get(body); // a draft changes as it is edited: one texture, rewritten
            LookBodyDraw.draw(pose, g.bufferSource(), texture, Direction.NORTH, LightTexture.FULL_BRIGHT, seeSteal ? LookBodyDraw.stealPulse(time) : 0, skin);
        }
        if (!hatNow.isEmpty()) {
            HatDraw.draw(pose, g.bufferSource(), hatNow, Direction.NORTH, body != null && hatTint && dress ? body.mainColour() : null, LightTexture.FULL_BRIGHT, time);
        }
        g.flush();
        Lighting.setupFor3DItems();
        pose.popPose();
    }

    /** The colour square and hue strip at the geometry in use. */
    private void picker(GuiGraphics g, int x, int y) {
        g.drawManaged(() -> pickerFills(g, x, y));
    }

    private void pickerFills(GuiGraphics g, int x, int y) {
        int sx = x + pickX, sy = y + pickY;
        for (int i = 0; i < pickW; i++) {
            for (int j = 0; j < pickH; j += 2) {
                g.fill(sx + i, sy + j, sx + i + 1, sy + j + 2, 0xFF000000 | rgb(hue, i / (float) (pickW - 1), 1 - j / (float) (pickH - 1)));
            }
        }
        outline(g, sx - 1, sy - 1, pickW + 2, pickH + 2, DIM);
        int mx = sx + Math.round(sat * (pickW - 1)), my = sy + Math.round((1 - val) * (pickH - 1));
        outline(g, mx - 2, my - 2, 5, 5, 0xFF000000);
        outline(g, mx - 1, my - 1, 3, 3, 0xFFFFFFFF);
        for (int j = 0; j < pickH; j++) {
            g.fill(x + pickHue, sy + j, x + pickHue + HUE_W, sy + j + 1, 0xFF000000 | rgb(j / (float) pickH, 1, 1));
        }
        outline(g, x + pickHue - 1, sy - 1, HUE_W + 2, pickH + 2, DIM);
        int hy = sy + Math.round(hue * (pickH - 1));
        g.fill(x + pickHue - 2, hy - 1, x + pickHue + HUE_W + 2, hy + 1, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ the paint bench (layout C)

    private static final int UW_X = 62, UW_Y = 24, UW_W = 108, UW_H = 80, PAL_X = 62, PAL_Y = 140, B_X = 176;

    /** Where the chosen part is drawn: left, top and pixel size. */
    private int[] unwrap(int x, int y) {
        LookLayout.Part p = LookLayout.PARTS[part];
        int bw = 2 * (p.w() + p.d()), bh = p.d() + p.h();
        int s = Math.max(1, Math.min(7, Math.min(UW_W / bw, UW_H / bh)));
        return new int[]{x + UW_X + (UW_W - bw * s) / 2, y + UW_Y + (UW_H - bh * s) / 2, s};
    }

    /** The painted pixel under the mouse, or -1. */
    private int pixelAt(int mx, int my, int x, int y) {
        int[] u = unwrap(x, y);
        LookLayout.Part p = LookLayout.PARTS[part];
        for (LookLayout.Face f : LookLayout.Face.values()) {
            int[] at = LookLayout.at(p, f), d = LookLayout.dims(p, f);
            int fx = u[0] + (at[0] - p.u()) * u[2], fy = u[1] + (at[1] - p.v()) * u[2];
            if (mx >= fx && my >= fy && mx < fx + d[0] * u[2] && my < fy + d[1] * u[2]) {
                return PaintTools.index(part, f, (mx - fx) / u[2], (my - fy) / u[2]);
            }
        }
        return -1;
    }

    private void benchBg(GuiGraphics g, int x, int y, int mouseX, int mouseY, float partial, @Nullable ShatterTotemBlockEntity totem) {
        pickX = B_X;
        pickY = 148;
        pickW = 104;
        pickH = 40;
        pickHue = B_X + 108;
        for (int k = 0; k < PART_KEYS.length; k++) {
            keyC(g, x + 10, y + 24 + k * 16, 46, 14, Component.translatable("gui.bsp_core.looks.part." + PART_KEYS[k]), true, part == k, mouseX, mouseY, TQ);
        }
        small(g, Component.translatable("gui.bsp_core.looks.start"), x + 10, y + 155, MUTED);
        keyC(g, x + 10, y + 164, 46, 13, Component.translatable("gui.bsp_core.looks.from_body"), true, false, mouseX, mouseY, TQ);
        keyC(g, x + 10, y + 179, 46, 13, Component.translatable("gui.bsp_core.looks.blank"), true, false, mouseX, mouseY, TQ);

        // the chosen part, unfolded
        int[] u = unwrap(x, y);
        LookLayout.Part p = LookLayout.PARTS[part];
        hoverPx = pixelAt(mouseX, mouseY, x, y);
        int mirrored = hoverPx >= 0 && mirror ? PaintTools.mirror(hoverPx) : -1;
        g.fill(x + UW_X - 1, y + UW_Y - 1, x + UW_X + UW_W + 1, y + UW_Y + UW_H + 1, WELL);
        g.drawManaged(() -> { // a few hundred painted pixels: one flush
            for (LookLayout.Face f : LookLayout.Face.values()) {
                int[] at = LookLayout.at(p, f), d = LookLayout.dims(p, f);
                int fx = u[0] + (at[0] - p.u()) * u[2], fy = u[1] + (at[1] - p.v()) * u[2];
                for (int j = 0; j < d[1]; j++) {
                    for (int i = 0; i < d[0]; i++) {
                        int n = PaintTools.index(part, f, i, j);
                        g.fill(fx + i * u[2], fy + j * u[2], fx + (i + 1) * u[2], fy + (j + 1) * u[2], 0xFF000000 | palette[PaintTools.pixel(paint, n)]);
                    }
                }
                outline(g, fx - 1, fy - 1, d[0] * u[2] + 2, d[1] * u[2] + 2, 0xFF3A4150);
            }
        });
        if (hoverPx >= 0) {
            PaintTools.Where w = PaintTools.where(hoverPx);
            int[] at = LookLayout.at(p, w.face());
            outline(g, u[0] + (at[0] - p.u() + w.i()) * u[2], u[1] + (at[1] - p.v() + w.j()) * u[2], u[2], u[2], 0xFFFFFFFF);
        }
        if (mirrored >= 0 && mirrored != hoverPx && PaintTools.where(mirrored).part() == part) {
            PaintTools.Where w = PaintTools.where(mirrored);
            int[] at = LookLayout.at(p, w.face());
            outline(g, u[0] + (at[0] - p.u() + w.i()) * u[2], u[1] + (at[1] - p.v() + w.j()) * u[2], u[2], u[2], 0xFF9AA3B5);
        }

        for (int k = 0; k < TOOLS.length; k++) {
            keyC(g, x + 62 + k * 27, y + 110, 25, 13, Component.translatable("gui.bsp_core.looks.tool." + TOOLS[k]), true, tool.equals(TOOLS[k]), mouseX, mouseY, TQ);
        }
        keyC(g, x + 143, y + 110, 25, 13, Component.translatable("gui.bsp_core.looks.tool.undo"), !undo.isEmpty(), false, mouseX, mouseY, TQ);
        g.fill(x + 62, y + 127, x + 69, y + 134, mirror ? TQ : DIM);
        g.fill(x + 63, y + 128, x + 68, y + 133, SLOT_BG);
        if (mirror) {
            g.fill(x + 64, y + 129, x + 67, y + 132, TQ);
        }
        small(g, Component.translatable("gui.bsp_core.looks.mirror"), x + 73, y + 127, TEXT);
        for (int k = 0; k < palette.length; k++) {
            int qx = x + PAL_X + (k % 8) * 13, qy = y + PAL_Y + (k / 8) * 14;
            g.fill(qx, qy, qx + 12, qy + 12, k == selColour ? 0xFFFFFFFF : DIM);
            g.fill(qx + 1, qy + 1, qx + 11, qy + 11, 0xFF000000 | palette[k]);
        }
        Component where = hoverPx >= 0
                ? Component.translatable("gui.bsp_core.looks.where", Component.translatable("gui.bsp_core.looks.face." + PaintTools.where(hoverPx).face().name().toLowerCase(Locale.ROOT)),
                PaintTools.where(hoverPx).i() + 1, PaintTools.where(hoverPx).j() + 1)
                : Component.translatable("gui.bsp_core.looks.part." + PART_KEYS[part]);
        small(g, where, x + 62, y + 172, TEXT);
        small(g, Component.translatable("gui.bsp_core.looks.bench_count"), x + 62, y + 181, MUTED);
        small(g, Component.translatable("gui.bsp_core.looks.bench_staff"), x + 62, y + 190, MUTED);
        small(g, Component.translatable("gui.bsp_core.looks.bench_size"), x + 10, y + 222, OFF);

        // the preview and the colour of the chosen slot
        g.fill(x + B_X, y + 24, x + B_X + 140, y + 142, DIM);
        g.fill(x + B_X + 1, y + 25, x + B_X + 139, y + 141, WELL);
        preview(g, x + B_X + 70, y + 136, 44, totem, partial, true);
        picker(g, x, y);
        g.fill(x + B_X + 120, y + 148, x + B_X + 140, y + 168, DIM);
        g.fill(x + B_X + 121, y + 149, x + B_X + 139, y + 167, 0xFF000000 | palette[selColour]);
        small(g, Component.translatable("gui.bsp_core.looks.slot", String.format(Locale.ROOT, "#%06X", palette[selColour]), selColour + 1), x + B_X, y + 192, TEXT);
        long wait = ClientLooks.waitMs();
        keyC(g, x + B_X, y + SAVE_Y, 68, 16, Component.translatable("gui.bsp_core.looks.back"), true, false, mouseX, mouseY, TQ);
        keyC(g, x + B_X + 72, y + SAVE_Y, 68, 16, wait > 0 ? Component.translatable("gui.bsp_core.looks.wait", (wait + 999) / 1000) : Component.translatable("gui.bsp_core.looks.save"), wait <= 0, wait <= 0, mouseX, mouseY, TQ);
    }

    private void openBench() {
        if (mode != TotemLook.Mode.PAINT) {
            bodyMode = mode;
            if (!hasPainting) {
                takePainting(PaintTools.from(bodyDraft())); // nobody starts from a blank: the bench starts from the body as it is
                undo.clear();
            }
            mode = TotemLook.Mode.PAINT;
            touched = true;
        }
        bench = true;
        takeColour();
    }

    private void pushUndo() {
        undo.push(paint.clone());
        while (undo.size() > 40) {
            undo.removeLast();
        }
    }

    private void paintAt(int n) {
        switch (tool) {
            case "fill" -> {
                PaintTools.fill(paint, n, selColour);
                if (mirror) {
                    PaintTools.fill(paint, PaintTools.mirror(n), selColour);
                }
            }
            case "pick" -> {
                selColour = PaintTools.pixel(paint, n);
                takeColour();
                return;
            }
            default -> {
                TotemLook.setPixel(paint, n, selColour);
                if (mirror) {
                    TotemLook.setPixel(paint, PaintTools.mirror(n), selColour);
                }
            }
        }
        touched = true;
    }

    private boolean benchClicked(int mx, int my, int x, int y) {
        for (int k = 0; k < PART_KEYS.length; k++) {
            if (in(mx, my, x + 10, y + 24 + k * 16, 46, 14)) {
                part = k;
                return true;
            }
        }
        if (in(mx, my, x + 10, y + 164, 46, 13)) {
            pushUndo();
            takePainting(PaintTools.from(bodyDraft()));
            touched = true;
            takeColour();
            return true;
        }
        if (in(mx, my, x + 10, y + 179, 46, 13)) {
            pushUndo();
            for (int i = 0; i < TotemLook.PAINT_PIXELS; i++) {
                TotemLook.setPixel(paint, i, selColour);
            }
            touched = true;
            return true;
        }
        int n = pixelAt(mx, my, x, y);
        if (n >= 0) {
            if (!tool.equals("pick")) {
                pushUndo();
            }
            paintAt(n);
            dragging = tool.equals("pen") ? "paint" : null;
            return true;
        }
        for (int k = 0; k < TOOLS.length; k++) {
            if (in(mx, my, x + 62 + k * 27, y + 110, 25, 13)) {
                tool = TOOLS[k];
                return true;
            }
        }
        if (in(mx, my, x + 143, y + 110, 25, 13) && !undo.isEmpty()) {
            paint = undo.pop();
            touched = true;
            return true;
        }
        if (in(mx, my, x + 62, y + 125, 100, 10)) {
            mirror = !mirror;
            return true;
        }
        for (int k = 0; k < palette.length; k++) {
            if (in(mx, my, x + PAL_X + (k % 8) * 13, y + PAL_Y + (k / 8) * 14, 12, 12)) {
                selColour = k;
                takeColour();
                return true;
            }
        }
        if (in(mx, my, x + pickX, y + pickY, pickW, pickH)) {
            dragging = "sv";
            dragTo(mx, my, x, y);
            return true;
        }
        if (in(mx, my, x + pickHue - 2, y + pickY, HUE_W + 4, pickH)) {
            dragging = "hue";
            dragTo(mx, my, x, y);
            return true;
        }
        if (in(mx, my, x + B_X, y + SAVE_Y, 68, 16)) {
            bench = false;
            takeColour();
            return true;
        }
        if (in(mx, my, x + B_X + 72, y + SAVE_Y, 68, 16) && ClientLooks.waitMs() <= 0) {
            ClientLooks.send(pos, LookActions.Action.PUT, 0, "", draft());
            touched = false;
            seenVersion = ClientLooks.version();
            return true;
        }
        if (in(mx, my, x + B_X, y + 24, 140, 118)) {
            Minecraft mc = Minecraft.getInstance();
            float now = mc.level == null ? 0 : mc.level.getGameTime();
            yaw += (now - spinFrom) * 0.6f;
            spinFrom = now;
            dragging = "well";
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ input

    public boolean mouseClicked(double mxd, double myd, int button, int x, int y, @Nullable ShatterTotemBlockEntity totem) {
        int mx = (int) mxd, my = (int) myd;
        boolean dress = dressing();
        if (bench && dress) {
            return benchClicked(mx, my, x, y);
        }
        if (in(mx, my, x + WELL_X, y + WELL_Y, WELL_W, WELL_H)) {
            Minecraft mc = Minecraft.getInstance();
            float now = mc.level == null ? 0 : mc.level.getGameTime();
            yaw += (now - spinFrom) * 0.6f; // keep the turn so far, so the totem does not jump when grabbed
            spinFrom = now;
            dragging = "well";
            return true;
        }
        if (in(mx, my, x + WELL_X, y + 230, 18, 14) || in(mx, my, x + WELL_X + 20, y + 230, 18, 14)) {
            yaw += in(mx, my, x + WELL_X, y + 230, 18, 14) ? -45 : 45;
            return true;
        }
        if (in(mx, my, x + WELL_X + 40, y + 230, 84, 14)) {
            seeSteal = !seeSteal;
            return true;
        }
        List<String> ids = new ArrayList<>();
        ids.add("");
        ids.addAll(TotemHats.IDS);
        for (int k = 0; k < ids.size(); k++) {
            int cx = x + X + (k % 8) * (CELL + 2), cy = y + HAT_Y + (k / 8) * (CELL + 2);
            if (in(mx, my, cx, cy, CELL, CELL)) {
                String id = ids.get(k);
                if (!id.isEmpty() && !owned().contains(id)) {
                    return true;
                }
                if (dress) {
                    hat = id;
                    touched = true;
                } else {
                    ClientLooks.send(pos, LookActions.Action.CHOOSE_HAT, 0, id.isEmpty() ? LookOwner.NO_HAT : id, null);
                }
                return true;
            }
        }
        if (!dress) {
            return false;
        }
        // saved looks
        List<TotemLook> saved = ClientLooks.record().saved;
        for (int k = 0; k < LookOwner.MAX_SAVED; k++) {
            if (in(mx, my, x + WELL_X + k * 21, y + SAVED_Y, 20, 14) && k < saved.size()) {
                if (Screen.hasShiftDown()) {
                    ClientLooks.send(pos, LookActions.Action.DELETE, k, "", null);
                } else {
                    loadFrom(saved.get(k));
                }
                return true;
            }
        }
        if (in(mx, my, x + WELL_X + 105, y + SAVED_Y, 19, 14)) {
            ClientLooks.send(pos, LookActions.Action.SAVE, -1, "", draft());
            return true;
        }
        if (in(mx, my, x + X + 118, y + 31, 56, 13)) {
            openBench();
            return true;
        }
        for (int k = 0; k < 2; k++) {
            if (in(mx, my, x + X + k * 59, y + 31, 56, 13)) {
                TotemLook.Mode m = k == 0 ? TotemLook.Mode.SOLID : TotemLook.Mode.GRADIENT;
                bodyMode = m;
                if (m != mode) {
                    if (m == TotemLook.Mode.GRADIENT) {
                        selStop = 0;
                    }
                    mode = m;
                    touched = true;
                    takeColour();
                }
                return true;
            }
        }
        if (mode == TotemLook.Mode.PAINT) {
            return headHatClicked(mx, my, x, y);
        }
        if (in(mx, my, x + SV_X, y + SV_Y, SV_W, SV_H)) {
            dragging = "sv";
            dragTo(mx, my, x, y);
            return true;
        }
        if (in(mx, my, x + HUE_X - 2, y + SV_Y, HUE_W + 4, SV_H)) {
            dragging = "hue";
            dragTo(mx, my, x, y);
            return true;
        }
        for (int k = 0; k < QUICK.length; k++) {
            if (in(mx, my, x + X + 112 + (k % 4) * 15, y + 76 + (k / 4) * 13, 12, 12)) {
                setActive(QUICK[k]);
                takeColour();
                return true;
            }
        }
        if (mode == TotemLook.Mode.GRADIENT) {
            for (int k = 0; k < stopCount; k++) {
                int hx = stopX(x + X, k);
                if (in(mx, my, hx - 4, y + GRAD_Y - 1, 9, 19)) {
                    selStop = k;
                    takeColour();
                    return true;
                }
            }
            if (in(mx, my, x + X, y + 125, 56, 12)) {
                stopCount = stopCount == 3 ? 2 : 3;
                if (stopCount == 2) {
                    stops[1] = stops[2]; // two stops: the top and the base
                }
                selStop = Math.min(selStop, stopCount - 1);
                touched = true;
                takeColour();
                return true;
            }
            if (in(mx, my, x + X + 60, y + 125, 86, 12)) {
                run = run == TotemLook.Run.UP ? TotemLook.Run.SIDE : TotemLook.Run.UP;
                touched = true;
                return true;
            }
        }
        return headHatClicked(mx, my, x, y);
    }

    /** HEAD, the hat's colours, SAVE and RESET, the same on every body page. */
    private boolean headHatClicked(int mx, int my, int x, int y) {
        if (in(mx, my, x + X, y + HEAD_Y, 85, 13) || in(mx, my, x + X + 89, y + HEAD_Y, 85, 13)) {
            head = in(mx, my, x + X, y + HEAD_Y, 85, 13) ? TotemLook.Head.TOTEM : TotemLook.Head.PLAYER;
            touched = true;
            return true;
        }
        if (in(mx, my, x + X + 96, y + 221, 78, 12) && !hat.isEmpty()) {
            hatTint = !hatTint;
            touched = true;
            return true;
        }
        if (in(mx, my, x + X, y + SAVE_Y, 85, 16) && ClientLooks.waitMs() <= 0) {
            ClientLooks.send(pos, LookActions.Action.PUT, 0, "", draft());
            touched = false;
            seenVersion = ClientLooks.version(); // take the server's reply, not the data from before the click
            return true;
        }
        if (in(mx, my, x + X + 89, y + SAVE_Y, 85, 16)) {
            ClientLooks.send(pos, LookActions.Action.TAKE_OFF, 0, "", null);
            touched = false;
            seenVersion = ClientLooks.version();
            mode = TotemLook.Mode.SOLID;
            solid = 0xE3B341;
            head = TotemLook.Head.TOTEM;
            hat = "";
            hatTint = false;
            hasPainting = false;
            takeColour();
            return true;
        }
        return false;
    }

    private void loadFrom(TotemLook l) {
        mode = l.mode();
        int[] c = l.colours();
        if (l.mode() == TotemLook.Mode.GRADIENT) {
            stopCount = c.length;
            System.arraycopy(c, 0, stops, 0, c.length);
            selStop = 0;
            bodyMode = mode;
        } else if (l.mode() == TotemLook.Mode.PAINT) {
            takePainting(PaintTools.from(l));
        } else {
            solid = c[0];
            bodyMode = mode;
        }
        run = l.run();
        head = l.head();
        hat = l.hat().isEmpty() || owned().contains(l.hat()) ? l.hat() : "";
        hatTint = l.hatTint();
        touched = true;
        takeColour();
    }

    public boolean mouseDragged(double mxd, double myd, double dx, int x, int y) {
        if (dragging == null) {
            return false;
        }
        if (dragging.equals("well")) {
            yaw += (float) dx * 2;
            return true;
        }
        dragTo((int) mxd, (int) myd, x, y);
        return true;
    }

    public boolean mouseReleased() {
        if (dragging != null && dragging.equals("well")) {
            Minecraft mc = Minecraft.getInstance();
            spinFrom = mc.level == null ? 0 : mc.level.getGameTime(); // carry on turning from where it was left
        }
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    private void dragTo(int mx, int my, int x, int y) {
        if ("paint".equals(dragging)) {
            int n = pixelAt(mx, my, x, y);
            if (n >= 0) {
                paintAt(n);
            }
            return;
        }
        if ("sv".equals(dragging)) {
            sat = clamp((mx - (x + pickX)) / (float) (pickW - 1));
            val = 1 - clamp((my - (y + pickY)) / (float) (pickH - 1));
        } else if ("hue".equals(dragging)) {
            hue = clamp((my - (y + pickY)) / (float) pickH);
            if (hue >= 1) {
                hue = 0.999f;
            }
        } else {
            return;
        }
        setActive(rgb(hue, sat, val));
    }

    /** Tooltips for the hats (their names, or how to get one) and the saved looks. */
    public void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        if (bench) {
            return; // the bench has no hat grid; its keys say what they do
        }
        List<String> ids = new ArrayList<>();
        ids.add("");
        ids.addAll(TotemHats.IDS);
        for (int k = 0; k < ids.size(); k++) {
            int cx = x + X + (k % 8) * (CELL + 2), cy = y + HAT_Y + (k / 8) * (CELL + 2);
            if (in(mouseX, mouseY, cx, cy, CELL, CELL)) {
                String id = ids.get(k);
                List<Component> tip = new ArrayList<>();
                tip.add(id.isEmpty() ? Component.translatable("gui.bsp_core.looks.no_hat") : Component.translatable("gui.bsp_core.looks.hat." + id));
                if (!id.isEmpty() && !owned().contains(id)) {
                    tip.add(Component.translatable("gui.bsp_core.looks.hat_locked").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                g.renderComponentTooltip(font, tip, mouseX, mouseY);
                return;
            }
        }
        if (dressing()) {
            for (int k = 0; k < LookOwner.MAX_SAVED; k++) {
                if (in(mouseX, mouseY, x + WELL_X + k * 21, y + SAVED_Y, 20, 14)) {
                    g.renderComponentTooltip(font, List.of(Component.translatable(k < ClientLooks.record().saved.size() ? "gui.bsp_core.looks.saved_tip" : "gui.bsp_core.looks.saved_empty")), mouseX, mouseY);
                    return;
                }
            }
            if (in(mouseX, mouseY, x + WELL_X + 105, y + SAVED_Y, 19, 14)) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.bsp_core.looks.keep_tip")), mouseX, mouseY);
            } else if (in(mouseX, mouseY, x + X + 118, y + 31, 56, 13)) {
                g.renderComponentTooltip(font, List.of(Component.translatable("gui.bsp_core.looks.paint_tip")), mouseX, mouseY);
            }
        }
    }

    // ------------------------------------------------------------------ small helpers

    private int gradAt(float t) {
        if (stopCount == 2) {
            return LookPixels.mix(stops[0], stops[1], t);
        }
        return t < 0.5f ? LookPixels.mix(stops[0], stops[1], t * 2) : LookPixels.mix(stops[1], stops[2], (t - 0.5f) * 2);
    }

    private int stopX(int gx, int k) {
        return gx + Math.round(k / (float) (stopCount - 1) * (W - 1));
    }

    private static String stopKey(int k) {
        return k == 0 ? "top" : k == 1 ? "middle" : "base";
    }

    /** A small picture of a saved look: its colour, or its gradient. */
    private static void swatchOf(GuiGraphics g, TotemLook l, int x, int y, int w, int h) {
        int[] c = l.colours();
        if (l.mode() == TotemLook.Mode.GRADIENT) {
            for (int i = 0; i < w; i++) {
                float t = i / (float) (w - 1);
                int col = c.length == 2 ? LookPixels.mix(c[0], c[1], t) : t < 0.5f ? LookPixels.mix(c[0], c[1], t * 2) : LookPixels.mix(c[1], c[2], (t - 0.5f) * 2);
                g.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | col);
            }
        } else {
            g.fill(x, y, x + w, y + h, 0xFF000000 | l.mainColour());
        }
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void rule(GuiGraphics g, int x, int y, int w, Component label) {
        g.fill(x, y + 3, x + w, y + 4, DIM);
        int lw = Math.round(font.width(label) * 0.75f);
        g.fill(x, y - 1, x + lw + 4, y + 8, BG);
        small(g, label, x, y, MUTED);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, boolean lit, int mouseX, int mouseY) {
        keyC(g, x, y, w, h, Component.literal(label), on, lit, mouseX, mouseY, TQ);
    }

    /** A key: lit in {@code litColour} when chosen, dim when it does nothing now. */
    private void keyC(GuiGraphics g, int x, int y, int w, int h, Component label, boolean on, boolean lit, int mouseX, int mouseY, int litColour) {
        boolean hot = on && in(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, lit ? litColour : on ? DIM : 0xFF1C2028);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hot ? HOVER : SLOT_BG);
        float k = Math.min(0.75f, (w - 4) / (float) Math.max(1, font.width(label)));
        int tw = Math.round(font.width(label) * k);
        g.pose().pushPose();
        g.pose().translate(x + (w - tw) / 2f, y + (h - 8 * k) / 2f + 0.5f, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(font, label, 0, 0, lit ? litColour : on ? TEXT : OFF, false);
        g.pose().popPose();
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Hue, saturation and value (0 to 1 each) to 0xRRGGBB. Not java.awt.Color: AWT must not load beside the game's window on macOS. */
    static int rgb(float h, float s, float v) {
        float hh = (h - (float) Math.floor(h)) * 6;
        int i = (int) Math.floor(hh);
        float f = hh - i, p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }

    /** 0xRRGGBB to hue, saturation and value. */
    static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) {
                h = ((g - b) / d) % 6;
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h /= 6;
            if (h < 0) {
                h += 1;
            }
        }
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }
}
