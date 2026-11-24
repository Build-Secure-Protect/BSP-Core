package com.mrgregles.bsp_core.client.tracking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Drawing helpers for machine tracking's screens, in the Holo HUD palette of BSP-Core's machine screens (dark glass, turquoise, gold) and
 * the steel of the Holo Table: raised keys, round lamps and keys, wells, screws, knurled knobs, text at full and three-quarter size.
 */
public final class Hud {
    public static final int BG = 0xF010151C, GLASS = 0xFF0C0E12, TQ = 0xFF19D3B0, TQ_D = 0xFF0E7A66, DIM = 0xFF2A2F3A, FG = 0xFFE8EAF0, MUTED = 0xFF9AA3B5,
            OFF = 0xFF565C6B, GOLD = 0xFFFFD23A, BAD = 0xFFD63B2F, VIO = 0xFFB58CFF, STEEL = 0xFF2B313C, STEEL_L = 0xFF596273, STEEL_D = 0xFF151A21, BLACK = 0xFF05070A,
            GREEN = 0xFF3DFF6E, COPPER = 0xFFD27D46, DIAMOND = 0xFF5FE3E3;

    private Hud() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    /** A sunken well: dark inside, shadow on the top and left, light on the bottom and right. */
    public static void well(GuiGraphics g, int x, int y, int w, int h, int inside) {
        rect(g, x, y, w, h, inside);
        rect(g, x, y, w, 1, 0xFF000000);
        rect(g, x, y, 1, h, 0xFF000000);
        rect(g, x, y + h - 1, w, 1, 0xFF2B3240);
        rect(g, x + w - 1, y, 1, h, 0xFF2B3240);
    }

