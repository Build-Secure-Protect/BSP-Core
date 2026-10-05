package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkActionPacket;
import com.mrgregles.bsp_core.network.ChunkViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * The chunk picker ("Map Tiles"): a top-down map of the 7 x 7 chunks around a totem or projector, one
 * tile per chunk, drawn from the terrain the client has loaded. Picked chunks are framed in turquoise,
 * chunks the same totem holds elsewhere in violet, and chunks out of range are dark. Shared by the
 * totem's CHUNKS tab and the projector screen; the server sends what to show in a {@link ChunkViewPacket}.
 */
public final class ChunkMap {
    private static final int SPAN = 7, PIXELS = SPAN * 16, TQ = 0xFF19D3B0, VIO = 0xFFB58CFF, GOLD = 0xFFFFD23A;
    private static final ResourceLocation TEXTURE = new ResourceLocation(BSPCore.MODID, "chunk_map");
    @Nullable
    private static ChunkViewPacket view;
    @Nullable
    private static DynamicTexture texture;
    @Nullable
    private static BlockPos builtFor;
    private static long builtAt;

    private ChunkMap() {}

    public static void receive(ChunkViewPacket msg) {
        view = msg;
        if (msg.has(ChunkViewPacket.OPEN) && msg.projector()) {
            Minecraft.getInstance().setScreen(new ProjectorScreen(msg.pos()));
        }
    }

    /** The latest data for the picker at {@code pos}, or null if the server has not answered yet. */
    @Nullable
    public static ChunkViewPacket viewFor(BlockPos pos) {
        return view != null && view.pos().equals(pos) ? view : null;
    }

    public static void request(BlockPos pos) {
        BSPNetwork.CHANNEL.sendToServer(new ChunkActionPacket(pos, ChunkActionPacket.VIEW, 0, 0));
    }

    private static boolean contains(long[] chunks, long chunk) {
        for (long c : chunks) {
            if (c == chunk) {
                return true;
            }
        }
        return false;
    }

    /** Redraws the terrain every two seconds, so chunks that arrive while the screen is open fill in. */
    private static void ensure(Level level, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (texture == null) {
            texture = new DynamicTexture(PIXELS, PIXELS, false);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        long now = level.getGameTime();
        if (pos.equals(builtFor) && now - builtAt < 40 && now >= builtAt) {
            return;
        }
        builtFor = pos;
        builtAt = now;
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int x0 = ((pos.getX() >> 4) - SPAN / 2) << 4, z0 = ((pos.getZ() >> 4) - SPAN / 2) << 4;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int px = 0; px < PIXELS; px++) {
            for (int pz = 0; pz < PIXELS; pz++) {
                int wx = x0 + px, wz = z0 + pz, rgb = 0x1A1E26;
                if (level.hasChunk(wx >> 4, wz >> 4)) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                    MapColor colour = MapColor.NONE;
                    for (int y = top - 1; y >= Math.max(level.getMinBuildHeight(), top - 8) && colour == MapColor.NONE; y--) {
                        colour = level.getBlockState(p.set(wx, y, wz)).getMapColor(level, p);
                    }
                    if (colour != MapColor.NONE) {
                        // lit from the north like a vanilla map: a step up is brighter, a step down darker
                        int north = level.hasChunk(wx >> 4, (wz - 1) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) : top;
                        int shade = top > north ? 255 : top < north ? 180 : 220;
                        rgb = ((colour.col >> 16 & 0xFF) * shade / 255) << 16 | ((colour.col >> 8 & 0xFF) * shade / 255) << 8 | (colour.col & 0xFF) * shade / 255;
                    }
                }
                image.setPixelRGBA(px, pz, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF)); // stored as ABGR
            }
        }
        texture.upload();
    }

    private static void frame(GuiGraphics g, int x, int y, int size, int thick, int colour) {
        g.fill(x, y, x + size, y + thick, colour);
        g.fill(x, y + size - thick, x + size, y + size, colour);
        g.fill(x, y + thick, x + thick, y + size - thick, colour);
        g.fill(x + size - thick, y + thick, x + size, y + size - thick, colour);
    }

    /** Draws the map with its top-left corner at {@code x, y}; each chunk is {@code tile} pixels square. North is up. */
    public static void draw(GuiGraphics g, int x, int y, int tile, ChunkViewPacket v, int mouseX, int mouseY) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ensure(level, v.pos());
        int size = tile * SPAN, cx = v.pos().getX() >> 4, cz = v.pos().getZ() >> 4;
        g.blit(TEXTURE, x, y, size, size, 0f, 0f, PIXELS, PIXELS, PIXELS, PIXELS);
        boolean edit = v.has(ChunkViewPacket.EDIT);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                int tx = x + (dx + 3) * tile, ty = y + (dz + 3) * tile;
                long chunk = ChunkPos.asLong(cx + dx, cz + dz);
                boolean inRange = Math.max(Math.abs(dx), Math.abs(dz)) <= v.radius() && v.slots() > 0;
                if (!inRange) {
                    g.fill(tx, ty, tx + tile, ty + tile, 0xC40A0C10);
                }
                frame(g, tx, ty, tile, 1, 0x30000000);
                if (contains(v.mine(), chunk)) {
                    g.fill(tx, ty, tx + tile, ty + tile, 0x3319D3B0);
                    frame(g, tx, ty, tile, 2, TQ);
                } else if (contains(v.others(), chunk)) {
                    g.fill(tx, ty, tx + tile, ty + tile, 0x33B58CFF);
                    frame(g, tx, ty, tile, 2, VIO);
                } else if (edit && inRange && mouseX >= tx && mouseX < tx + tile && mouseY >= ty && mouseY < ty + tile) {
                    frame(g, tx, ty, tile, 1, 0xB0FFFFFF);
                }
            }
        }
        int mx = x + 3 * tile + tile / 2, my = y + 3 * tile + tile / 2;
        g.fill(mx - 4, my - 4, mx + 4, my + 4, 0xFF000000);
        g.fill(mx - 3, my - 3, mx + 3, my + 3, v.projector() ? VIO : GOLD);
    }

    /** Handles a click on a map drawn at {@code x, y}. Returns true if the click landed on the map. */
    public static boolean click(double mouseX, double mouseY, int x, int y, int tile, ChunkViewPacket v) {
        if (mouseX < x || mouseY < y || mouseX >= x + tile * SPAN || mouseY >= y + tile * SPAN) {
            return false;
        }
        if (v.has(ChunkViewPacket.EDIT)) {
            int dx = (int) (mouseX - x) / tile - 3, dz = (int) (mouseY - y) / tile - 3;
            BSPNetwork.CHANNEL.sendToServer(new ChunkActionPacket(v.pos(), ChunkActionPacket.TOGGLE, (v.pos().getX() >> 4) + dx, (v.pos().getZ() >> 4) + dz));
        }
        return true;
    }

    /** A row of chunk slots: filled for chunks in use, outlined for free ones. */
    public static void pips(GuiGraphics g, int x, int y, ChunkViewPacket v) {
        for (int i = 0; i < Math.min(v.slots(), 12); i++) {
            g.fill(x + i * 9, y, x + i * 9 + 7, y + 7, TQ);
            if (i >= v.used()) {
                g.fill(x + i * 9 + 1, y + 1, x + i * 9 + 6, y + 6, 0xFF0C0E12);
            }
        }
    }
}
