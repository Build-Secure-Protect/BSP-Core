package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TeamActionPacket;
import com.mrgregles.bsp_core.network.TeamActionPacket.Action;
import com.mrgregles.bsp_core.network.TeamViewPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Team screen (look C, the roster board): a violet frame, the view in the middle, keys along the
 * bottom grouped by job. Views: no team yet (name, colour, CREATE), the members, invite, the board
 * (messages) and the leader's settings. Fed by {@link TeamViewPacket}; every key sends a
 * {@link TeamActionPacket} and the server answers with the fresh view.
 */
public class TeamScreen extends Screen {
    private static final int W = 340, H = 236, KEY_H = 20, ROW = 13, SW = 12;
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, LINE = 0xFF1A1F29, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2,
            GREEN = 0xFF58F29A, AMBER = 0xFFF2B544, GREY = 0xFF555C6A, RED = 0xFFFF6B5C, GOLD = 0xFFFFD23A, AQUA = 0xFF55FFFF;
    private static final List<ChatFormatting> COLOURS = new ArrayList<>();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm");

    static {
        for (ChatFormatting c : ChatFormatting.values()) {
            if (c.isColor()) {
                COLOURS.add(c);
            }
        }
    }

    private enum Mode { CREATE, TEAM, INVITE, MESSAGES, SETTINGS }

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private enum Look { NORMAL, ON, WARN, LOCKED }

    private TeamViewPacket view;
    private Mode mode;
    private int x, y, colour = ChatFormatting.AQUA.getId(), scroll, ticks;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox nameBox, lineBox, pinBox;
    private String nameText = "", lineText = "", pinText = "";
    private boolean pinLoaded, nameLoaded, readPending;
    @Nullable
    private String confirm;
    private long confirmUntil;

    private TeamScreen(TeamViewPacket view) {
        super(Component.translatable("gui.bsp_core.team.title"));
        this.view = view;
        this.mode = view.hasTeam() ? Mode.TEAM : Mode.CREATE;
    }

