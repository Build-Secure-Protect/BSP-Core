package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The totem's upgrade tree ("Tier Rings"): the totem in the centre, one ring per totem tier, and the
 * six paths as straight spokes. An upgrade sits on the ring of the tier it appears at; rings the
 * totem has not reached are dim. A node is dark while locked, outlined once it can be bought, lit in
 * its branch colour when it has levels and filled when maxed. Beside the tree a panel shows the
 * selected upgrade (level, what it gives now and next, the exact coin and XP price) and, under it,
 * the price of raising the totem's tier. Shared by the held-totem and placed-totem screens.
 */
public final class TotemTree {
    public static final int W = 170, H = 170, DETAIL_W = 128, BUY_Y = 108, BTN_H = 16, GATE_Y = 144, GATE_BTN_X = 78, GATE_BTN_W = 50, WALLET_H = 16;
    private static final int CX = 85, CY = 85, NODE = 14;
    private static final int[] RING = {26, 44, 62, 80};
    /** Spokes are spread wide enough that neighbouring nodes on the innermost ring do not touch. */
    private static final float[] SPOKE = {-128f, -52f, 12f, 78f, 180f, 129f};
    private static final int SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, RING_ON = 0xFF27566B, TQ = 0xFF19D3B0, XP = 0x8FE04A, MUTED = 0x9AA3B5, BAD = 0xFF6B5C;
    /** Branch colours: Carried (XP green), Base (coin gold), Raid (red). */
    private static final int[] COLOUR = {0xFF8FE04A, 0xFFFFD23A, 0xFFFF6B5C};
    private static final Map<Buff, int[]> POS = new EnumMap<>(Buff.class);

    static {
        for (Buff b : Buff.values()) {
            double a = Math.toRadians(SPOKE[b.path]);
            int r = RING[Math.min(b.tier, RING.length - 1)];
            POS.put(b, new int[]{CX + (int) Math.round(Math.cos(a) * r), CY + (int) Math.round(Math.sin(a) * r)});
        }
    }

    public Buff selected = Buff.MINING_SPEED;

    public static int colour(Buff buff) {
        return COLOUR[buff.branch().ordinal()];
    }

    private static String abbr(Buff buff) {
        return buff.key.substring(0, 2).toUpperCase(Locale.ROOT);
    }

    private static void dots(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2);
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps, y = y0 + (y1 - y0) * i / steps;
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    // ------------------------------------------------------------------ the tree