    /** A raised key; {@code on} pressed in and lit in {@code col}. */
    public static void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, boolean hover, int col) {
        rect(g, x, y, w, h, BLACK);
        int face = on ? darken(col, 0.28f) : hover ? 0xFF26303B : 0xFF1C222C;
        rect(g, x + 1, y + 1, w - 2, h - 2, face);
        rect(g, x + 1, y + 1, w - 2, 1, on ? darken(col, 0.18f) : 0xFF39414F);
        rect(g, x + 1, y + 1, 1, h - 2, on ? darken(col, 0.18f) : 0xFF39414F);
        rect(g, x + 1, y + h - 2, w - 2, 1, 0xFF0E1117);
        rect(g, x + w - 2, y + 1, 1, h - 2, 0xFF0E1117);
        if (on) {
            outline(g, x, y, w, h, col);
        }
        if (!label.isEmpty()) {
            small(g, label, x + w / 2f - smallWidth(label) / 2f, y + (h - 6) / 2f, on ? col : FG);
        }
    }

    /** A filled disc. */
    public static void disc(GuiGraphics g, int cx, int cy, int r, int c) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.floor(Math.sqrt(r * r - dy * dy + 0.5));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, c);
        }
    }

    /** A round lamp with a glint. */
    public static void lamp(GuiGraphics g, int cx, int cy, int r, int col, boolean on) {
        disc(g, cx, cy, r + 1, BLACK);
        disc(g, cx, cy, r, on ? col : darken(col, 0.25f));
        if (on && r >= 2) {
            rect(g, cx - r / 2, cy - r / 2, 1, 1, 0x8CFFFFFF);
        }
    }

    /** A round key with a highlight; lit in turquoise when on. */
    public static void roundKey(GuiGraphics g, int cx, int cy, int r, String label, boolean on, boolean present) {
        disc(g, cx, cy, r + 1, BLACK);
        disc(g, cx, cy, r, on ? TQ : present ? 0xFF39414F : 0xFF1A1F27);
        rect(g, cx - r / 2, cy - r + 1, r / 2 + 1, 1, on ? 0xFFB8FFF0 : present ? 0xFF4A5262 : 0xFF1A1F27);
        small(g, label, cx - smallWidth(label) / 2f + 0.5f, cy - 2.5f, on ? BLACK : present ? FG : OFF);
    }

    public static void screw(GuiGraphics g, int x, int y) {
        disc(g, x, y, 2, 0xFF4A505C);
        disc(g, x, y, 1, 0xFF8A909C);
        rect(g, x - 1, y, 3, 1, 0xFF2A2F3A);
    }

    /** A knurled knob with a value in its middle. */
    public static void knob(GuiGraphics g, int cx, int cy, String value) {
        disc(g, cx, cy, 10, BLACK);
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            rect(g, (int) Math.round(cx + Math.cos(a) * 9) - 1, (int) Math.round(cy + Math.sin(a) * 9) - 1, 2, 2, STEEL_L);
        }
        disc(g, cx, cy, 7, 0xFF3A414E);
        disc(g, cx - 1, cy - 1, 5, 0xFF4A5262);
        small(g, value, cx - smallWidth(value) / 2f, cy - 2.5f, TQ);
    }

    /** A plate of steel with corner screws. */
    public static void plate(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, STEEL);
        rect(g, x, y, w, 1, STEEL_L);
        rect(g, x, y + h - 1, w, 1, STEEL_D);
        screw(g, x + 5, y + 5);
        screw(g, x + w - 6, y + 5);
        screw(g, x + 5, y + h - 6);
        screw(g, x + w - 6, y + h - 6);
    }

    /** A segmented bar (horizontal when wider than tall). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float f, int col) {
        well(g, x, y, w, h, GLASS);
        f = Math.max(0, Math.min(1, f));
        if (h > w) {
            int fh = Math.round((h - 2) * f);
            rect(g, x + 1, y + h - 1 - fh, w - 2, fh, col);
            for (int i = y + h - 4; i > y; i -= 3) {
                rect(g, x + 1, i, w - 2, 1, 0x73000000);
            }
        } else {
            int fw = Math.round((w - 2) * f);
            rect(g, x + 1, y + 1, fw, h - 2, col);
            for (int i = x + 3; i < x + w - 1; i += 4) {
                rect(g, i, y + 1, 1, h - 2, 0x73000000);
            }
        }
    }

    // ------------------------------------------------------------------ text

    public static void text(GuiGraphics g, String s, int x, int y, int col) {
        g.drawString(font(), s, x, y, col & 0xFFFFFF, false);
    }

    /** Three-quarter size text. */
    public static void small(GuiGraphics g, String s, float x, float y, int col) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font(), s, 0, 0, col & 0xFFFFFF, false);
        g.pose().popPose();
    }

    public static float smallWidth(String s) {
        return font().width(s) * 0.75f;
    }

    /** {@code s} cut with an ellipsis to fit {@code max} pixels at three-quarter size. */
    public static String fitSmall(String s, float max) {
        if (smallWidth(s) <= max) {
            return s;
        }
        while (s.length() > 1 && smallWidth(s + "…") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    public static String fit(String s, int max) {
        if (font().width(s) <= max) {
            return s;
        }
        while (s.length() > 1 && font().width(s + "…") > max) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    /** 1234 → 1.2k, 1234567 → 1.2M. */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return String.format("%.1fT", v / 1e12);
        if (a >= 1e9) return String.format("%.1fG", v / 1e9);
        if (a >= 1e6) return String.format("%.1fM", v / 1e6);
        if (a >= 1e4) return String.format("%.1fk", v / 1e3);
        return String.valueOf((long) v);
    }

    public static int darken(int argb, float k) {
        int a = argb >>> 24, r = (argb >> 16) & 255, gg = (argb >> 8) & 255, b = argb & 255;
        return (a << 24) | (Math.min(255, (int) (r * k)) << 16) | (Math.min(255, (int) (gg * k)) << 8) | Math.min(255, (int) (b * k));
    }

    // ------------------------------------------------------------------ tags

    /** A tag's name as the player sees it: theirs, or the block's own. */
    public static String tagName(CompoundTag tag) {
        String name = tag.getString("name");
        if (!name.isEmpty()) {
            return name;
        }
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(tag.getString("block")));
        return block == null ? tag.getString("block") : block.getName().getString();
    }

    /** "survival-1 · Overworld" for a tag (no coordinates, for streamers). */
    public static String where(CompoundTag tag) {
        String dim = tag.getString("dim");
        String world = switch (dim) {
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "The Nether";
            case "minecraft:the_end" -> "The End";
            default -> dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
        };
        String server = tag.getString("server");
        return server.isEmpty() ? world : server + " · " + world;
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
