package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerActionPacket;
import com.mrgregles.bsp_core.tracking.TrackedTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The Machine Tagger's screen, the owner's pick "Device Screen" (P3): the tagger's own glass in a steel casing, three round keys on the
 * casing for TAGS (two columns of cards; pick one for RENAME and REMOVE), SLOTS (every slot, free, bought or for sale; click the next one
 * to buy it with Copper Shatter Coins) and REMOTE (a padlock with four sockets for the Diamond coins of the Remote Control unlock). A speaker
 * grille and a coin slot sit on the right. Drawn at 320 x 210 and shrunk to fit a small window. Never shows coordinates.
 */
public class TaggerScreen extends Screen {
    private static final int W = 320, H = 210, SX = 52, SY = 12, SW = 216, SH = 186;
    private enum Tab { TAGS, SLOTS, REMOTE }

    private Tab tab = Tab.TAGS;
    private int selected = 0, scroll = 0, boughtAt = -1;
    private String editing = null, buffer = "";
    private float scale = 1;
    private int left, top;

    public TaggerScreen() {
        super(tr("gui.bsp_core.tracking.tagger"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new TaggerScreen());
    }

    @Override
    protected void init() {
        scale = Math.min(1f, Math.min((width - 8) / (float) W, (height - 8) / (float) H));
        left = Math.round((width / scale - W) / 2);
        top = Math.round((height / scale - H) / 2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ListTag tags() {
        return ClientTracking.view().getList("tags", Tag.TAG_COMPOUND);
    }

    private int coins(CoinTier tier) {
        var p = Minecraft.getInstance().player;
        int n = 0;
        if (p != null) {
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack st = p.getInventory().getItem(i);
                if (st.is(tier.coin())) {
                    n += st.getCount();
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        int mx = Math.round(mouseX / scale) - left, my = Math.round(mouseY / scale) - top;
        g.pose().translate(left, top, 0);
        casing(g, mx, my);
        g.enableScissor(Math.round((left + SX) * scale), Math.round((top + SY) * scale), Math.round((left + SX + SW) * scale), Math.round((top + SY + SH) * scale));
        well(g, SX, SY, SW, SH, 0xFF081210);
        switch (tab) {
            case TAGS -> tagsPage(g, mx, my);
            case SLOTS -> slotsPage(g, mx, my);
            case REMOTE -> remotePage(g, mx, my);
        }
        for (int y = SY; y < SY + SH; y += 2) {
            rect(g, SX, y, SW, 1, 0x2E000000); // scan lines on the glass
        }
        g.disableScissor();
        g.pose().popPose();
    }

    private void casing(GuiGraphics g, int mx, int my) {
        plate(g, 0, 0, W, H);
        rect(g, SX - 3, SY - 3, SW + 6, SH + 6, BLACK);
        rect(g, SX - 2, SY - 2, SW + 4, 1, STEEL_L);
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int cx = 26, cy = 40 + i * 46;
            boolean on = tab == tabs[i];
            disc(g, cx, cy, 11, BLACK);
            disc(g, cx, cy, 10, on ? TQ : (Math.hypot(mx - cx, my - cy) < 11 ? 0xFF4A5262 : 0xFF3A414E));
            rect(g, cx - 5, cy - 8, 6, 1, on ? 0xFFB8FFF0 : STEEL_L);
            String label = tr("gui.bsp_core.tracking.tab." + tabs[i].name().toLowerCase()).getString();
            small(g, label, cx - smallWidth(label) / 2, cy + 13, on ? TQ : MUTED);
        }
        for (int r = 0; r < 6; r++) {
            for (int k = 0; k < 4; k++) {
                disc(g, 283 + k * 7, 26 + r * 7, 2, STEEL_D);
            }
        }
        rect(g, 282, 96, 28, 46, 0xFF1C222C);
        rect(g, 293, 101, 6, 26, BLACK);
        disc(g, 296, 106, 4, COPPER);
        small(g, "COPPER", 296 - smallWidth("COPPER") / 2, 130, COPPER);
        boolean remote = ClientTracking.view().getBoolean("remote");
        lamp(g, 296, 160, 3, remote ? TQ : VIO, true);
        small(g, "REMOTE", 296 - smallWidth("REMOTE") / 2, 166, MUTED);
    }

    private void tagsPage(GuiGraphics g, int mx, int my) {
        CompoundTag v = ClientTracking.view();
        ListTag tags = tags();
        int slots = v.getInt("slots");
        text(g, tr("gui.bsp_core.tracking.tracked").getString(), SX + 6, SY + 5, TQ);
        String count = tags.size() + "/" + slots;
        small(g, count, SX + SW - 6 - smallWidth(count), SY + 6, MUTED);
        int rows = (SH - 22) / 31;
        int first = scroll * 2;
        for (int i = first; i < Math.min(Math.max(slots, tags.size()), first + rows * 2); i++) {
            int col = (i - first) % 2, row = (i - first) / 2;
            int x = SX + 5 + col * 105, y = SY + 17 + row * 31;
            boolean has = i < tags.size();
            boolean on = has && selected == i;
            rect(g, x, y, 101, 28, on ? 0xFF0F2A25 : 0xFF0C1916);
            outline(g, x, y, 101, 28, on ? TQ : 0xFF1A3A34);
            if (!has) {
                String e = tr("gui.bsp_core.tracking.empty_slot").getString();
                small(g, e, x + 50 - smallWidth(e) / 2, y + 11, 0xFF2A5A50);
                continue;
            }
            CompoundTag t = tags.getCompound(i);
            byte st = ClientTracking.reading(t.getString("id")).getByte("st");
            lamp(g, x + 6, y + 6, 2, st == 0 ? TQ : OFF, true);
            if (t.getString("id").equals(editing)) {
                well(g, x + 11, y + 2, 86, 10, 0xFF071A17);
                outline(g, x + 11, y + 2, 86, 10, TQ);
                String shown = buffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "");
                small(g, fitSmall(shown, 82), x + 13, y + 4, FG);
            } else {
                small(g, fitSmall(tagName(t), 84), x + 11, y + 3, FG);
            }
            small(g, fitSmall(blockName(t), 94), x + 4, y + 13, MUTED);
            small(g, fitSmall(where(t), on ? 56 : 94), x + 4, y + 20, TQ_D);
            if (on) {
                key(g, x + 64, y + 16, 16, 10, "", t.getString("id").equals(editing), in(mx, my, x + 64, y + 16, 16, 10), TQ);
                pencil(g, x + 69, y + 17, FG);
                key(g, x + 82, y + 16, 16, 10, "", false, in(mx, my, x + 82, y + 16, 16, 10), BAD);
                cross(g, x + 87, y + 18, BAD);
            }
        }
        if (Math.max(slots, tags.size()) > rows * 2) {
            small(g, tr("gui.bsp_core.tracking.scroll").getString(), SX + 6, SY + SH - 9, OFF);
        }
    }

    private void slotsPage(GuiGraphics g, int mx, int my) {
        CompoundTag v = ClientTracking.view();
        int slots = v.getInt("slots"), free = v.getInt("free"), max = v.getInt("max"), next = v.getInt("nextPrice");
        text(g, tr("gui.bsp_core.tracking.tab.slots").getString(), SX + 6, SY + 5, TQ);
        small(g, tr("gui.bsp_core.tracking.slots_yours").getString(), SX + 6, SY + 16, MUTED);
        int step = slots - free <= 0 ? next : next / Math.max(1, slots - free + 1);
        for (int i = 0; i < Math.min(max, 20); i++) {
            int col = i % 2, row = i / 2;
            int x = SX + 10 + col * 100, y = SY + 27 + row * 14;
            boolean has = i < slots, isFree = i < free, isNext = i == slots;
            boolean hover = isNext && in(mx, my, x, y, 92, 12);
            rect(g, x, y, 92, 12, has ? (isFree ? 0xFF2A2410 : 0xFF0F2A25) : hover ? 0xFF1A2A26 : 0xFF0C1210);
            outline(g, x, y, 92, 12, has ? (isFree ? GOLD : TQ_D) : isNext ? COPPER : 0xFF1A2420);
            small(g, tr("gui.bsp_core.tracking.slot", i + 1).getString(), x + 4, y + 3, has ? FG : OFF);
            String right = isFree ? tr("gui.bsp_core.tracking.free").getString() : has ? tr("gui.bsp_core.tracking.bought").getString()
                    : String.valueOf(step * (i - free + 1));
            if (!has) {
                disc(g, x + 66, y + 6, 3, isNext ? COPPER : Hud.darken(COPPER, 0.5f));
            }
            small(g, right, x + 88 - smallWidth(right), y + 3, isFree ? GOLD : has ? TQ_D : isNext ? COPPER : OFF);
        }
        String carry = tr("gui.bsp_core.tracking.carry_copper", coins(CoinTier.COPPER)).getString();
        small(g, carry, SX + 6, SY + SH - 11, MUTED);
    }

    private void remotePage(GuiGraphics g, int mx, int my) {
        CompoundTag v = ClientTracking.view();
        boolean remote = v.getBoolean("remote");
        int price = v.getInt("remotePrice"), have = coins(CoinTier.DIAMOND);
        text(g, tr("gui.bsp_core.tracking.tab.remote").getString(), SX + 6, SY + 5, TQ);
        int cx = SX + SW / 2;
        padlock(g, cx - 16, SY + 24, 32, remote, remote ? TQ : VIO);
        String state = tr(remote ? "gui.bsp_core.tracking.unlocked" : "gui.bsp_core.tracking.locked").getString();
        text(g, state, cx - font().width(state) / 2, SY + 66, remote ? TQ : VIO);
        for (int i = 0; i < 3; i++) {
            String line = tr("gui.bsp_core.tracking.remote_line" + (i + 1)).getString();
            small(g, line, cx - smallWidth(line) / 2, SY + 82 + i * 9, FG);
        }
        if (!remote) {
            for (int i = 0; i < price && i < 8; i++) {
                int sx = cx - price * 8 + i * 16 + 8, sy = SY + 128;
                well(g, sx - 6, sy - 6, 12, 12, BLACK);
                if (i < have) {
                    disc(g, sx, sy, 4, DIAMOND);
                }
            }
            String cost = tr("gui.bsp_core.tracking.remote_cost", price).getString();
            small(g, cost, cx - smallWidth(cost) / 2, SY + 138, MUTED);
            key(g, cx - 36, SY + 150, 72, 16, tr("gui.bsp_core.tracking.unlock").getString(), false, in(mx, my, cx - 36, SY + 150, 72, 16), VIO);
        } else {
            String after = tr("gui.bsp_core.tracking.remote_resets").getString();
            small(g, after, cx - smallWidth(after) / 2, SY + 130, MUTED);
        }
    }

    private static void padlock(GuiGraphics g, int x, int y, int s, boolean open, int c) {
        int shackleTop = open ? y - s / 6 : y;
        rect(g, x + s / 5, shackleTop, s * 3 / 5, s / 8, c);
        rect(g, x + s / 5, shackleTop, s / 8, s * 2 / 5, c);
        rect(g, x + s * 4 / 5 - s / 8, open ? shackleTop : y, s / 8, open ? s / 4 : s * 2 / 5, c);
        rect(g, x, y + s * 2 / 5, s, s * 3 / 5, c);
        rect(g, x + s / 2 - 1, y + s * 3 / 5, 2, s / 5, BLACK);
    }

    private static void pencil(GuiGraphics g, int x, int y, int c) {
        for (int i = 0; i < 5; i++) {
            rect(g, x + 1 + i, y + 5 - i, 2, 1, c);
        }
    }

    private static void cross(GuiGraphics g, int x, int y, int c) {
        for (int k = 0; k < 5; k++) {
            rect(g, x + k, y + k, 1, 1, c);
            rect(g, x + 4 - k, y + k, 1, 1, c);
        }
    }

    private static String blockName(CompoundTag t) {
        var block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.tryParse(t.getString("block")));
        return block == null ? t.getString("block") : Hud.blockName(block);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) Math.round(mouseX / scale) - left, my = (int) Math.round(mouseY / scale) - top;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (Math.hypot(mx - 26, my - (40 + i * 46)) < 11) {
                tab = tabs[i];
                editing = null;
                return true;
            }
        }
        if (tab == Tab.TAGS) {
            ListTag tags = tags();
            int rows = (SH - 22) / 31, first = scroll * 2;
            for (int i = first; i < Math.min(tags.size(), first + rows * 2); i++) {
                int col = (i - first) % 2, row = (i - first) / 2;
                int x = SX + 5 + col * 105, y = SY + 17 + row * 31;
                String id = tags.getCompound(i).getString("id");
                if (selected == i && in(mx, my, x + 64, y + 16, 16, 10)) {
                    if (id.equals(editing)) {
                        commitRename();
                    } else {
                        editing = id;
                        buffer = tags.getCompound(i).getString("name");
                    }
                    return true;
                }
                if (selected == i && in(mx, my, x + 82, y + 16, 16, 10)) {
                    send(TrackerActionPacket.UNTAG, id, "");
                    editing = null;
                    selected = -1; // a second click must not remove the tag that moves up into this place
                    return true;
                }
                if (in(mx, my, x, y, 101, 28)) {
                    if (!id.equals(editing)) {
                        commitRename();
                    }
                    selected = i;
                    return true;
                }
            }
        } else if (tab == Tab.SLOTS) {
            CompoundTag v = ClientTracking.view();
            int slots = v.getInt("slots");
            if (slots < v.getInt("max") && slots < 20) {
                int x = SX + 10 + (slots % 2) * 100, y = SY + 27 + (slots / 2) * 14;
                if (in(mx, my, x, y, 92, 12) && boughtAt != ClientTracking.version()) {
                    boughtAt = ClientTracking.version(); // one purchase per answer from the server: a double click buys once
                    send(TrackerActionPacket.BUY_SLOT, "", "");
                    return true;
                }
            }
        } else if (!ClientTracking.view().getBoolean("remote") && in(mx, my, SX + SW / 2 - 36, SY + 150, 72, 16)) {
            send(TrackerActionPacket.UNLOCK_REMOTE, "", "");
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.TAGS) {
            int rows = (SH - 22) / 31;
            int total = Math.max(ClientTracking.view().getInt("slots"), tags().size());
            int maxScroll = Math.max(0, (total + 1) / 2 - rows);
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (editing != null && buffer.length() < TrackedTag.NAME_MAX && c >= ' ' && c != '§') {
            buffer += c;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editing = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
                return true;
            }
            return true; // typing a name: no other key does anything (E would close the screen)
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void commitRename() {
        if (editing != null) {
            send(TrackerActionPacket.RENAME, editing, buffer);
            editing = null;
        }
    }

    private static void send(int action, String id, String text) {
        BSPNetwork.CHANNEL.sendToServer(new TrackerActionPacket(action, id, text, new CompoundTag()));
    }
}
