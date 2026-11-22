package com.mrgregles.bsp_core.looks;

import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.UUID;

/**
 * How one Shatter Totem is dressed: its body (one colour, a gradient of two or three, or painted pixels), its head (the totem's own
 * face or the owner's skin) and its hat. A look is made for one owner and is shown only while that player owns the totem, so a
 * steal, a transfer or a staff reassignment leaves the totem plain, on the block and on its item alike.
 *
 * <p>Painted pixels follow {@link #PARTS}: every face of the body and head cuboids, one texture pixel per model pixel, in the
 * order the parts are listed and, within a part, the faces east, west, up, down, south, north, row by row. Each pixel is an
 * index into the painting's palette of up to {@value #MAX_PAINT_COLOURS} colours, two to a byte (high half first).
 */
public final class TotemLook {
    /** Item and block entity tag. */
    public static final String TAG = "Look";

    public enum Mode { SOLID, GRADIENT, PAINT }

    public enum Head { TOTEM, PLAYER }

    /** Which way a gradient runs over the body: from the top of the head down to the base, or from side to side. */
    public enum Run { UP, SIDE }

    public static final int MAX_STOPS = 3, MAX_PAINT_COLOURS = 16;

    /** The painted cuboids and their sizes in model pixels (width, height, depth), as the Guardian model has them. */
    public static final String[] PARTS = {"head", "torso", "knee_l", "knee_r", "arm_l", "arm_r", "hand_l", "hand_r"};
    private static final int[][] SIZES = {{8, 8, 8}, {8, 10, 5}, {5, 4, 8}, {5, 4, 8}, {3, 4, 7}, {3, 4, 7}, {3, 6, 4}, {3, 6, 4}};
    /** Pixels a painting covers: every face of every part. */
    public static final int PAINT_PIXELS;
    /** Bytes a painting takes, two pixels to a byte. */
    public static final int PAINT_BYTES;

    static {
        int n = 0;
        for (int[] s : SIZES) {
            n += 2 * (s[0] * s[1] + s[0] * s[2] + s[1] * s[2]);
        }
        PAINT_PIXELS = n;
        PAINT_BYTES = (n + 1) / 2;
    }

    /** A part's size in model pixels (width, height, depth). */
    public static int[] size(String part) {
        for (int i = 0; i < PARTS.length; i++) {
            if (PARTS[i].equals(part)) {
                return SIZES[i].clone();
            }
        }
        throw new IllegalArgumentException(part);
    }

    private final UUID owner;
    private final Mode mode;
    private final int[] colours;
    private final Run run;
    private final Head head;
    private final String hat;
    private final int[] palette;
    private final byte[] paint;
    /** When the look was made (ms): a staff clear of a player's looks drops every look made before it, wherever the totem is. */
    private final long made;
    /** The hat's cloth in the body's colours (a darker shade for its trim) instead of its own. */
    private final boolean hatTint;
    /** The client's texture key for this look, kept on it so a dressed totem does not rebuild it every frame. Not saved. */
    public transient Object renderKey;
    /** {@link #mainColour()} once worked out (a painting's takes a count of its pixels); -1 before. */
    private int mainColourCache = -1;

    private TotemLook(UUID owner, Mode mode, int[] colours, Run run, Head head, String hat, boolean hatTint, int[] palette, byte[] paint, long made) {
        this.hatTint = hatTint;
        this.made = made;
        this.owner = owner;
        this.mode = mode;
        this.colours = colours;
        this.run = run;
        this.head = head;
        this.hat = hat == null ? "" : hat;
        this.palette = palette;
        this.paint = paint;
    }

    /** One colour (0xRRGGBB) over the whole body. */
    public static TotemLook solid(UUID owner, int rgb) {
        return new TotemLook(owner, Mode.SOLID, new int[]{rgb}, Run.UP, Head.TOTEM, "", false, new int[0], new byte[0], System.currentTimeMillis());
    }