    /** Called for every view the server sends: opens the screen when asked, or refreshes an open one. */
    public static void receive(TeamViewPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TeamScreen s) {
            s.take(msg);
        } else if (msg.open()) {
            mc.setScreen(new TeamScreen(msg));
        }
    }

    private void take(TeamViewPacket msg) {
        view = msg;
        if (!msg.hasTeam()) {
            mode = Mode.CREATE;
            pinLoaded = nameLoaded = false;
        } else if (mode == Mode.CREATE || mode == Mode.SETTINGS && !msg.leader()) {
            mode = Mode.TEAM;
        }
        layout();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        x = (width - W) / 2;
        y = (height - H) / 2;
        int mx = x + 8, my = y + 20, mw = W - 16, mh = H - 20 - 34;
        nameBox = new EditBox(font, mx + 6, my + 24, mw - 12, 14, Component.translatable("gui.bsp_core.team.create.name"));
        nameBox.setMaxLength(32);
        nameBox.setHint(Component.translatable("gui.bsp_core.team.create.name").withStyle(ChatFormatting.DARK_GRAY));
        nameBox.setValue(nameText);
        nameBox.setResponder(t -> nameText = t);
        addRenderableWidget(nameBox);
        lineBox = new EditBox(font, mx + 6, my + mh - 18, mw - 12 - 44, 14, Component.translatable("gui.bsp_core.team.board.type"));
        lineBox.setMaxLength(256);
        lineBox.setHint(Component.translatable("gui.bsp_core.team.board.type").withStyle(ChatFormatting.DARK_GRAY));
        lineBox.setValue(lineText);
        lineBox.setResponder(t -> lineText = t);
        addRenderableWidget(lineBox);
        pinBox = new EditBox(font, mx + 6, my + mh - 18, mw - 12 - 60, 14, Component.translatable("gui.bsp_core.team.settings.pin"));
        pinBox.setMaxLength(200);
        pinBox.setHint(Component.translatable("gui.bsp_core.team.settings.pin.hint").withStyle(ChatFormatting.DARK_GRAY));
        pinBox.setValue(pinText);
        pinBox.setResponder(t -> pinText = t);
        addRenderableWidget(pinBox);
        layout();
    }

    /** Shows the boxes the view needs and loads their text once. */
    private void layout() {
        if (nameBox == null) {
            return;
        }
        if (mode == Mode.SETTINGS && !nameLoaded) {
            nameBox.setValue(view.name());
            nameLoaded = true;
        }
        if (mode == Mode.SETTINGS && !pinLoaded) {
            pinBox.setValue(view.pinText());
            pinLoaded = true;
        }
        int mx = x + 8, my = y + 20, mw = W - 16;
        nameBox.setY(mode == Mode.CREATE ? my + 24 : my + 16);
        nameBox.setWidth(mode == Mode.CREATE ? mw - 12 : 140);
        show(nameBox, mode == Mode.CREATE || mode == Mode.SETTINGS);
        show(lineBox, mode == Mode.MESSAGES);
        show(pinBox, mode == Mode.SETTINGS);
    }

    private static void show(EditBox box, boolean on) {
        box.setVisible(on);
        box.active = on;
        if (!on) {
            box.setFocused(false);
        }
    }

    private void setMode(Mode m) {
        if (mode == Mode.MESSAGES && m != Mode.MESSAGES && readPending) {
            send(TeamActionPacket.of(Action.MARK_READ));
            readPending = false;
        }
        mode = m;
        scroll = 0;
        confirm = null;
        if (m == Mode.MESSAGES && view.unread() > 0) {
            readPending = true;
        }
        layout();
    }

    private static void send(TeamActionPacket p) {
        BSPNetwork.CHANNEL.sendToServer(p);
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % 40 == 0) {
            send(TeamActionPacket.of(Action.REFRESH));
        }
    }

    @Override
    public void onClose() {
        if (readPending) {
            send(TeamActionPacket.of(Action.MARK_READ));
        }
        super.onClose();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        if (confirm != null && System.currentTimeMillis() > confirmUntil) {
            confirm = null;
        }
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, BG);
        // header
        g.drawString(font, Component.translatable("gui.bsp_core.team.title"), x + 10, y + 6, VIO & 0xFFFFFF, false);
        String who = Component.translatable(!view.hasTeam() ? "gui.bsp_core.team.who.none" : view.leader() ? "gui.bsp_core.team.who.lead" : "gui.bsp_core.team.who.member").getString();
        CashMachine.fitRight(g, font, who, x + W - 10, y + 6, 150, MUTED);
        // the main box
        int mx = x + 8, my = y + 20, mw = W - 16, mh = H - 20 - 34;
        g.fill(mx - 1, my - 1, mx + mw + 1, my + mh + 1, DIM);
        g.fill(mx, my, mx + mw, my + mh, SLOT);
        switch (mode) {
            case CREATE -> drawCreate(g, mx, my, mw, mh, mouseX, mouseY);
            case TEAM -> drawTeam(g, mx, my, mw, mh, mouseX, mouseY);
            case INVITE -> drawInvite(g, mx, my, mw, mh, mouseX, mouseY);
            case MESSAGES -> drawMessages(g, mx, my, mw, mh, mouseX, mouseY);
            case SETTINGS -> drawSettings(g, mx, my, mw, mh, mouseX, mouseY);
        }
        drawKeys(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component teamName() {
        ChatFormatting c = ChatFormatting.getById(view.colour());
        return Component.literal(view.name()).withStyle(c == null ? ChatFormatting.AQUA : c);
    }

    private void drawCreate(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        if (!view.enabled()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.disabled"), mx + 6, my + 8, mw - 12, RED & 0xFFFFFF);
            return;
        }
        int yy = my + 6 + CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.create.hint"), mx + 6, my + 6, mw - 12, MUTED);
        yy = my + 44;
        swatches(g, mx + 6, yy, colour, mouseX, mouseY, id -> colour = id);
        small(g, Component.translatable("gui.bsp_core.team.create.rule", 3, 16), mx + 6, yy + 34, MUTED);
        key(g, mx + mw - 6 - 70, my + mh - 26, 70, KEY_H, Component.translatable("gui.bsp_core.team.k.create").getString(), Look.ON, mouseX, mouseY,
                () -> send(TeamActionPacket.text(Action.CREATE, nameText, colour)));
    }

    private void drawTeam(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        g.drawString(font, teamName(), mx + 6, my + 5, TEXT, false);
        CashMachine.fitRight(g, font, Component.translatable("gui.bsp_core.team.members", view.members().size(), view.maxMembers()).getString(), mx + mw - 6, my + 5, 120, MUTED);
        int yy = my + 18;
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();
        for (TeamViewPacket.Member m : view.members()) {
            boolean online = m.state() != TeamViewPacket.OFFLINE;
            boolean tp = view.canTeleport() && online && !m.id().equals(me);
            memberRow(g, mx, yy, mw - (view.canTeleport() ? 50 : 0), m);
            if (tp) {
                UUID id = m.id();
                key(g, mx + mw - 6 - 46, yy, 46, ROW - 1, Component.translatable("gui.bsp_core.team.k.teleport").getString(), Look.ON, mouseX, mouseY, () -> send(TeamActionPacket.target(Action.TELEPORT, id)));
            }
            yy += ROW;
        }
        CashMachine.wrapUp(g, font, Component.translatable("gui.bsp_core.team.access_hint"), mx + 6, my + mh - 4, mw - 12, MUTED);
    }

    private void memberRow(GuiGraphics g, int mx, int yy, int mw, TeamViewPacket.Member m) {
        g.fill(mx + 4, yy + ROW - 1, mx + (W - 16) - 4, yy + ROW, LINE);
        int dot = m.state() == TeamViewPacket.HERE ? GREEN : m.state() == TeamViewPacket.ELSEWHERE ? AMBER : GREY;
        g.fill(mx + 7, yy + 3, mx + 12, yy + 8, dot);
        int nameW = mw - 12 - 10 - 160;
        CashMachine.fit(g, font, m.name(), mx + 17, yy + 2, nameW, TEXT);
        if (m.leader()) {
            small(g, Component.translatable("gui.bsp_core.team.lead"), mx + 17 + Math.min(nameW, font.width(m.name())) + 4, yy + 3, GOLD & 0xFFFFFF);
        }
        String totems = (m.totems() == 0 ? Component.translatable("gui.bsp_core.team.totems.none") : m.totems() == 1 ? Component.translatable("gui.bsp_core.team.totems.one") : Component.translatable("gui.bsp_core.team.totems.n", m.totems())).getString();
        small(g, Component.literal(totems), mx + mw - 6 - 150, yy + 3, MUTED);
        String state = (m.state() == TeamViewPacket.HERE ? Component.translatable("gui.bsp_core.team.here") : m.state() == TeamViewPacket.ELSEWHERE ? Component.translatable("gui.bsp_core.team.elsewhere", m.where()) : ago(m.seenMs())).getString();
        CashMachine.fitRight(g, font, state, mx + mw - 6, yy + 2, 80, m.state() == TeamViewPacket.HERE ? GREEN & 0xFFFFFF : MUTED);
    }

    private Component ago(long ms) {
        long d = Math.max(0, System.currentTimeMillis() - ms);
        if (ms <= 0 || d < 60_000L) {
            return Component.translatable("gui.bsp_core.team.ago.now");
        }
        if (d < 3_600_000L) {
            return Component.translatable("gui.bsp_core.team.ago.m", d / 60_000L);
        }
        if (d < 86_400_000L) {
            return Component.translatable("gui.bsp_core.team.ago.h", d / 3_600_000L);
        }
        return Component.translatable("gui.bsp_core.team.ago.d", d / 86_400_000L);
    }

    private void drawInvite(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        small(g, Component.translatable("gui.bsp_core.team.invite.hint"), mx + 6, my + 6, MUTED);
        boolean may = view.leader() || view.membersInvite();
        if (!may) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.invite.leader_only"), mx + 6, my + 18, mw - 12, MUTED);
            return;
        }
        int yy = my + 16;
        if (view.candidates().isEmpty()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.invite.nobody"), mx + 6, yy + 2, mw - 12, MUTED);
        }
        int shown = 0;
        for (TeamViewPacket.Candidate c : view.candidates()) {
            if (shown++ < scroll) {
                continue;
            }
            if (yy + ROW > my + mh - 30) {
                break;
            }
            g.fill(mx + 4, yy + ROW - 1, mx + mw - 4, yy + ROW, LINE);
            g.fill(mx + 7, yy + 3, mx + 12, yy + 8, GREEN);
            CashMachine.fit(g, font, c.name(), mx + 17, yy + 2, mw - 12 - 10 - 90, TEXT);
            if (c.waitSeconds() > 0) {
                CashMachine.fitRight(g, font, Component.translatable("gui.bsp_core.team.invite.waiting", c.waitSeconds()).getString(), mx + mw - 6, yy + 2, 80, AMBER & 0xFFFFFF);
            } else {
                UUID id = c.id();
                key(g, mx + mw - 6 - 44, yy, 44, ROW - 1, Component.translatable("gui.bsp_core.team.k.inv").getString(), Look.ON, mouseX, mouseY, () -> send(TeamActionPacket.target(Action.INVITE, id)));
            }
            yy += ROW;
        }
        CashMachine.wrapUp(g, font, Component.translatable("gui.bsp_core.team.invite.note", 60), mx + 6, my + mh - 4, mw - 12, MUTED);
    }

    private void drawMessages(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        int top = my + 4;
        if (!view.pinText().isEmpty()) {
            int h = CashMachine.height(font, Component.literal(view.pinText()), mw - 20) + 11;
            g.fill(mx + 4, top, mx + mw - 4, top + h, 0xFF141A22);
            g.fill(mx + 4, top, mx + 7, top + h, GOLD);
            small(g, Component.translatable("gui.bsp_core.team.board.pinned", view.pinBy().toUpperCase(java.util.Locale.ROOT)), mx + 11, top + 2, MUTED);
            CashMachine.wrap(g, font, Component.literal(view.pinText()), mx + 11, top + 10, mw - 20, TEXT);
            top += h + 3;
        }
        int bottom = my + mh - 22;
        key(g, mx + mw - 6 - 40, my + mh - 19, 40, 16, Component.translatable("gui.bsp_core.team.k.send").getString(), Look.ON, mouseX, mouseY, this::post);
        if (view.lines().isEmpty()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.board.empty"), mx + 6, top + 4, mw - 12, MUTED);
            return;
        }
        // newest at the bottom; the wheel scrolls back through older lines
        CashMachine.clip(g, 0, 0, mx + 2, top, mw - 4, bottom - top);
        int yy = bottom;
        int skipped = 0;
        for (int i = view.lines().size() - 1; i >= 0 && yy > top; i--) {
            if (skipped++ < scroll) {
                continue;
            }
            TeamViewPacket.Line l = view.lines().get(i);
            String when = WHEN.format(Instant.ofEpochMilli(l.ms()).atZone(ZoneId.systemDefault()));
            Component body = Component.literal(l.name()).withStyle(s -> s.withColor(VIO & 0xFFFFFF)).append(Component.literal(" " + l.text()).withStyle(s -> s.withColor(TEXT)));
            int h = CashMachine.height(font, body, mw - 12 - 48);
            yy -= h + 2;
            small(g, Component.literal(when), mx + 6, yy + 1, MUTED);
            CashMachine.wrap(g, font, body, mx + 48, yy, mw - 12 - 48, TEXT);
            if (l.fresh()) {
                g.fill(mx + mw - 9, yy + 2, mx + mw - 6, yy + 5, GREEN);
            }
        }
        g.disableScissor();
    }

    private void post() {
        if (!lineText.isBlank()) {
            send(TeamActionPacket.text(Action.POST, lineText, 0));
            lineBox.setValue("");
            lineText = "";
            scroll = 0;
        }
    }

    private void drawSettings(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        if (!view.leader()) {
            String leader = view.members().stream().filter(TeamViewPacket.Member::leader).map(TeamViewPacket.Member::name).findFirst().orElse("?");
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.team.settings.only_leader", leader), mx + 6, my + 8, mw - 12, MUTED);
            return;
        }
        small(g, Component.translatable("gui.bsp_core.team.settings.name"), mx + 6, my + 6, MUTED);
        key(g, mx + 6 + 144, my + 15, 48, 16, Component.translatable("gui.bsp_core.team.k.rename").getString(), Look.NORMAL, mouseX, mouseY, () -> send(TeamActionPacket.text(Action.RENAME, nameText, 0)));
        swatchGrid(g, mx + 6 + 200, my + 13, 10, 2, view.colour(), id -> send(TeamActionPacket.text(Action.RECOLOUR, "", id))); // 96 wide, ends well inside the box
        int yy = my + 44;
        small(g, Component.translatable("gui.bsp_core.team.settings.members"), mx + 6, yy, MUTED);
        yy += 10;
        for (TeamViewPacket.Member m : view.members()) {
            if (m.leader()) {
                continue;
            }
            if (yy + ROW > my + mh - 40) {
                break;
            }
            g.fill(mx + 4, yy + ROW - 1, mx + mw - 4, yy + ROW, LINE);
            CashMachine.fit(g, font, m.name(), mx + 7, yy + 2, mw - 12 - 130, TEXT);
            UUID id = m.id();
            confirmKey(g, "hand:" + id, mx + mw - 6 - 122, yy, 66, ROW - 1, Component.translatable("gui.bsp_core.team.k.handover").getString(), Look.NORMAL, mouseX, mouseY, () -> send(TeamActionPacket.target(Action.TRANSFER, id)));
            confirmKey(g, "kick:" + id, mx + mw - 6 - 52, yy, 52, ROW - 1, Component.translatable("gui.bsp_core.team.k.remove").getString(), Look.WARN, mouseX, mouseY, () -> send(TeamActionPacket.target(Action.KICK, id)));
            yy += ROW;
        }
        small(g, Component.translatable("gui.bsp_core.team.settings.pin"), mx + 6, my + mh - 29, MUTED);
        boolean hasPin = !view.pinText().isEmpty();
        key(g, mx + mw - 6 - 54, my + mh - 19, 54, 16, Component.translatable(hasPin && pinText.isBlank() ? "gui.bsp_core.team.k.unpin" : "gui.bsp_core.team.k.pin").getString(), Look.ON, mouseX, mouseY,
                () -> send(TeamActionPacket.text(Action.PIN, pinText, 0)));
    }

    private void drawKeys(GuiGraphics g, int mouseX, int mouseY) {
        int ky = y + H - 28, kx = x + 8;
        if (mode == Mode.CREATE) {
            return;
        }
        kx = bar(g, kx, ky, 46, "gui.bsp_core.team.k.members", mode == Mode.TEAM ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.TEAM));
        kx = bar(g, kx, ky, 40, "gui.bsp_core.team.k.invite", mode == Mode.INVITE ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.INVITE));
        int mkx = kx;
        kx = bar(g, kx, ky, 54, "gui.bsp_core.team.k.messages", mode == Mode.MESSAGES ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.MESSAGES));
        if (view.unread() > 0 && mode != Mode.MESSAGES) {
            String n = String.valueOf(view.unread());
            int bw = 6 + font.width(n) * 3 / 4;
            g.fill(mkx + 54 - bw / 2 - 2, ky - 4, mkx + 54 - bw / 2 - 2 + bw, ky + 4, VIO);
            small(g, Component.literal(n), mkx + 54 - bw / 2 + 1, ky - 3, 0x10151C);
        }
        kx += 6;
        boolean on = view.chatOn();
        kx = bar(g, kx, ky, 54, on ? "gui.bsp_core.team.k.chat" : "gui.bsp_core.team.k.chat", on ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.CHAT_TOGGLE)));
        small(g, Component.translatable(on ? "gui.bsp_core.team.k.chat.on" : "gui.bsp_core.team.k.chat.off"), kx - 54 + 20, ky + KEY_H - 7, on ? VIO & 0xFFFFFF : MUTED);
        kx += 6;
        if (view.leader()) {
            kx = bar(g, kx, ky, 50, "gui.bsp_core.team.k.settings", mode == Mode.SETTINGS ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.SETTINGS));
            kx += 6;
            if (view.members().size() > 1) {
                confirmKey(g, "disband", kx, ky, 48, KEY_H, Component.translatable("gui.bsp_core.team.k.disband").getString(), Look.WARN, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.DISBAND)));
            } else {
                confirmKey(g, "leave", kx, ky, 56, KEY_H, Component.translatable("gui.bsp_core.team.k.leave").getString(), Look.WARN, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.LEAVE)));
            }
        } else {
            confirmKey(g, "leave", kx, ky, 56, KEY_H, Component.translatable("gui.bsp_core.team.k.leave").getString(), Look.WARN, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.LEAVE)));
        }
    }

    private int bar(GuiGraphics g, int kx, int ky, int w, String labelKey, Look look, int mouseX, int mouseY, Runnable run) {
        key(g, kx, ky, w, KEY_H, Component.translatable(labelKey).getString(), look, mouseX, mouseY, run);
        return kx + w + 3;
    }

    // ------------------------------------------------------------------ widgets drawn by hand

    private void key(GuiGraphics g, int kx, int ky, int w, int h, String label, Look look, int mouseX, int mouseY, Runnable run) {
        boolean hover = mouseX >= kx && mouseX < kx + w && mouseY >= ky && mouseY < ky + h;
        int edge = look == Look.ON ? VIO : look == Look.WARN ? RED : DIM;
        int text = look == Look.ON ? VIO & 0xFFFFFF : look == Look.WARN ? RED & 0xFFFFFF : look == Look.LOCKED ? GREY & 0xFFFFFF : MUTED;
        g.fill(kx, ky, kx + w, ky + h, edge);
        g.fill(kx + 1, ky + 1, kx + w - 1, ky + h - 1, hover && look != Look.LOCKED ? 0xFF1A1230 : SLOT);
        int lw = font.width(label) * 3 / 4;
        small(g, Component.literal(label), kx + w / 2 - lw / 2, ky + h / 2 - 3, text);
        if (look != Look.LOCKED) {
            hits.add(new Hit(kx, ky, w, h, run));
        }
    }

    /** A key that asks "SURE?" and acts on the second click within three seconds. */
    private void confirmKey(GuiGraphics g, String id, int kx, int ky, int w, int h, String label, Look look, int mouseX, int mouseY, Runnable run) {
        boolean asking = id.equals(confirm);
        key(g, kx, ky, w, h, asking ? Component.translatable("gui.bsp_core.team.k.sure").getString() : label, asking ? Look.ON : look, mouseX, mouseY, () -> {
            if (id.equals(confirm)) {
                confirm = null;
                run.run();
            } else {
                confirm = id;
                confirmUntil = System.currentTimeMillis() + 3000;
            }
        });
    }

    private void swatches(GuiGraphics g, int sx, int sy, int selected, int mouseX, int mouseY, java.util.function.IntConsumer pick) {
        swatchGrid(g, sx, sy, SW, 3, selected, pick);
    }

    /** The sixteen chat colours in two rows of eight, {@code size} square with {@code gap} between; the chosen one outlined. */
    private void swatchGrid(GuiGraphics g, int sx, int sy, int size, int gap, int selected, java.util.function.IntConsumer pick) {
        for (int i = 0; i < COLOURS.size(); i++) {
            ChatFormatting c = COLOURS.get(i);
            int cx = sx + (i % 8) * (size + gap), cy = sy + (i / 8) * (size + gap);
            Integer rgb = c.getColor();
            g.fill(cx, cy, cx + size, cy + size, 0xFF000000 | (rgb == null ? 0 : rgb));
            if (c.getId() == selected) {
                g.fill(cx - 1, cy - 1, cx + size + 1, cy, 0xFFFFFFFF);
                g.fill(cx - 1, cy + size, cx + size + 1, cy + size + 1, 0xFFFFFFFF);
                g.fill(cx - 1, cy, cx, cy + size, 0xFFFFFFFF);
                g.fill(cx + size, cy, cx + size + 1, cy + size, 0xFFFFFFFF);
            }
            int id = c.getId();
            hits.add(new Hit(cx, cy, size, size, () -> pick.accept(id)));
        }
    }

    private void small(GuiGraphics g, Component text, int sx, int sy, int colour) {
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (mx >= h.x() && mx < h.x() + h.w() && my >= h.y() && my < h.y() + h.h()) {
                    h.run().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mode == Mode.MESSAGES || mode == Mode.INVITE) {
            int max = Math.max(0, (mode == Mode.MESSAGES ? view.lines().size() : view.candidates().size()) - 1);
            scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (mode == Mode.MESSAGES && lineBox.isFocused()) {
                post();
                return true;
            }
            if (mode == Mode.CREATE && nameBox.isFocused()) {
                send(TeamActionPacket.text(Action.CREATE, nameText, colour));
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
