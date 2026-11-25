package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * BSP-Core's own map for a map box, shown when JourneyMap's map cannot be (owner decisions JR2 and JR3): while JourneyMap reloads after an
 * error, after it has given up, or when a JourneyMap update breaks the box. It is made to look and work like JourneyMap's full map, from
 * JourneyMap's own parts:
 * <ul>
 * <li>the map is JourneyMap's saved map pictures ({@link JmTiles}): day, night, and the cave layer the player is in when underground (or in
 * a dimension with a ceiling), so the colours, shading and night look are JourneyMap's;</li>
 * <li>JourneyMap's player marker, its mob face icons, its waypoint and death point icons and its toolbar's button pictures;</li>
 * <li>the chunk grid, zoom from an eighth of a pixel to 32 pixels a block, drag to pan, follow, the status line and the block under the
 * mouse;</li>
 * <li>JourneyMap's waypoints and death points ({@link JmWaypoints}): its keys B (a waypoint at the mouse), N (the waypoint manager), F
 * (follow), [ (day, night), = and - (zoom) and the arrows (pan); right-click makes or edits a waypoint. Every change is saved into
 * JourneyMap's own waypoint store.</li>
 * </ul>
 * The toolbar follows the Holo Grid's JourneyMap controls setting. Without JourneyMap installed (no saved pictures), the map is drawn from
 * the land loaded around the player, as before.
 */
public final class MapView {
    /** One map box's view, kept per box. */
    public static final class State {
        boolean follow = true;
        /** The map's centre when not following the player. */
        double cx, cz;
        int scale = 3;
        boolean night, caves = true, grid = true, mobs = true, animals = true, players = true;
        /** Where the box was last drawn (host GUI pixels), for keys and clicks. */
        int x, y, w, h;
        /** Overlays: 0 none, 1 the waypoint editor, 2 the waypoint manager. */
        int overlay;
        JmWaypoints.Wp editing;
        final String[] field = {"", "", "", ""};
        int focus, color = PALETTE[0], scroll;
        boolean enabled = true;
        String toast = "";
        long toastMs;
        /** Typed characters are dropped until then: the key that opened the editor also sends its letter. */
        long ignoreCharsUntil;
        /** When the box was last drawn: an overlay of a box not drawn now (another page, BUILD) takes no keys. */
        long drawnMs;
        /** BSP-Core's chunk map (without JourneyMap): blocks a pixel. */
        int zoom = 2;
    }

    /** GUI pixels a block, by zoom step. */
    private static final double[] SCALES = {1 / 8.0, 1 / 4.0, 1 / 2.0, 1, 2, 4, 8, 16, 32};
    private static final int[] PALETTE = {0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55, 0xFF55FFFF, 0xFF5577FF, 0xFFDD66FF, 0xFFFFFFFF};
    private static final int BTN = 14, BG_JM = 0xFF222222, GRID = 0x3A9AA8C8;
    private static final String[] TOOLS = {"day", "night", "caves", "", "monsters", "animals", "players", "", "grid", "waypoints", "", "follow", "zoomin", "zoomout"};
    private static final Map<ResourceLocation, Boolean> HAS = new HashMap<>();

    private MapView() {}

    private static boolean has(ResourceLocation l) {
        return HAS.computeIfAbsent(l, k -> Minecraft.getInstance().getResourceManager().getResource(k).isPresent());
    }

    private static ResourceLocation jm(String path) {
        return new ResourceLocation("journeymap", path);
    }

    private static double scale(State s) {
        return SCALES[Math.max(0, Math.min(SCALES.length - 1, s.scale))];
    }

    private static Vec3 eye(Minecraft mc) {
        return mc.player.getPosition(mc.getFrameTime()); // between ticks, so the map glides with the player
    }

    private static double centreX(State s, Minecraft mc) {
        return s.follow ? eye(mc).x : s.cx;
    }

    private static double centreZ(State s, Minecraft mc) {
        return s.follow ? eye(mc).z : s.cz;
    }

    private static boolean controls() {
        return BSPClientConfig.holoMapControls();
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the map box. {@code tools}: the box can be used here (the toolbar and overlays show, the mouse hovers). */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, State s, boolean tools, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || w < 8 || h < 8) {
            return;
        }
        s.x = x;
        s.y = y;
        s.w = w;
        s.h = h;
        s.drawnMs = System.currentTimeMillis();
        File dimDir = JmTiles.dimDir(mc.level.dimension());
        if (dimDir == null) {
            drawChunks(g, x, y, w, h, s, tools, mx, my);
            recoveringNote(g, x, y, w, h);
            return;
        }
        rect(g, x, y, w, h, BG_JM);
        double sc = scale(s), cx = centreX(s, mc), cz = centreZ(s, mc);
        g.flush();
        drawTiles(g, dimDir, mapType(s, mc, dimDir), x, y, w, h, cx, cz, sc);
        if (s.grid && sc >= 0.25) {
            drawGrid(g, x, y, w, h, cx, cz, sc);
        }
        drawWaypoints(g, mc, x, y, w, h, cx, cz, sc);
        drawEntities(g, mc, s, x, y, w, h, cx, cz, sc);
        drawSelf(g, mc, x, y, w, h, cx, cz, sc);
        boolean hover = tools && mx >= x && my >= y && mx < x + w && my < y + h;
        if (tools && controls()) {
            drawToolbar(g, s, x, y, w, mx, my);
        }
        if (tools) {
            status(g, mc, s, x, y, w, h, cx, cz, sc, hover ? mx : -1, hover ? my : -1);
        }
        recoveringNote(g, x, y, w, h);
        if (s.overlay == 1) {
            editor(g, s, mx, my);
        } else if (s.overlay == 2) {
            manager(g, mc, s, mx, my);
        }
        if (!s.toast.isEmpty() && System.currentTimeMillis() - s.toastMs < 2500) {
            float tw = smallWidth(s.toast);
            g.pose().pushPose();
            g.pose().translate(0, 0, 400); // over the editor and manager
            rect(g, (int) (x + w / 2f - tw / 2 - 4), y + h / 2 - 6, (int) tw + 8, 12, 0xE0101418);
            small(g, s.toast, x + w / 2f - tw / 2, y + h / 2f - 3, FG);
            g.pose().popPose();
        }
    }

    /** While JourneyMap's map is not shown because it failed: reloading, or stopped until the next login. */
    private static void recoveringNote(GuiGraphics g, int x, int y, int w, int h) {
        if (JourneyMapView.recovering()) {
            String note = tr(JourneyMapView.gaveUp() ? "gui.bsp_core.tracking.jm.stopped" : "gui.bsp_core.tracking.jm.reloading").getString();
            small(g, fitSmall(note, w - 8), x + 4, y + h - 8, GOLD);
        }
    }

    /** The picture type to draw: a cave layer underground (or under a ceiling) when caves are on, else day or night. */
    private static String mapType(State s, Minecraft mc, File dimDir) {
        if (s.caves) {
            BlockPos p = mc.player.blockPosition();
            boolean under = mc.level.dimensionType().hasCeiling()
                    || mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()) > p.getY() + 3 && !mc.level.canSeeSky(p.above());
            String layer = String.valueOf(p.getY() >> 4);
            if (under && new File(dimDir, layer).isDirectory()) {
                return layer; // JourneyMap saved this cave layer; else the surface, rather than nothing
            }
        }
        return s.night ? "night" : "day";
    }

    private static void drawTiles(GuiGraphics g, File dimDir, String type, int x, int y, int w, int h, double cx, double cz, double sc) {
        double minX = cx - w / 2.0 / sc, maxX = cx + w / 2.0 / sc, minZ = cz - h / 2.0 / sc, maxZ = cz + h / 2.0 / sc;
        int rx0 = Math.floorDiv((int) Math.floor(minX), JmTiles.REGION), rx1 = Math.floorDiv((int) Math.floor(maxX), JmTiles.REGION);
        int rz0 = Math.floorDiv((int) Math.floor(minZ), JmTiles.REGION), rz1 = Math.floorDiv((int) Math.floor(maxZ), JmTiles.REGION);
        if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > 900) {
            return; // never more pictures than the far zoom needs
        }
        boolean small = sc < 0.5;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                JmTiles.Tile t = JmTiles.tile(dimDir, type, rx, rz, small);
                if (t == null || t.id() == null) {
                    continue;
                }
                // the part of the region inside the box, in blocks
                double bx0 = Math.max(minX, rx * (double) JmTiles.REGION), bx1 = Math.min(maxX, (rx + 1) * (double) JmTiles.REGION);
                double bz0 = Math.max(minZ, rz * (double) JmTiles.REGION), bz1 = Math.min(maxZ, (rz + 1) * (double) JmTiles.REGION);
                if (bx1 <= bx0 || bz1 <= bz0) {
                    continue;
                }
                float sx0 = (float) (x + (bx0 - minX) * sc), sx1 = (float) (x + (bx1 - minX) * sc);
                float sy0 = (float) (y + (bz0 - minZ) * sc), sy1 = (float) (y + (bz1 - minZ) * sc);
                float u0 = (float) ((bx0 - rx * (double) JmTiles.REGION) / JmTiles.REGION), u1 = (float) ((bx1 - rx * (double) JmTiles.REGION) / JmTiles.REGION);
                float v0 = (float) ((bz0 - rz * (double) JmTiles.REGION) / JmTiles.REGION), v1 = (float) ((bz1 - rz * (double) JmTiles.REGION) / JmTiles.REGION);
                quad(g, t.id(), sx0, sy0, sx1, sy1, u0, v0, u1, v1);
            }
        }
    }

    /** A textured rectangle at fractional GUI places (smooth panning). */
    private static void quad(GuiGraphics g, ResourceLocation tex, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
        RenderSystem.enableBlend(); // a fill drawn in between turns blending off
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, x0, y0, 0).uv(u0, v0).endVertex();
        bb.vertex(m, x0, y1, 0).uv(u0, v1).endVertex();
        bb.vertex(m, x1, y1, 0).uv(u1, v1).endVertex();
        bb.vertex(m, x1, y0, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void drawGrid(GuiGraphics g, int x, int y, int w, int h, double cx, double cz, double sc) {
        double minX = cx - w / 2.0 / sc, minZ = cz - h / 2.0 / sc;
        for (double bx = Math.ceil(minX / 16) * 16; (bx - minX) * sc < w; bx += 16) {
            rect(g, (int) Math.round(x + (bx - minX) * sc), y, 1, h, GRID);
        }
        for (double bz = Math.ceil(minZ / 16) * 16; (bz - minZ) * sc < h; bz += 16) {
            rect(g, x, (int) Math.round(y + (bz - minZ) * sc), w, 1, GRID);
        }
    }

    private static float[] onMap(int x, int y, int w, int h, double cx, double cz, double sc, double wx, double wz) {
        return new float[] {(float) (x + w / 2.0 + (wx - cx) * sc), (float) (y + h / 2.0 + (wz - cz) * sc)};
    }

    private static boolean inside(float[] p, int x, int y, int w, int h, int margin) {
        return p[0] >= x + margin && p[1] >= y + margin && p[0] < x + w - margin && p[1] < y + h - margin;
    }

    private static void drawWaypoints(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, double cx, double cz, double sc) {
        ResourceLocation icon = jm("textures/waypoint/icon/waypoint-icon.png"), death = jm("textures/waypoint/icon/waypoint-death-icon.png");
        for (JmWaypoints.Wp wp : JmWaypoints.list(mc.level.dimension())) {
            if (!wp.enabled()) {
                continue;
            }
            float[] p = onMap(x, y, w, h, cx, cz, sc, wp.x() + 0.5, wp.z() + 0.5);
            if (!inside(p, x, y, w, h, 4)) {
                continue;
            }
            ResourceLocation tex = wp.death() ? death : icon;
            int col = wp.death() ? 0xFFFF3030 : 0xFF000000 | wp.color();
            if (has(tex)) {
                RenderSystem.setShaderColor(((col >> 16) & 255) / 255f, ((col >> 8) & 255) / 255f, (col & 255) / 255f, 1f);
                quad(g, tex, p[0] - 6, p[1] - 6, p[0] + 6, p[1] + 6, 0, 0, 1, 1);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                rect(g, (int) p[0] - 3, (int) p[1] - 3, 6, 6, BLACK);
                rect(g, (int) p[0] - 2, (int) p[1] - 2, 4, 4, col);
            }
            String name = fitSmall(wp.name(), 90);
            float nw = smallWidth(name);
            rect(g, (int) (p[0] - nw / 2 - 1), (int) p[1] + 7, (int) nw + 2, 7, 0x90000000);
            small(g, name, p[0] - nw / 2, p[1] + 7.5f, col == 0xFF000000 ? FG : col);
        }
    }

    private static void drawEntities(GuiGraphics g, Minecraft mc, State s, int x, int y, int w, int h, double cx, double cz, double sc) {
        float pt = mc.getFrameTime();
        double py = mc.player.getY();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !(e instanceof LivingEntity) || e instanceof net.minecraft.world.entity.decoration.ArmorStand || Math.abs(e.getY() - py) > 16) {
                continue; // as JourneyMap's map: what is near the player's height, not the caves far below
            }
            boolean player = e instanceof Player, mob = e instanceof Enemy, animal = e instanceof Animal || e instanceof WaterAnimal;
            if (player ? !s.players : mob ? !s.mobs : animal ? !s.animals : !s.animals) {
                continue;
            }
            Vec3 at = e.getPosition(pt);
            float[] p = onMap(x, y, w, h, cx, cz, sc, at.x, at.z);
            if (!inside(p, x, y, w, h, 6)) {
                continue;
            }
            if (e instanceof AbstractClientPlayer other) {
                PlayerFaceRenderer.draw(g, other.getSkinTextureLocation(), (int) p[0] - 5, (int) p[1] - 5, 10);
                String n = other.getGameProfile().getName();
                small(g, n, p[0] - smallWidth(n) / 2, p[1] + 6, FG);
                continue;
            }
            ResourceLocation icon = mobIcon(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
            if (icon != null) {
                quad(g, icon, p[0] - 6, p[1] - 6, p[0] + 6, p[1] + 6, 0, 0, 1, 1); // JourneyMap's face icon for this mob
            } else {
                disc(g, (int) p[0], (int) p[1], 3, BLACK);
                disc(g, (int) p[0], (int) p[1], 2, mob ? 0xFFE03A2A : 0xFFE8E8E8);
            }
        }
    }

    private static final Map<ResourceLocation, ResourceLocation> ICONS = new HashMap<>();

    /** JourneyMap's face icon for a mob: {@code entity_icon/<name>.png}, or in a folder of its own ({@code allay/allay.png}); null when none. */
    private static ResourceLocation mobIcon(ResourceLocation type) {
        if (type == null) {
            return null;
        }
        return ICONS.computeIfAbsent(type, t -> {
            String p = t.getPath();
            for (String c : new String[] {p, p + "/" + p, p.replace("_", ""), p + "/" + p.replace("_", ""), p.replace("_", "") + "/" + p.replace("_", "")}) {
                ResourceLocation l = new ResourceLocation(t.getNamespace(), "textures/entity_icon/" + c + ".png");
                if (has(l)) {
                    return l;
                }
            }
            return null;
        });
    }

    private static void drawSelf(GuiGraphics g, Minecraft mc, int x, int y, int w, int h, double cx, double cz, double sc) {
        Vec3 at = eye(mc);
        float[] p = onMap(x, y, w, h, cx, cz, sc, at.x, at.z);
        if (!inside(p, x, y, w, h, 0)) {
            return;
        }
        ResourceLocation bg = jm("ui/img/marker-player-bg-160.png"), fg = jm("ui/img/marker-player-160.png");
        g.pose().pushPose();
        g.pose().translate(p[0], p[1], 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getViewYRot(mc.getFrameTime()) + 180));
        if (has(bg) && has(fg)) {
            quad(g, bg, -7, -7, 7, 7, 0, 0, 1, 1);
            RenderSystem.setShaderColor(0.13f, 0.25f, 1f, 1f); // JourneyMap's own blue for the player
            quad(g, fg, -7, -7, 7, 7, 0, 0, 1, 1);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        } else {
            for (int r = 0; r < 6; r++) {
                rect(g, -r / 2, -3 + r, r + 1, 1, 0xFFFFFFFF);
            }
        }
        g.pose().popPose();
    }

    private static int toolbarWidth() {
        int n = 0;
        for (String t : TOOLS) {
            n += t.isEmpty() ? 4 : BTN + 1;
        }
        return n;
    }

    private static boolean toolOn(State s, String t) {
        return switch (t) {
            case "day" -> !s.night;
            case "night" -> s.night;
            case "caves" -> s.caves;
            case "monsters" -> s.mobs;
            case "animals" -> s.animals;
            case "players" -> s.players;
            case "grid" -> s.grid;
            case "follow" -> s.follow;
            case "waypoints" -> s.overlay == 2;
            default -> false;
        };
    }

    private static void drawToolbar(GuiGraphics g, State s, int x, int y, int w, int mx, int my) {
        int tw = toolbarWidth();
        if (tw > w - 8) {
            return; // too narrow: the keys still work
        }
        int bx = x + (w - tw) / 2, by = y + 3;
        rect(g, bx - 2, by - 2, tw + 3, BTN + 4, 0xC0202020); // JourneyMap's toolbar strip
        g.flush();
        String hovered = null;
        for (String t : TOOLS) {
            if (t.isEmpty()) {
                bx += 4;
                continue;
            }
            boolean on = toolOn(s, t), hover = mx >= bx && my >= by && mx < bx + BTN && my < by + BTN;
            ResourceLocation icon = jm("theme/flat/icon/" + t + ".png");
            if (has(icon)) {
                // JourneyMap's theme colours its white button and icon pictures: grey keys, bright icons when on
                rect(g, bx, by, BTN, BTN, hover ? 0xFF7A7A7A : on ? 0xFF5E5E5E : 0xFF484848);
                g.flush();
                float k = on || hover ? 1f : 0.62f;
                RenderSystem.setShaderColor(k, k, k, 1f);
                quad(g, icon, bx + 1, by + 1, bx + BTN - 1, by + BTN - 1, 0, 0, 1, 1);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            } else {
                Hud.key(g, bx, by, BTN, BTN, t.substring(0, 1).toUpperCase(), on, hover, TQ);
            }
            if (hover) {
                hovered = t;
            }
            bx += BTN + 1;
        }
        if (hovered != null) {
            String tip = tr("gui.bsp_core.tracking.jm.tool." + hovered).getString();
            float tipW = smallWidth(tip);
            float tx = Math.max(x + 2, Math.min(mx - tipW / 2, x + w - tipW - 2));
            rect(g, (int) tx - 2, by + BTN + 2, (int) tipW + 4, 9, 0xE0101418);
            small(g, tip, tx, by + BTN + 3.5f, FG);
        }
    }

    /** JourneyMap's status line (name, place, chunk height, biome, zoom) and the block under the mouse. */
    private static void status(GuiGraphics g, Minecraft mc, State s, int x, int y, int w, int h, double cx, double cz, double sc, int mx, int my) {
        BlockPos p = mc.player.blockPosition();
        String top = mc.player.getGameProfile().getName() + " ■ x: " + p.getX() + ", z: " + p.getZ() + ", y: " + p.getY() + " (" + (p.getY() >> 4) + ") "
                + biome(mc, p) + " ■ " + tr("gui.bsp_core.tracking.jm.zoom", zoomLabel(sc)).getString();
        int ty = y + (controls() && toolbarWidth() <= w - 8 ? 3 + BTN + 3 : 3);
        top = fitSmall(top, w - 8);
        float tw = smallWidth(top);
        rect(g, (int) (x + w / 2f - tw / 2 - 2), ty - 1, (int) tw + 4, 8, 0x90000000);
        small(g, top, x + w / 2f - tw / 2, ty, FG);
        if (mx >= 0 && s.overlay == 0) {
            int bx = (int) Math.floor(cx + (mx - x - w / 2.0) / sc), bz = (int) Math.floor(cz + (my - y - h / 2.0) / sc);
            String line;
            if (mc.level.hasChunk(bx >> 4, bz >> 4)) {
                int by = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
                BlockPos at = new BlockPos(bx, by, bz);
                line = mc.level.getBlockState(at).getBlock().getName().getString() + " ■ x: " + bx + ", z: " + bz + ", y: " + by + " (" + (by >> 4) + ") " + biome(mc, at);
            } else {
                line = "x: " + bx + ", z: " + bz;
            }
            line = fitSmall(line, w - 8);
            float lw = smallWidth(line);
            rect(g, (int) (x + w / 2f - lw / 2 - 2), y + h - 10, (int) lw + 4, 8, 0x90000000);
            small(g, line, x + w / 2f - lw / 2, y + h - 9, FG);
        }
    }

    private static String biome(Minecraft mc, BlockPos p) {
        return mc.level.getBiome(p).unwrapKey().map(k -> Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()).getString()).orElse("");
    }

    /** JourneyMap's "Map Zoom" number for this scale: 512 is one block a screen point. */
    private static String zoomLabel(double sc) {
        double hostScale, ratio;
        if (Popout.drawing) {
            hostScale = Popout.scale();
            ratio = Popout.pixelRatio();
        } else {
            var win = Minecraft.getInstance().getWindow();
            hostScale = win.getGuiScale();
            ratio = win.getScreenWidth() > 0 ? win.getWidth() / (double) win.getScreenWidth() : 1;
        }
        return String.valueOf(Math.max(1, Math.round(sc * hostScale / ratio * 512)));
    }

    // ------------------------------------------------------------------ the waypoint editor and manager

    private static int[] editorBox(State s) {
        int pw = Math.max(190, Math.min(s.w - 8, 230)), ph = 120; // never narrower than its keys need (it may reach past a small box)
        return new int[] {s.x + (s.w - pw) / 2, s.y + Math.max(2, (s.h - ph) / 2), pw, ph};
    }

    private static void field(GuiGraphics g, int x, int y, int w, String text, boolean focus) {
        well(g, x, y, w, 12, 0xFF0A0D12);
        if (focus) {
            outline(g, x, y, w, 12, TQ);
        }
        String shown = fit(text + (focus && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : ""), w - 6);
        text(g, shown, x + 3, y + 2, FG);
    }

    private static void editor(GuiGraphics g, State s, int mx, int my) {
        int[] b = editorBox(s);
        int x = b[0], y = b[1], w = b[2];
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        rect(g, s.x, s.y, s.w, s.h, 0x80000000);
        rect(g, x, y, w, b[3], 0xF0303030);
        outline(g, x, y, w, b[3], 0xFF8A8A8A);
        String title = tr(s.editing == null ? "gui.bsp_core.tracking.wp.new" : "gui.bsp_core.tracking.wp.edit").getString();
        text(g, title, x + w / 2 - font().width(title) / 2, y + 5, 0xFF55FFFF);
        small(g, tr("gui.bsp_core.tracking.wp.name").getString(), x + 8, y + 18, 0xFF55FFFF);
        field(g, x + 8, y + 25, w - 16, s.field[0], s.focus == 0);
        int fw = (w - 16 - 8) / 3;
        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < 3; i++) {
            small(g, axes[i], x + 8 + i * (fw + 4), y + 41, 0xFF55FFFF);
            field(g, x + 8 + i * (fw + 4), y + 48, fw, s.field[i + 1], s.focus == i + 1);
        }
        small(g, tr("gui.bsp_core.tracking.wp.color").getString(), x + 8, y + 66, 0xFF55FFFF);
        for (int i = 0; i < PALETTE.length; i++) {
            int sx = x + 8 + i * 13;
            rect(g, sx, y + 74, 11, 11, BLACK);
            rect(g, sx + 1, y + 75, 9, 9, PALETTE[i]);
            if ((PALETTE[i] & 0xFFFFFF) == (s.color & 0xFFFFFF)) {
                outline(g, sx - 1, y + 73, 13, 13, FG);
            }
        }
        int ex = x + w - 58;
        Hud.key(g, ex, y + 72, 50, 14, tr(s.enabled ? "gui.bsp_core.tracking.wp.on" : "gui.bsp_core.tracking.wp.off").getString(), s.enabled,
                in(mx, my, ex, y + 72, 50, 14), TQ);
        boolean can = JmWaypoints.writable();
        int ky = y + b[3] - 22;
        if (can) {
            Hud.key(g, x + 8, ky, 50, 14, tr("gui.bsp_core.tracking.wp.save").getString(), false, in(mx, my, x + 8, ky, 50, 14), TQ);
        } else {
            small(g, fitSmall(tr("gui.bsp_core.tracking.wp.readonly").getString(), w - 70), x + 8, ky + 4, GOLD);
        }
        Hud.key(g, x + w - 58, ky, 50, 14, tr("gui.bsp_core.tracking.wp.cancel").getString(), false, in(mx, my, x + w - 58, ky, 50, 14), TQ);
        if (can && s.editing != null) {
            Hud.key(g, x + w / 2 - 25, ky, 50, 14, tr("gui.bsp_core.tracking.wp.delete").getString(), false, in(mx, my, x + w / 2 - 25, ky, 50, 14), BAD);
        }
        g.pose().popPose();
    }

    private static void manager(GuiGraphics g, Minecraft mc, State s, int mx, int my) {
        int x = s.x + 6, y = s.y + 6, w = s.w - 12, h = s.h - 12;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        rect(g, x, y, w, h, 0xF0303030);
        outline(g, x, y, w, h, 0xFF8A8A8A);
        text(g, tr("gui.bsp_core.tracking.wp.manager").getString(), x + 6, y + 5, 0xFF55FFFF);
        Hud.key(g, x + w - 16, y + 3, 13, 13, "x", false, in(mx, my, x + w - 16, y + 3, 13, 13), TQ);
        List<JmWaypoints.Wp> list = JmWaypoints.list(mc.level.dimension());
        boolean can = JmWaypoints.writable();
        int rows = Math.max(1, (h - 24) / 13);
        s.scroll = Math.max(0, Math.min(s.scroll, Math.max(0, list.size() - rows)));
        if (list.isEmpty()) {
            small(g, tr("gui.bsp_core.tracking.wp.none").getString(), x + 8, y + 24, MUTED);
        }
        Vec3 me = mc.player.position();
        for (int i = 0; i < rows && i + s.scroll < list.size(); i++) {
            JmWaypoints.Wp wp = list.get(i + s.scroll);
            int ry = y + 20 + i * 13;
            if (in(mx, my, x + 2, ry, w - 4, 12)) {
                rect(g, x + 2, ry, w - 4, 12, 0x30FFFFFF);
            }
            rect(g, x + 6, ry + 3, 6, 6, wp.death() ? 0xFFFF3030 : 0xFF000000 | wp.color());
            String dist = (int) Math.sqrt(me.distanceToSqr(wp.x() + 0.5, wp.y(), wp.z() + 0.5)) + " m";
            int keysW = can ? 26 + 28 + 26 : 0;
            text(g, fit((wp.death() ? "☠ " : "") + wp.name(), w - 70 - keysW), x + 16, ry + 2, wp.enabled() ? FG : OFF);
            small(g, dist, x + w - 8 - keysW - smallWidth(dist) - 4, ry + 3, MUTED);
            if (can) {
                int kx = x + w - 6 - keysW;
                Hud.key(g, kx, ry, 24, 12, tr(wp.enabled() ? "gui.bsp_core.tracking.wp.on" : "gui.bsp_core.tracking.wp.off").getString(), wp.enabled(),
                        in(mx, my, kx, ry, 24, 12), TQ);
                Hud.key(g, kx + 26, ry, 26, 12, tr("gui.bsp_core.tracking.wp.edit_short").getString(), false, in(mx, my, kx + 26, ry, 26, 12), TQ);
                Hud.key(g, kx + 54, ry, 24, 12, "x", false, in(mx, my, kx + 54, ry, 24, 12), BAD);
            }
        }
        g.pose().popPose();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static void toast(State s, String key) {
        s.toast = tr(key).getString();
        s.toastMs = System.currentTimeMillis();
    }

    /** Opens the editor for a new waypoint at this block (JourneyMap's name: "x, z"), or for an existing one. */
    private static void edit(State s, JmWaypoints.Wp wp, int bx, int by, int bz) {
        s.overlay = 1;
        s.editing = wp;
        s.focus = 0;
        if (wp == null) {
            s.field[0] = bx + ", " + bz;
            s.field[1] = String.valueOf(bx);
            s.field[2] = String.valueOf(by);
            s.field[3] = String.valueOf(bz);
            s.color = PALETTE[(int) (Math.random() * PALETTE.length)];
            s.enabled = true;
        } else {
            s.field[0] = wp.name();
            s.field[1] = String.valueOf(wp.rawX()); // its stored position, as JourneyMap's editor shows it
            s.field[2] = String.valueOf(wp.y());
            s.field[3] = String.valueOf(wp.rawZ());
            s.color = 0xFF000000 | wp.color();
            s.enabled = wp.enabled();
        }
    }

    private static void save(State s) {
        Minecraft mc = Minecraft.getInstance();
        int x, y, z;
        try {
            x = Integer.parseInt(s.field[1].trim());
            y = Integer.parseInt(s.field[2].trim());
            z = Integer.parseInt(s.field[3].trim());
        } catch (NumberFormatException e) {
            toast(s, "gui.bsp_core.tracking.wp.bad_number");
            return;
        }
        String name = s.field[0].trim().isEmpty() ? x + ", " + z : s.field[0].trim();
        boolean ok = s.editing == null ? JmWaypoints.create(name, new BlockPos(x, y, z), mc.level.dimension(), s.color, s.enabled)
                : JmWaypoints.update(s.editing, name, x, y, z, s.color, s.enabled);
        toast(s, ok ? "gui.bsp_core.tracking.wp.saved" : "gui.bsp_core.tracking.wp.failed");
        if (ok) {
            s.overlay = 0;
        }
    }

    /** The block under a place in the box: x, surface y (or the player's), z. */
    private static int[] blockAt(State s, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        double sc = scale(s);
        int bx = (int) Math.floor(centreX(s, mc) + (mx - s.x - s.w / 2.0) / sc), bz = (int) Math.floor(centreZ(s, mc) + (my - s.y - s.h / 2.0) / sc);
        int by = mc.level.hasChunk(bx >> 4, bz >> 4) ? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) : mc.player.getBlockY();
        return new int[] {bx, by, bz};
    }

    /** The waypoint drawn at this place in the box, or null. */
    private static JmWaypoints.Wp waypointAt(State s, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        double sc = scale(s), cx = centreX(s, mc), cz = centreZ(s, mc);
        for (JmWaypoints.Wp wp : JmWaypoints.list(mc.level.dimension())) {
            float[] p = onMap(s.x, s.y, s.w, s.h, cx, cz, sc, wp.x() + 0.5, wp.z() + 0.5);
            if (wp.enabled() && Math.abs(mx - p[0]) <= 6 && Math.abs(my - p[1]) <= 6) {
                return wp;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    /** True while the box's waypoint editor or manager is open: it takes every key. */
    public static boolean takesKeys(State s) {
        return s.overlay != 0 && System.currentTimeMillis() - s.drawnMs < 250; // only while it can be seen
    }

    /** A click in the box: true when it was taken (a key, an overlay, a waypoint); false starts a drag. */
    public static boolean click(State s, double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return true;
        }
        if (JmTiles.dimDir(mc.level.dimension()) == null) {
            return chunkClick(s, mx, my);
        }
        if (s.overlay == 1) {
            editorClick(s, mx, my);
            return true;
        }
        if (s.overlay == 2) {
            managerClick(s, mx, my);
            return true;
        }
        if (controls() && toolbarWidth() <= s.w - 8 && my >= s.y + 3 && my < s.y + 3 + BTN) {
            int bx = s.x + (s.w - toolbarWidth()) / 2;
            for (String t : TOOLS) {
                if (t.isEmpty()) {
                    bx += 4;
                    continue;
                }
                if (mx >= bx && mx < bx + BTN) {
                    tool(s, t);
                    return true;
                }
                bx += BTN + 1;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            JmWaypoints.Wp wp = waypointAt(s, mx, my);
            int[] b = blockAt(s, mx, my);
            edit(s, wp, b[0], b[1], b[2]);
            return true;
        }
        return false;
    }

    private static void tool(State s, String t) {
        switch (t) {
            case "day" -> s.night = false;
            case "night" -> s.night = true;
            case "caves" -> s.caves = !s.caves;
            case "monsters" -> s.mobs = !s.mobs;
            case "animals" -> s.animals = !s.animals;
            case "players" -> s.players = !s.players;
            case "grid" -> s.grid = !s.grid;
            case "waypoints" -> s.overlay = s.overlay == 2 ? 0 : 2;
            case "follow" -> s.follow = true;
            case "zoomin" -> s.scale = Math.min(SCALES.length - 1, s.scale + 1);
            case "zoomout" -> s.scale = Math.max(0, s.scale - 1);
            default -> {
            }
        }
    }

    private static void editorClick(State s, double mx, double my) {
        int[] b = editorBox(s);
        int x = b[0], y = b[1], w = b[2];
        if (in(mx, my, x + 8, y + 25, w - 16, 12)) {
            s.focus = 0;
            return;
        }
        int fw = (w - 16 - 8) / 3;
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, x + 8 + i * (fw + 4), y + 48, fw, 12)) {
                s.focus = i + 1;
                return;
            }
        }
        for (int i = 0; i < PALETTE.length; i++) {
            if (in(mx, my, x + 8 + i * 13, y + 74, 11, 11)) {
                s.color = PALETTE[i];
                return;
            }
        }
        if (in(mx, my, x + w - 58, y + 72, 50, 14)) {
            s.enabled = !s.enabled;
            return;
        }
        int ky = y + b[3] - 22;
        if (JmWaypoints.writable() && in(mx, my, x + 8, ky, 50, 14)) {
            save(s);
        } else if (in(mx, my, x + w - 58, ky, 50, 14)) {
            s.overlay = 0;
        } else if (JmWaypoints.writable() && s.editing != null && in(mx, my, x + w / 2 - 25, ky, 50, 14)) {
            toast(s, JmWaypoints.delete(s.editing) ? "gui.bsp_core.tracking.wp.deleted" : "gui.bsp_core.tracking.wp.failed");
            s.overlay = 0;
        }
    }

    private static void managerClick(State s, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int x = s.x + 6, y = s.y + 6, w = s.w - 12, h = s.h - 12;
        if (in(mx, my, x + w - 16, y + 3, 13, 13)) {
            s.overlay = 0;
            return;
        }
        List<JmWaypoints.Wp> list = JmWaypoints.list(mc.level.dimension());
        boolean can = JmWaypoints.writable();
        int rows = Math.max(1, (h - 24) / 13), keysW = can ? 26 + 28 + 26 : 0;
        for (int i = 0; i < rows && i + s.scroll < list.size(); i++) {
            JmWaypoints.Wp wp = list.get(i + s.scroll);
            int ry = y + 20 + i * 13, kx = x + w - 6 - keysW;
            if (can && in(mx, my, kx, ry, 24, 12)) {
                JmWaypoints.update(wp, wp.name(), wp.rawX(), wp.y(), wp.rawZ(), wp.color(), !wp.enabled());
                return;
            }
            if (can && in(mx, my, kx + 26, ry, 26, 12)) {
                edit(s, wp, 0, 0, 0);
                return;
            }
            if (can && in(mx, my, kx + 54, ry, 24, 12)) {
                toast(s, JmWaypoints.delete(wp) ? "gui.bsp_core.tracking.wp.deleted" : "gui.bsp_core.tracking.wp.failed");
                return;
            }
            if (in(mx, my, x + 2, ry, w - 4, 12)) {
                s.follow = false; // the map goes to it, as JourneyMap's manager does
                s.cx = wp.x() + 0.5;
                s.cz = wp.z() + 0.5;
                s.overlay = 0;
                return;
            }
        }
    }

    /** A drag on the map: it pans and stops following the player. */
    public static void drag(State s, double dx, double dy) {
        if (s.overlay != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (JmTiles.worldDir() == null) {
            s.cx -= dx * s.zoom; // BSP-Core's chunk map: its centre is an offset from the player
            s.cz -= dy * s.zoom;
            return;
        }
        if (s.follow) {
            s.follow = false;
            s.cx = mc.player.getX();
            s.cz = mc.player.getZ();
        }
        double sc = scale(s);
        s.cx -= dx / sc;
        s.cz -= dy / sc;
    }

    public static void scroll(State s, double delta) {
        if (s.overlay == 2) {
            s.scroll = Math.max(0, s.scroll - (int) Math.signum(delta));
            return;
        }
        if (s.overlay != 0) {
            return;
        }
        if (JmTiles.worldDir() == null) {
            s.zoom = delta > 0 ? Math.max(1, s.zoom / 2) : Math.min(8, s.zoom * 2);
            return;
        }
        s.scale = Math.max(0, Math.min(SCALES.length - 1, s.scale + (delta > 0 ? 1 : -1)));
    }

    /**
     * A key for the box (the mouse over it, or an overlay open): JourneyMap's map keys, or typing in the waypoint editor. True when taken.
     * {@code mx}, {@code my}: the mouse in host GUI pixels.
     */
    public static boolean key(State s, int key, int scan, int mods, double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || JmTiles.worldDir() == null) {
            return false;
        }
        if (s.overlay == 1) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> s.overlay = 0;
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (JmWaypoints.writable()) {
                        save(s);
                    }
                }
                case GLFW.GLFW_KEY_TAB -> s.focus = (s.focus + ((mods & GLFW.GLFW_MOD_SHIFT) != 0 ? 3 : 1)) % 4;
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    String f = s.field[s.focus];
                    if (!f.isEmpty()) {
                        s.field[s.focus] = f.substring(0, f.length() - 1);
                    }
                }
                default -> {
                }
            }
            return true; // every key is the editor's while it is open
        }
        if (s.overlay == 2) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_N -> s.overlay = 0;
                case GLFW.GLFW_KEY_UP -> s.scroll = Math.max(0, s.scroll - 1);
                case GLFW.GLFW_KEY_DOWN -> s.scroll++;
                default -> {
                }
            }
            return true;
        }
        double sc = scale(s);
        switch (key) {
            case GLFW.GLFW_KEY_B -> {
                int[] b = blockAt(s, mx, my);
                edit(s, null, b[0], b[1], b[2]);
                s.ignoreCharsUntil = System.currentTimeMillis() + 150; // the B's own letter, which arrives next
            }
            case GLFW.GLFW_KEY_N -> s.overlay = 2;
            case GLFW.GLFW_KEY_F -> s.follow = true;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> s.night = !s.night;
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> s.scale = Math.min(SCALES.length - 1, s.scale + 1);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> s.scale = Math.max(0, s.scale - 1);
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                drag(s, key == GLFW.GLFW_KEY_LEFT ? 16 * sc : key == GLFW.GLFW_KEY_RIGHT ? -16 * sc : 0,
                        key == GLFW.GLFW_KEY_UP ? 16 * sc : key == GLFW.GLFW_KEY_DOWN ? -16 * sc : 0); // 16 blocks, as JourneyMap
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** A typed character for the box's waypoint editor; true when taken. */
    public static boolean charTyped(State s, char c) {
        if (s.overlay != 1) {
            return s.overlay != 0;
        }
        if (System.currentTimeMillis() < s.ignoreCharsUntil) {
            s.ignoreCharsUntil = 0;
            return true;
        }
        if (c < ' ' || c == '§') {
            return true;
        }
        if (s.focus == 0 ? s.field[0].length() < 48 : (Character.isDigit(c) || c == '-') && s.field[s.focus].length() < 9) {
            s.field[s.focus] += c;
        }
        return true;
    }

    /** Opens JourneyMap's full map, as its J key does; nothing when JourneyMap is absent or changed. */
    public static void openJourneyMap() {
        if (!ModList.get().isLoaded("journeymap")) {
            return;
        }
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            Object instance = ui.getField("INSTANCE").get(null);
            ui.getMethod("getOrOpenFullscreenMap").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BSPCore.LOGGER.warn("BSP-Core could not open JourneyMap's map ({})", e.toString());
        }
    }

    // ------------------------------------------------------------------ without JourneyMap: the land loaded round the player

    private static final Map<Long, int[]> CHUNKS = new HashMap<>();
    private static final Map<Long, Long> READ = new HashMap<>();
    /** One texture per map box (its state), made again only when the box's size changes; the old one is released. */
    private static final Map<Object, Cached> TEXTURES = new java.util.WeakHashMap<>();
    private static final Object CENTRED = new Object();
    private static int made;

    private static final class Cached {
        final DynamicTexture tex;
        final ResourceLocation id;
        final int w, h;
        long builtMs;
        String key = "";

        Cached(int w, int h) {
            this.w = w;
            this.h = h;
            tex = new DynamicTexture(w, h, false);
            id = new ResourceLocation(BSPCore.MODID, "holo_map_" + (made++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
        }
    }

    private static Cached texture(Object owner, int w, int h) {
        Cached c = TEXTURES.get(owner);
        if (c != null && c.w == w && c.h == h) {
            return c;
        }
        if (c != null) {
            Minecraft.getInstance().getTextureManager().release(c.id);
        }
        c = new Cached(w, h);
        TEXTURES.put(owner, c);
        return c;
    }

    /** Without JourneyMap: the map of loaded land with the player's arrow and the zoom, centre and J keys. */
    private static void drawChunks(GuiGraphics g, int x, int y, int w, int h, State s, boolean tools, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        rect(g, x, y, w, h, BLACK);
        Cached c = texture(s, w, h);
        double cx = mc.player.getX() + s.cx, cz = mc.player.getZ() + s.cz;
        String key = Math.round(cx / s.zoom) + "," + Math.round(cz / s.zoom) + "," + s.zoom + "," + mc.level.dimension().location();
        long now = System.currentTimeMillis();
        if ((!key.equals(c.key) && now - c.builtMs > 200) || now - c.builtMs > 1000) {
            build(mc.level, c, cx, cz, s.zoom);
            c.key = key;
            c.builtMs = now;
        }
        g.blit(c.id, x, y, 0, 0, w, h, w, h);
        int ax = x + w / 2 + (int) Math.round(-s.cx / s.zoom), ay = y + h / 2 + (int) Math.round(-s.cz / s.zoom);
        if (ax >= x && ax < x + w && ay >= y && ay < y + h) {
            g.pose().pushPose();
            g.pose().translate(ax, ay, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getYRot() + 180));
            for (int r = 0; r < 6; r++) {
                rect(g, -r / 2, -3 + r, r + 1, 1, 0xFFFFFFFF);
            }
            rect(g, 0, -2, 1, 4, 0xFFE03A2A);
            g.pose().popPose();
        }
        if (tools) {
            String[] keys = {"+", "-", "o", ModList.get().isLoaded("journeymap") ? "J" : ""};
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].isEmpty()) {
                    continue;
                }
                int kx = x + w - 13, ky = y + 3 + i * 13;
                rect(g, kx, ky, 11, 11, 0xD8141414);
                outline(g, kx, ky, 11, 11, mx >= kx && mx < kx + 11 && my >= ky && my < ky + 11 ? 0xFFE8E8E8 : 0xFF777777);
                small(g, keys[i], kx + 5.5f - smallWidth(keys[i]) / 2, ky + 3, 0xFFFFFFFF);
            }
        }
    }

    private static boolean chunkClick(State s, double mx, double my) {
        for (int i = 0; i < 4; i++) {
            int kx = s.x + s.w - 13, ky = s.y + 3 + i * 13;
            if (mx >= kx && mx < kx + 11 && my >= ky && my < ky + 11) {
                switch (i) {
                    case 0 -> s.zoom = Math.max(1, s.zoom / 2);
                    case 1 -> s.zoom = Math.min(8, s.zoom * 2);
                    case 2 -> {
                        s.cx = 0;
                        s.cz = 0;
                    }
                    default -> openJourneyMap();
                }
                return true;
            }
        }
        return false;
    }

    /** A map centred on a block (the transmitter's screen), {@code zoom} blocks per pixel, with nothing drawn over it. */
    public static void drawCentred(GuiGraphics g, int x, int y, int w, int h, double cx, double cz, int zoom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || w < 8 || h < 8) {
            return;
        }
        Cached c = texture(CENTRED, w, h);
        String key = Math.round(cx) + "," + Math.round(cz) + "," + zoom + "," + mc.level.dimension().location();
        long now = System.currentTimeMillis();
        if (!key.equals(c.key) || now - c.builtMs > 2000) {
            build(mc.level, c, cx, cz, zoom);
            c.key = key;
            c.builtMs = now;
        }
        g.blit(c.id, x, y, 0, 0, w, h, w, h);
    }

    private static void build(ClientLevel level, Cached c, double cx, double cz, int zoom) {
        NativeImage img = c.tex.getPixels();
        if (img == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int py = 0; py < c.h; py++) {
            for (int px = 0; px < c.w; px++) {
                int bx = (int) Math.floor(cx + (px - c.w / 2.0) * zoom), bz = (int) Math.floor(cz + (py - c.h / 2.0) * zoom);
                img.setPixelRGBA(px, py, abgr(colour(level, bx, bz, now)));
            }
        }
        c.tex.upload();
    }

    /** The colour of one column of blocks, from its chunk's cached colours (read again after five seconds). */
    private static int colour(ClientLevel level, int bx, int bz, long now) {
        int chx = bx >> 4, chz = bz >> 4;
        long key = ((long) chx << 32) ^ (chz & 0xFFFFFFFFL);
        int[] cols = CHUNKS.get(key);
        Long read = READ.get(key);
        if (cols == null || read == null || now - read > 5000) {
            if (!level.hasChunk(chx, chz)) {
                return cols == null ? 0xFF101418 : cols[(bz & 15) * 16 + (bx & 15)];
            }
            cols = readChunk(level, chx, chz);
            CHUNKS.put(key, cols);
            READ.put(key, now);
            if (CHUNKS.size() > 16384) {
                CHUNKS.clear();
                READ.clear();
            }
        }
        return cols[(bz & 15) * 16 + (bx & 15)];
    }

    private static int[] readChunk(ClientLevel level, int chx, int chz) {
        int[] out = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = (chx << 4) + x, wz = (chz << 4) + z;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                m.set(wx, y, wz);
                BlockState st = level.getBlockState(m);
                MapColor mc = st.getMapColor(level, m);
                int rgb = mc == MapColor.NONE ? 0x101418 : mc.col;
                int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
                float shade = y > north ? 1.12f : y < north ? 0.82f : 1f;
                if (!st.getFluidState().isEmpty()) {
                    shade = 1f;
                }
                out[z * 16 + x] = 0xFF000000 | scaleColour(rgb, shade);
            }
        }
        return out;
    }

    private static int scaleColour(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** ARGB to the ABGR NativeImage stores. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /** Forgets the cached colours, JourneyMap's pictures and waypoints (on leaving a world). */
    public static void clear() {
        CHUNKS.clear();
        READ.clear();
        JmTiles.clear();
        JmWaypoints.clear();
    }
}
