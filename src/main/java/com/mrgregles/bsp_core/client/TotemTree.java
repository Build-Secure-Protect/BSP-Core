package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.totem.ShatterTotemItem;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The totem's upgrade tree ("Constellation"): the totem in the centre and the three branches
 * (Carried, Base, Raid) fanning out from it. A node lights up in its branch colour once it has a
 * level and fills in when it is maxed. Clicking a node selects it; the detail panel beside the tree
 * shows what it does now, what the next level gives and what that costs. Shared by the held-totem
 * screen and the placed-totem screen, which decide what can be bought and how.
 */
public final class TotemTree {
    public static final int W = 190, H = 165, DETAIL_W = 108, BUY_Y = 104, BUY_H = 16;
    private static final int CX = 95, CY = 82, NODE = 16;
    private static final int SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, MUTED = 0x9AA3B5;
    /** Branch colours: Carried (XP green), Base (coin gold), Raid (red). */
    private static final int[] COLOUR = {0xFF8FE04A, 0xFFFFD23A, 0xFFFF6B5C};
    private static final float[] CENTRE = {-90f, 30f, 178f}, SPREAD = {150f, 150f, 50f};
    private static final Map<Buff, int[]> POS = new EnumMap<>(Buff.class);

    static {
        for (TotemUpgrades.Branch branch : TotemUpgrades.Branch.values()) {
            List<Buff> list = new ArrayList<>();
            for (Buff b : Buff.values()) {
                if (b.branch() == branch) {
                    list.add(b);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                float t = list.size() > 1 ? i / (float) (list.size() - 1) - 0.5f : 0;
                double a = Math.toRadians(CENTRE[branch.ordinal()] + t * SPREAD[branch.ordinal()]);
                int r = i % 2 == 0 ? 39 : 59;
                POS.put(list.get(i), new int[]{CX + (int) Math.round(Math.cos(a) * r), CY + (int) Math.round(Math.sin(a) * r)});
            }
        }
    }

    public Buff selected = Buff.DAMAGE;

    public static int colour(Buff buff) {
        return COLOUR[buff.branch().ordinal()];
    }

    private static String abbr(Buff buff) {
        return buff.key.substring(0, 2).toUpperCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------ the tree

    public void render(GuiGraphics g, Font font, int ox, int oy, ToIntFunction<Buff> level, int mouseX, int mouseY) {
        g.fill(ox, oy, ox + W, oy + H, SLOT_BG);
        // dotted links from the totem to each node
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            int colour = level.applyAsInt(b) > 0 ? TQ : DIM;
            int steps = Math.max(Math.abs(p[0] - CX), Math.abs(p[1] - CY)) / 3;
            for (int i = 1; i < steps; i++) {
                int x = ox + CX + (p[0] - CX) * i / steps, y = oy + CY + (p[1] - CY) * i / steps;
                g.fill(x, y, x + 1, y + 1, colour);
            }
        }
        g.fill(ox + CX - 8, oy + CY - 8, ox + CX + 8, oy + CY + 8, TQ);
        g.drawString(font, "T", ox + CX - 2, oy + CY - 4, 0x0B0D11, false);
        for (TotemUpgrades.Branch branch : TotemUpgrades.Branch.values()) {
            double a = Math.toRadians(CENTRE[branch.ordinal()]);
            Component label = Component.translatable("gui.bsp_core.tree.branch." + branch.name().toLowerCase(Locale.ROOT));
            int lx = ox + CX + (int) Math.round(Math.cos(a) * 76) - font.width(label) * 3 / 8, ly = oy + CY + (int) Math.round(Math.sin(a) * 76) - 3;
            small(g, font, label, Math.max(ox + 2, Math.min(ox + W - 2 - font.width(label) * 3 / 4, lx)), Math.max(oy + 2, Math.min(oy + H - 8, ly)), COLOUR[branch.ordinal()] & 0xFFFFFF);
        }
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            int x = ox + p[0] - NODE / 2, y = oy + p[1] - NODE / 2, lvl = level.applyAsInt(b), c = colour(b);
            boolean maxed = lvl >= b.maxLevel();
            if (b == selected) {
                g.fill(x - 2, y - 2, x + NODE + 2, y + NODE + 2, 0xFFFFFFFF);
                g.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, SLOT_BG);
            }
            g.fill(x, y, x + NODE, y + NODE, lvl > 0 ? c : DIM);
            g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, maxed ? c : SLOT_BG);
            String s = abbr(b);
            g.drawString(font, s, x + NODE / 2 - font.width(s) / 2 + 1, y + 4, maxed ? 0x0B0D11 : lvl > 0 ? c & 0xFFFFFF : 0x6B7385, false);
        }
    }

    /** The upgrade whose node is under the mouse, or null. */
    @Nullable
    public Buff nodeAt(double mouseX, double mouseY, int ox, int oy) {
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            if (mouseX >= ox + p[0] - NODE / 2 && mouseX < ox + p[0] + NODE / 2 && mouseY >= oy + p[1] - NODE / 2 && mouseY < oy + p[1] + NODE / 2) {
                return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the detail panel

    private static void small(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
        for (FormattedCharSequence line : font.split(text, (int) (DETAIL_W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            y += 8;
        }
        return y;
    }

    /** What the upgrade gives at {@code level}, read from the live config. */
    public static Component effect(Buff b, int level) {
        if (level <= 0) {
            return Component.translatable("gui.bsp_core.tree.not_bought");
        }
        String key = "buff.bsp_core." + b.key + ".effect";
        return switch (b) {
            case DAMAGE -> Component.translatable(key, fmt(ShatterTotemItem.DAMAGE_PER_LEVEL * level));
            case RESISTANCE -> Component.translatable(key, level);
            case MINING_SPEED -> Component.translatable(key, Math.round(ShatterTotemItem.MINING_SPEED_PER_LEVEL * level * 100));
            case SWIFTNESS -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.SWIFTNESS_BONUS.get(), level, 0.0)));
            case VITALITY -> Component.translatable(key, fmt(BSPConfig.levelValue(BSPConfig.VITALITY_HEALTH.get(), level, 0) / 2.0));
            case FEATHERFALL -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.FEATHERFALL_REDUCTION.get(), level, 0.0)));
            case NIGHT_SIGHT -> Component.translatable(key);
            case FORTIFY -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.FORTIFY_EXPLOSION_PROTECTION.get(), level, 0.0)), b.radius(level));
            case HEALING -> Component.translatable(key, fmt(BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), level, 0.0)), b.radius(level));
            case WARD -> Component.translatable(key, level, b.reach(level));
            case ALARM, SANCTUARY -> Component.translatable(key, b.reach(level));
            case DEADLOCK -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.DEADLOCK_SECONDS.get(), level, 0));
            case OVERCLOCK -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.OVERCLOCK_BONUS.get(), level, 0.0)), b.reach(level));
            case LOCKPICK -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.LOCKPICK_SECONDS.get(), level, 0));
            case SHROUD -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.SHROUD_SECONDS.get(), level, 0));
        };
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static long pct(double fraction) {
        return Math.round(fraction * 100);
    }

    /**
     * Draws the detail panel for the selected upgrade.
     *
     * @param blocked why it cannot be bought here, or null if it can
     * @param button  label of the action button, or null for none
     * @param enabled whether the button can be pressed
     */
    public void detail(GuiGraphics g, Font font, int x, int y, int level, @Nullable Component blocked, @Nullable Component button, boolean enabled, int mouseX, int mouseY) {
        Buff b = selected;
        int c = colour(b) & 0xFFFFFF;
        g.drawString(font, Component.translatable(b.translationKey()), x, y, c, false);
        small(g, font, Component.translatable("gui.bsp_core.tree.kind." + b.branch().name().toLowerCase(Locale.ROOT)), x, y + 10, MUTED);
        for (int i = 0; i < b.maxLevel(); i++) {
            g.fill(x + i * 7, y + 19, x + i * 7 + 5, y + 24, i < level ? colour(b) : DIM);
        }
        small(g, font, Component.translatable("gui.bsp_core.tree.now"), x, y + 29, MUTED);
        int ny = wrapped(g, font, effect(b, level), x, y + 37, 0xE8EAF0);
        if (level < b.maxLevel()) {
            small(g, font, Component.translatable("gui.bsp_core.tree.next"), x, ny + 3, MUTED);
            wrapped(g, font, effect(b, level + 1), x, ny + 11, 0xE8EAF0);
        }
        if (button != null) {
            boolean hover = enabled && over(mouseX, mouseY, x, y);
            g.fill(x, y + BUY_Y, x + DETAIL_W, y + BUY_Y + BUY_H, enabled ? TQ : DIM);
            g.fill(x + 1, y + BUY_Y + 1, x + DETAIL_W - 1, y + BUY_Y + BUY_H - 1, hover ? 0xFF14362F : SLOT_BG);
            g.drawString(font, button, x + DETAIL_W / 2 - font.width(button) / 2, y + BUY_Y + 4, enabled ? TQ & 0xFFFFFF : 0x6B7385, false);
        }
        if (blocked != null) {
            wrapped(g, font, blocked, x, y + BUY_Y + BUY_H + 4, 0xFF6B5C);
        }
    }

    /** Whether the mouse is over the action button of a detail panel drawn at (x, y). */
    public static boolean over(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + DETAIL_W && mouseY >= y + BUY_Y && mouseY < y + BUY_Y + BUY_H;
    }

    /** "Buy: 15 XP", "Buy: 16 coin value" or "Maxed". */
    public static Component buyLabel(Buff b, int level) {
        int cost = b.costToUpgrade(level);
        if (cost < 0) {
            return Component.translatable("gui.bsp_core.upgrades.maxed");
        }
        return Component.translatable(b.currency == TotemUpgrades.Currency.XP ? "gui.bsp_core.upgrades.buy_cost" : "gui.bsp_core.upgrades.buy_coins", cost);
    }
}
