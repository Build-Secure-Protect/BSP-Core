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
 * supplier runs on the render thread while the page is shown (the renderer reads it about once a
 * second), so keep it cheap and never block in it.
 */
public final class BspBoards {
    private BspBoards() {}

    /**
     * One line: {@code text} at the left, {@code value} at the right (null for none), an item drawn
     * before the text ({@code ItemStack.EMPTY} for none), and the value's colour as RGB (0 for the
     * screen's gold).
     */
    public record BoardLine(Component text, @Nullable Component value, ItemStack icon, int colour) {
        public static BoardLine of(Component text, @Nullable Component value) {
            return new BoardLine(text, value, ItemStack.EMPTY, 0);
        }

        public static BoardLine of(Component text, @Nullable Component value, ItemStack icon) {
            return new BoardLine(text, value, icon == null ? ItemStack.EMPTY : icon, 0);
        }
    }

    /** A registered board. {@code id} is also what a screen's settings store, as {@code namespace:path}. */
    public record Board(ResourceLocation id, Component title, Supplier<List<BoardLine>> lines) {}

    private static final List<Board> BOARDS = new CopyOnWriteArrayList<>();

    /** Registers or replaces a board. Client side; call once, at client setup or when the mod's data first arrives. */
    public static void register(ResourceLocation id, Component title, Supplier<List<BoardLine>> lines) {
        Board board = new Board(id, title, lines);
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

    /** A board's lines right now; empty when the board is unknown or its supplier fails (the failure is logged once). */
    public static List<BoardLine> lines(ResourceLocation id) {
        Optional<Board> b = board(id);
        if (b.isEmpty()) {
            return List.of();
        }
        try {
            List<BoardLine> out = b.get().lines().get();
            return out == null ? List.of() : out;
        } catch (RuntimeException e) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Board {} failed to give its lines: {}", id, e.toString());
            return List.of();
        }
    }
}
