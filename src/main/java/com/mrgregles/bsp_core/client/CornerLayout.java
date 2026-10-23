package com.mrgregles.bsp_core.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds a free place in the top left corner of a screen for a row of small buttons, stepping past whatever already stands there.
 * Rectangles are {x, y, width, height}. Plain arithmetic, so it can be checked without a client.
 */
public final class CornerLayout {
    private CornerLayout() {}

    /**
     * The {@code slot}th button's place ({x, y}), counting from the corner along the top edge: stepping right past any blocked
     * rectangle and down a row when the left half of the screen is used up; the corner itself when nothing is free.
     */
    public static int[] place(List<int[]> obstacles, int screenWidth, int screenHeight, int slot, int w, int h, int gap, int margin) {
        List<int[]> blocked = new ArrayList<>(obstacles);
        int x = margin, y = margin, limit = Math.max(margin + w, screenWidth / 2);
        for (int placed = 0; ; ) {
            int[] hit = overlap(blocked, x, y, w, h);
            if (hit != null) {
                x = hit[0] + hit[2] + gap;
            } else if (placed == slot) {
                return new int[]{x, y};
            } else {
                blocked.add(new int[]{x, y, w, h}); // the buttons before this one
                placed++;
                x += w + gap;
            }
            if (x + w > limit) {
                x = margin;
                y += h + gap;
                if (y + h > screenHeight) {
                    return new int[]{margin, margin};
                }
            }
        }
    }

    private static int[] overlap(List<int[]> blocked, int x, int y, int w, int h) {
        for (int[] b : blocked) {
            if (x < b[0] + b[2] && x + w > b[0] && y < b[1] + b[3] && y + h > b[1]) {
                return b;
            }
        }
        return null;
    }
}