    public void render(GuiGraphics g, Font font, int ox, int oy, ToIntFunction<Buff> level, int tier) {
        g.fill(ox, oy, ox + W, oy + H, SLOT_BG);
        for (int i = 0; i < RING.length; i++) {
            int colour = tier >= i ? RING_ON : DIM;
            for (int a = 0; a < 360; a += 3) {
                double rad = Math.toRadians(a);
                int x = ox + CX + (int) Math.round(Math.cos(rad) * RING[i]), y = oy + CY + (int) Math.round(Math.sin(rad) * RING[i]);
                g.fill(x, y, x + 1, y + 1, colour);
            }
            String label = TotemUpgrades.roman(i);
            int lw = font.width(label) * 3 / 4;
            g.fill(ox + CX - lw / 2 - 2, oy + CY - RING[i] - 3, ox + CX + lw / 2 + 2, oy + CY - RING[i] + 4, SLOT_BG);
            small(g, font, Component.literal(label), ox + CX - lw / 2, oy + CY - RING[i] - 3, tier >= i ? 0x6FB7C9 : 0x4A5160);
        }
        // spokes: from the totem, or from the previous upgrade on the path, out to each node
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            Buff parent = b.parent();
            int[] from = parent == null ? new int[]{CX, CY} : POS.get(parent);
            dots(g, ox + from[0], oy + from[1], ox + p[0], oy + p[1], level.applyAsInt(b) > 0 ? TQ : DIM);
        }
        g.fill(ox + CX - 8, oy + CY - 8, ox + CX + 8, oy + CY + 8, TQ);
        String t = TotemUpgrades.roman(tier);
        g.drawString(font, t, ox + CX - font.width(t) / 2, oy + CY - 4, 0x0B0D11, false);
        for (Buff b : Buff.values()) {
            int[] p = POS.get(b);
            int x = ox + p[0] - NODE / 2, y = oy + p[1] - NODE / 2, lvl = level.applyAsInt(b), c = colour(b);
            boolean open = TotemUpgrades.unlocked(b, level, tier), maxed = lvl >= b.maxLevel();
            if (b == selected) {
                g.fill(x - 2, y - 2, x + NODE + 2, y + NODE + 2, 0xFFFFFFFF);
                g.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, SLOT_BG);
            }
            g.fill(x, y, x + NODE, y + NODE, lvl > 0 ? c : open ? (c & 0x00FFFFFF) | 0x90000000 : DIM);
            g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, maxed ? c : SLOT_BG);
            String s = abbr(b);
            g.drawString(font, s, x + NODE / 2 - font.width(s) / 2 + 1, y + 3, maxed ? 0x0B0D11 : lvl > 0 || open ? c & 0xFFFFFF : 0x4A5160, false);
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

    // ------------------------------------------------------------------ text helpers

    private static void small(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static int wrapped(GuiGraphics g, Font font, Component text, int x, int y, int colour, int maxLines) {
        int n = 0;
        for (FormattedCharSequence line : font.split(text, (int) (DETAIL_W / 0.75f))) {
            if (n++ >= maxLines) {
                break;
            }
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, colour, false);
            g.pose().popPose();
            y += 8;
        }
        return y;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private static long pct(double fraction) {
        return Math.round(fraction * 100);
    }

    /** What the upgrade gives at {@code level}, read from the live config. */
    public static Component effect(Buff b, int level) {
        if (level <= 0) {
            return Component.translatable("gui.bsp_core.tree.not_bought");
        }
        String key = "buff.bsp_core." + b.key + ".effect";
        return switch (b) {
            case DAMAGE -> Component.translatable(key, fmt(BSPConfig.DAMAGE_PER_LEVEL.get() * level));
            case RESISTANCE -> Component.translatable(key, pct(Math.min(0.9, BSPConfig.RESISTANCE_PER_LEVEL.get() * level)));
            case MINING_SPEED -> Component.translatable(key, pct(BSPConfig.MINING_SPEED_PER_LEVEL.get() * level));
            case SWIFTNESS -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.SWIFTNESS_BONUS.get(), level, 0.0)));
            case VITALITY -> Component.translatable(key, fmt(BSPConfig.levelValue(BSPConfig.VITALITY_HEALTH.get(), level, 0) / 2.0));
            case FEATHERFALL -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.FEATHERFALL_REDUCTION.get(), level, 0.0)));
            case NIGHT_SIGHT -> Component.translatable(key);
            case FORTIFY -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.FORTIFY_EXPLOSION_PROTECTION.get(), level, 0.0)), b.radius(level));
            case HEALING -> Component.translatable(key, fmt(BSPConfig.levelValue(BSPConfig.HEALING_PER_SECOND.get(), level, 0.0)), b.radius(level));
            case WARD -> Component.translatable(key, (level - 1) / 2 + 1, b.reach(level));
            case ALARM, SANCTUARY -> Component.translatable(key, b.reach(level));
            case DEADLOCK -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.DEADLOCK_SECONDS.get(), level, 0));
            case OVERCLOCK -> Component.translatable(key, pct(BSPConfig.levelValue(BSPConfig.OVERCLOCK_BONUS.get(), level, 0.0)), b.reach(level));
            case LOCKPICK -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.LOCKPICK_SECONDS.get(), level, 0));
            case SHROUD -> Component.translatable(key, BSPConfig.levelValue(BSPConfig.SHROUD_SECONDS.get(), level, 0));
            case ANCHOR -> Component.translatable(key, com.mrgregles.bsp_core.chunk.ChunkLoading.chunksAt(level));
            case SURVEY -> Component.translatable(key, com.mrgregles.bsp_core.chunk.ChunkLoading.radiusAt(level) * 2 + 1);
        };
    }

    /** In creative nothing is charged, so prices and the wallet read "free" instead of numbers. */
    private static boolean creative() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player != null && player.isCreative();
    }

    /** Draws a price as the coin's own icon, a count and the XP levels. Returns the x just past it. */
    private static int price(GuiGraphics g, Font font, int x, int y, TotemUpgrades.Price price) {
        if (creative()) {
            Component free = Component.translatable("gui.bsp_core.tree.free");
            g.drawString(font, free, x, y + 5, XP, false);
            return x + font.width(free);
        }
        g.renderItem(new ItemStack(price.coin().coin()), x, y);
        String n = "x" + price.coins();
        g.drawString(font, n, x + 17, y + 5, 0xE8EAF0, false);
        int nx = x + 19 + font.width(n);
        String xp = "+ " + price.xp() + " XP";
        g.drawString(font, xp, nx + 2, y + 5, XP, false);
        return nx + 2 + font.width(xp);
    }

    private static void button(GuiGraphics g, Font font, int x, int y, int w, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BTN_H;
        g.fill(x, y, x + w, y + BTN_H, enabled ? TQ : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, hover ? 0xFF14362F : SLOT_BG);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 4, enabled ? TQ & 0xFFFFFF : 0x6B7385, false);
    }

    // ------------------------------------------------------------------ the side panel

    /**
     * Draws the panel for the selected upgrade at (x, y).
     *
     * @param blocked why the next level cannot be bought here, or null
     * @param button  label of the action button
     * @param enabled whether the button can be pressed
     */
    public void detail(GuiGraphics g, Font font, int x, int y, int level, int tier, @Nullable Component blocked, Component button, boolean enabled, int mouseX, int mouseY) {
        Buff b = selected;
        g.drawString(font, Component.translatable(b.translationKey()), x, y, colour(b) & 0xFFFFFF, false);
        small(g, font, Component.translatable("gui.bsp_core.tree.kind." + b.branch().name().toLowerCase(Locale.ROOT), TotemUpgrades.roman(b.tier)), x, y + 10, MUTED);
        int cap = b.cap(tier);
        for (int i = 0; i < b.maxLevel(); i++) {
            g.fill(x + i * 7, y + 19, x + i * 7 + 5, y + 24, i < level ? colour(b) : i < cap ? 0xFF4A5160 : DIM);
        }
        small(g, font, Component.translatable(cap < b.maxLevel() && cap > 0 ? "gui.bsp_core.tree.level_cap" : "gui.bsp_core.tree.level", level, b.maxLevel(), cap), x, y + 27, MUTED);
        small(g, font, Component.translatable("gui.bsp_core.tree.now"), x, y + 37, MUTED);
        wrapped(g, font, effect(b, level), x, y + 45, 0xE8EAF0, 2);
        TotemUpgrades.Price price = TotemUpgrades.price(b, level);
        if (price != null) {
            small(g, font, Component.translatable("gui.bsp_core.tree.next"), x, y + 63, MUTED);
            wrapped(g, font, effect(b, level + 1), x, y + 71, 0xE8EAF0, 2);
            price(g, font, x, y + 89, price);
        }
        button(g, font, x, y + BUY_Y, DETAIL_W, button, enabled, mouseX, mouseY);
        if (blocked != null) {
            wrapped(g, font, blocked, x, y + BUY_Y + BTN_H + 3, BAD, 2);
        }
    }

    /** Draws the "raise the totem's tier" box under the upgrade panel. */
    public void gate(GuiGraphics g, Font font, int x, int y, int tier, Component button, boolean enabled, int mouseX, int mouseY) {
        g.fill(x - 3, y + GATE_Y - 3, x + DETAIL_W + 3, y + GATE_Y + 32, SLOT_BG);
        TotemUpgrades.Price price = TotemUpgrades.gatePrice(tier);
        if (price == null) {
            small(g, font, Component.translatable("gui.bsp_core.tree.gate.max"), x, y + GATE_Y + 10, MUTED);
            return;
        }
        small(g, font, Component.translatable("gui.bsp_core.tree.gate", TotemUpgrades.roman(tier + 1)), x, y + GATE_Y, MUTED);
        if (creative()) {
            g.drawString(font, Component.translatable("gui.bsp_core.tree.free"), x, y + GATE_Y + 15, XP, false);
        } else {
            g.renderItem(new ItemStack(price.coin().coin()), x, y + GATE_Y + 10);
            g.drawString(font, "x" + price.coins(), x + 17, y + GATE_Y + 9, 0xE8EAF0, false);
            g.drawString(font, price.xp() + " XP", x + 17, y + GATE_Y + 19, XP, false);
        }
        button(g, font, x + GATE_BTN_X, y + GATE_Y + 11, GATE_BTN_W, button, enabled, mouseX, mouseY);
    }

    public static boolean overBuy(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + DETAIL_W && mouseY >= y + BUY_Y && mouseY < y + BUY_Y + BTN_H;
    }

    public static boolean overGate(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x + GATE_BTN_X && mouseX < x + GATE_BTN_X + GATE_BTN_W && mouseY >= y + GATE_Y + 11 && mouseY < y + GATE_Y + 11 + BTN_H;
    }

    /** XP levels and how many of each Shatter Coin the player carries, in one row. */
    public static void wallet(GuiGraphics g, Font font, int x, int y, Player player) {
        if (player.isCreative()) {
            g.drawString(font, Component.translatable("gui.bsp_core.tree.creative"), x, y + 4, XP, false);
            return;
        }
        String xp = "XP " + player.experienceLevel;
        g.drawString(font, xp, x, y + 4, XP, false);
        int cx = x + 44;
        for (CoinTier tier : CoinTier.values()) {
            g.renderItem(new ItemStack(tier.coin()), cx, y);
            g.drawString(font, String.valueOf(player.getInventory().countItem(tier.coin())), cx + 17, y + 4, 0xE8EAF0, false);
            cx += 52;
        }
    }
}