    /** A gradient of two or three colours (0xRRGGBB), the first at the top (or the left side). */
    public static TotemLook gradient(UUID owner, Run run, int... stops) {
        return new TotemLook(owner, Mode.GRADIENT, stops.clone(), run, Head.TOTEM, "", false, new int[0], new byte[0], System.currentTimeMillis());
    }

    /** Painted pixels: {@code paint} holds {@link #PAINT_PIXELS} indices into {@code palette}, two to a byte. */
    public static TotemLook painted(UUID owner, int[] palette, byte[] paint) {
        return new TotemLook(owner, Mode.PAINT, new int[0], Run.UP, Head.TOTEM, "", false, palette.clone(), paint.clone(), System.currentTimeMillis());
    }

    public TotemLook withHead(Head h) {
        return new TotemLook(owner, mode, colours, run, h, hat, hatTint, palette, paint, made);
    }

    /** The same look with this hat ("" for none). */
    public TotemLook withHat(String h) {
        return new TotemLook(owner, mode, colours, run, head, h, hatTint, palette, paint, made);
    }

    /** The same look with the hat in the body's colours (true) or its own (false). */
    public TotemLook withHatTint(boolean tint) {
        return new TotemLook(owner, mode, colours, run, head, hat, tint, palette, paint, made);
    }

    public boolean hatTint() {
        return hatTint;
    }

    /** The body's main colour, for a hat in the body's colours: the colour, a gradient's middle, a painting's most used colour. */
    public int mainColour() {
        if (mainColourCache >= 0) {
            return mainColourCache;
        }
        return mainColourCache = mainColourNow();
    }

    private int mainColourNow() {
        if (mode == Mode.PAINT) {
            int[] uses = new int[palette.length];
            for (int i = 0; i < PAINT_PIXELS; i++) {
                uses[pixel(i)]++;
            }
            int best = 0;
            for (int k = 1; k < uses.length; k++) {
                if (uses[k] > uses[best]) {
                    best = k;
                }
            }
            return palette[best];
        }
        return mode == Mode.GRADIENT ? colours[colours.length / 2] : colours[0];
    }

    /** The player this look was made for. */
    public UUID owner() {
        return owner;
    }

    /** When the look was made (ms since 1970); 0 when unknown. */
    public long made() {
        return made;
    }

    /** The same look marked as made at {@code ms} (a look saved by the owner and put on later counts from when it is put on). */
    public TotemLook madeAt(long ms) {
        return new TotemLook(owner, mode, colours, run, head, hat, hatTint, palette, paint, ms);
    }

    public Mode mode() {
        return mode;
    }

    public int[] colours() {
        return colours.clone();
    }

    public Run run() {
        return run;
    }

    public Head head() {
        return head;
    }

    /** The hat's id, or "" for none. */
    public String hat() {
        return hat;
    }

    public int[] palette() {
        return palette.clone();
    }

    public byte[] paint() {
        return paint.clone();
    }

    /** The palette index of painted pixel {@code i}. */
    public int pixel(int i) {
        int b = paint[i >> 1] & 0xFF;
        return (i & 1) == 0 ? b >> 4 : b & 0x0F;
    }

    /** Sets pixel {@code i} of a painting held as {@link #PAINT_BYTES} bytes to palette index {@code index}. */
    public static void setPixel(byte[] paint, int i, int index) {
        int b = paint[i >> 1] & 0xFF;
        b = (i & 1) == 0 ? (b & 0x0F) | (index << 4) : (b & 0xF0) | index;
        paint[i >> 1] = (byte) b;
    }

