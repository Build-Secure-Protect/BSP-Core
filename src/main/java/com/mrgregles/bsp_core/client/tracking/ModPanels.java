package com.mrgregles.bsp_core.client.tracking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * A machine's box on the Holo Grid, the popout and the pinned box, in the owner's pick "Mod Look" (P8): a small copy of the mod's own grey
 * screen, with its slot frames, bar colours and words, laid out to fit the box's shape. Drawn from the latest reading
 * ({@link ClientTracking#reading}); an asleep, gone or offline machine shows its last reading dimmed with a line saying why.
 */
public final class ModPanels {
    private static final int PROGRESS = 0xFF36C636, ENERGY = 0xFFE03030;
    private static int GREY = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADE = 0xFF555555, INK = 0xFF404040, SLOT = 0xFF8B8B8B, SLOT_D = 0xFF373737;

    /** The pinned box's glass look (owner's pick P10 "Glass"): see-through panel, light ink, one turquoise line along the top. */
    static void glass(boolean on) {
        if (on) {
            GREY = 0x8C0C1014;
            LIGHT = 0x9919D3B0;
            SHADE = 0x40000000;
            INK = 0xFFE8EAF0;
            SLOT = 0x66000000;
            SLOT_D = 0x66000000;
        } else {
            GREY = 0xFFC6C6C6;
            LIGHT = 0xFFFFFFFF;
            SHADE = 0xFF555555;
            INK = 0xFF404040;
            SLOT = 0xFF8B8B8B;
            SLOT_D = 0xFF373737;
        }
    }

    private ModPanels() {}

    /**
     * Draws the box for one tag at {@code x, y} of size {@code w, h}. {@code operable}: the player may click it to open the machine's
     * own screen (inside a transmitter's range with the unlock); otherwise it shows the eye of a read-only box.
     */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, CompoundTag tag, CompoundTag reading, boolean operable, boolean hover) {
        boolean clip = !Popout.drawing; // the popout draws off-screen at its own size, where the game window's scissor would be wrong
        if (clip) {
            g.enableScissor(x, y, x + w, y + h);
        } else {
            g.flush();
            Popout.scissor(x, y, w, h);
        }
        try {
            panel(g, x, y, w, h);
            byte st = reading.getByte("st");
            boolean live = st == 0 && reading.contains("block");
            String name = tagName(tag);
            small(g, fitSmall(name, w - 50), x + 4, y + 3, INK);
            if (operable && st == 0) {
                rect(g, x + w - 44, y + 2, 42, 9, 0xFF0E3A33);
                outline(g, x + w - 44, y + 2, 42, 9, TQ);
                small(g, "OPERATE", x + w - 4 - smallWidth("OPERATE"), y + 3.5f, TQ);
            } else {
                eye(g, x + w - 11, y + 4, 0xFF555555);
            }
            if (reading.contains("block")) {
                body(g, x + 3, y + 12, w - 6, h - 15, reading);
            }
            if (st != 0) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 400); // over the item icons of the last reading
                rect(g, x + 1, y + 12, w - 2, h - 13, GREY == 0xFFC6C6C6 ? 0xA0C6C6C6 : 0xA00C1014);
                String why = switch (st) {
                    case 1 -> tr("gui.bsp_core.tracking.asleep").getString();
                    case 2 -> tr("gui.bsp_core.tracking.gone").getString();
                    case 3 -> tr("gui.bsp_core.tracking.no_access").getString();
                    case 4 -> tr("gui.bsp_core.tracking.offline", tag.getString("server")).getString();
                    default -> tr("gui.bsp_core.tracking.waiting", tag.getString("server")).getString();
                };
                small(g, fitSmall(why, w - 8), x + 4, y + h / 2f - 3, 0xFF7A2020);
                if (reading.contains("liveMs")) {
                    long ago = Math.max(0, (System.currentTimeMillis() - reading.getLong("liveMs")) / 60_000L);
                    small(g, fitSmall(tr("gui.bsp_core.tracking.last_seen", ago).getString(), w - 8), x + 4, y + h / 2f + 5, INK);
                }
                g.pose().popPose();
            } else if (!live) {
                small(g, tr("gui.bsp_core.tracking.reading").getString(), x + 4, y + h / 2f - 3, INK);
            }
            if (hover) {
                outline(g, x, y, w, h, TQ);
            }
        } finally {
            if (clip) {
                g.disableScissor();
            } else {
                g.flush();
                com.mojang.blaze3d.systems.RenderSystem.disableScissor();
            }
        }
    }

    /** The grey panel with its light top-left and dark bottom-right edges. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, GREY);
        rect(g, x, y, w, 1, LIGHT);
        rect(g, x, y, 1, h, LIGHT);
        rect(g, x, y + h - 1, w, 1, SHADE);
        rect(g, x + w - 1, y, 1, h, SHADE);
    }

    private static void eye(GuiGraphics g, int x, int y, int c) {
        rect(g, x + 1, y + 1, 5, 1, c);
        rect(g, x, y + 2, 7, 1, c);
        rect(g, x + 1, y + 3, 5, 1, c);
        rect(g, x + 3, y + 2, 1, 1, GREY);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        rect(g, x, y, 18, 18, SLOT);
        rect(g, x, y, 18, 1, SLOT_D);
        rect(g, x, y, 1, 18, SLOT_D);
        rect(g, x + 17, y, 1, 18, LIGHT);
        rect(g, x, y + 17, 18, 1, LIGHT);
    }

    private static void item(GuiGraphics g, int x, int y, String id, int n) {
        Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (it == null) {
            return;
        }
        ItemStack st = new ItemStack(it, Math.max(1, Math.min(n, 999)));
        g.renderItem(st, x, y);
        if (n > 1) {
            String s = compact(n);
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.drawString(font(), s, x + 17 - font().width(s), y + 9, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    private static void vbar(GuiGraphics g, int x, int y, int w, int h, float f, int col) {
        rect(g, x, y, w, h, SHADE);
        int fh = Math.round((h - 2) * Math.max(0, Math.min(1, f)));
        rect(g, x + 1, y + h - 1 - fh, w - 2, fh, col);
    }

    private static void hbar(GuiGraphics g, int x, int y, int w, int h, float f, int col) {
        rect(g, x, y, w, h, SHADE);
        rect(g, x + 1, y + 1, Math.round((w - 2) * Math.max(0, Math.min(1, f))), h - 2, col);
    }

    /** The reading's own drawing, by panel kind. */
    private static void body(GuiGraphics g, int x, int y, int w, int h, CompoundTag r) {
        CompoundTag xr = r.getCompound("x");
        String kind = r.getString("kind");
        int right = x + w;
        boolean energy = r.contains("feMax") && r.getLong("feMax") > 0;
        if (energy && !kind.equals("mek_matrix")) {
            vbar(g, right - 7, y, 6, Math.max(8, h), r.getLong("fe") / (float) r.getLong("feMax"), ENERGY);
            right -= 9;
        }
        int inner = right - x;
        switch (kind) {
            case "mek_factory" -> {
                ListTag procs = xr.getList("procs", Tag.TAG_INT);
                int n = procs.size();
                int colW = Math.max(8, Math.min(20, inner / Math.max(1, n)));
                for (int i = 0; i < n; i++) {
                    vbar(g, x + i * colW + colW / 2 - 3, y, 6, Math.max(8, h - (h > 40 ? 20 : 0)), procs.getInt(i) / 100f, PROGRESS);
                }
                if (h > 40) {
                    items(g, x, y + h - 18, inner, 18, r);
                }
                mode(g, x, y, inner, xr);
            }
            case "mek_machine", "bsp_machine" -> {
                hbar(g, x, y, inner, 6, xr.getFloat("progress"), PROGRESS);
                items(g, x, y + 8, inner, h - 8, r);
                mode(g, x, y + 8, inner, xr);
            }
            case "mek_fission" -> fission(g, x, y, inner, h, xr);
            case "mek_matrix" -> {
                double cap = xr.getDouble("capacity");
                hbar(g, x, y, inner, Math.min(14, Math.max(6, h / 3)), cap > 0 ? (float) (xr.getDouble("energy") / cap) : 0, ENERGY);
                int ty = y + Math.min(14, Math.max(6, h / 3)) + 3;
                small(g, compact(xr.getDouble("energy") / 2.5) + " / " + compact(cap / 2.5) + " FE", x, ty, INK);
                small(g, "In " + compact(xr.getDouble("in") / 2.5) + " FE/t", x, ty + 8, 0xFF2A6A2A);
                small(g, "Out " + compact(xr.getDouble("out") / 2.5) + " FE/t", x, ty + 16, 0xFF8A2A2A);
            }
            default -> {
                int used = tanks(g, right, y, h, r);
                items(g, x, y, inner - used, h, r);
            }
        }
    }

    /** The redstone mode and active lamp of a Mekanism machine, in the bottom right. */
    private static void mode(GuiGraphics g, int x, int y, int w, CompoundTag xr) {
        if (xr.contains("active")) {
            lamp(g, x + w - 4, y + 3, 2, xr.getBoolean("active") || xr.getBoolean("working") ? GREEN : 0xFF8A2020, true);
        }
    }

    /** As many of the reading's item slots as fit, in a grid. */
    private static void items(GuiGraphics g, int x, int y, int w, int h, CompoundTag r) {
        ListTag items = r.getList("items", Tag.TAG_COMPOUND);
        int slots = Math.max(items.size(), Math.min(r.getInt("slots"), 54));
        int cols = Math.max(1, w / 18), rows = Math.max(0, h / 18);
        int shown = Math.min(slots, cols * rows);
        for (int i = 0; i < shown; i++) {
            slot(g, x + (i % cols) * 18, y + (i / cols) * 18);
        }
        int k = 0;
        for (int i = 0; i < items.size(); i++) {
            CompoundTag it = items.getCompound(i);
            int s = r.getInt("slots") > shown ? k++ : it.getShort("s");
            if (s < shown) {
                item(g, x + (s % cols) * 18 + 1, y + (s / cols) * 18 + 1, it.getString("id"), it.getInt("n"));
            }
        }
    }

    /** Fluid and gas tanks as thin bars down the right side; returns the width used. */
    private static int tanks(GuiGraphics g, int right, int y, int h, CompoundTag r) {
        ListTag fl = r.getList("tanks", Tag.TAG_COMPOUND);
        ListTag gas = r.getCompound("x").getList("gas", Tag.TAG_COMPOUND);
        int used = 0;
        for (ListTag list : new ListTag[] {fl, gas}) {
            for (int i = 0; i < list.size() && used < 40; i++) {
                CompoundTag t = list.getCompound(i);
                long max = t.contains("max") ? t.getLong("max") : 0;
                long amount = t.contains("mb") ? t.getInt("mb") : t.getLong("amount");
                if (max <= 0) {
                    continue;
                }
                used += 7;
                vbar(g, right - used, y, 6, Math.max(8, h), amount / (float) max, list == fl ? 0xFF3A8BFF : 0xFFB0B0B0);
            }
        }
        return used + (used > 0 ? 2 : 0);
    }

    private static void fission(GuiGraphics g, int x, int y, int w, int h, CompoundTag xr) {
        ListTag tanks = xr.getList("tanks", Tag.TAG_COMPOUND);
        int bw = Math.max(6, Math.min(14, (w - 70) / Math.max(1, tanks.size()) - 3));
        int tx = x;
        for (int i = 0; i < tanks.size(); i++) {
            CompoundTag t = tanks.getCompound(i);
            String role = t.getString("role");
            int col = switch (role) {
                case "fuel" -> 0xFF7AD04A;
                case "fluidCoolant", "gasCoolant" -> 0xFF3A8BFF;
                case "heatedCoolant" -> 0xFFD8D8D8;
                default -> 0xFF7A4A1A;
            };
            vbar(g, tx, y, bw, Math.max(10, h - 2), t.getLong("max") > 0 ? t.getLong("amount") / (float) t.getLong("max") : 0, col);
            tx += bw + 3;
        }
        int lx = tx + 3;
        String[] lines = {
                tr(xr.getBoolean("active") ? "gui.bsp_core.tracking.mek.active" : "gui.bsp_core.tracking.mek.idle").getString(),
                tr("gui.bsp_core.tracking.mek.temp", Math.round(xr.getDouble("temp"))).getString(),
                tr("gui.bsp_core.tracking.mek.damage", xr.getLong("damage")).getString(),
                tr("gui.bsp_core.tracking.mek.burn", String.format("%.1f", xr.getDouble("burn"))).getString()};
        for (int i = 0; i < lines.length && 8 * i + 7 < h; i++) {
            small(g, fitSmall(lines[i], x + w - lx), lx, y + 1 + 8 * i, i == 0 ? (xr.getBoolean("active") ? 0xFF2A6A2A : 0xFF8A2020) : INK);
        }
    }
}
