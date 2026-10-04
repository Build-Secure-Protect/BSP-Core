package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.MachineBlockEntity;
import com.mrgregles.bsp_core.machine.MachineMenu;
import com.mrgregles.bsp_core.machine.MultiblockControllerBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.List;
import java.util.Locale;

/**
 * "Holo HUD" screen shared by the processing machines (concept C).
 *
 * <p>Top: a pipeline, inputs on the left, a progress dial in the middle, outputs on the right, with
 * chevrons that run toward the output while working. Under it: segmented gauges for heat, the tank
 * and RF, with the fuel or bucket slot beside them. Right: PORTS as an unfolded cube (click a face
 * to cycle off, input, output, both), then STATUS as a checklist of what is missing, then UPGRADES.
 * Bottom left: the power switch. Blue is input, orange output, turquoise both, grey off, violet an
 * upgrade slot, red something missing.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int BG = 0xF010151C, EDGE = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, SLOT_EDGE = 0xFF3A3F4B, DIM = 0xFF2A2F3A, GHOST = 0xA010151C;
    private static final int TQ = 0xFF19D3B0, IN = 0xFF3A8BFF, OUT = 0xFFFF8A3A, OFF = 0xFF565C6B, VIO = 0xFFB58CFF, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A, LAVA = 0xFFFF7A1A, MUTED = 0x9AA3B5;
    private static final int DIAL_X = 105, DIAL_Y = 39, DIAL_R = 15, GAUGE_X = 14, GAUGE_Y = 76, PORT_X = 196, PORT_Y = 32, STATUS_X = 284, POWER_X = 14, POWER_Y = 118, POWER_W = 38, POWER_H = 16;
    /** 0 = off, 1 = on; eased toward the real state every frame so the knob slides. */
    private float powerAnim = -1f;
    /** Row height of the named-port list shown for multiblocks. */
    private static final int PROW = 17;
    /** Unfolded cube: column, row, face key. */
    private static final Object[][] FACES = {{1, 0, "top"}, {0, 1, "left"}, {1, 1, "front"}, {2, 1, "right"}, {3, 1, "back"}, {1, 2, "bottom"}};

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 326;
        this.imageHeight = MachineMenu.INV_Y + 82;
        this.inventoryLabelX = MachineMenu.INV_X;
        this.inventoryLabelY = MachineMenu.INV_Y - 11;
    }

    private Direction face(String key) {
        MachineBlockEntity m = menu.getMachine();
        Direction front = m != null && m.getBlockState().hasProperty(MachineBlock.FACING) ? m.getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        return switch (key) {
            case "top" -> Direction.UP;
            case "bottom" -> Direction.DOWN;
            case "front" -> front;
            case "back" -> front.getOpposite();
            case "left" -> front.getClockWise();
            default -> front.getCounterClockWise();
        };
    }

    private static int modeColour(SideMode mode) {
        return switch (mode) {
            case INPUT -> IN;
            case OUTPUT -> OUT;
            case BOTH -> TQ;
            default -> OFF;
        };
    }

    /** The open machine as a multiblock controller, or null for a single-block machine. */
    private MultiblockControllerBlockEntity multi() {
        return menu.getMachine() instanceof MultiblockControllerBlockEntity m ? m : null;
    }

    private List<MachineBlockEntity.Need> needs() {
        MachineBlockEntity machine = menu.getMachine();
        return machine == null ? List.of() : machine.missing(menu.fluid(), menu.burn() > 0);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        MultiblockControllerBlockEntity mb = multi();
        if (mb != null) {
            for (int i = 0; i < mb.itemPortCount(); i++) {
                if (over(mx, my, x + PORT_X, y + PORT_Y + 10 + i * PROW, 14, 14)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    return true;
                }
            }
        } else {
            for (Object[] f : FACES) {
                if (over(mx, my, x + PORT_X + (int) f[0] * 20, y + PORT_Y + 10 + (int) f[1] * 20, 18, 18)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, face((String) f[2]).get3DDataValue());
                    return true;
                }
            }
        }
        if (over(mx, my, x + POWER_X, y + POWER_Y, POWER_W, POWER_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BTN_POWER);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private int slotEdge(MachineBlockEntity machine, int i) {
        if (machine == null || i >= machine.layout().size()) {
            return SLOT_EDGE;
        }
        return switch (machine.layout().get(i).role()) {
            case INPUT, FUEL -> IN;
            case OUTPUT -> OUT;
            default -> VIO;
        };
    }

    private void segments(GuiGraphics g, int x, int y, String key, float frac, int colour) {
        g.drawString(font, Component.translatable(key), x, y, MUTED, false);
        int lit = Math.round(12 * Mth.clamp(frac, 0, 1));
        for (int i = 0; i < 12; i++) {
            g.fill(x + 30 + i * 9, y - 1, x + 37 + i * 9, y + 7, i < lit ? colour : DIM);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        MachineBlockEntity machine = menu.getMachine();
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, EDGE);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);

        // slots, coloured by role, with a faint example in empty non-output slots
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, slotEdge(machine, i));
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
            if (machine != null && i < machine.layout().size() && !slot.hasItem() && machine.layout().get(i).role() != MachineBlockEntity.Role.OUTPUT) {
                g.renderFakeItem(machine.slotIcon(i), x + slot.x, y + slot.y);
                g.fill(RenderType.guiGhostRecipeOverlay(), x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GHOST);
            }
        }

        // progress dial: a ring of dots that lights clockwise from the top
        float p = menu.progress();
        for (int i = 0; i < 28; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / 28;
            int dx = x + DIAL_X + (int) Math.round(Math.cos(a) * DIAL_R), dy = y + DIAL_Y + (int) Math.round(Math.sin(a) * DIAL_R);
            g.fill(dx - 1, dy - 1, dx + 2, dy + 2, i < Math.round(28 * p) ? TQ : DIM);
        }

        // gauges
        int gy = y + GAUGE_Y;
        if (machine != null && machine.hasFuelSlot()) {
            segments(g, x + GAUGE_X, gy, "gui.bsp_core.hud.heat", menu.burn(), LAVA);
            gy += 14;
        }
        if (menu.fluidCapacity() > 0) {
            segments(g, x + GAUGE_X, gy, "gui.bsp_core.hud.fluid", menu.fluid() / (float) menu.fluidCapacity(), machine == null ? IN : machine.fluidColor());
            gy += 14;
        }
        if (menu.energyPermille() >= 0) {
            segments(g, x + GAUGE_X, gy, "gui.bsp_core.hud.rf", menu.energyPermille() / 1000f, GOLD);
        }

        // ports: named ports for a multiblock, an unfolded cube of faces for a single block
        MultiblockControllerBlockEntity mb = multi();
        if (mb != null) {
            int n = mb.itemPortCount();
            for (int i = 0; i < n; i++) {
                int fy = y + PORT_Y + 10 + i * PROW;
                g.fill(x + PORT_X, fy, x + PORT_X + 14, fy + 14, modeColour(menu.side(Direction.from3DDataValue(i))));
            }
            int fy = y + PORT_Y + 10 + n * PROW;
            g.fill(x + PORT_X, fy, x + PORT_X + 14, fy + 14, machine == null ? LAVA : machine.fluidColor()); // fluid socket: lava orange or water blue, same as in the world
            g.fill(x + PORT_X, fy + PROW, x + PORT_X + 14, fy + PROW + 14, menu.energyPermille() >= 0 ? GOLD : OFF); // RF sockets: yellow once an RF upgrade is fitted
        } else {
            for (Object[] f : FACES) {
                int fx = x + PORT_X + (int) f[0] * 20, fy = y + PORT_Y + 10 + (int) f[1] * 20;
                g.fill(fx, fy, fx + 18, fy + 18, modeColour(menu.side(face((String) f[2]))));
            }
        }

        // status checklist frames
        List<MachineBlockEntity.Need> needs = needs();
        for (int i = 0; i < Math.min(4, needs.size()); i++) {
            int sy = y + PORT_Y + 10 + i * 18;
            g.fill(x + STATUS_X - 1, sy - 1, x + STATUS_X + 17, sy + 17, BAD);
            g.fill(x + STATUS_X, sy, x + STATUS_X + 16, sy + 16, SLOT_BG);
            g.renderFakeItem(needs.get(i).icon(), x + STATUS_X, sy);
        }

        // power switch
        boolean on = menu.enabled();
        float target = on ? 1f : 0f;
        powerAnim = powerAnim < 0 ? target : powerAnim + (target - powerAnim) * Math.min(1f, 0.35f * (1f + partialTick));
        int px = x + POWER_X, py = y + POWER_Y;
        int colour = powerAnim > 0.5f ? TQ : BAD;
        g.fill(px - 1, py - 1, px + POWER_W + 1, py + POWER_H + 1, colour);          // housing
        g.fill(px, py, px + POWER_W, py + POWER_H, SLOT_BG);
        g.fill(px + 4, py + 6, px + POWER_W - 4, py + POWER_H - 6, DIM);              // rail
        g.fill(px + 4, py + 6, px + 4 + Math.round((POWER_W - 8) * powerAnim), py + POWER_H - 6, colour); // lit part of the rail
        int kx = px + 2 + Math.round((POWER_W - 16) * powerAnim);                    // knob slides left (off) to right (on)
        g.fill(kx, py + 2, kx + 12, py + POWER_H - 2, 0xFFC9CED8);
        g.fill(kx + 1, py + 3, kx + 11, py + POWER_H - 3, 0xFF8A909C);
        g.fill(kx + 5, py + 4, kx + 7, py + POWER_H - 4, colour);                    // indicator stripe on the knob
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        MachineBlockEntity machine = menu.getMachine();
        boolean working = menu.working() && menu.enabled() && menu.formed() && !menu.redstonePaused();
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        String state = !menu.enabled() ? "off" : menu.redstonePaused() ? "redstone" : !menu.formed() ? "unformed" : working ? "working" : needs().isEmpty() ? "ready" : "blocked";
        Component st = Component.translatable("gui.bsp_core.hud." + state);
        g.drawString(font, st, imageWidth - 10 - font.width(st), 8, state.equals("working") || state.equals("ready") ? TQ & 0xFFFFFF : 0xFF6B5C, false);

        // chevrons either side of the dial, running toward the output while working
        int phase = working ? (int) (System.currentTimeMillis() / 180 % 3) : -1;
        for (int i = 0; i < 3; i++) {
            int col = i == phase ? TQ & 0xFFFFFF : 0x3A4A55;
            g.drawString(font, ">", 62 + i * 6, DIAL_Y - 4, col, false);
            g.drawString(font, ">", 128 + i * 6, DIAL_Y - 4, col, false);
        }
        String pct = Math.round(menu.progress() * 100) + "%";
        g.drawString(font, pct, DIAL_X - font.width(pct) / 2 + 1, DIAL_Y - 3, TQ & 0xFFFFFF, false);
        if (working) {
            Component left = Component.translatable("gui.bsp_core.hud.left", String.format("%d:%02d", menu.secondsLeft() / 60, menu.secondsLeft() % 60));
            g.drawString(font, left, DIAL_X - font.width(left) / 2 + 1, DIAL_Y + DIAL_R + 6, MUTED, false);
        }

        g.drawString(font, Component.translatable("gui.bsp_core.hud.ports"), PORT_X, PORT_Y - 2, TQ & 0xFFFFFF, false);
        MultiblockControllerBlockEntity mb = multi();
        if (mb != null) {
            int n = mb.itemPortCount();
            g.pose().pushPose();
            g.pose().scale(0.75f, 0.75f, 1f); // small type so name and "accepts" fit beside the status column
            float s = 1 / 0.75f;
            for (int i = 0; i < n; i++) {
                SideMode mode = menu.side(Direction.from3DDataValue(i));
                int ly = PORT_Y + 10 + i * PROW;
                g.drawString(font, Component.translatable(mb.itemPortKey(i)), (int) ((PORT_X + 18) * s), (int) (ly * s), 0xE8EAF0, false);
                g.drawString(font, Component.translatable("gui.bsp_core.port.items", Component.translatable("gui.bsp_core.factory.mode." + mode.name().toLowerCase(Locale.ROOT))),
                        (int) ((PORT_X + 18) * s), (int) ((ly + 7) * s), MUTED, false);
            }
            int ly = PORT_Y + 10 + n * PROW;
            g.drawString(font, Component.translatable(mb.fluidPortKey()), (int) ((PORT_X + 18) * s), (int) (ly * s), 0xE8EAF0, false);
            g.drawString(font, Component.translatable(mb.fluidPortKey() + ".accepts"), (int) ((PORT_X + 18) * s), (int) ((ly + 7) * s), MUTED, false);
            g.drawString(font, Component.translatable("gui.bsp_core.port.rf"), (int) ((PORT_X + 18) * s), (int) ((ly + PROW) * s), 0xE8EAF0, false);
            g.drawString(font, Component.translatable(menu.energyPermille() >= 0 ? "gui.bsp_core.port.rf.on" : "gui.bsp_core.port.rf.off"), (int) ((PORT_X + 18) * s), (int) ((ly + PROW + 7) * s), MUTED, false);
            g.pose().popPose();
        } else {
            for (Object[] f : FACES) {
                String letter = f[2].equals("bottom") ? "D" : ((String) f[2]).substring(0, 1).toUpperCase(Locale.ROOT);
                g.drawString(font, letter, PORT_X + (int) f[0] * 20 + 6, PORT_Y + 15 + (int) f[1] * 20, 0x0B0D11, false);
            }
        }
        g.drawString(font, Component.translatable("gui.bsp_core.hud.status"), STATUS_X - 6, PORT_Y - 2, TQ & 0xFFFFFF, false);
        if (needs().isEmpty()) {
            g.drawString(font, "✓", STATUS_X + 5, PORT_Y + 14, TQ & 0xFFFFFF, false);
        }
        if (machine != null && machine.layout().stream().anyMatch(s -> s.role() == MachineBlockEntity.Role.UPGRADE || s.role() == MachineBlockEntity.Role.RF)) {
            g.drawString(font, Component.translatable("gui.bsp_core.hud.upgrades"), MachineMenu.UP_X, MachineMenu.UP_Y - 10, VIO & 0xFFFFFF, false);
        }
        g.drawString(font, Component.translatable("gui.bsp_core.hud.power"), POWER_X, POWER_Y - 11, MUTED, false);
        g.drawString(font, Component.translatable(menu.enabled() ? "gui.bsp_core.hud.on" : "gui.bsp_core.hud.off_short"), POWER_X + POWER_W + 6, POWER_Y + 4, menu.enabled() ? TQ & 0xFFFFFF : 0xFF6B5C, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        MachineBlockEntity machine = menu.getMachine();
        int x = leftPos, y = topPos;
        if (machine != null && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int index = menu.slots.indexOf(hoveredSlot);
            if (index >= 0 && index < machine.layout().size()) {
                g.renderTooltip(font, machine.slotHint(index), mouseX, mouseY);
            }
        }
        for (Object[] f : FACES) {
            if (multi() == null && over(mouseX, mouseY, x + PORT_X + (int) f[0] * 20, y + PORT_Y + 10 + (int) f[1] * 20, 18, 18)) {
                SideMode mode = menu.side(face((String) f[2]));
                g.renderTooltip(font, Component.translatable("gui.bsp_core.hud.port", Component.translatable("gui.bsp_core.hud.face." + f[2]),
                        Component.translatable("gui.bsp_core.factory.mode." + mode.name().toLowerCase(Locale.ROOT))), mouseX, mouseY);
            }
        }
        List<MachineBlockEntity.Need> needs = needs();
        for (int i = 0; i < Math.min(4, needs.size()); i++) {
            if (over(mouseX, mouseY, x + STATUS_X, y + PORT_Y + 10 + i * 18, 16, 16)) {
                g.renderTooltip(font, needs.get(i).text(), mouseX, mouseY);
            }
        }
        if (needs.isEmpty() && over(mouseX, mouseY, x + STATUS_X, y + PORT_Y + 10, 16, 16)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.hud.all_good"), mouseX, mouseY);
        }
        if (over(mouseX, mouseY, x + POWER_X, y + POWER_Y, POWER_W, POWER_H)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.hud.power_tip"), mouseX, mouseY);
        }
        if (menu.fluidCapacity() > 0 && over(mouseX, mouseY, x + GAUGE_X, y + GAUGE_Y - 2, 140, 40)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.machine.tank", String.format("%,d", menu.fluid()), String.format("%,d", menu.fluidCapacity())), mouseX, mouseY);
        }
    }
}
