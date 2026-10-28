package com.mrgregles.bsp_core.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Boards other mods put on the Score Screen (BSP-Teleport request R8). A mod registers a board once
 * on the client, with an id, a title and a supplier of lines read from data the mod already syncs;
 * admins then pick it in a screen's settings, alone or in the rotation with the leaderboard. The
 * supplier runs on the render thread while the page is shown, at most once a second however many
 * screens show it (call {@link #invalidate} when the data changes to show it at once), so keep it
 * cheap and never block in it.
 */
public final class BspBoards {
    private BspBoards() {}

    /**
     * One line: {@code text} at the left, {@code value} at the right (null for none), an item drawn
     * before the text ({@code ItemStack.EMPTY} for none), the value's colour as RGB (0 for the
     * screen's gold), and a small grey {@code note} (null for none: an age such as "3 h ago"), drawn
     * under the text in Tiles and before the value in the Ledger and Side looks. The four-part
     * constructor and the {@code of} methods make a line without a note, as before.
     */
    public record BoardLine(Component text, @Nullable Component value, ItemStack icon, int colour, @Nullable Component note) {
        public BoardLine(Component text, @Nullable Component value, ItemStack icon, int colour) {
            this(text, value, icon, colour, null);
        }

        public static BoardLine of(Component text, @Nullable Component value) {
            return new BoardLine(text, value, ItemStack.EMPTY, 0);
        }

        public static BoardLine of(Component text, @Nullable Component value, ItemStack icon) {
            return new BoardLine(text, value, icon == null ? ItemStack.EMPTY : icon, 0);
        }

        /** This line with a note. */
        public BoardLine withNote(@Nullable Component note) {
            return new BoardLine(text, value, icon, colour, note);
        }
    }

    /** A registered board. {@code id} is also what a screen's settings store, as {@code namespace:path}. */
    public record Board(ResourceLocation id, Component title, Supplier<List<BoardLine>> lines) {}

    private static final List<Board> BOARDS = new CopyOnWriteArrayList<>();
    /** Each board's lines as last read, and when: every screen showing a board, every frame, shares one read a second. */
    private static final java.util.Map<ResourceLocation, Cached> CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_MS = 1000;

    private record Cached(long atMs, List<BoardLine> lines) {}

    /** Registers or replaces a board. Client side; call once, at client setup or when the mod's data first arrives. */
    public static void register(ResourceLocation id, Component title, Supplier<List<BoardLine>> lines) {
        Board board = new Board(id, title, lines);
        CACHE.remove(id);
        for (int i = 0; i < BOARDS.size(); i++) {
            if (BOARDS.get(i).id().equals(id)) {
                BOARDS.set(i, board);
                return;
            }
        }
        BOARDS.add(board);
    }

    public static void unregister(ResourceLocation id) {
        BOARDS.removeIf(b -> b.id().equals(id));
        CACHE.remove(id);
    }

    /** The board's data changed: the next read asks its supplier at once instead of waiting out the second. */
    public static void invalidate(ResourceLocation id) {
        CACHE.remove(id);
    }

    /** Every registered board, in registration order. */
    public static List<Board> boards() {
        return List.copyOf(BOARDS);
    }

    public static Optional<Board> board(ResourceLocation id) {
        for (Board b : BOARDS) {
            if (b.id().equals(id)) {
                return Optional.of(b);
            }
        }
        return Optional.empty();
    }

    /**
     * A board's lines: its supplier's answer, asked at most once a second (or at once after {@link #invalidate}); empty
     * when the board is unknown or its supplier fails (the failure is logged).
     */
    public static List<BoardLine> lines(ResourceLocation id) {
        long now = System.currentTimeMillis();
        Cached c = CACHE.get(id);
        if (c != null && now - c.atMs() < CACHE_MS) {
            return c.lines();
        }
        Optional<Board> b = board(id);
        if (b.isEmpty()) {
            return List.of();
        }
        List<BoardLine> out;
        try {
            out = b.get().lines().get();
            out = out == null ? List.of() : List.copyOf(out);
        } catch (RuntimeException e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Board {} failed to give its lines: {}", id, e.toString());
            out = List.of();
        }
        CACHE.put(id, new Cached(now, out));
        return out;
    }
}
