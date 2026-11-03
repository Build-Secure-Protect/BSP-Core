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
 * (messages) and the leader's settings. A team's screen has two tabs in the header, TEAM and
 * ALLIANCE (alliances look A, in teal): the allies with where they are, the alliance's board, the
 * perks the team gives, and the teams to invite (or, in no alliance, a name and CREATE). Fed by
 * {@link TeamViewPacket}; every key sends a {@link TeamActionPacket} and the server answers with the
 * fresh view.
 */
public class TeamScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 340, H = 236, KEY_H = 20, ROW = 13, SW = 12;
    /** An ally's row on the ALLIES view: two lines. */
    private static final int PROW = 19;
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, LINE = 0xFF1A1F29, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2,
            GREEN = 0xFF58F29A, AMBER = 0xFFF2B544, GREY = 0xFF555C6A, RED = 0xFFFF6B5C, GOLD = 0xFFFFD23A, AQUA = 0xFF55FFFF, TEAL = 0xFF4FD8C8;
    /** The perks a team may give its allies, in the order the PERKS view lists them ({@code TotemAccess} flags). */
    private static final int[] PERKS = {16, 4, 2, 8};
    private static final String[] PERK_KEYS = {"cloak", "ward", "alarm", "machines"};
    private static final List<ChatFormatting> COLOURS = new ArrayList<>();
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm");

    static {
        for (ChatFormatting c : ChatFormatting.values()) {
            if (c.isColor()) {
                COLOURS.add(c);
            }
        }
    }

    private enum Mode {
        CREATE, TEAM, INVITE, MESSAGES, SETTINGS,
        /** The ALLIANCE tab. */
        ALLIES, ALLY_BOARD, ALLY_PERKS, ALLY_INVITE, ALLY_CREATE;

        boolean ally() {
            return ordinal() >= ALLIES.ordinal();
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private enum Look { NORMAL, ON, WARN, LOCKED }

    private TeamViewPacket view;
    private Mode mode;
    private int x, y, colour = ChatFormatting.AQUA.getId(), scroll, ticks;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox nameBox, lineBox, pinBox, allyNameBox, allyLineBox;
    private String nameText = "", lineText = "", pinText = "", allyNameText = "", allyLineText = "";
    private boolean pinLoaded, nameLoaded, readPending, allyReadPending;
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
        } else if (mode.ally() && !msg.alliance().enabled()) {
            mode = Mode.TEAM;
        } else if (mode == Mode.ALLY_CREATE && msg.alliance().has()) {
            mode = Mode.ALLIES;
        } else if (mode.ally() && mode != Mode.ALLY_CREATE && !msg.alliance().has()) {
            mode = Mode.ALLY_CREATE;
        }
        if (msg.alliance().has() && allyNameBox != null && !allyNameText.isEmpty()) {
            allyNameBox.setValue(""); // made: the box starts empty if the alliance ends later
            allyNameText = "";
        }
        // lines that arrive while a board is open are read too; a board that went away (the alliance ended) marks nothing
        if (mode == Mode.MESSAGES && msg.unread() > 0) {
            readPending = true;
        }
        if (mode == Mode.ALLY_BOARD && msg.alliance().unread() > 0) {
            allyReadPending = true;
        } else if (mode != Mode.ALLY_BOARD) {
            allyReadPending = false;
        }
        scroll = Math.min(scroll, Math.max(0, scrollMax()));
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
        allyNameBox = new EditBox(font, mx + 6, my + 30, 160, 14, Component.translatable("gui.bsp_core.alliance.create.name"));
        allyNameBox.setMaxLength(32);
        allyNameBox.setHint(Component.translatable("gui.bsp_core.alliance.create.name").withStyle(ChatFormatting.DARK_GRAY));
        allyNameBox.setValue(allyNameText);
        allyNameBox.setResponder(t -> allyNameText = t);
        addRenderableWidget(allyNameBox);
        allyLineBox = new EditBox(font, mx + 6, my + mh - 18, mw - 12 - 44, 14, Component.translatable("gui.bsp_core.alliance.board.type"));
        allyLineBox.setMaxLength(256);
        allyLineBox.setHint(Component.translatable("gui.bsp_core.alliance.board.type").withStyle(ChatFormatting.DARK_GRAY));
        allyLineBox.setValue(allyLineText);
        allyLineBox.setResponder(t -> allyLineText = t);
        addRenderableWidget(allyLineBox);
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
        show(allyNameBox, mode == Mode.ALLY_CREATE && view.alliance().leader() && view.alliance().invite().isEmpty());
        show(allyLineBox, mode == Mode.ALLY_BOARD);
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
        if (mode == Mode.ALLY_BOARD && m != Mode.ALLY_BOARD && allyReadPending) {
            send(TeamActionPacket.of(Action.ALLY_READ));
            allyReadPending = false;
        }
        mode = m;
        scroll = 0;
        confirm = null;
        if (m == Mode.MESSAGES && view.unread() > 0) {
            readPending = true;
        }
        if (m == Mode.ALLY_BOARD && view.alliance().unread() > 0) {
            allyReadPending = true;
        }
        layout();
    }

    /** The frame and lit keys: violet on the TEAM tab, teal on the ALLIANCE tab. */
    private int accent() {
        return mode.ally() ? TEAL : VIO;
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
        if (allyReadPending) {
            send(TeamActionPacket.of(Action.ALLY_READ));
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
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, accent());
        g.fill(x, y, x + W, y + H, BG);
        // header: the title, or the TEAM and ALLIANCE tabs once there is a team
        drawTabs(g, mouseX, mouseY);
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
            case ALLIES -> drawAllies(g, mx, my, mw, mh, mouseX, mouseY);
            case ALLY_BOARD -> drawAllyBoard(g, mx, my, mw, mh, mouseX, mouseY);
            case ALLY_PERKS -> drawPerks(g, mx, my, mw, mh, mouseX, mouseY);
            case ALLY_INVITE -> drawAllyInvite(g, mx, my, mw, mh, mouseX, mouseY);
            case ALLY_CREATE -> drawAllyCreate(g, mx, my, mw, mh, mouseX, mouseY);
        }
        if (mode.ally()) {
            drawAllyKeys(g, mouseX, mouseY);
        } else {
            drawKeys(g, mouseX, mouseY);
        }
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
        Component lead = Component.translatable("gui.bsp_core.team.lead");
        int leadW = m.leader() ? Math.round(font.width(lead) * 0.75f) + 4 : 0;
        int nameW = mw - 12 - 10 - 160 - leadW; // a leader's name leaves room for the tag before the totem count
        CashMachine.fit(g, font, m.name(), mx + 17, yy + 2, nameW, TEXT);
        if (m.leader()) {
            small(g, lead, mx + 17 + Math.min(nameW, font.width(m.name())) + 4, yy + 3, GOLD & 0xFFFFFF);
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
            int nameMax = mw - 12 - 10 - 90;
            CashMachine.fit(g, font, c.name(), mx + 17, yy + 2, nameMax, TEXT);
            if (!c.server().isEmpty()) {
                int nw = Math.min(font.width(c.name()), nameMax) + 4;
                if (nameMax - nw > 16) {
                    CashMachine.fit(g, font, Component.translatable("gui.bsp_core.team.elsewhere", c.server()).getString(), mx + 17 + nw, yy + 2, nameMax - nw, MUTED);
                }
            }
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

    // ------------------------------------------------------------------ the ALLIANCE tab

    /** TEAM and ALLIANCE in the header, the open one lit and underlined; the alliance's new lines as a teal count beside it. */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        Component team = Component.translatable("gui.bsp_core.team.title");
        if (!view.hasTeam() || !view.alliance().enabled()) {
            g.drawString(font, team, x + 10, y + 6, VIO & 0xFFFFFF, false);
            return;
        }
        Component ally = Component.translatable("gui.bsp_core.alliance.title");
        int tw = font.width(team), aw = font.width(ally), ax = x + 10 + tw + 12;
        boolean onAlly = mode.ally();
        g.drawString(font, team, x + 10, y + 6, onAlly ? GREY & 0xFFFFFF : VIO & 0xFFFFFF, false);
        g.drawString(font, ally, ax, y + 6, onAlly ? TEAL & 0xFFFFFF : GREY & 0xFFFFFF, false);
        g.fill(onAlly ? ax : x + 10, y + 15, onAlly ? ax + aw : x + 10 + tw, y + 16, onAlly ? TEAL : VIO);
        hits.add(new Hit(x + 8, y + 3, tw + 4, 13, () -> {
            if (mode.ally()) {
                setMode(Mode.TEAM);
            }
        }));
        hits.add(new Hit(ax - 2, y + 3, aw + 4, 13, () -> {
            if (!mode.ally()) {
                setMode(view.alliance().has() ? Mode.ALLIES : Mode.ALLY_CREATE);
            }
        }));
        int unread = view.alliance().unread();
        if (!onAlly && (unread > 0 || !view.alliance().invite().isEmpty())) {
            String n = unread > 0 ? String.valueOf(unread) : "!";
            int bw = 6 + font.width(n) * 3 / 4;
            g.fill(ax + aw + 3, y + 5, ax + aw + 3 + bw, y + 13, TEAL);
            small(g, Component.literal(n), ax + aw + 6, y + 6, 0x10151C);
        }
    }

    private void drawAllyKeys(GuiGraphics g, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        if (!a.has()) {
            return;
        }
        // 309 of the 324 the bar has: ALLIES 42, INVITE TEAM 56, ALLY BOARD 54 | ALLY CHAT 52 | PERKS 38 | LEAVE 40
        int ky = y + H - 28, kx = x + 8;
        kx = bar(g, kx, ky, 42, "gui.bsp_core.alliance.k.allies", mode == Mode.ALLIES ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.ALLIES));
        kx = bar(g, kx, ky, 56, "gui.bsp_core.alliance.k.invite", mode == Mode.ALLY_INVITE ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.ALLY_INVITE));
        int bkx = kx, bw0 = 54;
        kx = bar(g, kx, ky, bw0, "gui.bsp_core.alliance.k.board", mode == Mode.ALLY_BOARD ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.ALLY_BOARD));
        if (a.unread() > 0 && mode != Mode.ALLY_BOARD) {
            String n = String.valueOf(a.unread());
            int bw = 6 + font.width(n) * 3 / 4;
            g.fill(bkx + bw0 - bw / 2 - 2, ky - 4, bkx + bw0 - bw / 2 - 2 + bw, ky + 4, TEAL);
            small(g, Component.literal(n), bkx + bw0 - bw / 2 + 1, ky - 3, 0x10151C);
        }
        kx += 4;
        boolean on = a.chatOn();
        int ckx = kx;
        kx = bar(g, kx, ky, 52, "gui.bsp_core.alliance.k.chat", on ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.ALLY_CHAT)));
        small(g, Component.translatable(on ? "gui.bsp_core.team.k.chat.on" : "gui.bsp_core.team.k.chat.off"), ckx + 20, ky + KEY_H - 7, on ? TEAL & 0xFFFFFF : MUTED);
        kx += 4;
        kx = bar(g, kx, ky, 38, "gui.bsp_core.alliance.k.perks", mode == Mode.ALLY_PERKS ? Look.ON : Look.NORMAL, mouseX, mouseY, () -> setMode(Mode.ALLY_PERKS));
        if (a.leader()) {
            kx += 4;
            confirmKey(g, "ally_leave", kx, ky, 40, KEY_H, Component.translatable("gui.bsp_core.alliance.k.leave").getString(), Look.WARN, mouseX, mouseY,
                    () -> send(TeamActionPacket.of(Action.ALLY_LEAVE)));
        }
    }

    private static Component teamLabel(String name, int colour) {
        ChatFormatting c = ChatFormatting.getById(colour);
        return Component.literal(name).withStyle(c == null || !c.isColor() ? ChatFormatting.AQUA : c);
    }

    /** A dimension id as a short name: Overworld, Nether, End, or the id's own name. */
    private static String dimName(String dim) {
        return switch (dim) {
            case "" -> "";
            case "minecraft:overworld" -> Component.translatable("gui.bsp_core.alliance.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("gui.bsp_core.alliance.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("gui.bsp_core.alliance.dim.end").getString();
            default -> {
                String path = dim.substring(dim.indexOf(':') + 1).replace('_', ' ');
                yield path.isEmpty() ? dim : Character.toUpperCase(path.charAt(0)) + path.substring(1);
            }
        };
    }

    /** Three-quarter text shrunk further to stay within {@code maxW}. */
    private void smallFit(GuiGraphics g, String text, int sx, int sy, int maxW, int colour) {
        float scale = Math.min(0.75f, maxW / (float) Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(sx, sy + (6 - 8 * scale) / 2f + 1, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Rows the ALLIES view lists: a heading per ally team and a row per player. */
    private int allyRowCount() {
        int n = 0;
        for (TeamViewPacket.AllyTeam t : view.alliance().teams()) {
            if (!t.mine()) {
                n += 1 + t.players().size();
            }
        }
        return n;
    }

    private void drawAllies(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        g.drawString(font, Component.literal(a.name()).withStyle(s -> s.withColor(TEAL & 0xFFFFFF)), mx + 6, my + 5, TEXT, false);
        CashMachine.fitRight(g, font, Component.translatable("gui.bsp_core.alliance.teams", a.teams().size(), a.maxTeams()).getString(), mx + mw - 6, my + 5, 120, MUTED);
        int top = my + 18, bottom = my + mh - 4;
        if (a.teams().size() < 2) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.alliance.allies.none"), mx + 6, top + 2, mw - 12, MUTED);
            return;
        }
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();
        boolean tp = a.canTeleport();
        int yy = top, skipped = 0;
        for (TeamViewPacket.AllyTeam t : a.teams()) {
            if (t.mine()) {
                continue;
            }
            if (skipped++ >= scroll) {
                if (yy + ROW > bottom) {
                    return;
                }
                g.fill(mx + 7, yy + 3, mx + 12, yy + 8, 0xFF000000 | colourOf(t.colour()));
                CashMachine.fit(g, font, teamLabel(t.name(), t.colour()).getString(), mx + 17, yy + 2, 120, colourOf(t.colour()));
                long online = t.players().stream().filter(p -> p.state() != TeamViewPacket.OFFLINE).count();
                String note = Component.translatable(t.shares() ? "gui.bsp_core.alliance.team_note.shares" : "gui.bsp_core.alliance.team_note.hidden", online).getString();
                smallFit(g, note, mx + mw - 6 - Math.min(170, font.width(note) * 3 / 4), yy + 3, 170, MUTED);
                yy += ROW;
            }
            for (TeamViewPacket.Ally p : t.players()) {
                if (skipped++ < scroll) {
                    continue;
                }
                if (yy + PROW > bottom) {
                    return;
                }
                allyRow(g, mx, yy, mw, p, t.shares(), tp && p.state() != TeamViewPacket.OFFLINE && !p.id().equals(me), mouseX, mouseY);
                yy += PROW;
            }
        }
    }

    private static int colourOf(int id) {
        ChatFormatting c = ChatFormatting.getById(id);
        Integer rgb = c == null ? null : c.getColor();
        return rgb == null ? 0x55FFFF : rgb;
    }

    /**
     * One ally on two lines: a dot, the name (with LEAD) and their block when shared on top, where they are under it in small
     * text; TELEPORT at the right when a teleport mod answers. Two lines keep every part readable at the longest names and ids.
     */
    private void allyRow(GuiGraphics g, int mx, int yy, int mw, TeamViewPacket.Ally p, boolean shares, boolean teleport, int mouseX, int mouseY) {
        g.fill(mx + 14, yy + PROW - 1, mx + mw - 4, yy + PROW, LINE);
        int dot = p.state() == TeamViewPacket.HERE ? GREEN : p.state() == TeamViewPacket.ELSEWHERE ? AMBER : GREY;
        g.fill(mx + 17, yy + 4, mx + 21, yy + 8, dot);
        Component lead = Component.translatable("gui.bsp_core.team.lead");
        int nameW = 120, leadW = p.leader() ? Math.round(font.width(lead) * 0.75f) + 3 : 0;
        CashMachine.fit(g, font, p.name(), mx + 25, yy + 2, nameW - leadW, TEXT);
        if (p.leader()) {
            small(g, lead, mx + 25 + Math.min(nameW - leadW, font.width(p.name())) + 3, yy + 3, GOLD & 0xFFFFFF);
        }
        int right = mx + mw - 6 - (teleport ? 48 : 0);
        String where;
        if (p.state() == TeamViewPacket.OFFLINE) {
            where = Component.translatable("gui.bsp_core.alliance.where.offline", ago(p.seenMs())).getString();
        } else {
            String dim = dimName(p.dim());
            String at = p.state() == TeamViewPacket.HERE ? Component.translatable("gui.bsp_core.team.here").getString()
                    : Component.translatable("gui.bsp_core.team.elsewhere", p.where()).getString();
            where = dim.isEmpty() ? at : at + " · " + dim;
        }
        smallFit(g, where, mx + 25, yy + 11, right - mx - 25, p.state() == TeamViewPacket.HERE ? GREEN & 0xFFFFFF : MUTED);
        if (p.state() != TeamViewPacket.OFFLINE) {
            String at = p.hasPos() ? p.x() + ", " + p.y() + ", " + p.z() : Component.translatable(shares ? "gui.bsp_core.alliance.where.unknown" : "gui.bsp_core.alliance.where.hidden").getString();
            int room = right - (mx + 25 + nameW + 4);
            int w = Math.min(room, Math.round(font.width(at) * 0.75f));
            smallFit(g, at, right - w, yy + 3, room, p.hasPos() ? TEXT : GREY & 0xFFFFFF);
        }
        if (teleport) {
            UUID id = p.id();
            key(g, mx + mw - 6 - 44, yy + 2, 44, PROW - 5, Component.translatable("gui.bsp_core.team.k.teleport").getString(), Look.ON, mouseX, mouseY,
                    () -> send(TeamActionPacket.target(Action.ALLY_TELEPORT, id)));
        }
    }

    private void drawAllyBoard(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        int top = my + 4, bottom = my + mh - 22;
        key(g, mx + mw - 6 - 40, my + mh - 19, 40, 16, Component.translatable("gui.bsp_core.team.k.send").getString(), Look.ON, mouseX, mouseY, this::postAlly);
        if (a.lines().isEmpty()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.alliance.board.empty"), mx + 6, top + 4, mw - 12, MUTED);
            return;
        }
        CashMachine.clip(g, 0, 0, mx + 2, top, mw - 4, bottom - top);
        int yy = bottom, skipped = 0;
        for (int i = a.lines().size() - 1; i >= 0 && yy > top; i--) {
            if (skipped++ < scroll) {
                continue;
            }
            TeamViewPacket.AllyLine l = a.lines().get(i);
            String when = WHEN.format(Instant.ofEpochMilli(l.ms()).atZone(ZoneId.systemDefault()));
            int tc = colourOf(l.colour());
            Component body = Component.literal("[" + l.team() + "] ").withStyle(s -> s.withColor(tc))
                    .append(Component.literal(l.name()).withStyle(s -> s.withColor(TEAL & 0xFFFFFF)))
                    .append(Component.literal(" " + l.text()).withStyle(s -> s.withColor(TEXT)));
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

    private void postAlly() {
        if (!allyLineText.isBlank()) {
            send(TeamActionPacket.text(Action.ALLY_POST, allyLineText, 0));
            allyLineBox.setValue("");
            allyLineText = "";
            scroll = 0;
        }
    }

    /** What the team gives its allies (the leader switches it), whether it shares coordinates, and what it gets back. */
    private void drawPerks(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        boolean lead = a.leader();
        Component head = Component.translatable("gui.bsp_core.alliance.perks.head", teamName());
        String note = Component.translatable("gui.bsp_core.alliance.perks.leader_only").getString();
        int noteW = lead ? 0 : Math.min(124, Math.round(font.width(note) * 0.75f));
        smallFit(g, head.getString(), mx + 6, my + 5, mw - 12 - (lead ? 0 : noteW + 8), MUTED);
        if (!lead) {
            smallFit(g, note, mx + mw - 6 - noteW, my + 5, 124, GREY & 0xFFFFFF);
        }
        int yy = my + 16;
        for (int i = 0; i < PERKS.length; i++) {
            int flag = PERKS[i];
            boolean allowed = (a.allowed() & flag) != 0, on = (a.myPerks() & flag) != 0;
            g.drawString(font, Component.translatable("gui.bsp_core.alliance.perk.long." + PERK_KEYS[i]), mx + 6, yy + 3, allowed ? TEXT : GREY & 0xFFFFFF, false);
            toggle(g, mx + mw - 6 - 36, yy, on, allowed && lead, !allowed, mouseX, mouseY,
                    () -> send(TeamActionPacket.text(Action.ALLY_PERK, "", flag | (on ? 0 : TeamActionPacket.ON))));
            yy += 15;
        }
        g.fill(mx + 6, yy + 1, mx + mw - 6, yy + 2, LINE);
        yy += 5;
        g.drawString(font, Component.translatable("gui.bsp_core.alliance.share"), mx + 6, yy + 3, TEXT, false);
        boolean share = a.myShare();
        toggle(g, mx + mw - 6 - 36, yy, share, lead, false, mouseX, mouseY, () -> send(TeamActionPacket.text(Action.ALLY_SHARE, "", share ? 0 : 1)));
        yy += 15;
        smallFit(g, Component.translatable(share ? "gui.bsp_core.alliance.share.on" : "gui.bsp_core.alliance.share.off").getString(), mx + 6, yy, mw - 12, MUTED);
        yy += 11;
        small(g, Component.translatable("gui.bsp_core.alliance.perks.get"), mx + 6, yy, MUTED);
        yy += 9;
        int footTop = my + mh - 4 - CashMachine.height(font, Component.translatable("gui.bsp_core.alliance.perks.foot"), mw - 12);
        int others = a.teams().size() - 1, listed = 0;
        for (TeamViewPacket.AllyTeam t : a.teams()) {
            if (t.mine()) {
                continue;
            }
            if (yy + 19 > footTop && listed < others - 1) {
                smallFit(g, Component.translatable("gui.bsp_core.alliance.perks.more", others - listed).getString(), mx + 6, yy + 1, mw - 12, MUTED);
                break;
            }
            listed++;
            StringBuilder got = new StringBuilder();
            for (int i = 0; i < PERKS.length; i++) {
                if ((t.perks() & PERKS[i]) != 0) {
                    got.append(got.length() == 0 ? "" : " · ").append(Component.translatable("gui.bsp_core.alliance.perk." + PERK_KEYS[i]).getString());
                }
            }
            if (t.shares()) {
                got.append(got.length() == 0 ? "" : " · ").append(Component.translatable("gui.bsp_core.alliance.perk.coords").getString());
            }
            String gets = got.length() == 0 ? Component.translatable("gui.bsp_core.alliance.perk.nothing").getString() : got.toString();
            int nw = Math.min(90, font.width(t.name()));
            CashMachine.fit(g, font, t.name(), mx + 6, yy, 90, colourOf(t.colour()));
            smallFit(g, gets, mx + 6 + nw + 6, yy + 1, mw - 12 - nw - 6, TEXT);
            yy += 10;
        }
        CashMachine.wrapUp(g, font, Component.translatable("gui.bsp_core.alliance.perks.foot"), mx + 6, my + mh - 4, mw - 12, GREY & 0xFFFFFF);
    }

    /** An ON / OFF switch, 36 wide; {@code locked} (the server forbids the perk) reads OFF in grey and never acts. */
    private void toggle(GuiGraphics g, int tx, int ty, boolean on, boolean may, boolean locked, int mouseX, int mouseY, Runnable flip) {
        String label = Component.translatable(locked ? "gui.bsp_core.alliance.toggle.locked" : on ? "gui.bsp_core.alliance.toggle.on" : "gui.bsp_core.alliance.toggle.off").getString();
        Look look = locked || !may ? Look.LOCKED : on ? Look.ON : Look.NORMAL;
        if (!locked && !may && on) {
            look = Look.NORMAL; // a member sees the state but cannot flip it
            g.fill(tx, ty, tx + 36, ty + 13, TEAL);
            g.fill(tx + 1, ty + 1, tx + 35, ty + 12, SLOT);
            int lw = font.width(label) * 3 / 4;
            small(g, Component.literal(label), tx + 18 - lw / 2, ty + 3, TEAL & 0xFFFFFF);
            return;
        }
        key(g, tx, ty, 36, 13, label, look, mouseX, mouseY, flip);
    }

    private void drawAllyInvite(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        smallFit(g, Component.translatable("gui.bsp_core.alliance.invite.hint").getString(), mx + 6, my + 6, mw - 12, MUTED);
        int yy = my + 16;
        int bottom = my + mh - 14;
        if (a.candidates().isEmpty()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.alliance.invite.nobody"), mx + 6, yy + 2, mw - 12, MUTED);
        }
        long waiting = a.candidates().stream().filter(c -> c.waitSeconds() > 0).count();
        boolean full = a.teams().size() + waiting >= a.maxTeams();
        int shown = 0;
        for (TeamViewPacket.TeamCandidate c : a.candidates()) {
            if (shown++ < scroll) {
                continue;
            }
            if (yy + ROW > bottom) {
                break;
            }
            g.fill(mx + 4, yy + ROW - 1, mx + mw - 4, yy + ROW, LINE);
            g.fill(mx + 7, yy + 3, mx + 12, yy + 8, 0xFF000000 | colourOf(c.colour()));
            CashMachine.fit(g, font, c.name(), mx + 17, yy + 2, 90, colourOf(c.colour()));
            int right = mx + mw - 6;
            String status = c.waitSeconds() > 0 ? Component.translatable("gui.bsp_core.team.invite.waiting", c.waitSeconds()).getString()
                    : c.blockedHours() > 0 ? Component.translatable("gui.bsp_core.alliance.invite.blocked", c.blockedHours()).getString()
                    : !c.leaderOnline() ? Component.translatable("gui.bsp_core.alliance.invite.offline").getString()
                    : full ? Component.translatable("gui.bsp_core.alliance.invite.full").getString() : null;
            boolean keyShown = status == null && a.leader();
            int statusW = status == null ? 0 : Math.min(112, Math.round(font.width(status) * 0.75f));
            int aboutEnd = keyShown ? right - 48 : status == null ? right : right - statusW - 6;
            smallFit(g, Component.translatable("gui.bsp_core.alliance.invite.about", c.leader(), c.members()).getString(), mx + 111, yy + 3, aboutEnd - mx - 111, MUTED);
            if (status != null) {
                smallFit(g, status, right - statusW, yy + 3, 112, (c.waitSeconds() > 0 ? AMBER : GREY) & 0xFFFFFF);
            } else if (keyShown) {
                UUID id = c.id();
                key(g, right - 44, yy, 44, ROW - 1, Component.translatable("gui.bsp_core.team.k.inv").getString(), Look.ON, mouseX, mouseY, () -> send(TeamActionPacket.target(Action.ALLY_INVITE, id)));
            }
            yy += ROW;
        }
        String foot = Component.translatable(a.leader() ? "gui.bsp_core.alliance.invite.note" : "gui.bsp_core.alliance.invite.leader_only", a.name(), a.teams().size(), a.maxTeams()).getString();
        smallFit(g, foot, mx + 6, my + mh - 10, mw - 12, MUTED);
    }

    /** No alliance yet: an invite waiting (the leader answers it), or a name and CREATE. */
    private void drawAllyCreate(GuiGraphics g, int mx, int my, int mw, int mh, int mouseX, int mouseY) {
        TeamViewPacket.AllianceView a = view.alliance();
        int yy = my + 6;
        if (!a.invite().isEmpty()) {
            // the line may take two lines with long names: the keys sit below it, never beside it
            Component line = Component.translatable("gui.bsp_core.alliance.waiting.line", a.inviteFrom(), a.invite());
            int lh = Math.min(2 * 9, CashMachine.height(font, line, mw - 20));
            int boxH = 12 + lh + 20;
            g.fill(mx + 4, yy, mx + mw - 4, yy + boxH, 0xFF0E2624);
            g.fill(mx + 4, yy, mx + 7, yy + boxH, TEAL);
            small(g, Component.translatable("gui.bsp_core.alliance.waiting.title", a.inviteSeconds()), mx + 11, yy + 3, TEAL & 0xFFFFFF);
            CashMachine.wrap(g, font, line, mx + 11, yy + 12, mw - 20, TEXT);
            int ky = yy + 12 + lh + 3;
            if (a.leader()) {
                key(g, mx + mw - 6 - 132, ky, 64, 14, Component.translatable("gui.bsp_core.alliance.k.accept").getString(), Look.ON, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.ALLY_ACCEPT)));
                key(g, mx + mw - 6 - 64, ky, 64, 14, Component.translatable("gui.bsp_core.alliance.k.decline").getString(), Look.WARN, mouseX, mouseY, () -> send(TeamActionPacket.of(Action.ALLY_DECLINE)));
            } else {
                smallFit(g, Component.translatable("gui.bsp_core.alliance.waiting.leader").getString(), mx + 11, ky + 4, mw - 20, MUTED);
            }
            return;
        }
        int hintH = CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.alliance.create.hint"), mx + 6, yy, mw - 12, MUTED);
        if (!a.leader()) {
            CashMachine.wrap(g, font, Component.translatable("gui.bsp_core.alliance.create.leader_only"), mx + 6, yy + hintH + 6, mw - 12, GREY & 0xFFFFFF);
            return;
        }
        allyNameBox.setY(yy + hintH + 6);
        key(g, mx + 6 + 166, yy + hintH + 5, 70, 16, Component.translatable("gui.bsp_core.alliance.k.create").getString(), Look.ON, mouseX, mouseY,
                () -> send(TeamActionPacket.text(Action.ALLY_CREATE, allyNameText, 0)));
        CashMachine.wrapUp(g, font, Component.translatable("gui.bsp_core.alliance.create.rules", a.maxTeams()), mx + 6, my + mh - 4, mw - 12, GREY & 0xFFFFFF);
    }

    private int bar(GuiGraphics g, int kx, int ky, int w, String labelKey, Look look, int mouseX, int mouseY, Runnable run) {
        key(g, kx, ky, w, KEY_H, Component.translatable(labelKey).getString(), look, mouseX, mouseY, run);
        return kx + w + 3;
    }

    // ------------------------------------------------------------------ widgets drawn by hand

    private void key(GuiGraphics g, int kx, int ky, int w, int h, String label, Look look, int mouseX, int mouseY, Runnable run) {
        boolean hover = mouseX >= kx && mouseX < kx + w && mouseY >= ky && mouseY < ky + h;
        int edge = look == Look.ON ? accent() : look == Look.WARN ? RED : DIM;
        int text = look == Look.ON ? accent() & 0xFFFFFF : look == Look.WARN ? RED & 0xFFFFFF : look == Look.LOCKED ? GREY & 0xFFFFFF : MUTED;
        g.fill(kx, ky, kx + w, ky + h, edge);
        g.fill(kx + 1, ky + 1, kx + w - 1, ky + h - 1, hover && look != Look.LOCKED ? (mode.ally() ? 0xFF0E2624 : 0xFF1A1230) : SLOT);
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

    /** How far the current view can scroll: one less than its rows. */
    private int scrollMax() {
        return Math.max(0, (switch (mode) {
            case MESSAGES -> view.lines().size();
            case INVITE -> view.candidates().size();
            case ALLY_BOARD -> view.alliance().lines().size();
            case ALLY_INVITE -> view.alliance().candidates().size();
            case ALLIES -> allyRowCount();
            default -> 0;
        }) - 1);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mode == Mode.MESSAGES || mode == Mode.INVITE || mode == Mode.ALLIES || mode == Mode.ALLY_BOARD || mode == Mode.ALLY_INVITE) {
            // the boards grow upward (the wheel up shows older lines); the lists read downward (the wheel down shows more)
            boolean upward = mode == Mode.MESSAGES || mode == Mode.ALLY_BOARD;
            int step = (delta > 0) == upward ? 1 : -1;
            scroll = Math.max(0, Math.min(scrollMax(), scroll + step));
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
            if (mode == Mode.ALLY_BOARD && allyLineBox.isFocused()) {
                postAlly();
                return true;
            }
            if (mode == Mode.ALLY_CREATE && allyNameBox.isFocused()) {
                send(TeamActionPacket.text(Action.ALLY_CREATE, allyNameText, 0));
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }
}