    /** Why this look cannot be worn, as a message key's last part, or null when it is sound. */
    @Nullable
    public String problem() {
        for (int c : colours) {
            if (c < 0 || c > 0xFFFFFF) {
                return "bad_colour";
            }
        }
        for (int c : palette) {
            if (c < 0 || c > 0xFFFFFF) {
                return "bad_colour";
            }
        }
        if (mode == Mode.PAINT ? colours.length != 0 : palette.length != 0 || paint.length != 0) {
            return "bad_look"; // only a painting carries a palette and pixels, and only a colour or gradient carries colours
        }
        switch (mode) {
            case SOLID -> {
                if (colours.length != 1) {
                    return "bad_colours";
                }
            }
            case GRADIENT -> {
                if (colours.length < 2 || colours.length > MAX_STOPS) {
                    return "bad_colours";
                }
            }
            case PAINT -> {
                if (palette.length < 1 || palette.length > MAX_PAINT_COLOURS) {
                    return "too_many_colours";
                }
                if (paint.length != PAINT_BYTES) {
                    return "bad_painting";
                }
                for (int i = 0; i < PAINT_PIXELS; i++) {
                    if (pixel(i) >= palette.length) {
                        return "bad_painting";
                    }
                }
            }
        }
        if (!hat.isEmpty() && !TotemHats.known(hat)) {
            return "unknown_hat";
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("For", owner);
        t.putByte("Mode", (byte) mode.ordinal());
        t.putLong("Made", made);
        if (colours.length > 0) {
            t.putIntArray("Colours", colours);
        }
        if (run != Run.UP) {
            t.putByte("Run", (byte) run.ordinal());
        }
        if (head != Head.TOTEM) {
            t.putByte("Head", (byte) head.ordinal());
        }
        if (!hat.isEmpty()) {
            t.putString("Hat", hat);
        }
        if (hatTint) {
            t.putBoolean("HatTint", true);
        }
        if (palette.length > 0) {
            t.putIntArray("Palette", palette);
            t.putByteArray("Paint", paint);
        }
        return t;
    }

    /** The look in a tag, or null when the tag holds none or a look that is not sound (an unknown hat after an update, say). */
    @Nullable
    public static TotemLook load(@Nullable CompoundTag t) {
        if (t == null || !t.hasUUID("For")) {
            return null;
        }
        int m = t.getByte("Mode"), r = t.getByte("Run"), h = t.getByte("Head");
        if (m < 0 || m >= Mode.values().length || r < 0 || r >= Run.values().length || h < 0 || h >= Head.values().length) {
            return null;
        }
        TotemLook look = new TotemLook(t.getUUID("For"), Mode.values()[m], t.getIntArray("Colours"), Run.values()[r], Head.values()[h],
                t.getString("Hat"), t.getBoolean("HatTint"), t.getIntArray("Palette"), t.getByteArray("Paint"), t.getLong("Made"));
        return look.problem() == null ? look : null;
    }

    /** The look an item or block tag carries, only when it was made for {@code owner}. */
    @Nullable
    public static TotemLook readFor(@Nullable CompoundTag tag, @Nullable UUID owner) {
        if (tag == null || owner == null || !tag.contains(TAG)) {
            return null;
        }
        TotemLook look = load(tag.getCompound(TAG));
        return look != null && look.owner.equals(owner) ? look : null;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TotemLook l && owner.equals(l.owner) && mode == l.mode && Arrays.equals(colours, l.colours) && run == l.run
                && head == l.head && hat.equals(l.hat) && hatTint == l.hatTint && Arrays.equals(palette, l.palette) && Arrays.equals(paint, l.paint) && made == l.made;
    }

    @Override
    public int hashCode() {
        return owner.hashCode() * 31 + mode.hashCode() * 7 + Arrays.hashCode(colours) + hat.hashCode() + Arrays.hashCode(paint);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(mode.name().toLowerCase(java.util.Locale.ROOT));
        if (mode == Mode.PAINT) {
            s.append(" (").append(palette.length).append(" colours)");
        } else {
            for (int c : colours) {
                s.append(' ').append(String.format("#%06X", c));
            }
            if (mode == Mode.GRADIENT) {
                s.append(run == Run.UP ? " top to base" : " side to side");
            }
        }
        s.append(", head ").append(head == Head.PLAYER ? "the owner's" : "the totem's");
        s.append(", hat ").append(hat.isEmpty() ? "none" : hat + (hatTint ? " in the body's colours" : ""));
        return s.toString();
    }
}
